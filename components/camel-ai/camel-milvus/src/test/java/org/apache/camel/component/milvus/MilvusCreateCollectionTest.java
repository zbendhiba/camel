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

package org.apache.camel.component.milvus;

import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.milvus.helpers.MilvusHelperCreateCollection;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MilvusCreateCollectionTest extends MilvusTestSupport {

    @DisplayName("Tests that trying to create a collection without passing the action name triggers a failure")
    @Test
    public void createCollectionWithoutRequiredParameters() throws Exception {
        MilvusHelperCreateCollection ragCreateCollection = new MilvusHelperCreateCollection();
        ragCreateCollection.setCollectionName("test");
        ragCreateCollection.setCollectionDescription("customer info");
        ragCreateCollection.setIdFieldName("userID");
        ragCreateCollection.setVectorFieldName("userFace");
        ragCreateCollection.setTextFieldName("userAge");
        ragCreateCollection.setTextFieldDataType("Int8");
        ragCreateCollection.setDimension("64");

        Exchange tempExchange = new DefaultExchange(context);
        ragCreateCollection.process(tempExchange);

        // Send body without the action header to trigger failure
        Exchange result = fluentTemplate.to("milvus:createCollection")
                .withBody(tempExchange.getIn().getBody())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
    }
}
