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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.query.ComplexFilterPredicate;
import org.thingsboard.server.common.data.query.DynamicValue;
import org.thingsboard.server.common.data.query.DynamicValueSourceType;
import org.thingsboard.server.common.data.query.EntityCountQuery;
import org.thingsboard.server.common.data.query.FilterPredicateType;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.KeyFilterPredicate;
import org.thingsboard.server.common.data.query.SimpleKeyFilterPredicate;
import org.thingsboard.server.common.data.query.TsValue;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.service.ws.WebSocketService;
import org.thingsboard.server.service.ws.WebSocketSessionRef;
import org.thingsboard.server.service.ws.telemetry.cmd.v2.CmdUpdate;
import org.thingsboard.server.service.ws.telemetry.sub.TelemetrySubscriptionUpdate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tb抽象的订阅上下文
 * @param <T>
 */
@Slf4j
@Data
public abstract class TbAbstractSubCtx<T extends EntityCountQuery> {

    /**
     * 发送websocket锁
     */
    @Getter
    protected final Lock wsLock = new ReentrantLock(true);

    /**
     * 服务ID
     */
    protected final String serviceId;

    /**
     * 订阅服务统计
     */
    protected final SubscriptionServiceStatistics stats;

    /**
     * websocket服务
     */
    private final WebSocketService wsService;

    /**
     * 实体服务
     */
    protected final EntityService entityService;

    /**
     * 本地订阅服务
     */
    protected final TbLocalSubscriptionService localSubscriptionService;

    /**
     * 服务服务
     */
    protected final AttributesService attributesService;

    /**
     * websocket会话实体
     */
    protected final WebSocketSessionRef sessionRef;

    /**
     * 指令ID编号
     */
    protected final int cmdId;

    /**
     * 订阅到动态值的Key集合
     */
    protected final Set<Integer> subToDynamicValueKeySet;

    /**
     * 动态值Map
     */
    @Getter
    protected final Map<DynamicValueKey, List<DynamicValue>> dynamicValues;
    @Getter
    @Setter
    protected T query;

    /**
     * 可调度的future
     */
    @Setter
    protected volatile ScheduledFuture<?> refreshTask;

    /**
     * 任务已经停止
     */
    protected volatile boolean stopped;

    /**
     * 创建时间
     */
    @Getter
    protected long createdTime;

    public TbAbstractSubCtx(String serviceId, WebSocketService wsService,
                            EntityService entityService, TbLocalSubscriptionService localSubscriptionService,
                            AttributesService attributesService, SubscriptionServiceStatistics stats,
                            WebSocketSessionRef sessionRef, int cmdId) {
        this.createdTime = System.currentTimeMillis();
        this.serviceId = serviceId;
        this.wsService = wsService;
        this.entityService = entityService;
        this.localSubscriptionService = localSubscriptionService;
        this.attributesService = attributesService;
        this.stats = stats;
        this.sessionRef = sessionRef;
        this.cmdId = cmdId;
        this.subToDynamicValueKeySet = ConcurrentHashMap.newKeySet();
        this.dynamicValues = new ConcurrentHashMap<>();
    }

    /**
     * 设置并解析查询
     * @param query
     */
    public void setAndResolveQuery(T query) {
        dynamicValues.clear();
        this.query = query;
        if (query != null && query.getKeyFilters() != null) {
            for (KeyFilter filter : query.getKeyFilters()) {
                registerDynamicValues(filter.getPredicate());
            }
        }
        resolve(getTenantId(), getCustomerId(), getUserId());
    }

