/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ImageDescriptor;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TbImageDeleteResult;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.resource.ImageCacheKey;
import org.thingsboard.server.dao.resource.ImageService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.AbstractTbEntityService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@TbCoreComponent
public class DefaultTbImageService extends AbstractTbEntityService implements TbImageService {

    private final TbClusterService clusterService;
    private final ImageService imageService;
    private final Cache<ImageCacheKey, String> etagCache;

    public DefaultTbImageService(TbClusterService clusterService, ImageService imageService,
                                 @Value("${cache.image.etag.timeToLiveInMinutes:44640}") int cacheTtl,
                                 @Value("${cache.image.etag.maxSize:10000}") int cacheMaxSize) {
        this.clusterService = clusterService;
        this.imageService = imageService;
        this.etagCache = Caffeine.newBuilder()
                .expireAfterAccess(cacheTtl, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .build();
    }

    @Override
    public String getETag(ImageCacheKey imageCacheKey) {
        return etagCache.getIfPresent(imageCacheKey);
    }

    @Override
    public void putETag(ImageCacheKey imageCacheKey, String etag) {
        etagCache.put(imageCacheKey, etag);
    }

    @Override
    public void evictETags(ImageCacheKey imageCacheKey) {
        etagCache.invalidate(imageCacheKey);
        if (imageCacheKey.getPublicResourceKey() == null) {
            etagCache.invalidate(imageCacheKey.withPreview(true));
        }
    }

    @Override
    public TbResourceInfo save(TbResource image, User user) throws Exception {
        ActionType actionType = image.getId() == null ? ActionType.ADDED : ActionType.UPDATED;
        TenantId tenantId = image.getTenantId();
        try {
            var oldEtag = getEtag(image);
            TbResourceInfo existingImage = null;
            if (image.getId() == null && StringUtils.isNotEmpty(image.getResourceKey())) {
                existingImage = imageService.getImageInfoByTenantIdAndKey(tenantId, image.getResourceKey());
                if (existingImage != null) {
                    image.setId(existingImage.getId());
                }
            }
            TbResourceInfo savedImage = imageService.saveImage(image);
            notificationEntityService.logEntityAction(tenantId, savedImage.getId(), savedImage, actionType, user);

            List<ImageCacheKey> toEvict = new ArrayList<>();
            if (oldEtag.isPresent()) {
                var newEtag = getEtag(savedImage);
                if (newEtag.isPresent() && !oldEtag.get().equals(newEtag.get())) {
                    toEvict.add(ImageCacheKey.forImage(tenantId, image.getResourceKey()));
                    if (image.isPublic()) {
                        toEvict.add(ImageCacheKey.forPublicImage(savedImage.getPublicResourceKey()));
                    }
                }
            }
            if (existingImage != null && image.isPublic() != existingImage.isPublic()) {
                toEvict.add(ImageCacheKey.forPublicImage(image.getPublicResourceKey()));
            }
            if (!toEvict.isEmpty()) {
                evictFromCache(tenantId, toEvict);
            }
            return savedImage;
        } catch (Exception e) {
            image.setData(null);
            notificationEntityService.logEntityAction(tenantId, emptyId(EntityType.TB_RESOURCE), image, actionType, user, e);
            throw e;
        }
    }

    private Optional<String> getEtag(TbResourceInfo image) throws JsonProcessingException {
        var descriptor = image.getDescriptor(ImageDescriptor.class);
        return Optional.ofNullable(descriptor != null ? descriptor.getEtag() : null);
    }

    private Optional<String> getPreviewEtag(TbResourceInfo image) throws JsonProcessingException {
        var descriptor = image.getDescriptor(ImageDescriptor.class);
        descriptor = descriptor != null ? descriptor.getPreviewDescriptor() : null;
        return Optional.ofNullable(descriptor != null ? descriptor.getEtag() : null);
    }

    @Override
    public TbResourceInfo save(TbResourceInfo imageInfo, TbResourceInfo oldImageInfo, User user) {
        TenantId tenantId = imageInfo.getTenantId();
        TbResourceId imageId = imageInfo.getId();
        try {
            imageInfo = imageService.saveImageInfo(imageInfo);
            notificationEntityService.logEntityAction(tenantId, imageId, imageInfo, ActionType.UPDATED, user);

            if (imageInfo.isPublic() != oldImageInfo.isPublic()) {
                evictFromCache(tenantId, List.of(ImageCacheKey.forPublicImage(imageInfo.getPublicResourceKey())));
            }
            return imageInfo;
        } catch (Exception e) {
            notificationEntityService.logEntityAction(tenantId, imageId, imageInfo, ActionType.UPDATED, user, e);
            throw e;
        }
    }

    @Override
    public TbImageDeleteResult delete(TbResourceInfo imageInfo, User user, boolean force) {
        TenantId tenantId = imageInfo.getTenantId();
        TbResourceId imageId = imageInfo.getId();
        try {
            TbImageDeleteResult result = imageService.deleteImage(imageInfo, force);
            if (result.isSuccess()) {
                notificationEntityService.logEntityAction(tenantId, imageId, imageInfo, ActionType.DELETED, user, imageId.toString());

                List<ImageCacheKey> toEvict = new ArrayList<>();
                toEvict.add(ImageCacheKey.forImage(tenantId, imageInfo.getResourceKey()));
                if (imageInfo.isPublic()) {
                    toEvict.add(ImageCacheKey.forPublicImage(imageInfo.getPublicResourceKey()));
                }
                evictFromCache(tenantId, toEvict);
            }
            return result;
        } catch (Exception e) {
            notificationEntityService.logEntityAction(tenantId, imageId, ActionType.DELETED, user, e, imageId.toString());
            throw e;
        }
    }

    private void evictFromCache(TenantId tenantId, List<ImageCacheKey> toEvict) {
        toEvict.forEach(this::evictETags);
        clusterService.broadcastToCore(TransportProtos.ToCoreNotificationMsg.newBuilder()
                .setResourceCacheInvalidateMsg(TransportProtos.ResourceCacheInvalidateMsg.newBuilder()
                        .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                        .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                        .addAllKeys(toEvict.stream().map(ImageCacheKey::toProto).collect(Collectors.toList()))
                        .build())
                .build());
    }

}
