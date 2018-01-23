package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;

public abstract class ExpProtocolOutputTableImpl<C extends Enum> extends ExpTableImpl<C>
{
    protected ExpProtocolOutputTableImpl(String name, TableInfo rootTable, UserSchema schema, @Nullable ExpObjectImpl objectType)
    {
        super(name, rootTable, schema, objectType);
    }

    /**
     * Create a column with a lookup to MaterialInput that is joined by materialId
     * and a targetProtocolApplication provided by the <code>protocolApplication</code> column.
     */
    protected ColumnInfo createEdgeColumn(String alias, C protocolAppColumn, ExpSchema.TableType lookupTable)
    {
        ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("rowId"));
        LookupForeignKey fk = new LookupForeignKey()
        {
            @Override
            public @Nullable TableInfo getLookupTableInfo()
            {
                UserSchema schema = getUserSchema();
                ExpSchema expSchema = new ExpSchema(schema.getUser(), schema.getContainer());
                return expSchema.getTable(lookupTable);
            }
        };
        fk.addJoin(FieldKey.fromParts(protocolAppColumn.name()), ExpMaterialInputTable.Column.TargetProtocolApplication.name(), false);
        col.setFk(fk);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setHidden(true);
        return col;
    }


    protected ColumnInfo createLineageColumn(ExpTableImpl table, String alias, boolean inputs)
    {
        ColumnInfo ret = table.wrapColumn(alias, table.getRealTable().getColumn("LSID"));
        ret.setFk(new LineageForeignKey(table, inputs));
        ret.setCalculated(true);
        ret.setUserEditable(false);
        ret.setReadOnly(true);
        ret.setShownInDetailsView(false);
        ret.setShownInInsertView(false);
        ret.setShownInUpdateView(false);
        ret.setIsUnselectable(true);
        ret.setHidden(true);
        return ret;
    }

}
