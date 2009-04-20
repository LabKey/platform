/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.exp.ExperimentMembershipDisplayColumnFactory;

import java.io.IOException;
import java.io.Writer;
import java.sql.Types;
import java.util.*;

public class ExpRunTableImpl extends ExpTableImpl<ExpRunTable.Column> implements ExpRunTable
{
    ExpProtocol _protocol;
    ExpExperiment _experiment;
    String[] _protocolPatterns;

    private ExpMaterial _inputMaterial;
    private ExpData _inputData;

    public ExpRunTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoExperimentRun(), schema);
    }

    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    public void setProtocol(ExpProtocol protocol)
    {
        if (_protocol != null)
            throw new IllegalStateException("Cannot unset protocol");
        _protocol = protocol;
        if (_protocol != null)
        {
            addCondition(_rootTable.getColumn("ProtocolLSID"), protocol.getLSID());
        }
    }

    public void setProtocolPatterns(String... patterns)
    {
        _protocolPatterns = patterns;
        if (_protocolPatterns != null)
        {
            SQLFragment condition = new SQLFragment();
            condition.append("(");
            String separator = "";
            for (String pattern : _protocolPatterns)
            {
                condition.append(separator);
                condition.append(_rootTable.getColumn("ProtocolLSID").getAlias());
                condition.append(" LIKE ?");
                condition.add(pattern);
                separator = " OR "; 
            }
            condition.append(")");
            addCondition(condition);
        }
    }

    public ExpExperiment getExperiment()
    {
        return _experiment;
    }

    public void setInputMaterial(ExpMaterial material)
    {
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

    public ExpMaterial getInputMaterial()
    {
        return _inputMaterial;
    }

    public void setInputData(ExpData data)
    {
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

    public ExpData getInputData()
    {
        return _inputData;
    }

    public void setRuns(List<ExpRun> runs)
    {
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

    public void setExperiment(ExpExperiment experiment)
    {
        if (_experiment != null)
            throw new IllegalArgumentException("Cannot unset experiment");
        if (experiment == null)
            return;
        _experiment = experiment;
        addCondition(new SQLFragment(" RowId IN ( SELECT ExperimentRunId FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " "
                    +  " WHERE ExperimentId = " +  experiment.getRowId() + " ) "));

        if (_schema.getContainer().equals(ContainerManager.getSharedContainer()))
        {
            // If we're in the /Shared project, look everywhere
            setContainerFilter(new ContainerFilter.AllFolders(_schema.getUser()));
        }
        else if (getContainer().isProject())
        {
            // If we're in a project, look in subfolders
            setContainerFilter(new ContainerFilter.CurrentAndSubfolders(_schema.getUser()));
        }
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Comments:
                return wrapColumn(alias, _rootTable.getColumn("Comments"));
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case FilePathRoot:
                return wrapColumn(alias, _rootTable.getColumn("FilePathRoot"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Protocol:
                return wrapColumn(alias, _rootTable.getColumn("ProtocolLSID"));
            case ProtocolStep:
            {
                SQLFragment sql = new SQLFragment("(SELECT MIN(exp.Protocol.Name) FROM exp.Protocol " +
                        "\nINNER JOIN exp.ProtocolApplication ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID" +
                        "\nWHERE exp.ProtocolApplication.CpasType = 'ProtocolApplication' AND exp.ProtocolApplication.RunId = " +
                        ExprColumn.STR_TABLE_ALIAS + ".RowId)");

                return new ExprColumn(this, alias, sql, Types.VARCHAR);
            }
            case RowId:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                if (getPkColumns().isEmpty())
                    ret.setKeyField(true);
                ret.setFk(new RowIdForeignKey(ret));
                ret.setIsHidden(true);
                return ret;
            }
            case Flag:
                return createFlagColumn(alias);
            case Links:
            {
                ColumnInfo result = wrapColumn("Links", _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new RunGraphDisplayColumn(colInfo);
                    }
                });
                return result;
            }
            case RunGroups:
                ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("RowId"));
                final ExperimentsForeignKey fk = new ExperimentsForeignKey();
                col.setFk(fk);
                col.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new RunGroupListDisplayColumn(colInfo, fk);
                    }
                });

                return col;
            case Input:
                return createInputLookupColumn();
            case Output:
                return createOutputLookupColumn();

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public ColumnInfo addDataInputColumn(String alias, String role)
    {
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
        return doAdd(new ExprColumn(this, alias, sql, Types.INTEGER));
    }

    public ColumnInfo addDataCountColumn(String alias, String roleName)
    {
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
        return doAdd(new ExprColumn(this, alias, sql, Types.INTEGER));
    }

    public ColumnInfo createInputLookupColumn()
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.ProtocolApplication.RowId) FROM exp.ProtocolApplication " +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND exp.ProtocolApplication.CpasType = '" + ExpProtocol.ApplicationType.ExperimentRun + "')");
        ColumnInfo ret = new ExprColumn(this, Column.Input.toString(), sql, Types.INTEGER);
        ret.setFk(new InputForeignKey(getExpSchema(), ExpProtocol.ApplicationType.ExperimentRun, new DelegatingContainerFilter(this)));
        ret.setIsUnselectable(true);
        return ret;
    }

    public ColumnInfo createOutputLookupColumn()
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.ProtocolApplication.RowId) FROM exp.ProtocolApplication " +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND exp.ProtocolApplication.CpasType = '" + ExpProtocol.ApplicationType.ExperimentRunOutput + "')");
        ColumnInfo ret = new ExprColumn(this, Column.Output.toString(), sql, Types.INTEGER);
        ret.setFk(new InputForeignKey(getExpSchema(), ExpProtocol.ApplicationType.ExperimentRunOutput, new DelegatingContainerFilter(this)));
        ret.setIsUnselectable(true);
        return ret;
    }


    public String urlFlag(boolean flagged)
    {
        return flagged ? ExpRunImpl.s_urlFlagRun : ExpRunImpl.s_urlUnflagRun;
    }

    public void populate()
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
        addContainerColumn(Column.Folder, null);
        addColumn(Column.FilePathRoot).setIsHidden(true);
        addColumn(Column.LSID).setIsHidden(true);
        addColumn(Column.Protocol).setFk(schema.getProtocolForeignKey("LSID"));
        addColumn(Column.RunGroups);
        addColumn(Column.Input);
        addColumn(Column.Output);

        ActionURL urlDetails = new ActionURL(ExperimentController.ShowRunTextAction.class, schema.getContainer());
        setDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("rowId", "RowId")));
        addDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("LSID", "LSID")));

        List<FieldKey> defaultVisibleColumns = new ArrayList<FieldKey>(getDefaultVisibleColumns());
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.Comments));
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.Folder));
        setDefaultVisibleColumns(defaultVisibleColumns);
    }

    /**
     * This DisplayColumn renders a list of RunGroups.  The url expression
     * set on this DisplayColumn will be evaluated with a RenderContext
     * containing a key "experimentId" set to the value of the ExpExperiment run id. 
     */
    public class RunGroupListDisplayColumn extends DataColumn
    {
        private Set<ColumnInfo> _runGroupColumns;
        private ExpExperiment[] _experiments;
        private final ExperimentsForeignKey _fk;

        public RunGroupListDisplayColumn(ColumnInfo col, ExpRunTableImpl.ExperimentsForeignKey fk)
        {
            super(col);
            _fk = fk;
            col.setWidth(null);
            setWidth("200");
        }

        public boolean isFilterable()
        {
            return false;
        }

        public boolean isSortable()
        {
            return false;
        }

        public String buildString(RenderContext ctx, boolean addLinks)
        {
            StringBuilder sb = new StringBuilder();
            String separator = "";
            if (_experiments == null)
            {
                _experiments = ExperimentService.get().getExperiments(ctx.getContainer(), ctx.getViewContext().getUser(), true, false);
            }
            Object oldExperimentId = ctx.get("experimentId");
            for (ColumnInfo runGroupColumn : _runGroupColumns)
            {
                FieldKey key = FieldKey.fromString(runGroupColumn.getName());
                if (Boolean.TRUE.equals(runGroupColumn.getValue(ctx)))
                {
                    sb.append(separator);
                    ExpExperiment matchingRunGroup = null;
                    for (ExpExperiment experiment : _experiments)
                    {
                        if (experiment.getName().equals(key.getName()))
                        {
                            matchingRunGroup = experiment;
                            break;
                        }
                    }
                    if (matchingRunGroup != null && addLinks)
                    {
                        sb.append("<a href=\"");
                        ctx.put("experimentId", matchingRunGroup.getRowId());
                        String url = getURL(ctx);
                        if (url == null)
                        {
                            url = ExperimentController.ExperimentUrlsImpl.get().getExperimentDetailsURL(matchingRunGroup.getContainer(), matchingRunGroup).getLocalURIString();
                        }
                        sb.append(url);
                        sb.append("\">");
                    }
                    PageFlowUtil.filter(sb.append(key.getName()));
                    if (matchingRunGroup != null && addLinks)
                    {
                        sb.append("</a>");
                    }
                    separator = ", ";
                }
                ctx.put("experimentId", oldExperimentId);
            }
            if (sb.length() == 0)
            {
                return "&nbsp;";
            }
            return sb.toString();
        }

        public String getFormattedValue(RenderContext ctx)
        {
            return buildString(ctx, false);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(buildString(ctx, true));
        }

        public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(buildString(ctx, true));
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            FieldKey key = FieldKey.fromString(getBoundColumn().getName());
            List<FieldKey> runGroupFieldKeys = new ArrayList<FieldKey>();
            for (ExpExperiment exp : _fk.getExperiments())
            {
                runGroupFieldKeys.add(new FieldKey(key, exp.getName()));
            }
            Map<FieldKey, ColumnInfo> runGroupCols = QueryService.get().getColumns(getBoundColumn().getParentTable(), runGroupFieldKeys);
            _runGroupColumns = new LinkedHashSet<ColumnInfo>();
            for (FieldKey runGroupFieldKey : runGroupFieldKeys)
            {
                ColumnInfo col = runGroupCols.get(runGroupFieldKey);
                assert col != null : "Could not find ColumnInfo for " + runGroupFieldKey;
                _runGroupColumns.add(col);
            }
            columns.addAll(_runGroupColumns);
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

        public boolean isFilterable()
        {
            return false;
        }

        public boolean isSortable()
        {
            return false;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object rowId = getColumnInfo().getValue(ctx);
            if (rowId != null)
            {
                ActionURL graphURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(ctx.getContainer(), ((Number)rowId).intValue());
                out.write("<a href=\"" + graphURL.getLocalURIString() + "\" title=\"Experiment run graph\"><img src=\"" + AppProps.getInstance().getContextPath() + "/Experiment/images/graphIcon.gif\" height=\"18\" width=\"18\"/></a>");
            }
        }
    }

    private class ExperimentsForeignKey implements ForeignKey
    {
        private ExpExperiment[] _experiments;

        public ExperimentsForeignKey()
        {
        }

        private synchronized ExpExperiment[] getExperiments()
        {
            if (_experiments == null)
            {
                _experiments = ExperimentServiceImpl.get().getExperiments(getContainer(), _schema.getUser(), true, false);
            }
            return _experiments;
        }

        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            if (displayField == null)
                return null;
            for (ExpExperiment exp : getExperiments())
            {
                if (displayField.equalsIgnoreCase(exp.getName()))
                {
                    SQLFragment sql = new SQLFragment("(SELECT CAST((CASE WHEN (SELECT MAX(ExperimentId) FROM ");
                    sql.append(ExperimentServiceImpl.get().getTinfoRunList());
                    sql.append(" WHERE ExperimentRunId = " + parent.getValueSql() + " AND ExperimentId = " + exp.getRowId() + ")");
                    sql.append(" IS NOT NULL THEN 1 ELSE 0 END) AS ");
                    sql.append(parent.getSqlDialect().getBooleanDatatype());
                    sql.append("))");
                    ExprColumn result = new ExprColumn(parent.getParentTable(), exp.getName(), sql, Types.BOOLEAN, parent);
                    FieldKey parentFieldKey = FieldKey.fromString(parent.getName());
                    result.setCaption(parent.getCaption() + " " + exp.getName());
                    result.setDisplayColumnFactory(new ExperimentMembershipDisplayColumnFactory(exp.getRowId(), parentFieldKey.getParent()));
                    result.setFormatString("Y;N");
                    return result;
                }
            }
            return null;
        }

        public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        public String getLookupTableName()
        {
            return ExpSchema.TableType.RunGroups.toString();
        }

        public String getLookupSchemaName()
        {
            return ExpSchema.SCHEMA_NAME;
        }

        public String getLookupColumnName()
        {
            return null; // XXX: NYI
        }

        public TableInfo getLookupTableInfo()
        {
            VirtualTable result = new VirtualTable(ExperimentServiceImpl.get().getSchema());
            for (ExpExperiment experiment : getExperiments())
            {
                result.safeAddColumn(new ColumnInfo(experiment.getName()));
            }
            return result;
        }
    }

    @Override
    public boolean needsContainerClauseAdded()
    {
        if (!super.needsContainerClauseAdded())
        {
            return false;
        }
        return _experiment == null;
    }
}
