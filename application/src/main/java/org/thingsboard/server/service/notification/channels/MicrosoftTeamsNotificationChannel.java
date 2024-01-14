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
package org.thingsboard.server.service.notification.channels;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;
import org.thingsboard.server.common.data.notification.info.NotificationInfo;
import org.thingsboard.server.common.data.notification.targets.MicrosoftTeamsNotificationTargetConfig;
import org.thingsboard.server.common.data.notification.template.MicrosoftTeamsDeliveryMethodNotificationTemplate;
import org.thingsboard.server.common.data.notification.template.MicrosoftTeamsDeliveryMethodNotificationTemplate.Button.LinkType;
import org.thingsboard.server.service.notification.NotificationProcessingContext;
import org.thingsboard.server.service.security.system.SystemSecurityService;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MicrosoftTeamsNotificationChannel implements NotificationChannel<MicrosoftTeamsNotificationTargetConfig, MicrosoftTeamsDeliveryMethodNotificationTemplate> {

    private final SystemSecurityService systemSecurityService;

    @Setter
    private RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.of(15, ChronoUnit.SECONDS))
            .setReadTimeout(Duration.of(15, ChronoUnit.SECONDS))
            .build();

    @Override
    public void sendNotification(MicrosoftTeamsNotificationTargetConfig targetConfig, MicrosoftTeamsDeliveryMethodNotificationTemplate processedTemplate, NotificationProcessingContext ctx) throws Exception {
        Message message = new Message();
        message.setThemeColor(Strings.emptyToNull(processedTemplate.getThemeColor()));
        if (StringUtils.isEmpty(processedTemplate.getSubject())) {
            message.setText(processedTemplate.getBody());
        } else {
            message.setSummary(processedTemplate.getSubject());
            Message.Section section = new Message.Section();
            section.setActivityTitle(processedTemplate.getSubject());
            section.setActivitySubtitle(processedTemplate.getBody());
            message.setSections(List.of(section));
        }
        var button = processedTemplate.getButton();
        if (button != null && button.isEnabled()) {
            String uri;
            if (button.getLinkType() == LinkType.DASHBOARD) {
                String state = null;
                if (button.isSetEntityIdInState() || StringUtils.isNotEmpty(button.getDashboardState())) {
                    ObjectNode stateObject = JacksonUtil.newObjectNode();
                    if (button.isSetEntityIdInState()) {
                        stateObject.putObject("params")
                                .set("entityId", Optional.ofNullable(ctx.getRequest().getInfo())
                                        .map(NotificationInfo::getStateEntityId)
                                        .map(JacksonUtil::valueToTree)
                                        .orElse(null));
                    } else {
                        stateObject.putObject("params");
                    }
                    if (StringUtils.isNotEmpty(button.getDashboardState())) {
                        stateObject.put("id", button.getDashboardState());
                    }
                    state = Base64.encodeBase64String(JacksonUtil.OBJECT_MAPPER.writeValueAsBytes(List.of(stateObject)));
                }
                String baseUrl = systemSecurityService.getBaseUrl(ctx.getTenantId(), null, null);
                if (StringUtils.isEmpty(baseUrl)) {
                    throw new IllegalStateException("Failed to determine base url to construct dashboard link");
                }
                uri = baseUrl + "/dashboards/" + button.getDashboardId();
                if (state != null) {
                    uri += "?state=" + state;
                }
            } else {
                uri = button.getLink();
            }
            if (StringUtils.isNotBlank(uri) && button.getText() != null) {
                Message.ActionCard actionCard = new Message.ActionCard();
                actionCard.setType("OpenUri");
                actionCard.setName(button.getText());
                var target = new Message.ActionCard.Target("default", uri);
                actionCard.setTargets(List.of(target));
                message.setPotentialAction(List.of(actionCard));
            }
        }

        restTemplate.postForEntity(targetConfig.getWebhookUrl(), message, String.class);
    }

    @Override
    public void check(TenantId tenantId) throws Exception {
    }

    @Override
    public NotificationDeliveryMethod getDeliveryMethod() {
        return NotificationDeliveryMethod.MICROSOFT_TEAMS;
    }

    @Data
    public static class Message {
        @JsonProperty("@type")
        private final String type = "MessageCard";
        @JsonProperty("@context")
        private final String context = "http://schema.org/extensions";
        private String themeColor;
        private String summary;
        private String text;
        private List<Section> sections;
        private List<ActionCard> potentialAction;

        @Data
        public static class Section {
            private String activityTitle;
            private String activitySubtitle;
            private String activityImage;
            private List<Fact> facts;
            private boolean markdown;

            @Data
            public static class Fact {
                private final String name;
                private final String value;
            }
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ActionCard {
            @JsonProperty("@type")
            private String type; // ActionCard, OpenUri
            private String name;
            private List<Input> inputs; // for ActionCard
            private List<Action> actions; // for ActionCard
            private List<Target> targets;

            @Data
            public static class Input {
                @JsonProperty("@type")
                private String type; // TextInput, DateInput, MultichoiceInput
                private String id;
                private boolean isMultiple;
                private String title;
                private boolean isMultiSelect;

                @Data
                public static class Choice {
                    private final String display;
                    private final String value;
                }
            }

            @Data
            public static class Action {
                @JsonProperty("@type")
                private final String type; // HttpPOST
                private final String name;
                private final String target; // url
            }

            @Data
            public static class Target {
                private final String os;
                private final String uri;
            }
        }

    }

}
