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
package org.thingsboard.server.actors;

import org.thingsboard.server.common.msg.TbActorMsg;
import org.thingsboard.server.common.msg.TbActorStopReason;

/**
 * Actor顶级接口
 *
 * Tb使用actor模型处理接收到的message，有效隔离了消息的接收和处理过程
 *
 * Actor模型三个重要概念
 * 状态：Actor对象的变量信息，由Actor自己管理
 * 行为：Actor中的计算逻辑，通过Actor接收到消息来改变Actor状态
 * 邮箱：Actor和Actor之间的通信桥梁，邮箱内部通过FIFO消息队列来存储发送方Actor消息，接收方Actor从邮箱队列中获取消息
 *
 * TB中Actor与经典Actor模型对象关系
 * TbActor  -->  Actor
 * Dispatcher  -->  Behavior
 * TbActorRef  -->  mailBox,TbActorRef对应的实现类为TbActorMailbox
 *
 */
public interface TbActor {

    boolean process(TbActorMsg msg);

    TbActorRef getActorRef();

    default void init(TbActorCtx ctx) throws TbActorException {
    }

    default void destroy(TbActorStopReason stopReason, Throwable cause) throws TbActorException {
    }

    default InitFailureStrategy onInitFailure(int attempt, Throwable t) {
        return InitFailureStrategy.retryWithDelay(5000L * attempt);
    }

    default ProcessFailureStrategy onProcessFailure(Throwable t) {
        if (t instanceof Error) {
            return ProcessFailureStrategy.stop();
        } else {
            return ProcessFailureStrategy.resume();
        }
    }
}
