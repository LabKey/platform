/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.di.steps;

import org.apache.commons.lang3.EnumUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.writer.ContainerUser;
import org.labkey.di.pipeline.TaskRefTaskImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 7/22/2014
 */

/**
 * Perform requested OLAP cube cache reset and/or warm operations on a specified cube/container.
 *
 * Requires settings for cube, schema, and configId.
 *
 * "action" is an optional setting. Valid options are:
 *      refresh - reset the cube cache
 *      warm    - warm the cube
 *      both    - do both operations, reset, then rewarm
 * Default if not specified or an invalid value is given is "both"
 *
 * "scope" is an optional setting. Valid options are:
 *      container - perform the action only for the calling container
 *      site      - perform the action for ALL containers using the module which claims the specified schema
 * Default if not specified or invalid value is given is "container". (This effectively makes "project", "folder" and "container" synonyms.)
 */
public class CubeMaintenanceTask extends TaskRefTaskImpl
{

    private enum Action
    {
        @SuppressWarnings({"UnusedDeclaration"})
        refresh
                {
                    @Override
                    protected void doIt(User u, Map<Setting, String> settings, Set<Container> containers, Logger logger) throws PipelineJobException
                    {
                        for (Container c : containers)
                            refreshCube(c, settings.get(Setting.cube), logger);
                    }
                },
        @SuppressWarnings({"UnusedDeclaration"})
        warm
                {
                    @Override
                    protected void doIt(User u, Map<Setting, String> settings, Set<Container> containers, Logger logger) throws PipelineJobException
                    {
                        for (Container c : containers)
                            warmCube(c, u, settings, logger);
                    }
                },
        both
                {
                    @Override
                    protected void doIt(User u, Map<Setting, String> settings, Set<Container> containers, Logger logger) throws PipelineJobException
                    {
                        for (Container c : containers)
                        {
                            refreshCube(c, settings.get(Setting.cube), logger);
                            warmCube(c, u, settings, logger);
                        }
                    }
                };

        protected abstract void doIt(User u, Map<Setting, String> settings, Set<Container> containers, Logger logger) throws PipelineJobException;

        protected void run(Map<Setting, String> settings, ContainerUser cu, Logger logger) throws PipelineJobException
        {
            Set<Container> containers = new HashSet<>();
            if (Scope.container.name().equals(settings.get(Setting.scope)))
                containers.add(cu.getContainer());
            else
                containers.addAll(findContainers(settings.get(Setting.schema)));
            doIt(cu.getUser(), settings, containers, logger);
        };
    }

    private enum Scope
    {
        @SuppressWarnings({"UnusedDeclaration"})
        site,
        container
    }

    private enum Setting
    {
        cube,
        schema,
        configId,
        action,
        scope
    }

    private Map<Setting, String> enumSettings = new HashMap<>();
    
    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        Action action = Action.valueOf(enumSettings.get(Setting.action));
        action.run(enumSettings, containerUser, job.getLogger());
        return new RecordedActionSet(makeRecordedAction());
    }

    private static void warmCube(Container c, User u, Map<Setting, String> settings, Logger logger) throws PipelineJobException
    {
        logger.info("Warming cube " + settings.get(Setting.cube) + " in container " + c.getName());
        String warmResult = QueryService.get().warmCube(u, c, settings.get(Setting.schema), settings.get(Setting.configId), settings.get(Setting.cube));
        // I hate to derive an exception case from an arbitrary string set elsewhere, but otherwise altering the signature of warmCube()
        // is more disruptive to other callers.
        if (warmResult.startsWith("Error"))
            throw new PipelineJobException(warmResult);
        logger.info(warmResult);
    }

    private static void refreshCube(Container c, String cube, Logger logger)
    {
        logger.info("Refreshing cube " + cube + " in container " + c.getName());
        QueryService.get().cubeDataChanged(c);
        logger.info("Cube cache reset");
    }

    private static Set<Container> findContainers(String schema) throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModuleForSchemaName(schema);
        if (module == null)
            throw new PipelineJobException("No module found for schema " + schema);
        return ContainerManager.getAllChildrenWithModule(ContainerManager.getRoot(), module);
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.unmodifiableList(Arrays.asList(Setting.cube.name(), Setting.schema.name(), Setting.configId.name()));
    }

    @Override
    public void setSettings(Map<String, String> xmlSettings) throws XmlException
    {
        super.setSettings(xmlSettings);

        for (Setting enumSetting : Setting.values())
            enumSettings.put(enumSetting, settings.get(enumSetting.name()));

        // Unfortunately no way to validate cube name or configId, but we can validate the schema name
        if (null == ModuleLoader.getInstance().getModuleForSchemaName(enumSettings.get(Setting.schema)))
            throw new XmlException("No module found for schema " + enumSettings.get(Setting.schema));

        // Defaults for optional parameters
        if (!EnumUtils.isValidEnum(Scope.class, enumSettings.get(Setting.scope)))
            enumSettings.put(Setting.scope, Scope.container.name());
        if (!EnumUtils.isValidEnum(Action.class, enumSettings.get(Setting.action)))
            enumSettings.put(Setting.action, Action.both.name());
    }
}
