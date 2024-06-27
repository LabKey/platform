/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.ValidationException;

import java.util.Map;

public class SimpleAssayDataImportHelper implements OntologyManager.ImportHelper, OntologyManager.UpdateableTableImportHelper
{
    private final ExpData _data;
    private final ExpProtocol _protocol;
    private final AssayProvider _provider;

    public SimpleAssayDataImportHelper(ExpData data, ExpProtocol protocol, AssayProvider provider)
    {
        _data = data;
        _protocol = protocol;
        _provider = provider;
    }

    @Override
    public String beforeImportObject(Map<String, Object> map)
    {
        return null;
    }

    @Override
    public void afterBatchInsert(int currentRow)
    {

    }

    @Override
    public void updateStatistics(int currentRow)
    {
    }

    @Override
    public void bindAdditionalParameters(Map<String, Object> map, ParameterMapStatement target) throws ValidationException
    {
        target.put("DataId", _data.getRowId());
    }

    @Override
    public String afterImportObject(Map<String, Object> map)
    {
        // Now that we know the RowId we know the LSID. Keep in sync with AssayResultTable.createRowExpressionLsidColumn()
        return _provider.getResultRowLSIDExpression() + ".Protocol-" + _protocol.getRowId() + ":" + map.get("RowId");
    }
}
