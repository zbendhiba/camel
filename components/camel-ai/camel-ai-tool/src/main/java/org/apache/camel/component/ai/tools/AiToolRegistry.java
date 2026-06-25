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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared singleton registry mapping tags to {@link AiToolSpec} instances. All AI components (LangChain4j, Spring AI,
 * OpenAI) read from this registry to discover tools registered via the {@code ai-tool} consumer endpoint.
 * <p>
 * Replaces the duplicated {@code CamelToolExecutorCache} singletons from {@code camel-langchain4j-tools} and
 * {@code camel-spring-ai-tools}.
 *
 * @since 4.21
 */
public final class AiToolRegistry {

    private final Map<String, Set<AiToolSpec>> tools;
    private final Map<String, Set<AiToolSpec>> searchableTools;
    private final Set<AiToolSpec> defaultTools;
    private final Set<AiToolSpec> defaultSearchableTools;

    private AiToolRegistry() {
        tools = new ConcurrentHashMap<>();
        searchableTools = new ConcurrentHashMap<>();
        defaultTools = Collections.synchronizedSet(new LinkedHashSet<>());
        defaultSearchableTools = Collections.synchronizedSet(new LinkedHashSet<>());
    }

    private static final class SingletonHolder {
        private static final AiToolRegistry INSTANCE = new AiToolRegistry();
    }

    public static AiToolRegistry getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public void put(String tag, AiToolSpec spec) {
        tools.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(spec);
    }

    public void putSearchable(String tag, AiToolSpec spec) {
        searchableTools.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(spec);
    }

    public void remove(String tag, AiToolSpec spec) {
        Set<AiToolSpec> set = tools.get(tag);
        if (set != null) {
            set.remove(spec);
            if (set.isEmpty()) {
                tools.remove(tag);
            }
        }
    }

    public void removeSearchable(String tag, AiToolSpec spec) {
        Set<AiToolSpec> set = searchableTools.get(tag);
        if (set != null) {
            set.remove(spec);
            if (set.isEmpty()) {
                searchableTools.remove(tag);
            }
        }
    }

    public void putDefault(AiToolSpec spec) {
        defaultTools.add(spec);
    }

    public void putDefaultSearchable(AiToolSpec spec) {
        defaultSearchableTools.add(spec);
    }

    public void removeDefault(AiToolSpec spec) {
        defaultTools.remove(spec);
    }

    public void removeDefaultSearchable(AiToolSpec spec) {
        defaultSearchableTools.remove(spec);
    }

    /**
     * Returns tools registered for a specific tag, merged with the default pool (tools with no tags).
     */
    public Set<AiToolSpec> getToolsByTag(String tag) {
        Set<AiToolSpec> result = new LinkedHashSet<>(defaultTools);
        Set<AiToolSpec> tagTools = tools.get(tag);
        if (tagTools != null) {
            result.addAll(tagTools);
        }
        return result;
    }

    /**
     * Returns all tools across all tags and the default pool.
     */
    public Set<AiToolSpec> getAllTools() {
        Set<AiToolSpec> result = new LinkedHashSet<>(defaultTools);
        for (Set<AiToolSpec> tagTools : tools.values()) {
            result.addAll(tagTools);
        }
        return result;
    }

    public Map<String, Set<AiToolSpec>> getTools() {
        return tools;
    }

    public Set<AiToolSpec> getDefaultTools() {
        return defaultTools;
    }

    public Map<String, Set<AiToolSpec>> getSearchableTools() {
        return searchableTools;
    }

    public Set<AiToolSpec> getDefaultSearchableTools() {
        return defaultSearchableTools;
    }

    public boolean hasSearchableTools() {
        return !searchableTools.isEmpty() || !defaultSearchableTools.isEmpty();
    }
}
