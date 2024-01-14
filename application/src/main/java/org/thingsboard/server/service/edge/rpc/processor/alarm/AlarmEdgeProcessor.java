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
package org.thingsboard.server.service.edge.rpc.processor.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.gen.edge.v1.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class AlarmEdgeProcessor extends BaseAlarmProcessor implements AlarmProcessor {

    @Override
    public ListenableFuture<Void> processAlarmMsgFromEdge(TenantId tenantId, EdgeId edgeId, AlarmUpdateMsg alarmUpdateMsg) {
        log.trace("[{}] processAlarmMsgFromEdge [{}]", tenantId, alarmUpdateMsg);
        try {
            edgeSynchronizationManager.getEdgeId().set(edgeId);
            return processAlarmMsg(tenantId, alarmUpdateMsg);
        } finally {
            edgeSynchronizationManager.getEdgeId().remove();
        }
    }

    @Override
    public DownlinkMsg convertAlarmEventToDownlink(EdgeEvent edgeEvent, EdgeVersion edgeVersion) {
        AlarmUpdateMsg alarmUpdateMsg = convertAlarmEventToAlarmMsg(edgeEvent.getTenantId(), edgeEvent.getEntityId(),
                edgeEvent.getAction(), edgeEvent.getBody(), edgeVersion);
        if (alarmUpdateMsg != null) {
            return DownlinkMsg.newBuilder()
                    .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                    .addAlarmUpdateMsg(alarmUpdateMsg)
                    .build();
        }
        return null;
    }

    public ListenableFuture<Void> processAlarmNotification(TenantId tenantId, TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg) {
        EdgeEventActionType actionType = EdgeEventActionType.valueOf(edgeNotificationMsg.getAction());
        AlarmId alarmId = new AlarmId(new UUID(edgeNotificationMsg.getEntityIdMSB(), edgeNotificationMsg.getEntityIdLSB()));
        EdgeId originatorEdgeId = safeGetEdgeId(edgeNotificationMsg.getOriginatorEdgeIdMSB(), edgeNotificationMsg.getOriginatorEdgeIdLSB());
        switch (actionType) {
            case DELETED:
                Alarm deletedAlarm = JacksonUtil.fromString(edgeNotificationMsg.getBody(), Alarm.class);
                if (deletedAlarm == null) {
                    return Futures.immediateFuture(null);
                }
                List<ListenableFuture<Void>> delFutures = pushEventToAllRelatedEdges(tenantId, deletedAlarm.getOriginator(),
                        alarmId, actionType, JacksonUtil.valueToTree(deletedAlarm), originatorEdgeId);
                return Futures.transform(Futures.allAsList(delFutures), voids -> null, dbCallbackExecutorService);
            default:
                ListenableFuture<Alarm> alarmFuture = alarmService.findAlarmByIdAsync(tenantId, alarmId);
                return Futures.transformAsync(alarmFuture, alarm -> {
                    if (alarm == null) {
                        return Futures.immediateFuture(null);
                    }
                    EdgeEventType type = EdgeUtils.getEdgeEventTypeByEntityType(alarm.getOriginator().getEntityType());
                    if (type == null) {
                        return Futures.immediateFuture(null);
                    }
                    List<ListenableFuture<Void>> futures = pushEventToAllRelatedEdges(tenantId, alarm.getOriginator(),
                            alarmId, actionType, null, originatorEdgeId);
                    return Futures.transform(Futures.allAsList(futures), voids -> null, dbCallbackExecutorService);
                }, dbCallbackExecutorService);
        }
    }

    private List<ListenableFuture<Void>> pushEventToAllRelatedEdges(TenantId tenantId, EntityId originatorId, AlarmId alarmId, EdgeEventActionType actionType, JsonNode body, EdgeId sourceEdgeId) {
        PageLink pageLink = new PageLink(DEFAULT_PAGE_SIZE);
        PageData<EdgeId> pageData;
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        do {
            pageData = edgeService.findRelatedEdgeIdsByEntityId(tenantId, originatorId, pageLink);
            if (pageData != null && pageData.getData() != null && !pageData.getData().isEmpty()) {
                for (EdgeId relatedEdgeId : pageData.getData()) {
                    if (!relatedEdgeId.equals(sourceEdgeId)) {
                        futures.add(saveEdgeEvent(tenantId,
                                relatedEdgeId,
                                EdgeEventType.ALARM,
                                actionType,
                                alarmId,
                                body));
                    }
                }
                if (pageData.hasNext()) {
                    pageLink = pageLink.nextPageLink();
                }
            }
        } while (pageData != null && pageData.hasNext());
        return futures;
    }
}
