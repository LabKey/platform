/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;

public class PdLookupForeignKey extends AbstractForeignKey
{
    User _user;
    PropertyDescriptor _pd;
    public PdLookupForeignKey(User user, PropertyDescriptor pd)
    {
        _pd = pd;
        _user = user;
    }
    public TableInfo getLookupTableInfo()
    {
        if (_pd.getLookupSchema() == null || _pd.getLookupQuery() == null)
            return null;
        String containerId = _pd.getLookupContainer();
        Container container;
        if (containerId != null)
        {
            container = ContainerManager.getForId(containerId);
        }
        else
        {
            container = _pd.getContainer();
        }
        if (container == null)
            return null;
        if (!container.hasPermission(_user, ACL.PERM_READ))
            return null;
        QuerySchema qSchema = DefaultSchema.get(_user, container).getSchema(_pd.getLookupSchema());
        if (!(qSchema instanceof UserSchema))
        {
            return null;
        }
        UserSchema schema = (UserSchema) qSchema;
        TableInfo table = schema.getTable(_pd.getLookupQuery());
        if (table == null)
            return null;
        if (table.getPkColumns().size() != 1)
        {
            return null;
        }
        return table;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;
        if (displayField == null)
        {
            displayField = table.getTitleColumn();
        }
        if (displayField == null)
            return null;
        return LookupColumn.create(parent, table.getPkColumns().get(0), table.getColumn(displayField), true);
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return null;
        return lookupTable.getDetailsURL(Collections.singletonMap(lookupTable.getPkColumnNames().get(0), parent));
    }
}
