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
package org.thingsboard.server.queue.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.thingsboard.server.queue.TbQueueAdmin;
import org.thingsboard.server.queue.util.PropertyUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by ashvayka on 24.09.18.
 */
@Slf4j
public class TbKafkaAdmin implements TbQueueAdmin {

    @Getter
    private final AdminClient client;
    private final Map<String, String> topicConfigs;
    private final Set<String> topics = ConcurrentHashMap.newKeySet();
    private final int numPartitions;

    private final short replicationFactor;

    public TbKafkaAdmin(TbKafkaSettings settings, Map<String, String> topicConfigs) {
        client = AdminClient.create(settings.toAdminProps());
        this.topicConfigs = topicConfigs;

        try {
            topics.addAll(client.listTopics().names().get());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get all topics.", e);
        }

        String numPartitionsStr = topicConfigs.get(TbKafkaTopicConfigs.NUM_PARTITIONS_SETTING);
        if (numPartitionsStr != null) {
            numPartitions = Integer.parseInt(numPartitionsStr);
            topicConfigs.remove("partitions");
        } else {
            numPartitions = 1;
        }
        replicationFactor = settings.getReplicationFactor();
    }

    @Override
    public void createTopicIfNotExists(String topic, String properties) {
        if (topics.contains(topic)) {
            return;
        }
        try {
            NewTopic newTopic = new NewTopic(topic, numPartitions, replicationFactor).configs(PropertyUtils.getProps(topicConfigs, properties));
            createTopic(newTopic).values().get(topic).get();
            topics.add(topic);
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof TopicExistsException) {
                //do nothing
            } else {
                log.warn("[{}] Failed to create topic", topic, ee);
                throw new RuntimeException(ee);
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to create topic", topic, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteTopic(String topic) {
        if (topics.contains(topic)) {
            client.deleteTopics(Collections.singletonList(topic));
        } else {
            try {
                if (client.listTopics().names().get().contains(topic)) {
                    client.deleteTopics(Collections.singletonList(topic));
                } else {
                    log.warn("Kafka topic [{}] does not exist.", topic);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to delete kafka topic [{}].", topic, e);
            }
        }
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    public CreateTopicsResult createTopic(NewTopic topic) {
        return client.createTopics(Collections.singletonList(topic));
    }

    /**
     * Sync offsets from a fat group to a single-partition group
     * Migration back from single-partition consumer to a fat group is not supported
     * TODO: The best possible approach to synchronize the offsets is to do the synchronization as a part of the save Queue parameters with stop all consumers
     * */
    public void syncOffsets(String fatGroupId, String newGroupId, Integer partitionId) {
        try {
            syncOffsetsUnsafe(fatGroupId, newGroupId, partitionId);
        } catch (Exception e) {
            log.warn("Failed to syncOffsets from {} to {} partitionId {}", fatGroupId, newGroupId, partitionId, e);
        }
    }

    void syncOffsetsUnsafe(String fatGroupId, String newGroupId, Integer partitionId) throws ExecutionException, InterruptedException, TimeoutException {
        log.info("syncOffsets [{}][{}][{}]", fatGroupId, newGroupId, partitionId);
        if (partitionId == null) {
            return;
        }
        Map<TopicPartition, OffsetAndMetadata> oldOffsets =
                client.listConsumerGroupOffsets(fatGroupId).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
        if (oldOffsets.isEmpty()) {
            return;
        }

        for (var consumerOffset : oldOffsets.entrySet()) {
            var tp = consumerOffset.getKey();
            if (!tp.topic().endsWith("." + partitionId)) {
                continue;
            }
            var om = consumerOffset.getValue();
            Map<TopicPartition, OffsetAndMetadata> newOffsets =
                    client.listConsumerGroupOffsets(newGroupId).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);

            var existingOffset = newOffsets.get(tp);
            if (existingOffset == null) {
                log.info("[{}] topic offset does not exists in the new node group {}, all found offsets {}", tp, newGroupId, newOffsets);
            } else if (existingOffset.offset() >= om.offset()) {
                log.info("[{}] topic offset {} >= than old node group offset {}", tp, existingOffset.offset(), om.offset());
                break;
            } else {
                log.info("[{}] SHOULD alter topic offset [{}] less than old node group offset [{}]", tp, existingOffset.offset(), om.offset());
            }
            client.alterConsumerGroupOffsets(newGroupId, Map.of(tp, om)).all().get(10, TimeUnit.SECONDS);
            log.info("[{}] altered new consumer groupId {}", tp, newGroupId);
            break;
        }

    }

}
