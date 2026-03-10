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

import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.query.FieldKey;

public interface ExpDataTable extends ExpTable<ExpDataTable.Column>
{
    enum Column
    {
        Alias,
        ContentLink,
        ClassId, // database table only
        CpasType, // database table only
        Created,
        CreatedBy,
        DataClass,
        DataFileUrl,
        Description,
        DownloadLink,
        FileExtension,
        FileExists,
        FileSize,
        Flag,
        Folder,
        Generated,
        InlineThumbnail,
        Inputs,
        LastIndexed,
        LSID,
        Modified,
        ModifiedBy,
        Name,
        ObjectId, // database table only
        Outputs,
        Properties,
        Protocol,
        ReferenceCount,
        Run,
        RunApplication,
        RunApplicationOutput,
        RunId, // database table only
        RowId,
        SourceApplicationId, // database table only
        SourceApplicationInput,
        SourceProtocolApplication,
        Thumbnail,
        ViewFileLink,
        ViewOrDownload,
        WebDavUrl,
        WebDavUrlRelative;

        public FieldKey fieldKey()
        {
            return FieldKey.fromParts(name());
        }
    }

    void setExperiment(ExpExperiment experiment);
    ExpExperiment getExperiment();
    void setRun(ExpRun run);
    ExpRun getRun();
    
    void setDataType(DataType type);
    DataType getDataType();

    MutableColumnInfo addMaterialInputColumn(String alias, SamplesSchema schema, String inputRole, ExpSampleType sampleType);
    MutableColumnInfo addDataInputColumn(String alias, String role);
    MutableColumnInfo addInputRunCountColumn(String alias);
}
