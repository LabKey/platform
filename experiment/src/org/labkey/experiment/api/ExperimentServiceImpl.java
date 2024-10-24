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

import com.google.common.collect.Iterables;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.assay.AssayWellExclusionService;
import org.labkey.api.assay.DefaultAssayRunCreator;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.ExperimentAuditEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.AbstractParameter;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentProtocolHandler;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.LsidType;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ColumnExporter;
import org.labkey.api.exp.api.DataClassDomainKindProperties;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpDataProtocolInput;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageEdge;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialProtocolInput;
import org.labkey.api.exp.api.ExpMaterialRunInput;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInputCriteria;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunAttachmentParent;
import org.labkey.api.exp.api.ExpRunEditor;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.ObjectReferencer;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpDataClassTable;
import org.labkey.api.exp.query.ExpDataInputTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpRunGroupMapTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SampleStatusTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LSIDRelativizer;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.exp.xar.XarConstants;
import org.labkey.api.files.FileContentService;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataUnavailableException;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryViewProvider;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ReentrantLockWithName;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SubstitutionFormat;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.ExperimentAuditProvider;
import org.labkey.experiment.FileLinkFileListener;
import org.labkey.experiment.MissingFilesCheckInfo;
import org.labkey.experiment.XarExportType;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.XarReader;
import org.labkey.experiment.api.property.DomainPropertyManager;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.lineage.ExpLineageServiceImpl;
import org.labkey.experiment.pipeline.ExpGeneratorHelper;
import org.labkey.experiment.pipeline.ExperimentPipelineJob;
import org.labkey.experiment.pipeline.MoveRunsPipelineJob;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;
import org.labkey.experiment.xar.XarExportSelection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.labkey.api.data.CompareType.IN;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTCOMMIT;
import static org.labkey.api.data.DbScope.CommitTaskOption.POSTROLLBACK;
import static org.labkey.api.data.NameGenerator.ANCESTOR_INPUT_PREFIX_DATA;
import static org.labkey.api.data.NameGenerator.ANCESTOR_INPUT_PREFIX_MATERIAL;
import static org.labkey.api.data.NameGenerator.EXPERIMENTAL_ALLOW_GAP_COUNTER;
import static org.labkey.api.data.NameGenerator.EXPERIMENTAL_WITH_COUNTER;
import static org.labkey.api.exp.OntologyManager.getTinfoObject;
import static org.labkey.api.exp.XarContext.XAR_JOB_ID_NAME;
import static org.labkey.api.exp.api.ExpProtocol.ApplicationType.ExperimentRun;
import static org.labkey.api.exp.api.ExpProtocol.ApplicationType.ExperimentRunOutput;
import static org.labkey.api.exp.api.ExpProtocol.ApplicationType.ProtocolApplication;
import static org.labkey.api.exp.api.ExperimentJSONConverter.DATA_INPUTS_ALIAS_PREFIX;
import static org.labkey.api.exp.api.ExperimentJSONConverter.MATERIAL_INPUTS_ALIAS_PREFIX;
import static org.labkey.api.exp.api.NameExpressionOptionService.NAME_EXPRESSION_REQUIRED_MSG;
import static org.labkey.api.exp.api.NameExpressionOptionService.NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS;
import static org.labkey.api.exp.api.ProvenanceService.PROVENANCE_PROTOCOL_LSID;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.rollup;

public class ExperimentServiceImpl implements ExperimentService, ObjectReferencer, SearchService.DocumentProvider
{
    private static final Logger LOG = LogHelper.getLogger(ExperimentServiceImpl.class, "Experiment infrastructure including maintaining runs and lineage");

    private final Cache<Integer, ExpProtocolImpl> PROTOCOL_ROW_ID_CACHE = DatabaseCache.get(getExpSchema().getScope(), CacheManager.UNLIMITED, CacheManager.HOUR, "Protocol by RowId",
        (key, argument) -> getExpProtocol(new SimpleFilter(FieldKey.fromParts("RowId"), key)));

    private final Cache<String, ExpProtocolImpl> PROTOCOL_LSID_CACHE = DatabaseCache.get(getExpSchema().getScope(), CacheManager.UNLIMITED, CacheManager.HOUR, "Protocol by LSID",
        (key, argument) -> getExpProtocol(new SimpleFilter(FieldKey.fromParts("LSID"), key)));
    private final Cache<String, ExperimentRun> EXPERIMENT_RUN_CACHE = DatabaseCache.get(getExpSchema().getScope(), getTinfoExperimentRun().getCacheSize(), "Experiment Run by LSID", new ExperimentRunCacheLoader());

    private final Cache<String, SortedSet<DataClass>> dataClassCache = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Data classes", (containerId, argument) ->
    {
        Container c = ContainerManager.getForId(containerId);
        if (c == null)
            return Collections.emptySortedSet();

        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        return Collections.unmodifiableSortedSet(new TreeSet<>(new TableSelector(getTinfoDataClass(), filter, null).getCollection(DataClass.class)));
    });

    private static final List<ExperimentListener> _listeners = new CopyOnWriteArrayList<>();
    private static final ReentrantLock XAR_IMPORT_LOCK = new ReentrantLockWithName(ExperimentServiceImpl.class, "XAR_IMPORT_LOCK");

    private final List<ExperimentRunTypeSource> _runTypeSources = new CopyOnWriteArrayList<>();
    private final Set<ExperimentDataHandler> _dataHandlers = new HashSet<>();
    private final List<ExpRunEditor> _runEditors = new ArrayList<>();
    private final Map<String, DataType> _dataTypes = new HashMap<>();
    private final Map<String, ProtocolImplementation> _protocolImplementations = new HashMap<>();
    private final Map<String, ExpProtocolInputCriteria.Factory> _protocolInputCriteriaFactories = new HashMap<>();
    private final Set<ExperimentProtocolHandler> _protocolHandlers = new HashSet<>();
    private final List<ObjectReferencer> _objectReferencers = new ArrayList<>();
    private final List<ColumnExporter> _columnExporters = new ArrayList<>();

    private final List<QueryViewProvider<ExpRun>> _runInputsQueryViews = new CopyOnWriteArrayList<>();
    private final List<QueryViewProvider<ExpRun>> _runOutputsQueryViews = new CopyOnWriteArrayList<>();

    private final List<NameExpressionType> _nameExpressionTypes = new CopyOnWriteArrayList<>();

    private Cache<String, SortedSet<DataClass>> getDataClassCache()
    {
        return dataClassCache;
    }

    public void clearDataClassCache(@Nullable Container c)
    {
        LOG.debug("clearDataClassCache: " + (c == null ? "all" : c.getPath()));
        if (c == null)
            dataClassCache.clear();
        else
            dataClassCache.remove(c.getId());
    }

    private @NotNull List<ExperimentRun> getExperimentRuns(SimpleFilter filter)
    {
        return new TableSelector(getTinfoExperimentRun(), filter, null).getArrayList(ExperimentRun.class);
    }

    @Override
    public @Nullable ExpRunImpl getExpRun(int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpRunTable.Column.RowId.name()), rowId);
        ExperimentRun run = new TableSelector(getTinfoExperimentRun(), filter, null).getObject(ExperimentRun.class);
        return run == null ? null : new ExpRunImpl(run);
    }

    private List<ExpRunImpl> getExpRuns(SimpleFilter filter)
    {
        return ExpRunImpl.fromRuns(getExperimentRuns(filter));
    }

    @Override
    public List<ExpRunImpl> getExpRuns(Collection<Integer> rowIds)
    {
        if (rowIds == null || rowIds.isEmpty())
            return emptyList();
        return getExpRuns(new SimpleFilter().addInClause(FieldKey.fromParts(ExpRunTable.Column.RowId.name()), rowIds));
    }

    @Override
    public ReentrantLock getProtocolImportLock()
    {
        return XAR_IMPORT_LOCK;
    }

    @Override
    public HttpView<?> createRunExportView(Container container, String defaultFilenamePrefix)
    {
        ActionURL postURL = new ActionURL(ExperimentController.ExportRunsAction.class, container);
        return new JspView<>("/org/labkey/experiment/XARExportOptions.jsp", new ExperimentController.ExportBean(LSIDRelativizer.FOLDER_RELATIVE, XarExportType.BROWSER_DOWNLOAD, defaultFilenamePrefix + ".xar", new ExperimentController.ExportOptionsForm(), null, postURL));
    }

    @Override
    public HttpView<?> createFileExportView(Container container, String defaultFilenamePrefix)
    {
        Set<String> roles = getDataInputRoles(container, ContainerFilter.current(container));
        // Remove case-only dupes
        Set<String> dedupedRoles = new CaseInsensitiveHashSet();
        roles.removeIf(role -> !dedupedRoles.add(role));

        ActionURL postURL = new ActionURL(ExperimentController.ExportRunFilesAction.class, container);
        return new JspView<>("/org/labkey/experiment/fileExportOptions.jsp", new ExperimentController.ExportBean(LSIDRelativizer.FOLDER_RELATIVE, XarExportType.BROWSER_DOWNLOAD, defaultFilenamePrefix + ".zip", new ExperimentController.ExportOptionsForm(), roles, postURL));
    }

    @Override
    public void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String comment)
    {
        auditRunEvent(user, protocol, run, runGroup, comment, null);
    }

    @Override
    public void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String comment, String userComment)
    {
        Container c = run != null ? run.getContainer() : protocol.getContainer();
        ExperimentAuditEvent event = new ExperimentAuditEvent(c.getId(), comment);
        event.setUserComment(userComment);
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
        sql.append(containerFilter.getSQLFragment(getSchema(), new SQLFragment("er.Container")));

        // Translate the LSIDs into protocol objects
        List<ExpProtocolImpl> result = new ArrayList<>();
        for (String protocolLSID : new SqlSelector(getSchema(), sql).getArrayList(String.class))
        {
            result.add(getExpProtocol(protocolLSID));
        }
        return result;
    }

    @Override
    public @Nullable ExperimentProtocolHandler getExperimentProtocolHandler(@NotNull ExpProtocol protocol)
    {
        if (protocol.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun)
        {
            return getExperimentRunType(protocol);
        }
        else if (protocol.getApplicationType() == ExpProtocol.ApplicationType.ProtocolApplication)
        {
            return Handler.Priority.findBestHandler(_protocolHandlers, protocol);
        }
        return null;
    }

    @Nullable
    @Override
    public ExperimentRunType getExperimentRunType(@NotNull ExpProtocol protocol)
    {
        Set<ExperimentRunType> types = getExperimentRunTypes(protocol.getContainer());
        return Handler.Priority.findBestHandler(types, protocol);
    }

    @Nullable
    @Override
    public ExperimentRunType getExperimentRunType(@NotNull String description, @Nullable Container container)
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

    @Override
    public @Nullable ExpRunImpl getExpRun(String lsid)
    {
        ExperimentRun run = getExperimentRun(lsid);
        if (run == null)
            return null;
        return new ExpRunImpl(run);
    }

    @Override
    public List<ExpRunImpl> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol)
    {
        return getExpRuns(container, parentProtocol, childProtocol, run -> true);
    }

    @Override
    public List<ExpRunImpl> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol, @NotNull Predicate<ExpRun> filterFn)
    {

        SQLFragment sql = new SQLFragment();
        if (parentProtocol != null)
        {
            sql.append("\nER.ProtocolLSID = ?");
            sql.add(parentProtocol.getLSID());
        }
        if (childProtocol != null)
        {
            if (parentProtocol != null)
                sql.append(" AND ");

            sql.append("\nER.RowId IN (SELECT PA.RunId "
                    + " FROM exp.ProtocolApplication PA "
                    + " WHERE PA.ProtocolLSID = ? ) ");
            sql.add(childProtocol.getLSID());
        }

        return getExpRuns(sql, filterFn, container);
    }

    @Override
    public boolean hasExpRuns(Container container, @NotNull Predicate<ExpRun> filterFn)
    {
        SQLFragment sql = new SQLFragment(" SELECT ER.* "
                + " FROM exp.ExperimentRun ER "
                + " WHERE ER.Container = ? ");
        sql.add(container.getId());

        try (Stream<ExperimentRun> runs = new SqlSelector(getSchema(), sql).setJdbcCaching(false).uncachedStream(ExperimentRun.class))
        {
            return runs.map(ExpRunImpl::new).anyMatch(filterFn);
        }
    }

    @Override
    public List<ExpRunImpl> getExpRuns(@Nullable SQLFragment filterSQL, @NotNull Predicate<ExpRun> filterFn, @NotNull Container container)
    {
        SQLFragment sql = new SQLFragment(" SELECT ER.* "
                + " FROM exp.ExperimentRun ER "
                + " WHERE ER.Container = ? ");
        sql.add(container.getId());

        if (null != filterSQL && !filterSQL.isEmpty())
            sql.append(" AND " ).append(filterSQL);

        sql.append(" ORDER BY ER.RowId ");

        try (Stream<ExperimentRun> runs = new SqlSelector(getSchema(), sql).setJdbcCaching(false).uncachedStream(ExperimentRun.class))
        {
            return runs.map(ExpRunImpl::new).filter(filterFn).toList();
        }
    }

    @Override
    public List<ExpRunImpl> getExpRunsForJobId(int jobId)
    {
        return getExpRuns(new SimpleFilter(FieldKey.fromParts(ExpExperimentTable.Column.JobId.name()), jobId));
    }

    @Override
    public List<ExpRunImpl> getExpRunsForFilePathRoot(File filePathRoot)
    {
        String path = filePathRoot.getAbsolutePath();
        return getExpRuns(new SimpleFilter(FieldKey.fromParts(ExpExperimentTable.Column.FilePathRoot.name()), path));
    }

    @Override
    public ExpRunImpl createExperimentRun(Container container, String name)
    {
        ExperimentRun run = new ExperimentRun();
        run.setName(name);
        run.setLSID(generateGuidLSID(container, "Run"));
        run.setContainer(container);
        return new ExpRunImpl(run);
    }

    @Override
    public ExpRun createRunForProvenanceRecording(Container container, User user, RecordedActionSet actionSet, String runName, @Nullable Integer runJobId) throws ExperimentException, ValidationException
    {
        try
        {
            ExpProtocol protocol;
            try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction(getProtocolImportLock()))
            {
                List<String> sequenceProtocols = new ArrayList<>();
                Map<String, ExpProtocol> protocolCache = new HashMap<>();
                for (RecordedAction ra : actionSet.getActions())
                {
                    if (ra.isStart() || ra.isEnd())
                        continue;

                    String stepName = ra.getName();
                    sequenceProtocols.add(stepName);
                    if (!protocolCache.containsKey(stepName))
                    {
                        // Check if it's in the database already
                        ExpProtocol stepProtocol = getExpProtocol(container, stepName);
                        if (stepProtocol == null)
                        {
                            stepProtocol = createExpProtocol(container, ProtocolApplication, stepName);
                            stepProtocol.save(user);
                        }
                        protocolCache.put(stepName, stepProtocol);
                    }
                }
                Lsid provenanceLsid = new Lsid(PROVENANCE_PROTOCOL_LSID + container.getEntityId().toString());
                protocol = ExpGeneratorHelper.ensureProtocol(container, user, protocolCache, sequenceProtocols, provenanceLsid, runName, LOG);

                transaction.commit();
            }
            return ExpGeneratorHelper.insertRun(container, user, actionSet, runName, runJobId, protocol, LOG, null, null);
        }
        catch (ExperimentException | ValidationException e)
        {
            LOG.error(e);
            throw e;
        }
    }

    private @NotNull List<Data> getDatas(SimpleFilter filter, @Nullable Sort sort)
    {
        return new TableSelector(getTinfoData(), filter, sort).getArrayList(Data.class);
    }

    private @Nullable ExpDataImpl getExpData(SimpleFilter filter)
    {
        Data data = new TableSelector(getTinfoData(), filter, null).getObject(Data.class);
        return data == null ? null : new ExpDataImpl(data);
    }

    @Override
    public @Nullable ExpDataImpl getExpData(int rowId)
    {
        return getExpData(new SimpleFilter(FieldKey.fromParts("RowId"), rowId));
    }

    @Override
    public @Nullable ExpDataImpl getExpData(String lsid)
    {
        return getExpData(new SimpleFilter(FieldKey.fromParts("LSID"), lsid));
    }

    private @Nullable ExpDataImpl getExpDataByObjectId(Integer objectId)
    {
        return getExpData(new SimpleFilter(FieldKey.fromParts("ObjectId"), objectId));
    }

    private @NotNull List<ExpDataImpl> getExpDatas(SimpleFilter filter)
    {
        return getExpDatas(filter, null);
    }

    private @NotNull List<ExpDataImpl> getExpDatas(SimpleFilter filter, @Nullable Sort sort)
    {
        return ExpDataImpl.fromDatas(getDatas(filter, sort));
    }

    @Override
    public @NotNull List<ExpDataImpl> getExpDatas(int... rowIds)
    {
        return getExpDatas(Arrays.stream(rowIds).boxed().toList());
    }

    @Override
    public @NotNull List<ExpDataImpl> getExpDatasByLSID(Collection<String> lsids)
    {
        if (lsids.isEmpty())
            return Collections.emptyList();

        return getExpDatas(new SimpleFilter(FieldKey.fromParts(ExpDataTable.Column.LSID.name()), lsids, IN));
    }

    @Override
    public @NotNull List<ExpDataImpl> getExpDatas(Collection<Integer> rowIds)
    {
        if (rowIds.isEmpty())
            return Collections.emptyList();

        return getExpDatas(new SimpleFilter(FieldKey.fromParts(ExpDataTable.Column.RowId.name()), rowIds, IN));
    }

    @Override
    public @NotNull List<? extends ExpData> getExpDatas(@NotNull ExpDataClass dataClass, Collection<Integer> rowIds)
    {
        if (rowIds.isEmpty())
            return Collections.emptyList();

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpDataTable.Column.RowId.name()), rowIds, IN);
        filter.addCondition(FieldKey.fromParts("classId"), dataClass.getRowId());

        return getExpDatas(filter);
    }

    @Override
    public List<ExpDataImpl> getExpDatas(Container container, @Nullable DataType type, @Nullable String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (type != null)
            filter.addWhereClause(Lsid.namespaceFilter(ExpDataTable.Column.LSID.name(), type.getNamespacePrefix()), null);
        if (name != null)
            filter.addCondition(FieldKey.fromParts(ExpDataTable.Column.Name.name()), name);

        return getExpDatas(filter);
    }

    public List<ExpDataImpl> getOutputDatas(int runRowId, @Nullable DataType type)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RunId"), runRowId);
        if (type != null)
            filter.addWhereClause(Lsid.namespaceFilter(ExpDataTable.Column.LSID.name(), type.getNamespacePrefix()), null);

        return getExpDatas(filter);
    }

    @Override
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

    @Override
    public ExpDataImpl createData(Container container, @NotNull DataType type)
    {
        Lsid lsid = new Lsid(generateGuidLSID(container, type));
        return createData(container, lsid.getObjectId(), lsid.toString());
    }

    @Override
    public ExpDataImpl createData(Container container, @NotNull DataType type, @NotNull String name)
    {
        return createData(container, type, name, false);
    }

    @Override
    public ExpDataImpl createData(Container container, @NotNull DataType type, @NotNull String name, boolean generated)
    {
        return createData(container, name, generateLSID(container, type, name), generated);
    }

    @Override
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
    @Override
    public List<ExpMaterialImpl> getExpMaterialsByName(@NotNull String name, @Nullable Container container, User user)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.Name.name()), name);
        if (container != null)
            filter.addCondition(FieldKey.fromParts("Container"), container);

        return getExpMaterials(filter)
            .stream()
            .filter(m -> m.getContainer().hasPermission(user, ReadPermission.class) && m.getSampleType() != null)
            .toList();
    }

    public @Nullable ExpMaterialImpl getExpMaterial(SimpleFilter filter)
    {
        Material material = new TableSelector(getTinfoMaterial(), filter, null).getObject(Material.class);
        return material == null ? null : new ExpMaterialImpl(material);
    }

    @Override
    public @Nullable ExpMaterialImpl getExpMaterial(int rowId)
    {
        return getExpMaterial(new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId.name()), rowId));
    }

    @Override
    public @Nullable ExpMaterialImpl getExpMaterial(String lsid)
    {
        return getExpMaterial(new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.LSID.name()), lsid));
    }

    @Override
    public @Nullable ExpMaterialImpl getExpMaterial(int rowId, ContainerFilter containerFilter)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId.name()), rowId);
        if (containerFilter != null)
            filter.addClause(containerFilter.createFilterClause(getExpSchema(), FieldKey.fromParts("Container")));

        return getExpMaterial(filter);
    }

    @Override
    public @Nullable ExpMaterialImpl getExpMaterial(Container c, User u, int rowId, @Nullable ExpSampleType sampleType)
    {
        List<ExpMaterialImpl> materials = getExpMaterials(c, u, List.of(rowId), sampleType);
        if (materials == null || materials.isEmpty())
            return null;
        if (materials.size() > 1)
            throw new IllegalArgumentException("Expected 0 or 1 samples, got: " + materials.size());
        return materials.get(0);
    }

    public List<Integer> findIdsNotPermittedForOperation(List<? extends ExpMaterial> candidates, SampleTypeService.SampleOperations operation)
    {
        if (!SampleStatusService.get().supportsSampleStatus())
            return Collections.emptyList();

        return SampleTypeService.get().getSamplesNotPermitted(candidates, operation)
                .stream()
                .map(ExpObject::getRowId).collect(Collectors.toList());
    }

    private @NotNull List<Material> getMaterials(SimpleFilter filter, @Nullable Sort sort)
    {
        return new TableSelector(getTinfoMaterial(), filter, sort).getArrayList(Material.class);
    }

    private @NotNull List<ExpMaterialImpl> getExpMaterials(SimpleFilter filter)
    {
        return getExpMaterials(filter, null);
    }

    public @NotNull List<ExpMaterialImpl> getExpMaterials(SimpleFilter filter, @Nullable Sort sort)
    {
        return ExpMaterialImpl.fromMaterials(getMaterials(filter, sort));
    }

    @Override
    @NotNull
    public List<ExpMaterialImpl> getExpMaterials(Collection<Integer> rowIds)
    {
        if (rowIds.isEmpty())
            return emptyList();

        return getExpMaterials(new SimpleFilter().addInClause(FieldKey.fromParts(ExpMaterialTable.Column.RowId.name()), rowIds));
    }

    @Override
    @NotNull
    public List<ExpMaterialImpl> getExpMaterialsByLsid(Collection<String> lsids)
    {
        if (lsids.isEmpty())
            return emptyList();

        return getExpMaterials(new SimpleFilter().addInClause(FieldKey.fromParts(ExpMaterialTable.Column.LSID.name()), lsids));
    }

    @Override
    @Nullable
    public List<ExpMaterialImpl> getExpMaterials(Container container, User user, Collection<Integer> rowIds, @Nullable ExpSampleType sampleType)
    {
        if (rowIds.isEmpty())
            return emptyList();

        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts("RowId"), rowIds);
        if (sampleType != null)
            filter.addCondition(FieldKey.fromParts("CpasType"), sampleType.getLSID());

        // SampleType may live in different container
        ContainerFilter containerFilter = getContainerFilterTypeForFind(container).create(container, user);
        filter.addClause(containerFilter.createFilterClause(getSchema(), FieldKey.fromParts("Container")));

        return getExpMaterials(filter);
    }

    public List<ExpMaterialImpl> getExpMaterialsByObjectId(ContainerFilter containerFilter, Collection<Integer> objectIds)
    {
        if (objectIds.isEmpty())
            return emptyList();

        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts("ObjectId"), objectIds);
        filter.addClause(containerFilter.createFilterClause(getSchema(), FieldKey.fromParts("Container")));

        return getExpMaterials(filter);
    }

    @Override
    public @NotNull ExpMaterialImpl createExpMaterial(Container container, Lsid lsid)
    {
        return createExpMaterial(container, lsid.toString(), lsid.getObjectId());
    }

    @Override
    public @NotNull ExpMaterialImpl createExpMaterial(Container container, String lsid, String name)
    {
        ExpMaterialImpl result = new ExpMaterialImpl(new Material());
        result.setContainer(container);
        result.setLSID(lsid);
        result.setName(name);
        return result;
    }

    private static final int INDEXING_LIMIT = 10_000;

    @Override
    public void enumerateDocuments(final @NotNull SearchService.IndexTask task, final @NotNull Container c, final Date modifiedSince)
    {
        task.addRunnable(() -> {
            for (ExpSampleTypeImpl sampleType : getIndexableSampleTypes(c, modifiedSince))
            {
                sampleType.index(task);
            }
        }, SearchService.PRIORITY.bulk);

        task.addRunnable(() -> indexMaterials(task, c, modifiedSince, 0), SearchService.PRIORITY.bulk);

        task.addRunnable(() -> {
            for (ExpDataClassImpl dataClass : getIndexableDataClasses(c, modifiedSince))
            {
                dataClass.index(task);
            }
        }, SearchService.PRIORITY.bulk);

        task.addRunnable(() -> indexData(task, c, modifiedSince, 0), SearchService.PRIORITY.bulk);
    }

    @Override
    public void indexDeleted()
    {
        // Clear the last indexed value on all tables that back a search document
        for (TableInfo indexedTable : List.of(getTinfoSampleType(), getTinfoDataClass()))
        {
            new SqlExecutor(ExperimentService.get().getSchema()).execute("UPDATE " + indexedTable +
                    " SET LastIndexed = NULL WHERE LastIndexed IS NOT NULL");
        }

        for (TableInfo indexedTable : List.of(getTinfoMaterialIndexed(), getTinfoDataIndexed()))
        {
            new SqlExecutor(ExperimentService.get().getSchema()).execute("TRUNCATE TABLE " + indexedTable);
        }
    }

    private void indexMaterials(final @NotNull SearchService.IndexTask task, final @NotNull Container container, final Date modifiedSince, int minRowId)
    {
        final String materialAlias = "_m_";
        final String materialIndexedAlias = "_mi_";
        // Big hack to prevent indexing study specimens and bogus samples created from some plate assays (Issue 46037). Also in ExpMaterialImpl.index()
        SQLFragment sql = new SQLFragment("SELECT ").append(materialAlias).append(".* FROM ").
                append(getTinfoMaterial(), "_m_").
                append(" LEFT OUTER JOIN ").append(getTinfoMaterialIndexed(), "_mi_").
                append(" ON ").append(materialAlias).append(".RowId = ").
                append(materialIndexedAlias).append(".MaterialId WHERE Container = ? AND LSID NOT LIKE '%:" +
                        StudyService.SPECIMEN_NAMESPACE_PREFIX + "%' AND cpastype != 'Material' AND RowId > ?");
        sql.add(container.getId());
        sql.add(minRowId);
        SQLFragment modifiedSQL = new SearchService.LastIndexedClause(getTinfoMaterial(), modifiedSince, materialAlias, getTinfoMaterialIndexed(), materialIndexedAlias).toSQLFragment(null, null);
        if (!modifiedSQL.isEmpty())
            sql.append(" AND ").append(modifiedSQL);
        sql.append(" ORDER BY RowId");
        sql = getSchema().getSqlDialect().limitRows(sql, INDEXING_LIMIT);
        SqlSelector selector = new SqlSelector(getSchema(), sql);
        selector.setJdbcCaching(false);
        MutableInt maxRowIdProcessed = new MutableInt(minRowId);

        int rowCount = selector.forEach(Material.class, m -> {
            ExpMaterialImpl expMaterial = new ExpMaterialImpl(m);
            task.addResource(expMaterial.createIndexDocument(), SearchService.PRIORITY.bulk);
            maxRowIdProcessed.setValue(Math.max(maxRowIdProcessed.getValue(), expMaterial.getRowId()));
        });

        if (rowCount == INDEXING_LIMIT)
        {
            // Requeue for the next batch. This avoids overwhelming the indexer's queue with documents
            task.addRunnable(() -> indexMaterials(task, container, modifiedSince, maxRowIdProcessed.getValue()), SearchService.PRIORITY.bulk);
        }
    }

    public void indexData(final @NotNull SearchService.IndexTask task, final @NotNull Container container, final Date modifiedSince, int minRowId)
    {
        final String dataAlias = "_d_";
        final String dataIndexedAlias = "_di_";

        SQLFragment sql = new SQLFragment("SELECT ").append(dataAlias).append(".* FROM ").
                append(getTinfoData(), dataAlias).
                append(" LEFT OUTER JOIN ").append(getTinfoDataIndexed(), dataIndexedAlias).
                append(" ON ").append(dataAlias).append(".RowId = ").append(dataIndexedAlias).append(".DataId ").
                append(" WHERE Container = ? AND classId IS NOT NULL AND RowId > ?");
        sql.add(container.getId());
        sql.add(minRowId);
        SQLFragment modifiedSQL = new SearchService.LastIndexedClause(getTinfoData(), modifiedSince, dataAlias, getTinfoDataIndexed(), dataIndexedAlias).toSQLFragment(null, null);
        if (!modifiedSQL.isEmpty())
            sql.append(" AND ").append(modifiedSQL);
        sql.append(" ORDER BY RowId");

        sql = getSchema().getSqlDialect().limitRows(sql, INDEXING_LIMIT);
        SqlSelector selector = new SqlSelector(getSchema(), sql);
        selector.setJdbcCaching(false);
        MutableInt maxRowIdProcessed = new MutableInt(minRowId);

        int rowCount = selector.forEach(Data.class, d -> {
            ExpDataImpl expData = new ExpDataImpl(d);
            task.addResource(expData.createDocument(), SearchService.PRIORITY.bulk);
            maxRowIdProcessed.setValue(Math.max(maxRowIdProcessed.getValue(), expData.getRowId()));
        });

        if (rowCount == INDEXING_LIMIT)
        {
            // Requeue for the next batch. This avoids overwhelming the indexer's queue with documents
            task.addRunnable(() -> indexData(task, container, modifiedSince, maxRowIdProcessed.getValue()), SearchService.PRIORITY.bulk);
        }
    }

    public List<ExpDataClassImpl> getIndexableDataClasses(Container container, @Nullable Date modifiedSince)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM " + getTinfoDataClass() + " _x_ WHERE Container = ?").add(container.getId());
        SQLFragment modifiedSQL = new SearchService.LastIndexedClause(getTinfoDataClass(), modifiedSince, "_x_").toSQLFragment(null, null);
        if (!modifiedSQL.isEmpty())
            sql.append(" AND ").append(modifiedSQL);
        return ExpDataClassImpl.fromDataClasses(new SqlSelector(getSchema(), sql).getArrayList(DataClass.class));
    }

    private Collection<ExpSampleTypeImpl> getIndexableSampleTypes(@NotNull Container container, @Nullable Date modifiedSince)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM " + getTinfoSampleType() + " _st_ WHERE Container = ?").add(container.getId());
        SQLFragment modifiedSQL = new SearchService.LastIndexedClause(getTinfoSampleType(), modifiedSince, "_st_").toSQLFragment(null, null);
        if (!modifiedSQL.isEmpty())
            sql.append(" AND ").append(modifiedSQL);
        return ExpSampleTypeImpl.fromMaterialSources(new SqlSelector(getSchema(), sql).getArrayList(MaterialSource.class));
    }

    public void setDataLastIndexed(int rowId, long ms)
    {
        Date d = new Date(ms);
        SQLFragment sql = new SQLFragment("UPDATE " + getTinfoDataIndexed() + " SET LastIndexed = ? WHERE DataId = ?").appendEOS().
                append("INSERT INTO " + getTinfoDataIndexed() + " (DataId, LastIndexed) SELECT ?, ? WHERE NOT EXISTS (SELECT DataId FROM " +
                getTinfoDataIndexed() + " WHERE DataId = ?) AND EXISTS (SELECT RowId FROM " + getTinfoData() + " WHERE RowId = ?)");
        sql.add(d);
        sql.add(rowId);
        sql.add(rowId);
        sql.add(d);
        sql.add(rowId);
        sql.add(rowId);
        try
        {
            new SqlExecutor(getSchema()).execute(sql);
        }
        catch (DataIntegrityViolationException e)
        {
            // if we're not in a transaction just keep going...
            if (getSchema().getScope().isTransactionActive())
                throw e;
        }
    }

    public void setDataClassLastIndexed(int rowId, long ms)
    {
        setLastIndexed(getTinfoDataClass(), rowId, ms);
    }

    public void setMaterialLastIndexed(List<Pair<Integer,Long>> updates)
    {
        if (null == updates || updates.isEmpty())
            return;
        DbScope dbscope = getSchema().getScope();
        try (Connection c = dbscope.getConnection())
        {
            Parameter updateMaterialId = new Parameter("materialid", JdbcType.INTEGER);
            Parameter updateTS = new Parameter("ts", JdbcType.TIMESTAMP);
            SQLFragment updateSql = new SQLFragment("UPDATE " + getTinfoMaterialIndexed() + " SET LastIndexed = ? WHERE MaterialId = ?");
            updateSql.add(updateTS);
            updateSql.add(updateMaterialId);

            Parameter insertTS = new Parameter("ts", JdbcType.TIMESTAMP);
            Parameter insertRowId = new Parameter("rowid", JdbcType.INTEGER);
            Parameter insertMaterialId = new Parameter("materialid", JdbcType.INTEGER);
            SQLFragment insertSql = new SQLFragment("INSERT INTO " + getTinfoMaterialIndexed() + " (MaterialId, LastIndexed) " +
                    "SELECT RowId, ? FROM " + getTinfoMaterial() + " WHERE RowId = ? AND " +
                            " NOT EXISTS (SELECT MaterialId FROM " + getTinfoMaterialIndexed() + " WHERE MaterialId = ?)");
            insertSql.add(insertTS);
            insertSql.add(insertRowId);
            insertSql.add(insertMaterialId);

            try (ParameterMapStatement insertPM = new ParameterMapStatement(getSchema().getScope(), c, insertSql, null);
                 ParameterMapStatement updatePM = new ParameterMapStatement(getSchema().getScope(), c, updateSql, null))
            {
                ListUtils.partition(updates, 1000).forEach(sublist ->
                {
                    try
                    {
                        for (Pair<Integer, Long> p : sublist)
                        {
                            insertMaterialId.setValue(p.first);
                            insertTS.setValue(new Timestamp(p.second));
                            insertRowId.setValue(p.first);
                            insertPM.addBatch();

                            updateMaterialId.setValue(p.first);
                            updateTS.setValue(new Timestamp(p.second));
                            updatePM.addBatch();
                        }
                        insertPM.executeBatch();
                        updatePM.executeBatch();
                    }
                    catch (PessimisticLockingFailureException | DataIntegrityViolationException e)
                    {
                        // if we're not in a transaction just keep going...
                        if (dbscope.isTransactionActive())
                            throw e;
                    }
                });
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void setMaterialSourceLastIndexed(int rowId, long ms)
    {
        setLastIndexed(getTinfoSampleType(), rowId, ms);
    }

    private void setLastIndexed(TableInfo table, int rowId, long ms)
    {
        new SqlExecutor(getSchema()).execute("UPDATE " + table + " SET LastIndexed = ? WHERE RowId = ?",
                new Timestamp(ms), rowId);
    }

    public void indexDataClass(ExpDataClassImpl dataClass)
    {
        SearchService ss = SearchService.get();
        if (ss == null)
            return;

        SearchService.IndexTask task = ss.defaultTask();

        Runnable r = () -> {

            Domain d = dataClass.getDomain();
            if (d == null)
                return; // Domain may be null if the DataClass has been deleted

            TableInfo table = dataClass.getTinfo();
            if (table == null)
                return;

            indexDataClass(dataClass, task);
            indexDataClassData(dataClass, task);

        };

        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }

    private void indexDataClassData(ExpDataClassImpl dataClass, SearchService.IndexTask task)
    {
        TableInfo table = dataClass.getTinfo();
        // Index all ExpData that have never been indexed OR where either the ExpDataClass definition or ExpData itself has changed since last indexed
        SQLFragment sql = new SQLFragment()
            .append("SELECT * FROM ").append(getTinfoData(), "d")
            .append(" INNER JOIN ").append(table, "t")
            .append(" ON t.lsid = d.lsid")
            .append(" LEFT OUTER JOIN ").append(getTinfoDataIndexed(), "di")
            .append(" ON d.RowId = di.DataId")
            .append(" WHERE d.classId = ?").add(dataClass.getRowId())
            .append(" AND (di.lastIndexed IS NULL OR di.lastIndexed < ? OR (d.modified IS NOT NULL AND di.lastIndexed < d.modified))")
            .append(" ORDER BY d.RowId") // Issue 51263: order by RowId to reduce deadlock
                .add(dataClass.getModified());

        var scope = table.getSchema().getScope();
        scope.executeWithRetryReadOnly(tx ->
            new SqlSelector(scope, sql).forEachBatch(Data.class, 1000, batch ->
                    task.addRunnable(() -> batch.forEach(data ->
                        new ExpDataImpl(data).index(task, null)),
                    SearchService.PRIORITY.bulk)
        ));
    }

    private void indexDataClass(ExpDataClass expDataClass, SearchService.IndexTask task)
    {
        // Index the data class if it has never been indexed OR it has changed since it was last indexed
        SQLFragment sql = new SQLFragment("SELECT * FROM ")
            .append(getTinfoDataClass(), "dc")
            .append(" WHERE dc.LSID = ?").add(expDataClass.getLSID())
            .append(" AND (dc.lastIndexed IS NULL OR dc.lastIndexed < ?)")
            .add(expDataClass.getModified());

        DataClass dClass = new SqlSelector(getExpSchema().getScope(), sql).getObject(DataClass.class);
        if (dClass != null)
        {
            ExpDataClassImpl impl = new ExpDataClassImpl(dClass);
            impl.index(task);
        }
    }

    private @Nullable ExpExperimentImpl getExpExperiment(SimpleFilter filter)
    {
        Experiment experiment = new TableSelector(getTinfoExperiment(), filter, null).getObject(Experiment.class);
        return experiment == null ? null : new ExpExperimentImpl(experiment);
    }

    @Override
    public @Nullable ExpExperimentImpl getExpExperiment(int rowId)
    {
        return getExpExperiment(new SimpleFilter(FieldKey.fromParts(ExpExperimentTable.Column.RowId.name()), rowId));
    }

    @Override
    public ExpExperiment getExpExperiment(String lsid)
    {
        return getExpExperiment(new SimpleFilter(FieldKey.fromParts(ExpExperimentTable.Column.LSID.name()), lsid));
    }

    @Override
    public @NotNull ExpExperimentImpl createExpExperiment(Container container, String name)
    {
        Experiment exp = new Experiment();
        exp.setContainer(container);
        exp.setName(name);
        exp.setLSID(generateLSID(container, ExpExperiment.class, name));
        return new ExpExperimentImpl(exp);
    }

    @Override
    public List<? extends ExpExperiment> getExpExperiments(Collection<Integer> rowIds)
    {
        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts(ExpExperimentTable.Column.RowId.name()), rowIds);
        TableSelector selector = new TableSelector(getTinfoExperiment(), filter, null);

        final List<ExpExperimentImpl> experiments = new ArrayList<>(rowIds.size());
        selector.forEach(Experiment.class, exp -> experiments.add(new ExpExperimentImpl(exp)));

        return experiments;
    }

    @Override
    public ExpProtocolImpl getExpProtocol(int rowId)
    {
        return PROTOCOL_ROW_ID_CACHE.get(rowId);
    }

    @Override
    public ExpProtocolImpl getExpProtocol(String lsid)
    {
        return PROTOCOL_LSID_CACHE.get(lsid);
    }

    private @Nullable ExpProtocolImpl getExpProtocol(SimpleFilter filter)
    {
        Protocol protocol = new TableSelector(getTinfoProtocol(), filter, null).getObject(Protocol.class);
        return protocol == null ? null : new ExpProtocolImpl(protocol);
    }

    @Override
    public @Nullable ExpProtocolImpl getExpProtocol(Container container, String name)
    {
        return getExpProtocol(container, name, null);
    }

    private @Nullable ExpProtocolImpl getExpProtocol(Container container, String name, @Nullable ContainerFilter cf)
    {
        if (cf == null && container == null)
            throw new IllegalArgumentException("Either a container or a container filter must be supplied to retrieve an exp protocol by name.");

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Name"), name);
        if (cf != null)
            filter.addCondition(cf.createFilterClause(getExpSchema(), FieldKey.fromParts("Container")));
        else
            filter.addCondition(FieldKey.fromParts("Container"), container.getId());

        // When a container filter is applied we cannot guarantee a single protocol by name.
        // For backwards compatibility, and instead of throwing, get a sorted list of protocols and return the first one.
        List<ExpProtocolImpl> protocols = getExpProtocols(filter, new Sort("RowId"), 1);
        if (protocols.isEmpty())
            return null;

        return protocols.get(0);
    }

    private void uncacheProtocol(Protocol p)
    {
        PROTOCOL_ROW_ID_CACHE.remove(p.getRowId());
        PROTOCOL_LSID_CACHE.remove(p.getLSID());
    }

    @Override
    public ExpProtocolImpl createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name)
    {
        return createExpProtocol(container, type, name, generateLSID(container, ExpProtocol.class, name));
    }

    @Override
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
        protocol.setOutputMaterialType(ExpMaterial.DEFAULT_CPAS_TYPE);
        // the default for runs
        if (ExperimentRun.equals(type))
            protocol.setStatus(ExpProtocol.Status.Active);
        return new ExpProtocolImpl(protocol);
    }

    @Override
    public ExpDataProtocolInputImpl createDataProtocolInput(
            @NotNull String name, @NotNull ExpProtocol protocol, boolean input,
            @Nullable ExpDataClass dataClass,
            @Nullable ExpProtocolInputCriteria criteria,
            int minOccurs, @Nullable Integer maxOccurs)
    {
        Objects.requireNonNull(protocol, "Protocol required");
        Container c = Objects.requireNonNull(protocol.getContainer(), "protocol Container required");

        ExpDataProtocolInputImpl impl = createDataProtocolInput(c, name, protocol.getRowId(), input, dataClass, criteria, minOccurs, maxOccurs);
        impl.setProtocol(protocol);
        return impl;
    }

    // Used when constructing a Protocol from XarReader where the protocol id is not set known
    public ExpDataProtocolInputImpl createDataProtocolInput(
            @NotNull Container c,
            @NotNull String name, int protocolId, boolean input,
            @Nullable ExpDataClass dataClass,
            @Nullable ExpProtocolInputCriteria criteria,
            int minOccurs, @Nullable Integer maxOccurs)
    {
        DataProtocolInput obj = new DataProtocolInput();
        populateProtocolInput(obj, c, name, protocolId, input, criteria, minOccurs, maxOccurs);
        if (dataClass != null)
            obj.setDataClassId(dataClass.getRowId());

        return new ExpDataProtocolInputImpl(obj);
    }

    @Override
    public ExpMaterialProtocolInputImpl createMaterialProtocolInput(
            @NotNull String name, @NotNull ExpProtocol protocol, boolean input,
            @Nullable ExpSampleType sampleType,
            @Nullable ExpProtocolInputCriteria criteria,
            int minOccurs, @Nullable Integer maxOccurs)
    {
        Objects.requireNonNull(protocol, "Protocol required");
        Container c = Objects.requireNonNull(protocol.getContainer(), "protocol Container required");

        ExpMaterialProtocolInputImpl impl = createMaterialProtocolInput(c, name, protocol.getRowId(), input, sampleType, criteria, minOccurs, maxOccurs);
        impl.setProtocol(protocol);
        return impl;
    }

    // Used when constructing a Protocol from XarReader where the protocol id is not set known
    public ExpMaterialProtocolInputImpl createMaterialProtocolInput(
        @NotNull Container c,
        @NotNull String name,
        int protocolId,
        boolean input,
        @Nullable ExpSampleType sampleType,
        @Nullable ExpProtocolInputCriteria criteria,
        int minOccurs,
        @Nullable Integer maxOccurs
    )
    {
        MaterialProtocolInput obj = new MaterialProtocolInput();
        populateProtocolInput(obj, c, name, protocolId, input, criteria, minOccurs, maxOccurs);
        if (sampleType != null)
            obj.setMaterialSourceId(sampleType.getRowId());

        return new ExpMaterialProtocolInputImpl(obj);
    }

    private void populateProtocolInput(
        @NotNull AbstractProtocolInput obj,
        @NotNull Container c,
        @NotNull String name,
        int protocolId,
        boolean input,
        @Nullable ExpProtocolInputCriteria criteria,
        int minOccurs,
        @Nullable Integer maxOccurs
    )
    {
        Objects.requireNonNull(name, "Name required");


        String objectType = obj.getObjectType();
        if (!objectType.equals(ExpData.DEFAULT_CPAS_TYPE) && !objectType.equals(ExpMaterial.DEFAULT_CPAS_TYPE))
            throw new IllegalArgumentException("Only 'Data' or 'Material' input types are currently supported");

        // CONSIDER: What sort of validation do we want to do on the protocol inputs?
        // CONSIDER: e.e. assert that if the protocol is of type=ExperimentRunOutput that this isn't an output

        obj.setName(name);
        obj.setObjectType(objectType);
        obj.setLSID(generateGuidLSID(c, ExpProtocolInput.class));
        obj.setProtocolId(protocolId);
        obj.setInput(input);
        if (criteria != null)
        {
            obj.setCriteriaName(criteria.getTypeName());
            obj.setCriteriaConfig(criteria.serializeConfig());
        }
        obj.setMinOccurs(minOccurs);
        obj.setMaxOccurs(maxOccurs);
    }

    @Override
    public ExpRunTable createRunTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpRunTableImpl(name, schema, cf);
    }

    @Override
    public ExpRunGroupMapTable createRunGroupMapTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpRunGroupMapTableImpl(name, schema, cf);
    }

    @Override
    public ExpDataTable createDataTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpDataTableImpl(name, schema, cf);
    }

    @Override
    public ExpDataInputTable createDataInputTable(String name, ExpSchema expSchema, ContainerFilter cf)
    {
        return new ExpDataInputTableImpl(name, expSchema, cf);
    }

    @Override
    public ExpDataProtocolInputTableImpl createDataProtocolInputTable(String name, ExpSchema expSchema, ContainerFilter cf)
    {
        return new ExpDataProtocolInputTableImpl(name, expSchema, cf);
    }

    @Override
    public ExpSampleTypeTable createSampleTypeTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpSampleTypeTableImpl(name, schema, cf);
    }

    @Override
    public ExpDataClassTable createDataClassTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpDataClassTableImpl(name, schema, cf);
    }

    @Override
    public ExpProtocolTableImpl createProtocolTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpProtocolTableImpl(name, schema, cf);
    }

    @Override
    public ExpExperimentTableImpl createExperimentTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpExperimentTableImpl(name, schema, cf);
    }

    @Override
    public ExpMaterialTable createMaterialTable(UserSchema schema, ContainerFilter cf, @Nullable ExpSampleType sampleType)
    {
        return new ExpMaterialTableImpl(schema, cf, sampleType);
    }

    @Override
    public ExpDataClassDataTable createDataClassDataTable(String name, UserSchema schema, ContainerFilter cf, @NotNull ExpDataClass dataClass)
    {
        return new ExpDataClassDataTableImpl(name, schema, cf, (ExpDataClassImpl) dataClass);
    }

    @Override
    public ExpMaterialInputTable createMaterialInputTable(String name, ExpSchema schema, ContainerFilter cf)
    {
        return new ExpMaterialInputTableImpl(name, schema, cf);
    }

    @Override
    public ExpMaterialProtocolInputTableImpl createMaterialProtocolInputTable(String name, ExpSchema expSchema, ContainerFilter cf)
    {
        return new ExpMaterialProtocolInputTableImpl(name, expSchema, cf);
    }

    @Override
    public ExpProtocolApplicationTableImpl createProtocolApplicationTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpProtocolApplicationTableImpl(name, schema, cf);
    }

    @Override
    public ExpQCFlagTableImpl createQCFlagsTable(String name, UserSchema schema, ContainerFilter cf)
    {
        return new ExpQCFlagTableImpl(name, schema, cf);
    }

    @Override
    public ExpDataTable createFilesTable(String name, UserSchema schema)
    {
        return new ExpFilesTableImpl(name, schema);
    }

    @Override
    public SampleStatusTable createSampleStatusTable(ExpSchema expSchema, ContainerFilter containerFilter)
    {
        return new SampleStatusTable(expSchema, containerFilter);
    }

    public static String getNamespacePrefix(Class<? extends ExpObject> clazz)
    {
        if (clazz == ExpData.class)
            return ExpData.DEFAULT_CPAS_TYPE;
        if (clazz == ExpMaterial.class)
            return ExpMaterial.DEFAULT_CPAS_TYPE;
        if (clazz == ExpProtocol.class)
            return ExpProtocol.DEFAULT_CPAS_TYPE;
        if (clazz == ExpRun.class)
            return ExpRunImpl.NAMESPACE_PREFIX;
        if (clazz == ExpExperiment.class)
            return ExpExperiment.DEFAULT_CPAS_TYPE;
        if (clazz == ExpSampleType.class)
            return "SampleSet";
        if (clazz == ExpDataClass.class)
            return ExpDataClassImpl.NAMESPACE_PREFIX;
        if (clazz == ExpProtocolApplication.class)
            return ExpProtocolApplication.DEFAULT_CPAS_TYPE;
        if (clazz == ExpProtocolInput.class)
            return AbstractProtocolInput.NAMESPACE;
        throw new IllegalArgumentException("Invalid class " + clazz.getName());
    }

    private Pair<String, String> generateLSIDWithDBSeq(Container container, String lsidPrefix)
    {
        String dbSeqStr = String.valueOf(getLsidPrefixDbSeq(container, lsidPrefix, 1).next());
        String lsid = generateLSID(container, lsidPrefix, dbSeqStr);
        return new Pair<>(lsid, dbSeqStr);
    }

    public static DbSequence getLsidPrefixDbSeq(Container container, String lsidPrefix, int batchSize)
    {
        Container projectContainer = container; // use DBSeq at project level to avoid duplicate lsid for types in child folder
        if (!container.isProject() && container.getProject() != null)
            projectContainer = container.getProject();

        return DbSequenceManager.getPreallocatingSequence(projectContainer, LSID_COUNTER_DB_SEQUENCE_PREFIX + lsidPrefix, 0, batchSize);
    }

    private String generateGuidLSID(Container container, String lsidPrefix)
    {
        return generateLSID(container, lsidPrefix, GUID.makeGUID());
    }

    public String generateLSID(Container container, String lsidPrefix, String objectName)
    {
        return new Lsid(lsidPrefix, "Folder-" + container.getRowId(), objectName).toString();
    }

    @Override
    public String generateGuidLSID(Container container, Class<? extends ExpObject> clazz)
    {
        return generateGuidLSID(container, getNamespacePrefix(clazz));
    }

    @Override
    public Pair<String, String> generateLSIDWithDBSeq(@NotNull Container container, Class<? extends ExpObject> clazz)
    {
        return generateLSIDWithDBSeq(container, getNamespacePrefix(clazz));
    }

    @Override
    public String generateGuidLSID(Container container, DataType type)
    {
        return generateGuidLSID(container, type.getNamespacePrefix());
    }

    @Override
    public Pair<String, String> generateLSIDWithDBSeq(@NotNull Container container, DataType type)
    {
        return generateLSIDWithDBSeq(container, type.getNamespacePrefix());
    }

    @Override
    public String generateLSID(Container container, Class<? extends ExpObject> clazz, @NotNull String name)
    {
        return generateLSID(container, getNamespacePrefix(clazz), name);
    }

    @Override
    public String generateLSID(@NotNull Container container, @NotNull DataType type, @NotNull String name)
    {
        return generateLSID(container, type.getNamespacePrefix(), name);
    }

    @Nullable
    @Override
    public ExpObject findObjectFromLSID(String lsid)
    {
        Identifiable id = LsidManager.get().getObject(lsid);
        if (id == null)
            return null;

        return id.getExpObject();
    }

    @Override
    public List<ExpDataClassImpl> getDataClasses(@NotNull Container container, User user, boolean includeProjectAndShared)
    {
        SortedSet<DataClass> classes = new TreeSet<>();
        List<String> containerIds = createContainerList(container, user, includeProjectAndShared);
        for (String containerId : containerIds)
        {
            SortedSet<DataClass> dataClasses = getDataClassCache().get(containerId);
            classes.addAll(dataClasses);
        }

        // Do the sort on the Java side to make sure it's always case-insensitive, even on Postgres
        return classes.stream().map(ExpDataClassImpl::new).sorted().toList();
    }

    @Override
    public ExpDataClassImpl getDataClass(@NotNull Container c, @NotNull String dataClassName)
    {
        return getDataClass(c, null, false, dataClassName);
    }

    public ExpDataClassImpl getDataClassByObjectId(Integer objectId)
    {
        OntologyObject obj = OntologyManager.getOntologyObject(objectId);
        if (obj == null)
            return null;

        return getDataClass(obj.getObjectURI());
    }

    @Override
    public ExpProtocol getEffectiveProtocol(Container definitionContainer, User user, String schemaName, Date effectiveDate, ContainerFilter cf)
    {
        Integer legacyObjectId = getObjectIdWithLegacyName(schemaName, ExperimentServiceImpl.getNamespacePrefix(ExpProtocol.class), effectiveDate, definitionContainer, cf);
        if (legacyObjectId != null)
            return getExpProtocol(legacyObjectId);

        ExpProtocol protocol = getExpProtocol(definitionContainer, schemaName, cf);

        if (protocol != null && protocol.getCreated().compareTo(effectiveDate) <= 0)
            return protocol;

        return null;
    }

    @Override
    public @Nullable ExpDataClass getEffectiveDataClass(
        @NotNull Container definitionContainer,
        @NotNull User user,
        @NotNull String dataClassName,
        @NotNull Date effectiveDate,
        @Nullable ContainerFilter cf
    )
    {
        Integer legacyObjectId = getObjectIdWithLegacyName(dataClassName, ExperimentServiceImpl.getNamespacePrefix(ExpDataClass.class), effectiveDate, definitionContainer, cf);
        if (legacyObjectId != null)
            return getDataClassByObjectId(legacyObjectId);

        boolean includeProjectAndShared = cf != null && cf.getType() != ContainerFilter.Type.Current;
        ExpDataClassImpl dataClass = getDataClass(definitionContainer, user, includeProjectAndShared, dataClassName);
        if (dataClass != null && dataClass.getCreated().compareTo(effectiveDate) <= 0)
            return dataClass;

        return null;
    }

    @Override
    public ExpDataClassImpl getDataClass(@NotNull Container c, @NotNull User user, @NotNull String dataClassName)
    {
        return getDataClass(c, user, true, dataClassName);
    }

    private ExpDataClassImpl getDataClass(@NotNull Container c, @Nullable User user, boolean includeProjectAndShared, String dataClassName)
    {
        return getDataClass(c, user, includeProjectAndShared, (dataClass -> dataClass.getName().equalsIgnoreCase(dataClassName)));
    }

    @Override
    public ExpDataClassImpl getDataClass(@NotNull Container c, int rowId)
    {
        return getDataClass(c, null, rowId, false);
    }

    @Override
    public ExpDataClassImpl getDataClass(@NotNull Container c, @NotNull User user, int rowId)
    {
        return getDataClass(c, user, rowId, true);
    }

    private ExpDataClassImpl getDataClass(@NotNull Container c, @Nullable User user, int rowId, boolean includeProjectAndShared)
    {
        return getDataClass(c, user, includeProjectAndShared, (dataClass -> dataClass.getRowId() == rowId));
    }

    private ExpDataClassImpl getDataClass(@NotNull Container c, @Nullable User user, boolean includeProjectAndShared, Predicate<DataClass> predicate)
    {
        List<String> containerIds = createContainerList(c, user, includeProjectAndShared);
        for (String containerId : containerIds)
        {
            Collection<DataClass> dataClasses = getDataClassCache().get(containerId);
            for (DataClass dataClass : dataClasses)
            {
                if (predicate.test(dataClass))
                    return new ExpDataClassImpl(dataClass);
            }
        }

        return null;
    }

    // Prefer using one of the getDataClass methods that accept a Container and User for permission checking.
    @Override
    public ExpDataClassImpl getDataClass(int rowId)
    {
        DataClass dataClass = new TableSelector(getTinfoDataClass()).getObject(rowId, DataClass.class);
        if (dataClass == null)
            return null;

        return new ExpDataClassImpl(dataClass);
    }

    // Prefer using one of the getDataClass methods that accept a Container and User for permission checking.
    @Override
    public @Nullable ExpDataClassImpl getDataClass(@NotNull String lsid)
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

        TableInfo table = ((ExpDataClassImpl) dataClass).getTinfo();
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


    public List<ExpDataImpl> getExpDatasByObjectId(ContainerFilter containerFilter, Collection<Integer> objectIds)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("ObjectId"), objectIds);
        filter.addClause(containerFilter.createFilterClause(getSchema(), FieldKey.fromParts("Container")));
        return getExpDatas(filter);
    }

    @Override
    @Nullable
    public ExpDataImpl getExpData(ExpDataClass dataClass, String name)
    {
        Domain d = dataClass.getDomain();
        if (d == null)
            throw new IllegalStateException("No domain for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        TableInfo table = ((ExpDataClassImpl) dataClass).getTinfo();
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
    @Nullable
    public ExpDataImpl getExpData(ExpDataClass dataClass, int rowId)
    {
        Domain d = dataClass.getDomain();
        if (d == null)
            throw new IllegalStateException("No domain for DataClass '" + dataClass.getName() + "' in container '" + dataClass.getContainer().getPath() + "'");

        TableInfo table = ((ExpDataClassImpl) dataClass).getTinfo();
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

    @Override
    @Nullable
    public ExpData getEffectiveData(@NotNull ExpDataClass dataClass, String name, @NotNull Date effectiveDate, @NotNull Container container, @Nullable ContainerFilter cf)
    {
        Integer legacyObjectId = getObjectIdWithLegacyName(name, ExperimentServiceImpl.getNamespacePrefix(ExpData.class), effectiveDate, container, cf);
        if (legacyObjectId != null)
            return getExpDataByObjectId(legacyObjectId);

        ExpDataImpl data = getExpData(dataClass, name);
        if (data != null && data.getCreated().compareTo(effectiveDate) <= 0)
            return data;

        return null;
    }

    @NotNull
    private ContainerFilter.Type getContainerFilterTypeForFind(Container container)
    {
        ContainerFilter.Type type = QueryService.get().getContainerFilterTypeForLookups(container);
        return type == null ? ContainerFilter.Type.CurrentPlusProjectAndShared : type;
    }

    @Override
    @Nullable
    public ExpData findExpData(
        Container c,
        User user,
        @NotNull ExpDataClass dataClass,
        @NotNull String dataClassName,
        String dataName,
        RemapCache cache,
        Map<Integer, ExpData> dataCache
    ) throws ValidationException
    {
        StringBuilder errors = new StringBuilder();
        // Issue 44568, Issue 40302: Unable to use samples or data class with integer like names as material or data input
        // Attempt to resolve by name first.
        try
        {
            Integer rowId = cache.remap(ExpSchema.SCHEMA_EXP_DATA, dataClassName, user, c, getContainerFilterTypeForFind(c), dataName);
            if (rowId != null)
                return dataCache.computeIfAbsent(rowId, (x) -> getExpData(dataClass, rowId));
        }
        catch (ConversionException e2)
        {
            errors.append("Failed to resolve '").append(dataName).append("' into a data. ").append(e2.getMessage());
            errors.append(" Use 'DataInputs/<DataClassName>' column header to resolve parents from a specific DataClass.");
            if (e2.getMessage() != null)
                errors.append(" ").append(e2.getMessage());
        }

        try
        {
            Integer rowId = ConvertHelper.convert(dataName, Integer.class);

            // now attempt to resolve by rowId
            return dataCache.computeIfAbsent(rowId, (x) -> getExpData(dataClass, rowId));
        }
        catch (ConversionException e1)
        {
            // ignore
        }
        if (!errors.isEmpty())
            throw new ValidationException(errors.toString());
        return null;
    }

    @Override
    public @Nullable ExpMaterial findExpMaterial(
        Container container,
        User user,
        Object sampleIdentifier,
        @Nullable ExpSampleType sampleType,
        @NotNull RemapCache cache,
        @NotNull Map<Integer, ExpMaterial> materialCache
    ) throws ValidationException
    {
        if (sampleIdentifier == null)
            return null;

        if (sampleIdentifier instanceof Integer rowId)
            return materialCache.computeIfAbsent(rowId, id -> getExpMaterial(container, user, id, sampleType));

        if (sampleIdentifier instanceof ExpMaterial m)
        {
            materialCache.put(m.getRowId(), m);
            return m;
        }

        if (!(sampleIdentifier instanceof String sampleName))
            throw new ValidationException(String.format("Failed to resolve a %s into a sample.", sampleIdentifier.getClass().getName()));

        StringBuilder errors = new StringBuilder();
        String sampleTypeName = sampleType == null ? null : sampleType.getName();

        // Issue 44568, Issue 40302: Unable to use samples or data class with integer like names as material or data input
        // First attempt to resolve by name.
        try
        {
            // TODO, rowId is not found for samples newly created in the same import.
            // This is causing name patterns containing lineage lookup to fail to generate the correct names if the child samples and their parents are created from the same import file
            Integer rowId = (sampleTypeName == null) ?
                    cache.remap(ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Materials.name(), user, container, getContainerFilterTypeForFind(container), sampleName) :
                    cache.remap(SamplesSchema.SCHEMA_SAMPLES, sampleTypeName, user, container, getContainerFilterTypeForFind(container), sampleName);

            if (rowId != null)
                return materialCache.computeIfAbsent(rowId, id -> getExpMaterial(container, user, id, sampleType));
        }
        catch (ConversionException e2)
        {
            errors.append("Failed to resolve '").append(sampleName).append("' into a sample.");
            if (sampleTypeName == null)
            {
                errors.append(" Use 'MaterialInputs/<SampleTypeName>' column header to resolve parent samples from a specific SampleType.");
            }
            if (e2.getMessage() != null)
                errors.append(" ").append(e2.getMessage());
        }

        try
        {
            // next attempt to resolve by rowId
            Integer rowId = ConvertHelper.convert(sampleName, Integer.class);

            if (rowId != null)
                return materialCache.computeIfAbsent(rowId, id -> getExpMaterial(container, user, id, sampleType));
        }
        catch (ConversionException e1)
        {
            // ignore
        }
        if (!errors.isEmpty())
            throw new ValidationException(errors.toString());

        return null;
    }

    @Override
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
            // We're not actually mutating in this case, but we would be if some action hadn't already cached this run group. Flag it as if we're mutating.
            SpringActionController.executingMutatingSql("Creating an experiment run group");

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

    @Override
    public DbScope.Transaction ensureTransaction()
    {
        return getExpSchema().getScope().ensureTransaction();
    }

    @Override
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

    /**
     * export to temp directory
     */
    @Override
    public File exportXarForRuns(
        User user,
        Set<Integer> runIds,
        Integer expRowId,
        XarExportOptions options
    ) throws NotFoundException, IOException, ExperimentException
    {
        if (runIds.isEmpty())
        {
            throw new NotFoundException();
        }

        try
        {
            List<ExpRun> runs = new ArrayList<>();
            for (int id : runIds)
            {
                ExpRun run = getExpRun(id);
                if (run == null || !run.getContainer().hasPermission(user, ReadPermission.class))
                {
                    throw new NotFoundException("Could not find run " + id);
                }
                runs.add(run);
            }

            XarExportSelection selection = new XarExportSelection();
            if (expRowId != null)
            {
                ExpExperiment experiment = getExpExperiment(expRowId);
                if (experiment == null || !experiment.getContainer().hasPermission(user, ReadPermission.class))
                {
                    throw new NotFoundException("Run group " + expRowId);
                }
                selection.addExperimentIds(experiment.getRowId());
            }
            selection.addRuns(runs);
            // NOTE: selection distinguishes between null and empty (careful)
            // TODO have ArchiveURLRewriter() differentiate between input and output roles
            // TODO using Set<roles> is adequate for now (as long as the caller knows all the roles of interest)
            if (options.isFilterDataRoles())
                selection.addRoles(options.getDataRoles());
            XarExporter exporter = new XarExporter(
                    LSIDRelativizer.valueOf(options.getLsidRelativizer()),
                    selection,
                    user,
                    options.getXarXmlFileName(),
                    options.getLog(),
                    null
            );
            if (options.getExportFile().isDirectory())
            {
                exporter.writeAsDirectory(options.getExportFile());
            }
            else
            {
                try (FileOutputStream fOut = new FileOutputStream(options.getExportFile().getPath()))
                {
                    exporter.writeAsArchive(fOut);
                }
            }
            return options.getExportFile();
        }
        catch (NumberFormatException e)
        {
            throw new NotFoundException(runIds.toString());
        }
    }

    @Override
    public DbSchema getSchema()
    {
        return getExpSchema();
    }

    @Override
    public List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException
    {
        XarImportOptions options = new XarImportOptions().setReplaceExistingRuns(reloadExistingRuns);
        return importXar(source, pipelineJob, options);
    }

    @Override
    public List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, XarImportOptions options) throws ExperimentException
    {
        if (!source.getXarContext().getSubstitutions().containsKey(XAR_JOB_ID_NAME) && pipelineJob != null)
        {
            source.getXarContext().addSubstitution(XAR_JOB_ID_NAME, pipelineJob.getJobGUID());
        }
        XarReader reader = new XarReader(source, pipelineJob);
        reader.setReloadExistingRuns(options.isReplaceExistingRuns());
        reader.setUseOriginalFileUrl(options.isUseOriginalDataFileUrl());
        reader.setStrictValidateExistingSampleType(options.isStrictValidateExistingSampleType());
        reader.parseAndLoad();
        return reader.getExperimentRuns();
    }

    @Override
    public ExpRun importRun(PipelineJob job, XarSource source) throws PipelineJobException, ValidationException
    {
        return ExpGeneratorHelper.insertRun(job, source, null);
    }

    @Override
    public Set<String> getDataInputRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType... types)
    {
        return getInputRoles(container, filter, getTinfoDataInput(), types);
    }

    @Override
    public Set<String> getMaterialInputRoles(Container container, ExpProtocol.ApplicationType... types)
    {
        return getInputRoles(container, ContainerFilter.Type.Current.create(container, null), getTinfoMaterialInput(), types);
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
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("Container")));
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
        if (objectId == null || objectId.isEmpty())
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
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("dataId"), dataId);
        filter.addCondition(FieldKey.fromParts("targetApplicationId"), targetProtocolApplicationId);
        DataInput di = new TableSelector(getTinfoDataInput(), filter, null).getObject(DataInput.class);
        if (di == null)
            return null;

        return new ExpDataRunInputImpl(di);
    }

    private @Nullable ExpDataProtocolInputImpl getDataProtocolInput(@NotNull SimpleFilter filter)
    {
        filter.addCondition(FieldKey.fromParts("objectType"), ExpData.DEFAULT_CPAS_TYPE);
        DataProtocolInput mpi = new TableSelector(getTinfoProtocolInput(), filter, null).getObject(DataProtocolInput.class);

        return mpi == null ? null : new ExpDataProtocolInputImpl(mpi);
    }

    @Override
    @Nullable
    public ExpDataProtocolInputImpl getDataProtocolInput(int rowId)
    {
        return getDataProtocolInput(new SimpleFilter(FieldKey.fromParts("rowId"), rowId));
    }

    @Override
    @Nullable
    public ExpDataProtocolInputImpl getDataProtocolInput(Lsid lsid)
    {
        if (!DataProtocolInput.NAMESPACE.equals(lsid.getNamespace()))
            return null;

        return getDataProtocolInput(new SimpleFilter(FieldKey.fromParts("lsid"), lsid));
    }

    @Override
    public @NotNull List<? extends ExpDataProtocolInput> getDataProtocolInputs(
        int protocolId, 
        boolean input, 
        @Nullable String name,
        @Nullable Integer dataClassId
    )
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("objectType"), ExpData.DEFAULT_CPAS_TYPE);
        filter.addCondition(FieldKey.fromParts("protocolId"), protocolId);
        filter.addCondition(FieldKey.fromParts("input"), input);
        if (name != null)
            filter.addCondition(FieldKey.fromParts("name"), name);
        if (dataClassId != null)
            filter.addCondition(FieldKey.fromParts("dataClassId"), dataClassId);

        return new TableSelector(getTinfoProtocolInput(), filter, null)
                .getArrayList(DataProtocolInput.class)
                .stream()
                .map(ExpDataProtocolInputImpl::new)
                .toList();
    }

    @Override
    @Nullable
    public ExpMaterialRunInputImpl getMaterialInput(Lsid lsid)
    {
        String namespace = lsid.getNamespace();
        if (!MaterialInput.NAMESPACE.equals(namespace))
            return null;

        String objectId = lsid.getObjectId();
        if (objectId == null || objectId.isEmpty())
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
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("materialId"), materialId);
        filter.addCondition(FieldKey.fromParts("targetApplicationId"), targetProtocolApplicationId);

        MaterialInput mi = new TableSelector(getTinfoMaterialInput(), filter, null).getObject(MaterialInput.class);
        return mi == null ? null : new ExpMaterialRunInputImpl(mi);
    }

    private ExpProtocolInputImpl protocolInputObjectType(Map<String, Object> row)
    {
        String objectType = (String)row.get("ObjectType");
        if (ExpData.DEFAULT_CPAS_TYPE.equals(objectType))
        {
            DataProtocolInput obj = ObjectFactory.Registry.getFactory(DataProtocolInput.class).fromMap(row);
            return new ExpDataProtocolInputImpl(obj);
        }
        else if (ExpMaterial.DEFAULT_CPAS_TYPE.equals(objectType))
        {
            MaterialProtocolInput obj = ObjectFactory.Registry.getFactory(MaterialProtocolInput.class).fromMap(row);
            return new ExpMaterialProtocolInputImpl(obj);
        }
        else
            throw new IllegalStateException("objectType not supported: " + objectType);
    }

    public List<? extends ExpProtocolInputImpl> getProtocolInputs(int protocolId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("protocolId"), protocolId);
        Collection<Map<String, Object>> rows = new TableSelector(getTinfoProtocolInput(), filter, new Sort("rowId")).getMapCollection();
        return rows.stream().map(this::protocolInputObjectType).toList();
    }

    public ExpProtocolInputImpl getProtocolInput(int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        Map<String, Object> row = new TableSelector(getTinfoProtocolInput(), filter, null).getMap();
        return protocolInputObjectType(row);
    }

    @Override
    @Nullable
    public ExpProtocolInputImpl getProtocolInput(Lsid lsid)
    {
        if (!AbstractProtocolInput.NAMESPACE.equals(lsid.getNamespace()))
            return null;

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        Map<String, Object> row = new TableSelector(getTinfoProtocolInput(), filter, null).getMap();
        return protocolInputObjectType(row);
    }

    private @Nullable ExpMaterialProtocolInputImpl getMaterialProtocolInput(@NotNull SimpleFilter filter)
    {
        filter.addCondition(FieldKey.fromParts("ObjectType"), ExpMaterial.DEFAULT_CPAS_TYPE);
        MaterialProtocolInput mpi = new TableSelector(getTinfoProtocolInput(), filter, null).getObject(MaterialProtocolInput.class);
        return mpi == null ? null : new ExpMaterialProtocolInputImpl(mpi);
    }

    @Override
    @Nullable
    public ExpMaterialProtocolInputImpl getMaterialProtocolInput(int rowId)
    {
        return getMaterialProtocolInput(new SimpleFilter(FieldKey.fromParts("RowId"), rowId));
    }

    @Override
    @Nullable
    public ExpMaterialProtocolInputImpl getMaterialProtocolInput(Lsid lsid)
    {
        if (!AbstractProtocolInput.NAMESPACE.equals(lsid.getNamespace()))
            return null;

        return getMaterialProtocolInput(new SimpleFilter(FieldKey.fromParts("lsid"), lsid));
    }

    @Override
    @Nullable
    public List<? extends ExpMaterialProtocolInput> getMaterialProtocolInputs(int protocolId, boolean input, @Nullable String name, @Nullable Integer materialSourceId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("objectType"), ExpMaterial.DEFAULT_CPAS_TYPE);
        filter.addCondition(FieldKey.fromParts("protocolId"), protocolId);
        filter.addCondition(FieldKey.fromParts("input"), input);
        if (name != null)
            filter.addCondition(FieldKey.fromParts("name"), name);
        if (materialSourceId != null)
            filter.addCondition(FieldKey.fromParts("materialSourceId"), materialSourceId);

        return new TableSelector(getTinfoProtocolInput(), filter, null)
                .getArrayList(MaterialProtocolInput.class)
                .stream()
                .map(ExpMaterialProtocolInputImpl::new)
                .toList();
    }

    @Override
    public Pair<Set<ExpData>, Set<ExpMaterial>> getParents(Container c, User user, ExpRunItem start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);

        ExpLineage lineage = getLineage(c, user, start, options);
        return Pair.of(lineage.getDatas(), lineage.getMaterials());
    }

    @Override
    public Pair<Set<ExpData>, Set<ExpMaterial>> getChildren(Container c, User user, ExpRunItem start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(false);

        ExpLineage lineage = getLineage(c, user, start, options);
        return Pair.of(lineage.getDatas(), lineage.getMaterials());
    }

    @Override
    public Set<ExpMaterial> getRelatedChildSamples(Container c, User user, ExpData start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(false);

        ExpLineage lineage = getLineage(c, user, start, options);
        return lineage.findRelatedChildSamples(start);
    }

    @Override
    @NotNull
    public Set<ExpData> getParentDatas(Container c, User user, ExpMaterial start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);
        options.setDepth(2); // 2 because of the ExpRun that will always be in between

        ExpLineage lineage = getLineage(c, user, start, options);
        return lineage.findNearestParentDatas(start);
    }

    @Override
    @NotNull
    public Set<ExpMaterial> getParentMaterials(Container c, User user, ExpMaterial start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);
        options.setDepth(2); // 2 because of the ExpRun that will always be in between.

        ExpLineage lineage = getLineage(c, user, start, options);
        return lineage.findNearestParentMaterials(start);
    }

    public Map<String, Pair<Set<ExpMaterial>, Set<ExpData>>> getParentMaterialAndDataMap(Container c, User user, Set<Identifiable> seeds)
    {
        Map<String, Pair<Set<ExpMaterial>, Set<ExpData>>> results = new HashMap<>();
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);
        options.setDepth(2); // 2 because of the ExpRun that will always be in between.

        ExpLineage lineage = getLineage(c, user, seeds, options);

        for (Identifiable seed : seeds)
        {
            Set<ExpMaterial> materials = new HashSet<>();
            Set<ExpData> datas = new HashSet<>();

            for (ExpRunItem item : lineage.findNearestParentMaterialsAndDatas(seed))
            {
                if (item instanceof ExpMaterial mItem)
                    materials.add(mItem);
                else if (item instanceof ExpData dItem)
                    datas.add(dItem);
            }

            if (!materials.isEmpty() || !datas.isEmpty())
                results.put(seed.getLSID(), new Pair<>(materials, datas));
        }

        return results;
    }

    @Override
    @NotNull
    public Set<ExpData> getNearestParentDatas(Container c, User user, ExpMaterial start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);

        ExpLineage lineage = getLineage(c, user, start, options);
        return lineage.findNearestParentDatas(start);
    }

    @Override
    @NotNull
    public Set<ExpMaterial> getNearestParentMaterials(Container c, User user, ExpMaterial start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setChildren(false);

        ExpLineage lineage = getLineage(c, user, start, options);
        return lineage.findNearestParentMaterials(start);
    }

    // Get list of ExpRun LSIDs for the start Data or Material
    @Override
    public List<String> collectRunsToInvestigate(ExpRunItem start, ExpLineageOptions options)
    {
        Pair<Map<String, String>, Map<String, String>> pair = collectRunsAndRolesToInvestigate(start, options);
        List<String> runLsids = new ArrayList<>(pair.first.size() + pair.second.size());
        runLsids.addAll(pair.first.keySet());
        runLsids.addAll(pair.second.keySet());

        return runLsids;
    }

    // Get up and down maps of ExpRun LSID to Role
    public Pair<Map<String, String>, Map<String, String>> collectRunsAndRolesToInvestigate(ExpRunItem start, ExpLineageOptions options)
    {
        Map<String, String> runsUp = new HashMap<>();
        Map<String, String> runsDown = new HashMap<>();
        boolean up = options.isParents();
        boolean down = options.isChildren();

        ExpRun parentRun = start.getRun();
        if (up)
        {
            if (parentRun != null)
                runsUp.put(parentRun.getLSID(), start instanceof Data ? ExpData.DEFAULT_CPAS_TYPE : ExpMaterial.DEFAULT_CPAS_TYPE);
        }
        if (down)
        {
            if (start instanceof ExpData d)
                runsDown.putAll(flattenPairs(getRunsAndRolesUsingData(d)));
            else if (start instanceof ExpMaterial m)
                runsDown.putAll(flattenPairs(getRunsAndRolesUsingMaterial(m)));

            if (parentRun != null)
                runsDown.remove(parentRun.getLSID());
        }

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

    @Override
    @NotNull
    public ExpLineage getLineage(Container c, User user, @NotNull Identifiable start, @NotNull ExpLineageOptions options)
    {
        return getLineage(c, user, Set.of(start), options);
    }

    @NotNull
    public ExpLineage getLineage(Container c, User user, @NotNull Set<Identifiable> seeds, @NotNull ExpLineageOptions options)
    {
        return ExpLineageServiceImpl.get().getLineage(c, user, seeds, options);
    }

    @Override
    public SQLFragment generateExperimentTreeSQLLsidSeeds(List<String> lsids, ExpLineageOptions options)
    {
        assert !options.isUseObjectIds();
        String comma = "";
        SQLFragment sqlf = new SQLFragment();
        for (String lsid : lsids)
        {
            sqlf.append(comma).append("?").add(lsid);
            comma = ",";
        }
        return generateExperimentTreeSQL(sqlf, options);
    }

    public SQLFragment generateExperimentTreeSQLObjectIdsSeeds(Collection<Integer> objectIds, ExpLineageOptions options)
    {
        assert options.isUseObjectIds();
        String comma = "";
        SQLFragment sqlf = new SQLFragment("VALUES ");
        for (Integer objectId : objectIds)
        {
            sqlf.append(comma).append("(").appendValue(objectId).append(")");
            comma = ",";
        }
        return generateExperimentTreeSQL(sqlf, options);
    }

    /* return <ParentsQuery,ChildrenQuery> */
    private Pair<String,String> getRunGraphCommonTableExpressions(SQLFragment ret, SQLFragment lsidsFrag, ExpLineageOptions options)
    {
        String jspPath = options.isForLookup() ? "/org/labkey/experiment/api/ExperimentRunGraphForLookup2.jsp" : "/org/labkey/experiment/api/ExperimentRunGraph2.jsp";

        String sourceSQL;
        try
        {
            sourceSQL = new JspTemplate<>(jspPath, options).render();
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

        Map<String,String> map = new HashMap<>();
        SqlDialect dialect = getExpSchema().getSqlDialect();

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
            if (name.equals("$PARENTS_INNER$") || name.equals("$CHILDREN_INNER$"))
            {
                select = select.replace("$LSIDS$", lsidsFrag.getRawSQL());
                if (options.getSourceKey() != null)
                    select = select.replace("$SOURCEKEY$", dialect.getStringHandler().quoteStringLiteral(options.getSourceKey()));
            }
            map.put(name, select);
        }

        String edgesToken = null;

        boolean recursive = dialect.isPostgreSQL();

        String parentsToken = null;
        if (options.isParents())
        {
            String parentsInnerSelect = map.get("$PARENTS_INNER$");
            SQLFragment parentsInnerSelectFrag = SQLFragment.unsafe(parentsInnerSelect);
            parentsInnerSelectFrag.addAll(lsidsFrag.getParams());
            String parentsInnerToken = ret.addCommonTableExpression(parentsInnerSelect, "org_lk_exp_PARENTS_INNER", parentsInnerSelectFrag, recursive);

            String parentsSelect = map.get("$PARENTS$");
            parentsSelect = StringUtils.replace(parentsSelect, "$PARENTS_INNER$", parentsInnerToken);
            // don't use parentsSelect as key, it may not consolidate correctly because of parentsInnerToken
            parentsToken = ret.addCommonTableExpression("$PARENTS$/" + StringUtils.defaultString(options.getExpTypeValue(), "ALL") + "/" + parentsInnerSelect, "org_lk_exp_PARENTS", SQLFragment.unsafe(parentsSelect), recursive);
        }

        String childrenToken = null;
        if (options.isChildren())
        {
            String childrenInnerSelect = map.get("$CHILDREN_INNER$");
            childrenInnerSelect = StringUtils.replace(childrenInnerSelect, "$EDGES$", edgesToken);
            SQLFragment childrenInnerSelectFrag = SQLFragment.unsafe(childrenInnerSelect);
            childrenInnerSelectFrag.addAll(lsidsFrag.getParams());
            String childrenInnerToken = ret.addCommonTableExpression(childrenInnerSelect, "org_lk_exp_CHILDREN_INNER", childrenInnerSelectFrag, recursive);

            String childrenSelect = map.get("$CHILDREN$");
            childrenSelect = StringUtils.replace(childrenSelect, "$CHILDREN_INNER$", childrenInnerToken);
            // don't use childrenSelect as key, it may not consolidate correctly because of childrenInnerToken
            childrenToken = ret.addCommonTableExpression("$CHILDREN$/" + StringUtils.defaultString(options.getExpTypeValue(), "ALL") + "/" + childrenInnerSelect, "org_lk_exp_CHILDREN", SQLFragment.unsafe(childrenSelect), recursive);
        }

        return new Pair<>(parentsToken,childrenToken);
    }

    /**
     * Walk experiment graph with one tricky recursive query.
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
     * Each row in the result represents one 'edge' or 'leaf/root' in the experiment graph, that is to say
     * nodes (material,data,protocolapplication) may appear more than once, but edges should not.
     **/
    @Override
    public @NotNull SQLFragment generateExperimentTreeSQL(SQLFragment lsidsFrag, ExpLineageOptions options)
    {
        SQLFragment sqlf = new SQLFragment();
        Pair<String,String> tokens = getRunGraphCommonTableExpressions(sqlf, lsidsFrag, options);
        boolean up = options.isParents();
        boolean down = options.isChildren();

        if (up || down)
        {
            if (up)
            {
                SQLFragment parents = new SQLFragment();
                if (options.isOnlySelectObjectId())
                {
                    parents.append("\nSELECT objectid FROM ").append(tokens.first);
                }
                else if (options.isForLookup())
                {
                    parents.append("\nSELECT MIN(depth) AS depth, self, objectid, ");
                    parents.append("MIN(container) AS container, MIN(exptype) AS exptype, MIN(cpastype) AS cpastype, MIN(name) AS name, MIN(lsid) AS lsid, MIN(rowid) AS rowid ");
                    parents.append("\nFROM ").append(tokens.first);
                }
                else
                {
                    parents.append("\nSELECT * FROM " + tokens.first);
                }

                parents.append("\nWHERE depth != 0");
                String and = "\nAND ";

                if (options.isForLookup())
                {
                    parents.append(and).append("objectid <> self");
                }

                if (options.getExpTypeValue() != null && !"NULL".equalsIgnoreCase(options.getExpTypeValue()))
                {
                    if (options.isForLookup())
                        parents.append(and).append("exptype = ?\n");
                    else
                        parents.append(and).append("parent_exptype = ?\n");
                    parents.add(options.getExpTypeValue());
                }

                if (options.getCpasType() != null && !"NULL".equalsIgnoreCase(options.getCpasType()))
                {
                    if (options.isForLookup())
                        parents.append(and).append("cpastype = ?\n");
                    else
                        parents.append(and).append("parent_cpastype = ?\n");
                    parents.add(options.getCpasType());
                }

                if (options.getRunProtocolLsid() != null && !"NULL".equalsIgnoreCase(options.getRunProtocolLsid()))
                {
                    if (options.isForLookup())
                        parents.append(and).append("cpastype = ?\n");
                    else
                        parents.append(and).append("child_protocolLsid IN ('NONE', ?)\n");
                    parents.add(options.getRunProtocolLsid());
                }

                if (options.getDepth() != 0)
                {
                    // convert depth to negative value if it isn't
                    int depth = options.getDepth();
                    if (depth > 0)
                        depth *= -1;
                    parents.append(and).append("depth >= ").appendValue(depth);
                }

                if (options.isForLookup() && !options.isOnlySelectObjectId())
                {
                    parents.append("\nGROUP BY self, objectid");
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

                if (options.isOnlySelectObjectId())
                {
                    children.append("\nSELECT objectid FROM ").append(tokens.second);
                }
                else if (options.isForLookup())
                {
                    children.append("\nSELECT MIN(depth) AS depth, self, objectid, ");
                    children.append("MIN(container) AS container, MIN(exptype) AS exptype, MIN(cpastype) AS cpastype, MIN(name) AS name, MIN(lsid) AS lsid, MIN(rowid) AS rowid ");
                    children.append("\nFROM ").append(tokens.second);
                }
                else
                {
                    children.append("\nSELECT * FROM " + tokens.second);
                }

                children.append("\nWHERE depth != 0");
                String and = "\nAND ";

                if (options.isForLookup())
                {
                    children.append(and).append("objectid <> self");
                }

                if (options.getExpTypeValue() != null && !"NULL".equalsIgnoreCase(options.getExpTypeValue()))
                {
                    if (options.isForLookup())
                        children.append(and).append("exptype = ?\n");
                    else
                        children.append(and).append("child_exptype = ?\n");
                    children.add(options.getExpTypeValue());
                }

                if (options.getCpasType() != null && !"NULL".equalsIgnoreCase(options.getCpasType()))
                {
                    if (options.isForLookup())
                        children.append(and).append("cpastype = ?\n");
                    else
                        children.append(and).append("child_cpastype = ?\n");
                    children.add(options.getCpasType());
                }

                if (options.getRunProtocolLsid() != null && !"NULL".equalsIgnoreCase(options.getRunProtocolLsid()))
                {
                    if (options.isForLookup())
                        children.append(and).append("cpastype = ?\n");
                    else
                        children.append(and).append("parent_protocolLsid IN ('NONE', ?)\n");
                    children.add(options.getRunProtocolLsid());
                }

                if (options.getDepth() > 0)
                {
                    children.append(and).append("depth <= ").appendValue(options.getDepth());
                }

                if (options.isForLookup() && !options.isOnlySelectObjectId())
                {
                    children.append("\nGROUP BY self, objectid");
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

    private void removeEdgesForRun(int runId)
    {
        TableInfo edge = getTinfoEdge();
        int count = new SqlExecutor(edge.getSchema().getScope()).execute("DELETE FROM " + edge /* + (edge.getSqlDialect().isSqlServer() ? " WITH (TABLOCK, HOLDLOCK)" : "")  */ + " WHERE runId=?", runId);
        LOG.debug("Removed edges for run " + runId + "; count = " + count);
    }

    // prepare for bulk insert of edges
    private void prepEdgeForInsert(List<List<Object>> params, int from, int to, int runId)
    {
        assert getExpSchema().getScope().isTransactionActive();

        // ignore cycles from and to itself
        if (from == to)
            return;

        params.add(Arrays.asList(from, to, runId));
    }

    // insert objects for any LSIDs not yet in exp.object
    private void ensureNodeObjects(TableInfo expTable, Map<String, Map<String, Object>> allNodesByLsid, @NotNull Map<String, Integer> cpasTypeToObjectId)
    {
        // Issue 33932: partition into groups of 1000 to avoid SQLServer parameter limit
        var allMissingObjectLsids = allNodesByLsid.entrySet().stream()
                .filter(e -> e.getValue().get("objectId") == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (allMissingObjectLsids.isEmpty())
            return;

        DbScope scope = expTable.getSchema().getScope();
        try (Connection conn = scope.getConnection())
        {
            //noinspection SqlResolve
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE " + expTable.getSelectName() + " SET ObjectId=? WHERE LSID=?"))
            {
                Iterables.partition(allMissingObjectLsids, 1000).forEach(missingObjectLsids -> {

                    if (!missingObjectLsids.isEmpty())
                    {
                        try
                        {
                            if (LOG.isDebugEnabled())
                                LOG.debug("  creating exp.object for " + missingObjectLsids.size() + " nodes:\n" + StringUtils.join(missingObjectLsids));
                            for (var missingObjectLsid : missingObjectLsids)
                            {
                                Map<String, Object> missingObjectRow = allNodesByLsid.get(missingObjectLsid);
                                Container container = ContainerManager.getForId((String) missingObjectRow.get("container"));
                                if (container == null)
                                    throw new IllegalArgumentException();
                                String cpasType = (String) missingObjectRow.get("cpasType");
                                int objectid = ensureNodeObject(container, missingObjectLsid, cpasType, cpasTypeToObjectId);
                                missingObjectRow.put("objectid", objectid);
                                stmt.setInt(1, objectid);
                                stmt.setString(2, missingObjectLsid);
                                stmt.addBatch();
                            }
                            stmt.executeBatch();
                        }
                        catch (SQLException sqlx)
                        {
                            throw new RuntimeSQLException(sqlx);
                        }
                    }
                });
            }
        }
        catch (SQLException sqlx)
        {
            throw new RuntimeSQLException(sqlx);
        }
    }

    private int ensureNodeObject(@NotNull Container container, @NotNull String lsid, @Nullable String cpasType, @NotNull Map<String, Integer> cpasTypeToObjectId)
    {
        assert getExpSchema().getScope().isTransactionActive();

        Integer ownerObjectId = ensureOwnerObject(cpasType, cpasTypeToObjectId);
        return OntologyManager.ensureObject(container, lsid, ownerObjectId);
    }

    private Integer ensureOwnerObject(@Nullable String cpasType, @NotNull Map<String, Integer> cpasTypeToObjectId)
    {
        // NOTE: for current edge objects (Samples and Data), only Samples use ownerObjectId.  Maybe ExpData that belong to a DataClass should too?
        if (cpasType == null || cpasType.equals(ExpMaterial.DEFAULT_CPAS_TYPE) || cpasType.equals("Sample") || cpasType.equals(StudyService.SPECIMEN_NAMESPACE_PREFIX))
            return null;

        return cpasTypeToObjectId.computeIfAbsent(cpasType, (cpasType1) -> {

            // NOTE: We can't use OntologyManager.ensureObject() here (which caches) because we don't know what container the SampleType is defined in
            OntologyObject oo = OntologyManager.getOntologyObject(null, cpasType);
            if (oo == null)
            {
                // NOTE: We must get the SampleType definition so that the exp.object is ensured in the correct container
                ExpSampleType st = SampleTypeService.get().getSampleType(cpasType);
                if (st != null)
                {
                    LOG.debug("  creating exp.object.objectId for owner cpasType '" + cpasType + "' needed by child objects");
                    return OntologyManager.ensureObject(st.getContainer(), cpasType, (Integer) null);
                }
            }
            else
            {
                return oo.getObjectId();
            }
            return null;
        });
    }

    private boolean verifyEdges(int runId, Integer runObjectId, List<List<Object>> params)
    {
        // query the exp.edge table for the run and find any differences
        TableInfo edge = getTinfoEdge();
        TableSelector ts = new TableSelector(edge, edge.getColumns("fromObjectId", "toObjectId", "runId"), new SimpleFilter(FieldKey.fromParts("runId"), runId), null);

        List<List<Object>> edges = new ArrayList<>(params.size());
        ts.forEach(r -> {
            int fromObjectId = r.getInt("fromObjectId");
            int toObjectId = r.getInt("toObjectId");
            int edgeRunId = r.getInt("runId");
            edges.add(List.of(fromObjectId, toObjectId, edgeRunId));
        });

        if (params.size() == 0 && edges.size() == 0)
            return true;

        Set<List<Object>> paramSet = new HashSet<>(params);
        Set<List<Object>> edgesSet = new HashSet<>(edges);

        // compare the exp.edge table edges versus the run's edges
        final var paramEdgesNotInDb = SetUtils.difference(paramSet, edgesSet);
        final var dbEdgesNotInParams = SetUtils.difference(edgesSet, paramSet);

        if (paramEdgesNotInDb.isEmpty() && dbEdgesNotInParams.isEmpty())
        {
            LOG.debug("  all " + params.size() + " run edges and exp.edges match");
            return true;
        }
        else
        {
            Map<Integer, Identifiable> identifiableMap = new HashMap<>();

            LOG.warn("*** Run " + runId + " failed verification: " + (params.size() - paramEdgesNotInDb.size()) + " run edges and exp.edges match");
            if (!paramEdgesNotInDb.isEmpty())
            {
                LOG.warn("  " + paramEdgesNotInDb.size() + " run edges not in exp.edges table:");
                if (LOG.isDebugEnabled())
                {
                    for (List<Object> e : paramEdgesNotInDb)
                    {
                        Integer fromObjectId = (Integer) e.get(0);
                        Integer toObjectId = (Integer) e.get(1);

                        StringBuilder sb = new StringBuilder("  ");
                        appendIdent(sb, identifiableMap, runObjectId, fromObjectId);
                        sb.append(" -> ");
                        appendIdent(sb, identifiableMap, runObjectId, toObjectId);
                        LOG.debug(sb.toString());
                    }
                }
            }

            if (!dbEdgesNotInParams.isEmpty())
            {
                LOG.warn("  " + dbEdgesNotInParams.size() + " exp.edge table edges not in run:");
                if (LOG.isDebugEnabled())
                {
                    for (List<Object> e : dbEdgesNotInParams)
                    {
                        Integer fromObjectId = (Integer) e.get(0);
                        Integer toObjectId = (Integer) e.get(1);

                        StringBuilder sb = new StringBuilder("  ");
                        appendIdent(sb, identifiableMap, runObjectId, fromObjectId);
                        sb.append(" -> ");
                        appendIdent(sb, identifiableMap, runObjectId, toObjectId);
                        LOG.debug(sb.toString());
                    }
                }
            }

            return false;
        }
    }

    private void appendIdent(StringBuilder sb, Map<Integer, Identifiable> identifiableMap, Integer runObjectId, int objectId)
    {
        if (runObjectId != null && Objects.equals(runObjectId, objectId))
        {
            sb.append("{RUN}");
        }
        else
        {
            Identifiable to = identifiableMap.computeIfAbsent(objectId, this::fetchIdent);
            sb.append("{name=").append(to.getName()).append(", oid=").append(objectId).append(", lsid=").append(to.getLSID()).append("}");
        }
    }

    private Identifiable fetchIdent(Integer id)
    {
        OntologyObject oo = OntologyManager.getOntologyObject(id);
        // we have a database constraint in place
        assert oo != null;
        Identifiable ident = LsidManager.get().getObject(oo.getObjectURI());
        return Objects.requireNonNullElseGet(ident, () -> {
            var i = new IdentifiableBase(oo);
            i.setName("<not-found>");
            return i;
        });
    }

    private void insertEdges(List<List<Object>> params)
    {
        assert getExpSchema().getScope().isTransactionActive();
        if (params.isEmpty())
            return;

        try
        {
            TableInfo edge = getTinfoEdge();
            String edgeSql = "INSERT INTO " + edge +
                    /* (edge.getSqlDialect().isSqlServer() ? " WITH (TABLOCK, HOLDLOCK)" : "") + */
                    " (fromObjectId, toObjectId, runId)\n"+
                    "VALUES (?, ?, ?)";
            Table.batchExecute(getExpSchema(), edgeSql, params);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @NotNull @Override
    public Collection<Integer> getItemsWithReferences(Collection<Integer> referencedRowIds, @NotNull String referencedSchemaName, @Nullable String referencedQueryName)
    {
        if ("exp.data".equalsIgnoreCase(referencedSchemaName))
            return ExperimentServiceImpl.get().getDataUsedAsParents(referencedRowIds);
        else if ("samples".equalsIgnoreCase(referencedSchemaName))
            return ExperimentServiceImpl.get().getMaterialsUsedAsParents(referencedRowIds);
        return emptyList();
    }

    @Override
    public @NotNull String getObjectReferenceDescription(Class referencedClass)
    {
        if (referencedClass != ExpRun.class)
            return "derived data or sample dependencies";
        return null;
    }

    private class SyncRunEdgesTask implements Runnable
    {
        protected final int _runId;
        protected final Integer _runObjectId;
        protected final @Nullable String _runLsid;
        protected final @Nullable Container _runContainer;

        public SyncRunEdgesTask(int runId)
        {
            this(runId, null, null, null);
        }

        public SyncRunEdgesTask(int runId, Integer runObjectId, String runLsid, Container runContainer)
        {
            _runId = runId;
            _runObjectId = runObjectId;
            _runLsid = runLsid;
            _runContainer = runContainer;
        }

        @Override
        public void run()
        {
            if (_runObjectId !=null && _runLsid != null && _runContainer != null)
                new SyncRunEdges(_runId, _runObjectId, _runLsid, _runContainer).sync(null);
            else
                syncRunEdges(_runId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(_runId);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            return _runId == ((SyncRunEdgesTask)o)._runId;
        }
    }

    @Override
    public void queueSyncRunEdges(int runId)
    {
        DbScope scope = getExpSchema().getScope();
        if (scope.isTransactionActive())
        {
            LOG.debug("queueing syncRunEdges for run: " + runId);
            DbScope.Transaction tx = scope.getCurrentTransaction();
            tx.addCommitTask(new SyncRunEdgesTask(runId), POSTCOMMIT);
        }
        else
        {
            syncRunEdges(runId);
        }
    }

    @Override
    public void queueSyncRunEdges(ExpRun run)
    {
        DbScope scope = getExpSchema().getScope();
        if (scope.isTransactionActive())
        {
            LOG.debug("queueing syncRunEdges for run: " + run.getRowId() + " - " + run.getName());
            DbScope.Transaction tx = scope.getCurrentTransaction();
            tx.addCommitTask(new SyncRunEdgesTask(run.getRowId(), run.getObjectId(), run.getLSID(), run.getContainer()), POSTCOMMIT);
        }
        else
        {
            syncRunEdges(run);
        }
    }

    @Override
    public void syncRunEdges(int runId)
    {
        ExpRun run = getExpRun(runId);
        if (run != null)
            new SyncRunEdges(run).sync(null);
    }


    @Override
    public void syncRunEdges(ExpRun run)
    {
        new SyncRunEdges(run).sync(null);
    }


    @Override
    public void syncRunEdges(Collection<ExpRun> runs)
    {
        Map<String, Integer> cpasTypeToObjectId = new HashMap<>();

        for (ExpRun run : runs)
        {
            new SyncRunEdges(run).sync(cpasTypeToObjectId);
        }
    }


    /* syncRunEdges() has too many boolean parameters, so here's a mini builder */

    class SyncRunEdges
    {
        final int runId;
        Integer runObjectId;
        final String runLsid;
        final Container runContainer;
        boolean deleteFirst = true;
        boolean verifyEdgesNoInsert = false;
        boolean doIncrementalClosureInvalidation = true;

        SyncRunEdges(ExpRun run)
        {
            this.runId = run.getRowId();
            this.runObjectId = run.getObjectId();
            this.runLsid = run.getLSID();
            this.runContainer = run.getContainer();
        }

        SyncRunEdges(int runId, Integer runObjectId, String runLsid, Container runContainer)
        {
            this.runId = runId;
            this.runObjectId = runObjectId;
            this.runLsid = runLsid;
            this.runContainer = runContainer;
        }

        SyncRunEdges deleteFirst(boolean d)
        {
            deleteFirst = d;
            return this;
        }

        SyncRunEdges verifyEdgesNoInsert(boolean v)
        {
            verifyEdgesNoInsert = v;
            return this;
        }

        SyncRunEdges doIncrementalClosureInvalidation(boolean i)
        {
            doIncrementalClosureInvalidation = i;
            return this;
        }

        void sync(@Nullable Map<String, Integer> cpasTypeToObjectId)
        {
            DbScope expScope = getExpSchema().getScope();
            expScope.executeWithRetry((DbScope.RetryFn<Void>) tx -> {
                syncInner(cpasTypeToObjectId);
                return null;
            });
        }

        private void syncInner(@Nullable Map<String, Integer> cpasTypeToObjectId)
        {
            assert getExpSchema().getScope().isTransactionActive();

            // don't do any updates if we are just verifying
            if (verifyEdgesNoInsert)
                deleteFirst = false;

            CPUTimer timer = new CPUTimer("sync edges");
            timer.start();

            LOG.debug((verifyEdgesNoInsert ? "Verifying" : "Rebuilding") + " edges for runId " + runId);
            Set<String> dataToCpasTypes = new HashSet<>();
            // NOTE: Originally, we just filtered exp.data by runId.  This works for most runs but includes intermediate exp.data nodes and caused the ExpTest to fail
            SQLFragment dataObjects = new SQLFragment()
                    .append("SELECT d.Container, d.LSID, d.CpasType, d.ObjectId, pa.CpasType AS pa_cpas_type FROM exp.Data d\n")
                    .append("INNER JOIN exp.DataInput di ON d.rowId = di.dataId\n")
                    .append("INNER JOIN exp.ProtocolApplication pa ON di.TargetApplicationId = pa.RowId\n")
                    .append("WHERE pa.RunId = ").appendValue(runId).append(" AND pa.CpasType IN (").appendValue(ExperimentRun).append(",").appendValue(ExperimentRunOutput).append(")");

            Collection<Map<String, Object>> fromDataLsids = new ArrayList<>();
            Collection<Map<String, Object>> toDataLsids = new ArrayList<>();
            new SqlSelector(getSchema(), dataObjects).forEachMap(row -> {
                if (ExperimentRun.name().equals(row.get("pa_cpas_type")))
                    fromDataLsids.add(row);
                else
                {
                    dataToCpasTypes.add((String)row.get("cpastype"));
                    toDataLsids.add(row);
                }
            });
            if (LOG.isDebugEnabled())
            {
                if (!fromDataLsids.isEmpty())
                    LOG.debug("  fromDataLsids:\n  " + StringUtils.join(fromDataLsids, "\n  "));
                if (!toDataLsids.isEmpty())
                    LOG.debug("  toDataLsids:\n  " + StringUtils.join(toDataLsids, "\n  "));
            }

            SQLFragment materials = new SQLFragment()
                    .append("SELECT m.Container, m.LSID, m.CpasType, m.ObjectId, pa.CpasType AS pa_cpas_type FROM exp.material m\n")
                    .append("INNER JOIN exp.MaterialInput mi ON m.rowId = mi.materialId\n")
                    .append("INNER JOIN exp.ProtocolApplication pa ON mi.TargetApplicationId = pa.RowId\n")
                    .append("WHERE pa.RunId = ").appendValue(runId).append(" AND pa.CpasType IN (").appendValue(ExperimentRun).append(",").appendValue(ExperimentRunOutput).append(")");

            Set<String> materialToCpasTypes = new HashSet<>();
            Collection<Map<String, Object>> fromMaterialLsids = new ArrayList<>();
            Collection<Map<String, Object>> toMaterialLsids = new ArrayList<>();
            new SqlSelector(getSchema(), materials).forEachMap(row -> {
                if (ExperimentRun.name().equals(row.get("pa_cpas_type")))
                {
                    fromMaterialLsids.add(row);
                }
                else
                {
                    toMaterialLsids.add(row);
                    materialToCpasTypes.add((String)row.get("cpastype"));
                }
            });

            Set<Pair<Integer, Integer>> provenanceStartingInputs = emptySet();
            Set<Pair<Integer, Integer>> provenanceFinalOutputs = emptySet();

            ProvenanceService pvs = ProvenanceService.get();
            ProtocolApplication startProtocolApp = getStartingProtocolApplication(runId);
            if (null != startProtocolApp)
            {
                provenanceStartingInputs = pvs.getProvenanceObjectIds(startProtocolApp.getRowId());
            }

            ProtocolApplication finalProtocolApp = getFinalProtocolApplication(runId);
            if (null != finalProtocolApp)
            {
                provenanceFinalOutputs = pvs.getProvenanceObjectIds(finalProtocolApp.getRowId());
            }

            // delete all existing edges for this run
            if (deleteFirst)
                removeEdgesForRun(runId);

            int edgeCount = fromDataLsids.size() + fromMaterialLsids.size() + toDataLsids.size() + toMaterialLsids.size() + provenanceStartingInputs.size() + provenanceFinalOutputs.size();
            LOG.debug(String.format("  edge counts: input data=%d, input materials=%d, output data=%d, output materials=%d, input prov=%d, output prov=%d, total=%d",
                    fromDataLsids.size(), fromMaterialLsids.size(), toDataLsids.size(), toMaterialLsids.size(), provenanceStartingInputs.size(), provenanceFinalOutputs.size(), edgeCount));

            if (edgeCount > 0)
            {
                // ensure the run has an exp.object
                if (null == runObjectId || 0 == runObjectId)
                {
                    if (LOG.isDebugEnabled())
                    {
                        OntologyObject runObj = OntologyManager.getOntologyObject(runContainer, runLsid);
                        if (runObj == null)
                            LOG.debug("  run exp.object is null, creating: " + runLsid);
                    }
                    if (!verifyEdgesNoInsert)
                        runObjectId = OntologyManager.ensureObject(runContainer, runLsid, (Integer) null);
                }

                Map<String, Map<String, Object>> allDatasByLsid = new HashMap<>();
                fromDataLsids.forEach(row -> allDatasByLsid.put((String) row.get("lsid"), row));
                toDataLsids.forEach(row -> allDatasByLsid.put((String) row.get("lsid"), row));
                if (!verifyEdgesNoInsert)
                    ensureNodeObjects(getTinfoData(), allDatasByLsid, cpasTypeToObjectId != null ? cpasTypeToObjectId : new HashMap<>());

                Map<String, Map<String, Object>> allMaterialsByLsid = new HashMap<>();
                fromMaterialLsids.forEach(row -> allMaterialsByLsid.put((String) row.get("lsid"), row));
                toMaterialLsids.forEach(row -> allMaterialsByLsid.put((String) row.get("lsid"), row));
                if (!verifyEdgesNoInsert)
                    ensureNodeObjects(getTinfoMaterial(), allMaterialsByLsid, cpasTypeToObjectId != null ? cpasTypeToObjectId : new HashMap<>());

                List<List<Object>> params = new ArrayList<>(edgeCount);

                //
                // from lsid -> run lsid
                //

                Set<Integer> seen = new HashSet<>();
                for (Map<String, Object> fromDataLsid : fromDataLsids)
                {
                    assert null != fromDataLsid.get("objectid");
                    int objectid = (int)fromDataLsid.get("objectid");
                    if (seen.add(objectid))
                        prepEdgeForInsert(params, objectid, runObjectId, runId);
                }

                for (Map<String, Object> fromMaterialLsid : fromMaterialLsids)
                {
                    assert null != fromMaterialLsid.get("objectid");
                    int objectid = (int)fromMaterialLsid.get("objectid");
                    if (seen.add(objectid))
                        prepEdgeForInsert(params, objectid, runObjectId, runId);
                }

                if (!provenanceStartingInputs.isEmpty())
                {
                    for (Pair<Integer, Integer> pair : provenanceStartingInputs)
                    {
                        Integer fromId = pair.first;
                        if (null != fromId)
                        {
                            if (seen.add(fromId))
                                prepEdgeForInsert(params, fromId, runObjectId, runId);
                        }
                    }
                }

                //
                // run lsid -> to lsid
                //

                seen = new HashSet<>();
                for (Map<String, Object> toDataLsid : toDataLsids)
                {
                    int objectid = (int)toDataLsid.get("objectid");
                    if (seen.add(objectid))
                        prepEdgeForInsert(params, runObjectId, objectid, runId);
                }

                for (Map<String, Object> toMaterialLsid : toMaterialLsids)
                {
                    int objectid = (int)toMaterialLsid.get("objectid");
                    if (seen.add(objectid))
                        prepEdgeForInsert(params, runObjectId, objectid, runId);
                }

                if (!provenanceFinalOutputs.isEmpty())
                {
                    for (Pair<Integer, Integer> pair : provenanceFinalOutputs)
                    {
                        Integer toObjectId = pair.second;
                        if (null != toObjectId)
                        {
                            if (seen.add(toObjectId))
                                prepEdgeForInsert(params, runObjectId, toObjectId, runId);
                        }
                    }
                }

                if (verifyEdgesNoInsert)
                    verifyEdges(runId, runObjectId, params);
                else
                {
                    insertEdges(params);

                }
            }
            else
            {
                if (verifyEdgesNoInsert)
                    verifyEdges(runId, runObjectId, Collections.emptyList());
            }

            timer.stop();
            LOG.debug("  " + (verifyEdgesNoInsert ? "verified" : "synced") + " edges in " + timer.getDuration());

            if (!verifyEdgesNoInsert && doIncrementalClosureInvalidation)
            {
                materialToCpasTypes.forEach(type -> ClosureQueryHelper.recomputeMaterialAncestorsForRun(type, runId));
                dataToCpasTypes.forEach(type -> ClosureQueryHelper.recomputeDataAncestorsForRun(type, runId));
            }
        }
    }

    public void rebuildAllRunEdges()
    {
        try (CustomTiming timing = MiniProfiler.custom("exp", "rebuildAllEdges"))
        {
            try (Timing ignored = MiniProfiler.step("delete edges"))
            {
                LOG.debug("Deleting all run-based edges");
                Table.delete(getTinfoEdge(), new SimpleFilter().addCondition(FieldKey.fromParts("runId"), null, CompareType.NONBLANK));
            }

            // Local cache of SampleType LSID to objectId. The SampleType objectId will be used as the node's ownerObjectId.
            Map<String, Integer> cpasTypeToObjectId = new HashMap<>();

            Collection<Map<String, Object>> runs = new TableSelector(getTinfoExperimentRun(),
                    getTinfoExperimentRun().getColumns("rowId", "objectid", "lsid", "container"), null, new Sort("rowId")).getMapCollection();
            try (Timing ignored = MiniProfiler.step("create edges"))
            {
                LOG.debug("Rebuilding edges for " + runs.size() + " runs");
                for (Map<String, Object> run : runs)
                {
                    Integer runId = (Integer)run.get("rowId");
                    Integer runObjectId = (Integer)run.get("objectid");
                    String runLsid = (String)run.get("lsid");
                    String containerId = (String)run.get("container");
                    Container runContainer = ContainerManager.getForId(containerId);
                    new SyncRunEdges(runId, runObjectId, runLsid, runContainer)
                            .deleteFirst(false)
                            .verifyEdgesNoInsert(false)
                            .doIncrementalClosureInvalidation(false)      // don't do incremental invalidation calls
                            .sync(cpasTypeToObjectId);
                }
            }

            if (timing != null)
            {
                timing.stop();
                LOG.debug("Rebuilt all run-based edges: " + timing.getDuration() + " ms");
            }
        }
        ClosureQueryHelper.truncateAndRecreate(LOG);
    }

    public void verifyRunEdges(ExpRun run)
    {
        new SyncRunEdges(run)
                .deleteFirst(false)
                .verifyEdgesNoInsert(true)
                .sync(null);
    }

    public void verifyAllEdges(Container c, @Nullable Integer limit)
    {
        if (c.isRoot())
        {
            Set<Container> children = ContainerManager.getAllChildren(c);
            for (Container child : children)
            {
                _verifyAllEdges(child, limit);
            }
        }
        else
        {
            _verifyAllEdges(c, limit);
        }
    }

    private void _verifyAllEdges(Container c, @Nullable Integer limit)
    {
        try (CustomTiming timing = MiniProfiler.custom("exp", "verifyAllEdges"))
        {
            // Local cache of SampleType LSID to objectId. The SampleType objectId will be used as the node's ownerObjectId.
            Map<String, Integer> cpasTypeToObjectId = new HashMap<>();

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("container"), c.getId());
            var ts = new TableSelector(getTinfoExperimentRun(),
                    getTinfoExperimentRun().getColumns("rowId", "objectid", "lsid", "container"), filter, new Sort("rowId"));
            if (limit != null)
                ts.setMaxRows(limit);
            Collection<Map<String, Object>> runs = ts.getMapCollection();
            int runCount = 0;
            try (Timing ignored = MiniProfiler.step("create edges"))
            {
                LOG.info("Verifying edges for " + runs.size() + " runs in " + c.getPath());
                for (Map<String, Object> run : runs)
                {
                    Integer runId = (Integer)run.get("rowId");
                    Integer runObjectId = (Integer)run.get("objectid");
                    String runLsid = (String)run.get("lsid");
                    String containerId = (String)run.get("container");
                    Container runContainer = ContainerManager.getForId(containerId);
                    new SyncRunEdges(runId, runObjectId, runLsid, runContainer)
                            .deleteFirst(false)
                            .verifyEdgesNoInsert(true)
                            .sync(cpasTypeToObjectId);
                    runCount++;

                    if (runCount % 1000 == 0)
                    {
                        LOG.info("  verified " + runCount + " runs...");
                    }
                }
            }

            if (timing != null)
            {
                timing.stop();
                LOG.info("Verified edges for " + (limit == null ? "all " : "only ") + runCount + " runs: " + timing.getDuration() + " ms");
            }
        }
    }

    public void clearAncestors(ExpRunItem runItem)
    {
        boolean isSample = runItem instanceof ExpMaterial;
        if (isSample)
            ClosureQueryHelper.clearAncestorsForMaterial(runItem.getRowId());
        else
            ClosureQueryHelper.clearAncestorsForDataObject(runItem.getRowId());
    }

    public List<ProtocolApplication> getProtocolApplicationsForRun(int runId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RunId"), runId);
        return new TableSelector(getTinfoProtocolApplication(), filter, new Sort("ActionSequence, RowId")).getArrayList(ProtocolApplication.class);
    }

    public ProtocolApplication getStartingProtocolApplication(int runId)
    {
        List<ProtocolApplication> protocolApplications = getProtocolApplicationsForRun(runId);
        ProtocolApplication protocolApplication = null;

        if (!protocolApplications.isEmpty())
        {
            protocolApplication = protocolApplications.get(0);
        }
        return protocolApplication;
    }

    public ProtocolApplication getFinalProtocolApplication(int runId)
    {
        List<ProtocolApplication> protocolApplications = getProtocolApplicationsForRun(runId);
        ProtocolApplication protocolApplication = null;

        if (!protocolApplications.isEmpty())
        {
            int size = protocolApplications.size();
            protocolApplication = protocolApplications.get(size-1);
        }

        return protocolApplication;
    }

    public boolean isUnknownMaterial(@NotNull ExpRunItem output)
    {
        return "Unknown".equals(output.getName()) &&
                ParticipantVisit.ASSAY_RUN_MATERIAL_NAMESPACE.equals(output.getLSIDNamespacePrefix());
    }

    /**
     * @return the data objects that were attached to the run that should be attached to the run in its new folder
     */
    @Override
    public List<ExpDataImpl> deleteExperimentRunForMove(int runId, User user)
    {
        ExpRunImpl run = getExpRun(runId);
        if (run == null)
            return Collections.emptyList();

        List<ExpDataImpl> datasToDelete = getExpDatasForRun(runId);
        deleteRun(run, datasToDelete, user, null);
        return datasToDelete;
    }

    private void deleteRun(ExpRunImpl run, List<ExpDataImpl> datasToDelete, User user, String userComment)
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.beforeRunDelete(run.getProtocol(), run, user);
        }

        // Note: At the moment, FlowRun is the only example of an ExpRun attachment parent, but we're keeping this general
        // so other cases can be added in the future
        AttachmentService.get().deleteAttachments(new ExpRunAttachmentParent(run));

        // remove edges prior to deleting protocol applications
        // Calling deleteProtocolApplications calls ExperimentService.beforeDeleteData() which
        // eventually calls AbstractAssayTsvDataHandler.beforeDeleteData() to clean up any assay results
        // as well as the exp.object for the assay result rows.  The assay result rows will have an
        // output exp.edge created by the provenance module.
        removeEdgesForRun(run.getRowId());

        run.deleteProtocolApplications(datasToDelete, user);

        SQLFragment sql = new SQLFragment("DELETE FROM exp.RunList WHERE ExperimentRunId = ?").add(run.getRowId()).appendEOS();
        sql.append("\nUPDATE exp.ExperimentRun SET ReplacedByRunId = NULL WHERE ReplacedByRunId = ?").add(run.getRowId()).appendEOS();
        sql.append("\nDELETE FROM ").append(getTinfoEdge()).append(" WHERE runId = ?").add(run.getRowId()).appendEOS();
        sql.append("\nDELETE FROM exp.ExperimentRun WHERE RowId = ?").add(run.getRowId()).appendEOS();

        new SqlExecutor(getExpSchema()).execute(sql);

        // delete run properties and all children
        OntologyManager.deleteOntologyObject(run.getLSID(), run.getContainer(), true);

        ExpProtocolImpl protocol = run.getProtocol();
        if (protocol == null)
        {
            throw new IllegalStateException("Could not resolve protocol for run LSID " + run.getLSID() + " with protocol LSID " + run.getDataObject().getProtocolLSID() );
        }
        auditRunEvent(user, protocol, run, null, "Run deleted", userComment);

        for (ExperimentListener listener : _listeners)
        {
            listener.afterRunDelete(run.getProtocol(), run, user);
        }
    }


    public static DbSchema getExpSchema()
    {
        return DbSchema.get("exp", DbSchemaType.Module);
    }

    @Override
    public TableInfo getTinfoExperiment()
    {
        return getExpSchema().getTable("Experiment");
    }

    @Override
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

    @Override
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

    @Override
    public TableInfo getTinfoMaterial()
    {
        return getExpSchema().getTable("Material");
    }

    @Override
    public TableInfo getTinfoMaterialAncestors()
    {
        return getExpSchema().getTable("MaterialAncestors");
    }

    public TableInfo getTinfoMaterialIndexed()
    {
        return getExpSchema().getTable("MaterialIndexed");
    }

    @Override
    public TableInfo getTinfoMaterialInput()
    {
        return getExpSchema().getTable("MaterialInput");
    }

    @Override
    public TableInfo getTinfoSampleType()
    {
        return getExpSchema().getTable("MaterialSource");
    }

    @Override
    public TableInfo getTinfoData()
    {
        return getExpSchema().getTable("Data");
    }

    public TableInfo getTinfoDataIndexed()
    {
        return getExpSchema().getTable("DataIndexed");
    }

    @Override
    public TableInfo getTinfoDataClass()
    {
        return getExpSchema().getTable("DataClass");
    }

    @Override
    public TableInfo getTinfoDataInput()
    {
        return getExpSchema().getTable("DataInput");
    }

    @Override
    public TableInfo getTinfoDataAncestors()
    {
        return getExpSchema().getTable("DataAncestors");
    }

    @Override
    public TableInfo getTinfoProtocolInput()
    {
        return getExpSchema().getTable("ProtocolInput");
    }

    @Override
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

    @Override
    public TableInfo getTinfoPropertyDescriptor()
    {
        return getExpSchema().getTable("PropertyDescriptor");
    }

    @Override
    public TableInfo getTinfoRunList ()
    {
        return getExpSchema().getTable("RunList");
    }

    @Override
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

    @Override
    public TableInfo getTinfoEdge()
    {
        return getExpSchema().getTable("Edge");
    }

    @Override
    public TableInfo getTinfoObjectLegacyNames()
    {
        return getExpSchema().getTable("ObjectLegacyNames");
    }

    @Override
    public TableInfo getTinfoDataTypeExclusion()
    {
        return getExpSchema().getTable("DataTypeExclusion");
    }

    /**
     * return the object of any known experiment type that is identified with the LSID
     *
     * @return Object identified by this lsid or null if lsid not found
     */
    @Override
    public Identifiable getObject(Lsid lsid)
    {
        LsidType type = findType(lsid);

        return null != type ? type.getObject(lsid) : null;
    }

    static final String findTypeSql = "SELECT DISTINCT Type FROM exp.AllLsid WHERE Lsid = ?";

    /**
     * @param lsid Full lsid we're looking for.
     * @return Object type for this lsid. Hmm should we return a class
     */
    @Override
    public LsidType findType(Lsid lsid)
    {
        //First check if we created this. If so, might be able to find without query
        if (AppProps.getInstance().getDefaultLsidAuthority().equals(lsid.getAuthority()))
        {
            LsidType type = LsidType.get(lsid.getNamespacePrefix());
            if (null != type)
                return type;
        }
        // AssayRunMaterial, AssayRunTSVData, GeneralAssayProtocol, LuminexAssayProtocol
        // Recipe
        // AssayDomain-SampleWellGroup
        Set<String> types = new HashSet<>(new SqlSelector(getExpSchema(), findTypeSql, lsid.toString()).getArrayList(String.class));
        if (types.size() == 1)
        {
            return LsidType.get(types.iterator().next());
        }
        if (types.isEmpty())
        {
            return null;
        }
        throw new IllegalStateException("Found multiple matching LSID types for '" + lsid + "': " + types);
    }

    public List<String> createContainerList(@NotNull Container container, @Nullable User user, boolean includeProjectAndShared)
    {
        List<String> containerIds = new ArrayList<>();
        containerIds.add(container.getId());
        if (includeProjectAndShared)
        {
            if (user == null)
            {
                throw new IllegalArgumentException("Can't include data from other containers without a user to check permissions on");
            }
            
            Container project = container.getProject();
            if (project != null && project.getEntityId() != container.getEntityId() && project.hasPermission(user, ReadPermission.class))
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

    @Override
    public List<ExpExperimentImpl> getExperiments(Container container, User user, boolean includeProjectAndShared, boolean includeBatches)
    {
        return getExperiments(container, user, includeProjectAndShared, includeBatches, false);
    }

    public List<ExpExperimentImpl> getExperiments(Container container, User user, boolean includeProjectAndShared, boolean includeBatches, boolean includeHidden)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("Container"), createContainerList(container, user, includeProjectAndShared));

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

    public ExperimentRun getExperimentRun(String lsid)
    {
        return EXPERIMENT_RUN_CACHE.get(lsid);
    }

    private class ExperimentRunCacheLoader implements CacheLoader<String, ExperimentRun>
    {
        @Override
        public ExperimentRun load(@NotNull String lsid, @Nullable Object argument)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
            return new TableSelector(getTinfoExperimentRun(), filter, null).getObject(ExperimentRun.class);
        }
    }

    @Override
    public void clearCaches()
    {
        ((SampleTypeServiceImpl) SampleTypeService.get()).clearMaterialSourceCache(null);
        getDataClassCache().clear();
        PROTOCOL_ROW_ID_CACHE.clear();
        PROTOCOL_LSID_CACHE.clear();
        DomainPropertyManager.clearCaches();
        clearExperimentRunCache();
    }

    @Override
    public void clearExperimentRunCache()
    {
        EXPERIMENT_RUN_CACHE.clear();
    }

    @Override
    public void invalidateExperimentRun(String lsid)
    {
        EXPERIMENT_RUN_CACHE.remove(lsid);
    }

    @Override
    public ExpProtocolApplication getExpProtocolApplication(String lsid)
    {
        ProtocolApplication app = getProtocolApplication(lsid);
        return app == null ? null : new ExpProtocolApplicationImpl(app);
    }

    public ProtocolApplication getProtocolApplication(String lsid)
    {
        return new TableSelector(getTinfoProtocolApplication(), new SimpleFilter(FieldKey.fromParts("LSID"), lsid), null).getObject(ProtocolApplication.class);
    }

    @Override
    @NotNull
    public List<ExpProtocolApplicationImpl> getExpProtocolApplicationsByObjectId(Container container, String objectId)
    {
        String likeFilter = "%:" + objectId;
        final SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(getTinfoProtocolApplication(), "pa");
        sql.append(" WHERE LSID LIKE ? AND RunId IN (SELECT RowId FROM ");
        sql.append(getTinfoExperimentRun(), "er");
        sql.append(" WHERE Container = ?)");
        sql.add(likeFilter);
        sql.add(container);
        return ExpProtocolApplicationImpl.fromProtocolApplications(new SqlSelector(getExpSchema(), sql).getArrayList(ProtocolApplication.class));
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
            List<Material> list = outputMap.computeIfAbsent(runLSID, k -> new ArrayList<>());
            Material m = f.fromMap(map);
            list.add(m);
        }
        return outputMap;
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
                throw UnexpectedException.wrap(e);
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
    public List<ExpDataImpl> getAllExpDataByURL(Path path, @Nullable Container c)
    {
        if (!FileUtil.hasCloudScheme(path))
            return getAllExpDataByURL(path.toFile(), c);

        return getAllExpDataByURL(FileUtil.pathToString(path), c);
    }

    @Override
    public List<ExpDataImpl> getAllExpDataByURL(File file, @Nullable Container c)
    {
        File canonicalFile = FileUtil.getAbsoluteCaseSensitiveFile(file);
        String url = canonicalFile.toPath().toUri().toString();

        return getAllExpDataByURL(url, c);
    }

    @Override
    public List<ExpDataImpl> getAllExpDataByURL(String canonicalURL, @Nullable Container c)
    {
        String dataFileUrl = canonicalURL;

        // if canonicalURL endsWith "/", try query without trailing "/"
        // see ExpDataImpl.setDataFileURI
        if (canonicalURL != null && canonicalURL.endsWith("/"))
            dataFileUrl = canonicalURL.substring(0, canonicalURL.length() - 1);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("DataFileUrl"), dataFileUrl);
        if (c != null)
            filter.addCondition(FieldKey.fromParts("Container"), c);

        return getExpDatas(filter, new Sort("-Created"));
    }

    @Override
    public ExpDataImpl getExpDataByURL(String url, @Nullable Container c)
    {
        List<String> urls = new ArrayList<>();
        urls.add(url);
        // Issue 17202 - for directories, check if the path was stored in the database without a trailing slash, but do
        // it in a single query instead of two separate DB calls
        if (url.endsWith("/"))
        {
            urls.add(url.substring(0, url.length() - 1));
        }

        SimpleFilter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("DataFileUrl"), urls));
        if (c != null)
            filter.addCondition(FieldKey.fromParts("Container"), c);

        List<Data> data = getDatas(filter, new Sort("-Created"));
        if (data.isEmpty())
            return null;

        return new ExpDataImpl(data.get(0));
    }

    public Lsid getDataClassLsid(Container container)
    {
        return Lsid.parse(generateLSIDWithDBSeq(container, ExpDataClass.class).first);
    }

    @Override
    public void deleteExperimentRunsByRowIds(Container container, final User user, int... runRowIds)
    {
        deleteExperimentRunsByRowIds(container, user, null, Arrays.stream(runRowIds).boxed().toList());
    }

    @Override
    public void deleteExperimentRunsByRowIds(Container container, final User user, @Nullable final String userComment, @NotNull Collection<Integer> runRowIds)
    {
        deleteExperimentRuns(container, user, userComment, getExpRuns(runRowIds));
    }

    public void deleteExperimentRuns(Container container, final User user, @Nullable final String userComment, @NotNull Collection<ExpRunImpl> runs)
    {
        if (runs.isEmpty())
            return;

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            // This can be slightly expensive to fetch, so don't do it multiple times if runs share protocols
            Map<ExpProtocol, ProtocolImplementation> protocolImpls = new HashMap<>();

            for (ExpRunImpl run : runs)
            {
                SimpleFilter containerFilter = new SimpleFilter(FieldKey.fromParts("RunId"), run.getRowId());
                Table.delete(getTinfoAssayQCFlag(), containerFilter);

                ExpProtocol protocol = run.getProtocol();
                ProtocolImplementation protocolImpl = null;
                if (protocol != null)
                {
                    protocolImpl = protocolImpls.computeIfAbsent(protocol, ExpProtocol::getImplementation);

                    if (!run.canDelete(user))
                        throw new UnauthorizedException("You do not have permission to delete " +
                                (ExpProtocol.isSampleWorkflowProtocol(run.getProtocol().getLSID()) ? "jobs" : "runs")
                                + " in " + run.getContainer());
                    StudyPublishService publishService = StudyPublishService.get();
                    if (publishService != null)
                    {
                        AssayWellExclusionService svc = AssayWellExclusionService.getProvider(protocol);
                        if (svc != null)
                            svc.deleteExclusionsForRun(protocol, run.getRowId());

                        for (Dataset dataset : publishService.getDatasetsForAssayRuns(Collections.singletonList(run), user))
                        {
                            // NOTE: these datasets come from various different containers
                            UserSchema schema = QueryService.get().getUserSchema(user, dataset.getContainer(), "study");
                            TableInfo tableInfo = schema.getTable(dataset.getName());
                            if (null == tableInfo || !dataset.hasPermission(user, DeletePermission.class))
                            {
                                throw new UnauthorizedException("Cannot delete rows from dataset " + dataset);
                            }

                            AssayProvider provider = AssayService.get().getProvider(protocol);
                            if (provider != null)
                            {
                                AssayTableMetadata tableMetadata = provider.getTableMetadata(protocol);
                                SimpleFilter filter = new SimpleFilter(tableMetadata.getRunRowIdFieldKeyFromResults(), run.getRowId());
                                Collection<String> lsids = new TableSelector(tableInfo, singleton("LSID"), filter, null).getCollection(String.class);

                                // Add an audit event to the link to study history
                                publishService.addRecallAuditEvent(run.getContainer(), user, dataset, lsids.size(), null);

                                // Do the actual delete on the dataset for the rows in question
                                dataset.deleteDatasetRows(user, lsids);
                            }
                        }
                    }
                    else
                    {
                        LOG.info("Skipping delete of dataset rows associated with this run: Study service not available.");
                    }
                }

                // Grab these to delete after we've deleted the Data rows
                List<ExpDataImpl> datasToDelete = getExpDatasForRun(run.getRowId());

                // Find the cross-run file input or output exp.data to delete after the run is deleted:
                // - data outputs that have the same dataFileUrl as an "cross run input" exp.data input and aren't being used in another run.
                // - data inputs with "cross run input" role, have no custom file properties, and aren't being used in another run.
                List<ExpDataImpl> crossRunInputs = new ArrayList<>();
                List<ExpDataImpl> crossRunOutputs = new ArrayList<>();
                List<ExpDataImpl> inputData = run.getInputDatas(DefaultAssayRunCreator.CROSS_RUN_DATA_INPUT_ROLE, null);
                if (!inputData.isEmpty())
                {
                    for (ExpDataImpl input : inputData)
                    {
                        // Find a matching exp.data output with the same dataFileUrl
                        for (ExpDataImpl output : datasToDelete)
                        {
                            if (input.getDataFileUrl().equals(output.getDataFileUrl()))
                            {
                                // Don't delete the exp.data output if it is being used in other runs
                                List<? extends ExpRun> otherUsages = getRunsUsingDatas(List.of(output));
                                otherUsages.remove(run);
                                if (!otherUsages.isEmpty())
                                {
                                    LOG.debug("Skipping delete of cross-run output data '" + output.getName() + "' (" + output.getRowId() + ") used by other runs: " + otherUsages.stream().map(ExpRun::getName).collect(Collectors.joining(", ")));
                                    break;
                                }

                                crossRunOutputs.add(output);
                            }
                        }

                        // If there are comments or file properties attached to the exp.data don't delete it.
                        // TODO: We don't want to delete exp.data for uploaded files since we track created/createdby and other metadata... is there a better way to identify these exp.data we don't want to delete?
                        // CONSIDER: move these properties to the matching output exp.data with the same dataFileUrl?
                        if (hasFileProperties(container, input))
                        {
                            LOG.debug("Skipping delete of cross-run input data '" + input.getName() + "' (" + input.getRowId() + ") with custom file properties");
                            continue;
                        }

                        // If the file has no other usages, we can delete it.
                        List<? extends ExpRun> otherUsages = getRunsUsingDatas(List.of(input));
                        otherUsages.remove(run);
                        if (otherUsages.isEmpty())
                        {
                            crossRunInputs.add(input);
                        }
                    }
                }

                // Archive all data files prior to deleting
                //  ideally this would be transacted as a commit task but we decided against it due to complications
                run.archiveDataFiles(user);

                deleteRun(run, datasToDelete, user, userComment);

                for (ExpData data : datasToDelete)
                {
                    ExperimentDataHandler handler = data.findDataHandler();
                    handler.deleteData(data, container, user);
                }

                // Delete the cross run data completely
                for (ExpDataImpl data : crossRunInputs)
                {
                    LOG.debug("Deleting cross-run input data: name=" + data.getName() + ", rowId=" + data.getRowId() + ", dataFileUrl=" + data.getDataFileUrl());
                    data.delete(user, false);
                }
                for (ExpDataImpl data : crossRunOutputs)
                {
                    LOG.debug("Deleting cross-run output data: name=" + data.getName() + ", rowId=" + data.getRowId() + ", dataFileUrl=" + data.getDataFileUrl());
                    data.delete(user, false);
                }

                if (protocolImpl != null)
                    protocolImpl.onRunDeleted(container, user);
            }

            transaction.commit();
        }
    }

    // return true if the data has a comment or any other custom file properties
    private boolean hasFileProperties(Container c, ExpData data)
    {
        String comment = data.getComment();
        if (comment != null)
            return true;

        FileContentService svc = FileContentService.get();
        if (svc != null)
        {
            Domain d = PropertyService.get().getDomain(c, svc.getDomainURI(c));
            if (d == null && !c.equals(data.getContainer()))
                d = PropertyService.get().getDomain(data.getContainer(), svc.getDomainURI(data.getContainer()));

            if (d != null)
            {
                Map<String, ObjectProperty> properties = data.getObjectProperties();
                for (DomainProperty dp : d.getProperties())
                    if (properties.containsKey(dp.getPropertyURI()))
                        return true;
            }
        }

        return false;
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
            sb.append(")");
            Integer[] newIds = new SqlSelector(getExpSchema(), sb.toString()).getArray(Integer.class);

            idsToCheck.addAll(Arrays.asList(newIds));

            sb = new StringBuilder();
            sb.append("SELECT ChildProtocolId FROM exp.ProtocolAction WHERE ParentProtocolId IN (");
            sb.append(idsString);
            sb.append(")");
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

        String sb = "SELECT * FROM " +
                getTinfoExperimentRun().getSelectName() +
                " WHERE ProtocolLSID IN (" +
                "SELECT LSID FROM exp.Protocol WHERE RowId IN (" +
                StringUtils.join(allProtocolIds, ", ") +
                "))";
        return ExpRunImpl.fromRuns(new SqlSelector(getExpSchema(), sb).getArrayList(ExperimentRun.class));
    }

    public void deleteProtocolByRowIds(Container c, User user, String auditUserComment, int... selectedProtocolIds) throws ExperimentException
    {
        if (selectedProtocolIds.length == 0)
            return;

        List<ExpRunImpl> runs = getExpRunsForProtocolIds(false, selectedProtocolIds);

        String protocolIds = StringUtils.join(ArrayUtils.toObject(selectedProtocolIds), ", ");

        SQLFragment sql = new SQLFragment("SELECT * FROM exp.Protocol WHERE RowId IN (" + protocolIds + ")");
        Protocol[] protocols = new SqlSelector(getExpSchema(), sql).getArray(Protocol.class);

        sql = new SQLFragment("SELECT RowId FROM exp.ProtocolAction ");
        sql.append(" WHERE (ChildProtocolId IN (").append(protocolIds).append(")");
        sql.append(" OR ParentProtocolId IN (").append(protocolIds).append(") )");
        Integer[] actionIds = new SqlSelector(getExpSchema(), sql).getArray(Integer.class);
        List<ExpProtocolImpl> expProtocols = Arrays.stream(protocols).map(ExpProtocolImpl::new).collect(toList());

        if (!c.hasPermission(user, AdminPermission.class) && !runs.isEmpty())
            throw new UnauthorizedException("You do not have sufficient permissions to delete '" + (expProtocols.size() == 1 ? expProtocols.get(0).getName() : "the protocols") + "'.");

        AssayService assayService = AssayService.get();

        try (DbScope.Transaction transaction = ensureTransaction())
        {
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

                StudyService studyService = StudyService.get();
                if (studyService != null)
                {
                    for (Dataset dataset : StudyPublishService.get().getDatasetsForPublishSource(protocolToDelete.getRowId(), Dataset.PublishSource.Assay))
                    {
                        dataset.delete(user);
                    }
                }
                else
                {
                    LOG.warn("Could not delete datasets associated with this protocol: Study service not available.");
                }
            }

            // Delete runs after deleting datasets so that we don't have to do the work to clear out the data rows
            for (ExpRun run : runs)
            {
                run.delete(user, auditUserComment);
            }

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            if (actionIds.length > 0)
            {
                if (assayService != null)
                {
                    for (Protocol protocol : protocols)
                    {
                        ExpProtocol protocolToDelete = new ExpProtocolImpl(protocol);
                        AssayProvider provider = assayService.getProvider(protocolToDelete);
                        if (provider != null)
                            provider.deleteProtocol(protocolToDelete, user, auditUserComment);
                    }
                }
                else
                {
                    LOG.info("Skipping delete of assay protocol: Assay service not available.");
                }

                String actionIdsJoined = "(" + StringUtils.join(actionIds, ", ") + ")";
                executor.execute("DELETE FROM exp.ProtocolActionPredecessor WHERE ActionId IN " + actionIdsJoined + " OR PredecessorId IN " + actionIdsJoined);
                executor.execute("DELETE FROM exp.ProtocolAction WHERE RowId IN " + actionIdsJoined);
            }

            executor.execute("DELETE FROM exp.ProtocolParameter WHERE ProtocolId IN (" + protocolIds + ")");

            deleteAllProtocolInputs(c, protocolIds);

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
            deleteProtocolByRowIds(c, user,null, orphanedProtocolIds);

            removeDataTypeExclusion(Arrays.asList(ArrayUtils.toObject(selectedProtocolIds)), DataTypeForExclusion.AssayDesign);
            if (assayService != null)
            {
                transaction.addCommitTask(() -> {
                    // Be sure that we clear the cache after we commit the overall transaction, in case it
                    // gets repopulated by another thread before then
                    assayService.clearProtocolCache();
                    for (Protocol protocol : protocols)
                    {
                        uncacheProtocol(protocol);
                    }
                }, POSTCOMMIT, DbScope.CommitTaskOption.IMMEDIATE);
            }
            else
            {
                LOG.info("Skipping clear of protocol cache: Assay service not available.");
            }

            transaction.commit();
        }

        if (assayService != null)
            assayService.deindexAssays(Collections.unmodifiableCollection(expProtocols));
    }

    private void deleteAllProtocolInputs(Container c, String protocolIdsInClause)
    {
        OntologyManager.deleteOntologyObjects(getSchema(), new SQLFragment("SELECT LSID FROM exp.ProtocolInput WHERE ProtocolId IN (" + protocolIdsInClause + ")"), c);
        new SqlExecutor(getSchema()).execute("DELETE FROM exp.ProtocolInput WHERE ProtocolId IN (" + protocolIdsInClause + ")");
    }

    private void deleteProtocolInputs(@NotNull Protocol protocol, Collection<? extends ExpProtocolInput> protocolInputsToDelete)
    {
        if (protocolInputsToDelete == null || protocolInputsToDelete.isEmpty())
            return;

        var protocolInputRowIds = protocolInputsToDelete.stream().map(ExpObject::getRowId).filter(rowId -> rowId != 0).toList();
        if (protocolInputRowIds.isEmpty())
            return;

        var table = getTinfoProtocolInput();
        SQLFragment ontologyLSIDSql = new SQLFragment("SELECT LSID FROM ").append(getTinfoProtocolInput(), "")
                .append(" WHERE ProtocolId = ?").add(protocol.getRowId())
                .append(" AND RowId ");
        table.getSqlDialect().appendInClauseSql(ontologyLSIDSql, protocolInputRowIds);

        SQLFragment deleteSql = new SQLFragment("DELETE FROM ").append(getTinfoProtocolInput(), "")
                        .append(" WHERE ProtocolId = ?").add(protocol.getRowId())
                        .append(" AND RowId ");
        table.getSqlDialect().appendInClauseSql(deleteSql, protocolInputRowIds);

        OntologyManager.deleteOntologyObjects(getSchema(), ontologyLSIDSql, protocol.getContainer());
        new SqlExecutor(getSchema()).execute(deleteSql);
    }

    public static Map<String, Collection<Map<String, Object>>> partitionRequestedOperationObjects(Collection<Integer> requestIds, Collection<Integer> notAllowedIds, List<? extends ExpRunItem> allData)
    {
        List<Integer> allowedIds = new ArrayList<>(requestIds);
        allowedIds.removeAll(notAllowedIds);
        List<Map<String, Object>> allowedRows = new ArrayList<>();
        List<Map<String, Object>> notAllowedRows = new ArrayList<>();
        allData.forEach((dataObject) -> {
            Map<String, Object> rowMap = Map.of("RowId", dataObject.getRowId(), "Name", dataObject.getName(), "ContainerPath", dataObject.getContainer().getPath());
            if (allowedIds.contains(dataObject.getRowId()))
                allowedRows.add(rowMap);
            else
                notAllowedRows.add(rowMap);
        });

        Map<String, Collection<Map<String, Object>>> partitionedIds = new HashMap<>();
        partitionedIds.put("allowed", allowedRows);
        partitionedIds.put("notAllowed", notAllowedRows);
        return partitionedIds;
    }

    /**
     * For set of rows selected for deletion, find get all linked-to-study datasets that will be affected by the delete
     * and warn user
     */
    public static ArrayList<Map<String, Object>> includeLinkedToStudyText(List<? extends ExpMaterial> allMaterials, Set<Integer> deletable, User user, Container container)
    {
        ArrayList<Map<String, Object>> associatedDatasets = new ArrayList<>();
        StudyPublishService studyPublishService = StudyPublishService.get();
        if (studyPublishService != null && !allMaterials.isEmpty())
        {
            ExpSampleType sampleType = allMaterials.get(0).getSampleType();
            UserSchema userSchema = QueryService.get().getUserSchema(user, container, SamplesSchema.SCHEMA_NAME);
            TableInfo tableInfo = userSchema.getTable(sampleType.getName());

            // collect up columns of name 'dataset<N>'
            Set<String> linkedColumnNames = new LinkedHashSet<>();
            List<ColumnInfo> columns = tableInfo.getColumns();
            for (ColumnInfo column : columns)
            {
                if (column.getName().matches("(dataset)\\d+"))
                    linkedColumnNames.add(column.getName());
            }

            if (linkedColumnNames.size() > 0)
            {
                // Obtain, for selected rows, the ids of datasets that are linked
                Set<Integer> linkedDatasetsBySelectedRow = new HashSet<>();

                // Over each selected row
                SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts(ExpMaterialTable.Column.RowId.toString()), deletable);
                TableSelector rowIdsFromTableSelector = new TableSelector(tableInfo, linkedColumnNames, filter, null);
                Collection<Map<String, Object>> selectedRow = rowIdsFromTableSelector.getMapCollection();

                // Over each column of name 'dataset<N>'
                for (Map<String, Object> selectedColumn : selectedRow)
                {
                    // Check if each cell is populated (that is, if the given row was linked to a certain study)
                    for (Map.Entry<String, Object> entry : selectedColumn.entrySet())
                    {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        if (value instanceof Integer && linkedColumnNames.contains(key))
                        {
                            linkedDatasetsBySelectedRow.add((Integer) value);
                        }
                    }
                }

                // Verify that collected dataset ids constitute linked datasets, and construct payload
                for (Dataset dataset : studyPublishService.getDatasetsForPublishSource(sampleType.getRowId(), Dataset.PublishSource.SampleType))
                {
                    if (linkedDatasetsBySelectedRow.contains(dataset.getDatasetId()))
                    {
                        ActionURL datasetURL = StudyService.get().getDatasetURL(dataset.getContainer(), dataset.getDatasetId());
                        associatedDatasets.add(Map.of("name",dataset.getStudy().getResourceName(), "url", datasetURL));
                    }
                }
            }
        }
        return associatedDatasets;
    }

    /**
     * TODO move to SampleTypeService
     * Delete samples by rowId. When <code>stDeleteFrom</code> SampleType is provided,
     * the samples must all be members of the SampleType.  When <code>stSampleType</code> is
     * null, the samples must have cpasType of {@link ExpMaterial#DEFAULT_CPAS_TYPE}.
     */
    public int deleteMaterialByRowIds(
        User user,
        Container container,
        Collection<Integer> selectedMaterialIds,
        boolean deleteRunsUsingMaterials,
        @Nullable ExpSampleTypeImpl stDeleteFrom,
        boolean ignoreStatus,
        boolean truncateContainer
    )
    {
        SQLFragment rowIdSQL = new SQLFragment("RowId ");
        rowIdSQL.appendInClause(selectedMaterialIds, getSchema().getSqlDialect());
        return deleteMaterialBySqlFilter(user, container, rowIdSQL, deleteRunsUsingMaterials, false, stDeleteFrom, ignoreStatus, truncateContainer);
    }

    /**
     * Delete samples by rowId. When <code>stDeleteFrom</code> SampleType is provided,
     * the samples must all be members of the SampleType.  When <code>stSampleType</code> is
     * null, the samples must have cpasType of {@link ExpMaterial#DEFAULT_CPAS_TYPE} unless
     * the <code>deleteFromAllSampleTypes</code> flag is true.
     * Deleting from multiple SampleTypes is only needed when cleaning an entire container.
     * @param truncateContainer delete all rows for this container. Not a real DB truncate because there may be rows in other containers.
     */
    public int deleteMaterialBySqlFilter(
        User user,
        Container container,
        SQLFragment materialFilterSQL,
        boolean deleteRunsUsingMaterials,
        boolean deleteFromAllSampleTypes,
        @Nullable ExpSampleTypeImpl stDeleteFrom,
        boolean ignoreStatus,
        boolean truncateContainer
    )
    {
        if (stDeleteFrom != null && deleteFromAllSampleTypes)
            throw new IllegalArgumentException("Can only delete from multiple sample types when no sample type is provided");

        final SqlDialect dialect = getExpSchema().getSqlDialect();
        try (DbScope.Transaction transaction = ensureTransaction();
            Timing timing = MiniProfiler.step("delete materials"))
        {
            Map<ExpSampleType, Set<Integer>> sampleTypeAliquotRoots = new HashMap<>();

            Map<String, ExpSampleTypeImpl> sampleTypes = new HashMap<>();
            if (null != stDeleteFrom)
                sampleTypes.put(stDeleteFrom.getLSID(), stDeleteFrom);

            // Document IDs to tell the search indexer should be deleted. Do this at the end to make sure the transaction
            // will be successful
            final List<String> docids = new ArrayList<>();

            int count = 0;
            // Fetch in batches so that Postgres doesn't cache all rows in memory. Disabling caching doesn't work
            // because we're inside a transaction
            final int maxBatch = 10_000;
            boolean moreBatches = true;
            while (moreBatches)
            {
                SQLFragment sql = dialect.limitRows(new SQLFragment("SELECT *"), new SQLFragment("FROM exp.Material"), new SQLFragment("WHERE ").append(materialFilterSQL), "ORDER BY RowId", null, maxBatch, count);
                List<Material> rawMaterials = new SqlSelector(getExpSchema(), sql).getArrayList(Material.class);

                moreBatches = rawMaterials.size() == maxBatch;
                count += rawMaterials.size();

                List<ExpMaterialImpl> materials = ExpMaterialImpl.fromMaterials(rawMaterials);
                for (ExpMaterialImpl material : materials)
                {
                    if (!material.getContainer().hasPermission(user, DeletePermission.class))
                        throw new UnauthorizedException();

                    if (!ignoreStatus && !material.isOperationPermitted(SampleTypeService.SampleOperations.Delete))
                        throw new IllegalArgumentException(String.format("Sample %s with status %s cannot be deleted", material.getName(), material.getStateLabel()));

                    docids.add(material.getDocumentId());

                    if (null == stDeleteFrom)
                    {
                        if (deleteFromAllSampleTypes)
                        {
                            String cpasType = material.getCpasType();
                            if (!sampleTypes.containsKey(cpasType))
                            {
                                ExpSampleTypeImpl st = material.getSampleType();
                                if (st == null && !ExpMaterial.DEFAULT_CPAS_TYPE.equals(material.getCpasType()))
                                    LOG.warn("SampleType '" + material.getCpasType() + "' not found while deleting sample '" + material.getName() + "'");
                                sampleTypes.put(cpasType, st);
                            }
                        }
                        else
                        {
                            // verify the material doesn't belong to a SampleType
                            if (!ExpMaterial.DEFAULT_CPAS_TYPE.equals(material.getCpasType()))
                                throw new IllegalArgumentException("Error deleting sample of default '" + ExpMaterial.DEFAULT_CPAS_TYPE + "' type: '" + material.getName() + "' is in the sample type '" + material.getCpasType() + "'");
                        }
                    }
                    else
                    {
                        // verify the material doesn't belong to a SampleType
                        if (!stDeleteFrom.getLSID().equals(material.getCpasType()))
                            throw new IllegalArgumentException("Error deleting '" + stDeleteFrom.getName() + "' sample: '" + material.getName() + "' is in the sample type '" + material.getCpasType() + "'");
                    }

                    if (!truncateContainer && !Objects.equals(material.getRowId(), material.getRootMaterialRowId()))
                    {
                        ExpSampleType sampleType = material.getSampleType();
                        sampleTypeAliquotRoots.computeIfAbsent(sampleType, (k) -> new HashSet<>())
                                .add(material.getRootMaterialRowId());
                    }
                }

                try (Timing ignored = MiniProfiler.step("beforeDelete"))
                {
                    beforeDeleteMaterials(user, container, materials);
                }

                try (Timing ignored = MiniProfiler.step("deleteRunsUsingInput"))
                {
                    // Delete any runs using the material if the ProtocolImplementation allows deleting the run when an input is deleted.
                    if (deleteRunsUsingMaterials)
                    {
                        deleteRunsUsingInputs(user, null, rawMaterials);
                    }
                }
                LOG.debug("Completed batch of sample deletion. " + count + " rows processed so far");
            }

            // generate in clause for the Material LSIDs
            SQLFragment lsidInFrag = new SQLFragment(" IN (SELECT Lsid FROM ");
            lsidInFrag.append(getTinfoMaterial(), "m");
            lsidInFrag.append(" WHERE ");
            lsidInFrag.append(materialFilterSQL);
            lsidInFrag.append(")");

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            try (Timing ignored = MiniProfiler.step("exp.materialAliasMap"))
            {
                SQLFragment deleteAliasSql = new SQLFragment("DELETE FROM ").append(String.valueOf(getTinfoMaterialAliasMap())).append(" WHERE LSID ")
                        .append(lsidInFrag);
                executor.execute(deleteAliasSql);
            }

            // Stash the ObjectIds that we're going to delete after we delete from exp.material
            final String suffix = StringUtilsLabKey.getPaddedUniquifier(9);
            final String objectTempTableName = getSchema().getSqlDialect().getTempTablePrefix() + "ObjectId" + suffix;
            try (Timing ignored = MiniProfiler.step("create object temp table"))
            {
                executor.execute(new SQLFragment("CREATE ")
                        .append(getSchema().getSqlDialect().getTempTableKeyword())
                        .append(" TABLE ")
                        .append(objectTempTableName)
                        .append("(ObjectId INT NOT NULL PRIMARY KEY)"));

                executor.execute(new SQLFragment("INSERT INTO ")
                        .append(objectTempTableName)
                        .append("(ObjectId) SELECT ObjectId FROM exp.Material WHERE ")
                        .append(materialFilterSQL));
            }

            try (Timing ignored = MiniProfiler.step("exp.edges"))
            {
                TableInfo edge = getTinfoEdge();
                executor.execute(new SQLFragment("DELETE FROM ").append(edge).append(" WHERE fromObjectId IN (SELECT ObjectId FROM ").append(objectTempTableName).append(")"));
                executor.execute(new SQLFragment("DELETE FROM ").append(edge).append(" WHERE toObjectId IN (SELECT ObjectId FROM ").append(objectTempTableName).append(")"));
                executor.execute(new SQLFragment("DELETE FROM ").append(edge).append(" WHERE sourceId IN (SELECT ObjectId FROM ").append(objectTempTableName).append(")"));
            }

            SQLFragment materialIdSql = new SQLFragment("(SELECT RowId FROM ");
            materialIdSql.append(getTinfoMaterial(), "m");
            materialIdSql.append(" WHERE ");
            materialIdSql.append(materialFilterSQL);
            materialIdSql.append(")");

            // Delete MaterialInput exp.object and properties
            try (Timing ignored = MiniProfiler.step("MI exp.object"))
            {
                SQLFragment inputObjects = new SQLFragment("SELECT ")
                        .append(dialect.concatenate(
                                new SQLFragment().appendValue(MaterialInput.lsidPrefix(), dialect),
                                new SQLFragment("CAST(mi.materialId AS VARCHAR)"),
                                new SQLFragment("'.'"),
                                new SQLFragment("CAST(mi.targetApplicationId AS VARCHAR)")))
                        .append(" FROM ").append(getTinfoMaterialInput(), "mi")
                        .append(" WHERE mi.materialId IN ")
                        .append(materialIdSql);
                OntologyManager.deleteOntologyObjects(getSchema(), inputObjects, container);
            }

            // exp.MaterialIndexed handled via a ON DELETE CASCADE foreign key

            // delete exp.MaterialInput
            try (Timing ignored = MiniProfiler.step("exp.MaterialInput"))
            {
                SQLFragment materialInputSQL = new SQLFragment("DELETE FROM exp.MaterialInput WHERE MaterialId IN ");
                materialInputSQL.append(materialIdSql);
                executor.execute(materialInputSQL);
            }

            try (Timing ignored = MiniProfiler.step("expsampletype materialized tables"))
            {
                for (ExpSampleTypeImpl st : sampleTypes.values())
                {
                    // Material may have been orphaned from its SampleType
                    if (st == null)
                        continue;

                    TableInfo dbTinfo = st.getTinfo();
                    // NOTE: study specimens don't have a domain for their samples, so no table
                    if (null != dbTinfo)
                    {
                        SQLFragment sampleTypeSQL = new SQLFragment("DELETE FROM " + dbTinfo + " WHERE lsid IN (SELECT lsid FROM exp.Material WHERE ");
                        sampleTypeSQL.append(materialFilterSQL);
                        sampleTypeSQL.append(")");
                        executor.execute(sampleTypeSQL);
                    }
                }
            }

            try (Timing ignored = MiniProfiler.step("exp.Material"))
            {
                SQLFragment materialSQL = new SQLFragment("DELETE FROM exp.Material WHERE ");
                materialSQL.append(materialFilterSQL);
                executor.execute(materialSQL);
            }

            // clean up provenance
            ProvenanceService.get().deleteProvenanceByLsids(container, user, lsidInFrag, false, Set.of(StudyPublishService.STUDY_PUBLISH_PROTOCOL_LSID));

            // delete exp.objects
            try (Timing ignored = MiniProfiler.step("exp.object"))
            {
                SQLFragment objectIdSql = new SQLFragment("SELECT ObjectId FROM ")
                        .append(objectTempTableName);
                OntologyManager.deleteOntologyObjectsByObjectIdSql(getSchema(), objectIdSql);
            }

            // Get rid of our temp table
            try (Timing ignored = MiniProfiler.step("drop object temp table"))
            {
                executor.execute("DROP TABLE " + objectTempTableName);
            }

            // recalculate rollup
            if (!truncateContainer)
            {
                try (Timing ignored = MiniProfiler.step("recalculate aliquot rollup"))
                {
                    for (Map.Entry<ExpSampleType, Set<Integer>> sampleTypeRoots : sampleTypeAliquotRoots.entrySet())
                    {
                        ExpSampleType parentSampleType = sampleTypeRoots.getKey();
                        Set<Integer> rootSampleIds = sampleTypeRoots.getValue();
                        int recomputeCount = SampleTypeService.get().recomputeSamplesRollup(rootSampleIds, parentSampleType.getMetricUnit(), container);
                        if (0 < recomputeCount)
                            SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(parentSampleType, rollup);
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }

            // since we don't call onSamplesChanged() for deleted rows, need to tell someone to refresh the materialized view (if any)
            for (var st : sampleTypes.values())
                if (null != st)
                    SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(st, SampleTypeServiceImpl.SampleChangeType.delete);

            // On successful commit, start task to remove items from search index
            final SearchService ss = SearchService.get();
            if (null != ss)
            {
                transaction.addCommitTask(
                    () -> ss.defaultTask().addRunnable(() -> ss.deleteResources(docids), SearchService.PRIORITY.bulk),
                    POSTCOMMIT);
            }

            transaction.commit();
            if (timing != null)
                LOG.info("SampleType delete timings\n" + timing.dump());
            return count;
        }
    }

    private void deleteRunsUsingInputs(User user, Collection<Data> dataItems, Collection<Material> materialItems)
    {
        var runsUsingItems = new ArrayList<ExpRun>();
        Set<Integer> dataIds = dataItems == null || dataItems.isEmpty() ? Collections.emptySet() : dataItems.stream().map(RunItem::getRowId).collect(Collectors.toSet());
        Set<Integer> sampleIds = materialItems == null || materialItems.isEmpty() ? Collections.emptySet() : materialItems.stream().map(RunItem::getRowId).collect(Collectors.toSet());

        if (!sampleIds.isEmpty())
        {
            // get runs where these samples are used as inputs
            runsUsingItems.addAll(getDerivedRunsFromMaterial(sampleIds));
            // retain runs if there are other inputs that are not being deleted
            if (!runsUsingItems.isEmpty())
            {
                Set<ExpRun> runsToKeep = new HashSet<>();
                runsUsingItems.forEach(run -> {
                    run.getMaterialInputs().keySet().forEach(
                            sample -> {
                                if (!sampleIds.contains(sample.getRowId()))
                                    runsToKeep.add(run);
                            }
                    );
                    run.getDataInputs().keySet().forEach(
                            dataObject -> {
                                if (!dataIds.contains(dataObject.getRowId()))
                                    runsToKeep.add(run);
                            }
                    );
                });
                runsUsingItems.removeAll(runsToKeep);
            }
            // add runs that will no longer have any outputs
            runsUsingItems.addAll(getDeletableSourceRunsFromInputRowId(sampleIds, getTinfoMaterial(), Collections.emptySet(), getTinfoData()));
        }

        if (!dataIds.isEmpty())
        {
            // get runs where these data objects are used as inputs
            var dataInputRuns = new ArrayList<ExpRun>(getDerivedRunsFromData(dataIds));
            // retain runs if there are other inputs that are not being deleted
            if (!dataInputRuns.isEmpty())
            {
                Set<ExpRun> runsToKeep = new HashSet<>();
                dataInputRuns.forEach(run -> {
                    run.getMaterialInputs().keySet().forEach(
                            sample -> {
                                if (!sampleIds.contains(sample.getRowId()))
                                    runsToKeep.add(run);
                            }
                    );
                    run.getDataInputs().keySet().forEach(
                            dataObject -> {
                                if (!dataIds.contains(dataObject.getRowId()))
                                    runsToKeep.add(run);
                            }
                    );
                });
                dataInputRuns.removeAll(runsToKeep);
            }
            runsUsingItems.addAll(dataInputRuns);
            // get runs that will no longer have any outputs
            runsUsingItems.addAll(getDeletableSourceRunsFromInputRowId(dataIds, getTinfoData(), sampleIds, getTinfoMaterial()));
        }

        List<ExpRunImpl> runsToDelete = runsDeletedWithInput(runsUsingItems);
        if (runsToDelete.isEmpty())
            return;

        var containers = new HashSet<Container>();
        for (ExpRun run : runsToDelete)
        {
            Container runContainer = run.getContainer();
            if (containers.add(runContainer))
            {
                if (!runContainer.hasPermission(user, DeletePermission.class))
                    throw new UnauthorizedException();
            }
        }

        // do this the fast way if there is only one container involved
        if (containers.size() == 1)
        {
            Container runContainer = containers.iterator().next();
            deleteExperimentRuns(runContainer, user, null, runsToDelete);
        }
        else
        {
            // the slow way
            for (ExpRunImpl run : runsToDelete)
                deleteExperimentRuns(run.getContainer(), user, null, Collections.singleton(run));
        }
    }

    /* Finds the runs where all outputs are also being deleted */
    private Collection<? extends ExpRun> getDeletableSourceRunsFromInputRowId(Collection<Integer> rowIds, TableInfo primaryTableInfo, Collection<Integer> siblingRowIds, TableInfo siblingTableInfo)
    {
        if (rowIds == null || rowIds.isEmpty())
            return Collections.emptyList();

        /* Ex. SQL
        SELECT DISTINCT m.runId
	    FROM exp.material m
        WHERE m.rowId in (3592, 3593, 3594)
            AND NOT EXIST (
                -- Check for siblings
                SELECT DISTINCT m2.runId
                FROM exp.material m2
                WHERE m.rowId in (3592, 3593, 3594)
                    AND m.runId = m2.runId
                    -- exclude siblings from selected materialIds
                    AND NOT EXIST (
                        SELECT rowId
                        FROM exp.material m3
                        WHERE m3.rowId in (3592, 3593, 3594)
                            AND m2.rowId = m3.rowId
                    )
            )
            AND NOT EXIST (
             -- Check for siblings that are not being deleted
                SELECT DISTINCT d.runId
                FROM exp.data d
                WHERE m.rowId in (3592, 3593, 3594)
                    AND m.runId = d.runId
                    -- exclude siblings from selected materialIds
                    AND NOT EXIST (
                        SELECT rowId
                        FROM exp.data d2
                        WHERE d2.rowId in (23592, 23593, 23594)
                            AND d.rowId = d2.rowId
                    )

            );
         */

        SqlDialect d = getExpSchema().getSqlDialect();
        SQLFragment idInClause = getAppendInClause(rowIds);
        SQLFragment siblingIdInClause = getAppendInClause(siblingRowIds);

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<getDeletableSourceRunsFromInputRowId>", d);
        sql.append("SELECT DISTINCT m.runId\n")
                .append("FROM ").append(primaryTableInfo, "m").append("\n")
                .append("WHERE m.rowId ").append(idInClause).append("\n")
                .append("AND NOT EXISTS (\n")
                .append("SELECT DISTINCT m2.runId\n")
                .append("FROM ").append(primaryTableInfo, "m2").append("\n")
                .append("WHERE m.rowId ").append(idInClause).append("\n")
                .append("AND m.runId = m2.runId\n")
                .append("AND NOT EXISTS (\n") // m2.rowID not in materialIds
                .append("SELECT rowId FROM ").append(primaryTableInfo, "m3").append("\n")
                .append("WHERE m3.rowId ").append(idInClause).append("\n")
                .append("AND m2.rowId = m3.rowId\n")
                .append("))\n")
                .append("AND NOT EXISTS (\n")
                .append("SELECT DISTINCT s.runId\n")
                .append("FROM ").append(siblingTableInfo, "s").append("\n")
                .append("WHERE m.rowId ").append(idInClause).append("\n")
                .append("AND m.runId = s.runId\n");
        if (!siblingRowIds.isEmpty())
        {
            sql.append("AND NOT EXISTS (\n") // s2.rowID not in siblingRowIds
                    .append("SELECT rowId FROM ").append(siblingTableInfo, "s2").append("\n")
                    .append("WHERE s2.rowId ").append(siblingIdInClause).append("\n")
                    .append("AND s.rowId = s2.rowId\n")
                    .append(")");
        }
        sql.append(")");
        sql.appendComment("</getDeletableSourceRunsFromInputRowId>", d);

        return ExpRunImpl.fromRuns(getRunsForRunIds(sql));
    }

    private Collection<? extends ExpRun> getDerivedRunsFromMaterial(Collection<Integer> materialIds)
    {
        if (materialIds == null || materialIds.isEmpty())
            return Collections.emptyList();

        return ExpRunImpl.fromRuns(getRunsForRunIds(getTargetRunIdsFromMaterialIds(getAppendInClause(materialIds))));
    }

    private Collection<? extends ExpRun> getDerivedRunsFromData(Collection<Integer> rowIds)
    {
        if (rowIds == null || rowIds.isEmpty())
            return Collections.emptyList();

        return ExpRunImpl.fromRuns(getRunsForRunIds(getTargetRunIdsFromDataIds(getAppendInClause(rowIds))));
    }

    /**
     * Finds the subset of materialIds that are used as inputs to runs.
     *
     * Note that this currently will not find runs where the batch id references a sampleId.  See Issue 37918.
     */
    public List<Integer> getMaterialsUsedAsParents(Collection<Integer> materialIds)
    {
        return getSubsetUsedAsParents(materialIds, getTinfoMaterial(), "materialID", getTinfoMaterialInput());
    }

    /**
     * Finds the subset of data that are used as inputs to runs, or as starting nodes for lineage edges.
     */
    public List<Integer> getDataUsedAsParents(Collection<Integer> dataIds)
    {
        return getSubsetUsedAsParents(dataIds, getTinfoData(), "dataID", getTinfoDataInput());
    }

    private List<Integer> getSubsetUsedAsParents(Collection<Integer> rowIds, TableInfo primaryTInfo, String inputsIdFieldName, TableInfo inputsTInfo)
    {
        if (rowIds.isEmpty())
            return emptyList();
        final SqlDialect dialect = getExpSchema().getSqlDialect();
        SQLFragment rowIdInFrag = new SQLFragment();
        dialect.appendInClauseSql(rowIdInFrag, rowIds);

        // get ids used in derivation runs, assay runs, or jobs
        // ex SQL:
        /*
            SELECT DISTINCT d.dataId
            FROM exp.DataInput d, exp.protocolapplication pa
            WHERE d.targetapplicationId = pa.rowId
             AND pa.cpastype IN ('ProtocolApplication', 'ExperimentRun')
             AND d.dataId <dataRowIdSQL>;
         */
        SQLFragment sql = new SQLFragment();

        sql.append("SELECT DISTINCT i.").append(inputsIdFieldName).append("\n");
        sql.append("FROM ").append(inputsTInfo, "i").append(", \n\t");
        sql.append(getTinfoProtocolApplication(), "pa").append("\n");
        sql.append("WHERE i.TargetApplicationId = pa.rowId\n\t")
                .append("AND pa.cpastype IN (?, ?) \n").add(ProtocolApplication.name()).add(ExperimentRun.name())
                .append("AND i.").append(inputsIdFieldName).append(" ").append(rowIdInFrag).append("\n");

        ArrayList<Integer> parents = new SqlSelector(getExpSchema(), sql).getArrayList(Integer.class);

        // get any parents that are in lineage relationships not created by runs (e.g., for registry data).
        sql = new SQLFragment();
        sql.append("SELECT DISTINCT d.rowId FROM ").append(primaryTInfo, "d").append("\n")
                .append("  JOIN ").append(getTinfoEdge(), "e").append(" ON e.fromObjectId = d.objectId\n")
                .append("  WHERE d.rowId ").append(rowIdInFrag).append(" AND e.runId IS NULL ");
        parents.addAll(new SqlSelector(getExpSchema(), sql).getArrayList(Integer.class));
        return parents;

    }

    public void deleteDataByRowIds(User user, Container container, Collection<Integer> selectedDataIds)
    {
        deleteDataByRowIds(user, container, selectedDataIds, true);
    }

    public void deleteDataByRowIds(User user, Container container, Collection<Integer> selectedDataIds, boolean deleteRunsUsingData)
    {
        if (selectedDataIds.isEmpty())
            return;

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            List<Data> datas = getDatas(new SimpleFilter().addInClause(FieldKey.fromParts("RowId"), selectedDataIds), null);

            if (datas.isEmpty())
            {
                // Nothing to do - already deleted. Bail out. See issue 41715
                transaction.commit();
                return;
            }

            List<String> allLsids = new ArrayList<>(datas.size());
            Map<Integer, List<String>> lsidsByClass = new LinkedHashMap<>();

            for (Data data : datas)
            {
                if (!data.getContainer().hasPermission(user, DeletePermission.class))
                    throw new UnauthorizedException();
            }

            List<ExpDataImpl> expDatas = ExpDataImpl.fromDatas(datas);
            beforeDeleteData(user, container, expDatas);

            // Delete any runs using the data if the ProtocolImplementation allows it
            if (deleteRunsUsingData)
            {
                deleteRunsUsingInputs(user, datas, null);
            }

            for (Data data : datas)
            {
                if (!data.getContainer().equals(container))
                {
                    throw new SQLException("Attempting to delete a Data from another container");
                }

                SQLFragment deleteSql = new SQLFragment()
                    .append("DELETE FROM ").append(String.valueOf(getTinfoDataAliasMap())).append(" WHERE LSID = ?").add(data.getLSID()).appendEOS()
                    .append("DELETE FROM ").append(String.valueOf(getTinfoEdge())).append(" WHERE fromObjectId = (select objectid from exp.object where objecturi = ?)").add(data.getLSID()).appendEOS()
                    .append("DELETE FROM ").append(String.valueOf(getTinfoEdge())).append(" WHERE toObjectId = (select objectid from exp.object where objecturi = ?)").add(data.getLSID()).appendEOS()
                    .append("DELETE FROM ").append(String.valueOf(getTinfoEdge())).append(" WHERE sourceId = (select objectid from exp.object where objecturi = ?)").add(data.getLSID()).appendEOS();;
                new SqlExecutor(getExpSchema()).execute(deleteSql);

                if (data.getClassId() != null)
                {
                    List<String> byClass = lsidsByClass.computeIfAbsent(data.getClassId(), k -> new ArrayList<>(10));
                    byClass.add(data.getLSID());
                }
                allLsids.add(data.getLSID());
            }

            SqlDialect dialect = getExpSchema().getSqlDialect();

            // Delete DataInput exp.object and properties
            SQLFragment inputObjects = new SQLFragment("SELECT ")
                    .append(dialect.concatenate(
                            new SQLFragment().appendValue(DataInput.lsidPrefix(), dialect),
                                new SQLFragment("CAST(di.dataId AS VARCHAR)"),
                                new SQLFragment("'.'"),
                                new SQLFragment("CAST(di.targetApplicationId AS VARCHAR)")))
                    .append(" FROM ").append(getTinfoDataInput(), "di").append(" WHERE di.DataId ");
            dialect.appendInClauseSql(inputObjects, selectedDataIds);
            OntologyManager.deleteOntologyObjects(getSchema(), inputObjects, container);

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            SQLFragment dataInputSQL = new SQLFragment("DELETE FROM ").append(getTinfoDataInput()).append(" WHERE DataId ");
            dialect.appendInClauseSql(dataInputSQL, selectedDataIds);
            executor.execute(dataInputSQL);

            // exp.DataIndexed handled via a ON DELETE CASCADE foreign key

            // DELETE FROM provisioned dataclass tables
            for (Integer classId : lsidsByClass.keySet())
            {
                ExpDataClassImpl dataClass = getDataClass(classId);
                if (dataClass == null)
                    throw new SQLException("DataClass not found '" + classId + "'");

                List<String> lsids = lsidsByClass.get(classId);
                if (!lsids.isEmpty())
                {
                    TableInfo t = dataClass.getTinfo();
                    SQLFragment sql = new SQLFragment("DELETE FROM ").append(t).append(" WHERE lsid ");
                    dialect.appendInClauseSql(sql, lsids);
                    executor.execute(sql);
                }
            }

            SQLFragment dataSQL = new SQLFragment("DELETE FROM ").append(getTinfoData()).append(" WHERE RowId ");
            dialect.appendInClauseSql(dataSQL, selectedDataIds);
            executor.execute(dataSQL);

            // generate in clause for the Material LSIDs
            SQLFragment lsidInFrag = new SQLFragment("SELECT o.ObjectUri FROM ").append(getTinfoObject(), "o").append(" WHERE o.ObjectURI ");
            dialect.appendInClauseSql(lsidInFrag, allLsids);
            OntologyManager.deleteOntologyObjects(getSchema(), lsidInFrag, container);

            afterDeleteData(user, container, expDatas);

            // Remove from search index
            SearchService ss = SearchService.get();
            if (null != ss)
            {
                Set<String> documentIds = new HashSet<>();
                for (ExpDataImpl data : ExpDataImpl.fromDatas(datas))
                {
                    documentIds.add(data.getDocumentId());
                }
                ss.deleteResources(documentIds);
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public void deleteExpExperimentByRowId(Container c, User user, int rowId)
    {
        if (!c.hasPermission(user, DeletePermission.class))
        {
            throw new IllegalStateException("Not permitted");
        }

        ExpExperimentImpl experiment = getExpExperiment(rowId);
        if (experiment == null)
            return;

        deleteExpExperiment(c, user, experiment);
    }

    // TODO: This should be refactored to support deletion of multiple ExpExperiments at one time
    private void deleteExpExperiment(Container c, User user, @NotNull ExpExperimentImpl experiment)
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

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            SQLFragment sql = new SQLFragment("DELETE FROM " + getTinfoRunList()
                    + " WHERE ExperimentId IN ("
                    + " SELECT E.RowId FROM " + getTinfoExperiment() + " E "
                    + " WHERE E.RowId = " + experiment.getRowId()
                    + " AND E.Container = ? )", experiment.getContainer());
            executor.execute(sql);

            OntologyManager.deleteOntologyObjects(experiment.getContainer(), experiment.getLSID());

            // Inform the listeners.
            for (ExperimentListener listener : _listeners)
            {
                listener.beforeExperimentDeleted(c, user, experiment);
            }

            sql = new SQLFragment("DELETE FROM " + getTinfoExperiment()
                    + " WHERE RowId = " + experiment.getRowId()
                    + " AND Container = ?", experiment.getContainer());
            executor.execute(sql);

            for (ExperimentListener listener : _listeners)
            {
                listener.afterExperimentDeleted(c, user, experiment);
            }

            t.commit();
        }
    }

    @Override
    public void deleteAllExpObjInContainer(Container c, User user) throws ExperimentException
    {
        if (null == c)
            return;

        String sql = "SELECT RowId FROM " + getTinfoExperimentRun() + " WHERE Container = ?";
        int[] runIds = ArrayUtils.toPrimitive(new SqlSelector(getExpSchema(), sql, c).getArray(Integer.class));

        List<ExpExperimentImpl> exps = getExperiments(c, user, false, true, true);
        List<ExpSampleTypeImpl> sampleTypes = ((SampleTypeServiceImpl) SampleTypeService.get()).getSampleTypes(c, user, false);
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
                for (ListDefinition list : ListService.get().getLists(c, null, false).values())
                {
                    // Temporary fix for Issue 21400: **Deleting workbook deletes lists defined in parent container
                    if (list.getContainer().equals(c))
                    {
                        list.delete(user);
                    }
                }
            }

            // Delete DataClasses and their exp.Data members
            // Need to delete DataClass before SampleTypes since they may be referenced by the DataClass
            for (ExpDataClassImpl dataClass : dataClasses)
            {
                dataClass.delete(user);
            }

            // delete all exp.edges referenced by exp.objects in this container
            // These are usually deleted when the run is deleted (unless the run is in a different container)
            // and would be cleaned up when deleting the exp.Material and exp.Data in this container at the end of this method.
            // However, we need to delete any exp.edge referenced by exp.object before calling deleteAllObjects() for this container.
            SQLFragment deleteObjEdges = new SQLFragment()
                    .append("DELETE FROM ").append(getTinfoEdge()).append(" WHERE fromObjectId IN (SELECT ObjectId FROM ").append(getTinfoObject()).append(" WHERE Container = ?)").add(c).appendEOS()
                    .append("\nDELETE FROM ").append(getTinfoEdge()).append(" WHERE toObjectId IN (SELECT ObjectId FROM ").append(getTinfoObject()).append(" WHERE Container = ?)").add(c).appendEOS()
                    .append("\nDELETE FROM ").append(getTinfoEdge()).append(" WHERE sourceId IN (SELECT ObjectId FROM ").append(getTinfoObject()).append(" WHERE Container = ?)").add(c);
            new SqlExecutor(getExpSchema()).execute(deleteObjEdges);

            SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);
            Table.delete(getTinfoDataAliasMap(), containerFilter);
            Table.delete(getTinfoMaterialAliasMap(), containerFilter);
            deleteUnusedAliases();

            // delete material sources
            // now call the specialized function to delete the Materials that belong to the Material Source,
            // including the top-level properties of the Materials, of which there are often many
            for (ExpSampleType sampleType : sampleTypes)
            {
                sampleType.delete(user);
            }

            // delete project level sample counters
            if (c.isProject())
                SampleTypeServiceImpl.get().deleteSampleCounterSequences(c);

            // Delete all the experiments/run groups/batches
            for (ExpExperimentImpl exp : exps)
            {
                deleteExpExperiment(c, user, exp);
            }

            // now delete protocols (including their nested actions and parameters).
            deleteProtocolByRowIds(c, user, null, protIds);

            // now delete starting materials that were not associated with a MaterialSource upload.
            // we get this list now so that it doesn't include all the run-scoped Materials that were
            // deleted already
            deleteMaterialBySqlFilter(user, c, new SQLFragment("Container = ?", c), true, true, null, true, true);

            // same drill for data objects
            sql = "SELECT RowId FROM exp.Data WHERE Container = ?";
            Collection<Integer> dataIds = new SqlSelector(getExpSchema(), sql, c).getCollection(Integer.class);
            deleteDataByRowIds(user, c, dataIds);

            OntologyManager.deleteAllObjects(c, user);

            transaction.commit();
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e);
        }
    }

    @Override
    public void moveContainer(Container c, Container oldParent, Container newParent)
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

    private List<ExpDataImpl> getExpDatasForRun(int runId)
    {
        return getExpDatas(new SimpleFilter(FieldKey.fromParts("RunId"), runId), new Sort("RowId"));
    }

    @Override
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

    public void beforeDeleteData(User user, Container container, List<ExpDataImpl> datas)
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
                entry.getKey().beforeDeleteData(entry.getValue(), user);
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
        // Notify that a deletion is about to happen
        for (ExperimentListener materialListener : _listeners)
        {
            materialListener.beforeMaterialDelete(materials, container, user);
        }
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

    public List<ExpRunImpl> getRunsByObjectId(ContainerFilter containerFilter, Collection<Integer> objectIds)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("ObjectId"), objectIds);
        filter.addClause(containerFilter.createFilterClause(getSchema(), FieldKey.fromParts("Container")));

        return getExpRuns(filter);
    }

    private List<Pair<String, String>> getRunsAndRolesUsingInput(ExpRunItem item, TableInfo inputTable, String inputColumn, Supplier<List<ExpRunImpl>> runSupplier)
    {
        SQLFragment coreSql = new SQLFragment("""
                    SELECT r.LSID, i.Role, r.Created
                    FROM exp.ExperimentRun r
                    INNER JOIN exp.ProtocolApplication pa ON pa.RunId = r.RowId
                    INNER JOIN\s""");
        coreSql.append(inputTable, "i");
        coreSql.append(" ON i.targetApplicationId = pa.RowId\nWHERE ");

        SQLFragment sql = new SQLFragment("SELECT LSID, Role FROM (");
        sql.append(coreSql);
        sql.append("i.").append(inputColumn).append(" = ?\n");
        sql.add(item.getRowId());
        if (item.getSourceApplication() != null)
        {
            // Issue 46427 - speed up query by avoiding OR clause across two tables
            sql.append("UNION \n");
            sql.append(coreSql);
            sql.append("pa.RowId = ?\n");
            sql.add(item.getSourceApplication().getRowId());
        }
        sql.append(") x ORDER BY Created DESC");

        Set<String> runLsids = new HashSet<>();
        List<Pair<String, String>> runsAndRoles = new ArrayList<>();
        new SqlSelector(getExpSchema(), sql).forEachMap(row -> {
            String runLsid = (String)row.get("lsid");
            String role = (String)row.get("role");
            runsAndRoles.add(Pair.of(runLsid, role));
            runLsids.add(runLsid);
        });

        assert checkRunsMatch(runLsids, runSupplier.get());
        return runsAndRoles;
    }

    // Get a map of run LSIDs to Roles used by the Data ids.
    public List<Pair<String, String>> getRunsAndRolesUsingData(ExpData data)
    {
        return getRunsAndRolesUsingInput(data, getTinfoDataInput(), "DataId", () -> getRunsUsingDataIds(Arrays.asList(data.getRowId())));
    }

    private boolean checkRunsMatch(Set<String> lsids, List<ExpRunImpl> runs)
    {
        return lsids.size() == runs.size() && runs.stream().allMatch(r -> lsids.contains(r.getLSID()));
    }

    @Override
    public List<ExpRunImpl> getRunsUsingMaterials(List<ExpMaterial> materials)
    {
        if (materials.isEmpty())
            return Collections.emptyList();

        var ids = materials.stream().map(ExpMaterial::getRowId).toList();
        return getRunsUsingMaterials(ids);
    }

    @Override
    public List<ExpRunImpl> getRunsUsingMaterials(int... ids)
    {
        return getRunsUsingMaterials(Arrays.asList(ArrayUtils.toObject(ids)));
    }

    // consider including runs with provenance records as well
    public List<ExpRunImpl> getRunsUsingMaterials(Collection<Integer> ids)
    {
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }

        return ExpRunImpl.fromRuns(getRunsForMaterialList(getAppendInClause(ids)));
    }

    /**
     * Get sql IN clause using supplied ids
     * @param ids to include within parentheses
     * @return SQLFragment like: IN (1, 2, 3)
     */
    private SQLFragment getAppendInClause(Collection<?> ids)
    {
        return getExpSchema().getSqlDialect().appendInClauseSql(new SQLFragment(), ids);
    }

    // Get a map of run LSIDs to Roles used by the Material ids.
    public List<Pair<String, String>> getRunsAndRolesUsingMaterial(ExpMaterial material)
    {
        return getRunsAndRolesUsingInput(material, getTinfoMaterialInput(), "MaterialId", () -> getRunsUsingMaterials(material.getRowId()));
    }

    @Override
    public List<ExpRunImpl> runsDeletedWithInput(List<? extends ExpRun> runs)
    {
        List<ExpRunImpl> ret = new ArrayList<>();
        for (ExpRun run : runs)
        {
            ExpProtocol protocol = run.getProtocol();
            if (protocol != null)
            {
                ProtocolImplementation impl = protocol.getImplementation();
                if (impl != null && !impl.deleteRunWhenInputDeleted())
                {
                    continue;
                }
            }
            ret.add((ExpRunImpl) run);
        }
        return ret;
    }

    @Override
    public List<ExpRunImpl> getRunsUsingDataClasses(Collection<ExpDataClass> dataClasses)
    {
        List<Integer> rowIds = dataClasses.stream().map(ExpObject::getRowId).toList();

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
    public List<ExpRunImpl> getRunsUsingSampleTypes(ExpSampleType... sampleTypes)
    {
        List<String> materialSourceIds = new ArrayList<>(sampleTypes.length);
        for (ExpSampleType source : sampleTypes)
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

    /**
     * Get the Source and Target runs
     */
    private List<ExperimentRun> getRunsForMaterialList(@NotNull SQLFragment materialRowIdSQL)
    {
        SQLFragment sql = new SQLFragment("(\n");
        sql.append(getTargetRunIdsFromMaterialIds(materialRowIdSQL));
        sql.append("\n) UNION (\n");
        sql.append(getSourceRunIdsFromMaterialIds(materialRowIdSQL));
        sql.append("\n)");

        return getRunsForRunIds(sql);
    }

    /**
     * Get set ExperimentRuns from subquery
     * @param runIdsSQL subquery providing runIds
     * @return List of Experiment runs from subquery
     */
    private List<ExperimentRun> getRunsForRunIds(SQLFragment runIdsSQL)
    {
        SQLFragment sql = new SQLFragment("SELECT *\n");
        sql.append("FROM ").append(getTinfoExperimentRun(), "er").append("\n");
        sql.append("WHERE RowId IN (\n");
        sql.append(runIdsSQL);
        sql.append("\n)");

        return new SqlSelector(getExpSchema(), sql).getArrayList(ExperimentRun.class);
    }

    /**
     * Generate a query to get the runIds where the supplied set of material rowIds were used as inputs
     * @param materialRowIdSQL -- SQL clause generating material rowIds used to limit results
     * @return Query to retrieve set of runIds from supplied input material ids
     */
    private SQLFragment getTargetRunIdsFromMaterialIds(SQLFragment materialRowIdSQL)
    {
        return getTargetRunIdsFromInputRowIds(materialRowIdSQL, getTinfoMaterialInput(), "MaterialID");
    }

    /**
     * Generate a query to get the runIds where the supplied set of material rowIds were used as inputs
     * @param rowIdSQL -- SQL clause generating material rowIds used to limit results
     * @return Query to retrieve set of runIds from supplied input material ids
     */
    private SQLFragment getTargetRunIdsFromDataIds(SQLFragment rowIdSQL)
    {
        return getTargetRunIdsFromInputRowIds(rowIdSQL, getTinfoDataInput(), "DataID");
    }

    /**
     * Generate a query to get the runIds where the supplied set of material rowIds were used as inputs
     * @param rowIdSQL -- SQL clause generating rowIds used to limit results
     * @return Query to retrieve set of runIds from supplied input material ids
     */
    private SQLFragment getTargetRunIdsFromInputRowIds(SQLFragment rowIdSQL, TableInfo inputTable, String idColumn)
    {
        // ex SQL:
        /*
            SELECT pa.RunId
            FROM exp.protocolapplication pa,
                exp.materialinput mi
            WHERE mi.TargetApplicationId = pa.RowId
                AND pa.cpastype = 'ExperimentRun'  --Limit protocolapplications, where materials are inputs
                AND mi.MaterialID <materialRowIdSQL>
        */

        SQLFragment sql = new SQLFragment();

        sql.append("SELECT pa.RunId\n");
        sql.append("FROM ").append(getTinfoProtocolApplication(), "pa").append(",\n\t");
        sql.append(inputTable, "i").append("\n");
        sql.append("WHERE i.TargetApplicationId = pa.RowId ")
                .append("AND pa.cpastype = ?\n").add(ExperimentRun.name())
                .append("AND i.").append(idColumn).append(" ").append(rowIdSQL);

        return sql;
    }

    /**
     * Get query to obtain the runIds that created the materials requested by parameter
     * @param materialRowIdSQL -- SQL clause generating material rowIds used to limit results
     * @return Query to retrieve precursor runs based on supplied material ids.
     */
    private SQLFragment getSourceRunIdsFromMaterialIds(@NotNull SQLFragment materialRowIdSQL)
    {
        // ex SQL:
        /*
            SELECT pa.RunId
            FROM exp.protocolapplication pa,
                exp.material m
            WHERE m.SourceApplicationId = pa.RowId
                AND m.rowId <materialRowIdSQL>
        */

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT pa.RunId\n");
        sql.append("FROM ").append(getTinfoProtocolApplication(), "pa").append(",\n\t");
        sql.append(getTinfoMaterial(), "m").append("\n");
        sql.append("WHERE m.SourceApplicationId = pa.RowId AND m.RowId ").append(materialRowIdSQL);

        return sql;
    }

    void deleteDomainObjects(Container c, String lsid) throws ExperimentException
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
     * Delete all exp.Data from the DataClass.  If container is not provided,
     * all rows from the DataClass will be deleted regardless of container.
     */
    public int truncateDataClass(ExpDataClassImpl dataClass, User user, @Nullable Container c)
    {
        assert getExpSchema().getScope().isTransactionActive();

        truncateDataClassAttachments(dataClass);

        SimpleFilter filter = c == null ? new SimpleFilter() : SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("classId"), dataClass.getRowId());

        MultiValuedMap<String, Integer> byContainer = new ArrayListValuedHashMap<>();
        TableSelector ts = new TableSelector(getTinfoData(), Sets.newCaseInsensitiveHashSet("container", "rowid"), filter, null);
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

    public void deleteDataClass(int rowId, Container c, User user, @Nullable final String auditUserComment) throws ExperimentException
    {
        ExpDataClassImpl dataClass = getDataClass(rowId);
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
        if (!dcContainer.hasPermission(user, AdminPermission.class))
        {
            if (dataClass.hasData())
                throw new UnauthorizedException("You do not have sufficient permissions to delete this data class.");
        }

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            truncateDataClass(dataClass, user, null);

            d.delete(user, auditUserComment);

            deleteDomainObjects(dcContainer, dataClass.getLSID());

            SqlExecutor executor = new SqlExecutor(getExpSchema());
            executor.execute("UPDATE " + getTinfoProtocolInput() + " SET dataClassId = NULL WHERE dataClassId = ?", rowId);
            executor.execute("DELETE FROM " + getTinfoDataClass() + " WHERE RowId = ?", rowId);

            removeDataTypeExclusion(Collections.singleton(rowId), DataTypeForExclusion.DataClass);

            transaction.addCommitTask(() -> clearDataClassCache(dcContainer), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
            transaction.commit();
        }

        // Delete sequences (genId and the unique counters)
        DbSequenceManager.deleteLike(c, ExpDataClassImpl.SEQUENCE_PREFIX, dataClass.getRowId(), getExpSchema().getSqlDialect());

        QueryService.get().fireQueryDeleted(user, c, null, ExpSchema.SCHEMA_EXP_DATA, singleton(dataClass.getName()));

        // remove DataClass from search index
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            try (Timing ignored = MiniProfiler.step("search docs"))
            {
                ss.deleteResource(dataClass.getDocumentId());
            }
        }
    }

    private void deleteUnusedAliases()
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

    private void truncateDataClassAttachments(ExpDataClassImpl dataClass)
    {
        if (dataClass.getDomain() != null)
        {
            TableInfo table = dataClass.getTinfo();

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

    public void deleteDataClassAttachments(Container container, List<String> lsids)
    {
        List<AttachmentParent> attachmentParents = new ArrayList<>();

        for (String lsidStr : lsids)
        {
            if (null == lsidStr)
                continue;
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
        List<ExpProtocolApplicationImpl> protocolSteps = getExpProtocolApplicationsForRun(runId);
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

        List<ExpMaterialImpl> materials = getExpMaterialsForRun(runId);
        final Map<Integer, ExpMaterialImpl> runMaterialMap = new HashMap<>(materials.size());

        for (ExpMaterialImpl mat : materials)
        {
            runMaterialMap.put(mat.getRowId(), mat);
            Integer srcAppId = mat.getDataObject().getSourceApplicationId();
            ExpProtocolApplicationImpl protApp = resolveProtApp(expRun, protStepMap, srcAppId);
            protApp.getOutputMaterials().add(mat);
            mat.markAsPopulated(protApp);
        }

        List<ExpDataImpl> datas = getExpDatasForRun(runId);
        final Map<Integer, ExpDataImpl> runDataMap = new HashMap<>(datas.size());

        for (ExpDataImpl dat : datas)
        {
            runDataMap.put(dat.getRowId(), dat);
            Integer srcAppId = dat.getDataObject().getSourceApplicationId();
            ExpProtocolApplicationImpl protApp = resolveProtApp(expRun, protStepMap, srcAppId);
            protApp.getOutputDatas().add(dat);
            dat.markAsPopulated(protApp);
        }

        // get the set of starting materials, which do not belong to the run
        String materialSQL = "SELECT M.* "
                + " FROM " + getTinfoMaterial().getSelectName() + " M "
                + " INNER JOIN " + getExpSchema().getTable("MaterialInput").getSelectName() + " MI "
                + " ON (M.RowId = MI.MaterialId) "
                + " WHERE MI.TargetApplicationId IN "
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA "
                + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExpProtocol.ApplicationType.ExperimentRun + "')"
                + "  ORDER BY RowId";
        String materialInputSQL = "SELECT MI.* "
                + " FROM " + getExpSchema().getTable("MaterialInput").getSelectName() + " MI "
                + " WHERE MI.TargetApplicationId IN "
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA "
                + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExpProtocol.ApplicationType.ExperimentRun + "')"
                + "  ORDER BY MI.MaterialId";

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
                + "  ORDER BY RowId ";
        String dataInputSQL = "SELECT DI.*"
                + " FROM " + getTinfoDataInput().getSelectName() + " DI "
                + " WHERE DI.TargetApplicationId IN "
                + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getSelectName() + " PA "
                + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExpProtocol.ApplicationType.ExperimentRun + "')"
                + "  ORDER BY DataId";

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
                int successorRunId = materialOutputRS.getInt("RunId");
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
                        + " ORDER BY TargetApplicationId, DataId";

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

    @NotNull
    private ExpProtocolApplicationImpl resolveProtApp(ExpRunImpl expRun, Map<Integer, ExpProtocolApplicationImpl> protStepMap, Integer srcAppId)
    {
        ExpProtocolApplicationImpl protApp = protStepMap.get(srcAppId);
        if (protApp == null)
        {
            LOG.warn("Could not find cached protocol application " + srcAppId + " when populating run " + expRun.getRowId() + " in " + expRun.getContainer().getPath() + ", attempting to fetch");
            if (srcAppId != null)
            {
                protApp = getExpProtocolApplication(srcAppId);
            }
        }
        if (protApp == null)
        {
            throw new IllegalStateException("Could not find protocol application " + srcAppId + " when populating run " + expRun.getRowId() + " in " + expRun.getContainer().getPath());
        }
        return protApp;
    }

    public List<ProtocolActionPredecessor> getProtocolActionPredecessors(String parentProtocolLSID, String childProtocolLSID)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ChildProtocolLSID"), childProtocolLSID);
        filter.addCondition(FieldKey.fromParts("ParentProtocolLSID"), parentProtocolLSID);
        return new TableSelector(getTinfoProtocolActionPredecessorLSIDView(), filter, new Sort("+PredecessorSequence")).getArrayList(ProtocolActionPredecessor.class);
    }

    public List<Data> getOutputDataForApplication(int applicationId)
    {
        return getDatas(new SimpleFilter(FieldKey.fromParts("SourceApplicationId"), applicationId), null);
    }

    public List<DataInput> getDataOutputsForApplication(int applicationId)
    {
        return new SqlSelector(getExpSchema(), "SELECT di.* FROM exp.DataInput di\n" +
                "INNER JOIN exp.Data d ON d.rowId = di.dataId\n" +
                "WHERE d.sourceApplicationId = ?", applicationId).getArrayList(DataInput.class);
    }

    public List<Material> getOutputMaterialForApplication(int applicationId)
    {
        return getMaterials(new SimpleFilter(FieldKey.fromParts("SourceApplicationId"), applicationId), null);
    }

    public List<MaterialInput> getMaterialOutputsForApplication(int applicationId)
    {
        return new SqlSelector(getExpSchema(), "SELECT mi.* FROM exp.MaterialInput mi\n" +
                "INNER JOIN exp.Material m ON m.rowId = mi.materialId\n" +
                "WHERE m.sourceApplicationId = ?", applicationId).getArrayList(MaterialInput.class);
    }

    @Override
    public List<ExpDataImpl> getExpData(Container c)
    {
        return getExpDatas(SimpleFilter.createContainerFilter(c));
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

    @Override
    public List<ProtocolApplicationParameter> getProtocolApplicationParameters(int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProtocolApplicationId"), rowId);
        return new TableSelector(getTinfoProtocolApplicationParameter(), filter, null).getArrayList(ProtocolApplicationParameter.class);
    }

    public ProtocolActionStepDetail getProtocolActionStepDetail(String parentProtocolLSID, Integer actionSequence)
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

    @Override
    public List<ExpProtocolApplicationImpl> getExpProtocolApplicationsForProtocolLSID(String protocolLSID)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProtocolLSID"), protocolLSID);
        return ExpProtocolApplicationImpl.fromProtocolApplications(new TableSelector(getTinfoProtocolApplication(), filter, null).getArrayList(ProtocolApplication.class));
    }

    public Protocol saveProtocol(User user, Protocol protocol)
    {
        return saveProtocol(user, protocol, true, null);
    }

    // saveProperties is exposed due to how the transactions are handled for setting properties on protocols.
    // If a protocol has already had protocol.setProperty() called on it then the properties will have already
    // been saved to the database. The result is that it can cause the save to fail if this API attempts to save
    // the properties again. The only current recourse is for the caller to enforce their own transaction boundaries
    // using ensureTransaction().
    public Protocol saveProtocol(
        User user,
        Protocol protocol,
        boolean saveProperties,
        @Nullable Collection<? extends ExpProtocolInput> protocolInputsToDeleteOnUpdate
    )
    {
        Protocol result;
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            boolean newProtocol = protocol.getRowId() == 0;
            if (newProtocol)
            {
                // if protocol exist, throw error
                ExpProtocol existing = getExpProtocol(protocol.getContainer(), protocol.getName());
                if (existing != null && protocol.getLSIDNamespacePrefix().equals("GeneralAssayProtocol"))
                    throw new RuntimeSQLException(new SQLException("Assay design with name '" + existing.getName() + "' already exists."));

                result = Table.insert(user, getTinfoProtocol(), protocol);
            }
            else
            {
                result = Table.update(user, getTinfoProtocol(), protocol, protocol.getRowId());
            }

            uncacheProtocol(protocol);

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

            Collection<? extends ExpProtocolInputImpl> protocolInputs = protocol.retrieveProtocolInputs();
            if (!newProtocol)
            {
                if (null == protocolInputsToDeleteOnUpdate)
                    deleteAllProtocolInputs(protocol.getContainer(), String.valueOf(protocol.getRowId()));
                else
                    deleteProtocolInputs(protocol, protocolInputsToDeleteOnUpdate);
            }

            for (ExpProtocolInputImpl input : protocolInputs)
            {
                AbstractProtocolInput obj = (AbstractProtocolInput)input.getDataObject();
                obj.setProtocolId(result.getRowId());
                input.setProtocol(null);
                input.save(user);
            }

            AssayService assayService = AssayService.get();
            if (assayService != null)
            {
                assayService.clearProtocolCache();

                // Be sure that we clear the cache after we commit the overall transaction, in case it
                // gets repopulated by another thread before then
                getExpSchema().getScope().addCommitTask(assayService::clearProtocolCache, POSTCOMMIT, POSTROLLBACK);
            }
            else
            {
                LOG.info("Skipping clear of protocol cache: Assay service not available.");
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        indexAssay(result);

        return result;
    }

    private void indexAssay(Protocol protocol)
    {
        if (null == protocol)
            return;

        AssayService assayService = AssayService.get();
        SearchService ss = SearchService.get();

        if (assayService != null && ss != null)
        {
            SearchService.IndexTask task = ss.defaultTask();
            Runnable runEnumerate = () -> assayService.indexAssay(task, protocol.getContainer(), new ExpProtocolImpl(protocol));
            task.addRunnable(runEnumerate, SearchService.PRIORITY.item);
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
        if (propMap.isEmpty())
            return;
        ObjectProperty[] props = propMap.values().toArray(new ObjectProperty[0]);
        // Todo - make this more efficient - don't delete all the old ones if they're the same
        if (clearExisting)
        {
            OntologyManager.deleteOntologyObjects(container, ownerLSID);
            for (ObjectProperty prop : propMap.values())
            {
                prop.setObjectId(0);
            }
        }
        try
        {
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

    public void insertProtocolPredecessor(User user, int actionRowId, int predecessorRowId)
    {
        Map<String, Object> mValsPredecessor = new HashMap<>();
        mValsPredecessor.put("ActionId", actionRowId);
        mValsPredecessor.put("PredecessorId", predecessorRowId);

        Table.insert(user, getTinfoProtocolActionPredecessor(), mValsPredecessor);
    }

    @Override
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
    private @NotNull List<ExpData> ensureSimpleExperimentRunParameters(
        Collection<? extends ExpMaterial> inputMaterials,
        Collection<? extends ExpData> inputDatas,
        Collection<ExpMaterial> outputMaterials,
        Collection<ExpData> outputDatas,
        Collection<ExpData> transformedDatas,
        User user
    )
    {
        // Save all the input and output objects to make sure they've been inserted
        try
        {
            saveAll(inputMaterials, user);
            saveAll(inputDatas, user);
            saveAll(outputMaterials, user);
            saveAll(outputDatas, user);
            saveAll(transformedDatas, user);
        }
        catch (BatchValidationException e)
        {
            // None of these types actually throw the exception on save
            throw new UnexpectedException(e);
        }

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

    private void saveAll(Iterable<? extends ExpObject> objects, User user) throws BatchValidationException
    {
        for (ExpObject object : objects)
        {
            object.save(user);
        }
    }

    @Override
    public ExpRun saveSimpleExperimentRun(
        ExpRun run,
        Map<? extends ExpMaterial, String> inputMaterials,
        Map<? extends ExpData, String> inputDatas,
        Map<ExpMaterial, String> outputMaterials,
        Map<ExpData, String> outputDatas,
        Map<ExpData, String> transformedDatas,
        ViewBackgroundInfo info,
        Logger log,
        boolean loadDataFiles
    ) throws ExperimentException
    {
        return saveSimpleExperimentRun(run, inputMaterials, inputDatas, outputMaterials, outputDatas, transformedDatas, info, log, loadDataFiles, null, null);
    }

    @Override
    public ExpRun saveSimpleExperimentRun(
        ExpRun baseRun,
        Map<? extends ExpMaterial, String> inputMaterials,
        Map<? extends ExpData, String> inputDatas,
        Map<ExpMaterial, String> outputMaterials,
        Map<ExpData, String> outputDatas,
        Map<ExpData, String> transformedDatas,
        ViewBackgroundInfo info,
        @NotNull Logger log,
        boolean loadDataFiles,
        @Nullable Set<String> runInputLsids,
        @Nullable Set<Pair<String, String>> finalOutputLsids
    ) throws ExperimentException
    {
        ExpRunImpl run = (ExpRunImpl) baseRun;

        if (run.getFilePathRootPath() == null)
            throw new IllegalArgumentException("You must set the file path root on the experiment run");

        List<ExpData> insertedDatas;
        User user = info.getUser();
        XarContext context = new XarContext("Simple Run Creation", run.getContainer(), user);

        try (DbScope.Transaction transaction = getExpSchema().getScope().ensureTransaction(XAR_IMPORT_LOCK))
        {
            if (run.getContainer() == null)
                run.setContainer(info.getContainer());

            run.save(user);
            insertedDatas = ensureSimpleExperimentRunParameters(inputMaterials.keySet(), inputDatas.keySet(), outputMaterials.keySet(), outputDatas.keySet(), transformedDatas.keySet(), user);

            HashMap<ExpData, String> allOutputDatas = new HashMap<>();
            allOutputDatas.putAll(outputDatas);
            allOutputDatas.putAll(transformedDatas);

            ExpProtocolImpl parentProtocol = run.getProtocol();

            List<ExpProtocolActionImpl> actions = parentProtocol.getSteps();
            if (actions.size() != 3)
            {
                throw new IllegalArgumentException("Protocol has the wrong number of steps for a simple protocol; it should have three.");
            }
            ExpProtocolActionImpl action1 = actions.get(0);
            assert action1.getActionSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE;
            assert action1.getChildProtocol().getRowId() == parentProtocol.getRowId();

            context.addSubstitution("ExperimentRun.RowId", Integer.toString(run.getRowId()));

            Date date = new Date();

            ExpProtocolActionImpl action2 = actions.get(1);
            assert action2.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE;
            ExpProtocol protocol2 = action2.getChildProtocol();

            ExpProtocolActionImpl action3 = actions.get(2);
            assert action3.getActionSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE;
            ExpProtocol outputProtocol = action3.getChildProtocol();
            assert outputProtocol.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput : "Expected third protocol to be of type ExperimentRunOutput but was " + outputProtocol.getApplicationType();

            ExpProtocolApplicationImpl protApp1 = new ExpProtocolApplicationImpl(new ProtocolApplication());
            ExpProtocolApplicationImpl protApp2 = new ExpProtocolApplicationImpl(new ProtocolApplication());
            ExpProtocolApplicationImpl protApp3 = new ExpProtocolApplicationImpl(new ProtocolApplication());

            for (ExpProtocolApplicationImpl existingProtApp : run.getProtocolApplications())
            {
                if (existingProtApp.getProtocol().equals(parentProtocol) && existingProtApp.getActionSequence() == action1.getActionSequence())
                {
                    protApp1 = existingProtApp;
                }
                else if (existingProtApp.getProtocol().equals(protocol2))
                {
                    if (existingProtApp.getActionSequence() == SIMPLE_PROTOCOL_EXTRA_STEP_SEQUENCE)
                    {
                        existingProtApp.delete(user);
                    }
                    else if (existingProtApp.getActionSequence() == action2.getActionSequence())
                    {
                        protApp2 = existingProtApp;
                    }
                    else
                    {
                        throw new IllegalStateException("Unexpected existing protocol application: " + existingProtApp.getLSID() + " with sequence " + existingProtApp.getActionSequence());
                    }
                }
                else if (existingProtApp.getProtocol().equals(outputProtocol) && existingProtApp.getActionSequence() == action3.getActionSequence())
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

            if (null != runInputLsids)
            {
                protApp1.addProvenanceInput(runInputLsids);
            }

            addDataInputs(inputDatas, protApp1._object, user);
            addMaterialInputs(inputMaterials, protApp1._object, user);

            initializeProtocolApplication(protApp2, date, action2, run, protocol2, context);
            protApp2.save(user);
            addDataInputs(inputDatas, protApp2._object, user);
            addMaterialInputs(inputMaterials, protApp2._object, user);

            Set<String> inputDataTypes = new CaseInsensitiveHashSet();
            for (ExpData expData : inputDatas.keySet())
            {
                ExpDataClass dataClass = expData.getDataClass(user);
                if (dataClass != null)
                    inputDataTypes.add(ExpData.DATA_INPUT_PARENT + "/" + dataClass.getName());
            }

            for (ExpMaterial expMaterial : inputMaterials.keySet())
            {
                ExpSampleType sampleType = expMaterial.getSampleType();
                if (sampleType != null)
                    inputDataTypes.add(ExpMaterial.MATERIAL_INPUT_PARENT + "/" + sampleType.getName());
            }

            Set<String> requiredDataTypes = new CaseInsensitiveHashSet();
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

                try
                {
                    ExpSampleType sampleType = outputMaterial.getSampleType();
                    if (sampleType != null)
                        requiredDataTypes.addAll(sampleType.getRequiredImportAliases().values());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

                outputMaterial.setSourceApplication(protApp2);
                outputMaterial.setRun(run);
                Table.update(user, getTinfoMaterial(), ((ExpMaterialImpl)outputMaterial)._object, outputMaterial.getRowId());
            }

            for (ExpData outputData : allOutputDatas.keySet())
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

                try
                {
                    ExpDataClass dataClass = outputData.getDataClass(user);
                    if (dataClass != null)
                        requiredDataTypes.addAll(dataClass.getRequiredImportAliases().values());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

                outputData.setSourceApplication(protApp2);
                outputData.setRun(run);
                Table.update(user, getTinfoData(), ((ExpDataImpl)outputData).getDataObject(), outputData.getRowId());
            }

            boolean hasMissingRequiredParent = false;
            for (String required : requiredDataTypes)
            {
                if (!inputDataTypes.contains(required))
                {
                    hasMissingRequiredParent = true;
                    break;
                }
            }
            if (hasMissingRequiredParent)
                throw new ExperimentException("Inputs are required: " + String.join(",", requiredDataTypes));

            initializeProtocolApplication(protApp3, date, action3, run, outputProtocol, context);
            protApp3.save(user);

            if (null != finalOutputLsids && !finalOutputLsids.isEmpty())
            {
                protApp3.addProvenanceMapping(finalOutputLsids);
            }

            addDataInputs(allOutputDatas, protApp3._object, user);
            addMaterialInputs(outputMaterials, protApp3._object, user);

            transaction.commit();
        }
        catch (BatchValidationException e)
        {
            throw new ExperimentException(e);
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

    private void initializeProtocolApplication(
        ExpProtocolApplication protApp,
        Date activityDate,
        ExpProtocolActionImpl action,
        ExpRun run,
        ExpProtocol parentProtocol,
        XarContext context
    ) throws XarFormatException
    {
        protApp.setActivityDate(activityDate);
        protApp.setActionSequence(action.getActionSequence());
        protApp.setRun(run);
        protApp.setProtocol(parentProtocol);
        Map<String, ProtocolParameter> parentParams = parentProtocol.getProtocolParameters();
        ProtocolParameter parentLSIDTemplateParam = parentParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
        ProtocolParameter parentNameTemplateParam = parentParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
        assert parentLSIDTemplateParam != null : "Parent LSID Template was null";
        assert parentNameTemplateParam != null : "Parent Name Template was null";
        protApp.setLSID(LsidUtils.resolveLsidFromTemplate(parentLSIDTemplateParam.getStringValue(), context, ExpProtocolApplication.DEFAULT_CPAS_TYPE));
        protApp.setName(parentNameTemplateParam.getStringValue());
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
    public ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException, ValidationException
    {
        return derive(inputMaterials, Collections.emptyMap(), outputMaterials, Collections.emptyMap(), info, log);
    }

    private void _prepareRun(DeriveSamplesBulkHelper helper, ExpRunImpl run, SimpleRunRecord runRecord, User user, Date date)
    {
        helper.addRunParams(run._object, user.getUserId());

        // protocol applications
        ExpProtocolImpl parentProtocol = run.getProtocol();

        List<ExpProtocolActionImpl> actions = parentProtocol.getSteps();
        if (actions.size() != 3)
        {
            throw new IllegalArgumentException("Protocol has the wrong number of steps for a simple protocol; it should have three.");
        }
        ExpProtocolActionImpl action1 = actions.get(0);
        assert action1.getActionSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE;
        assert action1.getChildProtocol().getRowId() == parentProtocol.getRowId();

        ExpProtocolActionImpl action2 = actions.get(1);
        assert action2.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE;
        ExpProtocol protocol2 = action2.getChildProtocol();

        ExpProtocolActionImpl action3 = actions.get(2);
        assert action3.getActionSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE;
        ExpProtocol outputProtocol = action3.getChildProtocol();
        assert outputProtocol.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput : "Expected third protocol to be of type ExperimentRunOutput but was " + outputProtocol.getApplicationType();

        ExpProtocolApplicationImpl protApp1 = new ExpProtocolApplicationImpl(new ProtocolApplication());
        ExpProtocolApplicationImpl protApp2 = new ExpProtocolApplicationImpl(new ProtocolApplication());
        ExpProtocolApplicationImpl protApp3 = new ExpProtocolApplicationImpl(new ProtocolApplication());

        helper.addProtocolApp(protApp1, date, action1, parentProtocol, run, runRecord);

        helper.addProtocolApp(protApp2, date, action2, protocol2, run, runRecord);

        helper.addProtocolApp(protApp3, date, action3, outputProtocol, run, runRecord);
    }

    @Override
    public void deriveSamplesBulk(List<? extends SimpleRunRecord> runRecords, ViewBackgroundInfo info, Logger log) throws ExperimentException, ValidationException
    {
        final int MAX_RUNS_IN_BATCH = 1000;
        int countD = 0;
        int countA = 0;

        User user = info.getUser();
        XarContext context = new XarContext("Simple Run Creation", info.getContainer(), user);
        Date date = new Date();
        DeriveSamplesBulkHelper derivationHelper = new DeriveSamplesBulkHelper(info.getContainer(), context, false);
        DeriveSamplesBulkHelper aliquotHelper = new DeriveSamplesBulkHelper(info.getContainer(), context, true);

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            for (SimpleRunRecord runRecord : runRecords)
            {
                if (!runRecord.getInputDataMap().isEmpty() || !runRecord.getInputMaterialMap().isEmpty())
                {
                    countD++;
                    ExpRunImpl run = createRun(runRecord.getInputMaterialMap(), runRecord.getInputDataMap(), runRecord.getOutputMaterialMap(), runRecord.getOutputDataMap(), info);
                    _prepareRun(derivationHelper, run, runRecord, user, date);
                    if ((countD % MAX_RUNS_IN_BATCH) == 0)
                    {
                        derivationHelper.saveBatch();
                    }
                }
                if (runRecord.getAliquotInput() != null)
                {
                    countA++;
                    ExpRunImpl run = createAliquotRun(runRecord.getAliquotInput(), runRecord.getAliquotOutputs(), info);
                    _prepareRun(aliquotHelper, run, runRecord, user, date);
                    if ((countA % MAX_RUNS_IN_BATCH) == 0)
                    {
                        aliquotHelper.saveBatch();
                    }
                }
            }

            // process the rest of the list
            if (!derivationHelper.isEmpty())
            {
                derivationHelper.saveBatch();
            }
            if (!aliquotHelper.isEmpty())
            {
                aliquotHelper.saveBatch();
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
        private final Container _container;
        private final XarContext _context;
        private final boolean _isAliquot;

        private List<List<?>> _runParams;
        private List<List<?>> _protAppParams;
        private List<ProtocolAppRecord> _protAppRecords;
        private List<List<?>> _materialInputParams;
        private List<List<?>> _dataInputParams;

        private List<List<?>> _aliquotInputParams;

        private Map<String, Integer> _aliquotRootCache;

        public DeriveSamplesBulkHelper(Container container, XarContext context, boolean isAliquot)
        {
            _container = container;
            _context = context;
            _isAliquot = isAliquot;
            _aliquotRootCache = new HashMap<>();
            resetState();
        }

        private void resetState()
        {
            _runParams = new ArrayList<>();
            _protAppParams = new ArrayList<>();
            _protAppRecords = new ArrayList<>();
            _materialInputParams = new ArrayList<>();
            _dataInputParams = new ArrayList<>();

            _aliquotInputParams = new ArrayList<>();
            _aliquotRootCache = new HashMap<>();
        }

        public void addRunParams(ExperimentRun run, int userId)
        {
            Timestamp ts = new Timestamp(System.currentTimeMillis());
            _runParams.add(Arrays.asList(
                    run.getLSID(),
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

        public void addProtocolApp(ExpProtocolApplicationImpl protApp, Date activityDate, ExpProtocolActionImpl action, ExpProtocol protocol,
                                   ExpRun run, SimpleRunRecord runRecord)
        {
            _protAppRecords.add(new ProtocolAppRecord(protApp, activityDate, action, protocol, run, runRecord));
        }

        public void saveBatch() throws SQLException, XarFormatException, ValidationException
        {
            // insert into the experimentrun table
            Map<String, ExperimentRun> runLsidToRowId = saveExpRunsBatch(_container, _runParams);

            // insert into the protocolapplication table
            createProtocolAppParams(_protAppRecords, _protAppParams, _context, runLsidToRowId);
            saveExpProtocolApplicationBatch(_protAppParams);

            // insert into the materialinput table

            if (!_isAliquot)
            {
                createMaterialInputParams(_protAppRecords, _materialInputParams);
                saveExpMaterialInputBatch(_materialInputParams);
                saveExpMaterialOutputs(_protAppRecords);

                createDataInputParams(_protAppRecords, _dataInputParams);
                saveExpDataInputBatch(_dataInputParams);
                saveExpDataOutputs(_protAppRecords);
            }
            else
            {
                createAliquotInputParams(_protAppRecords, _aliquotInputParams);
                saveExpMaterialInputBatch(_aliquotInputParams);
                initAliquotRootsCache(_protAppRecords);
                saveExpMaterialAliquotOutputs(_protAppRecords);
            }

            // clear the stored records
            resetState();

            Map<String, Integer> cpasTypeToObjectId = new HashMap<>();
            for (var er : runLsidToRowId.values())
            {
                new SyncRunEdges(er.getExpObject())
                        .deleteFirst(false)
                        .doIncrementalClosureInvalidation(false) // do this update all at once below
                        .verifyEdgesNoInsert(false)
                        .sync(cpasTypeToObjectId);
            }
            ClosureQueryHelper.recomputeAncestorsForRuns(runLsidToRowId.values().stream().map(IdentifiableEntity::getRowId).toList());
        }

        private void initAliquotRootsCache(List<ProtocolAppRecord> protAppRecords)
        {
            Set<String> parentLSIDS = new HashSet<>();

            for (ProtocolAppRecord rec : protAppRecords)
            {
                if (rec._runRecord.getAliquotInput() != null)
                    parentLSIDS.add(rec._runRecord.getAliquotInput().getName());
            }

            TableInfo table = getTinfoMaterial();
            SQLFragment sqlfilter = new SimpleFilter(FieldKey.fromParts("Lsid"), parentLSIDS, IN).getSQLFragment(table, "m");

            new SqlSelector(table.getSchema(), new SQLFragment("SELECT Lsid, RootMaterialRowId FROM " + table + " ")
                    .append(sqlfilter).append(" AND RootMaterialRowId <> RowId")).forEach(rs ->
            {
                _aliquotRootCache.put(rs.getString("Lsid"), rs.getInt("RootMaterialRowId"));
            });
        }

        public boolean isEmpty()
        {
            return _runParams.isEmpty();
        }

        private Map<String, ExperimentRun> saveExpRunsBatch(Container c, List<List<?>> params) throws SQLException
        {
            // insert into exp.object
            List<List<?>> expObjectParams = params.stream().map(
                    runParams -> List.of(/* LSID */ runParams.get(0), c.getId())
            ).collect(toList());
            StringBuilder expObjectSql = new StringBuilder("INSERT INTO ").append(OntologyManager.getTinfoObject())
                    .append(" (ObjectUri, Container) VALUES (?, ?)");
            Table.batchExecute(getExpSchema(), expObjectSql.toString(), expObjectParams);

            StringBuilder sql = new StringBuilder("INSERT INTO ").append(getTinfoExperimentRun().toString()).
                    append(" (Lsid, ObjectId, Name, ProtocolLsid, FilePathRoot, EntityId, Created, CreatedBy, Modified, ModifiedBy, Container) " +
                            "VALUES (?,(select objectid from exp.object where objecturi = ?),?,?,?,?,?,?,?,?, '").
                    append(c.getId()).append("')");

            Table.batchExecute(getExpSchema(), sql.toString(), params);

            List<String> runLsids = params.stream().map(p -> (String) p.get(0)).toList();
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpExperimentTable.Column.LSID.name()), runLsids, IN);
            Map<String, ExperimentRun> ret = new CaseInsensitiveHashMap<>();
            getExperimentRuns(filter).forEach(er -> ret.put(er.getLSID(), er));
            return ret;
        }

        /**
         * Replace the placeholder run id with the actual run id
         */
        private void createProtocolAppParams(List<ProtocolAppRecord> protAppRecords, List<List<?>> protAppParams, XarContext context, Map<String, ExperimentRun> runLsidToRowId) throws XarFormatException
        {
            for (ProtocolAppRecord rec : protAppRecords)
            {
                assert runLsidToRowId.containsKey(rec._run.getLSID());

                Integer runId = runLsidToRowId.get(rec._run.getLSID()).getRowId();
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
                        rec._protApp._object.getLSID(),
                        rec._protApp.getEntityId() != null ? rec._protApp.getEntityId().toString() : GUID.makeGUID()));
            }
        }

        private void createMaterialInputParams(List<ProtocolAppRecord> protAppRecords, List<List<?>> materialInputParams)
        {
            // get the protocol application rows id's
            Map<String, Integer> protAppRowMap = new HashMap<>();

            for (ProtocolAppRecord rec : protAppRecords)
                protAppRowMap.put(rec._protApp.getLSID(), null);

            TableInfo pa = getTinfoProtocolApplication();
            SQLFragment sqlfilter = new SimpleFilter(FieldKey.fromParts("LSID"), protAppRowMap.keySet(), IN).getSQLFragment(pa, "pa");
            new SqlSelector(pa.getSchema(), new SQLFragment("SELECT Lsid, RowId FROM " + pa /* + (pa.getSqlDialect().isSqlServer() ? " WITH (UPDLOCK, HOLDLOCK)" : "") */ + " ")
                    .append(sqlfilter)).forEach(rs ->
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
                if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE ||
                        rec._action.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
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
                else if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE)
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

        private void createAliquotInputParams(List<ProtocolAppRecord> protAppRecords, List<List<?>> aliquotInputParams)
        {
            // get the protocol application rows id's
            Map<String, Integer> protAppRowMap = new HashMap<>();

            for (ProtocolAppRecord rec : protAppRecords)
                protAppRowMap.put(rec._protApp.getLSID(), null);

            TableInfo pa = getTinfoProtocolApplication();
            SQLFragment sqlfilter = new SimpleFilter(FieldKey.fromParts("LSID"), protAppRowMap.keySet(), IN).getSQLFragment(pa, "pa");
            new SqlSelector(pa.getSchema(), new SQLFragment("SELECT Lsid, RowId FROM " + pa + " ")
                    .append(sqlfilter)).forEach(rs ->
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
                if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE ||
                        rec._action.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
                {
                    ExpMaterial parent = rec._runRecord.getAliquotInput();
                    aliquotInputParams.add(Arrays.asList(
                            parent.getRowId(),
                            rowId,
                            parent.getSampleType().getName()));
                }
                // wire the output materials to the protocol input for the last action
                else if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE)
                {
                    for (ExpMaterial aliquot: rec._runRecord.getAliquotOutputs())
                    {
                        aliquotInputParams.add(Arrays.asList(
                                aliquot.getRowId(),
                                rowId,
                                aliquot.getSampleType().getName()));
                    }
                }
            }
        }

        private void createDataInputParams(List<ProtocolAppRecord> protAppRecords, List<List<?>> dataInputParams)
        {
            for (ProtocolAppRecord rec : protAppRecords)
            {
                if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE ||
                        rec._action.getActionSequence() == SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE)
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
                else if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE)
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

        private void saveExpMaterialAliquotOutputs(List<ProtocolAppRecord> protAppRecords) throws ValidationException
        {
            TableInfo tableInfo = getTinfoMaterial();
            for (ProtocolAppRecord rec : protAppRecords)
            {
                if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
                {
                    ExpMaterial parent = rec._runRecord.getAliquotInput();

                    // in the case when a sample, its aliquots, and subaliquots are imported/created together, the subaliquots's parent aliquot might not have AliquotedFromLSID yet.
                    // Use cache to double-check determine subaliquots's root
                    boolean isParentRootMaterial = StringUtils.isEmpty(parent.getAliquotedFromLSID()) && !_aliquotRootCache.containsKey(parent.getLSID());

                    for (ExpMaterial outputAliquot : rec._runRecord.getAliquotOutputs())
                    {
                        SQLFragment sql = new SQLFragment("UPDATE ").append(tableInfo, "").
                                append(" SET SourceApplicationId = ?, RunId = ?, RootMaterialRowId = ?, AliquotedFromLSID = ? WHERE RowId = ?");

                        Integer rootMaterialRowId = null;

                        if (isParentRootMaterial)
                        {
                            rootMaterialRowId = parent.getRowId();
                        }
                        else if (_aliquotRootCache.containsKey(parent.getLSID()))
                        {
                            rootMaterialRowId = _aliquotRootCache.get(parent.getLSID());
                        }

                        if (rootMaterialRowId == null)
                        {
                            rootMaterialRowId = parent.getRootMaterialRowId();
                            if (rootMaterialRowId == null)
                                throw new ValidationException("Unable to find aliquot parent");
                        }

                        _aliquotRootCache.put(outputAliquot.getLSID(), rootMaterialRowId); // add self's root to cache

                        sql.addAll(rec._protApp.getRowId(), rec._protApp._object.getRunId(), rootMaterialRowId, parent.getLSID(), outputAliquot.getRowId());
                        
                        new SqlExecutor(tableInfo.getSchema()).execute(sql);
                    }
                }
            }
        }
        
        private void saveExpMaterialOutputs(List<ProtocolAppRecord> protAppRecords)
        {
            for (ProtocolAppRecord rec : protAppRecords)
            {
                if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
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
                if (rec._action.getActionSequence() == SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
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
                String sql = "INSERT INTO " + getTinfoMaterialInput().toString() +
                        " (MaterialId, TargetApplicationId, Role)" +
                        " VALUES (?,?,?)";
                Table.batchExecute(getExpSchema(), sql, params);
            }
        }

        private void saveExpProtocolApplicationBatch(List<List<?>> params) throws SQLException
        {
            if (!params.isEmpty())
            {
                String sql = "INSERT INTO " + getTinfoProtocolApplication().toString() +
                        " (Name, CpasType, ProtocolLsid, ActivityDate, RunId, ActionSequence, Lsid, EntityId)" +
                        " VALUES (?,?,?,?,?,?,?,?)";
                Table.batchExecute(getExpSchema(), sql, params);
            }
        }

        private void saveExpDataInputBatch(List<List<?>> params) throws SQLException
        {
            if (!params.isEmpty())
            {
                String sql = "INSERT INTO " + getTinfoDataInput().toString() +
                        " (Role, DataId, TargetApplicationId)" +
                        " VALUES (?,?,?)";
                Table.batchExecute(getExpSchema(), sql, params);
            }
        }

        private class ProtocolAppRecord
        {
            ExpProtocolApplicationImpl _protApp;
            Date _activityDate;
            ExpProtocolActionImpl _action;
            ExpProtocol _protocol;
            ExpRun _run;
            SimpleRunRecord _runRecord;

            public ProtocolAppRecord(ExpProtocolApplicationImpl protApp, Date activityDate, ExpProtocolActionImpl action, ExpProtocol protocol,
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
    public ExpRun derive(@NotNull Map<? extends ExpMaterial, String> inputMaterials, @NotNull Map<? extends ExpData, String> inputDatas,
                                @NotNull Map<ExpMaterial, String> outputMaterials, @NotNull Map<ExpData, String> outputDatas,
                                @NotNull ViewBackgroundInfo info, @NotNull Logger log)
            throws ExperimentException, ValidationException
    {
        ExpRun run = createRun(inputMaterials, inputDatas, outputMaterials, outputDatas,info);
        return saveSimpleExperimentRun(run, inputMaterials, inputDatas, outputMaterials, outputDatas,
                Collections.emptyMap(), info, log, false);
    }

    private ExpRunImpl createRun(Map<? extends ExpMaterial, String> inputMaterials, Map<? extends ExpData, String> inputDatas,
                         Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, ViewBackgroundInfo info) throws ExperimentException, ValidationException
    {
        User user = info.getUser();
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());
        if (pipeRoot == null || !pipeRoot.isValid())
            throw new ValidationException("The child folder, " + info.getContainer().getPath() + ", must have a valid pipeline root.");

        if (outputDatas.isEmpty() && outputMaterials.isEmpty())
            throw new ValidationException("You must derive at least one child data object or sample.");

        if (inputDatas.isEmpty() && inputMaterials.isEmpty())
            throw new ValidationException("You must derive from at least one parent data object or sample.");

        for (ExpData expData : inputDatas.keySet())
        {
            if (outputDatas.containsKey(expData))
                throw new ValidationException("The data object " + expData.getName() + " cannot be an input to its own derivation.");
        }

        for (ExpMaterial expMaterial : inputMaterials.keySet())
        {
            if (outputMaterials.containsKey(expMaterial))
                throw new ValidationException("The sample " + expMaterial.getName() + " cannot be an input to its own derivation.");
        }

        ExpProtocol protocol = ensureSampleDerivationProtocol(info.getUser());
        ExpRunImpl run = createExperimentRun(info.getContainer(), getDerivationRunName(inputMaterials, inputDatas, outputMaterials.size(), outputDatas.size()));
        run.setProtocol(protocol);
        run.setFilePathRoot(pipeRoot.getRootPath());

        return run;
    }

    public static String getDerivationRunName(Map<? extends ExpMaterial, String> inputMaterials, Map<? extends ExpData, String> inputDatas,
                                       int numMaterialOutputs, int numDataOutputs)
    {
        StringBuilder name = new StringBuilder("Derive ");
        if (numDataOutputs <= 0)
        {
            if (numMaterialOutputs == 1)
                name.append("sample ");
            else
                name.append(numMaterialOutputs).append(" samples ");
        }
        else if (numMaterialOutputs <= 0)
        {
            if (numDataOutputs == 1)
                name.append("data ");
            else
                name.append(numDataOutputs).append(" data ");
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
        return name.toString();
    }

    public ExpRunImpl createAliquotRun(ExpMaterial parent, Collection<ExpMaterial> aliquots, ViewBackgroundInfo info) throws ExperimentException, ValidationException
    {
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());
        if (pipeRoot == null || !pipeRoot.isValid())
            throw new ValidationException("The child folder, " + info.getContainer().getPath() + ", must have a valid pipeline root");

        if (aliquots == null || aliquots.isEmpty())
            throw new ValidationException("You must create at least one aliquot.");

        if (parent == null)
            throw new ValidationException("You must create aliquot from a parent sample or aliquot");

        if (aliquots.contains(parent))
            throw new ValidationException("The sample " + parent.getName() + " cannot be its own aliquot.");

        ExpProtocol protocol = ensureSampleAliquotProtocol(info.getUser());
        ExpRunImpl run = createExperimentRun(info.getContainer(), getAliquotRunName(parent, aliquots.size()));
        run.setProtocol(protocol);
        run.setFilePathRoot(pipeRoot.getRootPath());

        return run;
    }

    public static String getAliquotRunName(ExpMaterial parent, int numAliquots)
    {
        StringBuilder name = new StringBuilder("Create ");
        if (numAliquots == 1)
            name.append("aliquot ");
        else
            name.append(numAliquots).append(" aliquots ");
        name.append("from ");
        name.append(parent.getName());
        return name.toString();
    }

    @Override
    public ExpProtocol ensureSampleAliquotProtocol(User user) throws ExperimentException
    {
        ExpProtocol protocol = getExpProtocol(SAMPLE_ALIQUOT_PROTOCOL_LSID);
        if (protocol == null)
        {
            ExpProtocolImpl baseProtocol = createExpProtocol(ContainerManager.getSharedContainer(), ExpProtocol.ApplicationType.ExperimentRun, SAMPLE_ALIQUOT_PROTOCOL_NAME);
            baseProtocol.setLSID(SAMPLE_ALIQUOT_PROTOCOL_LSID);
            baseProtocol.setMaxInputDataPerInstance(0);
            baseProtocol.setProtocolDescription("Simple protocol for creating aliquots or subaliquots from the original sample or aliquots.");
            return insertSimpleProtocol(baseProtocol, user);
        }
        return protocol;
    }

    @Override
    public ExpProtocol ensureSampleDerivationProtocol(User user) throws ExperimentException
    {
        ExpProtocol protocol = getExpProtocol(SAMPLE_DERIVATION_PROTOCOL_LSID);
        if (protocol == null)
        {
            ExpProtocolImpl baseProtocol = createExpProtocol(ContainerManager.getSharedContainer(), ExpProtocol.ApplicationType.ExperimentRun, SAMPLE_DERIVATION_PROTOCOL_NAME);
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

    public boolean isSampleAliquot(ExpProtocol protocol)
    {
        if (protocol == null)
            return false;

        return SAMPLE_ALIQUOT_PROTOCOL_LSID.equals(protocol.getLSID());
    }

    @Override
    public void registerExperimentDataHandler(ExperimentDataHandler handler)
    {
        _dataHandlers.add(handler);
        if (null != handler.getDataType())
            registerDataType(handler.getDataType());
    }

    @Override
    public void registerExperimentRunTypeSource(ExperimentRunTypeSource source)
    {
        _runTypeSources.add(source);
    }

    @Override
    public void registerDataType(DataType type)
    {
        DataType existing = _dataTypes.put(type.getNamespacePrefix(), type);
        if (existing != null)
            throw new IllegalArgumentException(existing.getClass().getSimpleName() + " already claims namespace prefix '" + existing.getNamespacePrefix() + "'");
    }

    @Override
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

    @Override
    public DataType getDataType(String namespacePrefix)
    {
        return _dataTypes.get(namespacePrefix);
    }

    @Override
    public void registerProtocolImplementation(ProtocolImplementation impl)
    {
        ProtocolImplementation existing = _protocolImplementations.put(impl.getName(), impl);
        if (existing != null)
            throw new IllegalArgumentException(existing.getClass().getSimpleName() + " already claims name '" + existing.getName() + "'");
    }

    @Override
    public void registerProtocolHandler(ExperimentProtocolHandler handler)
    {
        _protocolHandlers.add(handler);
    }

    @Override
    public @Nullable ProtocolImplementation getProtocolImplementation(String name)
    {
        return _protocolImplementations.get(name);
    }

    @Override
    public void registerRunEditor(ExpRunEditor editor)
    {
        _runEditors.add(editor);
    }

    @Override
    @NotNull
    public List<ExpRunEditor> getRunEditors()
    {
        return _runEditors;
    }

    @Override
    public void registerProtocolInputCriteria(ExpProtocolInputCriteria.Factory factory)
    {
        ExpProtocolInputCriteria.Factory existing = _protocolInputCriteriaFactories.put(factory.getName(), factory);
        if (existing != null)
            throw new IllegalArgumentException(existing.getClass().getSimpleName() + " already claims name '" + existing.getName() + "'");
    }

    @Override
    public void registerObjectReferencer(ObjectReferencer referencer)
    {
        _objectReferencers.add(referencer);
    }

    @Override
    public void registerColumnExporter(ColumnExporter exporter)
    {
        _columnExporters.add(exporter);
    }

    @Override
    public List<ColumnExporter> getColumnExporters()
    {
        return _columnExporters;
    }

    @Override
    @NotNull
    public List<ObjectReferencer> getObjectReferencers()
    {
        return _objectReferencers;
    }

    @NotNull
    public ExpProtocolInputCriteria createProtocolInputCriteria(@NotNull String criteriaName, @Nullable String config)
    {
        ExpProtocolInputCriteria.Factory factory = _protocolInputCriteriaFactories.get(criteriaName);
        if (factory == null)
            throw new IllegalArgumentException("No protocol input criteria registered for '" + criteriaName + "'");

        return factory.create(config);
    }

    @Override
    public @Nullable ExpProtocolApplicationImpl getExpProtocolApplication(int rowId)
    {
        ProtocolApplication app = new TableSelector(getTinfoProtocolApplication()).getObject(rowId, ProtocolApplication.class);
        if (app == null)
            return null;
        return new ExpProtocolApplicationImpl(app);
    }

    @Override
    public @Nullable ExpProtocolApplicationImpl getExpProtocolApplicationFromEntityId(String entityId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EntityId"), entityId);
        TableInfo table = getTinfoProtocolApplication();
        ProtocolApplication app = new TableSelector(table, filter, null).getObject(ProtocolApplication.class);

        if (app == null)
            return null;

        return new ExpProtocolApplicationImpl(app);
    }

    @Override
    public List<ExpProtocolApplicationImpl> getExpProtocolApplicationsForRun(int runId)
    {
        return ExpProtocolApplicationImpl.fromProtocolApplications(getProtocolApplicationsForRun(runId));
    }

    @Override
    public ExpDataClassImpl createDataClass(
        @NotNull Container c,
        @NotNull User u,
        @NotNull String name,
        @Nullable DataClassDomainKindProperties options,
        List<GWTPropertyDescriptor> properties,
        List<GWTIndex> indices,
        @Nullable TemplateInfo templateInfo,
        @Nullable List<String> disabledSystemField
    ) throws ExperimentException
    {
        name = StringUtils.trimToNull(name);
        validateDataClassName(c, u, name);
        validateDataClassOptions(c, u, options);

        Lsid lsid = getDataClassLsid(c);
        Domain domain = PropertyService.get().createDomain(c, lsid.toString(), name, templateInfo);
        DomainKind<?> kind = domain.getDomainKind();
        Set<String> lowerReservedNames;

        if (kind != null)
        {
            domain.setDisabledSystemFields(kind.getDisabledSystemFields(disabledSystemField));
            lowerReservedNames = kind.getReservedPropertyNames(domain, u)
                    .stream()
                    .map(String::toLowerCase)
                    .collect(toSet());
        }
        else
            lowerReservedNames = emptySet();

        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        Set<String> propertyUris = new HashSet<>();
        List<GWTPropertyDescriptor> calculatedFields = new ArrayList<>();
        for (GWTPropertyDescriptor pd : properties)
        {
            // calculatedFields will be handled separately
            if (pd.getValueExpression() != null)
            {
                calculatedFields.add(pd);
                continue;
            }

            String propertyName = pd.getName().toLowerCase();
            if (lowerReservedNames.contains(propertyName))
            {
                if (options != null && options.isStrictFieldValidation())
                    throw new ApiUsageException("Property name '" + propertyName + "' is a reserved name.");
            }
            else if (domain.getPropertyByName(propertyName) != null) // issue 25275
                throw new ApiUsageException("Property name '" + propertyName + "' is already defined for this domain.");
            else
                DomainUtil.addProperty(domain, pd, defaultValues, propertyUris, null);
        }

        domain.setPropertyIndices(indices, lowerReservedNames);

        String importAliasJson = ExperimentJSONConverter.getAliasJson(options == null ? null : options.getImportAliases(), name);

        DataClass bean = new DataClass();
        bean.setContainer(c);
        bean.setName(name);
        bean.setLSID(lsid.toString());
        bean.setDataParentImportAliasMap(importAliasJson);
        if (options != null)
        {
            String nameExpression = options.getNameExpression();
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
            }
            bean.setDescription(options.getDescription());
            bean.setNameExpression(StringUtilsLabKey.replaceBadCharacters(nameExpression));
            bean.setMaterialSourceId(options.getSampleType());
            bean.setCategory(options.getCategory());
        }

        ExpDataClassImpl impl = new ExpDataClassImpl(bean);
        try (DbScope.Transaction tx = ensureTransaction())
        {
            OntologyManager.ensureObject(c, lsid.toString());

            if (kind != null)
                domain.setPropertyForeignKeys(kind.getPropertyForeignKeys(c));
            domain.save(u);
            impl.save(u);

            SchemaKey schemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, DataClassUserSchema.NAME);
            QueryService.get().saveCalculatedFieldsMetadata(schemaKey.toString(), name, null, calculatedFields, false, u, c);

            //TODO do DataClasses actually support default values? The DataClassDomainKind does not override showDefaultValueSettings to return true so it isn't shown in the UI.
            DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);

            if (options != null && options.getExcludedContainerIds() != null && !options.getExcludedContainerIds().isEmpty())
                ExperimentService.get().ensureDataTypeContainerExclusions(DataTypeForExclusion.DataClass, options.getExcludedContainerIds(), impl.getRowId(), u);

            tx.addCommitTask(() -> clearDataClassCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
            tx.commit();
        }
        catch (MetadataUnavailableException e)
        {
            throw new ExperimentException(e.getMessage());
        }

        return impl;
    }

    @Override
    public ValidationException updateDataClass(@NotNull Container c, @NotNull User u, @NotNull ExpDataClass dataClass,
                                        @Nullable DataClassDomainKindProperties properties,
                                        GWTDomain<? extends GWTPropertyDescriptor> original,
                                        GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        ValidationException errors;

        // if options doesn't have a rowId value, then it is just coming from the property-editDomain action only only updating domain fields
        DataClassDomainKindProperties options = properties != null && properties.getRowId() == dataClass.getRowId() ? properties : null;
        boolean hasNameChange = false;
        String oldDataClassName = dataClass.getName();
        String newName = null;
        if (options != null)
        {
            validateDataClassOptions(c, u, options);
            newName = StringUtils.trimToNull(options.getName());
            if (!oldDataClassName.equals(newName))
            {
                validateDataClassName(c, u, newName);
                hasNameChange = true;
                dataClass.setName(newName);
            }
            dataClass.setDescription(options.getDescription());
            dataClass.setNameExpression(options.getNameExpression());
            dataClass.setSampleType(options.getSampleType());
            dataClass.setCategory(options.getCategory());
            Map<String, Map<String, Object>> newAliases = options.getImportAliases();
            if (newAliases != null && !newAliases.isEmpty())
            {
                try
                {
                    Set<String> existingRequiredInputs = new HashSet<>(dataClass.getRequiredImportAliases().values());
                    String invalidParentType = getInvalidRequiredImportAliasUpdate(dataClass.getLSID(), false, newAliases, existingRequiredInputs, c, u);
                    if (invalidParentType != null)
                        throw new ApiUsageException("'" + invalidParentType + "' cannot be required as a parent type when there are existing data without a parent of this type.");
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            dataClass.setImportAliasMap(newAliases);

            if (!NameExpressionOptionService.get().allowUserSpecifiedNames(c) && options.getNameExpression() == null)
                throw new ApiUsageException(c.hasProductFolders() ? NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS : NAME_EXPRESSION_REQUIRED_MSG);
        }

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            LOG.debug("Saving data class " +  dataClass.getName());
            dataClass.save(u);

            String auditComment = null;
            SchemaKey schemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, DataClassUserSchema.NAME);
            if (hasNameChange)
            {
                QueryChangeListener.QueryPropertyChange.handleQueryNameChange(oldDataClassName, newName, schemaKey, u, c);
                auditComment = "The name of the data class '" + oldDataClassName + "' was changed to '" + newName + "'.";
            }

            errors = DomainUtil.updateDomainDescriptor(original, update, c, u, hasNameChange, auditComment);

            QueryService.get().saveCalculatedFieldsMetadata(schemaKey.toString(), update.getQueryName(), hasNameChange ? newName : null, update.getCalculatedFields(), !original.getCalculatedFields().isEmpty(), u, c);

            if (hasNameChange)
                addObjectLegacyName(dataClass.getObjectId(), ExperimentServiceImpl.getNamespacePrefix(ExpDataClass.class), oldDataClassName, u);

            if (options != null && options.getExcludedContainerIds() != null)
                ExperimentService.get().ensureDataTypeContainerExclusions(DataTypeForExclusion.DataClass, options.getExcludedContainerIds(), dataClass.getRowId(), u);

            if (!errors.hasErrors())
            {
                transaction.addCommitTask(() -> clearDataClassCache(c), DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
                transaction.commit();
            }
        }
        catch (MetadataUnavailableException e)
        {
            errors = new ValidationException();
            errors.addError(new SimpleValidationError(e.getMessage()));
        }

        return errors;
    }

    private void validateDataClassName(@NotNull Container c, @NotNull User u, String name) throws IllegalArgumentException
    {
        if (name == null)
            throw new ApiUsageException("DataClass name is required.");

        TableInfo dataClassTable = getTinfoDataClass();
        int nameMax = dataClassTable.getColumn("Name").getScale();
        if (name.length() > nameMax)
            throw new ApiUsageException("DataClass name may not exceed " + nameMax + " characters.");

        ExpDataClass existing = getDataClass(c, u, name);
        if (existing != null)
            throw new ApiUsageException("DataClass '" + existing.getName() + "' already exists.");

        // Issue 51321: check reserved data class name: First, All
        if ("First".equalsIgnoreCase(name) || "All".equalsIgnoreCase(name))
            throw new ApiUsageException("DataClass name '" + name + "' is reserved.");
    }

    private void validateDataClassOptions(@NotNull Container c, @NotNull User u, @Nullable DataClassDomainKindProperties options)
    {
        if (options == null)
            return;

        TableInfo dataClassTable = getTinfoDataClass();
        int nameExpMax = dataClassTable.getColumn("NameExpression").getScale();
        if (options.getNameExpression() != null && options.getNameExpression().length() > nameExpMax)
            throw new ApiUsageException("Name expression may not exceed " + nameExpMax + " characters.");

        // Validate category length
        int categoryMax = dataClassTable.getColumn("Category").getScale();
        if (options.getCategory() != null && options.getCategory().length() > categoryMax)
            throw new ApiUsageException("Category may not exceed " + categoryMax + " characters.");

        if (options.getSampleType() != null)
        {
            ExpSampleType st = SampleTypeService.get().getSampleType(c, u, options.getSampleType());
            if (st == null)
                throw new ApiUsageException("SampleType '" + options.getSampleType() + "' not found.");

            if (!st.getContainer().equals(c))
                throw new ApiUsageException("Associated SampleType must be defined in the same container as this DataClass.");
        }
    }

    private @NotNull List<ExpProtocolImpl> getExpProtocols(@Nullable SimpleFilter filter)
    {
        return getExpProtocols(filter, null, null);
    }

    private @NotNull List<ExpProtocolImpl> getExpProtocols(@Nullable SimpleFilter filter, @Nullable Sort sort, @Nullable Integer maxRows)
    {
        TableSelector selector = new TableSelector(getTinfoProtocol(), filter, sort);
        if (maxRows != null)
            selector.setMaxRows(maxRows);

        return ExpProtocolImpl.fromProtocols(selector.getArrayList(Protocol.class));
    }

    @Override
    public List<ExpProtocolImpl> getExpProtocols(Container... containers)
    {
        return getExpProtocols(new SimpleFilter(FieldKey.fromParts("Container"), Arrays.asList(containers), IN));
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

    @Override
    public List<ExpProtocolImpl> getAllExpProtocols()
    {
        return getExpProtocols((SimpleFilter) null, null, null);
    }

    @Override
    public List<? extends ExpProtocol> getExpProtocolsWithParameterValue(
        @NotNull String parameterURI,
        @NotNull String parameterValue,
        @Nullable Container c,
        @Nullable User user,
        @Nullable ContainerFilter cf
    )
    {
        SimpleFilter parameterFilter = new SimpleFilter()
                .addCondition(FieldKey.fromParts("ontologyEntryURI"), parameterURI)
                .addCondition(FieldKey.fromParts("stringvalue"), parameterValue)
                .addCondition(FieldKey.fromParts("valuetype"), "String");

        Set<Integer> protocolIds = new HashSet<>(new TableSelector(getTinfoProtocolParameter(), singleton("protocolId"), parameterFilter, null).getArrayList(Integer.class));
        if (protocolIds.isEmpty())
            return emptyList();

        SimpleFilter protocolFilter;

        if (c == null)
            protocolFilter = new SimpleFilter(FieldKey.fromParts("rowId"), protocolIds, IN);
        else
        {
            if (user != null && cf != null)
            {
                protocolFilter = new SimpleFilter(FieldKey.fromParts("rowId"), protocolIds, IN);
                protocolFilter.addCondition(cf.createFilterClause(getTinfoProtocol().getSchema(), FieldKey.fromParts("Container")));
            }
            else
            {
                protocolFilter = SimpleFilter.createContainerFilter(c);
                protocolFilter.addCondition(FieldKey.fromParts("rowId"), protocolIds, IN);
            }
        }

        return getExpProtocols(protocolFilter);
    }

    @Override
    public PipelineJob importXarAsync(ViewBackgroundInfo info, File file, String description, PipeRoot root) throws IOException
    {
        ExperimentPipelineJob job = new ExperimentPipelineJob(info, file.toPath(), description, false, root);
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

    private void addMaterialInputs(Map<? extends ExpMaterial, String> inputMaterials, ProtocolApplication protApp1, User user)
    {
        Set<MaterialInput> existingInputs = new HashSet<>(getMaterialInputsForApplication(protApp1.getRowId()));

        Set<MaterialInput> desiredInputs = new HashSet<>();

        for (Map.Entry<? extends ExpMaterial, String> entry : inputMaterials.entrySet())
        {
            MaterialInput input = new MaterialInput();
            input.setRole(entry.getValue());
            input.setMaterialId(entry.getKey().getRowId());
            input.setTargetApplicationId(protApp1.getRowId());
            desiredInputs.add(input);
        }

        syncInputs(user, existingInputs, desiredInputs, FieldKey.fromParts("MaterialId"), getTinfoMaterialInput());
    }

    private void addDataInputs(Map<? extends ExpData, String> inputDatas, ProtocolApplication protApp1, User user)
    {
        Set<DataInput> existingInputs = new HashSet<>(getDataInputsForApplication(protApp1.getRowId()));

        Set<DataInput> desiredInputs = new HashSet<>();

        for (Map.Entry<? extends ExpData, String> entry : inputDatas.entrySet())
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

    /**
     * There are subtle differences between File.toURI() and Path.toUri() so ensure you pick the correct getExpDatasUnderPath method.
     */
    @Override
    @NotNull
    public List<ExpDataImpl> getExpDatasUnderPath(@NotNull File path, @Nullable Container c)
    {
        return getExpDatasUnderPath(path.toURI().toString(), c, false);
    }

    @Override
    @NotNull
    public List<ExpDataImpl> getExpDatasUnderPath(@NotNull Path path, @Nullable Container c, boolean includeExactPath)
    {
        return getExpDatasUnderPath(path.toUri().toString(), c, includeExactPath);
    }

    @NotNull
    public List<ExpDataImpl> getExpDatasUnderPath(@NotNull String path, @Nullable Container c, boolean includeExactPath)
    {
        SimpleFilter filter = new SimpleFilter();
        if (c != null)
            filter.addCondition(FieldKey.fromParts("Container"), c);

        String prefix = path;
        if (!prefix.endsWith("/"))
            prefix = prefix + "/";

        filter.addCondition(FieldKey.fromParts("datafileurl"), prefix, CompareType.STARTS_WITH);
        filter.addCondition(FieldKey.fromParts("datafileurl"), path, CompareType.NEQ);
        List<ExpDataImpl> childDatas = getExpDatas(filter);

        if (includeExactPath)
        {
            // Include exp.data at the path itself
            prefix = prefix.substring(0, prefix.length() - 1);

            filter = new SimpleFilter();
            if (c != null)
                filter.addCondition(FieldKey.fromParts("Container"), c);
            filter.addCondition(FieldKey.fromParts("datafileurl"), prefix);

            childDatas.addAll(getExpDatas(filter));
        }

        return childDatas;

    }

    @Override
    public ExpProtocol updateProtocol(@NotNull ExpProtocol wrappedProtocol, @Nullable List<ExpProtocol> steps, @Nullable Map<String, List<String>> predecessors, User user) throws ExperimentException
    {
        try (DbScope.Transaction tx = getSchema().getScope().ensureTransaction(getProtocolImportLock()))
        {
            Protocol baseProtocol = ((ExpProtocolImpl) wrappedProtocol).getDataObject();
            insertProtocolSteps(baseProtocol, steps, predecessors, user, true);

            tx.commit();

            return getExpProtocol(baseProtocol.getRowId());
        }
    }

    @Override
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

            insertProtocolSteps(baseProtocol, steps, predecessors, user, false);

            tx.commit();

            return getExpProtocol(baseProtocol.getRowId());
        }
    }

    private void insertProtocolSteps(Protocol baseProtocol, @Nullable List<ExpProtocol> steps, @Nullable Map<String, List<String>> predecessors, User user, boolean isUpdate) throws ExperimentException
    {
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
        ProtocolAction previousAction;
        if (isUpdate)
            previousAction = getProtocolActions(baseProtocol.getRowId()).get(0);
        else
        {
            previousAction = insertProtocolAction(baseProtocol, baseProtocol, actionSequence, user);
            insertProtocolPredecessor(user, previousAction.getRowId(), previousAction.getRowId());
        }
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
                if (previousAction != null)
                    insertProtocolPredecessor(user, stepAction.getRowId(), previousAction.getRowId());
                previousAction = stepAction;
            }
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
            throw new ExperimentException("Protocol '" + protocol.getLSID() + "' needs to declare its applicationType before being inserted");
        }

        if (baseProtocol.getOutputDataType() == null)
            baseProtocol.setOutputDataType(ExpData.DEFAULT_CPAS_TYPE);
        if (baseProtocol.getOutputMaterialType() == null)
            baseProtocol.setOutputMaterialType(ExpMaterial.DEFAULT_CPAS_TYPE);

        Map<String, ProtocolParameter> baseParams = new HashMap<>(protocol.getProtocolParameters());

        if (!baseParams.containsKey(XarConstants.APPLICATION_LSID_TEMPLATE_URI))
        {
            throw new ExperimentException("Protocol '" + protocol.getName() + "' needs to declare " + XarConstants.APPLICATION_LSID_TEMPLATE_URI + " before inserting protocol");
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

        return saveProtocol(user, baseProtocol, false, null);
    }

    /**
     * Helper to insert ProtocolActions during the Protocol insertion process. Use {@link ExperimentServiceImpl#insertProtocol(ExpProtocol, List, Map, User)}
     */
    private ProtocolAction insertProtocolAction(Protocol parent, Protocol child, int actionSequence, User user)
    {
        ProtocolAction action = new ProtocolAction();
        action.setParentProtocolId(parent.getRowId());
        action.setChildProtocolId(child.getRowId());
        action.setSequence(actionSequence);
        action = Table.insert(user, getTinfoProtocolAction(), action);
        return action;
    }

    // TODO: Switch this to use insertProtocol(ExpProtocol, List, Map, User)
    @Override
    public ExpProtocol insertSimpleProtocol(ExpProtocol wrappedProtocol, User user) throws ExperimentException
    {
        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction(getProtocolImportLock()))
        {
            if (getExpProtocol(wrappedProtocol.getLSID()) != null)
            {
                throw new ExperimentException("An assay with that name already exists.");
            }

            Protocol baseProtocol = ((ExpProtocolImpl)wrappedProtocol).getDataObject();
            wrappedProtocol.setApplicationType(ExpProtocol.ApplicationType.ExperimentRun);
            wrappedProtocol.setStatus(ExpProtocol.Status.Active);
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
    }

    public List<ExpMaterialImpl> getExpMaterialsForRun(int runId)
    {
        return getExpMaterials(new SimpleFilter(FieldKey.fromParts("RunId"), runId), new Sort("RowId"));
    }

    /**
    /**
     * Ensure that an alias entry exists for each string value passed in, else create it.
     * @return The list of rowId for each alias name.
     */
    public Collection<Integer> ensureAliases(User user, Set<String> aliasNames)
    {
        final ExperimentService svc = ExperimentService.get();

        TableInfo aliasTable = svc.getTinfoAlias();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("name"), aliasNames, IN);
        TableSelector ts = new TableSelector(aliasTable, aliasTable.getColumns("name","rowId"), filter, null);
        Map<String, Integer> existingAliases = ts.getValueMap();

        // Return the rowId for the existing alias names
        Set<Integer> rowIds = new HashSet<>(existingAliases.values());

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

    @Override
    public void addExperimentListener(ExperimentListener listener)
    {
        _listeners.add(listener);
    }

    public void onAfterExperimentSaved(ExpExperiment experiment, Container container, User user)
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.afterExperimentSaved(container, user, experiment);
        }
    }

    public void onAfterRunSaved(ExpProtocol protocol, ExpRun run, Container container, User user)
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.afterRunSaved(container, user, protocol, run);
        }
    }

    @Override
    public void onBeforeRunSaved(ExpProtocol protocol, ExpRun run, Container container, User user) throws BatchValidationException
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.beforeRunSaved(container, user, protocol, run);
        }
    }

    @Override
    public void onRunDataCreated(ExpProtocol protocol, ExpRun run, Container container, User user) throws BatchValidationException
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.afterResultDataCreated(container, user, run, protocol);
        }
    }

    @Override
    public void onMaterialsCreated(List<? extends ExpMaterial> materials, Container container, User user)
    {
        for (ExperimentListener listener : _listeners)
        {
            listener.afterMaterialCreated(materials, container, user);
        }
    }

    /**
     * Get runs that can potentially be deleted based on supplied materials
     * @param materials -- Set of materials to get runs for
     */
    @Override
    public List<ExpRun> getDeletableRunsFromMaterials(Collection<? extends ExpMaterial> materials)
    {
        var runsUsingItems = new ArrayList<ExpRun>();
        if (null != materials && !materials.isEmpty())
        {
            Set<Integer> materialIds = materials.stream().map(ExpMaterial::getRowId).collect(toSet());
            runsUsingItems.addAll(getDerivedRunsFromMaterial(materialIds));
            runsUsingItems.addAll(getDeletableSourceRunsFromInputRowId(materialIds, getTinfoMaterial(), Collections.emptySet(), getTinfoData()));
        }

        return new ArrayList<>(runsDeletedWithInput(runsUsingItems));
    }

    /*
    * this is used to register a query view in experiment-ShowRunText.view and this expects
    * the query to have RunId column
    * */
    @Override
    public void registerRunInputsViewProvider(@NotNull QueryViewProvider<ExpRun> provider)
    {
        _runInputsQueryViews.add(provider);
    }

    /*
     * this is used to register a query view in experiment-ShowRunText.view and this expects
     * the query to have RunId column
     * */
    @Override
    public void registerRunOutputsViewProvider(@NotNull QueryViewProvider<ExpRun> provider)
    {
        _runOutputsQueryViews.add(provider);
    }

    @Override
    public List<QueryViewProvider<ExpRun>> getRunInputsViewProviders()
    {
        return Collections.unmodifiableList(_runInputsQueryViews);
    }

    @Override
    public List<QueryViewProvider<ExpRun>> getRunOutputsViewProviders()
    {
        return Collections.unmodifiableList(_runOutputsQueryViews);
    }

    private void addDataTypeExclusion(int rowId, DataTypeForExclusion dataType, String excludedContainerId, User user)
    {
        Map<String, Object> fields = new HashMap<>();
        fields.put("DataTypeRowId", rowId);
        fields.put("DataType", dataType.name());
        fields.put("ExcludedContainer", excludedContainerId);
        Table.insert(user, getTinfoDataTypeExclusion(), fields);
    }

    @Override
    public void removeContainerDataTypeExclusions(String containerId)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM  ")
                .append(getTinfoDataTypeExclusion())
                .append(" WHERE excludedContainer = ? ");
        sql.add(containerId);
        new SqlExecutor(getExpSchema()).execute(sql);
    }

    @Override
    public void removeDataTypeExclusion(Collection<Integer> rowIds, DataTypeForExclusion dataType)
    {
        removeDataTypeExclusion(rowIds, dataType, null);
    }

    private void removeDataTypeExclusion(Collection<Integer> rowIds, DataTypeForExclusion dataType, @Nullable String excludedContainerId)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM  ")
                .append(getTinfoDataTypeExclusion())
                .append(" WHERE DataTypeRowId ");
        sql.appendInClause(rowIds, getExpSchema().getSqlDialect());
        sql.append(" AND DataType = ?");
        sql.add(dataType.name());
        if (!StringUtils.isEmpty(excludedContainerId))
        {
            sql.append(" AND ExcludedContainer = ?");
            sql.add(excludedContainerId);
        }

        new SqlExecutor(getExpSchema()).execute(sql);
    }

    @NotNull private List<Map<String, Object>> _getContainerDataTypeExclusions(@Nullable DataTypeForExclusion dataType, @Nullable String excludedContainerIdOrPath, @Nullable Integer dataTypeRowId)
    {
        SQLFragment sql = new SQLFragment("SELECT DataTypeRowId, DataType, ExcludedContainer FROM ")
                .append(getTinfoDataTypeExclusion())
                .append(" WHERE ");
        String and = "";
        if (dataType != null)
        {
            sql.append("DataType = ? ");
            sql.add(dataType.name());
            and = " AND ";
        }

        if (!StringUtils.isEmpty(excludedContainerIdOrPath))
        {
            String excludedContainerId = excludedContainerIdOrPath;
            if (!GUID.isGUID(excludedContainerIdOrPath))
            {
                Container container = ContainerManager.getForPath(excludedContainerIdOrPath);
                if (container == null)
                {
                    // container not found, it may have been deleted, return empty array instead of making the DB query
                    return Collections.emptyList();
                }

                excludedContainerId = container.getId();
            }

            sql.append(and);
            sql.append("ExcludedContainer = ? ");
            sql.add(excludedContainerId);
            and = " AND ";
        }

        if (dataTypeRowId != null && dataTypeRowId > 0)
        {
            sql.append(and);
            sql.append("DataTypeRowId = ? ");
            sql.add(dataTypeRowId);
        }

        return Arrays.stream(new SqlSelector(getTinfoDataTypeExclusion().getSchema(), sql).getMapArray()).toList();
    }

    @Override
    public @NotNull Map<DataTypeForExclusion, Set<Integer>> getContainerDataTypeExclusions(@NotNull String excludedContainerId)
    {
        List<Map<String, Object>> exclusions = _getContainerDataTypeExclusions(null, excludedContainerId, null);

        Map<DataTypeForExclusion, Set<Integer>> typeExclusions = new HashMap<>();
        for (Map<String, Object> exclusion : exclusions)
        {
            String dataTypeStr = (String) exclusion.get("DataType");
            DataTypeForExclusion dataType = DataTypeForExclusion.valueOf(dataTypeStr);
            if (!typeExclusions.containsKey(dataType))
                typeExclusions.put(dataType, new HashSet<>());
            typeExclusions.get(dataType).add((Integer) exclusion.get("DataTypeRowId"));
        }

        return typeExclusions;
    }

    @Override
    public Set<String> getDataTypeContainerExclusions(@NotNull DataTypeForExclusion dataType, @NotNull Integer dataTypeRowId)
    {
        List<Map<String, Object>> exclusions = _getContainerDataTypeExclusions(dataType, null, dataTypeRowId);
        Set<String> excludedProjects = new HashSet<>();
        for (Map<String, Object> exclusion : exclusions)
            excludedProjects.add((String) exclusion.get("ExcludedContainer"));
        return excludedProjects;
    }

    private Set<Integer> _getContainerDataTypeExclusions(DataTypeForExclusion dataType, String excludedContainerId)
    {
        Set<Integer> excludedRowIds = new HashSet<>();
        List<Map<String, Object>> exclusions = _getContainerDataTypeExclusions(dataType, excludedContainerId, null);
        for (Map<String, Object> exclusion : exclusions)
            excludedRowIds.add((Integer) exclusion.get("DataTypeRowId"));

        return excludedRowIds;
    }

    @Override
    public void ensureContainerDataTypeExclusions(@NotNull DataTypeForExclusion dataType, @Nullable DataTypeForExclusion relatedDataType, @Nullable Collection<Integer> excludedDataTypeRowIds, @NotNull String excludedContainerId, User user)
    {
        if (excludedDataTypeRowIds == null)
            return;

        Set<Integer> previousExclusions = _getContainerDataTypeExclusions(dataType, excludedContainerId);
        Set<Integer> relatedExclusions = relatedDataType != null ? _getContainerDataTypeExclusions(relatedDataType, excludedContainerId) : null;
        Set<Integer> updatedExclusions = new HashSet<>(excludedDataTypeRowIds);

        Set<Integer> toAdd = new HashSet<>(updatedExclusions);
        toAdd.removeAll(previousExclusions);

        Set<Integer> toRemove = new HashSet<>(previousExclusions);
        toRemove.removeAll(updatedExclusions);

        if (!toAdd.isEmpty())
        {
            for (Integer add : toAdd)
            {
                addDataTypeExclusion(add, dataType, excludedContainerId, user);

                // Prevent "double exclusion" for related exclusion types (i.e. if a sample type is excluded from the
                // project, then we can delete any "Dashboard Sample Type" exclusions for that same sample type).
                // Note that "double exclusions" won't cause any harm, they just aren't necessary and can be cleaned up here.
                if (relatedExclusions != null && relatedExclusions.contains(add))
                    removeDataTypeExclusion(Collections.singleton(add), relatedDataType, excludedContainerId);
            }
        }

        if (!toRemove.isEmpty())
            removeDataTypeExclusion(toRemove, dataType, excludedContainerId);
    }

    @Override
    public void ensureDataTypeContainerExclusions(@NotNull DataTypeForExclusion dataType, @Nullable Collection<String> excludedContainerIds, @NotNull Integer dataTypeId, User user)
    {
        if (excludedContainerIds == null)
            return;

        Set<String> previousExclusions = getDataTypeContainerExclusions(dataType, dataTypeId);
        Set<String> updatedExclusions = new HashSet<>(excludedContainerIds);

        Set<String> toAdd = new HashSet<>(updatedExclusions);
        toAdd.removeAll(previousExclusions);

        Set<String> toRemove = new HashSet<>(previousExclusions);
        toRemove.removeAll(updatedExclusions);

        if (!toAdd.isEmpty())
        {
            for (String add : toAdd)
            {
                addDataTypeExclusion(dataTypeId, dataType, add, user);
                addAuditEventForDataTypeContainerUpdate(dataType, add, user);
            }
        }

        if (!toRemove.isEmpty())
        {
            for (String remove : toRemove)
            {
                removeDataTypeExclusion(Collections.singleton(dataTypeId), dataType, remove);
                addAuditEventForDataTypeContainerUpdate(dataType, remove, user);
            }
        }
    }

    private void addAuditEventForDataTypeContainerUpdate(DataTypeForExclusion type, String containerId, User user)
    {
        Container container = ContainerManager.getForId(containerId);
        if (container != null)
        {
            Set<Integer> exclusions = _getContainerDataTypeExclusions(type, containerId);
            String auditMsg = ("Data exclusion for folder " + container.getName() + " was updated.\n")
                    + getDisabledDataTypeAuditMsg(type, exclusions.stream().toList(), true);
            AuditTypeEvent event = new AuditTypeEvent(ContainerAuditProvider.CONTAINER_AUDIT_EVENT, container.getId(), auditMsg);
            AuditLogService.get().addEvent(user, event);
        }
    }

    @Override
    public String getDisabledDataTypeAuditMsg(DataTypeForExclusion type, List<Integer> ids, boolean isUpdate)
    {
        StringBuilder builder = new StringBuilder();
        if (ids != null && (isUpdate || !ids.isEmpty()))
        {
            if (isUpdate && ids.isEmpty())
                builder.append(type.name()).append( " exclusion has been cleared.\n");
            else
                builder.append("Excluded ").append(type.name()).append(": ").append(StringUtils.join(ids, ", ")).append(".\n");
        }
        return builder.toString();
    }

    @Override
    public void addObjectLegacyName(int objectId, String objectType, String legacyName, User user)
    {
        Map<String, Object> fields = new HashMap<>();
        fields.put("ObjectId", objectId);
        fields.put("ObjectType", objectType);
        fields.put("Name", legacyName);
        Table.insert(user, getTinfoObjectLegacyNames(), fields);
    }

    @Override
    public Integer getObjectIdWithLegacyName(String name, String dataType, Date effectiveDate, Container c, @Nullable ContainerFilter cf)
    {
        Set<GUID> containerIds = new HashSet<>();
        if (cf != null)
        {
            Collection<GUID> ids = cf.getIds();
            if (ids != null && ids.size() > 1)
                containerIds.addAll(ids);
        }

        if (containerIds.isEmpty())
            containerIds.add(c.getEntityId());

        TableInfo tableInfo = getTinfoObjectLegacyNames();

        String objecIdSql = ExpProtocol.DEFAULT_CPAS_TYPE.equals(dataType) ? "RowId FROM exp.Protocol" : "ObjectId FROM exp.Object";

        // find the last ObjectLegacyNames record with matched name and timestamp
        SQLFragment sql = new SQLFragment("SELECT ObjectId, Created FROM exp.ObjectLegacyNames " +
                "WHERE Name = ? AND ObjectType = ? AND Created >= ? " +
                "AND ObjectId IN (SELECT " + objecIdSql + " WHERE Container ");
        sql.add(name);
        sql.add(dataType);
        sql.add(effectiveDate);
        sql.appendInClause(containerIds, tableInfo.getSqlDialect());
        sql.append(") ORDER BY CREATED DESC");

        Map<String, Object>[] legacyNames = new SqlSelector(tableInfo.getSchema(), sql).getMapArray();

        // verify the found ObjectLegacyNames is valid at effectiveDate.
        // If an even older name exist, the older name ended before effectiveDate
        if (legacyNames.length >= 1)
        {
            Integer objectId = (Integer) legacyNames[0].get("ObjectId");
            Date nameEndTime = (Date) legacyNames[0].get("Created");
            SQLFragment previousNameSql = new SQLFragment("SELECT Created FROM exp.ObjectLegacyNames " +
                    "WHERE ObjectType = ? AND Created < ? " +
                    "AND ObjectId IN (SELECT ObjectId FROM exp.Data WHERE Container ");
            previousNameSql.add(dataType);
            previousNameSql.add(nameEndTime);
            previousNameSql.appendInClause(containerIds, tableInfo.getSqlDialect());
            previousNameSql.append(") ORDER BY CREATED DESC");

            Map<String, Object>[] previousLegacyNames = new SqlSelector(tableInfo.getSchema(), previousNameSql).getMapArray();
            if (previousLegacyNames.length >= 1)
            {
                Date previousNameEnd = (Date) previousLegacyNames[0].get("Created");
                if (previousNameEnd.compareTo(effectiveDate) < 0 )
                {
                    return objectId;
                }
            }
            else
                return objectId;
        }

        return null;
    }

    @Override
    public void addEdges(Collection<ExpLineageEdge> edges)
    {
        if (edges == null || edges.isEmpty())
            return;

        List<List<?>> params = new ArrayList<>();

        for (var edge : edges)
        {
            if (edge.getRunId() != null)
                throw new IllegalArgumentException("Failed to add lineage edge. Adding edges with a runId is not supported. Use experiment protocol inputs/outputs if run support is necessary.");

            // ignore cycles from and to itself
            if (Objects.equals(edge.getFromObjectId(), edge.getToObjectId()))
                continue;

            params.add(Arrays.asList(
                edge.getFromObjectId(),
                edge.getToObjectId(),
                edge.getSourceId(),
                StringUtils.trimToNull(edge.getSourceKey())
            ));
        }

        if (params.isEmpty())
            return;

        try (DbScope.Transaction tx = ensureTransaction())
        {
            String sql = "INSERT INTO " + getTinfoEdge().toString() +
                    " (fromObjectId, toObjectId, sourceId, sourceKey) " +
                    " VALUES (?, ?, ?, ?) ";

            Table.batchExecute(getExpSchema(), sql, params);
            tx.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    @NotNull
    public List<ExpLineageEdge> getEdges(ExpLineageEdge.FilterOptions options)
    {
        SimpleFilter filter = getEdgeFilterFromOptions(options);
        return new TableSelector(getTinfoEdge(), filter, null).getArrayList(ExpLineageEdge.class);
    }

    private SimpleFilter getEdgeFilterFromOptions(ExpLineageEdge.FilterOptions options)
    {
        SimpleFilter filter = new SimpleFilter();

        if (options.fromObjectId != null)
            filter.addCondition(FieldKey.fromParts("fromObjectId"), options.fromObjectId);
        if (options.toObjectId != null)
            filter.addCondition(FieldKey.fromParts("toObjectId"), options.toObjectId);
        if (options.runId != null)
            filter.addCondition(FieldKey.fromParts("runId"), options.runId);
        if (options.sourceIds != null)
        {
            if (options.sourceIds.isEmpty())
                filter.addWhereClause("0 = 1", new Object[]{});
            else
                filter.addCondition(FieldKey.fromParts("sourceId"), options.sourceIds, CompareType.IN);
        }
        if (StringUtils.trimToNull(options.sourceKey) != null)
            filter.addCondition(FieldKey.fromParts("sourceKey"), options.sourceKey);

        return filter;
    }

    @Override
    public int removeEdges(ExpLineageEdge.FilterOptions options)
    {
        if (options.runId != null)
            throw new IllegalArgumentException("Failed to remove lineage edges. Edges with a runId cannot be deleted via removeEdge(). Use experiment protocol inputs/outputs if run support is necessary.");

        int count = 0;
        SimpleFilter filter = getEdgeFilterFromOptions(options);

        if (filter.getClauses().isEmpty())
            return count;

        filter.addCondition(FieldKey.fromParts("runId"), null, CompareType.ISBLANK);

        try (DbScope.Transaction tx = ensureTransaction())
        {
            count = Table.delete(getTinfoEdge(), filter);
            tx.commit();
        }

        return count;
    }

    @Override
    public void registerNameExpressionType(String dataType, String schemaName, String queryName, String nameExpressionCol)
    {
        _nameExpressionTypes.add(new NameExpressionType(dataType, schemaName, queryName, nameExpressionCol));
    }

    @Override
    public Map<String, Map<String, Object>> getDomainMetrics()
    {
        Map<String, Map<String, Object>> metrics = new HashMap<>();
        metrics.put("nameexpression", getNameExpressionMetrics());
        metrics.put("parentalias", getParentAliasMetrics());
        return metrics;
    }

    private Pair<Long, Long> getParentAliasMetrics(TableInfo tableInfo, String aliasField)
    {
        SQLFragment sql = new SQLFragment("SELECT ")
                .append(aliasField)
                .append(" FROM ")
                .append(tableInfo)
                .append(" WHERE ")
                .append(aliasField)
                .append(" IS NOT NULL");
        List<String> aliases = new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(String.class);

        Long requiredSampleParentCount = 0L;
        Long requiredDataParentCount = 0L;
        try
        {
            for (String aliasStr : aliases)
            {
                Map<String, Map<String, Object>> aliasMaps = ExperimentJSONConverter.parseImportAliases(aliasStr);
                for (Map<String, Object> aliasMap : aliasMaps.values())
                {
                    if ((Boolean) aliasMap.get("required"))
                    {
                        String inputType = (String) aliasMap.get("inputType");
                        if (inputType.startsWith(MATERIAL_INPUTS_ALIAS_PREFIX))
                            requiredSampleParentCount++;
                        else if (inputType.startsWith(DATA_INPUTS_ALIAS_PREFIX))
                            requiredDataParentCount++;
                    }
                }
            }
        }
        catch (IOException ignore)
        {
        }

        return new Pair<>(requiredSampleParentCount, requiredDataParentCount);
    }

    private Map<String, Object> getParentAliasMetrics()
    {
        Map<String, Object> metrics = new HashMap<>();
        Pair<Long, Long> samplesMetrics = getParentAliasMetrics(getTinfoSampleType(), "materialparentimportaliasmap");
        metrics.put("RequiredSampleParentsForSampleTypes", samplesMetrics.first);
        metrics.put("RequiredSourceParentsForSampleTypes", samplesMetrics.first);
        Pair<Long, Long> dataMetrics = getParentAliasMetrics(getTinfoDataClass(), "dataparentimportaliasmap");
        metrics.put("RequiredSampleParentsForDataClasses", dataMetrics.first);
        metrics.put("RequiredSourceParentsForDataClasses", dataMetrics.second);
        return metrics;
    }

    private Map<String, Object> getNameExpressionMetrics()
    {
        Map<String, Object> metrics = new HashMap<>();

        for (NameExpressionType nameExpressionType : _nameExpressionTypes)
        {
            Map<String, Object> typeMetrics = new HashMap<>();
            SQLFragment sql = new SQLFragment("SELECT ")
                    .append(nameExpressionType.nameExpressionCol)
                    .append(" FROM ")
                    .append(nameExpressionType.schemaName)
                    .append(".")
                    .append(nameExpressionType.queryName)
                    .append(" WHERE ")
                    .append(nameExpressionType.nameExpressionCol)
                    .append(" IS NOT NULL");
            List<String> nameExpressionStrs = new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(String.class);

            Map<String, Long> substitutionMetrics = new HashMap<>();
            for (NameGenerator.SubstitutionValue substitutionValue : NameGenerator.SubstitutionValue.values())
            {
                String substitution = substitutionValue.getKey();
                long count = 0L;

                for (String nameExpressionStr : nameExpressionStrs)
                {
                    if (nameExpressionStr.contains("${" + substitution))
                        count++;
                }

                if (count > 0)
                    substitutionMetrics.put(substitution, count);
            }
            if (!substitutionMetrics.isEmpty())
                typeMetrics.put("substitutions", substitutionMetrics);

            Map<String, Long> formatMetrics = new HashMap<>();
            for (SubstitutionFormat substitutionFormat : SubstitutionFormat.getSubstitutionFormats().values())
            {
                String format = substitutionFormat.name();
                long count = 0L;

                for (String nameExpressionStr : nameExpressionStrs)
                {
                    if (nameExpressionStr.contains(":" + format))
                        count++;
                }

                if (count > 0)
                    formatMetrics.put(format, count);
            }
            if (!formatMetrics.isEmpty())
                typeMetrics.put("formats", formatMetrics);


            long grandParentMatch = 0L;
            long withCounterMatch = 0L;
            for (String nameExpressionStr : nameExpressionStrs)
            {
                if (nameExpressionStr.contains(ANCESTOR_INPUT_PREFIX_MATERIAL) || nameExpressionStr.contains(ANCESTOR_INPUT_PREFIX_DATA))
                    grandParentMatch++;

                if (nameExpressionStr.contains(":withCounter}") || nameExpressionStr.contains(":withCounter("))
                    withCounterMatch++;
            }

            if (grandParentMatch > 0)
                typeMetrics.put("grandParent", grandParentMatch);
            if (withCounterMatch > 0)
                typeMetrics.put("withCounter", withCounterMatch);

            metrics.put(nameExpressionType.dataType, typeMetrics);
        }

        return metrics;
    }

    public @NotNull Pair<Set<String>, Set<String>> getDataTypesWithRequiredLineage(Integer parentDataTypeRowId, boolean isSampleParent, Container container, User user)
    {
        Set<String> sampleTypes = new HashSet<>();
        Set<String> dataClasses = new HashSet<>();

        String parentDataTypeName = null;
        if (isSampleParent)
        {
            ExpSampleType sampleType = SampleTypeService.get().getSampleType(parentDataTypeRowId);
            if (sampleType != null)
                parentDataTypeName = sampleType.getName();
        }
        else
        {
            ExpDataClass dataClass = getDataClass(container, user, parentDataTypeRowId, true);
            if (dataClass != null)
                parentDataTypeName = dataClass.getName();
        }

        if (StringUtils.isEmpty(parentDataTypeName))
            return new Pair<>(sampleTypes, dataClasses);

        String targetInputType = (isSampleParent ? MATERIAL_INPUTS_ALIAS_PREFIX : DATA_INPUTS_ALIAS_PREFIX) + parentDataTypeName;
        for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(container, user, true))
        {
            try
            {
                if (new CaseInsensitiveHashSet(sampleType.getRequiredImportAliases().values()).contains(targetInputType))
                    sampleTypes.add(sampleType.getDomain().getLabel());
            }
            catch (IOException ignore)
            {
            }
        }
        for (ExpDataClassImpl dataClass : getDataClasses(container, user, true))
        {
            try
            {
                if (new CaseInsensitiveHashSet(dataClass.getRequiredImportAliases().values()).contains(targetInputType))
                    dataClasses.add(dataClass.getDomain().getLabel());
            }
            catch (IOException ignore)
            {
            }
        }

        return new Pair<>(sampleTypes, dataClasses);
    }

    public boolean hasMissingRequiredParent(String parentCpasType, String childCpasType, boolean isSampleParent, boolean isSampleChild)
    {
        TableInfo protocolAppTableInfo = getTinfoProtocolApplication();
        TableInfo dataTableInfo = isSampleChild ? getTinfoMaterial() : getTinfoData();
        TableInfo parentDataTableInfo = isSampleParent ? getTinfoMaterial() : getTinfoData();
        TableInfo parentInputTableInfo = isSampleParent ? getTinfoMaterialInput() : getTinfoDataInput();
        String inputFieldName = isSampleParent ? "materialid" : "dataid";

        SQLFragment totalSql = new SQLFragment("SELECT COUNT(cur.rowId) FROM ");
        totalSql.append(dataTableInfo, "cur")
                .append("\nWHERE cpastype = ? ")
                .add(childCpasType);

        if (isSampleChild)
        {
            totalSql.append(" AND cur.rowId = cur.rootmaterialrowid"); // exclude aliquots
        }

        Long totalCount = new SqlSelector(dataTableInfo.getSchema(), totalSql).getObject(Long.class);

        if (totalCount == 0)
            return false;

        SQLFragment sql = new SQLFragment("SELECT COUNT(DISTINCT cur.rowId) FROM ");
        sql.append(dataTableInfo, "cur")
                .append(" LEFT OUTER JOIN ")
                .append(protocolAppTableInfo, "pa")
                .append(" ON cur.sourceapplicationid = pa.rowId\n")
                .append("JOIN ")
                .append(parentInputTableInfo, "ip")
                .append(" ON pa.rowId = ip.targetapplicationid\n")
                .append("JOIN ")
                .append(parentDataTableInfo, "p")
                .append(" ON p.rowId = ip.")
                .append(inputFieldName)
                .append("\nWHERE cur.cpastype = ? ")
                .add(childCpasType)
                .append(" AND p.cpastype = ? ")
                .add(parentCpasType);
        if (isSampleChild)
        {
            sql.append(" AND cur.rowId = cur.rootmaterialrowid"); // exclude aliquots
        }

        Long withParentCount = new SqlSelector(dataTableInfo.getSchema(), sql).getObject(Long.class);

        return totalCount > withParentCount;
    }

    public String getInvalidRequiredImportAliasUpdate(String dataTypeLsid, boolean isSampleType, Map<String, Map<String, Object>> newAliases, Set<String> existingRequiredInputs, Container c, User u)
    {
        for (Map.Entry<String, Map<String, Object>> newEntry : newAliases.entrySet())
        {
            String dataType = (String) newEntry.getValue().get("inputType");
            if ((Boolean) newEntry.getValue().get("required") && !existingRequiredInputs.contains(dataType))
            {
                boolean isParentSamples = dataType.toLowerCase().startsWith(MATERIAL_INPUTS_ALIAS_PREFIX.toLowerCase());
                String dataTypeName = dataType.substring(isParentSamples ? MATERIAL_INPUTS_ALIAS_PREFIX.length() : DATA_INPUTS_ALIAS_PREFIX.length());

                String parentCpas = null;
                if (isParentSamples)
                {
                    ExpSampleType sampleTypeParent = SampleTypeService.get().getSampleType(c, u, dataTypeName);
                    if (sampleTypeParent != null)
                        parentCpas = sampleTypeParent.getLSID();
                }
                else
                {
                    ExpDataClass dataClassParent = getDataClass(c, dataTypeName);
                    if (dataClassParent != null)
                        parentCpas = dataClassParent.getLSID();
                }
                if (hasMissingRequiredParent(parentCpas, dataTypeLsid, isParentSamples, isSampleType))
                    return dataTypeName;
            }
        }

        return null;
    }

    private static @Nullable TableInfo getTableInfo(String schemaName)
    {
        // 'samples' | 'exp.data' | 'assay'
        if (SamplesSchema.SCHEMA_NAME.equalsIgnoreCase(schemaName))
            return ExperimentService.get().getTinfoMaterial();
        else if ("exp.data".equalsIgnoreCase(schemaName))
            return  ExperimentService.get().getTinfoData();
        else if (AssaySchema.NAME.equalsIgnoreCase(schemaName))
            return ExperimentService.get().getTinfoExperimentRun();
        else
            return null;
    }

    public static Pair<Integer, Integer> getCurrentAndCrossFolderDataCount(Collection<Integer> rowIds, String dataType, Container container)
    {
        DbSchema expSchema = DbSchema.get("exp", DbSchemaType.Module);
        SqlDialect dialect = expSchema.getSqlDialect();

        TableInfo tableInfo = getTableInfo(dataType);
        if (tableInfo == null)
            return null;

        SQLFragment currentFolderCountSql = new SQLFragment()
                .append(" SELECT COUNT(*) FROM ")
                .append(tableInfo, "t")
                .append("\nWHERE Container = ? ")
                .add(container.getId())
                .append("\nAND RowId ");
        dialect.appendInClauseSql(currentFolderCountSql, rowIds);
        int currentFolderSelectionCount = new SqlSelector(expSchema, currentFolderCountSql).getArrayList(Integer.class).get(0);

        SQLFragment crossFolderCountSql = new SQLFragment()
                .append(" SELECT COUNT(*) FROM ")
                .append(tableInfo, "t")
                .append("\nWHERE Container <> ? ")
                .add(container.getId())
                .append("\nAND RowId ");
        dialect.appendInClauseSql(crossFolderCountSql, rowIds);
        int crossFolderSelectionCount = new SqlSelector(expSchema, crossFolderCountSql).getArrayList(Integer.class).get(0);

        return new Pair<>(currentFolderSelectionCount, crossFolderSelectionCount);
    }

    @Override
    public int updateExpObjectContainers(TableInfo tableInfo, List<Integer> rowIds, Container targetContainer)
    {
        if (rowIds == null || rowIds.isEmpty())
            return 0;

        TableInfo objectTable = OntologyManager.getTinfoObject();
        SQLFragment objectUpdate = new SQLFragment("UPDATE ").append(objectTable).append(" SET container = ").appendValue(targetContainer.getEntityId())
                .append(" WHERE objectid IN (SELECT objectid FROM ").append(tableInfo).append(" WHERE rowid ");
        objectTable.getSchema().getSqlDialect().appendInClauseSql(objectUpdate, rowIds);
        objectUpdate.append(")");
        return new SqlExecutor(objectTable.getSchema()).execute(objectUpdate);
    }

    private int updateExpObjectContainers(List<String> lsids, Container targetContainer)
    {
        if (lsids == null || lsids.isEmpty())
            return 0;

        TableInfo objectTable = OntologyManager.getTinfoObject();
        SQLFragment objectUpdate = new SQLFragment("UPDATE ").append(objectTable).append(" SET container = ").appendValue(targetContainer.getEntityId())
                .append(" WHERE objecturi ");
        objectTable.getSchema().getSqlDialect().appendInClauseSql(objectUpdate, lsids);
        return new SqlExecutor(objectTable.getSchema()).execute(objectUpdate);
    }

    @Override
    public int aliasMapRowContainerUpdate(TableInfo aliasMapTable, List<Integer> dataIds, Container targetContainer)
    {
        if (dataIds == null || dataIds.isEmpty())
            return 0;

        SQLFragment aliasMapUpdate = new SQLFragment("UPDATE ").append(aliasMapTable).append(" SET container = ").appendValue(targetContainer.getEntityId())
                .append(" WHERE lsid IN (SELECT lsid FROM ").append(getTinfoData()).append(" WHERE rowid ");
        aliasMapTable.getSchema().getSqlDialect().appendInClauseSql(aliasMapUpdate, dataIds);
        aliasMapUpdate.append(")");
        return new SqlExecutor(aliasMapTable.getSchema()).execute(aliasMapUpdate);
    }

    @Override
    public Map<String, Integer> moveDataClassObjects(Collection<? extends ExpData> dataObjects, @NotNull Container sourceContainer, @NotNull Container targetContainer, @NotNull User user, @Nullable String userComment, @Nullable AuditBehaviorType auditBehavior) throws ExperimentException, BatchValidationException
    {
        if (dataObjects == null || dataObjects.isEmpty())
            throw new IllegalArgumentException("No sources provided to move operation.");

        Map<ExpDataClass, List<ExpData>> dataClassesMap = new HashMap<>();
        dataObjects.forEach(dataObject ->
                dataClassesMap.computeIfAbsent(dataObject.getDataClass(user), t -> new ArrayList<>()).add(dataObject));

        Map<String, Integer> updateCounts = new HashMap<>();
        updateCounts.put("sources", 0);
        updateCounts.put("sourceAliases", 0);
        updateCounts.put("sourceAuditEvents", 0);

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            if (AuditBehaviorType.NONE != auditBehavior)
            {
                TransactionAuditProvider.TransactionAuditEvent auditEvent = AbstractQueryUpdateService.createTransactionAuditEvent(targetContainer, QueryService.AuditAction.UPDATE);
                auditEvent.updateCommentRowCount(dataObjects.size());
                AbstractQueryUpdateService.addTransactionAuditEvent(transaction, user, auditEvent);
            }

            for (Map.Entry<ExpDataClass, List<ExpData>> entry: dataClassesMap.entrySet())
            {
                ExpDataClass dataClass = entry.getKey();
                List<ExpData> classObjects = entry.getValue();
                List<Integer> dataIds = classObjects.stream().map(ExpData::getRowId).toList();
                DataClassUserSchema schema = new DataClassUserSchema(dataClass.getContainer(), user);
                TableInfo dataClassTable = schema.getTable(dataClass.getName());

                // update exp.data.container
                int updateCount = ContainerManager.updateContainer(getTinfoData(), "rowId", dataIds, targetContainer, user, true);
                updateCounts.put("sources", updateCounts.get("sources") + updateCount);

                // update for exp.object.container
                updateExpObjectContainers(getTinfoData(), dataIds, targetContainer);

                // update for exp.dataaliasmap.container
                updateCounts.put("sourceAliases", aliasMapRowContainerUpdate(getTinfoDataAliasMap(), dataIds, targetContainer));

                // update core.document.container for any files attached to the data objects that are moving
                moveDataClassObjectAttachments(dataClass, classObjects, targetContainer, user);

                // LKB registry data class objects can have related junction list rows that need to be updated as well.
                // Since those tables already wire up trigger scripts, we'll use that mechanism here as well for the move event.
                BatchValidationException errors = new BatchValidationException();
                Map<String, Object> extraContext = Map.of("targetContainer", targetContainer, "classObjects", classObjects, "dataIds", dataIds);
                dataClassTable.fireBatchTrigger(sourceContainer, user, TableInfo.TriggerType.MOVE, false, errors, extraContext);
                if (errors.hasErrors())
                    throw errors;

                // move audit events associated with the sources that are moving
                int auditEventCount = QueryService.get().moveAuditEvents(targetContainer, dataIds, "exp.data", dataClassTable.getName());
                updateCounts.compute("sourceAuditEvents", (k, c) -> c == null ? auditEventCount : c + auditEventCount );

                // create summary audit entries for the source container only.  The message is pretty generic, so having it
                // in both source and target doesn't help much.
                addDataClassSummaryAuditEvent(user, sourceContainer, dataClassTable, updateCount, userComment);

                // create new detailed events for each data object that was moved
                AuditBehaviorType dcAuditBehavior = dataClassTable.getAuditBehavior(auditBehavior);
                if (dcAuditBehavior == AuditBehaviorType.DETAILED)
                {
                    List<Map<String, Object>> oldRows = new ArrayList<>();
                    List<Map<String, Object>> newRows = new ArrayList<>();
                    for (ExpData data : classObjects)
                    {
                        Map<String, Object> oldRecordMap = new CaseInsensitiveHashMap<>();
                        oldRecordMap.put("Container", sourceContainer.getName());
                        oldRecordMap.put("rowId", data.getRowId());
                        oldRows.add(oldRecordMap);
                        Map<String, Object> newRecordMap = new CaseInsensitiveHashMap<>();
                        newRecordMap.put("Container", targetContainer.getName());
                        newRecordMap.put("rowId", data.getRowId());
                        newRows.add(newRecordMap);
                    }
                    QueryService.get().getDefaultAuditHandler().addAuditEvent(user, targetContainer, dataClassTable, dcAuditBehavior, userComment, QueryService.AuditAction.UPDATE, newRows, oldRows);
                }
            }

            // move derivation runs
            updateCounts.putAll(moveDerivationRuns(dataObjects, targetContainer, user));

            transaction.addCommitTask(() -> {
                // update search index for moved data class object via indexDataClass() helper. It filters for data objects
                // to index based on the modified date
                for (ExpDataClass dataClass : dataClassesMap.keySet())
                    indexDataClass((ExpDataClassImpl) dataClass);
            }, DbScope.CommitTaskOption.IMMEDIATE, POSTCOMMIT, POSTROLLBACK);
            transaction.commit();
        }

        return updateCounts;
    }

    private void addDataClassSummaryAuditEvent(User user, Container container, TableInfo dataClassTable, int rowCount, String auditUserComment)
    {
        QueryService queryService = QueryService.get();
        queryService.getDefaultAuditHandler().addSummaryAuditEvent(user, container, dataClassTable, QueryService.AuditAction.UPDATE, rowCount, AuditBehaviorType.SUMMARY, auditUserComment);
    }

    private void moveDataClassObjectAttachments(ExpDataClass dataClass, Collection<ExpData> classObjects, Container targetContainer, User user)
    {
        List<? extends DomainProperty> attachmentDomainProps = dataClass.getDomain()
                .getProperties().stream()
                .filter(prop -> PropertyType.ATTACHMENT.equals(prop.getPropertyType())).toList();
        if (attachmentDomainProps.isEmpty())
            return;

        List<AttachmentParent> parents = new ArrayList<>();
        for (ExpData data : classObjects)
        {
            Lsid lsid = new Lsid(data.getLSID());
            parents.add(new ExpDataClassAttachmentParent(data.getContainer(), lsid));

        }
        try
        {
            AttachmentService.get().moveAttachments(targetContainer, parents, user);
        }
        catch (IOException ignored)
        {
            // method doesn't actually throw.
        }
    }

    private Map<String, Integer> moveDerivationRuns(Collection<? extends ExpData> dataObjects, Container targetContainer, User user) throws ExperimentException, BatchValidationException
    {
        // collect unique runIds mapped to the dataobjects that are moving that have that runId
        Map<Integer, Set<ExpData>> runIdData = new HashMap<>();
        dataObjects.forEach(dataObject -> {
            if (dataObject.getRunId() != null)
                runIdData.computeIfAbsent(dataObject.getRunId(), t -> new HashSet<>()).add(dataObject);
        });
        // find the set of runs associated with data objects that are moving
        List<? extends ExpRun> runs = ExperimentService.get().getExpRuns(runIdData.keySet());
        List<ExpRun> toUpdate = new ArrayList<>();
        List<ExpRun> toSplit = new ArrayList<>();
        for (ExpRun run : runs)
        {
            Set<Integer> outputIds = run.getDataOutputs().stream().map(ExpData::getRowId).collect(Collectors.toSet());
            Set<Integer> movingIds = runIdData.get(run.getRowId()).stream().map(ExpData::getRowId).collect(Collectors.toSet());
            if (movingIds.size() == outputIds.size() && movingIds.containsAll(outputIds))
                toUpdate.add(run);
            else
                toSplit.add(run);
        }

        int updateCount = moveExperimentRuns(toUpdate, targetContainer, user);
        int splitCount = splitExperimentRuns(toSplit, runIdData, targetContainer, user);
        return Map.of("sourceDerivationRunsUpdated", updateCount, "sourceDerivationRunsSplit", splitCount);
    }

    @Override
    public int moveExperimentRuns(List<ExpRun> runs, Container targetContainer, User user)
    {
        if (runs.isEmpty())
            return 0;

        TableInfo runsTable = getTinfoExperimentRun();
        List<Integer> runRowIds = runs.stream().map(ExpRun::getRowId).toList();
        SQLFragment materialUpdate = new SQLFragment("UPDATE ").append(runsTable)
                .append(" SET container = ").appendValue(targetContainer.getEntityId())
                .append(", modified = ").appendValue(new Date())
                .append(", modifiedby = ").appendValue(user.getUserId())
                .append(" WHERE rowid ");
        runsTable.getSchema().getSqlDialect().appendInClauseSql(materialUpdate, runRowIds);
        int updateCount = new SqlExecutor(runsTable.getSchema()).execute(materialUpdate);

        ExperimentService.get().updateExpObjectContainers(getTinfoExperimentRun(), runRowIds, targetContainer);

        // LKB media have object properties associated with the protocol applications of the run
        // and object properties associated with the material and data inputs for those protocol applications
        List<String> lsidsToUpdate = new ArrayList<>();
        for (ExpRun run : runs)
        {
            for (ExpProtocolApplication pa : run.getProtocolApplications())
            {
                lsidsToUpdate.add(pa.getLSID());
                for (ExpDataRunInput dataInput : pa.getDataInputs())
                    lsidsToUpdate.add(DataInput.lsid(dataInput.getData().getRowId(), pa.getRowId()));
                for (ExpMaterialRunInput materialInput : pa.getMaterialInputs())
                    lsidsToUpdate.add(MaterialInput.lsid(materialInput.getMaterial().getRowId(), pa.getRowId()));
            }
        }
        updateExpObjectContainers(lsidsToUpdate, targetContainer);

        return updateCount;
    }

    private int splitExperimentRuns(List<ExpRun> runs, Map<Integer, Set<ExpData>> movingData, Container targetContainer, User user) throws ExperimentException, BatchValidationException
    {
        final ViewBackgroundInfo targetInfo = new ViewBackgroundInfo(targetContainer, user, null);
        ExperimentServiceImpl expService = (ExperimentServiceImpl) ExperimentService.get();
        int runCount = 0;
        for (ExpRun run : runs)
        {
            ExpProtocolApplication sourceApplication = null;
            ExpProtocolApplication outputApp = run.getOutputProtocolApplication();

            Set<ExpData> movingSet = movingData.get(run.getRowId());
            int numStaying = 0;
            Map<ExpData, String> movingOutputsMap = new HashMap<>();

            // the derived samples (outputs of the run) are inputs to the output step of the run (obviously)
            for (ExpDataRunInput dataInput : outputApp.getDataInputs())
            {
                ExpData dataObject = dataInput.getData();
                if (movingSet.contains(dataObject))
                {
                    // clear out the run and source application so a new derivation run can be created.
                    dataObject.setRun(null);
                    dataObject.setSourceApplication(null);
                    movingOutputsMap.put(dataObject, dataInput.getRole());
                }
                else
                {
                    if (sourceApplication == null)
                        sourceApplication = dataObject.getSourceApplication();
                    numStaying++;
                }
            }

            try
            {
                // create a new derivation run for the data that are moving
                expService.derive(run.getMaterialInputs(), run.getDataInputs(), Collections.emptyMap(), movingOutputsMap, targetInfo, LOG);
            }
            catch (ValidationException e)
            {
                BatchValidationException errors = new BatchValidationException();
                errors.addRowError(e);
                throw errors;
            }
            // Update the run for the data that have stayed behind. Change the name and remove the moved data as outputs
            run.setName(ExperimentServiceImpl.getDerivationRunName(run.getMaterialInputs(), run.getDataInputs(), run.getMaterialOutputs().size(), numStaying));

            run.save(user);
            List<Integer> movingDataIds = movingSet.stream().map(ExpData::getRowId).toList();

            outputApp.removeDataInputs(user, movingDataIds);
            if (sourceApplication != null)
                sourceApplication.removeDataInputs(user, movingDataIds);

            runCount++;
        }
        return runCount;
    }

    @Override
    public Map<String, Integer> moveAssayRuns(@NotNull List<? extends ExpRun> assayRuns, Container container, Container targetContainer, User user, String userComment, AuditBehaviorType auditBehavior)
    {
        if (assayRuns.isEmpty())
            throw new IllegalArgumentException("No assayRuns provided to move operation.");

        Map<ExpProtocol, List<ExpRun>> protocolMap = new HashMap<>();
        assayRuns.forEach(run ->
                protocolMap.computeIfAbsent(run.getProtocol(), t -> new ArrayList<>()).add(run));

        List<String> runLsids = assayRuns.stream().map(ExpRun::getLSID).toList();

        ExperimentService expService = ExperimentService.get();

        AbstractAssayProvider.AssayMoveData assayMoveData = new AbstractAssayProvider.AssayMoveData(new HashMap<>(), new HashMap<>());
        try (DbScope.Transaction transaction = ensureTransaction())
        {
            if (auditBehavior != null && AuditBehaviorType.NONE != auditBehavior)
            {
                TransactionAuditProvider.TransactionAuditEvent auditEvent = AbstractQueryUpdateService.createTransactionAuditEvent(targetContainer, QueryService.AuditAction.UPDATE);
                auditEvent.updateCommentRowCount(assayRuns.size());
                AbstractQueryUpdateService.addTransactionAuditEvent(transaction, user, auditEvent);
            }

            for (Map.Entry<ExpProtocol, List<ExpRun>> entry: protocolMap.entrySet())
            {
                ExpProtocol protocol = entry.getKey();
                AssayProvider provider = AssayService.get().getProvider(protocol);
                List<ExpRun> runs = entry.getValue();
                if (provider != null)
                {
                    provider.moveRuns(runs, targetContainer, user, assayMoveData);
                    Map<String, Integer> counts = assayMoveData.counts();
                    int auditEventCount = expService.moveAuditEvents(targetContainer, runLsids);
                    counts.put("auditEvents", counts.getOrDefault("auditEvents", 0) + auditEventCount);
                    if (auditBehavior != null && AuditBehaviorType.NONE != auditBehavior)
                    {
                        for (ExpRun run : runs)
                        {
                            run.setContainer(targetContainer);
                            auditRunEvent(user, protocol, run, null, "Assay run was moved.", userComment);
                        }
                    }
                }
            }

            Map<String, Integer> counts = assayMoveData.counts();
            int fileMoveCount = assayMoveData.fileMovesByRunId().values().stream().mapToInt(List::size).sum();
            counts.put("movedFiles", fileMoveCount);
            transaction.addCommitTask(() -> {
                for (List<AbstractAssayProvider.AssayFileMoveData> runFileRenameData : assayMoveData.fileMovesByRunId().values())
                {
                    for (AbstractAssayProvider.AssayFileMoveData renameData : runFileRenameData)
                        moveFile(renameData);
                }
            }, POSTCOMMIT);

            transaction.commit();
        }

        return assayMoveData.counts();
    }

    private boolean moveFile(AbstractAssayProvider.AssayFileMoveData renameData)
    {
        String fieldName = renameData.fieldName() == null ? "datafileurl" : renameData.fieldName();
        File targetFile = renameData.targetFile();
        File sourceFile = renameData.sourceFile();
        String assayName = renameData.run().getProtocol().getName();
        String runName = renameData.run().getName();
        if (!targetFile.getParentFile().exists())
        {
            if (!targetFile.getParentFile().mkdirs())
            {
                LOG.warn(String.format("Creation of target directory '%s' to move file '%s' to, for '%s' assay run '%s' (field: '%s') failed.",
                        targetFile.getParent(),
                        sourceFile.getAbsolutePath(),
                        assayName,
                        runName,
                        fieldName));
                return false;
            }
        }
        if (!sourceFile.renameTo(targetFile))
        {
            LOG.warn(String.format("Rename of '%s' to '%s' for '%s' assay run '%s' (field: '%s') failed.",
                    sourceFile.getAbsolutePath(),
                    targetFile.getAbsolutePath(),
                    assayName,
                    runName,
                    fieldName));
            return false;
        }

        return true;
    }

    @Override
    public int moveAuditEvents(Container targetContainer, List<String> runLsids)
    {
        ExperimentAuditProvider auditProvider = new ExperimentAuditProvider();
        return auditProvider.moveEvents(targetContainer, runLsids);
    }

    @Override
    public @NotNull Map<String, List<String>> getUniqueIdLsids(List<String> uniqueIds, User user, Container container)
    {
        final String UNIQUE_ID_COL_NAME = "UniqueId";

        Map<String, List<String>> idLsids = new HashMap<>();
        int numUniqueIdCols = 0;
        SQLFragment unionSql;

        DbSchema dbSchema = ExperimentService.get().getSchema();
        SqlDialect dialect = dbSchema.getSqlDialect();
        UserSchema samplesUserSchema = QueryService.get().getUserSchema(user, container, SamplesSchema.SCHEMA_NAME);
        List<ExpSampleTypeImpl> sampleTypes = SampleTypeServiceImpl.get().getSampleTypes(container, user, true);

        String unionAll = "";
        SQLFragment query = new SQLFragment();

        for (ExpSampleTypeImpl type : sampleTypes)
        {
            TableInfo provisioned = type.getTinfo();
            TableInfo tableInfo = samplesUserSchema.getTable(type.getName());
            if (tableInfo == null || provisioned == null)
                continue;
            List<ColumnInfo> uniqueIdCols = provisioned.getColumns().stream().filter(ColumnInfo::isScannableField).toList();
            numUniqueIdCols += uniqueIdCols.size();
            for (ColumnInfo col : uniqueIdCols)
            {
                boolean isIntegerField = col.getJdbcType().isInteger();
                List<Integer> intIds = new ArrayList<>();
                if (isIntegerField)
                {
                    for (String id : uniqueIds)
                    {
                        try
                        {
                            int intId = Integer.parseInt(id);
                            intIds.add(intId);
                        }
                        catch (NumberFormatException e)
                        {
                            // do nothing, skip non int ids
                        }
                    }
                    if (intIds.isEmpty())
                        continue;
                }
                query.append(unionAll);
                query.append("SELECT LSID, ")
                        .append("CAST (").appendIdentifier(col.getSelectName()).append(" AS VARCHAR)")
                        .append(" AS ").append(UNIQUE_ID_COL_NAME);
                query.append(" FROM expsampleset.").append(dialect.quoteIdentifier(provisioned.getName()));
                query.append(" WHERE ").appendIdentifier(col.getSelectName()).appendInClause(isIntegerField ? intIds : uniqueIds, dialect);
                unionAll = "\n UNION ALL\n";
            }
        }

        if (numUniqueIdCols == 0)
            return idLsids;

        unionSql = new SQLFragment();
        unionSql.appendComment("<ExpMaterialUniqueIdUnionTableInfo>", dialect);
        unionSql.append(query);
        unionSql.appendComment("</ExpMaterialUniqueIdUnionTableInfo>", dialect);

        Map<String, Object>[] results = new SqlSelector(samplesUserSchema.getDbSchema(), unionSql).getMapArray();
        for (Map<String, Object> row : results)
        {
            String uniqId = (String) row.get(UNIQUE_ID_COL_NAME);
            String lsid = (String) row.get("LSID");
            idLsids.putIfAbsent(uniqId, new ArrayList<>());
            idLsids.get(uniqId).add(lsid);
        }

        return idLsids;
    }

    private void _renameAssayProtocols(String newAssayName, String oldAssayName, ExpProtocol protocol, User user, String type)
    {
        ExpProtocolImpl protocolImpl= getExpProtocol(protocol.getLSID() + "." + type);

        if (protocolImpl != null)
        {
            String newProtName = newAssayName + " - " + type;
            if (!newProtName.equals(protocolImpl.getName()))
            {
                Protocol updatedProtocol = protocolImpl.getDataObject();
                updatedProtocol.setName(newProtName);

                Map<String, ProtocolParameter> updatedParams = new HashMap<>(protocolImpl.getProtocolParameters());
                ProtocolParameter nameParam = updatedParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
                if (nameParam != null)
                {
                    String paramVal = nameParam.getStringValue();
                    nameParam.setStringValue(paramVal.replace(oldAssayName, newAssayName));
                    updatedProtocol.storeProtocolParameters(updatedParams.values());
                }
                saveProtocol(user, updatedProtocol);
            }
        }
    }

    @Override
    public void handleAssayNameChange(String newAssayName, String oldAssayName, AssayProvider provider, ExpProtocol protocol, User user, Container container)
    {
        if (!provider.canRename())
            return;

        addObjectLegacyName(protocol.getRowId(), ExperimentServiceImpl.getNamespacePrefix(ExpProtocol.class), oldAssayName, user);

        _renameAssayProtocols(newAssayName, oldAssayName, protocol, user, "Core");
        _renameAssayProtocols(newAssayName, oldAssayName, protocol, user, "Output");

        SchemaKey newSchema = SchemaKey.fromParts(AssaySchema.NAME, provider.getResourceName(), newAssayName);
        SchemaKey oldSchema = SchemaKey.fromParts(AssaySchema.NAME, provider.getResourceName(), oldAssayName);
        QueryChangeListener.QueryPropertyChange.handleSchemaNameChange(oldSchema.toString(), newSchema.toString(), newSchema, user, container);
    }

    @Override
    public boolean useStrictCounter()
    {
        if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
        {
            return AppProps.getInstance().isOptionalFeatureEnabled(EXPERIMENTAL_WITH_COUNTER);
        }
        else
        {
            return !AppProps.getInstance().isOptionalFeatureEnabled(EXPERIMENTAL_ALLOW_GAP_COUNTER);
        }
    }

    @Override
    public @Nullable ExpSampleType getLookupSampleType(@NotNull DomainProperty dp, @NotNull Container container, @NotNull User user)
    {
        Lookup lookup = dp.getLookup();
        if (lookup == null)
            return null;

        // TODO: Use concept URI instead of the lookup target schema to determine if the column is a sample.
        if (!(SamplesSchema.SCHEMA_SAMPLES.equals(lookup.getSchemaKey()) || SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.TableType.Materials.name()).equals(lookup.getSchemaKey())))
            return null;

        JdbcType type = dp.getPropertyType().getJdbcType();
        if (!(type.isText() || type.isInteger()))
            return null;

        Container c = lookup.getContainer() != null ? lookup.getContainer() : container;
        return SampleTypeService.get().getSampleType(c, user, lookup.getQueryName());
    }

    @Override
    public boolean isLookupToMaterials(DomainProperty dp)
    {
        if (dp == null)
            return false;

        Lookup lookup = dp.getLookup();
        if (lookup == null)
            return false;

        if (!(ExpSchema.SCHEMA_EXP.equals(lookup.getSchemaKey()) && ExpSchema.TableType.Materials.name().equalsIgnoreCase(lookup.getQueryName())))
            return false;

        JdbcType type = dp.getPropertyType().getJdbcType();
        return type.isText() || type.isInteger();
    }

    public Map<String, Map<String, MissingFilesCheckInfo>> doMissingFilesCheck(User user, Container container, boolean trackMissingFiles) throws SQLException
    {
        if (container == null)
            container = ContainerManager.getRoot();

        if (!container.hasPermission(user, AdminPermission.class))
            throw new UnauthorizedException("You don't have the required permission to perform this action");

        ContainerFilter cf;
        if (container.isRoot())
        {
            cf = new ContainerFilter.AllFolders(user);
        }
        else
            cf = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);

        FileLinkFileListener fileListener = new FileLinkFileListener();

        // map of containers -> source names -> info (missing files, missing file count, valid file count)
        Map<String, Map<String, MissingFilesCheckInfo>> fileResults = new HashMap<>();
        SQLFragment unionSql = fileListener.listFilesQuery(true);
        Collection<GUID> containerIds = cf.getIds();
        SQLFragment selectSql;
        if (containerIds == null || containerIds.isEmpty())
            selectSql = unionSql;
        else
            selectSql = new SQLFragment("SELECT * FROM (").append(unionSql).append(") a "/*postgres 15 and older requires subquery alias*/).append(" WHERE Container ").appendInClause(cf.getIds(), CoreSchema.getInstance().getSchema().getSqlDialect());
        final int MAX_MISSING_COUNT = 1_000;
        final int MAX_ROWS = 100_000;
        int missingCount = 0;
        try (ResultSet rs = new SqlSelector(CoreSchema.getInstance().getSchema(), selectSql).setMaxRows(MAX_ROWS).getResultSet(false))
        {
            while (rs.next())
            {
                String filePath = rs.getString("FilePath");
                if (StringUtils.isEmpty(filePath))
                    continue;

                File file;
                // TODO: Issue 50538: support s3 files
                if (filePath.startsWith("file:"))
                    file = new File(URI.create(filePath));
                else
                    file = new File(filePath);

                String containerId = rs.getString("Container");
                if (!fileResults.containsKey(containerId))
                    fileResults.put(containerId, new HashMap<>());

                String sourceName = rs.getString("SourceName");
                if (!fileResults.get(containerId).containsKey(sourceName))
                    fileResults.get(containerId).put(sourceName, new MissingFilesCheckInfo());

                if (!file.exists())
                {
                    missingCount++;
                    fileResults.get(containerId).get(sourceName).addMissingFile(filePath, trackMissingFiles);
                }
                else
                    fileResults.get(containerId).get(sourceName).incrementValidFilesCount();

                if (trackMissingFiles && missingCount >= MAX_MISSING_COUNT)
                    break;
            }
        }
        return fileResults;
    }

    public static class TestCase extends Assert
    {
        final Logger log = LogManager.getLogger(ExperimentServiceImpl.class);

        @Before
        public void setUp()
        {
            JunitUtil.deleteTestContainer();
        }

        @After
        public void tearDown()
        {
            JunitUtil.deleteTestContainer();
        }

        @Test
        public void testRunInputProperties() throws Exception
        {
            Assume.assumeTrue("31193: Experiment module has undeclared dependency on study module", AssayService.get() != null);

            final User user = TestContext.get().getUser();
            final Container c = JunitUtil.getTestContainer();
            final ViewBackgroundInfo info = new ViewBackgroundInfo(c, user, null);

            // assert no MaterialInput exp.object exist
            assertEquals(0L, countMaterialInputObjects(c));

            // create sample type
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("name", "string"));
            ExpSampleType st = SampleTypeService.get().createSampleType(c, user, "TestSamples", null, props, Collections.emptyList(), -1, -1, -1, -1, null);

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

            ExpMaterial sampleIn = st.getSample(c, "bob");
            ExpMaterial sampleOut = st.getSample(c, "sally");

            // create run
            Map<ExpMaterial, String> inputMaterials = new HashMap<>();
            inputMaterials.put(sampleIn, "Sample Goo");

            Map<ExpData, String> inputData = new HashMap<>();
            Map<ExpMaterial, String> outputMaterials = new HashMap<>();
            outputMaterials.put(sampleOut, "Sample Boo");

            Map<ExpData, String> outputData = new HashMap<>();

            ExperimentServiceImpl impl = ExperimentServiceImpl.get();
            ExpRun run = impl.derive(inputMaterials, inputData, outputMaterials, outputData, info, log);
            run.save(user);

            ExpProtocolApplication pa = run.getInputProtocolApplication();
            List<? extends ExpMaterialRunInput> materialRunInputs = pa.getMaterialInputs();
            assertEquals(1, materialRunInputs.size());

            ExpMaterialRunInputImpl materialRunInput = (ExpMaterialRunInputImpl)materialRunInputs.get(0);
            assertEquals(sampleIn, materialRunInput.getMaterial());
            assertEquals("Sample Goo", materialRunInput.getRole());
            assertEquals(materialRunInput.getLSIDNamespacePrefix(), MaterialInput.NAMESPACE);
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

    public record edge(int to, int from) {};
    public static class InnerResult
    {
        public int depth, self, fromObjectId, toObjectId;
        String path;

        public int getDepth()
        {
            return depth;
        }

        public void setDepth(int depth)
        {
            this.depth = depth;
        }

        public int getSelf()
        {
            return self;
        }

        public void setSelf(int self)
        {
            this.self = self;
        }

        public Integer getFromObjectId()
        {
            return fromObjectId;
        }

        public void setFromObjectId(Integer fromObjectId)
        {
            this.fromObjectId = null==fromObjectId ? 0 : fromObjectId;
        }

        public Integer getToObjectId()
        {
            return toObjectId;
        }

        public void setToObjectId(Integer toObjectId)
        {
            this.toObjectId = null==toObjectId ? 0 : toObjectId;
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = path;
        }
    }

    public static class LineageQueryTestCase extends Assert
    {
        TempTableTracker tt;
        String tableName;

        // create graph that looks like this
        //        QQ
        //        |
        //    A   Q   Z
        //   / \ / \ /  \
        // A1  A2  Z1   Z2
        //  |   \  /    |
        // A11   AZ     Z21
        static public final int RUN = Integer.MAX_VALUE;
        static final int startId = 1_000_000_000;
        static public final int QQ = startId;
        static public final int A = startId+1;
        static public final int Q = startId+2;
        static public final int Z = startId+3;
        static public final int A1 = startId+4;
        static public final int A2 = startId+5;
        static public final int Z1 = startId+6;
        static public final int Z2 = startId+7;
        static public final int A11 = startId+8;
        static public final int AZ = startId+9;
        static public final int Z21 = startId+10;

        static edge e(int a,int b) {return new edge(a,b);};
        static public final List<edge> edges = List.of(
                e(Q,QQ),
                e(A1,A), e(A2,A), e(A2, Q), e(Z1, Q), e(Z1, Z), e(Z2, Z),
                e(A11, A1), e(AZ,A2), e(AZ, Z1), e(Z21, Z2)
        );

        Map<String,String> getParts(ExpLineageOptions options, String csv)
        {
            String jspPath = options.isForLookup() ? "/org/labkey/experiment/api/ExperimentRunGraphForLookup2.jsp" : "/org/labkey/experiment/api/ExperimentRunGraph2.jsp";

            String sourceSQL;
            try
            {
                sourceSQL = new JspTemplate<>(jspPath, options).render();
            }
            catch (Exception e)
            {
                throw UnexpectedException.wrap(e);
            }

            Map<String,String> map = new HashMap<>();
            SqlDialect dialect = getExpSchema().getSqlDialect();

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
                if (name.equals("$PARENTS_INNER$") || name.equals("$CHILDREN_INNER$"))
                {
                    select = select.replace("$LSIDS$", "VALUES (" + csv + ")");
                    if (options.getSourceKey() != null)
                        select = select.replace("$SOURCEKEY$", dialect.getStringHandler().quoteStringLiteral(options.getSourceKey()));
                }
                map.put(name, select);
            }
            return map;
        }


        List<InnerResult> getParents(int seed)
        {
            SqlDialect d = getExpSchema().getSqlDialect();
            var maps = getParts(new _ExpLineageOptions(tableName), String.valueOf(seed));
            String parentsInner = maps.get("$PARENTS_INNER$").replace("$SELF$", "parents");
            SQLFragment sql = new SQLFragment()
                    .append(d.isPostgreSQL() ? "WITH RECURSIVE" : "WITH").append(" parents AS (").append(parentsInner).append(")\n")
                    .append("SELECT * FROM parents WHERE self != fromObjectId");
            List<InnerResult> results = new SqlSelector(getExpSchema(),sql).getArrayList(InnerResult.class);
            return results;
        }

        List<InnerResult> getChildren(int seed)
        {
            SqlDialect d = getExpSchema().getSqlDialect();
            var maps = getParts(new _ExpLineageOptions(tableName), String.valueOf(seed));
            String childrenInner = maps.get("$CHILDREN_INNER$").replace("$SELF$", "children");
            SQLFragment sql = new SQLFragment()
                    .append(d.isPostgreSQL() ? "WITH RECURSIVE" : "WITH").append(" children AS (").append(childrenInner).append(")\n")
                    .append("SELECT * FROM children WHERE self != toObjectId");
            List<InnerResult> results = new SqlSelector(getExpSchema(),sql).getArrayList(InnerResult.class);
            return results;
        }

        String path(int... id)
        {
            return "/" + StringUtils.join(id,'/') + "/";
        }

        @Before
        public void setUp()
        {
            // It's actually a pain to use the real edges table so create a test clone
            DbSchema tempSchema = DbSchema.getTemp();
            String name = "edges" + GUID.makeHash();
            tableName = tempSchema.getName() + "." + name;
            tt = TempTableTracker.track(name,this);
            new SqlExecutor(getExpSchema()).execute("SELECT * INTO " + tableName + " FROM exp.edge WHERE 0=1");
            for (var e : edges)
                new SqlExecutor(getExpSchema()).execute(new SQLFragment(
                        "INSERT INTO " + tableName + " (fromObjectId, toObjectId, runid) VALUES (?,?,?)", e.from, e.to, RUN));
        }

        @After
        public void tearDown()
        {
            tt.delete();
        }

        @Test
        public void testParentsInner()
        {
            {
                var results = getParents(A);
                assertEquals(0, results.size());
            }
            {
                var results = getParents(AZ);
                assertEquals(8, results.size());
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(A2, AZ))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(A, A2, AZ))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Q, A2, AZ))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(QQ, Q, A2, AZ))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Z1, AZ))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Z, Z1, AZ))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Q, Z1, AZ))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(QQ, Q, Z1, AZ))));
            }
        }

        @Test
        public void testChildrenInner()
        {
            {
                var results = getChildren(A11);
                assertEquals(0, results.size());
            }
            {
                var results = getChildren(Q);
                assertEquals(4, results.size());
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(AZ, A2, Q))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(AZ, Z1, Q))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(A2, Q))));
                assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Z1, Q))));
            }
        }

        @Test
        public void testCycle()
        {
            try
            {
                new SqlExecutor(getExpSchema()).execute(new SQLFragment(
                        "INSERT INTO " + tableName + " (fromObjectId, toObjectId, runid) VALUES (?,?,?)", AZ, QQ, RUN));

                {
                    var results = getParents(A2);
                    assert(null != results);
                    assertEquals(6, results.size());
                    // loop should break short of self (A2->Q->QQ->AZ->A2)
                    assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(AZ,QQ,Q,A2))));
                    assertTrue(results.stream().map(r -> r.path).noneMatch(p -> p.equals(path(A2,AZ,QQ,Q,A2))));
                    // loop should break short of loop that does not contain self (A2->Q->QQ->AZ->Z1->Q)
                    assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Z1,AZ,QQ,Q,A2))));
                    assertTrue(results.stream().map(r -> r.path).noneMatch(p -> p.equals(path(Q,Z1,AZ,QQ,Q,A2))));
                }
                {
                    var results = getChildren(A2);
                    assertEquals(4, results.size());
                    assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(AZ,A2))));
                    assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(QQ,AZ,A2))));
                    assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Q, QQ,AZ,A2))));
                    assertTrue(results.stream().map(r -> r.path).anyMatch(p -> p.equals(path(Z1,Q,QQ,AZ,A2))));
                }
            }
            finally
            {
                new SqlExecutor(getExpSchema()).execute(new SQLFragment(
                        "DELETE FROM " + tableName + " WHERE fromObjectId=? AND toObjectId=?", AZ, QQ));
            }
        }
    }

    public static class ParseInputOutputAliasTestCase extends Assert
    {
        @Test
        public void nullCases()
        {
            assertNull(ExperimentService.parseInputOutputAlias(""));
            assertNull(ExperimentService.parseInputOutputAlias(" "));
            assertNull(ExperimentService.parseInputOutputAlias("bogus"));
            assertNull(ExperimentService.parseInputOutputAlias(ExpData.DATA_INPUT_PARENT));
            assertNull(ExperimentService.parseInputOutputAlias(ExpData.DATA_OUTPUT_CHILD));
            assertNull(ExperimentService.parseInputOutputAlias(ExpMaterial.MATERIAL_INPUT_PARENT));
            assertNull(ExperimentService.parseInputOutputAlias(ExpMaterial.MATERIAL_OUTPUT_CHILD));
        }

        @Test
        public void nonNullCases()
        {
            nonNullCases(ExpData.DATA_INPUT_PARENT);
            nonNullCases(ExpData.DATA_OUTPUT_CHILD);
            nonNullCases(ExpMaterial.MATERIAL_INPUT_PARENT);
            nonNullCases(ExpMaterial.MATERIAL_OUTPUT_CHILD);
        }

        private void nonNullCases(String prefix)
        {
            Pair<String, String> pair = ExperimentService.parseInputOutputAlias(prefix + "/foo");
            assertEquals(prefix, pair.first);
            assertEquals("foo", pair.second);

            pair = ExperimentService.parseInputOutputAlias(prefix + "/foo.bar");
            assertEquals(prefix, pair.first);
            assertEquals("foo.bar", pair.second);

            pair = ExperimentService.parseInputOutputAlias(prefix + "/foo$Pbar");
            assertEquals(prefix, pair.first);
            assertEquals("foo$Pbar", pair.second);

            pair = ExperimentService.parseInputOutputAlias(prefix + "/foo/bar");
            assertEquals(prefix, pair.first);
            assertEquals("foo/bar", pair.second);

            pair = ExperimentService.parseInputOutputAlias(prefix + "/foo$Sbar");
            assertEquals(prefix, pair.first);
            assertEquals("foo$Sbar", pair.second);
        }
    }

    private static class _ExpLineageOptions extends ExpLineageOptions
    {
        final String _tableName;

        _ExpLineageOptions(String tableName)
        {
            _tableName = tableName;
            setUseObjectIds(true);
            setForLookup(true);
        }
        @Override
        public String getExpEdge()
        {
            return _tableName;
        }
    }

    private record NameExpressionType(String dataType, String schemaName, String queryName, String nameExpressionCol)
    {
    }
}
