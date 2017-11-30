package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class ExpFilesTableImpl extends ExpDataTableImpl
{
    protected FileContentService _svc = ServiceRegistry.get().getService(FileContentService.class);

    public ExpFilesTableImpl(String name, UserSchema schema)
    {
        super(name, schema);
        addCondition(new SimpleFilter(FieldKey.fromParts("DataFileUrl"), null, CompareType.NONBLANK));
        _svc.ensureFileData(getUpdateService(), schema.getUser(), schema.getContainer());
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    public void populate()
    {
        addColumn(Column.RowId).setHidden(true);
        ColumnInfo nameCol = addColumn(Column.Name);
        nameCol.setUserEditable(true);
        nameCol.setShownInUpdateView(false);
        nameCol.setShownInDetailsView(true);
        nameCol.setShownInInsertView(false);

        ExpSchema schema = getExpSchema();
        ColumnInfo runCol = addColumn(Column.Run);
        runCol.setFk(schema.getRunIdForeignKey());
        runCol.setUserEditable(true);
        runCol.setShownInUpdateView(false);
        runCol.setShownInDetailsView(true);
        runCol.setShownInInsertView(false);

        addContainerColumn(Column.Folder, null);

        List<String> customProps = addFileColumns(true);
        if (showAbsoluteFilePath())
        {
            ColumnInfo dataFileUrlCol = addColumn(Column.DataFileUrl);
            dataFileUrlCol.setUserEditable(true);
            dataFileUrlCol.setShownInInsertView(false);
            dataFileUrlCol.setShownInUpdateView(false);
            addColumn(getAbsolutePathColumn());
        }

        addColumn(getRelativeFolderColumn());

        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);

        setDefaultColumns(customProps);
        setTitleColumn("Name");

        DetailsURL detailsURL = DetailsURL.fromString("/query/detailsQueryRow.view?schemaName=exp&query.queryName=Files&RowId=${rowId}");
        setDetailsURL(detailsURL);
        getColumn(Column.RowId).setURL(detailsURL);
        getColumn(Column.Name).setURL(detailsURL);
        ActionURL deleteUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteDatasURL(getContainer(), null);
        setDeleteURL(new DetailsURL(deleteUrl));
    }

    public void setDefaultColumns(List<String> customProps)
    {
        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(Column.Name));
        defaultCols.add(FieldKey.fromParts(Column.Run));
        defaultCols.add(FieldKey.fromParts(Column.DownloadLink));
        defaultCols.add(FieldKey.fromParts(Column.FileExists));
        defaultCols.add(FieldKey.fromParts(Column.FileSize));
        defaultCols.add(FieldKey.fromParts(Column.Flag));
        defaultCols.add(FieldKey.fromParts("RelativeFolder"));
        customProps.forEach(prop -> defaultCols.add(FieldKey.fromParts(prop)));
        if (showAbsoluteFilePath())
        {
            defaultCols.add(FieldKey.fromParts("AbsoluteFilePath"));
        }
        setDefaultVisibleColumns(defaultCols);
    }

    protected boolean showAbsoluteFilePath()
    {
        Container container = getUserSchema().getContainer();
        return (null != container && SecurityManager.canSeeFilePaths(container, getUserSchema().getUser()));
    }

    private ColumnInfo getAbsolutePathColumn()
    {
        ColumnInfo result = wrapColumn("AbsoluteFilePath", _rootTable.getColumn("RowId"));
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
                    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
                    {
                        out.write("<input type=\"text\" class=\"form-control\" name=\"quf_AbsoluteFilePath\" size=\"40\">");
                    }

                    @Override
                    protected Object getJsonValue(ExpData data)
                    {
                        String val;
                        if (data == null || data.getFile() == null || !data.getFile().exists())
                            val = "";
                        else
                        {
                            val = data.getFile().getAbsolutePath();
                        }
                        return val;
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return true;
                    }

                };
            }
        });
        result.setDescription("The absolute file path of the file record.");
        result.setUserEditable(true);
        result.setShownInUpdateView(false);
        result.setShownInInsertView(true);
        result.setShownInDetailsView(true);

        return result;
    }

    private ColumnInfo getRelativeFolderColumn()
    {
        ColumnInfo result = wrapColumn("RelativeFolder", _rootTable.getColumn("RowId"));
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
                        out.write(val == null ? "Not found" : val);
                    }

                    @Override
                    protected Object getJsonValue(ExpData data)
                    {
                        String val;
                        if (data == null || StringUtils.isEmpty(data.getDataFileUrl()))
                            val = null;
                        else
                        {
                            val = _svc.getDataFileRelativeFileRootPath(data.getDataFileUrl(), getContainer());
                        }
                        return val;
                    }

                };
            }
        });
        result.setDescription("The virtual folder path relative to file root of the container.");
        result.setUserEditable(true);
        result.setShownInUpdateView(false);
        result.setShownInInsertView(false);
        result.setShownInDetailsView(true);

        return result;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (perm.equals(InsertPermission.class))
            if (!showAbsoluteFilePath())
                return false;
        return super.hasPermission(user, perm);
    }

}
