/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExpDataFileConverter;
import org.labkey.api.data.JdbcType;
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
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
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
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.pipeline.AssayUploadPipelineJob;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableCollection;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public class DefaultAssayRunCreator<ProviderType extends AbstractAssayProvider> implements AssayRunCreator<ProviderType>
{
    private static final Logger LOG = Logger.getLogger(DefaultAssayRunCreator.class);

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

    /**
     * Create and save an experiment run synchronously or asynchronously in a background job depending upon the assay design.
     *
     * @param context The context used to create and save the batch and run.
     * @param batchId if not null, the run group that's already created for this batch. If null, a new one will be created.
     * @return Pair of batch and run that were inserted.  ExpBatch will not be null, but ExpRun may be null when inserting the run async.
     */
    @Override
    public Pair<ExpExperiment, ExpRun> saveExperimentRun(AssayRunUploadContext<ProviderType> context, @Nullable Integer batchId) throws ExperimentException, ValidationException
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
        boolean importInBackground = provider.isBackgroundUpload(protocol) && HttpView.hasCurrentView();
        if (!importInBackground)
        {
            File primaryFile = context.getUploadedData().get(AssayDataCollector.PRIMARY_FILE);
            run = AssayService.get().createExperimentRun(context.getName(), context.getContainer(), protocol, primaryFile);
            run.setComments(context.getComments());

            exp = saveExperimentRun(context, exp, run, false);
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
            final AssayUploadPipelineJob<ProviderType> pipelineJob = new AssayUploadPipelineJob<ProviderType>(
                    context.getProvider().createRunAsyncContext(context),
                    info,
                    batch,
                    forceSaveBatchProps,
                    PipelineService.get().getPipelineRootSetting(context.getContainer()),
                    primaryFile);

            // Don't queue the job until the transaction is committed, since otherwise the thread
            // that's running the job might start before it can access the job's row in the database.
            ExperimentService.get().getSchema().getScope().addCommitTask(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        PipelineService.get().queueJob(pipelineJob);
                    }
                    catch (PipelineValidationException e)
                    {
                        throw new UnexpectedException(e);
                    }
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
    public ExpExperiment saveExperimentRun(final AssayRunUploadContext<ProviderType> context, @Nullable ExpExperiment batch, @NotNull ExpRun run, boolean forceSaveBatchProps) throws ExperimentException, ValidationException
    {
        Map<ExpMaterial, String> inputMaterials = new HashMap<>();
        Map<ExpData, String> inputDatas = new HashMap<>();
        Map<ExpMaterial, String> outputMaterials = new HashMap<>();
        Map<ExpData, String> outputDatas = new HashMap<>();
        Map<ExpData, String> transformedDatas = new HashMap<>();

        Map<DomainProperty, String> runProperties = context.getRunProperties();
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

        addInputMaterials(context, inputMaterials, resolverType);
        addInputDatas(context, inputDatas, resolverType);
        addOutputMaterials(context, outputMaterials, resolverType);
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
                    savePropertyObject(batch, context.getContainer(), props, context.getUser());
                }
                else
                    savePropertyObject(batch, context.getContainer(), batchProperties, context.getUser());
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
                savePropertyObject(run, context.getContainer(), props, context.getUser());
            }
            else
                savePropertyObject(run, context.getContainer(), runProperties, context.getUser());

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

                    transaction.addCommitTask( new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            replacedRun.archiveDataFiles(context.getUser());
                        }
                    }, DbScope.CommitTaskOption.POSTCOMMIT);
                }
                else
                {
                    throw new ExperimentException("Unable to find run to be replaced (RowId " + reRunId + ")");
                }
            }

            AssayService.get().ensureUniqueBatchName(batch, context.getProtocol(), context.getUser());

            List<ValidationException> errors = ExperimentService.get().onRunDataCreated(context.getProtocol(), run, context.getContainer(), context.getUser());
            if (!errors.isEmpty())
            {
                StringBuilder errorMessage = new StringBuilder();
                for (ValidationException e : errors)
                {
                    errorMessage.append(e.getMessage()).append("\n");
                }
                throw new ExperimentException(errorMessage.toString());
            }

            ExperimentService.get().syncRunEdges(run);

            transaction.commit();
            return batch;
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
                dataHandler.importRows(primaryData, context.getUser(), run, context.getProtocol(), getProvider(), rawData, null);
            }
        }
        else
        {
            insertedDatas.addAll(inputDatas.keySet());
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
    protected void addInputMaterials(AssayRunUploadContext<ProviderType> context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Set<Container> searchContainers = ExpSchema.getSearchContainers(context.getContainer(), null, null, context.getUser());
        addMaterials(context, inputMaterials, context.getInputMaterials(), searchContainers);

        // Find lookups to a SampleSet and add the resolved material as an input sample
        for (Map.Entry<DomainProperty, String> entry : context.getRunProperties().entrySet())
        {
            if (entry.getValue() == null)
                continue;

            DomainProperty dp = entry.getKey();
            PropertyType pt = dp.getPropertyType();
            if (pt == null)
                continue;

            // Lookup must point at "Samples.*" or "exp.Materials"
            @Nullable ExpSampleSet ss = getLookupSampleSet(dp, context.getContainer());
            if (ss == null && !isLookupToMaterials(dp))
                continue;

            // Use the DomainProperty name as the role
            String role = dp.getName();

            String value = entry.getValue();
            if (pt.getJdbcType().isText())
            {
                addMaterialByName(context, inputMaterials, value, role, searchContainers, ss);
            }
            else if (pt.getJdbcType().isInteger())
            {
                try
                {
                    int sampleRowId = Integer.parseInt(value);
                    addMaterialById(context, inputMaterials, sampleRowId, role, searchContainers, ss);
                }
                catch (NumberFormatException ex)
                {
                    Logger logger = context.getLogger() != null ? context.getLogger() : LOG;
                    logger.warn("Failed to parse sample lookup '" + value + "' as integer.");
                }
            }
        }
    }

    @Nullable
    public static ExpSampleSet getLookupSampleSet(@NotNull DomainProperty dp, @NotNull Container container)
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
        return ExperimentService.get().getSampleSet(c, lookup.getQueryName(), true);
    }

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

    protected void addInputDatas(AssayRunUploadContext<ProviderType> context, @NotNull Map<ExpData, String> inputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Map<?, String> inputs = context.getInputDatas();
        addDatas(context.getContainer(), inputDatas, inputs);
    }

    // CONSIDER: Move this to ExperimentService
    // Resolve submitted values into ExpData objects
    protected void addDatas(Container c, @NotNull Map<ExpData, String> resolved, @NotNull Map<?, String> unresolved)
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
                        data = createData(c, file, file.getName(), dataType, false);
                    }

                    resolved.put(data, role);
                }
            }
        }
    }

    public static ExpData generateResultData(User user, Container container, AssayProvider provider, List<Map<String, Object>> dataArray, Map<Object, String> outputData)
    {
        ExpData newData = null;

        // Don't create an empty result data file if there are other outputs from this run, or if the user didn't
        // include any data rows
        if (dataArray.size() > 0 && outputData.isEmpty())
        {
            DataType dataType = provider.getDataType();
            if (dataType == null)
                dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;

            newData = createData(container, null, "Analysis Results", dataType, true);
            newData.save(user);
            outputData.put(newData, ExpDataRunInput.DEFAULT_ROLE);
        }

        return newData;
    }

    public static ExpData createData(Container c, File file, String name, @Nullable DataType dataType, boolean reuseExistingDatas)
    {
        ExpData data = null;
        if (file != null)
        {
            data = ExperimentService.get().getExpDataByURL(file, c);
        }
        if (!reuseExistingDatas && data != null && data.getRun() != null)
        {
            // There's an existing data, but it's already marked as being created by another run, so create a new one
            // for the same path so the new run claim it as its own
            data = null;
        }
        if (data == null)
        {
            if (dataType == null)
                dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;

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
                // Reset its LSID so that it's the correct type
                data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
            }
        }
        return data;
    }

    protected void addOutputMaterials(AssayRunUploadContext<ProviderType> context, Map<ExpMaterial, String> outputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Set<Container> searchContainers = ExpSchema.getSearchContainers(context.getContainer(), null, null, context.getUser());
        addMaterials(context, outputMaterials, context.getOutputMaterials(), searchContainers);
    }

    // CONSIDER: Move this to ExperimentService
    // Resolve submitted values into ExpMaterial objects
    protected void addMaterials(AssayRunUploadContext<ProviderType> context, @NotNull Map<ExpMaterial, String> resolved, @NotNull Map<?, String> unresolved, @NotNull Set<Container> searchContainers) throws ExperimentException
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
                addMaterialById(context, resolved, (Integer)o, role, searchContainers, null);
            }
            else if (o instanceof String)
            {
                addMaterialByName(context, resolved, (String)o, role, searchContainers, null);
            }
            else
                throw new ExperimentException("Unable to resolve sample: " + String.valueOf(o));
        }
    }

    protected void addMaterialByName(AssayRunUploadContext<ProviderType> context, Map<ExpMaterial, String> resolved, String sampleName, String role, @NotNull Set<Container> searchContainers, @Nullable ExpSampleSet ss) throws ExperimentException
    {
        // First, attempt to resolve by LSID
        ExpMaterial material = ExperimentService.get().getExpMaterial(sampleName);

        if (material == null)
        {
            // Next, attempt to resolve by name
            List<? extends ExpMaterial> matches = ExperimentService.get().getExpMaterials(context.getContainer(), context.getUser(), Collections.singleton(sampleName), ss, false, false);
            if (matches.size() == 0)
            {
                Logger logger = context.getLogger() != null ? context.getLogger() : LOG;
                logger.warn("No sample found for sample name '" + sampleName + "'");
            }
            else if (matches.size() == 1)
            {
                material = matches.get(0);
            }
            else if (matches.size() > 1)
            {
                Logger logger = context.getLogger() != null ? context.getLogger() : LOG;
                logger.warn("More than one sample found for sample name '" + sampleName + "'");
            }
        }

        if (material != null && !resolved.containsKey(material) && searchContainers.contains(material.getContainer()))
            resolved.put(material, role);
    }

    protected void addMaterialById(AssayRunUploadContext<ProviderType> context, Map<ExpMaterial, String> resolved, Integer sampleRowId, String role, @NotNull Set<Container> searchContainers, @Nullable ExpSampleSet ss)
    {
        ExpMaterial material = ExperimentService.get().getExpMaterial(sampleRowId);
        if (material != null && !resolved.containsKey(material) && searchContainers.contains(material.getContainer()))
        {
            if (ss != null && ss.getLSID().equals(material.getCpasType()))
                resolved.put(material, role);
        }
    }

    protected void addOutputDatas(AssayRunUploadContext<ProviderType> context, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Map<String, File> files = context.getUploadedData();

        AssayDataType dataType = context.getProvider().getDataType();
        for (Map.Entry<String, File> entry : files.entrySet())
        {
            ExpData data = DefaultAssayRunCreator.createData(context.getContainer(), entry.getValue(), entry.getValue().getName(), dataType, context.getReRunId() == null);
            String role = ExpDataRunInput.DEFAULT_ROLE;
            if (dataType != null && dataType.getFileType().isType(entry.getValue()))
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
            addRelatedOutputDatas(context, inputDatas, outputDatas, primaryFile);
        }

        Map<?, String> outputs = context.getOutputDatas();
        addDatas(context.getContainer(), outputDatas, outputs);
    }

    /**
     * Add files that follow the general naming convention (same basename) as the primary file
     */
    public void addRelatedOutputDatas(AssayRunUploadContext context, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, final File primaryFile) throws ExperimentException
    {
        AssayDataType dataType = getProvider().getDataType();
        final String baseName = dataType == null ? null : dataType.getFileType().getBaseName(primaryFile);
        if (baseName != null)
        {
            // Grab all the files that are related based on naming convention
            File[] relatedFiles = primaryFile.getParentFile().listFiles(getRelatedOutputDataFileFilter(primaryFile, baseName));
            if (relatedFiles != null)
            {
                // Create set of existing input files
                Set<File> inputFiles = new HashSet<>();
                for (ExpData inputData : inputDatas.keySet())
                {
                    File f = inputData.getFile();
                    if (f != null)
                        inputFiles.add(f);
                }

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
    public static Pair<ExpData, String> createdRelatedOutputData(AssayRunUploadContext context, String baseName, File relatedFile)
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

        ExpData data = createData(context.getContainer(), relatedFile, relatedFile.getName(), dataType, true);
        if (data.getSourceApplication() == null)
        {
            return new Pair<>(data, roleName);
        }

        // The file is already linked to another run, so this one must have not created it
        return null;
    }


    // Disallow creating a run with inputs which are also outputs
    protected void checkForCycles(AssayRunUploadContext<ProviderType> context, Map<? extends ExpProtocolOutput, String> inputs, Map<? extends ExpProtocolOutput, String> outputs) throws ExperimentException
    {
        Logger logger = context.getLogger() != null ? context.getLogger() : LOG;
        for (ExpProtocolOutput input : inputs.keySet())
        {
            if (outputs.containsKey(input))
            {
                String role = outputs.get(input);
                String msg = "Circular input/output '" + input.getName() + "' with role '" + role + "'";
                if (allowCycles(context))
                    logger.info(msg);
                else
                    throw new ExperimentException(msg);
            }
        }
    }

    protected boolean allowCycles(AssayRunUploadContext<ProviderType> context)
    {
        return false;
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
        for (Map.Entry<ColumnInfo, String> entry : properties.entrySet())
        {
            validateProperty(context, ColumnValidators.create(entry.getKey(), null), entry.getValue(), entry.getKey().getName(), false, entry.getKey().getJavaClass(), errors);
        }
        return errors;
    }

    public static List<ValidationError> validateProperties(ContainerUser context, Map<DomainProperty, String> properties)
    {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            DomainProperty dp = entry.getKey();
            String value = entry.getValue();
            String label = dp.getPropertyDescriptor().getNonBlankCaption();
            PropertyType type = dp.getPropertyDescriptor().getPropertyType();
            validateProperty(context, ColumnValidators.create(null, dp), value, label, dp.isRequired(), type.getJavaType(), errors);
        }
        return errors;
    }

    private static void validateProperty(ContainerUser context, List<ColumnValidator> validators, String value, String label, Boolean required, Class type, List<ValidationError> errors)
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
                String message = label + " must be of type " + ColumnInfo.getFriendlyTypeName(type) + ".";
                message +=  "  Value \"" + value + "\" could not be converted";
                if (e.getCause() instanceof ArithmeticException)
                    message +=  ": " + e.getCause().getLocalizedMessage();
                else
                    message += ".";

                errors.add(new SimpleValidationError(message));
            }
        }
    }

    protected FileFilter getRelatedOutputDataFileFilter(final File primaryFile, final String baseName)
    {
        return new FileFilter()
        {
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
