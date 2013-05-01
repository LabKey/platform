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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.di.DataIntegrationDbSchema;
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;
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
import org.quartz.impl.matchers.KeyMatcher;
import org.quartz.listeners.JobListenerSupport;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformManager implements DataIntegrationService
{
    private static final TransformManager INSTANCE = new TransformManager();

    private static final Logger LOG = Logger.getLogger(TransformManager.class);

    private static final String JOB_GROUP_NAME = "org.labkey.di.pipeline.ETLManager";

    private static boolean _shuttingDown = false;


    public static TransformManager get()
    {
        return INSTANCE;
    }


    private final Map<String,Pair<Module,ScheduledPipelineJobDescriptor>> _etls =
            Collections.synchronizedMap(new CaseInsensitiveTreeMap<Pair<Module, ScheduledPipelineJobDescriptor>>());


    private TransformManager()
    {
    }


    private BaseQueryTransformDescriptor parseETL(Resource resource, String moduleName)
    {
        if (resource != null && resource.isFile())
        {
            try
            {
                return new BaseQueryTransformDescriptor(resource, moduleName);
            }
            catch (IOException e)
            {
                LOG.warn("Unable to parse " + resource, e);
            }
            catch (XmlException e)
            {
                LOG.warn("Unable to parse " + resource, e);
            }
        }
        return null;
    }


    @NotNull
    public Collection<ScheduledPipelineJobDescriptor> getRegisteredDescriptors()
    {
        ArrayList<ScheduledPipelineJobDescriptor> list = new ArrayList<ScheduledPipelineJobDescriptor>(_etls.size());
        synchronized (_etls)
        {
            for (Pair<Module,ScheduledPipelineJobDescriptor> d : _etls.values())
                list.add(d.getValue());
        }
        return list;
    }


    @NotNull
    public Collection<ScheduledPipelineJobDescriptor> getDescriptors(Container c)
    {
        Set<Module> modules = c.getActiveModules();
        ArrayList<ScheduledPipelineJobDescriptor> list = new ArrayList<ScheduledPipelineJobDescriptor>(_etls.size());
        synchronized (_etls)
        {
            for (Pair<Module,ScheduledPipelineJobDescriptor> p : _etls.values())
                if (modules.contains(p.getKey()))
                    list.add(p.getValue());
        }
        return list;
    }


    @Nullable
    public ScheduledPipelineJobDescriptor getDescriptor(String id)
    {
        Pair<Module,ScheduledPipelineJobDescriptor> p = _etls.get(id);
        if (null == p)
            return null;
        return p.getValue();
    }


    public synchronized void runNow(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        if (_shuttingDown)
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
                .usingJobData(info.getJobDataMap())
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
        if (_shuttingDown)
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
                .usingJobData(info.getJobDataMap())
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
        JobDetail job = JobBuilder.newJob(descriptor.getJobClass())
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
        DbScope scope = DbSchema.get("dataintegration").getScope();

        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration WHERE enabled=?", true);
        ArrayList<TransformConfiguration> configs = new SqlSelector(scope, sql).getArrayList(TransformConfiguration.class);
        for (TransformConfiguration config : configs)
        {
            // CONSIDER explicit runAs user
            int runAsUserId = config.getModifiedBy();
            User runAsUser = UserManager.getUser(runAsUserId);

            Pair<Module,ScheduledPipelineJobDescriptor> etl = _etls.get(config.getTransformId());
            if (null == etl)
                continue;
            Container c = ContainerManager.getForId(config.getContainerId());
            if (null == c)
                continue;
            schedule(etl.getValue(), c, runAsUser, config.isVerboseLogging());
        }
    }


    public void shutdownPre()
    {
        _shuttingDown = true;
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

        if (expRunIds != null && expRunIds.length > 0)
            return expRunIds[0];

        return null;
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


    @Override
    public void registerDescriptors(Module module, Collection<ScheduledPipelineJobDescriptor> descriptors)
    {
        if (null == descriptors || descriptors.isEmpty())
            return;
        Map<String,Pair<Module,ScheduledPipelineJobDescriptor>> m = new TreeMap<String,Pair<Module,ScheduledPipelineJobDescriptor>>();
        for (ScheduledPipelineJobDescriptor d : descriptors)
            m.put(d.getId(), new Pair(module,d));
        _etls.putAll(m);
    }


    @Override
    public Collection<ScheduledPipelineJobDescriptor> loadDescriptorsFromFiles(Module module, boolean autoRegister)
    {
        Path etlsDirPath = new Path("etls");
        Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);
        ArrayList<ScheduledPipelineJobDescriptor> l = new ArrayList<ScheduledPipelineJobDescriptor>();

        if (etlsDir != null && etlsDir.isCollection())
        {
            for (Resource etlDir : etlsDir.list())
            {
                BaseQueryTransformDescriptor descriptor = parseETL(etlDir.find("config.xml"), module.getName());
                if (descriptor != null)
                {
                    l.add(descriptor);
                    if (autoRegister)
                        _etls.put(descriptor.getId(), new Pair<Module,ScheduledPipelineJobDescriptor>(module,descriptor));
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
                public Class<? extends Job> getJobClass()
                {
                    return TransformJobRunner.class;
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
            TransformManager.get().runNow(d, c, user);
            for (int i=0 ; i<10 && counter.get()<1 ; i++)
                sleep(1);
            assertEquals(1, counter.get());
            TransformManager.get().runNow(d, c, user);
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

/*
rewrite to use vehicle schema?

        @Test
        public void etlChecker() throws Exception
        {
            User u = TestContext.get().getUser();
            Container root = JunitUtil.getTestContainer();
            Container c = ContainerManager.createContainer(root, "junit_etl_" + GUID.makeHash(), "junit temp", "temp", Container.TYPE.normal, u);

            try
            {
                _etlChecker(c, u);
            }
            finally
            {
                ContainerManager.delete(c,u);
            }
        }
 */


        public void _etlChecker(Container c, User u) throws Exception
        {
            User newUser = null;
            final AtomicInteger checkerComplete = new AtomicInteger(0);
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            JobListenerSupport ujl = new JobListenerSupport()
            {
                @Override
                public String getName()
                {
                    return "UserJobListener";
                }

                @Override
                public void jobWasExecuted(org.quartz.JobExecutionContext context, org.quartz.JobExecutionException jobException)
                {
                    checkerComplete.incrementAndGet();
                }
            };

            // verify we have our known descriptors for the DataIntegration module
            Collection<ScheduledPipelineJobDescriptor> descriptors = TransformManager.get().getRegisteredDescriptors();
            assertTrue(descriptors.size() >= 2);

            // verify descriptors and underlying checker metadata
            ScheduledPipelineJobDescriptor issuesDescriptor = findDescriptor(descriptors, "Issues");
            ScheduledPipelineJobDescriptor usersDescriptor = findDescriptor(descriptors, "Hello World");

            assertTrue(usersDescriptor != null && issuesDescriptor != null);
            verifyDescriptor(usersDescriptor, c, u, "core", "users");
            verifyDescriptor(issuesDescriptor, c, u, "issues", "issues");

            // run the users's transform.  Since we cleared out the transformations the checker should run
            // and we should have one job in the tables
            JobKey jobKey = jobKeyFromDescriptor(usersDescriptor);
            scheduler.getListenerManager().addJobListener(ujl, KeyMatcher.keyEquals(jobKey));

            TransformManager.get().runNow(usersDescriptor, c, u);
            assertTrue(waitForChecker(checkerComplete));
            verifyJobs(c, u, usersDescriptor, 1);

            // run again and ensure that jobs table remain the same
            // checker ran but no job scheduled
            TransformManager.get().runNow(usersDescriptor, c, u);
            assertTrue(waitForChecker(checkerComplete));
            verifyJobs(c, u, usersDescriptor, 1);

            // now add a user and ensure we have 2 jobs
            try
            {
                // add a user so that the checker returns true
                newUser = createUser();
                TransformManager.get().runNow(usersDescriptor, c, u);
                assertTrue(waitForChecker(checkerComplete));
                verifyJobs(c, u, usersDescriptor, 2);
            }
            finally
            {
                if (newUser != null)
                {
                    UserManager.deleteUser(newUser.getUserId());
                }
            }
        }

        public User createUser() throws Exception
        {
            ValidEmail email = new ValidEmail("xformtest@labkey.com");
            // we want to force a change to the user's table so delete the user and recreate if it
            // already exists
            User u = UserManager.getUser(email);
            if (null != u)
            {
                UserManager.deleteUser(u.getUserId());
            }

            return SecurityManager.addUser(email).getUser();
        }

        // if job does not run in the allotted time then returns false
        public boolean waitForChecker(AtomicInteger checkerComplete)
        {
            for (int i = 0; i < 10; i++)
            {
                if (checkerComplete.get() > 0)
                {
                    checkerComplete.set(0);
                    return true;
                }
                sleep(1);
            }

            return false;
        }

//        void cleanTransforms(Container c, User u) throws Exception
//        {
//            // clean out any transform jobs we have lying around for the junit container and user
//            TableInfo statusInfo = DbSchema.get("pipeline").getTable("statusfiles");
//            TableInfo runsInfo = DataIntegrationDbSchema.getTransformRunTableInfo();
//            Table.delete(runsInfo, getDeleteFilter(runsInfo, c, u));
//            Table.delete(statusInfo, getDeleteFilter(statusInfo, c, u));
//        }

        SimpleFilter getDeleteFilter(TableInfo ti, Container c, User u)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(ti.getColumn("container"), c.getId());
            filter.addCondition(ti.getColumn("createdby"), u.getUserId());
            return filter;
        }

        void verifyTable(TableInfo ti, int expectedRows, Container c, String columnName, String columnValue) throws  Exception
        {
            SimpleFilter f = new SimpleFilter();
            if (null != c && null != ti.getColumn("container"))
                f = f.addCondition(new FieldKey(null,"container"), c.getId());
            if (null != columnName)
                f = f.addCondition(new FieldKey(null,columnName), columnValue, CompareType.CONTAINS);

            TableSelector ts = new TableSelector(ti, ti.getColumns(), f, null);
            assertEquals((int)expectedRows, (int)ts.getRowCount());

//            Results r = ts.getResults(false);
//            while (r.next())
//            {
//                // use contains instead of strict equality since the id is prefixed by "ETL Job: "
//                String act = r.getString(columnName).toLowerCase();
//                String exp = columnValue.toLowerCase();
//                assertTrue(act.contains(exp));
//            }
//            r.close();
        }

        void verifyPipelineJobs(Container c, User u, ScheduledPipelineJobDescriptor d, int expectedRows) throws Exception
        {
            TableInfo jobsTable = PipelineService.get().getJobsTable(u, c);
            verifyTable(jobsTable, expectedRows, c, "description", d.getDescription());
        }

        void verifyTransformRuns(Container c, User u, ScheduledPipelineJobDescriptor d, int expectedRows) throws Exception
        {
            TableInfo runsTable =  DataIntegrationDbSchema.getTransformRunTableInfo();
            verifyTable(runsTable, expectedRows, c, "transformid", d.getId());

        }

        void verifyJobs(Container c, User u, ScheduledPipelineJobDescriptor d, int expectedRows) throws Exception
        {
            verifyTransformRuns(c, u, d, expectedRows);
            verifyPipelineJobs(c, u, d, expectedRows);
        }

        ScheduledPipelineJobDescriptor findDescriptor(Collection<ScheduledPipelineJobDescriptor> descriptors, String name)
        {
            ScheduledPipelineJobDescriptor target = null;

            for (ScheduledPipelineJobDescriptor jd : descriptors)
            {
                if (StringUtils.equalsIgnoreCase(jd.getModuleName(), "DataIntegration"))
                {
                    if (StringUtils.equalsIgnoreCase(jd.getName(), name))
                    {
                        target = jd;
                        break;
                    }
                }
            }

            return target;
        }

        void verifyDescriptor(ScheduledPipelineJobDescriptor d, Container c, User u, String sourceSchema, String sourceQuery)
        {
            // ensure this is an instance of BaseQueryTransform
            BaseQueryTransformDescriptor bd = (BaseQueryTransformDescriptor)d;
            assertTrue(bd != null);
            assertTrue(d.getDescription() != null);
            assertTrue(d.getId() != null);
            TransformJobContext tc = bd.getJobContext(c, u);
            UpdatedRowsChecker checker = (UpdatedRowsChecker) bd.getChecker(tc);
            verifyRowsChecker(checker, sourceSchema, sourceQuery);
        }

        void verifyRowsChecker(UpdatedRowsChecker checker, String sourceSchema, String sourceQuery)
        {
            assertTrue(checker != null);
//            assertTrue(StringUtils.equalsIgnoreCase(sourceQuery, checker.getSourceQueryName()));
//            SchemaKey key = SchemaKey.fromString(sourceSchema);
//            assertTrue(checker.getSourceSchemaName().equals(key));
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

        public Class<? extends Job> getJobClass()
        {
            return _d.getJobClass();
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
