/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.StringExpression;

/**
 * User: kevink
 * Date: 2/23/16
 */
public class LineageTableInfo extends VirtualTable
{
    private @NotNull SQLFragment _lsids;
    private boolean _parents;
    private @Nullable Integer _depth;
    private @Nullable String _expType;
    private @Nullable String _cpasType;
    private boolean _veryNewHotness;

    public LineageTableInfo(String name, @NotNull UserSchema schema, @NotNull SQLFragment lsids, boolean parents, @Nullable Integer depth, @Nullable String expType, @Nullable String cpasType, boolean veryNewHotness)
    {
        super(schema.getDbSchema(), name, schema);
        _lsids = lsids;
        _parents = parents;

        // depth is negative for parent values
        if (depth != null && depth > 0 && _parents)
            depth = -1 * depth;
        _depth = depth;
        _expType = expType;
        _cpasType = cpasType;
        _veryNewHotness = veryNewHotness;

        ColumnInfo selfLsid = new ColumnInfo(FieldKey.fromParts("self_lsid"), this, JdbcType.VARCHAR);
        selfLsid.setSqlTypeName("lsidtype");
        addColumn(selfLsid);

        if (veryNewHotness)
        {
            ColumnInfo selfRowId = new ColumnInfo(FieldKey.fromParts("self_rowid"), this, JdbcType.INTEGER);
            addColumn(selfRowId);
        }

        ColumnInfo depthCol = new ColumnInfo(FieldKey.fromParts("depth"), this, JdbcType.INTEGER);
        addColumn(depthCol);

        ColumnInfo parentContainer = new ColumnInfo(FieldKey.fromParts("container"), this, JdbcType.VARCHAR);
        parentContainer.setSqlTypeName("entityid");
        ContainerForeignKey.initColumn(parentContainer, schema);
        addColumn(parentContainer);

        ColumnInfo parentExpType = new ColumnInfo(FieldKey.fromParts("exptype"), this, JdbcType.VARCHAR);
        addColumn(parentExpType);

        ColumnInfo parentCpasType = new ColumnInfo(FieldKey.fromParts("cpastype"), this, JdbcType.VARCHAR);
        addColumn(parentCpasType);

        ColumnInfo parentName = new ColumnInfo(FieldKey.fromParts("name"), this, JdbcType.VARCHAR);
        addColumn(parentName);

        ColumnInfo parentLsid = new ColumnInfo(FieldKey.fromParts("lsid"), this, JdbcType.VARCHAR);
        parentLsid.setSqlTypeName("lsidtype");
        parentLsid.setFk(createLsidLookup(_expType, _cpasType));
        addColumn(parentLsid);

        ColumnInfo parentRowId = new ColumnInfo(FieldKey.fromParts("rowId"), this, JdbcType.INTEGER);
        //parentRowId.setFk(new QueryForeignKey("exp", schema.getContainer(), schema.getContainer(), schema.getUser(), "Materials", "rowId", "Name"));
        addColumn(parentRowId);
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

        return new LookupForeignKey("lsid") {
            @Override
            public TableInfo getLookupTableInfo() { return new NodesTableInfo(_userSchema, _veryNewHotness); }
        };
    }

    private ForeignKey createExpTypeFK(String expType)
    {
        switch (expType) {
            case "Data":
                return new QueryForeignKey("exp", _userSchema.getContainer(), null, _userSchema.getUser(), "Data", "LSID", "Name");
            case "Material":
                return new QueryForeignKey("exp", _userSchema.getContainer(), null, _userSchema.getUser(), "Materials", "LSID", "Name");
            case "ExperimentRun":
                return new QueryForeignKey("exp", _userSchema.getContainer(), null, _userSchema.getUser(), "Runs", "LSID", "Name");
            default:
                return null;
        }
    }

