package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.QueryService;
import org.labkey.di.pipeline.TaskrefTaskImpl;

import java.util.Arrays;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 7/22/2014
 */

/**
 * Perform requested OLAP cube cache reset and/or warm operations on a specified cube/container.
 *
 * Requires settings for cube, schema, and configId.
 * "action" is an optional setting. Valid options are:
 *      refresh - reset the cube cache
 *      warm    - warm the cube
 *      both    - do both operations, reset, then rewarm
 * Default if not specified, or an invalid value is given, is "both"
 */
public class CubeMaintenanceTask extends TaskrefTaskImpl
{
    private static final String CUBE = "cube";
    private static final String SCHEMA = "schema";
    private static final String CONFIG_ID = "configId";
    private static final String ACTION = "action";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_WARM = "warm";
    private static final String ACTION_BOTH = "both";

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        String action = settings.get(ACTION);
        if (action == null)
            action = ACTION_BOTH;
        switch (action)
        {
            case ACTION_REFRESH:
                refreshCube();
                break;
            case ACTION_WARM:
                warmCube();
                break;
            case ACTION_BOTH:
                refreshCube();
                warmCube();
                break;
            default:
                logger.info("Unknown action specified: " + action + ". Refreshing and warming the cube.");
                refreshCube();
                warmCube();
            break;
        }

        return new RecordedActionSet(makeRecordedAction());
    }

    private void warmCube() throws PipelineJobException
    {
        logger.info("Warming cube " + settings.get(CUBE) + " in container " + containerUser.getContainer().getName());
        String warmResult = QueryService.get().warmCube(containerUser.getUser(), containerUser.getContainer(),
                settings.get(SCHEMA), settings.get(CONFIG_ID), settings.get(CUBE));
        // I hate to derive an exception case from an arbitrary string set elsewhere, but otherwise altering the signature of warmCube()
        // is more disruptive to other callers.
        if (warmResult.startsWith("Error"))
            throw new PipelineJobException(warmResult);
        logger.info(warmResult);
    }

    private void refreshCube()
    {
        logger.info("Refreshing cube " + settings.get(CUBE) + " in container " + containerUser.getContainer().getName());
        QueryService.get().cubeDataChanged(containerUser.getContainer());
        logger.info("Cube cache reset");
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Arrays.asList(CUBE, SCHEMA, CONFIG_ID);
    }
}
