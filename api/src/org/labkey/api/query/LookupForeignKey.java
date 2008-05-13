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

package org.labkey.api.query;

import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;

abstract public class LookupForeignKey implements ForeignKey
{
    ActionURL _baseURL;
    Object _param;
    String _pkColumnName;
    private boolean _prefixColumnCaption = true;
    String _titleColumn;

    public LookupForeignKey(ActionURL baseURL, String paramName, String pkColumnName, String titleColumn)
    {
        this(pkColumnName);
        _baseURL = baseURL;
        _param = paramName;
        _titleColumn = titleColumn;
    }

    public LookupForeignKey(ActionURL baseURL, Enum paramName, String pkColumnName, String titleColumn)
    {
        this(pkColumnName);
        _baseURL = baseURL;
        _param = paramName;
        _titleColumn = titleColumn;
    }

    public LookupForeignKey(String pkColumnName, String titleColumn)
    {
         this(null, (String) null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(String pkColumnName)
    {
        _pkColumnName = pkColumnName;
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
        return table.getColumn(_pkColumnName);
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        if (_baseURL == null)
        {
            TableInfo lookupTable = getLookupTableInfo();
            if (lookupTable == null)
            {
                return null;
            }
            return lookupTable.getDetailsURL(Collections.singletonMap(_pkColumnName, parent));
        }
        return new LookupURLExpression(_baseURL, Collections.singletonMap(_param, parent));
    }
}
