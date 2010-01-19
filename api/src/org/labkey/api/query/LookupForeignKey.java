/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;

abstract public class LookupForeignKey extends AbstractForeignKey
{
    ActionURL _baseURL;
    Object _param;
    private boolean _prefixColumnCaption = true;
    String _titleColumn;
    private boolean _joinOnContainer = false;

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

    public LookupForeignKey(ActionURL baseURL, String paramName, String pkColumnName, String titleColumn)
    {
        this(baseURL, paramName, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(ActionURL baseURL, Enum paramName, String pkColumnName, String titleColumn)
    {
        this(baseURL, paramName, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(String tableName, String pkColumnName, String titleColumn)
    {
         this(null, (String) null, tableName, pkColumnName, titleColumn);
    }

    public LookupForeignKey(String pkColumnName, String titleColumn)
    {
         this(null, (String) null, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(String pkColumnName)
    {
        this(null, (String) null, null, pkColumnName, null);
    }

    public void setPrefixColumnCaption(boolean prefix)
    {
        _prefixColumnCaption = prefix;
    }

    public boolean isJoinOnContainer()
    {
        return _joinOnContainer;
    }

    public void setJoinOnContainer(boolean joinOnContainer)
    {
        _joinOnContainer = joinOnContainer;
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
        LookupColumn result = LookupColumn.create(parent, getPkColumn(table), table.getColumn(displayField), _prefixColumnCaption);
        if (result != null)
        {
            result.setJoinOnContainer(_joinOnContainer);
        }
        return result;
    }

    /**
     * Override this method if the primary key of the lookup table does not really exist.
     */
    protected ColumnInfo getPkColumn(TableInfo table)
    {
        return table.getColumn(_columnName);
    }


    public StringExpression getURL(ColumnInfo parent)
    {
        return getURL(parent, false);
    }


    protected StringExpression getURL(ColumnInfo parent, boolean useDetailsURL)
    {
        if (null != _baseURL)
            return new DetailsURL(_baseURL, _param.toString(), parent.getFieldKey());

        if (!useDetailsURL)
            return null;

        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null || _columnName == null)
            return null;

        return getDetailsURL(parent, lookupTable, _columnName);
    }


    public static StringExpression getDetailsURL(ColumnInfo parent, TableInfo lookupTable, String columnName)
    {
        FieldKey columnKey = new FieldKey(null,columnName);

        StringExpression expr = lookupTable.getDetailsURL(Collections.singleton(columnKey), null);
        if (expr instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            StringExpressionFactory.FieldKeyStringExpression f = (StringExpressionFactory.FieldKeyStringExpression)expr;
            StringExpressionFactory.FieldKeyStringExpression rewrite;

            // if the URL only substitutes the PK we can rewrite as FK (does the DisplayColumn handle when the join fails?)
            if (f.validateFieldKeys(Collections.singleton(columnKey)))
                rewrite = f.addParent(null, Collections.singletonMap(columnKey, parent.getFieldKey()));
            else
                rewrite = f.addParent(parent.getFieldKey(), null);
            return rewrite;
        }
        return null;
    }
}
