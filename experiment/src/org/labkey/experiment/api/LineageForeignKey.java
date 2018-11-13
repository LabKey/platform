/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MaterializedQueryHelper;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MultiValuedLookupColumn;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

            addLevelColumn("Data", "Data", () -> ExperimentService.get().getDataClasses(_userSchema.getContainer(), _userSchema.getUser(), true));
            addLevelColumn("Materials", "Material", () -> ExperimentService.get().getSampleSets(_userSchema.getContainer(), _userSchema.getUser(), true));
            addLevelColumn("Runs", "ExperimentRun", () -> ExperimentServiceImpl.get().getExpProtocolsForRunsInContainer(_userSchema.getContainer()));

            return this;
        }

        private ColumnInfo addLevelColumn(@NotNull String name, @NotNull String expType, @NotNull Supplier<List<? extends ExpObject>> items)
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
            col.setFk(new _MultiValuedForeignKey(parents, depth, expType, cpasType));

            return addColumn(col);
        }


        private class _MultiValuedForeignKey extends MultiValuedForeignKey
        {
            final boolean parents;
            final Integer depth;
            final String expType;
            final String cpasType;

            public _MultiValuedForeignKey(boolean parents, Integer depth, String expType, String cpasType)
            {
                super(new LookupForeignKey("self_lsid", "Name")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        SQLFragment lsids =  new SQLFragment("(SELECT lsid FROM ").append(_table.getFromSQL("qq")).append(")");
                        return new LineageTableInfo("Foo", _table.getUserSchema(), lsids, parents, depth, expType, cpasType);
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return super.getURL(parent, true);
                    }
                }, "lsid");

                this.parents = parents;
                this.depth = depth;
                this.expType = expType;
                this.cpasType = cpasType;
            }

            private _MultiValuedForeignKey(_MultiValuedForeignKey from, FieldKey parent, Map<FieldKey, FieldKey> mapping)
            {
                super(from, parent, mapping);
                parents = from.parents;
                depth = from.depth;
                expType = from.expType;
                cpasType = from.cpasType;
            }

            @Override
            public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
            {
                return new _MultiValuedForeignKey(this, parent, mapping);
            }

            @Override
            protected MultiValuedLookupColumn createMultiValuedLookupColumn(ColumnInfo lookupColumn, ColumnInfo parent, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
            {
                return new MultiValuedLookupColumn(parent, childKey, junctionKey, fk, lookupColumn)
                {
                    @Override
                    protected SQLFragment getLookupSql(TableInfo lookupTable, String alias)
                    {
                        // NOTE: We used to cache the lookup fk sql as a materialized query for performance
                        return super.getLookupSql(lookupTable, alias);
                    }
                };
            }
        }
    }

    static Cache<Path,MaterializedQueryHelper> materializedLineageQueries = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.HOUR, "lineage queries", null);


    private class ByTypeLineageForeignKey extends LookupForeignKey
    {
        private final @NotNull String _expType;
        private final @NotNull Supplier<List<? extends ExpObject>> _items;

        ByTypeLineageForeignKey(LineageForeignKeyLookupTable table, @NotNull String expType, @NotNull Supplier<List<? extends ExpObject>> items)
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
        private final @NotNull String _expType;
        private final @NotNull Supplier<List<? extends ExpObject>> _items;

        ByTypeLineageForeignKeyLookupTable(String name, UserSchema schema, @NotNull String expType, @NotNull Supplier<List<? extends ExpObject>> items)
        {
            super(name, schema);
            _expType = expType;
            _items = items;
        }

        protected TableInfo init()
        {
            addLineageColumn("All", _parents, null, _expType, null);
            // TODO: Nearest

            // First level children or parents
            // NOTE: We currently only add the LineageForeignKey to exp.Data and exp.Material tables
            // NOTE: so the first generation in the lineage will always be an experiment run.  To get
            // NOTE: the first data or material generation, we must skip the run generation -- hence depth of 2.
            // NOTE: If we ever add the magic Inputs and Outputs columns to the Runs table, we'll need to change the depths.
            int depth = _expType.equals("ExperimentRuns") ? 1 : 2;
            addLineageColumn("First", _parents, depth, _expType, null);

            for (ExpObject item : _items.get())
            {
                String lsid = item.getLSID();
                addLineageColumn(item.getName(), _parents, null, _expType, lsid);
            }

            return this;
        }
    }
}


