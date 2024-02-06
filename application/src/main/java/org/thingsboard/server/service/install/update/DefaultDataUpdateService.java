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
package org.thingsboard.server.service.install.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.query.DynamicValue;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.dao.device.DeviceConnectivityConfiguration;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.sql.JpaExecutorService;
import org.thingsboard.server.service.component.ComponentDiscoveryService;
import org.thingsboard.server.service.component.RuleNodeClassInfo;
import org.thingsboard.server.utils.TbNodeUpgradeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@Profile("install")
@Slf4j
public class DefaultDataUpdateService implements DataUpdateService {

    private static final int MAX_PENDING_SAVE_RULE_NODE_FUTURES = 256;
    private static final int DEFAULT_PAGE_SIZE = 1024;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private ComponentDiscoveryService componentDiscoveryService;

    @Autowired
    JpaExecutorService jpaExecutorService;

    @Autowired
    AdminSettingsService adminSettingsService;

    @Autowired
    DeviceConnectivityConfiguration connectivityConfiguration;

    @Override
    public void updateData(String fromVersion) throws Exception {
        switch (fromVersion) {
            case "3.6.0":
                log.info("Updating data from version 3.6.0 to 3.6.1 ...");
                migrateDeviceConnectivity();
                break;
            default:
                throw new RuntimeException("Unable to update data, unsupported fromVersion: " + fromVersion);
        }
    }

    private void migrateDeviceConnectivity() {
        if (adminSettingsService.findAdminSettingsByKey(TenantId.SYS_TENANT_ID, "connectivity") == null) {
            AdminSettings connectivitySettings = new AdminSettings();
            connectivitySettings.setTenantId(TenantId.SYS_TENANT_ID);
            connectivitySettings.setKey("connectivity");
            connectivitySettings.setJsonValue(JacksonUtil.valueToTree(connectivityConfiguration.getConnectivity()));
            adminSettingsService.saveAdminSettings(TenantId.SYS_TENANT_ID, connectivitySettings);
        }
    }

    @Override
    public void upgradeRuleNodes() {
        try {
            int totalRuleNodesUpgraded = 0;
            log.info("Starting rule nodes upgrade ...");
            var nodeClassToVersionMap = componentDiscoveryService.getVersionedNodes();
            log.debug("Found {} versioned nodes to check for upgrade!", nodeClassToVersionMap.size());
            for (var ruleNodeClassInfo : nodeClassToVersionMap) {
                var ruleNodeTypeForLogs = ruleNodeClassInfo.getSimpleName();
                var toVersion = ruleNodeClassInfo.getCurrentVersion();
                log.debug("Going to check for nodes with type: {} to upgrade to version: {}.", ruleNodeTypeForLogs, toVersion);
                var ruleNodesIdsToUpgrade = getRuleNodesIdsWithTypeAndVersionLessThan(ruleNodeClassInfo.getClassName(), toVersion);
                if (ruleNodesIdsToUpgrade.isEmpty()) {
                    log.debug("There are no active nodes with type {}, or all nodes with this type already set to latest version!", ruleNodeTypeForLogs);
                    continue;
                }
                var ruleNodeIdsPartitions = Lists.partition(ruleNodesIdsToUpgrade, MAX_PENDING_SAVE_RULE_NODE_FUTURES);
                for (var ruleNodePack : ruleNodeIdsPartitions) {
                    totalRuleNodesUpgraded += processRuleNodePack(ruleNodePack, ruleNodeClassInfo);
                    log.info("{} upgraded rule nodes so far ...", totalRuleNodesUpgraded);
                }
            }
            log.info("Finished rule nodes upgrade. Upgraded rule nodes count: {}", totalRuleNodesUpgraded);
        } catch (Exception e) {
            log.error("Unexpected error during rule nodes upgrade: ", e);
        }
    }

