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
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
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

    private final List<ETLDescriptor> _etls;


    private ETLManager()
    {
        _etls = new ArrayList<ETLDescriptor>();

        Path etlsDirPath = new Path("etls");

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);
            if (etlsDir != null)
            {
                for (Resource etlDir : etlsDir.list())
                {
                    ETLDescriptor descriptor = parseETL(etlDir.find("config.xml"), module.getName());
                    if (descriptor != null)
                    {
                        _etls.add(descriptor);
                    }
                }
            }
        }
    }


    private ETLDescriptor parseETL(Resource resource, String moduleName)
    {
        if (resource != null && resource.isFile())
        {
            try
            {
                return new ETLDescriptor(resource, moduleName);
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


    public List<ETLDescriptor> getETLs()
    {
        return _etls;
    }


    public synchronized void runNow(ETLDescriptor etlDescriptor, Container container, User user)
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            ETLUpdateCheckerInfo info = new ETLUpdateCheckerInfo(etlDescriptor, container, user);

            // find job
            JobKey jobKey = JobKey.jobKey(info.getName(), JOB_GROUP_NAME);
            JobDetail job = null;
            if (!scheduler.checkExists(jobKey))
            {
                job = JobBuilder.newJob(ETLUpdateChecker.class)
                    .withIdentity(jobKey)
                    .build();
            }

            TriggerKey triggerKey = TriggerKey.triggerKey(info.getName()+ GUID.makeHash(), JOB_GROUP_NAME);
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


    public synchronized void schedule(ETLDescriptor etlDescriptor, Container container, User user)
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            ETLUpdateCheckerInfo info = new ETLUpdateCheckerInfo(etlDescriptor, container, user);

            // find job
            JobKey jobKey = JobKey.jobKey(info.getName(), JOB_GROUP_NAME);
            JobDetail job = null;
            if (!scheduler.checkExists(jobKey))
            {
                job = JobBuilder.newJob(ETLUpdateChecker.class)
                    .withIdentity(jobKey).build();
                info.setOnJobDetails(job);
            }

            // find trigger
            // Trigger the job to run now, and then every 60 seconds
            TriggerKey triggerKey = TriggerKey.triggerKey(info.getName(), JOB_GROUP_NAME);
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (null != trigger)
                return;
            trigger = TriggerBuilder.newTrigger()
                .forJob(jobKey)
                .withIdentity(triggerKey)
                .startNow()
                .withSchedule(etlDescriptor.getScheduleBuilder())
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


    public synchronized void unschedule(ETLDescriptor etlDescriptor, Container container, User user)
    {
        try
        {
            ETLUpdateCheckerInfo info = new ETLUpdateCheckerInfo(etlDescriptor, container, user);
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            TriggerKey triggerKey = TriggerKey.triggerKey(info.getName(), JOB_GROUP_NAME);
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


    boolean dumpScheduler()
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            {
            LOG.debug("Jobs");
            Set<JobKey> keys = scheduler.getJobKeys((GroupMatcher<JobKey>)GroupMatcher.groupEquals(JOB_GROUP_NAME));
            for (JobKey key : keys)
                LOG.debug("\t" + key.toString());
            }

            {
            LOG.debug("Triggers");
            Set<TriggerKey> keys = scheduler.getTriggerKeys((GroupMatcher<TriggerKey>)GroupMatcher.groupEquals(JOB_GROUP_NAME));
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

        CaseInsensitiveHashMap<ETLDescriptor> etls = new CaseInsensitiveHashMap<ETLDescriptor>();
        for (ETLDescriptor etl : getETLs())
        {
            etls.put(etl.getTransformId(), etl);
        }

        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration WHERE enabled=?", true);
        ArrayList<TransformConfiguration> configs = new SqlSelector(scope, sql).getArrayList(TransformConfiguration.class);
        for (TransformConfiguration config : configs)
        {
            // CONSIDER explicit runAs user
            int runAsUserId = config.getModifiedBy();
            User runAsUser = UserManager.getUser(runAsUserId);

            ETLDescriptor etl = etls.get(config.getTransformId());
            if (null == etl)
                continue;
            Container c = ContainerManager.getForId(config.getContainerId());
            if (null == c)
                continue;
            schedule(etl, c, runAsUser);
        }
    }
}
