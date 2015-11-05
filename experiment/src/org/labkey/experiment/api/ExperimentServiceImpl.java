/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.AbstractParameter;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentMaterialListener;
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
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProtocolImplementation;
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
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.ExperimentAuditViewFactory;
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
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ExperimentServiceImpl implements ExperimentService.Interface
{
    private DatabaseCache<MaterialSource> materialSourceCache;

    public static final String DEFAULT_MATERIAL_SOURCE_NAME = "Unspecified";

    private List<ExperimentRunTypeSource> _runTypeSources = new CopyOnWriteArrayList<>();
    private Set<ExperimentDataHandler> _dataHandlers = new HashSet<>();
    private List<ExperimentMaterialListener> _materialListeners = new CopyOnWriteArrayList<>();
    protected Map<String, DataType> _dataTypes = new HashMap<>();
    protected Map<String, ProtocolImplementation> _protocolImplementations = new HashMap<>();

    private static final List<ExperimentListener> _listeners = new CopyOnWriteArrayList<>();

    private static final ReentrantLock XAR_IMPORT_LOCK = new ReentrantLock();

    synchronized DatabaseCache<MaterialSource> getMaterialSourceCache()
    {
        if (materialSourceCache == null)
        {
            materialSourceCache = new DatabaseCache<>(getExpSchema().getScope(), 300, "Material source");
        }
        return materialSourceCache;
    }

    public ExpRunImpl getExpRun(int rowid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowid);
        ExperimentRun run = new TableSelector(getTinfoExperimentRun(), filter, null).getObject(ExperimentRun.class);
        return run == null ? null : new ExpRunImpl(run);
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
        Set<String> roles = ExperimentService.get().getDataInputRoles(container, ContainerFilter.CURRENT);
        // Remove case-only dupes
        Set<String> dedupedRoles = new CaseInsensitiveHashSet();
        for (Iterator<String> i = roles.iterator(); i.hasNext(); )
        {
            String role = i.next();
            if (!dedupedRoles.add(role))
            {
                i.remove();
            }
        }

        ActionURL postURL = new ActionURL(ExperimentController.ExportRunFilesAction.class, container);
        return new JspView<>("/org/labkey/experiment/fileExportOptions.jsp", new ExperimentController.ExportBean(LSIDRelativizer.FOLDER_RELATIVE, XarExportType.BROWSER_DOWNLOAD, defaultFilenamePrefix + ".zip", new ExperimentController.ExportOptionsForm(), roles, postURL));
    }

    public void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String comment)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setCreatedBy(user);
        event.setComment(comment);

        Container c = run != null ? run.getContainer() : protocol.getContainer();
        event.setContainerId(c.getId());
        event.setProjectId(c.getProject() == null ? null : c.getProject().getId());
        event.setIntKey1(runGroup == null ? null : runGroup.getRowId());

        event.setKey1(protocol.getLSID());
        if (run != null)
            event.setKey2(run.getLSID());
        event.setKey3(ExperimentAuditViewFactory.getKey3(protocol, run));
        event.setEventType(ExperimentAuditViewFactory.EXPERIMENT_AUDIT_EVENT);

        AuditLogService.get().addEvent(event);
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
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), new SimpleFilter(FieldKey.fromParts("LSID"), lsids, CompareType.IN), null).getArrayList(Data.class));
    }

    public List<ExpDataImpl> getExpDatas(Collection<Integer> rowids)
    {
        if (rowids.size() == 0)
            return null;
        return ExpDataImpl.fromDatas(new TableSelector(getTinfoData(), new SimpleFilter(FieldKey.fromParts("RowId"), rowids, CompareType.IN), null).getArrayList(Data.class));
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
        ExpDataImpl data;
        File f = null;
        // Check if it's in the database already
        if (uri.toString().startsWith("file:"))
        {
            f = new File(uri);
        }

        data = getExpDataByURL(uri.toString(), source.getXarContext().getContainer());
        if (data == null)
        {
            // Have to make a new one
            String name;
            if (f != null)
            {
                name = f.getName();
            }
            else
            {
                String[] parts = uri.toString().split("/");
                name = parts[parts.length - 1];
            }
            String path;
            if (f != null)
            {
                try
                {
                    f = new File(new URI(source.getCanonicalDataFileURL(f.getAbsolutePath())));
                    path = FileUtil.relativizeUnix(source.getRoot(), f, false);
                }
                catch (URISyntaxException e)
                {
                    path = f.toString();
                }
                catch (IOException e)
                {
                    path = f.toString();
                }
            }
            else
            {
                path = uri.toString();
            }

            Lsid lsid = new Lsid(LsidUtils.resolveLsidFromTemplate(AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION, source.getXarContext(), "Data", new AutoFileLSIDReplacer(path, source.getXarContext().getContainer(), source)));
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

    public List<ExpMaterialImpl> getExpMaterialsByName(String name, Container container, User user)
    {
        List<ExpMaterialImpl> result = getSamplesByName(container, user).get(name);
        return result == null ? Collections.<ExpMaterialImpl>emptyList() : result;
    }

    public ExpMaterialImpl getExpMaterial(int rowid)
    {
        Material material = new TableSelector(getTinfoMaterial()).getObject(rowid, Material.class);
        return material == null ? null : new ExpMaterialImpl(material);
    }

    public List<ExpMaterialImpl> getExpMaterials(Collection<Integer> rowids)
    {
        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts(ExpMaterialTable.Column.RowId.name()), rowids);
        TableSelector selector = new TableSelector(getTinfoMaterial(), filter, null);

        final List<ExpMaterialImpl> materials = new ArrayList<>(rowids.size());
        selector.forEach(new Selector.ForEachBlock<Material>()
        {
            @Override
            public void exec(Material material) throws SQLException
            {
                materials.add(new ExpMaterialImpl(material));
            }
        }, Material.class);

        return materials;
    }

    @Override
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
        SimpleFilter.FilterClause clause = containerFilter.createFilterClause(ExperimentService.get().getSchema(), FieldKey.fromParts("Container"), container);
        filter.addClause(clause);

        Set<String> selectNames = new LinkedHashSet<>();
        selectNames.add("Name");
        selectNames.add("RowId");
        TableSelector sampleTableSelector = new TableSelector(ExperimentService.get().getTinfoMaterial(), selectNames, filter, null);
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

        DbScope scope = ExperimentService.get().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
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
                    Lsid lsid = new Lsid(sampleSet.getMaterialLSIDPrefix() + "test");
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
            throw new IllegalArgumentException("name=" + name + " lsid="+lsid);
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

    private static final MaterialSource MISS_MARKER = new MaterialSource();

    public ExpSampleSetImpl getSampleSet(int rowId)
    {
        MaterialSource ms = getMaterialSourceCache().get(String.valueOf(rowId));

        if (null == ms)
        {
            ms = new TableSelector(getTinfoMaterialSource()).getObject(rowId, MaterialSource.class);

            if (null == ms)
                ms = MISS_MARKER;

            getMaterialSourceCache().put(String.valueOf(rowId), ms);
        }

        if (MISS_MARKER == ms)
            return null;

        return new ExpSampleSetImpl(ms);
    }

    public ExpSampleSetImpl getSampleSet(String lsid)
    {
        MaterialSource ms = getMaterialSource(lsid);
        if (ms == null)
            return null;
        return new ExpSampleSetImpl(ms);
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
            Number sampleSetCount = (Number)queryResult.get("SampleSetCount");
            if (sampleSetCount.intValue() == 1)
            {
                String sampleSetLSID = (String)queryResult.get("SampleSetLSID");
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
            result.put((String)queryResult.get("Role"), sampleSet);
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
        Protocol p = new TableSelector(getTinfoProtocol(), new SimpleFilter(FieldKey.fromParts("RowId"), rowid), null).getObject(Protocol.class);
        return p == null ? null : new ExpProtocolImpl(p);
    }

    public ExpProtocolImpl getExpProtocol(String lsid)
    {
        Protocol result = new TableSelector(getTinfoProtocol(), new SimpleFilter(FieldKey.fromParts("LSID"), lsid), null).getObject(Protocol.class);
        return result == null ? null : new ExpProtocolImpl(result);
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
    public ExpDataClassDataTable createDataClassDataTable(String name, UserSchema schema, ExpDataClass dataClass)
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

    public String generateLSID(Container container, Class<? extends ExpObject> clazz, String name)
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

    public ExpSampleSetImpl getSampleSet(Container container, String name)
    {
        return getSampleSet(generateLSID(container, ExpSampleSet.class, name));
    }

    public ExpSampleSetImpl getSampleSet(Container c, String name, boolean includeOtherContainers)
    {
        ExpSampleSetImpl ss = getSampleSet(c, name);
        if (ss == null && !c.isProject())
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
        SimpleFilter filter = createContainerFilter(container, user, includeOtherContainers);
        List<DataClass> classes = new TableSelector(getTinfoDataClass(), filter, null).getArrayList(DataClass.class);
        // Do the sort on the Java side to make sure it's always case-insensitive, even on Postgres
        return Collections.unmodifiableList(classes.stream().map(ExpDataClassImpl::new).sorted().collect(Collectors.toList()));
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
        SimpleFilter filter = createContainerFilter(c, user, includeOtherContainers)
                .addCondition(FieldKey.fromParts("name"), dataClassName);

        DataClass dataClass = new TableSelector(getTinfoDataClass(), filter, null).getObject(DataClass.class);
        if (dataClass == null)
            return null;

        return new ExpDataClassImpl(dataClass);
    }

    @Override
    public ExpDataClassImpl getDataClass(int rowId)
    {
        DataClass dataClass = new TableSelector(getTinfoDataClass()).getObject(rowId, DataClass.class);
        if (dataClass == null)
            return null;

        return new ExpDataClassImpl(dataClass);
    }

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

        return datas.stream().map(ExpDataImpl::new).collect(Collectors.toList());
    }

    @Override
    public ExpData getExpData(ExpDataClass dataClass, String name)
    {
        Domain d = dataClass.getDomain();
        if (d == null)
            throw new IllegalStateException("No domain for MaterialTypeSet '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        TableInfo table = ((ExpDataClassImpl)dataClass).getTinfo();
        if (table == null)
            throw new IllegalStateException("No table for MaterialTypeSet '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        SQLFragment sql = new SQLFragment()
                .append("SELECT * FROM ").append(getTinfoData(), "d")
                .append(", ").append(table, "t")
                .append(" WHERE t.lsid = d.lsid")
                .append(" AND d.classId = ?").add(dataClass.getRowId())
                .append(" AND d.Name = ?").add(name);

        Data data = new SqlSelector(table.getSchema().getScope(), sql).getObject(Data.class);

        return new ExpDataImpl(data);
    }

    @Override
    public ExpData getExpData(ExpDataClass dataClass, int rowId)
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

        return new ExpDataImpl(data);
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
    public Pair<Set<ExpData>, Set<ExpMaterial>> getParents(ExpProtocolOutput start)
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

    private boolean isUnknownMaterial(ExpProtocolOutput output)
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

        AttachmentService.get().deleteAttachments(run);

        run.deleteProtocolApplications(datasToDelete, user);

        //delete run properties and all children
        OntologyManager.deleteOntologyObject(run.getLSID(), run.getContainer(), true);
        
        SQLFragment sql = new SQLFragment("DELETE FROM exp.RunList WHERE ExperimentRunId = ?;\n");
        sql.add(run.getRowId());
        sql.append("UPDATE exp.ExperimentRun SET ReplacedByRunId = NULL WHERE ReplacedByRunId = ?;\n");
        sql.add(run.getRowId());
        sql.append("DELETE FROM exp.ExperimentRun WHERE RowId = ?;\n");
        sql.add(run.getRowId());

        new SqlExecutor(getExpSchema()).execute(sql);

        auditRunEvent(user, run.getProtocol(), run, null, "Run deleted");
    }


    public DbSchema getExpSchema()
    {
        return DbSchema.get("exp");
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
     * @return Object type for this lsid. Hmm should we reutnr a class
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

    public SimpleFilter createContainerFilter(Container container, User user, boolean includeProjectAndShared)
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
        SimpleFilter filter = new SimpleFilter();
        filter.addClause(new SimpleFilter.InClause(FieldKey.fromParts("Container"), containerIds));
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

    public ProtocolAction[] getProtocolActions(int parentProtocolRowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ParentProtocolId"), parentProtocolRowId);
        return new TableSelector(getTinfoProtocolAction(), filter, new Sort("+Sequence")).getArray(ProtocolAction.class);
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
                return createSampleSet(c, user, "Samples", null, properties, 0, -1, -1, -1);
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
        ExpSampleSetImpl matSource = getSampleSet(ExperimentService.get().getDefaultSampleSetLsid());
        if (null == matSource)
        {
            matSource = createSampleSet();
            matSource.setLSID(getDefaultSampleSetLsid());
            matSource.setName(DEFAULT_MATERIAL_SOURCE_NAME);
            matSource.setMaterialLSIDPrefix(new Lsid("Sample", DEFAULT_MATERIAL_SOURCE_NAME).toString() + "#");
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

    public ExpDataImpl getExpDataByURL(File file, @Nullable Container c)
    {
        File canonicalFile = FileUtil.getAbsoluteCaseSensitiveFile(file);
        try
        {
            String url = canonicalFile.toURI().toURL().toString();
            return getExpDataByURL(url, c);
        }
        catch (MalformedURLException e)
        {
            throw new UnexpectedException(e);
        }
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
        return new Lsid(generateLSID(container, ExpSampleSet.class, sourceName));
    }

    public Lsid getDataClassLsid(String name, Container container)
    {
        return new Lsid(generateLSID(container, ExpDataClass.class, name));
    }

    // TODO: @NotNull Collection<Integer> selectedRunIds
    public void deleteExperimentRunsByRowIds(Container container, final User user, int... selectedRunIds)
    {
        if (selectedRunIds == null || selectedRunIds.length == 0)
            return;

        for (int runId : selectedRunIds)
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
                        for (Dataset dataset : StudyService.get().getDatasetsForAssayRuns(Collections.<ExpRun>singletonList(run), user))
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
                                Collection<String> lsids = new TableSelector(tableInfo, Collections.singleton("LSID"), filter, null).getCollection(String.class);

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

    private int[] getRelatedProtocolIds(int[] selectedProtocolIds)
    {
        Set<Integer> allIds = new HashSet<>();
        for (int selectedProtocolId : selectedProtocolIds)
        {
            allIds.add(selectedProtocolId);
        }

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

        return ArrayUtils.toPrimitive(allIds.toArray(new Integer[allIds.size()]));
    }

    public List<ExpRunImpl> getExpRunsForProtocolIds(boolean includeRelated, int... protocolIds)
    {
        if (protocolIds.length == 0)
        {
            return Collections.emptyList();
        }

        int[] allProtocolIds;
        if (includeRelated)
        {
            allProtocolIds = getRelatedProtocolIds(protocolIds);
        }
        else
        {
            allProtocolIds = protocolIds;
        }

        if (allProtocolIds.length == 0)
        {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ");
        sb.append(getTinfoExperimentRun().getSelectName());
        sb.append(" WHERE ProtocolLSID IN (");
        sb.append("SELECT LSID FROM exp.Protocol WHERE RowId IN (");
        sb.append(StringUtils.join(ArrayUtils.toObject(allProtocolIds), ", "));
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
        sql.append(" WHERE (ChildProtocolId IN (" + protocolIds + ")");
        sql.append(" OR ParentProtocolId IN (" + protocolIds + ") );");
        Integer[] actionIds = new SqlSelector(getExpSchema(), sql).getArray(Integer.class);

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            for (Protocol protocol : protocols)
            {
                ExpProtocol protocolToDelete = new ExpProtocolImpl(protocol);
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
                DbCache.remove(getTinfoProtocol(), getCacheKey(protocol.getLSID()));
                OntologyManager.deleteOntologyObjects(c, protocol.getLSID());
            }

            executor.execute("DELETE FROM exp.Protocol WHERE RowId IN (" + protocolIds + ")");

            sql = new SQLFragment("SELECT RowId FROM exp.Protocol WHERE RowId NOT IN (SELECT ParentProtocolId FROM exp.ProtocolAction UNION SELECT ChildProtocolId FROM exp.ProtocolAction) AND Container = ?");
            sql.add(c.getId());
            int[] orphanedProtocolIds = ArrayUtils.toPrimitive(new SqlSelector(getExpSchema(), sql).getArray(Integer.class));
            deleteProtocolByRowIds(c, user, orphanedProtocolIds);

            transaction.addCommitTask(new Runnable()
            {
                @Override
                public void run()
                {
                    // Be sure that we clear the cache after we commit the overall transaction, in case it
                    // gets repopulated by another thread before then
                    AssayService.get().clearProtocolCache();
                }
            }, DbScope.CommitTaskOption.POSTCOMMIT, DbScope.CommitTaskOption.IMMEDIATE);

            transaction.commit();
        }
    }

    public void deleteMaterialByRowIds(User user, Container container, Collection<Integer> selectedMaterialIds)
    {
        if (selectedMaterialIds.isEmpty())
            return;

        try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            SQLFragment sql = new SQLFragment("SELECT * FROM exp.Material WHERE RowId ");
            getExpSchema().getSqlDialect().appendInClauseSql(sql, selectedMaterialIds);

            List<ExpMaterialImpl> materials = ExpMaterialImpl.fromMaterials(new SqlSelector(getExpSchema(), sql).getArrayList(Material.class));

            for (ExpMaterial material : materials)
            {
                if (!material.getContainer().hasPermission(user, DeletePermission.class))
                {
                    throw new UnauthorizedException();
                }
            }

            // Notify that a delete is about to happen
            for (ExperimentMaterialListener materialListener : _materialListeners)
            {
                materialListener.beforeDelete(materials);
            }

            for (ExpMaterial material : materials)
            {
                // Delete any runs using the material if the ProtocolImplementation allows deleting the run when an input is deleted.
                List<ExpRunImpl> runArray = getRunsUsingMaterials(material.getRowId());
                for (ExpRun run : ExperimentService.get().runsDeletedWithInput(runArray))
                {
                    Container runContainer = run.getContainer();
                    if (!runContainer.hasPermission(user, DeletePermission.class))
                        throw new UnauthorizedException();

                    deleteExperimentRunsByRowIds(run.getContainer(), user, run.getRowId());
                }

                OntologyManager.deleteOntologyObjects(container, material.getLSID());
            }

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            SQLFragment materialInputSQL = new SQLFragment("DELETE FROM exp.MaterialInput WHERE MaterialId ");
            getExpSchema().getSqlDialect().appendInClauseSql(materialInputSQL, selectedMaterialIds);
            executor.execute(materialInputSQL);
            SQLFragment materialSQL = new SQLFragment("DELETE FROM exp.Material WHERE RowId ");
            getExpSchema().getSqlDialect().appendInClauseSql(materialSQL, selectedMaterialIds);
            executor.execute(materialSQL);

            // Remove from search index
            SearchService ss = ServiceRegistry.get(SearchService.class);
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

    public void deleteDataByRowIds(Container container, Collection<Integer> selectedDataIds)
    {
        if (selectedDataIds.isEmpty())
            return;

        try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            SimpleFilter rowIdFilter = new SimpleFilter().addInClause(FieldKey.fromParts("RowId"), selectedDataIds);
            List<Data> datas = new TableSelector(getTinfoData(), rowIdFilter, null).getArrayList(Data.class);

            Map<Integer, List<String>> lsidsByClass = new LinkedHashMap<>();

            beforeDeleteData(ExpDataImpl.fromDatas(datas));
            for (Data data : datas)
            {
                if (!data.getContainer().equals(container))
                {
                    throw new SQLException("Attempting to delete a Data from another container");
                }
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

        deleteExpExperiment(user, experiment);
    }

    private void deleteExpExperiment(User user, ExpExperimentImpl experiment)
    {
        try (DbScope.Transaction t = ExperimentServiceImpl.get().getExpSchema().getScope().ensureTransaction())
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
            for(ExperimentListener listener: _listeners)
            {
                listener.beforeExperimentDeleted(experiment, user);
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

        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
        {
            // first delete the runs in the container, as that should be fast.  Deletes all Materials, Data,
            // and protocol applications and associated properties and parameters that belong to the run
            for (int runId : runIds)
            {
                deleteExperimentRunsByRowIds(c, user, runId);
            }
            ListService.Interface ls = ListService.get();
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

            OntologyManager.deleteAllObjects(c, user);

            // Delete DataClasses and their exp.Data members
            // Need to delete DataClass before SampleSets since they may be referenced by the DataClass
            for (ExpDataClassImpl dataClass : dataClasses)
            {
                dataClass.delete(user);
            }

            // delete material sources
            // now call the specialized function to delete the Materials that belong to the Material Source,
            // including the toplevel properties of the Materials, of which there are often many
            for (ExpSampleSet sampleSet : sampleSets)
            {
                sampleSet.delete(user);
            }

            SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);
            Table.delete(getTinfoActiveMaterialSource(), containerFilter);

            // Delete all the experiments/run groups/batches
            for (ExpExperimentImpl exp : exps)
            {
                deleteExpExperiment(user, exp);
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
            deleteDataByRowIds(c, dataIds);

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

        try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction())
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

    public void beforeDeleteData(List<ExpDataImpl> datas)
    {
        try
        {
            Map<ExperimentDataHandler, List<ExpData>> handlers = new HashMap<>();
            for (ExpData data : datas)
            {
                ExperimentDataHandler handler = data.findDataHandler();
                List<ExpData> list = handlers.get(handler);
                if (list == null)
                {
                    list = new ArrayList<>();
                    handlers.put(handler, list);
                }
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
    }

    public MaterialSource getMaterialSource(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
        return new TableSelector(getTinfoMaterialSource(), filter, null).getObject(MaterialSource.class);
    }

    public String getDefaultSampleSetLsid()
    {
        return new Lsid("SampleSource", "Default").toString();
    }


    @Override
    public List<ExpRunImpl> getRunsUsingDatas(List<ExpData> datas)
    {
        if (datas.isEmpty())
            return Collections.emptyList();

        List<Integer> ids = datas.stream().map(ExpData::getRowId).collect(Collectors.toList());
        return getRunsUsingDataIds(ids);
    }

    public List<ExpRunImpl> getRunsUsingDataIds(List<Integer> ids)
    {
        SimpleFilter.InClause in1 = new SimpleFilter.InClause(FieldKey.fromParts("DataID"), ids);
        SimpleFilter.InClause in2 = new SimpleFilter.InClause(FieldKey.fromParts("RowId"), ids);

        SQLFragment sql = new SQLFragment("SELECT * FROM exp.ExperimentRun WHERE\n" +
                            "RowId IN (SELECT pa.RunId FROM exp.ProtocolApplication pa WHERE pa.RowId IN\n" +
                            "(SELECT di.TargetApplicationId FROM exp.DataInput di WHERE ");
        sql.append(in1.toSQLFragment(Collections.<FieldKey, ColumnInfo>emptyMap(), getExpSchema().getSqlDialect()));
        sql.append(") UNION (SELECT d.SourceApplicationId FROM exp.Data d WHERE ");
        sql.append(in2.toSQLFragment(Collections.<FieldKey, ColumnInfo>emptyMap(), getExpSchema().getSqlDialect()));
        sql.append(")) ORDER BY Created DESC");

        return ExpRunImpl.fromRuns(new SqlSelector(getExpSchema(), sql).getArrayList(ExperimentRun.class));
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
        List<Integer> rowIds = dataClasses.stream().map(ExpObject::getRowId).collect(Collectors.toList());

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
        sql.append(" WHERE RowId IN (SELECT pa.RunId FROM ");
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
        sql.append("))");
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
        sql.append(" WHERE RowId IN (SELECT pa.RunId FROM ");
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
        sql.append("))");
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

    public void deleteSampleSet(int rowId, Container c, User user) throws ExperimentException
    {
        ExpSampleSet source = getSampleSet(rowId);
        if (null == source)
            throw new IllegalArgumentException("Can't find SampleSet with rowId " + rowId);
        if (!source.getContainer().equals(c))
            throw new ExperimentException("Trying to delete a SampleSet from a different container");

        try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            // Delete all Materials from the SampleSet
            SimpleFilter materialFilter = new SimpleFilter(FieldKey.fromParts("CpasType"), source.getLSID());
            Collection<Integer> materialIds = new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), Collections.singleton("RowId"), materialFilter, null).getCollection(Integer.class);
            deleteMaterialByRowIds(user, c, materialIds);

            deleteDomainObjects(source.getContainer(), source.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("DELETE FROM " + getTinfoActiveMaterialSource() + " WHERE MaterialSourceLSID = ?", source.getLSID());
            executor.execute("DELETE FROM " + getTinfoMaterialSource() + " WHERE RowId = ?", rowId);

            transaction.commit();
        }
        SchemaKey schemaPath = SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME);
        QueryService.get().fireQueryDeleted(user, c, null, schemaPath, Collections.singleton(source.getName()));

    }

    public int truncateDataClass(ExpDataClass dataClass, Container c)
    {
        assert getExpSchema().getScope().isTransactionActive();

        // Delete all exp.Data from the DataClass
        SimpleFilter dataClassFilter = new SimpleFilter(FieldKey.fromParts("classId"), dataClass.getRowId());
        Collection<Integer> dataIds = new TableSelector(ExperimentServiceImpl.get().getTinfoData(), Collections.singleton("RowId"), dataClassFilter, null).getCollection(Integer.class);
        deleteDataByRowIds(c, dataIds);
        return dataIds.size();
    }

    public void deleteDataClass(int rowId, Container c, User user) throws ExperimentException
    {
        ExpDataClass dataClass = getDataClass(rowId);
        if (null == dataClass)
            throw new IllegalArgumentException("Can't find DataClass with rowId " + rowId);
        if (!dataClass.getContainer().equals(c))
            throw new ExperimentException("Trying to delete a DataClass from a different container");

        Domain d = dataClass.getDomain();

        try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            truncateDataClass(dataClass, c);

            d.delete(user);

            deleteDomainObjects(dataClass.getContainer(), dataClass.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("DELETE FROM " + getTinfoDataClass() + " WHERE RowId = ?", rowId);

            transaction.commit();
        }
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
            protocolStep.setInputMaterials(new ArrayList<ExpMaterialImpl>());
            protocolStep.setInputDatas(new ArrayList<ExpDataImpl>());
            protocolStep.setOutputMaterials(new ArrayList<ExpMaterialImpl>());
            protocolStep.setOutputDatas(new ArrayList<ExpDataImpl>());
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
            mat.setSuccessorAppList(new ArrayList<ExpProtocolApplication>());
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

        new SqlSelector(getExpSchema(), dataSQL, runId).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet materialInputRS) throws SQLException
            {
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
                    mat.setSuccessorAppList(new ArrayList<ExpProtocolApplication>());
                }

                pa.getInputMaterials().add(mat);
                mat.getSuccessorApps().add(pa);

                if (pa.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput)
                {
                    expRun.getMaterialOutputs().add(mat);
                    outputMaterialMap.put(mat.getRowId(), mat);
                }
            }
        });

        // now hook up data inputs in both directions
        dataSQL = "SELECT TargetApplicationId, DataId"
                + " FROM " + getTinfoDataInput().getSelectName()
                + " WHERE TargetApplicationId IN"
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA"
                + " WHERE PA.RunId = ?)"
                + " ORDER BY TargetApplicationId, DataId";

        new SqlSelector(getExpSchema(), dataSQL, runId).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet dataInputRS) throws SQLException
            {
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
            sql.append(in.toSQLFragment(Collections.<FieldKey, ColumnInfo>emptyMap(), getExpSchema().getSqlDialect()));
            sql.append(" AND PA.RunId <> ? ORDER BY TargetApplicationId, MaterialId");
            sql.add(runId);

            new SqlSelector(getExpSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet materialOutputRS) throws SQLException
                {
                    Integer successorRunId = materialOutputRS.getInt("RunId");
                    Integer matId = materialOutputRS.getInt("MaterialId");
                    ExpMaterialImpl mat = outputMaterialMap.get(matId);
                    mat.addSuccessorRunId(successorRunId);
                }
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

                new SqlSelector(getExpSchema(), dataSQL, runId).forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet dataOutputRS) throws SQLException
                    {
                        int successorRunId = dataOutputRS.getInt("RunId");
                        Integer datId = dataOutputRS.getInt("DataId");
                        ExpDataImpl dat = outputDataMap.get(datId);
                        dat.addSuccessorRunId(successorRunId);
                    }
                });
            }
        }

        return expRun;
    }


    public ProtocolActionPredecessor[] getProtocolActionPredecessors(String parentProtocolLSID, String childProtocolLSID)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ChildProtocolLSID"), childProtocolLSID);
        filter.addCondition(FieldKey.fromParts("ParentProtocolLSID"), parentProtocolLSID);
        return new TableSelector(getTinfoProtocolActionPredecessorLSIDView(), filter, new Sort("+PredecessorSequence")).getArray(ProtocolActionPredecessor.class);
    }

    public List<Data> getOutputDataForApplication(int applicationId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceApplicationId"), applicationId);
        return new TableSelector(getTinfoData(), filter, null).getArrayList(Data.class);
    }

    public List<Material> getOutputMaterialForApplication(int applicationId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceApplicationId"), applicationId);
        return new TableSelector(getTinfoMaterial(), filter, null).getArrayList(Material.class);
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
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
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

            savePropertyCollection(protocol.retrieveObjectProperties(), protocol.getLSID(), protocol.getContainer(), !newProtocol);
            AssayService.get().clearProtocolCache();

            getExpSchema().getScope().addCommitTask(new Runnable()
            {
                @Override
                public void run()
                {
                    // Be sure that we clear the cache after we commit the overall transaction, in case it
                    // gets repopulated by another thread before then
                    AssayService.get().clearProtocolCache();
                }
            }, DbScope.CommitTaskOption.POSTCOMMIT);

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

    public void insertProtocolPredecessor(User user, int actionRowId, int predecessorRowId) throws SQLException {
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

        if (run.getFilePathRoot() == null)
        {
            throw new IllegalArgumentException("You must set the file path root on the experiment run");
        }

        List<ExpData> insertedDatas;
        User user = info.getUser();
        XarContext context = new XarContext("Simple Run Creation", run.getContainer(), user);

        try
        {
            try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction())
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

                ProtocolAction[] actions = getProtocolActions(parentProtocol.getRowId());
                if (actions.length != 3)
                {
                    throw new IllegalArgumentException("Protocol has the wrong number of steps for a simple protocol, it should have three");
                }
                ProtocolAction action1 = actions[0];
                assert action1.getSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE;
                assert action1.getChildProtocolId() == parentProtocol.getRowId();

                context.addSubstitution("ExperimentRun.RowId", Integer.toString(run.getRowId()));

                Date date = new Date();

                ProtocolAction action2 = actions[1];
                assert action2.getSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE;
                ExpProtocol protocol2 = getExpProtocol(action2.getChildProtocolId());

                ProtocolAction action3 = actions[2];
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

                protApp1.setActivityDate(date);
                protApp1.setActionSequence(action1.getSequence());
                protApp1.setRun(run);
                protApp1.setProtocol(parentProtocol);
                Map<String, ProtocolParameter> parentParams = parentProtocol.getProtocolParameters();
                ProtocolParameter parentLSIDTemplateParam = parentParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
                ProtocolParameter parentNameTemplateParam = parentParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
                assert parentLSIDTemplateParam != null : "Parent LSID Template was null";
                assert parentNameTemplateParam != null : "Parent Name Template was null";
                protApp1.setLSID(LsidUtils.resolveLsidFromTemplate(parentLSIDTemplateParam.getStringValue(), context, "ProtocolApplication"));
                protApp1.setName(parentNameTemplateParam.getStringValue());

                protApp1.save(user);

                addDataInputs(inputDatas, protApp1._object, user);
                addMaterialInputs(inputMaterials, protApp1._object, user);

                protApp2.setActivityDate(date);
                protApp2.setActionSequence(action2.getSequence());
                protApp2.setRun(run);
                protApp2.setProtocol(protocol2);

                Map<String, ProtocolParameter> coreParams = protocol2.getProtocolParameters();
                ProtocolParameter coreLSIDTemplateParam = coreParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
                ProtocolParameter coreNameTemplateParam = coreParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
                assert coreLSIDTemplateParam != null;
                assert coreNameTemplateParam != null;
                protApp2.setLSID(LsidUtils.resolveLsidFromTemplate(coreLSIDTemplateParam.getStringValue(), context, "ProtocolApplication"));
                protApp2.setName(coreNameTemplateParam.getStringValue());

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

                protApp3.setActivityDate(date);
                protApp3.setActionSequence(action3.getSequence());
                protApp3.setRun(run);
                protApp3.setProtocol(outputProtocol);

                Map<String, ProtocolParameter> outputParams = outputProtocol.getProtocolParameters();
                ProtocolParameter outputLSIDTemplateParam = outputParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
                ProtocolParameter outputNameTemplateParam = outputParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
                assert outputLSIDTemplateParam != null;
                assert outputNameTemplateParam != null;
                protApp3.setLSID(LsidUtils.resolveLsidFromTemplate(outputLSIDTemplateParam.getStringValue(), context, "ProtocolApplication"));
                protApp3.setName(outputNameTemplateParam.getStringValue());
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

            return run;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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
        for (ExpProtocol childProtocol : childProtocols)
        {
            if (childProtocol.getApplicationType() == ExpProtocol.ApplicationType.ProtocolApplication)
            {
                ExpProtocolApplicationImpl result = new ExpProtocolApplicationImpl(new ProtocolApplication());
                result.setProtocol(childProtocol);
                Lsid lsid = new Lsid(ExpProtocol.ApplicationType.ProtocolApplication.name(), GUID.makeGUID());
                result.setLSID(lsid);
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
    public ExpRun derive(Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas,
                                Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas,
                                ViewBackgroundInfo info, Logger log)
            throws ExperimentException
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
        String nameSeparator = " ";

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

        return saveSimpleExperimentRun(run, inputMaterials, inputDatas, outputMaterials, outputDatas,
                Collections.<ExpData, String>emptyMap(), info, log, false);
    }

    @Override
    public void registerExperimentMaterialListener(ExperimentMaterialListener listener)
    {
        _materialListeners.add(listener);
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

    public ExpSampleSetImpl createSampleSet()
    {
        return new ExpSampleSetImpl(new MaterialSource());
    }

    public ExpSampleSetImpl createSampleSet(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, int idCol1, int idCol2, int idCol3, int parentCol)
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

        Lsid lsid = getSampleSetLsid(name, c);
        Domain domain = PropertyService.get().createDomain(c, lsid.toString(), name);
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

        if (!hasNameProperty && idUri1 == null)
            throw new ExperimentException("Please provide either a 'Name' property or an index for idCol1");

        MaterialSource source = new MaterialSource();
        source.setLSID(lsid.toString());
        source.setName(name);
        source.setDescription(description);
        source.setMaterialLSIDPrefix(new Lsid("Sample", String.valueOf(c.getRowId()) + "." + PageFlowUtil.encode(name), "").toString());
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
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            domain.save(u);
            ss.save(u);
            DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);

            transaction.commit();
        }

        return ss;
    }

    public ExpDataClass createDataClass(Container c, User u, String name, String description, List<GWTPropertyDescriptor> properties, Integer sampleSetId, String nameExpression)
            throws ExperimentException
    {
        ExpDataClass existing = getDataClass(c, u, name, true);
        if (existing != null)
            throw new IllegalArgumentException("DataClass '" + name + "' already exists");

        if (sampleSetId != null)
        {
            ExpSampleSet ss = ExperimentService.get().getSampleSet(sampleSetId);
            if (ss == null)
                throw new IllegalArgumentException("SampleSet '" + sampleSetId + "' not found");

            if (!ss.getContainer().equals(c))
                throw new IllegalArgumentException("Associated SampleSet must be defined in the same container as this DataClass");
        }

        Lsid lsid = getDataClassLsid(name, c);
        Domain domain = PropertyService.get().createDomain(c, lsid.toString(), name);
        DomainKind kind = domain.getDomainKind();

        Set<String> reservedNames = kind.getReservedPropertyNames(domain);
        Set<String> lowerReservedNames = reservedNames.stream().map(String::
                toLowerCase).collect(Collectors.toSet());

        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        Set<String> propertyUris = new HashSet<>();
        for (GWTPropertyDescriptor pd : properties)
        {
            String propertyName = pd.getName().toLowerCase();
            if (lowerReservedNames.contains(propertyName))
                throw new IllegalArgumentException("Property name '" + propertyName + "' is a reserved name");

            DomainProperty dp = DomainUtil.addProperty(domain, pd, defaultValues, propertyUris, null);
        }

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
        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            OntologyManager.ensureObject(c, lsid.toString());

            domain.setPropertyForeignKeys(kind.getPropertyForeignKeys(c));
            domain.save(u);
            impl.save(u);
            DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);

            tx.commit();
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

    public ExpProtocol insertSimpleProtocol(ExpProtocol wrappedProtocol, User user) throws ExperimentException
    {
        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction(ExperimentService.get().getProtocolImportLock()))
        {
            if (ExperimentService.get().getExpProtocol(wrappedProtocol.getLSID()) != null)
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

    public void addExperimentListener(ExperimentListener listener)
    {
        _listeners.add(listener);
    }
}
