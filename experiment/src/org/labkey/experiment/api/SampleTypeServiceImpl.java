/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
import org.apache.commons.lang3.Strings;
import org.apache.commons.math3.util.Precision;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.audit.AbstractAuditHandler;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.LongArrayList;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.collections.LongHashSet;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConversionExceptionWithMessage;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
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
import org.labkey.api.ontology.KindOfQuantity;
import org.labkey.api.ontology.Quantity;
import org.labkey.api.ontology.Unit;
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
import org.labkey.api.query.UserSchema;
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
import java.sql.Timestamp;
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
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
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
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RawAmount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RawUnits;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.StoredAmount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Units;


public class SampleTypeServiceImpl extends AbstractAuditHandler implements SampleTypeService, DataColorManager.DataColorHandler
{
    public static final String SAMPLE_COUNT_SEQ_NAME = "org.labkey.api.exp.api.ExpMaterial:sampleCount";
    public static final String ROOT_SAMPLE_COUNT_SEQ_NAME = "org.labkey.api.exp.api.ExpMaterial:rootSampleCount";

    public static final List<Unit> SUPPORTED_UNITS = new ArrayList<>();
    public static final String CONVERSION_EXCEPTION_MESSAGE ="Units value (%s) is not compatible with the %s display units (%s).";

    static
    {
        SUPPORTED_UNITS.addAll(KindOfQuantity.Volume.getCommonUnits());
        SUPPORTED_UNITS.addAll(KindOfQuantity.Mass.getCommonUnits());
        SUPPORTED_UNITS.addAll(KindOfQuantity.Count.getCommonUnits());
    }

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

    @Override
    public String getHandlerType()
    {
        return "SampleColorMaterial";
    }

