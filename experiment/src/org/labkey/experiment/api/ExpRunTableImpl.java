/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.exp.ExperimentMembershipDisplayColumnFactory;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
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
        super(name, ExperimentServiceImpl.get().getTinfoExperimentRun(), schema, new ExpRunImpl(new ExperimentRun()));
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
                ColumnInfo containerColumn = wrapColumn(alias, _rootTable.getColumn("Container"));
                containerColumn.setUserEditable(false);
                return containerColumn;
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case FilePathRoot:
                ColumnInfo filePathRootColumn = wrapColumn(alias, _rootTable.getColumn("FilePathRoot"));
                filePathRootColumn.setUserEditable(false);
                return filePathRootColumn;
            case LSID:
                ColumnInfo lsidColumn = wrapColumn(alias, _rootTable.getColumn("LSID"));
                lsidColumn.setUserEditable(false);
                return lsidColumn;
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Protocol:
                ColumnInfo protocolColumn = wrapColumn(alias, _rootTable.getColumn("ProtocolLSID"));
                protocolColumn.setUserEditable(false);
                return protocolColumn;
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
                ret.setHidden(true);
                return ret;
            }
            case Flag:
                return createFlagColumn(alias);
            case Links:
            {
                ColumnInfo result = wrapColumn("Links", _rootTable.getColumn("RowId"));
                result.setShownInUpdateView(false);
                result.setShownInInsertView(false);
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
                col.setTextAlign("left");
                final ExperimentsForeignKey fk = new ExperimentsForeignKey();
                col.setFk(fk);
                col.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new RunGroupListDisplayColumn(colInfo, fk);
                    }
                });
                col.setShownInInsertView(false);
                col.setShownInUpdateView(false);
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
        ret.setDescription("Contains pointers to all of the different kinds of inputs (both materials and data files) that could be used for this run");
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
        ret.setDescription("Contains pointers to all of the different kinds of outputs (both materials and data files) that could be produced by this run");
        ret.setFk(new InputForeignKey(getExpSchema(), ExpProtocol.ApplicationType.ExperimentRunOutput, new DelegatingContainerFilter(this)));
        ret.setIsUnselectable(true);
        return ret;
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
        addColumn(Column.FilePathRoot).setHidden(true);
        addColumn(Column.LSID).setHidden(true);
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

        public String getDisplayValue(RenderContext ctx)
        {
            return buildString(ctx, false);
        }

        // 10481: convince ExcelColumn.setSimpleType() that we are actually a string.
        @Override
        public Class getDisplayValueClass()
        {
            return String.class;
        }

        @NotNull
        @Override
        protected String getCssStyle(RenderContext ctx)
        {
            // Use our custom concatenated string instead of the underlying RowId value
            String value = buildString(ctx, false);
            for (ConditionalFormat format : getBoundColumn().getConditionalFormats())
            {
                if (format.meetsCriteria(value))
                {
                    return format.getCssStyle();
                }
            }
            return "";
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(buildString(ctx, true));
        }

        public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
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

        public ColumnInfo createLookupColumn(final ColumnInfo parent, String displayField)
        {
            if (displayField == null)
                return null;
            for (final ExpExperiment exp : getExperiments())
            {
                if (displayField.equalsIgnoreCase(exp.getName()))
                {
                    ExprColumn result = new ExprColumn(parent.getParentTable(), exp.getName(), new SQLFragment("~~PLACEHOLDER~~"), Types.BOOLEAN, parent)
                    {
                        @Override
                        public SQLFragment getValueSql(String tableAlias)
                        {
                            SqlDialect d = getSqlDialect();
                            SQLFragment sql = new SQLFragment("(CASE WHEN EXISTS (SELECT ExperimentId FROM ");
                            sql.append(ExperimentServiceImpl.get().getTinfoRunList());
                            sql.append(" WHERE ExperimentRunId = " + parent.getValueSql(tableAlias).getSQL() + " AND ExperimentId = " + exp.getRowId() + ") THEN " + d.getBooleanTRUE() + " ELSE " + d.getBooleanFALSE() + " END)");
                            return sql;
                        }
                    };
                    FieldKey parentFieldKey = FieldKey.fromString(parent.getName());
                    result.setLabel(parent.getLabel() + " " + exp.getName());
                    result.setDisplayColumnFactory(new ExperimentMembershipDisplayColumnFactory(exp.getRowId(), parentFieldKey.getParent()));
                    result.setFormat("Y;N");
                    return result;
                }
            }
            return null;
        }

        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        public String getLookupContainerId()
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

        public NamedObjectList getSelectList()
        {
            // XXX: NYI
            return new NamedObjectList();
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
        RunTableUpdateService(ExpRunTable queryTable)
        {
            super(queryTable);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            Object rowIdRaw = keys.get(Column.RowId.toString());
            if (rowIdRaw != null)
            {
                Integer rowId = (Integer) ConvertUtils.convert(rowIdRaw.toString(), Integer.class);
                return Table.selectObject(getQueryTable(), Table.ALL_COLUMNS, new SimpleFilter(Column.RowId.toString(), rowId), null, Map.class);
            }
            return null;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            ExpRunImpl run = getRun(oldRow);
            if (run != null)
            {
                StringBuilder sb = new StringBuilder("Run edited.");
                for (Map.Entry<String, Object> entry : row.entrySet())
                {
                    // Most fields in the hard table can't be modified, but there are a few
                    if (entry.getKey().equalsIgnoreCase(Column.Name.toString()))
                    {
                        String newName = entry.getValue() == null ? null : (String) ConvertUtils.convert(entry.getValue().toString(), String.class);
                        sb.append(" Name changed from " + run.getName() + " to " + newName + ".");
                        run.setName(newName);
                    }
                    else if (entry.getKey().equalsIgnoreCase(Column.Comments.toString()))
                    {
                        String newComment = entry.getValue() == null ? null : (String) ConvertUtils.convert(entry.getValue().toString(), String.class);
                        sb.append(" Comment changed from " + run.getComments() + " to " + newComment + ".");
                        run.setComments(newComment);
                    }
                    else if (entry.getKey().equalsIgnoreCase(Column.Flag.toString()))
                    {
                        String newFlag = entry.getValue() == null ? null : (String) ConvertUtils.convert(entry.getValue().toString(), String.class);
                        sb.append(" Flag changed from " + run.getComment() + " to " + newFlag + ".");
                        run.setComment(user, newFlag);
                    }

                    // Also check for properties
                    ColumnInfo col = getQueryTable().getColumn(entry.getKey());
                    if (col != null && col instanceof PropertyColumn)
                    {
                        PropertyColumn propColumn = (PropertyColumn)col;
                        PropertyDescriptor propertyDescriptor = propColumn.getPropertyDescriptor();
                        Object oldValue = run.getProperty(propertyDescriptor);
                        sb.append(" " + propertyDescriptor.getNonBlankCaption() + " changed from " + oldValue + " to " + entry.getValue() + ".");
                        run.setProperty(user, propertyDescriptor, entry.getValue());
                    }
                }
                run.save(user);
                ExperimentServiceImpl.get().auditRunEvent(user, run.getProtocol(), run, sb.toString());
            }
            return getRow(user, container, oldRow);
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
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            ExpRun run = getRun(oldRow);
            if (run != null)
            {
                run.delete(user);
            }

            return oldRow;
        }
    }
}
