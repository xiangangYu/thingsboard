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
package org.thingsboard.server.service.subscription;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Tb订阅
 * @param <T>
 */
@Data
@AllArgsConstructor
public abstract class TbSubscription<T> {

    /**
     * 服务ID
     */
    private final String serviceId;

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 订阅ID
     */
    private final int subscriptionId;

    /**
     * 租户ID
     */
    private final TenantId tenantId;

    /**
     * 实体ID
     */
    private final EntityId entityId;

    /**
     * Tb订阅类型
     */
    private final TbSubscriptionType type;

    /**
     * 更新处理器
     */
    private final BiConsumer<TbSubscription<T>, T> updateProcessor;

    /**
     * 序列
     */
    protected final AtomicInteger sequence = new AtomicInteger();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TbSubscription that = (TbSubscription) o;
        return subscriptionId == that.subscriptionId &&
                sessionId.equals(that.sessionId) &&
                tenantId.equals(that.tenantId) &&
                entityId.equals(that.entityId) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, subscriptionId, tenantId, entityId, type);
    }
}
