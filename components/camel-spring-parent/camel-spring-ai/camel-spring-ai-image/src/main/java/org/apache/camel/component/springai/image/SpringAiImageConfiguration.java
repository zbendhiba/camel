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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.springframework.ai.image.ImageModel;

@Configurer
@UriParams
public class SpringAiImageConfiguration implements Cloneable {

    @Metadata(required = true, autowired = true)
    @UriParam
    private ImageModel imageModel;

    @UriParam(description = "Number of images to generate")
    private Integer n;

    @UriParam(description = "Image width in pixels")
    private Integer width;

    @UriParam(description = "Image height in pixels")
    private Integer height;

    @UriParam(description = "The model to use for image generation")
    private String model;

    @UriParam(description = "Response format: url or b64_json")
    private String responseFormat;

    @UriParam(description = "Image style (e.g., vivid, natural)")
    private String style;

    public ImageModel getImageModel() {
        return imageModel;
    }

    /**
     * The {@link ImageModel} to use for generating images.
     */
    public void setImageModel(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    public Integer getN() {
        return n;
    }

    /**
     * Number of images to generate. Default depends on the model provider.
     */
    public void setN(Integer n) {
        this.n = n;
    }

    public Integer getWidth() {
        return width;
    }

    /**
     * Image width in pixels.
     */
    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    /**
     * Image height in pixels.
     */
    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getModel() {
        return model;
    }

    /**
     * The model to use for image generation (e.g., dall-e-3).
     */
    public void setModel(String model) {
        this.model = model;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    /**
     * Response format: url or b64_json.
     */
    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public String getStyle() {
        return style;
    }

    /**
     * Image style (e.g., vivid, natural). Support depends on the model provider.
     */
    public void setStyle(String style) {
        this.style = style;
    }

    public SpringAiImageConfiguration copy() {
        try {
            return (SpringAiImageConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
