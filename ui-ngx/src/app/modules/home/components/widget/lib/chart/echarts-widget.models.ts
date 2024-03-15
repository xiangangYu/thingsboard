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

import * as echarts from 'echarts/core';
import { Axis } from 'echarts';
import AxisModel from 'echarts/types/src/coord/cartesian/AxisModel';
import { estimateLabelUnionRect } from 'echarts/lib/coord/axisHelper';
import { formatValue, isDefinedAndNotNull } from '@core/utils';
import TimeScale from 'echarts/types/src/scale/Time';
import {
  DataZoomComponent,
  DataZoomComponentOption,
  GridComponent,
  GridComponentOption,
  MarkLineComponent,
  MarkLineComponentOption,
  TooltipComponent,
  TooltipComponentOption,
  VisualMapComponent,
  VisualMapComponentOption
} from 'echarts/components';
import {
  BarChart,
  BarSeriesOption,
  CustomChart,
  CustomSeriesOption,
  LineChart,
  LineSeriesOption,
  PieChart,
  PieSeriesOption
} from 'echarts/charts';
import { LabelLayout } from 'echarts/features';
import { CanvasRenderer, SVGRenderer } from 'echarts/renderers';
import { DataEntry, DataKey, DataSet } from '@shared/models/widget.models';
import {
  calculateAggIntervalWithWidgetTimeWindow,
  Interval,
  IntervalMath,
  WidgetTimewindow
} from '@shared/models/time/time.models';
import { CallbackDataParams } from 'echarts/types/dist/shared';
import { Renderer2 } from '@angular/core';
import { DateFormatProcessor, DateFormatSettings, Font } from '@shared/models/widget-settings.models';
import GlobalModel from 'echarts/types/src/model/Global';
import Axis2D from 'echarts/types/src/coord/cartesian/Axis2D';
import SeriesModel from 'echarts/types/src/model/Series';

class EChartsModule {
  private initialized = false;

  init() {
    if (!this.initialized) {
      const axisGetBandWidth = Axis.prototype.getBandWidth;

      Axis.prototype.getBandWidth = function(){
        const model: AxisModel = this.model;
        const axisOption = model.option;
        if (this.scale.type === 'time') {
          let interval: number;
          const seriesDataIndices = axisOption.axisPointer?.seriesDataIndices;
          if (seriesDataIndices?.length) {
            const seriesDataIndex = seriesDataIndices[0];
            const series = model.ecModel.getSeriesByIndex(seriesDataIndex.seriesIndex);
            if (series) {
              const values = series.getData().getValues(seriesDataIndex.dataIndex);
              const start = values[2];
              const end = values[3];
              if (typeof start === 'number' && typeof end === 'number') {
                interval = Math.max(end - start, 1);
              }
            }
          }
          if (!interval) {
            const tbTimeWindow: WidgetTimewindow = (axisOption as any).tbTimeWindow;
            if (isDefinedAndNotNull(tbTimeWindow)) {
              if (axisOption.axisPointer?.value && typeof axisOption.axisPointer?.value === 'number') {
                const intervalArray = calculateAggIntervalWithWidgetTimeWindow(tbTimeWindow, axisOption.axisPointer.value);
                const start = intervalArray[0];
                const end = intervalArray[1];
                interval = Math.max(end - start, 1);
              } else {
                interval = IntervalMath.numberValue(tbTimeWindow.interval);
              }
            }
          }
          if (interval) {
            const timeScale: TimeScale = this.scale;
            const axisExtent: [number, number] = this._extent;
            const dataExtent = timeScale.getExtent();
            const size = Math.abs(axisExtent[1] - axisExtent[0]);
            return interval * (size / (dataExtent[1] - dataExtent[0]));
          } else {
            return axisGetBandWidth.call(this);
          }
        } else {
          return axisGetBandWidth.call(this);
        }
      };

      echarts.use([
        TooltipComponent,
        GridComponent,
        VisualMapComponent,
        DataZoomComponent,
        MarkLineComponent,
        LineChart,
        BarChart,
        PieChart,
        CustomChart,
        LabelLayout,
        CanvasRenderer,
        SVGRenderer
      ]);

      this.initialized = true;
    }
  }
}

export const echartsModule = new EChartsModule();

