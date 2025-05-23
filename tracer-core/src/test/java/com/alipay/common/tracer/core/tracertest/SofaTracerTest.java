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
package com.alipay.common.tracer.core.tracertest;

import com.alipay.common.tracer.core.SofaTracer;
import com.alipay.common.tracer.core.TestUtil;
import com.alipay.common.tracer.core.base.AbstractTestBase;
import com.alipay.common.tracer.core.configuration.SofaTracerConfiguration;
import com.alipay.common.tracer.core.context.span.SofaTracerSpanContext;
import com.alipay.common.tracer.core.reporter.digest.DiskReporterImpl;
import com.alipay.common.tracer.core.reporter.digest.event.SpanEventDiskReporter;
import com.alipay.common.tracer.core.reporter.facade.Reporter;
import com.alipay.common.tracer.core.samplers.Sampler;
import com.alipay.common.tracer.core.samplers.SofaTracerPercentageBasedSampler;
import com.alipay.common.tracer.core.span.SofaTracerSpan;
import com.alipay.common.tracer.core.span.SpanEventData;
import com.alipay.common.tracer.core.tracertest.encoder.ClientSpanEncoder;
import com.alipay.common.tracer.core.tracertest.encoder.ClientSpanEventEncoder;
import com.alipay.common.tracer.core.tracertest.encoder.ServerSpanEncoder;
import com.alipay.common.tracer.core.tracertest.type.TracerTestLogEnum;
import com.alipay.common.tracer.core.utils.StringUtils;
import io.opentracing.References;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tags;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * SofaTracer Tester.
 *
 * @author <guanchao.ygc>
 * @version 1.0
 * @since <pre>July 1, 2017</pre>
 */
public class SofaTracerTest extends AbstractTestBase {

    private final String tracerType           = "TracerTestService";
    private final String tracerGlobalTagKey   = "tracerkey";
    private final String tracerGlobalTagValue = "tracervalue";
    private SofaTracer   sofaTracer;

    @Before
    public void beforeInstance() {
        SofaTracerConfiguration.setProperty(SofaTracerConfiguration.SAMPLER_STRATEGY_NAME_KEY,
            SofaTracerPercentageBasedSampler.TYPE);
        SofaTracerConfiguration.setProperty(
            SofaTracerConfiguration.SAMPLER_STRATEGY_PERCENTAGE_KEY, "100");

        //client
        DiskReporterImpl clientReporter = new DiskReporterImpl(
            TracerTestLogEnum.RPC_CLIENT.getDefaultLogName(), new ClientSpanEncoder());

        SpanEventDiskReporter clientEventReporter = new SpanEventDiskReporter(
            TracerTestLogEnum.RPC_CLIENT_EVENT.getDefaultLogName(), "", "",
            new ClientSpanEventEncoder(), null);

        //server
        DiskReporterImpl serverReporter = new DiskReporterImpl(
            TracerTestLogEnum.RPC_SERVER.getDefaultLogName(), new ServerSpanEncoder());

        SpanEventDiskReporter serverEventReporter = new SpanEventDiskReporter(
            TracerTestLogEnum.RPC_SERVER.getDefaultLogName(), "", "", new ClientSpanEventEncoder(),
            null);

        sofaTracer = new SofaTracer.Builder(tracerType).withTag("tracer", "tracerTest")
            .withClientReporter(clientReporter).withServerReporter(serverReporter)
            .withClientEventReporter(clientEventReporter)
            .withServerEventReporter(serverEventReporter)
            .withTag(tracerGlobalTagKey, tracerGlobalTagValue).build();
    }

    /**
     * Method: buildSpan(String operationName)
     */
    @Test
    public void testBuildSpan() {
        String expectedOperation = "operation";
        SofaTracerSpan sofaTracerSpan = (SofaTracerSpan) this.sofaTracer.buildSpan(
            expectedOperation).start();
        assertEquals(expectedOperation, sofaTracerSpan.getOperationName());
    }

    /**
     * Method: inject(SpanContext spanContext, Format<C> format, C carrier)
     */
    @Test
    public void testInject() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testInjectSpan").start();
        TextMap carrier = new TextMap() {

            final Map<String, String> map = new HashMap<>();

            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                return map.entrySet().iterator();
            }

