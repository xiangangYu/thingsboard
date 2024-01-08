/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订阅服务统计
 */
@Data
public class SubscriptionServiceStatistics {

    /**
     * 告警查询调用数
     */
    private AtomicInteger alarmQueryInvocationCnt = new AtomicInteger();

    /**
     * 通用查询调用数
     */
    private AtomicInteger regularQueryInvocationCnt = new AtomicInteger();

    /**
     * 动态查询调用数
     */
    private AtomicInteger dynamicQueryInvocationCnt = new AtomicInteger();

    /**
     * 告警查询时间花费
     */
    private AtomicLong alarmQueryTimeSpent = new AtomicLong();

    /**
     * 通用查询时间花费
     */
    private AtomicLong regularQueryTimeSpent = new AtomicLong();

    /**
     * 动态查询时间花费
     */
    private AtomicLong dynamicQueryTimeSpent = new AtomicLong();
}
