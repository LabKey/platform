/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class ExpDataTableImpl extends ExpProtocolOutputTableImpl<ExpDataTable.Column> implements ExpDataTable
{
    protected ExpExperiment _experiment;
    protected boolean _runSpecified;
    protected ExpRun _run;
    protected DataType _type;
    protected ExpDataClass _dataClass;

    public ExpDataTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoData(), schema, new ExpDataImpl(new Data()));

        addAllowablePermission(UpdatePermission.class);
        addAllowablePermission(InsertPermission.class);
    }

    public void populate()
    {
        addColumn(Column.RowId).setHidden(true);
        addColumn(Column.Name);
        addColumn(Column.Description);
        addColumn(Column.DataClass);
        ExpSchema schema = getExpSchema();
        addColumn(Column.Run).setFk(schema.getRunIdForeignKey());
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);

        addColumn(Column.ContentLink);
        addColumn(Column.Thumbnail);
        addColumn(Column.InlineThumbnail);
        addColumn(Column.SourceProtocolApplication);
        addColumn(Column.Protocol);
        addContainerColumn(Column.Folder, null);
        addColumn(Column.ViewOrDownload);
        addColumn(Column.Generated);
        addColumn(Column.LastIndexed);

        addFileColumns(false);
        setDefaultColumns();
        setTitleColumn("Name");

        // Only include the dataClassId parameter if the ExpData row has a DataClass
        DetailsURL detailsURL = DetailsURL.fromString("/experiment/showData.view?rowId=${rowId}${dataClass:prefix('%26dataClassId=')}");

        setDetailsURL(detailsURL);
        getColumn(Column.RowId).setURL(detailsURL);
        getColumn(Column.Name).setURL(detailsURL);

        ActionURL deleteUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteDatasURL(getContainer(), null);
        setDeleteURL(new DetailsURL(deleteUrl));

        ColumnInfo colInputs = addColumn(Column.Inputs);
        addMethod("Inputs", new LineageMethod(getContainer(), colInputs, true));

        ColumnInfo colOutputs = addColumn(Column.Outputs);
        addMethod("Outputs", new LineageMethod(getContainer(), colOutputs, false));

    }

    public List<String> addFileColumns(boolean isFilesTable)
    {
        List<String> customProps = new ArrayList<>();
        ColumnInfo lsidColumn = addColumn(Column.LSID);
        lsidColumn.setHidden(true);
        if (!isFilesTable)
            addColumn(Column.DataFileUrl);

        addColumn(Column.DownloadLink).setUserEditable(isFilesTable);
        addColumn(Column.ViewFileLink).setUserEditable(isFilesTable);
        addColumn(Column.FileExists).setUserEditable(isFilesTable);
        addColumn(Column.FileSize).setUserEditable(isFilesTable);
        addColumn(Column.FileExtension).setUserEditable(isFilesTable);
        ColumnInfo flagCol = addColumn(Column.Flag);
        if (isFilesTable)
            flagCol.setLabel("Description");

        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        String domainURI = svc.getDomainURI(getContainer());
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, getContainer());

        if (dd != null)
        {
            Domain domain = PropertyService.get().getDomain(dd.getDomainId());
            if (domain != null)
            {
                for (DomainProperty prop : domain.getProperties())
                {
                    // don't set container on property column so that inherited domain properties work
                    ColumnInfo projectColumn = new PropertyColumn(prop.getPropertyDescriptor(), lsidColumn, getContainer(), _userSchema.getUser(), false);
                    addColumn(projectColumn);
                    customProps.add(projectColumn.getAlias());
                }
                setDomain(domain);
            }
        }

        return customProps;
    }

    public void setDefaultColumns()
    {
        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(Column.Name));
        defaultCols.add(FieldKey.fromParts(Column.Run));
        defaultCols.add(FieldKey.fromParts(Column.DataFileUrl));
        setDefaultVisibleColumns(defaultCols);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        return svc.getFilePropsUpdateService(this, getContainer());
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        return super.resolveColumn(name);
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case DataFileUrl:
                ColumnInfo dataFileUrl = wrapColumn(alias, _rootTable.getColumn("DataFileUrl"));
                dataFileUrl.setUserEditable(false);
                return dataFileUrl;
            case LSID:
                ColumnInfo lsid = wrapColumn(alias, _rootTable.getColumn("LSID"));
                lsid.setUserEditable(false);
                return lsid;
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Description:
                return wrapColumn(alias, _rootTable.getColumn("Description"));
            case LastIndexed:
                ColumnInfo lastIndexed = wrapColumn(alias, _rootTable.getColumn("LastIndexed"));
                lastIndexed.setUserEditable(false);
                return lastIndexed;
            case DataClass:
            {
                ColumnInfo c = wrapColumn(alias, _rootTable.getColumn("classId"));
                c.setUserEditable(false);
                c.setFk(new QueryForeignKey(ExpSchema.SCHEMA_NAME, getContainer(), getContainer(), getUserSchema().getUser(), ExpSchema.TableType.DataClasses.name(), "RowId", "Name"));
                return c;
            }
            case Protocol:
            {
                ExprColumn col = new ExprColumn(this, Column.Protocol.toString(), new SQLFragment(
                        "(SELECT ProtocolLSID FROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                        " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), JdbcType.VARCHAR, getColumn(Column.SourceProtocolApplication));
                col.setFk(getExpSchema().getProtocolForeignKey("LSID"));
                col.setSqlTypeName("lsidtype");
                col.setHidden(true);
                return col;
            }
            case SourceProtocolApplication:
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey());
                columnInfo.setUserEditable(false);
                columnInfo.setHidden(true);
                return columnInfo;
            }

            case SourceApplicationInput:
            {
                ColumnInfo col = createEdgeColumn(alias, Column.SourceProtocolApplication, ExpSchema.TableType.DataInputs);
                col.setDescription("Contains a reference to the DataInput row between this ExpData and it's SourceProtocolApplication");
                col.setHidden(true);
                return col;
            }

            case RunApplication:
            {
                SQLFragment sql = new SQLFragment("(SELECT pa.rowId FROM ")
                        .append(ExperimentService.get().getTinfoProtocolApplication(), "pa")
                        .append(" WHERE pa.runId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".runId")
                        .append(" AND pa.cpasType = '").append(ExpProtocol.ApplicationType.ExperimentRunOutput.name()).append("'")
                        .append(")");

                ColumnInfo col = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
                col.setFk(getExpSchema().getProtocolApplicationForeignKey());
                col.setDescription("Contains a reference to the ExperimentRunOutput protocol application of the run that created this data");
                col.setUserEditable(false);
                col.setReadOnly(true);
                col.setHidden(true);
                return col;
            }

            case RunApplicationOutput:
            {
                ColumnInfo col = createEdgeColumn(alias, Column.RunApplication, ExpSchema.TableType.DataInputs);
                col.setDescription("Contains a reference to the DataInput row between this ExpData and it's RunOutputApplication");
                return col;
            }
            case RowId:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                ret.setFk(new RowIdForeignKey(ret));
                ret.setHidden(true);
                return ret;
            }
            case Run:
                ColumnInfo runId = wrapColumn(alias, _rootTable.getColumn("RunId"));
                runId.setUserEditable(false);
                return runId;
            case Flag:
                return createFlagColumn(alias);
            case DownloadLink:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new DownloadFileDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A link to download the file");
                return result;
            }
            case ViewFileLink:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ViewFileDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A link to view the file directly on the web site");
                return result;
            }
            case ContentLink:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ViewContentDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A link to view the imported contents of the file on the web site");
                return result;
            }
            case Thumbnail:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ThumbnailDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A popup thumbnail of the file if it is an image");
                return result;
            }
            case InlineThumbnail:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new InlineThumbnailDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("An inline thumbnail of the file if it is an image");
                return result;
            }
            case FileSize:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setTextAlign("left");
                result.setJdbcType(JdbcType.VARCHAR);
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ExpDataFileColumn(colInfo)
                        {
                            @Override
                            protected void renderData(Writer out, ExpData data) throws IOException
                            {
                                String val = ((String)getJsonValue(data));
                                if (val != null)
                                    out.write(val);
                            }

                            @Override
                            public Object getJsonValue(ExpData data)
                            {
                                if (data == null || data.getFile() == null)
                                    return "";
                                else if (!data.getFile().exists())
                                    return "File Not Found";
                                else
                                {
                                    long size = data.getFile().length();
                                    return FileUtils.byteCountToDisplaySize(size);
                                }
                            }
                        };
                    }
                });
                result.setUserEditable(false);
                result.setShownInUpdateView(false);
                result.setShownInInsertView(false);

                return result;
            }
            case FileExists:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setJdbcType(JdbcType.BOOLEAN);
                result.setTextAlign("left");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ExpDataFileColumn(colInfo)
                        {
                            @Override
                            protected void renderData(Writer out, ExpData data) throws IOException
                            {
                                Boolean val = (Boolean)getJsonValue(data);
                                out.write(val.toString());
                            }

                            @Override
                            protected Object getJsonValue(ExpData data)
                            {
                                return !(data == null || data.getFile() == null || !data.getFile().exists());
                            }
                        };
                    }
                });
                result.setUserEditable(false);
                result.setShownInUpdateView(false);
                result.setShownInInsertView(false);
                return result;
            }
            case FileExtension:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setJdbcType(JdbcType.VARCHAR);
                result.setTextAlign("left");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ExpDataFileColumn(colInfo)
                        {
                            @Override
                            protected void renderData(Writer out, ExpData data) throws IOException
                            {
                                Object val = getJsonValue(data);
                                out.write(val == null ? "" : PageFlowUtil.filter(val.toString()));
                            }

                            @Override
                            protected Object getJsonValue(ExpData data)
                            {
                                return data.getFile() == null ? null : FileUtil.getExtension(data.getFile());
                            }
                        };
                    }
                });
                result.setUserEditable(false);
                result.setShownInUpdateView(false);
                result.setShownInInsertView(false);
                return result;
            }
            case ViewOrDownload:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setLabel("View/Download");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ViewOrDownloadDataColumn(colInfo);
                    }
                });
                result.setDescription("Displays links to either download the file or view directly on the web site");
                return result;
            }
            case Generated:
                return wrapColumn(alias, _rootTable.getColumn("Generated"));

            case Inputs:
                return createLineageColumn(this, alias, true);

            case Outputs:
                return createLineageColumn(this, alias, true);

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public void setExperiment(ExpExperiment experiment)
    {
        if (getExperiment() != null)
            throw new IllegalArgumentException("Attempt to unset experiment");
        if (experiment == null)
            return;
        SQLFragment condition = new SQLFragment("exp.Data.RunId IN "
                + " ( SELECT ExperimentRunId FROM exp.RunList "
                + " WHERE ExperimentId = ? )");
        condition.add(experiment.getRowId());
        addCondition(condition);
        _experiment = experiment;
    }

    public ExpExperiment getExperiment()
    {
        return _experiment;
    }

    public void setRun(ExpRun run)
    {
        if (_runSpecified)
            throw new IllegalArgumentException("Cannot unset run");
        _runSpecified = true;
        _run = run;
        if (run == null)
        {
            addCondition(new SQLFragment("(RunId IS NULL)"), FieldKey.fromParts("RunId"));
        }
        else
        {
            addCondition(_rootTable.getColumn("RunId"), run.getRowId());
        }
    }

    public ExpRun getRun()
    {
        return _run;
    }

    public ColumnInfo addDataInputColumn(String alias, String role)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.datainput.dataid)" +
                "\nFROM exp.datainput" +
                "\nWHERE " + ExprColumn.STR_TABLE_ALIAS +  ".SourceApplicationId = exp.datainput.TargetApplicationId" +
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
        ExprColumn ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
        return doAdd(ret);
    }

    public ColumnInfo addMaterialInputColumn(String alias, SamplesSchema schema, String pdRole, final ExpSampleSet ss)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(InputMaterial.RowId)" +
            "\nFROM exp.materialInput" +
            "\nINNER JOIN exp.material AS InputMaterial ON exp.materialInput.materialId = InputMaterial.RowId" +
            "\nWHERE " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId = exp.materialInput.TargetApplicationId");
        if (ss != null)
        {
            sql.append("\nAND InputMaterial.CPASType = ?");
            sql.add(ss.getLSID());
        }
        sql.append(")");
        ExprColumn ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
        ret.setFk(schema.materialIdForeignKey(ss, null));
        return doAdd(ret);
    }

    public DataType getDataType()
    {
        return _type;
    }

    public void setDataType(DataType type)
    {
        _type = type;
        getFilter().deleteConditions(FieldKey.fromParts("LSID"));
        if (_type != null)
        {
            addCondition(new SQLFragment("LSID LIKE " + getSqlDialect().concatenate("'urn:lsid:%:'", "?", "'%'"), _type.getNamespacePrefix()), FieldKey.fromParts("LSID"));
        }
    }

    public void setDataClass(ExpDataClass dataClass)
    {
        _dataClass = dataClass;
        getFilter().deleteConditions(FieldKey.fromParts("classId"));
        if (_dataClass != null)
        {
            addCondition(getColumn("classId"), _dataClass.getRowId());
        }
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        DataType type = getDataType();
        if (type != null)
            ret = type.urlFlag(flagged);
        if (ret != null)
            return ret;
        return super.urlFlag(flagged);
    }


    public ColumnInfo addInputRunCountColumn(String alias)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(DISTINCT exp.ProtocolApplication.RunId) " +
                "FROM exp.ProtocolApplication INNER JOIN Exp.DataInput ON exp.ProtocolApplication.RowId = Exp.DataInput.TargetApplicationId " +
                "WHERE Exp.DataInput.DataId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId)");
        ColumnInfo ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
        return doAdd(ret);
    }

    private class ThumbnailDataLinkColumn extends ViewFileDataLinkColumn
    {
        public ThumbnailDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        protected void renderData(Writer out, ExpData data) throws IOException
        {
            renderThumbnailPopup(out, data, getURL(data));
        }
    }

    private class InlineThumbnailDataLinkColumn extends ViewFileDataLinkColumn
    {
        public InlineThumbnailDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        protected void renderData(Writer out, ExpData data) throws IOException
        {
            out.write(renderThumbnailImg(data, getURL(data)));
        }
    }

    private class DownloadFileDataLinkColumn extends DataLinkColumn
    {
        public DownloadFileDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        protected ActionURL getURL(ExpData data)
        {
            return ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(data, false);
        }
    }

    private class ViewFileDataLinkColumn extends DataLinkColumn
    {
        public ViewFileDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        protected ActionURL getURL(ExpData data)
        {
            return ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(data, true);
        }
    }

    private class ViewContentDataLinkColumn extends DataLinkColumn
    {
        public ViewContentDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        protected ActionURL getURL(ExpData data)
        {
            return data.findDataHandler().getContentURL(data);
        }
    }

    private class ViewOrDownloadDataColumn extends DataLinkColumn
    {
        public ViewOrDownloadDataColumn (ColumnInfo colInfo)
        {
            super(colInfo);
        }

        protected ActionURL getURL(ExpData data)
        {
            return ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(data, true);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            ExpData data = getData(ctx);
            if (data != null)
            {
                if (data.isFileOnDisk())
                {
                    out.write(PageFlowUtil.textLink("View File", ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(data, true)));
                    out.write("<br>");
                    out.write(PageFlowUtil.textLink("Download", ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(data, false)));
                }
                else
                {
                    out.write("File not available");
                }
            }
        }
    }
}
