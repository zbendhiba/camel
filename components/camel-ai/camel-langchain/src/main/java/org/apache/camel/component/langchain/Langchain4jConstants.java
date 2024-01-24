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
package org.apache.camel.component.langchain;

public class Langchain4jConstants {

    public static final String PROMPT_TEMPLATE = "PROMPT_TEMPLATE";

    /**
     * Timeout of Response from OpenAI in Milliseconds
     */
    public static final int TIMEOUT = 1000;
    /**
     * Nb of retry with OpenAI
     */
    public static final int NB_RETRY = 1;

    /**
     * Log Resquests
     */
    public static final boolean LOG_REQUEST = false;

    /**
     * Log Response
     */
    public static final boolean LOG_RESPONSE = false;

    public static final boolean RETURN_FULL_TEXT = true;

    /**
     * Endpoint type of DATA INGEST ==> Embed + store
     */
    public static final String ENDPOINT_TYPE_DATA_INGEST = "data-ingest";

    /**
     * Endpoint type CHAT ==> using chatModel for simple conversational mode. In case we are using a dedicated component
     * as OpenAI, the component can init the ChatModel
     */
    public static final String ENDPOINT_TYPE_CHAT = "chat";

    /**
     * Endpoint type Embed ==> using Embedding Model to Embed data. In case we are using a dedicated component as
     * OpenAI, the component can init the EmbeddingModel
     */
    public static final String ENDPOINT_TYPE_EMBED = "embed";

    /**
     * Endpoint of type Store ==> Using embedding store to store the Embeddings.
     */
    public static final String ENDPOINT_TYPE_STORE = "store";

    /**
     * Conversational chain - advanced usage
     */
    public static final String ENDPOINT_TYPE_CHAIN = "chain";

    public static final String EMBEDDING_METADATA = "EMBEDDING_METADATA";

}
