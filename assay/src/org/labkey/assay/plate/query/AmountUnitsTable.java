package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.UserSchema;

import java.util.EnumSet;

public class AmountUnitsTable extends EnumTableInfo<AmountUnitsTable.Units>
{
    public static final String NAME = "AmountUnits";

    enum Units
    {
        MICROLITERS ("µL"),
        MILLILITERS("mL"),
        LITERS("L");

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

    public AmountUnitsTable(UserSchema schema)
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
            sql.add(_valueGetter.getValue(e).toLowerCase().replace("_", " ")); // flag todo
            sql.add(e.ordinal());
        }
        return sql;
    }
}
