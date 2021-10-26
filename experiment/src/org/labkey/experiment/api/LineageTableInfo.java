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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.util.StringExpression;

/**
 * User: kevink
 * Date: 2/23/16
 */
public class LineageTableInfo extends VirtualTable
{
    private @NotNull
    final SQLFragment _objectids;
    private final boolean _parents;
    private @Nullable
    final Integer _depth;
    private @Nullable
    final String _expType;
    private @Nullable
    final String _cpasType;
    private @Nullable
    final String _runProtocolLsid;

    public LineageTableInfo(String name, @NotNull UserSchema schema, @NotNull SQLFragment objectids, boolean parents, @Nullable Integer depth, @Nullable String expType, @Nullable String cpasType, @Nullable String runProtocolLsid)
    {
        super(schema.getDbSchema(), name, schema);
        _objectids = objectids;
        _parents = parents;

        // depth is negative for parent values
        if (depth != null && depth > 0 && _parents)
            depth = -1 * depth;
        _depth = depth;
        _expType = expType;
        _cpasType = cpasType;
        _runProtocolLsid = runProtocolLsid;

        var self = new BaseColumnInfo(FieldKey.fromParts("self"), this, JdbcType.INTEGER);
        addColumn(self);

        var selfRowId = new BaseColumnInfo(FieldKey.fromParts("self_rowid"), this, JdbcType.INTEGER);
        addColumn(selfRowId);

        var depthCol = new BaseColumnInfo(FieldKey.fromParts("depth"), this, JdbcType.INTEGER);
        addColumn(depthCol);

        var parentContainer = new BaseColumnInfo(FieldKey.fromParts("container"), this, JdbcType.VARCHAR);
        parentContainer.setSqlTypeName("entityid");
        parentContainer.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
        addColumn(parentContainer);

        var parentExpType = new BaseColumnInfo(FieldKey.fromParts("exptype"), this, JdbcType.VARCHAR);
        addColumn(parentExpType);

        var parentCpasType = new BaseColumnInfo(FieldKey.fromParts("cpastype"), this, JdbcType.VARCHAR);
        addColumn(parentCpasType);

        var parentRunProtocolLsid = new BaseColumnInfo(FieldKey.fromParts("runProtocolLsid"), this, JdbcType.VARCHAR);
        addColumn(parentRunProtocolLsid);

        var parentName = new BaseColumnInfo(FieldKey.fromParts("name"), this, JdbcType.VARCHAR);
        addColumn(parentName);

        var parentLsid = new BaseColumnInfo(FieldKey.fromParts("lsid"), this, JdbcType.VARCHAR);
        parentLsid.setSqlTypeName("lsidtype");
        parentLsid.setFk(createLsidLookup(_expType, _cpasType));
        addColumn(parentLsid);

        var parentRowId = new BaseColumnInfo(FieldKey.fromParts("rowId"), this, JdbcType.INTEGER);
        //parentRowId.setFk(new QueryForeignKey("exp", schema.getContainer(), schema.getContainer(), schema.getUser(), "Materials", "rowId", "Name"));
        addColumn(parentRowId);

        setTitleColumn("Name");
    }

    private ForeignKey createLsidLookup(String expType, String cpasType)
    {
        ForeignKey fk = null;
        if (cpasType != null)
            fk = createCpasTypeFK(cpasType);
        else if (expType != null)
            fk = createExpTypeFK(expType);

        if (fk != null)
            return fk;

        return new LookupForeignKey("lsid")
        {
            TableInfo _table = null;

            @Override
            public TableInfo getLookupTableInfo()
            {
                if (null == _table)
                    _table = getUserSchema().getCachedLookupTableInfo( this.getClass().getName()+"/NodesTable", () ->
                    {
                        var t = new NodesTableInfo(_userSchema);
                        t.setLocked(true);
                        return t;
                    });
                return _table;
            }
        };
    }

    private ForeignKey createExpTypeFK(String expType)
    {
        switch (expType)
        {
            case "Data":
                return QueryForeignKey.from(getUserSchema(), null).schema("exp").to("Data", "LSID", "Name").build();
            case "Material":
                return QueryForeignKey.from(getUserSchema(), null).schema("exp").to("Materials", "LSID", "Name").build();
            case "ExperimentRun":
                return QueryForeignKey.from(getUserSchema(), null).schema("exp").to("Runs", "LSID", "Name").build();
            default:
                return null;
        }
    }

