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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 实体数据分页查询
 */
@Data
@AllArgsConstructor
public class EntityDataPageLink {

    /**
     * 分页大小
     */
    private int pageSize;

    /**
     * 分页页码
     */
    private int page;

    /**
     * 文本搜索内容
     */
    private String textSearch;

    /**
     * 排序方式
     */
    private EntityDataSortOrder sortOrder;

    /**
     * 是否动态
     */
    private boolean dynamic = false;

    public EntityDataPageLink() {
    }

    public EntityDataPageLink(int pageSize, int page, String textSearch, EntityDataSortOrder sortOrder) {
        this(pageSize, page, textSearch, sortOrder, false);
    }

    @JsonIgnore
    public EntityDataPageLink nextPageLink() {
        return new EntityDataPageLink(this.pageSize, this.page + 1, this.textSearch, this.sortOrder);
    }
}
