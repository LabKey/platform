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
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class ETLManager
{
    private static final ETLManager INSTANCE = new ETLManager();

    private static final Logger LOG = Logger.getLogger(ETLManager.class);

    private static final String JOB_GROUP_NAME = "ETL";

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

    public void schedule(ETLDescriptor etlDescriptor, Container container, User user)
    {
        ETLUpdateCheckerInfo info = new ETLUpdateCheckerInfo(etlDescriptor, container, user);
        JobDetail job = JobBuilder.newJob(ETLUpdateChecker.class)
            .withIdentity(info.getName(), JOB_GROUP_NAME).build();
        info.setOnJobDetails(job);


          // Trigger the job to run now, and then every 60 seconds
          Trigger trigger = TriggerBuilder.newTrigger()
              .withIdentity(info.getName(), JOB_GROUP_NAME)
              .startNow()
              .withSchedule(etlDescriptor.getScheduleBuilder())
              .build();

        try
        {
            StdSchedulerFactory.getDefaultScheduler().scheduleJob(job, trigger);
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
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
            etls.put(etl.getTransformId(), etl);

        SQLFragment sql = new SQLFragment("SELECT * FROM dataintegration.transformconfiguration WHERE enabled=?", true);
        ArrayList<TransformConfiguration> configs = new SqlSelector(scope, sql).getArrayList(TransformConfiguration.class);
        for (TransformConfiguration config : configs)
        {
            ETLDescriptor etl = etls.get(config.getTransformId());
            if (null == etl)
                continue;
            Container c = ContainerManager.getForId(config.getContainerId());
            if (null == c)
                continue;
            schedule(etl, c, null);
        }
    }
}
