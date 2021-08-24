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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.labkey.api.exp.query.ExpSchema.TableType.DataClasses;

public class ExpDataTableImpl extends ExpRunItemTableImpl<ExpDataTable.Column> implements ExpDataTable
{
    protected ExpExperiment _experiment;
    protected boolean _runSpecified;
    protected ExpRun _run;
    protected DataType _type;
    protected ExpDataClass _dataClass;

    public ExpDataTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoData(), schema, cf);

        addAllowablePermission(UpdatePermission.class);
        addAllowablePermission(InsertPermission.class);
    }

    @Override
    protected void populateColumns()
    {
        addColumn(Column.RowId).setHidden(true);
        addColumn(Column.Name);
        addColumn(Column.Description);
        var aliasCol = addColumn(Column.Alias);
        aliasCol.setHidden(true);
        addColumn(Column.DataClass);
        ExpSchema schema = getExpSchema();
        addColumn(Column.Run).setFk(schema.getRunIdForeignKey(getContainerFilter()));
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
        getMutableColumn(Column.RowId).setURL(detailsURL);
        getMutableColumn(Column.Name).setURL(detailsURL);

        ActionURL deleteUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteDatasURL(getContainer(), null);
        setDeleteURL(new DetailsURL(deleteUrl));

        addVocabularyDomains();
        addColumn(Column.Properties);

        var colInputs = addColumn(Column.Inputs);
        addMethod("Inputs", new LineageMethod(getContainer(), colInputs, true));

        var colOutputs = addColumn(Column.Outputs);
        addMethod("Outputs", new LineageMethod(getContainer(), colOutputs, false));
    }

    public List<String> addFileColumns(boolean isFilesTable)
    {
        List<String> customProps = new ArrayList<>();
        var lsidColumn = addColumn(Column.LSID);
        lsidColumn.setHidden(true);
        if (!isFilesTable)
            addColumn(Column.DataFileUrl);

        addColumn(Column.DownloadLink);
        addColumn(Column.ViewFileLink);
        addColumn(Column.FileExists);
        addColumn(Column.FileSize);
        addColumn(Column.FileExtension);
        addColumn(Column.WebDavUrl);
        addColumn(Column.WebDavUrlRelative);
        var flagCol = addColumn(Column.Flag);
        if (isFilesTable)
            flagCol.setLabel("Description");

        FileContentService svc = FileContentService.get();
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
                    var projectColumn = new PropertyColumn(prop.getPropertyDescriptor(), lsidColumn, getContainer(), _userSchema.getUser(), false);
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
        checkLocked();
        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(Column.Name));
        defaultCols.add(FieldKey.fromParts(Column.Run));
        defaultCols.add(FieldKey.fromParts(Column.DataFileUrl));
        setDefaultVisibleColumns(defaultCols);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        FileContentService svc = FileContentService.get();
        return svc.getFilePropsUpdateService(this, getContainer());
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        return super.resolveColumn(name);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
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
                var dataFileUrl = wrapColumn(alias, _rootTable.getColumn("DataFileUrl"));
                dataFileUrl.setUserEditable(false);
                return dataFileUrl;
            case LSID:
                var lsid = wrapColumn(alias, _rootTable.getColumn("LSID"));
                lsid.setUserEditable(false);
                return lsid;
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Description:
                return wrapColumn(alias, _rootTable.getColumn("Description"));
            case LastIndexed:
                var lastIndexed = wrapColumn(alias, _rootTable.getColumn("LastIndexed"));
                lastIndexed.setUserEditable(false);
                return lastIndexed;
            case DataClass:
            {
                var c = wrapColumn(alias, _rootTable.getColumn("classId"));
                c.setUserEditable(false);
                var fk = QueryForeignKey.from(getUserSchema(), getContainerFilter())
                        .to(DataClasses.name(), "RowId", "Name");
                c.setFk( fk );
                return c;
            }
            case Protocol:
            {
                ExprColumn col = new ExprColumn(this, Column.Protocol.toString(), new SQLFragment(
                        "(SELECT ProtocolLSID FROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                        " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), JdbcType.VARCHAR, getColumn(Column.SourceProtocolApplication));
                col.setFk(getExpSchema().getProtocolForeignKey(getContainerFilter(),"LSID"));
                col.setSqlTypeName("lsidtype");
                col.setHidden(true);
                return col;
            }
            case SourceProtocolApplication:
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                columnInfo.setUserEditable(false);
                columnInfo.setHidden(true);
                return columnInfo;
            }

            case SourceApplicationInput:
            {
                var col = createEdgeColumn(alias, Column.SourceProtocolApplication, ExpSchema.TableType.DataInputs);
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

                var col = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
                col.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                col.setDescription("Contains a reference to the ExperimentRunOutput protocol application of the run that created this data");
                col.setUserEditable(false);
                col.setReadOnly(true);
                col.setHidden(true);
                return col;
            }

            case RunApplicationOutput:
            {
                var col = createEdgeColumn(alias, Column.RunApplication, ExpSchema.TableType.DataInputs);
                col.setDescription("Contains a reference to the DataInput row between this ExpData and it's RunOutputApplication");
                return col;
            }
            case RowId:
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                ret.setFk(new RowIdForeignKey(ret));
                ret.setHidden(true);
                return ret;
            }
            case Run:
                var runId = wrapColumn(alias, _rootTable.getColumn("RunId"));
                runId.setUserEditable(false);
                return runId;
            case Flag:
                return createFlagColumn(alias);
            case Alias:
                return createAliasColumn(alias, ExperimentService.get()::getTinfoDataAliasMap);
            case DownloadLink:
            {
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setTextAlign("left");
                result.setJdbcType(JdbcType.VARCHAR);
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setJdbcType(JdbcType.BOOLEAN);
                result.setTextAlign("left");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                                if (data == null)
                                {
                                    return false;
                                }
                                Path path = data.getFilePath();
                                return path != null && Files.exists(path);
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setJdbcType(JdbcType.VARCHAR);
                result.setTextAlign("left");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
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
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setLabel("View/Download");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ViewOrDownloadDataColumn(colInfo);
                    }
                });
                result.setDescription("Displays links to either download the file or view directly on the web site");
                return result;
            }
            case WebDavUrl:
            {
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setJdbcType(JdbcType.VARCHAR);
                result.setLabel("WebDAV URL");
                result.setDescription("This is the full WebDAV URL to this file");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new WebDavUrlColumn(colInfo, false);
                    }
                });
                return result;
            }
            case WebDavUrlRelative:
            {
                var result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setJdbcType(JdbcType.VARCHAR);
                result.setLabel("Relative WebDAV URL");
                result.setDescription("This is the WebDAV path of this file, relative to the file root of this container");
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new WebDavUrlColumn(colInfo, true);
                    }
                });
                return result;
            }
            case Generated:
                return wrapColumn(alias, _rootTable.getColumn("Generated"));

            case Inputs:
                return createLineageColumn(this, alias, true);

            case Outputs:
                return createLineageColumn(this, alias, true);

            case Properties:
                return (BaseColumnInfo) createPropertiesColumn(alias);

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public void setExperiment(ExpExperiment experiment)
    {
        checkLocked();
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

    @Override
    public ExpExperiment getExperiment()
    {
        return _experiment;
    }

    @Override
    public void setRun(ExpRun run)
    {
        checkLocked();
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

    @Override
    public ExpRun getRun()
    {
        return _run;
    }

    @Override
    public MutableColumnInfo addDataInputColumn(String alias, String role)
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

    @Override
    public MutableColumnInfo addMaterialInputColumn(String alias, SamplesSchema schema, String pdRole, final ExpSampleType sampleType)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(InputMaterial.RowId)" +
            "\nFROM exp.materialInput" +
            "\nINNER JOIN exp.material AS InputMaterial ON exp.materialInput.materialId = InputMaterial.RowId" +
            "\nWHERE " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId = exp.materialInput.TargetApplicationId");
        if (sampleType != null)
        {
            sql.append("\nAND InputMaterial.CPASType = ?");
            sql.add(sampleType.getLSID());
        }
        sql.append(")");
        ExprColumn ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
        ret.setFk(schema.materialIdForeignKey(sampleType, null));
        return doAdd(ret);
    }

    @Override
    public DataType getDataType()
    {
        return _type;
    }

    @Override
    public void setDataType(DataType type)
    {
        checkLocked();
        _type = type;
        getFilter().deleteConditions(FieldKey.fromParts("LSID"));
        if (_type != null)
        {
            addCondition(new SQLFragment("LSID LIKE " + getSqlDialect().concatenate("'urn:lsid:%:'", "?", "'%'"), _type.getNamespacePrefix()), FieldKey.fromParts("LSID"));
        }
    }

    public void setDataClass(ExpDataClass dataClass)
    {
        checkLocked();
        _dataClass = dataClass;
        getFilter().deleteConditions(FieldKey.fromParts("classId"));
        if (_dataClass != null)
        {
            addCondition(getColumn("classId"), _dataClass.getRowId());
        }
    }

    @Override
    public MutableColumnInfo addInputRunCountColumn(String alias)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(DISTINCT exp.ProtocolApplication.RunId) " +
                "FROM exp.ProtocolApplication INNER JOIN Exp.DataInput ON exp.ProtocolApplication.RowId = Exp.DataInput.TargetApplicationId " +
                "WHERE Exp.DataInput.DataId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId)");
        var ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
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

        @Override
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

        @Override
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

        @Override
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

        @Override
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

    private class WebDavUrlColumn extends ExpDataFileColumn
    {
        private boolean _relative;

        public WebDavUrlColumn (ColumnInfo colInfo, boolean relative)
        {
            super(colInfo);
            setTextAlign("left");
            _relative = relative;
        }

        @Override
        public Object getJsonValue(ExpData data)
        {
            if (data == null)
                return null;

            return data.getWebDavURL(_relative ? ExpData.PathType.folderRelative : ExpData.PathType.serverRelative);
        }

        @Override
        protected void renderData(Writer out, ExpData data) throws IOException
        {
            Object val = getJsonValue(data);
            out.write(val == null ? "" : PageFlowUtil.filter(val.toString()));
        }
    }

    public static class TestCase extends Assert
    {
        public static final String PROJECT_NAME = "ExptDataTableImplIntegrationTest";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            doInitialSetUp(PROJECT_NAME);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        private class WebDavTestCase
        {
            private ExpData _data;
            private String _relativePath;

            public WebDavTestCase(String name, File file, String relativePath)
            {
                _data = ExperimentService.get().createData(getProject(), new DataType("TestData"));
                _data.setDataFileURI(file.toURI());
                _data.setName(name);
                _data.save(getUser());

                _relativePath = relativePath;
            }

            public String getUrl()
            {
                if (_relativePath == null)
                {
                    return null;
                }

                PipeRoot pr = PipelineService.get().getPipelineRootSetting(getProject());

                return pr.getWebdavURL() + getUrlRelative();
            }

            public String getUrlRelative()
            {
                return _relativePath;
            }
        }

        @Test
        public void testWebDavColumns() throws Exception
        {
            PipeRoot pr = PipelineService.get().getPipelineRootSetting(getProject());
            if (pr == null)
            {
                throw new ConfigurationException("File root is not set");
            }

            List<WebDavTestCase> testCases = new ArrayList<>();

            final String PATH1 = "webDavTest.txt";
            testCases.add(new WebDavTestCase("TestData1", new File(pr.getRootPath(), PATH1), PATH1));
            testCases.add(new WebDavTestCase("TestData2", new File(pr.getRootPath(), "subfolder/webDavTest.txt"), "subfolder/webDavTest.txt"));
            testCases.add(new WebDavTestCase("TestData3", new File(pr.getRootPath(), "subfolder/webD  avT est.txt"), "subfolder/webD%20%20avT%20est.txt"));

            PipeRoot prHome = PipelineService.get().getPipelineRootSetting(ContainerManager.getHomeContainer());
            testCases.add(new WebDavTestCase("NotUnderFolderRoot", new File(prHome.getRootPath(), PATH1), null));

            testCases.add(new WebDavTestCase("NotUnderFileRoot", new File("/", PATH1), null));

            UserSchema us = QueryService.get().getUserSchema(getUser(), getProject(), ExpSchema.SCHEMA_NAME);

            for (final WebDavTestCase tc : testCases)
            {
                BindException errors = new NullSafeBindException(new Object(), "command");
                MutablePropertyValues mpv = new MutablePropertyValues();
                mpv.addPropertyValue("schemaName", ExpSchema.SCHEMA_NAME);
                mpv.addPropertyValue("query.queryName", ExpSchema.TableType.Data.name());
                List<FieldKey> fieldKeys = Arrays.asList("RowId", "Name", Column.WebDavUrl.name(), Column.WebDavUrlRelative.name()).stream().map(FieldKey::fromString).collect(Collectors.toList());
                mpv.addPropertyValue("query.columns", StringUtils.join(fieldKeys, ","));

                QuerySettings qs = us.getSettings(mpv, "query");

                qs.setBaseFilter(new SimpleFilter(FieldKey.fromString("RowId"), tc._data.getRowId()));
                QueryView view = new QueryView(us, qs, errors);

                ExtendedApiQueryResponse resp = new ExtendedApiQueryResponse(view, false, false, "auditLog", "ContainerAuditEvent", 0, fieldKeys, false, false, false);
                Writer writer = new StringWriter();
                ApiResponseWriter apiWriter = new ApiJsonWriter(writer);
                resp.render(apiWriter);
                JSONObject json = new JSONObject(writer.toString());
                JSONObject row = json.getJSONArray("rows").getJSONObject(0);

                String webDavUrl = row.getJSONObject("WebDavUrl").getString("value");
                String webDavUrlRelative = row.getJSONObject("WebDavUrlRelative").getString("value");

                assertEquals("Incorrect WebDavUrlRelative", tc.getUrlRelative(), webDavUrlRelative);
                assertEquals("Incorrect WebDavUrl", tc.getUrl(), webDavUrl);

                try (ExcelWriter excel = view.getExcelWriter(ExcelWriter.ExcelDocumentType.xlsx))
                {
                    VirtualFile f = new MemoryVirtualFile();

                    try (OutputStream os = f.getOutputStream("excel.xlsx"))
                    {
                        excel.write(os);
                    }

                    try (ExcelLoader loader = new ExcelLoader(f.getInputStream("excel.xlsx"), true, getProject()))
                    {
                        loader.iterator().forEachRemaining(map -> {
                            Object excelWebdavUrl = map.get("WebDAV URL");
                            Object excelWebdavUrlRelative = map.get("Relative WebDAV URL");
                            assertEquals("Incorrect WebDavUrl", tc.getUrl(), excelWebdavUrl);
                            assertEquals("Incorrect WebDavUrlRelative", tc.getUrlRelative(), excelWebdavUrlRelative);
                        });
                    }
                }
            }
        }

        protected static void doInitialSetUp(String projectName)
        {
            //pre-clean
            doCleanup(projectName);

            Container project = ContainerManager.getForPath(projectName);
            if (project == null)
            {
                ContainerManager.createContainer(ContainerManager.getRoot(), projectName);
            }
        }

        protected static void doCleanup(String projectName)
        {
            Container project = ContainerManager.getForPath(projectName);
            if (project != null)
            {
                ContainerManager.delete(project, getUser());
            }
        }

        protected static User getUser()
        {
            return TestContext.get().getUser();
        }

        protected Container getProject()
        {
            return ContainerManager.getForPath(PROJECT_NAME);
        }
    }
}
