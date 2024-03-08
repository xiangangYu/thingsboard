///
/// Copyright © 2016-2024 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators
} from '@angular/forms';
import {
  BarSeriesSettings,
  seriesLabelPositions,
  seriesLabelPositionTranslations
} from '@home/components/widget/lib/chart/time-series-chart.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { merge } from 'rxjs';
import { formatValue, isDefinedAndNotNull } from '@core/utils';
import { DataKeyConfigComponent } from '@home/components/widget/config/data-key-config.component';

@Component({
  selector: 'tb-time-series-chart-bar-settings',
  templateUrl: './time-series-chart-bar-settings.component.html',
  styleUrls: ['./../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TimeSeriesChartBarSettingsComponent),
      multi: true
    }
  ]
})
export class TimeSeriesChartBarSettingsComponent implements OnInit, ControlValueAccessor {

  seriesLabelPositions = seriesLabelPositions;

  seriesLabelPositionTranslations = seriesLabelPositionTranslations;

  labelPreviewFn = this._labelPreviewFn.bind(this);

  @Input()
  disabled: boolean;

  private modelValue: BarSeriesSettings;

  private propagateChange = null;

  public barSettingsFormGroup: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              private dataKeyConfigComponent: DataKeyConfigComponent,
              private fb: UntypedFormBuilder) {
  }

  ngOnInit(): void {
    this.barSettingsFormGroup = this.fb.group({
      showBorder: [null, []],
      borderWidth: [null, [Validators.min(0)]],
      borderRadius: [null, [Validators.min(0)]],
      showLabel: [null, []],
      labelPosition: [null, []],
      labelFont: [null, []],
      labelColor: [null, []],
      backgroundSettings: [null, []]
    });
    this.barSettingsFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
    merge(this.barSettingsFormGroup.get('showBorder').valueChanges,
      this.barSettingsFormGroup.get('showLabel').valueChanges)
    .subscribe(() => {
      this.updateValidators();
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(_fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.barSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.barSettingsFormGroup.enable({emitEvent: false});
      this.updateValidators();
    }
  }

  writeValue(value: BarSeriesSettings): void {
    this.modelValue = value;
    this.barSettingsFormGroup.patchValue(
      value, {emitEvent: false}
    );
    this.updateValidators();
  }

  private updateValidators() {
    const showBorder: boolean = this.barSettingsFormGroup.get('showBorder').value;
    const showLabel: boolean = this.barSettingsFormGroup.get('showLabel').value;
    if (showBorder) {
      this.barSettingsFormGroup.get('borderWidth').enable({emitEvent: false});
    } else {
      this.barSettingsFormGroup.get('borderWidth').disable({emitEvent: false});
    }
    if (showLabel) {
      this.barSettingsFormGroup.get('labelPosition').enable({emitEvent: false});
      this.barSettingsFormGroup.get('labelFont').enable({emitEvent: false});
      this.barSettingsFormGroup.get('labelColor').enable({emitEvent: false});
    } else {
      this.barSettingsFormGroup.get('labelPosition').disable({emitEvent: false});
      this.barSettingsFormGroup.get('labelFont').disable({emitEvent: false});
      this.barSettingsFormGroup.get('labelColor').disable({emitEvent: false});
    }
  }

  private updateModel() {
    this.modelValue = this.barSettingsFormGroup.getRawValue();
    this.propagateChange(this.modelValue);
  }

  private _labelPreviewFn(): string {
    const dataKey = this.dataKeyConfigComponent.modelValue;
    const widgetConfig = this.dataKeyConfigComponent.widgetConfig;
    const units = dataKey.units && dataKey.units.length ? dataKey.units : widgetConfig.config.units;
    const decimals = isDefinedAndNotNull(dataKey.decimals) ? dataKey.decimals :
      (isDefinedAndNotNull(widgetConfig.config.decimals) ? widgetConfig.config.decimals : 2);
    return formatValue(22, decimals, units, false);
  }
}
