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
 * 实体过滤器类型
 */
public enum EntityFilterType {
    /**
     * 单个实体
     */
    SINGLE_ENTITY("singleEntity"),
    /**
     * 实体列表
     */
    ENTITY_LIST("entityList"),
    /**
     * 实体名称
     */
    ENTITY_NAME("entityName"),
    /**
     * 实体类型
     */
    ENTITY_TYPE("entityType"),
    /**
     * 资产类型
     */
    ASSET_TYPE("assetType"),
    /**
     * 设备类型
     */
    DEVICE_TYPE("deviceType"),
    /**
     * 实体试图类型
     */
    ENTITY_VIEW_TYPE("entityViewType"),
    /**
     * 边缘类型
     */
    EDGE_TYPE("edgeType"),
    /**
     * 关系查询
     */
    RELATIONS_QUERY("relationsQuery"),
    /**
     * 资产搜索查询
     */
    ASSET_SEARCH_QUERY("assetSearchQuery"),
    /**
     * 设备搜索查询
     */
    DEVICE_SEARCH_QUERY("deviceSearchQuery"),
    /**
     * 实体试图搜索查询
     */
    ENTITY_VIEW_SEARCH_QUERY("entityViewSearchQuery"),
    /**
     * 边缘搜索查询
     */
    EDGE_SEARCH_QUERY("edgeSearchQuery"),
    /**
     * api使用状态
     */
    API_USAGE_STATE("apiUsageState");

    private final String label;

    EntityFilterType(String label) {
        this.label = label;
    }
}
