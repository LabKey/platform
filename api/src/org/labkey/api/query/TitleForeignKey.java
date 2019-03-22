/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;

/**
 * Class which is not really a foreign key to another table.
 * It's just used for a link on a column bound to rowid which displays
 * a different column from the same table.
 */
public class TitleForeignKey extends AbstractForeignKey
{
    ActionURL _baseURL;
    ColumnInfo _lookupKey;
    ColumnInfo _displayColumn;
    String _paramName;
    ContainerContext _cc;

    public TitleForeignKey(ActionURL baseURL, ColumnInfo lookupKey, ColumnInfo displayColumn, String paramName, ContainerContext cc)
    {
        _baseURL = baseURL;
        _lookupKey = lookupKey;
        _displayColumn = displayColumn;
        _paramName = paramName;
        _cc = cc;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField != null)
            return null;
        return LookupColumn.create(parent, _lookupKey, _displayColumn, false);
    }

    public TableInfo getLookupTableInfo()
    {
        return null;
    }

    public StringExpression getURL(ColumnInfo parent)
    {
        if (_baseURL == null)
            return null;
        DetailsURL d = new DetailsURL(_baseURL, _paramName, parent.getFieldKey());
        d.setContainerContext(_cc);
        return d;
    }
}
