/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.data.ColumnInfo;

import java.util.List;

public interface ExpRunTable extends ExpTable<ExpRunTable.Column>
{
    static public final String COLUMN_ROWID = "exp.experimentrun.rowid";

    enum Column
    {
        RowId,
        ProtocolStep,
        LSID,
        Name,
        Protocol,
        Comments,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy,
        Container,
        FilePathRoot,
        Flag,
        Links,
        RunGroups;

        public String getColumnName()
        {
            return name();
        }
    }

    void populate(ExpSchema schema);
    /**
     * Add a filter to only show runs using the specified protocol.
     */
    void setProtocol(ExpProtocol protocol);
    ExpProtocol getProtocol();

    void setProtocolPatterns(String... pattern);

    void setExperiment(ExpExperiment experiment);
    ExpExperiment getExperiment();

    void setInputMaterial(ExpMaterial material);
    ExpMaterial getInputMaterial();

    void setInputData(ExpData expData);
    ExpData getInputData();

    void setRuns(List<ExpRun> runs);


    /**
     * Returns a column which links to a data input of the specified type.
     */
    ColumnInfo addDataInputColumn(String alias, String roleName);

    /**
     * Returns a column which displays the number of data inputs of the specified role.
     */
    ColumnInfo addDataCountColumn(String alias, String roleName);

    /**
     * Returns a column which is a lookup to a table which has columns for input datas and materials from the run protocol app.
     */
    ColumnInfo createInputLookupColumn(String alias, ExpSchema schema);

    /**
     * Returns a column which is a lookup to a table which has columns for output datas and materials from the run output protocol app.
     */
    ColumnInfo createOutputLookupColumn(String alias, ExpSchema schema);
}
