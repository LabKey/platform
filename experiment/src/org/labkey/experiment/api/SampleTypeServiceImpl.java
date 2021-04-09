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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditHandler;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
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
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static org.labkey.api.audit.SampleTimelineAuditEvent.SAMPLE_TIMELINE_EVENT_TYPE;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTCOMMIT;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTROLLBACK;
import static org.labkey.api.exp.api.ExperimentJSONConverter.CPAS_TYPE;
import static org.labkey.api.exp.api.ExperimentJSONConverter.LSID;
import static org.labkey.api.exp.api.ExperimentJSONConverter.NAME;
import static org.labkey.api.exp.api.ExperimentJSONConverter.ROW_ID;
import static org.labkey.api.exp.query.ExpSchema.NestedSchemas.materials;


public class SampleTypeServiceImpl extends AbstractAuditHandler implements SampleTypeService
{
    public static SampleTypeServiceImpl get()
    {
        return (SampleTypeServiceImpl) SampleTypeService.get();
    }

    private static final Logger LOG = LogManager.getLogger(SampleTypeServiceImpl.class);

    // SampleType -> Container cache
    private final Cache<String, String> sampleTypeCache = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "SampleTypeToContainer");

    private final Cache<String, SortedSet<MaterialSource>> materialSourceCache = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "MaterialSource", (container, argument) ->
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
                .append(" WHERE ms.LSID NOT LIKE '%:").append(StudyService.SPECIMEN_NAMESPACE_PREFIX).append("%'")
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
        SQLFragment sql = new SQLFragment("SELECT * FROM ")
                .append(getTinfoMaterial(), "m")
                .append(" WHERE m.LSID NOT LIKE '%:").append(StudyService.SPECIMEN_NAMESPACE_PREFIX).append("%'")
                .append(" AND m.cpasType = ?").add(sampleType.getLSID())
                .append(" AND (m.lastIndexed IS NULL OR m.lastIndexed < ? OR (m.modified IS NOT NULL AND m.lastIndexed < m.modified))")
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
        sql.append(filter.getSQLFragment(getExpSchema(), new SQLFragment("r.Container"), container));
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

    @Override
    public String getDefaultSampleTypeLsid()
    {
        return new Lsid.LsidBuilder("SampleSource", "Default").toString();
    }

    @Override
    public String getDefaultSampleTypeMaterialLsidPrefix()
    {
        return new Lsid.LsidBuilder("Sample", ExperimentServiceImpl.DEFAULT_MATERIAL_SOURCE_NAME).toString() + "#";
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

    /**
     * Delete all exp.Material from the SampleType. If container is not provided,
     * all rows from the SampleType will be deleted regardless of container.
     */
    public int truncateSampleType(ExpSampleType source, User user, @Nullable Container c)
    {
        assert getExpSchema().getScope().isTransactionActive();

        SimpleFilter filter = c == null ? new SimpleFilter() : SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), source.getLSID());

        MultiValuedMap<String, Integer> byContainer = new ArrayListValuedHashMap<>();
        TableSelector ts = new TableSelector(SampleTypeServiceImpl.get().getTinfoMaterial(), Sets.newCaseInsensitiveHashSet("container", "rowid"), filter, null);
        ts.forEachMap(row -> byContainer.put((String)row.get("container"), (Integer)row.get("rowid")));

        int count = 0;
        for (Map.Entry<String, Collection<Integer>> entry : byContainer.asMap().entrySet())
        {
            Container container = ContainerManager.getForId(entry.getKey());
            // TODO move deleteMaterialByRowIds()?
            ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, entry.getValue(), true, source);
            count += entry.getValue().size();
        }
        return count;
    }

    @Override
    public void deleteSampleType(int rowId, Container c, User user) throws ExperimentException
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

            Domain d = source.getDomain();
            d.delete(user);

            ExperimentServiceImpl.get().deleteDomainObjects(source.getContainer(), source.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("UPDATE " + getTinfoDataClass() + " SET materialSourceId = NULL WHERE materialSourceId = ?", source.getRowId());
            executor.execute("UPDATE " + getTinfoProtocolInput() + " SET materialSourceId = NULL WHERE materialSourceId = ?", source.getRowId());
            executor.execute("DELETE FROM " + getTinfoMaterialSource() + " WHERE RowId = ?", rowId);

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
        return createSampleType(c,u,name,description,properties,indices,idCol1,idCol2,idCol3,parentCol,null, null);
    }

    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                              String nameExpression, @Nullable TemplateInfo templateInfo)
            throws ExperimentException
    {
        return createSampleType(c, u, name, description, properties, indices, idCol1, idCol2, idCol3,
                parentCol, nameExpression, templateInfo, null, null, null, null);
    }

    @NotNull
    @Override
    public ExpSampleTypeImpl createSampleType(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                              String nameExpression, @Nullable TemplateInfo templateInfo, @Nullable Map<String, String> importAliases, @Nullable String labelColor, @Nullable String metricUnit, @Nullable Container autoLinkTargetContainer)
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

        if (properties == null || properties.size() < 1)
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

        // Validate the name expression length
        int nameExpMax = materialSourceTable.getColumn("NameExpression").getScale();
        if (nameExpression != null && nameExpression.length() > nameExpMax)
            throw new ExperimentException("Name expression may not exceed " + nameExpMax + " characters.");

        // Validate the label color length
        int labelColorMax = materialSourceTable.getColumn("LabelColor").getScale();
        if (labelColor != null && labelColor.length() > labelColorMax)
            throw new ExperimentException("Label color may not exceed " + labelColorMax + " characters.");

        // Validate the metricUnit length
        int metricUnitMax = materialSourceTable.getColumn("MetricUnit").getScale();
        if (metricUnit != null && metricUnit.length() > metricUnitMax)
            throw new ExperimentException("Metric unit may not exceed " + metricUnitMax + " characters.");

        Lsid lsid = getSampleTypeLsid(name, c);
        Domain domain = PropertyService.get().createDomain(c, lsid.toString(), name, templateInfo);
        DomainKind kind = domain.getDomainKind();
        Set<String> reservedNames = kind.getReservedPropertyNames(domain);
        Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());

        boolean hasNameProperty = false;
        String idUri1 = null, idUri2 = null, idUri3 = null, parentUri = null;
        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        Set<String> propertyUris = new HashSet<>();
        for (int i = 0; i < properties.size(); i++)
        {
            GWTPropertyDescriptor pd = properties.get(i);
            String propertyName = pd.getName().toLowerCase();

            if (ExpMaterialTable.Column.Name.name().equalsIgnoreCase(propertyName))
            {
                hasNameProperty = true;
            }
            else
            {
                if (lowerReservedNames.contains(propertyName))
                {
                    throw new IllegalArgumentException("Property name '" + propertyName + "' is a reserved name.");
                }

                DomainProperty dp = DomainUtil.addProperty(domain, pd, defaultValues, propertyUris, null);

                if (idCol1 == i)    idUri1    = dp.getPropertyURI();
                if (idCol2 == i)    idUri2    = dp.getPropertyURI();
                if (idCol3 == i)    idUri3    = dp.getPropertyURI();
                if (parentCol == i) parentUri = dp.getPropertyURI();
            }
        }

        Set<PropertyStorageSpec.Index> propertyIndices = new HashSet<>();
        for (GWTIndex index : indices)
        {
            PropertyStorageSpec.Index propIndex = new PropertyStorageSpec.Index(index.isUnique(), index.getColumnNames());
            propertyIndices.add(propIndex);
        }
        domain.setPropertyIndices(propertyIndices);

        if (!hasNameProperty && idUri1 == null)
            throw new ExperimentException("Either a 'Name' property or an index for idCol1 is required");

        if (hasNameProperty && idUri1 != null)
            throw new ExperimentException("Either a 'Name' property or idCols can be used, but not both");

        String importAliasJson = getAliasJson(importAliases, name);

        MaterialSource source = new MaterialSource();
        source.setLSID(lsid.toString());
        source.setName(name);
        source.setDescription(description);
        source.setMaterialLSIDPrefix(new Lsid.LsidBuilder("Sample", c.getRowId() + "." + PageFlowUtil.encode(name), "").toString());
        if (nameExpression != null)
            source.setNameExpression(nameExpression);
        source.setLabelColor(labelColor);
        source.setMetricUnit(metricUnit);
        source.setAutoLinkTargetContainer(autoLinkTargetContainer);
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
                    DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);
                    transaction.addCommitTask(() -> clearMaterialSourceCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
                    return st;
                }
                catch (ExperimentException eex)
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

    public String getAliasJson(Map<String, String> importAliases, String currentAliasName)
    {
        if (importAliases == null || importAliases.size() == 0)
            return null;

        Map<String, String> aliases = sanitizeAliases(importAliases, currentAliasName);

        try
        {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(aliases);
        }
        catch (JsonProcessingException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private Map<String, String> sanitizeAliases(Map<String, String> importAliases, String currentAliasName)
    {
        Map<String, String> cleanAliases = new HashMap<>();
        importAliases.forEach((key, value) -> {
            String trimmedKey = StringUtils.trimToNull(key);
            String trimmedVal = StringUtils.trimToNull(value);

            //Sanity check this should be caught earlier
            if (trimmedKey == null || trimmedVal == null)
                throw new IllegalArgumentException("Parent aliases contain blanks");

            //Substitute the currentAliasName for the placeholder value
            if (trimmedVal.equalsIgnoreCase(NEW_SAMPLE_TYPE_ALIAS_VALUE) ||
                trimmedVal.equalsIgnoreCase(MATERIAL_INPUTS_PREFIX + NEW_SAMPLE_TYPE_ALIAS_VALUE))
            {
                trimmedVal = MATERIAL_INPUTS_PREFIX + currentAliasName;
            }

            cleanAliases.put(trimmedKey, trimmedVal);
        });

        return cleanAliases;
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

        public String getSequenceName(@Nullable Date date)
        {
            LocalDateTime ldt;
            if (date == null)
                ldt = LocalDateTime.now();
            else
                ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            String suffix = _formatter.format(ldt);
            return "org.labkey.api.exp.api.ExpMaterial:" + name() + ":" + suffix;
        }

        public long next(Date date)
        {
            String seqName = getSequenceName(date);
            DbSequence seq = DbSequenceManager.getPreallocatingSequence(ContainerManager.getRoot(), seqName);
            return seq.next();
        }
    }

    @Override
    public Map<String, Long> incrementSampleCounts(@Nullable Date counterDate)
    {
        Map<String, Long> counts = new HashMap<>();
        counts.put("dailySampleCount",   SampleSequenceType.DAILY.next(counterDate));
        counts.put("weeklySampleCount",  SampleSequenceType.WEEKLY.next(counterDate));
        counts.put("monthlySampleCount", SampleSequenceType.MONTHLY.next(counterDate));
        counts.put("yearlySampleCount",  SampleSequenceType.YEARLY.next(counterDate));
        return counts;
    }

    @Override
    public ValidationException updateSampleType(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings)
    {
        ExpSampleTypeImpl st = new ExpSampleTypeImpl(getMaterialSource(update.getDomainURI()));

        String newDescription = StringUtils.trimToNull(update.getDescription());
        String description = st.getDescription();
        if (description == null || !description.equals(newDescription))
        {
            st.setDescription(newDescription);
        }

        if (options != null)
        {
            String sampleIdPattern = StringUtils.trimToNull(options.getNameExpression());
            String oldPattern = st.getNameExpression();
            if (oldPattern == null || !oldPattern.equals(sampleIdPattern))
            {
                st.setNameExpression(sampleIdPattern);
            }

            st.setLabelColor(options.getLabelColor());
            st.setMetricUnit(options.getMetricUnit());
            st.setImportAliasMap(options.getImportAliases());
            st.setAutoLinkTargetContainer(ContainerManager.getForId(options.getAutoLinkTargetContainerId()));
        }

        ValidationException errors;
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            st.save(user);
            errors = DomainUtil.updateDomainDescriptor(original, update, container, user);

            if (!errors.hasErrors())
            {
                transaction.addCommitTask(() -> clearMaterialSourceCache(container), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
                transaction.commit();
            }
        }

        return errors;
    }

    @Override
    public boolean parentAliasHasCorrectFormat(String parentAlias)
    {
        //check if it is of the expected format or targeting the to be created sample type
        if (!(UploadSamplesHelper.isInputOutputHeader(parentAlias) || NEW_SAMPLE_TYPE_ALIAS_VALUE.equals(parentAlias)))
            throw new IllegalArgumentException(String.format("Invalid parent alias header: %1$s", parentAlias));

        return true;
    }

    public String getCommentDetailed(QueryService.AuditAction action, boolean isUpdate)
    {
        String comment = SampleTimelineAuditEvent.SampleTimelineEventType.getActionCommentDetailed(action, isUpdate);
        return StringUtils.isEmpty(comment) ? action.getCommentDetailed() : comment;
    }

    @Override
    public DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> row, Map<String, Object> existingRow)
    {
        // not doing anything with userComment at the moment
        return createAuditRecord(c, getCommentDetailed(action, !existingRow.isEmpty()), action, row, existingRow);
    }

    @Override
    protected AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row)
    {
        // not doing anything with userComment at the moment
        return createAuditRecord(c, String.format(action.getCommentSummary(), rowCount), row);
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

    private SampleTimelineAuditEvent createAuditRecord(Container c, String comment, @Nullable Map<String, Object> row)
    {
        return createAuditRecord(c, comment, null, row, null);
    }

    // move to UploadSamplesHelper?
    private boolean isInputFieldKey(String fieldKey)
    {
        int slash = fieldKey.indexOf('/');
        return  slash==ExpData.DATA_INPUT_PARENT.length() && StringUtils.startsWithIgnoreCase(fieldKey,ExpData.DATA_INPUT_PARENT) ||
                slash==ExpMaterial.MATERIAL_INPUT_PARENT.length() && StringUtils.startsWithIgnoreCase(fieldKey,ExpMaterial.MATERIAL_INPUT_PARENT);
    }

    private SampleTimelineAuditEvent createAuditRecord(Container c, String comment, @Nullable QueryService.AuditAction action, @Nullable Map<String, Object> row, @Nullable Map<String, Object> existingRow)
    {
        SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(c.getId(), comment);
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

            String sampleTypeLsid = null;
            if (staticsRow.containsKey(CPAS_TYPE))
                sampleTypeLsid =  String.valueOf(staticsRow.get(CPAS_TYPE));
            // When a sample is deleted, the LSID is provided via the "sampleset" field instead of "LSID"
            if (sampleTypeLsid == null && staticsRow.containsKey("sampleset"))
                sampleTypeLsid = String.valueOf(staticsRow.get("sampleset"));
            if (sampleTypeLsid != null)
            {
                ExpSampleType sampleType = SampleTypeService.get().getSampleTypeByType(sampleTypeLsid, c);
                if (sampleType != null)
                {
                    event.setSampleType(sampleType.getName());
                    event.setSampleTypeId(sampleType.getRowId());
                }
            }
            if (staticsRow.containsKey(LSID))
                event.setSampleLsid(String.valueOf(staticsRow.get(LSID)));
            if (staticsRow.containsKey(ROW_ID) && staticsRow.get(ROW_ID) != null)
                event.setSampleId((Integer) staticsRow.get(ROW_ID));
            if (staticsRow.containsKey(NAME))
                event.setSampleName(String.valueOf(staticsRow.get(NAME)));
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

    private SampleTimelineAuditEvent createAuditRecord(Container container, String comment, ExpMaterial sample, Map<String, Object> metadata)
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
        event.setMetadata(AbstractAuditTypeProvider.encodeForDataMap(container, metadata));
        return event;
    }

    @Override
    public void addAuditEvent(User user, Container container, String comment, ExpMaterial sample, Map<String, Object> metadata)
    {
        AuditLogService.get().addEvent(user, createAuditRecord(container, comment, sample, metadata));
    }

    @Override
    public void addAuditEvent(User user, Container container, String comment, ExpMaterial sample, Map<String, Object> metadata, String updateType)
    {
        SampleTimelineAuditEvent event = createAuditRecord(container, comment, sample, metadata);
        event.setInventoryUpdateType(updateType);
        AuditLogService.get().addEvent(user, event);
    }
}
