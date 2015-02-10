package org.labkey.study.pipeline;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * Created by klum on 2/9/2015.
 */
public class StudyReloadSourceJob extends StudyBatch implements Serializable, StudyReloadSourceJobSupport
{
    private String _reloadSourceName;

    public StudyReloadSourceJob(ViewBackgroundInfo info, PipeRoot root, String reloadSourceName) throws SQLException
    {
        super(info, null, root);
        _reloadSourceName = reloadSourceName;

        File logFile = new File(root.getRootPath(), FileUtil.makeFileNameWithTimestamp("study_reload_source", "log"));
        setLogFile(logFile);
    }

    protected File createLogFile()
    {
        return null;
    }

    @Override
    public String getStudyReloadSource()
    {
        return _reloadSourceName;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(StudyReloadSourceJob.class));
    }
}
