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

import { Component, forwardRef, Input, OnDestroy } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import {
  DeliveryMethodsTemplates,
  NotificationDeliveryMethod,
  NotificationDeliveryMethodInfoMap,
  NotificationTemplateTypeTranslateMap,
  NotificationType
} from '@shared/models/notification.models';
import { takeUntil } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { deepClone, isDefinedAndNotNull } from '@core/utils';
import { coerceBoolean } from '@shared/decorators/coercion';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'tb-template-configuration',
  templateUrl: './notification-template-configuration.component.html',
  styleUrls: ['./notification-template-configuration.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => NotificationTemplateConfigurationComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => NotificationTemplateConfigurationComponent),
      multi: true,
    }
  ]
})
export class NotificationTemplateConfigurationComponent implements OnDestroy, ControlValueAccessor, Validator {

  templateConfigurationForm: FormGroup;

  NotificationDeliveryMethodInfoMap = NotificationDeliveryMethodInfoMap;

  @Input()
  set predefinedDeliveryMethodsTemplate(value: Partial<DeliveryMethodsTemplates>) {
    if (isDefinedAndNotNull(value)) {
      this.templateConfigurationForm.patchValue(value, {emitEvent: false});
      this.updateDisabledForms();
      this.templateConfigurationForm.updateValueAndValidity();
    }
  }

  @Input()
  notificationType: NotificationType;

  @Input()
  @coerceBoolean()
  interacted: boolean;

  readonly NotificationDeliveryMethod = NotificationDeliveryMethod;
  readonly NotificationTemplateTypeTranslateMap = NotificationTemplateTypeTranslateMap;

  tinyMceOptions: Record<string, any> = {
    base_url: '/assets/tinymce',
    suffix: '.min',
    plugins: ['link table image imagetools code fullscreen'],
    menubar: 'edit insert tools view format table',
    toolbar: 'fontselect fontsizeselect | formatselect | bold italic  strikethrough  forecolor backcolor ' +
      '| link | table | image | alignleft aligncenter alignright alignjustify  ' +
      '| numlist bullist outdent indent  | removeformat | code | fullscreen',
    toolbar_mode: 'sliding',
    height: 400,
    autofocus: false,
    branding: false
  };

  private propagateChange = (v: any) => { };
  private readonly destroy$ = new Subject<void>();

