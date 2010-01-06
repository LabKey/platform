package org.labkey.experiment.api;

import org.labkey.api.exp.query.ExpDataInputTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;

import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Jan 4, 2010
 */
public class ExpDataInputTableImpl extends ExpInputTableImpl<ExpDataInputTable.Column> implements ExpDataInputTable
{
    public ExpDataInputTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoDataInput(), schema);
    }

    public ColumnInfo createColumn(String alias, ExpDataInputTable.Column column)
    {
        switch (column)
        {
            case Data:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("DataId"));
                result.setFk(getExpSchema().getDataIdForeignKey());
                return result;
            }
            case Role:
                return wrapColumn(alias, _rootTable.getColumn("Role"));
            case TargetProtocolApplication:
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("TargetApplicationId"));
                result.setFk(getExpSchema().getProtocolApplicationForeignKey());
                return result;
            default:
                throw new IllegalArgumentException("Unsupported column: " + column);
        }
    }

    public void populate()
    {
        addColumn(Column.Data);
        addColumn(Column.TargetProtocolApplication);
        addColumn(Column.Role);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(Column.Data));
        defaultCols.add(FieldKey.fromParts(Column.Role));
        setDefaultVisibleColumns(defaultCols);
    }

}
