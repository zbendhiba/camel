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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

import org.apache.camel.Converter;
import org.springframework.ai.image.Image;

/**
 * Type converters for Spring AI {@link Image} objects.
 *
 * Enables automatic conversion of generated images to byte arrays or input streams, so they can be directly consumed by
 * components like the file component without an intermediate processor.
 *
 * <pre>
 * from("direct:generate")
 *         .to("spring-ai-image:gen?imageModel=#imageModel")
 *         .to("file:/tmp?fileName=image.png");
 * </pre>
 */
@Converter(generateLoader = true)
public final class SpringAiImageConverter {

    private SpringAiImageConverter() {
    }

    @Converter
    public static byte[] toBytes(Image image) {
        if (image.getB64Json() != null) {
            return Base64.getDecoder().decode(image.getB64Json());
        }
        if (image.getUrl() != null) {
            return image.getUrl().getBytes();
        }
        return new byte[0];
    }

    @Converter
    public static InputStream toInputStream(Image image) {
        return new ByteArrayInputStream(toBytes(image));
    }

    @Converter
    public static String toString(Image image) {
        if (image.getB64Json() != null) {
            return image.getB64Json();
        }
        if (image.getUrl() != null) {
            return image.getUrl();
        }
        return "";
    }
}
