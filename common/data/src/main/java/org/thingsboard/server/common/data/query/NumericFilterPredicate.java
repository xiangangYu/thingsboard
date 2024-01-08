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

import lombok.Data;

/**
 * 数字过滤器断言
 */
@Data
public class NumericFilterPredicate implements SimpleKeyFilterPredicate<Double>  {

    private NumericOperation operation;
    private FilterPredicateValue<Double> value;

    @Override
    public FilterPredicateType getType() {
        return FilterPredicateType.NUMERIC;
    }

    /**
     * 数字操作枚举
     */
    public enum NumericOperation {
        /**
         * 等于
         */
        EQUAL,

        /**
         * 不等于
         */
        NOT_EQUAL,

        /**
         * 大于
         */
        GREATER,

        /**
         * 小于
         */
        LESS,

        /**
         * 大于或等于
         */
        GREATER_OR_EQUAL,

        /**
         * 小于或等于
         */
        LESS_OR_EQUAL
    }
}
