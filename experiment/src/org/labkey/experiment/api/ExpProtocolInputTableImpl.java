package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;

public abstract class ExpProtocolInputTableImpl<C extends Enum> extends ExpTableImpl<C>
{
    protected ExpProtocolInputTableImpl(String name, TableInfo rootTable, UserSchema schema)
    {
        super(name, rootTable, schema, null);
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // We don't have our own Container column, so filter based on the Container column of this ProtocolInput's protocol
        FieldKey containerFK = FieldKey.fromParts("Container");
        clearConditions(containerFK);
        SQLFragment sqlFragment = new SQLFragment("(SELECT p.Container FROM ");
        sqlFragment.append(ExperimentServiceImpl.get().getTinfoProtocol(), "p");
        sqlFragment.append(" WHERE p.RowId = ProtocolId)");

        addCondition(getContainerFilter().getSQLFragment(getSchema(), sqlFragment, getContainer(), false), containerFK);
    }

    protected ColumnInfo createRowIdColumn(String alias)
    {
        ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("RowId"));
        col.setHidden(true);
        return col;
    }

    protected ColumnInfo createNameColumn(String alias)
    {
        return wrapColumn(alias, _rootTable.getColumn("Name"));
    }

    protected ColumnInfo createLsidColumn(String alias)
    {
        ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("LSID"));
        col.setHidden(true);
        col.setShownInInsertView(false);
        col.setShownInUpdateView(false);
        col.setUserEditable(false);
        col.setCalculated(true);
        return col;
    }

    protected ColumnInfo createInputColumn(String alias)
    {
        ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("input"));
        return col;
    }

    protected ColumnInfo createProtocolColumn(String alias)
    {
        ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("protocolId"));
        col.setFk(getExpSchema().getProtocolForeignKey("RowId"));
        return col;
    }

    protected ColumnInfo createSampleSetColumn(String alias)
    {
        ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("MaterialSourceId"));
        QueryForeignKey fk = new QueryForeignKey(ExpSchema.SCHEMA_NAME, getContainer(), null, getUserSchema().getUser(), ExpSchema.TableType.SampleSets.name(), "RowId", null);
        col.setFk(fk);
        return col;
    }

    protected ColumnInfo createDataClassColumn(String alias)
    {
        ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("DataClassId"));
        QueryForeignKey fk = new QueryForeignKey(ExpSchema.SCHEMA_NAME, getContainer(), null, getUserSchema().getUser(), ExpSchema.TableType.DataClasses.name(), "RowId", null);
        col.setFk(fk);
        return col;
    }

    protected ColumnInfo createMinOccursColumn(String alias)
    {
        return wrapColumn(alias, _rootTable.getColumn("MinOccurs"));
    }

    protected ColumnInfo createMaxOccursColumn(String alias)
    {
        return wrapColumn(alias, _rootTable.getColumn("MaxOccurs"));
    }

}
