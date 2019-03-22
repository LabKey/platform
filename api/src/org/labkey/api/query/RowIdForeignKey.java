/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.StringExpression;

/**
 * A lookup from the __PRIMARY KEY__ of a table back to ITSELF.
 * Why would anyone want to have a lookup back to the exact same column?
 * So that, if the column is used in a query, it is then a lookup so that the user can choose columns which were not
 * necessarily included in the query.
 * This particular type of Foreign Key is treated specially in the Query Designer.  You can only select columns from the
 * lookup table if the RowIdForeignKey is attached to a column that has been imported into another table. 
 */
public class RowIdForeignKey extends AbstractForeignKey
{
    protected ColumnInfo _rowidColumn;

    public RowIdForeignKey(ColumnInfo rowidColumn)
    {
        super(rowidColumn.getParentTable().getPublicSchemaName(), rowidColumn.getParentTable().getName(), rowidColumn.getName());
        _rowidColumn = rowidColumn;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField == null)
            return null;
        return LookupColumn.create(parent, _rowidColumn, getLookupTableInfo().getColumn(displayField), false);
    }

    public TableInfo getLookupTableInfo()
    {
        return _rowidColumn.getParentTable();
    }

    public StringExpression getURL(ColumnInfo parent)
    {
        return LookupForeignKey.getDetailsURL(parent, getLookupTableInfo(), _rowidColumn.getName());
    }

    public ColumnInfo getOriginalColumn()
    {
        return _rowidColumn;
    }
}
