/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.labkey.api.data.Parameter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.query.ValidationException;

import java.util.Map;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Jul 23, 2007
 */
public class SimpleAssayDataImportHelper implements OntologyManager.ImportHelper, OntologyManager.UpdateableTableImportHelper
{
    private int _id = 0;
    private ExpData _data;
    public SimpleAssayDataImportHelper(ExpData data)
    {
        _data = data;
    }

    public String beforeImportObject(Map<String, Object> map) throws SQLException
    {
        return _data.getLSID() + ".DataRow-" + _id++;
    }

    public void afterBatchInsert(int currentRow) throws SQLException
    {

    }

    public void updateStatistics(int currentRow) throws SQLException
    {
    }

    @Override
    public void bindAdditionalParameters(Map<String, Object> map, Parameter.ParameterMap target) throws ValidationException
    {
        target.put("DataId", _data.getRowId());
    }

    @Override
    public void afterImportObject(Map<String, Object> map) throws SQLException
    {
    }
}
