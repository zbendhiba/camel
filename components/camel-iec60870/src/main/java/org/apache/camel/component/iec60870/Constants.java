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
package org.apache.camel.component.iec60870;

public interface Constants {
    String PARAM_DATA_MODULE_OPTIONS = "dataModuleOptions";

    String PARAM_PROTOCOL_OPTIONS = "protocolOptions";

    String PARAM_CONNECTION_OPTIONS = "connectionOptions";

    String IEC60870_VALUE = "CamelIec60870Value";
    String IEC60870_TIMESTAMP = "CamelIec60870Timestamp";
    String IEC60870_QUALITY = "CamelIec60870Quality";
    String IEC60870_OVERFLOW = "CamelIec60870Overflow";
}