    private int processRuleNodePack(List<RuleNodeId> ruleNodeIdsBatch, RuleNodeClassInfo ruleNodeClassInfo) {
        var saveFutures = new ArrayList<ListenableFuture<?>>(MAX_PENDING_SAVE_RULE_NODE_FUTURES);
        String ruleNodeType = ruleNodeClassInfo.getSimpleName();
        int toVersion = ruleNodeClassInfo.getCurrentVersion();
        var ruleNodesPack = ruleChainService.findAllRuleNodesByIds(ruleNodeIdsBatch);
        for (var ruleNode : ruleNodesPack) {
            if (ruleNode == null) {
                continue;
            }
            var ruleNodeId = ruleNode.getId();
            int fromVersion = ruleNode.getConfigurationVersion();
            log.debug("Going to upgrade rule node with id: {} type: {} fromVersion: {} toVersion: {}",
                    ruleNodeId, ruleNodeType, fromVersion, toVersion);
            try {
                TbNodeUpgradeUtils.upgradeConfigurationAndVersion(ruleNode, ruleNodeClassInfo);
                saveFutures.add(jpaExecutorService.submit(() -> {
                    ruleChainService.saveRuleNode(TenantId.SYS_TENANT_ID, ruleNode);
                    log.debug("Successfully upgrade rule node with id: {} type: {} fromVersion: {} toVersion: {}",
                            ruleNodeId, ruleNodeType, fromVersion, toVersion);
                }));
            } catch (Exception e) {
                log.warn("Failed to upgrade rule node with id: {} type: {} fromVersion: {} toVersion: {} due to: ",
                        ruleNodeId, ruleNodeType, fromVersion, toVersion, e);
            }
        }
        try {
            return Futures.allAsList(saveFutures).get().size();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to process save rule nodes requests due to: ", e);
        }
    }

    private List<RuleNodeId> getRuleNodesIdsWithTypeAndVersionLessThan(String type, int toVersion) {
        var ruleNodeIds = new ArrayList<RuleNodeId>();
        new PageDataIterable<>(pageLink ->
                ruleChainService.findAllRuleNodeIdsByTypeAndVersionLessThan(type, toVersion, pageLink), DEFAULT_PAGE_SIZE
        ).forEach(ruleNodeIds::add);
        return ruleNodeIds;
    }

    boolean convertDeviceProfileForVersion330(JsonNode profileData) {
        boolean isUpdated = false;
        if (profileData.has("alarms") && !profileData.get("alarms").isNull()) {
            JsonNode alarms = profileData.get("alarms");
            for (JsonNode alarm : alarms) {
                if (alarm.has("createRules")) {
                    JsonNode createRules = alarm.get("createRules");
                    for (AlarmSeverity severity : AlarmSeverity.values()) {
                        if (createRules.has(severity.name())) {
                            JsonNode spec = createRules.get(severity.name()).get("condition").get("spec");
                            if (convertDeviceProfileAlarmRulesForVersion330(spec)) {
                                isUpdated = true;
                            }
                        }
                    }
                }
                if (alarm.has("clearRule") && !alarm.get("clearRule").isNull()) {
                    JsonNode spec = alarm.get("clearRule").get("condition").get("spec");
                    if (convertDeviceProfileAlarmRulesForVersion330(spec)) {
                        isUpdated = true;
                    }
                }
            }
        }
        return isUpdated;
    }

    boolean convertDeviceProfileAlarmRulesForVersion330(JsonNode spec) {
        if (spec != null) {
            if (spec.has("type") && spec.get("type").asText().equals("DURATION")) {
                if (spec.has("value")) {
                    long value = spec.get("value").asLong();
                    var predicate = new FilterPredicateValue<>(
                            value, null, new DynamicValue<>(null, null, false)
                    );
                    ((ObjectNode) spec).remove("value");
                    ((ObjectNode) spec).putPOJO("predicate", predicate);
                    return true;
                }
            } else if (spec.has("type") && spec.get("type").asText().equals("REPEATING")) {
                if (spec.has("count")) {
                    int count = spec.get("count").asInt();
                    var predicate = new FilterPredicateValue<>(
                            count, null, new DynamicValue<>(null, null, false)
                    );
                    ((ObjectNode) spec).remove("count");
                    ((ObjectNode) spec).putPOJO("predicate", predicate);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean getEnv(String name, boolean defaultValue) {
        String env = System.getenv(name);
        if (env == null) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(env);
        }
    }

}
