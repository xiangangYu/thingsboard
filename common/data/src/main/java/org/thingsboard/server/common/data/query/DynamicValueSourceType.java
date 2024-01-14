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
package org.thingsboard.server.common.data.query;

/**
 * 动态值源类型枚举
 */
public enum DynamicValueSourceType {

    /**
     * 当前租户
     */
    CURRENT_TENANT,

    /**
     * 当前客户
     */
    CURRENT_CUSTOMER,

    /**
     * 当前用户
     */
    CURRENT_USER,

    /**
     * 当前设备
     */
    CURRENT_DEVICE
}
