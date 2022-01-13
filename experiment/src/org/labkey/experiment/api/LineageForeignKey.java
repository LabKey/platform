/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.BaseColumnInfo;
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
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * User: kevink
 * Date: 3/22/16
 */
class LineageForeignKey extends AbstractForeignKey
{
    private final boolean _useLineageDisplayColumn;
    private final ExpTableImpl _seedTable;
    private final SQLFragment _seedSql;
    private final UserSchema _userSchema;
    private final boolean _parents;

    /* generate a ForeignKey that returns a wrapper over objectid with a LineageDisplayColumn */
    public static LineageForeignKey createWithDisplayColumn(UserSchema schema, ExpTableImpl seedTable, boolean parents)
    {
        return new LineageForeignKey(schema, seedTable, null, parents, true);
    }

    /* generate a real MultiValued ForeignKey, use for one row at a time */
    public static LineageForeignKey createWithMultiValuedColumn(UserSchema schema, SQLFragment seedSql, boolean parents)
    {
        return new LineageForeignKey(schema, null, seedSql, parents, false);
    }

    protected LineageForeignKey(UserSchema schema, ExpTableImpl seedTable, SQLFragment seedSql, boolean parents, boolean useLineageDisplayColumn)
    {
        super(schema, null);
        _seedTable = seedTable;
        _seedSql = seedSql;
        _userSchema = schema;
        _parents = parents;
        this._useLineageDisplayColumn = useLineageDisplayColumn;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        Path cacheKey = new Path(this.getClass().getName(), (_useLineageDisplayColumn ? "LDC": "MVFK"), (_parents ? "Inputs" : "Outputs"));
        return _userSchema.getCachedLookupTableInfo(cacheKey.toString(), () ->
        {
            var ret = new LineageForeignKeyLookupTable(_parents ? "Inputs" : "Outputs", _userSchema, cacheKey).init();
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
                return SampleTypeService.get().getSampleTypes(s.getContainer(), s.getUser(), true);
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

    public ColumnInfo _createLookupColumn(ColumnInfo parent, TableInfo table, String displayField, boolean unselectable)
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

        // We want to create a placeholder column here that DOES NOT generate any joins,
        // that's why we extend AbstractForeignKey instead of LookupForeignKey.
        // CONSIDER: we could consider adding a "really don't add any joins" flag to LookupForeignKey for this pattern
        SQLFragment sql = parent.getValueSql(ExprColumn.STR_TABLE_ALIAS);

        // Issue 42873 - need to include parent as a dependency so that it can be resolved when we're not coming from
        // the base table of the query, but are instead being resolved through a lookup
        var col = new ExprColumn(parent.getParentTable(), new FieldKey(parent.getFieldKey(), displayField), sql, JdbcType.INTEGER, parent);
        col.setFk(lookup.getFk());
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setIsUnselectable(unselectable);
        col.setDisplayColumnFactory(lookup.getDisplayColumnFactory());
        return col;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        return _createLookupColumn(parent, getLookupTableInfo(), displayField, true);
    }

    public void applyDisplayColumn(BaseColumnInfo column, Integer depth, String expType, String cpasType, @Nullable String lookupColumnName)
    {
        // the users's FieldKey may not match the canonical FieldKey (say due to column renaming in queries)
        // this is the 'canonical' field key that the LineageDisplayColumn will use
        FieldKey lineageDisplayColumnFieldKey = FieldKey.fromParts(_parents?"Inputs":"Outputs");
        switch (StringUtils.trimToEmpty(expType))
        {
            case "Material":
            {
                lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, "Materials");
                if (depth != null  && depth != 0)
                    lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, "First");
                else
                {
                    var type = "All";
                    if (null != cpasType)
                    {
                        var ss = SampleTypeService.get().getSampleType(cpasType);
                        if (null != ss)
                            type = ss.getName();
                    }
                    lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, type);
                }
                break;
            }
            case "Data":
            {
                lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, "Data");
                if (depth != null  && depth != 0)
                    lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, "First");
                else
                {
                    var type = "All";
                    if (null != cpasType)
                    {
                        var dc = ExperimentServiceImpl.get().getDataClass(cpasType);
                        if (null != dc)
                            type = dc.getName();
                    }
                    lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, type);
                }
                break;
            }
            case "ExperimentRun":
            {
                lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, "Runs");
                if (depth != null  && depth != 0)
                    lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, "First");
                else
                {
                    var type = "All";
                    if (null != cpasType)
                    {
                        var protocol = ExperimentService.get().getExpProtocol(cpasType);
                        if (protocol != null)
                            type = protocol.getName();
                    }
                    lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, type);
                }
                break;
            }
            default:
            {
                lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, "All");
                break;
            }
        }
        if (null != lookupColumnName)
            lineageDisplayColumnFieldKey = new FieldKey(lineageDisplayColumnFieldKey, lookupColumnName);

        // We could add this DisplayColumnFactory in createLookupColumn(), but we have all the information
        // we need right here (parents,depth,expType,cpasType), so it's easier to construct it here, and
        // copy it in createLookupColumn()
        if (_useLineageDisplayColumn)
        {
            final FieldKey ldcfk = lineageDisplayColumnFieldKey;
            column.setDisplayColumnFactory(colInfo -> LineageDisplayColumn.create(_sourceSchema, colInfo, ldcfk));
        }
    }


    private class LineageForeignKeyLookupTable extends VirtualTable<UserSchema>
    {
        final private Path cacheKeyPrefix;

        LineageForeignKeyLookupTable(String name, UserSchema schema, Path cacheKeyPrefix)
        {
            super(schema.getDbSchema(), name, schema);
            this.cacheKeyPrefix = cacheKeyPrefix;
        }

        protected TableInfo init()
        {
            addLineageColumn("All", null, null, null, null, "Name");
            addLevelColumn(LevelColumnType.Data);
            addLevelColumn(LevelColumnType.Material);
            addLevelColumn(LevelColumnType.ExperimentRun);
            return this;
        }

        void addLevelColumn(@NotNull LevelColumnType level)
        {
            SQLFragment sql = new SQLFragment("'#ERROR'");
            var col = new ExprColumn(this, FieldKey.fromParts(level.columnName), sql, JdbcType.VARCHAR);
            col.setFk(new ByTypeLineageForeignKey(requireNonNull(getUserSchema()), level, cacheKeyPrefix));
            col.setUserEditable(false);
            col.setReadOnly(true);
            col.setIsUnselectable(true);
            applyDisplayColumn(col, 0, level.expType, null, null);
            addColumn(col);
        }

        void addLineageColumn(String name, Integer depth, String expType, String cpasType, String runProtocolLsid, String lookupColumnName)
        {
//            SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".objectid");
            SQLFragment sql = new SQLFragment("'#ERROR'");
            var col = new ExprColumn(this, FieldKey.fromParts(name), sql, JdbcType.INTEGER);
            col.setFk(new _MultiValuedForeignKey(cacheKeyPrefix, depth, expType, cpasType, runProtocolLsid));
            applyDisplayColumn(col, depth, expType, cpasType, lookupColumnName);
            addColumn(col);
        }
    }


    private class _MultiValuedForeignKey extends MultiValuedForeignKey
    {
        final Integer depth;
        final String expType;
        final String cpasType;

        public _MultiValuedForeignKey(Path cacheKeyPrefix, Integer depth, String expType, String cpasType, String runProtocolLsid)
        {
            super(new LookupForeignKey("self", "Name")
            {
                TableInfo _table = null;

                @Override
                public TableInfo getLookupTableInfo()
                {
                    if (null == _table)
                    {
                        Path cacheKey = cacheKeyPrefix.append(_MultiValuedForeignKey.class.getSimpleName(), String.valueOf(_parents), null==depth?"-":String.valueOf(depth), defaultString(expType,"-"), defaultString(cpasType,"-"));
                            _table = LineageForeignKey.this._userSchema.getCachedLookupTableInfo(cacheKey.toString(), () ->
                            {
                            SQLFragment objectids;
                            if (null != _seedSql)
                            {
                                objectids = new SQLFragment("(").append(_seedSql).append(")");
                            }
                            else
                            {
                                objectids = new SQLFragment("(SELECT objectid FROM ").append(_seedTable.getFromSQL("qq")).append(")");
                            }
                            var ret = new LineageTableInfo("LineageTableInfo (" + (_parents?"parents)":"children)"), _userSchema, objectids, _parents, depth, expType, cpasType, runProtocolLsid);
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
            }, "lsid"); // self is the seed objectid

            this.depth = depth;
            this.expType = expType;
            this.cpasType = cpasType;
        }

        private _MultiValuedForeignKey(_MultiValuedForeignKey from, FieldKey parent, Map<FieldKey, FieldKey> mapping)
        {
            super(from, parent, mapping);
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
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            if (_useLineageDisplayColumn)
            {
                FieldKey aliasFieldKey = new FieldKey(parent.getFieldKey(), StringUtils.defaultString(displayField,"Name"));
                var alias = new _AliasedParentColumn(parent, aliasFieldKey, depth, expType, cpasType, aliasFieldKey.getName());
                alias.clearFk();
                return alias;
            }
            else
            {
                return super.createLookupColumn(parent, displayField);
            }
        }

        /** JUST FOR BREAKPOINTS VVVV */

        @Override
        public ColumnInfo createJunctionLookupColumn(@NotNull ColumnInfo parent)
        {
            return super.createJunctionLookupColumn(parent);
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
            return _createLookupColumn(parent, getLookupTableInfo(), displayField, false);
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            if (null == _table)
            {
                Path cacheKey = _cacheKeyPrefix.append(getClass().getSimpleName(), _level.name());
                _table = _schema.getCachedLookupTableInfo(cacheKey.toString(), () ->
                {
                    var ret = new ByTypeLineageForeignKeyLookupTable("ByTypeLineageForeignKeyLookupTable (" + _level.expType + ")", _schema, cacheKey, _level.expType, ()->_level.getItems(_schema)).init();
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
            addLineageColumn("All", null, _expType, null, null, null);

            // First level children or parents
            // NOTE: When adding the LineageForeignKey to exp.Data and exp.Material tables
            // NOTE: the first generation in the lineage will always be an experiment run.  To get
            // NOTE: the first data or material generation, we must skip the run generation -- hence depth of 2.
            int depth = _expType.equals("ExperimentRuns") ? 1 : 2;
            addLineageColumn("First", depth, _expType, null, null, null);

            for (ExpObject item : _items.get())
            {
                String cpasType = item.getLSID();
                addLineageColumn(item.getName(), null, _expType, cpasType, null, null);
            }

            return this;
        }
    }

    // this is a wrapper column we return when we want to to attach a LineageDisplayColumn
    // same as AliasedColumn, but avoids getDisplayField() noise
    public class _AliasedParentColumn extends AliasedColumn
    {
        // lookupColumnName is for explictly selected lookup to column in target table (vs internal virtual column)
        public _AliasedParentColumn(ColumnInfo parent, FieldKey key, Integer depth, String expType, String cpasType, @Nullable String lookupColumnName)
        {
            super(parent.getParentTable(), key, parent, false);
            applyDisplayColumn(this, depth, expType, cpasType, lookupColumnName);
        }

        @Override
        public @Nullable ColumnInfo getDisplayField()
        {
            return null;
        }
    }
}


