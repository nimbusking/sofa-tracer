/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.common.tracer.core.span;

import com.alipay.common.tracer.core.SofaTracer;
import com.alipay.common.tracer.core.TestUtil;
import com.alipay.common.tracer.core.base.AbstractTestBase;
import com.alipay.common.tracer.core.context.span.SofaTracerSpanContext;
import com.alipay.common.tracer.core.generator.TraceIdGenerator;
import com.alipay.common.tracer.core.reporter.digest.DiskReporterImpl;
import com.alipay.common.tracer.core.reporter.digest.event.SpanEventDiskReporter;
import com.alipay.common.tracer.core.reporter.facade.Reporter;
import com.alipay.common.tracer.core.tracertest.encoder.ClientSpanEncoder;
import com.alipay.common.tracer.core.tracertest.encoder.ClientSpanEventEncoder;
import com.alipay.common.tracer.core.tracertest.encoder.ServerSpanEncoder;
import com.alipay.common.tracer.core.utils.StringUtils;
import com.google.common.collect.Lists;
import io.opentracing.tag.Tags;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * SofaTracerSpan Tester.
 *
 * @author <guanchao.ygc>
 * @version 1.0
 * @since <pre>July 6, 2017</pre>
 */
public class SofaTracerSpanTest extends AbstractTestBase {

    private final String   clientLogType   = "client-log-test.log";

    private final String   clientEventType = "client-event-log-test.log";

    private final String   serverLogType   = "server-log-test.log";

    private final String   serverEventType = "server-event-log-test.log";

    private SofaTracer     sofaTracer;

    private SofaTracerSpan sofaTracerSpan;

    @Before
    public void setup() {
        Reporter clientReporter = new DiskReporterImpl(clientLogType, new ClientSpanEncoder());

        Reporter serverReporter = new DiskReporterImpl(serverLogType, new ServerSpanEncoder());

        Reporter clientEventReporter = new SpanEventDiskReporter(clientEventType, "", "",
            new ClientSpanEventEncoder(), null);

        Reporter serverEventReporter = new SpanEventDiskReporter(serverEventType, "", "",
            new ClientSpanEventEncoder(), null);
        String tracerType = "SofaTracerSpanTest";
        sofaTracer = new SofaTracer.Builder(tracerType)
            .withTag("tracer", "SofaTraceContextHolderTest").withClientReporter(clientReporter)
            .withServerReporter(serverReporter).withClientEventReporter(clientEventReporter)
            .withServerEventReporter(serverEventReporter).build();

        sofaTracerSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("SofaTracerSpanTest").start();
    }

