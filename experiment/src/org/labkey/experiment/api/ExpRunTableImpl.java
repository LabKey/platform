/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.query.ExpRunGroupMapTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.exp.ExperimentMembershipDisplayColumnFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ExpRunTableImpl extends ExpTableImpl<ExpRunTable.Column> implements ExpRunTable
{
    ExpProtocol _protocol;
    ExpExperiment _experiment;

    private ExpMaterial _inputMaterial;
    private ExpData _inputData;

    public ExpRunTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoExperimentRun(), schema, cf);
        Table.checkAllColumns(this, getColumns(), "ExpRunTableImpl");
    }

    @Override
    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    @Override
    public void setProtocol(ExpProtocol protocol)
    {
        checkLocked();
        if (_protocol != null)
            throw new IllegalStateException("Cannot unset protocol");
        _protocol = protocol;
        if (_protocol != null)
        {
            addCondition(_rootTable.getColumn("ProtocolLSID"), protocol.getLSID());
        }
    }

    @Override
    public void setProtocolPatterns(String... patterns)
    {
        setFilterPatterns("ProtocolLSID", patterns);
    }

    @Override
    public ExpExperiment getExperiment()
    {
        return _experiment;
    }

    @Override
    public void setInputMaterial(ExpMaterial material)
    {
        checkLocked();
        if (_inputMaterial != null)
        {
            throw new IllegalArgumentException("Cannot unset source material");
        }
        if (material == null)
        {
            return;
        }
        _inputMaterial = material;
        addCondition(new SQLFragment( " LSID IN " +
            "(SELECT RunLSID FROM " + ExperimentServiceImpl.get().getTinfoExperimentRunMaterialInputs() + " WHERE RowId = ?)", _inputMaterial.getRowId()));
    }

    @Override
    public ExpMaterial getInputMaterial()
    {
        return _inputMaterial;
    }

    @Override
    public void setInputData(ExpData data)
    {
        checkLocked();
        if (_inputData != null)
        {
            throw new IllegalArgumentException("Cannot unset input data");
        }
        if (data == null)
        {
            return;
        }
        _inputData = data;
        addCondition(new SQLFragment( " LSID IN " +
            "(SELECT RunLSID FROM " + ExperimentServiceImpl.get().getTinfoExperimentRunDataInputs() + " WHERE RowId = ?)", _inputData.getRowId()));

    }

    @Override
    public ExpData getInputData()
    {
        return _inputData;
    }

    @Override
    public void setRuns(List<ExpRun> runs)
    {
        checkLocked();
        if (runs.isEmpty())
        {
            addCondition(new SQLFragment("1 = 2"));
        }
        else
        {
            SQLFragment sql = new SQLFragment();
            //sql.append(ExperimentServiceImpl.get().getTinfoExperimentRun());
            sql.append("RowID IN (");
            String separator = "";
            for (ExpRun run : runs)
            {
                sql.append(separator);
                separator = ", ";
                sql.append(run.getRowId());
            }
            sql.append(")");
            addCondition(sql);
        }
    }

    @Override
    public void setExperiment(ExpExperiment experiment)
    {
        checkLocked();
        if (_experiment != null)
            throw new IllegalArgumentException("Cannot unset experiment");
        if (experiment == null)
            return;
        _experiment = experiment;
        addCondition(new SQLFragment(" RowId IN ( SELECT ExperimentRunId FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " "
                    +  " WHERE ExperimentId = " +  experiment.getRowId() + " ) "));

        if (_userSchema.getContainer().equals(ContainerManager.getSharedContainer()))
        {
            // If we're in the /Shared project, look everywhere
            setContainerFilter(new ContainerFilter.AllFolders(_userSchema.getUser()));
        }
        else if (getContainer().isProject())
        {
            // If we're in a project, look in subfolders
            setContainerFilter(ContainerFilter.Type.CurrentAndSubfolders.create(_userSchema));
        }
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Comments:
                return wrapColumn(alias, _rootTable.getColumn("Comments"));
            case Folder:
                var containerColumn = wrapColumn(alias, _rootTable.getColumn("Container"));
                containerColumn.setUserEditable(false);
                containerColumn.setShownInInsertView(false);
                containerColumn.setShownInUpdateView(false);
                return containerColumn;
            case Created:
                var createdCol = wrapColumn(alias, _rootTable.getColumn("Created"));
                createdCol.setUserEditable(false);
                createdCol.setShownInInsertView(false);
                createdCol.setShownInUpdateView(false);
                return createdCol;
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case FilePathRoot:
                var filePathRootColumn = wrapColumn(alias, _rootTable.getColumn("FilePathRoot"));
                filePathRootColumn.setUserEditable(false);
                filePathRootColumn.setShownInInsertView(false);
                filePathRootColumn.setShownInUpdateView(false);
                return filePathRootColumn;
            case LSID:
                var lsidColumn = wrapColumn(alias, _rootTable.getColumn("LSID"));
                lsidColumn.setShownInInsertView(false);
                lsidColumn.setShownInUpdateView(false);
                lsidColumn.setShownInDetailsView(false);
                lsidColumn.setUserEditable(false);
                return lsidColumn;
            case Modified:
                var modifiedCol = wrapColumn(alias, _rootTable.getColumn("Modified"));
                modifiedCol.setUserEditable(false);
                modifiedCol.setShownInInsertView(false);
                modifiedCol.setShownInUpdateView(false);
                return modifiedCol;
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Protocol:
                var protocolColumn = wrapColumn(alias, _rootTable.getColumn("ProtocolLSID"));
                protocolColumn.setUserEditable(false);
                protocolColumn.setShownInInsertView(false);
                protocolColumn.setShownInUpdateView(false);
                return protocolColumn;
            case ProtocolStep:
            {
                SQLFragment sql = new SQLFragment("(SELECT MIN(exp.Protocol.Name) FROM exp.Protocol " +
                        "\nINNER JOIN exp.ProtocolApplication ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID" +
                        "\nWHERE exp.ProtocolApplication.CpasType = 'ProtocolApplication' AND exp.ProtocolApplication.RunId = " +
                        ExprColumn.STR_TABLE_ALIAS + ".RowId)");

                return new ExprColumn(this, alias, sql, JdbcType.VARCHAR);
            }
            case RowId:
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                if (getPkColumns().isEmpty())
                    ret.setKeyField(true);
                ret.setSortDirection(Sort.SortDirection.DESC);
                ret.setFk(new RowIdForeignKey(ret));
                ret.setHidden(true);
                ret.setURL(DetailsURL.fromString("/experiment/showRunText.view?rowId=${rowid}"));
                return ret;
            }
            case Flag:
                return createFlagColumn(alias);
            case Links:
            {
                var result = wrapColumn("Links", _rootTable.getColumn("RowId"));
                result.setDescription("Link to the run's graph");
                result.setShownInUpdateView(false);
                result.setShownInInsertView(false);
                result.setDisplayColumnFactory(RunGraphDisplayColumn::new);
                return result;
            }
            case RunGroupToggle:
                var toggleCol = wrapColumn(alias, _rootTable.getColumn("RowId"));
                toggleCol.setKeyField(false);
                toggleCol.setDescription("A lookup to individual columns that show if the run is a member of each run group that's in scope");
                toggleCol.setTextAlign("left");
                toggleCol.setIsUnselectable(true);
                final ExperimentsForeignKey fk = new ExperimentsForeignKey();
                toggleCol.setFk(fk);
                toggleCol.setDisplayColumnFactory(colInfo -> new RunGroupListDisplayColumn(colInfo, fk));
                toggleCol.setShownInInsertView(false);
                toggleCol.setShownInUpdateView(false);
                return toggleCol;
            case RunGroups:
                var col = wrapColumn(alias, _rootTable.getColumn("RowId"));
                col.setKeyField(false);
                col.setShownInInsertView(false);
                col.setShownInUpdateView(false);
                col.setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
                col.setFk(new MultiValuedForeignKey(new LookupForeignKey(getContainerFilter(), ExpRunGroupMapTable.Column.Run.toString(), null)
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        ContainerFilter cf = getLookupContainerFilter();
                        UserSchema schema = getUserSchema();
                        String key = getClass().getName() + "/RunGroups.RowId.fk/" + cf.getCacheKey();
                        // since getTable(forWrite=true) does not cache, cache this tableinfo using getCachedLookupTableInfo()
                        return schema.getCachedLookupTableInfo(key, this::createLookupTableInfo);
                    }

                    private TableInfo createLookupTableInfo()
                    {
                        // TODO ContainerFilter: getLookupTableInfo() should not mutate table
                        // for now use forWrite==true to get mutable tableinfo
                        ExpTable<ExpRunGroupMapTable.Column> result = (ExpTable)getExpSchema().getTable(ExpSchema.TableType.RunGroupMap.name(), getLookupContainerFilter(), true, true);
                        result.getMutableColumn(ExpRunGroupMapTable.Column.RunGroup).setFk(getExpSchema().getRunGroupIdForeignKey(getContainerFilter(), false));
                        result.setLocked(true);
                        return result;
                    }
                }, ExpRunGroupMapTable.Column.RunGroup.toString()));
                return col;
            case Input:
                return createInputLookupColumn();
            case Output:
                return createOutputLookupColumn();
            case DataInputs:
                return createDataInputsColumn(alias);
            case DataOutputs:
                return createDataOutputsColumn(alias);
            case JobId:
                var ret = wrapColumn(alias, _rootTable.getColumn("JobId"));
                ret.setLabel("Job");
                ret.setUserEditable(false);
                ret.setShownInInsertView(false);
                ret.setShownInUpdateView(false);
                return ret;
            case ReplacedByRun:
                var replacedByRunCol = wrapColumn(alias, _rootTable.getColumn("ReplacedByRunId"));
                replacedByRunCol.setFk(getExpSchema().getRunIdForeignKey(getContainerFilter()));
                replacedByRunCol.setLabel("Replaced By");
                replacedByRunCol.setShownInInsertView(false);
                replacedByRunCol.setShownInUpdateView(false);
                return replacedByRunCol;
            case Replaced:
                SQLFragment replacedSQL = new SQLFragment("CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".ReplacedByRunId IS NULL THEN ? ELSE ? END");
                replacedSQL.add(false);
                replacedSQL.add(true);
                var replacedCol = new ExprColumn(this, alias, replacedSQL, JdbcType.BOOLEAN);
                replacedCol.setHidden(true);
                replacedCol.setShownInInsertView(false);
                replacedCol.setShownInUpdateView(false);
                return replacedCol;
            case ReplacesRun:
                SQLFragment replacesSQL = new SQLFragment("(SELECT MIN(er.RowId) FROM ");
                replacesSQL.append(ExperimentServiceImpl.get().getTinfoExperimentRun(), "er");
                replacesSQL.append(" WHERE er.ReplacedByRunId = ");
                replacesSQL.append(ExprColumn.STR_TABLE_ALIAS);
                replacesSQL.append(".RowId)");
                var replacesRunCol = new ExprColumn(this, "ReplacesRun", replacesSQL, JdbcType.INTEGER);
                replacesRunCol.setFk(getExpSchema().getRunIdForeignKey(getContainerFilter()));
                replacesRunCol.setLabel("Replaces");
                replacesRunCol.setDescription("The run that this run replaces, usually with updated or corrected information");
                return replacesRunCol;
            case Batch:
                var batchIdCol = wrapColumn(alias, _rootTable.getColumn("BatchId"));
                batchIdCol.setUserEditable(false);
                batchIdCol.setShownInInsertView(false);
                batchIdCol.setShownInUpdateView(false);
                batchIdCol.setFk(getExpSchema().getRunGroupIdForeignKey(getContainerFilter(), true));
                return batchIdCol;
            case Properties:
                return createPropertiesColumn(alias);
            case WorkflowTask:
                var workflowTaskCol = wrapColumn(alias, _rootTable.getColumn("WorkflowTask"));
                workflowTaskCol.setShownInInsertView(false);
                workflowTaskCol.setShownInUpdateView(false);
                workflowTaskCol.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                workflowTaskCol.setLabel("Workflow Task");
                return workflowTaskCol;
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public MutableColumnInfo addDataInputColumn(String alias, String role)
    {
        checkLocked();
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.datainput.dataid)" +
                "\nFROM exp.datainput" +
                "\nINNER JOIN exp.protocolapplication on exp.datainput.targetapplicationid = exp.protocolapplication.rowid" +
                "\nWHERE exp.protocolapplication.cpastype = '" + ExpProtocol.ApplicationType.ExperimentRun + "'" +
                "\nAND exp.protocolapplication.runid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid" +
                "\nAND ");
        if (role == null)
        {
            sql.append("1 = 0");
        }
        else
        {
            sql.append("exp.datainput.role = ?");
            sql.add(role);
        }
        sql.append(")");
        return doAdd(new ExprColumn(this, alias, sql, JdbcType.INTEGER));
    }

    @Override
    public MutableColumnInfo addDataCountColumn(String alias, String roleName)
    {
        checkLocked();
        SQLFragment sql = new SQLFragment("(SELECT COUNT(DISTINCT exp.DataInput.DataId) FROM exp.DataInput " +
                "\nINNER JOIN exp.ProtocolApplication ON exp.ProtocolApplication.RowId = exp.DataInput.TargetApplicationId" +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND ");
        if (roleName == null)
        {
            sql.append("1 = 0");
        }
        else
        {
            sql.append("exp.DataInput.Role = ?");
            sql.add(roleName);
        }
        sql.append(")");
        return doAdd(new ExprColumn(this, alias, sql, JdbcType.INTEGER));
    }

    public BaseColumnInfo createInputLookupColumn()
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.ProtocolApplication.RowId) FROM exp.ProtocolApplication " +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND exp.ProtocolApplication.CpasType = '" + ExpProtocol.ApplicationType.ExperimentRun + "')");
        ExprColumn ret = new ExprColumn(this, Column.Input.toString(), sql, JdbcType.INTEGER);
        ret.setDescription("Contains pointers to all of the different kinds of inputs (both materials and data files) that could be used for this run");
        ret.setFk(new InputForeignKey(getExpSchema(), ExpProtocol.ApplicationType.ExperimentRun, getContainerFilter()));
        ret.setIsUnselectable(true);
        ret.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);
        return ret;
    }

    public BaseColumnInfo createOutputLookupColumn()
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.ProtocolApplication.RowId) FROM exp.ProtocolApplication " +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND exp.ProtocolApplication.CpasType = '" + ExpProtocol.ApplicationType.ExperimentRunOutput + "')");
        ExprColumn ret = new ExprColumn(this, Column.Output.toString(), sql, JdbcType.INTEGER);
        ret.setDescription("Contains pointers to all of the different kinds of outputs (both materials and data files) that could be produced by this run");
        ret.setFk(new InputForeignKey(getExpSchema(), ExpProtocol.ApplicationType.ExperimentRunOutput, getContainerFilter()));
        ret.setIsUnselectable(true);
        ret.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);
        return ret;
    }

    public MutableColumnInfo createDataInputsColumn(String alias)
    {
        var col = createMultiValueDatasColumn(alias, ExpProtocol.ApplicationType.ExperimentRun);
        col.setDescription("Contains multi-value lookup to each data inputs produced by this run");
        return col;
    }

    public MutableColumnInfo createDataOutputsColumn(String alias)
    {
        var col = createMultiValueDatasColumn(alias, ExpProtocol.ApplicationType.ExperimentRunOutput);
        col.setDescription("Contains multi-value lookup to each data outputs produced by this run");
        return col;
    }

    protected MutableColumnInfo createMultiValueDatasColumn(String alias, final ExpProtocol.ApplicationType type)
    {
        final String dataIdName = type == ExpProtocol.ApplicationType.ExperimentRun ? "InputDataId" : "OutputDataId";

        var dataOutputsCol = wrapColumn(alias, _rootTable.getColumn("RowId"));
        dataOutputsCol.setReadOnly(true);
        dataOutputsCol.setShownInInsertView(false);
        dataOutputsCol.setShownInUpdateView(false);
        dataOutputsCol.setFk(new MultiValuedForeignKey(new LookupForeignKey("RunId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                final ContainerFilter cf = ExpRunTableImpl.this.getContainerFilter();
                VirtualTable t = new VirtualTable(ExperimentServiceImpl.get().getSchema(), null, getUserSchema(), cf)
                {
                    @NotNull
                    @Override
                    public SQLFragment getFromSQL()
                    {
                        SQLFragment sql = new SQLFragment("SELECT pa.RunId, di.DataId AS ");
                        sql.append(dataIdName);
                        sql.append(" FROM ");
                        sql.append(ExperimentServiceImpl.get().getTinfoProtocolApplication(), "pa");
                        sql.append(", ");
                        sql.append(ExperimentServiceImpl.get().getTinfoDataInput(), "di");
                        sql.append(" WHERE di.TargetApplicationId = pa.RowId AND pa.CpasType = '");
                        sql.append(type);
                        sql.append("'");
                        return sql;
                    }
                };
                var runCol = new BaseColumnInfo("RunId", t, JdbcType.INTEGER);
                t.addColumn(runCol);

                var dataCol = new BaseColumnInfo(dataIdName, t, JdbcType.INTEGER);
                dataCol.setFk(getExpSchema().getDataIdForeignKey(cf));
                t.addColumn(dataCol);
                return t;
            }
        }, dataIdName));

        return dataOutputsCol;
    }

    @Override
    protected void populateColumns()
    {
        ExpSchema schema = getExpSchema();
        addColumn(Column.RowId);
        addColumn(Column.Flag);
        addColumn(Column.Links);
        addColumn(Column.Name);
        setTitleColumn(Column.Name.toString());
        addColumn(Column.Comments);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addContainerColumn(Column.Folder, null);
        addColumn(Column.FilePathRoot).setHidden(true);
        addColumn(Column.JobId).setFk(schema.getJobForeignKey());
        addColumn(Column.Replaced);
        addColumn(Column.ReplacedByRun);
        addColumn(Column.ReplacesRun);
        addColumn(Column.LSID).setHidden(true);
        addColumn(Column.Protocol).setFk(schema.getProtocolForeignKey(getContainerFilter(), "LSID"));
        addColumn(Column.RunGroups);
        addColumn(Column.RunGroupToggle);
        addColumn(Column.Input);
        addColumn(Column.Output);
        addColumn(Column.DataInputs);
        addColumn(Column.DataOutputs);
        addColumn(Column.Properties);
        addColumn(Column.WorkflowTask);
        addVocabularyDomains();

        ActionURL urlDetails = new ActionURL(ExperimentController.ShowRunTextAction.class, schema.getContainer());
        setDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("rowId", "RowId")));

        List<FieldKey> defaultVisibleColumns = new ArrayList<>(getDefaultVisibleColumns());
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.Comments));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.JobId));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.Folder));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.RunGroupToggle));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.ReplacedByRun));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.ReplacesRun));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.DataInputs));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.DataOutputs));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.Modified));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.ModifiedBy));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.WorkflowTask));
        setDefaultVisibleColumns(defaultVisibleColumns);

        addExpObjectMethod();
    }


    @Override
    public ColumnInfo getExpObjectColumn()
    {
        var ret = wrapColumn("_ExpRunTableImpl_object", _rootTable.getColumn("objectid"));
        ret.setConceptURI(BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI);
        return ret;
    }


    /**
     * This DisplayColumn renders a list of RunGroups.  The url expression
     * set on this DisplayColumn will be evaluated with a RenderContext
     * containing a key "experimentId" set to the value of the ExpExperiment run id. 
     */
    public class RunGroupListDisplayColumn extends DataColumn
    {
        private final ExperimentsForeignKey _fk;

        public RunGroupListDisplayColumn(ColumnInfo col, ExpRunTableImpl.ExperimentsForeignKey fk)
        {
            super(col);
            _fk = fk;
            setWidth("200");
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        public String buildString(RenderContext ctx, boolean addLinks)
        {
            StringBuilder sb = new StringBuilder();
            String separator = "";
            Object oldExperimentId = ctx.get("experimentId");

            for (ExpExperiment exp : _fk.getExperiments())
            {
                FieldKey key = new FieldKey(getBoundColumn().getFieldKey(), exp.getName());
                ColumnInfo runGroupColumn = ctx.getFieldMap().get(key);
                if (runGroupColumn != null && Boolean.TRUE.equals(runGroupColumn.getValue(ctx)))
                {
                    sb.append(separator);
                    if (addLinks)
                    {
                        sb.append("<a href=\"");
                        ctx.put("experimentId", exp.getRowId());
                        String url = renderURL(ctx);
                        if (url == null)
                        {
                            url = ExperimentController.ExperimentUrlsImpl.get().getExperimentDetailsURL(exp.getContainer(), exp).getLocalURIString();
                        }
                        sb.append(url);
                        sb.append("\">");
                    }
                    PageFlowUtil.filter(sb.append(exp.getName()));
                    if (addLinks)
                    {
                        sb.append("</a>");
                    }
                    separator = ", ";
                }
                ctx.put("experimentId", oldExperimentId);
            }
            if (sb.length() == 0)
            {
                return "";
            }
            return sb.toString();
        }

        @Override
        public String getDisplayValue(RenderContext ctx)
        {
            return buildString(ctx, false);
        }

        // 10481: convince ExcelColumn.setSimpleType() that we are actually a string.
        @Override
        public Class<?> getDisplayValueClass()
        {
            return String.class;
        }

        @NotNull
        @Override
        public String getCssStyle(RenderContext ctx)
        {
            // Use our custom concatenated string instead of the underlying RowId value
            String value = buildString(ctx, false);
            for (ConditionalFormat format : getBoundColumn().getConditionalFormats())
            {
                if (format.meetsCriteria(getBoundColumn(), value))
                {
                    return format.getCssStyle();
                }
            }
            return "";
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(buildString(ctx, true));
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            FieldKey key = getBoundColumn().getFieldKey();
            for (ExpExperiment exp : _fk.getExperiments())
            {
                keys.add(new FieldKey(key, exp.getName()));
            }
        }
    }

    public static class RunGraphDisplayColumn extends DataColumn
    {
        public RunGraphDisplayColumn(ColumnInfo info)
        {
            super(info);
            setCaption("");
            setWidth("18");
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object rowId = getColumnInfo().getValue(ctx);
            if (rowId != null)
            {
                ActionURL graphURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(ctx.getContainer(), ((Number)rowId).intValue());
                out.write("<a href=\"" + graphURL.getLocalURIString() + "\" title=\"Experiment run graph\"><img src=\"" + AppProps.getInstance().getContextPath() + "/experiment/images/graphIcon.gif\" height=\"18\" width=\"18\"/></a>");
            }
        }
    }

    private class ExperimentsForeignKey implements ForeignKey
    {
        private List<ExpExperimentImpl> _experiments;

        public ExperimentsForeignKey()
        {
        }

        private synchronized List<ExpExperimentImpl> getExperiments()
        {
            if (_experiments == null)
            {
                _experiments = ExperimentServiceImpl.get().getExperiments(getContainer(), _userSchema.getUser(), true, false);
            }
            return _experiments;
        }

        @Override
        public ColumnInfo createLookupColumn(final ColumnInfo parent, String displayField)
        {
            if (displayField == null)
                return null;
            for (final ExpExperiment exp : getExperiments())
            {
                if (displayField.equalsIgnoreCase(exp.getName()))
                {
                    ExprColumn result = new ExprColumn(parent.getParentTable(), FieldKey.fromParts(exp.getName()), new SQLFragment("~~PLACEHOLDER~~"), JdbcType.BOOLEAN, parent)
                    {
                        @Override
                        public SQLFragment getValueSql(String tableAlias)
                        {
                            SQLFragment sql = new SQLFragment("EXISTS (SELECT ExperimentId FROM ");
                            sql.append(ExperimentServiceImpl.get().getTinfoRunList(), "rl");
                            sql.append(" WHERE ExperimentRunId = ").append(parent.getValueSql(tableAlias).getSQL()).append(" AND ExperimentId = ").append(exp.getRowId()).append(")");

                            return getSqlDialect().wrapExistsExpression(sql);
                        }
                    };
                    FieldKey parentFieldKey = FieldKey.fromString(parent.getName());
                    result.setDisplayColumnFactory(new ExperimentMembershipDisplayColumnFactory(exp.getRowId(), parentFieldKey.getParent()));
                    result.setFormat("Y;N");
                    return result;
                }
            }
            return null;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public Container getLookupContainer()
        {
            return null;
        }

        @Override
        public String getLookupTableName()
        {
            return ExpSchema.TableType.RunGroups.toString();
        }

        @Override
        public String getLookupSchemaName()
        {
            return ExpSchema.SCHEMA_NAME;
        }

        @Override
        public String getLookupColumnName()
        {
            return null; // XXX: NYI
        }

        @Override
        public String getLookupDisplayName()
        {
            return null;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            VirtualTable<?> result = new VirtualTable<>(ExperimentServiceImpl.get().getSchema(), ExpSchema.TableType.RunGroups.name(), getUserSchema());
            for (ExpExperiment experiment : getExperiments())
            {
                var column = new BaseColumnInfo(experiment.getName(), JdbcType.BOOLEAN);
                column.setParentTable(result);
                result.safeAddColumn(column);
            }
            return result;
        }

        @Override @NotNull
        public NamedObjectList getSelectList(RenderContext ctx)
        {
            // XXX: NYI
            return new NamedObjectList();
        }

        @Override
        public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
        {
            return this;
        }

        @Override
        public Set<FieldKey> getSuggestedColumns()
        {
            return null;
        }
    }

    @Override
    public boolean needsContainerClauseAdded()
    {
        return super.needsContainerClauseAdded() && _experiment == null;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new RunTableUpdateService(this);
    }

    private static class RunTableUpdateService extends AbstractQueryUpdateService
    {
        private final RemapCache _cache = new RemapCache();

        RunTableUpdateService(ExpRunTable queryTable)
        {
            super(queryTable);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
        {
            Object rowIdRaw = keys.get(Column.RowId.toString());
            if (rowIdRaw != null)
            {
                Integer rowId = (Integer) ConvertUtils.convert(rowIdRaw.toString(), Integer.class);
                return new TableSelector(getQueryTable(), new SimpleFilter(FieldKey.fromParts(Column.RowId), rowId), null).getMap();
            }
            return null;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws ValidationException, QueryUpdateServiceException
        {
            ExpRunImpl run = getRun(oldRow);
            if (run != null)
            {
                // Don't trust that they're trying to edit a run from the current container
                if (!run.getContainer().hasPermission(user, UpdatePermission.class))
                {
                    throw new UnauthorizedException("You do not have permission to edit a run in " + run.getContainer());
                }

                try
                {
                    StringBuilder sb = new StringBuilder("Run edited.");
                    for (Map.Entry<String, Object> entry : row.entrySet())
                    {
                        // Most fields in the hard table can't be modified, but there are a few
                        Object value = entry.getValue();
                        if (entry.getKey().equalsIgnoreCase(Column.Name.toString()))
                        {
                            String newName = value == null ? null : (String)ConvertUtils.convert(value.toString(), String.class);
                            appendPropertyIfChanged(sb, "Name", run.getName(), newName);
                            run.setName(newName);
                        }
                        else if (entry.getKey().equalsIgnoreCase(Column.Comments.toString()))
                        {
                            String newComment = value == null ? null : (String)ConvertUtils.convert(value.toString(), String.class);
                            appendPropertyIfChanged(sb, "Comment", run.getComments(), newComment);
                            run.setComments(newComment);
                        }
                        else if (entry.getKey().equalsIgnoreCase(Column.Flag.toString()))
                        {
                            String newFlag = value == null ? null : (String)ConvertUtils.convert(value.toString(), String.class);
                            appendPropertyIfChanged(sb, "Flag", run.getComment(), newFlag);
                            run.setComment(user, newFlag);
                        }
                        else if (entry.getKey().equalsIgnoreCase(Column.WorkflowTask.toString()))
                        {
                            Integer newWorkflowTaskId = value == null ? null : (Integer)ConvertUtils.convert(value.toString(), Integer.class);
                            run.setWorkflowTaskId(newWorkflowTaskId);
                        }

                        // Also check for properties
                        ColumnInfo col = getQueryTable().getColumn(entry.getKey());
                        if (col instanceof PropertyColumn)
                        {
                            PropertyColumn propColumn = (PropertyColumn)col;
                            PropertyDescriptor propertyDescriptor = propColumn.getPropertyDescriptor();
                            Object oldValue = run.getProperty(propertyDescriptor);
                            if (propertyDescriptor.getPropertyType() == PropertyType.FILE_LINK && (value instanceof MultipartFile || value instanceof SpringAttachmentFile))
                            {
                                value = saveFile(container, col.getName(), value, AssayFileWriter.DIR_NAME);
                            }

                            ForeignKey fk = col.getFk();
                            if (fk != null && fk.allowImportByAlternateKey() && value != null)
                            {
                                try
                                {
                                    value = ConvertUtils.convert(String.valueOf(value), col.getJavaClass());
                                }
                                catch (ConversionException e)
                                {
                                    Container remapContainer = fk.getLookupContainer() != null ? fk.getLookupContainer() : container;
                                    Object remappedValue = _cache.remap(SchemaKey.fromParts(fk.getLookupSchemaName()), fk.getLookupTableName(), user, remapContainer, ContainerFilter.Type.CurrentPlusProjectAndShared, String.valueOf(value));
                                    if (remappedValue != null)
                                        value = remappedValue;
                                }
                            }
                            run.setProperty(user, propertyDescriptor, value);

                            Object newValue = value;
                            TableInfo fkTableInfo = fk != null ? fk.getLookupTableInfo() : null;
                            String lookupColumnName = fk != null ? fk.getLookupColumnName() : null;
                            ColumnInfo lookupColumn = fkTableInfo != null && lookupColumnName != null ? fkTableInfo.getColumn(lookupColumnName) : null;

                            // Don't attempt type conversion if the lookup target isn't the PK column
                            if (lookupColumn != null && lookupColumn.isKeyField())
                            {
                                // Do type conversion in case there's a mismatch in the lookup source and target columns
                                Class<?> keyColumnType = lookupColumn.getJavaClass();
                                if (newValue != null && !keyColumnType.isAssignableFrom(newValue.getClass()))
                                {
                                    newValue = ConvertUtils.convert(newValue.toString(), keyColumnType);
                                }
                                if (oldValue != null && !keyColumnType.isAssignableFrom(oldValue.getClass()))
                                {
                                    oldValue = ConvertUtils.convert(oldValue.toString(), keyColumnType);
                                }
                                Map<String, Object> oldLookupTarget = new TableSelector(fkTableInfo).getMap(oldValue);
                                if (oldLookupTarget != null)
                                {
                                    oldValue = oldLookupTarget.get(fkTableInfo.getTitleColumn());
                                }
                                Map<String, Object> newLookupTarget = new TableSelector(fkTableInfo).getMap(newValue);
                                if (newLookupTarget != null)
                                {
                                    newValue = newLookupTarget.get(fkTableInfo.getTitleColumn());
                                }
                            }
                            appendPropertyIfChanged(sb, propertyDescriptor.getNonBlankCaption(), oldValue, newValue);
                        }
                    }
                    run.save(user);
                    ExperimentServiceImpl.get().auditRunEvent(user, run.getProtocol(), run, null, sb.toString());
                }
                catch (ConversionException e)
                {
                    throw new ValidationException(e.getMessage());
                }
                catch (BatchValidationException e)
                {
                    throw new QueryUpdateServiceException(e);
                }
            }
            return getRow(user, container, oldRow);
        }

        private void appendPropertyIfChanged(StringBuilder sb, String label, Object oldValue, Object newValue)
        {
            if (!Objects.equals(oldValue, newValue))
            {
                sb.append(" ");
                sb.append(label);
                sb.append(" changed from ");
                sb.append(oldValue == null ? "blank" : "'" + oldValue + "'");
                sb.append(" to ");
                sb.append(newValue == null ? "blank" : "'" + newValue + "'");
                sb.append(".");
            }
        }

        private ExpRunImpl getRun(Map<String, Object> row)
        {
            Object rowIdRaw = row.get(Column.RowId.toString());
            if (rowIdRaw != null)
            {
                Integer rowId = (Integer) ConvertUtils.convert(rowIdRaw.toString(), Integer.class);
                if (rowId != null)
                {
                    return ExperimentServiceImpl.get().getExpRun(rowId.intValue());
                }
            }
            Object lsidRaw = row.get(Column.LSID.toString());
            if (lsidRaw != null)
            {
                String lsid = lsidRaw.toString();
                return ExperimentServiceImpl.get().getExpRun(lsid);
            }
            return null;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
        {
            ExpRun run = getRun(oldRow);
            if (run != null)
            {
                run.delete(user);
            }

            return oldRow;
        }

        @Override
        protected int truncateRows(User user, Container c)
        {
            final ExperimentServiceImpl svc = ExperimentServiceImpl.get();
            String sql = "SELECT RowId FROM " + svc.getTinfoExperimentRun() + " WHERE Container = ?";
            int[] runIds = ArrayUtils.toPrimitive(new SqlSelector(svc.getExpSchema(), sql, c).getArray(Integer.class));

            ExperimentServiceImpl.get().deleteExperimentRunsByRowIds(c, user, runIds);
            return runIds.length;
        }
    }
}
