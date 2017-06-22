/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleDependencySorter;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.util.BreakpointThread;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.mule.JMSStatusWriter;
import org.labkey.pipeline.mule.LabKeySpringContainerContext;
import org.labkey.pipeline.mule.LoggerUtil;
import org.mule.config.ConfigurationException;
import org.mule.config.builders.MuleXmlConfigurationBuilder;
import org.mule.umo.manager.UMOManager;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Aug 7, 2008
 */
public abstract class AbstractPipelineStartup
{
    private static Logger _log = Logger.getLogger(AbstractPipelineStartup.class);

    /**
     * @return map from module name to BeanFactory
     */
    protected Map<String, BeanFactory> initContext(String log4JConfigPath, List<File> moduleFiles, List<File> moduleConfigFiles, List<File> customConfigFiles, File webappDir, PipelineJobService.LocationType locationType) throws IOException
    {
        LoggerUtil.initLogging(log4JConfigPath);

        //load the modules and sort them by dependencies
        ModuleLoader moduleLoader = new ModuleLoader();
        List<Module> modules = moduleLoader.doInit(moduleFiles);
        moduleLoader.setWebappDir(webappDir);
        ModuleDependencySorter sorter = new ModuleDependencySorter();
        modules = sorter.sortModulesByDependencies(modules);

        // Horrible hack to work around failure to respect module dependencies in non-built file based modules
        Module pipelineModule = null;
        Module experimentModule = null;
        for (Module module : modules)
        {
            if ("pipeline".equalsIgnoreCase(module.getName()))
            {
                pipelineModule = module;
            }
            else if ("experiment".equalsIgnoreCase(module.getName()))
            {
                experimentModule = module;
            }
        }
        if (pipelineModule != null)
        {
            modules.remove(pipelineModule);
            modules.add(0, pipelineModule);
        }
        if (experimentModule != null)
        {
            modules.remove(experimentModule);
            modules.add(1, experimentModule);
        }

        Map<String, BeanFactory> result = new CaseInsensitiveHashMap<>();

        // Set up the PipelineJobService so that Spring can configure it
        PipelineJobServiceImpl.initDefaults(locationType);

        for (final Module module : modules)
        {
            List<String> springConfigPaths = new ArrayList<>();
            File moduleConfig = findFile(moduleConfigFiles, module.getName() + "Context.xml");
            if (moduleConfig != null)
            {
                springConfigPaths.add(moduleConfig.getAbsoluteFile().toURI().toString());
            }
            File customConfig = findFile(customConfigFiles, module.getName() + "Config.xml");
            if (customConfig != null)
            {
                springConfigPaths.add(customConfig.getAbsoluteFile().toURI().toString());
            }

            if (!springConfigPaths.isEmpty())
            {
                _log.info("Loading Spring configuration for the " + module.getName() + " module from " + springConfigPaths);

                // Initialize the Spring context
                BeanFactory context = new FileSystemXmlApplicationContext(springConfigPaths.toArray(new String[springConfigPaths.size()]))
                {
                    @Override
                    public String getDisplayName()
                    {
                        return module.getName();
                    }
                };
                result.put(module.getName(), context);
            }
        }

        return result;
    }

    /**
     * Start up a thread that listens for a thread dump request, and lets us hit a breakpoint easily
     */
    protected static void doSharedStartup(List<File> moduleFiles)
    {
        for (File moduleFile : moduleFiles)
        {
            if (moduleFile.getName().toLowerCase().startsWith("core"))
            {
                BreakpointThread thread = new BreakpointThread();
                thread.start();
                new DebugInfoDumper(moduleFile.getParentFile());
                break;
            }
        }
    }

    /**
     * Check if JMS is configured and spin up mule configuration if true
     */
    protected UMOManager setupMuleConfig(String muleConfig, Map<String, BeanFactory> factories, String hostName) throws PipelineJobException
    {
        PipelineStatusFile.StatusWriter writer = PipelineJobServiceImpl.get().getStatusWriter();
        if (writer instanceof JMSStatusWriter)
        {
            try
            {
                PipelineJobServiceImpl.get().getStatusWriter().setHostName(hostName);
                LabKeySpringContainerContext.setContext(factories.get(PipelineService.MODULE_NAME));

                // Hack - wait a little bit for Mule to connect to the JMS server
                Thread.sleep(5000);

                // Spin up the mule configuration
                MuleXmlConfigurationBuilder builder = new MuleXmlConfigurationBuilder();
                return builder.configure(muleConfig);
            }
            catch (InterruptedException | ConfigurationException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return null;
    }

    private File findFile(List<File> files, String name)
    {
        for (File file : files)
        {
            if (file.getName().equalsIgnoreCase(name))
            {
                return file;
            }
        }
        return null;
    }

}