export type EChartsOption = echarts.ComposeOption<
  | TooltipComponentOption
  | GridComponentOption
  | VisualMapComponentOption
  | DataZoomComponentOption
  | MarkLineComponentOption
  | LineSeriesOption
  | CustomSeriesOption
  | BarSeriesOption
  | PieSeriesOption
>;

export type ECharts = echarts.ECharts;

export type EChartsDataItem = [number, any, number, number];

export type NamedDataSet = {name: string; value: EChartsDataItem}[];

export type EChartsSeriesItem = {
  id: string;
  dataKey: DataKey;
  data: NamedDataSet;
  dataSet?: DataSet;
  enabled: boolean;
  units?: string;
  decimals?: number;
};

export const getXAxis = (chart: ECharts): Axis2D => {
  const model: GlobalModel = (chart as any).getModel();
  const models = model.queryComponents({mainType: 'xAxis'});
  if (models?.length) {
    const axisModel = models[0] as AxisModel;
    return axisModel.axis;
  }
  return null;
};

export const getYAxis = (chart: ECharts, axisId: string): Axis2D => {
  const model: GlobalModel = (chart as any).getModel();
  const models = model.queryComponents({mainType: 'yAxis', id: axisId});
  if (models?.length) {
    const axisModel = models[0] as AxisModel;
    return axisModel.axis;
  }
  return null;
};

export const calculateYAxisWidth = (chart: ECharts, axisId: string): number => {
  const axis = getYAxis(chart, axisId);
  return calculateAxisSize(axis);
};

export const calculateXAxisHeight = (chart: ECharts): number => {
  const axis = getXAxis(chart);
  return calculateAxisSize(axis);
};

const calculateAxisSize = (axis: Axis2D): number => {
  let size = 0;
  if (axis && axis.model.option.show) {
    const labelUnionRect = estimateLabelUnionRect(axis);
    if (labelUnionRect) {
      const margin = axis.model.get(['axisLabel', 'margin']);
      const dimension = axis.isHorizontal() ? 'height' : 'width';
      size += labelUnionRect[dimension] + margin;
    }
    if (!axis.scale.isBlank() && axis.model.get(['axisTick', 'show'])) {
      const tickLength = axis.model.get(['axisTick', 'length']);
      size += tickLength;
    }
  }
  return size;
};

export const measureYAxisNameWidth = (chart: ECharts, axisId: string, name: string): number => {
  const axis = getYAxis(chart, axisId);
  if (axis) {
    return axis.model.getModel('nameTextStyle').getTextRect(name).height;
  }
  return 0;
};

export const measureXAxisNameHeight = (chart: ECharts, name: string): number => {
  const axis = getXAxis(chart);
  if (axis) {
    return axis.model.getModel('nameTextStyle').getTextRect(name).height;
  }
  return 0;
};

export const measureThresholdLabelOffset = (chart: ECharts, axisId: string, thresholdId: string, value: any): [number, number] => {
  const axis = getYAxis(chart, axisId);
  if (axis && !axis.scale.isBlank()) {
    const extent = axis.scale.getExtent();
    const model: GlobalModel = (chart as any).getModel();
    const models = model.queryComponents({mainType: 'series', id: thresholdId});
    if (models?.length) {
      const lineSeriesModel = models[0] as SeriesModel<LineSeriesOption>;
      const markLineModel = lineSeriesModel.getModel('markLine');
      const labelPosition = markLineModel.get(['label', 'position']);
      if (labelPosition === 'start' || labelPosition === 'end') {
        const labelModel = markLineModel.getModel('label');
        const formatter = markLineModel.get(['label', 'formatter']);
        let textWidth = 0;
        if (Array.isArray(value)) {
          for (const val of value) {
            if (val >= extent[0] && val <= extent[1]) {
              const textVal = typeof formatter === 'string' ? formatter : formatter({value: val} as CallbackDataParams);
              textWidth = Math.max(textWidth, labelModel.getTextRect(textVal).width);
            }
          }
        } else {
          if (value >= extent[0] && value <= extent[1]) {
            const textVal = typeof formatter === 'string' ? formatter : formatter({value} as CallbackDataParams);
            textWidth = labelModel.getTextRect(textVal).width;
          }
        }
        if (!textWidth) {
          return [0,0];
        }
        const distanceOpt = markLineModel.get(['label', 'distance']);
        let distance = 5;
        if (distanceOpt) {
          distance = typeof distanceOpt === 'number' ? distanceOpt : distanceOpt[0];
        }
        const offset = distance + textWidth;
        if (labelPosition === 'start') {
          return [offset, 0];
        } else {
          return [0, offset];
        }
      }
    }
  }
  return [0,0];
};

