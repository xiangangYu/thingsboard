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

import { Component, EventEmitter, forwardRef, Input, OnInit, Output } from '@angular/core';
import {
  ControlValueAccessor,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALUE_ACCESSOR,
  Validators
} from '@angular/forms';
import {
  BACnetObjectTypes,
  BACnetObjectTypesTranslates,
  BACnetRequestTypes,
  BACnetRequestTypesTranslates,
  BLEMethods,
  BLEMethodsTranslates,
  CANByteOrders,
  ConnectorType,
  GatewayConnectorDefaultTypesTranslates,
  HTTPMethods,
  ModbusCodesTranslate,
  ModbusCommandTypes,
  RPCCommand,
  RPCTemplateConfig,
  SNMPMethods,
  SNMPMethodsTranslations,
  SocketEncodings,
  SocketMethodProcessings,
  SocketMethodProcessingsTranslates
} from '@home/components/widget/lib/gateway/gateway-widget.models';
import { MatDialog } from '@angular/material/dialog';
import {
  JsonObjectEditDialogComponent,
  JsonObjectEditDialogData
} from '@shared/components/dialog/json-object-edit-dialog.component';
import { jsonRequired } from '@shared/components/json-object-edit.component';
import { deepClone } from '@core/utils';

export const noLeadTrailSpacesRegex: RegExp = /^(?! )[\S\s]*(?<! )$/;

@Component({
  selector: 'tb-gateway-service-rpc-connector',
  templateUrl: './gateway-service-rpc-connector.component.html',
  styleUrls: ['./gateway-service-rpc-connector.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => GatewayServiceRPCConnectorComponent),
      multi: true
    }
  ]
})
export class GatewayServiceRPCConnectorComponent implements OnInit, ControlValueAccessor {

  @Input()
  connectorType: ConnectorType;

  @Output()
  sendCommand: EventEmitter<RPCCommand> = new EventEmitter();

  @Output()
  saveTemplate: EventEmitter<RPCTemplateConfig> = new EventEmitter();

  commandForm: FormGroup;
  isMQTTWithResponse: FormControl;
  codesArray: Array<number> = [1, 2, 3, 4, 5, 6, 15, 16];
  ConnectorType = ConnectorType;
  modbusCommandTypes = Object.values(ModbusCommandTypes) as ModbusCommandTypes[];
  bACnetRequestTypes = Object.values(BACnetRequestTypes) as BACnetRequestTypes[];
  bACnetObjectTypes = Object.values(BACnetObjectTypes) as BACnetObjectTypes[];
  bLEMethods = Object.values(BLEMethods) as BLEMethods[];
  cANByteOrders = Object.values(CANByteOrders) as CANByteOrders[];
  socketMethodProcessings = Object.values(SocketMethodProcessings) as SocketMethodProcessings[];
  socketEncodings = Object.values(SocketEncodings) as SocketEncodings[];
  sNMPMethods = Object.values(SNMPMethods) as SNMPMethods[];
  hTTPMethods = Object.values(HTTPMethods) as HTTPMethods[];

  bACnetRequestTypesTranslates = BACnetRequestTypesTranslates;
  bACnetObjectTypesTranslates = BACnetObjectTypesTranslates;
  bLEMethodsTranslates = BLEMethodsTranslates;
  SocketMethodProcessingsTranslates = SocketMethodProcessingsTranslates;
  SNMPMethodsTranslations = SNMPMethodsTranslations;
  gatewayConnectorDefaultTypesTranslates = GatewayConnectorDefaultTypesTranslates;
  modbusCodesTranslate = ModbusCodesTranslate;

  urlPattern = /^[-a-zA-Zd_$:{}?~+=\/.0-9-]*$/;
  numbersOnlyPattern = /^[0-9]*$/;
  hexOnlyPattern = /^[0-9A-Fa-f ]+$/;

  private propagateChange = (v: any) => {
  }

  constructor(private fb: FormBuilder,
              private dialog: MatDialog,) {
  }

  ngOnInit() {
    this.commandForm = this.connectorParamsFormGroupByType(this.connectorType);
    this.commandForm.valueChanges.subscribe(value => {
      const httpHeaders = {};
      const security = {};
      switch (this.connectorType) {
        case ConnectorType.REST:
          value.httpHeaders.forEach(data => {
            httpHeaders[data.headerName] = data.value;
          })
          value.httpHeaders = httpHeaders;
          value.security.forEach(data => {
            security[data.securityName] = data.value;
          })
          value.security = security;
          break;
        case ConnectorType.REQUEST:
          value.httpHeaders.forEach(data => {
            httpHeaders[data.headerName] = data.value;
          })
          value.httpHeaders = httpHeaders;
          break;
      }
      if (this.commandForm.valid) {
        this.propagateChange({...this.commandForm.value, ...value});
      }
    });
    this.isMQTTWithResponse = this.fb.control(false);
  }

