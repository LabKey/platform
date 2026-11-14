/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.dao.PessimisticLockingFailureException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.exp.query.SamplesSchema.SCHEMA_SAMPLES;
import static org.labkey.api.util.StringUtilsLabKey.append;

public class ExpMaterialImpl extends AbstractRunItemImpl<Material> implements ExpMaterial
{
    private static final Logger LOG = LogHelper.getLogger(ExpMaterialImpl.class, "Sample search indexing");

    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("material", "Materials/Samples", false);
    public static final SearchService.SearchCategory mediaSearchCategory = new SearchService.SearchCategory("media", "Media Samples", false){
        @Override
        public Class<? extends Permission> getRequiredPermission()
        {
            return MediaReadPermission.class;
        }
    };

    static public List<ExpMaterialImpl> fromMaterials(Collection<Material> materials)
    {
        return materials.stream().map(ExpMaterialImpl::new).toList();
    }

    // For serialization
    protected ExpMaterialImpl() {}

    public ExpMaterialImpl(Material material)
    {
        super(material);
    }

    @Override
    public ActionURL detailsURL()
    {
        return _object.detailsURL();
    }

    @Override
    public ActionURL detailsURL(Container container, boolean checkForOverride)
    {
        return _object.detailsURL(container, checkForOverride);
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        ExpSampleType st = getSampleType();
        if (st != null)
            return new QueryRowReference(getContainer(), SamplesSchema.SCHEMA_SAMPLES, st.getName(), ExpMaterialTable.Column.RowId.fieldKey(), getRowId());
        else
            return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Materials.name(), ExpMaterialTable.Column.RowId.fieldKey(), getRowId());
    }

    @Override
    public @Nullable ExpSampleTypeImpl getSampleType()
    {
        return ExperimentServiceImpl.get().getSampleTypeForCpasType(_object.getCpasType(), getContainer());
    }

    @Override
    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("MaterialId"), getRowId()), ExperimentServiceImpl.get().getTinfoMaterialInput());
    }

    @Override
    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? ExpMaterial.DEFAULT_CPAS_TYPE : result;
    }

    @Override
    public Long getRootMaterialRowId()
    {
        return _object.getRootMaterialRowId();
    }

    @Override
    public void setRootMaterialRowId(long rowId)
    {
        _object.setRootMaterialRowId(rowId);
    }

    @Override
    public String getAliquotedFromLSID()
    {
        return _object.getAliquotedFromLSID();
    }

    @Override
    public void setAliquotedFromLSID(String lsid)
    {
        _object.setAliquotedFromLSID(lsid);
    }

    @Override
    public Long getSampleStateId()
    {
        return _object.getSampleState();
    }

    @Override
    public void setSampleStateId(Long stateId)
    {
        _object.setSampleState(stateId);
    }

    @Override
    public Long getSampleColorId()
    {
        return _object.getExpMaterialColor();
    }

    @Override
    public void setSampleColorId(Long colorId)
    {
        _object.setExpMaterialColor(colorId);
    }

    @Override
    public DataState getSampleState()
    {
        if (getSampleStateId() == null)
            return null;

        return SampleStatusService.get().getStateForRowId(getContainer(), getSampleStateId());
    }

    @Override
    public String getStateLabel()
    {
        DataState state = getSampleState();
        if (state == null)
            return null;
        return state.getLabel();
    }

    @Override
    public Double getStoredAmount()
    {
        return _object.getStoredAmount();
    }

    @Override
    public void setStoredAmount(Double amount)
    {
        _object.setStoredAmount(amount);
    }

    @Override
    public String getUnits()
    {
        return _object.getUnits();
    }

    @Override
    public void setUnits(String units)
    {
        _object.setUnits(units);
    }

    @Override
    public int getAliquotCount()
    {
        return _object.getAliquotCount();
    }

    @Override
    public Double getAliquotVolume()
    {
        return _object.getAliquotVolume();
    }

    @Override
    public Double getAvailableAliquotVolume()
    {
        return _object.getAvailableAliquotVolume();
    }

    @Override
    public String getAliquotUnit()
    {
        return _object.getAliquotUnit();
    }

    @Override
    public void setAliquotUnit(String units)
    {
        _object.setAliquotUnit(units);
    }

    @Override
    public Date getMaterialExpDate()
    {
        return _object.getMaterialExpDate();
    }

    @Override
    public boolean isOperationPermitted(SampleTypeService.SampleOperations operation)
    {
        return SampleStatusService.get().isOperationPermitted(getSampleState(), operation);
    }

    @Override
    public String getNameAndStatus()
    {
        String statusLabel = getStateLabel();
        return getName() + (statusLabel == null ? "" : " (status: " + statusLabel + ")");
    }

    @Override
    @NotNull
    public Collection<String> getAliases()
    {
        TableInfo mapTi = ExperimentService.get().getTinfoMaterialAliasMap();
        TableInfo ti = ExperimentService.get().getTinfoAlias();
        SQLFragment sql = new SQLFragment()
                .unsafeAppend("SELECT a.name FROM ").append(mapTi, "m")
                .unsafeAppend(" JOIN ").append(ti, "a")
                .unsafeAppend(" ON m.alias = a.RowId WHERE m.lsid = ? ");
        sql.add(getLSID());
        ArrayList<String> aliases = new SqlSelector(mapTi.getSchema(), sql).getArrayList(String.class);
        return Collections.unmodifiableList(aliases);
    }

    /** Get the ObjectId of the ExpSampleType that this ExpMaterial belongs to. */
    @Override
    public @Nullable Long getParentObjectId()
    {
        ExpSampleType st = getSampleType();
        if (st == null)
            return null;

        return st.getObjectId();
    }

    @Override
    public void save(User user)
    {
        save(user, getSampleType());
    }

    public void save(User user, ExpSampleTypeImpl st)
    {
        save(user, ExperimentServiceImpl.get().getTinfoMaterial(), true);
        if (null != st)
        {
            TableInfo ti = st.getTinfo();
            if (null != ti)
            {
                new SqlExecutor(ti.getSchema()).execute("INSERT INTO " + ti + " (rowId, name) SELECT ?, ? WHERE NOT EXISTS (SELECT rowId FROM " + ti + " WHERE rowId = ?)", getRowId(), getName(), getRowId());
                SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(st, SampleTypeServiceImpl.SampleChangeType.insert);
            }
        }

        // LATE_POSTCOMMIT so indexing reads the materialized view after the refresh signal above
        ExperimentServiceImpl.getExpSchema().getScope().addCommitTask(
            () -> index(SearchService.get().defaultTask().getQueue(getContainer(), SearchService.PRIORITY.modified), null),
            DbScope.CommitTaskOption.LATE_POSTCOMMIT);
    }

    @Override
    protected void save(User user, TableInfo table, boolean ensureObject)
    {
        assert ensureObject;
        boolean isInsert = false;
        if (getRowId() == 0)
        {
            isInsert = true;
            long longId = DbSequenceManager.getPreallocatingSequence(ContainerManager.getRoot(), ExperimentService.get().getTinfoMaterial().getDbSequenceName(ExpMaterialTable.Column.RowId.name())).next();
            if (longId > Integer.MAX_VALUE)
                throw new OutOfRangeException(longId, 0, Integer.MAX_VALUE);
            setRowId((int) longId);
            if (null == getRootMaterialRowId())
                setRootMaterialRowId(getRowId());

            // If a MaterialSourceId is not yet specified and the material is associated with a sample type,
            // then set the MaterialSourceId to the sample type
            if (null == _object.getMaterialSourceId())
            {
                ExpSampleType st = getSampleType();
                if (st != null)
                {
                    assert st.getLSID().equals(getCpasType());
                    _object.setMaterialSourceId(st.getRowId());
                }
            }
        }
        super.save(user, table, true, isInsert);
    }

    @Override
    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, getContainer(), Collections.singleton(getRowId()), true, getSampleType(), false, false);
        // Deleting from search index is handled inside deleteMaterialByRowIds()
    }

    @Override
    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoMaterialInput(), "MaterialId");
    }

    @Override
    protected @Nullable ExpObject getSearchTableType()
    {
        return getSampleType();
    }

    @Override
    protected @NotNull SchemaKey getSearchSchemaKey()
    {
        return SCHEMA_SAMPLES;
    }

    /** Study specimens and bogus samples created from some plate assays (Issue 46037) aren't indexed as samples */
    private boolean isIndexable()
    {
        return !StudyService.SPECIMEN_NAMESPACE_PREFIX.equals(getLSIDNamespacePrefix()) && !ExpMaterial.DEFAULT_CPAS_TYPE.equals(getCpasType()) && getContainer() != null;
    }

    @Override
    public @Nullable WebdavResource createIndexDocument(@Nullable TableInfo tableInfo)
    {
        if (!isIndexable())
            return null;

        ExpSampleTypeImpl st = getSampleType();
        SampleTypeDocContext ctx = SampleTypeDocContext.create(getContainer(), st, tableInfo);
        IndexValues values = null;
        if (ctx.table() != null && !ctx.table().getSearchIndexColumns().isEmpty())
        {
            try
            {
                values = getIndexValues(ctx.table(), List.of(getRowId())).get(getRowId());
            }
            catch (SQLException e)
            {
                // ignore
            }
        }

        ExpRunImpl run = getRun();
        ExpProtocolApplicationImpl app = getSourceApplication();
        ExpProtocol protocol = app == null ? null : app.getProtocol();
        return createIndexDocument(ctx, values, getAliases(), run == null ? null : run.getName(),
                protocol == null ? null : protocol.getName(), app == null ? null : app.getName());
    }

    /** Per (container, sample type) state shared by every sample document in an indexing group */
    record SampleTypeDocContext(@NotNull Container container, @Nullable ExpSampleTypeImpl sampleType, @Nullable ExpMaterialTableImpl table, @Nullable String navtrail)
    {
        static SampleTypeDocContext create(@NotNull Container container, @Nullable ExpSampleTypeImpl st, @Nullable TableInfo tableInfo)
        {
            if (st == null)
                return new SampleTypeDocContext(container, null, null, null);

            if (tableInfo == null)
                tableInfo = getSearchTable(SCHEMA_SAMPLES, container, st);

            if (!(tableInfo instanceof ExpMaterialTableImpl expMaterialTable))
                throw new IllegalArgumentException(String.format("Unable to index material in %s. Table must be an instance of %s", st.getName(), ExpMaterialTableImpl.class.getName()));

            if (!expMaterialTable.getSampleType().equals(st))
                throw new IllegalArgumentException(String.format("Sample type table mismatch for %s", st.getName()));

            ActionURL show = new ActionURL(ExperimentController.ShowSampleTypeAction.class, container).addParameter("rowId", st.getRowId());
            NavTree t = new NavTree("SampleType - " + st.getName(), show);
            String navtrail = NavTree.toJS(Collections.singleton(t), null, false, true).toString();
            return new SampleTypeDocContext(container, st, expMaterialTable, navtrail);
        }
    }

    /** Assembles the document from values already read, so it runs no queries of its own */
    private @NotNull WebdavResource createIndexDocument(@NotNull SampleTypeDocContext ctx, @Nullable IndexValues values, @NotNull Collection<String> aliases,
                                                       @Nullable String runName, @Nullable String protocolName, @Nullable String applicationName)
    {
        Container container = ctx.container();
        ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getMaterialDetailsURL(this);
        url.setExtraPath(container.getId());

        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();
        Set<String> keyworksHi = new HashSet<>();

        // Name is identifier with the highest weight
        identifiersHi.add(getName());
        keyworksHi.add(getName()); // support "Boosting syntax (^4)" on name

        // Add aliases in parentheses in the title
        StringBuilder title = new StringBuilder("Sample - " + getName());
        if (!aliases.isEmpty())
        {
            title.append(" (").append(StringUtils.join(aliases, ", ")).append(")");
            identifiersHi.addAll(aliases);
        }

        props.put(SearchService.PROPERTY.categories.toString(), searchCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), title.toString());
        props.put(SearchService.PROPERTY.keywordsLo.toString(), "Sample");      // Treat the word "Sample" a low priority keyword

        StringBuilder body = new StringBuilder();

        // Append the interesting standard properties: "Source Experiment Run", "Source Protocol", and "Source Protocol Application"
        append(body, getDescription());
        append(body, runName);
        append(body, protocolName);
        append(body, applicationName);

        // Add all String and Integer custom property descriptions and values to body
        JSONObject jsonData = new JSONObject();
        ExpSampleTypeImpl st = ctx.sampleType();
        if (null != st && ctx.table() != null && !ctx.table().getSearchIndexColumns().isEmpty())
        {
            Set<String> identifiersMed = new HashSet<>();
            Set<String> identifiersLo = new HashSet<>();
            Set<String> keywordsMed = new HashSet<>();
            Set<String> keywordsLo = new HashSet<>();
            if (values != null)
                values.addTo(identifiersHi, identifiersMed, identifiersLo, keyworksHi, keywordsMed, keywordsLo, jsonData);
            putIndexProps(props, identifiersHi, identifiersMed, identifiersLo, keyworksHi, keywordsMed, keywordsLo);
        }

        props.put(SearchService.PROPERTY.jsonData.toString(), jsonData);

        if (null != st)
        {
            if (st.isMedia())
                props.put(SearchService.PROPERTY.categories.toString(), mediaSearchCategory.toString());
            props.put(SearchService.PROPERTY.navtrail.toString(), ctx.navtrail());

            // Add sample type name to body, if it's not already present
            String sampleTypeName = st.getName();
            if (-1 == body.indexOf(sampleTypeName))
                append(body, sampleTypeName);
        }

        return new SimpleDocumentResource(new Path(getDocumentId()), getDocumentId(),
                container.getEntityId(), "text/plain",
                body.toString(), url,
                getCreatedBy(), getCreated(),
                getModifiedBy(), getModified(),
                props)
        {
            @Override
            public void setLastIndexed(long ms, long modified)
            {
                // setLastIndexed() can get called very rapidly after a bulk insert/update
                // so we'll collect these instead of updating one at a time
                synchronized (updateLastIndexedList)
                {
                    boolean wasEmpty = updateLastIndexedList.isEmpty();
                    updateLastIndexedList.add(new Pair<>(getRowId(), ms));
                    if (wasEmpty)
                    {
                        JobRunner.getDefault().execute(() ->
                        {
                            List<Pair<Long, Long>> copy;
                            synchronized (updateLastIndexedList)
                            {
                                copy = List.copyOf(updateLastIndexedList);
                                updateLastIndexedList.clear();
                            }
                            ExperimentServiceImpl.get().setMaterialLastIndexed(copy);
                        }, 1000);
                    }
                }
            }
        };
    }

    /**
     * Indexes a batch of samples with a few queries per (container, sample type) group instead of several per sample.
     * Pass at most {@link SearchService#INDEXING_LIMIT} samples.
     */
    static void indexBatch(@NotNull SearchService.TaskIndexingQueue queue, @NotNull List<Material> batch, @NotNull Map<TableCacheKey, TableInfo> tableCache)
    {
        List<WebdavResource> docs = ExperimentServiceImpl.getExpSchema().getScope().executeWithRetryReadOnly(_ -> createIndexDocuments(batch, tableCache));
        docs.forEach(queue::addResource);
    }

    private record GroupKey(String containerId, String cpasType) {}

    /** Omits samples deleted since {@code batch} was read */
    static @NotNull List<WebdavResource> createIndexDocuments(@NotNull List<Material> batch, @NotNull Map<TableCacheKey, TableInfo> tableCache)
    {
        List<ExpMaterialImpl> materials = batch.stream().map(ExpMaterialImpl::new).filter(ExpMaterialImpl::isIndexable).toList();
        if (materials.isEmpty())
            return List.of();

        DbScope scope = ExperimentServiceImpl.getExpSchema().getScope();
        // Holding the thread's connection lets every query below reuse it instead of borrowing and validating one per query
        try (Connection ignored = scope.getConnection())
        {
            Map<String, List<String>> aliases = getAliases(materials.stream().map(ExpMaterialImpl::getLSID).toList());
            Map<Long, String> runNames = new HashMap<>();
            ExperimentServiceImpl.get().getExpRuns(materials.stream().map(ExpMaterialImpl::getRunId).filter(Objects::nonNull).collect(Collectors.toSet()))
                    .forEach(run -> runNames.put(run.getRowId(), run.getName()));
            Map<Long, ExpProtocolApplicationImpl> apps = getProtocolApplications(materials.stream().map(m -> m._object.getSourceApplicationId()).filter(Objects::nonNull).collect(Collectors.toSet()));

            Map<GroupKey, List<ExpMaterialImpl>> groups = materials.stream()
                    .collect(Collectors.groupingBy(m -> new GroupKey(m.getContainer().getId(), m.getCpasType()), LinkedHashMap::new, Collectors.toList()));

            List<WebdavResource> docs = new ArrayList<>(materials.size());
            for (List<ExpMaterialImpl> group : groups.values())
            {
                try
                {
                    createGroupIndexDocuments(group, tableCache, aliases, runNames, apps, docs);
                }
                catch (RuntimeException e)
                {
                    if (isRetryable(e))
                        throw e;

                    // Index the rest of the group one sample at a time so one bad row doesn't stop the whole pass
                    LOG.warn("Failed to index {} samples of type {} in {} together; indexing them individually", group.size(), group.getFirst().getCpasType(), group.getFirst().getContainer().getPath(), e);
                    for (ExpMaterialImpl m : group)
                    {
                        try
                        {
                            WebdavResource doc = m.createIndexDocument(null);
                            if (doc != null)
                                docs.add(doc);
                        }
                        catch (RuntimeException x)
                        {
                            if (isRetryable(x))
                                throw x;
                            LOG.warn("Failed to index sample {}", m.getRowId(), x);
                        }
                    }
                }
            }
            return docs;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    // Deadlocks propagate so executeWithRetryReadOnly() can retry the batch
    private static boolean isRetryable(RuntimeException e)
    {
        return e instanceof PessimisticLockingFailureException || (e instanceof RuntimeSQLException sqlx && SqlDialect.isTransactionException(sqlx));
    }

    private static void createGroupIndexDocuments(List<ExpMaterialImpl> group, Map<TableCacheKey, TableInfo> tableCache, Map<String, List<String>> aliases,
                                                  Map<Long, String> runNames, Map<Long, ExpProtocolApplicationImpl> apps, List<WebdavResource> docs)
    {
        ExpMaterialImpl first = group.getFirst();
        Container container = first.getContainer();
        ExpSampleTypeImpl st = first.getSampleType();
        TableInfo table = st == null ? null : tableCache.computeIfAbsent(new TableCacheKey(container, st), _ -> getSearchTable(SCHEMA_SAMPLES, container, st));
        SampleTypeDocContext ctx = SampleTypeDocContext.create(container, st, table);

        Map<Long, IndexValues> values = Map.of();
        if (ctx.table() != null)
        {
            try
            {
                values = getIndexValues(ctx.table(), group.stream().map(ExpMaterialImpl::getRowId).toList());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        List<WebdavResource> groupDocs = new ArrayList<>(group.size());
        for (ExpMaterialImpl m : group)
        {
            IndexValues v = values.get(m.getRowId());
            if (ctx.table() != null && v == null)
                continue;

            ExpProtocolApplicationImpl app = m._object.getSourceApplicationId() == null ? null : apps.get(m._object.getSourceApplicationId());
            ExpProtocol protocol = app == null ? null : app.getProtocol();
            groupDocs.add(m.createIndexDocument(ctx, v, aliases.getOrDefault(m.getLSID(), List.of()),
                    m.getRunId() == null ? null : runNames.get(m.getRunId()),
                    protocol == null ? null : protocol.getName(), app == null ? null : app.getName()));
        }
        docs.addAll(groupDocs);
    }

    private static @NotNull Map<String, List<String>> getAliases(@NotNull Collection<String> lsids)
    {
        TableInfo mapTi = ExperimentService.get().getTinfoMaterialAliasMap();
        SQLFragment sql = new SQLFragment()
                .unsafeAppend("SELECT m.lsid, a.name FROM ").append(mapTi, "m")
                .unsafeAppend(" JOIN ").append(ExperimentService.get().getTinfoAlias(), "a")
                .unsafeAppend(" ON m.alias = a.RowId WHERE ").appendInClause("m.lsid", lsids, mapTi.getSqlDialect());
        Map<String, List<String>> aliases = new HashMap<>();
        new SqlSelector(mapTi.getSchema(), sql).forEach(rs -> aliases.computeIfAbsent(rs.getString(1), _ -> new ArrayList<>()).add(rs.getString(2)));
        return aliases;
    }

    private static @NotNull Map<Long, ExpProtocolApplicationImpl> getProtocolApplications(@NotNull Collection<Long> rowIds)
    {
        if (rowIds.isEmpty())
            return Map.of();

        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts("RowId"), rowIds);
        Map<Long, ExpProtocolApplicationImpl> apps = new HashMap<>();
        new TableSelector(ExperimentServiceImpl.get().getTinfoProtocolApplication(), filter, null)
                .forEach(ProtocolApplication.class, app -> apps.put(app.getRowId(), new ExpProtocolApplicationImpl(app)));
        return apps;
    }

    static final List<Pair<Long,Long>> updateLastIndexedList = new ArrayList<>();

    @Override
    public String getDocumentId()
    {
        return "material:" + getRowId();
    }

    /* This is expensive! consider using getProperties(SampleType st) */
    @Override
    public Map<String, Object> getProperties()
    {
        return getProperties(getSampleType());
    }

    public Map<String,Object> getProperties(ExpSampleTypeImpl st)
    {
        var ret = super.getProperties();
        var ti = null == st ? null : st.getTinfo();
        if (null != ti)
        {
            getObjectPropertiesSelector(ti).forEach(rs ->
            {
                for (ColumnInfo c : ti.getColumns())
                {
                    if (c.getPropertyURI() == null)
                        continue;
                    Object value = c.getValue(rs);
                    ret.put(c.getPropertyURI(), value);
                }
            });
        }
        return ret;
    }

    @Override
    public Map<PropertyDescriptor, Object> getPropertyValues()
    {
        ExpSampleTypeImpl sampleType = getSampleType();
        Map<String,Object> uriMap = getProperties(sampleType);
        Map<PropertyDescriptor, Object> values = new HashMap<>();
        if (sampleType != null)
        {
            for (DomainProperty pd : sampleType.getDomain().getProperties())
            {
                values.put(pd.getPropertyDescriptor(), uriMap.get(pd.getPropertyURI()));
            }
        }
        return values;
    }

    @Override
    public Map<String, ObjectProperty> getObjectProperties()
    {
        return getObjectProperties(getSampleType());
    }

    public Map<String, ObjectProperty> getObjectProperties(ExpSampleTypeImpl st)
    {
        HashMap<String,ObjectProperty> ret = new HashMap<>(super.getObjectProperties());
        var ti = null == st ? null : st.getTinfo();
        if (null != ti)
        {
            ret.putAll(getObjectProperties(ti));
        }
        return ret;
    }

    @Override
    protected TableSelector getObjectPropertiesSelector(@NotNull TableInfo table)
    {
        return new TableSelector(table, new SimpleFilter(ExpMaterialTable.Column.RowId.fieldKey(), getRowId()), null);
    }

    @Override
    public Object getProperty(PropertyDescriptor pd)
    {
        var map = getProperties();
        return map.get(pd.getPropertyURI());
    }

    @Override
    public void setProperty(User user, PropertyDescriptor pd, Object value, boolean insertNullValues) throws ValidationException
    {
        if (null == pd.getStorageColumnName())
            super.setProperty(user, pd, value, insertNullValues);
        else
            setProperties(user, Collections.singletonMap(pd.getName(), value), insertNullValues);
    }

    public void setProperties(User user, Map<String,?> values_, boolean insertNullValues) throws ValidationException
    {
        ExpSampleTypeImpl st = getSampleType();
        Map<String, Object> values = new HashMap<>(values_);
        Map<String,Object> converted = new HashMap<>();
        RemapCache cache = new RemapCache();

        TableInfo ti = null==st ? null : st.getTinfo();
        if (null != ti)
        {
            Domain d = st.getDomain();
            for (DomainProperty dp : d.getProperties())
            {
                String key;
                Object value;
                if (values.containsKey(dp.getName()))
                    value = values.get(key = dp.getName());
                else if (values.containsKey(dp.getPropertyURI()))
                    value = values.get(key = dp.getPropertyURI());
                else
                    continue;
                if (value instanceof ObjectProperty property)
                {
                    // NOTE: ExpObjectImpl.setProperty() does not support MvIndicator and neither does Table.update().
                    // we could handle it here if we need to
                    value = property.value();
                }
                try
                {
                    value = dp.getJdbcType().convert(value);
                }
                catch (ConversionException x)
                {
                    // Attempt to resolve lookups by display value
                    boolean skipError = false;
                    if (dp.getLookup() != null)
                    {
                        Object remappedValue = OntologyManager.getRemappedValueForLookup(user, getContainer(), cache, dp.getLookup(), value);
                        if (remappedValue != null)
                        {
                            value = remappedValue;
                            skipError = true;
                        }
                    }

                    if (!skipError)
                        throw new ValidationException(ConvertHelper.getStandardConversionErrorMessage(value, dp.getName(), dp.getPropertyDescriptor().getPropertyType().getJavaType()));
                }
                converted.put(dp.getName(), value);
                values.remove(key);
            }
            TableInfo tableInfo = st.getTinfo();
            ColumnInfo rowIdCol = tableInfo.getColumn(ExpMaterialTable.Column.RowId.fieldKey());
            Table.update(user, tableInfo, converted, rowIdCol, getRowId(), null, Level.WARN);
        }
        for (var entry : values.entrySet())
        {
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(entry.getKey(), st == null ? getContainer() : st.getContainer());
            if (null != pd)
                super.setProperty(user, pd, entry.getValue(), insertNullValues);
        }
    }
}
