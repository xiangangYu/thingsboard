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
package org.thingsboard.server.install;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.server.service.component.ComponentDiscoveryService;
import org.thingsboard.server.service.install.DatabaseEntitiesUpgradeService;
import org.thingsboard.server.service.install.DatabaseTsUpgradeService;
import org.thingsboard.server.service.install.EntityDatabaseSchemaService;
import org.thingsboard.server.service.install.InstallScripts;
import org.thingsboard.server.service.install.NoSqlKeyspaceService;
import org.thingsboard.server.service.install.SystemDataLoaderService;
import org.thingsboard.server.service.install.TsDatabaseSchemaService;
import org.thingsboard.server.service.install.TsLatestDatabaseSchemaService;
import org.thingsboard.server.service.install.migrate.EntitiesMigrateService;
import org.thingsboard.server.service.install.migrate.TsLatestMigrateService;
import org.thingsboard.server.service.install.update.CacheCleanupService;
import org.thingsboard.server.service.install.update.DataUpdateService;

import static org.thingsboard.server.service.install.update.DefaultDataUpdateService.getEnv;

@Service
@Profile("install")
@Slf4j
public class ThingsboardInstallService {

    @Value("${install.upgrade:false}")
    private Boolean isUpgrade;

    @Value("${install.upgrade.from_version:1.2.3}")
    private String upgradeFromVersion;

    @Value("${install.load_demo:false}")
    private Boolean loadDemo;

    @Value("${state.persistToTelemetry:false}")
    private boolean persistToTelemetry;

    @Autowired
    private EntityDatabaseSchemaService entityDatabaseSchemaService;

    @Autowired(required = false)
    private NoSqlKeyspaceService noSqlKeyspaceService;

    @Autowired
    private TsDatabaseSchemaService tsDatabaseSchemaService;

    @Autowired(required = false)
    private TsLatestDatabaseSchemaService tsLatestDatabaseSchemaService;

    @Autowired
    private DatabaseEntitiesUpgradeService databaseEntitiesUpgradeService;

    @Autowired(required = false)
    private DatabaseTsUpgradeService databaseTsUpgradeService;

    @Autowired
    private ComponentDiscoveryService componentDiscoveryService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SystemDataLoaderService systemDataLoaderService;

    @Autowired
    private DataUpdateService dataUpdateService;

    @Autowired
    private CacheCleanupService cacheCleanupService;

    @Autowired(required = false)
    private EntitiesMigrateService entitiesMigrateService;

    @Autowired(required = false)
    private TsLatestMigrateService latestMigrateService;

    @Autowired
    private InstallScripts installScripts;

