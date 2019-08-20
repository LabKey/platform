/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;

import java.util.Map;


/**
 * User: jeckels
 * Date: Jan 26, 2009
 */
public abstract class AbstractTsvAssayProvider extends AbstractAssayProvider
{
    public static final String ASSAY_SCHEMA_NAME = "assayresult";
    public static final String ROW_ID_COLUMN_NAME = "RowId";
    public static final String DATA_ID_COLUMN_NAME = "DataId";

    public AbstractTsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, AssayDataType dataType, Module declaringModule)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType, declaringModule);
    }

    public ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol)
    {
        if (dataRowId == null)
            return null;

        Integer id;
        if (dataRowId instanceof Integer)
        {
            id = (Integer)dataRowId;
        }
        else
        {
            try
            {
                id = Integer.parseInt(dataRowId.toString());
            }
            catch (NumberFormatException nfe)
            {
                return null;
            }
        }

        TableInfo table = StorageProvisioner.createTableInfo(getResultsDomain(protocol));
        Map<String, Object>[] rows = new TableSelector(table, table.getColumns(AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME), new SimpleFilter(FieldKey.fromParts(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME), id), null).getMapArray();

        for (Map<String, Object> row : rows)
        {
            Number dataId = (Number)row.get(AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME);
            if (dataId != null)
            {
                return ExperimentService.get().getExpData(dataId.intValue());
            }
        }

        return null;
    }
}
