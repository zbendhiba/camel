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
package org.apache.camel.component.springai.image;

import org.apache.camel.spi.Metadata;

public final class SpringAiImageHeaders {

    @Metadata(description = "Number of images to generate", javaType = "Integer")
    public static final String N = "CamelSpringAiImageN";

    @Metadata(description = "Image width in pixels", javaType = "Integer")
    public static final String WIDTH = "CamelSpringAiImageWidth";

    @Metadata(description = "Image height in pixels", javaType = "Integer")
    public static final String HEIGHT = "CamelSpringAiImageHeight";

    @Metadata(description = "The model to use for image generation", javaType = "String")
    public static final String MODEL = "CamelSpringAiImageModel";

    @Metadata(description = "Response format: url or b64_json", javaType = "String")
    public static final String RESPONSE_FORMAT = "CamelSpringAiImageResponseFormat";

    @Metadata(description = "Image style (e.g., vivid, natural)", javaType = "String")
    public static final String STYLE = "CamelSpringAiImageStyle";

    @Metadata(description = "The image response metadata",
              javaType = "org.springframework.ai.image.ImageResponseMetadata")
    public static final String RESPONSE_METADATA = "CamelSpringAiImageResponseMetadata";

    @Metadata(description = "The ImageGeneration object for single image results",
              javaType = "org.springframework.ai.image.ImageGeneration")
    public static final String IMAGE_GENERATION = "CamelSpringAiImageGeneration";

    @Metadata(description = "List of ImageGeneration objects for multiple image results",
              javaType = "java.util.List<org.springframework.ai.image.ImageGeneration>")
    public static final String IMAGE_GENERATIONS = "CamelSpringAiImageGenerations";

    private SpringAiImageHeaders() {
    }
}
