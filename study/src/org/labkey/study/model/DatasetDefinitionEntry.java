/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.study.model;

import org.labkey.data.xml.reportProps.PropertyList;

/**
 * User: daxh
 * Date: Jan 6, 2006
 * Time: 10:29:31 AM
 */
public class DatasetDefinitionEntry
{
    public DatasetDefinitionEntry(DatasetDefinition datasetDefinition, boolean isNew, PropertyList tags)
    {
        this.datasetDefinition = datasetDefinition;
        this.isNew = isNew;
        this.isModified = isNew;
        this.tags = tags;
    }

    public DatasetDefinitionEntry(DatasetDefinition datasetDefinition, boolean isNew, boolean isModified, PropertyList tags)
    {
        this.datasetDefinition = datasetDefinition;
        this.isNew = isNew;
        this.isModified = isModified;
        this.tags = tags;
    }

    public DatasetDefinition datasetDefinition;
    public boolean isNew;
    public boolean isModified;
    public PropertyList tags;
}