    public void performInstall() {
        try {
            if (isUpgrade) {
                log.info("Starting ThingsBoard Upgrade from version {} ...", upgradeFromVersion);
                // 根据升级版本清除缓存
                cacheCleanupService.clearCache(upgradeFromVersion);

                if ("2.5.0-cassandra".equals(upgradeFromVersion)) {
                    log.info("Migrating(迁移) ThingsBoard entities data from cassandra to SQL database ...");
                    // 查询原始数据然后插入到指定的表中
                    entitiesMigrateService.migrate();
                    log.info("Updating system data...");
                    // 先删除系统widget再保存系统widget
                    systemDataLoaderService.loadSystemWidgets();
                } else if ("3.0.1-cassandra".equals(upgradeFromVersion)) {
                    log.info("Migrating ThingsBoard latest timeseries data from cassandra to SQL database ...");
                    latestMigrateService.migrate();
                } else if (upgradeFromVersion.equals("3.6.2-images")) {
                    // 更新镜像:先替换再保存
                    installScripts.updateImages();
                } else {
                    switch (upgradeFromVersion) {
                        case "1.2.3": //NOSONAR, Need to execute gradual upgrade starting from upgradeFromVersion
                            log.info("Upgrading ThingsBoard from version 1.2.3 to 1.3.0 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("1.2.3");

                        case "1.3.0":  //NOSONAR, Need to execute gradual upgrade starting from upgradeFromVersion
                            log.info("Upgrading ThingsBoard from version 1.3.0 to 1.3.1 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("1.3.0");

                        case "1.3.1": //NOSONAR, Need to execute gradual upgrade starting from upgradeFromVersion
                            log.info("Upgrading ThingsBoard from version 1.3.1 to 1.4.0 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("1.3.1");

                        case "1.4.0":
                            log.info("Upgrading ThingsBoard from version 1.4.0 to 2.0.0 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("1.4.0");

                            dataUpdateService.updateData("1.4.0");

                        case "2.0.0":
                            log.info("Upgrading ThingsBoard from version 2.0.0 to 2.1.1 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("2.0.0");

                        case "2.1.1":
                            log.info("Upgrading ThingsBoard from version 2.1.1 to 2.1.2 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("2.1.1");
                        case "2.1.3":
                            log.info("Upgrading ThingsBoard from version 2.1.3 to 2.2.0 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("2.1.3");

                        case "2.3.0":
                            log.info("Upgrading ThingsBoard from version 2.3.0 to 2.3.1 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("2.3.0");

                        case "2.3.1":
                            log.info("Upgrading ThingsBoard from version 2.3.1 to 2.4.0 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("2.3.1");

                        case "2.4.0":
                            log.info("Upgrading ThingsBoard from version 2.4.0 to 2.4.1 ...");

                        case "2.4.1":
                            log.info("Upgrading ThingsBoard from version 2.4.1 to 2.4.2 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("2.4.1");
                        case "2.4.2":
                            log.info("Upgrading ThingsBoard from version 2.4.2 to 2.4.3 ...");

                            databaseEntitiesUpgradeService.upgradeDatabase("2.4.2");

                        case "2.4.3":
                            log.info("Upgrading ThingsBoard from version 2.4.3 to 2.5 ...");

                            if (databaseTsUpgradeService != null) {
                                databaseTsUpgradeService.upgradeDatabase("2.4.3");
                            }
                            databaseEntitiesUpgradeService.upgradeDatabase("2.4.3");
                        case "2.5.0":
                            log.info("Upgrading ThingsBoard from version 2.5.0 to 2.5.1 ...");
                            if (databaseTsUpgradeService != null) {
                                databaseTsUpgradeService.upgradeDatabase("2.5.0");
                            }
                        case "2.5.1":
                            log.info("Upgrading ThingsBoard from version 2.5.1 to 3.0.0 ...");
                        case "3.0.1":
                            log.info("Upgrading ThingsBoard from version 3.0.1 to 3.1.0 ...");
                            // 根据不同的版本号执行不同的修改表操作sql
                            databaseEntitiesUpgradeService.upgradeDatabase("3.0.1");
                            dataUpdateService.updateData("3.0.1");
                        case "3.1.0":
                            log.info("Upgrading ThingsBoard from version 3.1.0 to 3.1.1 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.1.0");
                        case "3.1.1":
                            log.info("Upgrading ThingsBoard from version 3.1.1 to 3.2.0 ...");
                            if (databaseTsUpgradeService != null) {
                                databaseTsUpgradeService.upgradeDatabase("3.1.1");
                            }
                            databaseEntitiesUpgradeService.upgradeDatabase("3.1.1");
                            dataUpdateService.updateData("3.1.1");
                            // 根据指定目录中的OAuth2信息创建OAuth2并保存
                            systemDataLoaderService.createOAuth2Templates();
                        case "3.2.0":
                            log.info("Upgrading ThingsBoard from version 3.2.0 to 3.2.1 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.2.0");
                        case "3.2.1":
                            log.info("Upgrading ThingsBoard from version 3.2.1 to 3.2.2 ...");
                            if (databaseTsUpgradeService != null) {
                                databaseTsUpgradeService.upgradeDatabase("3.2.1");
                            }
                            databaseEntitiesUpgradeService.upgradeDatabase("3.2.1");
                        case "3.2.2":
                            log.info("Upgrading ThingsBoard from version 3.2.2 to 3.3.0 ...");
                            if (databaseTsUpgradeService != null) {
                                databaseTsUpgradeService.upgradeDatabase("3.2.2");
                            }
                            databaseEntitiesUpgradeService.upgradeDatabase("3.2.2");

                            dataUpdateService.updateData("3.2.2");
                            systemDataLoaderService.createOAuth2Templates();
                        case "3.3.0":
                            log.info("Upgrading ThingsBoard from version 3.3.0 to 3.3.1 ...");
                        case "3.3.1":
                            log.info("Upgrading ThingsBoard from version 3.3.1 to 3.3.2 ...");
                        case "3.3.2":
                            log.info("Upgrading ThingsBoard from version 3.3.2 to 3.3.3 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.3.2");
                            dataUpdateService.updateData("3.3.2");
                        case "3.3.3":
                            log.info("Upgrading ThingsBoard from version 3.3.3 to 3.3.4 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.3.3");
                        case "3.3.4":
                            log.info("Upgrading ThingsBoard from version 3.3.4 to 3.4.0 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.3.4");
                            dataUpdateService.updateData("3.3.4");
                        case "3.4.0":
                            log.info("Upgrading ThingsBoard from version 3.4.0 to 3.4.1 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.4.0");
                            dataUpdateService.updateData("3.4.0");
                        case "3.4.1":
                            log.info("Upgrading ThingsBoard from version 3.4.1 to 3.4.2 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.4.1");
                            // 转换实体并更新
                            dataUpdateService.updateData("3.4.1");
                        case "3.4.2":
                            log.info("Upgrading ThingsBoard from version 3.4.2 to 3.4.3 ...");
                        case "3.4.3":
                            log.info("Upgrading ThingsBoard from version 3.4.3 to 3.4.4 ...");
                        case "3.4.4":
                            log.info("Upgrading ThingsBoard from version 3.4.4 to 3.5.0 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.4.4");
                            if (!getEnv("SKIP_DEFAULT_NOTIFICATION_CONFIGS_CREATION", false)) {
                                systemDataLoaderService.createDefaultNotificationConfigs();
                            } else {
                                log.info("Skipping default notification configs creation");
                            }
                        case "3.5.0":
                            log.info("Upgrading ThingsBoard from version 3.5.0 to 3.5.1 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.5.0");
                        case "3.5.1":
                            log.info("Upgrading ThingsBoard from version 3.5.1 to 3.6.0 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.5.1");
                            dataUpdateService.updateData("3.5.1");
                            systemDataLoaderService.updateDefaultNotificationConfigs();
                        case "3.6.0":
                            log.info("Upgrading ThingsBoard from version 3.6.0 to 3.6.1 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.6.0");
                            dataUpdateService.updateData("3.6.0");
                        case "3.6.1":
                            log.info("Upgrading ThingsBoard from version 3.6.1 to 3.6.2 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.6.1");
                            if (!getEnv("SKIP_IMAGES_MIGRATION", false)) {
                                installScripts.setUpdateImages(true);
                            } else {
                                log.info("Skipping images migration. Run the upgrade with fromVersion as '3.6.2-images' to migrate");
                            }
                        case "3.6.2":
                            log.info("Upgrading ThingsBoard from version 3.6.2 to 3.6.3 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.6.2");
                            //TODO DON'T FORGET to update switch statement in the CacheCleanupService if you need to clear the cache
                            break;
                        default:
                            throw new RuntimeException("Unable to upgrade ThingsBoard, unsupported fromVersion: " + upgradeFromVersion);
                    }
                    entityDatabaseSchemaService.createOrUpdateViewsAndFunctions();
                    // 创建或更新设备的view
                    entityDatabaseSchemaService.createOrUpdateDeviceInfoView(persistToTelemetry);
                    log.info("Updating system data...");
                    // 升级规则节点
                    dataUpdateService.upgradeRuleNodes();
                    // 加载系统widget
                    systemDataLoaderService.loadSystemWidgets();
                    // 加载Lwm2m资源
                    installScripts.loadSystemLwm2mResources();
                    // 加载系统镜像
                    installScripts.loadSystemImages();
                    if (installScripts.isUpdateImages()) {
                        installScripts.updateImages();
                    }
                }
                log.info("Upgrade finished successfully!");

            } else {

                log.info("Starting ThingsBoard Installation...");

                log.info("Installing DataBase schema for entities...");

                // 创建数据库schema
                entityDatabaseSchemaService.createDatabaseSchema();

                entityDatabaseSchemaService.createOrUpdateViewsAndFunctions();
                entityDatabaseSchemaService.createOrUpdateDeviceInfoView(persistToTelemetry);

                log.info("Installing DataBase schema for timeseries...");

                if (noSqlKeyspaceService != null) {
                    noSqlKeyspaceService.createDatabaseSchema();
                }

                tsDatabaseSchemaService.createDatabaseSchema();

                if (tsLatestDatabaseSchemaService != null) {
                    tsLatestDatabaseSchemaService.createDatabaseSchema();
                }
                // 下面的调用都是面向接口编程的方式
                log.info("Loading system data...");
                // 加载系统组件
                componentDiscoveryService.discoverComponents();
                // 创建系统admin
                systemDataLoaderService.createSysAdmin();
                // 创建默认的租户profile
                systemDataLoaderService.createDefaultTenantProfiles();
                // 创建admin设置
                systemDataLoaderService.createAdminSettings();
                // 创建随机的jwt设置
                systemDataLoaderService.createRandomJwtSettings();
                // 加载系统widget
                systemDataLoaderService.loadSystemWidgets();
                // 创建OAuth2认证模板
                systemDataLoaderService.createOAuth2Templates();
                // 创建查询
                systemDataLoaderService.createQueues();
                // 创建默认的通知配置
                systemDataLoaderService.createDefaultNotificationConfigs();

//                systemDataLoaderService.loadSystemPlugins();
//                systemDataLoaderService.loadSystemRules();
                // 加载Lwm2m资源
                installScripts.loadSystemLwm2mResources();
                // 加载系统镜像
                installScripts.loadSystemImages();

                if (loadDemo) {
                    log.info("Loading demo data...");
                    // 加载demo数据
                    systemDataLoaderService.loadDemoData();
                }
                log.info("Installation finished successfully!");
            }


        } catch (Exception e) {
            log.error("Unexpected error during ThingsBoard installation!", e);
            throw new ThingsboardInstallException("Unexpected error during ThingsBoard installation!", e);
        } finally {
            SpringApplication.exit(context);
        }
    }

}

