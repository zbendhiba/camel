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
package org.apache.camel.component.vector.search;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

public class LangChain4jVectorSearchProducer extends DefaultProducer {
    public LangChain4jVectorSearchProducer(LangChain4jVectorSearchEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public LangChain4jVectorSearchEndpoint getEndpoint() {
        return (LangChain4jVectorSearchEndpoint) super.getEndpoint();
    }


    @Override
    public void process(Exchange exchange) throws Exception {

        final TextSegment in = exchange.getMessage().getMandatoryBody(TextSegment.class);
        final EmbeddingModel model = getEndpoint().getConfiguration().getEmbeddingModel();



    }
}
