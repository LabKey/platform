package org.labkey.di.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 10/19/13
 */
public class TransformUtils
{
    /*

    Convenience class to group/wrap ETL methods separate from UI functionality. Initial intent is to aid unit testing of ETL jobs,
    though there could be additional utility.

     */

    public static void setEnabled(String transformId, Container c, User u, boolean enabled)
    {
        ScheduledPipelineJobDescriptor etl = getDescriptor(transformId);
        TransformConfiguration config = getTransformConfiguration(transformId, c);
        config.setEnabled(enabled);
        config = saveTransformConfiguration(config, u);

        if (config.isEnabled())
        {
            TransformManager.get().schedule(etl, c, u, config.isVerboseLogging());
        }
        else
        {
            TransformManager.get().unschedule(etl, c, u);
        }
    }

    public static boolean isEnabled(String transformId, Container c)
    {
        return getTransformConfiguration(transformId, c).isEnabled();
    }

    public static void setVerbose(String transformId, Container c, User u, boolean verbose)
    {
        TransformConfiguration config = getTransformConfiguration(transformId, c);
        config.setVerboseLogging(verbose);
        saveTransformConfiguration(config, u);
    }

    public static boolean isVerbose(String transformId, Container c)
    {
        return getTransformConfiguration(transformId, c).isVerboseLogging();
    }

    public static TransformConfiguration getTransformConfiguration(String transformId, Container c)
    {
        TransformConfiguration ret = TransformManager.get().getTransformConfiguration(c, getDescriptor(transformId));
        if (null != ret)
            return ret;
        // If it didn't exist, create a new one. TransformManager getTransformConfiguration should have already done this,
        // but for safety, do it here as well.
        return saveTransformConfiguration(new TransformConfiguration(getDescriptor(transformId), c), null);
    }

    public static TransformConfiguration saveTransformConfiguration(TransformConfiguration config, User u)
    {
        return TransformManager.get().saveTransformConfiguration(u, config);
    }

    public static void setSchedule(String transformId, Long interval)
    {
        throw new UnsupportedOperationException();
    }

    public static String getSchedule(String transformId)
    {
        return getDescriptor(transformId).getScheduleDescription();
    }

    public static String getTransformRunStatus(int jobId, Container c)
    {
        return getTransformRun(jobId, c).getStatus();
    }

    public static String getPipelineStatus(int jobId)
    {
        return getStatusFile(jobId).getStatus();
    }

    public static String getLogFilePath(int jobId)
    {
        return getStatusFile(jobId).getFilePath();
    }

    public static PipelineStatusFile getStatusFile(int rowId)
    {
        return PipelineService.get().getStatusFile(rowId);
    }

    public static TransformRun getTransformRun(int jobId, Container c)
    {
        return TransformManager.get().getTransformRunForJob(c, jobId);
    }

    /**
     * Returns a transformId -> name map of the transforms in a given container
     * @param c Container of interest
     * @return Map of transformId's -> names
     */
    public static Map<String, String> getTransforms(Container c)
    {
        Map<String, String> ret = new HashMap<>();

        for (ScheduledPipelineJobDescriptor etl : getDescriptors(c))
        {
            ret.put(etl.getId(), etl.getName());
        }

        return ret;
    }

    public static Collection<ScheduledPipelineJobDescriptor> getDescriptors(Container c)
    {
        return TransformManager.get().getDescriptors(c);
    }

    public static void resetState(String transformId, Container c, User u)
    {
        TransformConfiguration config = getTransformConfiguration(transformId, c);
        config.setTransformState(null);
        saveTransformConfiguration(config, u);
    }

    /**
     * Schedule a job to run immediately, equivalent to using the "Run Now" button in the UI
     * @param transformId The transform to run
     * @param c Its container
     * @param u User authorized to run the transform
     * @return jobId of the scheduled pipeline job. null if no work to do.
     * @throws PipelineJobException
     */
    public static Integer runEtl(String transformId, Container c, User u) throws PipelineJobException
    {
        return TransformManager.get().runNowPipeline(getDescriptor(transformId), c, u);
    }

    public static ScheduledPipelineJobDescriptor getDescriptor(String transformId)
    {
        ScheduledPipelineJobDescriptor etl = TransformManager.get().getDescriptor(transformId);
        if (null == etl)
            throw new NotFoundException(transformId);
        return etl;
    }
}
