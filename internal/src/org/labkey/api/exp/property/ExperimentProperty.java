/*
 * Copyright (c) 2006-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.exp.property;

import org.labkey.api.exp.PropertyType;

public class ExperimentProperty
{
    static private String EXPERIMENT_PROPERTY_URIBASE = "urn:exp.labkey.org/#";

    static public SystemProperty COMMENT = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "Comment", PropertyType.STRING);
    static public SystemProperty LOGTEXT = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "LogText", PropertyType.STRING);
    static public SystemProperty PROTOCOLIMPLEMENTATION = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "ProtocolImplementation", PropertyType.STRING);
    static public SystemProperty SampleSetLSID = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "SampleSetLSID", PropertyType.STRING);
    static public void register()
    {
        // do nothing
    }
}
