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

package org.labkey.api.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;

public class UserIdForeignKey extends LookupForeignKey
{
    private final UserSchema _userSchema;

    static public <COL extends MutableColumnInfo> COL initColumn(COL column)
    {
        column.setFk(new UserIdForeignKey(column.getParentTable().getUserSchema()));
        column.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer(colInfo);
            }
        });
        return column;
    }


    public UserIdForeignKey(UserSchema userSchema)
    {
        super("UserId", "DisplayName");
        _userSchema = userSchema;
        setShowAsPublicDependency(false);
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        TableInfo tinfoUsersData = CoreSchema.getInstance().getTableInfoUsersData();
        FilteredTable ret = new FilteredTable<>(tinfoUsersData, _userSchema);
        ret.addWrapColumn(tinfoUsersData.getColumn("UserId"));
        ret.addColumn(ret.wrapColumn("DisplayName", tinfoUsersData.getColumn("DisplayName")));
        ret.setTitleColumn("DisplayName");
        ret.setPublic(false);
        return ret;
    }
}
