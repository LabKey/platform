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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.di.api.ScheduledPipelineJobContext;
import org.labkey.di.api.ScheduledPipelineJobDescriptor;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class ETLManager
{
    private static final ETLManager INSTANCE = new ETLManager();

    private static final Logger LOG = Logger.getLogger(ETLManager.class);

    private static final String JOB_GROUP_NAME = "org.labkey.di.pipeline.ETLManager";

    public static ETLManager get()
    {
        return INSTANCE;
    }

    private final List<ScheduledPipelineJobDescriptor> _etls;


    private ETLManager()
    {
        _etls = new ArrayList<ScheduledPipelineJobDescriptor>();

        Path etlsDirPath = new Path("etls");

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);
            if (etlsDir != null)
            {
                for (Resource etlDir : etlsDir.list())
                {
                    BaseQueryTransformDescriptor descriptor = parseETL(etlDir.find("config.xml"), module.getName());
                    if (descriptor != null)
                    {
                        _etls.add(descriptor);
                    }
                }
            }
        }
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


    public List<ScheduledPipelineJobDescriptor> getETLs()
    {
        return _etls;
    }


    public synchronized void runNow(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            ScheduledPipelineJobContext info = descriptor.getJobContext(container, user);

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


    public synchronized void schedule(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            ScheduledPipelineJobContext info = descriptor.getJobContext(container, user);

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

        CaseInsensitiveHashMap<ScheduledPipelineJobDescriptor> etls = new CaseInsensitiveHashMap<ScheduledPipelineJobDescriptor>();
        for (ScheduledPipelineJobDescriptor etl : getETLs())
        {
            etls.put(etl.getId(), etl);
        }

        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration WHERE enabled=?", true);
        ArrayList<TransformConfiguration> configs = new SqlSelector(scope, sql).getArrayList(TransformConfiguration.class);
        for (TransformConfiguration config : configs)
        {
            // CONSIDER explicit runAs user
            int runAsUserId = config.getModifiedBy();
            User runAsUser = UserManager.getUser(runAsUserId);

            ScheduledPipelineJobDescriptor etl = etls.get(config.getDescriptionId());
            if (null == etl)
                continue;
            Container c = ContainerManager.getForId(config.getContainerId());
            if (null == c)
                continue;
            schedule(etl, c, runAsUser);
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void scheduler() throws Exception
        {
            final AtomicInteger counter = new AtomicInteger(0);
            final String id = GUID.makeHash();
            final User user = TestContext.get().getUser();
            final Container c = JunitUtil.getTestContainer();

            ScheduledPipelineJobDescriptor d = new ScheduledPipelineJobDescriptor()
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
                public PipelineJob getPipelineJob(ScheduledPipelineJobContext context) throws JobExecutionException
                {
                    return null;
                }
            };


            assertEquals(0, counter.get());
            ETLManager.get().runNow(d, c, user);
            for (int i=0 ; i<10 && counter.get()<1 ; i++)
                sleep(1);
            assertEquals(1, counter.get());
            ETLManager.get().runNow(d, c, user);
            for (int i=0 ; i<10 && counter.get()<2 ; i++)
                sleep(2);
            assertEquals(2, counter.get());

            ETLManager.get().schedule(d, c, user);
            sleep(5);
            assertTrue(counter.get() <= 10);
            for (int i=0 ; i<10 && counter.get() < 12 ; i++)
                sleep(1);
            assertTrue(counter.get() >= 12);

            ETLManager.get().unschedule(d, c, user);
            sleep(1);
            int count = counter.get();
            sleep(2);
            assertEquals(count, counter.get());

            // TODO don't have an official way to unregister a job
            // and we're currently adding them as durable
            JobKey jobKey = jobKeyFromDescriptor(d);
            assertTrue(StdSchedulerFactory.getDefaultScheduler().checkExists(jobKey));
            StdSchedulerFactory.getDefaultScheduler().deleteJob(jobKey);
            assertFalse(StdSchedulerFactory.getDefaultScheduler().checkExists(jobKey));
        }

        void sleep(int sec)
        {
            try
            {
                Thread.sleep(2*1000);
            }
            catch (InterruptedException x)
            {
            }
        }
    }

}
