/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.issues;

import java.util.Iterator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the issue stated in <a href="https://issues.apache.org/jira/browse/CAMEL-12441">CAMEL-12441</a>.
 */
public class SplitterParallelWithIteratorThrowingExceptionTest extends ContextTestSupport {

    @Test
    public void testIteratorThrowExceptionOnFirst() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:start", new MyIterator(1)),
                "Should throw exception");

        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Forced error", iae.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIteratorThrowExceptionOnSecond() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:start", new MyIterator(2)),
                "Should throw exception");

        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Forced error", iae.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIteratorThrowExceptionOnThird() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(2);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:start", new MyIterator(3)),
                "Should throw exception");

        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Forced error", iae.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").split(body()).aggregationStrategy(new UseLatestAggregationStrategy()).streaming()
                        .stopOnException().parallelProcessing().parallelAggregate()
                        .to("mock:line").end().to("mock:end");
            }
        };
    }

    public static class MyIterator implements Iterator<String> {

        private int count;

        public MyIterator(int count) {
            this.count = count;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public String next() {
            if (--count > 0) {
                return "Hello";
            } else {
                throw new IllegalArgumentException("Forced error");
            }
        }
    }
}
