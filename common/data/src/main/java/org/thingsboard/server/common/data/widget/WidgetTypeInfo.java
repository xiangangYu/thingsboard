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
package org.thingsboard.server.common.data.widget;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.validation.NoXss;

@Data
public class WidgetTypeInfo extends BaseWidgetType {

    @Schema(description = "Base64 encoded widget thumbnail", accessMode = Schema.AccessMode.READ_ONLY)
    private String image;
    @NoXss
    @Schema(description = "Description of the widget type", accessMode = Schema.AccessMode.READ_ONLY)
    private String description;
    @NoXss
    @Schema(description = "Tags of the widget type", accessMode = Schema.AccessMode.READ_ONLY)
    private String[] tags;
    @NoXss
    @Schema(description = "Type of the widget (timeseries, latest, control, alarm or static)", accessMode = Schema.AccessMode.READ_ONLY)
    private String widgetType;

    public WidgetTypeInfo() {
        super();
    }

    public WidgetTypeInfo(WidgetTypeId id) {
        super(id);
    }

    public WidgetTypeInfo(BaseWidgetType baseWidgetType) {
        super(baseWidgetType);
    }

    public WidgetTypeInfo(WidgetTypeInfo widgetTypeInfo) {
        super(widgetTypeInfo);
        this.image = widgetTypeInfo.getImage();
        this.description = widgetTypeInfo.getDescription();
        this.tags = widgetTypeInfo.getTags();
        this.widgetType = widgetTypeInfo.getWidgetType();
    }

    public WidgetTypeInfo(WidgetTypeDetails widgetTypeDetails) {
        super(widgetTypeDetails);
        this.image = widgetTypeDetails.getImage();
        this.description = widgetTypeDetails.getDescription();
        this.tags = widgetTypeDetails.getTags();
        if (widgetTypeDetails.getDescriptor() != null && widgetTypeDetails.getDescriptor().has("type")) {
            this.widgetType = widgetTypeDetails.getDescriptor().get("type").asText();
        } else {
            this.widgetType = "";
        }
    }
}
