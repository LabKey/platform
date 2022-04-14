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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.element.Input.InputBuilder;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpDataImpl extends AbstractRunItemImpl<Data> implements ExpData
{
    private static final Logger LOG = LogManager.getLogger(ExpDataImpl.class);

    public static final SearchService.SearchCategory expDataCategory = new SearchService.SearchCategory("data", "ExpData") {
        @Override
        public Set<String> getPermittedContainerIds(User user, Map<String, Container> containers)
        {
            return getPermittedContainerIds(user, containers, DataClassReadPermission.class);
        }
    };
    public static final SearchService.SearchCategory expMediaDataCategory = new SearchService.SearchCategory("mediaData", "ExpData for media objects") {
        @Override
        public Set<String> getPermittedContainerIds(User user, Map<String, Container> containers)
        {
            return getPermittedContainerIds(user, containers, MediaReadPermission.class);
        }
    };

    /** Cache this because it can be expensive to recompute */
    private Boolean _finalRunOutput;

    /**
     * Temporary mapping until experiment.xml contains the mime type
     */
    private static final MimeMap MIME_MAP = new MimeMap();

    static public List<ExpDataImpl> fromDatas(List<Data> datas)
    {
        List<ExpDataImpl> ret = new ArrayList<>(datas.size());
        for (Data data : datas)
        {
            ret.add(new ExpDataImpl(data));
        }
        return ret;
    }

    // For serialization
    protected ExpDataImpl() {}

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

    @Override
    @Nullable
    public ActionURL detailsURL()
    {
        DataType dataType = getDataType();
        if (dataType != null)
        {
            ActionURL url = dataType.getDetailsURL(this);
            if (url != null)
                return url;
        }

        return _object.detailsURL();
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        ExpDataClassImpl dc = getDataClass();
        if (dc != null)
            return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP_DATA, dc.getName(), FieldKey.fromParts(ExpDataTable.Column.RowId), getRowId());

        // Issue 40123: see MedImmuneDataHandler MEDIMMUNE_DATA_TYPE, this claims the "Data" namespace
        DataType type = getDataType();
        if (type != null)
        {
            QueryRowReference queryRowReference = type.getQueryRowReference(this);
            if (queryRowReference != null)
                return queryRowReference;
        }

        return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Data.name(), FieldKey.fromParts(ExpDataTable.Column.RowId), getRowId());
    }

    @Override
    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("DataId"), getRowId()), ExperimentServiceImpl.get().getTinfoDataInput());
    }

    @Override
    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoDataInput(), "DataId");
    }

    @Override
    public DataType getDataType()
    {
        return ExperimentService.get().getDataType(getLSIDNamespacePrefix());
    }

    @Override
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

    @Override
    public void save(User user)
    {
        // Replace the default "Data" cpastype if the Data belongs to a DataClass
        ExpDataClassImpl dataClass = getDataClass(null);
        if (dataClass != null && DEFAULT_CPAS_TYPE.equals(getCpasType()))
           setCpasType(dataClass.getLSID());

        boolean isNew = getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoData(), true);

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

    @Override
    protected void save(User user, TableInfo table, boolean ensureObject)
    {
        assert ensureObject;
        super.save(user, table, true);
    }

    @Override
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

    @Override
    public ExperimentDataHandler findDataHandler()
    {
        return Handler.Priority.findBestHandler(ExperimentServiceImpl.get().getExperimentDataHandlers(), this);
    }

    @Override
    public String getDataFileUrl()
    {
        return _object.getDataFileUrl();
    }

    @Override
    public boolean hasFileScheme()
    {
        return !FileUtil.hasCloudScheme(getDataFileUrl());
    }

    @Override
    @Nullable
    public File getFile()
    {
        return _object.getFile();
    }

    @Override
    @Nullable
    public java.nio.file.Path getFilePath()
    {
        return _object.getFilePath();
    }

    @Override
    public boolean isInlineImage()
    {
        return null != getFile() && MIME_MAP.isInlineImageFor(getFile());
    }

    @Override
    public void delete(User user)
    {
        delete(user, true);
    }

    @Override
    public void delete(User user, boolean deleteRunsUsingData)
    {
        ExperimentServiceImpl.get().deleteDataByRowIds(user, getContainer(), Collections.singleton(getRowId()), deleteRunsUsingData);
    }

    public String getMimeType()
    {
        if (null != getDataFileUrl())
            return MIME_MAP.getContentTypeFor(getDataFileUrl());
        else
            return null;
    }

    @Override
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

    @Override
    public String getCpasType()
    {
        String result = _object.getCpasType();
        if (result != null)
            return result;

        ExpDataClass dataClass = getDataClass(null);
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
        if (_finalRunOutput == null)
        {
            ExpRun run = getRun();
            _finalRunOutput = run != null && run.isFinalOutput(this);
        }
        return _finalRunOutput.booleanValue();
    }

    @Override
    @Nullable
    public ExpDataClassImpl getDataClass()
    {
        return getDataClass(null);
    }

    @Override
    @Nullable
    public ExpDataClassImpl getDataClass(@Nullable User user)
    {
        if (_object.getClassId() != null)
        {
            if (user == null)
                return ExperimentServiceImpl.get().getDataClass(getContainer(), _object.getClassId());
            else
                return ExperimentServiceImpl.get().getDataClass(getContainer(), user, _object.getClassId());
        }

        return null;
    }

    @Override
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
    private void getIndexValues(Set<String> identifiers, Set<String> keywords)
    {
        ExpDataClassImpl dc = this.getDataClass(null);
        if (dc == null)
            return;

        TableInfo table = QueryService.get().getUserSchema(User.getSearchUser(), getContainer(), "exp.data").getTable(dc.getName());
        if (table == null)
            return;

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

                    List<String> values;

                    String s = (String)o;
                    if (col instanceof MultiValuedLookupColumn)
                        values = Arrays.asList(s.split(MultiValuedRenderContext.VALUE_DELIMITER_REGEX));
                    else
                        values = Arrays.asList(s);

                    // treat multi-line text values as keywords, otherwise treat as an identifier
                    if ("textarea".equalsIgnoreCase(col.getInputType()))
                    {
                        keywords.addAll(values);
                    }
                    else
                    {
                        identifiers.addAll(values);
                    }
                }
            }

        }
        catch (SQLException e)
        {
            // ignore
        }
    }

    @Override
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

    @Override
    public String getDocumentId()
    {
        String dataClassName = "-";
        ExpDataClass dc = getDataClass(null);
        if (dc != null)
            dataClassName = dc.getName();
        return "data:" + new Path(getContainer().getId(), dataClassName, Integer.toString(getRowId()));
    }

    @Override
    public Map<String, ObjectProperty> getObjectProperties()
    {
        return getObjectProperties(getDataClass());
    }

    private Map<String, ObjectProperty> getObjectProperties(ExpDataClassImpl dataClass)
    {
        HashMap<String,ObjectProperty> ret = new HashMap<>(super.getObjectProperties());
        var ti = null == dataClass ? null : dataClass.getTinfo();
        if (null != ti)
        {
            ret.putAll(getObjectProperties(ti));
        }
        return ret;
    }

    private static Pair<Integer, ExpDataClass> getRowIdClassNameContainerFromDocumentId(String resourceIdentifier, Map<String, ExpDataClassImpl> dcCache)
    {
        if (resourceIdentifier.startsWith("data:"))
            resourceIdentifier = resourceIdentifier.substring("data:".length());

        Path path = Path.parse(resourceIdentifier);
        if (path.size() != 3)
            return null;
        String containerId = path.get(0);
        String dataClassName = path.get(1);
        String rowIdString = path.get(2);

        int rowId;
        try
        {
            rowId = Integer.parseInt(rowIdString);
            if (rowId == 0)
                return null;
        }
        catch (NumberFormatException ex)
        {
            return null;
        }

        Container c = ContainerManager.getForId(containerId);
        if (c == null)
            return null;

        ExpDataClass dc = null;
        if (dataClassName.length() > 0 && !dataClassName.equals("-"))
        {
            String dcKey = containerId + '-' + dataClassName;
            dc = dcCache.computeIfAbsent(dcKey, (x) -> ExperimentServiceImpl.get().getDataClass(c, dataClassName));
        }

        return new Pair<>(rowId, dc);
    }

    @Nullable
    public static ExpDataImpl fromDocumentId(String resourceIdentifier)
    {
        Pair<Integer, ExpDataClass> rowIdDataClass = getRowIdClassNameContainerFromDocumentId(resourceIdentifier, new HashMap<>());
        if (rowIdDataClass == null)
            return null;

        Integer rowId = rowIdDataClass.first;
        ExpDataClass dc = rowIdDataClass.second;

        if (dc != null)
            return ExperimentServiceImpl.get().getExpData(dc, rowId);
        else
            return ExperimentServiceImpl.get().getExpData(rowId);
    }

    @Nullable
    public static Map<String, ExpData> fromDocumentIds(Collection<String> resourceIdentifiers)
    {
        Map<Integer, String> rowIdIdentifierMap = new HashMap<>();
        Map<String, ExpDataClassImpl> dcCache = new HashMap<>();
        Map<Integer, ExpDataClass> dcMap = new HashMap<>();
        Map<Integer, List<Integer>> dcRowIdMap = new HashMap<>(); // data rowIds with dataClass
        List<Integer> rowIds = new ArrayList<>(); // data rowIds without dataClass
        for (String resourceIdentifier : resourceIdentifiers)
        {
            Pair<Integer, ExpDataClass> rowIdDataClass = getRowIdClassNameContainerFromDocumentId(resourceIdentifier, dcCache);
            if (rowIdDataClass == null)
                continue;

            Integer rowId = rowIdDataClass.first;
            ExpDataClass dc = rowIdDataClass.second;

            rowIdIdentifierMap.put(rowId, resourceIdentifier);

            if (dc != null)
            {
                dcMap.put(dc.getRowId(), dc);
                dcRowIdMap
                        .computeIfAbsent(dc.getRowId(), (k) -> new ArrayList<>())
                        .add(rowId);
            }
            else
                rowIds.add(rowId);
        }

        List<ExpData> expDatas = new ArrayList<>();
        if (!rowIds.isEmpty())
            expDatas.addAll(ExperimentServiceImpl.get().getExpDatas(rowIds));

        if (!dcRowIdMap.isEmpty())
        {
            for (Integer dataClassId : dcRowIdMap.keySet())
            {
                ExpDataClass dc = dcMap.get(dataClassId);
                if (dc != null)
                    expDatas.addAll(ExperimentServiceImpl.get().getExpDatas(dc, dcRowIdMap.get(dataClassId)));
            }
        }

        Map<String, ExpData> identifierDatas = new HashMap<>();
        for (ExpData data : expDatas)
        {
            identifierDatas.put(rowIdIdentifierMap.get(data.getRowId()), data);
        }

        return identifierDatas;
    }

    @Override
    @Nullable
    public String getWebDavURL(@NotNull FileContentService.PathType type)
    {
        java.nio.file.Path path = getFilePath();
        if (path == null)
        {
            return null;
        }

        Container c = getContainer();
        if (c == null)
        {
            return null;
        }

        return FileContentService.get().getWebDavUrl(path, c, type);
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
        Set<String> keywordsMed = new HashSet<>();
        Set<String> keywordsLo = new HashSet<>();

        Set<String> identifiersHi = new HashSet<>();
        Set<String> identifiersMed = new HashSet<>();
        Set<String> identifiersLo = new HashSet<>();

        StringBuilder body = new StringBuilder();

        // Name is an identifier with highest weight
        identifiersHi.add(getName());

        // Description is added as a keywordsLo -- in Biologics it is common for the description to
        // contain names of other DataClasses, e.g., "Mature desK of PS-10", which would will be tokenized as
        // [mature, desk, ps, 10] if added it as a keyword so we lower its priority to avoid useless results.
        // CONSIDER: tokenize the description and extract identifiers
        if (null != getDescription())
            keywordsLo.add(getDescription());

        String comment = getComment();
        if (comment != null)
            keywordsMed.add(comment);

        // Add aliases in parenthesis in the title
        StringBuilder title = new StringBuilder(getName());
        Collection<String> aliases = this.getAliases();
        if (!aliases.isEmpty())
        {
            title.append(" (").append(StringUtils.join(aliases, ", ")).append(")");
            identifiersHi.addAll(aliases);
        }

        // Collect other text columns and lookup display columns
        getIndexValues(identifiersMed, keywordsLo);

        ExpDataClass dc = this.getDataClass(null);
        if (null != dc)
        {
            ActionURL show = new ActionURL(ExperimentController.ShowDataClassAction.class,getContainer()).addParameter("rowId", dc.getRowId());
            NavTree t = new NavTree(dc.getName(), show);
            String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
            props.put(SearchService.PROPERTY.navtrail.toString(), nav);

            props.put(DataSearchResultTemplate.PROPERTY, dc.getName());
            body.append(dc.getName());
        }


        // === Not stemmed

        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiersMed, " "));
        props.put(SearchService.PROPERTY.identifiersLo.toString(), StringUtils.join(identifiersLo, " "));


        // === Stemmed

        props.put(SearchService.PROPERTY.keywordsMed.toString(), StringUtils.join(keywordsMed, " "));
        props.put(SearchService.PROPERTY.keywordsLo.toString(), StringUtils.join(keywordsLo, " "));


        // === Stored, not indexed

        if (dc != null && dc.isMedia())
            props.put(SearchService.PROPERTY.categories.toString(), expMediaDataCategory.toString());
        else
            props.put(SearchService.PROPERTY.categories.toString(), expDataCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), title.toString());

        ActionURL view = ExperimentController.ExperimentUrlsImpl.get().getDataDetailsURL(this);
        view.setExtraPath(getContainer().getId());
        String docId = getDocumentId();

        // Generate a summary explicitly instead of relying on a summary to be extracted
        // from the document body.  Placing lookup values and the description in the  body
        // would tokenize using the English analyzer and index "PS-12" as ["ps", "12"] which leads to poor results.
        StringBuilder summary = new StringBuilder();
        if (StringUtils.isNotEmpty(getDescription()))
            summary.append(getDescription()).append("\n");

        appendTokens(summary, keywordsMed);
        appendTokens(summary, identifiersMed);
        appendTokens(summary, identifiersLo);

        props.put(SearchService.PROPERTY.summary.toString(), summary);

        final int id = getRowId();
        return new ExpDataResource(
                id,
                new Path(docId),
                docId,
                getContainer().getId(),
                "text/plain",
                body.toString(),
                view,
                props,
                getCreatedBy(),
                getCreated(),
                getModifiedBy(),
                getModified()
        );
    }


    private static void appendTokens(StringBuilder sb, Collection<String> toks)
    {
        if (toks.isEmpty())
            return;

        sb.append(toks.stream().map(s -> s.length() > 30 ? s.substring(0, 30) + "\u2026" : s).collect(Collectors.joining(", "))).append("\n");
    }

    private static class ExpDataResource extends SimpleDocumentResource
    {
        final int _rowId;

        public ExpDataResource(int rowId, Path path, String documentId, String containerId, String contentType, String body, URLHelper executeUrl, Map<String, Object> properties, User createdBy, Date created, User modifiedBy, Date modified)
        {
            super(path, documentId, containerId, contentType, body, executeUrl, createdBy, created, modifiedBy, modified, properties);
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

        private ExpDataClass getDataClass()
        {
            if (HttpView.hasCurrentView())
            {
                ViewContext ctx = HttpView.currentContext();
                String dataclass = ctx.getActionURL().getParameter(PROPERTY);
                if (dataclass != null)
                    return ExperimentService.get().getDataClass(ctx.getContainer(), ctx.getUser(), dataclass);
            }
            return null;
        }

        @Nullable
        @Override
        public String getCategories()
        {
            ExpDataClass dataClass = getDataClass();

            if (dataClass != null && dataClass.isMedia())
                return expMediaDataCategory.getName();

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
            ExpDataClass dc = getDataClass();
            if (dc != null)
                return dc.getName();
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
        public HtmlString getExtraHtml(ViewContext ctx)
        {
            String q = ctx.getActionURL().getParameter("q");

            if (StringUtils.isNotBlank(q))
            {
                String dataclass = ctx.getActionURL().getParameter(PROPERTY);
                ActionURL url = ctx.cloneActionURL().deleteParameter(PROPERTY);
                url.replaceParameter("_dc", (int)Math.round(1000 * Math.random()));

                StringBuilder html = new StringBuilder();
                html.append("<div class=\"labkey-search-filter\">");

                appendParam(html, null, dataclass, "All", false, url);
                for (ExpDataClass dc : ExperimentService.get().getDataClasses(ctx.getContainer(), ctx.getUser(), true))
                {
                    appendParam(html, dc.getName(), dataclass, dc.getName(), true, url);
                }

                html.append("</div>");
                return HtmlString.unsafe(html.toString());
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
                if (addParam)
                    url = url.clone().addParameter(PROPERTY, dataclass);

                sb.append(new LinkBuilder(label).href(url).clearClasses());
            }
            else
            {
                sb.append(label);
            }

            sb.append("</span> ");
        }

        @Override
        public HtmlString getHiddenInputsHtml(ViewContext ctx)
        {
            String dataclass = ctx.getActionURL().getParameter(PROPERTY);
            if (dataclass != null)
            {
                return new InputBuilder().type("hidden").id("search-type").name(PROPERTY).value(dataclass).getHtmlString();
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
        public void addNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
        {
            SearchResultTemplate.super.addNavTrail(root, ctx, scope, category);

            String dataclass = ctx.getActionURL().getParameter(PROPERTY);
            if (dataclass != null)
            {
                String text = root.getText();
                root.setText(text + " - " + dataclass);
            }
        }
    }
}