    @Override
    public boolean isColorInUse(Container container, long colorRowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ExpMaterialColor"), colorRowId);
        return new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), filter, null).exists();
    }

    private static final Logger LOG = LogHelper.getLogger(SampleTypeServiceImpl.class, "Info about sample type operations");

    /** SampleType LSID -> Container cache */
    private final Cache<String, String> sampleTypeCache = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "SampleType to container");

    /** ContainerId -> MaterialSources */
    private final Cache<String, SortedSet<MaterialSource>> materialSourceCache = DatabaseCache.get(ExperimentServiceImpl.get().getSchema().getScope(), CacheManager.UNLIMITED, CacheManager.DAY, "Material sources", (container, argument) ->
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

    @Override @NotNull
    public List<Unit> getSupportedUnits()
    {
        return SUPPORTED_UNITS;
    }

    @Nullable @Override
    public Unit getValidatedUnit(@Nullable Object rawUnits, @Nullable Unit defaultUnits, String sampleTypeName)
    {
        if (rawUnits == null)
            return null;
        if (rawUnits instanceof Unit u)
        {
            if (defaultUnits == null)
                return u;
            else if (u.getKindOfQuantity() != defaultUnits.getKindOfQuantity())
                throw new ConversionExceptionWithMessage(String.format(CONVERSION_EXCEPTION_MESSAGE, rawUnits, sampleTypeName == null ? "" : sampleTypeName, defaultUnits));
            else
                return u;
        }
        if (!(rawUnits instanceof String rawUnitsString))
            throw new ConversionExceptionWithMessage(String.format(CONVERSION_EXCEPTION_MESSAGE, rawUnits, sampleTypeName == null ? "" : sampleTypeName, defaultUnits));
        if (!StringUtils.isBlank(rawUnitsString))
        {
            rawUnitsString = rawUnitsString.trim();

            Unit mUnit = Unit.fromName(rawUnitsString);
            List<Unit> commonUnits = getSupportedUnits();
            if (mUnit == null || !commonUnits.contains(mUnit))
            {
                if (defaultUnits != null)
                    commonUnits = commonUnits.stream().filter(u -> u.getKindOfQuantity() == defaultUnits.getKindOfQuantity()).collect(Collectors.toList());
                throw new ConversionExceptionWithMessage("Unsupported Units value (" + rawUnitsString + "). Supported values are: " + StringUtils.join(commonUnits, ", ") + ".");
            }
            if (defaultUnits != null && mUnit.getKindOfQuantity() != defaultUnits.getKindOfQuantity())
                throw new ConversionExceptionWithMessage(String.format(CONVERSION_EXCEPTION_MESSAGE, rawUnits, sampleTypeName == null ? "" : sampleTypeName, defaultUnits));
            return mUnit;
        }
        return null;
    }

    public void clearMaterialSourceCache(@Nullable Container c)
    {
        LOG.debug("clearMaterialSourceCache: {}", c == null ? "all" : c.getPath());
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
        return ExperimentServiceImpl.getExpSchema();
    }

    @Override
    public void indexSampleType(ExpSampleType sampleType, SearchService.TaskIndexingQueue queue)
    {
        if (sampleType == null)
            return;

        queue.addRunnable((q) -> {
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
                impl.index(q, null);
            }

            indexSampleTypeMaterials(sampleType, q, 0);
        });
    }

    private void indexSampleTypeMaterials(ExpSampleType sampleType, SearchService.TaskIndexingQueue queue, long minRowId)
    {
        // Index all ExpMaterial that have never been indexed OR where either the ExpSampleType definition or ExpMaterial itself has changed since last indexed
        SQLFragment sql = new SQLFragment("SELECT m.* FROM ")
                .append(getTinfoMaterial(), "m")
                .append(" LEFT OUTER JOIN ")
                .append(ExperimentServiceImpl.get().getTinfoMaterialIndexed(), "mi")
                .append(" ON m.RowId = mi.MaterialId WHERE m.LSID NOT LIKE ").appendValue("%:" + StudyService.SPECIMEN_NAMESPACE_PREFIX + "%", getExpSchema().getSqlDialect())
                .append(" AND m.cpasType = ?").add(sampleType.getLSID())
                .append(" AND m.RowId > ?").add(minRowId)
                .append(" AND (mi.lastIndexed IS NULL OR mi.lastIndexed < ? OR (m.modified IS NOT NULL AND mi.lastIndexed < m.modified))")
                .append(" ORDER BY m.RowId") // Issue 51263: order by RowId to reduce deadlock
                .add(sampleType.getModified());
        sql = getExpSchema().getSqlDialect().limitRows(sql, SearchService.INDEXING_LIMIT);
        SqlSelector selector = new SqlSelector(getExpSchema().getScope(), sql);
        selector.setJdbcCaching(false);
        SearchService.IndexBatchCursor tracker = new SearchService.IndexBatchCursor(minRowId);

        // Work in modest block sizes and fetch as a list so we don't keep the ResultSet open, which could lock the tables
        tracker.forEach(selector.getArrayList(Material.class), Material::getRowId, m -> {
            ExpMaterialImpl impl = new ExpMaterialImpl(m);
            impl.index(queue, null /* null tableInfo since samples may belong to multiple containers*/);
        });

        if (tracker.wasFull())
            // Requeue for the next batch. This avoids overwhelming the indexer's queue with documents
            queue.addRunnable((q) -> indexSampleTypeMaterials(sampleType, q, tracker.getMaxRowId()));
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
    public void removeAutoLinkedStudy(@NotNull Container studyContainer)
    {
        SQLFragment sql = new SQLFragment("UPDATE ").append(getTinfoMaterialSource())
                .append(" SET autolinkTargetContainer = NULL WHERE autolinkTargetContainer = ?")
                .add(studyContainer.getId());
        new SqlExecutor(ExperimentService.get().getSchema()).execute(sql);
    }

    public ExpSampleTypeImpl getSampleTypeByObjectId(Long objectId)
    {
        OntologyObject obj = OntologyManager.getOntologyObject(objectId);
        if (obj == null)
            return null;

        return getSampleType(obj.getObjectURI());
    }

    @Override
    public @Nullable ExpSampleType getEffectiveSampleType(
        @NotNull Container definitionContainer,
        @NotNull String sampleTypeName,
        @NotNull Date effectiveDate,
        @Nullable ContainerFilter cf
    )
    {
        Long legacyObjectId = ExperimentService.get().getObjectIdWithLegacyName(sampleTypeName, ExperimentServiceImpl.getNamespacePrefix(ExpSampleType.class), effectiveDate, definitionContainer, cf);
        if (legacyObjectId != null)
            return getSampleTypeByObjectId(legacyObjectId);

        boolean includeOtherContainers = cf != null && cf.getType() != ContainerFilter.Type.Current;
        ExpSampleTypeImpl sampleType = getSampleType(definitionContainer, sampleTypeName, includeOtherContainers);
        if (sampleType != null && sampleType.getCreated().compareTo(effectiveDate) <= 0)
            return sampleType;

        return null;
    }

    @Override
    public List<ExpSampleTypeImpl> getSampleTypes(@NotNull Container container, boolean includeOtherContainers)
    {
        List<String> containerIds = ExperimentServiceImpl.get().createContainerList(container, includeOtherContainers);

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
        return getSampleType(c, sampleTypeName, false);
    }

    @Override
    public ExpSampleTypeImpl getSampleType(@NotNull Container c, @NotNull String sampleTypeName, boolean includeOtherContainers)
    {
        return getSampleType(c, includeOtherContainers, (materialSource -> materialSource.getName().equalsIgnoreCase(sampleTypeName)));
    }

    @Override
    public ExpSampleTypeImpl getSampleType(@NotNull Container c, long rowId)
    {
        return getSampleType(c, rowId, false);
    }

    @Override
    public ExpSampleTypeImpl getSampleType(@NotNull Container c, long rowId, boolean includeOtherContainers)
    {
        return getSampleType(c, includeOtherContainers, (materialSource -> materialSource.getRowId() == rowId));
    }

    private ExpSampleTypeImpl getSampleType(@NotNull Container c, boolean includeOtherContainers, Predicate<MaterialSource> predicate)
    {
        List<String> containerIds = ExperimentServiceImpl.get().createContainerList(c, includeOtherContainers);
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
    public ExpSampleTypeImpl getSampleType(long rowId)
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

    @Override
    public ExpSampleTypeImpl getSampleTypeByType(@NotNull String lsid, Container hint)
    {
        Container c = hint;
        String id = sampleTypeCache.get(lsid);
        if (null != id && (null == hint || !id.equals(hint.getId())))
            c = ContainerManager.getForId(id);
        ExpSampleTypeImpl st = null;
        if (null != c)
            st = getSampleType(c, false, ms -> lsid.equals(ms.getLSID()) );
        if (null == st)
            st = _getSampleType(lsid);
        if (null != st && null==id)
            sampleTypeCache.put(lsid,st.getContainer().getId());
        return st;
    }

    @Nullable
    @Override
    public DataState getSampleState(Container container, Long stateRowId)
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
    public void deleteSampleType(long rowId, Container c, User user, @Nullable String auditUserComment) throws ExperimentException
    {
        CPUTimer timer = new CPUTimer("delete sample type");
        timer.start();

        ExpSampleTypeImpl source = getSampleType(c, rowId, true);
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
                    dataset.delete(user, auditUserComment);
                }
            }
            else
            {
                LOG.warn("Could not delete datasets associated with this protocol: Study service not available.");
            }

            Domain d = source.getDomain();
            d.delete(user, auditUserComment);

            ExperimentServiceImpl.get().deleteDomainObjects(source.getContainer(), source.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("UPDATE " + getTinfoDataClass() + " SET materialSourceId = NULL WHERE materialSourceId = ?", source.getRowId());
            executor.execute("UPDATE " + getTinfoProtocolInput() + " SET materialSourceId = NULL WHERE materialSourceId = ?", source.getRowId());
            executor.execute("DELETE FROM " + getTinfoMaterialSource() + " WHERE RowId = ?", rowId);

            addSampleTypeDeletedAuditEvent(user, c, source, auditUserComment);

            ExperimentService.get().removeDataTypeExclusion(Collections.singleton(rowId), ExperimentService.DataTypeForExclusion.SampleType);
            ExperimentService.get().removeDataTypeExclusion(Collections.singleton(rowId), ExperimentService.DataTypeForExclusion.DashboardSampleType);
            ExperimentService.get().removeDataColorExclusionsForDataType(rowId, ExperimentService.DataTypeForExclusion.SampleType);

            transaction.addCommitTask(() -> clearMaterialSourceCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
            transaction.commit();
        }

        // Delete sequences (genId and the unique counters)
        DbSequenceManager.deleteLike(c, ExpSampleType.SEQUENCE_PREFIX, (int)source.getRowId(), getExpSchema().getSqlDialect());

        QueryService.get().fireQueryDeleted(user, c, null, SamplesSchema.SCHEMA_SAMPLES, singleton(source.getName()));
        QueryService.get().fireQueryDeleted(user, c, null, ExpSchema.SCHEMA_EXP_MATERIALS, singleton(source.getName()));

        // Remove SampleType from search index
        try (Timing ignored = MiniProfiler.step("search docs"))
        {
            SearchService.get().deleteResource(source.getDocumentId());
        }

        timer.stop();
        LOG.info("Deleted SampleType '{}' from '{}' in {}", source.getName(), c.getPath(), timer.getDuration());
    }

    private void addSampleTypeDeletedAuditEvent(User user, Container c, ExpSampleType sampleType, @Nullable String auditUserComment)
    {
        addSampleTypeAuditEvent(user, c, sampleType, String.format("Sample Type deleted: %1$s", sampleType.getName()),auditUserComment, "delete type");
    }

    private void addSampleTypeAuditEvent(User user, Container c, ExpSampleType sampleType, String comment, @Nullable String auditUserComment, String insertUpdateChoice)
    {
        SampleTypeAuditProvider.SampleTypeAuditEvent event = new SampleTypeAuditProvider.SampleTypeAuditEvent(c, comment);
        event.setUserComment(auditUserComment);

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
        return createSampleType(c, u, name, description, properties, indices, idCol1, idCol2, idCol3, parentCol, nameExpression, aliquotNameExpression, templateInfo, importAliases, labelColor, metricUnit, null, null, null, null, null, null, null);
    }

    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                              String nameExpression, String aliquotNameExpression, @Nullable TemplateInfo templateInfo, @Nullable Map<String, Map<String, Object>> importAliases, @Nullable String labelColor, @Nullable String metricUnit,
                                              @Nullable Container autoLinkTargetContainer, @Nullable String autoLinkCategory, @Nullable String category, @Nullable List<String> disabledSystemField,
                                              @Nullable List<String> excludedContainerIds, @Nullable List<String> excludedDashboardContainerIds, @Nullable Map<String, Object> changeDetails)
        throws ExperimentException
    {
        validateSampleTypeName(c, u, name, false);

        if (properties == null || properties.isEmpty())
            throw new ApiUsageException("At least one property is required");

        if (idCol2 != -1 && idCol1 == idCol2)
            throw new ApiUsageException("You cannot use the same id column twice.");

        if (idCol3 != -1 && (idCol1 == idCol3 || idCol2 == idCol3))
            throw new ApiUsageException("You cannot use the same id column twice.");

        if ((idCol1 > -1 && idCol1 >= properties.size()) ||
            (idCol2 > -1 && idCol2 >= properties.size()) ||
            (idCol3 > -1 && idCol3 >= properties.size()) ||
            (parentCol > -1 && parentCol >= properties.size()))
            throw new ApiUsageException("column index out of range");

        // Name expression is only allowed when no idCol is set
        if (nameExpression != null && idCol1 > -1)
            throw new ApiUsageException("Name expression cannot be used with id columns");

        NameExpressionOptionService svc = NameExpressionOptionService.get();
        if (!svc.allowUserSpecifiedNames(c))
        {
            if (nameExpression == null)
                throw new ApiUsageException(c.hasProductFolders() ? NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS : NAME_EXPRESSION_REQUIRED_MSG);
        }

        if (svc.getExpressionPrefix(c) != null)
        {
            // automatically apply the configured prefix to the name expression
            nameExpression = svc.createPrefixedExpression(c, nameExpression, false);
            aliquotNameExpression = svc.createPrefixedExpression(c, aliquotNameExpression, true);
        }

        // Validate the name expression length
        TableInfo materialSourceTable = ExperimentService.get().getTinfoSampleType();
        int nameExpMax = materialSourceTable.getColumn("NameExpression").getScale();
        if (nameExpression != null && nameExpression.length() > nameExpMax)
            throw new ApiUsageException("Name expression may not exceed " + nameExpMax + " characters.");

        // Validate the aliquot name expression length
        int aliquotNameExpMax = materialSourceTable.getColumn("AliquotNameExpression").getScale();
        if (aliquotNameExpression != null && aliquotNameExpression.length() > aliquotNameExpMax)
            throw new ApiUsageException("Aliquot naming patten may not exceed " + aliquotNameExpMax + " characters.");

        // Validate the label color length
        int labelColorMax = materialSourceTable.getColumn("LabelColor").getScale();
        if (labelColor != null && labelColor.length() > labelColorMax)
            throw new ApiUsageException("Label color may not exceed " + labelColorMax + " characters.");

        // Validate the metricUnit length
        int metricUnitMax = materialSourceTable.getColumn("MetricUnit").getScale();
        if (metricUnit != null && metricUnit.length() > metricUnitMax)
            throw new ApiUsageException("Metric unit may not exceed " + metricUnitMax + " characters.");

        // Validate the category length
        int categoryMax = materialSourceTable.getColumn("Category").getScale();
        if (category != null && category.length() > categoryMax)
            throw new ApiUsageException("Category may not exceed " + categoryMax + " characters.");

        Pair<String, String> dbSeqLsids = getSampleTypeSamplePrefixLsids(c);
        String lsid = dbSeqLsids.first;
        String materialPrefixLsid = dbSeqLsids.second;
        Domain domain = PropertyService.get().createDomain(c, lsid, name, templateInfo);
        DomainKind<?> kind = domain.getDomainKind();
        if (kind != null)
        {
            domain.setDisabledSystemFields(kind.getDisabledSystemFields(disabledSystemField));
            domain.setPropertyForeignKeys(kind.getPropertyForeignKeys(c)); // GitHub Issue 1117
        }

        Set<String> reservedNames = kind.getReservedPropertyNames(domain, u);
        Set<String> reservedPrefixes = kind.getReservedPropertyNamePrefixes();
        Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());

        boolean hasNameProperty = false;
        String idUri1 = null, idUri2 = null, idUri3 = null, parentUri = null;
        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        Set<String> propertyUris = new CaseInsensitiveHashSet();
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
            throw new ApiUsageException("Either a 'Name' property or an index for idCol1 is required");

        if (hasNameProperty && idUri1 != null)
            throw new ApiUsageException("Either a 'Name' property or idCols can be used, but not both");

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
                    domain.save(u, changeDetails, calculatedFields);
                    st.save(u);
                    QueryService.get().saveCalculatedFieldsMetadata(SamplesSchema.SCHEMA_NAME, name, null, calculatedFields, false, u, c);
                    DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);
                    if (excludedContainerIds != null && !excludedContainerIds.isEmpty())
                        ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.SampleType, excludedContainerIds, st.getRowId(), u);
                    else
                        ExperimentService.get().ensureDataTypeContainerExclusionsNonAdmin(ExperimentService.DataTypeForExclusion.SampleType, st.getRowId(), c, u);
                    if (excludedDashboardContainerIds != null && !excludedDashboardContainerIds.isEmpty())
                        ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.DashboardSampleType, excludedDashboardContainerIds, st.getRowId(), u);
                    else
                        ExperimentService.get().ensureDataTypeContainerExclusionsNonAdmin(ExperimentService.DataTypeForExclusion.DashboardSampleType, st.getRowId(), c, u);
                    transaction.addCommitTask(() -> clearMaterialSourceCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
                    transaction.addCommitTask(() -> indexSampleType(SampleTypeService.get().getSampleType(domain.getTypeURI()), SearchService.get().defaultTask().getQueue(c, SearchService.PRIORITY.modified)), POSTCOMMIT);

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
            return DbSequenceManager.getPreallocatingSequence(ContainerManager.getRoot(), seqName.first, seqName.second, 100);
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
    public void validateSampleTypeName(Container container, User user, String name, boolean skipExistingCheck)
    {
        if (name == null || StringUtils.isBlank(name))
            throw new ApiUsageException("Sample Type name is required.");

        TableInfo materialSourceTable = ExperimentService.get().getTinfoSampleType();
        int nameMax = materialSourceTable.getColumn("Name").getScale();
        if (name.length() > nameMax)
            throw new ApiUsageException("Sample Type name may not exceed " + nameMax + " characters.");

        if (!skipExistingCheck)
        {
            if (getSampleType(container, name, true) != null)
                throw new ApiUsageException("A Sample Type with name '" + name + "' already exists.");
        }

        String reservedError = DomainUtil.validateReservedName(name, "Sample Type");
        if (reservedError != null)
            throw new ApiUsageException(reservedError);
    }

    private boolean hasIncompatibleUnits(ExpSampleTypeImpl st, String newUnitStr)
    {
        if (StringUtils.isEmpty(newUnitStr) || newUnitStr.equalsIgnoreCase(st.getMetricUnit()))
            return false;

        boolean hasToValidateUnit = true;
        Unit newUnit = Unit.fromName(newUnitStr);
        if (!StringUtils.isEmpty(st.getMetricUnit()))
        {
            Unit oldUnit = Unit.fromName(st.getMetricUnit());
            if (oldUnit != null && newUnit != null)
                hasToValidateUnit = !oldUnit.getBase().equals(newUnit.getBase());
        }

        if (hasToValidateUnit)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("CpasType"), st.getLSID());
            filter.addCondition(FieldKey.fromParts("StoredAmount"), null, CompareType.NONBLANK);
            if (newUnit != null && newUnit.getBase() == Unit.unit.getBase())
            {
                List<String> compatibleUnits = KindOfQuantity.Count.getCommonUnits().stream().map(Unit::name).collect(Collectors.toList());
                filter.addCondition(FieldKey.fromParts("Units"), compatibleUnits, CompareType.NOT_IN);
            }
            else if (newUnit != null)
                filter.addCondition(FieldKey.fromParts("Units"), newUnit.getBase().name(), CompareType.NEQ);
            else
                filter.addCondition(FieldKey.fromParts("Units"), newUnitStr, CompareType.NEQ);

            TableSelector ts = new TableSelector(getTinfoMaterial(), filter, null);
            return ts.exists();
        }

        return false;
    }

    @Override
    public ValidationException updateSampleType(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings, @Nullable String auditUserComment)
    {
        ValidationException errors;

        ExpSampleTypeImpl st = new ExpSampleTypeImpl(getMaterialSource(update.getDomainURI()));

        StringBuilder changeDetails = new StringBuilder();

        Map<String, Object> oldProps = new LinkedHashMap<>();
        Map<String, Object> newProps = new LinkedHashMap<>();

        String newName = StringUtils.trimToNull(update.getName());
        String oldSampleTypeName = st.getName();
        oldProps.put("Name", oldSampleTypeName);
        newProps.put("Name", newName);

        boolean hasNameChange = false;
        if (!oldSampleTypeName.equals(newName))
        {
            validateSampleTypeName(container, user, newName, oldSampleTypeName.equalsIgnoreCase(newName));
            hasNameChange = true;
            st.setName(newName);
            changeDetails.append("The name of the sample type '").append(oldSampleTypeName).append("' was changed to '").append(newName).append("'.");
        }

        String newDescription = StringUtils.trimToNull(update.getDescription());
        String description = st.getDescription();
        if (StringUtils.isNotBlank(description))
            oldProps.put("Description", description);
        if (StringUtils.isNotBlank(newDescription))
            newProps.put("Description", newDescription);
        if (description == null || !description.equals(newDescription))
            st.setDescription(newDescription);

        Map<String, Object> oldProps_ = st.getAuditRecordMap();
        Map<String, Object> newProps_ = options != null ? options.getAuditRecordMap() : st.getAuditRecordMap() /* no update */;
        newProps.putAll(newProps_);
        oldProps.putAll(oldProps_);

        if (options != null)
        {
            String sampleIdPattern = StringUtils.trimToNull(StringUtilsLabKey.replaceBadCharacters(options.getNameExpression()));
            String oldPattern = st.getNameExpression();
            if (oldPattern == null || !oldPattern.equals(sampleIdPattern))
            {
                st.setNameExpression(sampleIdPattern);
                if (!NameExpressionOptionService.get().allowUserSpecifiedNames(container) && sampleIdPattern == null)
                    throw new ApiUsageException(container.hasProductFolders() ? NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS : NAME_EXPRESSION_REQUIRED_MSG);
            }

            String aliquotIdPattern = StringUtils.trimToNull(options.getAliquotNameExpression());
            String oldAliquotPattern = st.getAliquotNameExpression();
            if (oldAliquotPattern == null || !oldAliquotPattern.equals(aliquotIdPattern))
                st.setAliquotNameExpression(aliquotIdPattern);

            st.setLabelColor(options.getLabelColor());

            if (hasIncompatibleUnits(st, options.getMetricUnit()))
                throw new ApiUsageException("Unable to update 'Display Units' to '" + options.getMetricUnit() + "'. There are existing samples with incompatible units.");

            st.setMetricUnit(options.getMetricUnit());

            if (options.getImportAliases() != null && !options.getImportAliases().isEmpty())
            {
                try
                {
                    Map<String, Map<String, Object>> newAliases = options.getImportAliases();
                    Set<String> existingRequiredInputs = new HashSet<>(st.getRequiredImportAliases().values());
                    String invalidParentType = ExperimentServiceImpl.get().getInvalidRequiredImportAliasUpdate(st.getLSID(), true, newAliases, existingRequiredInputs, container, user);
                    if (invalidParentType != null)
                        throw new ApiUsageException("'" + invalidParentType + "' cannot be required as a parent type when there are existing samples without a parent of this type.");

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
            st.save(user);
            if (hasNameChange)
                QueryChangeListener.QueryPropertyChange.handleQueryNameChange(oldSampleTypeName, newName, new SchemaKey(null, SamplesSchema.SCHEMA_NAME), user, container);

            if (options != null && options.getExcludedContainerIds() != null)
            {
                Pair<Collection<String>, Collection<String>> exclusionChanges = ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.SampleType, options.getExcludedContainerIds(), st.getRowId(), user);
                oldProps.put("ContainerExclusions", exclusionChanges.first);
                newProps.put("ContainerExclusions", exclusionChanges.second);
            }
            if (options != null && options.getExcludedDashboardContainerIds() != null)
            {
                Pair<Collection<String>, Collection<String>> exclusionChanges = ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.DashboardSampleType, options.getExcludedDashboardContainerIds(), st.getRowId(), user);
                oldProps.put("DashboardContainerExclusions", exclusionChanges.first);
                newProps.put("DashboardContainerExclusions", exclusionChanges.second);
            }
            if (options != null && options.getDisabledSampleColorRowIds() != null)
            {
                List<Long> disabledColorRowIds = options.getDisabledSampleColorRowIds().stream().map(Integer::longValue).toList();
                boolean hasChange = ExperimentService.get().ensureDataColorExclusions(st.getRowId(), ExperimentService.DataTypeForExclusion.SampleType, disabledColorRowIds, container, user);
                if (hasChange)
                    auditSampleColorExclusion(container, st.getRowId(), user);
            }

            errors = DomainUtil.updateDomainDescriptor(original, update, container, user, hasNameChange, changeDetails.toString(), auditUserComment, oldProps, newProps);

            if (!errors.hasErrors())
            {
                QueryService.get().saveCalculatedFieldsMetadata(SamplesSchema.SCHEMA_NAME, update.getQueryName(), hasNameChange ? newName : null, update.getCalculatedFields(), !original.getCalculatedFields().isEmpty(), user, container);

                if (hasNameChange)
                    ExperimentService.get().addObjectLegacyName(st.getObjectId(), ExperimentServiceImpl.getNamespacePrefix(ExpSampleType.class), oldSampleTypeName, user);

                transaction.addCommitTask(() -> indexSampleType(st, SearchService.get().defaultTask().getQueue(container, SearchService.PRIORITY.modified)), POSTCOMMIT);
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

    @Override
    public void auditSampleColorExclusion(Container container, long materialSourceId, User user)
    {
        Set<Long> disabled = ExperimentService.get().getDataTypeExcludedColors(ExperimentService.DataTypeForExclusion.SampleType, materialSourceId);
        String msg = "Sample color exclusion was updated for sample type (rowId " + materialSourceId + "). "
                + (disabled.isEmpty() ? "All colors enabled." : "Excluded color rowIds: " + StringUtils.join(disabled, ", ") + ".");
        AuditTypeEvent event = new AuditTypeEvent(SampleTypeAuditProvider.EVENT_TYPE, container, msg);
        AuditLogService.get().addEvent(user, event);
    }


    public String getCommentDetailed(QueryService.AuditAction action, boolean isUpdate)
    {
        String comment = SampleTimelineAuditEvent.SampleTimelineEventType.getActionCommentDetailed(action, isUpdate);
        return StringUtils.isEmpty(comment) ? action.getCommentDetailed() : comment;
    }

    @Override
    public DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> row, Map<String, Object> existingRow, Map<String, Object> providedValues)
    {
        return createAuditRecord(c, tInfo, getCommentDetailed(action, !existingRow.isEmpty()), userComment, action, row, existingRow, providedValues);
    }

    @Override
    protected AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row)
    {
        return createAuditRecord(c, tInfo, String.format(action.getCommentSummary(), rowCount), userComment, row);
    }

    private SampleTimelineAuditEvent createAuditRecord(Container c, AuditConfigurable tInfo, String comment, String userComment, @Nullable Map<String, Object> row)
    {
        return createAuditRecord(c, tInfo, comment, userComment, null, row, null, null);
    }

    private boolean isInputFieldKey(String fieldKey)
    {
        int slash = fieldKey.indexOf('/');
        return  slash==ExpData.DATA_INPUT_PARENT.length() && Strings.CI.startsWith(fieldKey,ExpData.DATA_INPUT_PARENT) ||
                slash==ExpMaterial.MATERIAL_INPUT_PARENT.length() && Strings.CI.startsWith(fieldKey,ExpMaterial.MATERIAL_INPUT_PARENT);
    }

    private SampleTimelineAuditEvent createAuditRecord(Container c, AuditConfigurable tInfo, String comment, String userComment, @Nullable QueryService.AuditAction action, @Nullable Map<String, Object> row, @Nullable Map<String, Object> existingRow, @Nullable Map<String, Object> providedValues)
    {
        SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(c, comment);
        event.setUserComment(userComment);

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
        else if (tInfo != null)
        {
            UserSchema schema = tInfo.getUserSchema();
            if (schema != null)
            {
                ExpSampleType sampleType = getSampleType(c, tInfo.getName(), true);
                if (sampleType != null)
                {
                    event.setSampleType(sampleType.getName());
                    event.setSampleTypeId(sampleType.getRowId());
                }
            }
        }

        // Put the raw amount and units into the stored amount and unit fields to override the conversion to display values that has happened via the expression columns
        if (existingRow != null && !existingRow.isEmpty())
        {
            if (existingRow.containsKey(RawAmount.name()))
                existingRow.put(StoredAmount.name(), existingRow.get(RawAmount.name()));
            if (existingRow.containsKey(RawUnits.name()))
                existingRow.put(Units.name(), existingRow.get(RawUnits.name()));
        }

        // Add providedValues to eventMetadata
        Map<String, Object> eventMetadata = new HashMap<>();
        if (providedValues != null)
        {
            eventMetadata.putAll(providedValues);
        }
        if (action != null)
        {
            SampleTimelineAuditEvent.SampleTimelineEventType timelineEventType = SampleTimelineAuditEvent.SampleTimelineEventType.getTypeFromAction(action);
            if (timelineEventType != null)
                eventMetadata.put(SAMPLE_TIMELINE_EVENT_TYPE, action);
        }
        if (!eventMetadata.isEmpty())
            event.setMetadata(AbstractAuditTypeProvider.encodeForDataMap(eventMetadata));

        return event;
    }

    private SampleTimelineAuditEvent createAuditRecord(Container container, String comment, String userComment, ExpMaterial sample, @Nullable Map<String, Object> metadata)
    {
        SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(container, comment);
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
        event.setMetadata(AbstractAuditTypeProvider.encodeForDataMap(metadata));
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
        Pair<Collection<Long>, Collection<Long>> parentsGroup = getAliquotParentsForRecalc(sampleType.getLSID(), container);
        Collection<Long> allParents = parentsGroup.first;
        Collection<Long> withAmountsParents = parentsGroup.second;
        return recomputeSamplesRollup(allParents, withAmountsParents, sampleType.getMetricUnit(), container);
    }

    /** This method updates exp.material, caller should call {@link SampleTypeServiceImpl#refreshSampleTypeMaterializedView} as appropriate. */
    @Override
    public int recomputeSamplesRollup(Collection<Long> sampleIds, String sampleTypeMetricUnit, Container container) throws IllegalStateException, SQLException
    {
        return recomputeSamplesRollup(sampleIds, sampleIds, sampleTypeMetricUnit, container);
    }

    public record AliquotAmountUnitResult(Double amount, String unit, boolean isAvailable) {}

    public record AliquotAvailableAmountUnit(Double amount, String unit, Double availableAmount) {}

    /** This method updates exp.material, caller should call {@link SampleTypeServiceImpl#refreshSampleTypeMaterializedView} as appropriate. */
    private int recomputeSamplesRollup(Collection<Long> parents, Collection<Long> withAmountsParents, String sampleTypeUnit, Container container) throws IllegalStateException, SQLException
    {
        return recomputeSamplesRollup(parents, null, withAmountsParents, sampleTypeUnit, container);
    }

    /** This method updates exp.material, caller should call {@link SampleTypeServiceImpl#refreshSampleTypeMaterializedView} as appropriate. */
    public int recomputeSamplesRollup(
        Collection<Long> parents,
        @Nullable Collection<Long> availableParents,
        Collection<Long> withAmountsParents,
        String sampleTypeUnit,
        Container container
    ) throws IllegalStateException, SQLException
    {
        Map<Long, String> sampleUnits = new LongHashMap<>();
        TableInfo materialTable = ExperimentService.get().getTinfoMaterial();
        DbScope scope = materialTable.getSchema().getScope();

        List<Long> availableSampleStates = new LongArrayList();

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
            Map<Long, Pair<Integer, String>> sampleAliquotCounts = getSampleAliquotCounts(parents);
            try (Connection c = scope.getConnection())
            {
                Parameter rowid = new Parameter("rowid", JdbcType.INTEGER);
                Parameter count = new Parameter("rollupCount", JdbcType.INTEGER);
                ParameterMapStatement pm = new ParameterMapStatement(scope, c,
                        new SQLFragment("UPDATE ").append(materialTable).append(" SET AliquotCount = ? WHERE RowId = ?").addAll(count, rowid), null);

                List<Map.Entry<Long, Pair<Integer, String>>> sampleAliquotCountList = new ArrayList<>(sampleAliquotCounts.entrySet());

                ListUtils.partition(sampleAliquotCountList, 1000).forEach(sublist ->
                {
                    for (Map.Entry<Long, Pair<Integer, String>> sampleAliquotCount: sublist)
                    {
                        Long sampleId = sampleAliquotCount.getKey();
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
            Map<Long, Pair<Integer, String>> sampleAliquotCounts = getSampleAvailableAliquotCounts(availableParents == null ? parents : availableParents, availableSampleStates);
            try (Connection c = scope.getConnection())
            {
                Parameter rowid = new Parameter("rowid", JdbcType.INTEGER);
                Parameter count = new Parameter("AvailableAliquotCount", JdbcType.INTEGER);
                ParameterMapStatement pm = new ParameterMapStatement(scope, c,
                        new SQLFragment("UPDATE ").append(materialTable).append(" SET AvailableAliquotCount = ? WHERE RowId = ?").addAll(count, rowid), null);

                List<Map.Entry<Long, Pair<Integer, String>>> sampleAliquotCountList = new ArrayList<>(sampleAliquotCounts.entrySet());

                ListUtils.partition(sampleAliquotCountList, 1000).forEach(sublist ->
                {
                    for (var sampleAliquotCount: sublist)
                    {
                        var sampleId = sampleAliquotCount.getKey();
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
            if (!StringUtils.isEmpty(sampleTypeUnit))
            {
                Unit sampleTypeDisplayUnit = Unit.valueOf(sampleTypeUnit);
                // if sample type has unit, use it for simple rollup without need for conversion
                Unit sampleTypeBaseUnit = sampleTypeDisplayUnit.getBase();
                String baseUnit = sampleTypeBaseUnit.name();

                TableInfo tableInfo = ExperimentService.get().getTinfoMaterial();

                ListUtils.partition(new ArrayList<>(withAmountsParents), 1000).forEach(sublist ->
                {
                    if (sublist.isEmpty())
                        return;

                    int precisionScale = sampleTypeBaseUnit.getPrecisionScale();
                    if (precisionScale > 9 && sampleTypeDisplayUnit.getValue() > 1e-9)
                    {
                        // reserve higher precisionScale for when display units are very small, like ng or pg
                        precisionScale = 9;
                    }

                    boolean isCountUnitType = sampleTypeBaseUnit.getKindOfQuantity() == KindOfQuantity.Count;
                    String aliquotUnitSql = isCountUnitType ? "CASE WHEN MIN(im.units) = MAX(im.units) THEN MIN(im.units) ELSE ? END" : "?";

                    SQLFragment statsSql = new SQLFragment("SELECT im.rootmaterialrowid, SUM(im.storedamount) AS total_volume, \n")
                            .append("SUM(CASE WHEN im.samplestate ").appendInClause(availableSampleStates, tableInfo.getSqlDialect()).append(" THEN im.storedamount ELSE 0 END) AS avail_volume, \n")
                            .append(aliquotUnitSql)
                            .append(" AS common_unit \n").add(baseUnit)
                            .append("FROM exp.material im\n")
                            .append("WHERE im.rootmaterialrowid ")
                            .appendInClause(sublist, tableInfo.getSqlDialect())
                            .append(" AND im.rowid != im.rootmaterialrowid\n")
                            .append(" GROUP BY im.rootmaterialrowid\n");

                    SQLFragment quickRollUpSql = null;

                    if (tableInfo.getSchema().getSqlDialect().isSqlServer())
                    {
                        /*
                         * SqlServer needs to specify the alias in the FROM clause, and use that alias as the target of the update.
                         */
                        quickRollUpSql = new SQLFragment("UPDATE exp.material SET \n")
                                .append("aliquotvolume = ROUND(CAST(COALESCE(stats.total_volume, 0) AS NUMERIC(38,12)) , ?),\n").add(precisionScale)
                                .append("aliquotunit = stats.common_unit,\n")
                                .append("availablealiquotvolume = ROUND(CAST(COALESCE(stats.avail_volume, 0) AS NUMERIC(38,12)), ?)\n").add(precisionScale)
                                .append("FROM exp.material m INNER JOIN (")
                                .append(statsSql)
                                .append(") AS stats\n")
                                .append("ON m.rowid = stats.rootmaterialrowid"
                                );
                    }
                    else
                    {
                        /*
                         * Alias usage: PostgreSQL allows you to use an alias in the UPDATE clause itself
                         * Type casting: PostgreSQL uses ::NUMERIC for type casting.
                         * JOIN condition: The WHERE clause is used for joining the tables instead of an INNER JOIN with ON.
                         */
                        quickRollUpSql = new SQLFragment("UPDATE exp.material AS m SET \n")
                                .append("aliquotvolume = ROUND(COALESCE(stats.total_volume, 0)::NUMERIC, ?),\n").add(precisionScale)
                                .append("aliquotunit = stats.common_unit,\n")
                                .append("availablealiquotvolume = ROUND(COALESCE(stats.avail_volume, 0)::NUMERIC, ?)\n").add(precisionScale)
                                .append("FROM (")
                                .append(statsSql)
                                .append(") AS stats\n")
                                .append("WHERE m.rowid = stats.rootmaterialrowid"
                                );
                    }

                    new SqlExecutor(tableInfo.getSchema()).execute(quickRollUpSql);

                    // Now clear out rollups for samples that have zero aliquots
                    SQLFragment quickClearRollupSql = new SQLFragment("UPDATE exp.material SET \n")
                            .append("aliquotvolume = 0, availablealiquotvolume = 0, ")
                            .append("aliquotunit = ?\n").add(baseUnit)
                            .append("WHERE rowid = rootmaterialrowid AND AliquotCount = 0 AND rowid ")
                            .appendInClause(sublist, tableInfo.getSqlDialect());
                    new SqlExecutor(tableInfo.getSchema()).execute(quickClearRollupSql);

                });
            }
            else
            {
                Map<Long, List<AliquotAmountUnitResult>> samplesAliquotAmounts = getSampleAliquotAmounts(withAmountsParents, availableSampleStates);

                try (Connection c = scope.getConnection())
                {
                    Parameter rowid = new Parameter("rowid", JdbcType.INTEGER);
                    Parameter amount = new Parameter("amount", JdbcType.DOUBLE);
                    Parameter unit = new Parameter("unit", JdbcType.VARCHAR);
                    Parameter availableAmount = new Parameter("availableAmount", JdbcType.DOUBLE);

                    ParameterMapStatement pm = new ParameterMapStatement(scope, c,
                            new SQLFragment("UPDATE ").append(materialTable).append(" SET AliquotVolume = ?, AliquotUnit = ? , AvailableAliquotVolume = ? WHERE RowId = ? ").addAll(amount, unit, availableAmount, rowid), null);

                    List<Map.Entry<Long, List<AliquotAmountUnitResult>>> sampleAliquotAmountsList = new ArrayList<>(samplesAliquotAmounts.entrySet());

                    ListUtils.partition(sampleAliquotAmountsList, 1000).forEach(sublist ->
                    {
                        for (Map.Entry<Long, List<AliquotAmountUnitResult>> sampleAliquotAmounts: sublist)
                        {
                            Long sampleId = sampleAliquotAmounts.getKey();
                            List<AliquotAmountUnitResult> aliquotAmounts = sampleAliquotAmounts.getValue();

                            if (aliquotAmounts == null || aliquotAmounts.isEmpty())
                                continue;
                            AliquotAvailableAmountUnit amountUnit = computeAliquotTotalAmounts(aliquotAmounts, sampleTypeUnit, sampleUnits.get(sampleId));
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
        }

        return !parents.isEmpty() ? parents.size() : (availableParents != null ? availableParents.size() : withAmountsParents.size());
    }

    @Override
    public int recomputeSampleTypeRollup(@NotNull ExpSampleType sampleType, Set<Long> rootRowIds, Set<String> parentNames, Container container) throws SQLException
    {
        Set<Long> rootSamplesToRecalc = new LongHashSet();
        if (rootRowIds != null)
            rootSamplesToRecalc.addAll(rootRowIds);
        if (parentNames != null)
            rootSamplesToRecalc.addAll(getRootSampleIdsFromParentNames(sampleType.getLSID(), parentNames));

        return recomputeSamplesRollup(rootSamplesToRecalc, rootSamplesToRecalc, sampleType.getMetricUnit(), container);
    }

    private Set<Long> getRootSampleIdsFromParentNames(String sampleTypeLsid, Set<String> parentNames)
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

        return new SqlSelector(tableInfo.getSchema(), sql).fillSet(Long.class, new HashSet<>());
    }

    private AliquotAvailableAmountUnit computeAliquotTotalAmounts(List<AliquotAmountUnitResult> volumeUnits, String sampleTypeUnitsStr, String sampleItemUnitsStr)
    {
        if (volumeUnits == null || volumeUnits.isEmpty())
            return null;

        Set<String> uniqueAliquotUnits = volumeUnits.stream().map(AliquotAmountUnitResult::unit).collect(Collectors.toSet());
        boolean hasSameAliquotUnit = uniqueAliquotUnits.size() <= 1;

        Unit totalUnit = null;
        String totalUnitsStr;
        if (!StringUtils.isEmpty(sampleTypeUnitsStr))
            totalUnitsStr = sampleTypeUnitsStr;
        else if (hasSameAliquotUnit && !StringUtils.isEmpty(volumeUnits.getFirst().unit)) // if all aliquots have the same unit, prefer it over parent's unit
            totalUnitsStr = volumeUnits.getFirst().unit;
        else if (!StringUtils.isEmpty(sampleItemUnitsStr))
            totalUnitsStr = sampleItemUnitsStr;
        else // use the unit of the first aliquot if there are no other indications
            totalUnitsStr = volumeUnits.getFirst().unit;
        if (!StringUtils.isEmpty(totalUnitsStr))
        {
            try
            {
                if (!StringUtils.isEmpty(sampleTypeUnitsStr))
                    totalUnit = Unit.valueOf(totalUnitsStr).getBase();
                else
                    totalUnit = Unit.valueOf(totalUnitsStr);
            }
            catch (IllegalArgumentException e)
            {
                // do nothing; leave unit as null
            }
        }

        double totalVolume = 0.0;
        double totalAvailableVolume = 0.0;

        for (AliquotAmountUnitResult volumeUnit : volumeUnits)
        {
            Unit unit = null;
            try
            {
                double storedAmount = volumeUnit.amount;
                String aliquotUnit = volumeUnit.unit;
                boolean isAvailable = volumeUnit.isAvailable;

                try
                {
                    unit = StringUtils.isEmpty(aliquotUnit) ? totalUnit : Unit.fromName(aliquotUnit);
                }
                catch (IllegalArgumentException ignore)
                {
                    // if aliquot units are incompatible, skip
                }

                double convertedAmount = 0;
                // include in total volume only if aliquot unit is compatible
                if (totalUnit != null && totalUnit.isCompatible(unit))
                    convertedAmount = Unit.convert(storedAmount, unit, totalUnit);
                else if (totalUnit == null) // sample (or 1st aliquot) unit is not a supported unit
                {
                    if (StringUtils.isEmpty(sampleTypeUnitsStr) && StringUtils.isEmpty(aliquotUnit)) //aliquot units are empty
                        convertedAmount = storedAmount;
                    else if (sampleTypeUnitsStr != null && sampleTypeUnitsStr.equalsIgnoreCase(aliquotUnit)) //aliquot units use the same unsupported unit ('cc')
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
        int scale = totalUnit == null ? Quantity.DEFAULT_PRECISION_SCALE : totalUnit.getPrecisionScale();
        totalVolume = Precision.round(totalVolume, scale);
        totalAvailableVolume = Precision.round(totalAvailableVolume, scale);

        return new AliquotAvailableAmountUnit(totalVolume, totalUnit == null ? null : totalUnit.name(), totalAvailableVolume);
    }

    public Pair<Collection<Long>, Collection<Long>> getAliquotParentsForRecalc(String sampleTypeLsid, Container container) throws SQLException
    {
        Collection<Long> parents = getAliquotParents(sampleTypeLsid, container);
        Collection<Long> withAmountsParents = parents.isEmpty() ? Collections.emptySet() : getAliquotsWithAmountsParents(sampleTypeLsid, container);
        return new Pair<>(parents, withAmountsParents);
    }

    private Collection<Long> getAliquotParents(String sampleTypeLsid, Container container) throws IllegalStateException, SQLException
    {
        return getAliquotParents(sampleTypeLsid, false, container);
    }

    private Collection<Long> getAliquotsWithAmountsParents(String sampleTypeLsid, Container container) throws IllegalStateException, SQLException
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

    private Collection<Long> getAliquotParents(String sampleTypeLsid, boolean withAmount, Container container) throws SQLException
    {
        DbSchema dbSchema = getExpSchema();

        SQLFragment sql = withAmount ? getParentsOfAliquotsWithAmountsSql() : getParentsOfAliquotsSql();

        sql.append("parent.cpastype = ?");
        sql.add(sampleTypeLsid);
        sql.append(" AND parent.container = ?");
        sql.add(container.getId());

        Set<Long> parentIds = new LongHashSet();
        try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
        {
            while (rs.next())
                parentIds.add(rs.getLong(1));
        }

        return parentIds;
    }

    private Map<Long, Pair<Integer, String>> getSampleAliquotCounts(Collection<Long> sampleIds) throws SQLException
    {
        DbSchema dbSchema = getExpSchema();
        SqlDialect dialect = dbSchema.getSqlDialect();

        SQLFragment sql = new SQLFragment("SELECT m.RowId as SampleId, m.Units, (SELECT COUNT(*) FROM exp.material a WHERE ")
                .append("a.rootMaterialRowId = m.rowId")
                .append(")-1 AS CreatedAliquotCount FROM exp.material AS m WHERE m.rowid ");
        dialect.appendInClauseSql(sql, sampleIds);

        Map<Long, Pair<Integer, String>> sampleAliquotCounts = new TreeMap<>(); // Order sample by rowId to reduce probability of deadlock with search indexer
        try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
        {
            while (rs.next())
            {
                long parentId = rs.getLong(1);
                String sampleUnit = rs.getString(2);
                int aliquotCount = rs.getInt(3);

                sampleAliquotCounts.put(parentId, new Pair<>(aliquotCount, sampleUnit));
            }
        }

        return sampleAliquotCounts;
    }

    private Map<Long, Pair<Integer, String>> getSampleAvailableAliquotCounts(Collection<Long> sampleIds, Collection<Long> availableSampleStates) throws SQLException
    {
        DbSchema dbSchema = getExpSchema();
        SqlDialect dialect = dbSchema.getSqlDialect();

        SQLFragment sql = new SQLFragment(
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
        dialect.appendInClauseSql(sql, sampleIds);

        Map<Long, Pair<Integer, String>> sampleAliquotCounts = new TreeMap<>(); // Order by rowId to reduce deadlock with search indexer
        try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
        {
            while (rs.next())
            {
                long parentId = rs.getLong(1);
                String sampleUnit = rs.getString(2);
                int aliquotCount = rs.getInt(3);

                sampleAliquotCounts.put(parentId, new Pair<>(aliquotCount, sampleUnit));
            }
        }

        return sampleAliquotCounts;
    }

    private Map<Long, List<AliquotAmountUnitResult>> getSampleAliquotAmounts(Collection<Long> sampleIds, List<Long> availableSampleStates) throws SQLException
    {
        DbSchema exp = getExpSchema();
        SqlDialect dialect = exp.getSqlDialect();

        SQLFragment sql = new SQLFragment("SELECT parent.rowid AS parentSampleId, aliquot.StoredAmount, aliquot.Units, aliquot.samplestate\n")
                .append("FROM exp.material AS aliquot JOIN exp.material AS parent ON ")
                .append("parent.rowid = aliquot.rootmaterialrowid")
                .append(" WHERE ")
                .append("aliquot.rootmaterialrowid <> aliquot.rowid")
                .append(" AND parent.rowid ");
        dialect.appendInClauseSql(sql, sampleIds);

        Map<Long, List<AliquotAmountUnitResult>> sampleAliquotAmounts = new LongHashMap<>();

        try (ResultSet rs = new SqlSelector(exp, sql).getResultSet())
        {
            while (rs.next())
            {
                long parentId = rs.getLong(1);
                Double volume = rs.getDouble(2);
                String unit = rs.getString(3);
                long sampleState = rs.getLong(4);

                if (!sampleAliquotAmounts.containsKey(parentId))
                    sampleAliquotAmounts.put(parentId, new ArrayList<>());

                sampleAliquotAmounts.get(parentId).add(new AliquotAmountUnitResult(volume, unit, availableSampleStates.contains(sampleState)));
            }
        }
        // for any parents with no remaining aliquots, set the amounts to 0
        for (var parentId : sampleIds)
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
        Map<Long, List<FileFieldRenameData>> fileMovesBySampleId = new LongHashMap<>();
        ExperimentService expService = ExperimentService.get();
        Timestamp changedSince = SampleTypeUpdateServiceDI.captureChangedSince();

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            if (AuditBehaviorType.NONE != auditBehavior && transaction.getAuditEvent() == null)
            {
                TransactionAuditProvider.TransactionAuditEvent auditEvent = AbstractQueryUpdateService.createTransactionAuditEvent(targetContainer, QueryService.AuditAction.UPDATE);
                auditEvent.updateCommentRowCount(samples.size());
                AbstractQueryUpdateService.addTransactionAuditEvent(transaction, user, auditEvent);
            }

            for (Map.Entry<ExpSampleType, List<ExpMaterial>> entry : sampleTypesMap.entrySet())
            {
                ExpSampleType sampleType = entry.getKey();
                SamplesSchema schema = new SamplesSchema(user, sampleType.getContainer());
                TableInfo samplesTable = schema.getTable(sampleType, null);

                List<ExpMaterial> typeSamples = entry.getValue();
                List<Long> sampleIds = typeSamples.stream().map(ExpMaterial::getRowId).toList();

                // update for exp.material.container
                updateCounts.put("samples", updateCounts.get("samples") + Table.updateContainer(getTinfoMaterial(), "rowid", sampleIds, targetContainer, user, true));

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
                    inventoryCounts.forEach((key, count) -> updateCounts.compute(key, (k, c) -> c == null ? count : c + count));
                }

                // create summary audit entries for the source and target containers
                String samplesPhrase = StringUtilsLabKey.pluralize(sampleIds.size(), "sample");
                addSampleTypeAuditEvent(user, sourceContainer, sampleType,
                        "Moved " + samplesPhrase + " to " + targetContainer.getPath(), userComment, "moved from project");
                addSampleTypeAuditEvent(user, targetContainer, sampleType,
                        "Moved " + samplesPhrase  + " from " + sourceContainer.getPath(), userComment, "moved to project");

                // move the events associated with the samples that have moved
                SampleTimelineAuditProvider auditProvider = new SampleTimelineAuditProvider();
                int auditEventCount = auditProvider.moveEvents(targetContainer, sampleIds);
                updateCounts.compute("sampleAuditEvents", (k, c) -> c == null ? auditEventCount : c + auditEventCount);

                AuditBehaviorType stAuditBehavior = samplesTable.getEffectiveAuditBehavior(auditBehavior);
                // create new events for each sample that was moved.
                if (stAuditBehavior == AuditBehaviorType.DETAILED)
                {
                    for (ExpMaterial sample : typeSamples)
                    {
                        SampleTimelineAuditEvent event = createAuditRecord(targetContainer, "Sample folder was updated.", userComment, sample, null);
                        Map<String, Object> oldRecordMap = new HashMap<>();
                        // ContainerName is remapped to "Folder" within SampleTimelineEvent, but we don't
                        // use "Folder" here because this sample-type field is filtered out of timeline events by default
                        oldRecordMap.put("ContainerName", sourceContainer.getName());
                        Map<String, Object> newRecordMap = new HashMap<>();
                        newRecordMap.put("ContainerName", targetContainer.getName());
                        if (fileMovesBySampleId.containsKey(sample.getRowId()))
                        {
                            fileMovesBySampleId.get(sample.getRowId()).forEach(fileUpdateData -> {
                               oldRecordMap.put(fileUpdateData.fieldName, fileUpdateData.sourceFile.getAbsolutePath());
                               newRecordMap.put(fileUpdateData.fieldName, fileUpdateData.targetFile.getAbsolutePath());
                            });
                        }
                        event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(oldRecordMap));
                        event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(newRecordMap));
                        AuditLogService.get().addEvent(user, event);
                    }
                }
            }

            updateCounts.putAll(moveDerivationRuns(samples, targetContainer, user));

            transaction.addCommitTask(() -> {
                for (ExpSampleType sampleType : sampleTypesMap.keySet())
                {
                    // force refresh of materialized view
                    refreshSampleTypeMaterializedView(sampleType, SampleChangeType.update, changedSince);
                    // update search index for moved samples via indexSampleType() helper, it filters for samples to index
                    // based on the modified date
                    indexSampleType(sampleType, SearchService.get().defaultTask().getQueue(sampleType.getContainer(), SearchService.PRIORITY.modified));
                }
            }, DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);

            // add up the size of the value arrays in the fileMovesBySampleId map
            int fileMoveCount = fileMovesBySampleId.values().stream().mapToInt(List::size).sum();
            updateCounts.put("sampleFiles", fileMoveCount);
            transaction.addCommitTask(() -> {
                for (List<FileFieldRenameData> sampleFileRenameData : fileMovesBySampleId.values())
                {
                    for (FileFieldRenameData renameData : sampleFileRenameData)
                        moveFile(renameData, sourceContainer, user, transaction.getAuditEvent());
                }
            }, POSTCOMMIT);

            transaction.commit();
        }

        return updateCounts;
    }

    private Map<String, Integer> moveDerivationRuns(Collection<? extends ExpMaterial> samples, Container targetContainer, User user) throws ExperimentException, BatchValidationException
    {
        // collect unique runIds mapped to the samples that are moving that have that runId
        Map<Long, Set<ExpMaterial>> runIdSamples = new LongHashMap<>();
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
            Set<Long> outputIds = run.getMaterialOutputs().stream().map(ExpMaterial::getRowId).collect(Collectors.toSet());
            Set<Long> movingIds = runIdSamples.get(run.getRowId()).stream().map(ExpMaterial::getRowId).collect(Collectors.toSet());
            if (movingIds.size() == outputIds.size() && movingIds.containsAll(outputIds))
                toUpdate.add(run);
            else
                toSplit.add(run);
        }

        int updateCount = expService.moveExperimentRuns(toUpdate, targetContainer, user);
        int splitCount = splitExperimentRuns(toSplit, runIdSamples, targetContainer, user);
        return Map.of("sampleDerivationRunsUpdated", updateCount, "sampleDerivationRunsSplit", splitCount);
    }

    private int splitExperimentRuns(List<ExpRun> runs, Map<Long, Set<ExpMaterial>> movingSamples, Container targetContainer, User user) throws ExperimentException, BatchValidationException
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
            List<Long> movingSampleIds = movingSet.stream().map(ExpMaterial::getRowId).toList();

            outputApp.removeMaterialInputs(user, movingSampleIds);
            if (sourceApplication != null)
                sourceApplication.removeMaterialInputs(user, movingSampleIds);

            runCount++;
        }
        return runCount;
    }

    record SampleFileMoveReference(String sourceFilePath, File targetFile, Container sourceContainer, String sampleName, String fieldName) {}

    // return the map of file renames
    private Map<Long, List<FileFieldRenameData>> updateSampleFilePaths(ExpSampleType sampleType, List<ExpMaterial> samples, Container targetContainer, User user) throws ExperimentException
    {
        Map<Long, List<FileFieldRenameData>> sampleFileRenames = new LongHashMap<>();

        FileContentService fileService = FileContentService.get();
        if (fileService == null)
        {
            LOG.warn("No file service available. Sample files cannot be moved.");
            return sampleFileRenames;
        }

        if (fileService.getFileRoot(targetContainer) == null)
        {
            LOG.warn("No file root found for target container {}'. Files cannot be moved.", targetContainer);
            return sampleFileRenames;
        }

        List<? extends DomainProperty> fileDomainProps = sampleType.getDomain()
                .getProperties().stream()
                .filter(prop -> PropertyType.FILE_LINK.getTypeUri().equals(prop.getRangeURI())).toList();
        if (fileDomainProps.isEmpty())
            return sampleFileRenames;

        Map<Container, Boolean> hasFileRoot = new HashMap<>();
        Map<String, Integer> fileMoveCounts = new HashMap<>();
        Map<String, SampleFileMoveReference> fileMoveReferences = new HashMap<>();
        for (ExpMaterial sample : samples)
        {
            boolean hasSourceRoot = hasFileRoot.computeIfAbsent(sample.getContainer(), (container) -> fileService.getFileRoot(container) != null);
            if (!hasSourceRoot)
                LOG.warn("No file root found for source container {}. Files cannot be moved.", sample.getContainer());
            else
                for (DomainProperty fileProp : fileDomainProps )
                {
                    String sourceFileName = (String) sample.getProperty(fileProp);
                    if (StringUtils.isBlank(sourceFileName))
                        continue;
                    File updatedFile = FileContentService.get().getMoveTargetFile(sourceFileName, sample.getContainer(), targetContainer);
                    if (updatedFile != null)
                    {

                        if (!fileMoveReferences.containsKey(sourceFileName))
                            fileMoveReferences.put(sourceFileName, new SampleFileMoveReference(sourceFileName, updatedFile, sample.getContainer(), sample.getName(), fileProp.getName()));
                        if (!fileMoveCounts.containsKey(sourceFileName))
                            fileMoveCounts.put(sourceFileName, 0);
                        fileMoveCounts.put(sourceFileName, fileMoveCounts.get(sourceFileName) + 1);

                        File sourceFile = new File(sourceFileName);
                        FileFieldRenameData renameData = new FileFieldRenameData(sampleType, sample.getName(), fileProp.getName(), sourceFile, updatedFile);
                        sampleFileRenames.putIfAbsent(sample.getRowId(), new ArrayList<>());
                        List<FileFieldRenameData> fieldRenameData = sampleFileRenames.get(sample.getRowId());
                        fieldRenameData.add(renameData);
                    }
                }
        }

        for (String filePath : fileMoveReferences.keySet())
        {
            SampleFileMoveReference ref = fileMoveReferences.get(filePath);
            File sourceFile = new File(filePath);
            if (!ExperimentServiceImpl.get().canMoveFileReference(user, ref.sourceContainer, sourceFile, fileMoveCounts.get(filePath)))
                throw new ExperimentException("Sample " + ref.sampleName + " cannot be moved since it references a shared file: " + sourceFile.getName());

            // TODO, support batch fireFileMoveEvent to avoid excessive FileLinkFileListener.hardTableFileLinkColumns calls
            fileService.fireFileMoveEvent(sourceFile.toPath(), ref.targetFile.toPath(), user, ref.sourceContainer, targetContainer);
            FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(targetContainer, "File moved from " + ref.sourceContainer.getPath() + " to " + targetContainer.getPath() + ".");
            event.setProvidedFileName(sourceFile.getName());
            event.setFile(ref.targetFile.getName());
            event.setDirectory(ref.targetFile.getParent());
            event.setFieldName(ref.fieldName);
            AuditLogService.get().addEvent(user, event);
        }

        return sampleFileRenames;
    }

    private boolean moveFile(FileFieldRenameData renameData, Container sourceContainer, User user, TransactionAuditProvider.TransactionAuditEvent txAuditEvent)
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
                LOG.warn("{}{}", errorMsg, e.getMessage());
            }
        }

        String changeDetail = String.format("sample type '%s' sample '%s'", renameData.sampleType.getName(), renameData.sampleName);
        return ExperimentServiceImpl.get().moveFileLinkFile(renameData.sourceFile, renameData.targetFile, sourceContainer, user, changeDetail, txAuditEvent, renameData.fieldName);
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

    public enum SampleChangeType { insert, update, merge, delete, rollup /* aliquot count */, schema }

    public void refreshSampleTypeMaterializedView(@NotNull ExpSampleType st, SampleChangeType reason)
    {
        refreshSampleTypeMaterializedView(st, reason, null);
    }

    /**
     * @param changedSince a database-clock watermark captured before the update's writes, at or after which the changed
     *                     samples were modified (only meaningful for update); null means the caller could not capture a
     *                     watermark, forcing a full re-sync on the next read.
     */
    public void refreshSampleTypeMaterializedView(@NotNull ExpSampleType st, SampleChangeType reason, @Nullable Timestamp changedSince)
    {
        ExpMaterialTableImpl.refreshMaterializedView(st.getLSID(), reason, changedSince);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetValidatedUnit()
        {
            SampleTypeService service = SampleTypeService.get();
            try
            {
                service.getValidatedUnit("g", Unit.mg, "Sample Type");
                service.getValidatedUnit("g ", Unit.mg, "Sample Type");
                service.getValidatedUnit(" g ", Unit.mg, "Sample Type");
                service.getValidatedUnit("box", Unit.unit, "Sample Type");
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("Compatible unit should not throw exception.");
            }
            try
            {
                assertNull(service.getValidatedUnit(null, Unit.unit, "Sample Type"));
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("null units should be null");
            }
            try
            {
                assertNull(service.getValidatedUnit("", Unit.unit, "Sample Type"));
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("empty units should be null");
            }
            try
            {
                service.getValidatedUnit("g", Unit.unit, "Sample Type");
                fail("Units that are not comparable should throw exception.");
            }
            catch (ConversionExceptionWithMessage ignore)
            {

            }

            try
            {
                service.getValidatedUnit("nonesuch", Unit.unit, "Sample Type");
                fail("Invalid units should throw exception.");
            }
            catch (ConversionExceptionWithMessage ignore)
            {

            }

        }
    }
}
