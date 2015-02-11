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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.QueryService;
import org.labkey.di.pipeline.TaskRefTaskImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
    private static final String CUBE = "cube";
    private static final String SCHEMA = "schema";
    private static final String CONFIG_ID = "configId";
    private static final String ACTION = "action";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_WARM = "warm";
    private static final String ACTION_BOTH = "both";
    private static final String SCOPE = "scope";
    private static final String SCOPE_CONTAINER = "container";
    private static final String SCOPE_SITE = "site";

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        String action = settings.get(ACTION);
        if (action == null)
            action = ACTION_BOTH;
        String scope = settings.get(SCOPE);
        if (!SCOPE_SITE.equals(scope))
            scope = SCOPE_CONTAINER;
        performAction(action, scope);

        return new RecordedActionSet(makeRecordedAction());
    }

    private void performAction(String action, String scope) throws PipelineJobException
    {
        String cube = settings.get(CUBE);
        String schema = settings.get(SCHEMA);
        String configId = settings.get(CONFIG_ID);
        Set<Container> containers = new HashSet<>();

        if (SCOPE_CONTAINER.equals(scope))
            containers.add(containerUser.getContainer());
        else
            containers.addAll(findContainers(schema));

        switch (action)
        {
            case ACTION_REFRESH:
                for (Container c : containers)
                    refreshCube(c, cube);
                break;
            case ACTION_WARM:
                for (Container c : containers)
                    warmCube(c, cube, schema, configId);
                break;
            case ACTION_BOTH:
                for (Container c : containers)
                {
                    refreshCube(c, cube);
                    warmCube(c, cube, schema, configId);
                }
                break;
            default:
                logger.info("Unknown action specified: " + action + ". Refreshing and warming.");
                for (Container c : containers)
                {
                    refreshCube(c, cube);
                    warmCube(c, cube, schema, configId);
                }
            break;
        }
    }

    private void warmCube(Container c, String cube, String schema, String configId) throws PipelineJobException
    {
        logger.info("Warming cube " + cube + " in container " + c.getName());
        String warmResult = QueryService.get().warmCube(containerUser.getUser(), c, schema, configId, cube);
        // I hate to derive an exception case from an arbitrary string set elsewhere, but otherwise altering the signature of warmCube()
        // is more disruptive to other callers.
        if (warmResult.startsWith("Error"))
            throw new PipelineJobException(warmResult);
        logger.info(warmResult);
    }

    private void refreshCube(Container c, String cube)
    {
        logger.info("Refreshing cube " + cube + " in container " + c.getName());
        QueryService.get().cubeDataChanged(c);
        logger.info("Cube cache reset");
    }

    private Set<Container> findContainers(String schema) throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModuleForSchemaName(schema);
        if (module == null)
            throw new PipelineJobException("No module found for schema " + schema);
        return ContainerManager.getAllChildrenWithModule(ContainerManager.getRoot(), module);
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Arrays.asList(CUBE, SCHEMA, CONFIG_ID);
    }
}