export const getAxisExtent = (chart: ECharts, axisId: string): [number, number] => {
  const axis = getYAxis(chart, axisId);
  if (axis) {
    return axis.scale.getExtent();
  }
  return [0,0];
};

export const toNamedData = (data: DataSet): NamedDataSet => {
  if (!data?.length) {
    return [];
  } else {
    return data.map(d => {
      const ts = isDefinedAndNotNull(d[2]) ? d[2][0] : d[0];
      return {
        name: ts + '',
        value: toEChartsDataItem(d)
      };
    });
  }
};

const toEChartsDataItem = (entry: DataEntry): EChartsDataItem => {
  const item: EChartsDataItem = [entry[0], entry[1], entry[0], entry[0]];
  if (isDefinedAndNotNull(entry[2])) {
    item[2] = entry[2][0];
    item[3] = entry[2][1];
  }
  return item;
};

export enum EChartsTooltipTrigger {
  point = 'point',
  axis = 'axis'
}

export const tooltipTriggerTranslationMap = new Map<EChartsTooltipTrigger, string>(
  [
    [ EChartsTooltipTrigger.point, 'tooltip.trigger-point' ],
    [ EChartsTooltipTrigger.axis, 'tooltip.trigger-axis' ]
  ]
);

export interface EChartsTooltipWidgetSettings {
  showTooltip: boolean;
  tooltipTrigger?: EChartsTooltipTrigger;
  tooltipValueFont: Font;
  tooltipValueColor: string;
  tooltipShowDate: boolean;
  tooltipDateInterval?: boolean;
  tooltipDateFormat: DateFormatSettings;
  tooltipDateFont: Font;
  tooltipDateColor: string;
  tooltipBackgroundColor: string;
  tooltipBackgroundBlur: number;
}

export const echartsTooltipFormatter = (renderer: Renderer2,
                                        tooltipDateFormat: DateFormatProcessor,
                                        settings: EChartsTooltipWidgetSettings,
                                        params: CallbackDataParams[] | CallbackDataParams,
                                        decimals: number,
                                        units: string,
                                        focusedSeriesIndex: number,
                                        series?: EChartsSeriesItem[],
                                        interval?: Interval): null | HTMLElement => {
  if (!params || Array.isArray(params) && !params[0]) {
    return null;
  }
  const firstParam = Array.isArray(params) ? params[0] : params;
  const tooltipElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(tooltipElement, 'display', 'flex');
  renderer.setStyle(tooltipElement, 'flex-direction', 'column');
  renderer.setStyle(tooltipElement, 'align-items', 'flex-start');
  renderer.setStyle(tooltipElement, 'gap', '4px');
  if (settings.tooltipShowDate) {
    const dateElement: HTMLElement = renderer.createElement('div');
    let dateText: string;
    const startTs = firstParam.value[2];
    const endTs = firstParam.value[3];
    if (settings.tooltipDateInterval && startTs && endTs && (endTs - 1) > startTs) {
      const startDateText = tooltipDateFormat.update(startTs, interval);
      const endDateText = tooltipDateFormat.update(endTs - 1, interval);
      if (startDateText === endDateText) {
        dateText = startDateText;
      } else {
        dateText = startDateText + ' - ' + endDateText;
      }
    } else {
      const ts = firstParam.value[0];
      dateText = tooltipDateFormat.update(ts, interval);
    }
    renderer.appendChild(dateElement, renderer.createText(dateText));
    renderer.setStyle(dateElement, 'font-family', settings.tooltipDateFont.family);
    renderer.setStyle(dateElement, 'font-size', settings.tooltipDateFont.size + settings.tooltipDateFont.sizeUnit);
    renderer.setStyle(dateElement, 'font-style', settings.tooltipDateFont.style);
    renderer.setStyle(dateElement, 'font-weight', settings.tooltipDateFont.weight);
    renderer.setStyle(dateElement, 'line-height', settings.tooltipDateFont.lineHeight);
    renderer.setStyle(dateElement, 'color', settings.tooltipDateColor);
    renderer.appendChild(tooltipElement, dateElement);
  }
  let seriesParams: CallbackDataParams = null;
  if (Array.isArray(params) && focusedSeriesIndex > -1) {
    seriesParams = params.find(param => param.seriesIndex === focusedSeriesIndex);
  } else if (!Array.isArray(params)) {
    seriesParams = params;
  }
  if (seriesParams) {
    renderer.appendChild(tooltipElement, constructEchartsTooltipSeriesElement(renderer, settings, seriesParams, decimals, units, series));
  } else if (Array.isArray(params)) {
    for (seriesParams of params) {
      renderer.appendChild(tooltipElement, constructEchartsTooltipSeriesElement(renderer, settings, seriesParams, decimals, units, series));
    }
  }
  return tooltipElement;
};

