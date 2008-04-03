package org.labkey.api.exp;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.util.PageFlowUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.List;

/**
 * User: jeckels
 * Date: Oct 26, 2005
 */
public class ExperimentPipelineJob extends PipelineJob
{
    private static Logger _log = Logger.getLogger(ExperimentPipelineJob.class);

    private static final Object _experimentLock = new Object();

    private final File _xarFile;
    private final String _description;
    private final boolean _deleteExistingRuns;

    private transient XarSource _xarSource;

    public ExperimentPipelineJob(ViewBackgroundInfo info, File file, String description, boolean deleteExistingRuns) throws IOException, SQLException
    {
        super(ExperimentPipelineProvider.NAME, info);
        _xarFile = file;
        _description = description + " - " + file.getName();
        _deleteExistingRuns = deleteExistingRuns;

        _xarSource = createXarSource(file);
        setLogFile(_xarSource.getLogFile());

        header("XAR Import from " + _xarSource.toString());
    }

    private static XarSource createXarSource(File file)
    {
        if (file.getName().toLowerCase().endsWith(".xar"))
        {
            return new CompressedXarSource(file);
        }
        else
        {
            return new FileXarSource(file);
        }
    }

    private XarSource getXarSource()
    {
        if (_xarSource == null)
        {
            _xarSource = createXarSource(_xarFile);

            try
            {
                setLogFile(_xarSource.getLogFile());
            }
            catch (IOException e)
            {
                _log.error("Failed to get log file for " + _xarFile, e);
            }
        }
        return _xarSource;
    }

    public ActionURL getStatusHref()
    {
        ExpRun run = getXarSource().getExperimentRun();
        if (run != null)
        {
            return PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(run);
        }
        return null;
    }

    public String getDescription()
    {
        return _description;
    }

    public static boolean loadExperiment(PipelineJob job, XarSource source, boolean deleteExistingRuns)
    {
        try
        {
            source.init();
        }
        catch (Exception e)
        {
            job.getLogger().error("Failed to initialize XAR source", e);
            return false;
        }

        synchronized (_experimentLock)
        {
            job.getLogger().info("Starting to import XAR");

            try
            {
                List<ExpRun> runs = ExperimentService.get().loadXar(source, job, deleteExistingRuns);
                if (!runs.isEmpty())
                {
                    source.setExperimentRunRowId(runs.get(0).getRowId());
                }

                job.getLogger().info("");
                job.getLogger().info("XAR import completed successfully");
            }
            catch (Throwable t)
            {
                job.getLogger().info("");
                job.getLogger().fatal("Exception during import", t);
                job.getLogger().fatal("XAR import FAILED");
                if (t instanceof BatchUpdateException)
                {
                    job.getLogger().fatal("Underlying exception", ((BatchUpdateException)t).getNextException());
                }
                return false;
            }
            return true;
        }
    }

    public void run()
    {
        setStatus("LOADING EXPERIMENT");
        if (loadExperiment(this, getXarSource(), _deleteExistingRuns))
            setStatus(PipelineJob.COMPLETE_STATUS);
    }
}
