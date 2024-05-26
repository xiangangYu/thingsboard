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

import { HasTenantId } from '@shared/models/entity.models';

export interface MobileAppSettings extends HasTenantId {
  useDefaultApp: boolean;
  androidConfig: AndroidConfig;
  iosConfig: IosConfig;
  qrCodeConfig: QRCodeConfig;
}

export interface AndroidConfig {
  enabled: boolean;
  appPackage: string;
  sha256CertFingerprints: string
}

export interface IosConfig {
  enabled: boolean;
  appId: string;
}

export interface QRCodeConfig {
  showOnHomePage: boolean;
  badgeEnabled: boolean;
  badgePosition: BadgePosition;
  badgeStyle: BadgeStyle;
  qrCodeLabelEnabled: boolean;
  qrCodeLabel: string;
}

export interface MobileOSBadgeURL {
  iOS: string;
  android: string;
}

export enum BadgePosition {
  RIGHT = 'RIGHT',
  LEFT = 'LEFT'
}

export const badgePositionTranslationsMap = new Map<BadgePosition, string>([
  [BadgePosition.RIGHT, 'admin.mobile-app.right'],
  [BadgePosition.LEFT, 'admin.mobile-app.left']
]);

export enum BadgeStyle {
  ORIGINAL = 'ORIGINAL',
  WHITE = 'WHITE'
}

export const badgeStyleTranslationsMap = new Map<BadgeStyle, string>([
  [BadgeStyle.ORIGINAL, 'admin.mobile-app.original'],
  [BadgeStyle.WHITE, 'admin.mobile-app.white']
]);

export const badgeStyleURLMap = new Map<BadgeStyle, MobileOSBadgeURL>([
  [BadgeStyle.ORIGINAL, {
    iOS: 'assets/android-ios-stores-badges/ios_store_en_black_badge.svg',
    android: 'assets/android-ios-stores-badges/android_store_en_black_badge.svg'
  }],
  [BadgeStyle.WHITE, {
    iOS: 'assets/android-ios-stores-badges/ios_store_en_white_badge.svg',
    android: 'assets/android-ios-stores-badges/android_store_en_black_badge.svg'
  }]
]);
