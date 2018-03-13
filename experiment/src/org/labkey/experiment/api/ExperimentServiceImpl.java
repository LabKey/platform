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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.AbstractParameter;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.LsidType;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialRunInput;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunAttachmentParent;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpDataClassTable;
import org.labkey.api.exp.query.ExpDataInputTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.ExpRunGroupMapTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSampleSetTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.exp.xar.XarConstants;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JdbcUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.Tuple3;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.ExperimentAuditProvider;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExportType;
import org.labkey.experiment.XarReader;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.pipeline.ExpGeneratorHelper;
import org.labkey.experiment.pipeline.ExperimentPipelineJob;
import org.labkey.experiment.pipeline.MoveRunsPipelineJob;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.labkey.api.data.CompareType.IN;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTCOMMIT;
import static org.labkey.api.exp.OntologyManager.getTinfoObject;
import static org.labkey.api.exp.query.ExpSchema.NestedSchemas.materials;

public class ExperimentServiceImpl implements ExperimentService
{
    private static final Logger LOG = Logger.getLogger(ExperimentServiceImpl.class);

    private DatabaseCache<MaterialSource> materialSourceCache;
    private StringKeyCache<Protocol> protocolCache;

    private final StringKeyCache<SortedSet<DataClass>> dataClassCache = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "DataClass", (containerId, argument) ->
    {
        Container c = ContainerManager.getForId(containerId);
        if (c == null)
            return Collections.emptySortedSet();

        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        return Collections.unmodifiableSortedSet(new TreeSet<>(new TableSelector(getTinfoDataClass(), filter, null).getCollection(DataClass.class)));
    });

    public static final String EXPERIMENTAL_LEGACY_LINEAGE = "legacy-lineage";
    public static final String DEFAULT_MATERIAL_SOURCE_NAME = "Unspecified";

    private List<ExperimentRunTypeSource> _runTypeSources = new CopyOnWriteArrayList<>();
    private Set<ExperimentDataHandler> _dataHandlers = new HashSet<>();
    protected Map<String, DataType> _dataTypes = new HashMap<>();
    protected Map<String, ProtocolImplementation> _protocolImplementations = new HashMap<>();

    private static final List<ExperimentListener> _listeners = new CopyOnWriteArrayList<>();

    private static final ReentrantLock XAR_IMPORT_LOCK = new ReentrantLock();

    synchronized DatabaseCache<MaterialSource> getMaterialSourceCache()
    {
        if (materialSourceCache == null)
        {
            materialSourceCache = new DatabaseCache<>(getExpSchema().getScope(), CacheManager.UNLIMITED, CacheManager.HOUR, "Material source");
        }
        return materialSourceCache;
    }

    StringKeyCache<SortedSet<DataClass>> getDataClassCache()
    {
        return dataClassCache;
    }

    public void clearDataClassCache(@Nullable Container c)
    {
        if (c == null)
            dataClassCache.clear();
        else
            dataClassCache.remove(c.getId());
    }

    synchronized StringKeyCache<Protocol> getProtocolCache()
    {
        if (protocolCache == null)
        {
            protocolCache = new DatabaseCache<>(getExpSchema().getScope(), CacheManager.UNLIMITED, CacheManager.HOUR, "Protocol");
        }
        return protocolCache;
    }

    public ExpRunImpl getExpRun(int rowid)
    {
        SimpleFilter filter = new SimpleFilter().addCondition(FieldKey.fromParts(ExpRunTable.Column.RowId.name()), rowid);
        ExperimentRun run = new TableSelector(getTinfoExperimentRun(), filter, null).getObject(ExperimentRun.class);
        return run == null ? null : new ExpRunImpl(run);
    }

    private List<ExpRunImpl> getExpRuns(Collection<Integer> rowids)
    {
        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts(ExpRunTable.Column.RowId.name()), rowids);
        TableSelector selector = new TableSelector(getTinfoExperimentRun(), filter, null);

        final List<ExpRunImpl> runs = new ArrayList<>(rowids.size());
        selector.forEach(run -> runs.add(new ExpRunImpl(run)), ExperimentRun.class);

        return runs;
    }

    public ReentrantLock getProtocolImportLock()
    {
        return XAR_IMPORT_LOCK;
    }

    public HttpView createRunExportView(Container container, String defaultFilenamePrefix)
    {
        ActionURL postURL = new ActionURL(ExperimentController.ExportRunsAction.class, container);
        return new JspView<>("/org/labkey/experiment/XARExportOptions.jsp", new ExperimentController.ExportBean(LSIDRelativizer.FOLDER_RELATIVE, XarExportType.BROWSER_DOWNLOAD, defaultFilenamePrefix + ".xar", new ExperimentController.ExportOptionsForm(), null, postURL));
    }

    public HttpView createFileExportView(Container container, String defaultFilenamePrefix)
    {
        Set<String> roles = getDataInputRoles(container, ContainerFilter.CURRENT);
        // Remove case-only dupes
        Set<String> dedupedRoles = new CaseInsensitiveHashSet();
        roles.removeIf(role -> !dedupedRoles.add(role));

        ActionURL postURL = new ActionURL(ExperimentController.ExportRunFilesAction.class, container);
        return new JspView<>("/org/labkey/experiment/fileExportOptions.jsp", new ExperimentController.ExportBean(LSIDRelativizer.FOLDER_RELATIVE, XarExportType.BROWSER_DOWNLOAD, defaultFilenamePrefix + ".zip", new ExperimentController.ExportOptionsForm(), roles, postURL));
    }

    public void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String comment)
    {
        Container c = run != null ? run.getContainer() : protocol.getContainer();
        ExperimentAuditProvider.ExperimentAuditEvent event = new ExperimentAuditProvider.ExperimentAuditEvent(c.getId(), comment);

        event.setProjectId(c.getProject() == null ? null : c.getProject().getId());
        if (runGroup != null)
            event.setRunGroup(runGroup.getRowId());
        event.setProtocolLsid(protocol.getLSID());
        if (run != null)
            event.setRunLsid(run.getLSID());
        event.setProtocolRun(ExperimentAuditProvider.getKey3(protocol, run));

        AuditLogService.get().addEvent(user, event);
    }

    @Override
    public List<ExpExperimentImpl> getMatchingBatches(String name, Container container, ExpProtocol protocol)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Name"), name);
        filter.addCondition(FieldKey.fromParts("BatchProtocolId"), protocol.getRowId());
        Experiment[] experiment = new TableSelector(getTinfoExperiment(), filter, null).getArray(Experiment.class);
        return ExpExperimentImpl.fromExperiments(experiment);
    }

    @Override
    public List<ExpProtocolImpl> getExpProtocolsUsedByRuns(Container c, ContainerFilter containerFilter)
    {
        // Get the Protocol LSIDs out instead of doing a DISTINCT on exp.Protocol.* since SQLServer can't do DISTINCT
        // on ntext fields
        SQLFragment sql = new SQLFragment("SELECT DISTINCT er.ProtocolLSID FROM ");
        sql.append(getTinfoExperimentRun(), "er");
        sql.append(" WHERE ");
        sql.append(containerFilter.getSQLFragment(getSchema(), new SQLFragment("er.Container"), c));

        // Translate the LSIDs into protocol objects
        List<ExpProtocolImpl> result = new ArrayList<>();
        for (String protocolLSID : new SqlSelector(getSchema(), sql).getArrayList(String.class))
        {
            result.add(getExpProtocol(protocolLSID));
        }
        return result;
    }

    @Nullable
    @Override
    public ExperimentRunType getExperimentRunType(@NotNull String description, @NotNull Container container)
    {
        for (ExperimentRunTypeSource runTypeSource : _runTypeSources)
        {
            for (ExperimentRunType experimentRunType : runTypeSource.getExperimentRunTypes(container))
            {
                if (description.equalsIgnoreCase(experimentRunType.getDescription()))
                {
                    return experimentRunType;
                }
            }
        }
        return null;
    }

    public ExpRunImpl getExpRun(String lsid)
    {
        ExperimentRun run = getExperimentRun(lsid);
        if (run == null)
            return null;
        return new ExpRunImpl(run);
    }

    public List<ExpRunImpl> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol)
    {
        SQLFragment sql = new SQLFragment(" SELECT ER.* "
                + " FROM exp.ExperimentRun ER "
                + " WHERE ER.Container = ? ");
        sql.add(container.getId());
        if (parentProtocol != null)
        {
            sql.append("\nAND ER.ProtocolLSID = ?");
            sql.add(parentProtocol.getLSID());
        }
        if (childProtocol != null)
        {
            sql.append("\nAND ER.RowId IN (SELECT PA.RunId "
                    + " FROM exp.ProtocolApplication PA "
                    + " WHERE PA.ProtocolLSID = ? ) ");
            sql.add(childProtocol.getLSID());
        }
        return ExpRunImpl.fromRuns(new SqlSelector(getSchema(), sql).getArrayList(ExperimentRun.class));
    }

    public List<ExpRunImpl> getExpRunsForJobId(int jobId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("jobid"), jobId);
        return ExpRunImpl.fromRuns(new TableSelector(getTinfoExperimentRun(), filter, null).getArrayList(ExperimentRun.class));
    }

    public List<ExpRunImpl> getExpRunsForFilePathRoot(File filePathRoot)
    {
        String path = filePathRoot.getAbsolutePath();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("filepathroot"), path);
        return ExpRunImpl.fromRuns(new TableSelector(getTinfoExperimentRun(), filter, null).getArrayList(ExperimentRun.class));
    }

    public ExpRunImpl createExperimentRun(Container container, String name)
    {
        ExperimentRun run = new ExperimentRun();
        run.setName(name);
        run.setLSID(generateGuidLSID(container, "Run"));
        run.setContainer(container);
        return new ExpRunImpl(run);
    }

    public ExpDataImpl getExpData(int rowid)
    {
        Data data = new TableSelector(getTinfoData(), new SimpleFilter(FieldKey.fromParts("RowId"), rowid), null).getObject(Data.class);
        if (data == null)
            return null;
        return new ExpDataImpl(data);
    }

    public ExpDataImpl getExpData(String lsid)
    {
        Data data = new TableSelector(getTinfoData(), new SimpleFilter(FieldKey.fromParts("LSID"), lsid), null).getObject(Data.class);
        if (data == null)
            return null;
        return new ExpDataImpl(data);
    }

    public List<ExpDataImpl> getExpDatas(int... rowids)
    {
        if (rowids.length == 0)
            return null;
        Collection<Integer> ids = new ArrayList<>(rowids.length);
        for (int rowid : rowids)
            ids.add(rowid);
        return getExpDatas(ids);
    }

    public List<ExpDataImpl> getExpDatasByLSID(Collection<String> lsids)
    {
        if (lsids.size() == 0)
            return null;
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), new SimpleFilter(FieldKey.fromParts("LSID"), lsids, IN), null).getArrayList(Data.class));
    }

    public List<ExpDataImpl> getExpDatas(Collection<Integer> rowids)
    {
        if (rowids.size() == 0)
            return null;
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), new SimpleFilter(FieldKey.fromParts("RowId"), rowids, IN), null).getArrayList(Data.class));
    }

    public List<ExpDataImpl> getExpDatas(Container container, @Nullable DataType type, @Nullable String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (type != null)
            filter.addWhereClause(Lsid.namespaceFilter("LSID", type.getNamespacePrefix()), null);
        if (name != null)
            filter.addCondition(FieldKey.fromParts("Name"), name);
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), filter, null).getArrayList(Data.class));
    }

    public ExpDataImpl createData(URI uri, XarSource source) throws XarFormatException
    {
        // Check if it's in the database already
        ExpDataImpl data = getExpDataByURL(FileUtil.getPath(source.getXarContext().getContainer(), uri), source.getXarContext().getContainer());
        if (data == null)
        {
            // Have to make a new one
            String pathStr = FileUtil.uriToString(uri);
            String[] parts = pathStr.split("/");
            String name = FileUtil.decodeSpaces(parts[parts.length - 1]);
            Path path = FileUtil.getPath(source.getXarContext().getContainer(), uri);

            if (path != null)
            {
                try
                {
                    path = FileUtil.stringToPath(source.getXarContext().getContainer(),
                            source.getCanonicalDataFileURL(FileUtil.pathToString(path)));
                    pathStr = FileUtil.relativizeUnix(source.getRootPath(), path, false);
                }
                catch (IOException e)
                {
                    pathStr = FileUtil.pathToString(path);
                }
            }
            else
            {
                pathStr = FileUtil.uriToString(uri);
            }

            Lsid.LsidBuilder lsid = new Lsid.LsidBuilder(LsidUtils.resolveLsidFromTemplate(AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION, source.getXarContext(), "Data", new AutoFileLSIDReplacer(pathStr, source.getXarContext().getContainer(), source)));
            int version = 1;
            do
            {
                data = getExpData(lsid.toString());
                if (data != null)
                {
                    lsid.setVersion(Integer.toString(++version));
                }
            }
            while (data != null);

            data = createData(source.getXarContext().getContainer(), name, lsid.toString());
            data.setDataFileURI(uri);
            data.save(source.getXarContext().getUser());
        }
        return data;
    }

    public ExpDataImpl createData(Container container, @NotNull DataType type)
    {
        Lsid lsid = new Lsid(generateGuidLSID(container, type));
        return createData(container, lsid.getObjectId(), lsid.toString());
    }

    public ExpDataImpl createData(Container container, @NotNull DataType type, @NotNull String name)
    {
        return createData(container, type, name, false);
    }

    public ExpDataImpl createData(Container container, @NotNull DataType type, @NotNull String name, boolean generated)
    {
        return createData(container, name, generateLSID(container, type, name), generated);
    }

    public ExpDataImpl createData(Container container, String name, String lsid)
    {
        return createData(container, name, lsid, false);
    }

    public ExpDataImpl createData(Container container, String name, String lsid, boolean generated)
    {
        Data data = new Data();
        data.setLSID(lsid);
        data.setName(name);
        data.setCpasType(ExpData.DEFAULT_CPAS_TYPE);
        data.setContainer(container);
        data.setGenerated(generated);
        return new ExpDataImpl(data);
    }

    @NotNull
    public List<ExpMaterialImpl> getExpMaterialsByName(String name, Container container, User user)
    {
        List<ExpMaterialImpl> result = getSamplesByName(container, user).get(name);
        return result == null ? Collections.emptyList() : result;
    }

    public ExpMaterialImpl getExpMaterial(int rowid)
    {
        Material material = new TableSelector(getTinfoMaterial()).getObject(rowid, Material.class);
        return material == null ? null : new ExpMaterialImpl(material);
    }

    @NotNull
    public List<ExpMaterialImpl> getExpMaterials(Collection<Integer> rowids)
    {
        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts(ExpMaterialTable.Column.RowId.name()), rowids);
        TableSelector selector = new TableSelector(getTinfoMaterial(), filter, null);

        final List<ExpMaterialImpl> materials = new ArrayList<>(rowids.size());
        selector.forEach(material -> materials.add(new ExpMaterialImpl(material)), Material.class);

        return materials;
    }

    @Override
    @NotNull
    public List<ExpMaterialImpl> getExpMaterials(Container container, User user, Set<String> sampleNames, @Nullable ExpSampleSet sampleSet, boolean throwIfMissing, boolean createIfMissing)
            throws ExperimentException
    {
        if (throwIfMissing && createIfMissing)
            throw new IllegalArgumentException("Either throwIfMissing or createIfMissing can be true; not both.");

        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("Name"), sampleNames);
        if (sampleSet != null)
            filter.addCondition(FieldKey.fromParts("CpasType"), sampleSet.getLSID());

        // SampleSet may live in different container
        ContainerFilter.CurrentPlusProjectAndShared containerFilter = new ContainerFilter.CurrentPlusProjectAndShared(user);
        SimpleFilter.FilterClause clause = containerFilter.createFilterClause(getSchema(), FieldKey.fromParts("Container"), container);
        filter.addClause(clause);

        Set<String> selectNames = new LinkedHashSet<>();
        selectNames.add("Name");
        selectNames.add("RowId");
        TableSelector sampleTableSelector = new TableSelector(getTinfoMaterial(), selectNames, filter, null);
        Map<String, Integer> sampleMap = sampleTableSelector.getValueMap();

        List<ExpMaterialImpl> resolvedSamples = getExpMaterials(sampleMap.values());

        if (sampleMap.size() < sampleNames.size())
        {
            Set<String> missingSamples = new HashSet<>(sampleNames);
            missingSamples.removeAll(sampleMap.keySet());
            if (throwIfMissing)
                throw new ExperimentException("No samples found for: " + StringUtils.join(missingSamples, ", "));

            if (createIfMissing)
                resolvedSamples.addAll(createExpMaterials(container, user, sampleSet, missingSamples));
        }

        return resolvedSamples;
    }

    // Insert new materials into the given sample set or the active sample set.
    private List<ExpMaterialImpl> createExpMaterials(Container container, User user, @Nullable ExpSampleSet sampleSet, Set<String> sampleNames)
            throws ExperimentException
    {
        List<ExpMaterialImpl> materials = new ArrayList<>(sampleNames.size());

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            if (sampleSet == null)
                sampleSet = ensureActiveSampleSet(container, user, true);

            // Create materials directly using Name.
            for (String name : sampleNames)
            {
                List<ExpMaterialImpl> existingMaterials = getExpMaterialsByName(name, container, user);
                if (existingMaterials.size() > 0)
                {
                    ExpMaterialImpl material = existingMaterials.get(0);
                    materials.add(material);
                }
                else
                {
                    Lsid.LsidBuilder lsid = new Lsid.LsidBuilder(sampleSet.getMaterialLSIDPrefix() + "test");
                    lsid.setObjectId(name);
                    String materialLsid = lsid.toString();

                    ExpMaterialImpl material = createExpMaterial(container, materialLsid, name);
                    material.setCpasType(sampleSet.getLSID());
                    material.save(user);

                    materials.add(material);
                }
            }

            transaction.commit();
            return materials;
        }
    }

    public ExpMaterialImpl createExpMaterial(Container container, Lsid lsid)
    {
        ExpMaterialImpl result = new ExpMaterialImpl(new Material());
        result.setContainer(container);
        result.setLSID(lsid);
        result.setName(lsid.getObjectId());
        return result;
    }

    public ExpMaterialImpl createExpMaterial(Container container, String lsid, String name)
    {
        ExpMaterialImpl result = new ExpMaterialImpl(new Material());
        result.setContainer(container);
        result.setLSID(lsid);
        result.setName(name);
        if (!name.equals(new Lsid(lsid).getObjectId()))
            throw new IllegalArgumentException("name=" + name + " lsid=" + lsid);
        return result;
    }

    public ExpMaterialImpl getExpMaterial(String lsid)
    {
        Material result = new TableSelector(getTinfoMaterial(), new SimpleFilter(FieldKey.fromParts("LSID"), lsid), null).getObject(Material.class);
        return result == null ? null : new ExpMaterialImpl(result);
    }

    public List<ExpMaterialImpl> getIndexableMaterials(Container container, @Nullable Date modifiedSince)
    {
        // Big hack to prevent indexing study specimens. Also in ExpMaterialImpl.index()
        SQLFragment sql = new SQLFragment("SELECT * FROM " + getTinfoMaterial() + " WHERE Container = ? AND LSID NOT LIKE '%:"
                + StudyService.SPECIMEN_NAMESPACE_PREFIX + "%'");
        sql.add(container.getId());
        SQLFragment modifiedSQL = new SearchService.LastIndexedClause(getTinfoMaterial(), modifiedSince, null).toSQLFragment(null, null);
        if (!modifiedSQL.isEmpty())
            sql.append(" AND ").append(modifiedSQL);
        return ExpMaterialImpl.fromMaterials(new SqlSelector(getSchema(), sql).getArrayList(Material.class));
    }

    public List<ExpDataImpl> getIndexableData(Container container, @Nullable Date modifiedSince)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM " + getTinfoData() + " WHERE Container = ? AND classId IS NOT NULL");
        sql.add(container.getId());
        SQLFragment modifiedSQL = new SearchService.LastIndexedClause(getTinfoData(), modifiedSince, null).toSQLFragment(null, null);
        if (!modifiedSQL.isEmpty())
            sql.append(" AND ").append(modifiedSQL);
        return ExpDataImpl.fromDatas(new SqlSelector(getSchema(), sql).getArrayList(Data.class));
    }

    public void setDataLastIndexed(int rowId, long ms)
    {
        setLastIndexed(getTinfoData(), rowId, ms);
    }

    public void setMaterialLastIndexed(int rowId, long ms)
    {
        setLastIndexed(getTinfoMaterial(), rowId, ms);
    }

    private void setLastIndexed(TableInfo table, int rowId, long ms)
    {
        new SqlExecutor(getSchema()).execute("UPDATE " + table + " SET LastIndexed = ? WHERE RowId = ?",
                new Timestamp(ms), rowId);
    }

    public void indexDataClass(ExpDataClass dataClass)
    {
        SearchService ss = SearchService.get();
        if (ss == null)
            return;

        SearchService.IndexTask task = ss.defaultTask();

        Domain d = dataClass.getDomain();
        if (d == null)
            return; // Domain may be null if the DataClass has been deleted

        TableInfo table = ((ExpDataClassImpl)dataClass).getTinfo();
        if (table == null)
            return;

        Runnable r = () -> {
            // Index all ExpData that have never been indexed OR where either the ExpDataClass definition or ExpData itself has changed since last indexed
            SQLFragment sql = new SQLFragment()
                    .append("SELECT * FROM ").append(getTinfoData(), "d")
                    .append(", ").append(table, "t")
                    .append(" WHERE t.lsid = d.lsid")
                    .append(" AND d.classId = ?").add(dataClass.getRowId())
                    .append(" AND (d.lastIndexed IS NULL OR d.lastIndexed < ? OR (d.modified IS NOT NULL AND d.lastIndexed < d.modified))")
                    .add(dataClass.getModified());

            new SqlSelector(table.getSchema().getScope(), sql).forEachBatch(batch -> {
                for (Data data : batch)
                {
                    ExpDataImpl impl = new ExpDataImpl(data);
                    impl.index(task);
                }
            }, Data.class, 1000);
        };

        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }

    public void indexSampleSet(ExpSampleSet sampleSet)
    {
        SearchService ss = SearchService.get();
        if (ss == null)
            return;

        SearchService.IndexTask task = ss.defaultTask();

        Runnable r = () -> {
            // Index all ExpMaterial that have never been indexed OR where either the ExpSampleSet definition or ExpMaterial itself has changed since last indexed
            SQLFragment sql = new SQLFragment("SELECT * FROM ")
                    .append(getTinfoMaterial(), "m")
                    .append(" WHERE m.LSID NOT LIKE '%:").append(StudyService.SPECIMEN_NAMESPACE_PREFIX).append("%'")
                    .append(" AND m.cpasType = ?").add(sampleSet.getLSID())
                    .append(" AND (m.lastIndexed IS NULL OR m.lastIndexed < ? OR (m.modified IS NOT NULL AND m.lastIndexed < m.modified))")
                    .add(sampleSet.getModified());

            new SqlSelector(getSchema().getScope(), sql).forEachBatch(batch -> {
                for (Material m : batch)
                {
                    ExpMaterialImpl impl = new ExpMaterialImpl(m);
                    impl.index(task);
                }
            }, Material.class, 1000);
        };

        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }


    private static final MaterialSource MISS_MARKER = new MaterialSource();

    @Nullable
    public ExpSampleSetImpl getSampleSet(int rowId)
    {
        MaterialSource ms = getMaterialSourceCache().get("ROWID/" + rowId);

        if (null == ms)
        {
            ms = new TableSelector(getTinfoMaterialSource()).getObject(rowId, MaterialSource.class);

            if (null == ms)
            {
                getMaterialSourceCache().put("ROWID/" + rowId, MISS_MARKER);
                return null;
            }
            cacheMaterialSource(ms);
        }

        if (MISS_MARKER == ms)
            return null;

        return new ExpSampleSetImpl(ms);
    }

    @Nullable
    public ExpSampleSetImpl getSampleSet(String lsid)
    {
        MaterialSource ms = getMaterialSourceCache().get(getCacheKey(lsid));

        if (null == ms)
        {
            ms = getMaterialSource(lsid);

            if (null == ms)
            {
                getMaterialSourceCache().put(getCacheKey(lsid), MISS_MARKER);
                return null;
            }
            cacheMaterialSource(ms);
        }

        if (MISS_MARKER == ms)
            return null;

        return new ExpSampleSetImpl(ms);
    }


    void cacheMaterialSource(MaterialSource ms)
    {
        DatabaseCache<MaterialSource> c = getMaterialSourceCache();
        c.put("ROWID/" + ms.getRowId(), ms);
        c.put(getCacheKey(ms.getLSID()), ms);
    }


    public Map<String, ExpSampleSet> getSampleSetsForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT mi.Role, MAX(m.CpasType) AS SampleSetLSID, COUNT (DISTINCT m.CpasType) AS SampleSetCount FROM ");
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
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("r.Container"), container));
        sql.append(" GROUP BY mi.Role ORDER BY mi.Role");

        Map<String, Object>[] queryResults = new SqlSelector(getSchema(), sql).getMapArray();
        Map<String, ExpSampleSet> lsidToSampleSet = new HashMap<>();
        ExpSampleSet defaultSampleSet = lookupActiveSampleSet(container);
        if (defaultSampleSet != null)
        {
            lsidToSampleSet.put(defaultSampleSet.getLSID(), defaultSampleSet);
        }

        Map<String, ExpSampleSet> result = new LinkedHashMap<>();
        for (Map<String, Object> queryResult : queryResults)
        {
            ExpSampleSet sampleSet = null;
            Number sampleSetCount = (Number) queryResult.get("SampleSetCount");
            if (sampleSetCount.intValue() == 1)
            {
                String sampleSetLSID = (String) queryResult.get("SampleSetLSID");
                if (!lsidToSampleSet.containsKey(sampleSetLSID))
                {
                    sampleSet = getSampleSet(sampleSetLSID);
                    lsidToSampleSet.put(sampleSetLSID, sampleSet);
                }
                else
                {
                    sampleSet = lsidToSampleSet.get(sampleSetLSID);
                }
            }
            if (sampleSet == null)
            {
                sampleSet = defaultSampleSet;
            }
            result.put((String) queryResult.get("Role"), sampleSet);
        }
        return result;
    }

    public List<ExpSampleSetImpl> getSampleSets(Container container, User user, boolean includeOtherContainers)
    {
        SimpleFilter filter = createContainerFilter(container, user, includeOtherContainers);
        List<MaterialSource> sources = new TableSelector(getTinfoMaterialSource(), filter, null).getArrayList(MaterialSource.class);
        List<ExpSampleSetImpl> result = new ArrayList<>(sources.size());
        for (MaterialSource source : sources)
        {
            result.add(new ExpSampleSetImpl(source));
        }
        // Do the sort on the Java side to make sure it's always case-insensitive, even on Postgres
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public ExpExperimentImpl getExpExperiment(int rowid)
    {
        Experiment experiment = new TableSelector(getTinfoExperiment()).getObject(rowid, Experiment.class);
        if (null != experiment)
        {
            return new ExpExperimentImpl(experiment);
        }
        return null;
    }

    public ExpExperimentImpl createExpExperiment(Container container, String name)
    {
        Experiment exp = new Experiment();
        exp.setContainer(container);
        exp.setName(name);
        exp.setLSID(generateLSID(container, ExpExperiment.class, name));
        return new ExpExperimentImpl(exp);
    }

    public ExpExperiment getExpExperiment(String lsid)
    {
        Experiment experiment =
                new TableSelector(getTinfoExperiment(), new SimpleFilter(FieldKey.fromParts("LSID"), lsid), null).getObject(Experiment.class);
        return experiment == null ? null : new ExpExperimentImpl(experiment);
    }


    public ExpProtocolImpl getExpProtocol(int rowid)
    {
        return getExpProtocol(rowid, true);
    }

    public ExpProtocolImpl getExpProtocol(int rowid, boolean useCache)
    {
        Protocol result;

        if (useCache)
        {
            result = getProtocolCache().get("ROWID/" + rowid);
            if (null != result)
                return new ExpProtocolImpl(result);
        }

        result = new TableSelector(getTinfoProtocol(), new SimpleFilter(FieldKey.fromParts("RowId"), rowid), null).getObject(Protocol.class);

        if (null != result && useCache)
        {
            cacheProtocol(result);
        }

        return result == null ? null : new ExpProtocolImpl(result);
    }


    public ExpProtocolImpl getExpProtocol(String lsid)
    {
        return getExpProtocol(lsid, true);
    }

    public ExpProtocolImpl getExpProtocol(String lsid, boolean useCache)
    {
        Protocol result;

        if (useCache)
        {
            result = getProtocolCache().get(getCacheKey(lsid));
            if (null != result)
                return new ExpProtocolImpl(result);
        }

        result = new TableSelector(getTinfoProtocol(), new SimpleFilter(FieldKey.fromParts("LSID"), lsid), null).getObject(Protocol.class);

        if (null != result && useCache)
        {
            cacheProtocol(result);
        }

        return result == null ? null : new ExpProtocolImpl(result);
    }

    private void cacheProtocol(Protocol p)
    {
        StringKeyCache<Protocol> c = getProtocolCache();
        c.put(getCacheKey(p.getLSID()), p);
        c.put("ROWID/" + p.getRowId(), p);
    }

    private void uncacheProtocol(Protocol p)
    {
        StringKeyCache<Protocol> c = getProtocolCache();
        c.remove(getCacheKey(p.getLSID()));
        c.remove("ROWID/" + p.getRowId());
        //TODO I don't think we're using a DbCache for protocols...
        DbCache.remove(getTinfoProtocol(), getCacheKey(p.getLSID()));
    }

    protected void uncacheMaterialSource(MaterialSource ms)
    {
        StringKeyCache<MaterialSource> c = getMaterialSourceCache();
        c.remove(getCacheKey(ms.getLSID()));
        c.remove("ROWID/" + ms.getRowId());
    }


    public ExpProtocolImpl getExpProtocol(Container container, String name)
    {
        return getExpProtocol(generateLSID(container, ExpProtocol.class, name));
    }


    public ExpProtocolImpl createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name)
    {
        return createExpProtocol(container, type, name, generateLSID(container, ExpProtocol.class, name));
    }

    public ExpProtocolImpl createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name, String lsid)
    {
        ExpProtocolImpl existing = getExpProtocol(lsid);
        if (existing != null)
        {
            throw new IllegalArgumentException("Protocol " + existing.getLSID() + " already exists.");
        }
        Protocol protocol = new Protocol();
        protocol.setName(name);
        protocol.setLSID(lsid);
        protocol.setContainer(container);
        protocol.setApplicationType(type.toString());
        protocol.setOutputDataType(ExpData.DEFAULT_CPAS_TYPE);
        protocol.setOutputMaterialType("Material");
        return new ExpProtocolImpl(protocol);
    }

    public ExpRunTable createRunTable(String name, UserSchema schema)
    {
        return new ExpRunTableImpl(name, schema);
    }

    public ExpRunGroupMapTable createRunGroupMapTable(String name, UserSchema schema)
    {
        return new ExpRunGroupMapTableImpl(name, schema);
    }

    public ExpDataTable createDataTable(String name, UserSchema schema)
    {
        return new ExpDataTableImpl(name, schema);
    }

    public ExpDataInputTable createDataInputTable(String name, ExpSchema expSchema)
    {
        return new ExpDataInputTableImpl(name, expSchema);
    }

    public ExpSampleSetTable createSampleSetTable(String name, UserSchema schema)
    {
        return new ExpSampleSetTableImpl(name, schema);
    }

    @Override
    public ExpDataClassTable createDataClassTable(String name, UserSchema schema)
    {
        return new ExpDataClassTableImpl(name, schema);
    }

    public ExpProtocolTableImpl createProtocolTable(String name, UserSchema schema)
    {
        return new ExpProtocolTableImpl(name, schema);
    }

    public ExpExperimentTableImpl createExperimentTable(String name, UserSchema schema)
    {
        return new ExpExperimentTableImpl(name, schema);
    }

    public ExpMaterialTable createMaterialTable(String name, UserSchema schema)
    {
        return new ExpMaterialTableImpl(name, schema);
    }

    @Override
    public ExpDataClassDataTable createDataClassDataTable(String name, UserSchema schema, @NotNull ExpDataClass dataClass)
    {
        return new ExpDataClassDataTableImpl(name, schema, (ExpDataClassImpl)dataClass);
    }

    public ExpMaterialInputTable createMaterialInputTable(String name, ExpSchema schema)
    {
        return new ExpMaterialInputTableImpl(name, schema);
    }

    public ExpProtocolApplicationTable createProtocolApplicationTable(String name, UserSchema schema)
    {
        return new ExpProtocolApplicationTableImpl(name, schema);
    }

    @Override
    public ExpQCFlagTableImpl createQCFlagsTable(String name, UserSchema schema)
    {
        return new ExpQCFlagTableImpl(name, schema);
    }

    public ExpDataTable createFilesTable(String name, UserSchema schema)
    {
        return new ExpFilesTableImpl(name, schema);
    }

    private String getNamespacePrefix(Class<? extends ExpObject> clazz)
    {
        if (clazz == ExpData.class)
            return "Data";
        if (clazz == ExpMaterial.class)
            return "Material";
        if (clazz == ExpProtocol.class)
            return "Protocol";
        if (clazz == ExpRun.class)
            return "Run";
        if (clazz == ExpExperiment.class)
            return "Experiment";
        if (clazz == ExpSampleSet.class)
            return "SampleSet";
        if (clazz == ExpDataClass.class)
            return ExpDataClassImpl.NAMESPACE_PREFIX;
        if (clazz == ExpProtocolApplication.class)
            return "ProtocolApplication";
        throw new IllegalArgumentException("Invalid class " + clazz.getName());
    }

    private String generateGuidLSID(Container container, String lsidPrefix)
    {
        return generateLSID(container, lsidPrefix, GUID.makeGUID());
    }

    private String generateLSID(Container container, String lsidPrefix, String objectName)
    {
        return new Lsid(lsidPrefix, "Folder-" + container.getRowId(), objectName).toString();
    }

    public String generateGuidLSID(Container container, Class<? extends ExpObject> clazz)
    {
        return generateGuidLSID(container, getNamespacePrefix(clazz));
    }

    public String generateGuidLSID(Container container, DataType type)
    {
        return generateGuidLSID(container, type.getNamespacePrefix());
    }

    public String generateLSID(Container container, Class<? extends ExpObject> clazz, @NotNull String name)
    {
        if (clazz == ExpSampleSet.class && name.equals(DEFAULT_MATERIAL_SOURCE_NAME) && ContainerManager.getSharedContainer().equals(container))
            return getDefaultSampleSetLsid();
        return generateLSID(container, getNamespacePrefix(clazz), name);
    }

    public String generateLSID(@NotNull Container container, @NotNull DataType type, @NotNull String name)
    {
        return generateLSID(container, type.getNamespacePrefix(), name);
    }

    @Nullable
    public ExpObject findObjectFromLSID(String lsid)
    {
        Identifiable id = LsidManager.get().getObject(lsid);
        if (id instanceof ExpObject)
        {
            return (ExpObject)id;
        }
        return null;
    }

    public ExpSampleSetImpl getSampleSet(@NotNull Container container, @NotNull String name)
    {
        return getSampleSet(generateLSID(container, ExpSampleSet.class, name));
    }

    public ExpSampleSetImpl getSampleSet(Container c, String name, boolean includeOtherContainers)
    {
        ExpSampleSetImpl ss = getSampleSet(c, name);
        if (ss == null && !c.isProject() && c.getProject() != null)
            ss = getSampleSet(c.getProject(), name);
        if (ss == null && !c.equals(ContainerManager.getSharedContainer()))
            ss = getSampleSet(ContainerManager.getSharedContainer(), name);
        return ss;
    }

    public ExpSampleSetImpl lookupActiveSampleSet(Container container)
    {
        MaterialSource materialSource = lookupActiveMaterialSource(container);
        if (materialSource == null)
        {
            return null;
        }
        return new ExpSampleSetImpl(materialSource);
    }

    public void setActiveSampleSet(Container container, ExpSampleSet sampleSet)
    {
        String materialSourceLSID = sampleSet.getLSID();
        MaterialSource current = lookupActiveMaterialSource(container);
        if (current == null)
        {
            if (materialSourceLSID == null)
            {
                // No current value, no new value
                return;
            }
            else
            {
                // No current value, so need to insert a new row
                String sql = "INSERT INTO " + getTinfoActiveMaterialSource() + " (Container, MaterialSourceLSID) " +
                    "VALUES (?, ?)";
                new SqlExecutor(getExpSchema()).execute(sql, container.getId(), materialSourceLSID);
            }
        }
        else
        {
            if (materialSourceLSID == null)
            {
                // Current value exists, needs to be deleted
                String sql = "DELETE FROM " + getTinfoActiveMaterialSource() + " WHERE Container = ?";
                new SqlExecutor(getExpSchema()).execute(sql, container);
            }
            else
            {
                // Current value exists, needs to be changed
                String sql = "UPDATE " + getTinfoActiveMaterialSource() + " SET MaterialSourceLSID = ? WHERE Container = ?";
                new SqlExecutor(getExpSchema()).execute(sql, materialSourceLSID, container);
            }
        }
    }

    @Override
    public List<ExpDataClassImpl> getDataClasses(Container container, User user, boolean includeOtherContainers)
    {
        SortedSet<DataClass> classes = new TreeSet<>();
        List<String> containerIds = createContainerList(container, user, includeOtherContainers);
        for (String containerId : containerIds)
        {
            SortedSet<DataClass> dataClasses = getDataClassCache().get(containerId);
            classes.addAll(dataClasses);
        }

        // Do the sort on the Java side to make sure it's always case-insensitive, even on Postgres
        return Collections.unmodifiableList(classes.stream().map(ExpDataClassImpl::new).sorted().collect(toList()));
    }

    @Override
    public ExpDataClassImpl getDataClass(Container c, String dataClassName)
    {
        return getDataClass(c, null, dataClassName, false);
    }

    @Override
    public ExpDataClassImpl getDataClass(Container c, @NotNull User user, String dataClassName)
    {
        return getDataClass(c, user, dataClassName, true);
    }

    private ExpDataClassImpl getDataClass(Container c, @Nullable User user, String dataClassName, boolean includeOtherContainers)
    {
        List<String> containerIds = createContainerList(c, user, includeOtherContainers);
        for (String containerId : containerIds)
        {
            Collection<DataClass> dataClasses = getDataClassCache().get(containerId);
            for (DataClass dataClass : dataClasses)
            {
                if (dataClass.getName().equalsIgnoreCase(dataClassName))
                    return new ExpDataClassImpl(dataClass);
            }
        }

        return null;
    }

    // TODO: add container parameter and use getDataClassCache()
    @Override
    public ExpDataClassImpl getDataClass(int rowId)
    {
        DataClass dataClass = new TableSelector(getTinfoDataClass()).getObject(rowId, DataClass.class);
        if (dataClass == null)
            return null;

        return new ExpDataClassImpl(dataClass);
    }

    // TODO: add container parameter and use getDataClassCache()
    @Override
    public ExpDataClassImpl getDataClass(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        DataClass dataClass = new TableSelector(getTinfoDataClass(), filter, null).getObject(DataClass.class);
        if (dataClass == null)
            return null;

        return new ExpDataClassImpl(dataClass);
    }

    @Override
    public List<? extends ExpData> getExpDatas(ExpDataClass dataClass)
    {
        Domain d = dataClass.getDomain();
        if (d == null)
            throw new IllegalStateException("No domain for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        TableInfo table = ((ExpDataClassImpl)dataClass).getTinfo();
        if (table == null)
            throw new IllegalStateException("No table for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        SQLFragment sql = new SQLFragment()
                .append("SELECT * FROM ").append(getTinfoData(), "d")
                .append(", ").append(table, "t")
                .append(" WHERE t.lsid = d.lsid")
                .append(" AND d.classId = ?").add(dataClass.getRowId());

        List<Data> datas = new SqlSelector(table.getSchema().getScope(), sql).getArrayList(Data.class);

        return datas.stream().map(ExpDataImpl::new).collect(toList());
    }

    @Override
    public ExpData getExpData(ExpDataClass dataClass, String name)
    {
        Domain d = dataClass.getDomain();
        if (d == null)
            throw new IllegalStateException("No domain for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        TableInfo table = ((ExpDataClassImpl)dataClass).getTinfo();
        if (table == null)
            throw new IllegalStateException("No table for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        SQLFragment sql = new SQLFragment()
                .append("SELECT * FROM ").append(getTinfoData(), "d")
                .append(", ").append(table, "t")
                .append(" WHERE t.lsid = d.lsid")
                .append(" AND d.classId = ?").add(dataClass.getRowId())
                .append(" AND d.Name = ?").add(name);

        Data data = new SqlSelector(table.getSchema().getScope(), sql).getObject(Data.class);

        return data == null ? null : new ExpDataImpl(data);
    }

    @Override
    public ExpDataImpl getExpData(ExpDataClass dataClass, int rowId)
    {
        Domain d = dataClass.getDomain();
        if (d == null)
            throw new IllegalStateException("No domain for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        TableInfo table = ((ExpDataClassImpl)dataClass).getTinfo();
        if (table == null)
            throw new IllegalStateException("No table for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        SQLFragment sql = new SQLFragment()
                .append("SELECT * FROM ").append(getTinfoData(), "d")
                .append(", ").append(table, "t")
                .append(" WHERE t.lsid = d.lsid")
                .append(" AND d.classId = ?").add(dataClass.getRowId())
                .append(" AND d.rowId = ?").add(rowId);

        Data data = new SqlSelector(table.getSchema().getScope(), sql).getObject(Data.class);

        return data == null ? null : new ExpDataImpl(data);
    }

    public ExpExperiment createHiddenRunGroup(Container container, User user, ExpRun... runs)
    {
        if (runs.length == 0)
        {
            return null;
        }

        // Try to find an existing run group with the same set of runs.
        // An identical group will have the same total run count, and the same total count when the runs
        // are restricted to just the runs of interest
        SQLFragment sql = new SQLFragment("SELECT E.* FROM ");
        sql.append(getTinfoExperiment(), "E");
        sql.append(", (SELECT ExperimentId, COUNT(ExperimentRunId) AS C FROM ");
        sql.append(getTinfoRunList(), "RL");
        sql.append(" WHERE ExperimentRunId ");
        List<Integer> rowIds = new ArrayList<>();
        for (ExpRun run : runs)
        {
            rowIds.add(run.getRowId());
        }
        getExpSchema().getScope().getSqlDialect().appendInClauseSql(sql, rowIds);
        sql.append(" GROUP BY ExperimentId) IncludedRuns, ");
        sql.append("(SELECT ExperimentId, COUNT(ExperimentRunId) AS C FROM ");
        sql.append(getTinfoRunList(), "RL2");
        sql.append(" GROUP BY ExperimentId) AllRuns ");
        sql.append(" WHERE IncludedRuns.C = ? AND AllRuns.C = ? AND ");
        sql.append(" E.RowId = AllRuns.ExperimentId AND E.RowId = IncludedRuns.ExperimentId AND E.Container = ? AND E.Hidden = ?");
        sql.add(runs.length);
        sql.add(runs.length);
        sql.add(container);
        sql.add(Boolean.TRUE);

        List<Experiment> exp = new SqlSelector(getSchema(), sql).getArrayList(Experiment.class);
        if (!exp.isEmpty())
        {
            // We don't care which one we use. It's possible to have multiple matches if a run was deleted that was
            // already part of a hidden run group.
            return new ExpExperimentImpl(exp.get(0));
        }
        else
        {
            ExpExperimentImpl result = createExpExperiment(container, GUID.makeGUID());
            result.setHidden(true);
            result.save(user);
            for (ExpRun run : runs)
            {
                result.addRuns(user, run);
            }
            return result;
        }
    }

    public DbScope.Transaction ensureTransaction()
    {
        return getExpSchema().getScope().ensureTransaction();
    }

    public ExperimentRunListView createExperimentRunWebPart(ViewContext context, ExperimentRunType type)
    {
        ExperimentRunListView view = ExperimentRunListView.createView(context, type, true);
        view.setShowDeleteButton(true);
        view.setShowAddToRunGroupButton(true);
        view.setShowMoveRunsButton(true);
        if (type == ExperimentRunType.ALL_RUNS_TYPE)
        {
            view.setShowUploadAssayRunsButton(true);
        }
        view.setTitle("Experiment Runs");
        ActionURL url = new ActionURL(ExperimentController.ShowRunsAction.class, context.getContainer());
        url.addParameter("experimentRunFilter", type.getDescription());
        view.setTitleHref(url);
        return view;
    }

    public DbSchema getSchema()
    {
        return getExpSchema();
    }

    public List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException
    {
        XarReader reader = new XarReader(source, pipelineJob);
        reader.parseAndLoad(reloadExistingRuns);
        return reader.getExperimentRuns();
    }

    public ExpRun importRun(PipelineJob job, XarSource source) throws PipelineJobException, ValidationException
    {
        return ExpGeneratorHelper.insertRun(job, source, null);
    }

    public Set<String> getDataInputRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType... types)
    {
        return getInputRoles(container, filter, getTinfoDataInput(), types);
    }

    public Set<String> getMaterialInputRoles(Container container, ExpProtocol.ApplicationType... types)
    {
        return getInputRoles(container, ContainerFilter.Type.Current.create(null), getTinfoMaterialInput(), types);
    }

    private Set<String> getInputRoles(Container container, ContainerFilter filter, TableInfo table, ExpProtocol.ApplicationType... types)
    {
        SQLFragment sql = new SQLFragment("SELECT role FROM ");
        sql.append(table, "t");
        sql.append(" WHERE targetapplicationid IN (SELECT pa.rowid FROM ");
        sql.append(getTinfoProtocolApplication(), "pa");
        if (types != null && types.length > 0)
        {
            sql.append(", ");
            sql.append(getTinfoProtocol(), "p");
            sql.append(" WHERE p.lsid = pa.protocollsid AND p.applicationtype ");
            List<String> typeNames = new ArrayList<>(types.length);
            for (ExpProtocol.ApplicationType type : types)
            {
                typeNames.add(type.toString());
            }
            getExpSchema().getSqlDialect().appendInClauseSql(sql, typeNames);
            sql.append(" AND ");
        }
        else
        {
            sql.append(" WHERE ");
        }
        sql.append(" pa.runid IN (SELECT rowid FROM ");
        sql.append(getTinfoExperimentRun(), "er");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("Container"), container));
        sql.append("))");
        return new TreeSet<>(new SqlSelector(getSchema(), sql).getCollection(String.class));
    }

    @Override
    @Nullable
    public ExpDataRunInputImpl getDataInput(Lsid lsid)
    {
        String namespace = lsid.getNamespace();
        if (!DataInput.NAMESPACE.equals(namespace))
            return null;

        String objectId = lsid.getObjectId();
        if (objectId == null || objectId.length() == 0)
            return null;

        String[] parts = StringUtils.split(objectId, ".");
        if (parts.length == 0 || parts.length > 2)
            return null;

        int dataId = NumberUtils.toInt(parts[0]);
        int targetApplicationId = NumberUtils.toInt(parts[1]);
        return getDataInput(dataId, targetApplicationId);
    }

    @Override
    @Nullable
    public ExpDataRunInputImpl getDataInput(int dataId, int targetProtocolApplicationId)
    {
        TableInfo inputTable = ExperimentServiceImpl.get().getTinfoDataInput();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("dataId"), dataId);
        filter.addCondition(FieldKey.fromParts("targetApplicationId"), targetProtocolApplicationId);
        DataInput di = new TableSelector(inputTable, TableSelector.ALL_COLUMNS, filter, null).getObject(DataInput.class);
        if (di == null)
            return null;

        return new ExpDataRunInputImpl(di);
    }

    @Override
    @Nullable
    public ExpMaterialRunInputImpl getMaterialInput(Lsid lsid)
    {
        String namespace = lsid.getNamespace();
        if (!MaterialInput.NAMESPACE.equals(namespace))
            return null;

        String objectId = lsid.getObjectId();
        if (objectId == null || objectId.length() == 0)
            return null;

        String[] parts = StringUtils.split(objectId, ".");
        if (parts.length == 0 || parts.length > 2)
            return null;

        int materialId = NumberUtils.toInt(parts[0]);
        int targetApplicationId = NumberUtils.toInt(parts[1]);
        return getMaterialInput(materialId, targetApplicationId);
    }

    @Override
    @Nullable
    public ExpMaterialRunInputImpl getMaterialInput(int materialId, int targetProtocolApplicationId)
    {
        TableInfo inputTable = ExperimentServiceImpl.get().getTinfoMaterialInput();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("materialId"), materialId);
        filter.addCondition(FieldKey.fromParts("targetApplicationId"), targetProtocolApplicationId);
        MaterialInput mi = new TableSelector(inputTable, TableSelector.ALL_COLUMNS, filter, null).getObject(MaterialInput.class);
        if (mi == null)
            return null;

        return new ExpMaterialRunInputImpl(mi);
    }

    @Override
    public Pair<Set<ExpData>, Set<ExpMaterial>> getParents(ExpProtocolOutput start)
    {
        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_LEGACY_LINEAGE))
        {
            return getParentsOldAndBusted(start);
        }

        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_LINEAGE_PERFORMANCE))
        {
            Pair<Set<ExpData>, Set<ExpMaterial>> veryNewHotness = getParentsVeryNewHotness(start);

            Pair<Set<ExpData>, Set<ExpMaterial>> newHotness = null;
            assert null != (newHotness = getParentsNewHotness(start));
            assert assertLineage(start, veryNewHotness, newHotness);

            Pair<Set<ExpData>, Set<ExpMaterial>> oldAndBusted = null;
            assert null != (oldAndBusted = getParentsOldAndBusted(start));
            assert assertLineage(start, veryNewHotness, oldAndBusted);

            return veryNewHotness;
        }
        else
        {
            Pair<Set<ExpData>, Set<ExpMaterial>> newHotness   = getParentsNewHotness(start);

            Pair<Set<ExpData>, Set<ExpMaterial>> oldAndBusted = null;
            assert null != (oldAndBusted = getParentsOldAndBusted(start));
            assert assertLineage(start, newHotness, oldAndBusted);

            return newHotness;
        }
    }

    // Make boolean so it can hide behind 'assert' and no-op in production mode
    private boolean assertLineage(ExpProtocolOutput seed, Pair<Set<ExpData>, Set<ExpMaterial>> newHotness, Pair<Set<ExpData>, Set<ExpMaterial>> oldAndBusted)
    {
        if (newHotness.first.equals(oldAndBusted.first) &&
                newHotness.second.equals(oldAndBusted.second))
            return true; // short-circuit if everything matches

        // when there is a recursive lineage, the old lineage includes the seed but the new lineage doesn't
        if (oldAndBusted.first.contains(seed) || oldAndBusted.second.contains(seed))
        {
            Set<ExpData> recursiveDataCheck = new HashSet<>(oldAndBusted.first);
            recursiveDataCheck.remove(seed);
            Set<ExpMaterial> recursiveMaterialCheck = new HashSet<>(oldAndBusted.second);
            recursiveMaterialCheck.remove(seed);
            if (newHotness.first.equals(recursiveDataCheck) &&
                    newHotness.second.equals(recursiveMaterialCheck))
                return true;
        }

        Set<ExpData> newExpDataUniques = new HashSet<>(newHotness.first);
        Set<ExpData> oldExpDataUniques = new HashSet<>(oldAndBusted.first);
        Set<ExpMaterial> newExpMaterialUniques = new HashSet<>(newHotness.second);
        Set<ExpMaterial> oldExpMaterialUniques = new HashSet<>(oldAndBusted.second);
        newExpDataUniques.removeAll(oldAndBusted.first);
        oldExpDataUniques.removeAll(newHotness.first);
        newExpMaterialUniques.removeAll(oldAndBusted.second);
        oldExpMaterialUniques.removeAll(newHotness.second);

        Set<ExpData> expDataOverlap = new HashSet<>(newHotness.first);
        expDataOverlap.removeAll(newExpDataUniques);
        Set<ExpMaterial> expMaterialOverlap = new HashSet<>(newHotness.second);
        expMaterialOverlap.removeAll(newExpMaterialUniques);

        StringBuilder errorMsg = new StringBuilder("Old lineage doesn't match new lineage for: " + seed);
        if (!newExpDataUniques.isEmpty())
            errorMsg.append("\nOld missing data: ").append(newExpDataUniques.toString());
        if (!oldExpDataUniques.isEmpty())
            errorMsg.append("\nNew missing data: ").append(oldExpDataUniques.toString());
        if (!newExpMaterialUniques.isEmpty())
            errorMsg.append("\nOld missing materials: ").append(newExpMaterialUniques.toString());
        if (!oldExpMaterialUniques.isEmpty())
            errorMsg.append("\nNew missing materials: ").append(oldExpMaterialUniques.toString());
        errorMsg.append("\nMatching Data: ").append(expDataOverlap);
        errorMsg.append("\nMatching Materials: ").append(expMaterialOverlap);
        errorMsg.append("\n");
        assert false : errorMsg.toString();
        return false; // Unreachable
    }

    /**
     * walk experiment graph in memory, with tons queries, mostly repetitive queries over and over again
     **/
    private Pair<Set<ExpData>, Set<ExpMaterial>> getParentsOldAndBusted(ExpProtocolOutput start)
    {
        if (isUnknownMaterial(start))
            return Pair.of(Collections.emptySet(), Collections.emptySet());

        List<ExpRun> runsToInvestigate = new ArrayList<>();
        ExpRun parentRun = start.getRun();
        if (parentRun != null)
            runsToInvestigate.add(parentRun);

        Set<ExpRun> investigatedRuns = new HashSet<>();

        final Set<ExpData> parentData = new HashSet<>();
        final Set<ExpMaterial> parentMaterials = new HashSet<>();
        while (!runsToInvestigate.isEmpty())
        {
            ExpRun predecessorRun = runsToInvestigate.remove(0);
            investigatedRuns.add(predecessorRun);

            for (ExpData d : predecessorRun.getDataInputs().keySet())
            {
                ExpRun dRun = d.getRun();
                if (dRun != null && !investigatedRuns.contains(dRun))
                    runsToInvestigate.add(dRun);

                parentData.add(d);
            }
            for (ExpMaterial m : removeUnknownMaterials(predecessorRun.getMaterialInputs().keySet()))
            {
                ExpRun mRun = m.getRun();
                if (mRun != null && !investigatedRuns.contains(mRun))
                    runsToInvestigate.add(mRun);

                parentMaterials.add(m);
            }
        }
        return Pair.of(parentData, parentMaterials);
    }


    @Override
    public Pair<Set<ExpData>, Set<ExpMaterial>> getChildren(ExpProtocolOutput start)
    {
        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_LEGACY_LINEAGE))
        {
            return getChildrenOldAndBusted(start);
        }

        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_LINEAGE_PERFORMANCE))
        {
            Pair<Set<ExpData>, Set<ExpMaterial>> veryNewHotness = getChildrenVeryNewHotness(start);

            Pair<Set<ExpData>, Set<ExpMaterial>> newHotness = null;
            assert null != (newHotness   = getChildrenNewHotness(start));
            assert assertLineage(start, veryNewHotness, newHotness);

            Pair<Set<ExpData>, Set<ExpMaterial>> oldAndBusted = null;
            assert null != (oldAndBusted = getChildrenOldAndBusted(start));
            assert assertLineage(start, veryNewHotness, oldAndBusted);

            return veryNewHotness;
        }
        else
        {
            Pair<Set<ExpData>, Set<ExpMaterial>> newHotness = getChildrenNewHotness(start);

            Pair<Set<ExpData>, Set<ExpMaterial>> oldAndBusted = null;
            assert null != (oldAndBusted = getChildrenOldAndBusted(start));
            assert assertLineage(start, newHotness, oldAndBusted);

            return newHotness;
        }
    }


    private Pair<Set<ExpData>, Set<ExpMaterial>> getChildrenOldAndBusted(ExpProtocolOutput start)
    {
        if (isUnknownMaterial(start))
            return Pair.of(Collections.emptySet(), Collections.emptySet());

        List<ExpRun> runsToInvestigate = new ArrayList<>();
        if (start instanceof ExpData)
            runsToInvestigate.addAll(ExperimentServiceImpl.get().getRunsUsingDataIds(Arrays.asList(start.getRowId())));
        else if (start instanceof ExpMaterial)
            runsToInvestigate.addAll(ExperimentServiceImpl.get().getRunsUsingMaterials(start.getRowId()));

        runsToInvestigate.remove(start.getRun());
        Set<ExpData> childDatas = new HashSet<>();
        Set<ExpMaterial> childMaterials = new HashSet<>();

        Set<ExpRun> investigatedRuns = new HashSet<>();
        while (!runsToInvestigate.isEmpty())
        {
            ExpRun childRun = runsToInvestigate.remove(0);
            if (!investigatedRuns.contains(childRun))
            {
                investigatedRuns.add(childRun);

                List<ExpMaterial> materialOutputs = removeUnknownMaterials(childRun.getMaterialOutputs());
                childMaterials.addAll(materialOutputs);

                List<ExpData> dataOutputs = childRun.getDataOutputs();
                childDatas.addAll(dataOutputs);

                runsToInvestigate.addAll(ExperimentServiceImpl.get().getRunsUsingMaterials(materialOutputs));
                runsToInvestigate.addAll(ExperimentServiceImpl.get().getRunsUsingDatas(dataOutputs));
            }
        }

        if (start instanceof ExpData)
            childDatas.remove(start);
        else if (start instanceof ExpMaterial)
            childMaterials.remove(start);

        return Pair.of(childDatas, childMaterials);
    }


    /**
     * walk experiment graph with one tricky recursive query
     * <p>
     * <p>
     * TWO BIG PROBLEMS
     * <p>
     * A) Can't mutually recurse between CTE (urg)
     * 2) Can only reference the recursive CTE exactly once (urg)
     * <p>
     * NOTE: when recursing UP:      INNER M.rowid=MI.materialid AND INNER MI.targetapplicationid=PA.rowid\
     * NOTE: when recursing DOWN:    INNER PA.rowid=D.sourceapplicationid AND OUTER M.rowid=MI.materialid
     * <p>
     * NOTE: it is very unfortunately that experiment objects do not have globally unique objectids
     * NOTE: this requires that we join internally on rowid, but globally on lsid...
     * <p>
     * each row in the result represents one 'edge' or 'leaf/root' in the experiment graph, that is to say
     * nodes (material,data,protocolapplication) may appear more than once, but edges shouldn't
     **/
    private Pair<Set<ExpData>, Set<ExpMaterial>> getParentsNewHotness(ExpProtocolOutput start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);

        ExpLineage lineage = getLineage(start, options);
        return Pair.of(lineage.getDatas(), lineage.getMaterials());
    }

    private Pair<Set<ExpData>, Set<ExpMaterial>> getParentsVeryNewHotness(ExpProtocolOutput start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setVeryNewHotness(true);
        options.setChildren(false);

        ExpLineage lineage = getLineage(start, options);
        return Pair.of(lineage.getDatas(), lineage.getMaterials());
    }


    /**
     * walk experiment graph with one tricky recursive query
     **/
    public Pair<Set<ExpData>, Set<ExpMaterial>> getChildrenNewHotness(ExpProtocolOutput start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(false);

        ExpLineage lineage = getLineage(start, options);
        return Pair.of(lineage.getDatas(), lineage.getMaterials());
    }

    public Pair<Set<ExpData>, Set<ExpMaterial>> getChildrenVeryNewHotness(ExpProtocolOutput start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setVeryNewHotness(true);
        options.setParents(false);

        ExpLineage lineage = getLineage(start, options);
        return Pair.of(lineage.getDatas(), lineage.getMaterials());
    }

    @Override
    public Set<ExpMaterial> getRelatedChildSamples(ExpData start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(false);

        ExpLineage lineage = getLineage(start, options);
        return lineage.findRelatedChildSamples();
    }

    @Override
    public Set<ExpData> getNearestParentDatas(ExpMaterial start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);

        ExpLineage lineage = getLineage(start, options);
        return lineage.findNearestParentDatas();
    }


    static final String exp_graph_sql;
    static final String exp_graph_sql_for_lookup;

    static final String exp_graph_sql2;
    static final String exp_graph_sql_for_lookup2;

    static
    {
        try
        {
            String sql = IOUtils.toString(ExperimentServiceImpl.class.getResourceAsStream("ExperimentRunGraph.sql"), "UTF-8");
            if (DbSchema.get("exp", DbSchemaType.Module).getSqlDialect().isPostgreSQL())
                exp_graph_sql = StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", "RECURSIVE"), "$VARCHAR$", "VARCHAR");
            else
                exp_graph_sql = StringUtils.replace(StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", ""), "$VARCHAR$", "NVARCHAR"), "||", "+");

            sql = IOUtils.toString(ExperimentServiceImpl.class.getResourceAsStream("ExperimentRunGraphForLookup.sql"), "UTF-8");
            if (DbSchema.get("exp", DbSchemaType.Module).getSqlDialect().isPostgreSQL())
                exp_graph_sql_for_lookup = StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", "RECURSIVE"), "$VARCHAR$", "VARCHAR");
            else
                exp_graph_sql_for_lookup = StringUtils.replace(StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", ""), "$VARCHAR$", "NVARCHAR"), "||", "+");
        }
        catch (IOException x)
        {
            throw new ConfigurationException("Cannot read file ExperimentRunGraph.sql");
        }
    }

    static
    {
        try
        {
            String sql = IOUtils.toString(ExperimentServiceImpl.class.getResourceAsStream("ExperimentRunGraph2.sql"), "UTF-8");
            if (DbSchema.get("exp", DbSchemaType.Module).getSqlDialect().isPostgreSQL())
                exp_graph_sql2 = StringUtils.replace(StringUtils.replace(sql, "$LSIDTYPE$", "LSIDTYPE"), "$VARCHAR$", "VARCHAR");
            else
                exp_graph_sql2 = StringUtils.replace(StringUtils.replace(StringUtils.replace(sql, "$LSIDTYPE$", "NVARCHAR(300)"), "$VARCHAR$", "NVARCHAR"), "||", "+");

            sql = IOUtils.toString(ExperimentServiceImpl.class.getResourceAsStream("ExperimentRunGraphForLookup2.sql"), "UTF-8");
            if (DbSchema.get("exp", DbSchemaType.Module).getSqlDialect().isPostgreSQL())
                exp_graph_sql_for_lookup2 = StringUtils.replace(StringUtils.replace(sql, "$LSIDTYPE$", "LSIDTYPE"), "$VARCHAR$", "VARCHAR");
            else
                exp_graph_sql_for_lookup2 = StringUtils.replace(StringUtils.replace(StringUtils.replace(sql, "$LSIDTYPE$", "NVARCHAR(300)"), "$VARCHAR$", "NVARCHAR"), "||", "+");
        }
        catch (IOException x)
        {
            throw new ConfigurationException("Cannot read file ExperimentRunGraph.sql");
        }
    }

    public List<ExpRun> oldCollectRunsToInvestigate(ExpProtocolOutput start, ExpLineageOptions options)
    {
        List<ExpRun> runsToInvestigate = new ArrayList<>();
        boolean up = options.isParents();
        boolean down = options.isChildren();

        if (up)
        {
            ExpRun parentRun = start.getRun();
            if (parentRun != null)
                runsToInvestigate.add(parentRun);
        }
        if (down)
        {
            if (start instanceof ExpData)
                runsToInvestigate.addAll(ExperimentServiceImpl.get().getRunsUsingDataIds(Arrays.asList(start.getRowId())));
            else if (start instanceof ExpMaterial)
                runsToInvestigate.addAll(ExperimentServiceImpl.get().getRunsUsingMaterials(start.getRowId()));
            runsToInvestigate.remove(start.getRun());
        }
        return runsToInvestigate;
    }

    // Get lisd of ExpRun LSIDs for the start Data or Material
    public List<String> collectRunsToInvestigate(ExpProtocolOutput start, ExpLineageOptions options)
    {
        Pair<Map<String, String>, Map<String, String>> pair = collectRunsAndRolesToInvestigate(start, options);
        List<String> runLsids = new ArrayList<>(pair.first.size() + pair.second.size());
        runLsids.addAll(pair.first.keySet());
        runLsids.addAll(pair.second.keySet());

        return runLsids;
    }

    // Get up and down maps of ExpRun LSID to Role
    public Pair<Map<String, String>, Map<String, String>> collectRunsAndRolesToInvestigate(ExpProtocolOutput start, ExpLineageOptions options)
    {
        Map<String, String> runsUp = new HashMap<>();
        Map<String, String> runsDown = new HashMap<>();
        boolean up = options.isParents();
        boolean down = options.isChildren();

        ExpRun parentRun = start.getRun();
        if (up)
        {
            if (parentRun != null)
                runsUp.put(parentRun.getLSID(), start instanceof Data ? "Data" : "Material");
        }
        if (down)
        {
            if (start instanceof ExpData)
                runsDown.putAll(flattenPairs(ExperimentServiceImpl.get().getRunsAndRolesUsingDataIds(Arrays.asList(start.getRowId()))));
            else if (start instanceof ExpMaterial)
                runsDown.putAll(flattenPairs(ExperimentServiceImpl.get().getRunsAndRolesUsingMaterialIds(Arrays.asList(start.getRowId()))));

            if (parentRun != null)
                runsDown.remove(parentRun.getLSID());
        }

        assert checkRunsAndRoles(start, options, runsUp, runsDown);

        return Pair.of(runsUp, runsDown);
    }

    // Reduce a list of run LSID and role pairs to a single map
    // Only use this when there is a single input Data or Material.
    private Map<String, String> flattenPairs(List<Pair<String, String>> runsAndRoles)
    {
        Map<String, String> runLsidToRoleMap = new HashMap<>();
        runsAndRoles.forEach(pair -> runLsidToRoleMap.put(pair.first, pair.second));
        return runLsidToRoleMap;
    }

    private boolean checkRunsAndRoles(ExpProtocolOutput start, ExpLineageOptions options, Map<String, String> runsUp, Map<String, String> runsDown)
    {
        Set<ExpRun> runs = new HashSet<>();
        runsUp.keySet().stream().map(this::getExpRun).forEach(runs::add);
        runsDown.keySet().stream().map(this::getExpRun).forEach(runs::add);

        Set<ExpRun> oldRuns = new HashSet<>(oldCollectRunsToInvestigate(start, options));
        if (!runs.equals(oldRuns))
        {
            LOG.warn("Mismatch between collectRunsAndRolesToInvestigate and oldCollectRunsToInvestiate. start: " + start + "\nruns: " + runs + "\nold runs:" + oldRuns);
            return false;
        }
        return true;
    }

    @Override
    public ExpLineage getLineage(ExpProtocolOutput start, ExpLineageOptions options)
    {
        if (isUnknownMaterial(start))
            return new ExpLineage(start);

        List<String> lsids;
        Pair<Map<String, String>, Map<String, String>> pair = collectRunsAndRolesToInvestigate(start, options);
        if (options.isVeryNewHotness())
        {
            lsids = Collections.singletonList(start.getLSID());
        }
        else
        {
            lsids = new ArrayList<>(pair.first.size() + pair.second.size());
            lsids.addAll(pair.first.keySet());
            lsids.addAll(pair.second.keySet());
            if (lsids.isEmpty())
                return new ExpLineage(start);
        }

        SQLFragment sqlf = generateExperimentTreeSQL(lsids, options);
        Set<Integer> dataids = new HashSet<>();
        Set<Integer> materialids = new HashSet<>();
        Set<Integer> runids = new HashSet<>();
        Set<ExpLineage.Edge> edges = new HashSet<>();

        // add edges for initial runs and roles up
        for (Map.Entry<String, String> runAndRole : pair.first.entrySet())
            edges.add(new ExpLineage.Edge(runAndRole.getKey(), start.getLSID(), options.isVeryNewHotness() ? "no role" : runAndRole.getValue()));

        // add edges for initial runs and roles down
        for (Map.Entry<String, String> runAndRole : pair.second.entrySet())
            edges.add(new ExpLineage.Edge(start.getLSID(), runAndRole.getKey(), options.isVeryNewHotness() ? "no role" :runAndRole.getValue()));

        new SqlSelector(getExpSchema(), sqlf).forEachMap((m)->
        {
            String parentLSID = (String)m.get("parent_lsid");
            String childLSID = (String)m.get("child_lsid");

            String parentExpType = (String)m.get("parent_exptype");
            String childExpType = (String)m.get("child_exptype");

            Integer parentRowId = (Integer)m.get("parent_rowid");
            Integer childRowId = (Integer)m.get("child_rowid");

            if (options.isVeryNewHotness())
            {
                Integer runId = (Integer)m.get("runId");
                String runLsid = (String)m.get("runLsid");
                if (runId != null && runLsid != null)
                {
                    edges.add(new ExpLineage.Edge(parentLSID, runLsid, "no role"));
                    edges.add(new ExpLineage.Edge(runLsid, childLSID, "no role"));
                    runids.add(runId);
                }
            }
            else
            {
                String role = (String)m.get("role");
                edges.add(new ExpLineage.Edge(parentLSID, childLSID, role));
            }

            // process parents
            if ("Data".equals(parentExpType))
                dataids.add(parentRowId);
            else if ("Material".equals(parentExpType))
                materialids.add(parentRowId);
            else if ("ExperimentRun".equals(parentExpType))
                runids.add(parentRowId);

            // process children
            if ("Data".equals(childExpType))
                dataids.add(childRowId);
            else if ("Material".equals(childExpType))
                materialids.add(childRowId);
            else if ("ExperimentRun".equals(childExpType))
                runids.add(childRowId);
        });

        Set<ExpData> datas = new HashSet<>();
        List<ExpDataImpl> expDatas = getExpDatas(dataids);
        if (null != expDatas)
            datas.addAll(expDatas);

        Set<ExpMaterial> materials = new HashSet<>();
        List<ExpMaterialImpl> expMaterials = getExpMaterials(materialids);
        if (null != expMaterials)
            materials.addAll(expMaterials);

        Set<ExpRun> runs = new HashSet<>();
        List<ExpRunImpl> expRuns = getExpRuns(runids);
        if (null != expRuns)
            runs.addAll(expRuns);

        if (start instanceof ExpData)
            datas.remove(start);
        else if (start instanceof ExpMaterial)
            materials.remove(start);
        else if (start instanceof ExpRun)
            runs.remove(start);

        return new ExpLineage(start, datas, materials, runs, edges);
    }


    public SQLFragment generateExperimentTreeSQL(List<String> lsids, ExpLineageOptions options)
    {
        String comma="";
        SQLFragment sqlf = new SQLFragment();
        for (String lsid : lsids)
        {
            sqlf.append(comma).append("?").add(lsid);
            comma = ",";
        }
        return generateExperimentTreeSQL(sqlf, options);
    }

    /* return <ParentsQuery,ChildrenQuery> */
    private Pair<String,String> getRunGraphCommonTableExpressions(SQLFragment ret, SQLFragment lsidsFrag, boolean forLookup, Integer depth, boolean veryNewHotness)
    {
        String sourceSQL = veryNewHotness ?
                (forLookup ? exp_graph_sql_for_lookup2 : exp_graph_sql2) :
                (forLookup ? exp_graph_sql_for_lookup : exp_graph_sql);

        Map<String,String> map = new HashMap<>();

        String[] strs = StringUtils.splitByWholeSeparator(sourceSQL,"/* CTE */");
        for (int i=1 ; i<strs.length ; i++)
        {
            String s = strs[i].trim();
            int as = s.indexOf(" AS");
            String name = s.substring(0,as).trim();
            String select = s.substring(as+3).trim();
            if (select.endsWith(","))
                select = select.substring(0,select.length()-1).trim();
            if (select.endsWith(")"))
                select = select.substring(0,select.length()-1).trim();
            if (select.startsWith("("))
                select = select.substring(1).trim();
            if (name.equals("$SEED$"))
                select = select.replace("$LSIDS$", lsidsFrag.getRawSQL());
            if (veryNewHotness && (name.equals("$PARENTS_INNER$") || name.equals("$CHILDREN_INNER$")))
                select = select.replace("$LSIDS$", lsidsFrag.getRawSQL());
            map.put(name, select);
        }

        String nodesToken = null;
        String seedToken = null;
        String edgesToken = null;
        if (!veryNewHotness)
        {
            String nodesSelect = map.get("$NODES$");
            SQLFragment nodesMaterialized = materializeNodesCTE(nodesSelect);
            nodesToken = ret.addCommonTableExpression(ExperimentServiceImpl.class.getName() + "$CTE_NODES", "org_lk_exp_NODES", nodesMaterialized);

            String seedSelect = map.get("$SEED$");
            seedSelect = StringUtils.replace(seedSelect, "$NODES$", nodesToken);
            SQLFragment seedSqlfSelect = new SQLFragment(seedSelect, lsidsFrag.getParams());
            seedToken = ret.addCommonTableExpression(JdbcUtil.format(seedSqlfSelect), "org_lk_exp_SEED", seedSqlfSelect);

            String edgesSelect = map.get("$EDGES$");
            SQLFragment edgesMaterialized = materializeEdgesCTE(edgesSelect);
            edgesToken = ret.addCommonTableExpression(ExperimentServiceImpl.class.getName() + "$CTE_EDGES", "org_lk_exp_EDGES", edgesMaterialized);
        }

        boolean recursive = getExpSchema().getSqlDialect().isPostgreSQL();

        String parentsInnerSelect = map.get("$PARENTS_INNER$");
        if (!veryNewHotness)
        {
            parentsInnerSelect = StringUtils.replace(parentsInnerSelect, "$SEED$", seedToken);
            parentsInnerSelect = StringUtils.replace(parentsInnerSelect, "$EDGES$", edgesToken);
        }
        // NOTE: Adding depth clause to the inner recursive CTE makes it more efficient, but does mean it won't be shared if we have a lookup by a different depth in the query
        String parentDepth = "";
        if (depth != null && depth != 0)
        {
            int d = depth < 0 ? depth : (-1 * depth);
            parentDepth = "AND _Graph.depth - 1 >= " + d;
        }
        parentsInnerSelect = StringUtils.replace(parentsInnerSelect, "$AND_STUFF$", parentDepth);
        SQLFragment parentsInnerSelectFrag = new SQLFragment(parentsInnerSelect);
        if (veryNewHotness)
            parentsInnerSelectFrag.addAll(lsidsFrag.getParams());
        String parentsInnerToken = ret.addCommonTableExpression(parentsInnerSelect, "org_lk_exp_PARENTS_INNER", parentsInnerSelectFrag, recursive);

        String parentsSelect = map.get("$PARENTS$");
        parentsSelect = StringUtils.replace(parentsSelect, "$PARENTS_INNER$", parentsInnerToken);
        if (!veryNewHotness)
            parentsSelect = StringUtils.replace(parentsSelect, "$NODES$", nodesToken);
        String parentsToken = ret.addCommonTableExpression(parentsSelect, "org_lk_exp_PARENTS", new SQLFragment(parentsSelect), recursive);

        String childrenInnerSelect = map.get("$CHILDREN_INNER$");
        childrenInnerSelect = StringUtils.replace(childrenInnerSelect, "$SEED$", seedToken);
        childrenInnerSelect = StringUtils.replace(childrenInnerSelect, "$EDGES$", edgesToken);
        // NOTE: Adding depth clause to the inner recursive CTE makes it more efficient, but does mean it won't be shared if we have a lookup by a different depth in the query
        String childrenDepth = "";
        if (depth != null && depth != 0)
        {
            childrenDepth = "AND _Graph.depth + 1 <= " + depth;
        }
        childrenInnerSelect = StringUtils.replace(childrenInnerSelect, "$AND_STUFF$", childrenDepth);
        SQLFragment childrenInnerSelectFrag = new SQLFragment(childrenInnerSelect);
        if (veryNewHotness)
            childrenInnerSelectFrag.addAll(lsidsFrag.getParams());
        String childrenInnerToken = ret.addCommonTableExpression(childrenInnerSelect, "org_lk_exp_CHILDREN_INNER", childrenInnerSelectFrag, recursive);

        String childrenSelect = map.get("$CHILDREN$");
        childrenSelect = StringUtils.replace(childrenSelect, "$CHILDREN_INNER$", childrenInnerToken);
        if (!veryNewHotness)
            childrenSelect = StringUtils.replace(childrenSelect, "$NODES$", nodesToken);
        String childrenToken = ret.addCommonTableExpression(childrenSelect, "org_lk_exp_CHILDREN", new SQLFragment(childrenSelect), recursive);

        return new Pair<>(parentsToken,childrenToken);
    }

    MaterializedQueryHelper materializedNodes = null;
    MaterializedQueryHelper materializedEdges = null;
    final Object initEdgesLock = new Object();
    public final AtomicLong expLineageCounter = new AtomicLong();

    public void uncacheLineageGraph()
    {
        MaterializedQueryHelper nodes;
        MaterializedQueryHelper edges;

        // only lock on retrieving the object
        synchronized (initEdgesLock)
        {
            nodes = materializedNodes;
            edges = materializedEdges;
        }

        if (null != nodes)
            nodes.uncache(null);
        if (null != edges)
            edges.uncache(null);

        getExpSchema().getScope().addCommitTask(expLineageCounter::incrementAndGet, POSTCOMMIT);
    }

    /* TODO AND CONSIDER:
     *
     * remove the DataInput and MaterialInput tables and replace with a table that looks like this "edges" table
     */
    SQLFragment materializeNodesCTE(String selectNodes)
    {
        final DbSchema exp = getExpSchema();

        synchronized (initEdgesLock)
        {
            if (null == materializedNodes)
            {
                String materializeKeySql = "SELECT\n" + exp.getSqlDialect().concatenate(
                        "(select coalesce(cast(count(*) as varchar(40)),'-') from exp.material)",
                        "'/'",
                        "(select coalesce(cast(count(*) as varchar(40)),'-') from exp.data)",
                        "'/'",
                        "(select coalesce(cast(max(rowid) as varchar(40)),'-') from exp.experimentrun)") + " AS \"key\"";

                materializedNodes = (new MaterializedQueryHelper.Builder( "exp_nodes", getExpSchema().getScope(), new SQLFragment(selectNodes)))
                        .upToDateSql(new SQLFragment(materializeKeySql))
                        .addIndex("CREATE INDEX node_lsid_${NAME} ON temp.${NAME}  (lsid)")
                        .maxTimeToCache(CacheManager.HOUR)
                        .addInvalidCheck(() -> String.valueOf(expLineageCounter.get()))
                        .build();
                assert MemTracker.get().remove(materializedNodes);
                CacheManager.addListener(materializedNodes);
            }
        }

        SQLFragment sqlf = new SQLFragment("SELECT * FROM ").append(materializedNodes.getFromSql(null,null));
        return sqlf;
    }

    /* TODO AND CONSIDER:
     *
     * remove the DataInput and MaterialInput tables and replace with a table that looks like this "edges" table
     */
    SQLFragment materializeEdgesCTE(String selectEdges)
    {
        final DbSchema exp = getExpSchema();
        final long now = System.currentTimeMillis();

        synchronized (initEdgesLock)
        {
            if (null == materializedEdges)
            {
                String materializeKeySql = "SELECT\n" + exp.getSqlDialect().concatenate(
                        "(select coalesce(cast(count(*) as varchar(40)),'-') from exp.materialinput)",
                        "'/'",
                        "(select coalesce(cast(count(*) as varchar(40)),'-') from exp.datainput)",
                        "'/'",
                        "(select coalesce(cast(max(rowid) as varchar(40)),'-') from exp.protocolapplication)") + " AS \"key\"";

                materializedEdges = MaterializedQueryHelper.create( "exp_edges",
                        getExpSchema().getScope(),
                        new SQLFragment(selectEdges),
                        new SQLFragment(materializeKeySql),
                        Arrays.asList(
                               "CREATE INDEX child_${NAME} ON temp.${NAME}  (child_lsid, parent_lsid, child_pathpart, parent_pathpart, role)",
                               "CREATE INDEX parent_${NAME} ON temp.${NAME}  (parent_lsid, child_lsid, parent_pathpart, child_pathpart, role)"
                        ),
                        CacheManager.HOUR
                        );
                assert MemTracker.get().remove(materializedEdges);
                CacheManager.addListener(materializedEdges);
            }
        }

        SQLFragment sqlf = new SQLFragment("SELECT * FROM ").append(materializedEdges.getFromSql(null,null));
        return sqlf;
    }


    public SQLFragment generateExperimentTreeSQL(SQLFragment lsidsFrag, ExpLineageOptions options)
    {
        SQLFragment sqlf = new SQLFragment();
        Pair<String,String> tokens = getRunGraphCommonTableExpressions(sqlf, lsidsFrag, options.isForLookup(), options.getDepth(), options.isVeryNewHotness());
        boolean up = options.isParents();
        boolean down = options.isChildren();

        if (up || down)
        {
            if (up)
            {
                SQLFragment parents = new SQLFragment();
                if (options.isForLookup())
                {
                    parents.append("\nSELECT MIN(depth) AS depth, self_lsid, ");
                    if (!options.isVeryNewHotness())
                        parents.append("MIN(self_rowid) AS self_rowid, ");
                    parents.append("MIN(container) AS container, MIN(exptype) AS exptype, MIN(cpastype) AS cpastype, MIN(name) AS name, lsid, MIN(rowid) AS rowid ");
                    parents.append("\nFROM ").append(tokens.first);
                }
                else
                {
                    parents.append("\nSELECT * FROM " + tokens.first);
                }
                String and = "\nWHERE ";

                if (options.isVeryNewHotness())
                {
                    parents.append(and).append("depth != 0");
                    and = "\nAND ";
                }

                if (options.isForLookup())
                {
                    parents.append(and).append("lsid <> self_lsid");
                    and = "\nAND ";
                }

                if (options.getExpType() != null && !"NULL".equalsIgnoreCase(options.getExpType()))
                {
                    if (options.isForLookup())
                        parents.append(and).append("exptype = ?\n");
                    else
                        parents.append(and).append("parent_exptype = ?\n");
                    parents.add(options.getExpType());
                    and = "\nAND ";
                }

                if (options.getCpasType() != null && !"NULL".equalsIgnoreCase(options.getCpasType()))
                {
                    if (options.isForLookup())
                        parents.append(and).append("cpastype = ?\n");
                    else
                        parents.append(and).append("parent_cpastype = ?\n");
                    parents.add(options.getCpasType());
                    and = "\nAND ";
                }

                if (options.getDepth() != 0)
                {
                    // convert depth to negative value if it isn't
                    int depth = options.getDepth();
                    if (depth > 0)
                        depth *= -1;
                    parents.append(and).append("depth >= ").append(depth);
                    and = "\nAND ";
                }

                if (options.isForLookup())
                {
                    parents.append("\nGROUP BY self_lsid, lsid");
                }
                sqlf.append(parents);
            }

            if (up && down)
            {
                sqlf.append("\nUNION");
            }

            if (down)
            {
                SQLFragment children = new SQLFragment();

                if (options.isForLookup())
                {
                    children.append("\nSELECT MIN(depth) AS depth, self_lsid, ");
                    if (!options.isVeryNewHotness())
                        children.append("MIN(self_rowid) AS self_rowid, ");
                    children.append("MIN(container) AS container, MIN(exptype) AS exptype, MIN(cpastype) AS cpastype, MIN(name) as name, lsid, MIN(rowid) AS rowid ");
                    children.append("\nFROM ").append(tokens.second);
                }
                else
                {
                    children.append("\nSELECT * FROM " + tokens.second);
                }

                String and = "\nWHERE ";

                if (options.isVeryNewHotness())
                {
                    children.append(and).append("depth != 0");
                    and = "\nAND ";
                }

                if (options.isForLookup())
                {
                    children.append(and).append("lsid <> self_lsid");
                    and = "\nAND ";
                }

                if (options.getExpType() != null && !"NULL".equalsIgnoreCase(options.getExpType()))
                {
                    if (options.isForLookup())
                        children.append(and).append("exptype = ?\n");
                    else
                        children.append(and).append("child_exptype = ?\n");
                    children.add(options.getExpType());
                    and = "\nAND ";
                }

                if (options.getCpasType() != null && !"NULL".equalsIgnoreCase(options.getCpasType()))
                {
                    if (options.isForLookup())
                        children.append(and).append("cpastype = ?\n");
                    else
                        children.append(and).append("child_cpastype = ?\n");
                    children.add(options.getCpasType());
                    and = "\nAND ";
                }

                if (options.getDepth() > 0)
                {
                    children.append(and).append("depth <= ").append(options.getDepth());
                    and = "\nAND ";
                }

                if (options.isForLookup())
                {
                    children.append("\nGROUP BY self_lsid, lsid");
                }
                sqlf.append(children);
            }
        }
        else
        {
            sqlf.append("\nSELECT * FROM _Seed");
        }

        return sqlf;
    }


    public int removeEdgesForRun(int runId)
    {
        LOG.info("Removing edges for run " + runId);
        return Table.delete(getTinfoEdge(), new SimpleFilter("runId", runId));
    }

    // cleanup edges for the object
    public void removeEdges(String lsid)
    {
        Table.delete(getTinfoEdge(), new SimpleFilter("fromLsid", lsid));
        Table.delete(getTinfoEdge(), new SimpleFilter("toLsid", lsid));
    }

    // prepare for bulk insert of edges
    private void prepEdgeForInsert(List<List<Object>> params, Set<Pair<String, String>> seenEdges, Set<String> seenNodes,
                                   Map<String, Object> from, Map<String, Object> to, int runId)
    {
        assert getExpSchema().getScope().isTransactionActive();

        // from object
        String fromLsid = (String)from.get("LSID");
        Objects.requireNonNull(fromLsid, "lsid");

        // to object
        String toLsid = (String)to.get("LSID");
        Objects.requireNonNull(fromLsid, "lsid");

        // ignore cycles from and to itself
        if (fromLsid.equals(toLsid))
            return;

        // ignore duplicate edges within the same run
        Pair<String, String> key = Pair.of(fromLsid, toLsid);
        if (seenEdges.contains(key))
            return;
        seenEdges.add(key);

        String fromContainerId = (String)from.get("container");
        Objects.requireNonNull(fromContainerId, "containerId");

        String toContainerId = (String)to.get("container");
        Objects.requireNonNull(toContainerId, "containerId");

        if (!seenNodes.contains(fromLsid))
        {
            seenNodes.add(fromLsid);
            Container fromContainer = ContainerManager.getForId(fromContainerId);
            if (fromContainer == null)
                throw new IllegalArgumentException();

            ensureEdgeObject(fromContainer, fromLsid, (String) from.get("cpasType"));
        }

        if (!seenNodes.contains(toLsid))
        {
            seenNodes.add(toLsid);
            Container toContainer = ContainerManager.getForId(toContainerId);
            if (toContainer == null)
                throw new IllegalArgumentException();

            ensureEdgeObject(toContainer, toLsid, (String) to.get("cpasType"));
        }

        params.add(Arrays.asList(fromLsid, toLsid, runId));
    }

    private int ensureEdgeObject(@NotNull Container container, @NotNull String lsid, @Nullable String cpasType)
    {
        assert getExpSchema().getScope().isTransactionActive();

        // To set the ownerObjectId, find the owner object if present
        Integer ownerObjectId = null;
        if (cpasType != null && !cpasType.equals(ExpMaterial.DEFAULT_CPAS_TYPE) && !cpasType.equals("Sample") && !cpasType.equals(StudyService.SPECIMEN_NAMESPACE_PREFIX))
        {
            org.labkey.api.exp.OntologyObject oo = OntologyManager.getOntologyObject(null, cpasType);
            if (oo == null)
            {
                // NOTE: for current edge objects (Samples and Data), only Samples use ownerObjectId.  Maybe ExpData that belong to a DataClass should too?
                ExpSampleSet ss = getSampleSet(cpasType);
                if (ss != null)
                {
                    LOG.info("creating exp.object.objectId for owner cpasType '" + cpasType + "' needed by child object '" + lsid + "'");
                    ownerObjectId = OntologyManager.ensureObject(ss.getContainer(), cpasType, (Integer) null);
                }
            }
            else
            {
                ownerObjectId = oo.getObjectId();
            }
        }
        return OntologyManager.ensureObject(container, lsid, ownerObjectId);
    }

    private void insertEdge(String fromLsid, String toLsid, int runId)
    {
        assert getExpSchema().getScope().isTransactionActive();
        if (fromLsid == null || toLsid == null)
            throw new IllegalArgumentException();

        if (runId == 0)
            throw new IllegalArgumentException();

        Table.insert(null, getTinfoEdge(), CaseInsensitiveHashMap.of(
                "fromLsid", fromLsid,
                "toLsid", toLsid,
                "runId", runId));
    }

    private void insertEdges(List<List<Object>> params)
    {
        assert getExpSchema().getScope().isTransactionActive();
        if (params.isEmpty())
            return;

        String sql = "INSERT INTO " + getTinfoEdge() + " (fromLsid, toLsid, runId) VALUES (?, ?, ?)";
        try
        {
            Table.batchExecute(getExpSchema(), sql, params);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    // CONSIDER: Incrementally add/remove edges as they are created
    @Override
    public void syncRunEdges(ExpRun run)
    {
        syncRunEdges(run.getRowId());
    }

    public void syncRunEdges(Collection<Integer> runIds)
    {
        for (Integer runId : runIds)
            syncRunEdges(runId);
    }

    public void syncRunEdges(int runId)
    {
        syncRunEdges(runId, true);
    }

    private void syncRunEdges(int runId, boolean deleteFirst)
    {
        CPUTimer timer = new CPUTimer("sync edges");
        timer.start();

        LOG.info("Rebuilding edges for runId " + runId);
        try (DbScope.Transaction tx = getExpSchema().getScope().ensureTransaction())
        {
            SQLFragment inputDatas = new SQLFragment()
                    .append("SELECT d.Container, d.LSID FROM exp.Data d\n")
                    .append("INNER JOIN exp.DataInput di ON d.rowId = di.dataId\n")
                    .append("INNER JOIN exp.ProtocolApplication pa ON di.TargetApplicationId = pa.RowId\n")
                    .append("WHERE pa.RunId = ?")
                    .append("  AND pa.CpasType = 'ExperimentRun'")
                    .add(runId);
            Collection<Map<String, Object>> fromDataLsids = new SqlSelector(getSchema(), inputDatas).getMapCollection();

            SQLFragment inputMaterials = new SQLFragment()
                    .append("SELECT m.Container, m.LSID, m.CpasType FROM exp.material m\n")
                    .append("INNER JOIN exp.MaterialInput mi ON m.rowId = mi.materialId\n")
                    .append("INNER JOIN exp.ProtocolApplication pa ON mi.TargetApplicationId = pa.RowId\n")
                    .append("WHERE pa.RunId = ?")
                    .append("  AND pa.CpasType = 'ExperimentRun'")
                    .add(runId);
            Collection<Map<String, Object>> fromMaterialLsids = new SqlSelector(getSchema(), inputMaterials).getMapCollection();

            Collection<Map<String, Object>> toDataLsids = new TableSelector(getTinfoData(), getTinfoData().getColumns("Container, LSID"), new SimpleFilter(FieldKey.fromParts("RunId"), runId), null).getMapCollection();
            Collection<Map<String, Object>> toMaterialLsids = new TableSelector(getTinfoMaterial(), getTinfoMaterial().getColumns("Container, LSID, CpasType"), new SimpleFilter(FieldKey.fromParts("RunId"), runId), null).getMapCollection();

            // delete all existing edges for this run
            if (deleteFirst)
                removeEdgesForRun(runId);

            int edgeCount = (fromDataLsids.size()     * (toDataLsids.size() + toMaterialLsids.size())) +
                            (fromMaterialLsids.size() * (toDataLsids.size() + toMaterialLsids.size()));
            LOG.info(String.format("  edge counts: input data=%d, input materials=%d, output data=%d, output materials=%d, total=%d",
                    fromDataLsids.size(), fromMaterialLsids.size(), toDataLsids.size(), toMaterialLsids.size(), edgeCount));

            Set<Pair<String, String>> seenEdges = new HashSet<>(edgeCount);
            Set<String> seenNodes = new HashSet<>(edgeCount);
            List<List<Object>> params = new ArrayList<>(edgeCount);

            // create edge for each input and output combination
            for (Map<String, Object> fromDataLsid : fromDataLsids)
            {
                for (Map<String, Object> toDataLsid : toDataLsids)
                    prepEdgeForInsert(params, seenEdges, seenNodes, fromDataLsid, toDataLsid, runId);

                for (Map<String, Object> toMaterialLsid : toMaterialLsids)
                    prepEdgeForInsert(params, seenEdges, seenNodes, fromDataLsid, toMaterialLsid, runId);
            }

            // create edge for each input and output combination
            for (Map<String, Object> fromMaterialLsid : fromMaterialLsids)
            {
                for (Map<String, Object> toDataLsid : toDataLsids)
                    prepEdgeForInsert(params, seenEdges, seenNodes, fromMaterialLsid, toDataLsid, runId);

                for (Map<String, Object> toMaterialLsid : toMaterialLsids)
                    prepEdgeForInsert(params, seenEdges, seenNodes, fromMaterialLsid, toMaterialLsid, runId);
            }

            insertEdges(params);

            tx.commit();
            timer.stop();
            LOG.info("  synced edges in " + timer.getDuration());
        }
    }

    public void rebuildAllEdges()
    {
        try (CustomTiming timing = MiniProfiler.custom("exp", "rebuildAllEdges"))
        {
            try (Timing t = MiniProfiler.step("delete edges"))
            {
                LOG.info("Deleting all edges");
                Table.delete(getTinfoEdge());
            }

            List<Integer> runIds = new TableSelector(getTinfoExperimentRun(), singleton("rowId"), null, new Sort("rowId")).getArrayList(Integer.class);
            try (Timing t = MiniProfiler.step("create edges"))
            {
                LOG.info("Rebuilding edges for " + runIds.size() + " runs");
                for (Integer runId : runIds)
                    syncRunEdges(runId, false);
            }

            if (timing != null)
            {
                timing.stop();
                LOG.info("Rebuilt all edges: " + timing.getDuration() + " ms");
            }
        }
    }



    public boolean isUnknownMaterial(ExpProtocolOutput output)
    {
        return "Unknown".equals(output.getName()) &&
                ParticipantVisit.ASSAY_RUN_MATERIAL_NAMESPACE.equals(output.getLSIDNamespacePrefix());
    }

    private List<ExpMaterial> removeUnknownMaterials(Iterable<ExpMaterial> materials)
    {
        // Filter out the generic unknown material, which is just a placeholder and doesn't represent a real
        // parent
        ArrayList<ExpMaterial> result = new ArrayList<>();
        for (ExpMaterial material : materials)
        {
            if (!isUnknownMaterial(material))
                result.add(material);
        }
        return result;
    }

    /**
     * @return the data objects that were attached to the run that should be attached to the run in its new folder
     */
    public List<ExpDataImpl> deleteExperimentRunForMove(int runId, User user)
    {
        List<ExpDataImpl> datasToDelete = getAllDataOwnedByRun(runId);

        deleteRun(runId, datasToDelete, user);
        return datasToDelete;
    }


    private void deleteRun(int runId, List<ExpDataImpl> datasToDelete, User user)
    {
        ExpRunImpl run = getExpRun(runId);
        if (run == null)
        {
            return;
        }

        for (ExperimentListener listener : _listeners)
        {
            listener.beforeRunDelete(run.getProtocol(), run);
        }

        // Note: At the moment, FlowRun is the only example of an ExpRun attachment parent, but we're keeping this general
        // so other cases can be added in the future
        AttachmentService.get().deleteAttachments(new ExpRunAttachmentParent(run));

        run.deleteProtocolApplications(datasToDelete, user);

        //delete run properties and all children
        OntologyManager.deleteOntologyObject(run.getLSID(), run.getContainer(), true);

        SQLFragment sql = new SQLFragment("DELETE FROM exp.RunList WHERE ExperimentRunId = ?;\n");
        sql.add(run.getRowId());
        sql.append("UPDATE exp.ExperimentRun SET ReplacedByRunId = NULL WHERE ReplacedByRunId = ?;\n");
        sql.add(run.getRowId());
        sql.append("DELETE FROM ").append(getTinfoEdge()).append(" WHERE runId = ?;\n");
        sql.add(run.getRowId());
        sql.append("DELETE FROM exp.ExperimentRun WHERE RowId = ?;\n");
        sql.add(run.getRowId());

        new SqlExecutor(getExpSchema()).execute(sql);

        ExpProtocolImpl protocol = run.getProtocol();
        if (protocol == null)
        {
            throw new IllegalStateException("Could not resolve protocol for run LSID " + run.getLSID() + " with protocol LSID " + run.getDataObject().getProtocolLSID() );
        }
        auditRunEvent(user, protocol, run, null, "Run deleted");
    }


    public DbSchema getExpSchema()
    {
        return DbSchema.get("exp", DbSchemaType.Module);
    }

    public TableInfo getTinfoExperiment()
    {
        return getExpSchema().getTable("Experiment");
    }

    public TableInfo getTinfoExperimentRun()
    {
        return getExpSchema().getTable("ExperimentRun");
    }

    public TableInfo getTinfoExperimentRunMaterialInputs()
    {
        return getExpSchema().getTable("ExperimentRunMaterialInputs");
    }

    public TableInfo getTinfoExperimentRunDataInputs()
    {
        return getExpSchema().getTable("ExperimentRunDataInputs");
    }

    public TableInfo getTinfoProtocol()
    {
        return getExpSchema().getTable("Protocol");
    }

    public TableInfo getTinfoProtocolAction()
    {
        return getExpSchema().getTable("ProtocolAction");
    }

    public TableInfo getTinfoProtocolActionPredecessor()
    {
        return getExpSchema().getTable("ProtocolActionPredecessor");
    }

    public TableInfo getTinfoProtocolParameter()
    {
        return getExpSchema().getTable("ProtocolParameter");
    }

    public TableInfo getTinfoMaterial()
    {
        return getExpSchema().getTable("Material");
    }

    public TableInfo getTinfoMaterialInput()
    {
        return getExpSchema().getTable("MaterialInput");
    }

    public TableInfo getTinfoMaterialSource()
    {
        return getExpSchema().getTable("MaterialSource");
    }

    public TableInfo getTinfoActiveMaterialSource()
    {
        return getExpSchema().getTable("ActiveMaterialSource");
    }

    public TableInfo getTinfoData()
    {
        return getExpSchema().getTable("Data");
    }

    public TableInfo getTinfoDataClass()
    {
        return getExpSchema().getTable("DataClass");
    }

    public TableInfo getTinfoDataInput()
    {
        return getExpSchema().getTable("DataInput");
    }

    public TableInfo getTinfoProtocolApplication()
    {
        return getExpSchema().getTable("ProtocolApplication");
    }

    public TableInfo getTinfoProtocolActionDetails()
    {
        return getExpSchema().getTable("ProtocolActionStepDetailsView");
    }

    public TableInfo getTinfoProtocolApplicationParameter()
    {
        return getExpSchema().getTable("ProtocolApplicationParameter");
    }

    public TableInfo getTinfoProtocolActionPredecessorLSIDView()
    {
        return getExpSchema().getTable("ProtocolActionPredecessorLSIDView");
    }

    public TableInfo getTinfoPropertyDescriptor()
    {
        return getExpSchema().getTable("PropertyDescriptor");
    }

    public TableInfo getTinfoRunList ()
    {
        return getExpSchema().getTable("RunList");
    }

    public TableInfo getTinfoAssayQCFlag()
    {
        return getExpSchema().getTable("AssayQCFlag");
    }

    @Override
    public TableInfo getTinfoAlias()
    {
        return getExpSchema().getTable("Alias");
    }

    @Override
    public TableInfo getTinfoDataAliasMap()
    {
        return getExpSchema().getTable("DataAliasMap");
    }

    @Override
    public TableInfo getTinfoMaterialAliasMap()
    {
        return getExpSchema().getTable("MaterialAliasMap");
    }

    public TableInfo getTinfoEdge()
    {
        return getExpSchema().getTable("Edge");
    }

    /**
     * return the object of any known experiment type that is identified with the LSID
     *
     * @return Object identified by this lsid or null if lsid not found
     */
    public Identifiable getObject(Lsid lsid)
    {
        LsidType type = findType(lsid);

        return null != type ? type.getObject(lsid) : null;
    }

    static final String findTypeSql = "SELECT Type FROM exp.AllLsid WHERE Lsid = ?";

    /**
     * @param lsid Full lsid we're looking for.
     * @return Object type for this lsid. Hmm should we return a class
     */
    public LsidType findType(Lsid lsid)
    {
        //First check if we created this. If so, might be able to find without query
        if (AppProps.getInstance().getDefaultLsidAuthority().equals(lsid.getAuthority()))
        {
            LsidType type = LsidType.get(lsid.getNamespacePrefix());
            if (null != type)
                return type;
        }

        String typeName = new SqlSelector(getExpSchema(), findTypeSql, lsid.toString()).getObject(String.class);
        return LsidType.get(typeName);
    }

    public List<String> createContainerList(Container container, User user, boolean includeProjectAndShared)
    {
        List<String> containerIds = new ArrayList<>();
        containerIds.add(container.getId());
        if (includeProjectAndShared && user == null)
        {
            throw new IllegalArgumentException("Can't include data from other containers without a user to check permissions on");
        }
        if (includeProjectAndShared)
        {
            Container project = container.getProject();
            if (project != null && project.hasPermission(user, ReadPermission.class))
            {
                containerIds.add(project.getId());
            }
            Container shared = ContainerManager.getSharedContainer();
            if (shared.hasPermission(user, ReadPermission.class))
            {
                containerIds.add(shared.getId());
            }
        }
        return containerIds;
    }

    public SimpleFilter createContainerFilter(Container container, User user, boolean includeProjectAndShared)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addClause(new SimpleFilter.InClause(FieldKey.fromParts("Container"), createContainerList(container, user, includeProjectAndShared)));
        return filter;
    }

    public List<ExpExperimentImpl> getExperiments(Container container, User user, boolean includeOtherContainers, boolean includeBatches)
    {
        return getExperiments(container, user, includeOtherContainers, includeBatches, false);
    }

    public List<ExpExperimentImpl> getExperiments(Container container, User user, boolean includeOtherContainers, boolean includeBatches, boolean includeHidden)
    {
        SimpleFilter filter = createContainerFilter(container, user, includeOtherContainers);
        if (!includeHidden)
        {
            filter.addCondition(FieldKey.fromParts("Hidden"), Boolean.FALSE);
        }
        if (!includeBatches)
        {
            filter.addCondition(FieldKey.fromParts("BatchProtocolId"), null, CompareType.ISBLANK);
        }
        Sort sort = new Sort("RowId");
        sort.insertSort(new Sort("Name"));
        return ExpExperimentImpl.fromExperiments(new TableSelector(getTinfoExperiment(), filter, sort).getArray(Experiment.class));
    }

    public ExperimentRun getExperimentRun(String LSID)
    {
        //Use main cache so updates/deletes through table layer get handled
        String cacheKey = getCacheKey(LSID);
        ExperimentRun run = (ExperimentRun) DbCache.get(getTinfoExperimentRun(), cacheKey);
        if (null != run)
            return run;

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), LSID);
        run = new TableSelector(getTinfoExperimentRun(), filter, null).getObject(ExperimentRun.class);
        if (null != run)
            DbCache.put(getTinfoExperimentRun(), cacheKey, run);
        return run;
    }

    public void clearCaches()
    {
        getMaterialSourceCache().clear();
        getDataClassCache().clear();
        getProtocolCache().clear();
    }

    public ExpProtocolApplication getExpProtocolApplication(String lsid)
    {
        ProtocolApplication app = getProtocolApplication(lsid);
        return app == null ? null : new ExpProtocolApplicationImpl(app);
    }

    public ProtocolApplication getProtocolApplication(String lsid)
    {
        return new TableSelector(getTinfoProtocolApplication(), new SimpleFilter(FieldKey.fromParts("LSID"), lsid), null).getObject(ProtocolApplication.class);
    }

    public List<ProtocolAction> getProtocolActions(int parentProtocolRowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ParentProtocolId"), parentProtocolRowId);
        return new TableSelector(getTinfoProtocolAction(), filter, new Sort("+Sequence")).getArrayList(ProtocolAction.class);
    }

    public List<Material> getRunInputMaterial(String runLSID)
    {
        final String sql = "SELECT * FROM " + getTinfoExperimentRunMaterialInputs() + " WHERE RunLSID = ?";
        Map<String, Object>[] maps = new SqlSelector(getExpSchema(), new SQLFragment(sql, runLSID)).getMapArray();
        Map<String, List<Material>> material = getRunInputMaterial(maps);
        List<Material> result = material.get(runLSID);
        if (result == null)
        {
            result = Collections.emptyList();
        }
        return result;
    }


    private Map<String, List<Material>> getRunInputMaterial(Map<String, Object>[] maps)
    {
        Map<String, List<Material>> outputMap = new HashMap<>();
        BeanObjectFactory<Material> f = new BeanObjectFactory<>(Material.class);
        for (Map<String, Object> map : maps)
        {
            String runLSID = (String) map.get("RunLSID");
            List<Material> list = outputMap.get(runLSID);
            if (null == list)
            {
                list = new ArrayList<>();
                outputMap.put(runLSID, list);
            }
            Material m = f.fromMap(map);
            list.add(m);
        }
        return outputMap;
    }

    public MaterialSource lookupActiveMaterialSource(Container c)
    {
        String sql = "SELECT ms.* " +
            "FROM " + getTinfoMaterialSource() + " ms, " + getTinfoActiveMaterialSource() + " ams " +
            "WHERE ms.lsid = ams.materialsourcelsid AND ams.container = ?";

        return new SqlSelector(getExpSchema(), new SQLFragment(sql, c.getId())).getObject(MaterialSource.class);
    }

    public ExpSampleSetImpl ensureActiveSampleSet(Container c)
    {
        try
        {
            return ensureActiveSampleSet(c, null, false);
        }
        catch (ExperimentException e)
        {
            // Shouldn't happen since this exception can only be thrown if we are creating a new sample set
            throw new RuntimeException(e);
        }
    }

    public ExpSampleSetImpl ensureActiveSampleSet(Container c, User user, boolean createNewSet)
            throws ExperimentException
    {
        MaterialSource result = lookupActiveMaterialSource(c);
        if (result == null)
        {
            if (createNewSet && user != null)
            {
                // Create a new SampleSet in the current container
                List<GWTPropertyDescriptor> properties = new ArrayList<>();
                properties.add(new GWTPropertyDescriptor("Name", "http://www.w3.org/2001/XMLSchema#string"));
                return createSampleSet(c, user, "Samples", null, properties, Collections.emptyList(), 0, -1, -1, -1, null, null);
            }
            else
            {
                return ensureDefaultSampleSet();
            }
        }
        return new ExpSampleSetImpl(result);
    }

    public ExpSampleSetImpl ensureDefaultSampleSet()
    {
        ExpSampleSetImpl sampleSet = getSampleSet(getDefaultSampleSetLsid());

        if (null == sampleSet)
            return createDefaultSampleSet();
        else
            return sampleSet;
    }

    private synchronized ExpSampleSetImpl createDefaultSampleSet()
    {
        //might have been created on another thread, so check within synch block
        ExpSampleSetImpl matSource = getSampleSet(getDefaultSampleSetLsid());
        if (null == matSource)
        {
            matSource = createSampleSet();
            matSource.setLSID(getDefaultSampleSetLsid());
            matSource.setName(DEFAULT_MATERIAL_SOURCE_NAME);
            matSource.setMaterialLSIDPrefix(new Lsid.LsidBuilder("Sample", DEFAULT_MATERIAL_SOURCE_NAME).toString() + "#");
            matSource.setContainer(ContainerManager.getSharedContainer());
            matSource.save(null);
        }

        return matSource;
    }

    /**
     * @return map from OntologyEntryURI to parameter
     */
    public Map<String, ProtocolParameter> getProtocolParameters(int protocolRowId)
    {
        ProtocolParameter[] params = new TableSelector(getTinfoProtocolParameter(), new SimpleFilter(FieldKey.fromParts("ProtocolId"), protocolRowId), null).getArray(ProtocolParameter.class);
        Map<String, ProtocolParameter> result = new HashMap<>();
        for (ProtocolParameter param : params)
        {
            result.put(param.getOntologyEntryURI(), param);
        }
        return result;
    }

    @Override
    public ExpDataImpl getExpDataByURL(File file, @Nullable Container c)
    {
        File canonicalFile = FileUtil.getAbsoluteCaseSensitiveFile(file);
        String url = canonicalFile.toPath().toUri().toString();
        ExpDataImpl data = getExpDataByURL(url, c);
        if (null == data)
        {                   // Look for legacy format
            try
            {
                data = getExpDataByURL(canonicalFile.toURI().toURL().toString(), c);
            }
            catch (MalformedURLException e)
            {
                throw new UnexpectedException(e);
            }
        }
        return data;
    }

    @Override
    public ExpDataImpl getExpDataByURL(Path path, @Nullable Container c)
    {
        if (!FileUtil.hasCloudScheme(path))
            return getExpDataByURL(path.toFile(), c);

        return getExpDataByURL(FileUtil.pathToString(path), c);
    }

    @Override
    public List<ExpDataImpl> getAllExpDataByURL(String canonicalURL)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("DataFileUrl"), canonicalURL);
        Sort sort = new Sort("-Created");
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), filter, sort).getArrayList(Data.class));
    }

    public ExpDataImpl getExpDataByURL(String url, @Nullable Container c)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("DataFileUrl"), url);
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Sort sort = new Sort("-Created");
        Data[] data = new TableSelector(getTinfoData(), filter, sort).getArray(Data.class);
        if (data.length > 0)
        {
            return new ExpDataImpl(data[0]);
        }
        // Issue 17202 - for directories, check if the path was stored in the database without a trailing slash
        if (url.endsWith("/"))
        {
            return getExpDataByURL(url.substring(0, url.length() - 1), c);
        }
        return null;
    }

    public Lsid getSampleSetLsid(String sourceName, Container container)
    {
        return Lsid.parse(generateLSID(container, ExpSampleSet.class, sourceName));
    }

    public Lsid getDataClassLsid(String name, Container container)
    {
        return Lsid.parse(generateLSID(container, ExpDataClass.class, name));
    }

    @Override
    public void deleteExperimentRunsByRowIds(Container container, final User user, int... selectedRunIds)
    {
        List<Integer> ids = new ArrayList<>(selectedRunIds.length);
        for (int id : selectedRunIds)
            ids.add(id);
        deleteExperimentRunsByRowIds(container, user, ids);
    }

    @Override
    public void deleteExperimentRunsByRowIds(Container container, final User user, @NotNull Collection<Integer> selectedRunIds)
    {
        if (selectedRunIds.isEmpty())
            return;

        for (Integer runId : selectedRunIds)
        {
            try (DbScope.Transaction transaction = ensureTransaction())
            {
                final ExpRunImpl run = getExpRun(runId);
                if (run != null)
                {
                    SimpleFilter containerFilter = new SimpleFilter(FieldKey.fromParts("RunId"), runId);
                    Table.delete(getTinfoAssayQCFlag(), containerFilter);

                    ExpProtocol protocol = run.getProtocol();
                    ProtocolImplementation protocolImpl = null;
                    if (protocol != null)
                    {
                        protocolImpl = protocol.getImplementation();
                        for (Dataset dataset : StudyService.get().getDatasetsForAssayRuns(Collections.singletonList(run), user))
                        {
                            if (!dataset.canWrite(user))
                            {
                                throw new UnauthorizedException("Cannot delete rows from dataset " + dataset);
                            }
                            UserSchema schema = QueryService.get().getUserSchema(user, dataset.getContainer(), "study");
                            TableInfo tableInfo = schema.getTable(dataset.getName());
                            AssayProvider provider = AssayService.get().getProvider(protocol);
                            if (provider != null)
                            {
                                AssayTableMetadata tableMetadata = provider.getTableMetadata(protocol);
                                SimpleFilter filter = new SimpleFilter(tableMetadata.getRunRowIdFieldKeyFromResults(), run.getRowId());
                                Collection<String> lsids = new TableSelector(tableInfo, singleton("LSID"), filter, null).getCollection(String.class);

                                // Do the actual delete on the dataset for the rows in question
                                dataset.deleteDatasetRows(user, lsids);

                                // Add an audit event to the copy to study history
                                StudyService.get().addAssayRecallAuditEvent(dataset, lsids.size(), run.getContainer(), user);
                            }
                        }
                    }

                    // Grab these to delete after we've deleted the Data rows
                    List<ExpDataImpl> datasToDelete = getAllDataOwnedByRun(runId);

                    // Archive all data files prior to deleting
                    //  ideally this would be transacted as a commit task but we decided against it due to complications
                    run.archiveDataFiles(user);

                    deleteRun(runId, datasToDelete, user);

                    for (ExpData data : datasToDelete)
                    {
                        ExperimentDataHandler handler = data.findDataHandler();
                        handler.deleteData(data, container, user);
                    }

                    if (protocolImpl != null)
                        protocolImpl.onRunDeleted(container, user);
                }

                transaction.commit();
            }
        }
    }

    private Collection<Integer> getRelatedProtocolIds(Collection<Integer> selectedProtocolIds)
    {
        Set<Integer> allIds = new HashSet<>(selectedProtocolIds);

        Set<Integer> idsToCheck = new HashSet<>(allIds);
        while (!idsToCheck.isEmpty())
        {
            String idsString = StringUtils.join(idsToCheck.iterator(), ", ");
            idsToCheck = new HashSet<>();

            StringBuilder sb = new StringBuilder();
            sb.append("SELECT ParentProtocolId FROM exp.ProtocolAction WHERE ChildProtocolId IN (");
            sb.append(idsString);
            sb.append(");");
            Integer[] newIds = new SqlSelector(getExpSchema(), sb.toString()).getArray(Integer.class);

            idsToCheck.addAll(Arrays.asList(newIds));

            sb = new StringBuilder();
            sb.append("SELECT ChildProtocolId FROM exp.ProtocolAction WHERE ParentProtocolId IN (");
            sb.append(idsString);
            sb.append(");");
            newIds = new SqlSelector(getExpSchema(), sb.toString()).getArray(Integer.class);

            idsToCheck.addAll(Arrays.asList(newIds));
            idsToCheck.removeAll(allIds);
            allIds.addAll(idsToCheck);
        }

        return allIds;
    }

    @Override
    public List<ExpRunImpl> getExpRunsForProtocolIds(boolean includeRelated, int... protocolIds)
    {
        List<Integer> ids = new ArrayList<>(protocolIds.length);
        for (int id : protocolIds)
            ids.add(id);
        return getExpRunsForProtocolIds(includeRelated, ids);
    }

    @Override
    public List<ExpRunImpl> getExpRunsForProtocolIds(boolean includeRelated, @NotNull Collection<Integer> protocolIds)
    {
        if (protocolIds.isEmpty())
        {
            return Collections.emptyList();
        }

        Collection<Integer> allProtocolIds = protocolIds;
        if (includeRelated)
            allProtocolIds = getRelatedProtocolIds(protocolIds);

        if (allProtocolIds.isEmpty())
        {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ");
        sb.append(getTinfoExperimentRun().getSelectName());
        sb.append(" WHERE ProtocolLSID IN (");
        sb.append("SELECT LSID FROM exp.Protocol WHERE RowId IN (");
        sb.append(StringUtils.join(allProtocolIds, ", "));
        sb.append("))");
        return ExpRunImpl.fromRuns(new SqlSelector(getExpSchema(), sb.toString()).getArrayList(ExperimentRun.class));
    }

    public void deleteProtocolByRowIds(Container c, User user, int... selectedProtocolIds) throws ExperimentException
    {
        if (selectedProtocolIds.length == 0)
            return;

        List<ExpRunImpl> runs = getExpRunsForProtocolIds(false, selectedProtocolIds);

        String protocolIds = StringUtils.join(ArrayUtils.toObject(selectedProtocolIds), ", ");

        SQLFragment sql = new SQLFragment("SELECT * FROM exp.Protocol WHERE RowId IN (" + protocolIds + ");");
        Protocol[] protocols = new SqlSelector(getExpSchema(), sql).getArray(Protocol.class);

        sql = new SQLFragment("SELECT RowId FROM exp.ProtocolAction ");
        sql.append(" WHERE (ChildProtocolId IN (").append(protocolIds).append(")");
        sql.append(" OR ParentProtocolId IN (").append(protocolIds).append(") );");
        Integer[] actionIds = new SqlSelector(getExpSchema(), sql).getArray(Integer.class);

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            List<ExpProtocolImpl> expProtocols = Arrays.stream(protocols).map(ExpProtocolImpl::new).collect(toList());

            for (ExperimentListener listener : _listeners)
            {
                listener.beforeProtocolsDeleted(c, user, expProtocols);
            }

            for (ExpProtocol protocolToDelete : expProtocols)
            {
                for (ExpExperiment batch : protocolToDelete.getBatches())
                {
                    batch.delete(user);
                }

                for (Dataset dataset : StudyService.get().getDatasetsForAssayProtocol(protocolToDelete))
                {
                    dataset.delete(user);
                }
            }

            // Delete runs after deleting datasets so that we don't have to do the work to clear out the data rows
            for (ExpRun run : runs)
            {
                run.delete(user);
            }

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            if (actionIds.length > 0)
            {
                for (Protocol protocol : protocols)
                {
                    ExpProtocol protocolToDelete = new ExpProtocolImpl(protocol);
                    AssayProvider provider = AssayService.get().getProvider(protocolToDelete);
                    if (provider != null)
                    {
                        provider.deleteProtocol(protocolToDelete, user);
                    }
                }

                String actionIdsJoined = "(" + StringUtils.join(actionIds, ", ") + ")";
                executor.execute("DELETE FROM exp.ProtocolActionPredecessor WHERE ActionId IN " + actionIdsJoined + " OR PredecessorId IN " + actionIdsJoined + ";");
                executor.execute("DELETE FROM exp.ProtocolAction WHERE RowId IN " + actionIdsJoined);
            }

            executor.execute("DELETE FROM exp.ProtocolParameter WHERE ProtocolId IN (" + protocolIds + ")");

            for (Protocol protocol : protocols)
            {
                if (!protocol.getContainer().equals(c))
                {
                    throw new IllegalArgumentException("Attempting to delete a Protocol from another container");
                }
                OntologyManager.deleteOntologyObjects(c, protocol.getLSID());
            }

            executor.execute("DELETE FROM exp.Protocol WHERE RowId IN (" + protocolIds + ")");

            sql = new SQLFragment("SELECT RowId FROM exp.Protocol WHERE RowId NOT IN (SELECT ParentProtocolId FROM exp.ProtocolAction UNION SELECT ChildProtocolId FROM exp.ProtocolAction) AND Container = ?");
            sql.add(c.getId());
            int[] orphanedProtocolIds = ArrayUtils.toPrimitive(new SqlSelector(getExpSchema(), sql).getArray(Integer.class));
            deleteProtocolByRowIds(c, user, orphanedProtocolIds);

            transaction.addCommitTask(() -> {
                // Be sure that we clear the cache after we commit the overall transaction, in case it
                // gets repopulated by another thread before then
                AssayService.get().clearProtocolCache();
                for (Protocol protocol : protocols)
                {
                    uncacheProtocol(protocol);
                }
            }, POSTCOMMIT, DbScope.CommitTaskOption.IMMEDIATE);

            transaction.commit();
        }
    }

    public void deleteMaterialByRowIds(User user, Container container, Collection<Integer> selectedMaterialIds)
    {
        deleteMaterialByRowIds(user, container, selectedMaterialIds, true);
    }

    public void deleteMaterialByRowIds(User user, Container container, Collection<Integer> selectedMaterialIds, boolean deleteRunsUsingMaterials)
    {
        if (selectedMaterialIds.isEmpty())
            return;

        final SqlDialect dialect = getExpSchema().getSqlDialect();
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            SQLFragment sql = new SQLFragment("SELECT * FROM exp.Material WHERE RowId ");
            dialect.appendInClauseSql(sql, selectedMaterialIds);

            List<ExpMaterialImpl> materials = ExpMaterialImpl.fromMaterials(new SqlSelector(getExpSchema(), sql).getArrayList(Material.class));

            for (ExpMaterial material : materials)
            {
                if (!material.getContainer().hasPermission(user, DeletePermission.class))
                    throw new UnauthorizedException();
            }

            beforeDeleteMaterials(user, container, materials);

            for (ExpMaterialImpl material : materials)
            {
                // Delete any runs using the material if the ProtocolImplementation allows deleting the run when an input is deleted.
                if (deleteRunsUsingMaterials)
                {
                    deleteRunsUsingInput(user, material.getDataObject());
                }

                SQLFragment deleteSql = new SQLFragment()
                        .append("DELETE FROM ").append(String.valueOf(getTinfoMaterialAliasMap())).append(" WHERE LSID = ?;\n").add(material.getLSID())
                        .append("DELETE FROM ").append(String.valueOf(getTinfoEdge())).append(" WHERE fromLsid = ? OR toLsid = ?;").add(material.getLSID()).add(material.getLSID());
                new SqlExecutor(getExpSchema()).execute(deleteSql);

                OntologyManager.deleteOntologyObjects(container, material.getLSID());
            }

            // Delete MaterialInput exp.object and properties
            SQLFragment inputObjects = new SQLFragment("SELECT ")
                    .append(dialect.concatenate("'" + MaterialInput.lsidPrefix() + "'",
                            "CAST(mi.materialId AS VARCHAR)", "'.'", "CAST(mi.targetApplicationId AS VARCHAR)"))
                    .append(" FROM ").append(getTinfoMaterialInput(), "mi").append(" WHERE mi.materialId ");
            dialect.appendInClauseSql(inputObjects, selectedMaterialIds);
            OntologyManager.deleteOntologyObjects(getSchema(), inputObjects, container, false);

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            SQLFragment materialInputSQL = new SQLFragment("DELETE FROM exp.MaterialInput WHERE MaterialId ");
            dialect.appendInClauseSql(materialInputSQL, selectedMaterialIds);
            executor.execute(materialInputSQL);

            SQLFragment materialSQL = new SQLFragment("DELETE FROM exp.Material WHERE RowId ");
            dialect.appendInClauseSql(materialSQL, selectedMaterialIds);
            executor.execute(materialSQL);

            // Remove from search index
            SearchService ss = SearchService.get();
            if (null != ss)
            {
                for (ExpMaterial material : materials)
                {
                    ss.deleteResource(material.getDocumentId());
                }
            }

            transaction.commit();
        }
    }

    private void deleteRunsUsingInput(User user, ProtocolOutput item)
    {
        List<? extends ExpRun> runsUsingItem;
        if (item instanceof Data)
            runsUsingItem = getRunsUsingDataIds(Arrays.asList(item.getRowId()));
        else if (item instanceof Material)
            runsUsingItem = getRunsUsingMaterials(item.getRowId());
        else
            throw new IllegalArgumentException("Expected Data or Material");

        List<? extends ExpRun> runsToDelete = runsDeletedWithInput(runsUsingItem);
        if (runsToDelete.isEmpty())
            LOG.debug("No runs to delete for item '" + item.getName() + "'");
        else
            LOG.debug("Deleting runs using input item '" + item.getName() + "': " + runsToDelete.stream().map(ExpRun::getName).collect(Collectors.joining(", ")));
        for (ExpRun run : runsToDelete)
        {
            Container runContainer = run.getContainer();
            if (!runContainer.hasPermission(user, DeletePermission.class))
                throw new UnauthorizedException();

            deleteExperimentRunsByRowIds(run.getContainer(), user, run.getRowId());
        }
    }

    public void deleteDataByRowIds(User user, Container container, Collection<Integer> selectedDataIds) throws ExperimentException
    {
        deleteDataByRowIds(user, container, selectedDataIds, true);
    }

    public void deleteDataByRowIds(User user, Container container, Collection<Integer> selectedDataIds, boolean deleteRunsUsingData) throws ExperimentException
    {
        if (selectedDataIds.isEmpty())
            return;

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            SimpleFilter rowIdFilter = new SimpleFilter().addInClause(FieldKey.fromParts("RowId"), selectedDataIds);
            List<Data> datas = new TableSelector(getTinfoData(), rowIdFilter, null).getArrayList(Data.class);

            Map<Integer, List<String>> lsidsByClass = new LinkedHashMap<>();

            for (Data data : datas)
            {
                if (!data.getContainer().hasPermission(user, DeletePermission.class))
                    throw new UnauthorizedException();
            }

            List<ExpDataImpl> expDatas = ExpDataImpl.fromDatas(datas);
            beforeDeleteData(user, container, expDatas);

            for (Data data : datas)
            {
                if (!data.getContainer().equals(container))
                {
                    throw new SQLException("Attempting to delete a Data from another container");
                }

                // Delete any runs using the data if the ProtocolImplementation allows it
                if (deleteRunsUsingData)
                {
                    deleteRunsUsingInput(user, data);
                }

                SQLFragment deleteSql = new SQLFragment()
                    .append("DELETE FROM ").append(String.valueOf(getTinfoDataAliasMap())).append(" WHERE LSID = ?;\n").add(data.getLSID())
                    .append("DELETE FROM ").append(String.valueOf(getTinfoEdge())).append(" WHERE fromLsid = ? OR toLsid = ?;").add(data.getLSID()).add(data.getLSID());
                new SqlExecutor(getExpSchema()).execute(deleteSql);

                OntologyManager.deleteOntologyObjects(container, data.getLSID());

                if (data.getClassId() != null)
                {
                    List<String> byClass = lsidsByClass.get(data.getClassId());
                    if (byClass == null)
                        lsidsByClass.put(data.getClassId(), byClass = new ArrayList<>(10));
                    byClass.add(data.getLSID());
                }
            }

            SqlDialect dialect = getExpSchema().getSqlDialect();

            // Delete DataInput exp.object and properties
            SQLFragment inputObjects = new SQLFragment("SELECT ")
                    .append(dialect.concatenate("'" + DataInput.lsidPrefix() + "'",
                            "CAST(di.dataId AS VARCHAR)", "'.'", "CAST(di.targetApplicationId AS VARCHAR)"))
                    .append(" FROM ").append(getTinfoDataInput(), "di").append(" WHERE di.DataId ");
            dialect.appendInClauseSql(inputObjects, selectedDataIds);
            OntologyManager.deleteOntologyObjects(getSchema(), inputObjects, container, false);

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            SQLFragment dataInputSQL = new SQLFragment("DELETE FROM ").append(getTinfoDataInput()).append(" WHERE DataId ");
            dialect.appendInClauseSql(dataInputSQL, selectedDataIds);
            executor.execute(dataInputSQL);

            // DELETE FROM provisioned dataclass tables
            for (Integer classId : lsidsByClass.keySet())
            {
                ExpDataClass dataClass = getDataClass(classId);
                if (dataClass == null)
                    throw new SQLException("DataClass not found '" + classId + "'");

                List<String> lsids = lsidsByClass.get(classId);
                if (!lsids.isEmpty())
                {
                    TableInfo t = ((ExpDataClassImpl)dataClass).getTinfo();
                    SQLFragment sql = new SQLFragment("DELETE FROM ").append(t).append(" WHERE lsid ");
                    dialect.appendInClauseSql(sql, lsids);
                    executor.execute(sql);
                }
            }

            SQLFragment dataSQL = new SQLFragment("DELETE FROM ").append(getTinfoData()).append(" WHERE RowId ");
            dialect.appendInClauseSql(dataSQL, selectedDataIds);
            executor.execute(dataSQL);

            afterDeleteData(user, container, expDatas);

            // Remove from search index
            SearchService ss = SearchService.get();
            if (null != ss)
            {
                for (ExpDataImpl data : ExpDataImpl.fromDatas(datas))
                {
                    ss.deleteResource(data.getDocumentId());
                }
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void deleteExpExperimentByRowId(Container c, User user, int rowId)
    {
        if (!c.hasPermission(user, DeletePermission.class))
        {
            throw new IllegalStateException("Not permitted");
        }

        ExpExperimentImpl experiment = getExpExperiment(rowId);
        if(experiment == null)
            return;

        deleteExpExperiment(c, user, experiment);
    }

    private void deleteExpExperiment(Container c, User user, ExpExperimentImpl experiment)
    {
        try (DbScope.Transaction t = ensureTransaction())
        {
            // If we're a batch, delete all the runs too
            if (experiment.getDataObject().getBatchProtocolId() != null)
            {
                for (ExpRunImpl expRun : experiment.getRuns())
                {
                    expRun.delete(user);
                }
            }

            SqlExecutor executor = new SqlExecutor(ExperimentServiceImpl.get().getExpSchema());

            SQLFragment sql = new SQLFragment("DELETE FROM " + ExperimentServiceImpl.get().getTinfoRunList()
                    + " WHERE ExperimentId IN ("
                    + " SELECT E.RowId FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                    + " WHERE E.RowId = " + experiment.getRowId()
                    + " AND E.Container = ? )", experiment.getContainer());
            executor.execute(sql);

            OntologyManager.deleteOntologyObjects(experiment.getContainer(), experiment.getLSID());

            // Inform the listeners.
            for (ExperimentListener listener: _listeners)
            {
                listener.beforeExperimentDeleted(c, user, experiment);
            }

            sql = new SQLFragment("DELETE FROM " + ExperimentServiceImpl.get().getTinfoExperiment()
                    + " WHERE RowId = " + experiment.getRowId()
                    + " AND Container = ?", experiment.getContainer());
            executor.execute(sql);

            t.commit();
        }
    }

    public void deleteAllExpObjInContainer(Container c, User user) throws ExperimentException
    {
        if (null == c)
            return;

        String sql = "SELECT RowId FROM " + getTinfoExperimentRun() + " WHERE Container = ?";
        int[] runIds = ArrayUtils.toPrimitive(new SqlSelector(getExpSchema(), sql, c).getArray(Integer.class));

        List<ExpExperimentImpl> exps = getExperiments(c, user, false, true, true);
        List<ExpSampleSetImpl> sampleSets = getSampleSets(c, user, false);
        List<ExpDataClassImpl> dataClasses = getDataClasses(c, user, false);

        sql = "SELECT RowId FROM " + getTinfoProtocol() + " WHERE Container = ?";
        int[] protIds = ArrayUtils.toPrimitive(new SqlSelector(getExpSchema(), sql, c).getArray(Integer.class));

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            // first delete the runs in the container, as that should be fast.  Deletes all Materials, Data,
            // and protocol applications and associated properties and parameters that belong to the run
            for (int runId : runIds)
            {
                deleteExperimentRunsByRowIds(c, user, runId);
            }
            ListService ls = ListService.get();
            if (ls != null)
            {
                for (ListDefinition list : ListService.get().getLists(c).values())
                {
                    // Temporary fix for Issue 21400: **Deleting workbook deletes lists defined in parent container
                    if (list.getContainer().equals(c))
                    {
                        list.delete(user);
                    }
                }
            }

            // Delete DataClasses and their exp.Data members
            // Need to delete DataClass before SampleSets since they may be referenced by the DataClass
            for (ExpDataClassImpl dataClass : dataClasses)
            {
                dataClass.delete(user);
            }

            // delete all exp.edges referenced by exp.objects in this container
            // These are usually deleted when the run is deleted (unless the run is in a different container)
            // and would be cleaned up when deleting the exp.Material and exp.Data in this container at the end of this method.
            // However, we need to delete any exp.edge referenced by exp.object before calling deleteAllObjects() for this container.
            String deleteObjEdges = "DELETE FROM " + getTinfoEdge() + " WHERE EXISTS " +
                    "(SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = ? " +
                    "AND edge.fromLsid = ObjectUri OR edge.toLsid = ObjectUri)";
            new SqlExecutor(getExpSchema()).execute(deleteObjEdges, c);

            OntologyManager.deleteAllObjects(c, user);
            SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);
            Table.delete(getTinfoDataAliasMap(), containerFilter);
            Table.delete(getTinfoMaterialAliasMap(), containerFilter);
            deleteUnusedAliases(c, user);

            // delete material sources
            // now call the specialized function to delete the Materials that belong to the Material Source,
            // including the toplevel properties of the Materials, of which there are often many
            for (ExpSampleSet sampleSet : sampleSets)
            {
                sampleSet.delete(user);
            }

            Table.delete(getTinfoActiveMaterialSource(), containerFilter);

            // Delete all the experiments/run groups/batches
            for (ExpExperimentImpl exp : exps)
            {
                deleteExpExperiment(c, user, exp);
            }

            // now delete protocols (including their nested actions and parameters.
            deleteProtocolByRowIds(c, user, protIds);

            // now delete starting materials that were not associated with a MaterialSource upload.
            // we get this list now so that it doesn't include all of the run-scoped Materials that were
            // deleted already
            sql = "SELECT RowId FROM exp.Material WHERE Container = ? ;";
            Collection<Integer> matIds = new SqlSelector(getExpSchema(), sql, c).getCollection(Integer.class);
            deleteMaterialByRowIds(user, c, matIds);

            // same drill for data objects
            sql = "SELECT RowId FROM exp.Data WHERE Container = ?";
            Collection<Integer> dataIds = new SqlSelector(getExpSchema(), sql, c).getCollection(Integer.class);
            deleteDataByRowIds(user, c, dataIds);

            transaction.commit();
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e);
        }
    }

    public void moveContainer(Container c, Container oldParent, Container newParent) throws ExperimentException
    {
        if (null == c)
            return;

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            OntologyManager.moveContainer(c, oldParent, newParent);

            // do the same for all of its children
            for (Container ctemp : ContainerManager.getAllChildren(c))
            {
                if (ctemp.equals(c))
                    continue;
                OntologyManager.moveContainer(ctemp, oldParent, newParent);
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public List<ExpDataImpl> getAllDataOwnedByRun(int runId)
    {
        Filter filter = new SimpleFilter(FieldKey.fromParts("RunId"), runId);
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), filter, null).getArrayList(Data.class));
    }

    public void moveRuns(ViewBackgroundInfo info, Container sourceContainer, List<ExpRun> runs) throws IOException
    {
        int[] rowIds = new int[runs.size()];
        for (int i = 0; i < runs.size(); i++)
        {
            rowIds[i] = runs.get(i).getRowId();
        }

        MoveRunsPipelineJob job = new MoveRunsPipelineJob(info, sourceContainer, rowIds, PipelineService.get().findPipelineRoot(info.getContainer()));
        try
        {
            PipelineService.get().queueJob(job);
        }
        catch (PipelineValidationException e)
        {
            throw new IOException(e);
        }
    }


    public String getCacheKey(String lsid)
    {
        return "LSID/" + lsid;
    }

    public void beforeDeleteData(User user, Container container, List<ExpDataImpl> datas) throws ExperimentException
    {
        try
        {
            Map<ExperimentDataHandler, List<ExpData>> handlers = new HashMap<>();
            for (ExpData data : datas)
            {
                ExperimentDataHandler handler = data.findDataHandler();
                List<ExpData> list = handlers.computeIfAbsent(handler, k -> new ArrayList<>());
                list.add(data);
            }
            for (Map.Entry<ExperimentDataHandler, List<ExpData>> entry : handlers.entrySet())
            {
                entry.getKey().beforeDeleteData(entry.getValue());
            }
        }
        catch (ExperimentException e)
        {
            throw UnexpectedException.wrap(e);
        }

        for (ExperimentListener listener : _listeners)
        {
            listener.beforeDataDelete(container, user, datas);
        }
    }

    public void afterDeleteData(User user, Container container, List<ExpDataImpl> datas)
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.afterDataDelete(container, user, datas);
        }
    }

    public void beforeDeleteMaterials(User user, Container container, List<? extends ExpMaterial> materials)
    {
        // Notify that a delete is about to happen
        for (ExperimentListener materialListener : _listeners)
        {
            materialListener.beforeMaterialDelete(materials, container, user);
        }
    }

    public MaterialSource getMaterialSource(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
        return new TableSelector(getTinfoMaterialSource(), filter, null).getObject(MaterialSource.class);
    }

    public String getDefaultSampleSetLsid()
    {
        return new Lsid.LsidBuilder("SampleSource", "Default").toString();
    }


    @Override
    public List<ExpRunImpl> getRunsUsingDatas(List<ExpData> datas)
    {
        if (datas.isEmpty())
            return Collections.emptyList();

        List<Integer> ids = datas.stream().map(ExpData::getRowId).collect(toList());
        return getRunsUsingDataIds(ids);
    }

    public List<ExpRunImpl> getRunsUsingDataIds(List<Integer> ids)
    {
        SimpleFilter.InClause in1 = new SimpleFilter.InClause(FieldKey.fromParts("DataID"), ids);
        SimpleFilter.InClause in2 = new SimpleFilter.InClause(FieldKey.fromParts("RowId"), ids);

        SQLFragment sql = new SQLFragment("SELECT * FROM exp.ExperimentRun WHERE\n" +
                            "RowId IN (SELECT pa.RunId FROM exp.ProtocolApplication pa WHERE pa.RowId IN (\n" +
                            "(SELECT di.TargetApplicationId FROM exp.DataInput di WHERE ");
        sql.append(in1.toSQLFragment(Collections.emptyMap(), getExpSchema().getSqlDialect()));
        sql.append(") UNION (SELECT d.SourceApplicationId FROM exp.Data d WHERE ");
        sql.append(in2.toSQLFragment(Collections.emptyMap(), getExpSchema().getSqlDialect()));
        sql.append("))) ORDER BY Created DESC");

        return ExpRunImpl.fromRuns(new SqlSelector(getExpSchema(), sql).getArrayList(ExperimentRun.class));
    }

    // Get a map of run LSIDs to Roles used by the Data ids.
    public List<Pair<String, String>> getRunsAndRolesUsingDataIds(List<Integer> ids)
    {
        SQLFragment sql = new SQLFragment(
                "SELECT r.LSID, di.Role\n" +
                "FROM exp.ExperimentRun r\n" +
                "INNER JOIN exp.ProtocolApplication pa ON pa.RunId = r.RowId\n" +
                "INNER JOIN exp.DataInput di ON di.targetApplicationId = pa.RowId\n" +
                "WHERE di.dataId ");
        getExpSchema().getSqlDialect().appendInClauseSql(sql, ids);
        sql.append("\n");
        sql.append("OR pa.RowId IN (SELECT d.sourceApplicationID FROM exp.Data d WHERE d.RowId ");
        getExpSchema().getSqlDialect().appendInClauseSql(sql, ids);
        sql.append(")\n");
        sql.append("ORDER BY Created DESC");

        Set<String> runLsids = new HashSet<>();
        List<Pair<String, String>> runsAndRoles = new ArrayList<>(ids.size());
        new SqlSelector(getExpSchema(), sql).forEachMap(row -> {
            String runLsid = (String)row.get("lsid");
            String role = (String)row.get("role");
            runsAndRoles.add(Pair.of(runLsid, role));
            runLsids.add(runLsid);
        });

        assert checkRunsMatch(runLsids, getRunsUsingDataIds(ids));
        return runsAndRoles;
    }

    private boolean checkRunsMatch(Set<String> lsids, List<ExpRunImpl> runs)
    {
        return runs.stream().allMatch(r -> lsids.contains(r.getLSID()));
    }

    @Override
    public List<ExpRunImpl> getRunsUsingMaterials(List<ExpMaterial> materials)
    {
        if (materials.isEmpty())
            return Collections.emptyList();

        int[] ids = materials.stream().mapToInt(ExpMaterial::getRowId).toArray();
        return getRunsUsingMaterials(ids);
    }

    public List<ExpRunImpl> getRunsUsingMaterials(int... ids)
    {
        if (ids.length == 0)
        {
            return Collections.emptyList();
        }

        return ExpRunImpl.fromRuns(getRunsForMaterialList(getExpSchema().getSqlDialect().appendInClauseSql(new SQLFragment(), Arrays.asList(ArrayUtils.toObject(ids)))));
    }

    // Get a map of run LSIDs to Roles used by the Material ids.
    public List<Pair<String, String>> getRunsAndRolesUsingMaterialIds(List<Integer> ids)
    {
        SQLFragment sql = new SQLFragment(
                "SELECT r.LSID, mi.Role\n" +
                "FROM exp.ExperimentRun r\n" +
                "INNER JOIN exp.ProtocolApplication pa ON pa.RunId = r.RowId\n" +
                "INNER JOIN exp.MaterialInput mi ON mi.targetApplicationId = pa.RowId\n" +
                "WHERE mi.materialId ");
        getExpSchema().getSqlDialect().appendInClauseSql(sql, ids);
        sql.append("\n");
        sql.append("OR pa.RowId IN (SELECT m.sourceApplicationID FROM exp.Material m WHERE m.RowId ");
        getExpSchema().getSqlDialect().appendInClauseSql(sql, ids);
        sql.append(")\n");
        sql.append("ORDER BY Created DESC");

        Set<String> runLsids = new HashSet<>();
        List<Pair<String, String>> runsAndRoles = new ArrayList<>(ids.size());
        new SqlSelector(getExpSchema(), sql).forEachMap(row -> {
            String runLsid = (String)row.get("lsid");
            String role = (String)row.get("role");
            runsAndRoles.add(Pair.of(runLsid, role));
            runLsids.add(runLsid);
        });

        assert checkRunsMatch(runLsids, getRunsUsingMaterials(ids.stream().mapToInt(Integer::intValue).toArray()));
        return runsAndRoles;
    }


    public List<? extends ExpRun> runsDeletedWithInput(List<? extends ExpRun> runs)
    {
        List<ExpRun> ret = new ArrayList<>();
        for (ExpRun run : runs)
        {
            ExpProtocol protocol = run.getProtocol();
            if (protocol != null)
            {
                ProtocolImplementation impl = protocol.getImplementation();
                if (impl != null)
                {
                    if (!impl.deleteRunWhenInputDeleted())
                    {
                        continue;
                    }
                }
            }
            ret.add(run);
        }
        return ret;
    }

    @Override
    public List<ExpRunImpl> getRunsUsingDataClasses(Collection<ExpDataClass> dataClasses)
    {
        List<Integer> rowIds = dataClasses.stream().map(ExpObject::getRowId).collect(toList());

        SQLFragment sql = new SQLFragment("IN (SELECT RowId FROM ");
        sql.append(getTinfoData(), "d");
        sql.append(" WHERE d.classId ");
        getExpSchema().getSqlDialect().appendInClauseSql(sql, rowIds);
        sql.append(")");
        return ExpRunImpl.fromRuns(getRunsForDataList(sql));

    }

    private List<ExperimentRun> getRunsForDataList(SQLFragment dataRowIdSQL)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(getTinfoExperimentRun(), "er");
        sql.append(" WHERE \n RowId IN (SELECT RowId FROM ");
        sql.append(getTinfoExperimentRun(), "er2");
        sql.append(" WHERE RowId IN ((SELECT pa.RunId FROM ");
        sql.append(getTinfoProtocolApplication(), "pa");
        sql.append(", ");
        sql.append(getTinfoDataInput(), "di");
        sql.append(" WHERE di.TargetApplicationId = pa.RowId AND di.DataID ");
        sql.append(dataRowIdSQL);
        sql.append(")");
        sql.append("\n UNION \n (SELECT pa.RunId FROM ");
        sql.append(getTinfoProtocolApplication(), "pa");
        sql.append(", ");
        sql.append(getTinfoData(), "d");
        sql.append(" WHERE d.SourceApplicationId = pa.RowId AND d.RowId ");
        sql.append(dataRowIdSQL);
        sql.append(")))");
        return new SqlSelector(getExpSchema(), sql).getArrayList(ExperimentRun.class);
    }

    @Override
    public List<ExpRunImpl> getRunsUsingSampleSets(ExpSampleSet... sources)
    {
        List<String> materialSourceIds = new ArrayList<>(sources.length);
        for (ExpSampleSet source : sources)
        {
            materialSourceIds.add(source.getLSID());
        }

        SQLFragment materialRowIdSQL = new SQLFragment("IN (SELECT RowId FROM ");
        materialRowIdSQL.append(getTinfoMaterial(), "m");
        materialRowIdSQL.append(" WHERE CpasType ");
        getExpSchema().getSqlDialect().appendInClauseSql(materialRowIdSQL, materialSourceIds);
        materialRowIdSQL.append(")");
        return ExpRunImpl.fromRuns(getRunsForMaterialList(materialRowIdSQL));
    }

    private List<ExperimentRun> getRunsForMaterialList(SQLFragment materialRowIdSQL)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(getTinfoExperimentRun(), "er");
        sql.append(" WHERE \n RowId IN (SELECT RowId FROM ");
        sql.append(getTinfoExperimentRun(), "er2");
        sql.append(" WHERE RowId IN ((SELECT pa.RunId FROM ");
        sql.append(getTinfoProtocolApplication(), "pa");
        sql.append(", ");
        sql.append(getTinfoMaterialInput(), "mi");
        sql.append(" WHERE mi.TargetApplicationId = pa.RowId AND mi.MaterialID ");
        sql.append(materialRowIdSQL);
        sql.append(")");
        sql.append("\n UNION \n (SELECT pa.RunId FROM ");
        sql.append(getTinfoProtocolApplication(), "pa");
        sql.append(", ");
        sql.append(getTinfoMaterial(), "m");
        sql.append(" WHERE m.SourceApplicationId = pa.RowId AND m.RowId ");
        sql.append(materialRowIdSQL);
        sql.append(")))");
        return new SqlSelector(getExpSchema(), sql).getArrayList(ExperimentRun.class);
    }

    private void deleteDomainObjects(Container c, String lsid) throws ExperimentException
    {
        //Delete everything the ontology knows about this
        //includes all properties where this is the owner.
        // BUGBUG? What about objects in subfolders?
        OntologyManager.deleteOntologyObjects(c, lsid);
        if (OntologyManager.getDomainDescriptor(lsid, c) != null)
        {
            try
            {
                OntologyManager.deleteType(lsid, c);
            }
            catch (DomainNotFoundException e)
            {
                throw new ExperimentException(e);
            }
        }
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
        TableSelector ts = new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), Sets.newCaseInsensitiveHashSet("container", "rowid"), filter, null);
        ts.forEachMap(row -> byContainer.put((String)row.get("container"), (Integer)row.get("rowid")));

        int count = 0;
        for (Map.Entry<String, Collection<Integer>> entry : byContainer.asMap().entrySet())
        {
            Container container = ContainerManager.getForId(entry.getKey());
            deleteMaterialByRowIds(user, container, entry.getValue());
            count += entry.getValue().size();
        }
        return count;
    }

    public void deleteSampleSet(int rowId, Container c, User user) throws ExperimentException
    {
        ExpSampleSetImpl source = getSampleSet(rowId);
        if (null == source)
            throw new IllegalArgumentException("Can't find SampleSet with rowId " + rowId);
        if (!source.getContainer().equals(c))
            throw new ExperimentException("Trying to delete a SampleSet from a different container");

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            truncateSampleSet(source, user, null);

            deleteDomainObjects(source.getContainer(), source.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("UPDATE " + getTinfoDataClass() + " SET materialSourceId = NULL WHERE materialSourceId = ?", source.getRowId());
            executor.execute("DELETE FROM " + getTinfoActiveMaterialSource() + " WHERE MaterialSourceLSID = ?", source.getLSID());
            executor.execute("DELETE FROM " + getTinfoMaterialSource() + " WHERE RowId = ?", rowId);

            transaction.addCommitTask(() -> uncacheMaterialSource(source.getDataObject()), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT);
            transaction.commit();
        }
        SchemaKey samplesSchema = SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME);
        QueryService.get().fireQueryDeleted(user, c, null, samplesSchema, singleton(source.getName()));

        SchemaKey expMaterialsSchema = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, materials.toString());
        QueryService.get().fireQueryDeleted(user, c, null, expMaterialsSchema, singleton(source.getName()));
    }

    /**
     * Delete all exp.Data from the DataClass.  If container is not provided,
     * all rows from the DataClass will be deleted regardless of container.
     */
    public int truncateDataClass(ExpDataClass dataClass, User user, @Nullable Container c) throws ExperimentException
    {
        assert getExpSchema().getScope().isTransactionActive();

        truncateDataClassAttachments(dataClass);

        SimpleFilter filter = c == null ? new SimpleFilter() : SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("classId"), dataClass.getRowId());

        MultiValuedMap<String, Integer> byContainer = new ArrayListValuedHashMap<>();
        TableSelector ts = new TableSelector(ExperimentServiceImpl.get().getTinfoData(), Sets.newCaseInsensitiveHashSet("container", "rowid"), filter, null);
        ts.forEachMap(row -> byContainer.put((String)row.get("container"), (Integer)row.get("rowid")));

        int count = 0;
        for (Map.Entry<String, Collection<Integer>> entry : byContainer.asMap().entrySet())
        {
            Container container = ContainerManager.getForId(entry.getKey());
            deleteDataByRowIds(user, container, entry.getValue());
            count += entry.getValue().size();
        }
        return count;
    }

    public void deleteDataClass(int rowId, Container c, User user) throws ExperimentException
    {
        ExpDataClass dataClass = getDataClass(rowId);
        if (null == dataClass)
        {
            // this can happen if the DataClass wasn't created completely
            LOG.warn("Can't find DataClass with rowId " + rowId + " for deletion");
            return;
        }
        if (!dataClass.getContainer().equals(c))
            throw new ExperimentException("Trying to delete a DataClass from a different container");

        Domain d = dataClass.getDomain();
        Container dcContainer = dataClass.getContainer();

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            DbSequenceManager.delete(c, ExpDataClassImpl.GENID_SEQUENCE_NAME, dataClass.getRowId());

            truncateDataClass(dataClass, user, null);

            d.delete(user);

            deleteDomainObjects(dcContainer, dataClass.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("DELETE FROM " + getTinfoDataClass() + " WHERE RowId = ?", rowId);

            transaction.commit();
            clearDataClassCache(dcContainer);
        }

        SchemaKey expDataSchema = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString());
        QueryService.get().fireQueryDeleted(user, c, null, expDataSchema, singleton(dataClass.getName()));
    }

    private void deleteUnusedAliases(Container c, User user)
    {
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            SQLFragment sql = new SQLFragment("DELETE FROM ").
                    append(getTinfoAlias(), "").
                    append(" WHERE RowId NOT IN (SELECT Alias FROM ").
                    append(getTinfoDataAliasMap(), "").append(")").
                    append(" AND RowId NOT IN (SELECT Alias FROM ").
                    append(getTinfoMaterialAliasMap(), "").append(")");

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute(sql);

            transaction.commit();
        }
    }

    private void truncateDataClassAttachments(ExpDataClass dataClass)
    {
        if (dataClass != null && dataClass instanceof ExpDataClassImpl)
        {
            if (dataClass.getDomain() != null)
            {
                TableInfo table = ((ExpDataClassImpl) dataClass).getTinfo();

                SQLFragment sql = new SQLFragment()
                        .append("SELECT t.lsid FROM ").append(getTinfoData(), "d")
                        .append(" LEFT OUTER JOIN ").append(table, "t")
                        .append(" ON d.lsid = t.lsid")
                        .append(" WHERE d.Container = ?").add(dataClass.getContainer().getEntityId())
                        .append(" AND d.ClassId = ?").add(dataClass.getRowId());

                List<String> lsids = new SqlSelector(table.getSchema().getScope(), sql).getArrayList(String.class);
                deleteDataClassAttachments(dataClass.getContainer(), lsids);
            }
        }
    }

    public void deleteDataClassAttachments(Container container, List<String> lsids)
    {
        List<AttachmentParent> attachmentParents = new ArrayList<>();

        for (String lsidStr : lsids)
        {
            Lsid lsid = Lsid.parse(lsidStr);
            AttachmentParent parent = new ExpDataClassAttachmentParent(container, lsid);
            attachmentParents.add(parent);
        }

        AttachmentService.get().deleteAttachments(attachmentParents);
    }

    public ExpRunImpl populateRun(final ExpRunImpl expRun)
    {
        //todo cache populated runs
        final Map<Integer, ExpMaterialImpl> outputMaterialMap = new HashMap<>();
        final Map<Integer, ExpDataImpl> outputDataMap = new HashMap<>();

        int runId = expRun.getRowId();
        SimpleFilter filt = new SimpleFilter(FieldKey.fromParts("RunId"), runId);
        Sort sort = new Sort("ActionSequence, RowId");
        List<ExpProtocolApplicationImpl> protocolSteps = ExpProtocolApplicationImpl.fromProtocolApplications(new TableSelector(getTinfoProtocolApplication(), getTinfoProtocolApplication().getColumns(), filt, sort).getArrayList(ProtocolApplication.class));
        expRun.setProtocolApplications(protocolSteps);
        final Map<Integer, ExpProtocolApplicationImpl> protStepMap = new HashMap<>(protocolSteps.size());

        for (ExpProtocolApplicationImpl protocolStep : protocolSteps)
        {
            protStepMap.put(protocolStep.getRowId(), protocolStep);
            protocolStep.setInputMaterials(new ArrayList<>());
            protocolStep.setInputDatas(new ArrayList<>());
            protocolStep.setOutputMaterials(new ArrayList<>());
            protocolStep.setOutputDatas(new ArrayList<>());
        }

        sort = new Sort("RowId");
        List<ExpMaterialImpl> materials = ExpMaterialImpl.fromMaterials(new TableSelector(getTinfoMaterial(), filt, sort).getArrayList(Material.class));
        final Map<Integer, ExpMaterialImpl> runMaterialMap = new HashMap<>(materials.size());

        for (ExpMaterialImpl mat : materials)
        {
            runMaterialMap.put(mat.getRowId(), mat);
            ExpProtocolApplication sourceApplication = mat.getSourceApplication();
            Integer srcAppId = sourceApplication == null ? null : sourceApplication.getRowId();
            assert protStepMap.containsKey(srcAppId);
            protStepMap.get(srcAppId).getOutputMaterials().add(mat);
            mat.markAsPopulated(protStepMap.get(srcAppId));
        }

        List<ExpDataImpl> datas = ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), filt, sort).getArrayList(Data.class));
        final Map<Integer, ExpDataImpl> runDataMap = new HashMap<>(datas.size());

        for (ExpDataImpl dat : datas)
        {
            runDataMap.put(dat.getRowId(), dat);
            Integer srcAppId = dat.getDataObject().getSourceApplicationId();
            assert protStepMap.containsKey(srcAppId);
            protStepMap.get(srcAppId).getOutputDatas().add(dat);
            dat.markAsPopulated(protStepMap.get(srcAppId));
        }

        // get the set of starting materials, which do not belong to the run
        String materialSQL = "SELECT M.* "
                + " FROM " + getTinfoMaterial().getSelectName() + " M "
                + " INNER JOIN " + getExpSchema().getTable("MaterialInput").getSelectName() + " MI "
                + " ON (M.RowId = MI.MaterialId) "
                + " WHERE MI.TargetApplicationId IN "
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA "
                + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExpProtocol.ApplicationType.ExperimentRun + "')"
                + "  ORDER BY RowId ;";
        String materialInputSQL = "SELECT MI.* "
                + " FROM " + getExpSchema().getTable("MaterialInput").getSelectName() + " MI "
                + " WHERE MI.TargetApplicationId IN "
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA "
                + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExpProtocol.ApplicationType.ExperimentRun + "')"
                + "  ORDER BY MI.MaterialId;";

        materials = ExpMaterialImpl.fromMaterials(new SqlSelector(getExpSchema(), materialSQL, runId).getArrayList(Material.class));
        List<MaterialInput> materialInputs = new SqlSelector(getExpSchema(), materialInputSQL, runId).getArrayList(MaterialInput.class);
        assert materials.size() == materialInputs.size();
        final Map<Integer, ExpMaterialImpl> startingMaterialMap = new HashMap<>(materials.size());
        int index = 0;

        for (ExpMaterialImpl mat : materials)
        {
            startingMaterialMap.put(mat.getRowId(), mat);
            MaterialInput input = materialInputs.get(index++);
            expRun.getMaterialInputs().put(mat, input.getRole());
            mat.setSuccessorAppList(new ArrayList<>());
        }

        // and starting data
        String dataSQL = "SELECT D.*"
                + " FROM " + getTinfoData().getSelectName() + " D "
                + " INNER JOIN " + getTinfoDataInput().getSelectName() + " DI "
                + " ON (D.RowId = DI.DataId) "
                + " WHERE DI.TargetApplicationId IN "
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA "
                + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExpProtocol.ApplicationType.ExperimentRun + "')"
                + "  ORDER BY RowId ;";
        String dataInputSQL = "SELECT DI.*"
                + " FROM " + getTinfoDataInput().getSelectName() + " DI "
                + " WHERE DI.TargetApplicationId IN "
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA "
                + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExpProtocol.ApplicationType.ExperimentRun + "')"
                + "  ORDER BY DataId;";

        datas = ExpDataImpl.fromDatas(new SqlSelector(getExpSchema(), dataSQL, runId).getArrayList(Data.class));
        DataInput[] dataInputs = new SqlSelector(getExpSchema(), dataInputSQL, runId).getArray(DataInput.class);
        final Map<Integer, ExpDataImpl> startingDataMap = new HashMap<>(datas.size());
        index = 0;

        for (ExpDataImpl dat : datas)
        {
            startingDataMap.put(dat.getRowId(), dat);
            DataInput input = dataInputs[index++];
            expRun.getDataInputs().put(dat, input.getRole());
            dat.markSuccessorAppsAsPopulated();
        }

        // now hook up material inputs to processes in both directions
        dataSQL = "SELECT TargetApplicationId, MaterialId"
                + " FROM " + getTinfoMaterialInput().getSelectName()
                + " WHERE TargetApplicationId IN"
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA"
                + " WHERE PA.RunId = ?)"
                + " ORDER BY TargetApplicationId, MaterialId";

        new SqlSelector(getExpSchema(), dataSQL, runId).forEach(materialInputRS -> {
            Integer appId = materialInputRS.getInt("TargetApplicationId");
            int matId = materialInputRS.getInt("MaterialId");
            ExpProtocolApplicationImpl pa = protStepMap.get(appId);
            ExpMaterialImpl mat;

            if (runMaterialMap.containsKey(matId))
                mat = runMaterialMap.get(matId);
            else
                mat = startingMaterialMap.get(matId);

            if (mat == null)
            {
                mat = getExpMaterial(matId);
                mat.setSuccessorAppList(new ArrayList<>());
            }

            pa.getInputMaterials().add(mat);
            mat.getSuccessorApps().add(pa);

            if (pa.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput)
            {
                expRun.getMaterialOutputs().add(mat);
                outputMaterialMap.put(mat.getRowId(), mat);
            }
        });

        // now hook up data inputs in both directions
        dataSQL = "SELECT TargetApplicationId, DataId"
                + " FROM " + getTinfoDataInput().getSelectName()
                + " WHERE TargetApplicationId IN"
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA"
                + " WHERE PA.RunId = ?)"
                + " ORDER BY TargetApplicationId, DataId";

        new SqlSelector(getExpSchema(), dataSQL, runId).forEach(dataInputRS -> {
            Integer appId = dataInputRS.getInt("TargetApplicationId");
            Integer datId = dataInputRS.getInt("DataId");
            ExpProtocolApplicationImpl pa = protStepMap.get(appId);
            ExpDataImpl dat;

            if (runDataMap.containsKey(datId))
                dat = runDataMap.get(datId);
            else
                dat = startingDataMap.get(datId);

            if (dat == null)
            {
                dat = getExpData(datId.intValue());
                dat.markSuccessorAppsAsPopulated();
            }

            pa.getInputDatas().add(dat);
            dat.getSuccessorApps().add(pa);

            if (pa.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput)
            {
                expRun.getDataOutputs().add(dat);
                outputDataMap.put(dat.getRowId(), dat);
            }
        });

        //For run summary view, need to know if other ExperimentRuns
        // use the outputs of this run.
        if (!outputMaterialMap.isEmpty())
        {
            SimpleFilter.InClause in = new SimpleFilter.InClause(FieldKey.fromParts("MaterialId"), outputMaterialMap.keySet());

            SQLFragment sql = new SQLFragment();
            sql.append("SELECT TargetApplicationId, MaterialId, PA.RunId"
                    + " FROM " + getTinfoMaterialInput().getSelectName() + " M"
                    + " INNER JOIN " + getTinfoProtocolApplication().getSelectName() + " PA"
                    + " ON M.TargetApplicationId = PA.RowId"
                    + " WHERE ");
            sql.append(in.toSQLFragment(Collections.emptyMap(), getExpSchema().getSqlDialect()));
            sql.append(" AND PA.RunId <> ? ORDER BY TargetApplicationId, MaterialId");
            sql.add(runId);

            new SqlSelector(getExpSchema(), sql).forEach(materialOutputRS -> {
                Integer successorRunId = materialOutputRS.getInt("RunId");
                Integer matId = materialOutputRS.getInt("MaterialId");
                ExpMaterialImpl mat = outputMaterialMap.get(matId);
                mat.addSuccessorRunId(successorRunId);
            });
        }

        if (!outputDataMap.isEmpty())
        {
            List<Integer> dataIds = new ArrayList<>(outputDataMap.keySet());
            int batchSize = 200;

            for (int i = 0; i < dataIds.size(); i += batchSize)
            {
                List<Integer> subset = dataIds.subList(i, Math.min(dataIds.size(), i + batchSize));
                String inClause = StringUtils.join(subset, ", ");
                dataSQL = "SELECT TargetApplicationId, DataId, PA.RunId "
                        + " FROM " + getTinfoDataInput().getSelectName() + " D  "
                        + " INNER JOIN " + getTinfoProtocolApplication().getSelectName() + " PA "
                        + " ON D.TargetApplicationId = PA.RowId "
                        + " WHERE DataId IN ( " + inClause + " ) "
                        + " AND PA.RunId <> ? "
                        + " ORDER BY TargetApplicationId, DataId ;";

                new SqlSelector(getExpSchema(), dataSQL, runId).forEach(dataOutputRS -> {
                    int successorRunId = dataOutputRS.getInt("RunId");
                    Integer datId = dataOutputRS.getInt("DataId");
                    ExpDataImpl dat = outputDataMap.get(datId);
                    dat.addSuccessorRunId(successorRunId);
                });
            }
        }

        return expRun;
    }


    public List<ProtocolActionPredecessor> getProtocolActionPredecessors(String parentProtocolLSID, String childProtocolLSID)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ChildProtocolLSID"), childProtocolLSID);
        filter.addCondition(FieldKey.fromParts("ParentProtocolLSID"), parentProtocolLSID);
        return new TableSelector(getTinfoProtocolActionPredecessorLSIDView(), filter, new Sort("+PredecessorSequence")).getArrayList(ProtocolActionPredecessor.class);
    }

    public List<Data> getOutputDataForApplication(int applicationId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceApplicationId"), applicationId);
        return new TableSelector(getTinfoData(), filter, null).getArrayList(Data.class);
    }

    public List<DataInput> getDataOutputsForApplication(int applicationId)
    {
        return new SqlSelector(getExpSchema(), "SELECT di.* FROM exp.DataInput di\n" +
                "INNER JOIN exp.Data d ON d.rowId = di.dataId\n" +
                "WHERE d.sourceApplicationId = ?", applicationId).getArrayList(DataInput.class);
    }

    public List<Material> getOutputMaterialForApplication(int applicationId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceApplicationId"), applicationId);
        return new TableSelector(getTinfoMaterial(), filter, null).getArrayList(Material.class);
    }

    public List<MaterialInput> getMaterialOutputsForApplication(int applicationId)
    {
        return new SqlSelector(getExpSchema(), "SELECT mi.* FROM exp.MaterialInput mi\n" +
                "INNER JOIN exp.Material m ON m.rowId = mi.materialId\n" +
                "WHERE m.sourceApplicationId = ?", applicationId).getArrayList(MaterialInput.class);
    }

    public List<ExpDataImpl> getExpData(Container c)
    {
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), SimpleFilter.createContainerFilter(c), null).getArrayList(Data.class));
    }

    public List<Data> getDataInputReferencesForApplication(int rowId)
    {
        String outputSQL = "SELECT exp.Data.* from exp.Data, exp.DataInput " +
                "WHERE exp.Data.RowId = exp.DataInput.DataId " +
                "AND exp.DataInput.TargetApplicationId = ?";
        return new SqlSelector(getExpSchema(), outputSQL, rowId).getArrayList(Data.class);
    }

    public List<DataInput> getDataInputsForApplication(int applicationId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("TargetApplicationId"), applicationId);
        return new TableSelector(getTinfoDataInput(), filter, null).getArrayList(DataInput.class);
    }

    public List<Material> getMaterialInputReferencesForApplication(int rowId)
    {
        String outputSQL = "SELECT exp.Material.* from exp.Material, exp.MaterialInput " +
                "WHERE exp.Material.RowId = exp.MaterialInput.MaterialId " +
                "AND exp.MaterialInput.TargetApplicationId = ?";
        return new SqlSelector(getExpSchema(), outputSQL, rowId).getArrayList(Material.class);
    }

    public List<MaterialInput> getMaterialInputsForApplication(int applicationId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("TargetApplicationId"), applicationId);
        return new TableSelector(getTinfoMaterialInput(), filter, null).getArrayList(MaterialInput.class);
    }

    public List<ProtocolApplicationParameter> getProtocolApplicationParameters(int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProtocolApplicationId"), rowId);
        return new TableSelector(getTinfoProtocolApplicationParameter(), filter, null).getArrayList(ProtocolApplicationParameter.class);
    }

    public ProtocolActionStepDetail getProtocolActionStepDetail(String parentProtocolLSID, Integer actionSequence) throws XarFormatException
    {
        String cmdSql = "SELECT * FROM exp.ProtocolActionStepDetailsView "
                + " WHERE ParentProtocolLSID = ? "
                + " AND Sequence = ? "
                + " ORDER BY Sequence";

        ProtocolActionStepDetail[] details = new SqlSelector(getExpSchema(), cmdSql, parentProtocolLSID, actionSequence).getArray(ProtocolActionStepDetail.class);
        if (details.length == 0)
        {
            return null;
        }
        assert (details.length == 1);
        return details[0];
    }

    public List<ExpProtocolApplicationImpl> getExpProtocolApplicationsForProtocolLSID(String protocolLSID)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProtocolLSID"), protocolLSID);
        return ExpProtocolApplicationImpl.fromProtocolApplications(new TableSelector(getTinfoProtocolApplication(), filter, null).getArrayList(ProtocolApplication.class));
    }

    public Protocol saveProtocol(User user, Protocol protocol)
    {
        return saveProtocol(user, protocol, true);
    }

    // saveProperties is exposed due to how the transactions are handled for setting properties on protocols.
    // If a protocol has already had protocol.setProperty() called on it then the properties will have already
    // been saved to the database. The result is that it can cause the save to fail if this API attempts to save
    // the properties again. The only current recourse is for the caller to enforce their own transaction boundaries
    // using ensureTransaction().
    public Protocol saveProtocol(User user, Protocol protocol, boolean saveProperties)
    {
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            Protocol result;
            boolean newProtocol = protocol.getRowId() == 0;
            if (newProtocol)
            {
                result = Table.insert(user, getTinfoProtocol(), protocol);
            }
            else
            {
                result = Table.update(user, getTinfoProtocol(), protocol, protocol.getRowId());
                uncacheProtocol(protocol);
            }

            Collection<ProtocolParameter> protocolParams = protocol.retrieveProtocolParameters().values();
            if (!newProtocol)
            {
                new SqlExecutor(getExpSchema()).execute("DELETE FROM exp.ProtocolParameter WHERE ProtocolId = ?", protocol.getRowId());
            }
            for (ProtocolParameter param : protocolParams)
            {
                param.setProtocolId(result.getRowId());
                loadParameter(user, param, getTinfoProtocolParameter(), FieldKey.fromParts("ProtocolId"), protocol.getRowId());
            }

            if (saveProperties)
                savePropertyCollection(protocol.retrieveObjectProperties(), protocol.getLSID(), protocol.getContainer(), !newProtocol);
            AssayService.get().clearProtocolCache();

            getExpSchema().getScope().addCommitTask(() -> {
                // Be sure that we clear the cache after we commit the overall transaction, in case it
                // gets repopulated by another thread before then
                AssayService.get().clearProtocolCache();
            }, POSTCOMMIT);

            transaction.commit();
            return result;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void loadParameter(User user, AbstractParameter param,
                                   TableInfo tiValueTable,
                                   FieldKey pkName, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(pkName, rowId);
        filter.addCondition(FieldKey.fromParts("OntologyEntryURI"), param.getOntologyEntryURI());
        Map<String, Object> existingValue = new TableSelector(tiValueTable, filter, null).getMap();

        if (existingValue == null)
        {
            Table.insert(user, tiValueTable, param);
        }
        else
        {
            throw new RuntimeSQLException(new SQLException("Duplicate " + tiValueTable.getSelectName() + " value, filter= " + filter + ". Existing parameter is " + existingValue + ", new value is " + param.getValue()));
        }
    }

    public void savePropertyCollection(Map<String, ObjectProperty> propMap, String ownerLSID, Container container, boolean clearExisting) throws SQLException
    {
        if (propMap.size() == 0)
            return;
        ObjectProperty[] props = propMap.values().toArray(new ObjectProperty[propMap.values().size()]);
        // Todo - make this more efficient - don't delete all the old ones if they're the same
        if (clearExisting)
        {
            OntologyManager.deleteOntologyObjects(container, ownerLSID);
            for (ObjectProperty prop : propMap.values())
            {
                prop.setObjectId(0);
            }
        }
        try {
            OntologyManager.insertProperties(container, ownerLSID, props);
            for (ObjectProperty prop : props)
            {
                Map<String, ObjectProperty> childProps = prop.retrieveChildProperties();
                if (childProps != null)
                {
                    savePropertyCollection(childProps, ownerLSID, container, false);
                }
            }
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    public void insertProtocolPredecessor(User user, int actionRowId, int predecessorRowId) throws SQLException
    {
        Map<String, Object> mValsPredecessor = new HashMap<>();
        mValsPredecessor.put("ActionId", actionRowId);
        mValsPredecessor.put("PredecessorId", predecessorRowId);

        Table.insert(user, getTinfoProtocolActionPredecessor(), mValsPredecessor);
    }

    public ExpRun getCreatingRun(File file, Container c)
    {
        ExpDataImpl data = getExpDataByURL(file, c);
        if (data != null)
        {
            return data.getRun();
        }
        return null;
    }

    public static ExperimentServiceImpl get()
    {
        return (ExperimentServiceImpl)ExperimentService.get();
    }

    /** @return all the Data objects from this run */
    private List<ExpData> ensureSimpleExperimentRunParameters(Collection<ExpMaterial> inputMaterials,
                                                     Collection<ExpData> inputDatas, Collection<ExpMaterial> outputMaterials,
                                                     Collection<ExpData> outputDatas, Collection<ExpData> transformedDatas, User user) throws SQLException
    {
        // Save all the input and output objects to make sure they've been inserted
        saveAll(inputMaterials, user);
        saveAll(inputDatas, user);
        saveAll(outputMaterials, user);
        saveAll(outputDatas, user);
        saveAll(transformedDatas, user);

        List<ExpData> result = new ArrayList<>();
        if (transformedDatas.isEmpty())
        {
            result.addAll(inputDatas);
            result.addAll(outputDatas);
        }
        else
            result.addAll(transformedDatas);
        return result;
    }

    private void saveAll(Iterable<? extends ExpObject> objects, User user)
    {
        for (ExpObject object : objects)
        {
            object.save(user);
        }
    }

    public ExpRun saveSimpleExperimentRun(ExpRun baseRun, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials,
                                            Map<ExpData, String> outputDatas, Map<ExpData, String> transformedDatas, ViewBackgroundInfo info, Logger log, boolean loadDataFiles) throws ExperimentException
    {
        ExpRunImpl run = (ExpRunImpl)baseRun;

        if (run.getFilePathRootPath() == null)
        {
            throw new IllegalArgumentException("You must set the file path root on the experiment run");
        }

        List<ExpData> insertedDatas;
        User user = info.getUser();
        XarContext context = new XarContext("Simple Run Creation", run.getContainer(), user);

        try
        {
            try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction(XAR_IMPORT_LOCK))
            {
                if (run.getContainer() == null)
                {
                    run.setContainer(info.getContainer());
                }
                run.save(user);
                insertedDatas = ensureSimpleExperimentRunParameters(inputMaterials.keySet(), inputDatas.keySet(), outputMaterials.keySet(), outputDatas.keySet(), transformedDatas.keySet(), user);

                // add any transformed data to the outputDatas collection
                for (Map.Entry<ExpData, String> entry : transformedDatas.entrySet())
                    outputDatas.put(entry.getKey(), entry.getValue());

                ExpProtocolImpl parentProtocol = run.getProtocol();

                List<ProtocolAction> actions = getProtocolActions(parentProtocol.getRowId());
                if (actions.size() != 3)
                {
                    throw new IllegalArgumentException("Protocol has the wrong number of steps for a simple protocol, it should have three");
                }
                ProtocolAction action1 = actions.get(0);
                assert action1.getSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE;
                assert action1.getChildProtocolId() == parentProtocol.getRowId();

                context.addSubstitution("ExperimentRun.RowId", Integer.toString(run.getRowId()));

                Date date = new Date();

                ProtocolAction action2 = actions.get(1);
                assert action2.getSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE;
                ExpProtocol protocol2 = getExpProtocol(action2.getChildProtocolId());

                ProtocolAction action3 = actions.get(2);
                assert action3.getSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE;
                ExpProtocol outputProtocol = getExpProtocol(action3.getChildProtocolId());
                assert outputProtocol.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput : "Expected third protocol to be of type ExperimentRunOutput but was " + outputProtocol.getApplicationType();

                ExpProtocolApplicationImpl protApp1 = new ExpProtocolApplicationImpl(new ProtocolApplication());
                ExpProtocolApplicationImpl protApp2 = new ExpProtocolApplicationImpl(new ProtocolApplication());
                ExpProtocolApplicationImpl protApp3 = new ExpProtocolApplicationImpl(new ProtocolApplication());

                for (ExpProtocolApplicationImpl existingProtApp : run.getProtocolApplications())
                {
                    if (existingProtApp.getProtocol().equals(parentProtocol) && existingProtApp.getActionSequence() == action1.getSequence())
                    {
                        protApp1 = existingProtApp;
                    }
                    else if (existingProtApp.getProtocol().equals(protocol2))
                    {
                        if (existingProtApp.getActionSequence() == SIMPLE_PROTOCOL_EXTRA_STEP_SEQUENCE)
                        {
                            existingProtApp.delete(user);
                        }
                        else if (existingProtApp.getActionSequence() == action2.getSequence())
                        {
                            protApp2 = existingProtApp;
                        }
                        else
                        {
                            throw new IllegalStateException("Unexpected existing protocol application: " + existingProtApp.getLSID() + " with sequence " + existingProtApp.getActionSequence());
                        }
                    }
                    else if (existingProtApp.getProtocol().equals(outputProtocol) && existingProtApp.getActionSequence() == action3.getSequence())
                    {
                        protApp3 = existingProtApp;
                    }
                    else
                    {
                        throw new IllegalStateException("Unexpected existing protocol application: " + existingProtApp.getLSID() + " with sequence " + existingProtApp.getActionSequence());
                    }
                }

                initializeProtocolApplication(protApp1, date, action1, run, parentProtocol, context);
                protApp1.save(user);
                addDataInputs(inputDatas, protApp1._object, user);
                addMaterialInputs(inputMaterials, protApp1._object, user);

                initializeProtocolApplication(protApp2, date, action2, run, protocol2, context);
                protApp2.save(user);
                addDataInputs(inputDatas, protApp2._object, user);
                addMaterialInputs(inputMaterials, protApp2._object, user);

                for (ExpMaterial outputMaterial : outputMaterials.keySet())
                {
                    if (outputMaterial.getSourceApplication() != null)
                    {
                        throw new IllegalArgumentException("Output material " + outputMaterial.getName() + " is already marked as being created by another protocol application");
                    }
                    if (outputMaterial.getRun() != null)
                    {
                        throw new IllegalArgumentException("Output material " + outputMaterial.getName() + " is already marked as being created by another run");
                    }
                    outputMaterial.setSourceApplication(protApp2);
                    outputMaterial.setRun(run);
                    Table.update(user, getTinfoMaterial(), ((ExpMaterialImpl)outputMaterial)._object, outputMaterial.getRowId());
                }

                for (ExpData outputData : outputDatas.keySet())
                {
                    ExpRun existingRun = outputData.getRun();
                    if (existingRun != null && !existingRun.equals(run))
                    {
                        throw new ExperimentException("Output data " + outputData.getName() + " (RowId " + outputData.getRowId() + ") is already marked as being created by another run '" + outputData.getRun().getName() + "' (RowId " + outputData.getRunId() + ")");
                    }
                    ExpProtocolApplication existingProtApp = outputData.getSourceApplication();
                    if (existingProtApp != null && !existingProtApp.equals(protApp2))
                    {
                        throw new ExperimentException("Output data " + outputData.getName() + " (RowId " + outputData.getRowId() + ") is already marked as being created by another protocol application");
                    }
                    outputData.setSourceApplication(protApp2);
                    outputData.setRun(run);
                    Table.update(user, getTinfoData(), ((ExpDataImpl)outputData).getDataObject(), outputData.getRowId());
                }

                initializeProtocolApplication(protApp3, date, action3, run, outputProtocol, context);
                protApp3.save(user);
                addDataInputs(outputDatas, protApp3._object, user);
                addMaterialInputs(outputMaterials, protApp3._object, user);

                transaction.commit();
            }

            if (loadDataFiles)
            {
                for (ExpData insertedData : insertedDatas)
                {
                    insertedData.findDataHandler().importFile(getExpData(insertedData.getRowId()), insertedData.getFile(), info, log, context);
                }
            }

            run.clearCache();

            syncRunEdges(run);

            return run;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private ExpProtocolApplication initializeProtocolApplication(ExpProtocolApplication protApp, Date activityDate, ProtocolAction action, ExpRun run, ExpProtocol parentProtocol, XarContext context ) throws XarFormatException
    {
        protApp.setActivityDate(activityDate);
        protApp.setActionSequence(action.getSequence());
        protApp.setRun(run);
        protApp.setProtocol(parentProtocol);
        Map<String, ProtocolParameter> parentParams = parentProtocol.getProtocolParameters();
        ProtocolParameter parentLSIDTemplateParam = parentParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
        ProtocolParameter parentNameTemplateParam = parentParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
        assert parentLSIDTemplateParam != null : "Parent LSID Template was null";
        assert parentNameTemplateParam != null : "Parent Name Template was null";
        protApp.setLSID(LsidUtils.resolveLsidFromTemplate(parentLSIDTemplateParam.getStringValue(), context, "ProtocolApplication"));
        protApp.setName(parentNameTemplateParam.getStringValue());

        return protApp;
    }

    @Override
    public ExpProtocolApplication createSimpleRunExtraProtocolApplication(ExpRun run, String name)
    {
        ExpProtocol protocol = run.getProtocol();
        List<? extends ExpProtocol> childProtocols = protocol.getChildProtocols();
        if (childProtocols.size() != 3)
        {
            throw new IllegalArgumentException("Expected to be called for a protocol with three steps, but found " + childProtocols.size());
        }
        Lsid.LsidBuilder builder = new Lsid.LsidBuilder(ExpProtocol.ApplicationType.ProtocolApplication.name(),"");
        for (ExpProtocol childProtocol : childProtocols)
        {
            if (childProtocol.getApplicationType() == ExpProtocol.ApplicationType.ProtocolApplication)
            {
                ExpProtocolApplicationImpl result = new ExpProtocolApplicationImpl(new ProtocolApplication());
                result.setProtocol(childProtocol);
                result.setLSID(builder.setObjectId(GUID.makeGUID()).build());
                result.setActionSequence(SIMPLE_PROTOCOL_EXTRA_STEP_SEQUENCE);
                result.setRun(run);
                result.setName(name);
                return result;
            }
        }
        throw new IllegalArgumentException("Could not find childProtocol of type " + ExpProtocol.ApplicationType.ProtocolApplication);
    }

    @Override
    public ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException
    {
        return derive(inputMaterials, Collections.emptyMap(), outputMaterials, Collections.emptyMap(), info, log);
    }

    @Override
    public void deriveSamplesBulk(List<? extends SimpleRunRecord> runRecords, ViewBackgroundInfo info, Logger log) throws ExperimentException
    {
        final int MAX_RUNS_IN_BATCH = 1000;
        int count = 0;

        User user = info.getUser();
        XarContext context = new XarContext("Simple Run Creation", info.getContainer(), user);
        Date date = new Date();
        DeriveSamplesBulkHelper helper = new DeriveSamplesBulkHelper(info.getContainer(), context);

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            for (SimpleRunRecord runRecord : runRecords)
            {
                count++;
                ExpRunImpl run = createRun(runRecord.getInputMaterialMap(), runRecord.getInputDataMap(), runRecord.getOutputMaterialMap(), runRecord.getOutputDataMap(), info);

                helper.addRunParams(run._object, user.getUserId());

                // protocol applications
                ExpProtocol parentProtocol = run.getProtocol();

                List<ProtocolAction> actions = getProtocolActions(parentProtocol.getRowId());
                if (actions.size() != 3)
                {
                    throw new IllegalArgumentException("Protocol has the wrong number of steps for a simple protocol, it should have three");
                }
                ProtocolAction action1 = actions.get(0);
                assert action1.getSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE;
                assert action1.getChildProtocolId() == parentProtocol.getRowId();

                ProtocolAction action2 = actions.get(1);
                assert action2.getSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE;
                ExpProtocol protocol2 = getExpProtocol(action2.getChildProtocolId());

                ProtocolAction action3 = actions.get(2);
                assert action3.getSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE;
                ExpProtocol outputProtocol = getExpProtocol(action3.getChildProtocolId());
                assert outputProtocol.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput : "Expected third protocol to be of type ExperimentRunOutput but was " + outputProtocol.getApplicationType();

                ExpProtocolApplicationImpl protApp1 = new ExpProtocolApplicationImpl(new ProtocolApplication());
                ExpProtocolApplicationImpl protApp2 = new ExpProtocolApplicationImpl(new ProtocolApplication());
                ExpProtocolApplicationImpl protApp3 = new ExpProtocolApplicationImpl(new ProtocolApplication());

                helper.addProtocolApp(protApp1, date, action1, parentProtocol, run, runRecord);

                helper.addProtocolApp(protApp2, date, action2, protocol2, run, runRecord);

                helper.addProtocolApp(protApp3, date, action3, outputProtocol, run, runRecord);

                if ((count % MAX_RUNS_IN_BATCH) == 0)
                {
                    helper.saveBatch();
                }
            }

            // process the rest of the list
            if (!helper.isEmpty())
            {
                helper.saveBatch();
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
    }

    private class DeriveSamplesBulkHelper
    {
        private Container _container;
        private XarContext _context;

        private List<List<?>> _runParams;
        private List<List<?>> _protAppParams;
        private List<ProtocolAppRecord> _protAppRecords;
        private List<List<?>> _materialInputParams;
        private List<List<?>> _dataInputParams;

        public DeriveSamplesBulkHelper(Container container, XarContext context)
        {
            _container = container;
            _context = context;
            resetState();
        }

        private void resetState()
        {
            _runParams = new ArrayList<>();
            _protAppParams = new ArrayList<>();
            _protAppRecords = new ArrayList<>();
            _materialInputParams = new ArrayList<>();
            _dataInputParams = new ArrayList<>();
        }

        public void addRunParams(ExperimentRun run, int userId)
        {
            Timestamp ts = new Timestamp(System.currentTimeMillis());
            _runParams.add(Arrays.asList(
                    run.getLSID(),
                    run.getName(),
                    run.getProtocolLSID(),
                    run.getFilePathRoot(),
                    GUID.makeGUID(),
                    ts,
                    userId,
                    ts,
                    userId));
        }

        public void addProtocolApp(ExpProtocolApplicationImpl protApp, Date activityDate, ProtocolAction action, ExpProtocol protocol,
                                   ExpRun run, SimpleRunRecord runRecord)
        {
            _protAppRecords.add(new ProtocolAppRecord(protApp, activityDate, action, protocol, run, runRecord));
        }

        public void saveBatch() throws SQLException, XarFormatException
        {
            // insert into the experimentrun table
            Map<String, Integer> runLsidToRowId = saveExpRunsBatch(_container, _runParams);

            // insert into the protocolapplication table
            createProtocolAppParams(_container, _protAppRecords, _protAppParams, _context, runLsidToRowId);
            saveExpProtocolApplicationBatch(_protAppParams);

            // insert into the materialinput table
            createMaterialInputParams(_protAppRecords, _materialInputParams);
            saveExpMaterialInputBatch(_materialInputParams);
            saveExpMaterialOutputs(_protAppRecords);
            createDataInputParams(_protAppRecords, _dataInputParams);
            saveExpDataInputBatch(_dataInputParams);
            saveExpDataOutputs(_protAppRecords);

            // clear the stored records
            resetState();

            uncacheLineageGraph();

            syncRunEdges(runLsidToRowId.values());
        }

        public boolean isEmpty()
        {
            return _runParams.isEmpty();
        }

        private Map<String, Integer> saveExpRunsBatch(Container c, List<List<?>> params) throws SQLException
        {
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(ExperimentServiceImpl.get().getTinfoExperimentRun().toString()).
                    append(" (Lsid, Name, ProtocolLsid, FilePathRoot, EntityId, Created, CreatedBy, Modified, ModifiedBy, Container) VALUES (?,?,?,?,?,?,?,?,?, '").
                    append(c.getId()).append("')");

            Table.batchExecute(getExpSchema(), sql.toString(), params);

            List<String> runLsids = _runParams.stream().map(p -> (String) p.get(0)).collect(toList());
            SimpleFilter filter = new SimpleFilter("LSID", runLsids, IN);
            return new TableSelector(getTinfoExperimentRun(), getTinfoExperimentRun().getColumns("LSID", "RowId"), filter, null).getValueMap();
        }

        /**
         * Replace the placeholder run id with the actual run id
         */
        private void createProtocolAppParams(Container c, List<ProtocolAppRecord> protAppRecords, List<List<?>> protAppParams, XarContext context, Map<String, Integer> runLsidToRowId) throws XarFormatException
        {
            for (ProtocolAppRecord rec : protAppRecords)
            {
                assert runLsidToRowId.containsKey(rec._run.getLSID());

                Integer runId = runLsidToRowId.get(rec._run.getLSID());
                context.addSubstitution("ExperimentRun.RowId", Integer.toString(runId));

                initializeProtocolApplication(rec._protApp, rec._activityDate, rec._action, rec._run, rec._protocol, context);
                rec._protApp._object.setRunId(runId);

                protAppParams.add(Arrays.asList(
                        rec._protApp._object.getName(),
                        rec._protApp._object.getCpasType(),
                        rec._protApp._object.getProtocolLSID(),
                        rec._protApp._object.getActivityDate(),
                        runId,
                        rec._protApp._object.getActionSequence(),
                        rec._protApp._object.getLSID()));
            }
        }

        private void createMaterialInputParams(List<ProtocolAppRecord> protAppRecords, List<List<?>> materialInputParams)
        {
            // get the protocol application rows id's
            Map<String, Integer> protAppRowMap = new HashMap<>();

            for (ProtocolAppRecord rec : protAppRecords)
                protAppRowMap.put(rec._protApp.getLSID(), null);

            new TableSelector(getTinfoProtocolApplication(), new SimpleFilter(FieldKey.fromParts("LSID"), protAppRowMap.keySet(), IN), null).forEach(rs ->
            {
                if (protAppRowMap.containsKey(rs.getString("Lsid")))
                    protAppRowMap.put(rs.getString("Lsid"), rs.getInt("RowId"));
            });

            for (ProtocolAppRecord rec : protAppRecords)
            {
                assert protAppRowMap.containsKey(rec._protApp.getLSID());

                Integer rowId = protAppRowMap.get(rec._protApp.getLSID());
                rec._protApp._object.setRowId(rowId);

                // wire the input materials to the protocol inputs for actions 1&2
                if (rec._action.getSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE ||
                        rec._action.getSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
                {
                    // optimize, should be only 1 material input
                    for (Map.Entry<ExpMaterial, String> entry : rec._runRecord.getInputMaterialMap().entrySet())
                    {
                        materialInputParams.add(Arrays.asList(
                                entry.getKey().getRowId(),
                                rowId,
                                entry.getValue()));
                    }
                }
                // wire the output materials to the protocol input for the last action
                else if (rec._action.getSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE)
                {
                    for (Map.Entry<ExpMaterial, String> entry : rec._runRecord.getOutputMaterialMap().entrySet())
                    {
                        materialInputParams.add(Arrays.asList(
                                entry.getKey().getRowId(),
                                rowId,
                                entry.getValue()));
                    }
                }
            }
        }

        private void createDataInputParams(List<ProtocolAppRecord> protAppRecords, List<List<?>> dataInputParams)
        {
            for (ProtocolAppRecord rec : protAppRecords)
            {
                if (rec._action.getSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE ||
                        rec._action.getSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE)
                {
                    // optimize, should be only 1 material input
                    for (Map.Entry<ExpData, String> entry : rec._runRecord.getInputDataMap().entrySet())
                    {
                        dataInputParams.add(Arrays.asList(
                                entry.getValue(),
                                entry.getKey().getRowId(),
                                rec._protApp.getRowId()
                        ));
                    }
                }
                // wire the output materials to the protocol input for the last action
                else if (rec._action.getSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE)
                {
                    for (Map.Entry<ExpData, String> entry : rec._runRecord.getOutputDataMap().entrySet())
                    {
                        dataInputParams.add(Arrays.asList(
                                entry.getValue(),
                                entry.getKey().getRowId(),
                                rec._protApp.getRowId()
                        ));
                    }
                }
            }
        }

        private void saveExpMaterialOutputs(List<ProtocolAppRecord> protAppRecords)
        {
            for (ProtocolAppRecord rec : protAppRecords)
            {
                if (rec._action.getSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
                {
                    for (ExpMaterial outputMaterial : rec._runRecord.getOutputMaterialMap().keySet())
                    {
                        SQLFragment sql = new SQLFragment("UPDATE ").append(getTinfoMaterial(), "").
                                append(" SET SourceApplicationId = ?, RunId = ? WHERE RowId = ?");

                        sql.addAll(rec._protApp.getRowId(), rec._protApp._object.getRunId(), outputMaterial.getRowId());

                        new SqlExecutor(getTinfoMaterial().getSchema()).execute(sql);
                    }
                }
            }
        }

        private void saveExpDataOutputs(List<ProtocolAppRecord> protAppRecords)
        {
            for (ProtocolAppRecord rec : protAppRecords)
            {
                if (rec._action.getSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
                {
                    for (ExpData outputData : rec._runRecord.getOutputDataMap().keySet())
                    {
                        SQLFragment sql = new SQLFragment("UPDATE ").append(getTinfoData(), "").
                                append(" SET SourceApplicationId = ?, RunId = ? WHERE RowId = ?");

                        sql.addAll(rec._protApp.getRowId(), rec._protApp._object.getRunId(), outputData.getRowId());

                        new SqlExecutor(getTinfoMaterial().getSchema()).execute(sql);
                    }
                }
            }
        }

        private void saveExpMaterialInputBatch(List<List<?>> params) throws SQLException
        {
            if (!params.isEmpty())
            {
                StringBuilder sql = new StringBuilder("INSERT INTO ").append(ExperimentServiceImpl.get().getTinfoMaterialInput().toString()).
                        append(" (MaterialId, TargetApplicationId, Role)").
                        append(" VALUES (?,?,?)");

                Table.batchExecute(getExpSchema(), sql.toString(), params);
            }
        }

        private void saveExpProtocolApplicationBatch(List<List<?>> params) throws SQLException
        {
            if (!params.isEmpty())
            {
                StringBuilder sql = new StringBuilder("INSERT INTO ").append(ExperimentServiceImpl.get().getTinfoProtocolApplication().toString()).
                        append(" (Name, CpasType, ProtocolLsid, ActivityDate, RunId, ActionSequence, Lsid)").
                        append(" VALUES (?,?,?,?,?,?,?)");

                Table.batchExecute(getExpSchema(), sql.toString(), params);
            }
        }

        private void saveExpDataInputBatch(List<List<?>> params) throws SQLException
        {
            if (!params.isEmpty())
            {
                StringBuilder sql = new StringBuilder("INSERT INTO ").append(ExperimentServiceImpl.get().getTinfoDataInput().toString()).
                        append(" (Role, DataId, TargetApplicationId)").
                        append(" VALUES (?,?,?)");

                Table.batchExecute(getExpSchema(), sql.toString(), params);
            }
        }

        private class ProtocolAppRecord
        {
            ExpProtocolApplicationImpl _protApp;
            Date _activityDate;
            ProtocolAction _action;
            ExpProtocol _protocol;
            ExpRun _run;
            SimpleRunRecord _runRecord;

            public ProtocolAppRecord(ExpProtocolApplicationImpl protApp, Date activityDate, ProtocolAction action, ExpProtocol protocol,
                                     ExpRun run, SimpleRunRecord runRecord)
            {
                _protApp = protApp;
                _activityDate = activityDate;
                _action = action;
                _protocol = protocol;
                _run = run;
                _runRecord = runRecord;
            }
        }
    }

    @Override
    public ExpRun derive(Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas,
                                Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas,
                                ViewBackgroundInfo info, Logger log)
            throws ExperimentException
    {
        ExpRun run = createRun(inputMaterials, inputDatas, outputMaterials, outputDatas,info);
        return saveSimpleExperimentRun(run, inputMaterials, inputDatas, outputMaterials, outputDatas,
                Collections.emptyMap(), info, log, false);
    }

    private ExpRunImpl createRun(Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas,
                         Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, ViewBackgroundInfo info) throws ExperimentException
    {
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());
        if (pipeRoot == null || !pipeRoot.isValid())
            throw new ExperimentException("The child folder, " + info.getContainer().getPath() + ", must have a valid pipeline root");

        if (outputDatas.isEmpty() && outputMaterials.isEmpty())
            throw new IllegalArgumentException("You must derive at least one child data or material");

        if (inputDatas.isEmpty() && inputMaterials.isEmpty())
            throw new IllegalArgumentException("You must derive from at least one parent data or material");

        for (ExpData expData : inputDatas.keySet())
        {
            if (outputDatas.containsKey(expData))
                throw new ExperimentException("The data " + expData.getName() + " cannot be an input to its own derivation.");
        }

        for (ExpMaterial expMaterial : inputMaterials.keySet())
        {
            if (outputMaterials.containsKey(expMaterial))
                throw new ExperimentException("The material " + expMaterial.getName() + " cannot be an input to its own derivation.");
        }

        StringBuilder name = new StringBuilder("Derive ");
        if (outputDatas.isEmpty())
        {
            if (outputMaterials.size() == 1)
                name.append(" sample ");
            else
                name.append(outputMaterials.size()).append(" samples ");
        }
        else if (outputMaterials.isEmpty())
        {
            if (outputDatas.size() == 1)
                name.append(" data ");
            else
                name.append(outputDatas.size()).append(" data ");
        }
        name.append("from ");
        String nameSeparator = "";

        for (ExpData data : inputDatas.keySet())
        {
            name.append(nameSeparator);
            name.append(data.getName());
            nameSeparator = ", ";
        }

        for (ExpMaterial material : inputMaterials.keySet())
        {
            name.append(nameSeparator);
            name.append(material.getName());
            nameSeparator = ", ";
        }

        ExpProtocol protocol = ensureSampleDerivationProtocol(info.getUser());
        ExpRunImpl run = createExperimentRun(info.getContainer(), name.toString());
        run.setProtocol(protocol);
        run.setFilePathRoot(pipeRoot.getRootPath());

        return run;
    }


    private ExpProtocol ensureSampleDerivationProtocol(User user) throws ExperimentException
    {
        ExpProtocol protocol = getExpProtocol(SAMPLE_DERIVATION_PROTOCOL_LSID);
        if (protocol == null)
        {
            ExpProtocolImpl baseProtocol = createExpProtocol(ContainerManager.getSharedContainer(), ExpProtocol.ApplicationType.ExperimentRun, "Sample Derivation Protocol");
            baseProtocol.setLSID(SAMPLE_DERIVATION_PROTOCOL_LSID);
            baseProtocol.setMaxInputDataPerInstance(0);
            baseProtocol.setProtocolDescription("Simple protocol for creating derived samples that may have different properties from the original sample.");
            return insertSimpleProtocol(baseProtocol, user);
        }
        return protocol;
    }

    public boolean isSampleDerivation(ExpProtocol protocol)
    {
        if (protocol == null)
            return false;

        return SAMPLE_DERIVATION_PROTOCOL_LSID.equals(protocol.getLSID());
    }

    public void registerExperimentDataHandler(ExperimentDataHandler handler)
    {
        _dataHandlers.add(handler);
        if (null != handler.getDataType())
            registerDataType(handler.getDataType());
    }

    public void registerExperimentRunTypeSource(ExperimentRunTypeSource source)
    {
        _runTypeSources.add(source);
    }

    public void registerDataType(DataType type)
    {
        _dataTypes.put(type.getNamespacePrefix(), type);
    }

    @NotNull
    public Set<ExperimentRunType> getExperimentRunTypes(@Nullable Container container)
    {
        Set<ExperimentRunType> result = new TreeSet<>();
        for (ExperimentRunTypeSource runTypeSource : _runTypeSources)
        {
            result.addAll(runTypeSource.getExperimentRunTypes(container));
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<ExperimentDataHandler> getExperimentDataHandlers()
    {
        return Collections.unmodifiableSet(_dataHandlers);
    }

    public DataType getDataType(String namespacePrefix)
    {
        return _dataTypes.get(namespacePrefix);
    }

    public void registerProtocolImplementation(ProtocolImplementation impl)
    {
        _protocolImplementations.put(impl.getName(), impl);
    }

    public ProtocolImplementation getProtocolImplementation(String name)
    {
        return _protocolImplementations.get(name);
    }

    public ExpProtocolApplicationImpl getExpProtocolApplication(int rowId)
    {
        ProtocolApplication app = new TableSelector(getTinfoProtocolApplication()).getObject(rowId, ProtocolApplication.class);
        if (app == null)
            return null;
        return new ExpProtocolApplicationImpl(app);
    }

    public List<ExpProtocolApplicationImpl> getExpProtocolApplicationsForRun(int runId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RunId"), runId);
        Sort sort = new Sort("ActionSequence, RowId");
        return ExpProtocolApplicationImpl.fromProtocolApplications(new TableSelector(getTinfoProtocolApplication(), filter, sort).getArrayList(ProtocolApplication.class));
    }

    @NotNull
    public ExpSampleSetImpl createSampleSet()
    {
        return new ExpSampleSetImpl(new MaterialSource());
    }

    @NotNull
    public ExpSampleSetImpl createSampleSet(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol, String nameExpression)
            throws ExperimentException
    {
        return createSampleSet(c,u,name,description,properties,indices,idCol1,idCol2,idCol3,parentCol,null, null);
    }

    @NotNull
    public ExpSampleSetImpl createSampleSet(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                            String nameExpression, @Nullable TemplateInfo templateInfo)
        throws ExperimentException
    {
        ExpSampleSet existing = getSampleSet(c, name);
        if (existing != null)
            throw new IllegalArgumentException("SampleSet '" + name + "' already exists");

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
                    if (pd.getLabel() == null)
                        pd.setLabel(pd.getName());
                    pd.setName("Property_" + pd.getName());
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

        MaterialSource source = new MaterialSource();
        source.setLSID(lsid.toString());
        source.setName(name);
        source.setDescription(description);
        source.setMaterialLSIDPrefix(new Lsid.LsidBuilder("Sample", String.valueOf(c.getRowId()) + "." + PageFlowUtil.encode(name), "").toString());
        if (nameExpression != null)
            source.setNameExpression(nameExpression);
        source.setContainer(c);

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

        ExpSampleSetImpl ss = new ExpSampleSetImpl(source);
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            domain.save(u);
            ss.save(u);
            DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);

            transaction.commit();
            getMaterialSourceCache().clear();
        }

        return ss;
    }

    public ExpDataClassImpl createDataClass(
            Container c, User u, String name, String description,
            List<GWTPropertyDescriptor> properties,
            List<GWTIndex> indices, Integer sampleSetId, String nameExpression,
            @Nullable TemplateInfo templateInfo)
        throws ExperimentException
    {
        ExpDataClass existing = getDataClass(c, u, name, true);
        if (existing != null)
            throw new IllegalArgumentException("DataClass '" + name + "' already exists");

        if (sampleSetId != null)
        {
            ExpSampleSet ss = getSampleSet(sampleSetId);
            if (ss == null)
                throw new IllegalArgumentException("SampleSet '" + sampleSetId + "' not found");

            if (!ss.getContainer().equals(c))
                throw new IllegalArgumentException("Associated SampleSet must be defined in the same container as this DataClass");
        }

        Lsid lsid = getDataClassLsid(name, c);
        Domain domain = PropertyService.get().createDomain(c, lsid.toString(), name, templateInfo);
        DomainKind kind = domain.getDomainKind();

        Set<String> reservedNames = kind.getReservedPropertyNames(domain);
        Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());

        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        Set<String> propertyUris = new HashSet<>();
        for (GWTPropertyDescriptor pd : properties)
        {
            String propertyName = pd.getName().toLowerCase();
            if (lowerReservedNames.contains(propertyName))
                throw new IllegalArgumentException("Property name '" + propertyName + "' is a reserved name");
            else if (domain.getPropertyByName(propertyName) != null) // issue 25275
                throw new IllegalArgumentException("Property name '" + propertyName + "' is already defined for this domain");

            DomainProperty dp = DomainUtil.addProperty(domain, pd, defaultValues, propertyUris, null);
        }

        Set<PropertyStorageSpec.Index> propertyIndices = new HashSet<>();
        for (GWTIndex index : indices)
        {
            // issue 25273: verify that each index column name exists in the domain
            for (String indexColName : index.getColumnNames())
            {
                if (!lowerReservedNames.contains(indexColName.toLowerCase()) && domain.getPropertyByName(indexColName) == null)
                    throw new IllegalArgumentException("Index column name '" + indexColName + "' does not exist");
            }

            PropertyStorageSpec.Index propIndex = new PropertyStorageSpec.Index(index.isUnique(), index.getColumnNames());
            propertyIndices.add(propIndex);
        }
        domain.setPropertyIndices(propertyIndices);

        DataClass dataClass = new DataClass();
        dataClass.setLSID(lsid.toString());
        dataClass.setName(name);
        dataClass.setDescription(description);
        if (sampleSetId != null)
            dataClass.setMaterialSourceId(sampleSetId);
        if (nameExpression != null)
            dataClass.setNameExpression(nameExpression);
        dataClass.setContainer(c);

        ExpDataClassImpl impl = new ExpDataClassImpl(dataClass);
        try (DbScope.Transaction tx = ensureTransaction())
        {
            OntologyManager.ensureObject(c, lsid.toString());

            domain.setPropertyForeignKeys(kind.getPropertyForeignKeys(c));
            domain.save(u);
            impl.save(u);
            DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);

            tx.commit();
            clearDataClassCache(c);
        }

        return impl;
    }

    public List<ExpProtocolImpl> getExpProtocols(Container... containers)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("Container"), Arrays.asList(containers)));
        return ExpProtocolImpl.fromProtocols(new TableSelector(getTinfoProtocol(), filter, null).getArrayList(Protocol.class));
    }

    public List<ExpProtocolImpl> getExpProtocolsForRunsInContainer(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT p.* FROM ");
        sql.append(getTinfoProtocol(), "p");
        sql.append(" WHERE LSID IN (SELECT ProtocolLSID FROM ");
        sql.append(getTinfoExperimentRun(), "er");
        sql.append(" WHERE er.Container = ?)");
        sql.add(container.getId());
        return ExpProtocolImpl.fromProtocols(new SqlSelector(getSchema(), sql).getArrayList(Protocol.class));
    }

    public List<ExpProtocolImpl> getAllExpProtocols()
    {
        return ExpProtocolImpl.fromProtocols(new TableSelector(getTinfoProtocol()).getArrayList(Protocol.class));
    }

    @Override
    public List<? extends ExpProtocol> getExpProtocolsWithParameterValue(@NotNull String parameterURI, @NotNull String parameterValue, @Nullable Container c)
    {
        SimpleFilter parameterFilter = new SimpleFilter()
                .addCondition(FieldKey.fromParts("ontologyEntryURI"), parameterURI)
                .addCondition(FieldKey.fromParts("stringvalue"), parameterValue)
                .addCondition(FieldKey.fromParts("valuetype"), "String");

        Set<Integer> protocolIds = new HashSet<>(new TableSelector(getTinfoProtocolParameter(), singleton("protocolId"), parameterFilter, null).getArrayList(Integer.class));

        SimpleFilter protocolFilter = c == null ? new SimpleFilter() : SimpleFilter.createContainerFilter(c);
        protocolFilter.addCondition(FieldKey.fromParts("rowId"), protocolIds, IN);

        return ExpProtocolImpl.fromProtocols(new TableSelector(getTinfoProtocol(), protocolFilter, null).getArrayList(Protocol.class));
    }

    public PipelineJob importXarAsync(ViewBackgroundInfo info, File file, String description, PipeRoot root) throws IOException
    {
        ExperimentPipelineJob job = new ExperimentPipelineJob(info, file, description, false, root);
        try
        {
            PipelineService.get().queueJob(job);
        }
        catch (PipelineValidationException e)
        {
            throw new IOException(e);
        }
        return job;
    }

    private void addMaterialInputs(Map<ExpMaterial, String> inputMaterials, ProtocolApplication protApp1, User user)
            throws SQLException
    {
        Set<MaterialInput> existingInputs = new HashSet<>(getMaterialInputsForApplication(protApp1.getRowId()));

        Set<MaterialInput> desiredInputs = new HashSet<>();

        for (Map.Entry<ExpMaterial, String> entry : inputMaterials.entrySet())
        {
            MaterialInput input = new MaterialInput();
            input.setRole(entry.getValue());
            input.setMaterialId(entry.getKey().getRowId());
            input.setTargetApplicationId(protApp1.getRowId());
            desiredInputs.add(input);
        }

        syncInputs(user, existingInputs, desiredInputs, FieldKey.fromParts("MaterialId"), getTinfoMaterialInput());
    }

    private void addDataInputs(Map<ExpData, String> inputDatas, ProtocolApplication protApp1, User user)
            throws SQLException
    {
        Set<DataInput> existingInputs = new HashSet<>(getDataInputsForApplication(protApp1.getRowId()));

        Set<DataInput> desiredInputs = new HashSet<>();

        for (Map.Entry<ExpData, String> entry : inputDatas.entrySet())
        {
            DataInput input = new DataInput();
            input.setRole(entry.getValue());
            input.setDataId(entry.getKey().getRowId());
            input.setTargetApplicationId(protApp1.getRowId());
            desiredInputs.add(input);
        }

        syncInputs(user, existingInputs, desiredInputs, FieldKey.fromParts("DataId"), getTinfoDataInput());
    }

    private void syncInputs(User user, Set<? extends AbstractRunInput> existingInputs, Set<? extends AbstractRunInput> desiredInputs, FieldKey keyName, TableInfo table)
            throws SQLException
    {
        Set<AbstractRunInput> inputsToDelete = new HashSet<>(existingInputs);
        inputsToDelete.removeAll(desiredInputs);
        for (AbstractRunInput input : inputsToDelete)
        {
            SimpleFilter filter = new SimpleFilter(keyName, input.getInputKey());
            filter.addCondition(FieldKey.fromParts("TargetApplicationId"), input.getTargetApplicationId());
            Table.delete(table, filter);
        }

        Set<AbstractRunInput> inputsToInsert = new HashSet<>(desiredInputs);
        inputsToInsert.removeAll(existingInputs);
        for (AbstractRunInput input : inputsToInsert)
        {
            Table.insert(user, table, input);
        }

        uncacheLineageGraph();
    }

    @NotNull
    public List<ExpDataImpl> getExpDatasUnderPath(@NotNull File path, @Nullable Container c)
    {
        SimpleFilter filter = new SimpleFilter();
        if (c != null)
            filter.addCondition(FieldKey.fromParts("Container"), c);

        String prefix = path.toURI().toString();
        if (!prefix.endsWith("/"))
            prefix = prefix + "/";

        filter.addCondition(FieldKey.fromParts("datafileurl"), prefix, CompareType.STARTS_WITH);
        filter.addCondition(FieldKey.fromParts("datafileurl"), path.toURI().toString(), CompareType.NEQ);
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), filter, null).getArrayList(Data.class));
    }

    public ExpProtocol insertProtocol(@NotNull ExpProtocol wrappedProtocol, @Nullable List<ExpProtocol> steps, @Nullable Map<String, List<String>> predecessors, User user) throws ExperimentException
    {
        if (wrappedProtocol == null)
        {
            throw new ExperimentException("Cannot insert a \"null\" protocol");
        }

        try (DbScope.Transaction tx = getSchema().getScope().ensureTransaction(getProtocolImportLock()))
        {
            if (getExpProtocol(wrappedProtocol.getLSID()) != null)
            {
                throw new ExperimentException("A protocol with that name already exists");
            }

            Protocol baseProtocol = insertProtocol(wrappedProtocol, user);

            List<Protocol> stepProtocols = new ArrayList<>();
            if (steps != null)
            {
                for (ExpProtocol wrappedStepProtocol : steps)
                {
                    if (wrappedStepProtocol == null)
                    {
                        throw new ExperimentException("Cannot insert a \"null\" protocol step");
                    }

                    stepProtocols.add(insertProtocol(wrappedStepProtocol, user));
                }
            }

            // all protocols are now inserted, now link them together
            int actionSequence = 1;
            Map<String, ProtocolAction> stepActions = new HashMap<>();

            // insert base ProtocolAction prior to inserting steps
            ProtocolAction previousAction = insertProtocolAction(baseProtocol, baseProtocol, actionSequence, user);
            insertProtocolPredecessor(user, previousAction.getRowId(), previousAction.getRowId());
            stepActions.put(baseProtocol.getLSID(), previousAction);
            actionSequence = 10;

            // insert ProtocolAction for each step prior to mapping actionSequence
            for (Protocol stepProtocol : stepProtocols)
            {
                ProtocolAction stepAction = insertProtocolAction(baseProtocol, stepProtocol, actionSequence, user);
                stepActions.put(stepProtocol.getLSID(), stepAction);
                actionSequence += 10;
            }

            // map actionSequences
            for (Protocol stepProtocol : stepProtocols)
            {
                String LSID = stepProtocol.getLSID();
                ProtocolAction stepAction = stepActions.get(LSID);

                if (predecessors != null)
                {
                    List<String> stepPredecessors = predecessors.get(LSID);

                    if (stepPredecessors == null)
                        throw new ExperimentException("Invalid predecessor map provided. Unable to find entry for \"" + LSID + "\". Each step protocol must have an entry.");

                    for (String predecessorLSID : stepPredecessors)
                    {
                        ProtocolAction predecessorAction = stepActions.get(predecessorLSID);

                        if (predecessorAction == null)
                            throw new ExperimentException("Invalid predecessor map provided. Unable to find \"" + predecessorLSID + "\" in set of steps.");

                        insertProtocolPredecessor(user, stepAction.getRowId(), predecessorAction.getRowId());
                        previousAction = stepAction;
                    }
                }
                else
                {
                    insertProtocolPredecessor(user, stepAction.getRowId(), previousAction.getRowId());
                    previousAction = stepAction;
                }
            }

            tx.commit();

            return getExpProtocol(baseProtocol.getRowId());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /**
     * Helper to insert a Protocol during the Protocol insertion process. Use {@link ExperimentServiceImpl#insertProtocol(ExpProtocol, List, Map, User)}
     */
    private Protocol insertProtocol(ExpProtocol protocol, User user) throws ExperimentException
    {
        Protocol baseProtocol = ((ExpProtocolImpl)protocol).getDataObject();

        if (protocol.getApplicationType() == null)
        {
            throw new ExperimentException("Protocol \"" + protocol.getLSID() + "\" needs to declare it's applicationType before being inserted");
        }

        if (baseProtocol.getOutputDataType() == null)
            baseProtocol.setOutputDataType(ExpData.DEFAULT_CPAS_TYPE);
        if (baseProtocol.getOutputMaterialType() == null)
            baseProtocol.setOutputMaterialType(ExpMaterial.DEFAULT_CPAS_TYPE);

        Map<String, ProtocolParameter> baseParams = new HashMap<>(protocol.getProtocolParameters());

        if (!baseParams.containsKey(XarConstants.APPLICATION_LSID_TEMPLATE_URI))
        {
            throw new ExperimentException(XarConstants.APPLICATION_LSID_TEMPLATE_URI + " needs to be declared before inserting protocol");
        }

        if (!baseParams.containsKey(XarConstants.APPLICATION_NAME_TEMPLATE_URI))
        {
            ProtocolParameter baseNameTemplate = new ProtocolParameter();
            baseNameTemplate.setName(XarConstants.APPLICATION_NAME_TEMPLATE_NAME);
            baseNameTemplate.setOntologyEntryURI(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            baseNameTemplate.setValue(SimpleTypeNames.STRING, baseProtocol.getName()); // TODO: Consider for base adding " Protocol"
            baseParams.put(XarConstants.APPLICATION_NAME_TEMPLATE_URI, baseNameTemplate);
            baseProtocol.storeProtocolParameters(baseParams.values());
        }

        return saveProtocol(user, baseProtocol, false /* saveProperties */);
    }

    /**
     * Helper to insert ProtocolActions during the Protocol insertion process. Use {@link ExperimentServiceImpl#insertProtocol(ExpProtocol, List, Map, User)}
     */
    private ProtocolAction insertProtocolAction(Protocol parent, Protocol child, int actionSequence, User user) throws SQLException
    {
        ProtocolAction action = new ProtocolAction();
        action.setParentProtocolId(parent.getRowId());
        action.setChildProtocolId(child.getRowId());
        action.setSequence(actionSequence);
        action = Table.insert(user, getTinfoProtocolAction(), action);
        return action;
    }

    // TODO: Switch this to use insertProtocol(ExpProtocol, List, Map, User)
    public ExpProtocol insertSimpleProtocol(ExpProtocol wrappedProtocol, User user) throws ExperimentException
    {
        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction(getProtocolImportLock()))
        {
            if (getExpProtocol(wrappedProtocol.getLSID()) != null)
            {
                throw new ExperimentException("An assay with that name already exists");
            }

            Protocol baseProtocol = ((ExpProtocolImpl)wrappedProtocol).getDataObject();
            wrappedProtocol.setApplicationType(ExpProtocol.ApplicationType.ExperimentRun);
            baseProtocol.setOutputDataType(ExpData.DEFAULT_CPAS_TYPE);
            baseProtocol.setOutputMaterialType(ExpMaterial.DEFAULT_CPAS_TYPE);
            baseProtocol.setContainer(baseProtocol.getContainer());

            Map<String, ProtocolParameter> baseParams = new HashMap<>(wrappedProtocol.getProtocolParameters());
            ProtocolParameter baseLSIDTemplate = new ProtocolParameter();
            baseLSIDTemplate.setName(XarConstants.APPLICATION_LSID_TEMPLATE_NAME);
            baseLSIDTemplate.setOntologyEntryURI(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            baseLSIDTemplate.setValue(SimpleTypeNames.STRING, "${RunLSIDBase}:SimpleProtocol.InputStep");
            baseParams.put(XarConstants.APPLICATION_LSID_TEMPLATE_URI, baseLSIDTemplate);
            ProtocolParameter baseNameTemplate = new ProtocolParameter();
            baseNameTemplate.setName(XarConstants.APPLICATION_NAME_TEMPLATE_NAME);
            baseNameTemplate.setOntologyEntryURI(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            baseNameTemplate.setValue(SimpleTypeNames.STRING, baseProtocol.getName() + " Protocol");
            baseParams.put(XarConstants.APPLICATION_NAME_TEMPLATE_URI, baseNameTemplate);
            baseProtocol.storeProtocolParameters(baseParams.values());

            baseProtocol = saveProtocol(user, baseProtocol);

            Protocol coreProtocol = new Protocol();
            coreProtocol.setOutputDataType(ExpData.DEFAULT_CPAS_TYPE);
            coreProtocol.setOutputMaterialType(ExpMaterial.DEFAULT_CPAS_TYPE);
            coreProtocol.setContainer(baseProtocol.getContainer());
            coreProtocol.setApplicationType(ExpProtocol.ApplicationType.ProtocolApplication.name());
            coreProtocol.setName(baseProtocol.getName() + " - Core");
            coreProtocol.setLSID(baseProtocol.getLSID() + ".Core");

            List<ProtocolParameter> coreParams = new ArrayList<>();
            ProtocolParameter coreLSIDTemplate = new ProtocolParameter();
            coreLSIDTemplate.setName(XarConstants.APPLICATION_LSID_TEMPLATE_NAME);
            coreLSIDTemplate.setOntologyEntryURI(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            coreLSIDTemplate.setValue(SimpleTypeNames.STRING, "${RunLSIDBase}:SimpleProtocol.CoreStep");
            coreParams.add(coreLSIDTemplate);
            ProtocolParameter coreNameTemplate = new ProtocolParameter();
            coreNameTemplate.setName(XarConstants.APPLICATION_NAME_TEMPLATE_NAME);
            coreNameTemplate.setOntologyEntryURI(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            coreNameTemplate.setValue(SimpleTypeNames.STRING, baseProtocol.getName());
            coreParams.add(coreNameTemplate);
            coreProtocol.storeProtocolParameters(coreParams);

            coreProtocol = saveProtocol(user, coreProtocol);

            Protocol outputProtocol = new Protocol();
            outputProtocol.setOutputDataType(ExpData.DEFAULT_CPAS_TYPE);
            outputProtocol.setOutputMaterialType(ExpMaterial.DEFAULT_CPAS_TYPE);
            outputProtocol.setName(baseProtocol.getName() + " - Output");
            outputProtocol.setLSID(baseProtocol.getLSID() + ".Output");
            outputProtocol.setApplicationType(ExpProtocol.ApplicationType.ExperimentRunOutput.name());
            outputProtocol.setContainer(baseProtocol.getContainer());

            List<ProtocolParameter> outputParams = new ArrayList<>();
            ProtocolParameter outputLSIDTemplate = new ProtocolParameter();
            outputLSIDTemplate.setName(XarConstants.APPLICATION_LSID_TEMPLATE_NAME);
            outputLSIDTemplate.setOntologyEntryURI(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            outputLSIDTemplate.setValue(SimpleTypeNames.STRING, "${RunLSIDBase}:SimpleProtocol.OutputStep");
            outputParams.add(outputLSIDTemplate);
            ProtocolParameter outputNameTemplate = new ProtocolParameter();
            outputNameTemplate.setName(XarConstants.APPLICATION_NAME_TEMPLATE_NAME);
            outputNameTemplate.setOntologyEntryURI(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            outputNameTemplate.setValue(SimpleTypeNames.STRING, baseProtocol.getName() + " output");
            outputParams.add(outputNameTemplate);
            outputProtocol.storeProtocolParameters(outputParams);

            outputProtocol = saveProtocol(user, outputProtocol);

            ProtocolAction action1 = new ProtocolAction();
            action1.setParentProtocolId(baseProtocol.getRowId());
            action1.setChildProtocolId(baseProtocol.getRowId());
            action1.setSequence(SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE);
            action1 = Table.insert(user, getTinfoProtocolAction(), action1);

            insertProtocolPredecessor(user, action1.getRowId(), action1.getRowId());

            ProtocolAction action2 = new ProtocolAction();
            action2.setParentProtocolId(baseProtocol.getRowId());
            action2.setChildProtocolId(coreProtocol.getRowId());
            action2.setSequence(SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE);
            action2 = Table.insert(user, getTinfoProtocolAction(), action2);

            insertProtocolPredecessor(user, action2.getRowId(), action1.getRowId());

            ProtocolAction action3 = new ProtocolAction();
            action3.setParentProtocolId(baseProtocol.getRowId());
            action3.setChildProtocolId(outputProtocol.getRowId());
            action3.setSequence(SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE);
            action3 = Table.insert(user, getTinfoProtocolAction(), action3);

            insertProtocolPredecessor(user, action3.getRowId(), action2.getRowId());

            transaction.commit();
            return wrappedProtocol;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public List<ExpMaterialImpl> getExpMaterialsForRun(int runId)
    {
        return ExpMaterialImpl.fromMaterials(new SqlSelector(getExpSchema(), new SQLFragment("SELECT * FROM " + getTinfoMaterial() + " WHERE RunId = ?", runId)).getArrayList(Material.class));
    }

    /**
     * @return all of the samples visible from the current container, mapped from name to sample.
     */
    public Map<String, List<ExpMaterialImpl>> getSamplesByName(Container container, User user)
    {
        Map<String, List<ExpMaterialImpl>> potentialParents = new HashMap<>();
        for (ExpSampleSetImpl sampleSet : getSampleSets(container, user, true))
        {
            for (ExpMaterialImpl expMaterial : sampleSet.getSamples())
            {
                List<ExpMaterialImpl> matchingSamples = potentialParents.get(expMaterial.getName());
                if (matchingSamples == null)
                {
                    matchingSamples = new LinkedList<>();
                    potentialParents.put(expMaterial.getName(), matchingSamples);
                }
                matchingSamples.add(expMaterial);
            }
        }
        return potentialParents;
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
    }

    private DbSequence getSampleSequence(@NotNull SampleSequenceType type, @Nullable Date date)
    {
        // For now, we only create sequences globally for all samples.  We could create sequences for each SampleSet
        // as well by using the id parameter as the SampleSet's rowId, but we don't need them at this time.
        DbSequence sequence = DbSequenceManager.get(ContainerManager.getRoot(), type.getSequenceName(date));
        return sequence;
    }

    @Override
    public Map<String, Integer> incrementSampleCounts(@Nullable Date counterDate)
    {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("dailySampleCount", getSampleSequence(SampleSequenceType.DAILY, counterDate).next());
        counts.put("weeklySampleCount", getSampleSequence(SampleSequenceType.WEEKLY, counterDate).next());
        counts.put("monthlySampleCount", getSampleSequence(SampleSequenceType.MONTHLY, counterDate).next());
        counts.put("yearlySampleCount", getSampleSequence(SampleSequenceType.YEARLY, counterDate).next());
        return counts;
    }

    /**
     * Ensure that an alias entry exists for each string value passed in, else create it.
     * @return The list of rowId for each alias name.
     */
    public Collection<Integer> ensureAliases(User user, Set<String> aliasNames)
    {
        final ExperimentService svc = ExperimentService.get();
        Set<Integer> rowIds = new HashSet<>();

        TableInfo aliasTable = svc.getTinfoAlias();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("name"), aliasNames, IN);
        TableSelector ts = new TableSelector(aliasTable, aliasTable.getColumns("name","rowId"), filter, null);
        Map<String, Integer> existingAliases = ts.getValueMap();

        // Return the rowId for the existing alias names
        rowIds.addAll(existingAliases.values());

        Set<String> missingNames = new HashSet<>(aliasNames);
        missingNames.removeAll(existingAliases.keySet());

        // Create aliases for the missing alias names
        for (String aliasName : missingNames)
        {
            Map<String, Object> inserted = Table.insert(user, aliasTable, CaseInsensitiveHashMap.of("name", aliasName));
            Integer rowId = (Integer)inserted.get("rowId");
            rowIds.add(rowId);
        }

        return rowIds;
    }

    public GWTDomain convertJsonToDomain(JSONObject obj) throws JSONException
    {
        GWTDomain domain = new GWTDomain();
        JSONObject jsonDomain = obj.getJSONObject("domainDesign");

        domain.set_Ts(jsonDomain.optString("ts"));
        domain.setDomainId(jsonDomain.optInt("domainId", -1));

        domain.setName(jsonDomain.getString("name"));
        domain.setDomainURI(jsonDomain.optString("domainURI", null));
        domain.setContainer(jsonDomain.getString("container"));

        // Description can be null
        domain.setDescription(jsonDomain.optString("description", null));

        JSONArray jsonFields = jsonDomain.getJSONArray("fields");
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        for (int i=0; i<jsonFields.length(); i++)
        {
            JSONObject jsonProp = jsonFields.getJSONObject(i);
            GWTPropertyDescriptor prop = convertJsonToPropertyDescriptor(jsonProp);
            props.add(prop);
        }

        domain.setFields(props);

        JSONArray jsonIndices = jsonDomain.optJSONArray("indices");
        if (jsonIndices != null)
        {
            List<GWTIndex> indices = new ArrayList<>();
            for (JSONObject jsonIndex : jsonIndices.toJSONObjectArray())
            {
                List<String> columnNames = new ArrayList<>();
                for (Object o : jsonIndex.getJSONArray("columns").toArray())
                    columnNames.add(String.valueOf(o));

                // Only unique is supported currently
                boolean unique = TableInfo.IndexType.Unique.name().equalsIgnoreCase(jsonIndex.optString("type"));
                GWTIndex index = new GWTIndex(columnNames, unique);
                indices.add(index);
            }
            domain.setIndices(indices);
        }

        return domain;
    }

    public GWTPropertyDescriptor convertJsonToPropertyDescriptor(JSONObject obj) throws JSONException
    {
        GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
        prop.setName(obj.getString("name"));
        prop.setRangeURI(obj.getString("rangeURI"));

        // Other fields can be null
        Integer propId = (Integer)obj.get("propertyId");
        if (propId != null)
            prop.setPropertyId(propId.intValue());

        Boolean required = (Boolean)obj.get("required");
        if (required != null)
            prop.setRequired(required.booleanValue());

        Boolean shownInInsertView = (Boolean)obj.get("shownInInsertView");
        if (shownInInsertView != null)
            prop.setShownInInsertView(shownInInsertView);

        Boolean shownInUpdateView = (Boolean)obj.get("shownInUpdateView");
        if (shownInUpdateView != null)
            prop.setShownInUpdateView(shownInUpdateView);

        Boolean shownInDetailsView = (Boolean)obj.get("shownInDetailsView");
        if (shownInDetailsView != null)
            prop.setShownInDetailsView(shownInDetailsView);

        prop.setPropertyURI((String)obj.get("propertyURI"));
        prop.setOntologyURI((String)obj.get("ontologyURI"));
        prop.setDescription((String)obj.get("description"));
        prop.setConceptURI((String)obj.get("conceptURI"));
        prop.setLabel((String)obj.get("label"));
        prop.setSearchTerms((String)obj.get("searchTerms"));
        prop.setSemanticType((String)obj.get("semanticType"));
        prop.setFormat((String)obj.get("format"));
        prop.setLookupContainer((String)obj.get("lookupContainer"));
        prop.setLookupSchema((String)obj.get("lookupSchema"));
        prop.setLookupQuery((String)obj.get("lookupQuery"));
        prop.setImportAliases((String)obj.get("importAliases"));
        prop.setURL((String)obj.get("url"));
        prop.setRedactedText((String)obj.get("redactedText"));
        prop.setDefaultValue((String)obj.get("defaultValue"));

        String defaultValueType = ((String)obj.get("defaultValueType"));
        if (defaultValueType != null)
            prop.setDefaultValueType(DefaultValueType.valueOf(defaultValueType));

        if (obj.has("scale"))
            prop.setScale((Integer)obj.get("scale"));

        JSONArray jsonValidators = obj.optJSONArray("validators");
        if(null != jsonValidators)
        {
            List<GWTPropertyValidator> validators = new ArrayList<>();
            for (int i = 0; i < jsonValidators.length(); i++)
            {
                JSONObject jsonValidator = jsonValidators.getJSONObject(i);
                if (null != jsonValidator)
                {
                    validators.add(convertJsonToPropertyValidator(jsonValidator));
                }
            }
            prop.setPropertyValidators(validators);
        }

        return prop;
    }

    public GWTPropertyValidator convertJsonToPropertyValidator(JSONObject obj) throws JSONException
    {
        GWTPropertyValidator validator = new GWTPropertyValidator();
        validator.setName(obj.optString("name", "Validator"));
        validator.setDescription(obj.optString("description"));
        validator.setType(PropertyValidatorType.getType(obj.getString("type")));
        validator.setExpression(obj.optString("expression"));
        validator.setErrorMessage(obj.optString("errorMessage"));

        return validator;
    }

    public JSONArray convertPropertyValidatorsToJson(GWTPropertyDescriptor pd)
    {
        JSONArray json = new JSONArray();
        JSONObject obj;
        for (GWTPropertyValidator pv : pd.getPropertyValidators())
        {
            obj = new JSONObject();
            obj.put("name", pv.getName());
            obj.put("description", pv.getDescription());
            obj.put("type", pv.getType().getTypeName());
            obj.put("expression", pv.getExpression());
            obj.put("errorMessage", pv.getErrorMessage());
            json.put(obj);
        }

        return json;
    }

    public JSONObject convertPropertyDescriptorToJson(GWTPropertyDescriptor pd)
    {
        JSONObject json = new JSONObject();
        json.put("propertyId", pd.getPropertyId());
        json.put("name", pd.getName());
        json.put("required", pd.isRequired());
        json.put("label", pd.getLabel());
        json.put("scale", pd.getScale());
        json.put("format", pd.getFormat());
        json.put("lookupSchema", pd.getLookupSchema());
        json.put("lookupQuery", pd.getLookupQuery());
        json.put("defaultValue", pd.getDefaultValue());
        json.put("redactedText", pd.getRedactedText());
        json.put("validators", convertPropertyValidatorsToJson(pd));
        json.put("rangeURI", pd.getRangeURI());
        json.put("conceptURI", pd.getConceptURI());

        return json;
    }

    public void addExperimentListener(ExperimentListener listener)
    {
        _listeners.add(listener);
    }

    @Override
    public List<ValidationException> onBeforeRunCreated(ExpProtocol protocol, ExpRun run, Container container, User user)
    {
        List<ValidationException> errors = new ArrayList<>();
        for (ExperimentListener listener : _listeners)
        {
            errors.addAll(listener.beforeRunCreated(container, user, protocol, run));
        }
        return errors;
    }

    @Override
    public List<ValidationException> onRunDataCreated(ExpProtocol protocol, ExpRun run, Container container, User user)
    {
        List<ValidationException> errors = new ArrayList<>();
        for (ExperimentListener listener : _listeners)
        {
            errors.addAll(listener.afterResultDataCreated(container, user, run, protocol));
        }
        return errors;
    }

    @Override
    public void onMaterialsCreated(List<? extends ExpMaterial> materials, Container container, User user)
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.afterMaterialCreated(materials, container, user);
        }
    }

    public static class TestCase extends Assert
    {
        @Before
        public void setUp()
        {
            ContainerManager.deleteAll(JunitUtil.getTestContainer(), TestContext.get().getUser());
        }

        @After
        public void tearDown()
        {
            ContainerManager.deleteAll(JunitUtil.getTestContainer(), TestContext.get().getUser());
        }

        //@Test
        public void testRecursiveSql()
        {
            ExperimentServiceImpl impl = new ExperimentServiceImpl();

            // just test if syntactically correct
            SQLFragment sqlf = impl.generateExperimentTreeSQL(new SQLFragment("?", GUID.makeGUID()), new ExpLineageOptions());
            //System.out.println(sqlf.toDebugString());
            assertFalse(new SqlSelector(impl.getExpSchema().getScope(), sqlf).exists());


            SQLFragment sqlfA = impl.generateExperimentTreeSQL(new SQLFragment("?", 'A' + GUID.makeGUID()), new ExpLineageOptions());
            SQLFragment sqlfB = impl.generateExperimentTreeSQL(new SQLFragment("?", 'B' + GUID.makeGUID()), new ExpLineageOptions());
            SQLFragment union = new SQLFragment();

            union.append("(\n");
            union.append(sqlfA);
            union.append("\n) UNION (\n");
            union.append(sqlfB);
            union.append("\n)");

            //System.out.println(union.toDebugString());
            assertFalse(new SqlSelector(impl.getExpSchema().getScope(), union).exists());
        }

        @Test
        public void testRunInputProperties() throws Exception
        {
            Assume.assumeTrue("31193: Experiment module has undeclared dependency on study module", AssayService.get() != null);

            final User user = TestContext.get().getUser();
            final Container c = JunitUtil.getTestContainer();
            final ViewBackgroundInfo info = new ViewBackgroundInfo(c, user, null);
            final Logger log = Logger.getLogger(ExperimentServiceImpl.class);

            // assert no MaterialInput exp.object exist
            assertEquals(0L, countMaterialInputObjects(c));

            ExperimentServiceImpl impl = ExperimentServiceImpl.get();

            // create sample set
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("name", "string"));
            ExpSampleSet ss = impl.createSampleSet(c, user, "TestSamples", null, props, Collections.emptyList(), -1, -1, -1, -1, null);

            // create material
            UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
            TableInfo table = schema.getTable("TestSamples");
            QueryUpdateService svc = table.getUpdateService();

            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(CaseInsensitiveHashMap.of("name", "bob", "age", 10));
            rows.add(CaseInsensitiveHashMap.of("name", "sally", "age", 10));

            BatchValidationException errors = new BatchValidationException();
            svc.insertRows(user, c, rows, errors, null, null);
            if (errors.hasErrors())
                throw errors;

            ExpMaterial sampleIn = ss.getSample(c, "bob");
            ExpMaterial sampleOut = ss.getSample(c, "sally");


            // create run
            Map<ExpMaterial, String> inputMaterials = new HashMap<>();
            inputMaterials.put(sampleIn, "Sample Goo");

            Map<ExpData, String> inputData = new HashMap<>();
            Map<ExpMaterial, String> outputMaterials = new HashMap<>();
            outputMaterials.put(sampleOut, "Sample Boo");

            Map<ExpData, String> outputData = new HashMap<>();

            ExpRun run = impl.derive(inputMaterials, inputData, outputMaterials, outputData, info, log);
            run.save(user);

            ExpProtocolApplication pa = run.getInputProtocolApplication();
            List<? extends ExpMaterialRunInput> materialRunInputs = pa.getMaterialInputs();
            assertEquals(1, materialRunInputs.size());

            ExpMaterialRunInputImpl materialRunInput = (ExpMaterialRunInputImpl)materialRunInputs.get(0);
            assertEquals(sampleIn, materialRunInput.getMaterial());
            assertEquals("Sample Goo", materialRunInput.getRole());
            assertTrue(materialRunInput.getLSIDNamespacePrefix().equals(MaterialInput.NAMESPACE));
            assertTrue(materialRunInput.getLSID().contains(":MaterialInput:" + sampleIn.getRowId() + "." + pa.getRowId()));

            ExpMaterialRunInputImpl x = impl.getMaterialInput(sampleIn.getRowId(), pa.getRowId());
            assertEquals(materialRunInput.getLSID(), x.getLSID());

            Map<String, Object> materialInputProps = materialRunInput.getProperties();
            assertTrue(materialInputProps.isEmpty());

            // save an edge property -- using the comment property will suffice
            materialRunInput.setComment(user, "hello world");
            assertEquals("hello world", materialRunInput.getComment());

            // assert one MaterialInput exp.object exist
            assertEquals(1L, countMaterialInputObjects(c));

            run.delete(user);

            // assert we cleaned up properly
            ExpMaterialRunInputImpl y = impl.getMaterialInput(sampleIn.getRowId(), pa.getRowId());
            assertNull(y);

            // assert we deleted all MaterialInput exp.object
            assertEquals(0L, countMaterialInputObjects(c));
        }

        private int countMaterialInputObjects(Container c)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(c);
            filter.addCondition(FieldKey.fromParts("objecturi"), ":MaterialInput:", CompareType.CONTAINS);
            TableSelector ts = new TableSelector(getTinfoObject(), TableSelector.ALL_COLUMNS, filter, null);
            return (int) ts.getRowCount();
        }
    }
}
