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
package org.thingsboard.server.common.data.query;

import io.swagger.annotations.ApiModel;
import lombok.Data;

import java.io.Serializable;

/**
 * key过滤器
 */
@ApiModel
@Data
public class KeyFilter implements Serializable {

    /**
     * 实体key
     */
    private EntityKey key;

    /**
     * 实体key的值的类型
     */
    private EntityKeyValueType valueType;

    /**
     * key过滤器断言
     */
    private KeyFilterPredicate predicate;

}
