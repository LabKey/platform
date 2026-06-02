/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.UserSchema;

import java.util.EnumSet;

public class WellGroupTypeTable extends EnumTableInfo<WellGroup.Type>
{
    public static final String NAME = "WellGroupType";

    public WellGroupTypeTable(UserSchema schema)
    {
        super(WellGroup.Type.class, schema, "All supported well group types", false);
        setName(NAME);
        setPublicName(NAME);
        setTitleColumn("Label");

        ExprColumn labelColumn = new ExprColumn(this, "Label", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Label"), JdbcType.VARCHAR);
        addColumn(labelColumn);
    }

    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        checkReadBeforeExecute();
        SQLFragment sql = new SQLFragment();
        String separator = "";
        EnumSet<WellGroup.Type> enumSet = EnumSet.allOf(_enum);
        for (WellGroup.Type e : enumSet)
        {
            sql.append(separator);
            separator = " UNION ";
            sql.append("SELECT ? AS Value, ? AS RowId, ? AS Label, ? AS Ordinal");
            sql.add(_valueGetter.getValue(e));
            sql.add(_rowIdGetter.getRowId(e));
            sql.add(e.getLabel());
            sql.add(e.ordinal());
        }
        return sql;
    }
}
