/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.ParameterDescriptionImpl;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ModuleResourceCaches.CacheId;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.writer.ContainerUser;
import org.labkey.di.DataIntegrationQuerySchema;
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.filters.RunFilterStrategy;
import org.labkey.di.filters.SelectAllFilterStrategy;
import org.labkey.di.steps.ExternalPipelineTaskProvider;
import org.labkey.di.steps.RemoteQueryTransformStepProvider;
import org.labkey.di.steps.SimpleQueryTransformStepMeta;
import org.labkey.di.steps.SimpleQueryTransformStepProvider;
import org.labkey.di.steps.StepMeta;
import org.labkey.di.steps.StepProvider;
import org.labkey.di.steps.StoredProcedureStepProvider;
import org.labkey.di.steps.TaskRefTransformStepProvider;
import org.labkey.di.steps.TestTaskProvider;
import org.labkey.etl.xml.EtlDocument;
import org.labkey.etl.xml.EtlType;
import org.labkey.etl.xml.FilterType;
import org.labkey.etl.xml.ParameterType;
import org.labkey.etl.xml.PipelineParameterType;
import org.labkey.etl.xml.TransformType;
import org.labkey.etl.xml.TransformsType;
import org.quartz.CronExpression;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformManager implements DataIntegrationService
{
    private static final TransformManager INSTANCE = new TransformManager();
    private static final Logger LOG = Logger.getLogger(TransformManager.class);
    private static final String JOB_GROUP_NAME = "org.labkey.di.pipeline.ETLManager";
    private static final String DIR_NAME = "etls";
    private static final ModuleResourceCache<Map<String, ScheduledPipelineJobDescriptor>> DESCRIPTOR_CACHE = ModuleResourceCaches.create("ETL job descriptors", new DescriptorCacheHandler(), ResourceRootProvider.getStandard(new Path(DIR_NAME)));
    private static final String JOB_PENDING_MSG = "Not queuing job because ETL is already pending";

    private Map<String, StepProvider> _providers = new CaseInsensitiveHashMap<>();

    public static TransformManager get()
    {
        return INSTANCE;
    }


    private TransformManager()
    {
    }


    @Nullable TransformDescriptor parseETL(Resource resource, Module module)
    {
        try
        {
            return parseETLThrow(resource, module);
        }
        catch (XmlValidationException|XmlException|IOException e)
        {
            LOG.warn("ETL Config: Unable to parse " + resource + " : " + e.getMessage());
            LOG.debug(e);
        }
        return null;
    }


    TransformDescriptor parseETLThrow(Resource resource, Module module) throws IOException, XmlException, XmlValidationException
    {
        FilterStrategy.Factory defaultFactory = null;
        Long interval = null;
        CronExpression cron = null;

        final ArrayList<StepMeta> stepMetaDatas = new ArrayList<>();
        final Path resourcePath = resource.getPath();
        final CaseInsensitiveHashSet stepIds = new CaseInsensitiveHashSet();
        final String configName = getConfigName(resourcePath.getName());

        String configId = createConfigId(module, configName);

        try (InputStream inputStream = resource.getInputStream())
        {
            if (inputStream == null)
            {
                throw new IOException("Unable to get InputStream from " + resource);
            }

            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            options.setValidateStrict();
            EtlDocument document = EtlDocument.Factory.parse(inputStream, options);
            EtlType etlXml = document.getEtl();

            // handle default transform
            FilterType ft = etlXml.getIncrementalFilter();
            if (null != ft)
                defaultFactory = createFilterFactory(ft);
            if (null == defaultFactory)
                defaultFactory = new SelectAllFilterStrategy.Factory(null);

            // schedule
            if (null != etlXml.getSchedule())
            {
                if (null != etlXml.getSchedule().getPoll())
                {
                    String s = etlXml.getSchedule().getPoll().getInterval();
                    if (StringUtils.isNumeric(s))
                        interval = Long.parseLong(s) * 60 * 1000;
                    else
                        interval = DateUtil.parseDuration(s);
                }
                else if (null != etlXml.getSchedule().getCron())
                {
                    try
                    {
                        cron = new CronExpression(etlXml.getSchedule().getCron().getExpression());
                    }
                    catch (ParseException x)
                    {
                        throw new XmlException("Could not parse cron expression: " + etlXml.getSchedule().getCron().getExpression(), x);
                    }
                }
            }

            Map<ParameterDescription, Object> constants = new LinkedHashMap<>();
            if (null != etlXml.getConstants())
            {
                populateParameterMap(etlXml.getConstants().getColumnArray(), constants);
            }

            TransformsType transforms = etlXml.getTransforms();
            boolean hasGateStep = false;
            if (null != transforms)
            {
                TransformType[] transformTypes = transforms.getTransformArray();
                for (TransformType t : transformTypes)
                {
                    StepMeta meta = buildTransformStepMeta(t, stepIds, constants, etlXml.getName());
                    stepMetaDatas.add(meta);
                    if (meta.isGating())
                        hasGateStep = true;
                }
            }

            Map<ParameterDescription,Object> declaredVariables = new LinkedHashMap<>();
            if (null != etlXml.getParameters())
            {
                populateParameterMap(etlXml.getParameters().getParameterArray(), declaredVariables);
            }

            Map<String, String> pipelineParameters = new LinkedHashMap<>();
            if (null != etlXml.getPipelineParameters())
            {
                for (PipelineParameterType xmlPipeParam : etlXml.getPipelineParameters().getParameterArray())
                {
                    pipelineParameters.put(xmlPipeParam.getName(), xmlPipeParam.getValue());
                }
            }

            // XmlSchema validate the document after we've attempted to parse it since we can provide better error messages.
            XmlBeansUtil.validateXmlDocument(document, "ETL '" + resource.getPath() + "'");

            return new TransformDescriptor(configId, etlXml, module.getName(), interval, cron, defaultFactory, stepMetaDatas, declaredVariables, hasGateStep, pipelineParameters, constants);
        }
    }

    public void populateParameterMap(ParameterType[] params, Map<ParameterDescription, Object> mapToPopulate)
    {
        for (ParameterType xmlp : params)
        {
            String name = xmlp.getName();
            try
            {
                JdbcType type = JdbcType.valueOf(xmlp.getType().toString().toUpperCase());
                String strValue = xmlp.isSetValue() ? xmlp.getValue() : null;
                Object value = type.convert(strValue);
                ParameterDescription p = new ParameterDescriptionImpl(name, type, null);
                mapToPopulate.put(p, value);
            }
            catch (IllegalArgumentException e)
            {
                throw new IllegalArgumentException("Unknown JDBC parameter type: '" + xmlp.getType() + "'. Supported types are: " + Arrays.toString(JdbcType.values()), e);
            }
        }
    }

    String getConfigName(String filename)
    {
        assert filename.endsWith(DescriptorCacheHandler.DESCRIPTOR_EXTENSION) : "Configuration filename \"" + filename + "\" does not end with " + DescriptorCacheHandler.DESCRIPTOR_EXTENSION;
        return FileUtil.getBaseName(filename);
    }

    String createConfigId(Module module, String configName)
    {
        return "{" + module.getName() + "}/" + configName;
    }

    private static final Pattern CONFIG_ID_PATTERN = Pattern.compile("\\{(" + ModuleLoader.MODULE_NAME_REGEX + ")\\}/(.+)");

    CacheId parseConfigId(String configId)
    {
        return ModuleResourceCaches.parseCacheKey(configId, CONFIG_ID_PATTERN);
    }

    private FilterStrategy.Factory createFilterFactory(FilterType filterTypeXML)
    {
        String className = StringUtils.defaultString(filterTypeXML.getClassName().toString(), ModifiedSinceFilterStrategy.class.getName());
        if (!className.contains("."))
            className = "org.labkey.di.filters." + className;
        if (className.equals(ModifiedSinceFilterStrategy.class.getName()))
            return new ModifiedSinceFilterStrategy.Factory(filterTypeXML);
        else if (className.equals(RunFilterStrategy.class.getName()))
            return new RunFilterStrategy.Factory(filterTypeXML);
        else if (className.equals(SelectAllFilterStrategy.class.getName()))
            return new SelectAllFilterStrategy.Factory(filterTypeXML);
        throw new ConfigurationException("Class is not a recognized filter strategy: " + className);
    }

    // errors
    static final String INVALID_TYPE = "Invalid transform type specified";
    private static final String TYPE_REQUIRED = "Transform type attribute is required";
    static final String ID_REQUIRED = "Id attribute is required";
    static final String DUPLICATE_ID = "Id attribute must be unique for each Transform";
    // errors used by stepMeta's
    public static final String INVALID_TARGET_OPTION = "Invalid targetOption attribute value specified";
    public static final String INVALID_SOURCE_OPTION = "Invalid sourceOption attribute value specified";
    public static final String INVALID_SOURCE = "No source element specified.";
    public static final String INVALID_DESTINATION = "No destination element specified.";
    public static final String INVALID_PROCEDURE = "No procedure element specified.";

    private StepMeta buildTransformStepMeta(TransformType transformXML, Set<String> stepIds, Map<ParameterDescription, Object> constants, String etlName) throws XmlException
    {
        if (null == transformXML.getId())
            throw new XmlException(ID_REQUIRED);
        if (stepIds.contains(transformXML.getId()))
            throw new XmlException(DUPLICATE_ID);

        if (null == transformXML.getType())
            throw new XmlException(TYPE_REQUIRED);

        StepProvider provider = getStepProvider(transformXML.getType());
        if (null == provider)
            throw new XmlException(INVALID_TYPE);

        Class taskClass = provider.getStepClass();
        StepMeta meta;
        if (!isValidTaskClass(taskClass))
            throw new XmlException(INVALID_TYPE);

        meta = provider.createMetaInstance();
        meta.setProvider(provider);
        meta.setEtlName(etlName);
        // Apply the global set of constants to be provided to the target.
        // When the StepMeta config is parsed, any constants at the step level
        // will override this initial set.
        meta.putConstants(constants);
        meta.parseConfig(transformXML); // will throw XmlException on validation error

        // Only add to the list of steps after passing validation, including whatever extra validation was in StepMeta
        stepIds.add(transformXML.getId());

        return meta;
    }

    private boolean isValidTaskClass(Class taskClass)
    {
        return TransformTask.class.isAssignableFrom(taskClass);
    }

    @NotNull
    public Collection<ScheduledPipelineJobDescriptor> getDescriptors(Container c)
    {
        final Collection<ScheduledPipelineJobDescriptor> descriptors;

        if (!c.isRoot())
        {
            descriptors = DESCRIPTOR_CACHE.streamResourceMaps(c)
                .map(Map::values)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        }
        else
        {
            descriptors = DESCRIPTOR_CACHE.streamAllResourceMaps()
                .flatMap(map -> map.values().stream())
                .filter(ScheduledPipelineJobDescriptor::isSiteScope)
                .collect(Collectors.toList());
        }

        return Collections.unmodifiableCollection(descriptors);
    }

    @Nullable
    public ScheduledPipelineJobDescriptor getDescriptor(String configId)
    {
        CacheId id = parseConfigId(configId);
        Module module = id.getModule();

        return null != module ? DESCRIPTOR_CACHE.getResourceMap(module).get(configId) : null;
    }

    synchronized Integer runNowPipeline(ScheduledPipelineJobDescriptor descriptor, Container container, User user,
                                        Map<ParameterDescription, Object> params)
        throws PipelineJobException
    {
        return runNowPipeline(descriptor, descriptor.getJobContext(container, user, params), null, null, null);
    }

    public synchronized Integer runNowPipeline(ScheduledPipelineJobDescriptor descriptor, ContainerUser context) throws PipelineJobException
    {
        return runNowPipeline(descriptor, context, null, null, null);
    }

    public synchronized Integer runNowPipeline(ScheduledPipelineJobDescriptor descriptor, ContainerUser context,
                                               Map<String, String> jobParams, File analysisDirectory, String baseName)
        throws PipelineJobException
    {
        if (ViewServlet.isShuttingDown())
            throw new PipelineJobException("Could not create job: server is shutting down");

        try
        {
            // Don't double queue jobs
            if (descriptor.isPending(context) && !descriptor.isAllowMultipleQueuing())
            {
                LOG.info(getJobPendingMessage(descriptor.getId()));
                return null;
            }
            // see if we have work to do before directly scheduling the pipeline job
            boolean hasWork = descriptor.checkForWork(context, false, true);
            if (!hasWork)
                return null;

            TransformPipelineJob job = (TransformPipelineJob) descriptor.getPipelineJob(context);
            if (null == job)
                throw new PipelineJobException("Could not create job: " + descriptor.toString());
            if (null != jobParams)
                job.getParameters().putAll(jobParams);
            if (null != analysisDirectory)
                job.setAnalysisDirectory(analysisDirectory);
            if (null != baseName)
                job.setBaseName(baseName);

            try
            {
                PipelineService.get().setStatus(job, PipelineJob.TaskStatus.waiting.toString(), null, true);
                PipelineService.get().queueJob(job);
                return PipelineService.get().getJobId(context.getUser(), context.getContainer(), job.getJobGUID());
            }
            catch (Exception e)
            {
                throw new PipelineJobException(e);
            }
        }
        finally
        {
            assert dumpScheduler();
        }
    }

    @NotNull
    public static String getJobPendingMessage(@Nullable String id)
    {
        StringBuilder sb = new StringBuilder().append(JOB_PENDING_MSG);
        if (null == id)
            return sb.append(".").toString();
        else return sb.append(": ").append(id).toString();
    }


    public synchronized void runNowQuartz(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        if (ViewServlet.isShuttingDown())
            return;

        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            ScheduledPipelineJobContext info = (ScheduledPipelineJobContext)descriptor.getJobContext(container, user, null);
            info.setVerbose(true);

            // find job
            JobKey jobKey = jobKeyFromDescriptor(descriptor);
            JobDetail job = null;
            if (!scheduler.checkExists(jobKey))
            {
                job = jobFromDescriptor(descriptor);
            }

            TriggerKey triggerKey = TriggerKey.triggerKey(info.getKey() + GUID.makeHash(), JOB_GROUP_NAME);
            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .startNow()
                .usingJobData(info.getQuartzJobDataMap())
                .build();

            if (null != job)
                scheduler.scheduleJob(job, trigger);
            else
                scheduler.scheduleJob(trigger);
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
        finally
        {
            assert dumpScheduler();
        }
    }


    public synchronized void schedule(ScheduledPipelineJobDescriptor descriptor, Container container, User user, boolean verbose)
    {
        if (ViewServlet.isShuttingDown())
            return;

        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            ScheduledPipelineJobContext info = (ScheduledPipelineJobContext)descriptor.getJobContext(container, user, null);
            info.setVerbose(verbose);
            info.setLocked();

            // find job
            JobKey jobKey = jobKeyFromDescriptor(descriptor);
            JobDetail job = null;
            if (!scheduler.checkExists(jobKey))
            {
                job = jobFromDescriptor(descriptor);
            }

            // find trigger
            // Trigger the job to run now, and then every 60 seconds
            TriggerKey triggerKey = TriggerKey.triggerKey(info.getKey(), JOB_GROUP_NAME);
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (null != trigger)
                return;
            trigger = TriggerBuilder.newTrigger()
                .forJob(jobKey)
                .withIdentity(triggerKey)
                .startNow()
                .withSchedule(descriptor.getScheduleBuilder())
                .usingJobData(info.getQuartzJobDataMap())
                .build();

            if (null != job)
                scheduler.scheduleJob(job, trigger);
            else
                scheduler.scheduleJob(trigger);
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
        finally
        {
            assert dumpScheduler();
        }
    }


    public synchronized void unschedule(ScheduledPipelineJobDescriptor etlDescriptor, Container container, User user)
    {
        try
        {
            TransformJobContext info = new TransformJobContext(etlDescriptor, container, user, null);
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            TriggerKey triggerKey = TriggerKey.triggerKey(info.getKey(), JOB_GROUP_NAME);
            scheduler.unscheduleJob(triggerKey);
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
        finally
        {
            assert dumpScheduler();
        }
    }


    public synchronized void unscheduleAll(Container container)
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            Set<TriggerKey> keys = scheduler.getTriggerKeys(GroupMatcher.groupEquals(JOB_GROUP_NAME));
            String containerPrefix = "" + container.getRowId() + "/";
            for (TriggerKey key : keys)
            {
                if (key.getName().startsWith(containerPrefix))
                    scheduler.unscheduleJob(key);
            }
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
        finally
        {
            assert dumpScheduler();
        }
    }



    private static JobKey jobKeyFromDescriptor(ScheduledPipelineJobDescriptor descriptor)
    {
        return JobKey.jobKey(descriptor.getId(), JOB_GROUP_NAME);
    }


    private JobDetail jobFromDescriptor(ScheduledPipelineJobDescriptor descriptor)
    {
        JobKey jobKey = JobKey.jobKey(descriptor.getId(), JOB_GROUP_NAME);
        JobDetail job = JobBuilder.newJob(descriptor.getQuartzJobClass())
            .withIdentity(jobKey)
            .storeDurably()
            .build();
        // Consider using a facade for the descriptor instead of the full descriptor. Really only need id and
        // pointer to correct descriptor class, and the descriptor is responsible for getting the current
        // version from the cache.
        job.getJobDataMap().put(ScheduledPipelineJobDescriptor.class.getName(),descriptor);
        return job;
    }


    private boolean dumpScheduler()
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            {
            LOG.debug("Jobs");
            Set<JobKey> keys = scheduler.getJobKeys(GroupMatcher.groupEquals(JOB_GROUP_NAME));
            for (JobKey key : keys)
                LOG.debug("\t" + key.toString());
            }

            {
            LOG.debug("Triggers");
            Set<TriggerKey> keys = scheduler.getTriggerKeys(GroupMatcher.groupEquals(JOB_GROUP_NAME));
            for (TriggerKey key : keys)
                LOG.debug("\t" + key.toString());
            }
        }
        catch(SchedulerException x)
        {
            LOG.warn(x);
        }
        return true;
    }

    private SQLFragment transformConfigurationSQLFragment(Container c)
    {
        return new SQLFragment("with runs AS")
                .append(" (SELECT *, ROW_NUMBER() OVER (PARTITION BY transformid, container ORDER BY starttime DESC) as rn")
                .append(" FROM dataintegration.transformrun WHERE status != 'NO WORK'),")
                .append("workCheckedRuns AS")
                .append(" (SELECT *, ROW_NUMBER() OVER (PARTITION BY transformid, container ORDER BY starttime DESC) as rn")
                .append(" FROM dataintegration.transformrun),")
                .append("successRuns AS")
                .append(" (SELECT *, ROW_NUMBER() OVER (PARTITION BY transformid, container ORDER BY starttime DESC) as rn")
                .append(" FROM dataintegration.transformrun WHERE endtime is not NULL)")
                .append(" select c.*, runs.jobid as lastJobId, runs.transformrunid as lastRunId,")
                .append(" CASE")
                .append(" WHEN s.status = 'COMPLETE' OR s.status LIKE 'CANCEL%' THEN s.status")
                .append(" WHEN runs.status != 'PENDING' OR s.Status = 'WAITING' THEN runs.status ELSE 'RUNNING' END as lastStatus,")
                .append(" successRuns.jobid as lastCompletionJobId, successRuns.endtime as lastCompletion, workCheckedRuns.startTime as lastChecked from dataintegration.transformconfiguration c")
                .append(" LEFT JOIN workCheckedRuns ON c.container = workCheckedRuns.container AND c.transformid = workCheckedRuns.transformid AND workCheckedRuns.rn = 1")
                .append(" LEFT JOIN runs ON c.container = runs.container AND c.transformid = runs.transformid AND runs.rn = 1")
                .append(" LEFT JOIN pipeline.StatusFiles s ON runs.jobid = s.rowId")
                .append(" LEFT JOIN successRuns ON c.container = successRuns.container AND c.transformid = successRuns.transformid AND successRuns.rn = 1")
                .append(" WHERE c.container=?").add(c.getId());
    }

    public List<TransformConfiguration> getTransformConfigurations(Container c)
    {
        DbScope scope = DbSchema.get("dataintegration", DbSchemaType.Module).getScope();
        SQLFragment sql = transformConfigurationSQLFragment(c);
        return new SqlSelector(scope, sql).getArrayList(TransformConfiguration.class);
    }


    public TransformConfiguration getTransformConfiguration(Container c, ScheduledPipelineJobDescriptor etl)
    {
        DbScope scope = DbSchema.get("dataintegration", DbSchemaType.Module).getScope();
        SQLFragment sql = transformConfigurationSQLFragment(c).append(" and c.transformid=?").add(etl.getId());
        TransformConfiguration ret = new SqlSelector(scope, sql).getObject(TransformConfiguration.class);
        if (null != ret)
            return ret;
        return saveTransformConfiguration(null, new TransformConfiguration(etl, c));
    }


    public TransformConfiguration saveTransformConfiguration(User user, TransformConfiguration config)
    {
        try
        {
            TableInfo t = DbSchema.get("dataintegration", DbSchemaType.Module).getTable("TransformConfiguration");
            if (-1 == config.rowId)
                return Table.insert(user, t, config);
            else
                return Table.update(user, t, config, new Object[]{config.rowId});
        }
        catch (RuntimeSQLException x)
        {
            if (!x.isConstraintException())
                throw x;
            // if the container went away, ignore this exception
            if (null == ContainerManager.getForId(config.getContainerId()))
                return config;
            throw x;
        }
    }

    public String getJobDetailsLink(Container c, Integer jobId, String text, boolean styled)
    {
        if (null == jobId)
            return null;
        ActionURL detailsURL = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(c, jobId);

        return styled ? PageFlowUtil.textLink(text, detailsURL ) : PageFlowUtil.unstyledTextLink(text, detailsURL);
    }

    public String getTransformRunlog(Container c, Integer runId)
    {
        return (runId != null) ?
            getTransformRun(c, runId).getTransformRunLog() :
            null;
    }

    /* Should only be called once at startup */
    public void startAllConfigurations()
    {
        DbSchema schema = DataIntegrationQuerySchema.getSchema();
        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration");

        new SqlSelector(schema, sql).forEach((TransformConfiguration config) ->
        {
            CacheId id = parseConfigId(config.getTransformId());
            Module module = id.getModule();
            ScheduledPipelineJobDescriptor descriptor;

            // Issue 30051. If module, descriptor (xml), or container no longer exist, delete the configuration. If the descriptor is
            // now standalone == false, disable the configuration and don't schedule.
            if (null == module) // Module has been deleted or renamed
            {
                deleteConfiguration(config.getRowId());
            }
            else
            {
                descriptor = DESCRIPTOR_CACHE.getResourceMap(module).get(config.getTransformId());
                if (null == descriptor)
                {
                    deleteConfiguration(config.getRowId());
                }
                else
                {
                    Container c = ContainerManager.getForId(config.getContainerId());
                    if (null == c) // This should never happen, as there's a container listener to purge the TransformConfiguration table
                    {
                        deleteConfiguration(config.getRowId());
                    }
                    else if (config.isEnabled())
                    {
                        // CONSIDER explicit runAs user
                        int runAsUserId = config.getModifiedBy();
                        User runAsUser = UserManager.getUser(runAsUserId);

                        if (descriptor.isStandalone())
                        {
                            schedule(descriptor, c, runAsUser, config.isVerboseLogging());
                        }
                        else
                        {
                            disableConfiguration(runAsUser, config);
                        }
                    }
                }
            }
        }, TransformConfiguration.class);
    }

    private void deleteConfiguration(int rowId)
    {
        TableInfo t = DbSchema.get("dataintegration", DbSchemaType.Module).getTable("TransformConfiguration");
        Table.delete(t, rowId);
    }

    private void disableConfiguration(User u, TransformConfiguration config)
    {
        config.setEnabled(false);
        saveTransformConfiguration(u, config);
    }

    public String getRunDetailsLink(Container c, Integer runId, String text)
    {
        if (null == runId)
            return null;
        ActionURL runDetailURL = new ActionURL();
        runDetailURL.setContainer(c);
        runDetailURL.addParameter("schemaName", DataIntegrationQuerySchema.SCHEMA_NAME);
        runDetailURL.addParameter("query.queryName", DataIntegrationQuerySchema.TRANSFORMRUN_TABLE_NAME);
        runDetailURL.addParameter("query.TransformRunId~eq", runId.intValue());

        return PageFlowUtil.unstyledTextLink(text, PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(runDetailURL));
    }

    public TransformRun getTransformRun(Container c, int runId)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ").append(DataIntegrationQuerySchema.getTransformRunTableInfo().getFromSQL("x"))
                .append(" WHERE container=? and transformrunid=?").add(c.getId()).add(runId);
        return new SqlSelector(DataIntegrationQuerySchema.getSchema(), sql).getObject(TransformRun.class);
    }

    public TransformRun getTransformRunForJob(Container c, int jobId)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ").append(DataIntegrationQuerySchema.getTransformRunTableInfo().getFromSQL("x"))
                .append(" WHERE container=? and jobid=?").add(c.getId()).add(jobId);
        return new SqlSelector(DataIntegrationQuerySchema.getSchema(), sql).getObject(TransformRun.class);
    }

    public boolean transformIsPending(ContainerUser context, String transformId, String firstTaskName)
    {
        final String WAITING = PipelineJob.TaskStatus.waiting.toString();
        SQLFragment sql = new SQLFragment("SELECT 1 FROM ").append(DataIntegrationQuerySchema.getTransformRunTableInfo().getFromSQL("r"))
                .append(" JOIN ").append(PipelineService.get().getJobsTable(context.getUser(), context.getContainer()).getFromSQL("j"))
                .append(" ON r.jobid = j.rowid ").append(" WHERE r.container=? and r.transformid=?")
                .append(" AND (j.status = '").append(WAITING).append("'")
                .append(" OR j.status LIKE '%").append(firstTaskName).append(" ").append(WAITING).append("')")
                .add(context.getContainer().getId()).add(transformId);
        return new SqlSelector(DataIntegrationQuerySchema.getSchema(), sql).exists();
    }

    public TransformRun insertTransformRun(User user, TransformRun run)
    {
        run = Table.insert(user, DataIntegrationQuerySchema.getTransformRunTableInfo(), run);
        return run;
    }


    public void updateTransformRun(User user, TransformRun run)
    {
        Table.update(user, DataIntegrationQuerySchema.getTransformRunTableInfo(), run, run.getTransformRunId());
    }

    public VariableMap getVariableMapForTransformJob(TransformRun run)
    {
        Integer expRunId = getExpRunIdForTransformRun(run);
        if (null != expRunId)
        {
            ExpRun expRun = ExperimentService.get().getExpRun(expRunId);
            return new VariableMapImpl(null, expRun.getObjectProperties());
        }
        return null;
    }

    public VariableMap getVariableMapForTransformStep(TransformRun run, String transformStepId)
    {
        Integer expRunId = getExpRunIdForTransformRun(run);
        if (null != expRunId)
        {
            for (ExpProtocolApplication protocolApp : ExperimentService.get().getExpProtocolApplicationsForRun(expRunId))
            {
                if (StringUtils.equals(protocolApp.getName(), transformStepId))
                {
                    return new VariableMapImpl(null, protocolApp.getObjectProperties());
                }
            }
        }
        return null;
    }

    Integer getExpRunIdForTransformRun(TransformRun run)
    {
        Integer expRunId = null;
        List<? extends ExpRun> expRuns = ExperimentService.get().getExpRunsForJobId(run.getJobId());
        // There should be at most 1.
        if (!expRuns.isEmpty())
        {
            expRunId = expRuns.get(0).getRowId();
        }
        return expRunId;
    }

    public Map<String, String> truncateTargets(String id, User user, Container c)
    {
        int deletedRows = 0;
        TransformDescriptor etl = (TransformDescriptor)getDescriptor(id);
        Map<String, String> retMap = new HashMap<>();
        StringBuilder errorBuilder = new StringBuilder();
        if(etl != null)
        {
            List<StepMeta> stepMetas = etl.getStepMetaDatas();
            List<TableInfo> targets = new ArrayList<>();

            // Iterate through steps to verify schemas and tables exist
            for (StepMeta stepMeta : stepMetas)
            {
                if (stepMeta.isUseTarget() && ((CopyConfig)stepMeta).getTargetType() == CopyConfig.TargetTypes.query)
                {
                    if (SimpleQueryTransformStepMeta.class.isInstance(stepMeta) && ((SimpleQueryTransformStepMeta)stepMeta).isTruncateStep()
                            || null == stepMeta.getTargetSchema())
                        continue; // Don't bother truncating from a truncate step, or any step without a target.

                    QuerySchema querySchema = DefaultSchema.get(user, c, stepMeta.getTargetSchema());
                    if (null == querySchema || null == querySchema.getDbSchema())
                    {
                        errorBuilder.append("Could not find schema: ").append(stepMeta.getTargetSchema()).append("\n");
                        continue;
                    }
                    TableInfo targetTableInfo = querySchema.getTable(stepMeta.getTargetQuery());
                    if (null == targetTableInfo)
                    {
                        errorBuilder.append("Could not find table: ").append(stepMeta.getTargetSchema()).append('.').append(stepMeta.getTargetQuery()).append("\n");
                        continue;
                    }
                    targets.add(targetTableInfo);
                }
            }

            if(errorBuilder.length()<1)
            {
                // Reverse targets to delete in reverse order in case foreign keys exist between targets
                Collections.reverse(targets);

                // Truncate tables
                for (TableInfo target : targets)
                {
                    QueryUpdateService qus = target.getUpdateService();
                    if (qus != null)
                    {
                        try (DbScope.Transaction transaction = target.getSchema().getScope().ensureTransaction())
                        {
                            deletedRows += qus.truncateRows(user, c, null, null);
                            transaction.commit();
                        }
                        catch(QueryUpdateServiceException | SQLException | BatchValidationException e)
                        {
                            String msg = "Unable to perform truncate transaction on " + target.toString();
                            errorBuilder.append(msg).append("\n");
                            errorBuilder.append(e.getMessage()).append("\n");
                            LOG.error(msg, e);
                        }
                    }
                    else
                    {
                        errorBuilder.append("No truncation on target '").append(target.toString()).append( "' is not an updatable table.\n");
                    }
                }
            }
            if(errorBuilder.length()>0)
                retMap.put("error", errorBuilder.toString());
            retMap.put("rows", Integer.toString(deletedRows));
        }
        return retMap;
    }

    //
    // DataIntegrationService
    //


    @Override
    public void registerStepProviders()
    {
        _providers.putAll(new SimpleQueryTransformStepProvider().getNameProviderMap());
        _providers.putAll(new RemoteQueryTransformStepProvider().getNameProviderMap());
        _providers.putAll(new StoredProcedureStepProvider().getNameProviderMap());
        _providers.putAll(new TaskRefTransformStepProvider().getNameProviderMap());
        _providers.putAll(new ExternalPipelineTaskProvider().getNameProviderMap());
        _providers.putAll(new TestTaskProvider().getNameProviderMap());

    }

    @Nullable
    private StepProvider getStepProvider(String providerName)
    {
        return _providers.get(providerName);
    }

    @Nullable
    @Override
    public Integer runTransformNow(Container c, User u, String transformId) throws PipelineJobException, NotFoundException
    {
        ScheduledPipelineJobDescriptor etl = getDescriptor(transformId);
        if (etl == null)
            throw new NotFoundException(transformId);
        return runNowPipeline(etl, c, u, new LinkedHashMap<>());
    }

    //
    // Tests
    //

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        @Test
        public void scheduler() throws Exception
        {
            final AtomicInteger counter = new AtomicInteger(0);

            final class TestTransformDescriptor extends TransformDescriptor
            {
                private TestTransformDescriptor(String id, String name, String moduleName) throws XmlException, IOException
                {
                    super(id, name, moduleName);
                }

                @Override
                public int getVersion()
                {
                    return 0;
                }

                @Override
                public ScheduleBuilder getScheduleBuilder()
                {
                    return SimpleScheduleBuilder.repeatSecondlyForever();
                }

                @Override
                public String getScheduleDescription()
                {
                    return "1s";
                }

                @Override
                public boolean checkForWork(ScheduledPipelineJobContext context, boolean background, boolean verbose)
                {
                    counter.incrementAndGet();
                    return false;
                }

                @Override
                public PipelineJob getPipelineJob(ScheduledPipelineJobContext context) throws PipelineJobException
                {
                    return null;
                }

                @Override
                public ScheduledPipelineJobDescriptor getDescriptorFromCache()
                {
                    return this;
                }

                @Override
                public boolean isPending(ContainerUser context)
                {
                    return false;
                }
            }

            final ScheduledPipelineJobDescriptor d = new TestTransformDescriptor(GUID.makeHash(), "TestCase", "dataintegration");
            final User user = TestContext.get().getUser();
            final Container c = JunitUtil.getTestContainer();

            assertEquals(0, counter.get());
            TransformManager.get().runNowQuartz(d, c, user);
            for (int i=0 ; i<10 && counter.get()<1 ; i++)
                sleep(1);
            assertEquals(1, counter.get());
            TransformManager.get().runNowQuartz(d, c, user);
            for (int i=0 ; i<10 && counter.get()<2 ; i++)
                sleep(2);
            assertEquals(2, counter.get());

            TransformManager.get().schedule(d, c, user, false);
            sleep(5);
            assertTrue(counter.get() <= 10);
            for (int i=0 ; i<10 && counter.get() < 12 ; i++)
                sleep(1);
            assertTrue(counter.get() >= 12);

            TransformManager.get().unschedule(d, c, user);
            sleep(1);
            int count = counter.get();
            sleep(2);
            assertEquals(count, counter.get());

            JobKey jobKey = jobKeyFromDescriptor(d);
            assertTrue(StdSchedulerFactory.getDefaultScheduler().checkExists(jobKey));
            StdSchedulerFactory.getDefaultScheduler().deleteJob(jobKey);
            assertFalse(StdSchedulerFactory.getDefaultScheduler().checkExists(jobKey));
        }


        void sleep(int sec)
        {
           try
           {
               Thread.sleep(sec * 1000);
           }
           catch (InterruptedException x)
           {
           }
        }


        @Test
        public void testModuleResourceCache()
        {
            // Load all the ETL descriptors to ensure no exceptions
            int descriptorCount = DESCRIPTOR_CACHE.streamAllResourceMaps()
                .mapToInt(Map::size)
                .sum();

            LOG.info(descriptorCount + " ETL descriptors defined in all modules");

            // Make sure the cache retrieves the expected number of ETL descriptors from a couple test modules, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
                assertEquals("ETL descriptors from the simpletest module", 2, DESCRIPTOR_CACHE.getResourceMap(simpleTest).size());

            Module etlTest = ModuleLoader.getInstance().getModule("ETLTest");

            if (null != etlTest)
                assertEquals("ETL descriptors from the ETLTest module", 59, DESCRIPTOR_CACHE.getResourceMap(etlTest).size());
        }
    }


    /*
    private static class TestDescriptorWrapper implements ScheduledPipelineJobDescriptor
    {
        private final ScheduledPipelineJobDescriptor _d;
        private final Runnable _done;

        TestDescriptorWrapper(ScheduledPipelineJobDescriptor d, Runnable done)
        {
            _d = d;
            _done = done;
        }

        public String getId()
        {
            return _d.getId();
        }

        public String getName()
        {
            return _d.getName();
        }

        public String getDescription()
        {
            return _d.getDescription();
        }

        public String getModuleName()
        {
            return _d.getModuleName();
        }

        public int getVersion()
        {
            return _d.getVersion();
        }

        public ScheduleBuilder getScheduleBuilder()
        {
            return _d.getScheduleBuilder();
        }

        public String getScheduleDescription()
        {
            return _d.getScheduleDescription();
        }

        public Class<? extends Job> getQuartzJobClass()
        {
            return _d.getQuartzJobClass();
        }

        public ContainerUser getJobContext(Container c, User user)
        {
            return _d.getJobContext(c, user);
        }

        public Callable<Boolean> getChecker(ContainerUser context)
        {
            return _d.getChecker(context);
        }

        public PipelineJob getPipelineJob(ContainerUser context) throws PipelineJobException
        {
            return _d.getPipelineJob(context);
        }
    }
    */
}