    private ForeignKey createCpasTypeFK(String cpasType)
    {
        // TODO: check in scope and has permission
        ExpSampleType st = SampleTypeService.get().getSampleType(cpasType);
        if (st != null)
        {
            return new LookupForeignKey("lsid", "Name")
            {
                TableInfo _table = null;

                @Override
                public TableInfo getLookupTableInfo()
                {
                    if (null == _table)
                        _table = getUserSchema().getCachedLookupTableInfo(getClass().getName() + "/Samples/" + st.getRowId() + "/" + st.getName(), () ->
                        {
                            SamplesSchema samplesSchema = new SamplesSchema(_userSchema);
                            return samplesSchema.getTable(st, null);
                        });
                    return _table;
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }
            };
        }


        // TODO: check in scope and has permission
        ExpDataClass dc = ExperimentServiceImpl.get().getDataClass(cpasType);
        if (dc != null)
        {
            return new LookupForeignKey(getContainerFilter(), "lsid", "Name")
            {
                TableInfo _table = null;

                @Override
                public TableInfo getLookupTableInfo()
                {
                    if (null == _table)
                        _table = getUserSchema().getCachedLookupTableInfo(getClass().getName() + "/DataClass/" + dc.getRowId() + "/" + dc.getName(), () ->
                        {
                            DataClassUserSchema dcus = new DataClassUserSchema(_userSchema.getContainer(), _userSchema.getUser());
                            return dcus.getTable(dc.getName(), getLookupContainerFilter());
                        });
                    return _table;
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }
            };
        }

        // TODO: check in scope and has permission
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(cpasType);
        if (protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            return new LookupForeignKey("lsid", "Name")
            {
                TableInfo _table;

                @Override
                public TableInfo getLookupTableInfo()
                {
                    if (null == _table)
                        _table = getUserSchema().getCachedLookupTableInfo(getClass().getName() + "/Runs/" + protocol.getRowId() + "/" + protocol.getName(), () ->
                        {
                            if (provider != null)
                            {
                                AssayProtocolSchema schema = provider.createProtocolSchema(_userSchema.getUser(), _userSchema.getContainer(), protocol, null);
                                if (schema != null)
                                {
                                    var runsTable = schema.createRunsTable(null);
                                    runsTable.setLocked(true);
                                    return runsTable;
                                }
                            }
                            var ret = new ExpSchema(getUserSchema().getUser(), getUserSchema().getContainer()).getTable(ExpSchema.TableType.Runs.toString(), null);
                            assert null != ret;
                            ret.setLocked(true);
                            return ret;
                        });
                    return _table;
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }
            };
        }

        return null;
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL()
    {
        checkReadBeforeExecute();
        ExpLineageOptions options = new ExpLineageOptions();
        options.setForLookup(true);
        options.setParents(_parents);
        options.setChildren(!_parents);
        options.setCpasType(_cpasType);
        options.setExpType(_expType);
        options.setRunProtocolLsid(_runProtocolLsid);
        if (_depth != null)
            options.setDepth(_depth);

        options.setUseObjectIds(true);
        SQLFragment tree = ExperimentServiceImpl.get().generateExperimentTreeSQL(_objectids, options);

        String comment = String.format("<LineageTableInfo parents=%b, depth=%d, expType=%s, cpasType=%s, runProtocolLsid=%s>\n", _parents, _depth, _expType, _cpasType, _runProtocolLsid);

        SQLFragment sql = new SQLFragment();
        sql.appendComment(comment, getSqlDialect());
        sql.append(tree);
        sql.appendComment("</LineageTableInfo>\n", getSqlDialect());

        return sql;
    }

    /**
     * Union of all Data, Material, and ExperimentRun rows for use as a generic lookup target.
     */
    private static class NodesTableInfo extends VirtualTable
    {
        public NodesTableInfo(@Nullable UserSchema schema)
        {
            super(schema.getDbSchema(), "Nodes", schema);

            var containerCol = new BaseColumnInfo(FieldKey.fromParts("Container"), this, JdbcType.VARCHAR);
            containerCol.setSqlTypeName("entityid");
            containerCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
            addColumn(containerCol);

            var name = new BaseColumnInfo(FieldKey.fromParts("name"), this, JdbcType.VARCHAR);
            name.setURL(DetailsURL.fromString("experiment/resolveLsid.view?lsid=${LSID}&type=${exptype}"));
            addColumn(name);

            var expType = new BaseColumnInfo(FieldKey.fromParts("exptype"), this, JdbcType.VARCHAR);
            addColumn(expType);

            var cpasType = new BaseColumnInfo(FieldKey.fromParts("cpastype"), this, JdbcType.VARCHAR);
            addColumn(cpasType);

            var runProtocolLsid = new BaseColumnInfo(FieldKey.fromParts("runProtocolLsid"), this, JdbcType.VARCHAR);
            addColumn(runProtocolLsid);

            var lsid = new BaseColumnInfo(FieldKey.fromParts("lsid"), this, JdbcType.VARCHAR);
            lsid.setSqlTypeName("lsidtype");
            addColumn(lsid);

            var rowId = new BaseColumnInfo(FieldKey.fromParts("rowId"), this, JdbcType.INTEGER);
            addColumn(rowId);

        }

        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            checkReadBeforeExecute();
            SQLFragment sql = new SQLFragment();
            sql.append(
                    "SELECT container, CAST('Data' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid, CAST(NULL AS VARCHAR(200)) AS RunProtocolLsid\n" +
                    "FROM exp.Data\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('Material' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid, CAST(NULL AS VARCHAR(200)) AS RunProtocolLsid\n" +
                    "FROM exp.Material\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('ExperimentRun' AS VARCHAR(50)) AS exptype, CAST(NULL AS VARCHAR(200)) AS cpastype, name, lsid, rowid, protocolLsid AS RunProtocolLsid\n" +
                    "FROM exp.ExperimentRun\n");
            return sql;
        }
    }

}
