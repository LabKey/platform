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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.Sets;
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
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleSetService;
import org.labkey.api.exp.api.SampleTypeDomainKindProperties;
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
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTCOMMIT;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTROLLBACK;
import static org.labkey.api.exp.query.ExpSchema.NestedSchemas.materials;


public class SampleSetServiceImpl implements SampleSetService
{
    public static SampleSetServiceImpl get()
    {
        return (SampleSetServiceImpl)SampleSetService.get();
    }


    private static final Logger LOG = Logger.getLogger(SampleSetServiceImpl.class);

    // SampleSet -> Container cache
    private Cache<String, String> sampleSetCache = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "SampleSetToContainer");

    private Cache<String, SortedSet<MaterialSource>> materialSourceCache = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "MaterialSource", (container, argument) ->
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
        return ExperimentServiceImpl.get().getTinfoMaterialSource();
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
    public void indexSampleSet(ExpSampleSet sampleSet)
    {
        SearchService ss = SearchService.get();
        if (ss == null)
            return;

        SearchService.IndexTask task = ss.defaultTask();

        Runnable r = () -> {

            indexSampleSet(sampleSet, task);
            indexSampleSetMaterials(sampleSet, task);

        };

        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }


    private void indexSampleSet(ExpSampleSet sampleSet, SearchService.IndexTask task)
    {
        // Index all ExpMaterial that have never been indexed OR where either the ExpSampleSet definition or ExpMaterial itself has changed since last indexed
        SQLFragment sql = new SQLFragment("SELECT * FROM ")
                .append(getTinfoMaterialSource(), "ms")
                .append(" WHERE ms.LSID NOT LIKE '%:").append(StudyService.SPECIMEN_NAMESPACE_PREFIX).append("%'")
                .append(" AND ms.LSID = ?").add(sampleSet.getLSID())
                .append(" AND (ms.lastIndexed IS NULL OR ms.lastIndexed < ? OR (ms.modified IS NOT NULL AND ms.lastIndexed < ms.modified))")
                .add(sampleSet.getModified());

        MaterialSource materialSource = new SqlSelector(getExpSchema().getScope(), sql).getObject(MaterialSource.class);
        if (materialSource != null)
        {
            ExpSampleSetImpl impl = new ExpSampleSetImpl(materialSource);
            impl.index(task);
        }
    }

    private void indexSampleSetMaterials(ExpSampleSet sampleSet, SearchService.IndexTask task)
    {
        // Index all ExpMaterial that have never been indexed OR where either the ExpSampleSet definition or ExpMaterial itself has changed since last indexed
        SQLFragment sql = new SQLFragment("SELECT * FROM ")
                .append(getTinfoMaterial(), "m")
                .append(" WHERE m.LSID NOT LIKE '%:").append(StudyService.SPECIMEN_NAMESPACE_PREFIX).append("%'")
                .append(" AND m.cpasType = ?").add(sampleSet.getLSID())
                .append(" AND (m.lastIndexed IS NULL OR m.lastIndexed < ? OR (m.modified IS NOT NULL AND m.lastIndexed < m.modified))")
                .add(sampleSet.getModified());

        new SqlSelector(getExpSchema().getScope(), sql).forEachBatch(batch -> {
            for (Material m : batch)
            {
                ExpMaterialImpl impl = new ExpMaterialImpl(m);
                impl.index(task);
            }
        }, Material.class, 1000);
    }


    @Override
    public Map<String, ExpSampleSet> getSampleSetsForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type)
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

        Map<String, ExpSampleSet> result = new LinkedHashMap<>();
        for (Map<String, Object> queryResult : new SqlSelector(getExpSchema(), sql).getMapCollection())
        {
            ExpSampleSet sampleSet = null;
            String maxSampleSetLSID = (String) queryResult.get("MaxSampleSetLSID");
            String minSampleSetLSID = (String) queryResult.get("MinSampleSetLSID");

            // Check if we have a sample set that was being referenced
            if (maxSampleSetLSID != null && maxSampleSetLSID.equalsIgnoreCase(minSampleSetLSID))
            {
                // If the min and the max are the same, it means all rows share the same value so we know that there's
                // a single sample set being targeted
                sampleSet = getSampleSet(container, maxSampleSetLSID);
            }
            result.put((String) queryResult.get("Role"), sampleSet);
        }
        return result;
    }

    @Override
    public List<ExpSampleSetImpl> getSampleSets(@NotNull Container container, @Nullable User user, boolean includeOtherContainers)
    {
        List<String> containerIds = ExperimentServiceImpl.get().createContainerList(container, user, includeOtherContainers);

        // Do the sort on the Java side to make sure it's always case-insensitive, even on Postgres
        TreeSet<ExpSampleSetImpl> result = new TreeSet<>();
        for (String containerId : containerIds)
        {
            for (MaterialSource source : getMaterialSourceCache().get(containerId))
            {
                result.add(new ExpSampleSetImpl(source));
            }
        }

        return List.copyOf(result);
    }

    @Override
    public ExpSampleSetImpl getSampleSet(@NotNull Container c, @NotNull String sampleSetName)
    {
        return getSampleSet(c, null, false, sampleSetName);
    }

    // NOTE: This method used to not take a user or check permissions
    @Override
    public ExpSampleSetImpl getSampleSet(@NotNull Container c, @NotNull User user, @NotNull String sampleSetName)
    {
        return getSampleSet(c, user, true, sampleSetName);
    }

    private ExpSampleSetImpl getSampleSet(@NotNull Container c, @Nullable User user, boolean includeOtherContainers, String sampleSetName)
    {
        return getSampleSet(c, user, includeOtherContainers, (materialSource -> materialSource.getName().equalsIgnoreCase(sampleSetName)));
    }

    @Override
    public ExpSampleSetImpl getSampleSet(@NotNull Container c, int rowId)
    {
        return getSampleSet(c, null, rowId, false);
    }

    @Override
    public ExpSampleSetImpl getSampleSet(@NotNull Container c, @NotNull User user, int rowId)
    {
        return getSampleSet(c, user, rowId, true);
    }


    @Override
    public ExpSampleSetImpl getSampleSetByType(@NotNull String lsid, Container hint)
    {
        Container c = hint;
        String id = sampleSetCache.get(lsid);
        if (null != id && (null == hint || !id.equals(hint.getId())))
            c = ContainerManager.getForId(id);
        ExpSampleSetImpl ss = null;
        if (null != c)
            ss = getSampleSet(c, null, false, ms -> lsid.equals(ms.getLSID()) );
        if (null == ss)
            ss = _getSampleSet(lsid);
        if (null != ss && null==id)
            sampleSetCache.put(lsid,ss.getContainer().getId());
        return ss;
    }


    private ExpSampleSetImpl getSampleSet(@NotNull Container c, @Nullable User user, int rowId, boolean includeOtherContainers)
    {
        return getSampleSet(c, user, includeOtherContainers, (materialSource -> materialSource.getRowId() == rowId));
    }

    private ExpSampleSetImpl getSampleSet(@NotNull Container c, @Nullable User user, boolean includeOtherContainers, Predicate<MaterialSource> predicate)
    {
        List<String> containerIds = ExperimentServiceImpl.get().createContainerList(c, user, includeOtherContainers);
        for (String containerId : containerIds)
        {
            Collection<MaterialSource> sampleSets = getMaterialSourceCache().get(containerId);
            for (MaterialSource materialSource : sampleSets)
            {
                if (predicate.test(materialSource))
                    return new ExpSampleSetImpl(materialSource);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public ExpSampleSetImpl getSampleSet(int rowId)
    {
        // TODO: Cache
        MaterialSource materialSource = new TableSelector(getTinfoMaterialSource()).getObject(rowId, MaterialSource.class);
        if (materialSource == null)
            return null;

        return new ExpSampleSetImpl(materialSource);
    }

    @Nullable
    @Override
    public ExpSampleSetImpl getSampleSet(String lsid)
    {
        return getSampleSetByType(lsid, null);
    }

    private ExpSampleSetImpl _getSampleSet(String lsid)
    {
        MaterialSource ms = getMaterialSource(lsid);
        if (ms == null)
            return null;

        return new ExpSampleSetImpl(ms);
    }


    public MaterialSource getMaterialSource(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
        return new TableSelector(getTinfoMaterialSource(), filter, null).getObject(MaterialSource.class);
    }

    @Override
    public String getDefaultSampleSetLsid()
    {
        return new Lsid.LsidBuilder("SampleSource", "Default").toString();
    }

    @Override
    public String getDefaultSampleSetMaterialLsidPrefix()
    {
        return new Lsid.LsidBuilder("Sample", ExperimentServiceImpl.DEFAULT_MATERIAL_SOURCE_NAME).toString() + "#";
    }


    public DbScope.Transaction ensureTransaction()
    {
        return getExpSchema().getScope().ensureTransaction();
    }


    public void deleteDefaultSampleSet()
    {
        SQLFragment sql = new SQLFragment()
                .append("SELECT ms.rowId, dd.domainId\n")
                .append("FROM ").append(ExperimentService.get().getTinfoMaterialSource(), "ms").append("\n")
                .append("INNER JOIN ").append(OntologyManager.getTinfoDomainDescriptor(), "dd").append("\n")
                .append("ON ms.lsid = dd.domainUri\n")
                .append("WHERE ms.lsid = ?").add(getDefaultSampleSetLsid());
        SqlSelector ss = new SqlSelector(ExperimentService.get().getSchema(), sql);

        Map<String, Object> row = ss.getMap();
        if (row != null)
        {
            try (DbScope.Transaction tx = ensureTransaction())
            {
                Integer rowId = (Integer) row.get("rowId");
                Integer domainId = (Integer) row.get("domainId");

                DbSequenceManager.delete(ContainerManager.getSharedContainer(), ExpSampleSetImpl.SEQUENCE_PREFIX, rowId);

                Domain d = PropertyService.get().getDomain(domainId);
                if (d != null)
                {
                    d.delete(null);
                }

                Table.delete(getTinfoMaterialSource(), rowId);

                tx.commit();
                LOG.info("Deleted the default " + ExperimentServiceImpl.DEFAULT_MATERIAL_SOURCE_NAME + " SampleSet");
            }
            catch (DomainNotFoundException e)
            {
                LOG.info("Failed to delete the default " + ExperimentServiceImpl.DEFAULT_MATERIAL_SOURCE_NAME + " SampleSet, domain not found");
            }
        }
    }


    @Override
    public Lsid getSampleSetLsid(String sourceName, Container container)
    {
        return Lsid.parse(ExperimentService.get().generateLSID(container, ExpSampleSet.class, sourceName));
    }


    /**
     * Delete all exp.Material from the SampleSet.  If container is not provided,
     * all rows from the SampleSet will be deleted regardless of container.
     */
    public int truncateSampleSet(ExpSampleSet source, User user, @Nullable Container c)
    {
        assert getExpSchema().getScope().isTransactionActive();

        SimpleFilter filter = c == null ? new SimpleFilter() : SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), source.getLSID());

        MultiValuedMap<String, Integer> byContainer = new ArrayListValuedHashMap<>();
        TableSelector ts = new TableSelector(SampleSetServiceImpl.get().getTinfoMaterial(), Sets.newCaseInsensitiveHashSet("container", "rowid"), filter, null);
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
    public void deleteSampleSet(int rowId, Container c, User user) throws ExperimentException
    {
        CPUTimer timer = new CPUTimer("delete sampleset");
        timer.start();

        ExpSampleSetImpl source = getSampleSet(c, user, rowId);
        if (null == source)
            throw new IllegalArgumentException("Can't find SampleSet with rowId " + rowId);
        if (!source.getContainer().equals(c))
            throw new ExperimentException("Trying to delete a SampleSet from a different container");

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            // TODO: option to skip deleting rows from the materialized table since we're about to delete it anyway
            // TODO do we need both truncateSampleSet() and deleteDomainObjects()?
            truncateSampleSet(source, user, null);

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
        DbSequenceManager.deleteLike(c, ExpSampleSet.SEQUENCE_PREFIX, source.getRowId(), getExpSchema().getSqlDialect());

        SchemaKey samplesSchema = SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME);
        QueryService.get().fireQueryDeleted(user, c, null, samplesSchema, singleton(source.getName()));

        SchemaKey expMaterialsSchema = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, materials.toString());
        QueryService.get().fireQueryDeleted(user, c, null, expMaterialsSchema, singleton(source.getName()));

        // Remove SampleSet from search index
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            try (Timing ignored = MiniProfiler.step("search docs"))
            {
                ss.deleteResource(source.getDocumentId());
            }
        }

        timer.stop();
        LOG.info("Deleted SampleSet '" + source.getName() + "' from '" + c.getPath() + "' in " + timer.getDuration());
    }


    @NotNull
    @Override
    public ExpSampleSetImpl createSampleSet()
    {
        return new ExpSampleSetImpl(new MaterialSource());
    }

    @NotNull
    @Override
    public ExpSampleSetImpl createSampleSet(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol, String nameExpression)
            throws ExperimentException
    {
        return createSampleSet(c,u,name,description,properties,indices,idCol1,idCol2,idCol3,parentCol,null, null);
    }

    @NotNull
    @Override
    public ExpSampleSetImpl createSampleSet(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                            String nameExpression, @Nullable TemplateInfo templateInfo)
            throws ExperimentException
    {
        return createSampleSet(c, u, name, description, properties, indices, idCol1, idCol2, idCol3,
                parentCol, nameExpression, templateInfo, null);
    }

    @NotNull
    @Override
    public ExpSampleSetImpl createSampleSet(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                            String nameExpression, @Nullable TemplateInfo templateInfo, @Nullable Map<String, String> importAliases)
        throws ExperimentException
    {
        if (name == null)
            throw new ExperimentException("SampleSet name is required");

        TableInfo materialSourceTable = ExperimentService.get().getTinfoMaterialSource();
        int nameMax = materialSourceTable.getColumn("Name").getScale();
        if (name.length() > nameMax)
            throw new ExperimentException("SampleSet name may not exceed " + nameMax + " characters.");

        ExpSampleSet existing = getSampleSet(c, name);
        if (existing != null)
            throw new IllegalArgumentException("SampleSet '" + existing.getName() + "' already exists");

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

        Lsid lsid = getSampleSetLsid(name, c);
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

        final ExpSampleSetImpl ss = new ExpSampleSetImpl(source);

        try
        {
            getExpSchema().getScope().executeWithRetry(transaction ->
            {
                try
                {
                    domain.save(u);
                    ss.save(u);
                    DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);
                    transaction.addCommitTask(() -> clearMaterialSourceCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
                    return ss;
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

        return ss;
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
            if (trimmedVal.equalsIgnoreCase(NEW_SAMPLE_SET_ALIAS_VALUE) ||
                trimmedVal.equalsIgnoreCase(MATERIAL_INPUTS_PREFIX + NEW_SAMPLE_SET_ALIAS_VALUE))
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
    public ValidationException updateSampleSet(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings)
    {
        ExpSampleSetImpl ss = new ExpSampleSetImpl(getMaterialSource(update.getDomainURI()));

        String newDescription = StringUtils.trimToNull(update.getDescription());
        String description = ss.getDescription();
        if (description == null || !description.equals(newDescription))
        {
            ss.setDescription(newDescription);
        }

        if (options != null)
        {
            String sampleIdPattern = StringUtils.trimToNull(options.getNameExpression());
            String oldPattern = ss.getNameExpression();
            if (oldPattern == null || !oldPattern.equals(sampleIdPattern))
            {
                ss.setNameExpression(sampleIdPattern);
            }

            ss.setImportAliasMap(options.getImportAliases());
        }

        ValidationException errors;
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            ss.save(user);
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
        //check if it is of the expected format or targeting the to be created sampleset
        if (!(UploadSamplesHelper.isInputOutputHeader(parentAlias) || NEW_SAMPLE_SET_ALIAS_VALUE.equals(parentAlias)))
            throw new IllegalArgumentException(String.format("Invalid parent alias header: %1$s", parentAlias));

        return true;
    }
}
