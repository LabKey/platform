/*
 * Copyright (c) 2005-2016 LabKey Corporation
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

package org.labkey.di.data;

import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.SystemProperty;

public abstract class TransformProperty
{
    static final public String PROPERTY_BASE = "urn:di.labkey.org/#";
    static public final SystemProperty RecordsInserted = new SystemProperty(PROPERTY_BASE + "RecordsInserted", PropertyType.INTEGER);
    static public final SystemProperty RecordsDeleted = new SystemProperty(PROPERTY_BASE + "RecordsDeleted", PropertyType.INTEGER);
    static public final SystemProperty RecordsModified = new SystemProperty(PROPERTY_BASE + "RecordsModified", PropertyType.INTEGER);
    static public final SystemProperty IncrementalStartTimestamp = new SystemProperty(PROPERTY_BASE + "IncrementalStartTimestamp", PropertyType.DATE_TIME);
    static public final SystemProperty IncrementalEndTimestamp = new SystemProperty(PROPERTY_BASE + "IncrementalEndTimestamp", PropertyType.DATE_TIME);
    static public final SystemProperty IncrementalStartRowversion = new SystemProperty(PROPERTY_BASE + "IncrementalStartRowversion", PropertyType.BIGINT);
    static public final SystemProperty IncrementalEndRowversion = new SystemProperty(PROPERTY_BASE + "IncrementalEndRowversion", PropertyType.BIGINT);
    static public final SystemProperty IncrementalRunId = new SystemProperty(PROPERTY_BASE + "IncrementalRunId", PropertyType.INTEGER);
    static public final SystemProperty DeletedIncrementalStartTimestamp = new SystemProperty(PROPERTY_BASE + "DeletedIncrementalStartTimestamp", PropertyType.DATE_TIME);
    static public final SystemProperty DeletedIncrementalEndTimestamp = new SystemProperty(PROPERTY_BASE + "DeletedIncrementalEndTimestamp", PropertyType.DATE_TIME);
    static public final SystemProperty DeletedIncrementalStartRowversion = new SystemProperty(PROPERTY_BASE + "DeletedIncrementalStartRowversion", PropertyType.BIGINT);
    static public final SystemProperty DeletedIncrementalEndRowversion = new SystemProperty(PROPERTY_BASE + "DeletedIncrementalEndRowversion", PropertyType.BIGINT);
    static public final SystemProperty DeletedIncrementalRunId = new SystemProperty(PROPERTY_BASE + "DeletedIncrementalRunId", PropertyType.INTEGER);
    static public final String Parameters = "Parameters";
    static public final String GlobalParameters = "GlobalParameters";
    static public final String RanStep1 = "RanStep1";
    static public final String Constants = "Constants";
    static public final String GlobalConstants = "GlobalConstants";
    static public final String PipelineParameters = "PipelineParameters";

    public static void register()
    {
        // referring to this method during module startup ensures the SystemProperties are registered
    }
}
