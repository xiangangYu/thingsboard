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
package org.thingsboard.server.service.install;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientRegistrationTemplate;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.widget.DeprecatedFilter;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetTypeInfo;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.oauth2.OAuth2ConfigTemplateService;
import org.thingsboard.server.dao.resource.ResourceService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.widget.WidgetTypeService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.service.install.update.ImagesUpdater;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.thingsboard.server.utils.LwM2mObjectModelUtils.toLwm2mResource;

/**
 * Created by ashvayka on 18.04.18.
 */
@Component
@Slf4j
public class InstallScripts {

    public static final String APP_DIR = "application";
    public static final String SRC_DIR = "src";
    public static final String MAIN_DIR = "main";
    public static final String DATA_DIR = "data";
    public static final String JSON_DIR = "json";
    public static final String SYSTEM_DIR = "system";
    public static final String TENANT_DIR = "tenant";
    public static final String EDGE_DIR = "edge";
    public static final String DEVICE_PROFILE_DIR = "device_profile";
    public static final String DEMO_DIR = "demo";
    public static final String RULE_CHAINS_DIR = "rule_chains";
    public static final String WIDGET_TYPES_DIR = "widget_types";
    public static final String WIDGET_BUNDLES_DIR = "widget_bundles";
    public static final String OAUTH2_CONFIG_TEMPLATES_DIR = "oauth2_config_templates";
    public static final String DASHBOARDS_DIR = "dashboards";
    public static final String MODELS_LWM2M_DIR = "lwm2m-registry";
    public static final String CREDENTIALS_DIR = "credentials";

    public static final String JSON_EXT = ".json";
    public static final String XML_EXT = ".xml";

    @Value("${install.data_dir:}")
    private String dataDir;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private WidgetTypeService widgetTypeService;

    @Autowired
    private WidgetsBundleService widgetsBundleService;

    @Autowired
    private OAuth2ConfigTemplateService oAuth2TemplateService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ImagesUpdater imagesUpdater;
    @Getter @Setter
    private boolean updateImages = false;