    /**
     * 解析查询
     * @param tenantId 租户ID
     * @param customerId 客户ID
     * @param userId 用户ID
     */
    public void resolve(TenantId tenantId, CustomerId customerId, UserId userId) {
        List<ListenableFuture<DynamicValueKeySub>> futures = new ArrayList<>();
        for (DynamicValueKey key : dynamicValues.keySet()) {
            switch (key.getSourceType()) {
                case CURRENT_TENANT:
                    futures.add(resolveEntityValue(tenantId, tenantId, key));
                    break;
                case CURRENT_CUSTOMER:
                    if (customerId != null && !customerId.isNullUid()) {
                        futures.add(resolveEntityValue(tenantId, customerId, key));
                    }
                    break;
                case CURRENT_USER:
                    if (userId != null && !userId.isNullUid()) {
                        futures.add(resolveEntityValue(tenantId, userId, key));
                    }
                    break;
            }
        }
        try {
            Map<EntityId, Map<String, DynamicValueKeySub>> tmpSubMap = new HashMap<>();
            // 多个future获取值
            for (DynamicValueKeySub sub : Futures.successfulAsList(futures).get()) {
                tmpSubMap.computeIfAbsent(sub.getEntityId(), tmp -> new HashMap<>()).put(sub.getKey().getSourceAttribute(), sub);
            }
            for (EntityId entityId : tmpSubMap.keySet()) {
                Map<String, Long> keyStates = new HashMap<>();
                Map<String, DynamicValueKeySub> dynamicValueKeySubMap = tmpSubMap.get(entityId);
                dynamicValueKeySubMap.forEach((k, v) -> keyStates.put(k, v.getLastUpdateTs()));
                int subIdx = sessionRef.getSessionSubIdSeq().incrementAndGet();
                TbAttributeSubscription sub = TbAttributeSubscription.builder()
                        .serviceId(serviceId)
                        .sessionId(sessionRef.getSessionId())
                        .subscriptionId(subIdx)
                        .tenantId(sessionRef.getSecurityCtx().getTenantId())
                        .entityId(entityId)
                        .updateProcessor((subscription, subscriptionUpdate) -> dynamicValueSubUpdate(subscription.getSessionId(), subscriptionUpdate, dynamicValueKeySubMap))
                        .queryTs(createdTime)
                        .allKeys(false)
                        .keyStates(keyStates)
                        .scope(TbAttributeSubscriptionScope.SERVER_SCOPE)
                        .build();
                subToDynamicValueKeySet.add(subIdx);
                localSubscriptionService.addSubscription(sub);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.info("[{}][{}][{}] Failed to resolve dynamic values: {}", tenantId, customerId, userId, dynamicValues.keySet());
        }

    }

    /**
     * 动态值订阅更新
     * @param sessionId 会话ID
     * @param subscriptionUpdate 遥测订阅更新
     * @param dynamicValueKeySubMap 动态值key订阅map
     */
    private void dynamicValueSubUpdate(String sessionId, TelemetrySubscriptionUpdate subscriptionUpdate,
                                       Map<String, DynamicValueKeySub> dynamicValueKeySubMap) {
        Map<String, TsValue> latestUpdate = new HashMap<>();
        subscriptionUpdate.getData().forEach((k, v) -> {
            Object[] data = (Object[]) v.get(0);
            latestUpdate.put(k, new TsValue((Long) data[0], (String) data[1]));
        });

        boolean invalidateFilter = false;
        for (Map.Entry<String, TsValue> entry : latestUpdate.entrySet()) {
            String k = entry.getKey();
            TsValue tsValue = entry.getValue();
            DynamicValueKeySub sub = dynamicValueKeySubMap.get(k);
            if (sub.updateValue(tsValue)) {
                invalidateFilter = true;
                updateDynamicValuesByKey(sub, tsValue);
            }
        }

        if (invalidateFilter) {
            update();
        }
    }

    /**
     * 抽象方法，是否时动态的
     * @return
     */
    public abstract boolean isDynamic();

    /**
     * 抽象方法，获取数据
     */
    public abstract void fetchData();

    /**
     * 抽象方法，更新
     */
    protected abstract void update();

    /**
     * 清除订阅
     */
    public void clearSubscriptions() {
        clearDynamicValueSubscriptions();
    }

    /**
     * 停止任务
     */
    public void stop() {
        stopped = true;
        // 取消任务
        cancelTasks();
        // 清除订阅
        clearSubscriptions();
    }

    /**
     * 动态值key订阅
     */
    @Data
    private static class DynamicValueKeySub {
        /**
         * 动态值key
         */
        private final DynamicValueKey key;

        /**
         * 实体ID
         */
        private final EntityId entityId;

        /**
         * 上次更新时间
         */
        private long lastUpdateTs;

        /**
         * 上次更新值
         */
        private String lastUpdateValue;

        /**
         * 更新值
         * @param value
         * @return
         */
        boolean updateValue(TsValue value) {
            if (value.getTs() > lastUpdateTs && (lastUpdateValue == null || !lastUpdateValue.equals(value.getValue()))) {
                this.lastUpdateTs = value.getTs();
                this.lastUpdateValue = value.getValue();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * 解析实体值
     * @param tenantId 租户ID
     * @param entityId 实体ID
     * @param key 动态值key
     * @return
     */
    private ListenableFuture<DynamicValueKeySub> resolveEntityValue(TenantId tenantId, EntityId entityId, DynamicValueKey key) {
        // 根据条件查询服务端属性
        ListenableFuture<Optional<AttributeKvEntry>> entry = attributesService.find(tenantId, entityId,
                AttributeScope.SERVER_SCOPE, key.getSourceAttribute());
        return Futures.transform(entry, attributeOpt -> {
            DynamicValueKeySub sub = new DynamicValueKeySub(key, entityId);
            if (attributeOpt.isPresent()) {
                AttributeKvEntry attribute = attributeOpt.get();
                sub.setLastUpdateTs(attribute.getLastUpdateTs());
                sub.setLastUpdateValue(attribute.getValueAsString());
                updateDynamicValuesByKey(sub, new TsValue(attribute.getLastUpdateTs(), attribute.getValueAsString()));
            }
            return sub;
        }, MoreExecutors.directExecutor());
    }

    /**
     * 更新动态值
     * @param sub
     * @param tsValue
     */
    @SuppressWarnings("unchecked")
    protected void updateDynamicValuesByKey(DynamicValueKeySub sub, TsValue tsValue) {
        DynamicValueKey dvk = sub.getKey();
        switch (dvk.getPredicateType()) {
            case STRING:
                dynamicValues.get(dvk).forEach(dynamicValue -> dynamicValue.setResolvedValue(tsValue.getValue()));
                break;
            case NUMERIC:
                try {
                    Double dValue = Double.parseDouble(tsValue.getValue());
                    dynamicValues.get(dvk).forEach(dynamicValue -> dynamicValue.setResolvedValue(dValue));
                } catch (NumberFormatException e) {
                    dynamicValues.get(dvk).forEach(dynamicValue -> dynamicValue.setResolvedValue(null));
                }
                break;
            case BOOLEAN:
                Boolean bValue = Boolean.parseBoolean(tsValue.getValue());
                dynamicValues.get(dvk).forEach(dynamicValue -> dynamicValue.setResolvedValue(bValue));
                break;
        }
    }

    /**
     * 注册动态值
     * @param predicate
     */
    @SuppressWarnings("unchecked")
    private void registerDynamicValues(KeyFilterPredicate predicate) {
        switch (predicate.getType()) {
            case STRING:
            case NUMERIC:
            case BOOLEAN:
                Optional<DynamicValue> value = getDynamicValueFromSimplePredicate((SimpleKeyFilterPredicate) predicate);
                if (value.isPresent()) {
                    DynamicValue dynamicValue = value.get();
                    DynamicValueKey key = new DynamicValueKey(
                            predicate.getType(),
                            dynamicValue.getSourceType(),
                            dynamicValue.getSourceAttribute());
                    dynamicValues.computeIfAbsent(key, tmp -> new ArrayList<>()).add(dynamicValue);
                }
                break;
            case COMPLEX:
                ((ComplexFilterPredicate) predicate).getPredicates().forEach(this::registerDynamicValues);
        }
    }

    /**
     * 从简单断言获取动态值
     * @param predicate
     * @return
     */
    private Optional<DynamicValue<T>> getDynamicValueFromSimplePredicate(SimpleKeyFilterPredicate<T> predicate) {
        if (predicate.getValue().getUserValue() == null) {
            return Optional.ofNullable(predicate.getValue().getDynamicValue());
        } else {
            return Optional.empty();
        }
    }

    /**
     * 获取会话ID
     * @return
     */
    public String getSessionId() {
        return sessionRef.getSessionId();
    }

    /**
     * 获取租户ID
     * @return 租户ID
     */
    public TenantId getTenantId() {
        return sessionRef.getSecurityCtx().getTenantId();
    }

    /**
     * 获取客户ID
     * @return 客户ID
     */
    public CustomerId getCustomerId() {
        return sessionRef.getSecurityCtx().getCustomerId();
    }

    /**
     * 获取用户ID
     * @return 用户ID
     */
    public UserId getUserId() {
        return sessionRef.getSecurityCtx().getId();
    }

    /**
     * 清除动态值订阅
     */
    protected void clearDynamicValueSubscriptions() {
        if (subToDynamicValueKeySet != null) {
            for (Integer subId : subToDynamicValueKeySet) {
                localSubscriptionService.cancelSubscription(sessionRef.getSessionId(), subId);
            }
            subToDynamicValueKeySet.clear();
        }
    }

    public void setRefreshTask(ScheduledFuture<?> task) {
        // 如果任务没有停止，直接赋值。否则如果任务已经停止，把新传进来的任务取消
        if (!stopped) {
            this.refreshTask = task;
        } else {
            task.cancel(true);
        }
    }

    /**
     * 取消任务
     */
    public void cancelTasks() {
        // 如果任务不为空，取消任务
        if (this.refreshTask != null) {
            log.trace("[{}][{}] Canceling old refresh task", sessionRef.getSessionId(), cmdId);
            this.refreshTask.cancel(true);
        }
    }

    /**
     * 动态值Key
     */
    @Data
    public static class DynamicValueKey {
        /**
         * 过滤器断言类型
         */
        @Getter
        private final FilterPredicateType predicateType;

        /**
         * 动态值源类型
         */
        @Getter
        private final DynamicValueSourceType sourceType;

        /**
         * 源属性
         */
        @Getter
        private final String sourceAttribute;
    }

    /**
     * 发送websocket消息
     * @param update 发送的消息
     */
    public void sendWsMsg(CmdUpdate update) {
        // 获取锁
        wsLock.lock();
        try {
            // 根据指定的会话，发送消息
            wsService.sendUpdate(sessionRef.getSessionId(), update);
        } finally {
            // 释放锁
            wsLock.unlock();
        }
    }

}