const constructEchartsTooltipSeriesElement = (renderer: Renderer2,
                                              settings: EChartsTooltipWidgetSettings,
                                              seriesParams: CallbackDataParams,
                                              decimals: number,
                                              units: string,
                                              series?: EChartsSeriesItem[]): HTMLElement => {
  const labelValueElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(labelValueElement, 'display', 'flex');
  renderer.setStyle(labelValueElement, 'flex-direction', 'row');
  renderer.setStyle(labelValueElement, 'align-items', 'center');
  renderer.setStyle(labelValueElement, 'align-self', 'stretch');
  renderer.setStyle(labelValueElement, 'gap', '12px');
  const labelElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(labelElement, 'display', 'flex');
  renderer.setStyle(labelElement, 'align-items', 'center');
  renderer.setStyle(labelElement, 'gap', '8px');
  renderer.appendChild(labelValueElement, labelElement);
  const circleElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(circleElement, 'width', '8px');
  renderer.setStyle(circleElement, 'height', '8px');
  renderer.setStyle(circleElement, 'border-radius', '50%');
  renderer.setStyle(circleElement, 'background', seriesParams.color);
  renderer.appendChild(labelElement, circleElement);
  const labelTextElement: HTMLElement = renderer.createElement('div');
  renderer.appendChild(labelTextElement, renderer.createText(seriesParams.seriesName));
  renderer.setStyle(labelTextElement, 'font-family', 'Roboto');
  renderer.setStyle(labelTextElement, 'font-size', '12px');
  renderer.setStyle(labelTextElement, 'font-style', 'normal');
  renderer.setStyle(labelTextElement, 'font-weight', '400');
  renderer.setStyle(labelTextElement, 'line-height', '16px');
  renderer.setStyle(labelTextElement, 'letter-spacing', '0.4px');
  renderer.setStyle(labelTextElement, 'color', 'rgba(0, 0, 0, 0.76)');
  renderer.appendChild(labelElement, labelTextElement);
  const valueElement: HTMLElement = renderer.createElement('div');
  let formatDecimals = decimals;
  let formatUnits = units;
  if (series) {
    const item = series.find(s => s.id === seriesParams.seriesId);
    if (item) {
      formatDecimals = item.decimals;
      formatUnits = item.units;
    }
  }
  const value = formatValue(seriesParams.value[1], formatDecimals, formatUnits, false);
  renderer.appendChild(valueElement, renderer.createText(value));
  renderer.setStyle(valueElement, 'flex', '1');
  renderer.setStyle(valueElement, 'text-align', 'end');
  renderer.setStyle(valueElement, 'font-family', settings.tooltipValueFont.family);
  renderer.setStyle(valueElement, 'font-size', settings.tooltipValueFont.size + settings.tooltipValueFont.sizeUnit);
  renderer.setStyle(valueElement, 'font-style', settings.tooltipValueFont.style);
  renderer.setStyle(valueElement, 'font-weight', settings.tooltipValueFont.weight);
  renderer.setStyle(valueElement, 'line-height', settings.tooltipValueFont.lineHeight);
  renderer.setStyle(valueElement, 'color', settings.tooltipValueColor);
  renderer.appendChild(labelValueElement, valueElement);
  return labelValueElement;
};