    Path getTenantRuleChainsDir() {
        return Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, RULE_CHAINS_DIR);
    }

    Path getDeviceProfileDefaultRuleChainTemplateFilePath() {
        return Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, DEVICE_PROFILE_DIR, "rule_chain_template.json");
    }

    Path getEdgeRuleChainsDir() {
        return Paths.get(getDataDir(), JSON_DIR, EDGE_DIR, RULE_CHAINS_DIR);
    }

    public String getDataDir() {
        if (!StringUtils.isEmpty(dataDir)) {
            if (!Paths.get(this.dataDir).toFile().isDirectory()) {
                throw new RuntimeException("'install.data_dir' property value is not a valid directory!");
            }
            return dataDir;
        } else {
            String workDir = System.getProperty("user.dir");
            if (workDir.endsWith("application")) {
                return Paths.get(workDir, SRC_DIR, MAIN_DIR, DATA_DIR).toString();
            } else {
                Path dataDirPath = Paths.get(workDir, APP_DIR, SRC_DIR, MAIN_DIR, DATA_DIR);
                if (Files.exists(dataDirPath)) {
                    return dataDirPath.toString();
                } else {
                    throw new RuntimeException("Not valid working directory: " + workDir + ". Please use either root project directory, application module directory or specify valid \"install.data_dir\" ENV variable to avoid automatic data directory lookup!");
                }
            }
        }
    }

    public void createDefaultRuleChains(TenantId tenantId) throws IOException {
        Path tenantChainsDir = getTenantRuleChainsDir();
        loadRuleChainsFromPath(tenantId, tenantChainsDir);
    }

    public void createDefaultEdgeRuleChains(TenantId tenantId) throws IOException {
        Path edgeChainsDir = getEdgeRuleChainsDir();
        loadRuleChainsFromPath(tenantId, edgeChainsDir);
    }

    private void loadRuleChainsFromPath(TenantId tenantId, Path ruleChainsPath) throws IOException {
        findRuleChainsFromPath(ruleChainsPath).forEach(path -> {
            try {
                createRuleChainFromFile(tenantId, path, null);
            } catch (Exception e) {
                log.error("Unable to load rule chain from json: [{}]", path.toString());
                throw new RuntimeException("Unable to load rule chain from json", e);
            }
        });
    }

    List<Path> findRuleChainsFromPath(Path ruleChainsPath) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(ruleChainsPath, path -> path.toString().endsWith(InstallScripts.JSON_EXT))) {
            dirStream.forEach(paths::add);
        }
        return paths;
    }

    public RuleChain createDefaultRuleChain(TenantId tenantId, String ruleChainName) throws IOException {
        return createRuleChainFromFile(tenantId, getDeviceProfileDefaultRuleChainTemplateFilePath(), ruleChainName);
    }

    public RuleChain createRuleChainFromFile(TenantId tenantId, Path templateFilePath, String newRuleChainName) throws IOException {
        JsonNode ruleChainJson = JacksonUtil.toJsonNode(templateFilePath.toFile());
        RuleChain ruleChain = JacksonUtil.treeToValue(ruleChainJson.get("ruleChain"), RuleChain.class);
        RuleChainMetaData ruleChainMetaData = JacksonUtil.treeToValue(ruleChainJson.get("metadata"), RuleChainMetaData.class);

        ruleChain.setTenantId(tenantId);
        if (!StringUtils.isEmpty(newRuleChainName)) {
            ruleChain.setName(newRuleChainName);
        }
        ruleChain = ruleChainService.saveRuleChain(ruleChain);

        ruleChainMetaData.setRuleChainId(ruleChain.getId());
        ruleChainService.saveRuleChainMetaData(TenantId.SYS_TENANT_ID, ruleChainMetaData, Function.identity());

        return ruleChain;
    }

    public void loadSystemWidgets() throws Exception {
        log.info("Loading system widgets");
        Map<Path, JsonNode> widgetsBundlesMap = new HashMap<>();
        Path widgetBundlesDir = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, WIDGET_BUNDLES_DIR);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(widgetBundlesDir, path -> path.toString().endsWith(JSON_EXT))) {
            dirStream.forEach(
                    path -> {
                        JsonNode widgetsBundleDescriptorJson;
                        try {
                            widgetsBundleDescriptorJson = JacksonUtil.toJsonNode(path.toFile());
                        } catch (Exception e) {
                            log.error("Unable to parse widgets bundle from json: [{}]", path);
                            throw new RuntimeException("Unable to parse widgets bundle from json", e);
                        }
                        if (widgetsBundleDescriptorJson == null || !widgetsBundleDescriptorJson.has("widgetsBundle")) {
                            log.error("Invalid widgets bundle json: [{}]", path);
                            throw new RuntimeException("Invalid widgets bundle json: [" + path + "]");
                        }
                        widgetsBundlesMap.put(path, widgetsBundleDescriptorJson);
                        JsonNode bundleAliasNode = widgetsBundleDescriptorJson.get("widgetsBundle").get("alias");
                        if (bundleAliasNode == null || !bundleAliasNode.isTextual()) {
                            log.error("Invalid widgets bundle json: [{}]", path);
                            throw new RuntimeException("Invalid widgets bundle json: [" + path + "]");
                        }
                        String bundleAlias = bundleAliasNode.asText();
                        try {
                            this.deleteSystemWidgetBundle(bundleAlias);
                        } catch (Exception e) {
                            log.error("Failed to delete system widgets bundle: [{}]", bundleAlias);
                            throw new RuntimeException("Failed to delete system widgets bundle: [" + bundleAlias + "]", e);
                        }
                    }
            );
        }
        Path widgetTypesDir = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, WIDGET_TYPES_DIR);
        if (Files.exists(widgetTypesDir)) {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(widgetTypesDir, path -> path.toString().endsWith(JSON_EXT))) {
                dirStream.forEach(
                        path -> {
                            try {
                                JsonNode widgetTypeJson = JacksonUtil.toJsonNode(path.toFile());
                                WidgetTypeDetails widgetTypeDetails = JacksonUtil.treeToValue(widgetTypeJson, WidgetTypeDetails.class);
                                widgetTypeService.saveWidgetType(widgetTypeDetails);
                            } catch (Exception e) {
                                log.error("Unable to load widget type from json: [{}]", path.toString());
                                throw new RuntimeException("Unable to load widget type from json", e);
                            }
                        }
                );
            }
        }
        for (var widgetsBundleDescriptorEntry : widgetsBundlesMap.entrySet()) {
            Path path = widgetsBundleDescriptorEntry.getKey();
            try {
                JsonNode widgetsBundleDescriptorJson = widgetsBundleDescriptorEntry.getValue();
                JsonNode widgetsBundleJson = widgetsBundleDescriptorJson.get("widgetsBundle");
                WidgetsBundle widgetsBundle = JacksonUtil.treeToValue(widgetsBundleJson, WidgetsBundle.class);
                WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);
                List<String> widgetTypeFqns = new ArrayList<>();
                if (widgetsBundleDescriptorJson.has("widgetTypes")) {
                    JsonNode widgetTypesArrayJson = widgetsBundleDescriptorJson.get("widgetTypes");
                    widgetTypesArrayJson.forEach(
                            widgetTypeJson -> {
                                try {
                                    WidgetTypeDetails widgetTypeDetails = JacksonUtil.treeToValue(widgetTypeJson, WidgetTypeDetails.class);
                                    var savedWidgetType = widgetTypeService.saveWidgetType(widgetTypeDetails);
                                    widgetTypeFqns.add(savedWidgetType.getFqn());
                                } catch (Exception e) {
                                    log.error("Unable to load widget type from json: [{}]", path.toString());
                                    throw new RuntimeException("Unable to load widget type from json", e);
                                }
                            }
                    );
                }
                if (widgetsBundleDescriptorJson.has("widgetTypeFqns")) {
                    JsonNode widgetFqnsArrayJson = widgetsBundleDescriptorJson.get("widgetTypeFqns");
                    widgetFqnsArrayJson.forEach(fqnJson -> {
                        widgetTypeFqns.add(fqnJson.asText());
                    });
                }
                widgetTypeService.updateWidgetsBundleWidgetFqns(TenantId.SYS_TENANT_ID, savedWidgetsBundle.getId(), widgetTypeFqns);
            } catch (Exception e) {
                log.error("Unable to load widgets bundle from json: [{}]", path.toString());
                throw new RuntimeException("Unable to load widgets bundle from json", e);
            }
        }
    }

    private void deleteSystemWidgetBundle(String bundleAlias) {
        WidgetsBundle widgetsBundle = widgetsBundleService.findWidgetsBundleByTenantIdAndAlias(TenantId.SYS_TENANT_ID, bundleAlias);
        if (widgetsBundle != null) {
            PageData<WidgetTypeInfo> widgetTypes;
            var pageLink = new PageLink(1024);
            do {
                widgetTypes = widgetTypeService.findWidgetTypesInfosByWidgetsBundleId(TenantId.SYS_TENANT_ID, widgetsBundle.getId(), false, DeprecatedFilter.ALL, null, pageLink);
                for (var widgetType : widgetTypes.getData()) {
                    widgetTypeService.deleteWidgetType(TenantId.SYS_TENANT_ID, widgetType.getId());
                }
                pageLink.nextPageLink();
            } while (widgetTypes.hasNext());
            widgetsBundleService.deleteWidgetsBundle(TenantId.SYS_TENANT_ID, widgetsBundle.getId());
        }
    }

    public void updateImages() {
        imagesUpdater.updateWidgetsBundlesImages();
        imagesUpdater.updateWidgetTypesImages();
        imagesUpdater.updateDashboardsImages();
        imagesUpdater.updateDeviceProfilesImages();
        imagesUpdater.updateAssetProfilesImages();
    }

    @SneakyThrows
    public void loadSystemImages() {
        log.info("Loading system images...");
        Stream<Path> dashboardsFiles = Files.list(Paths.get(getDataDir(), JSON_DIR, DEMO_DIR, DASHBOARDS_DIR));
        try (dashboardsFiles) {
            dashboardsFiles.forEach(file -> {
                try {
                    Dashboard dashboard = JacksonUtil.OBJECT_MAPPER.readValue(file.toFile(), Dashboard.class);
                    imagesUpdater.createSystemImages(dashboard);
                } catch (Exception e) {
                    log.error("Failed to create system images for default dashboard {}", file.getFileName(), e);
                }
            });
        }
    }

    public void loadDashboards(TenantId tenantId, CustomerId customerId) throws Exception {
        Path dashboardsDir = Paths.get(getDataDir(), JSON_DIR, DEMO_DIR, DASHBOARDS_DIR);
        loadDashboardsFromDir(tenantId, customerId, dashboardsDir);
    }

    public void createDefaultTenantDashboards(TenantId tenantId, CustomerId customerId) throws Exception {
        Path dashboardsDir = Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, DASHBOARDS_DIR);
        loadDashboardsFromDir(tenantId, customerId, dashboardsDir);
    }

    private void loadDashboardsFromDir(TenantId tenantId, CustomerId customerId, Path dashboardsDir) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dashboardsDir, path -> path.toString().endsWith(JSON_EXT))) {
            dirStream.forEach(
                    path -> {
                        try {
                            JsonNode dashboardJson = JacksonUtil.toJsonNode(path.toFile());
                            Dashboard dashboard = JacksonUtil.treeToValue(dashboardJson, Dashboard.class);
                            dashboard.setTenantId(tenantId);
                            Dashboard savedDashboard = dashboardService.saveDashboard(dashboard);
                            if (customerId != null && !customerId.isNullUid()) {
                                dashboardService.assignDashboardToCustomer(TenantId.SYS_TENANT_ID, savedDashboard.getId(), customerId);
                            }
                        } catch (Exception e) {
                            log.error("Unable to load dashboard from json: [{}]", path.toString());
                            throw new RuntimeException("Unable to load dashboard from json", e);
                        }
                    }
            );
        }
    }

    public void loadDemoRuleChains(TenantId tenantId) {
        try {
            createDefaultRuleChains(tenantId);
            createDefaultRuleChain(tenantId, "Thermostat");
            createDefaultEdgeRuleChains(tenantId);
        } catch (Exception e) {
            log.error("Unable to load rule chain from json", e);
            throw new RuntimeException("Unable to load rule chain from json", e);
        }
    }

    public void createOAuth2Templates() throws Exception {
        Path oauth2ConfigTemplatesDir = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, OAUTH2_CONFIG_TEMPLATES_DIR);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(oauth2ConfigTemplatesDir, path -> path.toString().endsWith(JSON_EXT))) {
            dirStream.forEach(
                    path -> {
                        try {
                            JsonNode oauth2ConfigTemplateJson = JacksonUtil.toJsonNode(path.toFile());
                            OAuth2ClientRegistrationTemplate clientRegistrationTemplate = JacksonUtil.treeToValue(oauth2ConfigTemplateJson, OAuth2ClientRegistrationTemplate.class);
                            Optional<OAuth2ClientRegistrationTemplate> existingClientRegistrationTemplate =
                                    oAuth2TemplateService.findClientRegistrationTemplateByProviderId(clientRegistrationTemplate.getProviderId());
                            if (existingClientRegistrationTemplate.isPresent()) {
                                clientRegistrationTemplate.setId(existingClientRegistrationTemplate.get().getId());
                            }
                            oAuth2TemplateService.saveClientRegistrationTemplate(clientRegistrationTemplate);
                        } catch (Exception e) {
                            log.error("Unable to load oauth2 config templates from json: [{}]", path.toString());
                            throw new RuntimeException("Unable to load oauth2 config templates from json", e);
                        }
                    }
            );
        }
    }

    public void loadSystemLwm2mResources() {
        Path resourceLwm2mPath = Paths.get(getDataDir(), MODELS_LWM2M_DIR);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(resourceLwm2mPath, path -> path.toString().endsWith(InstallScripts.XML_EXT))) {
            dirStream.forEach(
                    path -> {
                        try {
                            byte[] data = Files.readAllBytes(path);
                            TbResource tbResource = new TbResource();
                            tbResource.setTenantId(TenantId.SYS_TENANT_ID);
                            tbResource.setData(data);
                            tbResource.setResourceType(ResourceType.LWM2M_MODEL);
                            tbResource.setFileName(path.toFile().getName());
                            doSaveLwm2mResource(tbResource);
                        } catch (Exception e) {
                            log.error("Unable to load resource lwm2m object model from file: [{}]", path.toString());
                            throw new RuntimeException("resource lwm2m object model from file", e);
                        }
                    }
            );
        } catch (Exception e) {
            log.error("Unable to load resources lwm2m object model from file: [{}]", resourceLwm2mPath.toString());
            throw new RuntimeException("resource lwm2m object model from file", e);
        }
    }

    private void doSaveLwm2mResource(TbResource resource) throws ThingsboardException {
        log.trace("Executing saveResource [{}]", resource);
        if (resource.getData() == null || resource.getData().length == 0) {
            throw new DataValidationException("Resource data should be specified!");
        }
        toLwm2mResource(resource);
        TbResource foundResource = resourceService.findResourceByTenantIdAndKey(TenantId.SYS_TENANT_ID, ResourceType.LWM2M_MODEL, resource.getResourceKey());
        if (foundResource == null) {
            resourceService.saveResource(resource);
        }
    }
}
