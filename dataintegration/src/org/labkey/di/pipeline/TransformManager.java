/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.writer.ContainerUser;
import org.labkey.di.DataIntegrationDbSchema;
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.filters.RunFilterStrategy;
import org.labkey.di.filters.SelectAllFilterStrategy;
import org.labkey.di.steps.SimpleQueryTransformStepMeta;
import org.labkey.etl.xml.EtlDocument;
import org.labkey.etl.xml.EtlType;
import org.labkey.etl.xml.FilterType;
import org.labkey.etl.xml.SchemaQueryType;
import org.labkey.etl.xml.TransformType;
import org.labkey.etl.xml.TransformsType;
import org.quartz.CronExpression;
import org.quartz.Job;
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

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformManager implements DataIntegrationService
{
    private static final TransformManager INSTANCE = new TransformManager();
    private static final Logger LOG = Logger.getLogger(TransformManager.class);
    private static final String JOB_GROUP_NAME = "org.labkey.di.pipeline.ETLManager";

    public static TransformManager get()
    {
        return INSTANCE;
    }


    private TransformManager()
    {
    }


    @Nullable TransformDescriptor parseETL(Resource resource, String moduleName)
    {
        try
        {
            return parseETLThrow(resource, moduleName);
        }
        catch (XmlException|IOException e)
        {
            LOG.warn("Unable to parse " + resource, e);
        }
        return null;
    }


    TransformDescriptor parseETLThrow(Resource resource, String moduleName) throws IOException, XmlException
    {
        FilterStrategy.Factory defaultFactory = null;
        Long interval = null;
        CronExpression cron = null;

        final ArrayList<SimpleQueryTransformStepMeta> stepMetaDatas = new ArrayList<>();
        final Path resourcePath = resource.getPath();
        final String filename;
        final CaseInsensitiveHashSet stepIds = new CaseInsensitiveHashSet();

        // TODO: Change this... no longer special case config.xml?
        if ("config.xml".equals(resourcePath.getName().toLowerCase()))
            filename = resourcePath.getParent().getName();
        else
            filename = FileUtil.getBaseName(resourcePath.getName());

        String id = createConfigId(moduleName, filename);

        try (InputStream inputStream = resource.getInputStream())
        {
            if (inputStream == null)
            {
                throw new IOException("Unable to get InputStream from " + resource);
            }

            XmlOptions options = new XmlOptions();
            options.setValidateStrict();
            EtlDocument document = EtlDocument.Factory.parse(inputStream, options);
            EtlType etlXML = document.getEtl();

            // handle default transform
            FilterType ft = etlXML.getIncrementalFilter();
            if (null != ft)
                defaultFactory = createFilterFactory(ft);
            if (null == defaultFactory)
                defaultFactory = new ModifiedSinceFilterStrategy.Factory();

            // schedule
            if (null != etlXML.getSchedule())
            {
                if (null != etlXML.getSchedule().getPoll())
                {
                    String s = etlXML.getSchedule().getPoll().getInterval();
                    if (StringUtils.isNumeric(s))
                        interval = Long.parseLong(s) * 60 * 1000;
                    else
                        interval = DateUtil.parseDuration(s);
                }
                else if (null != etlXML.getSchedule().getCron())
                {
                    try
                    {
                        cron = new CronExpression(etlXML.getSchedule().getCron().getExpression());
                    }
                    catch (ParseException x)
                    {
                        throw new XmlException("Could not parse cron expression: " + etlXML.getSchedule().getCron().getExpression(), x);
                    }
                }
            }

            TransformsType transforms = etlXML.getTransforms();
            if (null != transforms)
            {
                TransformType[] transformTypes = transforms.getTransformArray();
                for (TransformType t : transformTypes)
                {
                    SimpleQueryTransformStepMeta meta = buildSimpleQueryTransformStepMeta(t, stepIds);
                    stepMetaDatas.add(meta);
                }
            }

            return new TransformDescriptor(id, etlXML.getName(), etlXML.getDescription(), moduleName, interval, cron, defaultFactory, stepMetaDatas);
        }
    }


    String createConfigId(String moduleName, String filename)
    {
        return "{" + moduleName + "}/" + filename;
    }


    private static final Pattern CONFIG_ID_PATTERN = Pattern.compile("\\{(\\w+)\\}/(.+)");

    Pair<Module, String> parseConfigId(String configId)
    {
        // Parse out the module name and the config name
        Matcher matcher = CONFIG_ID_PATTERN.matcher(configId);

        if (!matcher.matches() || matcher.groupCount() != 2)
            throw new IllegalStateException("Unrecognized configuration ID format: " + configId);

        String moduleName = matcher.group(1);
        String filename = matcher.group(2);
        Module module = ModuleLoader.getInstance().getModule(moduleName);

        if (null == module)
            throw new IllegalStateException("Module does not exist: " + moduleName);

        return new Pair<>(module, filename);
    }


    private FilterStrategy.Factory createFilterFactory(FilterType filterTypeXML)
    {
        String className = StringUtils.defaultString(filterTypeXML.getClassName(), ModifiedSinceFilterStrategy.class.getName());
        if (!className.contains("."))
            className = "org.labkey.di.filters." + className;
        if (className.equals(ModifiedSinceFilterStrategy.class.getName()))
            return new ModifiedSinceFilterStrategy.Factory(filterTypeXML);
        else if (className.equals(RunFilterStrategy.class.getName()))
            return new RunFilterStrategy.Factory(filterTypeXML);
        else if (className.equals(SelectAllFilterStrategy.class.getName()))
            return new SelectAllFilterStrategy.Factory();
        throw new IllegalArgumentException("Class is not a recognized filter strategy: " + className);
    }


    // errors
    static final String INVALID_TYPE = "Invalid transform type specified";
    static final String TYPE_REQUIRED = "Transform type attribute is required";
    static final String ID_REQUIRED = "Id attribute is required";
    static final String DUPLICATE_ID = "Id attribute must be unique for each Transform";
    static final String INVALID_TARGET_OPTION = "Invaild targetOption attribute value specified";
    static final String INVALID_SOURCE_OPTION = "Invaild sourceOption attribute value specified";

    private SimpleQueryTransformStepMeta buildSimpleQueryTransformStepMeta(TransformType transformXML, Set<String> stepIds) throws XmlException
    {
        SimpleQueryTransformStepMeta meta = new SimpleQueryTransformStepMeta();

        if (null == transformXML.getId())
            throw new XmlException(ID_REQUIRED);

        if (stepIds.contains(transformXML.getId()))
            throw new XmlException(DUPLICATE_ID);

        stepIds.add(transformXML.getId());
        meta.setId(transformXML.getId());

        if (null != transformXML.getDescription())
        {
            meta.setDescription(transformXML.getDescription());
        }

        String className = transformXML.getType();

        if (null == className)
        {
            className = TransformTask.class.getName();
        }

        try
        {
            Class taskClass = Class.forName(className);
            if (isValidTaskClass(taskClass))
            {
                meta.setTaskClass(taskClass);
            }
            else
            {
                throw new XmlException(INVALID_TYPE);
            }
        }
        catch (ClassNotFoundException e)
        {
            throw new XmlException(INVALID_TYPE);
        }

        SchemaQueryType source = transformXML.getSource();

        if (null != source)
        {
            meta.setSourceSchema(SchemaKey.fromString(source.getSchemaName()));
            meta.setSourceQuery(source.getQueryName());
            if (null != source.getTimestampColumnName())
                meta.setSourceTimestampColumnName(source.getTimestampColumnName());
            if (null != source.getSourceOption())
            {
                try
                {
                    meta.setSourceOptions(CopyConfig.SourceOptions.valueOf(source.getSourceOption()));
                }
                catch (IllegalArgumentException x)
                {
                    throw new XmlException(INVALID_SOURCE_OPTION);
                }
            }
        }

        SchemaQueryType destination = transformXML.getDestination();

        if (null != destination)
        {
            meta.setTargetSchema(SchemaKey.fromString(destination.getSchemaName()));
            meta.setTargetQuery(destination.getQueryName());
            if (null != destination.getTargetOption())
            {
                try
                {
                    meta.setTargetOptions(CopyConfig.TargetOptions.valueOf(destination.getTargetOption()));
                }
                catch (IllegalArgumentException x)
                {
                    throw new XmlException(INVALID_TARGET_OPTION);
                }
            }
        }

        return meta;
    }

    private boolean isValidTaskClass(Class taskClass)
    {
        return TransformTask.class.isAssignableFrom(taskClass);
    }

    @NotNull
    public Collection<ScheduledPipelineJobDescriptor> getRegisteredDescriptors()
    {
        // Easy to implement, if we actually need it
        throw new UnsupportedOperationException();
    }


    @NotNull
    public Collection<ScheduledPipelineJobDescriptor> getDescriptors(Container c)
    {
        return DescriptorCache.getDescriptors(c);
    }


    @Nullable
    public ScheduledPipelineJobDescriptor getDescriptor(String id)
    {
        return DescriptorCache.getDescriptor(id);
    }


    public synchronized ActionURL runNowPipeline(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
            throws PipelineJobException
    {
        if (ViewServlet.isShuttingDown())
            throw new PipelineJobException("Could not create job: server is shutting down");

        try
        {
            ContainerUser context = descriptor.getJobContext(container, user);

            // see if we have work to do before directly scheduling the pipeline job
            Callable c = descriptor.getChecker(context);
            try
            {
                boolean hasWork = Boolean.TRUE == c.call();
                if (!hasWork)
                    return null;
            }
            catch (Exception e)
            {
                throw new UnexpectedException(e);
            }

            PipelineJob job = descriptor.getPipelineJob(context);
            if (null == job)
                throw new PipelineJobException("Could not create job: " + descriptor.toString());

            try
            {
                PipelineService.get().setStatus(job, PipelineJob.WAITING_STATUS, null, true);
                PipelineService.get().queueJob(job);
                int jobid = PipelineService.get().getJobId(user, container, job.getJobGUID());
                return new ActionURL("pipeline-status", "details", container).addParameter("rowId", jobid);
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


    public synchronized void runNowQuartz(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        if (ViewServlet.isShuttingDown())
            return;

        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            ScheduledPipelineJobContext info = (ScheduledPipelineJobContext)descriptor.getJobContext(container, user);
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
            ScheduledPipelineJobContext info = (ScheduledPipelineJobContext)descriptor.getJobContext(container, user);
            info.setVerbose(verbose);

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
            TransformJobContext info = new TransformJobContext(etlDescriptor, container, user);
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
            Set<TriggerKey> keys = scheduler.getTriggerKeys(GroupMatcher.<TriggerKey>groupEquals(JOB_GROUP_NAME));
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
        job.getJobDataMap().put(ScheduledPipelineJobDescriptor.class.getName(),descriptor);
        return job;
    }


    boolean dumpScheduler()
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            {
            LOG.debug("Jobs");
            Set<JobKey> keys = scheduler.getJobKeys(GroupMatcher.<JobKey>groupEquals(JOB_GROUP_NAME));
            for (JobKey key : keys)
                LOG.debug("\t" + key.toString());
            }

            {
            LOG.debug("Triggers");
            Set<TriggerKey> keys = scheduler.getTriggerKeys(GroupMatcher.<TriggerKey>groupEquals(JOB_GROUP_NAME));
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


    public List<TransformConfiguration> getTransformConfigurations(Container c)
    {
        DbScope scope = DbSchema.get("dataintegration").getScope();
        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration WHERE container=?", c.getId());
        return new SqlSelector(scope, sql).getArrayList(TransformConfiguration.class);
    }


    public TransformConfiguration getTransformConfiguration(Container c, ScheduledPipelineJobDescriptor etl)
    {
        DbScope scope = DbSchema.get("dataintegration").getScope();
        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration WHERE container=? and transformid=?", c.getId(), etl.getId());
        TransformConfiguration ret = new SqlSelector(scope, sql).getObject(TransformConfiguration.class);
        if (null != ret)
            return ret;
        return saveTransformConfiguration(null, new TransformConfiguration(etl, c));
    }


    public TransformConfiguration saveTransformConfiguration(User user, TransformConfiguration config)
    {
        try
        {
            TableInfo t = DbSchema.get("dataintegration").getTable("TransformConfiguration");
            if (-1 == config.rowId)
                return Table.insert(user, t, config);
            else
                return Table.update(user, t, config, new Object[] {config.rowId});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }



    /* Should only be called once at startup */
    public void startAllConfigurations()
    {
        DbSchema schema = DataIntegrationDbSchema.getSchema();
        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration WHERE enabled=?", true);

        new SqlSelector(schema, sql).forEach(new Selector.ForEachBlock<TransformConfiguration>(){
            @Override
            public void exec(TransformConfiguration config) throws SQLException
            {
                // CONSIDER explicit runAs user
                int runAsUserId = config.getModifiedBy();
                User runAsUser = UserManager.getUser(runAsUserId);

                ScheduledPipelineJobDescriptor descriptor = DescriptorCache.getDescriptor(config.getTransformId());
                if (null == descriptor)
                    return;
                Container c = ContainerManager.getForId(config.getContainerId());
                if (null == c)
                    return;
                schedule(descriptor, c, runAsUser, config.isVerboseLogging());
                }
        }, TransformConfiguration.class);
    }


    public void shutdownPre()
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.standby();
        }
        catch (SchedulerException x)
        {
        }
    }


    public void shutdownStarted()
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.shutdown(true);
        }
        catch (SchedulerException x)
        {
        }
    }

    public Integer getLastSuccessfulTransformExpRun(String transformId, int version)
    {
        SimpleFilter f = new SimpleFilter();
        Sort s = new Sort("-StartTime");
        TableInfo ti = DataIntegrationDbSchema.getTransformRunTableInfo();

        f.addCondition(new FieldKey(null, "TransformId"), transformId, CompareType.EQUAL);
        f.addCondition(new FieldKey(null, "TransformVersion"), version, CompareType.EQUAL);
        f.addCondition(new FieldKey(null, "status"), "Complete", CompareType.EQUAL);

        Integer[] expRunIds = new TableSelector(ti.getColumn("ExpRunId"), f, s).getArray(Integer.class);

        if (expRunIds.length > 0)
            return expRunIds[0];

        return null;
   }

    public VariableMap getVariableMapForTransformJob(Integer expRunId)
    {
        ExpRun run = ExperimentService.get().getExpRun(expRunId);
        return new VariableMapImpl(null, run.getObjectProperties());
    }


    public VariableMap getVariableMapForTransformStep(Integer expRunId, String transformStepId)
    {
        ExpProtocolApplication[] protocolApps = ExperimentService.get().getExpProtocolApplicationsForRun(expRunId);
        for (ExpProtocolApplication protocolApp : protocolApps)
        {
            if (StringUtils.equals(protocolApp.getName(), transformStepId))
            {
                return new VariableMapImpl(null, protocolApp.getObjectProperties());
            }
        }

        return null;
    }

    //
    // DataIntegrationService
    //


    @Deprecated   // Never queried
    private final Map<String,Pair<Module,ScheduledPipelineJobDescriptor>> _etls =
            Collections.synchronizedMap(new CaseInsensitiveTreeMap<Pair<Module, ScheduledPipelineJobDescriptor>>());


    @Override   @Deprecated  // Nobody calls this
    public void registerDescriptors(Module module, Collection<ScheduledPipelineJobDescriptor> descriptors)
    {
        if (null == descriptors || descriptors.isEmpty())
            return;
        Map<String,Pair<Module,ScheduledPipelineJobDescriptor>> m = new TreeMap<>();
        for (ScheduledPipelineJobDescriptor d : descriptors)
            m.put(d.getId(), new Pair(module,d));
        _etls.putAll(m);
    }


    @Override   @Deprecated  // Nobody should call this
    public Collection<ScheduledPipelineJobDescriptor> registerDescriptorsFromFiles(Module module)
    {
        Path etlsDirPath = new Path("etls");
        Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);
        ArrayList<ScheduledPipelineJobDescriptor> l = new ArrayList<>();

        if (etlsDir != null && etlsDir.isCollection())
        {
            for (Resource r : etlsDir.list())
            {
                Resource configXml = null;
                if (r.isCollection())
                    configXml = r.find("config.xml");
                else if (r.isFile() && r.getName().toLowerCase().endsWith(".xml"))
                    configXml = r;
                if (null != configXml && configXml.isFile())
                {
                    TransformDescriptor descriptor = parseETL(configXml, module.getName());
                    if (descriptor != null)
                    {
                        l.add(descriptor);
                        _etls.put(descriptor.getId(), new Pair<Module, ScheduledPipelineJobDescriptor>(module, descriptor));
                    }
                }
            }
        }
        return l;
    }



    //
    // Tests
    //

    public static class TestCase extends Assert
    {
        @Test
        public void testCache()
        {
            Container home = ContainerManager.getHomeContainer();
            DescriptorCache.getDescriptors(home);
        }

        @Test
        public void scheduler() throws Exception
        {
            final AtomicInteger counter = new AtomicInteger(0);
            final String id = GUID.makeHash();
            final User user = TestContext.get().getUser();
            final Container c = JunitUtil.getTestContainer();

            ScheduledPipelineJobDescriptor d = new ScheduledPipelineJobDescriptor<ScheduledPipelineJobContext>()
            {
                @Override
                public String getId()
                {
                    return id;
                }

                @Override
                public String getName()
                {
                    return "TestCase";
                }

                @Override
                public String getDescription()
                {
                    return null;
                }

                @Override
                public String getModuleName()
                {
                    return "dataintegration";
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
                public Class<? extends Job> getQuartzJobClass()
                {
                    return TransformQuartzJobRunner.class;
                }

                @Override
                public ScheduledPipelineJobContext getJobContext(Container c, User user)
                {
                    return new ScheduledPipelineJobContext(this, c, user);
                }

                @Override
                public Callable<Boolean> getChecker(ScheduledPipelineJobContext context)
                {
                    return new Callable<Boolean>()
                    {
                        @Override
                        public Boolean call() throws Exception
                        {
                            counter.incrementAndGet();
                            return false;
                        }
                    };
                }

                @Override
                public PipelineJob getPipelineJob(ScheduledPipelineJobContext context) throws PipelineJobException
                {
                    return null;
                }
            };


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
