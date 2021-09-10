/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.api.exp.query;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public interface ExpSampleTypeTable extends ExpTable<ExpSampleTypeTable.Column>
{
    enum Column
    {
        RowId,
        LSID,
        Name,
        Description,
        NameExpression,
        AliquotNameExpression,
        LabelColor,
        MetricUnit,
        AutoLinkTargetContainer,
        AutoLinkCategory,
        MaterialLSIDPrefix,
        Created,
        Modified,
        CreatedBy,
        ModifiedBy,
        Folder,
        SampleCount,
        ImportAliases,
        MaterialInputImportAliases,
        DataInputImportAliases,
        // Columns not supported by ExpSampleSetTableImpl.createColumn()
        Flag,
        Run,
        SampleSet,
        SourceProtocolLSID,
        SourceProtocolApplication,
        SourceApplicationInput,
        RunApplication,
        RunApplicationOutput,
        Property,
        Alias,
        Inputs,
        Outputs,
        Properties
    }
}
