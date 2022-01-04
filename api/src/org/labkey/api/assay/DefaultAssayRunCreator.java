/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
package org.labkey.api.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.assay.actions.AssayRunUploadForm;
import org.labkey.api.assay.pipeline.AssayRunAsyncContext;
import org.labkey.api.assay.pipeline.AssayUploadPipelineJob;
import org.labkey.api.assay.plate.PlateMetadataDataHandler;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExpDataFileConverter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.ParticipantVisitResolver;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableCollection;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public class DefaultAssayRunCreator<ProviderType extends AbstractAssayProvider> implements AssayRunCreator<ProviderType>
{
    private static final Logger LOG = LogManager.getLogger(DefaultAssayRunCreator.class);
    public static final String CROSS_RUN_DATA_INPUT_ROLE = "cross run input";

    private final ProviderType _provider;

    public DefaultAssayRunCreator(ProviderType provider)
    {
        _provider = provider;
    }

    public TransformResult transform(AssayRunUploadContext<ProviderType> context, ExpRun run) throws ValidationException
    {
        DataTransformer<ProviderType> transformer = new DefaultDataTransformer<>();
        return transformer.transformAndValidate(context, run);
    }
    @Override
    public Pair<ExpExperiment, ExpRun> saveExperimentRun(AssayRunUploadContext<ProviderType> context, @Nullable Integer batchId) throws ExperimentException, ValidationException
    {
        return saveExperimentRun(context, batchId, false);
    }

    /**
     * Create and save an experiment run synchronously or asynchronously in a background job depending upon the assay design.
     *
     * @param context The context used to create and save the batch and run.
     * @param batchId if not null, the run group that's already created for this batch. If null, a new one will be created.
     * @return Pair of batch and run that were inserted.  ExpBatch will not be null, but ExpRun may be null when inserting the run async.
     */
    @Override
    public Pair<ExpExperiment, ExpRun> saveExperimentRun(AssayRunUploadContext<ProviderType> context, @Nullable Integer batchId, boolean forceAsync) throws ExperimentException, ValidationException
    {
        ExpExperiment exp = null;
        if (batchId != null)
        {
            exp = ExperimentService.get().getExpExperiment(batchId);
        }

        AssayProvider provider = context.getProvider();
        ExpProtocol protocol = context.getProtocol();
        ExpRun run = null;
        context.init();

        // Check if assay protocol is configured to import in the background.
        // Issue 26811: If we don't have a view, assume that we are on a background job thread already.
        boolean importInBackground = forceAsync || (provider.isBackgroundUpload(protocol) && HttpView.hasCurrentView());
        if (!importInBackground)
        {
            File primaryFile = context.getUploadedData().get(AssayDataCollector.PRIMARY_FILE);
            run = AssayService.get().createExperimentRun(context.getName(), context.getContainer(), protocol, primaryFile);
            run.setComments(context.getComments());
            run.setWorkflowTaskId(context.getWorkflowTask());

            exp = saveExperimentRun(context, exp, run, false);

            // re-fetch the run after is has been fully constructed
            run = ExperimentService.get().getExpRun(run.getRowId());

            context.uploadComplete(run);
        }
        else
        {
            context.uploadComplete(null);
            exp = saveExperimentRunAsync(context, exp);
        }

        return Pair.of(exp, run);
    }

    private ExpExperiment saveExperimentRunAsync(AssayRunUploadContext<ProviderType> context, @Nullable ExpExperiment batch) throws ExperimentException
    {
        try
        {
            // Whether or not we need to save batch properties
            boolean forceSaveBatchProps = false;
            if (batch == null)
            {
                // No batch yet, so make one
                batch = AssayService.get().createStandardBatch(context.getContainer(), null, context.getProtocol());
                batch.save(context.getUser());
                // It's brand new, so we need to eventually set its properties
                forceSaveBatchProps = true;
            }

            // Queue up a pipeline job to do the actual import in the background
            ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());

            File primaryFile = context.getUploadedData().get(AssayDataCollector.PRIMARY_FILE);
            // Check if the primary file from the previous import is no longer present for a re-run
            if (primaryFile == null && !context.getUploadedData().isEmpty())
            {
                // Choose another file as the primary
                primaryFile = context.getUploadedData().entrySet().iterator().next().getValue();
            }
            AssayRunAsyncContext asyncContext = context.getProvider().createRunAsyncContext(context);
            final AssayUploadPipelineJob<ProviderType> pipelineJob = new AssayUploadPipelineJob<ProviderType>(
                    asyncContext,
                    info,
                    batch,
                    forceSaveBatchProps,
                    PipelineService.get().getPipelineRootSetting(context.getContainer()),
                    primaryFile);

            context.setPipelineJobGUID(pipelineJob.getJobGUID());

            // Don't queue the job until the transaction is committed, since otherwise the thread
            // that's running the job might start before it can access the job's row in the database.
            ExperimentService.get().getSchema().getScope().addCommitTask(() -> {
                try
                {
                    PipelineService.get().queueJob(pipelineJob, asyncContext.getJobNotificationProvider());
                }
                catch (PipelineValidationException e)
                {
                    throw new UnexpectedException(e);
                }
            }, DbScope.CommitTaskOption.POSTCOMMIT);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }

        return batch;
    }

    /**
     * @param batch if not null, the run group that's already created for this batch. If null, a new one needs to be created
     * @param run The run to save
     * @return the run and batch that were inserted
     */
    @Override
    public ExpExperiment saveExperimentRun(final AssayRunUploadContext<ProviderType> context, @Nullable ExpExperiment batch, @NotNull ExpRun run, boolean forceSaveBatchProps) throws ExperimentException, ValidationException
    {
        final Container container = context.getContainer();

        Map<ExpMaterial, String> inputMaterials = new HashMap<>();
        Map<ExpData, String> inputDatas = new HashMap<>();
        Map<ExpMaterial, String> outputMaterials = new HashMap<>();
        Map<ExpData, String> outputDatas = new HashMap<>();
        Map<ExpData, String> transformedDatas = new HashMap<>();

        Map<DomainProperty, String> runProperties = context.getRunProperties();
        Map<String, Object> unresolvedRunProperties = context.getUnresolvedRunProperties();
        Map<DomainProperty, String> batchProperties = context.getBatchProperties();

        Map<DomainProperty, String> allProperties = new HashMap<>();
        allProperties.putAll(runProperties);
        allProperties.putAll(batchProperties);

        ParticipantVisitResolverType resolverType = null;
        for (Map.Entry<DomainProperty, String> entry : allProperties.entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                resolverType = AbstractAssayProvider.findType(entry.getValue(), getProvider().getParticipantVisitResolverTypes());
                if (resolverType != null)
                {
                    resolverType.configureRun(context, run, inputDatas);
                }
                break;
            }
        }

        // TODO: Share these RemapCache and materialCache instances with AbstractAssayTsvDataHandler.checkData and ExpressionMatrixDataHandler.importFile
        // Cache of resolved alternate lookup keys -> rowId
        final RemapCache cache = new RemapCache(true);
        // Cache of rowId -> ExpMaterial
        final Map<Integer, ExpMaterial> materialCache = new HashMap<>();

        addInputMaterials(context, inputMaterials, resolverType, cache, materialCache);
        addInputDatas(context, inputDatas, resolverType);
        addOutputMaterials(context, outputMaterials, resolverType, cache, materialCache);
        addOutputDatas(context, inputDatas, outputDatas, resolverType);

        DbScope scope = ExperimentService.get().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction(ExperimentService.get().getProtocolImportLock()))
        {
            boolean saveBatchProps = forceSaveBatchProps;

            // Add any material/data inputs related to the specimen IDs, etc in the incoming data.
            // Some subclasses may actually create ExpMaterials or do other database changes, so do this inside the
            // overall transaction
            resolveParticipantVisits(context, inputMaterials, inputDatas, outputMaterials, outputDatas, allProperties, resolverType);

            // Check for circular inputs/outputs
            checkForCycles(context, inputMaterials, outputMaterials);
            checkForCycles(context, inputDatas, outputDatas);

            // Create the batch, if needed
            if (batch == null)
            {
                // Make sure that we have a batch to associate with this run
                batch = AssayService.get().createStandardBatch(run.getContainer(), null, context.getProtocol());
                batch.save(context.getUser());
                saveBatchProps = true;
            }
            run.save(context.getUser());
            // Add the run to the batch so that we can find it when we're loading the data files
            batch.addRuns(context.getUser(), run);
            assert batch.equals(run.getBatch()) : "Run's batch should be the current batch";

            ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
            XarContext xarContext = new AssayUploadXarContext("Simple Run Creation", context);

            run = ExperimentService.get().saveSimpleExperimentRun(run,
                    inputMaterials,
                    inputDatas,
                    outputMaterials,
                    outputDatas,
                    transformedDatas,
                    info,
                    context.getLogger() != null ? context.getLogger() : LOG,
                    false);

            // handle data transformation
            TransformResult transformResult = transform(context, run);
            List<ExpData> insertedDatas = new ArrayList<>();

            if (transformResult.getWarnings() != null && context instanceof AssayRunUploadForm)
            {
                context.setTransformResult(transformResult);
                ((AssayRunUploadForm)context).setName(run.getName());
                ((AssayRunUploadForm) context).setComments(run.getComments());
                throw new ValidationException(" ");
            }

            if (saveBatchProps)
            {
                if (!transformResult.getBatchProperties().isEmpty())
                {
                    Map<DomainProperty, String> props = transformResult.getBatchProperties();
                    List<ValidationError> errors = validateProperties(context, props);
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);
                    savePropertyObject(batch, container, props, context.getUser());
                }
                else
                    savePropertyObject(batch, container, batchProperties, context.getUser());
            }

            if (null != transformResult.getAssayId())
                run.setName(transformResult.getAssayId());
            if (null != transformResult.getComments())
                run.setComments(transformResult.getComments());
            if (!transformResult.getRunProperties().isEmpty())
            {
                Map<DomainProperty, String> props = transformResult.getRunProperties();
                List<ValidationError> errors = validateProperties(context, props);
                if (!errors.isEmpty())
                    throw new ValidationException(errors);
                savePropertyObject(run, container, props, context.getUser());
            }
            else
                savePropertyObject(run, container, runProperties, context.getUser());

            importResultData(context, run, inputDatas, outputDatas, info, xarContext, transformResult, insertedDatas);

            Integer reRunId = context.getReRunId();
            if (reRunId != null && getProvider().getReRunSupport() == AssayProvider.ReRunSupport.ReRunAndReplace)
            {
                final ExpRun replacedRun = ExperimentService.get().getExpRun(reRunId);
                // Make sure the run to be replaced is still around
                if (replacedRun != null)
                {
                    if (replacedRun.getContainer().hasPermission(context.getUser(), UpdatePermission.class))
                    {
                        replacedRun.setReplacedByRun(run);
                        replacedRun.save(context.getUser());
                    }
                    ExperimentService.get().auditRunEvent(context.getUser(), context.getProtocol(), replacedRun, null, "Run id " + replacedRun.getRowId() + " was replaced by run id " + run.getRowId());

                    transaction.addCommitTask(() -> replacedRun.archiveDataFiles(context.getUser()), DbScope.CommitTaskOption.POSTCOMMIT);
                }
                else
                {
                    throw new ExperimentException("Unable to find run to be replaced (RowId " + reRunId + ")");
                }
            }

            AssayService.get().ensureUniqueBatchName(batch, context.getProtocol(), context.getUser());

            ExperimentService.get().onRunDataCreated(context.getProtocol(), run, container, context.getUser());

            transaction.commit();

            // Inspect the run properties for a “prov:objectInputs” property that is a list of LSID strings.
            // Attach run's starting protocol application with starting input LSIDs.
            Object provInputsProperty = unresolvedRunProperties.get(ProvenanceService.PROVENANCE_INPUT_PROPERTY);
            if (provInputsProperty != null)
            {
                ProvenanceService pvs = ProvenanceService.get();
                Set<String> runInputLSIDs = null;
                if (provInputsProperty instanceof String)
                {
                    // parse as a JSONArray of values or a comma-separated list of values
                    String provInputs = (String)provInputsProperty;
                    if (provInputs.startsWith("[") && provInputs.endsWith("]"))
                        provInputsProperty = new JSONArray(provInputs);
                    else
                        runInputLSIDs = Set.of(provInputs.split(","));
                }

                if (provInputsProperty instanceof JSONArray)
                {
                    runInputLSIDs = Arrays.stream(((JSONArray)provInputsProperty).toArray()).map(String::valueOf).collect(Collectors.toSet());
                }

                if (runInputLSIDs != null && !runInputLSIDs.isEmpty())
                {
                    ExpProtocolApplication inputProtocolApp = run.getInputProtocolApplication();
                    pvs.addProvenanceInputs(container, inputProtocolApp, runInputLSIDs);
                }
            }

            ExperimentService.get().queueSyncRunEdges(run);

            return batch;
        }
        catch (BatchValidationException e)
        {
            throw new ExperimentException(e);
        }
    }

    private void resolveParticipantVisits(AssayRunUploadContext<ProviderType> context, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, Map<DomainProperty, String> allProperties, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        try
        {
            ParticipantVisitResolver resolver = null;
            if (resolverType != null)
            {
                String targetStudyId = null;
                for (Map.Entry<DomainProperty, String> property : allProperties.entrySet())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(property.getKey().getName()))
                    {
                        targetStudyId = property.getValue();
                        break;
                    }
                }
                Container targetStudy = null;
                if (targetStudyId != null && targetStudyId.length() > 0)
                    targetStudy = ContainerManager.getForId(targetStudyId);

                resolver = resolverType.createResolver(
                        unmodifiableCollection(inputMaterials.keySet()),
                        unmodifiableCollection(inputDatas.keySet()),
                        unmodifiableCollection(outputMaterials.keySet()),
                        unmodifiableCollection(outputDatas.keySet()),
                        context.getContainer(),
                        targetStudy, context.getUser());
            }

            resolveExtraRunData(resolver, context, inputMaterials, inputDatas, outputMaterials, outputDatas);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    protected void importStandardResultData(AssayRunUploadContext<ProviderType> context, ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, ViewBackgroundInfo info, XarContext xarContext, TransformResult transformResult, List<ExpData> insertedDatas) throws ExperimentException, ValidationException
    {
        List<Map<String, Object>> rawData = context.getRawData();
        if (rawData != null)
        {
            insertedDatas.addAll(outputDatas.keySet());

            ExpData primaryData = null;
            // Decide which file to treat as the primary, to which the data rows will be attached
            for (Map.Entry<ExpData, String> entry : outputDatas.entrySet())
            {
                if (ExpDataRunInput.DEFAULT_ROLE.equalsIgnoreCase(entry.getValue()))
                {
                    primaryData = entry.getKey();
                }
            }
            if (primaryData == null && !insertedDatas.isEmpty())
                primaryData = insertedDatas.get(0);

            if (primaryData != null)
            {
                TsvDataHandler dataHandler = new TsvDataHandler();
                dataHandler.setAllowEmptyData(true);
                dataHandler.setRawPlateMetadata(context.getRawPlateMetadata());
                dataHandler.importRows(primaryData, context.getUser(), run, context.getProtocol(), getProvider(), rawData, null);
            }
        }
        else
        {
            for (Map.Entry<ExpData, String> entry : inputDatas.entrySet())
            {
                // skip any of the cross run inputData that are already in the outputData
                if (CROSS_RUN_DATA_INPUT_ROLE.equals(entry.getValue()))
                    continue;

                insertedDatas.add(entry.getKey());
            }

            insertedDatas.addAll(outputDatas.keySet());

            Logger logger = context.getLogger() != null ? context.getLogger() : LOG;
            for (ExpData insertedData : insertedDatas)
            {
                insertedData.findDataHandler().importFile(insertedData, insertedData.getFile(), info, logger, xarContext);
            }
        }
    }

    private void importResultData(AssayRunUploadContext<ProviderType> context, ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, ViewBackgroundInfo info, XarContext xarContext, TransformResult transformResult, List<ExpData> insertedDatas) throws ExperimentException, ValidationException
    {
        if (transformResult.getTransformedData().isEmpty())
        {
            importStandardResultData(context, run, inputDatas, outputDatas, info, xarContext, transformResult, insertedDatas);
        }
        else
        {
            DataType dataType = context.getProvider().getDataType();
            if (dataType == null)
                // we know that we are importing transformed data at this point
                dataType = TsvDataHandler.RELATED_TRANSFORM_FILE_DATA_TYPE;

            ExpData data = ExperimentService.get().createData(context.getContainer(), dataType);
            ExperimentDataHandler handler = data.findDataHandler();

            // this should assert to always be true
            if (handler instanceof TransformDataHandler)
            {
                for (Map.Entry<ExpData, List<Map<String, Object>>> entry : transformResult.getTransformedData().entrySet())
                {
                    ExpData expData = entry.getKey();
                    // The object may have already been claimed by
                    if (expData.getSourceApplication() == null)
                    {
                        expData.setSourceApplication(run.getOutputProtocolApplication());
                    }
                    expData.save(context.getUser());

                    run.getOutputProtocolApplication().addDataInput(context.getUser(), expData, ExpDataRunInput.IMPORTED_DATA_ROLE);
                    // Add to the cached list of outputs
                    run.getDataOutputs().add(expData);

                    ((TransformDataHandler)handler).importTransformDataMap(expData, context, run, entry.getValue());
                }
            }
        }
    }

    // See also AbstractAssayTsvDataHandler.resolveSampleNames
    protected void addInputMaterials(AssayRunUploadContext<ProviderType> context,
                                     Map<ExpMaterial, String> inputMaterials,
                                     ParticipantVisitResolverType resolverType,
                                     @NotNull RemapCache cache,
                                     @NotNull Map<Integer, ExpMaterial> materialCache)
            throws ExperimentException, ValidationException
    {
        Set<Container> searchContainers = ExpSchema.getSearchContainers(context.getContainer(), null, null, context.getUser());
        addMaterials(context, inputMaterials, context.getInputMaterials(), searchContainers, cache, materialCache);

        // Find lookups to a SampleType and add the resolved material as an input sample
        for (Map.Entry<DomainProperty, String> entry : context.getRunProperties().entrySet())
        {
            if (entry.getValue() == null)
                continue;

            DomainProperty dp = entry.getKey();
            PropertyType pt = dp.getPropertyType();
            if (pt == null)
                continue;

            // Lookup must point at "Samples.*", "exp.materials.*", or "exp.Materials"
            @Nullable ExpSampleType st = getLookupSampleType(dp, context.getContainer(), context.getUser());
            if (st == null && !isLookupToMaterials(dp))
                continue;

            // Use the DomainProperty name as the role
            String role = dp.getName();

            String value = entry.getValue();
            if (pt.getJdbcType().isText())
            {
                addMaterialByName(context, inputMaterials, value, role, searchContainers, st, cache, materialCache);
            }
            else if (pt.getJdbcType().isInteger())
            {
                try
                {
                    int sampleRowId = Integer.parseInt(value);
                    addMaterialById(context, inputMaterials, sampleRowId, role, searchContainers, st, materialCache);
                }
                catch (NumberFormatException ex)
                {
                    Logger logger = context.getLogger() != null ? context.getLogger() : LOG;
                    logger.warn("Failed to parse sample lookup '" + value + "' as integer.");
                }
            }
        }
    }

    /** returns the lookup ExpSampleType if the property has a lookup to samples.<SampleTypeName> or exp.materials.<SampleTypeName> and is an int or string. */
    @Nullable
    public static ExpSampleType getLookupSampleType(@NotNull DomainProperty dp, @NotNull Container container, @NotNull User user)
    {
        Lookup lookup = dp.getLookup();
        if (lookup == null)
            return null;

        // TODO: Use concept URI instead of the lookup target schema to determine if the column is a sample.
        if (!(SamplesSchema.SCHEMA_NAME.equalsIgnoreCase(lookup.getSchemaName()) || "exp.materials".equalsIgnoreCase(lookup.getSchemaName())))
            return null;

        JdbcType type = dp.getPropertyType().getJdbcType();
        if (!(type.isText() || type.isInteger()))
            return null;

        Container c = lookup.getContainer() != null ? lookup.getContainer() : container;
        return SampleTypeService.get().getSampleType(c, user, lookup.getQueryName());
    }

    /** returns true if the property has a lookup to exp.Materials and is an int or string. */
    public static boolean isLookupToMaterials(@NotNull DomainProperty dp)
    {
        Lookup lookup = dp.getLookup();
        if (lookup == null)
            return false;

        if (!(ExpSchema.SCHEMA_NAME.equalsIgnoreCase(lookup.getSchemaName()) && ExpSchema.TableType.Materials.name().equalsIgnoreCase(lookup.getQueryName())))
            return false;

        JdbcType type = dp.getPropertyType().getJdbcType();
        if (!(type.isText() || type.isInteger()))
            return false;

        return true;
    }

    protected void addInputDatas(AssayRunUploadContext<ProviderType> context,
                                 @NotNull Map<ExpData, String> inputDatas,
                                 ParticipantVisitResolverType resolverType) throws ExperimentException, ValidationException
    {
        Logger log = context.getLogger() != null ? context.getLogger() : LOG;

        Map<?, String> inputs = context.getInputDatas();
        addDatas(context.getContainer(), inputDatas, inputs, log);

        // Inspect the uploaded files which will be added as outputs of the run
        if (context.isAllowCrossRunFileInputs())
        {
            Map<String, File> files = context.getUploadedData();
            for (Map.Entry<String, File> entry : files.entrySet())
            {
                String key = entry.getKey();
                if (AssayDataCollector.PRIMARY_FILE.equals(key))
                {
                    File file = entry.getValue();

                    // Check if the file is created by a run
                    ExpData existingData = ExperimentService.get().getExpDataByURL(file, context.getContainer());
                    if (existingData != null && existingData.getRunId() != null && !inputDatas.containsKey(existingData))
                    {
                        // Add this file as an input to the run. When we add the outputs to the run, we will detect
                        // that this file was already added as an input and create a new exp.data for the same file
                        // path and attach it as an output.
                        log.debug("found existing cross run file input: name=" + existingData.getName() + ", rowId=" + existingData.getRowId() + ", dataFileUrl=" + existingData.getDataFileUrl());
                        inputDatas.put(existingData, CROSS_RUN_DATA_INPUT_ROLE);
                    }
                }
            }
        }
    }

    // CONSIDER: Move this to ExperimentService
    // Resolve submitted values into ExpData objects
    protected void addDatas(Container c, @NotNull Map<ExpData, String> resolved, @NotNull Map<?, String> unresolved, @Nullable Logger log) throws ValidationException
    {
        ExpDataFileConverter expDataFileConverter = new ExpDataFileConverter();

        for (Map.Entry<?, String> entry : unresolved.entrySet())
        {
            Object o = entry.getKey();
            String role = entry.getValue();

            if (o instanceof ExpData)
            {
                resolved.put((ExpData)o, role);
            }
            else
            {
                File file = (File)expDataFileConverter.convert(File.class, o);
                if (file != null)
                {
                    ExpData data = ExperimentService.get().getExpDataByURL(file, c);
                    if (data == null)
                    {
                        DataType dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
                        data = createData(c, file, file.getName(), dataType, false, true, log);
                    }

                    resolved.put(data, role);
                }
            }
        }
    }

    public static ExpData generateResultData(User user, Container container, AssayProvider provider, List<Map<String, Object>> dataArray, Map<Object, String> outputData) throws ValidationException
    {
        return generateResultData(user, container, provider, dataArray, outputData, null);
    }

    public static ExpData generateResultData(User user, Container container, AssayProvider provider, List<Map<String, Object>> dataArray, Map<Object, String> outputData, @Nullable Logger log) throws ValidationException
    {
        if (log == null)
            log = LOG;

        ExpData newData = null;

        // Don't create an empty result data file if there are other outputs from this run, or if the user didn't
        // include any data rows
        if (dataArray.size() > 0 && outputData.isEmpty())
        {
            DataType dataType = provider.getDataType();
            if (dataType == null)
                dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;

            newData = createData(container, "Analysis Results", dataType, log);
            newData.save(user);
            outputData.put(newData, ExpDataRunInput.DEFAULT_ROLE);
        }

        return newData;
    }

    // Find an existing ExpData for the File or null.
    public static @Nullable ExpData findExistingData(Container c, @Nullable File file, @Nullable Logger log)
    {
        if (file == null)
            return null;

        if (log == null)
            log = LOG;

        List<? extends ExpData> existing = ExperimentService.get().getAllExpDataByURL(file, c);
        if (!existing.isEmpty())
        {
            for (ExpData d : existing)
            {
                log.debug("found existing exp.data for file, rowId=" + d.getRowId() + ", runId=" + d.getRunId() + ", dataFileUrl=" + d.getDataFileUrl());
            }

            // pick the most recently created one
            return existing.get(0);
        }

        return null;
    }

    public static @NotNull ExpData createData(Container c, String name, @NotNull DataType dataType, @Nullable Logger log) throws ValidationException
    {
        // NOTE: reuseExistingData and errorOnDataOwned flags are irrelevant when we aren't providing a File
        return createData(c, null, name, dataType, false, false, log);
    }

    public static @NotNull ExpData createData(Container c, File file, String name, @Nullable DataType dataType, boolean reuseExistingData, boolean errorIfDataOwned, @Nullable Logger log) throws ValidationException
    {
        if (log == null)
            log = LOG;

        ExpData data = findExistingData(c, file, log);

        ExpRun previousRun;
        if (data != null && null != (previousRun = data.getRun()))
        {
            // There's an existing data, but it's already marked as being created by another run
            String msg = "File '" + data.getName() + "' has been previously imported in run '" + previousRun.getName() + "' (" + previousRun.getRowId() + ")";
            if (reuseExistingData && errorIfDataOwned)
                throw new ValidationException(msg);

            log.debug(msg);

            // Create a new one for the same path so the new run can claim it as its own
            if (!reuseExistingData)
            {
                log.debug("ignoring existing exp.data, will create a new one");
                data = null;
            }
        }

        if (data == null)
        {
            if (dataType == null)
                dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;

            log.debug("creating assay exp.data for file. dataType=" + dataType.getNamespacePrefix() + ", file=" + file);
            data = ExperimentService.get().createData(c, dataType, name);
            data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
            if (file != null)
            {
                data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
            }
        }
        else
        {
            if (dataType != null && !dataType.matches(new Lsid(data.getLSID())))
            {
                // Reset its LSID so that it's the correct type // CONSIDER: creating a new ExpData with the correct type instead
                String newLsid = ExperimentService.get().generateGuidLSID(c, dataType);
                log.debug("LSID doesn't match desired type. Changed the LSID from '" + data.getLSID() + "' to '" + newLsid + "'");
                data.setLSID(newLsid);
            }
        }
        return data;
    }

    protected void addOutputMaterials(AssayRunUploadContext<ProviderType> context,
                                      Map<ExpMaterial, String> outputMaterials,
                                      ParticipantVisitResolverType resolverType,
                                      @NotNull RemapCache cache,
                                      @NotNull Map<Integer, ExpMaterial> materialCache)
            throws ExperimentException, ValidationException
    {
        Set<Container> searchContainers = ExpSchema.getSearchContainers(context.getContainer(), null, null, context.getUser());
        addMaterials(context, outputMaterials, context.getOutputMaterials(), searchContainers, cache, materialCache);
    }

    // CONSIDER: Move this to ExperimentService
    // Resolve submitted values into ExpMaterial objects
    protected void addMaterials(AssayRunUploadContext<ProviderType> context,
                                @NotNull Map<ExpMaterial, String> resolved,
                                @NotNull Map<?, String> unresolved,
                                @NotNull Set<Container> searchContainers,
                                @NotNull RemapCache cache,
                                @NotNull Map<Integer, ExpMaterial> materialCache)
            throws ExperimentException, ValidationException
    {
        for (Map.Entry<?, String> entry : unresolved.entrySet())
        {
            Object o = entry.getKey();
            String role = entry.getValue();

            if (o instanceof ExpMaterial)
            {
                ExpMaterial m = (ExpMaterial)o;
                if (!resolved.containsKey(m) && searchContainers.contains(m.getContainer()))
                    resolved.put(m, role);
            }
            else if (o instanceof Integer)
            {
                addMaterialById(context, resolved, (Integer)o, role, searchContainers, null, materialCache);
            }
            else if (o instanceof String)
            {
                addMaterialByName(context, resolved, (String)o, role, searchContainers, null, cache, materialCache);
            }
            else
                throw new ExperimentException("Unable to resolve sample: " + o);
        }
    }

    protected void addMaterialByName(AssayRunUploadContext<ProviderType> context,
                                     Map<ExpMaterial, String> resolved,
                                     String sampleName,
                                     String role,
                                     @NotNull Set<Container> searchContainers,
                                     @Nullable ExpSampleType st,
                                     @NotNull RemapCache cache,
                                     @NotNull Map<Integer, ExpMaterial> materialCache)
            throws ValidationException
    {
        ExpMaterial material = ExperimentService.get().findExpMaterial(context.getContainer(), context.getUser(), null, null, sampleName, cache, materialCache, true);
        if (material == null)
        {
            Logger logger = context.getLogger() != null ? context.getLogger() : LOG;
            logger.warn("No sample found for sample name '" + sampleName + "'");
        }

        if (material != null && !resolved.containsKey(material) && searchContainers.contains(material.getContainer()))
        {
            if (st == null || st.getLSID().equals(material.getCpasType()))
                resolved.put(material, role);
        }
    }

    protected void addMaterialById(AssayRunUploadContext<ProviderType> context,
                                   Map<ExpMaterial, String> resolved,
                                   Integer sampleRowId,
                                   String role,
                                   @NotNull Set<Container> searchContainers,
                                   @Nullable ExpSampleType st,
                                   @NotNull Map<Integer, ExpMaterial> materialCache) throws ExperimentException
    {
        final Container c = context.getContainer();
        final User user = context.getUser();
        ExpMaterial material = materialCache.computeIfAbsent(sampleRowId, (x) -> ExperimentService.get().getExpMaterial(c, user, sampleRowId, null));

        if (material != null && !resolved.containsKey(material) && searchContainers.contains(material.getContainer()))
        {
            if (!material.isOperationPermitted(SampleTypeService.SampleOperations.AddAssayData))
                throw new ExperimentException(SampleTypeService.get().getOperationNotPermittedMessage(Collections.singleton(material), SampleTypeService.SampleOperations.AddAssayData));
            if (st == null || st.getLSID().equals(material.getCpasType()))
                resolved.put(material, role);
        }
    }

    protected void addOutputDatas(AssayRunUploadContext<ProviderType> context, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException, ValidationException
    {
        Logger log = context.getLogger() != null ? context.getLogger() : LOG;

        // Create set of existing input files
        Set<File> inputFiles = new HashSet<>();
        for (ExpData inputData : inputDatas.keySet())
        {
            File f = inputData.getFile();
            if (f != null)
                inputFiles.add(f);
        }

        Map<String, File> files = context.getUploadedData();

        AssayDataType dataType;
        for (Map.Entry<String, File> entry : files.entrySet())
        {
            String key = entry.getKey();
            File file = entry.getValue();

            if (key.equals(AssayDataCollector.PLATE_METADATA_FILE))
                dataType = PlateMetadataDataHandler.DATA_TYPE;
            else
                dataType = context.getProvider().getDataType();

            // Reuse existing exp.data as the assay output file unless:
            // - we are re-importing the run
            // - or the output file is already one of the input files and if we are allowing cross-run file inputs
            boolean reuseExistingData = true;
            if (context.getReRunId() != null)
                reuseExistingData = false;
            if (context.isAllowCrossRunFileInputs() && inputFiles.contains(file))
                reuseExistingData = false;

            // For Luminex re-import, we want to reuse the existing exp.data but not
            // throw an error when we discover that the exp.data is already owned. The
            // original run will be duplicated for re-import and then will be deleted.
            boolean errorIfDataOwned = getProvider().getReRunSupport() != AssayProvider.ReRunSupport.ReRunAndDelete;

            log.debug("adding output data: file=" + file.getPath());
            log.debug("  context.getReRunId()=" + context.getReRunId());
            log.debug("  provider.getReRunSupport()=" + getProvider().getReRunSupport());
            log.debug("  context.allowCrossRunFileInputs=" + context.isAllowCrossRunFileInputs());
            log.debug("  inputFiles.contains(file)=" + inputFiles.contains(file));
            log.debug("==> reuseExistingData = " + reuseExistingData);
            log.debug("==> errorIfDataOwned = " + errorIfDataOwned);

            ExpData data = DefaultAssayRunCreator.createData(context.getContainer(), file, file.getName(), dataType, reuseExistingData, errorIfDataOwned, log);
            String role = ExpDataRunInput.DEFAULT_ROLE;
            if (dataType != null && dataType.getFileType().isType(file))
            {
                if (dataType.getRole() != null)
                {
                    role = dataType.getRole();
                }
            }
            outputDatas.put(data, role);
        }

        File primaryFile = files.get(AssayDataCollector.PRIMARY_FILE);
        if (primaryFile != null)
        {
            addRelatedOutputDatas(context, inputFiles, outputDatas, primaryFile);
        }

        Map<?, String> outputs = context.getOutputDatas();
        addDatas(context.getContainer(), outputDatas, outputs, log);
    }

    /**
     * Add files that follow the general naming convention (same basename) as the primary file
     */
    public void addRelatedOutputDatas(AssayRunUploadContext context, Set<File> inputFiles, Map<ExpData, String> outputDatas, final File primaryFile) throws ValidationException
    {
        AssayDataType dataType = getProvider().getDataType();
        final String baseName = dataType == null ? null : dataType.getFileType().getBaseName(primaryFile);
        if (baseName != null)
        {
            // Grab all the files that are related based on naming convention
            File[] relatedFiles = primaryFile.getParentFile().listFiles(getRelatedOutputDataFileFilter(primaryFile, baseName));
            if (relatedFiles != null)
            {
                for (File relatedFile : relatedFiles)
                {
                    // Ignore files already considered inputs to the run
                    if (inputFiles.contains(relatedFile))
                        continue;

                    Pair<ExpData, String> dataOutput = createdRelatedOutputData(context, baseName, relatedFile);
                    if (dataOutput != null)
                    {
                        outputDatas.put(dataOutput.getKey(), dataOutput.getValue());
                    }
                }
            }
        }
    }

    protected void resolveExtraRunData(ParticipantVisitResolver resolver,
                                  AssayRunUploadContext context,
                                  Map<ExpMaterial, String> inputMaterials,
                                  Map<ExpData, String> inputDatas,
                                  Map<ExpMaterial, String> outputMaterials,
                                  Map<ExpData, String> outputDatas) throws ExperimentException
    {

    }

    /**
     * Create an ExpData object for the file, and figure out what its role name should be
     * @return null if the file is already linked to another run
     */
    @Nullable
    public static Pair<ExpData, String> createdRelatedOutputData(AssayRunUploadContext context, String baseName, File relatedFile) throws ValidationException
    {
        String roleName = null;
        DataType dataType = null;
        for (AssayDataType inputType : context.getProvider().getRelatedDataTypes())
        {
            // Check if we recognize it as a specially handled file type
            if (inputType.getFileType().isMatch(relatedFile.getName(), baseName))
            {
                roleName = inputType.getRole();
                dataType = inputType;
                break;
            }
        }
        // If not, make up a new type and role for it
        if (roleName == null)
        {
            roleName = relatedFile.getName().substring(baseName.length());
            while (roleName.length() > 0 && (roleName.startsWith(".") || roleName.startsWith("-") || roleName.startsWith("_") || roleName.startsWith(" ")))
            {
                roleName = roleName.substring(1);
            }
            if ("".equals(roleName))
            {
                roleName = null;
            }
        }
        if (dataType == null)
        {
            dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
        }

        // Find an existing data that isn't owned by another run or create a new own
        ExpData data = findExistingData(context.getContainer(), relatedFile, context.getLogger());
        if (data != null)
        {
            if (data.getSourceApplication() == null)
                return new Pair<>(data, roleName);

            // The file is already linked to another run, so this one must have not created it
            return null;
        }
        else
        {
            data = createData(context.getContainer(), relatedFile, relatedFile.getName(), dataType, true, true, context.getLogger());
            assert data.getSourceApplication() == null;
            return new Pair<>(data, roleName);
        }
    }


    // Disallow creating a run with inputs which are also outputs
    protected void checkForCycles(AssayRunUploadContext<ProviderType> context, Map<? extends ExpRunItem, String> inputs, Map<? extends ExpRunItem, String> outputs) throws ExperimentException
    {
        for (ExpRunItem input : inputs.keySet())
        {
            if (outputs.containsKey(input))
            {
                String role = outputs.get(input);
                throw new ExperimentException("Circular input/output '" + input.getName() + "' with role '" + role + "'");
            }
        }
    }


    protected void savePropertyObject(ExpObject object, Container container, Map<DomainProperty, String> properties, User user) throws ExperimentException
    {
        try
        {
            for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
            {
                DomainProperty pd = entry.getKey();
                String value = entry.getValue();

                // resolve any file links for batch or run properties
                File resolvedFile = AssayUploadFileResolver.resolve(value, container, entry.getKey());
                if (resolvedFile != null)
                {
                    value = resolvedFile.getAbsolutePath();
                }

                // Treat the empty string as a null in the database, which is our normal behavior when receiving data
                // from HTML forms.
                if ("".equals(value))
                {
                    value = null;
                }
                if (value != null)
                {
                    object.setProperty(user, pd.getPropertyDescriptor(), value);
                }
                else
                {
                    // We still need to validate blanks
                    List<ValidationError> errors = new ArrayList<>();
                    OntologyManager.validateProperty(pd.getValidators(), pd.getPropertyDescriptor(), new ObjectProperty(object.getLSID(), object.getContainer(), pd.getPropertyDescriptor(), value), errors, new ValidatorContext(pd.getContainer(), user));
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);
                }
            }
        }
        catch (ValidationException ve)
        {
            throw new ExperimentException(ve.getMessage(), ve);
        }
    }

    public static List<ValidationError> validateColumnProperties(ContainerUser context, Map<ColumnInfo, String> properties)
    {
        List<ValidationError> errors = new ArrayList<>();
        RemapCache cache = new RemapCache();
        for (Map.Entry<ColumnInfo, String> entry : properties.entrySet())
        {
            validateProperty(context, entry.getKey(), entry.getValue(), cache, errors);
        }
        return errors;
    }

    public static List<ValidationError> validateProperties(ContainerUser context, Map<DomainProperty, String> properties)
    {
        List<ValidationError> errors = new ArrayList<>();
        RemapCache cache = new RemapCache();
        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            validateProperty(context, entry.getKey(), entry.getValue(), cache, errors);
        }
        return errors;
    }

    private static void validateProperty(ContainerUser context, ColumnInfo columnInfo, String value, RemapCache cache, List<ValidationError> errors)
    {
        Lookup lookup = null;
        if (columnInfo.isLookup())
        {
            ForeignKey fk = columnInfo.getFk();
            lookup = new Lookup(fk.getLookupContainer(), fk.getLookupSchemaName(), fk.getLookupTableName());
        }
        validateProperty(context, ColumnValidators.create(columnInfo, null), value, columnInfo.getName(),
                false, lookup, columnInfo.getJavaClass(), cache, errors);
    }

    private static void validateProperty(ContainerUser context, DomainProperty dp, String value, RemapCache cache, List<ValidationError> errors)
    {
        String label = dp.getPropertyDescriptor().getNonBlankCaption();
        PropertyType type = dp.getPropertyDescriptor().getPropertyType();
        validateProperty(context, ColumnValidators.create(null, dp), value, label, dp.isRequired(),
                dp.getLookup(), type.getJavaType(), cache, errors);
    }

    private static void validateProperty(ContainerUser context, List<ColumnValidator> validators, String value,
                                         String label, Boolean required, Lookup lookup, Class type, RemapCache cache,
                                         List<ValidationError> errors)
    {
        boolean missing = (value == null || value.length() == 0);
        int rowNum = 0;

        if (required && missing)
        {
            errors.add(new SimpleValidationError(label + " is required and must be of type " + ColumnInfo.getFriendlyTypeName(type) + "."));
        }
        else if (!missing)
        {
            try
            {
                Object o = ConvertUtils.convert(value, type);
                ValidatorContext validatorContext = new ValidatorContext(context.getContainer(), context.getUser());
                for (ColumnValidator validator : validators)
                {
                    String msg = validator.validate(rowNum, o, validatorContext);
                    if (msg != null)
                        errors.add(new PropertyValidationError(msg, label));
                }
            }
            catch (ConversionException e)
            {
                String message = ConvertHelper.getStandardConversionErrorMessage(value, label, type);
                if (e.getCause() instanceof ArithmeticException)
                    message += ": " + e.getCause().getLocalizedMessage();
                else
                    message += ".";

                // Attempt to resolve lookups by display value
                boolean skipError = false;
                if (lookup != null)
                {
                    Container container = lookup.getContainer() != null ? lookup.getContainer() : context.getContainer();
                    Object remappedValue = cache.remap(SchemaKey.fromParts(lookup.getSchemaName()), lookup.getQueryName(), context.getUser(), container, ContainerFilter.Type.CurrentPlusProjectAndShared, value);
                    if (remappedValue != null)
                        skipError = true;
                }

                if (!skipError)
                    errors.add(new SimpleValidationError(message));
            }
        }
    }

    protected FileFilter getRelatedOutputDataFileFilter(final File primaryFile, final String baseName)
    {
        return new FileFilter()
        {
            @Override
            public boolean accept(File f)
            {
                // baseName doesn't include the trailing '.', so add it here.  We want to associate myRun.jpg
                // with myRun.xls, but we don't want to associate myRun2.xls with myRun.xls (which will happen without
                // the trailing dot in the check).
                return f.getName().startsWith(baseName + ".") && !primaryFile.equals(f);
            }
        };
    }

    protected ProviderType getProvider()
    {
        return _provider;
    }
}