  connectorParamsFormGroupByType(type: ConnectorType): FormGroup {
    let formGroup: FormGroup;

    switch (type) {
      case ConnectorType.MQTT:
        formGroup = this.fb.group({
          methodFilter: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          requestTopicExpression: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          responseTopicExpression: [null, [Validators.pattern(noLeadTrailSpacesRegex)]],
          responseTimeout: [null, [Validators.min(10), Validators.pattern(this.numbersOnlyPattern)]],
          valueExpression: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
        })
        break;
      case ConnectorType.MODBUS:
        formGroup = this.fb.group({
          tag: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          type: [null, [Validators.required]],
          functionCode: [null, [Validators.required]],
          value: [null, []],
          address: [null, [Validators.required, Validators.min(0), Validators.pattern(this.numbersOnlyPattern)]],
          objectsCount: [null, [Validators.required, Validators.min(0), Validators.pattern(this.numbersOnlyPattern)]]
        })
        const valueForm = formGroup.get('value');
        formGroup.get('functionCode').valueChanges.subscribe(value => {
          if (value > 4) {
            valueForm.addValidators([Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]);
          } else {
            valueForm.clearValidators();
            valueForm.setValue(null);
          }
          valueForm.updateValueAndValidity();
        })
        break;
      case ConnectorType.BACNET:
        formGroup = this.fb.group({
          method: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          requestType: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          requestTimeout: [null, [Validators.required, Validators.min(10), Validators.pattern(this.numbersOnlyPattern)]],
          objectType: [null, []],
          identifier: [null, [Validators.required, Validators.min(1), Validators.pattern(this.numbersOnlyPattern)]],
          propertyId: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]]
        })
        break;
      case ConnectorType.BLE:
        formGroup = this.fb.group({
          methodRPC: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          characteristicUUID: ["00002A00-0000-1000-8000-00805F9B34FB", [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          methodProcessing: [null, [Validators.required]],
          withResponse: [false, []]
        })
        break;
      case ConnectorType.CAN:
        formGroup = this.fb.group({
          method: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          nodeID: [null, [Validators.required, Validators.min(0), Validators.pattern(this.numbersOnlyPattern)]],
          isExtendedID: [false, []],
          isFD: [false, []],
          bitrateSwitch: [false, []],
          dataLength: [null, [Validators.min(1), Validators.pattern(this.numbersOnlyPattern)]],
          dataByteorder: [null, []],
          dataBefore: [null, [Validators.pattern(noLeadTrailSpacesRegex), Validators.pattern(this.hexOnlyPattern)]],
          dataAfter: [null, [Validators.pattern(noLeadTrailSpacesRegex), Validators.pattern(this.hexOnlyPattern)]],
          dataInHEX: [null, [Validators.pattern(noLeadTrailSpacesRegex), Validators.pattern(this.hexOnlyPattern)]],
          dataExpression: [null, [Validators.pattern(noLeadTrailSpacesRegex)]]
        })
        break;
      case ConnectorType.FTP:
        formGroup = this.fb.group({
          methodFilter: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          valueExpression: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]]
        })
        break;
      case ConnectorType.OCPP:
        formGroup = this.fb.group({
          methodRPC: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          valueExpression: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          withResponse: [false, []]
        })
        break;
      case ConnectorType.SOCKET:
        formGroup = this.fb.group({
          methodRPC: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          methodProcessing: [null, [Validators.required]],
          encoding: [SocketEncodings.UTF_8, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          withResponse: [false, []]
        })
        break;
      case ConnectorType.XMPP:
        formGroup = this.fb.group({
          methodRPC: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          valueExpression: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          withResponse: [false, []]
        })
        break;
      case ConnectorType.SNMP:
        formGroup = this.fb.group({
          requestFilter: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          method: [null, [Validators.required]],
          withResponse: [false, []],
          oid: this.fb.array([], [Validators.required])
        })
        break;
      case ConnectorType.REST:
        formGroup = this.fb.group({
          methodFilter: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          httpMethod: [null, [Validators.required]],
          requestUrlExpression: [null, [Validators.required, Validators.pattern(this.urlPattern)]],
          responseTimeout: [null, [Validators.required, Validators.min(10), Validators.pattern(this.numbersOnlyPattern)]],
          timeout: [null, [Validators.required, Validators.min(10), Validators.pattern(this.numbersOnlyPattern)]],
          tries: [null, [Validators.required, Validators.min(1), Validators.pattern(this.numbersOnlyPattern)]],
          valueExpression: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          httpHeaders: this.fb.array([]),
          security: this.fb.array([])
        })
        break;
      case ConnectorType.REQUEST:
        formGroup = this.fb.group({
          methodFilter: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          httpMethod: [null, [Validators.required]],
          requestUrlExpression: [null, [Validators.required, Validators.pattern(this.urlPattern)]],
          responseTimeout: [null, [Validators.required, Validators.min(10), Validators.pattern(this.numbersOnlyPattern)]],
          timeout: [null, [Validators.required, Validators.min(10), Validators.pattern(this.numbersOnlyPattern)]],
          tries: [null, [Validators.required, Validators.min(1), Validators.pattern(this.numbersOnlyPattern)]],
          requestValueExpression: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          responseValueExpression: [null, [Validators.pattern(noLeadTrailSpacesRegex)]],
          httpHeaders: this.fb.array([]),
        })
        break;
      case ConnectorType.OPCUA:
      case ConnectorType.OPCUA_ASYNCIO:
        formGroup = this.fb.group({
          method: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          arguments: this.fb.array([]),
        })
        break;
      default:
        formGroup = this.fb.group({
          command: [null, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
          params: [{}, [jsonRequired]],
        })
    }
    return formGroup;
  }

