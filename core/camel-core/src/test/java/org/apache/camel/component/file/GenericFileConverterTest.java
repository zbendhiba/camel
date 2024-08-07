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
package org.apache.camel.component.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.junit.jupiter.api.Test;

public class GenericFileConverterTest extends ContextTestSupport {

    public static final String TEST_FILE_NAME = "hello." + UUID.randomUUID() + ".txt";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testToFile() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).convertBodyTo(File.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(File.class);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(String.class);
        mock.message(0).body().isEqualTo("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToBytes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).convertBodyTo(byte[].class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToSerializable() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).convertBodyTo(Serializable.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Serializable.class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToInputStream() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).convertBodyTo(InputStream.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(InputStream.class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToFileInputStream() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).convertBodyTo(InputStream.class).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                        Object body = exchange.getIn().getBody();
                        assertIsInstanceOf(InputStreamCache.class, body);
                    }
                }).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(InputStreamCache.class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToFileInputStreamNoStreamCaching() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                from(fileUri("?initialDelay=0&delay=10"))
                        .streamCache(Boolean.toString(false))
                        .convertBodyTo(InputStream.class).process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                Object body = exchange.getIn().getBody();
                                assertIsInstanceOf(BufferedInputStream.class, body);
                            }
                        }).to("mock:result");
            }
        });
        context.start();

        // a file input stream is wrapped in a buffered so its faster

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(BufferedInputStream.class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

}
