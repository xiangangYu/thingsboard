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

import lombok.Data;

import jakarta.validation.Valid;

/**
 * 字符串过滤器断言
 */
@Data
public class StringFilterPredicate implements SimpleKeyFilterPredicate<String> {

    private StringOperation operation;
    @Valid
    private FilterPredicateValue<String> value;
    private boolean ignoreCase;

    @Override
    public FilterPredicateType getType() {
        return FilterPredicateType.STRING;
    }

    /**
     * 字符串操作枚举
     */
    public enum StringOperation {
        /**
         * 等于
         */
        EQUAL,

        /**
         * 不等于
         */
        NOT_EQUAL,

        /**
         * 开始于
         */
        STARTS_WITH,

        /**
         * 结束于
         */
        ENDS_WITH,

        /**
         * 包含
         */
        CONTAINS,

        /**
         * 不包含
         */
        NOT_CONTAINS,

        /**
         * 在什么范围之内
         */
        IN,

        /**
         * 不在什么范围内
         */
        NOT_IN
    }
}
