package org.labkey.experiment.api;

import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;

import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Jan 4, 2010
 */
public class ExpMaterialInputTableImpl extends ExpInputTableImpl<ExpMaterialInputTable.Column> implements ExpMaterialInputTable
{
    public ExpMaterialInputTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoMaterialInput(), schema);
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Material:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("MaterialId"));
                result.setFk(getExpSchema().getMaterialIdForeignKey());
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
        addColumn(Column.Material);
        addColumn(Column.TargetProtocolApplication);
        addColumn(Column.Role);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(Column.Material));
        defaultCols.add(FieldKey.fromParts(Column.Role));
        setDefaultVisibleColumns(defaultCols);
    }

}