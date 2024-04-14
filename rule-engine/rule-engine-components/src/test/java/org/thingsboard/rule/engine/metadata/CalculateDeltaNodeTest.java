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
package org.thingsboard.rule.engine.metadata;

import com.google.common.util.concurrent.Futures;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.AbstractRuleNodeUpgradeTest;
import org.thingsboard.rule.engine.TestDbCallbackExecutor;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.msg.TbNodeConnectionType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CalculateDeltaNodeTest extends AbstractRuleNodeUpgradeTest {

    private static final DeviceId DUMMY_DEVICE_ORIGINATOR = new DeviceId(UUID.randomUUID());
    private static final TenantId TENANT_ID = new TenantId(UUID.randomUUID());
    private static final ListeningExecutor DB_EXECUTOR = new TestDbCallbackExecutor();
    @Mock
    private TbContext ctxMock;
    @Mock
    private TimeseriesService timeseriesServiceMock;
    @Spy
    private CalculateDeltaNode node;
    private CalculateDeltaNodeConfiguration config;
    private TbNodeConfiguration nodeConfiguration;

    @BeforeEach
    public void setUp() throws TbNodeException {
        config = new CalculateDeltaNodeConfiguration().defaultConfiguration();
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        when(ctxMock.getTimeseriesService()).thenReturn(timeseriesServiceMock);

        node.init(ctxMock, nodeConfiguration);
    }

    @Test
    public void givenDefaultConfig_whenDefaultConfiguration_thenVerify() {
        assertEquals(config.getInputValueKey(), "pulseCounter");
        assertEquals(config.getOutputValueKey(), "delta");
        assertTrue(config.isUseCache());
        assertFalse(config.isAddPeriodBetweenMsgs());
        assertEquals(config.getPeriodValueKey(), "periodInMs");
        assertTrue(config.isTellFailureIfDeltaIsNegative());
    }

    @Test
    public void givenInvalidMsgType_whenOnMsg_thenShouldTellNextOther() {
        // GIVEN
        var msgData = "{\"pulseCounter\": 42}";
        var msg = TbMsg.newMsg(TbMsgType.POST_ATTRIBUTES_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        verify(ctxMock, times(1)).tellNext(eq(msg), eq(TbNodeConnectionType.OTHER));
        verify(ctxMock, never()).tellSuccess(any());
        verify(ctxMock, never()).tellFailure(any(), any());
    }

    @Test
    public void givenInvalidMsgDataType_whenOnMsg_thenShouldTellNextOther() {
        // GIVEN
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_ARRAY);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        verify(ctxMock, times(1)).tellNext(eq(msg), eq(TbNodeConnectionType.OTHER));
        verify(ctxMock, never()).tellSuccess(any());
        verify(ctxMock, never()).tellFailure(any(), any());
    }


    @Test
    public void givenInputKeyIsNotPresent_whenOnMsg_thenShouldTellNextOther() {
        // GIVEN
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_OBJECT);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        verify(ctxMock, times(1)).tellNext(eq(msg), eq(TbNodeConnectionType.OTHER));
        verify(ctxMock, never()).tellSuccess(any());
        verify(ctxMock, never()).tellFailure(any(), any());
    }

    @Test
    public void givenDoubleValue_whenOnMsgAndCachingOff_thenShouldTellSuccess() throws TbNodeException {
        // GIVEN
        config.setRound(1);
        config.setInputValueKey("temperature");
        config.setOutputValueKey("temp_delta");
        config.setUseCache(false);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatestAsync(new BasicTsKvEntry(System.currentTimeMillis(), new DoubleDataEntry("temperature", 40.5)));

        var msgData = "{\"temperature\": 42,\"airPressure\":123}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);

        verify(ctxMock, times(1)).tellSuccess(actualMsgCaptor.capture());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());
        verify(ctxMock, never()).tellFailure(any(), any());

        var expectedMsgData = "{\"temperature\":42,\"airPressure\":123,\"temp_delta\":1.5}";

        assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
    }

    @Test
    public void givenLongStringValue_whenOnMsgAndCachingOff_thenShouldTellSuccess() throws TbNodeException {
        // GIVEN
        config.setInputValueKey("temperature");
        config.setOutputValueKey("temp_delta");
        config.setUseCache(false);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatestAsync(new BasicTsKvEntry(System.currentTimeMillis(), new LongDataEntry("temperature", 40L)));

        var msgData = "{\"temperature\": 42,\"airPressure\":123}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);

        verify(ctxMock, times(1)).tellSuccess(actualMsgCaptor.capture());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());
        verify(ctxMock, never()).tellFailure(any(), any());

        var expectedMsgData = "{\"temperature\":42,\"airPressure\":123,\"temp_delta\":2}";

        assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
    }

    @Test
    public void givenValidStringValue_whenOnMsgAndCachingOff_thenShouldTellSuccess() throws TbNodeException {
        // GIVEN
        config.setInputValueKey("temperature");
        config.setOutputValueKey("temp_delta");
        config.setUseCache(false);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatestAsync(new BasicTsKvEntry(System.currentTimeMillis(), new StringDataEntry("temperature", "40.0")));

        var msgData = "{\"temperature\": 42,\"airPressure\":123}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);

        verify(ctxMock, times(1)).tellSuccess(actualMsgCaptor.capture());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());
        verify(ctxMock, never()).tellFailure(any(), any());

        var expectedMsgData = "{\"temperature\":42,\"airPressure\":123,\"temp_delta\":2}";

        assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
    }

    @Test
    public void givenTwoMessagesAndPeriodOnAndCachingOn_whenOnMsg_thenVerify() throws TbNodeException {
        // STAGE 1
        // GIVEN
        config.setInputValueKey("temperature");
        config.setOutputValueKey("temp_delta");
        config.setPeriodValueKey("ts_delta");
        config.setAddPeriodBetweenMsgs(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatest(new BasicTsKvEntry(1L, new DoubleDataEntry("temperature", 40.0)));

        var msgData = "{\"temperature\": 42,\"airPressure\":123}";
        var firstMsgMetaData = new TbMsgMetaData();
        firstMsgMetaData.putValue("ts", String.valueOf(3L));
        var firstMsg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, firstMsgMetaData, msgData);

        // WHEN
        node.onMsg(ctxMock, firstMsg);

        // THEN
        var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);

        verify(ctxMock, times(1)).tellSuccess(actualMsgCaptor.capture());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());
        verify(ctxMock, never()).tellFailure(any(), any());

        var expectedMsgData = "{\"temperature\":42,\"airPressure\":123,\"temp_delta\":2,\"ts_delta\":2}";

        assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());

        // STAGE 2
        // GIVEN
        reset(ctxMock);
        reset(timeseriesServiceMock);

        var secondMsgMetaData = new TbMsgMetaData();
        secondMsgMetaData.putValue("ts", String.valueOf(6L));
        var secondMsg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, secondMsgMetaData, msgData);

        // WHEN
        node.onMsg(ctxMock, secondMsg);

        // THEN
        actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);

        verify(timeseriesServiceMock, never()).findLatest(any(), any(), anyList());
        verify(ctxMock, times(1)).tellSuccess(actualMsgCaptor.capture());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());
        verify(ctxMock, never()).tellFailure(any(), any());

        expectedMsgData = "{\"temperature\":42,\"airPressure\":123,\"temp_delta\":0,\"ts_delta\":3}";

        assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
    }

    @Test
    public void givenLastValueIsNull_whenOnMsgAndCachingOff_thenDeltaShouldBeZero() throws TbNodeException {
        // GIVEN
        config.setInputValueKey("temperature");
        config.setOutputValueKey("temp_delta");
        config.setUseCache(false);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatestAsync(new BasicTsKvEntry(System.currentTimeMillis(), new DoubleDataEntry("temperature", null)));

        var msgData = "{\"temperature\": 42,\"airPressure\":123}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);

        verify(ctxMock, times(1)).tellSuccess(actualMsgCaptor.capture());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());
        verify(ctxMock, never()).tellFailure(any(), any());

        var expectedMsgData = "{\"temperature\":42,\"airPressure\":123,\"temp_delta\":0}";

        assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
    }

    @Test
    public void givenNegativeDeltaAndTellFailureIfNegativeDeltaTrue_whenOnMsg_thenShouldTellFailure() throws TbNodeException {
        // GIVEN
        config.setTellFailureIfDeltaIsNegative(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatest(new BasicTsKvEntry(System.currentTimeMillis(), new LongDataEntry("pulseCounter", 200L)));

        var msgData = "{\"pulseCounter\":\"123\"}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        var actualExceptionCaptor = ArgumentCaptor.forClass(Exception.class);

        verify(ctxMock, times(1)).tellFailure(actualMsgCaptor.capture(), actualExceptionCaptor.capture());
        verify(ctxMock, never()).tellSuccess(any());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());

        var expectedExceptionMsg = "Delta value is negative!";
        var actualException = actualExceptionCaptor.getValue();

        assertEquals(msg, actualMsgCaptor.getValue());
        assertInstanceOf(IllegalArgumentException.class, actualException);
        assertEquals(expectedExceptionMsg, actualException.getMessage());
    }

    @Test
    public void givenNegativeDeltaAndTellFailureIfNegativeDeltaFalse_whenOnMsg_thenShouldTellSuccess() throws TbNodeException {
        // GIVEN
        config.setTellFailureIfDeltaIsNegative(false);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatest(new BasicTsKvEntry(System.currentTimeMillis(), new LongDataEntry("pulseCounter", 200L)));

        var msgData = "{\"pulseCounter\":\"123\"}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN
        node.onMsg(ctxMock, msg);

        // THEN
        var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);

        verify(ctxMock, times(1)).tellSuccess(actualMsgCaptor.capture());
        verify(ctxMock, never()).tellFailure(any(), any());
        verify(ctxMock, never()).tellNext(any(), anyString());
        verify(ctxMock, never()).tellNext(any(), anySet());

        String expectedMsgData = "{\"pulseCounter\":\"123\",\"delta\":-77}";
        assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
    }

    @Test
    public void givenInvalidStringValue_whenOnMsg_thenException() {
        // GIVEN
        mockFindLatest(new BasicTsKvEntry(System.currentTimeMillis(), new StringDataEntry("pulseCounter", "high")));

        var msgData = "{\"pulseCounter\":\"123\"}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN-THEN
        Assertions.assertThatThrownBy(() -> node.onMsg(ctxMock, msg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculation failed. Unable to parse value [high] of telemetry [pulseCounter] to Double");
    }

    @Test
    public void givenBooleanValue_whenOnMsg_thenException() {
        // GIVEN
        mockFindLatest(new BasicTsKvEntry(System.currentTimeMillis(), new BooleanDataEntry("pulseCounter", false)));

        var msgData = "{\"pulseCounter\":true}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN-THEN
        Assertions.assertThatThrownBy(() -> node.onMsg(ctxMock, msg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculation failed. Boolean values are not supported!");
    }

    @Test
    public void givenJsonValue_whenOnMsg_thenException() {
        // GIVEN
        mockFindLatest(new BasicTsKvEntry(System.currentTimeMillis(), new JsonDataEntry("pulseCounter", "{\"isActive\":false}")));

        var msgData = "{\"pulseCounter\":{\"isActive\":true}}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN-THEN
        Assertions.assertThatThrownBy(() -> node.onMsg(ctxMock, msg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculation failed. JSON values are not supported!");
    }

    @ParameterizedTest
    @MethodSource("CalculateDeltaTestConfig")
    public void givenCalculateDeltaConfig_whenOnMsg_thenVerify(CalculateDeltaTestConfig testConfig) throws TbNodeException {
        // GIVEN
        config.setTellFailureIfDeltaIsNegative(testConfig.isTellFailureIfDeltaIsNegative());
        config.setExcludeZeroDeltas(testConfig.isExcludeZeroDeltas());
        config.setInputValueKey("temperature");
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctxMock, nodeConfiguration);

        mockFindLatest(new BasicTsKvEntry(1L, new DoubleDataEntry("temperature", testConfig.getPrevValue())));

        var msgData = "{\"temperature\":" + testConfig.getCurrentValue() + ",\"airPressure\":123}";
        var msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DUMMY_DEVICE_ORIGINATOR, TbMsgMetaData.EMPTY, msgData);

        // WHEN

        node.onMsg(ctxMock, msg);

        // THEN
        testConfig.getVerificationMethod().accept(ctxMock, msg);
    }

    private static Stream<CalculateDeltaTestConfig> CalculateDeltaTestConfig() {
        return Stream.of(
                // delta = 0, tell failure if delta is negative is set to true and exclude zero deltas is set to true so delta should filter out the message.
                new CalculateDeltaTestConfig(true, true, 40, 40, (ctx, msg) -> {
                    verify(ctx).tellSuccess(eq(msg));
                    verify(ctx).getDbCallbackExecutor();
                    verifyNoMoreInteractions(ctx);
                }),
                // delta < 0, tell failure if delta is negative is set to true so it should throw exception.
                new CalculateDeltaTestConfig(true, true, 41, 40, (ctx, msg) -> {
                    var errorCaptor = ArgumentCaptor.forClass(Throwable.class);
                    verify(ctx).tellFailure(eq(msg), errorCaptor.capture());
                    verify(ctx).getDbCallbackExecutor();
                    verifyNoMoreInteractions(ctx);
                    assertThat(errorCaptor.getValue()).isInstanceOf(IllegalArgumentException.class).hasMessage("Delta value is negative!");
                }),
                // delta < 0, exclude zero deltas is set to true so it should return message with delta if delta is negative is set to false.
                new CalculateDeltaTestConfig(false, true, 41, 40, (ctx, msg) -> {
                    var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
                    verify(ctx).tellSuccess(actualMsgCaptor.capture());
                    verify(ctx).getDbCallbackExecutor();
                    verifyNoMoreInteractions(ctx);
                    String expectedMsgData = "{\"temperature\":40.0,\"airPressure\":123,\"delta\":-1}";
                    assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
                }),
                // delta = 0, tell failure if delta is negative is set to false and exclude zero deltas is set to true so delta should filter out the message.
                new CalculateDeltaTestConfig(false, true, 40, 40, (ctx, msg) -> {
                    verify(ctx).tellSuccess(eq(msg));
                    verify(ctx).getDbCallbackExecutor();
                    verifyNoMoreInteractions(ctx);
                }),
                // delta > 0, exclude zero deltas is set to true so it should return message with delta.
                new CalculateDeltaTestConfig(false, true, 39, 40, (ctx, msg) -> {
                    var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
                    verify(ctx).tellSuccess(actualMsgCaptor.capture());
                    verify(ctx).getDbCallbackExecutor();
                    verifyNoMoreInteractions(ctx);
                    String expectedMsgData = "{\"temperature\":40.0,\"airPressure\":123,\"delta\":1}";
                    assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
                }),
                // delta > 0, exclude zero deltas is set to false so it should return message with delta.
                new CalculateDeltaTestConfig(false, false, 39, 40, (ctx, msg) -> {
                    var actualMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
                    verify(ctx).tellSuccess(actualMsgCaptor.capture());
                    verify(ctx).getDbCallbackExecutor();
                    verifyNoMoreInteractions(ctx);
                    String expectedMsgData = "{\"temperature\":40.0,\"airPressure\":123,\"delta\":1}";
                    assertEquals(expectedMsgData, actualMsgCaptor.getValue().getData());
                })
        );
    }

    @Data
    @RequiredArgsConstructor
    private static class CalculateDeltaTestConfig {
        private final boolean tellFailureIfDeltaIsNegative;
        private final boolean excludeZeroDeltas;
        private final double prevValue;
        private final double currentValue;
        private final BiConsumer<TbContext, TbMsg> verificationMethod;
    }

    private void mockFindLatest(TsKvEntry tsKvEntry) {
        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(timeseriesServiceMock.findLatestSync(
                eq(TENANT_ID), eq(DUMMY_DEVICE_ORIGINATOR), argThat(new ListMatcher<>(List.of(tsKvEntry.getKey())))
        )).thenReturn(List.of(tsKvEntry));
    }

    private void mockFindLatestAsync(TsKvEntry tsKvEntry) {
        when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(timeseriesServiceMock.findLatest(
                eq(TENANT_ID), eq(DUMMY_DEVICE_ORIGINATOR), argThat(new ListMatcher<>(List.of(tsKvEntry.getKey())))
        )).thenReturn(Futures.immediateFuture(List.of(tsKvEntry)));
    }

    @RequiredArgsConstructor
    private static class ListMatcher<T> implements ArgumentMatcher<List<T>> {

        private final List<T> expectedList;

        @Override
        public boolean matches(List<T> actualList) {
            if (actualList == expectedList) {
                return true;
            }
            if (actualList.size() != expectedList.size()) {
                return false;
            }
            return actualList.containsAll(expectedList);
        }

    }

    private static Stream<Arguments> givenFromVersionAndConfig_whenUpgrade_thenVerifyHasChangesAndConfig() {
        return Stream.of(
                // default config for version 0
                Arguments.of(0,
                        "{\"inputValueKey\":\"pulseCounter\",\"outputValueKey\":\"delta\",\"useCache\":true,\"addPeriodBetweenMsgs\":false, \"periodValueKey\":\"periodInMs\", \"round\":null,\"tellFailureIfDeltaIsNegative\":true}",
                        true,
                        "{\"inputValueKey\":\"pulseCounter\",\"outputValueKey\":\"delta\",\"useCache\":true,\"addPeriodBetweenMsgs\":false, \"periodValueKey\":\"periodInMs\", \"round\":null,\"tellFailureIfDeltaIsNegative\":true, \"excludeZeroDeltas\":false}"),
                // default config for version 1 with upgrade from version 0
                Arguments.of(1,
                        "{\"inputValueKey\":\"pulseCounter\",\"outputValueKey\":\"delta\",\"useCache\":true,\"addPeriodBetweenMsgs\":false, \"periodValueKey\":\"periodInMs\", \"round\":null,\"tellFailureIfDeltaIsNegative\":true, \"excludeZeroDeltas\":false}",
                        false,
                        "{\"inputValueKey\":\"pulseCounter\",\"outputValueKey\":\"delta\",\"useCache\":true,\"addPeriodBetweenMsgs\":false, \"periodValueKey\":\"periodInMs\", \"round\":null,\"tellFailureIfDeltaIsNegative\":true, \"excludeZeroDeltas\":false}")
        );

    }

    @Override
    protected TbNode getTestNode() {
        return node;
    }
}