    private ForeignKey createCpasTypeFK(String cpasType)
    {
        ExpSampleSet ss = ExperimentService.get().getSampleSet(cpasType);
        if (ss != null)
        {
            return new LookupForeignKey("lsid", "Name")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    SamplesSchema samplesSchema = new SamplesSchema(_userSchema.getUser(), _userSchema.getContainer());
                    return samplesSchema.getSampleTable(ss);
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }
            };
        }

        ExpDataClass dc = ExperimentService.get().getDataClass(cpasType);
        if (dc != null)
        {
            return new LookupForeignKey("lsid", "Name")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    DataClassUserSchema dcus = new DataClassUserSchema(_userSchema.getContainer(), _userSchema.getUser());
                    return dcus.createTable(dc);
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }
            };
        }

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(cpasType);
        if (protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            return new LookupForeignKey("lsid", "Name")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    if (provider != null)
                    {
                        AssayProtocolSchema schema = provider.createProtocolSchema(_userSchema.getUser(), _userSchema.getContainer(), protocol, null);
                        if (schema != null)
                            return schema.createRunsTable();
                    }

                    return new ExpSchema(getUserSchema().getUser(), getUserSchema().getContainer()).getTable(ExpSchema.TableType.Runs.toString());
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
        ExpLineageOptions options = new ExpLineageOptions();
        options.setForLookup(true);
        options.setParents(_parents);
        options.setChildren(!_parents);
        options.setCpasType(_cpasType);
        options.setExpType(_expType);
        if (_depth != null)
            options.setDepth(_depth);
        options.setVeryNewHotness(_veryNewHotness);

        SQLFragment tree = ExperimentServiceImpl.get().generateExperimentTreeSQL(_lsids, options);

        String comment = String.format("<LineageTableInfo parents=%b, depth=%d, expType=%s, cpasType=%s>\n", _parents, _depth, _expType, _cpasType);

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
        private boolean _veryNewHotness;

        public NodesTableInfo(@Nullable UserSchema schema, boolean veryNewHotness)
        {
            super(schema.getDbSchema(), "Nodes", schema);
            _veryNewHotness = veryNewHotness;

            ColumnInfo containerCol = new ColumnInfo(FieldKey.fromParts("Container"), this, JdbcType.VARCHAR);
            containerCol.setSqlTypeName("entityid");
            ContainerForeignKey.initColumn(containerCol, schema);
            addColumn(containerCol);

            ColumnInfo name = new ColumnInfo(FieldKey.fromParts("name"), this, JdbcType.VARCHAR);
            name.setURL(DetailsURL.fromString("experiment/resolveLsid.view?lsid=${LSID}&type=${exptype}"));
            addColumn(name);

            ColumnInfo expType = new ColumnInfo(FieldKey.fromParts("exptype"), this, JdbcType.VARCHAR);
            addColumn(expType);

            ColumnInfo cpasType = new ColumnInfo(FieldKey.fromParts("cpastype"), this, JdbcType.VARCHAR);
            addColumn(cpasType);

            ColumnInfo lsid = new ColumnInfo(FieldKey.fromParts("lsid"), this, JdbcType.VARCHAR);
            lsid.setSqlTypeName("lsidtype");
            addColumn(lsid);

            ColumnInfo rowId = new ColumnInfo(FieldKey.fromParts("rowId"), this, JdbcType.INTEGER);
            addColumn(rowId);

        }

        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            // Attempt to use materialized nodes table
            if (!_veryNewHotness)
            {
                ExperimentServiceImpl impl = ExperimentServiceImpl.get();
                synchronized (impl.initEdgesLock)
                {
                    if (impl.materializedEdges != null)
                    {
                        SQLFragment temp = impl.materializedNodes.getFromSql(null, null);
                        return new SQLFragment("SELECT * FROM ").append(temp);
                    }
                }
            }

            // Fallback to giant union query
            SQLFragment sql = new SQLFragment();
            sql.append(
                    "SELECT container, CAST('Data' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.Data\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('Material' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.Material\n");
            if (!_veryNewHotness)
            {
                    sql.append("\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('ExperimentRun' AS VARCHAR(50)) AS exptype, CAST(NULL AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.ExperimentRun\n");
            }
            return sql;
        }
    }

}
