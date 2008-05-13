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

package org.labkey.study.plate.query;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupURLExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.view.ActionURL;

import java.util.Map;
import java.util.Collections;

/**
 * User: brittp
 * Date: Nov 3, 2006
 * Time: 10:06:59 AM
 */
public abstract class BasePlateTable extends FilteredTable
{
    protected PlateSchema _schema;

    public BasePlateTable(PlateSchema schema, TableInfo info)
    {
        super(info, schema.getContainer());
        _schema = schema;
    }

    protected abstract String getPlateIdColumnName();

    @Override
    public StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns)
    {
        ColumnInfo rowid = columns.get(getPlateIdColumnName());
        if (rowid == null)
            return null;
        ActionURL url = new ActionURL("Plate",
            "plateDetails", _schema.getContainer());
        return new LookupURLExpression(url, Collections.singletonMap("rowId", rowid));
    }
}