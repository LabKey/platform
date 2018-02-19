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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.MultiValuedLookupColumn;
import org.labkey.api.data.MultiValuedRenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpDataImpl extends AbstractProtocolOutputImpl<Data> implements ExpData
{
    public static final SearchService.SearchCategory expDataCategory = new SearchService.SearchCategory("data", "ExpData");


    /**
     * Temporary mapping until experiment.xml contains the mime type
     */
    private static MimeMap MIME_MAP = new MimeMap();

    static public List<ExpDataImpl> fromDatas(List<Data> datas)
    {
        List<ExpDataImpl> ret = new ArrayList<>(datas.size());
        for (Data data : datas)
        {
            ret.add(new ExpDataImpl(data));
        }
        return ret;
    }

    public ExpDataImpl(Data data)
    {
        super(data);
    }

    @Override
    public void setComment(User user, String comment) throws ValidationException
    {
        super.setComment(user, comment);
        index(null);
    }

    @Nullable
    public URLHelper detailsURL()
    {
        DataType dataType = getDataType();
        if (dataType == null)
            return null;

        return dataType.getDetailsURL(this);
    }

    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("DataId"), getRowId()), ExperimentServiceImpl.get().getTinfoDataInput());
    }

    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoDataInput(), "DataId");
    }

    public DataType getDataType()
    {
        return ExperimentService.get().getDataType(getLSIDNamespacePrefix());
    }

    public void setDataFileURI(URI uri)
    {
        ensureUnlocked();
        if (uri != null && !uri.isAbsolute())
        {
            throw new IllegalArgumentException("URI must be absolute.");
        }
        String s = FileUtil.uriToString(uri);

        // Strip off any trailing "/"
        if (s != null && s.endsWith("/"))
        {
            s = s.substring(0, s.length() - 1);
        }
        _object.setDataFileUrl(s);
    }

    public void save(User user)
    {
        // Replace the default "Data" cpastype if the Data belongs to a DataClass
        ExpDataClassImpl dataClass = getDataClass();
        if (dataClass != null && DEFAULT_CPAS_TYPE.equals(getCpasType()))
           setCpasType(dataClass.getLSID());

        boolean isNew = getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoData());

        if (isNew)
        {
            if (dataClass != null)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("lsid", getLSID());
                Table.insert(user, dataClass.getTinfo(), map);
            }
        }
        index(null);
    }

    public URI getDataFileURI()
    {
        String url = _object.getDataFileUrl();
        if (url == null)
            return null;
        try
        {
            return new URI(_object.getDataFileUrl());
        }
        catch (URISyntaxException use)
        {
            return null;
        }
    }

    public ExperimentDataHandler findDataHandler()
    {
        return Handler.Priority.findBestHandler(ExperimentServiceImpl.get().getExperimentDataHandlers(), this);
    }

    public String getDataFileUrl()
    {
        return _object.getDataFileUrl();
    }

    public boolean hasFileScheme()
    {
        return !FileUtil.hasCloudScheme(getDataFileUrl());
    }

    @Nullable
    public File getFile()
    {
        return _object.getFile();
    }

    @Nullable
    public java.nio.file.Path getFilePath()
    {
        return _object.getFilePath();
    }

    public boolean isInlineImage()
    {
        return null != getFile() && MIME_MAP.isInlineImageFor(getFile());
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        if (getLSID() != null)
        {
            DataType type = getDataType();
            if (type != null)
            {
                ret = type.urlFlag(flagged);
            }
            if (ret != null)
                return ret;
        }
        if (flagged)
        {
            return AppProps.getInstance().getContextPath() + "/Experiment/flagData.png";
        }
        return AppProps.getInstance().getContextPath() + "/Experiment/images/unflagData.png";
    }

    public void delete(User user)
    {
        delete(user, true);
    }

    public void delete(User user, boolean deleteRunsUsingData)
    {
        try
        {
            ExperimentServiceImpl.get().deleteDataByRowIds(user, getContainer(), Collections.singleton(getRowId()), deleteRunsUsingData);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeValidationException(e);
        }
    }

    public String getMimeType()
    {
        if (null != getDataFileUrl())
            return MIME_MAP.getContentTypeFor(getDataFileUrl());
        else
            return null;
    }

    public boolean isFileOnDisk()
    {
        java.nio.file.Path f = getFilePath();
        if (f != null)
            if (!FileUtil.hasCloudScheme(f))
                return NetworkDrive.exists(f.toFile()) && !Files.isDirectory(f);
            else
                return Files.exists(f);
        else
            return false;
    }

    public boolean isPathAccessible()
    {
        java.nio.file.Path path = getFilePath();
        return (null != path && Files.exists(path));
    }

    public String getCpasType()
    {
        String result = _object.getCpasType();
        if (result != null)
            return result;

        ExpDataClass dataClass = getDataClass();
        if (dataClass != null)
            return dataClass.getLSID();

        return ExpData.DEFAULT_CPAS_TYPE;
    }

    public void setGenerated(boolean generated)
    {
        ensureUnlocked();
        _object.setGenerated(generated);
    }

    @Override
    public boolean isGenerated()
    {
        return _object.isGenerated();
    }

    @Override
    public boolean isFinalRunOutput()
    {
        ExpRun run = getRun();
        if (run == null)
            return false;
        else
            return run.isFinalOutput(this);
    }

    @Override
    @Nullable
    public ExpDataClassImpl getDataClass()
    {
        if (_object.getClassId() != null)
            return ExperimentServiceImpl.get().getDataClass(_object.getClassId());

        return null;
    }

    public void importDataFile(PipelineJob job, XarSource xarSource) throws ExperimentException
    {
        String dataFileURL = getDataFileUrl();
        if (dataFileURL == null)
            return;

        if (xarSource.shouldIgnoreDataFiles())
        {
            job.debug("Skipping load of data file " + dataFileURL + " based on the XAR source");
            return;
        }

        job.debug("Trying to load data file " + dataFileURL + " into the system");

        java.nio.file.Path path = FileUtil.stringToPath(getContainer(), dataFileURL);

        if (!Files.exists(path))
        {
            job.debug("Unable to find the data file " + FileUtil.getAbsolutePath(getContainer(), path) + " on disk.");
            return;
        }

        // Check that the file is under the pipeline root to prevent users from referencing a file that they
        // don't have permission to import
        PipeRoot pr = PipelineService.get().findPipelineRoot(job.getContainer());
        if (!xarSource.allowImport(pr, job.getContainer(), path))
        {
            if (pr == null)
            {
                job.warn("No pipeline root was set, skipping load of file " + FileUtil.getAbsolutePath(getContainer(), path));
                return;
            }
            job.debug("The data file " + FileUtil.getAbsolutePath(getContainer(), path) + " is not under the folder's pipeline root: " + pr + ". It will not be loaded directly, but may be loaded if referenced from other files that are under the pipeline root.");
            return;
        }

        ExperimentDataHandler handler = findDataHandler();
        try
        {
            handler.importFile(this, path, job.getInfo(), job.getLogger(), xarSource.getXarContext());
        }
        catch (ExperimentException e)
        {
            throw new XarFormatException(e);
        }

        job.debug("Finished trying to load data file " + dataFileURL + " into the system");
    }

    // Get all text strings from the data class for indexing
    @NotNull
    private List<String> getIndexValues()
    {
        ExpDataClassImpl dc = this.getDataClass();
        if (dc == null)
            return Collections.emptyList();

        TableInfo table = QueryService.get().getUserSchema(User.getSearchUser(), getContainer(), "exp.data").getTable(dc.getName());
        if (table == null)
            return Collections.emptyList();

        // collect the set of columns to index
        Set<ColumnInfo> columns = table.getExtendedColumns(true).values().stream().filter(col -> {
            // skip the base-columns - they will be added to the index separately
            final String name = col.getName();
            try
            {
                ExpDataClassDataTable.Column x = ExpDataClassDataTable.Column.valueOf(name);
                return false;
            }
            catch (IllegalArgumentException ex)
            {
                // ok
            }

            // skip non-text columns or columns that aren't lookups
            if (!(col.getJdbcType().isText() || col.getFk() != null))
                return false;

            if (name.equalsIgnoreCase("container") || name.equalsIgnoreCase("folder"))
                return false;

            return true;
        }).collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> values = new ArrayList<>();
        TableSelector ts = new TableSelector(table, columns, new SimpleFilter("rowId", getRowId()), null);
        ts.setForDisplay(true);
        try (Results r = ts.getResults())
        {
            Map<FieldKey, ColumnInfo> fields = r.getFieldMap();
            if (r.next())
            {
                Map<FieldKey, Object> map = r.getFieldKeyRowMap();
                for (Map.Entry<FieldKey, ColumnInfo> entry : fields.entrySet())
                {
                    FieldKey fieldKey = entry.getKey();
                    ColumnInfo col = entry.getValue();
                    if (!col.getJdbcType().isText())
                        continue;

                    if (col.getName().equalsIgnoreCase("lsid") || col.getSqlTypeName().equalsIgnoreCase("lsidtype") || col.getSqlTypeName().equalsIgnoreCase("entityid"))
                        continue;

                    Object o = map.get(fieldKey);
                    if (!(o instanceof String))
                        continue;

                    String s = (String)o;
                    if (col instanceof MultiValuedLookupColumn)
                        values.addAll(Arrays.asList(s.split(MultiValuedRenderContext.VALUE_DELIMITER_REGEX)));
                    else
                        values.add(s);
                }
            }

        }
        catch (SQLException e)
        {
            // ignore
        }
        return values;
    }

    @NotNull
    public Collection<String> getAliases()
    {
        TableInfo mapTi = ExperimentService.get().getTinfoDataAliasMap();
        TableInfo ti = ExperimentService.get().getTinfoAlias();
        SQLFragment sql = new SQLFragment()
                .append("SELECT a.name FROM ").append(mapTi, "m")
                .append(" JOIN ").append(ti, "a")
                .append(" ON m.alias = a.RowId WHERE m.lsid = ? ");
        sql.add(getLSID());
        ArrayList<String> aliases = new SqlSelector(mapTi.getSchema(), sql).getArrayList(String.class);
        return Collections.unmodifiableList(aliases);
    }

    public String getDocumentId()
    {
        String dataClassName = "-";
        ExpDataClass dc = getDataClass();
        if (dc != null)
            dataClassName = dc.getName();
        return "data:" + new Path(getContainer().getId(), dataClassName, Integer.toString(getRowId()));
    }

    @Nullable
    public static ExpDataImpl fromDocumentId(String resourceIdentifier)
    {
        if (resourceIdentifier.startsWith("data:"))
            resourceIdentifier = resourceIdentifier.substring("data:".length());

        Path path = Path.parse(resourceIdentifier);
        if (path.size() != 3)
            return null;
        String containerId = path.get(0);
        String dataClassName = path.get(1);
        String rowIdString = path.get(2);

        Container c = ContainerManager.getForId(containerId);
        if (c == null)
            return null;

        ExpDataClass dc = null;
        if (dataClassName.length() > 0 && !dataClassName.equals("-"))
            dc = ExperimentService.get().getDataClass(c, dataClassName);
        Integer rowId;
        try
        {
            rowId = Integer.parseInt(rowIdString);
        }
        catch (NumberFormatException ex)
        {
            return null;
        }

        if (dc != null)
            return ExperimentServiceImpl.get().getExpData(dc, rowId);
        else
            return ExperimentServiceImpl.get().getExpData(rowId);
    }

    public void index(SearchService.IndexTask task)
    {
        if (task == null)
        {
            SearchService ss = SearchService.get();
            if (null == ss)
                return;
            task = ss.defaultTask();
        }

        WebdavResource doc = createDocument();
        task.addResource(doc, SearchService.PRIORITY.item);
    }

    public WebdavResource createDocument()
    {
        Map<String, Object> props = new HashMap<>();
        Set<String> keywords = new HashSet<>();
        Set<String> identifiers = new HashSet<>();

        // Add name to title
        if (null != getDescription())
            keywords.add(getDescription());

        String comment = getComment();
        if (comment != null)
            keywords.add(comment);

        StringBuilder title = new StringBuilder(getName());
        identifiers.add(getName());

        // Add aliases in parenthesis in the title
        Collection<String> aliases = this.getAliases();
        if (!aliases.isEmpty())
        {
            title.append(" (").append(StringUtils.join(aliases, ", ")).append(")");
            identifiers.addAll(aliases);
        }

        List<String> indexValues = getIndexValues();
        keywords.addAll(indexValues);
        //identifiers.addAll(indexValues);

        ExpDataClass dc = this.getDataClass();
        if (null != dc)
        {
            ActionURL show = new ActionURL(ExperimentController.ShowDataClassAction.class,getContainer()).addParameter("rowId", dc.getRowId());
            NavTree t = new NavTree(dc.getName(), show);
            String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
            props.put(SearchService.PROPERTY.navtrail.toString(), nav);

            props.put(DataSearchResultTemplate.PROPERTY, dc.getName());
        }

        props.put(SearchService.PROPERTY.categories.toString(), expDataCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), title.toString());
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiers, " "));
        props.put(SearchService.PROPERTY.keywordsMed.toString(), StringUtils.join(keywords, " "));

        ActionURL view = ExperimentController.ExperimentUrlsImpl.get().getDataDetailsURL(this);
        view.setExtraPath(getContainer().getId());
        String docId = getDocumentId();

        // All identifiers (indexable text values) in the body
        StringBuilder sb = new StringBuilder(title)
                .append("\n")
                .append(keywords.stream().map(s -> s.length() > 30 ? s.substring(0, 30) + "\u2026" : s).collect(Collectors.joining(", ")))
                .append("\n")
                .append(identifiers.stream().map(s -> s.length() > 30 ? s.substring(0, 30) + "\u2026" : s).collect(Collectors.joining(", ")));
        String body;
        if (sb.length() > 120)
            body = sb.substring(0, 120) + "\u2026";
        else
            body = sb.toString();

        final int id = getRowId();
        return new ExpDataResource(
                id,
                new Path(docId),
                docId,
                getContainer().getId(),
                "text/plain",
                body,
                view, props);
    }

    private static class ExpDataResource extends SimpleDocumentResource
    {
        final int _rowId;

        public ExpDataResource(int rowId, Path path, String documentId, String containerId, String contentType, String body, URLHelper executeUrl, Map<String, Object> properties)
        {
            super(path, documentId, containerId, contentType, body, executeUrl, properties);
            _rowId = rowId;
        }

        @Override
        public void setLastIndexed(long ms, long modified)
        {
            ExperimentServiceImpl.get().setDataLastIndexed(_rowId, ms);
        }
    }

    public static class DataSearchResultTemplate implements SearchResultTemplate
    {
        public static final String NAME = "data";
        public static final String PROPERTY = "dataclass";

        @Nullable
        @Override
        public String getName()
        {
            return NAME;
        }

        @Nullable
        @Override
        public String getCategories()
        {
            return expDataCategory.getName();
        }

        @Nullable
        @Override
        public SearchScope getSearchScope()
        {
            return SearchScope.FolderAndSubfolders;
        }

        @NotNull
        @Override
        public String getResultNameSingular()
        {
            if (HttpView.hasCurrentView())
            {
                ViewContext ctx = HttpView.currentContext();
                String dataclass = ctx.getActionURL().getParameter(PROPERTY);
                if (dataclass != null)
                {
                    ExpDataClass dc = ExperimentService.get().getDataClass(ctx.getContainer(), ctx.getUser(), dataclass);
                    if (dc != null)
                        return dc.getName();
                }
            }

            return "data";
        }

        @NotNull
        @Override
        public String getResultNamePlural()
        {
            return getResultNameSingular();
        }

        @Override
        public boolean includeNavigationLinks()
        {
            return true;
        }

        @Override
        public boolean includeAdvanceUI()
        {
            return false;
        }

        @Nullable
        @Override
        public String getExtraHtml(ViewContext ctx)
        {
            String q = ctx.getActionURL().getParameter("q");

            if (StringUtils.isNotBlank(q))
            {
                String dataclass = ctx.getActionURL().getParameter(PROPERTY);
                ActionURL url = ctx.cloneActionURL().deleteParameter(PROPERTY);
                url.replaceParameter("_dc", String.valueOf((int)Math.round(1000 * Math.random())));

                StringBuilder html = new StringBuilder();
                html.append("<div class=\"labkey-search-filter\">");

                appendParam(html, null, dataclass, "All", false, url);
                for (ExpDataClass dc : ExperimentService.get().getDataClasses(ctx.getContainer(), ctx.getUser(), true))
                {
                    appendParam(html, dc.getName(), dataclass, dc.getName(), true, url);
                }

                html.append("</div>");
                return html.toString();
            }
            else
            {
                return null;
            }
        }

        private void appendParam(StringBuilder sb, @Nullable String dataclass, @Nullable String current, @NotNull String label, boolean addParam, ActionURL url)
        {
            sb.append("<span>");

            if (!Objects.equals(dataclass, current))
            {
                sb.append("<a href=\"");

                if (addParam)
                    url = url.clone().addParameter(PROPERTY, dataclass);

                sb.append(PageFlowUtil.filter(url));
                sb.append("\">");
                sb.append(PageFlowUtil.filter(label));
                sb.append("</a>");
            }
            else
            {
                sb.append(label);
            }

            sb.append("</span> ");
        }

        @Nullable
        @Override
        public String getHiddenInputsHtml(ViewContext ctx)
        {
            String dataclass = ctx.getActionURL().getParameter(PROPERTY);
            if (dataclass != null)
            {
                return "<input type='hidden' id='search-type' name='" + PROPERTY + "' value='" + PageFlowUtil.filter(dataclass) + "'>";
            }

            return null;
        }


        @Override
        public String reviseQuery(ViewContext ctx, String q)
        {
            String dataclass = ctx.getActionURL().getParameter(PROPERTY);

            if (null != dataclass)
                return "+(" + q + ") +" + PROPERTY + ":" + dataclass;
            else
                return q;
        }

        @Override
        public NavTree appendNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
        {
            NavTree tree = SearchResultTemplate.super.appendNavTrail(root, ctx, scope, category);

            String dataclass = ctx.getActionURL().getParameter(PROPERTY);
            if (dataclass != null)
            {
                String text = tree.getText();
                tree.setText(text + " - " + dataclass);
            }
            return tree;
        }
    }
}
