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
package org.apache.camel.component.springai.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for MCP (Model Context Protocol) client support.
 *
 * Uses the MCP filesystem server via stdio transport. Requires Node.js and npx to be available on the system path.
 *
 * The test creates temporary files and verifies that the LLM can use MCP tools to read and list them.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatMcpIT extends OllamaTestSupport {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setupTestFiles() throws IOException {
        // Create test files for the MCP filesystem server to read
        Files.writeString(tempDir.resolve("hello.txt"), "Hello from Camel Spring AI MCP test!");
        Files.writeString(tempDir.resolve("data.csv"), "name,age\nAlice,30\nBob,25");
    }

    @Test
    public void testMcpListFiles() {
        String response = template().requestBody("direct:mcpChat",
                "List the files in the allowed directory", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("hello", "data", "txt", "csv", "file");
    }

    @Test
    public void testMcpReadFile() {
        String response = template().requestBody("direct:mcpChat",
                "Read the contents of hello.txt", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("hello", "camel", "mcp");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        String tempDirPath = tempDir.toAbsolutePath().toString();

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(getCamelContext());

                // Chat endpoint with MCP filesystem server via stdio transport
                from("direct:mcpChat")
                        .toF("spring-ai-chat:mcpChat?chatModel=#chatModel"
                             + "&mcpServer.fs.transportType=stdio"
                             + "&mcpServer.fs.command=npx"
                             + "&mcpServer.fs.args=-y,@modelcontextprotocol/server-filesystem,%s",
                                tempDirPath);
            }
        };
    }
}
