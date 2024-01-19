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
package org.thingsboard.server.service.entitiy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.rule.engine.DeviceCredentialsUpdateNotificationMsg;
import org.thingsboard.server.service.action.EntityActionService;
import org.thingsboard.server.service.gateway_device.GatewayNotificationsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbNotificationEntityService implements TbNotificationEntityService {

    private final EntityActionService entityActionService;
    private final TbClusterService tbClusterService;
    private final GatewayNotificationsService gatewayNotificationsService;

    @Override
    public <I extends EntityId> void logEntityAction(TenantId tenantId, I entityId, ActionType actionType,
                                                     User user, Exception e, Object... additionalInfo) {
        logEntityAction(tenantId, entityId, null, null, actionType, user, e, additionalInfo);
    }

    @Override
    public <E extends HasName, I extends EntityId> void logEntityAction(TenantId tenantId, I entityId, E entity,
                                                                        ActionType actionType, User user, Object... additionalInfo) {
        logEntityAction(tenantId, entityId, entity, null, actionType, user, null, additionalInfo);
    }

    @Override
    public <E extends HasName, I extends EntityId> void logEntityAction(TenantId tenantId, I entityId, E entity,
                                                                        ActionType actionType, User user, Exception e,
                                                                        Object... additionalInfo) {
        logEntityAction(tenantId, entityId, entity, null, actionType, user, e, additionalInfo);
    }

    @Override
    public <E extends HasName, I extends EntityId> void logEntityAction(TenantId tenantId, I entityId, E entity, CustomerId customerId,
                                                                        ActionType actionType, User user, Object... additionalInfo) {
        logEntityAction(tenantId, entityId, entity, customerId, actionType, user, null, additionalInfo);
    }

    @Override
    public <E extends HasName, I extends EntityId> void logEntityAction(TenantId tenantId, I entityId, E entity,
                                                                        CustomerId customerId, ActionType actionType,
                                                                        User user, Exception e, Object... additionalInfo) {
        if (user != null) {
            entityActionService.logEntityAction(user, entityId, entity, customerId, actionType, e, additionalInfo);
        } else if (e == null) {
            entityActionService.pushEntityActionToRuleEngine(entityId, entity, tenantId, customerId, actionType, null, additionalInfo);
        }
    }

    @Override
    public void notifyCreateOrUpdateTenant(Tenant tenant, ComponentLifecycleEvent event) {
        tbClusterService.onTenantChange(tenant, null);
        tbClusterService.broadcastEntityStateChangeEvent(tenant.getId(), tenant.getId(), event);
    }

    @Override
    public void notifyDeleteTenant(Tenant tenant) {
        tbClusterService.onTenantDelete(tenant, null);
        tbClusterService.broadcastEntityStateChangeEvent(tenant.getId(), tenant.getId(), ComponentLifecycleEvent.DELETED);
    }

    @Override
    public void notifyCreateOrUpdateDevice(TenantId tenantId, DeviceId deviceId, CustomerId customerId,
                                           Device device, Device oldDevice, ActionType actionType,
                                           User user, Object... additionalInfo) {
        tbClusterService.onDeviceUpdated(device, oldDevice);
        logEntityAction(tenantId, deviceId, device, customerId, actionType, user, additionalInfo);
    }

    @Override
    public void notifyDeleteDevice(TenantId tenantId, DeviceId deviceId, CustomerId customerId, Device device,
                                   User user, Object... additionalInfo) {
        gatewayNotificationsService.onDeviceDeleted(device);
        tbClusterService.onDeviceDeleted(tenantId, device, null);
        logEntityAction(tenantId, deviceId, device, customerId, ActionType.DELETED, user, additionalInfo);
    }

    @Override
    public void notifyUpdateDeviceCredentials(TenantId tenantId, DeviceId deviceId, CustomerId customerId, Device device,
                                              DeviceCredentials deviceCredentials, User user) {
        tbClusterService.pushMsgToCore(new DeviceCredentialsUpdateNotificationMsg(tenantId, deviceCredentials.getDeviceId(), deviceCredentials), null);
        logEntityAction(tenantId, deviceId, device, customerId, ActionType.CREDENTIALS_UPDATED, user, deviceCredentials);
    }

    @Override
    public void notifyAssignDeviceToTenant(TenantId tenantId, TenantId newTenantId, DeviceId deviceId, CustomerId customerId,
                                           Device device, Tenant tenant, User user, Object... additionalInfo) {
        tbClusterService.onDeviceAssignedToTenant(tenantId, device);
        logEntityAction(tenantId, deviceId, device, customerId, ActionType.ASSIGNED_TO_TENANT, user, additionalInfo);
        pushAssignedFromNotification(tenant, newTenantId, device);
    }

    @Override
    public void notifyCreateOrUpdateOrDeleteEdge(TenantId tenantId, EdgeId edgeId, CustomerId customerId, Edge edge,
                                                 ActionType actionType, User user, Object... additionalInfo) {
        ComponentLifecycleEvent lifecycleEvent;
        switch (actionType) {
            case ADDED:
                lifecycleEvent = ComponentLifecycleEvent.CREATED;
                break;
            case UPDATED:
                lifecycleEvent = ComponentLifecycleEvent.UPDATED;
                break;
            case DELETED:
                lifecycleEvent = ComponentLifecycleEvent.DELETED;
                break;
            default:
                throw new IllegalArgumentException("Unknown actionType: " + actionType);
        }
        tbClusterService.broadcastEntityStateChangeEvent(tenantId, edgeId, lifecycleEvent);
        logEntityAction(tenantId, edgeId, edge, customerId, actionType, user, additionalInfo);
    }

    @Override
    public void logEntityRelationAction(TenantId tenantId, CustomerId customerId, EntityRelation relation, User user,
                                        ActionType actionType, Exception e, Object... additionalInfo) {
        logEntityAction(tenantId, relation.getFrom(), null, customerId, actionType, user, e, additionalInfo);
        logEntityAction(tenantId, relation.getTo(), null, customerId, actionType, user, e, additionalInfo);
    }

    private void pushAssignedFromNotification(Tenant currentTenant, TenantId newTenantId, Device assignedDevice) {
        String data = JacksonUtil.toString(JacksonUtil.valueToTree(assignedDevice));
        if (data != null) {
            TbMsg tbMsg = TbMsg.newMsg(TbMsgType.ENTITY_ASSIGNED_FROM_TENANT, assignedDevice.getId(),
                    assignedDevice.getCustomerId(), getMetaDataForAssignedFrom(currentTenant), TbMsgDataType.JSON, data);
            tbClusterService.pushMsgToRuleEngine(newTenantId, assignedDevice.getId(), tbMsg, null);
        }
    }

    private TbMsgMetaData getMetaDataForAssignedFrom(Tenant tenant) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("assignedFromTenantId", tenant.getId().getId().toString());
        metaData.putValue("assignedFromTenantName", tenant.getName());
        return metaData;
    }
}