            @Override
            public void put(String key, String value) {
                map.put(key, value);
            }
        };
        SofaTracerSpanContext originContext = (SofaTracerSpanContext) span.context();
        assertTrue(StringUtils.isBlank(originContext.getParentId()));
        this.sofaTracer.inject(originContext, Format.Builtin.TEXT_MAP, carrier);

        SofaTracerSpanContext extractSpanContext = (SofaTracerSpanContext) this.sofaTracer.extract(
            Format.Builtin.TEXT_MAP, carrier);
        assertTrue("Origin Context : " + originContext.toString(),
            StringUtils.isBlank(extractSpanContext.getParentId()));
        assertEquals("Extract Context : " + extractSpanContext, originContext, extractSpanContext);
    }

    /**
     * Method: reportSpan(SofaTracerSpan span)
     */
    @Test
    public void testReportSpan() {
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testInjectSpan")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        //Report Do not prohibit writing
        SpanEventData spanEventData = new SpanEventData();
        spanEventData.setTimestamp(System.currentTimeMillis());
        spanEventData.getEventTagWithStr().put("kkk11", "vvv22");
        span.addEvent(spanEventData);

        SpanEventData spanEventData2 = new SpanEventData();
        spanEventData2.setTimestamp(System.currentTimeMillis());
        spanEventData2.getEventTagWithStr().put("kkk222", "vvv33");
        span.addEvent(spanEventData2);

        span.finish();

        TestUtil.periodicallyAssert(() -> {
            try {
                List<String> contents = FileUtils.readLines(customFileLog(TracerTestLogEnum.RPC_CLIENT
                        .getDefaultLogName()));
                assertEquals(contents.get(0), 1, contents.size());
                String contextStr = contents.get(0);
                //Test print one only put one tag
                assertTrue(contextStr.contains(Tags.SPAN_KIND.getKey())
                        && contextStr.contains(Tags.SPAN_KIND_CLIENT));
            } catch (IndexOutOfBoundsException | IOException e) {
                throw new AssertionError(e);
            }
        }, 5000);
    }

    /**
     * Method: isDisableDigestLog(SofaTracerSpan span)
     */
    @Test
    public void testIsDisableAllDigestLog() {
        //Close the digest log globally
        SofaTracerConfiguration.setProperty(
            SofaTracerConfiguration.DISABLE_MIDDLEWARE_DIGEST_LOG_KEY, "true");
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testInjectSpan")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        SpanEventData spanEventData = new SpanEventData();
        spanEventData.getEventTagWithNumber().put("tag.num", 999);
        spanEventData.getEventTagWithBool().put("tag.key", true);
        span.addEvent(spanEventData);
        //report
        span.finish();
        assertFalse(customFileLog(TracerTestLogEnum.RPC_CLIENT.getDefaultLogName()).exists());
        // reset
        SofaTracerConfiguration.setProperty(
            SofaTracerConfiguration.DISABLE_MIDDLEWARE_DIGEST_LOG_KEY, "");
    }

    @Test
    public void testIsDisableClientDigestLog() {
        //Close the client digest log
        String clientLogTypeName = TracerTestLogEnum.RPC_CLIENT.getDefaultLogName();

        Map<String, String> prop = new HashMap<>();
        prop.put(clientLogTypeName, "true");
        SofaTracerConfiguration.setProperty(SofaTracerConfiguration.DISABLE_DIGEST_LOG_KEY, prop);
        //create
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testInjectSpan")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        SpanEventData spanEventData = new SpanEventData();
        spanEventData.getEventTagWithStr().put("kkk", "vvv");
        span.addEvent(spanEventData);
        //report
        span.finish();
        assertFalse(customFileLog(clientLogTypeName).exists());
        SofaTracerConfiguration.setProperty(SofaTracerConfiguration.DISABLE_DIGEST_LOG_KEY,
            new HashMap<>());
    }

    /**
     * Method: close()
     */
    @Test
    public void testClose() {
        //create
        SofaTracerSpan span = (SofaTracerSpan) this.sofaTracer.buildSpan("testClose")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        this.sofaTracer.close();
        //report
        SpanEventData spanEventData = new SpanEventData();
        spanEventData.getEventTagWithStr().put("kkk", "vvv");
        span.addEvent(spanEventData);
        span.finish();
        String clientLogTypeName = TracerTestLogEnum.RPC_CLIENT.getDefaultLogName();
        assertFalse(customFileLog(clientLogTypeName).exists());
    }

    @Test
    public void testTracerClose() {
        Reporter reporter = mock(Reporter.class);
        Sampler sampler = mock(Sampler.class);
        SofaTracer sofaTracer = new SofaTracer.Builder(tracerType).withClientReporter(reporter)
            .withSampler(sampler).build();
        sofaTracer.close();
        verify(reporter).close();
        sampler.close();
        verify(sampler).close();
    }

    /**
     * Method: getTracerType()
     */
    @Test
    public void testGetTracerType() {
        String tracerType = this.sofaTracer.getTracerType();
        assertEquals(tracerType, this.tracerType);
    }

    /**
     * Method: getClientSofaTracerDigestReporter()
     */
    @Test
    public void testGetSofaTracerDigestReporter() {
        assertNotNull(this.sofaTracer.getClientReporter());
    }

    /**
     * Method: getClientSofaTracerStatisticReporter()
     */
    @Test
    public void testGetSofaTracerStatisticReporter() {
        assertNotNull(this.sofaTracer.getClientReporter());
        assertTrue(this.sofaTracer.getClientReporter() instanceof DiskReporterImpl);
        DiskReporterImpl clientReporter = (DiskReporterImpl) this.sofaTracer.getClientReporter();
        assertTrue(StringUtils.isBlank(clientReporter.getStatReporterType()));
        assertNull(clientReporter.getStatReporter());
    }

    /**
     * Method: getTracerTags()
     */
    @Test
    public void testGetTracerTags() {
        Map<String, Object> tags = this.sofaTracer.getTracerTags();
        assertTrue(tags.containsKey(this.tracerGlobalTagKey));
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (this.tracerGlobalTagKey.equals(key)) {
                assertEquals("tracer tags : key=" + key + ",value = " + value,
                    this.tracerGlobalTagValue, value);
            }
        }
    }

    /**
     * Method: asChildOf(SpanContext parent)
     */
    @Test
    public void testAsChildOfParent() {
        //create
        Map<String, String> bizBaggage = new HashMap<>();
        bizBaggage.put("biz", "value");
        bizBaggage.put("biz1", "value1");
        bizBaggage.put("biz2", "value2");
        Map<String, String> sysBaggage = new HashMap<>();
        sysBaggage.put("sys", "value");
        sysBaggage.put("sys1", "value1");
        sysBaggage.put("sys2", "value2");
        SofaTracerSpan spanParent = (SofaTracerSpan) this.sofaTracer.buildSpan("spanParent")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        spanParent.getSofaTracerSpanContext().addBizBaggage(bizBaggage);
        spanParent.getSofaTracerSpanContext().addSysBaggage(sysBaggage);
        String parentTraceId = spanParent.getSofaTracerSpanContext().getTraceId();
        SofaTracerSpanContext parentSpanContext = (SofaTracerSpanContext) spanParent.context();
        assertEquals("\nroot spanId : " + parentSpanContext.getSpanId(),
            parentSpanContext.getSpanId(), SofaTracer.ROOT_SPAN_ID);
        //child
        SofaTracerSpan spanChild = (SofaTracerSpan) this.sofaTracer.buildSpan("spanChild")
            .asChildOf(spanParent).withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        SofaTracerSpanContext childContext = spanChild.getSofaTracerSpanContext();
        String childTraceId = childContext.getTraceId();

        String childSpanId = childContext.getSpanId();
        String[] childArray = childSpanId.split("\\.");
        assertEquals("child spanId : " + childSpanId, 2, childArray.length);
        assertEquals(SofaTracer.ROOT_SPAN_ID, childArray[0]);
        assertEquals("Traceid : " + parentTraceId, parentTraceId, childTraceId);
        //baggage
        assertEquals(bizBaggage, childContext.getBizBaggage());
        assertEquals(
            "Biz : " + childContext.getBizBaggage() + ",Sys : " + childContext.getSysBaggage(),
            sysBaggage, childContext.getSysBaggage());
    }

    /**
     * Method: asChildOf(SpanContext parent)
     */
    @Test
    public void testAsChildOfParentTestBizBaggageAndSysBaggage() {
        //create
        SofaTracerSpan spanParent = (SofaTracerSpan) this.sofaTracer.buildSpan("spanParent")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        String parentTraceId = spanParent.getSofaTracerSpanContext().getTraceId();
        SofaTracerSpanContext parentSpanContext = (SofaTracerSpanContext) spanParent.context();
        assertEquals(parentSpanContext.getSpanId(), SofaTracer.ROOT_SPAN_ID);
        //child
        SofaTracerSpan spanChild = (SofaTracerSpan) this.sofaTracer.buildSpan("spanChild")
            .asChildOf(spanParent).withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        String childTraceId = spanChild.getSofaTracerSpanContext().getTraceId();
        String childSpanId = spanChild.getSofaTracerSpanContext().getSpanId();
        String[] childArray = childSpanId.split("\\.");
        assertEquals("\nroot spanId : " + parentSpanContext.getSpanId(), 2, childArray.length);
        assertEquals("child spanId : " + childSpanId, SofaTracer.ROOT_SPAN_ID, childArray[0]);
        assertEquals("Traceid : " + parentTraceId, parentTraceId, childTraceId);
    }

    /**
     * Method: asChildOf(Span parentSpan)
     * Multiple times, baggage reuse only select the first father
     */
    @Test
    public void testAsChildOfMultiParentSpan() {
        //create
        SofaTracerSpan spanParent = (SofaTracerSpan) this.sofaTracer.buildSpan("spanParent")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        String parentTraceID = spanParent.getSofaTracerSpanContext().getTraceId();
        String parentSpanId = spanParent.getSofaTracerSpanContext().getSpanId();
        //follow
        SofaTracerSpan spanFollow = (SofaTracerSpan) this.sofaTracer.buildSpan("spanFollow")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        String followTraceId = spanFollow.getSofaTracerSpanContext().getTraceId();
        String followSpanId = spanFollow.getSofaTracerSpanContext().getSpanId();
        //parent1
        SofaTracerSpan spanParent1 = (SofaTracerSpan) this.sofaTracer.buildSpan("spanParent1")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        String p1TraceId = spanParent1.getSofaTracerSpanContext().getTraceId();
        String p1SpanId = spanParent1.getSofaTracerSpanContext().getSpanId();
        //assert
        assertTrue("Parent1 --> TraceId : " + p1TraceId + ", spanId : " + p1SpanId,
            SofaTracer.ROOT_SPAN_ID.equals(parentSpanId) && parentSpanId.equals(followSpanId)
                    && followSpanId.equals(p1SpanId));
        assertNotEquals(parentTraceID, followTraceId);
        assertNotEquals(followTraceId, p1TraceId);
        //child
        SofaTracerSpan childSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("childFollow")
            //parent
            .addReference(References.CHILD_OF, spanParent.context())
            //follow
            .addReference(References.FOLLOWS_FROM, spanFollow.context())
            //parent1
            .addReference(References.CHILD_OF, spanParent1.context())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        String childTraceid = childSpan.getSofaTracerSpanContext().getTraceId();
        String childSpanId = childSpan.getSofaTracerSpanContext().getSpanId();
        assertNotEquals("Child --> TraceId : " + childTraceid + ", spanId :" + childSpanId,
            p1TraceId, childTraceid);
        //child context
        assertEquals(childTraceid, parentTraceID);
        //grandson
        SofaTracerSpan grandsonSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("grandson")
            //parent
            .addReference(References.CHILD_OF, childSpan.context())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        String grandsonTraceid = grandsonSpan.getSofaTracerSpanContext().getTraceId();
        String grandsonSpanId = grandsonSpan.getSofaTracerSpanContext().getSpanId();
        //check traceId
        assertEquals("Grandson --> TraceId : " + grandsonTraceid + ", Grandson spanId :"
                     + grandsonSpanId, childTraceid, parentTraceID);
        assertEquals(childTraceid, grandsonTraceid);
        //check spanId
        assertEquals(grandsonSpan.getSofaTracerSpanContext().getParentId(), childSpan
            .getSofaTracerSpanContext().getSpanId());
        assertEquals(childSpan.getSofaTracerSpanContext().getParentId(), spanParent
            .getSofaTracerSpanContext().getSpanId());
    }

    /**
     * Method: addReference(String referenceType, SpanContext referencedContext)
     */
    @Test
    public void testAddReferenceForReferenceTypeReferencedContextAndBaggageMultipleReferences() {
        //create
        SofaTracerSpan spanParent = (SofaTracerSpan) this.sofaTracer
            .buildSpan(
                "testAddReferenceForReferenceTypeReferencedContextAndBaggageMultipleReferences")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        //baggage
        String parBagKey = "parBagKey";
        String parBagValue = "parBagValue";
        spanParent.setBaggageItem(parBagKey, parBagValue);

        String parentTraceID = spanParent.getSofaTracerSpanContext().getTraceId();
        //follow
        SofaTracerSpan spanFollow1 = (SofaTracerSpan) this.sofaTracer.buildSpan("spanFollow1")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        //baggage
        String fol1BagKey = "fol1BagKey";
        String fol1BagValue = "fol1BagValue";
        spanFollow1.setBaggageItem(fol1BagKey, fol1BagValue);
        //follow1
        SofaTracerSpan spanFollow2 = (SofaTracerSpan) this.sofaTracer.buildSpan("spanFollow2")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        //baggage
        String fol2BagKey = "fol2BagKey";
        String fol2BagValue = "fol2BagValue";
        spanFollow2.setBaggageItem(fol2BagKey, fol2BagValue);
        String followTraceId2 = spanFollow2.getSofaTracerSpanContext().getTraceId();
        String followSpanId2 = spanFollow2.getSofaTracerSpanContext().getSpanId();
        //child
        SofaTracerSpan childSpan = (SofaTracerSpan) this.sofaTracer.buildSpan("childSpan")
            //parent
            .addReference(References.CHILD_OF, spanParent.context())
            //follow1
            .addReference(References.FOLLOWS_FROM, spanFollow1.context())
            //follow2
            .addReference(References.FOLLOWS_FROM, spanFollow2.context())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
        //check traceId
        assertEquals("Follow --> TraceId : " + followTraceId2 + ", spanId : " + followSpanId2,
            parentTraceID, childSpan.getSofaTracerSpanContext().getTraceId());
        //check spanId
        assertEquals(childSpan.getSofaTracerSpanContext().getParentId(), spanParent
            .getSofaTracerSpanContext().getSpanId());
        //baggage
        assertEquals("Child Baggage : " + childSpan.getSofaTracerSpanContext().getBizBaggage(),
            parBagValue, childSpan.getBaggageItem(parBagKey));
        assertEquals(fol1BagValue, childSpan.getBaggageItem(fol1BagKey));
        assertEquals(fol2BagValue, childSpan.getBaggageItem(fol2BagKey));
    }

    /**
     * Method: withTag(String key, String value)
     */
    @Test
    public void testWithTagForKeyValue() {
        //create
        SofaTracerSpan spanParent = (SofaTracerSpan) this.sofaTracer
            .buildSpan("testWithTagForKeyValue")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
        //tags
        StringTag stringTag = new StringTag("tagkey");
        stringTag.set(spanParent, "tagvalue");
        //tags
        spanParent.setTag("tag1", "value");

        Map<String, String> tagsStr = spanParent.getTagsWithStr();
        //string
        assertTrue("tagsStr : " + tagsStr,
            tagsStr.containsKey("tagkey") && tagsStr.containsValue("tagvalue"));
        assertTrue(tagsStr.containsKey("tag1") && tagsStr.containsValue("value"));
        //bool
        spanParent.setTag("bool", Boolean.TRUE);
        spanParent.setTag("bool1", Boolean.FALSE);
        assertEquals(spanParent.getTagsWithBool().get("bool"), Boolean.TRUE);
        assertEquals(spanParent.getTagsWithBool().get("bool1"), Boolean.FALSE);
        //number
        spanParent.setTag("num1", 10);
        spanParent.setTag("num2", 20);
        spanParent.setTag("num3", 2.22);
        assertEquals(10, spanParent.getTagsWithNumber().get("num1"));
        assertEquals(20, spanParent.getTagsWithNumber().get("num2"));
        assertEquals(2.22, spanParent.getTagsWithNumber().get("num3"));
    }

    /**
     * Method: withStartTimestamp(long microseconds)
     */
    @Test
    public void testWithStartTimestampMicroseconds() {
        long startTime = 111;
        //create
        SofaTracerSpan spanParent = (SofaTracerSpan) this.sofaTracer
            .buildSpan("testWithStartTimestampMicroseconds")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).withStartTimestamp(startTime)
            .start();
        //
        assertEquals(startTime, spanParent.getStartTime());
    }

    /**
     * Method: withClientStatsReporter(SofaTracerDigestReporter sofaTracerDigestReporter)
     */
    @Test
    public void testWithStatsReporterSofaTracerDigestReporter() {
        assertNotNull(this.sofaTracer.getServerReporter());
    }

    /**
     * Method: withSampler(Sampler sampler)
     */
    @Test
    public void testWithSampler() {
        assertNotNull(this.sofaTracer.getSampler());
    }

}
