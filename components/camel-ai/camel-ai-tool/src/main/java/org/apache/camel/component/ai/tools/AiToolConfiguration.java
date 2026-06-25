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
package org.apache.camel.component.ai.tools;

import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class AiToolConfiguration implements Cloneable {

    @UriParam(description = "Comma-separated list of tags used to group tools. "
                            + "Producers filter the registry by these tags to select which tools to expose to the LLM. "
                            + "When omitted, the tool goes into a default pool available to all producers.")
    private String tags;

    @Metadata(label = "consumer")
    @UriParam(description = "Human-readable description of what this tool does. "
                            + "Passed verbatim to the LLM — be precise and action-oriented.")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool input parameters. Format: parameter.<name>=<type>, "
                            + "parameter.<name>.description=<text>, "
                            + "parameter.<name>.required=<true|false>, "
                            + "parameter.<name>.enum=<val1,val2>. "
                            + "Supported types: string, integer, number, boolean.",
              prefix = "parameter.", multiValue = true)
    private Map<String, String> parameters;

    @Metadata(label = "consumer", defaultValue = "true")
    @UriParam(description = "When true (default) the tool is exposed directly to the LLM. "
                            + "When false the tool is added to the searchable pool and discovered "
                            + "on demand via the ToolSearchTool mechanism.",
              defaultValue = "true")
    private boolean exposed = true;

    public AiToolConfiguration() {
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }

    public AiToolConfiguration copy() {
        try {
            return (AiToolConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
