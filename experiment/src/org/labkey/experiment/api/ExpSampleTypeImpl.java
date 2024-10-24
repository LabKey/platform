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

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.writer.ContainerUser;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ExpSampleTypeImpl extends ExpIdentifiableEntityImpl<MaterialSource> implements ExpSampleType
{
    private static final String categoryName = "materialSource";
    private static final String mediaCategoryName = "mediaMaterialSource";
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory(categoryName, "Sample Types", false);
    public static final SearchService.SearchCategory mediaSearchCategory = new SearchService.SearchCategory(mediaCategoryName, "Media Sample Types", false) {
        @Override
        public Set<String> getPermittedContainerIds(User user, Map<String, Container> containers)
        {
            return getPermittedContainerIds(user, containers, MediaReadPermission.class);
        }
    };

    public static final String ALIQUOT_NAME_EXPRESSION = "${" + ALIQUOTED_FROM_EXPRESSION + "-:withCounter}";
    public static final String SAMPLE_COUNTER_SEQ_PREFIX = "samplenamegencounter-";

    private static final String MATERIAL_LSID_SUFFIX = "ToBeReplaced";

    private Domain _domain;
    private NameGenerator _nameGen;
    private NameGenerator _aliquotNameGen;

    // For serialization
    protected ExpSampleTypeImpl() {}

    static public Collection<ExpSampleTypeImpl> fromMaterialSources(List<MaterialSource> materialSources)
    {
        return materialSources.stream().map(ExpSampleTypeImpl::new).collect(Collectors.toList());
    }

    public ExpSampleTypeImpl(MaterialSource ms)
    {
        super(ms);
    }

    @Override
    public ActionURL detailsURL()
    {
        return _object.detailsURL();
    }

    @Override
    public ActionURL urlEditDefinition(ContainerUser cu)
    {
        ActionURL ret = new ActionURL(ExperimentController.EditSampleTypeAction.class, getContainer());
        ret.addParameter("RowId", getRowId());
        return ret;
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        return _object.getQueryRowReference();
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    @Override
    public String getMaterialLSIDPrefix()
    {
        return _object.getMaterialLSIDPrefix();
    }

    @Override
    public String getDescription()
    {
        return _object.getDescription();
    }

    @Override
    public boolean canImportMoreSamples()
    {
        return hasNameAsIdCol() || getIdCol1() != null;
    }

    private DomainProperty getDomainProperty(String uri)
    {
        if (uri == null)
        {
            return null;
        }

        return getDomain().getPropertyByURI(uri);
    }

    @Override
    @NotNull
    public List<DomainProperty> getIdCols()
    {
        List<DomainProperty> result = new ArrayList<>();
        if (hasNameAsIdCol())
            return result;

        DomainProperty idCol1 = getIdCol1();
        if (idCol1 != null)
        {
            result.add(idCol1);
        }
        DomainProperty idCol2 = getIdCol2();
        if (idCol2 != null)
        {
            result.add(idCol2);
        }
        DomainProperty idCol3 = getIdCol3();
        if (idCol3 != null)
        {
            result.add(idCol3);
        }
        return result;
    }

    @Override
    public boolean hasIdColumns()
    {
        return _object.getIdCol1() != null;
    }

    @Override
    public boolean hasNameAsIdCol()
    {
        return ExpMaterialTable.Column.Name.name().equals(_object.getIdCol1());
    }

    // NOTE: intentionally not public in ExpSampleType interface
    public void setParentCol(@Nullable String parentColumnPropertyURI)
    {
        _object.setParentCol(getPropertyOrThrow(parentColumnPropertyURI).getPropertyURI());
    }

    // NOTE: intentionally not public in ExpSampleType interface
    public void setIdCols(@NotNull List<String> propertyURIs)
    {
        if (_object.getNameExpression() != null)
            throw new IllegalArgumentException("Can't set both a name expression and idCols");

        if (!propertyURIs.isEmpty())
        {
            _object.setIdCol1(getPropertyOrThrow(propertyURIs.get(0)).getPropertyURI());
            if (propertyURIs.size() > 1)
            {
                _object.setIdCol2(getPropertyOrThrow(propertyURIs.get(1)).getPropertyURI());
                if (propertyURIs.size() > 2)
                {
                    _object.setIdCol3(getPropertyOrThrow(propertyURIs.get(2)).getPropertyURI());
                    if (propertyURIs.size() > 3)
                    {
                        throw new IllegalArgumentException("Only three ID columns are supported, but " + propertyURIs.size() + " were requested: " + propertyURIs);
                    }
                }
            }
        }
    }

    @NotNull
    private DomainProperty getPropertyOrThrow(String propertyURI)
    {
        DomainProperty dp = getDomainProperty(propertyURI);
        if (dp == null)
            throw new IllegalArgumentException("Failed to find property '" + propertyURI + "'");

        return dp;
    }

    @Override
    @Nullable
    public DomainProperty getIdCol1()
    {
        if (hasNameAsIdCol())
            return null;

        DomainProperty result = getDomainProperty(_object.getIdCol1());
        if (result == null)
        {
            // CONSIDER: don't grab the first property but require it to be explicitly set when the sample set is saved
            List<? extends DomainProperty> props = getDomain().getProperties();
            if (!props.isEmpty())
            {
                result = props.get(0);
            }
        }
        return result;
    }

    @Override
    public DomainProperty getIdCol2()
    {
        return getDomainProperty(_object.getIdCol2());
    }

    @Override
    public DomainProperty getIdCol3()
    {
        return getDomainProperty(_object.getIdCol3());
    }

    //TODO remove
    @Override
    public DomainProperty getParentCol()
    {
        return getDomainProperty(_object.getParentCol());
    }

    @Override
    public void setNameExpression(String expression)
    {
        if (expression != null && hasIdColumns() && !hasNameAsIdCol())
            throw new IllegalArgumentException("Can't set both a name expression and idCols");

        _object.setNameExpression(expression);
    }

    @Override
    public void setAliquotNameExpression(String expression)
    {
        _object.setAliquotNameExpression(expression);
    }

    @Override
    public String getNameExpression()
    {
        return _object.getNameExpression();
    }

    @Override
    public boolean hasNameExpression()
    {
        return _object.getNameExpression() != null;
    }

    @Override
    public String getAliquotNameExpression()
    {
        return _object.getAliquotNameExpression();
    }

    @Override
    public boolean hasAliquotNameExpression()
    {
        return _object.getAliquotNameExpression() != null;
    }

    // NOTE: intentionally not public in ExpSampleType interface
    public void setLabelColor(String labelColor)
    {
        _object.setLabelColor(labelColor);
    }

    @Override
    public @Nullable String getLabelColor()
    {
        return _object.getLabelColor();
    }

    // NOTE: intentionally not public in ExpSampleType interface
    public void setMetricUnit(String metricUnit)
    {
        _object.setMetricUnit(metricUnit);
    }

    @Override
    public @Nullable String getMetricUnit()
    {
        return _object.getMetricUnit();
    }

    public void setAutoLinkTargetContainer(Container autoLinkTargetContainerId)
    {
        _object.setAutoLinkTargetContainer(autoLinkTargetContainerId);
    }

    @Override
    public @Nullable Container getAutoLinkTargetContainer()
    {
        return _object.getAutoLinkTargetContainer();
    }

    public void setAutoLinkCategory(String autoLinkCategory)
    {
        _object.setAutoLinkCategory(autoLinkCategory);
    }

    @Override
    public @Nullable String getAutoLinkCategory()
    {
        return _object.getAutoLinkCategory();
    }

    @Override
    public void setCategory(String category)
    {
        _object.setCategory(category);
    }

    @Override
    public @Nullable String getCategory()
    {
        return _object.getCategory();
    }

    @NotNull
    private NameGenerator createNameGenerator(@NotNull String expr, @Nullable Container dataContainer, @Nullable User user, boolean skipMaxSampleCounterFunction/* used by ExperimentStressTest only to avoid deadlock */)
    {
        Map<String, String> importAliasMap = null;
        try
        {
            importAliasMap = getImportAliases();
        }
        catch (IOException e)
        {
            // do nothing
        }

        Container sampleTypeContainer = getContainer();
        Container nameGenContainer = dataContainer != null ? dataContainer : sampleTypeContainer;

        User user_ = user == null ? User.getSearchUser() : user;
        ContainerFilter cf = null;

        // Issue 46939: Naming Patterns for Not Working in Sub Projects
        if (dataContainer != null && dataContainer.hasProductFolders())
            cf = new ContainerFilter.CurrentPlusProjectAndShared(dataContainer, user_); // use lookup CF

        TableInfo parentTable = QueryService.get().getUserSchema(user_, nameGenContainer, SamplesSchema.SCHEMA_NAME).getTable(getName(), cf);

        return new NameGenerator(expr, parentTable, true, importAliasMap, nameGenContainer, skipMaxSampleCounterFunction ? null : getMaxSampleCounterFunction(), SAMPLE_COUNTER_SEQ_PREFIX + getRowId() + "-", false, null, null, !skipMaxSampleCounterFunction);
    }

    @Nullable
    public NameGenerator getNameGenerator(Container dataContainer, @Nullable User user)
    {
        return getNameGenerator(dataContainer, user, false);
    }

    @Nullable
    public NameGenerator getNameGenerator(Container dataContainer, @Nullable User user, boolean skipMaxSampleCounterFunction)
    {
        if (_nameGen == null)
        {
            String s = null;
            if (_object.getNameExpression() != null)
            {
                s = _object.getNameExpression();
            }
            else if (hasNameAsIdCol())
            {
                s = "${name}";
            }
            else if (hasIdColumns())
            {
                List<DomainProperty> idCols = getIdCols();
                StringBuilder expr = new StringBuilder();
                String sep = "";
                for (DomainProperty dp : idCols)
                {
                    expr.append(sep).append("${").append(dp.getName()).append("}");
                    sep = "-";
                }
                s = expr.toString();
            }

            if (s != null)
                _nameGen = createNameGenerator(s, dataContainer, user, skipMaxSampleCounterFunction);
        }

        return _nameGen;
    }

    @NotNull
    public NameGenerator getAliquotNameGenerator(Container dataContainer, @Nullable User user)
    {
        return getAliquotNameGenerator(dataContainer, user, false);
    }

    @NotNull
    public NameGenerator getAliquotNameGenerator(Container dataContainer, @Nullable User user, boolean skipDuplicateCheck)
    {
        if (_aliquotNameGen == null)
        {
            String s;
            if (_object.getAliquotNameExpression() != null)
                s = _object.getAliquotNameExpression();
            else
                s = ALIQUOT_NAME_EXPRESSION;

            _aliquotNameGen = createNameGenerator(s, dataContainer, user, skipDuplicateCheck);
        }

        return _aliquotNameGen;
    }

    /*
     * Check name expression and aliquot name expression for '${genId:minValue(100)}' syntax to return the specified startInd for genId
     */
    public long getMinGenId()
    {
        return getMinCounterValue(NameGenerator.EntityCounter.genId);
    }

    private long getMinCounterValue(NameGenerator.EntityCounter type)
    {
        long nameMin = NameGenerator.getCounterStartValue(_object.getNameExpression(), type);
        long aliquotNameMin = NameGenerator.getCounterStartValue(_object.getAliquotNameExpression(), type);
        return Math.max(nameMin, aliquotNameMin);
    }

    public long getMinSampleCounter()
    {
        return getMinCounterValue(NameGenerator.EntityCounter.sampleCount);
    }

    public long getMinRootSampleCounter()
    {
        return getMinCounterValue(NameGenerator.EntityCounter.rootSampleCount);
    }

    @Override
    public void createSampleNames(@NotNull List<Map<String, Object>> maps,
                                  @Nullable StringExpressionFactory.FieldKeyStringExpression expr,
                                  @Nullable Set<ExpData> parentDatas,
                                  @Nullable Set<ExpMaterial> parentSamples,
                                  boolean skipDuplicates)
            throws ExperimentException
    {
        NameGenerator nameGen;
        User user = User.getSearchUser();
        if (expr != null)
        {
            TableInfo parentTable = QueryService.get().getUserSchema(user, getContainer(), SamplesSchema.SCHEMA_NAME).getTable(getName());
            nameGen = new NameGenerator(expr, parentTable, getContainer());
        }
        else
        {
            nameGen = getNameGenerator(getContainer(), user);
            if (nameGen == null)
                throw new ExperimentException("Error creating name expression generator");
        }

        try (NameGenerator.State state = nameGen.createState(true))
        {
            DbSequence sequence = genIdSequence();
            Supplier<Map<String, Object>> extraPropsFn = () -> Map.of("genId", sequence.next());
            nameGen.generateNames(state, maps, parentDatas, parentSamples, List.of(extraPropsFn), skipDuplicates);
            state.cleanUp();
        }
        catch (NameGenerator.DuplicateNameException dup)
        {
            throw new ExperimentException("Duplicate name '" + dup.getName() + "' on row " + dup.getRowNumber(), dup);
        }
        catch (NameGenerator.NameGenerationException e)
        {
            // Failed to generate a name due to some part of the expression not in the row
            if (hasNameExpression())
                throw new ExperimentException("Failed to generate name for sample on row " + e.getRowNumber(), e);
            else if (hasNameAsIdCol())
                throw new ExperimentException("SampleID or Name is required for sample on row " + e.getRowNumber(), e);
            else
                throw new ExperimentException("All id columns are required for sample on row " + e.getRowNumber(), e);
        }
    }

    @Override
    public String createSampleName(@NotNull Map<String, Object> rowMap) throws ExperimentException
    {
        return createSampleName(rowMap, null, null, null);
    }

    @Override
    public String createSampleName(@NotNull Map<String, Object> rowMap,
                                   @Nullable Set<ExpData> parentDatas,
                                   @Nullable Set<ExpMaterial> parentSamples,
                                   @Nullable Container container)
            throws ExperimentException
    {
        return createSampleName(rowMap, parentDatas, parentSamples, container, null);
    }

    @Override
    public String createSampleName(@NotNull Map<String, Object> rowMap,
                                   @Nullable Set<ExpData> parentDatas,
                                   @Nullable Set<ExpMaterial> parentSamples,
                                   @Nullable Container container,
                                   @Nullable User user)
            throws ExperimentException
    {
        NameGenerator nameGen = getNameGenerator(container, user);
        if (nameGen == null)
            throw new ExperimentException("Error creating name expression generator");

        try (NameGenerator.State state = nameGen.createState(true))
        {
            DbSequence sequence = genIdSequence();
            Supplier<Map<String, Object>> extraPropsFn = () -> {
                Map<String, Object> map = new HashMap<>();
                map.put("genId", sequence.next());
                if (container != null)
                    map.put(NameExpressionOptionService.FOLDER_PREFIX_TOKEN, StringUtils.trimToEmpty(NameExpressionOptionService.get().getExpressionPrefix(container)));
                return map;
            };
            String generatedName = nameGen.generateName(state, rowMap, parentDatas, parentSamples, List.of(extraPropsFn));
            state.cleanUp();
            return generatedName;
        }
        catch (NameGenerator.NameGenerationException e)
        {
            throw new ExperimentException("Failed to generate name for Sample", e);
        }
    }

    private Container getGenIdSequenceContainer()
    {
        // use DBSeq at project level to avoid duplicate genId for samples in child folders
        Container container = getContainer();
        if (container.isProject() || container.getProject() == null)
            return container;
        return container.getProject();
    }

    // The DbSequence used to generate the ${genId} column values
    public DbSequence genIdSequence()
    {
        long minGenId = getMinGenId();
        DbSequence seq = DbSequenceManager.getPreallocatingSequence(getGenIdSequenceContainer(), SEQUENCE_PREFIX, getRowId(), 100);
        if (minGenId > 1)
            seq.ensureMinimum(minGenId - 1);
        return seq;
    }

    @Override
    public long getCurrentGenId()
    {
        Container container = getGenIdSequenceContainer();
        Integer seqRowId = DbSequenceManager.getRowId(container, SEQUENCE_PREFIX, getRowId());
        if (null == seqRowId)
            return 0;

        DbSequence seq = DbSequenceManager.getPreallocatingSequence(container, SEQUENCE_PREFIX, getRowId(), 0);
        return seq.current();
    }

    @Override
    public void ensureMinGenId(long newSeqValue) throws ExperimentException
    {
        Container container = getGenIdSequenceContainer();
        DbSequence seq = DbSequenceManager.getPreallocatingSequence(container, SEQUENCE_PREFIX, getRowId(), 0);
        long current = seq.current();
        if (newSeqValue < current)
        {
            if (hasData())
                throw new ExperimentException("Unable to set genId to " + newSeqValue + " due to conflict with existing samples.");

            seq.setSequenceValue(newSeqValue);
            DbSequenceManager.invalidatePreallocatingSequence(container, SEQUENCE_PREFIX, getRowId());
        }
        else
        {
            seq.ensureMinimum(newSeqValue);
            DbSequenceManager.invalidatePreallocatingSequence(container, SEQUENCE_PREFIX, getRowId());
        }
    }

    @Override
    public boolean hasData()
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("CpasType"), getLSID());
        return new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), Collections.singleton("CpasType"), filter, null).exists();
    }

    @Override
    public void setIdCol1(String s)
    {
        ensureUnlocked();
        _object.setIdCol1(s);
    }

    @Override
    public void setDescription(String s)
    {
        ensureUnlocked();
        _object.setDescription(s);
    }

    @Override
    public void setMaterialLSIDPrefix(String s)
    {
        ensureUnlocked();
        _object.setMaterialLSIDPrefix(s);
    }

    @Override
    public List<ExpMaterialImpl> getSamples(Container c)
    {
        return getSamples(c, null);
    }

    @Override
    public List<ExpMaterialImpl> getSamples(Container c, @Nullable ContainerFilter cf)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), getLSID());
        if (cf != null)
            filter.addCondition(cf.createFilterClause(ExperimentServiceImpl.getExpSchema(), FieldKey.fromParts("Container")));

        return ExperimentServiceImpl.get().getExpMaterials(filter, new Sort("Name"));
    }

    @Override
    public long getSamplesCount(Container c, @Nullable ContainerFilter cf)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), getLSID());
        if (cf != null)
            filter.addCondition(cf.createFilterClause(ExperimentServiceImpl.getExpSchema(), FieldKey.fromParts("Container")));

        TableInfo tInfo = ExperimentServiceImpl.get().getTinfoMaterial();
        return new TableSelector(tInfo, tInfo.getPkColumns(), filter, null).getRowCount();
    }

    @Override
    public ExpMaterialImpl getSample(Container c, String name)
    {
        return getSample(c, name, null);
    }

    public ExpMaterialImpl getSample(Container c, String name, @Nullable ContainerFilter cf)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("CpasType"), getLSID());
        filter.addCondition(FieldKey.fromParts("Name"), name);
        if (cf != null)
            filter.addCondition(cf.createFilterClause(ExperimentServiceImpl.getExpSchema(), FieldKey.fromParts("Container")));

        return ExperimentServiceImpl.get().getExpMaterial(filter);
    }

    private ExpMaterialImpl getSampleByObjectId(Integer objectId)
    {
        return ExperimentServiceImpl.get().getExpMaterial(new SimpleFilter(FieldKey.fromParts("ObjectId"), objectId));
    }

    @Override
    public ExpMaterial getEffectiveSample(Container c, String name, Date effectiveDate, @Nullable ContainerFilter cf)
    {
        Integer legacyObjectId = ExperimentService.get().getObjectIdWithLegacyName(name, ExperimentServiceImpl.getNamespacePrefix(ExpMaterial.class), effectiveDate, c, cf);
        if (legacyObjectId != null)
            return getSampleByObjectId(legacyObjectId);

        ExpMaterial material = getSample(c, name, cf);
        if (material != null && material.getCreated().compareTo(effectiveDate) <= 0)
            return material;

        return null;
    }

    @Override
    @NotNull
    public Domain getDomain()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (_domain == null)
            {
                _domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    _domain.save(null);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw UnexpectedException.wrap(e);
                }
            }
        }
        return _domain;
    }

    public ExpProtocol[] getProtocols(User user)
    {
        TableInfo tinfoProtocol = ExperimentServiceImpl.get().getTinfoProtocol();
        ColumnInfo colLSID = tinfoProtocol.getColumn("LSID");
        ColumnInfo colSampleLSID = new PropertyColumn(ExperimentProperty.SampleTypeLSID.getPropertyDescriptor(), colLSID, getContainer(), user, false);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(colSampleLSID, getLSID());
        List<ColumnInfo> selectColumns = new ArrayList<>(tinfoProtocol.getColumns());
        selectColumns.add(colSampleLSID);
        Protocol[] protocols = new TableSelector(tinfoProtocol, selectColumns, filter, null).getArray(Protocol.class);
        ExpProtocol[] ret = new ExpProtocol[protocols.length];
        for (int i = 0; i < protocols.length; i ++)
        {
            ret[i] = new ExpProtocolImpl(protocols[i]);
        }
        return ret;
    }

    public void onSamplesChanged(User user, List<Material> materials, SampleTypeServiceImpl.SampleChangeType reason)
    {
        SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(this, reason);

        ExpProtocol[] protocols = getProtocols(user);
        if (protocols.length != 0)
        {
            List<ExpMaterialImpl> expMaterials = null;

            if (materials != null)
            {
                expMaterials = new ArrayList<>(materials.size());
                for (Material material : materials)
                {
                    expMaterials.add(new ExpMaterialImpl(material));
                }
            }
            for (ExpProtocol protocol : protocols)
            {
                ProtocolImplementation impl = protocol.getImplementation();
                if (impl == null)
                    continue;
                impl.onSamplesChanged(user, protocol, expMaterials);
            }
        }
    }


    @Override
    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    @Override
    public void save(User user)
    {
        save(user, false);
    }

    public void save(User user, boolean skipCleanUpTasks /* index and cache might have been called explicitly in a postcommit task*/)
    {
        boolean isNew = _object.getRowId() == 0;

        //Issue 51024: When materialLSIDprefix is set via XAR, naming collisions can happen
        if (isNew)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("MaterialLSIDPrefix"), getMaterialLSIDPrefix());
            if (new TableSelector(ExperimentServiceImpl.get().getTinfoSampleType(), filter, null).exists())
                throw new RuntimeException("Duplicate 'MaterialLSIDPrefix' found: " + getMaterialLSIDPrefix());
        }

        save(user, ExperimentServiceImpl.get().getTinfoSampleType(), true);
        if (isNew)
        {
            Domain domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (domain == null)
            {
                domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    domain.save(user);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw UnexpectedException.wrap(e);
                }
            }
        }

        if (!skipCleanUpTasks)
        {
            // NOTE cacheMaterialSource() of course calls transactioncache.put(), which does not alter the shared cache! (BUG?)
            // Just call uncache(), and let normal cache loading do its thing
            SampleTypeServiceImpl.get().clearMaterialSourceCache(getContainer());

            SampleTypeServiceImpl.get().indexSampleType(this);
        }
    }

    @Override
    @Deprecated // Prefer to use the version that provides an audit comment
    public void delete(User user)
    {
        delete(user, null);
    }

    @Override
    public void delete(User user, String auditUserComment)
    {
        try
        {
            SampleTypeService.get().deleteSampleType(getRowId(), getContainer(), user, auditUserComment);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeValidationException(e);
        }
    }

    public TableInfo getTinfo()
    {
        Domain d = getDomain();
        DomainKind<?> dk = d.getDomainKind();
        if (null == dk || null == dk.getStorageSchemaName())
            return null;
        return StorageProvisioner.createTableInfo(d);
    }

    @Override
    public Lsid.LsidBuilder generateSampleLSID()
    {
        return new Lsid.LsidBuilder(this.getDataObject().getMaterialLSIDPrefix() + MATERIAL_LSID_SUFFIX);
    }

    @Override
    public Lsid.LsidBuilder generateNextDBSeqLSID()
    {
        String dbSeqStr = String.valueOf(getSampleLsidDbSeq(1, getContainer()).next());
        return new Lsid.LsidBuilder(this.getDataObject().getMaterialLSIDPrefix() + dbSeqStr);
    }

    public DbSequence getSampleLsidDbSeq(int batchSize, Container container)
    {
        return ExperimentServiceImpl.getLsidPrefixDbSeq(container, "SampleType-" + getRowId(), batchSize);
    }

    @Override
    public String toString()
    {
        return "SampleType " + getName() + " in " + getContainer().getPath();
    }

    public void index(SearchService.IndexTask task)
    {
        // Big hack to prevent study specimens from being indexed as part of sample types
        // Check needed on restart, as all documents are enumerated.
        if (StudyService.SPECIMEN_NAMESPACE_PREFIX.equals(getLSIDNamespacePrefix()))
        {
            return;
        }
        if (task == null)
        {
            SearchService ss = SearchService.get();
            if (null == ss)
                return;
            task = ss.defaultTask();
        }

        final SearchService.IndexTask indexTask = task;
        final ExpSampleTypeImpl me = this;
        indexTask.addRunnable(
                () -> me.indexSampleType(indexTask)
                , SearchService.PRIORITY.bulk
        );
    }

    private void indexSampleType(SearchService.IndexTask indexTask)
    {
        ExperimentUrls urlProvider = PageFlowUtil.urlProvider(ExperimentUrls.class);
        ActionURL url = null;

        if (urlProvider != null)
        {
            url = urlProvider.getShowSampleTypeURL(this);
            url.setExtraPath(getContainer().getId());
        }

        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();

        // Name is identifier with the highest weight
        identifiersHi.add(getName());

        if (isMedia())
            props.put(SearchService.PROPERTY.categories.toString(), mediaSearchCategory.toString());
        else
            props.put(SearchService.PROPERTY.categories.toString(), searchCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), "Sample Type - " + getName());
        props.put(SearchService.PROPERTY.summary.toString(), getDescription());

        props.put(SearchService.PROPERTY.keywordsLo.toString(), "Sample Type");      // Treat the words "Sample Type" as a low priority keyword
        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));

        String body = StringUtils.isNotBlank(getDescription()) ? getDescription() : "";
        SimpleDocumentResource sdr = new SimpleDocumentResource(new Path(getDocumentId()), getDocumentId(),
                getContainer().getId(), "text/plain",
                body, url,
                getCreatedBy(), getCreated(),
                getModifiedBy(), getModified(),
                props)
        {
            @Override
            public void setLastIndexed(long ms, long modified)
            {
                ExperimentServiceImpl.get().setMaterialSourceLastIndexed(getRowId(), ms);
            }
        };

        indexTask.addResource(sdr, SearchService.PRIORITY.item);
    }

    public String getDocumentId()
    {
        return categoryName + ":" + getRowId();
    }

    @Contract("null -> new")
    private @NotNull Map<String, Map<String, Object>> getImportAliases(MaterialSource ms) throws IOException
    {
        if (ms == null || StringUtils.isBlank(ms.getMaterialParentImportAliasMap()))
            return Collections.emptyMap();

        try
        {
            return ExperimentJSONConverter.parseImportAliases(ms.getMaterialParentImportAliasMap());
        }
        catch (IOException e)
        {
            throw new IOException(String.format("Failed to parse MaterialSource [%1$s] import alias json", ms.getRowId()), e);
        }
    }

    public String getImportAliasJson()
    {
        return _object.getMaterialParentImportAliasMap();
    }

    @Override
    public @NotNull Map<String, String> getImportAliases() throws IOException
    {
        Map<String, String> aliases = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : getImportAliasMap().entrySet())
        {
            aliases.put(entry.getKey(), (String) entry.getValue().get("inputType"));
        }
        return Collections.unmodifiableMap(aliases);
    }

    @Override
    public @NotNull Map<String, String> getRequiredImportAliases() throws IOException
    {
        Map<String, String> aliases = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : getImportAliasMap().entrySet())
        {
            if ((Boolean) entry.getValue().get("required"))
                aliases.put(entry.getKey(), (String) entry.getValue().get("inputType"));
        }
        return Collections.unmodifiableMap(aliases);
    }

    @Override
    public @NotNull Map<String, Map<String, Object>> getImportAliasMap() throws IOException
    {
        return Collections.unmodifiableMap(getImportAliases(_object));
    }

    @Override
    public void setImportAliasMap(Map<String, Map<String, Object>> aliasMap)
    {
        setImportAliasMapJson(ExperimentJSONConverter.getAliasJson(aliasMap, _object.getName()));
    }

    public void setImportAliasMapJson(String aliasJon)
    {
        _object.setMaterialParentImportAliasMap(aliasJon);
    }

    @Override
    public Function<String, Long> getMaxSampleCounterFunction()
    {
        return getMaxCounterWithPrefixFunction(ExperimentServiceImpl.get().getTinfoMaterial());
    }

    @Override
    public boolean isMedia()
    {
        return ExpSchema.SampleTypeCategoryType.media.name().equalsIgnoreCase(getCategory());
    }
}