  addSNMPoid(value: string = null) {
    const oidsFA = this.commandForm.get('oid') as FormArray;
    if (oidsFA) {
      oidsFA.push(this.fb.control(value, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]), {emitEvent: false});
    }
  }

  removeSNMPoid(index: number) {
    const oidsFA = this.commandForm.get('oid') as FormArray;
    oidsFA.removeAt(index);
  }

  addHTTPHeader(value: { headerName: string, value: string } = {headerName: null, value: null}) {
    const headerFA = this.commandForm.get('httpHeaders') as FormArray;
    const formGroup = this.fb.group({
      headerName: [value.headerName, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
      value: [value.value, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]]
    })
    if (headerFA) {
      headerFA.push(formGroup, {emitEvent: false});
    }
  }

  removeHTTPHeader(index: number) {
    const oidsFA = this.commandForm.get('httpHeaders') as FormArray;
    oidsFA.removeAt(index);
  }

  addHTTPSecurity(value: { securityName: string, value: string } = {securityName: null, value: null}) {
    const securityFA = this.commandForm.get('security') as FormArray;
    const formGroup = this.fb.group({
      securityName: [value.securityName, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]],
      value: [value.value, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]]
    })
    if (securityFA) {
      securityFA.push(formGroup, {emitEvent: false});
    }
  }

  removeHTTPSecurity(index: number) {
    const oidsFA = this.commandForm.get('security') as FormArray;
    oidsFA.removeAt(index);
  }

  getFormArrayControls(path: string) {
    return (this.commandForm.get(path) as FormArray).controls as FormControl[];
  }

  addOCPUAArguments(value: string = null) {
    const oidsFA = this.commandForm.get('arguments') as FormArray;
    if (oidsFA) {
      oidsFA.push(this.fb.control(value, [Validators.required, Validators.pattern(noLeadTrailSpacesRegex)]), {emitEvent: false});
    }
  }

  removeOCPUAArguments(index: number) {
    const oidsFA = this.commandForm.get('arguments') as FormArray;
    oidsFA.removeAt(index);
  }

  openEditJSONDialog($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<JsonObjectEditDialogComponent, JsonObjectEditDialogData, object>(JsonObjectEditDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        jsonValue: this.commandForm.get('params').value,
        required: true
      }
    }).afterClosed().subscribe(
      (res) => {
        if (res) {
          this.commandForm.get('params').setValue(res);
        }
      }
    );
  }

  save() {
    this.saveTemplate.emit();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  clearFromArrayByName(name: string) {
    const formArray = this.commandForm.get(name) as FormArray;
    while (formArray.length !== 0) {
      formArray.removeAt(0)
    }
  }

  writeValue(value: RPCTemplateConfig): void {
    if (typeof value == "object") {
      value = deepClone(value);
      switch (this.connectorType) {
        case ConnectorType.SNMP:
          this.clearFromArrayByName("oids");
          value.oids.forEach(value => {
            this.addSNMPoid(value)
          })
          delete value.oids;
          break;
        case ConnectorType.REQUEST:
          this.clearFromArrayByName("httpHeaders");
          value.httpHeaders && Object.entries(value.httpHeaders).forEach(httpHeader => {
            this.addHTTPHeader({headerName: httpHeader[0], value: httpHeader[1] as string})
          })
          delete value.httpHeaders;
          break;
        case ConnectorType.REST:
          this.clearFromArrayByName("httpHeaders");
          this.clearFromArrayByName("security");
          value.security && Object.entries(value.security).forEach(securityHeader => {
            this.addHTTPSecurity({securityName: securityHeader[0], value: securityHeader[1] as string})
          })
          delete value.security;
          value.httpHeaders && Object.entries(value.httpHeaders).forEach(httpHeader => {
            this.addHTTPHeader({headerName: httpHeader[0], value: httpHeader[1] as string})
          })
          delete value.httpHeaders;
          break;
        case ConnectorType.OPCUA:
        case ConnectorType.OPCUA_ASYNCIO:
          this.clearFromArrayByName("arguments");
          value.arguments.forEach(value => {
            this.addOCPUAArguments(value)
          })
          delete value.arguments;
          break;
      }
      this.commandForm.patchValue(value, {onlySelf: false});
    }
  }
}
