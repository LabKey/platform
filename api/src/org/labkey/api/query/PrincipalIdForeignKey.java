/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableInfo;

public class PrincipalIdForeignKey extends LookupForeignKey
{
    private final UserSchema _userSchema;

    static public ColumnInfo initColumn(ColumnInfo column)
    {
        column.setFk(new PrincipalIdForeignKey(column.getParentTable().getUserSchema()));
        column.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer(colInfo);
            }
        });
        return column;
    }


    public PrincipalIdForeignKey(UserSchema userSchema)
    {
        super("UserId", "Name");
        _userSchema = userSchema;
    }

    public TableInfo getLookupTableInfo()
    {
        TableInfo tinfoUsersData = CoreSchema.getInstance().getTableInfoPrincipals();
        FilteredTable ret = new FilteredTable<>(tinfoUsersData, _userSchema);
        ret.addWrapColumn(tinfoUsersData.getColumn("UserId"));
        ret.addColumn(ret.wrapColumn("Name", tinfoUsersData.getColumn("Name")));
        ret.setTitleColumn("Name");
        ret.setPublic(false);
        return ret;
    }
}
