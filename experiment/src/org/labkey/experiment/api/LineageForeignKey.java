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
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
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
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * User: kevink
 * Date: 3/22/16
 */
class LineageForeignKey extends AbstractForeignKey
{
    private final ExpTableImpl _seedTable;
    private final UserSchema _schema;
    private final boolean _parents;


    LineageForeignKey(UserSchema schema, ExpTableImpl seedTable, boolean parents)
    {
        super(schema, null);
        _seedTable = seedTable;
        _schema = schema;
        _parents = parents;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        Path cacheKey = new Path(this.getClass().getName(), _parents ? "Inputs" : "Outputs");
        return _schema.getCachedLookupTableInfo(cacheKey.toString(), () ->
        {
            var ret = new LineageForeignKeyLookupTable(_parents ? "Inputs" : "Outputs", _schema, cacheKey).init();
            ret.setLocked(true);
            return ret;
        });
    }

    enum LevelColumnType
    {
        Data("Data", "Data")
        {
            @Override
            List<? extends ExpObject> getItems(UserSchema s)
            {
                return ExperimentService.get().getDataClasses(s.getContainer(), s.getUser(), true);
            }
        },
        Material("Materials", "Material")
        {
            @Override
            List<? extends ExpObject> getItems(UserSchema s)
            {
                return ExperimentService.get().getSampleSets(s.getContainer(), s.getUser(), true);
            }
        },
        ExperimentRun("Runs", "ExperimentRun")
        {
            @Override
            List<? extends ExpObject> getItems(UserSchema s)
            {
                return ExperimentServiceImpl.get().getExpProtocolsForRunsInContainer(s.getContainer());
            }
        };

        final String columnName;
        final String expType;

        LevelColumnType(String name, String expType)
        {
            this.columnName = name;
            this.expType = expType;
        }

        abstract List<? extends ExpObject> getItems(UserSchema s);
    }

    public ColumnInfo _createLookupColumn(ColumnInfo parent, TableInfo table, String displayField)
    {
        if (table == null)
        {
            return null;
        }
        if (displayField == null)
        {
            displayField = _displayColumnName;
            if (displayField == null)
                displayField = table.getTitleColumn();
        }
        if (displayField == null)
            return null;

        var lookup = table.getColumn(displayField);
        if (null == lookup)
            return null;

        // We want to create a placeholder column here that DOES NOT add generate any joins
        // so that's why we extend AbstractForeignKey instead of LookupForeignKey.
        // I could "wrap" the lookup column and call its getValueSql() method, but I know what the expression is
        // and this column is not selectable anyway, so I'm just constructing a new ExprColumn
        // CONSIDER: we could consider adding a "really don't add any joins" flag to LookupForeignKey for this pattern
        SQLFragment sql = parent.getValueSql(ExprColumn.STR_TABLE_ALIAS);
        var col = new ExprColumn(parent.getParentTable(), FieldKey.fromParts(displayField), sql, JdbcType.VARCHAR);
        col.setFk(lookup.getFk());
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        return col;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        return _createLookupColumn(parent, getLookupTableInfo(), displayField);
    }

    private class LineageForeignKeyLookupTable extends VirtualTable
    {
        final private Path cacheKeyPrefix;

        LineageForeignKeyLookupTable(String name, UserSchema schema, Path cacheKeyPrefix)
        {
            super(schema.getDbSchema(), name, schema);
            this.cacheKeyPrefix = cacheKeyPrefix;
        }
        
        protected TableInfo init()
        {
            addLineageColumn("All", _parents, null, null, null);
            //addLineageColumn("Nearest", _parents, null, null, null); // TODO: filter to the nearest parent.  Not sure if this is still needed

            addLevelColumn(LevelColumnType.Data);
            addLevelColumn(LevelColumnType.Material);
            addLevelColumn(LevelColumnType.ExperimentRun);

            return this;
        }

        private ColumnInfo addLevelColumn(@NotNull LevelColumnType level)
        {
            SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".lsid");
            var col = new ExprColumn(this, FieldKey.fromParts(level.columnName), sql, JdbcType.VARCHAR);
            col.setFk(new ByTypeLineageForeignKey( getUserSchema(), level, cacheKeyPrefix ));
            col.setUserEditable(false);
            col.setReadOnly(true);
            col.setIsUnselectable(true);
            return addColumn(col);
        }

        ColumnInfo addLineageColumn(String name, boolean parents, Integer depth, String expType, String cpasType)
        {
            SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".lsid");
            var col = new ExprColumn(this, FieldKey.fromParts(name), sql, JdbcType.VARCHAR);
            col.setFk(new _MultiValuedForeignKey(cacheKeyPrefix, parents, depth, expType, cpasType));

            return addColumn(col);
        }


        private class _MultiValuedForeignKey extends MultiValuedForeignKey
        {
            final boolean parents;
            final Integer depth;
            final String expType;
            final String cpasType;

            public _MultiValuedForeignKey(Path cacheKeyPrefix, boolean parents, Integer depth, String expType, String cpasType)
            {
                super(new LookupForeignKey("self_lsid", "Name")
                {
                    TableInfo _table = null;

                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        if (null == _table)
                        {
                            Path cacheKey = cacheKeyPrefix.append(_MultiValuedForeignKey.class.getSimpleName(), String.valueOf(parents), null==depth?"-":String.valueOf(depth), defaultString(expType,"-"), defaultString(cpasType,"-"));
                            _table = LineageForeignKey.this._schema.getCachedLookupTableInfo(cacheKey.toString(), () ->
                            {
                                SQLFragment lsids =  new SQLFragment("(SELECT lsid FROM ").append(_seedTable.getFromSQL("qq")).append(")");
                                var ret = new LineageTableInfo("Foo", getUserSchema(), lsids, parents, depth, expType, cpasType);
                                ret.setLocked(true);
                                return ret;
                            });
                        }
                        return _table;
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


    private class ByTypeLineageForeignKey extends AbstractForeignKey
    {
        private final @NotNull LevelColumnType _level;
        private final @NotNull UserSchema _schema;
        private final Path _cacheKeyPrefix;
        private TableInfo _table;

        ByTypeLineageForeignKey( @NotNull UserSchema s, @NotNull LevelColumnType level, Path cacheKeyPrefix)
        {
            super(s, null);
            _schema = s;
            _level = level;
            _cacheKeyPrefix = cacheKeyPrefix;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            return _createLookupColumn(parent, getLookupTableInfo(), displayField);
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            if (null == _table)
            {
                Path cacheKey = _cacheKeyPrefix.append(getClass().getSimpleName(), _level.name());
                _table = _schema.getCachedLookupTableInfo(cacheKey.toString(), () ->
                {
                    var ret = new ByTypeLineageForeignKeyLookupTable("Foo", _schema, cacheKey, _level.expType, ()->_level.getItems(_schema)).init();
                    ret.setLocked(true);
                    return ret;
                });
            }
            return _table;
        }
    }


    private class ByTypeLineageForeignKeyLookupTable extends LineageForeignKeyLookupTable
    {
        private final @NotNull String _expType;
        private final @NotNull Supplier<List<? extends ExpObject>> _items;

        ByTypeLineageForeignKeyLookupTable(String name, UserSchema schema, Path cacheKey, @NotNull String expType, @NotNull Supplier<List<? extends ExpObject>> items)
        {
            super(name, schema, cacheKey);
            _expType = expType;
            _items = items;
        }

        @Override
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


