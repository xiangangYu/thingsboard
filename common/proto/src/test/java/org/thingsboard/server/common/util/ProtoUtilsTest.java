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
package org.thingsboard.server.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKey;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.rpc.RpcError;
import org.thingsboard.server.common.data.rpc.ToDeviceRpcRequestBody;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.msg.edge.EdgeEventUpdateMsg;
import org.thingsboard.server.common.msg.edge.FromEdgeSyncResponse;
import org.thingsboard.server.common.msg.edge.ToEdgeSyncRequest;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.rpc.FromDeviceRpcResponse;
import org.thingsboard.server.common.msg.rpc.FromDeviceRpcResponseActorMsg;
import org.thingsboard.server.common.msg.rpc.RemoveRpcActorMsg;
import org.thingsboard.server.common.msg.rpc.ToDeviceRpcRequest;
import org.thingsboard.server.common.msg.rpc.ToDeviceRpcRequestActorMsg;
import org.thingsboard.server.common.msg.rule.engine.DeviceAttributesEventNotificationMsg;
import org.thingsboard.server.common.msg.rule.engine.DeviceCredentialsUpdateNotificationMsg;
import org.thingsboard.server.common.msg.rule.engine.DeviceEdgeUpdateMsg;
import org.thingsboard.server.common.msg.rule.engine.DeviceNameOrTypeUpdateMsg;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProtoUtilsTest {

    TenantId tenantId = TenantId.fromUUID(UUID.fromString("35e10f77-16e7-424d-ae46-ee780f87ac4f"));
    EntityId entityId = new RuleChainId(UUID.fromString("c640b635-4f0f-41e6-b10b-25a86003094e"));
    DeviceId deviceId = new DeviceId(UUID.fromString("ceebb9e5-4239-437c-a507-dc5f71f1232d"));
    EdgeId edgeId = new EdgeId(UUID.fromString("364be452-2183-459b-af93-1ddb325feac1"));
    UUID id = UUID.fromString("31a07d85-6ed5-46f8-83c0-6715cb0a8782");

    @Test
    void protoComponentLifecycleSerialization() {
        ComponentLifecycleMsg msg = new ComponentLifecycleMsg(tenantId, entityId, ComponentLifecycleEvent.UPDATED);
        assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(msg))).as("deserialized").isEqualTo(msg);
        msg = new ComponentLifecycleMsg(tenantId, entityId, ComponentLifecycleEvent.STARTED);
        assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(msg))).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoEntityTypeSerialization() {
        for (EntityType entityType : EntityType.values()) {
            assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(entityType))).as(entityType.getNormalName()).isEqualTo(entityType);
        }
    }

    @Test
    void protoEdgeEventUpdateSerialization() {
        EdgeEventUpdateMsg msg = new EdgeEventUpdateMsg(tenantId, edgeId);
        assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(msg))).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoToEdgeSyncRequestSerialization() {
        ToEdgeSyncRequest msg = new ToEdgeSyncRequest(id, tenantId, edgeId);
        assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(msg))).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoFromEdgeSyncResponseSerialization() {
        FromEdgeSyncResponse msg = new FromEdgeSyncResponse(id, tenantId, edgeId, true);
        assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(msg))).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoDeviceEdgeUpdateSerialization() {
        DeviceEdgeUpdateMsg msg = new DeviceEdgeUpdateMsg(tenantId, deviceId, edgeId);
        TransportProtos.ToDeviceActorNotificationMsgProto serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoDeviceNameOrTypeSerialization() {
        String deviceName = "test", deviceType = "test";
        DeviceNameOrTypeUpdateMsg msg = new DeviceNameOrTypeUpdateMsg(tenantId, deviceId, deviceName, deviceType);
        TransportProtos.ToDeviceActorNotificationMsgProto serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoDeviceAttributesEventSerialization() {
        DeviceAttributesEventNotificationMsg msg = new DeviceAttributesEventNotificationMsg(tenantId, deviceId, null, "CLIENT_SCOPE",
                List.of(new BaseAttributeKvEntry(System.currentTimeMillis(), new StringDataEntry("key", "value"))), false);
        TransportProtos.ToDeviceActorNotificationMsgProto serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);

        msg = new DeviceAttributesEventNotificationMsg(tenantId, deviceId, null, "SERVER_SCOPE",
                List.of(new BaseAttributeKvEntry(System.currentTimeMillis(), new DoubleDataEntry("doubleEntry", 231.5)),
                        new BaseAttributeKvEntry(System.currentTimeMillis(), new JsonDataEntry("jsonEntry", "jsonValue"))), false);
        serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);

        msg = new DeviceAttributesEventNotificationMsg(tenantId, deviceId, null, "SERVER_SCOPE",
                List.of(new BaseAttributeKvEntry(System.currentTimeMillis(), new DoubleDataEntry("entry", 11.3)),
                        new BaseAttributeKvEntry(System.currentTimeMillis(), new BooleanDataEntry("jsonEntry", true))), false);
        serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);

        msg = new DeviceAttributesEventNotificationMsg(tenantId, deviceId, Set.of(new AttributeKey("SHARED_SCOPE", "attributeKey")), null, null, true);
        serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoDeviceCredentialsUpdateSerialization() {
        DeviceCredentials deviceCredentials = new DeviceCredentials();
        deviceCredentials.setDeviceId(deviceId);
        deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
        deviceCredentials.setCredentialsValue("test");
        deviceCredentials.setCredentialsId("test");
        DeviceCredentialsUpdateNotificationMsg msg = new DeviceCredentialsUpdateNotificationMsg(tenantId, deviceId, deviceCredentials);
        TransportProtos.ToDeviceActorNotificationMsgProto serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoToDeviceRpcRequestSerialization() {
        String serviceId = "cadcaac6-85c3-4211-9756-f074dcd1e7f7";
        ToDeviceRpcRequest request = new ToDeviceRpcRequest(id, tenantId, deviceId, true, 0, new ToDeviceRpcRequestBody("method", "params"), false, 0, "");
        ToDeviceRpcRequestActorMsg msg = new ToDeviceRpcRequestActorMsg(serviceId, request);
        TransportProtos.ToDeviceActorNotificationMsgProto serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoFromDeviceRpcResponseSerialization() {
        FromDeviceRpcResponseActorMsg msg = new FromDeviceRpcResponseActorMsg(23, tenantId, deviceId, new FromDeviceRpcResponse(id, "response", RpcError.NOT_FOUND));
        TransportProtos.ToDeviceActorNotificationMsgProto serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoRemoveRpcActorSerialization() {
        RemoveRpcActorMsg msg = new RemoveRpcActorMsg(tenantId, deviceId, id);
        TransportProtos.ToDeviceActorNotificationMsgProto serializedMsg = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(serializedMsg);
        assertThat(ProtoUtils.fromProto(serializedMsg)).as("deserialized").isEqualTo(msg);
    }
}
