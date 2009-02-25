/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.CaseInsensitiveHashMap;

import java.util.Collections;

abstract public class LookupForeignKey extends AbstractForeignKey
{
    ActionURL _baseURL;
    Object _param;
    private boolean _prefixColumnCaption = true;
    String _titleColumn;

    public LookupForeignKey(ActionURL baseURL, String paramName, String tableName, String pkColumnName, String titleColumn)
    {
        super(tableName, pkColumnName);
        _baseURL = baseURL;
        _param = paramName;
        _titleColumn = titleColumn;
    }

    public LookupForeignKey(ActionURL baseURL, Enum paramName, String tableName, String pkColumnName, String titleColumn)
    {
        this(pkColumnName);
        _baseURL = baseURL;
        _param = paramName;
        _titleColumn = titleColumn;
    }

    // XXX: remove all calls to this constructor
    public LookupForeignKey(ActionURL baseURL, String paramName, String pkColumnName, String titleColumn)
    {
        this(baseURL, paramName, null, pkColumnName, titleColumn);
    }

    // XXX: remove all calls to this constructor
    public LookupForeignKey(ActionURL baseURL, Enum paramName, String pkColumnName, String titleColumn)
    {
        this(baseURL, paramName, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(String tableName, String pkColumnName, String titleColumn)
    {
         this(null, (String) null, tableName, pkColumnName, titleColumn);
    }

    // XXX: remove all calls to this constructor
    public LookupForeignKey(String pkColumnName, String titleColumn)
    {
         this(null, (String) null, null, pkColumnName, titleColumn);
    }

    // XXX: remove all calls to this constructor
    public LookupForeignKey(String pkColumnName)
    {
        this(null, (String) null, null, pkColumnName, null);
    }

    public void setPrefixColumnCaption(boolean prefix)
    {
        _prefixColumnCaption = prefix;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo table = getLookupTableInfo();
        if (displayField == null)
        {
            displayField = _titleColumn;
            if (displayField == null)
                displayField = table.getTitleColumn();
        }
        if (displayField == null)
            return null;
        if (table instanceof ContainerFilterable && parent.getParentTable().getContainerFilter() != null)
        {
            ContainerFilterable newTable = (ContainerFilterable)table;
            
            // Only override if the new table doesn't already have some special filter
            if (newTable.hasDefaultContainerFilter())
                newTable.setContainerFilter(new DelegatingContainerFilter(parent.getParentTable()));
        }
        return LookupColumn.create(parent, getPkColumn(table), table.getColumn(displayField), _prefixColumnCaption);
    }

    /**
     * Override this method if the primary key of the lookup table does not really exist.
     *
     * @param table
     * @return
     */
    protected ColumnInfo getPkColumn(TableInfo table)
    {
        return table.getColumn(_columnName);
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        if (_baseURL == null)
        {
            TableInfo lookupTable = getLookupTableInfo();
            if (lookupTable == null || _columnName == null)
            {
                return null;
            }
            return lookupTable.getDetailsURL(new CaseInsensitiveHashMap<ColumnInfo>(Collections.singletonMap(_columnName, parent)));
        }
        return new LookupURLExpression(_baseURL, Collections.singletonMap(_param, parent));
    }
}
