/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.common.util.Pair;
import org.labkey.experiment.controllers.exp.ExperimentMembershipDisplayColumnFactory;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.Types;
import java.util.*;
import java.io.Writer;
import java.io.IOException;

public class ExpRunTableImpl extends ExpTableImpl<ExpRunTable.Column> implements ExpRunTable
{
    ExpProtocol _protocol;
    ExpExperiment _experiment;
    String[] _protocolPatterns;

    private ExpMaterial _inputMaterial;
    private ExpData _inputData;
    private ExpSchema _schema;
    private List<ExpRun> _runs;

    public ExpRunTableImpl(String alias)
    {
        super(alias, ExperimentServiceImpl.get().getTinfoExperimentRun());
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

    public void clearContainer()
    {
        if (_container != null)
        {
            _container = null;
            clearConditions(_rootTable.getColumn("container"));
        }
        _schema.setRestrictContainer(false);
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
                condition.append(_rootTable.getColumn("ProtocolLSID").getValueSql());
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
        addCondition(new SQLFragment( " " + ExperimentServiceImpl.get().getTinfoExperimentRun() + ".LSID IN " +
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
        addCondition(new SQLFragment( " " + ExperimentServiceImpl.get().getTinfoExperimentRun() + ".LSID IN " +
            "(SELECT RunLSID FROM " + ExperimentServiceImpl.get().getTinfoExperimentRunDataInputs() + " WHERE RowId = ?)", _inputData.getRowId()));

    }

    public ExpData getInputData()
    {
        return _inputData;
    }

    public void setRuns(List<ExpRun> runs)
    {
        _runs = runs;
        if (runs.isEmpty())
        {
            addCondition(new SQLFragment("1 = 2"));
        }
        else
        {
            SQLFragment sql = new SQLFragment();
            sql.append(ExperimentServiceImpl.get().getTinfoExperimentRun());
            sql.append(".RowID IN (");
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
        addCondition(new SQLFragment(" " + ExperimentServiceImpl.get().getTinfoExperimentRun() + ".RowId IN ( SELECT ExperimentRunId FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " "
                    +  " WHERE ExperimentId = " +  experiment.getRowId() + " ) "));
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Comments:
                return wrapColumn(alias, _rootTable.getColumn("Comments"));
            case Container:
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
                col.setFk(new ExperimentsForeignKey());
                col.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new RunGroupListDisplayColumn(colInfo);
                    }
                });

                return col;

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public ColumnInfo addDataInputColumn(String alias, PropertyDescriptor pd)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.datainput.dataid)" +
                "\nFROM exp.datainput" +
                "\nINNER JOIN exp.protocolapplication on exp.datainput.targetapplicationid = exp.protocolapplication.rowid" +
                "\nWHERE exp.protocolapplication.cpastype = '" + ExpProtocol.ApplicationType.ExperimentRun + "'" +
                "\nAND exp.protocolapplication.runid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid" +
                "\nAND ");
        if (pd == null)
        {
            sql.append("1 = 0");
        }
        else
        {
            sql.append("exp.datainput.propertyid = " + pd.getPropertyId());
        }
        sql.append(")");
        return doAdd(new ExprColumn(this, alias, sql, Types.INTEGER));
    }

    public ColumnInfo addDataCountColumn(String alias, PropertyDescriptor pd)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(DISTINCT exp.DataInput.DataId) FROM exp.DataInput " +
                "\nINNER JOIN exp.ProtocolApplication ON exp.ProtocolApplication.RowId = exp.DataInput.TargetApplicationId" +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND ");
        if (pd == null)
        {
            sql.append("1 = 0");
        }
        else
        {
            sql.append("exp.DataInput.PropertyId = " + pd.getPropertyId());
        }
        sql.append(")");
        return doAdd(new ExprColumn(this, alias, sql, Types.INTEGER));
    }

    public ColumnInfo createInputLookupColumn(String alias, ExpSchema schema, Collection<Map.Entry<String, PropertyDescriptor>> dataInputs, Collection<Map.Entry<String, PropertyDescriptor>> materialInputs)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.ProtocolApplication.RowId) FROM exp.ProtocolApplication " +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND exp.ProtocolApplication.CpasType = '" + ExpProtocol.ApplicationType.ExperimentRun + "')");
        ColumnInfo ret = new ExprColumn(this, alias, sql, Types.INTEGER);
        ret.setFk(new InputForeignKey(schema, dataInputs, materialInputs));
        ret.setIsUnselectable(true);
        return ret;
    }

    public ColumnInfo createOutputLookupColumn(String alias, ExpSchema schema, Collection<Map.Entry<String, PropertyDescriptor>> dataOutputs, Collection<Map.Entry<String, PropertyDescriptor>> materialOutputs)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.ProtocolApplication.RowId) FROM exp.ProtocolApplication " +
                "\nWHERE exp.ProtocolApplication.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId" +
                "\nAND exp.ProtocolApplication.CpasType = '" + ExpProtocol.ApplicationType.ExperimentRunOutput + "')");
        ColumnInfo ret = new ExprColumn(this, alias, sql, Types.INTEGER);
        ret.setFk(new InputForeignKey(schema, dataOutputs, materialOutputs));
        ret.setIsUnselectable(true);
        return ret;
    }


    public String urlFlag(boolean flagged)
    {
        return flagged ? ExpRunImpl.s_urlFlagRun : ExpRunImpl.s_urlUnflagRun;
    }

    public void populate(ExpSchema schema)
    {
        _schema = schema;
        //FIX: ExpDataTableImpl did this, but runs did not for some reason. Needed for ms1 search results
        if(schema.isRestrictContainer())
            setContainer(schema.getContainer());
        addColumn(Column.RowId);
        addColumn(Column.Flag);
        addColumn(Column.Links);
        addColumn(Column.Name);
        addColumn(Column.Comments);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addContainerColumn(Column.Container);
        addColumn(Column.FilePathRoot).setIsHidden(true);
        addColumn(Column.LSID).setIsHidden(true);
        addColumn(Column.Protocol).setFk(schema.getProtocolLSIDForeignKey());
        addColumn(Column.RunGroups);
        List<Map.Entry<String, PropertyDescriptor>> dataInputRoles = new ArrayList<Map.Entry<String, PropertyDescriptor>>();
        dataInputRoles.add(new Pair<String, PropertyDescriptor>("Data", null));
        dataInputRoles.addAll(ExperimentService.get().getDataInputRoles(schema.getContainer()).entrySet());
        List<Map.Entry<String, PropertyDescriptor>> materialInputRoles = new ArrayList<Map.Entry<String, PropertyDescriptor>>();
        materialInputRoles.add(new Pair<String, PropertyDescriptor>("Material", null));
        materialInputRoles.addAll(ExperimentService.get().getMaterialInputRoles(schema.getContainer()).entrySet());

        addColumn(createInputLookupColumn("Input", schema, dataInputRoles, materialInputRoles));
        addColumn(createOutputLookupColumn("Output", schema, dataInputRoles, materialInputRoles));
        ActionURL urlDetails = new ActionURL(ExperimentController.ShowRunTextAction.class, schema.getContainer());
        setDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("rowId", "RowId")));
        addDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("LSID", "LSID")));

        List<FieldKey> defaultVisibleColumns = new ArrayList<FieldKey>(getDefaultVisibleColumns());
        defaultVisibleColumns.remove(FieldKey.fromParts(Column.Comments.getColumnName()));
        setDefaultVisibleColumns(defaultVisibleColumns);
    }

    public class RunGroupListDisplayColumn extends DataColumn
    {
        private Set<ColumnInfo> _runGroupColumns;
        private ExpExperiment[] _experiments;

        public RunGroupListDisplayColumn(ColumnInfo col)
        {
            super(col);
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
                _experiments = ExperimentService.get().getExperiments(ctx.getContainer());
            }
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
                        ActionURL url = ExperimentController.ExperimentUrlsImpl.get().getExperimentDetailsURL(matchingRunGroup.getContainer(), matchingRunGroup);
                        sb.append(url.getLocalURIString());
                        sb.append("\">");
                    }
                    PageFlowUtil.filter(sb.append(key.getName()));
                    if (matchingRunGroup != null && addLinks)
                    {
                        sb.append("</a>");
                    }
                    separator = ", ";
                }
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
            ExperimentsForeignKey fk = (ExperimentsForeignKey)getBoundColumn().getFk();
            List<FieldKey> runGroupFieldKeys = new ArrayList<FieldKey>();
            for (ExpExperiment exp : fk.getExperiments())
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
                Container c = getContainer();
                if (c == null)
                {
                    c = _schema.getContainer();
                }
                _experiments = ExperimentServiceImpl.get().getExperiments(c);
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
            return "Experiments";
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
}
