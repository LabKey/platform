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
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.UserSchema;

import java.util.EnumSet;

public class ConcentrationUnitsTable extends EnumTableInfo<ConcentrationUnitsTable.Units>
{
    public static final String NAME = "ConcentrationUnits";

    enum Units
    {
        MICROMOLAR("µM"),
        MILLIMOLAR("mM"),
        NANOMOLAR("nM"),
        PICOMOLAR("pM"),
        MILIGRAMS_PER_MILLILITER("mg/mL"),
        MICROGRAMS_PER_MILLILITER("µg/mL"),
        MICROGRAMS_PER_MICROLITER("µg/µL"),
        NANOGRAMS_PER_MICROLITER("ng/µL");

        private final String _label;

        Units(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }
    }

    public ConcentrationUnitsTable(UserSchema schema)
    {
        super(Units.class, schema, "Supported Concentration Units", false);
        setName(NAME);
        setPublicName(NAME);
        setTitleColumn("Value");

        ExprColumn labelColumn = new ExprColumn(this, "Label", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Label"), JdbcType.VARCHAR);
        addColumn(labelColumn);
    }

    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        checkReadBeforeExecute();
        SQLFragment sql = new SQLFragment();
        String separator = "";
        EnumSet<Units> enumSet = EnumSet.allOf(_enum);
        for (Units e : enumSet)
        {
            sql.append(separator);
            separator = " UNION ";
            sql.append("SELECT ? AS Value, ? AS RowId, ? AS Label, ? AS Ordinal");
            sql.add(e.getLabel());
            sql.add(_rowIdGetter.getRowId(e));
            sql.add(_valueGetter.getValue(e).toLowerCase().replace("_", " "));
            sql.add(e.ordinal());
        }
        return sql;
    }
}
