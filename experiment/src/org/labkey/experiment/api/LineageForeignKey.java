package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpression;

import java.util.List;
import java.util.function.Supplier;

/**
 * User: kevink
 * Date: 3/22/16
 */
class LineageForeignKey extends LookupForeignKey
{
    private final ExpTableImpl _table;
    private final UserSchema _schema;
    private final boolean _parents;

    LineageForeignKey(ExpTableImpl expTable, boolean parents)
    {
        super("lsid", "Name");
        _table = expTable;
        _schema = _table.getUserSchema();
        _parents = parents;
    }

    protected ColumnInfo getPkColumn(TableInfo table)
    {
        return _table.getLSIDColumn();
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        return new LineageForeignKeyLookupTable(_parents ? "Inputs" : "Outputs", _schema).init();
    }

    private class LineageForeignKeyLookupTable extends VirtualTable
    {
        LineageForeignKeyLookupTable(String name, UserSchema schema)
        {
            super(schema.getDbSchema(), name, schema);
        }
        
        protected TableInfo init()
        {
            addLineageColumn("All", _parents, null, null, null);
            //addLineageColumn("Nearest", _parents, null, null, null); // TODO: filter to the nearest parent.  Not sure if this is still needed

            //addLevelColumn("Runs", "Run", ...
            addLevelColumn("Data", "Data", () -> ExperimentService.get().getDataClasses(_userSchema.getContainer(), _userSchema.getUser(), true));
            addLevelColumn("Materials", "Material", () -> ExperimentService.get().getSampleSets(_userSchema.getContainer(), _userSchema.getUser(), true));

            return this;
        }

        private ColumnInfo addLevelColumn(String name, String expType, Supplier<List<? extends ExpObject>> items)
        {
            SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".lsid");
            ColumnInfo col = new ExprColumn(this, FieldKey.fromParts(name), sql, JdbcType.VARCHAR);
            col.setFk(new ByTypeLineageForeignKey(this, expType, items));
            col.setUserEditable(false);
            col.setReadOnly(true);
            col.setIsUnselectable(true);
            return addColumn(col);

        }

        ColumnInfo addLineageColumn(String name, boolean parents, Integer depth, String expType, String cpasType)
        {
            SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".lsid");
            ColumnInfo col = new ExprColumn(this, FieldKey.fromParts(name), sql, JdbcType.VARCHAR);
            col.setFk(new MultiValuedForeignKey(new LookupForeignKey("self_lsid", "Name")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    final UserSchema schema = _table.getUserSchema();
                    final SQLFragment lsids = new SQLFragment();
                    lsids.append("(SELECT qq.").append("lsid").append(" FROM ");
                    lsids.append(_table.getFromSQL("qq"));
                    lsids.append(")");

                    return new LineageTableInfo("Foo", schema, lsids, parents, depth, expType, cpasType);
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }

            }, _parents ? "parent_lsid" : "child_lsid"));

            return addColumn(col);
        }

    }


    private class ByTypeLineageForeignKey extends LookupForeignKey
    {
        private final String _expType;
        private final Supplier<List<? extends ExpObject>> _items;

        ByTypeLineageForeignKey(LineageForeignKeyLookupTable table, String expType, Supplier<List<? extends ExpObject>> items)
        {
            super("lsid", "Name");
            _expType = expType;
            _items = items;
        }

        protected ColumnInfo getPkColumn(TableInfo table)
        {
            return _table.getLSIDColumn();
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            return new ByTypeLineageForeignKeyLookupTable("Foo", _schema, _expType, _items).init();
        }

    }
    
    private class ByTypeLineageForeignKeyLookupTable extends LineageForeignKeyLookupTable
    {
        private final String _expType;
        private final Supplier<List<? extends ExpObject>> _items;

        ByTypeLineageForeignKeyLookupTable(String name, UserSchema schema, String expType, Supplier<List<? extends ExpObject>> items)
        {
            super(name, schema);
            _expType = expType;
            _items = items;
        }

        protected TableInfo init()
        {
            addLineageColumn("All", _parents, null, _expType, null);

            for (ExpObject item : _items.get())
            {
                String lsid = item.getLSID();
                addLineageColumn(item.getName(), _parents, null, _expType, lsid);
            }

            return this;
        }
    }
}