  constructor(private fb: FormBuilder,
              private translate: TranslateService) {
    this.templateConfigurationForm = this.buildForm();
    this.templateConfigurationForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.propagateChange(value);
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  writeValue(value: Partial<DeliveryMethodsTemplates>) {
    const settings = deepClone(value);
    if (isDefinedAndNotNull(settings)) {
      for (const method of Object.values(NotificationDeliveryMethod)) {
        if (isDefinedAndNotNull(settings[method]?.enabled)) {
          delete settings[method].enabled;
        }
      }
    }
    this.templateConfigurationForm.patchValue(settings, {emitEvent: false});
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean) {
    if (isDisabled) {
      this.templateConfigurationForm.disable({emitEvent: false});
    } else {
      this.updateDisabledForms();
    }
  }

  validate(): ValidationErrors {
    return this.templateConfigurationForm.valid ? null : {
      templateConfiguration: {
        valid: false,
      },
    };
  }

  get hotificationTapActionHint(): string {
    switch (this.notificationType) {
      case NotificationType.ALARM:
      case NotificationType.ALARM_ASSIGNMENT:
      case NotificationType.ALARM_COMMENT:
        return this.translate.instant('notification.notification-tap-action-hint');
    }
    return '';
  }

  private updateDisabledForms(){
    Object.values(NotificationDeliveryMethod).forEach((method) => {
      const form = this.templateConfigurationForm.get(method);
      if (!form.get('enabled').value) {
        form.disable({emitEvent: false});
      } else {
        form.enable({emitEvent: false});
         switch (method) {
           case NotificationDeliveryMethod.WEB:
             form.get('additionalConfig.icon.enabled').updateValueAndValidity({onlySelf: true});
             break;
           case NotificationDeliveryMethod.MOBILE_APP:
             form.get('additionalConfig.icon.enabled').updateValueAndValidity({onlySelf: true});
             break;
         }
      }
    });
  }

  private buildForm(): FormGroup {
    const form = this.fb.group({});

    Object.values(NotificationDeliveryMethod).forEach((method) => {
      form.addControl(method, this.buildDeliveryMethodControl(method), {emitEvent: false});
    });

    return form;
  }

  private buildDeliveryMethodControl(deliveryMethod: NotificationDeliveryMethod): FormGroup {
    let deliveryMethodForm: FormGroup;
    switch (deliveryMethod) {
      case NotificationDeliveryMethod.WEB:
        deliveryMethodForm = this.fb.group({
          subject: ['', Validators.required],
          body: ['', Validators.required],
          additionalConfig: this.fb.group({
            icon: this.fb.group({
              enabled: [false],
              icon: [{value: 'notifications', disabled: true}, Validators.required],
              color: [{value: '#757575', disabled: true}]
            }),
            actionButtonConfig: [null]
          })
        });

        deliveryMethodForm.get('additionalConfig.icon.enabled').valueChanges.pipe(
          takeUntil(this.destroy$)
        ).subscribe((value) => {
          if (value) {
            deliveryMethodForm.get('additionalConfig.icon.icon').enable({emitEvent: false});
            deliveryMethodForm.get('additionalConfig.icon.color').enable({emitEvent: false});
          } else {
            deliveryMethodForm.get('additionalConfig.icon.icon').disable({emitEvent: false});
            deliveryMethodForm.get('additionalConfig.icon.color').disable({emitEvent: false});
          }
        });
        break;
      case NotificationDeliveryMethod.EMAIL:
        deliveryMethodForm = this.fb.group({
          subject: ['', Validators.required],
          body: ['', Validators.required]
        });
        break;
      case NotificationDeliveryMethod.SMS:
        deliveryMethodForm = this.fb.group({
          body: ['', [Validators.required, Validators.maxLength(320)]]
        });
        break;
      case NotificationDeliveryMethod.SLACK:
        deliveryMethodForm = this.fb.group({
          body: ['', Validators.required]
        });
        break;
      case NotificationDeliveryMethod.MOBILE_APP:
        deliveryMethodForm = this.fb.group({
          subject: ['', [Validators.required, Validators.maxLength(50)]],
          body: ['', [Validators.required, Validators.maxLength(150)]],
          additionalConfig: this.fb.group({
            icon: this.fb.group({
              enabled: [false],
              icon: [{value: 'notifications', disabled: true}, Validators.required],
              color: [{value: '#757575', disabled: true}]
            }),
            onClick: [null]
          })
        });
        deliveryMethodForm.get('additionalConfig.icon.enabled').valueChanges.pipe(
          takeUntil(this.destroy$)
        ).subscribe((value) => {
          if (value) {
            deliveryMethodForm.get('additionalConfig.icon.icon').enable({emitEvent: false});
            deliveryMethodForm.get('additionalConfig.icon.color').enable({emitEvent: false});
          } else {
            deliveryMethodForm.get('additionalConfig.icon.icon').disable({emitEvent: false});
            deliveryMethodForm.get('additionalConfig.icon.color').disable({emitEvent: false});
          }
        });
        break;
      case NotificationDeliveryMethod.MICROSOFT_TEAMS:
        deliveryMethodForm = this.fb.group({
          subject: [''],
          body: ['', Validators.required],
          themeColor: [''],
          button: [null]
        });
        break;
      default:
        throw new Error(`Not configured templated for notification delivery method: ${deliveryMethod}`);
    }
    deliveryMethodForm.addControl('enabled', this.fb.control(false), {emitEvent: false});
    deliveryMethodForm.addControl('method', this.fb.control(deliveryMethod), {emitEvent: false});
    return deliveryMethodForm;
  }
}
