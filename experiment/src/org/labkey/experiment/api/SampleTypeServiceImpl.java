/*
 * Copyright (c) 2019 LabKey Corporation
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

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditHandler;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.measurement.Measurement;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialRunInput;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.SampleTypeDomainKindProperties;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataUnavailableException;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.SampleTypeAuditProvider;
import org.labkey.experiment.samples.SampleTimelineAuditProvider;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static org.labkey.api.audit.SampleTimelineAuditEvent.SAMPLE_TIMELINE_EVENT_TYPE;
import static org.labkey.api.data.CompareType.STARTS_WITH;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTCOMMIT;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTROLLBACK;
import static org.labkey.api.exp.api.ExperimentJSONConverter.CPAS_TYPE;
import static org.labkey.api.exp.api.ExperimentJSONConverter.LSID;
import static org.labkey.api.exp.api.ExperimentJSONConverter.NAME;
import static org.labkey.api.exp.api.ExperimentJSONConverter.ROW_ID;
import static org.labkey.api.exp.api.ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_LSID;
import static org.labkey.api.exp.api.NameExpressionOptionService.NAME_EXPRESSION_REQUIRED_MSG;
import static org.labkey.api.exp.api.NameExpressionOptionService.NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS;
import static org.labkey.api.exp.query.ExpSchema.NestedSchemas.materials;


public class SampleTypeServiceImpl extends AbstractAuditHandler implements SampleTypeService
{
    public static final String SAMPLE_COUNT_SEQ_NAME = "org.labkey.api.exp.api.ExpMaterial:sampleCount";
    public static final String ROOT_SAMPLE_COUNT_SEQ_NAME = "org.labkey.api.exp.api.ExpMaterial:rootSampleCount";

    // columns that may appear in a row when only the sample status is updating.
    public static final Set<String> statusUpdateColumns = Set.of(
            ExpMaterialTable.Column.Modified.name().toLowerCase(),
            ExpMaterialTable.Column.ModifiedBy.name().toLowerCase(),
            ExpMaterialTable.Column.SampleState.name().toLowerCase(),
            ExpMaterialTable.Column.Folder.name().toLowerCase()
    );

    public static SampleTypeServiceImpl get()
    {
        return (SampleTypeServiceImpl) SampleTypeService.get();
    }

    private static final Logger LOG = LogHelper.getLogger(SampleTypeServiceImpl.class, "Info about sample type operations");

    // SampleType -> Container cache
    private final Cache<String, String> sampleTypeCache = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "SampleType to container");

    private final Cache<String, SortedSet<MaterialSource>> materialSourceCache = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Material sources", (container, argument) ->
    {
        Container c = ContainerManager.getForId(container);
        if (c == null)
            return Collections.emptySortedSet();

        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        return Collections.unmodifiableSortedSet(new TreeSet<>(new TableSelector(getTinfoMaterialSource(), filter, null).getCollection(MaterialSource.class)));
    });

    Cache<String, SortedSet<MaterialSource>> getMaterialSourceCache()
    {
        return materialSourceCache;
    }


    public void clearMaterialSourceCache(@Nullable Container c)
    {
        LOG.debug("clearMaterialSourceCache: " + (c == null ? "all" : c.getPath()));
        if (c == null)
            materialSourceCache.clear();
        else
            materialSourceCache.remove(c.getId());
    }


    private TableInfo getTinfoMaterialSource()
    {
        return ExperimentServiceImpl.get().getTinfoSampleType();
    }

    private TableInfo getTinfoMaterial()
    {
        return ExperimentServiceImpl.get().getTinfoMaterial();
    }

    private TableInfo getTinfoProtocolApplication()
    {
        return ExperimentServiceImpl.get().getTinfoProtocolApplication();
    }

    private TableInfo getTinfoProtocol()
    {
        return ExperimentServiceImpl.get().getTinfoProtocol();
    }

    private TableInfo getTinfoMaterialInput()
    {
        return ExperimentServiceImpl.get().getTinfoMaterialInput();
    }

    private TableInfo getTinfoExperimentRun()
    {
        return ExperimentServiceImpl.get().getTinfoExperimentRun();
    }

    private TableInfo getTinfoDataClass()
    {
        return ExperimentServiceImpl.get().getTinfoDataClass();
    }

    private TableInfo getTinfoProtocolInput()
    {
        return ExperimentServiceImpl.get().getTinfoProtocolInput();
    }

    private TableInfo getTinfoMaterialAliasMap()
    {
        return ExperimentServiceImpl.get().getTinfoMaterialAliasMap();
    }

    private DbSchema getExpSchema()
    {
        return ExperimentServiceImpl.get().getExpSchema();
    }

    @Override
    public void indexSampleType(ExpSampleType sampleType)
    {
        SearchService ss = SearchService.get();
        if (ss == null)
            return;

        SearchService.IndexTask task = ss.defaultTask();

        Runnable r = () -> {

            indexSampleType(sampleType, task);
            indexSampleTypeMaterials(sampleType, task);

        };

        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }


    private void indexSampleType(ExpSampleType sampleType, SearchService.IndexTask task)
    {
        // Index all ExpMaterial that have never been indexed OR where either the ExpSampleType definition or ExpMaterial itself has changed since last indexed
        SQLFragment sql = new SQLFragment("SELECT * FROM ")
                .append(getTinfoMaterialSource(), "ms")
                .append(" WHERE ms.LSID NOT LIKE ").appendValue("%:" + StudyService.SPECIMEN_NAMESPACE_PREFIX + "%", getExpSchema().getSqlDialect())
                .append(" AND ms.LSID = ?").add(sampleType.getLSID())
                .append(" AND (ms.lastIndexed IS NULL OR ms.lastIndexed < ? OR (ms.modified IS NOT NULL AND ms.lastIndexed < ms.modified))")
                .add(sampleType.getModified());

        MaterialSource materialSource = new SqlSelector(getExpSchema().getScope(), sql).getObject(MaterialSource.class);
        if (materialSource != null)
        {
            ExpSampleTypeImpl impl = new ExpSampleTypeImpl(materialSource);
            impl.index(task);
        }
    }

    private void indexSampleTypeMaterials(ExpSampleType sampleType, SearchService.IndexTask task)
    {
        // Index all ExpMaterial that have never been indexed OR where either the ExpSampleType definition or ExpMaterial itself has changed since last indexed
        SQLFragment sql = new SQLFragment("SELECT m.* FROM ")
                .append(getTinfoMaterial(), "m")
                .append(" LEFT OUTER JOIN ")
                .append(ExperimentServiceImpl.get().getTinfoMaterialIndexed(), "mi")
                .append(" ON m.RowId = mi.MaterialId WHERE m.LSID NOT LIKE ").appendValue("%:" + StudyService.SPECIMEN_NAMESPACE_PREFIX + "%", getExpSchema().getSqlDialect())
                .append(" AND m.cpasType = ?").add(sampleType.getLSID())
                .append(" AND (mi.lastIndexed IS NULL OR mi.lastIndexed < ? OR (m.modified IS NOT NULL AND mi.lastIndexed < m.modified))")
                .add(sampleType.getModified());

        new SqlSelector(getExpSchema().getScope(), sql).forEachBatch(Material.class, 1000, batch -> {
            for (Material m : batch)
            {
                ExpMaterialImpl impl = new ExpMaterialImpl(m);
                impl.index(task);
            }
        });
    }


    @Override
    public Map<String, ExpSampleType> getSampleTypesForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT mi.Role, MAX(m.CpasType) AS MaxSampleSetLSID, MIN (m.CpasType) AS MinSampleSetLSID FROM ");
        sql.append(getTinfoMaterial(), "m");
        sql.append(", ");
        sql.append(getTinfoMaterialInput(), "mi");
        sql.append(", ");
        sql.append(getTinfoProtocolApplication(), "pa");
        sql.append(", ");
        sql.append(getTinfoExperimentRun(), "r");

        if (type != null)
        {
            sql.append(", ");
            sql.append(getTinfoProtocol(), "p");
            sql.append(" WHERE p.lsid = pa.protocollsid AND p.applicationtype = ? AND ");
            sql.add(type.toString());
        }
        else
        {
            sql.append(" WHERE ");
        }

        sql.append(" m.RowId = mi.MaterialId AND mi.TargetApplicationId = pa.RowId AND " +
                "pa.RunId = r.RowId AND ");
        sql.append(filter.getSQLFragment(getExpSchema(), new SQLFragment("r.Container")));
        sql.append(" GROUP BY mi.Role ORDER BY mi.Role");

        Map<String, ExpSampleType> result = new LinkedHashMap<>();
        for (Map<String, Object> queryResult : new SqlSelector(getExpSchema(), sql).getMapCollection())
        {
            ExpSampleType sampleType = null;
            String maxSampleTypeLSID = (String) queryResult.get("MaxSampleSetLSID");
            String minSampleTypeLSID = (String) queryResult.get("MinSampleSetLSID");

            // Check if we have a sample type that was being referenced
            if (maxSampleTypeLSID != null && maxSampleTypeLSID.equalsIgnoreCase(minSampleTypeLSID))
            {
                // If the min and the max are the same, it means all rows share the same value so we know that there's
                // a single sample type being targeted
                sampleType = getSampleType(container, maxSampleTypeLSID);
            }
            result.put((String) queryResult.get("Role"), sampleType);
        }
        return result;
    }

    @Override
    public void removeAutoLinkedStudy(@NotNull Container studyContainer, @Nullable User user)
    {
        SQLFragment sql = new SQLFragment("UPDATE ").append(getTinfoMaterialSource())
                .append(" SET autolinkTargetContainer = NULL WHERE autolinkTargetContainer = ?")
                .add(studyContainer.getId());
        new SqlExecutor(ExperimentService.get().getSchema()).execute(sql);
    }

    public ExpSampleTypeImpl getSampleTypeByObjectId(Integer objectId)
    {
        OntologyObject obj = OntologyManager.getOntologyObject(objectId);
        if (obj == null)
            return null;

        return getSampleType(obj.getObjectURI());
    }

    @Override
    public @Nullable ExpSampleType getEffectiveSampleType(
        @NotNull Container definitionContainer,
        @NotNull User user,
        @NotNull String sampleTypeName,
        @NotNull Date effectiveDate,
        @Nullable ContainerFilter cf
    )
    {
        Integer legacyObjectId = ExperimentService.get().getObjectIdWithLegacyName(sampleTypeName, ExperimentServiceImpl.getNamespacePrefix(ExpSampleType.class), effectiveDate, definitionContainer, cf);
        if (legacyObjectId != null)
            return getSampleTypeByObjectId(legacyObjectId);

        boolean includeOtherContainers = cf != null && cf.getType() != ContainerFilter.Type.Current;
        ExpSampleTypeImpl sampleType = getSampleType(definitionContainer, user, includeOtherContainers, sampleTypeName);
        if (sampleType != null && sampleType.getCreated().compareTo(effectiveDate) <= 0)
            return sampleType;

        return null;
    }

    @Override
    public List<ExpSampleTypeImpl> getSampleTypes(@NotNull Container container, @Nullable User user, boolean includeOtherContainers)
    {
        List<String> containerIds = ExperimentServiceImpl.get().createContainerList(container, user, includeOtherContainers);

        // Do the sort on the Java side to make sure it's always case-insensitive, even on Postgres
        TreeSet<ExpSampleTypeImpl> result = new TreeSet<>();
        for (String containerId : containerIds)
        {
            for (MaterialSource source : getMaterialSourceCache().get(containerId))
            {
                result.add(new ExpSampleTypeImpl(source));
            }
        }

        return List.copyOf(result);
    }

    @Override
    public ExpSampleTypeImpl getSampleType(@NotNull Container c, @NotNull String sampleTypeName)
    {
        return getSampleType(c, null, false, sampleTypeName);
    }

    // NOTE: This method used to not take a user or check permissions
    @Override
    public ExpSampleTypeImpl getSampleType(@NotNull Container c, @NotNull User user, @NotNull String sampleTypeName)
    {
        return getSampleType(c, user, true, sampleTypeName);
    }

    private ExpSampleTypeImpl getSampleType(@NotNull Container c, @Nullable User user, boolean includeOtherContainers, String sampleTypeName)
    {
        return getSampleType(c, user, includeOtherContainers, (materialSource -> materialSource.getName().equalsIgnoreCase(sampleTypeName)));
    }

    @Override
    public ExpSampleTypeImpl getSampleType(@NotNull Container c, int rowId)
    {
        return getSampleType(c, null, rowId, false);
    }

    @Override
    public ExpSampleTypeImpl getSampleType(@NotNull Container c, @NotNull User user, int rowId)
    {
        return getSampleType(c, user, rowId, true);
    }

    @Override
    public ExpSampleTypeImpl getSampleTypeByType(@NotNull String lsid, Container hint)
    {
        Container c = hint;
        String id = sampleTypeCache.get(lsid);
        if (null != id && (null == hint || !id.equals(hint.getId())))
            c = ContainerManager.getForId(id);
        ExpSampleTypeImpl st = null;
        if (null != c)
            st = getSampleType(c, null, false, ms -> lsid.equals(ms.getLSID()) );
        if (null == st)
            st = _getSampleType(lsid);
        if (null != st && null==id)
            sampleTypeCache.put(lsid,st.getContainer().getId());
        return st;
    }

    private ExpSampleTypeImpl getSampleType(@NotNull Container c, @Nullable User user, int rowId, boolean includeOtherContainers)
    {
        return getSampleType(c, user, includeOtherContainers, (materialSource -> materialSource.getRowId() == rowId));
    }

    private ExpSampleTypeImpl getSampleType(@NotNull Container c, @Nullable User user, boolean includeOtherContainers, Predicate<MaterialSource> predicate)
    {
        List<String> containerIds = ExperimentServiceImpl.get().createContainerList(c, user, includeOtherContainers);
        for (String containerId : containerIds)
        {
            Collection<MaterialSource> sampleTypes = getMaterialSourceCache().get(containerId);
            for (MaterialSource materialSource : sampleTypes)
            {
                if (predicate.test(materialSource))
                    return new ExpSampleTypeImpl(materialSource);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public ExpSampleTypeImpl getSampleType(int rowId)
    {
        // TODO: Cache
        MaterialSource materialSource = new TableSelector(getTinfoMaterialSource()).getObject(rowId, MaterialSource.class);
        if (materialSource == null)
            return null;

        return new ExpSampleTypeImpl(materialSource);
    }

    @Nullable
    @Override
    public ExpSampleTypeImpl getSampleType(String lsid)
    {
        return getSampleTypeByType(lsid, null);
    }

    @Nullable
    @Override
    public DataState getSampleState(Container container, Integer stateRowId)
    {
        return SampleStatusService.get().getStateForRowId(container, stateRowId);
    }

    private ExpSampleTypeImpl _getSampleType(String lsid)
    {
        MaterialSource ms = getMaterialSource(lsid);
        if (ms == null)
            return null;

        return new ExpSampleTypeImpl(ms);
    }

    public MaterialSource getMaterialSource(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
        return new TableSelector(getTinfoMaterialSource(), filter, null).getObject(MaterialSource.class);
    }

    public DbScope.Transaction ensureTransaction()
    {
        return getExpSchema().getScope().ensureTransaction();
    }

    @Override
    public Lsid getSampleTypeLsid(String sourceName, Container container)
    {
        return Lsid.parse(ExperimentService.get().generateLSID(container, ExpSampleType.class, sourceName));
    }

    @Override
    public Pair<String, String> getSampleTypeSamplePrefixLsids(Container container)
    {
        Pair<String, String> lsidDbSeq = ExperimentService.get().generateLSIDWithDBSeq(container, ExpSampleType.class);
        String sampleTypeLsidStr = lsidDbSeq.first;
        Lsid sampleTypeLsid = Lsid.parse(sampleTypeLsidStr);

        String dbSeqStr = lsidDbSeq.second;
        String samplePrefixLsid = new Lsid.LsidBuilder("Sample", "Folder-" + container.getRowId() + "." + dbSeqStr, "").toString();

        return new Pair<>(sampleTypeLsid.toString(), samplePrefixLsid);
    }

    /**
     * Delete all exp.Material from the SampleType. If container is not provided,
     * all rows from the SampleType will be deleted regardless of container.
     */
    public int truncateSampleType(ExpSampleTypeImpl source, User user, @Nullable Container c)
    {
        assert getExpSchema().getScope().isTransactionActive();

        Set<Container> containers = new HashSet<>();
        if (c == null)
        {
            SQLFragment containerSql = new SQLFragment("SELECT DISTINCT Container FROM ");
            containerSql.append(getTinfoMaterial(), "m");
            containerSql.append(" WHERE CpasType = ?");
            containerSql.add(source.getLSID());
            new SqlSelector(getExpSchema(), containerSql).forEach(String.class, cId -> containers.add(ContainerManager.getForId(cId)));
        }
        else
        {
            containers.add(c);
        }

        int count = 0;
        for (Container toDelete : containers)
        {
            SQLFragment sqlFilter = new SQLFragment("CpasType = ? AND Container = ?");
            sqlFilter.add(source.getLSID());
            sqlFilter.add(toDelete);
            count += ExperimentServiceImpl.get().deleteMaterialBySqlFilter(user, toDelete, sqlFilter, true, false, source, true, true);
        }
        return count;
    }

    @Override
    public void deleteSampleType(int rowId, Container c, User user, @Nullable String auditUserComment) throws ExperimentException
    {
        CPUTimer timer = new CPUTimer("delete sample type");
        timer.start();

        ExpSampleTypeImpl source = getSampleType(c, user, rowId);
        if (null == source)
            throw new IllegalArgumentException("Can't find SampleType with rowId " + rowId);
        if (!source.getContainer().equals(c))
            throw new ExperimentException("Trying to delete a SampleType from a different container");

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            // TODO: option to skip deleting rows from the materialized table since we're about to delete it anyway
            // TODO do we need both truncateSampleType() and deleteDomainObjects()?
            truncateSampleType(source, user, null);

            StudyService studyService = StudyService.get();
            if (studyService != null)
            {
                for (Dataset dataset : StudyPublishService.get().getDatasetsForPublishSource(rowId, Dataset.PublishSource.SampleType))
                {
                    dataset.delete(user);
                }
            }
            else
            {
                LOG.warn("Could not delete datasets associated with this protocol: Study service not available.");
            }

            Domain d = source.getDomain();
            d.delete(user);

            ExperimentServiceImpl.get().deleteDomainObjects(source.getContainer(), source.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("UPDATE " + getTinfoDataClass() + " SET materialSourceId = NULL WHERE materialSourceId = ?", source.getRowId());
            executor.execute("UPDATE " + getTinfoProtocolInput() + " SET materialSourceId = NULL WHERE materialSourceId = ?", source.getRowId());
            executor.execute("DELETE FROM " + getTinfoMaterialSource() + " WHERE RowId = ?", rowId);

            addSampleTypeDeletedAuditEvent(user, c, source, transaction.getAuditId(), auditUserComment);

            ExperimentService.get().removeDataTypeExclusion(Collections.singleton(rowId), ExperimentService.DataTypeForExclusion.SampleType);
            ExperimentService.get().removeDataTypeExclusion(Collections.singleton(rowId), ExperimentService.DataTypeForExclusion.DashboardSampleType);

            transaction.addCommitTask(() -> clearMaterialSourceCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
            transaction.commit();
        }

        // Delete sequences (genId and the unique counters)
        DbSequenceManager.deleteLike(c, ExpSampleType.SEQUENCE_PREFIX, source.getRowId(), getExpSchema().getSqlDialect());

        SchemaKey samplesSchema = SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME);
        QueryService.get().fireQueryDeleted(user, c, null, samplesSchema, singleton(source.getName()));

        SchemaKey expMaterialsSchema = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, materials.toString());
        QueryService.get().fireQueryDeleted(user, c, null, expMaterialsSchema, singleton(source.getName()));

        // Remove SampleType from search index
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            try (Timing ignored = MiniProfiler.step("search docs"))
            {
                ss.deleteResource(source.getDocumentId());
            }
        }

        timer.stop();
        LOG.info("Deleted SampleType '" + source.getName() + "' from '" + c.getPath() + "' in " + timer.getDuration());
    }

    private void addSampleTypeDeletedAuditEvent(User user, Container c, ExpSampleType sampleType, Long txAuditId, String auditUserComment)
    {
        addSampleTypeAuditEvent(user, c, sampleType, txAuditId, String.format("Sample Type deleted: %1$s", sampleType.getName()),auditUserComment, "delete type");
    }

    private void addSampleTypeAuditEvent(User user, Container c, ExpSampleType sampleType, Long txAuditId, String comment, String auditUserComment, String insertUpdateChoice)
    {
        SampleTypeAuditProvider.SampleTypeAuditEvent event = new SampleTypeAuditProvider.SampleTypeAuditEvent(c.getId(), comment);
        event.setUserComment(auditUserComment);

        if (txAuditId != null)
            event.setTransactionId(txAuditId);

        if (sampleType != null)
        {
            event.setSourceLsid(sampleType.getLSID());
            event.setSampleSetName(sampleType.getName());
        }
        event.setInsertUpdateChoice(insertUpdateChoice);
        AuditLogService.get().addEvent(user, event);
    }


    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType()
    {
        return new ExpSampleTypeImpl(new MaterialSource());
    }

    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol, String nameExpression)
            throws ExperimentException
    {
        return createSampleType(c,u,name,description,properties,indices,idCol1,idCol2,idCol3,parentCol,nameExpression, null);
    }

    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                              String nameExpression, @Nullable TemplateInfo templateInfo)
            throws ExperimentException
    {
        return createSampleType(c, u, name, description, properties, indices, idCol1, idCol2, idCol3,
                parentCol, nameExpression, null, templateInfo, null, null, null);
    }

    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                              String nameExpression, String aliquotNameExpression, @Nullable TemplateInfo templateInfo, @Nullable Map<String, Map<String, Object>> importAliases, @Nullable String labelColor, @Nullable String metricUnit) throws ExperimentException
    {
        return createSampleType(c, u, name, description, properties, indices, idCol1, idCol2, idCol3, parentCol, nameExpression, aliquotNameExpression, templateInfo, importAliases, labelColor, metricUnit, null, null, null, null, null, null);
    }

    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                              String nameExpression, String aliquotNameExpression, @Nullable TemplateInfo templateInfo, @Nullable Map<String, Map<String, Object>> importAliases, @Nullable String labelColor, @Nullable String metricUnit,
                                              @Nullable Container autoLinkTargetContainer, @Nullable String autoLinkCategory, @Nullable String category, @Nullable List<String> disabledSystemField,
                                              @Nullable List<String> excludedContainerIds, @Nullable List<String> excludedDashboardContainerIds)
        throws ExperimentException
    {
        if (name == null)
            throw new ExperimentException("SampleType name is required");

        TableInfo materialSourceTable = ExperimentService.get().getTinfoSampleType();
        int nameMax = materialSourceTable.getColumn("Name").getScale();
        if (name.length() > nameMax)
            throw new ExperimentException("SampleType name may not exceed " + nameMax + " characters.");

        ExpSampleType existing = getSampleType(c, name);
        if (existing != null)
            throw new IllegalArgumentException("SampleType '" + existing.getName() + "' already exists");

        if (properties == null || properties.isEmpty())
            throw new ExperimentException("At least one property is required");

        if (idCol2 != -1 && idCol1 == idCol2)
            throw new ExperimentException("You cannot use the same id column twice.");

        if (idCol3 != -1 && (idCol1 == idCol3 || idCol2 == idCol3))
            throw new ExperimentException("You cannot use the same id column twice.");

        if ((idCol1 > -1 && idCol1 >= properties.size()) ||
            (idCol2 > -1 && idCol2 >= properties.size()) ||
            (idCol3 > -1 && idCol3 >= properties.size()) ||
            (parentCol > -1 && parentCol >= properties.size()))
            throw new ExperimentException("column index out of range");

        // Name expression is only allowed when no idCol is set
        if (nameExpression != null && idCol1 > -1)
            throw new ExperimentException("Name expression cannot be used with id columns");

        NameExpressionOptionService svc = NameExpressionOptionService.get();
        if (!svc.allowUserSpecifiedNames(c))
        {
            if (nameExpression == null)
                throw new ExperimentException(c.hasProductProjects() ? NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS : NAME_EXPRESSION_REQUIRED_MSG);
        }

        if (svc.getExpressionPrefix(c) != null)
        {
            // automatically apply the configured prefix to the name expression
            nameExpression = svc.createPrefixedExpression(c, nameExpression, false);
            aliquotNameExpression = svc.createPrefixedExpression(c, aliquotNameExpression, true);
        }

        // Validate the name expression length
        int nameExpMax = materialSourceTable.getColumn("NameExpression").getScale();
        if (nameExpression != null && nameExpression.length() > nameExpMax)
            throw new ExperimentException("Name expression may not exceed " + nameExpMax + " characters.");

        // Validate the aliquot name expression length
        int aliquotNameExpMax = materialSourceTable.getColumn("AliquotNameExpression").getScale();
        if (aliquotNameExpression != null && aliquotNameExpression.length() > aliquotNameExpMax)
            throw new ExperimentException("Aliquot naming patten may not exceed " + aliquotNameExpMax + " characters.");

        // Validate the label color length
        int labelColorMax = materialSourceTable.getColumn("LabelColor").getScale();
        if (labelColor != null && labelColor.length() > labelColorMax)
            throw new ExperimentException("Label color may not exceed " + labelColorMax + " characters.");

        // Validate the metricUnit length
        int metricUnitMax = materialSourceTable.getColumn("MetricUnit").getScale();
        if (metricUnit != null && metricUnit.length() > metricUnitMax)
            throw new ExperimentException("Metric unit may not exceed " + metricUnitMax + " characters.");

        // Validate the category length
        int categoryMax = materialSourceTable.getColumn("Category").getScale();
        if (category != null && category.length() > categoryMax)
            throw new ExperimentException("Category may not exceed " + categoryMax + " characters.");

        Pair<String, String> dbSeqLsids = getSampleTypeSamplePrefixLsids(c);
        String lsid = dbSeqLsids.first;
        String materialPrefixLsid = dbSeqLsids.second;
        Domain domain = PropertyService.get().createDomain(c, lsid, name, templateInfo);
        DomainKind<?> kind = domain.getDomainKind();
        if (kind != null)
            domain.setDisabledSystemFields(kind.getDisabledSystemFields(disabledSystemField));
        Set<String> reservedNames = kind.getReservedPropertyNames(domain, u);
        Set<String> reservedPrefixes = kind.getReservedPropertyNamePrefixes();
        Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());

        boolean hasNameProperty = false;
        String idUri1 = null, idUri2 = null, idUri3 = null, parentUri = null;
        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        Set<String> propertyUris = new HashSet<>();
        List<GWTPropertyDescriptor> calculatedFields = new ArrayList<>();
        for (int i = 0; i < properties.size(); i++)
        {
            GWTPropertyDescriptor pd = properties.get(i);
            String propertyName = pd.getName().toLowerCase();

            // calculatedFields will be handled separately
            if (pd.getValueExpression() != null)
            {
                calculatedFields.add(pd);
                continue;
            }

            if (ExpMaterialTable.Column.Name.name().equalsIgnoreCase(propertyName))
            {
                hasNameProperty = true;
            }
            else
            {
                if (!reservedPrefixes.isEmpty())
                {
                    Optional<String> reservedPrefix = reservedPrefixes.stream().filter(prefix -> propertyName.startsWith(prefix.toLowerCase())).findAny();
                    reservedPrefix.ifPresent(s -> {
                        throw new IllegalArgumentException("The prefix '" + s + "' is reserved for system use.");
                    });
                }

                if (lowerReservedNames.contains(propertyName))
                {
                    throw new IllegalArgumentException("Property name '" + propertyName + "' is a reserved name.");
                }

                DomainProperty dp = DomainUtil.addProperty(domain, pd, defaultValues, propertyUris, null);

                if (dp != null)
                {
                    if (idCol1 == i) idUri1 = dp.getPropertyURI();
                    if (idCol2 == i) idUri2 = dp.getPropertyURI();
                    if (idCol3 == i) idUri3 = dp.getPropertyURI();
                    if (parentCol == i) parentUri = dp.getPropertyURI();
                }
            }
        }

        domain.setPropertyIndices(indices, lowerReservedNames);

        if (!hasNameProperty && idUri1 == null)
            throw new ExperimentException("Either a 'Name' property or an index for idCol1 is required");

        if (hasNameProperty && idUri1 != null)
            throw new ExperimentException("Either a 'Name' property or idCols can be used, but not both");

        String importAliasJson = ExperimentJSONConverter.getAliasJson(importAliases, name);

        MaterialSource source = new MaterialSource();
        source.setLSID(lsid);
        source.setName(name);
        source.setDescription(description);
        source.setMaterialLSIDPrefix(materialPrefixLsid);
        if (nameExpression != null)
            source.setNameExpression(nameExpression);
        if (aliquotNameExpression != null)
            source.setAliquotNameExpression(aliquotNameExpression);
        source.setLabelColor(labelColor);
        source.setMetricUnit(metricUnit);
        source.setAutoLinkTargetContainer(autoLinkTargetContainer);
        source.setAutoLinkCategory(autoLinkCategory);
        source.setCategory(category);
        source.setContainer(c);
        source.setMaterialParentImportAliasMap(importAliasJson);

        if (hasNameProperty)
        {
            source.setIdCol1(ExpMaterialTable.Column.Name.name());
        }
        else
        {
            source.setIdCol1(idUri1);
            if (idUri2 != null)
                source.setIdCol2(idUri2);
            if (idUri3 != null)
                source.setIdCol3(idUri3);
        }
        if (parentUri != null)
            source.setParentCol(parentUri);

        final ExpSampleTypeImpl st = new ExpSampleTypeImpl(source);

        try
        {
            getExpSchema().getScope().executeWithRetry(transaction ->
            {
                try
                {
                    domain.save(u);
                    st.save(u);
                    QueryService.get().saveCalculatedFieldsMetadata(SamplesSchema.SCHEMA_NAME, name, null, calculatedFields, false, u, c);
                    DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);
                    if (excludedContainerIds != null && !excludedContainerIds.isEmpty())
                        ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.SampleType, excludedContainerIds, st.getRowId(), u);
                    if (excludedDashboardContainerIds != null && !excludedDashboardContainerIds.isEmpty())
                        ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.DashboardSampleType, excludedDashboardContainerIds, st.getRowId(), u);
                    transaction.addCommitTask(() -> clearMaterialSourceCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
                    return st;
                }
                catch (ExperimentException | MetadataUnavailableException eex)
                {
                    throw new DbScope.RetryPassthroughException(eex);
                }
            });
        }
        catch (DbScope.RetryPassthroughException x)
        {
            x.rethrow(ExperimentException.class);
            throw x;
        }

        return st;
    }

    public enum SampleSequenceType
    {
        DAILY("yyyy-MM-dd"),
        WEEKLY("YYYY-'W'ww"),
        MONTHLY("yyyy-MM"),
        YEARLY("yyyy");

        final DateTimeFormatter _formatter;

        SampleSequenceType(String pattern)
        {
            _formatter = DateTimeFormatter.ofPattern(pattern);
        }

        public Pair<String,Integer> getSequenceName(@Nullable Date date)
        {
            LocalDateTime ldt;
            if (date == null)
                ldt = LocalDateTime.now();
            else
                ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            String suffix = _formatter.format(ldt);
            // NOTE: it would make sense to use the dbsequence "id" feature here.
            // e.g. instead of name=org.labkey.api.exp.api.ExpMaterial:DAILY:2021-05-25 id=0
            // we could use name=org.labkey.api.exp.api.ExpMaterial:DAILY id=20210525
            // however, that would require a fix up on upgrade.
            return new Pair<>("org.labkey.api.exp.api.ExpMaterial:" + name() + ":" + suffix, 0);
        }

        public long next(Date date)
        {
            return getDbSequence(date).next();
        }

        public DbSequence getDbSequence(Date date)
        {
            Pair<String,Integer> seqName = getSequenceName(date);
            final DbSequence seq = DbSequenceManager.getPreallocatingSequence(ContainerManager.getRoot(), seqName.first, seqName.second, 100);
            return seq;
        }
    }


    @Override
    public Function<Map<String,Long>,Map<String,Long>> getSampleCountsFunction(@Nullable Date counterDate)
    {
        final var dailySampleCount = SampleSequenceType.DAILY.getDbSequence(counterDate);
        final var weeklySampleCount = SampleSequenceType.WEEKLY.getDbSequence(counterDate);
        final var monthlySampleCount = SampleSequenceType.MONTHLY.getDbSequence(counterDate);
        final var yearlySampleCount = SampleSequenceType.YEARLY.getDbSequence(counterDate);

        return (counts) ->
        {
            if (null==counts)
                counts = new HashMap<>();
            counts.put("dailySampleCount",   dailySampleCount.next());
            counts.put("weeklySampleCount",  weeklySampleCount.next());
            counts.put("monthlySampleCount", monthlySampleCount.next());
            counts.put("yearlySampleCount",  yearlySampleCount.next());
            return counts;
        };
    }

    @Override
    public ValidationException updateSampleType(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings)
    {
        ValidationException errors;

        ExpSampleTypeImpl st = new ExpSampleTypeImpl(getMaterialSource(update.getDomainURI()));

        String newName = StringUtils.trimToNull(update.getName());
        String oldSampleTypeName = st.getName();
        boolean hasNameChange = false;
        if (newName != null && !oldSampleTypeName.equals(newName))
        {
            ExpSampleType duplicateType = SampleTypeService.get().getSampleType(container, user, newName);
            if (duplicateType != null)
                throw new IllegalArgumentException("A Sample Type with name '" + newName + "' already exists.");

            hasNameChange = true;
            st.setName(newName);
        }

        String newDescription = StringUtils.trimToNull(update.getDescription());
        String description = st.getDescription();
        if (description == null || !description.equals(newDescription))
        {
            st.setDescription(newDescription);
        }

        boolean hasMetricUnitChanged = false;

        if (options != null)
        {
            String sampleIdPattern = StringUtils.trimToNull(StringUtilsLabKey.replaceBadCharacters(options.getNameExpression()));
            String oldPattern = st.getNameExpression();
            if (oldPattern == null || !oldPattern.equals(sampleIdPattern))
            {
                st.setNameExpression(sampleIdPattern);
                if (!NameExpressionOptionService.get().allowUserSpecifiedNames(container) && sampleIdPattern == null)
                    throw new IllegalArgumentException(container.hasProductProjects() ? NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS : NAME_EXPRESSION_REQUIRED_MSG);
            }

            String aliquotIdPattern = StringUtils.trimToNull(options.getAliquotNameExpression());
            String oldAliquotPattern = st.getAliquotNameExpression();
            if (oldAliquotPattern == null || !oldAliquotPattern.equals(aliquotIdPattern))
            {
                st.setAliquotNameExpression(aliquotIdPattern);
            }

            st.setLabelColor(options.getLabelColor());
            String oldMetricUnit = StringUtils.trimToNull(st.getMetricUnit());
            String newMetricUnit = StringUtils.trimToNull(options.getMetricUnit());

            if (!Objects.equals(oldMetricUnit, newMetricUnit))
                hasMetricUnitChanged = true;

            st.setMetricUnit(options.getMetricUnit());

            if (options.getImportAliases() != null && !options.getImportAliases().isEmpty())
            {
                try
                {
                    Map<String, Map<String, Object>> newAliases = options.getImportAliases();
                    Set<String> existingRequiredInputs = new HashSet<>(st.getRequiredImportAliases().values());
                    String invalidParentType = ExperimentServiceImpl.get().getInvalidRequiredImportAliasUpdate(st.getLSID(), true, newAliases, existingRequiredInputs, container, user);
                    if (invalidParentType != null)
                        throw new IllegalArgumentException("'" + invalidParentType + "' cannot be required as a parent type when there are existing samples without a parent of this type.");

                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }

            st.setImportAliasMap(options.getImportAliases());
            String targetContainerId = StringUtils.trimToNull(options.getAutoLinkTargetContainerId());
            st.setAutoLinkTargetContainer(targetContainerId != null ? ContainerManager.getForId(targetContainerId) : null);
            st.setAutoLinkCategory(options.getAutoLinkCategory());
            if (options.getCategory() != null) // update sample type category is currently not supported
                st.setCategory(options.getCategory());
        }

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            st.save(user, true);
            String auditComment = null;
            if (hasNameChange)
            {
                QueryChangeListener.QueryPropertyChange.handleQueryNameChange(oldSampleTypeName, newName, new SchemaKey(null, SamplesSchema.SCHEMA_NAME), user, container);
                auditComment = "The name of the sample type '" + oldSampleTypeName + "' was changed to '" + newName + "'.";
            }

            errors = DomainUtil.updateDomainDescriptor(original, update, container, user, hasNameChange, auditComment);

            if (!errors.hasErrors())
            {
                QueryService.get().saveCalculatedFieldsMetadata(SamplesSchema.SCHEMA_NAME, update.getQueryName(), hasNameChange ? newName : null, update.getCalculatedFields(), !original.getCalculatedFields().isEmpty(), user, container);

                if (hasNameChange)
                    ExperimentService.get().addObjectLegacyName(st.getObjectId(), ExperimentServiceImpl.getNamespacePrefix(ExpSampleType.class), oldSampleTypeName, user);

                if (options != null && options.getExcludedContainerIds() != null)
                    ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.SampleType, options.getExcludedContainerIds(), st.getRowId(), user);
                if (options != null && options.getExcludedDashboardContainerIds() != null)
                    ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.DashboardSampleType, options.getExcludedDashboardContainerIds(), st.getRowId(), user);

                boolean finalHasMetricUnitChanged = hasMetricUnitChanged;
                transaction.addCommitTask(() -> {
                    clearMaterialSourceCache(container);
                    SampleTypeServiceImpl.get().indexSampleType(st);

                    if (finalHasMetricUnitChanged)
                    {
                        try
                        {
                            recomputeSampleTypeRollup(st, container);
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeSQLException(e);
                        }
                    }

                }, DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
                transaction.commit();
                refreshSampleTypeMaterializedView(st, SampleChangeType.schema);
            }
        }
        catch (MetadataUnavailableException e)
        {
            errors = new ValidationException();
            errors.addError(new SimpleValidationError(e.getMessage()));
        }

        return errors;
    }

    public String getCommentDetailed(QueryService.AuditAction action, boolean isUpdate)
    {
        String comment = SampleTimelineAuditEvent.SampleTimelineEventType.getActionCommentDetailed(action, isUpdate);
        return StringUtils.isEmpty(comment) ? action.getCommentDetailed() : comment;
    }

    @Override
    public DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> row, Map<String, Object> existingRow)
    {
        return createAuditRecord(c, getCommentDetailed(action, !existingRow.isEmpty()), userComment, action, row, existingRow);
    }

    @Override
    protected AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row)
    {
        return createAuditRecord(c, String.format(action.getCommentSummary(), rowCount), userComment, row);
    }

    @Override
    protected void addDetailedModifiedFields(Map<String, Object> originalRow, Map<String, Object> modifiedRow, Map<String, Object> updatedRow)
    {
        // we want to include the fields that indicate parent lineage has changed.
        // Note that we don't need to check for output fields because lineage can be modified only by changing inputs not outputs
        updatedRow.forEach((fieldName, value) -> {
            if (fieldName.toLowerCase().startsWith(ExpData.DATA_INPUT_PARENT.toLowerCase()) || fieldName.toLowerCase().startsWith(ExpMaterial.MATERIAL_INPUT_PARENT.toLowerCase()))
                if (!originalRow.containsKey(fieldName))
                {
                    modifiedRow.put(fieldName, value);
                }
        });
    }

    private SampleTimelineAuditEvent createAuditRecord(Container c, String comment, String userComment, @Nullable Map<String, Object> row)
    {
        return createAuditRecord(c, comment, userComment, null, row, null);
    }

    private boolean isInputFieldKey(String fieldKey)
    {
        int slash = fieldKey.indexOf('/');
        return  slash==ExpData.DATA_INPUT_PARENT.length() && StringUtils.startsWithIgnoreCase(fieldKey,ExpData.DATA_INPUT_PARENT) ||
                slash==ExpMaterial.MATERIAL_INPUT_PARENT.length() && StringUtils.startsWithIgnoreCase(fieldKey,ExpMaterial.MATERIAL_INPUT_PARENT);
    }

    private SampleTimelineAuditEvent createAuditRecord(Container c, String comment, String userComment, @Nullable QueryService.AuditAction action, @Nullable Map<String, Object> row, @Nullable Map<String, Object> existingRow)
    {
        SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(c.getId(), comment);
        event.setUserComment(userComment);
        var tx = getExpSchema().getScope().getCurrentTransaction();
        if (tx != null)
            event.setTransactionId(tx.getAuditId());

        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        var staticsRow = existingRow != null && !existingRow.isEmpty() ? existingRow : row;
        if (row != null)
        {
            Optional<String> parentFields = row.keySet().stream().filter(this::isInputFieldKey).findAny();
            event.setLineageUpdate(parentFields.isPresent());

            if (staticsRow.containsKey(LSID))
                event.setSampleLsid(String.valueOf(staticsRow.get(LSID)));
            if (staticsRow.containsKey(ROW_ID) && staticsRow.get(ROW_ID) != null)
                event.setSampleId((Integer) staticsRow.get(ROW_ID));
            if (staticsRow.containsKey(NAME))
                event.setSampleName(String.valueOf(staticsRow.get(NAME)));

            String sampleTypeLsid = null;
            if (staticsRow.containsKey(CPAS_TYPE))
                sampleTypeLsid =  String.valueOf(staticsRow.get(CPAS_TYPE));
            // When a sample is deleted, the LSID is provided via the "sampleset" field instead of "LSID"
            if (sampleTypeLsid == null && staticsRow.containsKey("sampleset"))
                sampleTypeLsid = String.valueOf(staticsRow.get("sampleset"));

            ExpSampleType sampleType = null;
            if (sampleTypeLsid != null)
                sampleType = SampleTypeService.get().getSampleTypeByType(sampleTypeLsid, c);
            else if (event.getSampleId() > 0)
            {
                ExpMaterial sample = ExperimentService.get().getExpMaterial(event.getSampleId());
                if (sample != null) sampleType = sample.getSampleType();
            }
            else if (event.getSampleLsid() != null)
            {
                ExpMaterial sample = ExperimentService.get().getExpMaterial(event.getSampleLsid());
                if (sample != null) sampleType = sample.getSampleType();
            }
            if (sampleType != null)
            {
                event.setSampleType(sampleType.getName());
                event.setSampleTypeId(sampleType.getRowId());
            }

            // NOTE: to avoid a diff in the audit log make sure row("rowid") is correct! (not the unused generated value)
            row.put(ROW_ID,staticsRow.get(ROW_ID));
        }

        if (action != null)
        {
            Map<String, Object> eventMetadata = new HashMap<>();
            SampleTimelineAuditEvent.SampleTimelineEventType timelineEventType = SampleTimelineAuditEvent.SampleTimelineEventType.getTypeFromAction(action);
            if (timelineEventType != null)
                eventMetadata.put(SAMPLE_TIMELINE_EVENT_TYPE, action);
            event.setMetadata(AbstractAuditTypeProvider.encodeForDataMap(c, eventMetadata));
        }

        return event;
    }

    private SampleTimelineAuditEvent createAuditRecord(Container container, String comment, String userComment, ExpMaterial sample, @Nullable Map<String, Object> metadata)
    {
        SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(container.getId(), comment);
        if (getExpSchema().getScope().getCurrentTransaction() != null)
            event.setTransactionId(getExpSchema().getScope().getCurrentTransaction().getAuditId());
        if (container.getProject() != null)
            event.setProjectId(container.getProject().getId());
        event.setSampleName(sample.getName());
        event.setSampleLsid(sample.getLSID());
        event.setSampleId(sample.getRowId());
        ExpSampleType type = sample.getSampleType();
        if (type != null)
        {
            event.setSampleType(type.getName());
            event.setSampleTypeId(type.getRowId());
        }
        event.setUserComment(userComment);
        event.setMetadata(AbstractAuditTypeProvider.encodeForDataMap(container, metadata));
        return event;
    }

    @Override
    public void addAuditEvent(User user, Container container, String comment, String userComment, ExpMaterial sample, Map<String, Object> metadata)
    {
        AuditLogService.get().addEvent(user, createAuditRecord(container, comment, userComment, sample, metadata));
    }

    @Override
    public void addAuditEvent(User user, Container container, String comment, String userComment, ExpMaterial sample, Map<String, Object> metadata, String updateType)
    {
        SampleTimelineAuditEvent event = createAuditRecord(container, comment, userComment, sample, metadata);
        event.setInventoryUpdateType(updateType);
        event.setUserComment(userComment);
        AuditLogService.get().addEvent(user, event);
    }

    @Override
    public long getMaxAliquotId(@NotNull String sampleName, @NotNull String sampleTypeLsid, Container container)
    {
        long max = 0;
        String aliquotNamePrefix = sampleName + "-";

        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("cpastype"), sampleTypeLsid);
        filter.addCondition(FieldKey.fromParts("Name"), aliquotNamePrefix, STARTS_WITH);

        TableSelector selector = new TableSelector(getTinfoMaterial(), Collections.singleton("Name"), filter, null);
        final List<String> aliquotIds = new ArrayList<>();
        selector.forEach(String.class, fullname -> aliquotIds.add(fullname.replace(aliquotNamePrefix, "")));

        for (String aliquotId : aliquotIds)
        {
            try
            {
                long id = Long.parseLong(aliquotId);
                if (id > max)
                    max = id;
            }
            catch (NumberFormatException ignored) {
                ;
            }
        }

        return max;
    }

    @Override
    public Collection<? extends ExpMaterial> getSamplesNotPermitted(Collection<? extends ExpMaterial> samples, SampleOperations operation)
    {
        return samples.stream()
                .filter(sample -> !sample.isOperationPermitted(operation))
                .collect(Collectors.toList());
    }

    @Override
    public String getOperationNotPermittedMessage(Collection<? extends ExpMaterial> samples, SampleOperations operation)
    {
        String message;
        if (samples.size() == 1)
        {
            ExpMaterial sample = samples.iterator().next();
            message = "Sample " + sample.getName() + " has status " + sample.getStateLabel() + ", which prevents";
        }
        else
        {
            message = samples.size() + " samples (";
            message += samples.stream().limit(10).map(ExpMaterial::getNameAndStatus).collect(Collectors.joining(", "));
            if (samples.size() > 10)
                message += " ...";
            message += ") have statuses that prevent";
        }
        return message + " " + operation.getDescription() + ".";
    }

    /** This method updates exp.material, caller should call {@link SampleTypeServiceImpl#refreshSampleTypeMaterializedView} as appropriate. */
    @Override
    public int recomputeSampleTypeRollup(ExpSampleType sampleType, Container container) throws IllegalStateException, SQLException
    {
        Pair<Collection<Integer>, Collection<Integer>> parentsGroup = getAliquotParentsForRecalc(sampleType.getLSID(), container);
        Collection<Integer> allParents = parentsGroup.first;
        Collection<Integer> withAmountsParents = parentsGroup.second;
        return recomputeSamplesRollup(allParents, withAmountsParents, sampleType.getMetricUnit(), container);
    }

    /** This method updates exp.material, caller should call {@link SampleTypeServiceImpl#refreshSampleTypeMaterializedView} as appropriate. */
    @Override
    public int recomputeSamplesRollup(Collection<Integer> sampleIds, String sampleTypeMetricUnit, Container container) throws IllegalStateException, SQLException
    {
        return recomputeSamplesRollup(sampleIds, sampleIds, sampleTypeMetricUnit, container);
    }

    public record AliquotAmountUnitResult(Double amount, String unit, boolean isAvailable) {}

    public record AliquotAvailableAmountUnit(Double amount, String unit, Double availableAmount) {}

    /** This method updates exp.material, caller should call {@link SampleTypeServiceImpl#refreshSampleTypeMaterializedView} as appropriate. */
    private int recomputeSamplesRollup(Collection<Integer> parents, Collection<Integer> withAmountsParents, String sampleTypeUnit, Container container) throws IllegalStateException, SQLException
    {
        return recomputeSamplesRollup(parents, null, withAmountsParents, sampleTypeUnit, container, false);
    }

    /** This method updates exp.material, caller should call {@link SampleTypeServiceImpl#refreshSampleTypeMaterializedView} as appropriate. */
    public int recomputeSamplesRollup(
        Collection<Integer> parents,
        @Nullable Collection<Integer> availableParents,
        Collection<Integer> withAmountsParents,
        String sampleTypeUnit,
        Container container,
        boolean useRootMaterialLSID
    ) throws IllegalStateException, SQLException
    {
        Map<Integer, String> sampleUnits = new HashMap<>();
        TableInfo materialTable = ExperimentService.get().getTinfoMaterial();
        DbScope scope = materialTable.getSchema().getScope();

        List<Integer> availableSampleStates = new ArrayList<>();

        if (SampleStatusService.get().supportsSampleStatus())
        {
            for (DataState state: SampleStatusService.get().getAllProjectStates(container))
            {
                if (ExpSchema.SampleStateType.Available.name().equals(state.getStateType()))
                    availableSampleStates.add(state.getRowId());
            }
        }

        if (!parents.isEmpty())
        {
            Map<Integer, Pair<Integer, String>> sampleAliquotCounts = getSampleAliquotCounts(parents, useRootMaterialLSID);
            try (Connection c = scope.getConnection())
            {
                Parameter rowid = new Parameter("rowid", JdbcType.INTEGER);
                Parameter count = new Parameter("rollupCount", JdbcType.INTEGER);
                ParameterMapStatement pm = new ParameterMapStatement(scope, c,
                        new SQLFragment("UPDATE ").append(materialTable).append(" SET AliquotCount = ? WHERE RowId = ?").addAll(count, rowid), null);

                List<Map.Entry<Integer, Pair<Integer, String>>> sampleAliquotCountList = new ArrayList<>(sampleAliquotCounts.entrySet());

                ListUtils.partition(sampleAliquotCountList, 1000).forEach(sublist ->
                {
                    for (Map.Entry<Integer, Pair<Integer, String>> sampleAliquotCount: sublist)
                    {
                        Integer sampleId = sampleAliquotCount.getKey();
                        Integer aliquotCount = sampleAliquotCount.getValue().first;
                        String sampleUnit = sampleAliquotCount.getValue().second;
                        sampleUnits.put(sampleId, sampleUnit);

                        rowid.setValue(sampleId);
                        count.setValue(aliquotCount);

                        pm.addBatch();
                    }
                    pm.executeBatch();
                });
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }

        if (!parents.isEmpty() || (availableParents != null && !availableParents.isEmpty()))
        {
            Map<Integer, Pair<Integer, String>> sampleAliquotCounts = getSampleAvailableAliquotCounts(availableParents == null ? parents : availableParents, availableSampleStates, useRootMaterialLSID);
            try (Connection c = scope.getConnection())
            {
                Parameter rowid = new Parameter("rowid", JdbcType.INTEGER);
                Parameter count = new Parameter("AvailableAliquotCount", JdbcType.INTEGER);
                ParameterMapStatement pm = new ParameterMapStatement(scope, c,
                        new SQLFragment("UPDATE ").append(materialTable).append(" SET AvailableAliquotCount = ? WHERE RowId = ?").addAll(count, rowid), null);

                List<Map.Entry<Integer, Pair<Integer, String>>> sampleAliquotCountList = new ArrayList<>(sampleAliquotCounts.entrySet());

                ListUtils.partition(sampleAliquotCountList, 1000).forEach(sublist ->
                {
                    for (Map.Entry<Integer, Pair<Integer, String>> sampleAliquotCount: sublist)
                    {
                        Integer sampleId = sampleAliquotCount.getKey();
                        Integer aliquotCount = sampleAliquotCount.getValue().first;
                        String sampleUnit = sampleAliquotCount.getValue().second;
                        sampleUnits.put(sampleId, sampleUnit);

                        rowid.setValue(sampleId);
                        count.setValue(aliquotCount);

                        pm.addBatch();
                    }
                    pm.executeBatch();
                });
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }

        if (!withAmountsParents.isEmpty())
        {
            Map<Integer, List<AliquotAmountUnitResult>> samplesAliquotAmounts = getSampleAliquotAmounts(withAmountsParents, availableSampleStates, useRootMaterialLSID);

            try (Connection c = scope.getConnection())
            {
                Parameter rowid = new Parameter("rowid", JdbcType.INTEGER);
                Parameter amount = new Parameter("amount", JdbcType.DOUBLE);
                Parameter unit = new Parameter("unit", JdbcType.VARCHAR);
                Parameter availableAmount = new Parameter("availableAmount", JdbcType.DOUBLE);

                ParameterMapStatement pm = new ParameterMapStatement(scope, c,
                        new SQLFragment("UPDATE ").append(materialTable).append(" SET AliquotVolume = ?, AliquotUnit = ? , AvailableAliquotVolume = ? WHERE RowId = ? ").addAll(amount, unit, availableAmount, rowid), null);

                List<Map.Entry<Integer, List<AliquotAmountUnitResult>>> sampleAliquotAmountsList = new ArrayList<>(samplesAliquotAmounts.entrySet());

                ListUtils.partition(sampleAliquotAmountsList, 1000).forEach(sublist ->
                {
                    for (Map.Entry<Integer, List<AliquotAmountUnitResult>> sampleAliquotAmounts: sublist)
                    {
                        Integer sampleId = sampleAliquotAmounts.getKey();
                        List<AliquotAmountUnitResult> aliquotAmounts = sampleAliquotAmounts.getValue();

                        AliquotAvailableAmountUnit amountUnit = convertToDisplayUnits(aliquotAmounts, sampleTypeUnit, sampleUnits.get(sampleId));
                        if (amountUnit == null)
                            continue;

                        rowid.setValue(sampleId);
                        amount.setValue(amountUnit.amount);
                        unit.setValue(amountUnit.unit);
                        availableAmount.setValue(amountUnit.availableAmount);

                        pm.addBatch();
                    }
                    pm.executeBatch();
                });
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }

        return !parents.isEmpty() ? parents.size() : (availableParents != null ? availableParents.size() : withAmountsParents.size());
    }

    @Override
    public int recomputeSampleTypeRollup(@NotNull ExpSampleType sampleType, Set<Integer> rootRowIds, Set<String> parentNames, Container container) throws SQLException
    {
        Set<Integer> rootSamplesToRecalc = new HashSet<>();
        if (rootRowIds != null)
            rootSamplesToRecalc.addAll(rootRowIds);
        if (parentNames != null)
            rootSamplesToRecalc.addAll(getRootSampleIdsFromParentNames(sampleType.getLSID(), parentNames));

        return recomputeSamplesRollup(rootSamplesToRecalc, rootSamplesToRecalc, sampleType.getMetricUnit(), container);
    }

    private Set<Integer> getRootSampleIdsFromParentNames(String sampleTypeLsid, Set<String> parentNames)
    {
        if (parentNames == null || parentNames.isEmpty())
            return Collections.emptySet();

        TableInfo tableInfo = ExperimentService.get().getTinfoMaterial();

        SQLFragment sql = new SQLFragment("SELECT rowid FROM ").append(tableInfo, "")
                .append(" WHERE cpastype = ").appendValue(sampleTypeLsid)
                .append(" AND rowid IN (")
                .append(" SELECT DISTINCT rootmaterialrowid FROM ").append(tableInfo, "")
                .append(" WHERE Name").appendInClause(parentNames, tableInfo.getSqlDialect())
                .append(")");

        return new SqlSelector(tableInfo.getSchema(), sql).fillSet(new HashSet<>());
    }

    private AliquotAvailableAmountUnit convertToDisplayUnits(List<AliquotAmountUnitResult> volumeUnits, String sampleTypeUnitsStr, String sampleItemUnit)
    {
        if (volumeUnits == null || volumeUnits.isEmpty())
            return null;

        String totalDisplayUnitStr = sampleTypeUnitsStr;
        Measurement.Unit totalDisplayUnit = null;

        if (StringUtils.isEmpty(totalDisplayUnitStr) && (sampleItemUnit != null))
        {
            // if sample type lacks unit, but the sample has a unit, use sample's unit
            totalDisplayUnitStr = sampleItemUnit;
        }

        // if sample unit is empty, use 1st aliquot unit
        if (StringUtils.isEmpty(totalDisplayUnitStr))
        {
            String aliquotUnit = volumeUnits.get(0).unit;
            if (!StringUtils.isEmpty(aliquotUnit))
                totalDisplayUnitStr = aliquotUnit;
        }

        if (!StringUtils.isEmpty(totalDisplayUnitStr))
        {
            try
            {
                totalDisplayUnit = Measurement.Unit.valueOf(totalDisplayUnitStr);
            }
            catch (IllegalArgumentException e)
            {
                // do nothing; leave unit as null;
            }
        }

        Double totalVolume = 0.0;
        Double totalAvailableVolume = 0.0;

        for (AliquotAmountUnitResult volumeUnit : volumeUnits)
        {
            Measurement.Unit unit = null;
            try
            {
                double storedAmount = volumeUnit.amount;
                String aliquotUnit = volumeUnit.unit;
                boolean isAvailable = volumeUnit.isAvailable;

                try
                {
                    unit = StringUtils.isEmpty(aliquotUnit) ? totalDisplayUnit : Measurement.Unit.valueOf(aliquotUnit);
                }
                catch (IllegalArgumentException ignore)
                {
                }

                double convertedAmount = 0;
                // include in total volume only if aliquot unit is compatible
                if (totalDisplayUnit != null && totalDisplayUnit.isCompatible(unit))
                    convertedAmount = unit.convertAmount(storedAmount, totalDisplayUnit);
                else if (totalDisplayUnit == null) // sample (or 1st aliquot) unit is not a supported unit, or is blank
                {
                    if (StringUtils.isEmpty(totalDisplayUnitStr) && StringUtils.isEmpty(aliquotUnit)) //aliquot units are empty
                        convertedAmount = storedAmount;
                    else if (totalDisplayUnitStr != null && totalDisplayUnitStr.equalsIgnoreCase(aliquotUnit)) //aliquot units use the same no supported unit ('cc')
                        convertedAmount = storedAmount;
                }

                totalVolume += convertedAmount;
                if (isAvailable)
                    totalAvailableVolume += convertedAmount;
            }
            catch (IllegalArgumentException ignore) // invalid volume
            {

            }
        }

        totalVolume = Precision.round(totalVolume, 6);
        totalAvailableVolume = Precision.round(totalAvailableVolume, 6);

        if (Double.compare(totalVolume, 0.0) == 0)
            totalDisplayUnit = null;

        return new AliquotAvailableAmountUnit(totalVolume, totalDisplayUnit == null ? null : totalDisplayUnit.name(), totalAvailableVolume);
    }

    public Pair<Collection<Integer>, Collection<Integer>> getAliquotParentsForRecalc(String sampleTypeLsid, Container container) throws SQLException
    {
        Collection<Integer> parents = getAliquotParents(sampleTypeLsid, container);
        Collection<Integer> withAmountsParents = parents.isEmpty() ? Collections.emptySet() : getAliquotsWithAmountsParents(sampleTypeLsid, container);
        return new Pair<>(parents, withAmountsParents);
    }

    private Collection<Integer> getAliquotParents(String sampleTypeLsid, Container container) throws IllegalStateException, SQLException
    {
        return getAliquotParents(sampleTypeLsid, false, container);
    }

    private Collection<Integer> getAliquotsWithAmountsParents(String sampleTypeLsid, Container container) throws IllegalStateException, SQLException
    {
        return getAliquotParents(sampleTypeLsid, true, container);
    }

    private SQLFragment getParentsOfAliquotsWithAmountsSql()
    {
        return new SQLFragment(
    """
        SELECT DISTINCT parent.rowId, parent.cpastype
        FROM exp.material AS aliquot
            JOIN exp.material AS parent ON aliquot.rootMaterialRowId = parent.rowId AND aliquot.rootMaterialRowId <> aliquot.rowId
        WHERE aliquot.storedAmount IS NOT NULL AND\s
        """);
    }

    private SQLFragment getParentsOfAliquotsSql()
    {
        return new SQLFragment(
    """
        SELECT DISTINCT parent.rowId, parent.cpastype
        FROM exp.material AS aliquot
            JOIN exp.material AS parent ON aliquot.rootMaterialRowId = parent.rowId AND aliquot.rootMaterialRowId <> aliquot.rowId
        WHERE
        """);
    }

    private Collection<Integer> getAliquotParents(String sampleTypeLsid, boolean withAmount, Container container) throws SQLException
    {
        DbSchema dbSchema = getExpSchema();
        SqlDialect dialect = dbSchema.getSqlDialect();

        SQLFragment sql = withAmount ? getParentsOfAliquotsWithAmountsSql() : getParentsOfAliquotsSql();

        sql.append("parent.cpastype = ?");
        sql.add(sampleTypeLsid);
        sql.append(" AND parent.container = ?");
        sql.add(container.getId());

        Set<Integer> parentIds = new HashSet<>();
        try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
        {
            while (rs.next())
                parentIds.add(rs.getInt(1));
        }

        return parentIds;
    }

    private Map<Integer, Pair<Integer, String>> getSampleAliquotCounts(Collection<Integer> sampleIds, boolean useRootMaterialLSID) throws SQLException
    {
        DbSchema dbSchema = getExpSchema();
        SqlDialect dialect = dbSchema.getSqlDialect();

        // Issue 49150: In 23.12 we migrated from RootMaterialLSID to RootMaterialRowID, however, there is still an
        // upgrade path that requires these queries be done with RootMaterialLSID since the 23.12 upgrade will not
        // have run yet.
        SQLFragment sql = new SQLFragment("SELECT m.RowId as SampleId, m.Units, (SELECT COUNT(*) FROM exp.material a WHERE ")
                .append(useRootMaterialLSID ? "a.rootMaterialLsid = m.lsid" : "a.rootMaterialRowId = m.rowId")
                .append(")-1 AS CreatedAliquotCount FROM exp.material AS m WHERE m.rowid\s");
        dialect.appendInClauseSql(sql, sampleIds);

        Map<Integer, Pair<Integer, String>> sampleAliquotCounts = new HashMap<>();
        try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
        {
            while (rs.next())
            {
                int parentId = rs.getInt(1);
                String sampleUnit = rs.getString(2);
                int aliquotCount = rs.getInt(3);

                sampleAliquotCounts.put(parentId, new Pair<>(aliquotCount, sampleUnit));
            }
        }

        return sampleAliquotCounts;
    }

    private Map<Integer, Pair<Integer, String>> getSampleAvailableAliquotCounts(Collection<Integer> sampleIds, Collection<Integer> availableSampleStates, boolean useRootMaterialLSID) throws SQLException
    {
        DbSchema dbSchema = getExpSchema();
        SqlDialect dialect = dbSchema.getSqlDialect();

        // Issue 49150: In 23.12 we migrated from RootMaterialLSID to RootMaterialRowID, however, there is still an
        // upgrade path that requires these queries be done with RootMaterialLSID since the 23.12 upgrade will not
        // have run yet.
        SQLFragment sql;
        if (useRootMaterialLSID)
        {
            sql = new SQLFragment(
            """
                    SELECT m.RowId as SampleId, m.Units,
                    (CASE WHEN c.aliquotCount IS NULL THEN 0 ELSE c.aliquotCount END) as CreatedAliquotCount
                    FROM exp.material AS m
                        LEFT JOIN (
                        SELECT RootMaterialLSID as rootLsid, COUNT(*) as aliquotCount
                        FROM exp.material
                        WHERE RootMaterialLSID <> LSID AND SampleState\s""")
                .appendInClause(availableSampleStates, dialect)
                .append("""
                        GROUP BY RootMaterialLSID
                    ) AS c ON m.lsid = c.rootLsid
                    WHERE m.rootmateriallsid = m.LSID AND m.rowid\s""");
        }
        else
        {
            sql = new SQLFragment(
            """
                    SELECT m.RowId as SampleId, m.Units,
                    (CASE WHEN c.aliquotCount IS NULL THEN 0 ELSE c.aliquotCount END) as CreatedAliquotCount
                    FROM exp.material AS m
                        LEFT JOIN (
                        SELECT RootMaterialRowId as rootRowId, COUNT(*) as aliquotCount
                        FROM exp.material
                        WHERE RootMaterialRowId <> RowId AND SampleState\s""")
                .appendInClause(availableSampleStates, dialect)
                .append("""
                        GROUP BY RootMaterialRowId
                    ) AS c ON m.rowId = c.rootRowId
                    WHERE m.rootmaterialrowid = m.rowid AND m.rowid\s""");
        }
        dialect.appendInClauseSql(sql, sampleIds);

        Map<Integer, Pair<Integer, String>> sampleAliquotCounts = new HashMap<>();
        try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
        {
            while (rs.next())
            {
                int parentId = rs.getInt(1);
                String sampleUnit = rs.getString(2);
                int aliquotCount = rs.getInt(3);

                sampleAliquotCounts.put(parentId, new Pair<>(aliquotCount, sampleUnit));
            }
        }

        return sampleAliquotCounts;
    }

    private Map<Integer, List<AliquotAmountUnitResult>> getSampleAliquotAmounts(Collection<Integer> sampleIds, List<Integer> availableSampleStates, boolean useRootMaterialLSID) throws SQLException
    {
        DbSchema exp = getExpSchema();
        SqlDialect dialect = exp.getSqlDialect();

        // Issue 49150: In 23.12 we migrated from RootMaterialLSID to RootMaterialRowID, however, there is still an
        // upgrade path that requires these queries be done with RootMaterialLSID since the 23.12 upgrade will not
        // have run yet.
        SQLFragment sql = new SQLFragment("SELECT parent.rowid AS parentSampleId, aliquot.StoredAmount, aliquot.Units, aliquot.samplestate\n")
                .append("FROM exp.material AS aliquot JOIN exp.material AS parent ON ")
                .append(useRootMaterialLSID ? "parent.lsid = aliquot.rootmateriallsid" : "parent.rowid = aliquot.rootmaterialrowid")
                .append(" WHERE ")
                .append(useRootMaterialLSID ? "aliquot.rootmateriallsid <> aliquot.lsid" : "aliquot.rootmaterialrowid <> aliquot.rowid")
                .append(" AND parent.rowid\s");
        dialect.appendInClauseSql(sql, sampleIds);

        Map<Integer, List<AliquotAmountUnitResult>> sampleAliquotAmounts = new HashMap<>();

        try (ResultSet rs = new SqlSelector(exp, sql).getResultSet())
        {
            while (rs.next())
            {
                int parentId = rs.getInt(1);
                Double volume = rs.getDouble(2);
                String unit = rs.getString(3);
                int sampleState = rs.getInt(4);

                if (!sampleAliquotAmounts.containsKey(parentId))
                    sampleAliquotAmounts.put(parentId, new ArrayList<>());

                sampleAliquotAmounts.get(parentId).add(new AliquotAmountUnitResult(volume, unit, availableSampleStates.contains(sampleState)));
            }
        }
        // for any parents with no remaining aliquots, set the amounts to 0
        for (Integer parentId : sampleIds)
        {
            if (!sampleAliquotAmounts.containsKey(parentId))
            {
                List<AliquotAmountUnitResult> aliquotAmounts = new ArrayList<>();
                aliquotAmounts.add(new AliquotAmountUnitResult(0.0, null, false));
                sampleAliquotAmounts.put(parentId, aliquotAmounts);
            }
        }

        return sampleAliquotAmounts;
    }

    record FileFieldRenameData(ExpSampleType sampleType, String sampleName, String fieldName, File sourceFile, File targetFile) { }

    @Override
    public Map<String, Integer> moveSamples(Collection<? extends ExpMaterial> samples, @NotNull Container sourceContainer, @NotNull Container targetContainer, @NotNull User user, @Nullable String userComment, @Nullable AuditBehaviorType auditBehavior) throws ExperimentException, BatchValidationException
    {
        if (samples == null || samples.isEmpty())
            throw new IllegalArgumentException("No samples provided to move operation.");

        Map<ExpSampleType, List<ExpMaterial>> sampleTypesMap = new HashMap<>();
        samples.forEach(sample ->
            sampleTypesMap.computeIfAbsent(sample.getSampleType(), t -> new ArrayList<>()).add(sample));
        Map<String, Integer> updateCounts = new HashMap<>();
        updateCounts.put("samples", 0);
        updateCounts.put("sampleAliases", 0);
        updateCounts.put("sampleAuditEvents", 0);
        Map<Integer, List<FileFieldRenameData>> fileMovesBySampleId = new HashMap<>();
        ExperimentService expService = ExperimentService.get();

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            if (AuditBehaviorType.NONE != auditBehavior)
            {
                TransactionAuditProvider.TransactionAuditEvent auditEvent = AbstractQueryUpdateService.createTransactionAuditEvent(targetContainer, QueryService.AuditAction.UPDATE);
                auditEvent.updateCommentRowCount(samples.size());
                AbstractQueryUpdateService.addTransactionAuditEvent(transaction, user, auditEvent);
            }

            for (Map.Entry<ExpSampleType, List<ExpMaterial>> entry: sampleTypesMap.entrySet())
            {
                ExpSampleType sampleType = entry.getKey();
                SamplesSchema schema = new SamplesSchema(user, sampleType.getContainer());
                TableInfo samplesTable = schema.getTable(sampleType, null);

                List<ExpMaterial> typeSamples = entry.getValue();
                List<Integer> sampleIds = typeSamples.stream().map(ExpMaterial::getRowId).toList();

                // update for exp.material.container
                updateCounts.put("samples", updateCounts.get("samples") + ContainerManager.updateContainer(getTinfoMaterial(), "rowid", sampleIds, targetContainer, user, true));

                // update for exp.object.container
                expService.updateExpObjectContainers(getTinfoMaterial(), sampleIds, targetContainer);

                // update the paths to files associated with individual samples
                fileMovesBySampleId.putAll(updateSampleFilePaths(sampleType, typeSamples, targetContainer, user));

                // update for exp.materialaliasmap.container
                updateCounts.put("sampleAliases", updateCounts.get("sampleAliases") + expService.aliasMapRowContainerUpdate(getTinfoMaterialAliasMap(), sampleIds, targetContainer));

                // update inventory.item.container
                InventoryService inventoryService = InventoryService.get();
                if (inventoryService != null)
                {
                    Map<String, Integer> inventoryCounts = inventoryService.moveSamples(sampleIds, targetContainer, user);
                    inventoryCounts.forEach((key, count) -> {
                        updateCounts.compute(key, (k, c) -> c == null ? count : c + count);
                    });
                }

                // create summary audit entries for the source and target containers
                String samplesPhrase = StringUtilsLabKey.pluralize(sampleIds.size(), "sample");
                addSampleTypeAuditEvent(user, sourceContainer, sampleType, transaction.getAuditId(),
                        "Moved " + samplesPhrase + " to " + targetContainer.getPath(), userComment, "moved from project");
                addSampleTypeAuditEvent(user, targetContainer, sampleType, transaction.getAuditId(),
                        "Moved " + samplesPhrase  + " from " + sourceContainer.getPath(), userComment, "moved to project");

                // move the events associated with the samples that have moved
                SampleTimelineAuditProvider auditProvider = new SampleTimelineAuditProvider();
                int auditEventCount = auditProvider.moveEvents(targetContainer, sampleIds);
                updateCounts.compute("sampleAuditEvents", (k, c) -> c == null ? auditEventCount : c + auditEventCount );

                AuditBehaviorType stAuditBehavior = samplesTable.getAuditBehavior(auditBehavior);
                // create new events for each sample that was moved.
                if (stAuditBehavior == AuditBehaviorType.DETAILED)
                {
                    for (ExpMaterial sample : typeSamples)
                    {
                        SampleTimelineAuditEvent event = createAuditRecord(targetContainer, "Sample project was updated.", userComment, sample, null);
                        Map<String, Object> oldRecordMap = new HashMap<>();
                        oldRecordMap.put("Project", sourceContainer.getName());
                        Map<String, Object> newRecordMap = new HashMap<>();
                        newRecordMap.put("Project", targetContainer.getName());
                        if (fileMovesBySampleId.containsKey(sample.getRowId()))
                        {
                            fileMovesBySampleId.get(sample.getRowId()).forEach(fileUpdateData -> {
                               oldRecordMap.put(fileUpdateData.fieldName, fileUpdateData.sourceFile.getAbsolutePath());
                               newRecordMap.put(fileUpdateData.fieldName, fileUpdateData.targetFile.getAbsolutePath());
                            });
                        }
                        event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(targetContainer, oldRecordMap));
                        event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(targetContainer, newRecordMap));
                        AuditLogService.get().addEvent(user, event);
                    }
                }
            }

            updateCounts.putAll(moveDerivationRuns(samples, targetContainer, user));

            transaction.addCommitTask(() -> {
                for (ExpSampleType sampleType : sampleTypesMap.keySet())
                {
                    // force refresh of materialized view
                    SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(sampleType, SampleChangeType.update);
                    // update search index for moved samples via indexSampleType() helper, it filters for samples to index
                    // based on the modified date
                    SampleTypeServiceImpl.get().indexSampleType(sampleType);
                }
            }, DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);

            // add up the size of the value arrays in the fileMovesBySampleId map
            int fileMoveCount = fileMovesBySampleId.values().stream().mapToInt(List::size).sum();
            updateCounts.put("sampleFiles", fileMoveCount);
            transaction.addCommitTask(() -> {
                for (List<FileFieldRenameData> sampleFileRenameData : fileMovesBySampleId.values())
                {
                    for (FileFieldRenameData renameData : sampleFileRenameData)
                        moveFile(renameData);
                }
            }, POSTCOMMIT);

            transaction.commit();
        }

        return updateCounts;
    }

    private Map<String, Integer> moveDerivationRuns(Collection<? extends ExpMaterial> samples, Container targetContainer, User user) throws ExperimentException, BatchValidationException
    {
        // collect unique runIds mapped to the samples that are moving that have that runId
        Map<Integer, Set<ExpMaterial>> runIdSamples = new HashMap<>();
        samples.forEach(sample -> {
            if (sample.getRunId() != null)
                runIdSamples.computeIfAbsent(sample.getRunId(), t -> new HashSet<>()).add(sample);
        });
        ExperimentService expService = ExperimentService.get();
        // find the set of runs associated with samples that are moving
        List<? extends ExpRun> runs = expService.getExpRuns(runIdSamples.keySet());
        List<ExpRun> toUpdate = new ArrayList<>();
        List<ExpRun> toSplit = new ArrayList<>();
        for (ExpRun run : runs)
        {
            Set<Integer> outputIds = run.getMaterialOutputs().stream().map(ExpMaterial::getRowId).collect(Collectors.toSet());
            Set<Integer> movingIds = runIdSamples.get(run.getRowId()).stream().map(ExpMaterial::getRowId).collect(Collectors.toSet());
            if (movingIds.size() == outputIds.size() && movingIds.containsAll(outputIds))
                toUpdate.add(run);
            else
                toSplit.add(run);
        }

        int updateCount = expService.moveExperimentRuns(toUpdate, targetContainer, user);
        int splitCount = splitExperimentRuns(toSplit, runIdSamples, targetContainer, user);
        return Map.of("sampleDerivationRunsUpdated", updateCount, "sampleDerivationRunsSplit", splitCount);
    }

    private int splitExperimentRuns(List<ExpRun> runs, Map<Integer, Set<ExpMaterial>> movingSamples, Container targetContainer, User user) throws ExperimentException, BatchValidationException
    {
        final ViewBackgroundInfo targetInfo = new ViewBackgroundInfo(targetContainer, user, null);
        ExperimentServiceImpl expService = (ExperimentServiceImpl) ExperimentService.get();
        int runCount = 0;
        for (ExpRun run : runs)
        {
            ExpProtocolApplication sourceApplication = null;
            ExpProtocolApplication outputApp = run.getOutputProtocolApplication();
            boolean isAliquot = SAMPLE_ALIQUOT_PROTOCOL_LSID.equals(run.getProtocol().getLSID());

            Set<ExpMaterial> movingSet = movingSamples.get(run.getRowId());
            int numStaying = 0;
            Map<ExpMaterial, String> movingOutputsMap = new HashMap<>();
            ExpMaterial aliquotParent = null;
            // the derived samples (outputs of the run) are inputs to the output step of the run (obviously)
            for (ExpMaterialRunInput materialInput : outputApp.getMaterialInputs())
            {
                ExpMaterial material = materialInput.getMaterial();
                if (movingSet.contains(material))
                {
                    // clear out the run and source application so a new derivation run can be created.
                    material.setRun(null);
                    material.setSourceApplication(null);
                    movingOutputsMap.put(material, materialInput.getRole());
                }
                else
                {
                    if (sourceApplication == null)
                        sourceApplication = material.getSourceApplication();
                    numStaying++;
                }
                if (isAliquot && aliquotParent == null && material.getAliquotedFromLSID() != null)
                {
                    aliquotParent = expService.getExpMaterial(material.getAliquotedFromLSID());
                }
            }

            try
            {
                if (isAliquot && aliquotParent != null)
                {
                    ExpRunImpl expRun = expService.createAliquotRun(aliquotParent, movingOutputsMap.keySet(), targetInfo);
                    expService.saveSimpleExperimentRun(expRun, run.getMaterialInputs(), run.getDataInputs(), movingOutputsMap, Collections.emptyMap(), Collections.emptyMap(), targetInfo, LOG, false);
                    // Update the run for the samples that have stayed behind. Change the name and remove the moved samples as outputs
                    run.setName(ExperimentServiceImpl.getAliquotRunName(aliquotParent, numStaying));
                }
                else
                {
                    // create a new derivation run for the samples that are moving
                    expService.derive(run.getMaterialInputs(), run.getDataInputs(), movingOutputsMap, Collections.emptyMap(), targetInfo, LOG);
                    // Update the run for the samples that have stayed behind. Change the name and remove the moved samples as outputs
                    run.setName(ExperimentServiceImpl.getDerivationRunName(run.getMaterialInputs(), run.getDataInputs(), numStaying, run.getDataOutputs().size()));
                }
            }
            catch (ValidationException e)
            {
                BatchValidationException errors = new BatchValidationException();
                errors.addRowError(e);
                throw errors;
            }
            run.save(user);
            List<Integer> movingSampleIds = movingSet.stream().map(ExpMaterial::getRowId).toList();

            outputApp.removeMaterialInputs(user, movingSampleIds);
            if (sourceApplication != null)
                sourceApplication.removeMaterialInputs(user, movingSampleIds);

            runCount++;
        }
        return runCount;
    }

    // return the map of file renames
    private Map<Integer, List<FileFieldRenameData>> updateSampleFilePaths(ExpSampleType sampleType, List<ExpMaterial> samples, Container targetContainer, User user)
    {
        Map<Integer, List<FileFieldRenameData>> sampleFileRenames = new HashMap<>();

        FileContentService fileService = FileContentService.get();
        if (fileService == null)
        {
            LOG.warn("No file service available. Sample files cannot be moved.");
            return sampleFileRenames;
        }

        if (fileService.getFileRoot(targetContainer) == null)
        {
            LOG.warn("No file root found for target container " + targetContainer + "'. Files cannot be moved.");
            return sampleFileRenames;
        }

        List<? extends DomainProperty> fileDomainProps = sampleType.getDomain()
                .getProperties().stream()
                .filter(prop -> PropertyType.FILE_LINK.getTypeUri().equals(prop.getRangeURI())).toList();
        if (fileDomainProps.isEmpty())
            return sampleFileRenames;

        Map<Container, Boolean> hasFileRoot = new HashMap<>();
        for (ExpMaterial sample : samples)
        {
            boolean hasSourceRoot = hasFileRoot.computeIfAbsent(sample.getContainer(), (container) -> fileService.getFileRoot(container) != null);
            if (!hasSourceRoot)
                LOG.warn("No file root found for source container " + sample.getContainer() + ". Files cannot be moved.");
            else
                for (DomainProperty fileProp : fileDomainProps )
                {
                    String sourceFileName = (String) sample.getProperty(fileProp);
                    File updatedFile = FileContentService.get().getMoveTargetFile(sourceFileName, sample.getContainer(), targetContainer);
                    if (updatedFile != null)
                    {
                        FileFieldRenameData renameData = new FileFieldRenameData(sampleType, sample.getName(), fileProp.getName(), new File(sourceFileName), updatedFile);
                        sampleFileRenames.putIfAbsent(sample.getRowId(), new ArrayList<>());
                        List<FileFieldRenameData> fieldRenameData = sampleFileRenames.get(sample.getRowId());
                        fieldRenameData.add(renameData);
                    }
                }
        }

        // TODO, support batch fireFileMoveEvent to avoid excessive FileLinkFileListener.hardTableFileLinkColumns calls
        for (int sampleId: sampleFileRenames.keySet())
        {
            List<FileFieldRenameData> fieldRenameRecords = sampleFileRenames.get(sampleId);
            for (FileFieldRenameData renameData : fieldRenameRecords)
                fileService.fireFileMoveEvent(renameData.sourceFile, renameData.targetFile, user, targetContainer);
        }

        return sampleFileRenames;
    }

    private boolean moveFile(FileFieldRenameData renameData)
    {
        if (!renameData.targetFile.getParentFile().exists())
        {
            String errorMsg = String.format("Creation of target directory '%s' to move file '%s' to, for '%s' sample '%s' (field: '%s') failed.",
                    renameData.targetFile.getParent(),
                    renameData.sourceFile.getAbsolutePath(),
                    renameData.sampleType.getName(),
                    renameData.sampleName,
                    renameData.fieldName);
            try
            {
                if (!FileUtil.mkdirs(renameData.targetFile.getParentFile()))
                {
                    LOG.warn(errorMsg);
                    return false;
                }
            }
            catch (IOException e)
            {
                LOG.warn(errorMsg + e.getMessage());
            }
        }
        if (!renameData.sourceFile.renameTo(renameData.targetFile))
        {
            LOG.warn(String.format("Rename of '%s' to '%s' for '%s' sample '%s' (field: '%s') failed.",
                    renameData.sourceFile.getAbsolutePath(),
                    renameData.targetFile.getAbsolutePath(),
                    renameData.sampleType.getName(),
                    renameData.sampleName,
                    renameData.fieldName));
            return false;
        }

        return true;
    }

    @Override
    @Nullable
    public DbSequence getSampleCountSequence(Container container, boolean isRootSampleOnly)
    {
        return getSampleCountSequence(container, isRootSampleOnly, true);
    }

    public DbSequence getSampleCountSequence(Container container, boolean isRootSampleOnly, boolean create)
    {
        Container seqContainer = container.getProject();
        if (seqContainer == null)
            return null;

       String seqName = isRootSampleOnly ? ROOT_SAMPLE_COUNT_SEQ_NAME : SAMPLE_COUNT_SEQ_NAME;

       if (!create)
       {
           // check if sequence already exist so we don't create one just for querying
           Integer seqRowId = DbSequenceManager.getRowId(seqContainer, seqName, 0);
           if (null == seqRowId)
               return null;
       }

       if (ExperimentService.get().useStrictCounter())
            return DbSequenceManager.getReclaimable(seqContainer, seqName, 0);

       return DbSequenceManager.getPreallocatingSequence(seqContainer, seqName, 0, 100);
    }

    @Override
    public void ensureMinSampleCount(long newSeqValue, NameGenerator.EntityCounter counterType, Container container) throws ExperimentException
    {
        boolean isRootOnly = counterType == NameGenerator.EntityCounter.rootSampleCount;

        DbSequence seq = getSampleCountSequence(container, isRootOnly, newSeqValue >= 1);
        if (seq == null)
            return;

        long current = seq.current();
        if (newSeqValue < current)
        {
            if ((isRootOnly ? getProjectRootSampleCount(container) : getProjectSampleCount(container)) > 0)
                throw new ExperimentException("Unable to set " + counterType.name() + " to " + newSeqValue + " due to conflict with existing samples.");

            if (newSeqValue <= 0)
            {
                deleteSampleCounterSequence(container, isRootOnly);
                return;
            }
        }

        seq.ensureMinimum(newSeqValue);
        seq.sync();
    }

    public void deleteSampleCounterSequences(Container container)
    {
        deleteSampleCounterSequence(container, false);
        deleteSampleCounterSequence(container, true);
    }

    private void deleteSampleCounterSequence(Container container, boolean isRootOnly)
    {
        String seqName = isRootOnly ? ROOT_SAMPLE_COUNT_SEQ_NAME : SAMPLE_COUNT_SEQ_NAME;
        Container seqContainer = container.getProject();
        DbSequenceManager.delete(seqContainer, seqName);
        DbSequenceManager.invalidatePreallocatingSequence(container, seqName, 0);
        return;
    }

    @Override
    public long getProjectSampleCount(Container container)
    {
        return getProjectSampleCount(container, false);
    }

    @Override
    public long getProjectRootSampleCount(Container container)
    {
        return getProjectSampleCount(container, true);
    }

    private long getProjectSampleCount(Container container, boolean isRootOnly)
    {
        User searchUser = User.getSearchUser();
        ContainerFilter.ContainerFilterWithPermission cf = new ContainerFilter.AllInProject(container, searchUser);
        Collection<GUID> validContainerIds =  cf.generateIds(container, ReadPermission.class, null);
        TableInfo tableInfo = ExperimentService.get().getTinfoMaterial();
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ");
        sql.append(tableInfo);
        sql.append(" WHERE ");
        if (isRootOnly)
            sql.append(" AliquotedFromLsid IS NULL AND ");
        sql.append("Container ");
        sql.appendInClause(validContainerIds, tableInfo.getSqlDialect());
        return new SqlSelector(ExperimentService.get().getSchema(), sql).getObject(Long.class).longValue();
    }

    @Override
    public long getCurrentCount(NameGenerator.EntityCounter counterType, Container container)
    {
        boolean isRootOnly = counterType == NameGenerator.EntityCounter.rootSampleCount;
        DbSequence seq = getSampleCountSequence(container, isRootOnly, false);
        if (seq != null)
        {
            long current = seq.current();
            if (current > 0)
                return current;
        }

        return getProjectSampleCount(container, counterType == NameGenerator.EntityCounter.rootSampleCount);
    }

    public enum SampleChangeType { insert, update, delete, rollup /* aliquot count */, schema }

    public void refreshSampleTypeMaterializedView(@NotNull ExpSampleType st, SampleChangeType reason)
    {
        ExpMaterialTableImpl.refreshMaterializedView(st.getLSID(), reason);
    }
}
