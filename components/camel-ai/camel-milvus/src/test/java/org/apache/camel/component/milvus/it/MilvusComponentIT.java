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
package org.apache.camel.component.milvus.it;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.QueryResults;
import io.milvus.param.IndexType;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.highlevel.dml.response.SearchResponse;
import io.milvus.param.index.CreateIndexParam;
import org.apache.camel.Exchange;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;
import org.apache.camel.component.milvus.MilvusTestSupport;
import org.apache.camel.component.milvus.helpers.MilvusHelperCreateCollection;
import org.apache.camel.component.milvus.helpers.MilvusHelperCreateIndex;
import org.apache.camel.component.milvus.helpers.MilvusHelperDelete;
import org.apache.camel.component.milvus.helpers.MilvusHelperSearch;
import org.apache.camel.support.DefaultExchange;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MilvusComponentIT extends MilvusTestSupport {
    @Test
    @Order(1)
    public void createCollection() throws Exception {
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

        Exchange result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, tempExchange.getIn().getHeader(MilvusHeaders.ACTION))
                .withBody(tempExchange.getIn().getBody())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void createIndex() throws Exception {
        CreateIndexParam createAgeIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName("test")
                .withFieldName("userAge")
                .withIndexType(IndexType.STL_SORT)
                .withSyncMode(Boolean.TRUE)
                .build();

        Exchange result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_INDEX)
                .withBody(
                        createAgeIndexParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        MilvusHelperCreateIndex ragCreateIndex = new MilvusHelperCreateIndex();
        ragCreateIndex.setCollectionName("test");
        ragCreateIndex.setVectorFieldName("userFace");
        ragCreateIndex.setIndexType("IVF_FLAT");
        ragCreateIndex.setMetricType("L2");
        ragCreateIndex.setExtraParam("{\"nlist\":128}");

        Exchange tempExchange = new DefaultExchange(context);
        ragCreateIndex.process(tempExchange);

        result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, tempExchange.getIn().getHeader(MilvusHeaders.ACTION))
                .withBody(tempExchange.getIn().getBody())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(3)
    public void insert() {
        Random ran = new Random();
        List<Integer> ages = new ArrayList<>();
        for (long i = 0L; i < 2; ++i) {
            ages.add(ran.nextInt(99));
        }
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("userAge", ages));
        fields.add(new InsertParam.Field("userFace", generateFloatVectors(2)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName("test")
                .withFields(fields)
                .build();

        Exchange result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.INSERT)
                .withBody(
                        insertParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(4)
    public void upsert() {
        Random ran = new Random();
        List<Integer> ages = new ArrayList<>();
        for (long i = 0L; i < 2; ++i) {
            ages.add(ran.nextInt(99));
        }
        List<UpsertParam.Field> fields = new ArrayList<>();
        fields.add(new UpsertParam.Field("userAge", ages));
        fields.add(new UpsertParam.Field("userFace", generateFloatVectors(2)));

        UpsertParam upsertParam = UpsertParam.newBuilder()
                .withCollectionName("test")
                .withFields(fields)
                .build();

        Exchange result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.UPSERT)
                .withBody(
                        upsertParam)
                .request(Exchange.class);

        // we cannot upsert as we lack userID field
        assertThat(result).isNotNull();
        Assertions.assertTrue(result.isFailed());
    }

    @Test
    @Order(5)
    public void search() throws Exception {
        MilvusHelperSearch ragSearch = new MilvusHelperSearch();
        ragSearch.setCollectionName("test");
        ragSearch.setOutputFields("userAge");
        ragSearch.setFilter("userAge>0");
        ragSearch.setLimit("100");

        Exchange tempExchange = new DefaultExchange(context);
        tempExchange.getIn().setBody(generateFloatVector());
        ragSearch.process(tempExchange);

        Exchange result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, tempExchange.getIn().getHeader(MilvusHeaders.ACTION))
                .withBody(tempExchange.getIn().getBody())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(SearchResponse.class).getRowRecords().size() == 2);
    }

    @Test
    @Order(6)
    public void query() {
        QueryParam searchSimpleParam = QueryParam.newBuilder()
                .withCollectionName("test")
                .withExpr("userAge>0")
                .withOutFields(Lists.newArrayList("userAge"))
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();

        Exchange result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.QUERY)
                .withBody(
                        searchSimpleParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(QueryResults.class).getFieldsDataCount() == 2);
    }

    @Test
    @Order(7)
    public void delete() throws Exception {
        MilvusHelperDelete ragDelete = new MilvusHelperDelete();
        ragDelete.setCollectionName("test");
        ragDelete.setFilter("userAge>0");

        Exchange tempExchange = new DefaultExchange(context);
        ragDelete.process(tempExchange);

        Exchange result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, tempExchange.getIn().getHeader(MilvusHeaders.ACTION))
                .withBody(tempExchange.getIn().getBody())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        MilvusHelperSearch ragSearch = new MilvusHelperSearch();
        ragSearch.setCollectionName("test");
        ragSearch.setOutputFields("userAge");
        ragSearch.setFilter("userAge>0");
        ragSearch.setLimit("100");

        tempExchange = new DefaultExchange(context);
        tempExchange.getIn().setBody(generateFloatVector());
        ragSearch.process(tempExchange);

        result = fluentTemplate.to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, tempExchange.getIn().getHeader(MilvusHeaders.ACTION))
                .withBody(tempExchange.getIn().getBody())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(SearchResponse.class).getRowRecords().size() == 0);
    }

    private List<List<Float>> generateFloatVectors(int count) {
        Random ran = new Random();
        List<List<Float>> vectors = new ArrayList<>();
        for (int n = 0; n < count; ++n) {
            List<Float> vector = new ArrayList<>();
            for (int i = 0; i < 64; ++i) {
                vector.add(ran.nextFloat());
            }
            vectors.add(vector);
        }

        return vectors;
    }

    private List<Float> generateFloatVector() {
        Random ran = new Random();
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 64; ++i) {
            vector.add(ran.nextFloat());
        }
        return vector;
    }

}