    @After
    public void afterMethod() throws Exception {
        File file = tracerSelfLog();
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.createNewFile();
        }

    }

    @Test
    public void testCloneInstance() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testCloneInstance")
            .start();
        span.setParentSofaTracerSpan(this.sofaTracerSpan);

        SofaTracerSpanContext spanContext = span.getSofaTracerSpanContext();
        SofaTracerSpan cloneSpan = span.cloneInstance();
        SofaTracerSpanContext cloneSpanContext = cloneSpan.getSofaTracerSpanContext();

        assertEquals(spanContext, cloneSpanContext);
        assertEquals(spanContext.getBizBaggage(), cloneSpanContext.getBizBaggage());
        assertEquals(spanContext.isSampled(), cloneSpanContext.isSampled());
        assertSame(spanContext.getChildContextIndex(), cloneSpanContext.getChildContextIndex());
        assertEquals(span.getTagsWithStr(), cloneSpan.getTagsWithStr());
        assertEquals(span.getTagsWithNumber(), cloneSpan.getTagsWithNumber());
        assertEquals(span.getTagsWithBool(), cloneSpan.getTagsWithBool());
        assertEquals(span.getStartTime(), cloneSpan.getStartTime());
        assertEquals(span.getEndTime(), cloneSpan.getEndTime());
        assertEquals(Lists.newArrayList(span.getLogs()), Lists.newArrayList(cloneSpan.getLogs()));
        assertEquals(span.getLogType(), cloneSpan.getLogType());
        assertEquals(span.getOperationName(), cloneSpan.getOperationName());
        assertSame(span.getParentSofaTracerSpan(), cloneSpan.getParentSofaTracerSpan());
    }

    @Test
    public void testConstructSpan() {
        long startTime = 111;
        String traceId = "traceId";
        String spanId = "spanId";
        SofaTracerSpanContext sofaTracerSpanContext = new SofaTracerSpanContext(traceId, spanId,
            null);
        Map<String, String> tags = new HashMap<>();
        tags.put("key", "value");
        SofaTracerSpan sofaTracerSpan = new SofaTracerSpan(this.sofaTracer, startTime,
            "testConstructSpan", sofaTracerSpanContext, tags);
        sofaTracerSpan.finish(222);

        SofaTracerSpanContext context = (SofaTracerSpanContext) sofaTracerSpan.context();
        assertEquals(sofaTracerSpanContext, context);

        Map<String, String> getTags = sofaTracerSpan.getTagsWithStr();
        assertEquals(tags, getTags);

        assertEquals("testConstructSpan", sofaTracerSpan.getOperationName());
        assertEquals(111, sofaTracerSpan.getStartTime());
        assertEquals(222 - 111, sofaTracerSpan.getDurationMicroseconds());

    }

    /**
     * Method: context()
     */
    @Test
    public void testContext() {
        SofaTracerSpanContext spanContext = (SofaTracerSpanContext) sofaTracerSpan.context();
        assertEquals(spanContext.toString(), SofaTracer.ROOT_SPAN_ID, spanContext.getSpanId());
    }

    @Test
    public void testSetAndGetBaggageItem() {
        String expected = "expected";
        String key = "some.BAGGAGE";
        sofaTracerSpan.setBaggageItem(key, expected);
        assertEquals(expected, sofaTracerSpan.getBaggageItem(key));
    }

    @Test
    public void testSetBooleanTag() {
        String key = "tag.key";

        sofaTracerSpan.setTag(key, true);
        assertEquals(true, sofaTracerSpan.getTagsWithBool().get(key));
    }

    @Test
    public void testSetOperationName() {
        String expected = "modified.operation";

        assertEquals("SofaTracerSpanTest", sofaTracerSpan.getOperationName());
        sofaTracerSpan.setOperationName(expected);
        assertEquals(expected, sofaTracerSpan.getOperationName());
    }

    /**
     * Method: setTag(String key, String value)
     */
    @Test
    public void testSetStringTag() {
        String expected = "expected.value";
        String key = "tag.key";

        sofaTracerSpan.setTag(key, expected);
        assertEquals(expected, sofaTracerSpan.getTagsWithStr().get(key));
    }

    /**
     * Method: setTag(String key, Number number)
     */
    @Test
    public void testSetNumberTag() {
        Integer expected = 5;
        String key = "tag.key";

        sofaTracerSpan.setTag(key, expected);
        assertEquals(expected, sofaTracerSpan.getTagsWithNumber().get(key));
    }

    @Test
    public void testWithTimestampDurationEndTimeMinusStartTime() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testWithTimestamp")
            .withStartTimestamp(111).start();
        span.finish(999);
        assertEquals(111, span.getStartTime());
        assertEquals(999 - 111, span.getDurationMicroseconds());
    }

    /**
     * Method: finish()
     */
    @Test
    public void testFinish() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testWithTimestamp")
            .withStartTimestamp(111).start();
        long endTime = System.currentTimeMillis();
        span.finish();
        assertTrue("Endtime : " + endTime + ", Duration :" + span.getDurationMicroseconds(),
            111 < span.getDurationMicroseconds() && span.getDurationMicroseconds() < endTime);
    }

    @Test
    public void testEvent() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testWithTimestamp")
            .withStartTimestamp(111).start();
        SpanEventData spanEventData = new SpanEventData();
        spanEventData.setTimestamp(System.currentTimeMillis());
        spanEventData.getEventTagWithStr().put("tag.key", "value");
        span.addEvent(spanEventData);
        span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
    }

    /**
     * Method: close()
     */
    @Test
    public void testClose() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testWithTimestamp")
            .withStartTimestamp(111).start();
        //close
        span.close();
        long endTime = System.currentTimeMillis();
        assertTrue("Endtime : " + endTime + ", Duration :" + span.getDurationMicroseconds(),
            111 < span.getDurationMicroseconds() && span.getDurationMicroseconds() < endTime);
    }

    /**
     * Method: log(String eventType)
     */
    @Test
    public void testLogEventType() {
        List<String> valueStr = Arrays.asList("value0", "value1", "value2");
        long beginTime = System.currentTimeMillis();
        sofaTracerSpan.log(valueStr.get(0));
        sofaTracerSpan.log(valueStr.get(1));
        sofaTracerSpan.log(valueStr.get(2));
        ConcurrentLinkedQueue<LogData> logDataList = sofaTracerSpan.getLogs();
        assertEquals(3, logDataList.size());
        for (LogData logData : logDataList) {
            String value = (String) logData.getFields().get(LogData.EVENT_TYPE_KEY);
            assertTrue(valueStr.contains(value));
            long time = logData.getTime();
            assertTrue(beginTime <= time && time <= System.currentTimeMillis());
        }
        logDataList.clear();
    }

    /**
     * Method: log(long currentTime, String eventType)
     */
    @Test
    public void testLogForCurrentTimeEventType() {
        List<String> valueStr = Arrays.asList("value0", "value1", "value2");
        SofaTracerSpan sofaTracerSpan1 = (SofaTracerSpan) this.sofaTracer
            .buildSpan("testLogForCurrentTimeEventType").withStartTimestamp(110).start();

        sofaTracerSpan1.log(111, valueStr.get(0));
        sofaTracerSpan1.log(111, valueStr.get(1));
        sofaTracerSpan1.log(111, valueStr.get(2));
        ConcurrentLinkedQueue<LogData> logDataList = sofaTracerSpan1.getLogs();
        assertEquals(3, logDataList.size());
        for (LogData logData : logDataList) {
            String value = (String) logData.getFields().get(LogData.EVENT_TYPE_KEY);
            long time = logData.getTime();
            assertTrue(valueStr.contains(value));
            assertEquals(111, time);
        }
        logDataList.clear();
    }

    /**
     * Method: log(long currentTime, Map<String, ?> map)
     */
    @Test
    public void testLogForCurrentTimeMap() {
        SofaTracerSpan testLogForCurrentTimeMapSpan = (SofaTracerSpan) this.sofaTracer
            .buildSpan("testLogForCurrentTimeMap").withStartTimestamp(111).start();
        Map<String, String> fields = new HashMap<>();
        fields.put("key", "value");
        Map<String, String> fields1 = new HashMap<>();
        fields1.put("key1", "value1");
        Map<String, String> fields2 = new HashMap<>();
        fields2.put("key2", "value2");
        Map<String, String> fields3 = new HashMap<>();
        fields3.put("key3", "value3");

        testLogForCurrentTimeMapSpan.log(222, fields);
        testLogForCurrentTimeMapSpan.log(222, fields1);
        testLogForCurrentTimeMapSpan.log(222, fields2);
        testLogForCurrentTimeMapSpan.log(222, fields3);
        ConcurrentLinkedQueue<LogData> queue = testLogForCurrentTimeMapSpan.getLogs();
        ArrayList<LogData> logDataList = Lists.newArrayList(queue);
        assertEquals(4, logDataList.size());
        assertEquals(222, logDataList.get(0).getTime());
        assertTrue(logDataList.get(0).getFields().containsKey("key")
                   && logDataList.get(0).getFields().containsValue("value"));
        assertEquals(222, logDataList.get(1).getTime());
        assertTrue(logDataList.get(1).getFields().containsKey("key1")
                   && logDataList.get(1).getFields().containsValue("value1"));
        assertEquals(222, logDataList.get(2).getTime());
        assertTrue(logDataList.get(2).getFields().containsKey("key2")
                   && logDataList.get(2).getFields().containsValue("value2"));
        assertEquals(222, logDataList.get(3).getTime());
        assertTrue(logDataList.get(3).getFields().containsKey("key3")
                   && logDataList.get(3).getFields().containsValue("value3"));

    }

    /**
     * Method: log(Map<String, ?> map)
     */
    @Test
    public void testLogMap() {
        SofaTracerSpan testLogMap = (SofaTracerSpan) this.sofaTracer.buildSpan("testLogMap")
            .withStartTimestamp(111).start();
        Map<String, String> fields = new HashMap<>();
        fields.put("key", "value");
        testLogMap.log(222, fields);
        ConcurrentLinkedQueue<LogData> logDataList = testLogMap.getLogs();
        assertEquals(1, logDataList.size());
        assertTrue(logDataList.peek().getFields().containsKey("key")
                   && logDataList.peek().getFields().containsValue("value"));
    }

    /**
     * Method: log(String eventName, Object payload)
     */
    @Test
    public void testLogForEventNamePayload() {
        SofaTracerSpan testLogForEventNamePayloadSpan = (SofaTracerSpan) this.sofaTracer
            .buildSpan("testLogForEventNamePayload").withStartTimestamp(111).start();
        Object payload = new Object();

        testLogForEventNamePayloadSpan.log("eventName", payload);
        //
        Object load = testLogForEventNamePayloadSpan.getLogs().peek().getFields().get("eventName");
        assertSame(load, payload);
    }

    /**
     * Method: log(long currentTime, String eventName, Object payload)
     */
    @Test
    public void testLogForCurrentTimeEventNamePayload() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer
            .buildSpan("testLogForEventNamePayload").withStartTimestamp(111).start();
        span.log(222, "eventName222", "value222");
        span.log(333, "eventName333", "value333");
        span.log(444, "eventName444", "value444");
        ConcurrentLinkedQueue<LogData> queue = span.getLogs();
        ArrayList<LogData> logDataList = Lists.newArrayList(queue);
        assertEquals(3, logDataList.size());
        assertEquals(222, logDataList.get(0).getTime());
        assertEquals(1, logDataList.get(0).getFields().size());
        assertTrue(logDataList.get(0).getFields().containsKey("eventName222")
                   && logDataList.get(0).getFields().containsValue("value222"));
        assertEquals(333, logDataList.get(1).getTime());
        assertEquals(444, logDataList.get(2).getTime());
    }

    /**
     * Method: getTagsWithStr()
     */
    @Test
    public void testGetTagsWithStr() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testGetTagsWithStr")
            .withStartTimestamp(111).start();
        //str
        span.setTag("key", "value");
        assertTrue(span.getTagsWithStr().containsKey("key")
                   && span.getTagsWithStr().containsValue("value"));
    }

    /**
     * Method: getTagsWithBool()
     */
    @Test
    public void testGetTagsWithBool() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testGetTagsWithStr")
            .withStartTimestamp(111).start();
        //bool
        span.setTag("key", true);
        assertTrue(span.getTagsWithBool().containsKey("key")
                   && span.getTagsWithBool().containsValue(true));
    }

    /**
     * Method: getTagsWithNumber()
     */
    @Test
    public void testGetTagsWithNumber() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testGetTagsWithNumber")
            .withStartTimestamp(111).start();
        span.setTag("key", 100);
        span.setTag("key1", 2.22);
        assertTrue(span.getTagsWithNumber().containsKey("key")
                   && span.getTagsWithNumber().containsValue(100));
        assertTrue(span.getTagsWithNumber().containsKey("key1")
                   && span.getTagsWithNumber().containsValue(2.22));
    }

    /**
     * Method: getLogType()
     */
    @Test
    public void testGetLogType() {
        //client
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testGetLogTypeClient")
            .withStartTimestamp(111).start();
        //client
        span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
        assertTrue(span.isClient());
        assertFalse(span.isServer());
        //logtype
        String logType = span.getLogType();
        assertEquals(clientLogType, logType);
        //server
        SofaTracerSpan serverSpan = (SofaTracerSpan) this.sofaTracer
            .buildSpan("testGetLogTypeServer").withStartTimestamp(111).start();
        serverSpan.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        assertEquals(serverLogType, serverSpan.getLogType());
    }

    /**
     * Method: setLogType(String logType)
     */
    @Test
    public void testSetLogType() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testSetLogType")
            .withStartTimestamp(111).start();
        span.setLogType("client");
        assertEquals("client", span.getLogType());
    }

    /**
     * Method: getParentSofaTracerSpan()
     */
    @Test
    public void testGetParentSofaTracerSpan() {
        SofaTracerSpan parentSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("parent")
            .withStartTimestamp(111).start();

        SofaTracerSpan childSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("child")
            .withStartTimestamp(222).start();
        childSpan.setParentSofaTracerSpan(parentSpan);
        assertSame(childSpan.getParentSofaTracerSpan(), parentSpan);
    }

    /**
     * Method: isServer()
     */
    @Test
    public void testIsServer() {
        SofaTracerSpan clientSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("testIsClient")
            .withStartTimestamp(111).start();
        clientSpan.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
        //
        SofaTracerSpan serverSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("testIsServer")
            .withStartTimestamp(111).start();
        serverSpan.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        //
        assertTrue(clientSpan.isClient());
        assertTrue(serverSpan.isServer());
    }

    /**
     * Method: toString()
     */
    @Test
    public void testToString() {
        String str = sofaTracerSpan.toString();
        assertTrue(str, StringUtils.isNotBlank(str));
    }

    /**
     * Method: getThisAsParentWhenExceedLayer()
     */
    @Test
    public void testGetThisAsParentWhenExceedLayer() {
        StringBuilder spanIdBuilder = new StringBuilder("0").append(".");
        int i = 1;
        for (; i < 150; i++) {
            spanIdBuilder.append(i).append(".");
        }
        spanIdBuilder.append(i);
        Map<String, String> baggage = new HashMap<>();
        baggage.put("key", "value");
        baggage.put("key1", "value1");
        baggage.put("key2", "value2");
        String traceId = TraceIdGenerator.generate();
        SofaTracerSpanContext sofaTracerSpanContext = new SofaTracerSpanContext(traceId,
                spanIdBuilder.toString(), "");
        sofaTracerSpanContext.addBizBaggage(baggage);
        SofaTracerSpan sofaTracerSpan = new SofaTracerSpan(this.sofaTracer,
                System.currentTimeMillis(), "open", sofaTracerSpanContext, null);
        //
        SofaTracerSpan thisAsParentSpan = sofaTracerSpan.getThisAsParentWhenExceedLayer();
        assertEquals("\n" + sofaTracerSpanContext, SofaTracer.ROOT_SPAN_ID, thisAsParentSpan
                .getSofaTracerSpanContext().getSpanId());
        assertNotEquals(traceId, thisAsParentSpan.getSofaTracerSpanContext().getTraceId());
        assertEquals(baggage, sofaTracerSpanContext.getBizBaggage());

        TestUtil.periodicallyAssert(() -> {
            try {
                Assert.assertTrue(checkSelfLogContainsError());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }, 500);
    }

    @Test
    public void testTags() {
        SofaTracerSpan sofaTracerSpan = new SofaTracerSpan(this.sofaTracer,
            System.currentTimeMillis(), "open", SofaTracerSpanContext.rootStart(), null);
        sofaTracerSpan.setTag("key", "");
        assertEquals(0, sofaTracerSpan.getTagsWithStr().size());
    }
}