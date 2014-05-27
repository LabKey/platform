package org.labkey.study.pipeline;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * Created by klum on 5/24/2014.
 */
public class SpecimenReloadJob extends SpecimenBatch implements Serializable, SpecimenReloadJobSupport
{
    private String _transformName;
    private SpecimenTransform.ExternalImportConfig _importConfig;

    public SpecimenReloadJob(ViewBackgroundInfo info, PipeRoot root, String transformName) throws IOException, SQLException
    {
        super(info, null, root, false);

        File logFile = new File(root.getRootPath(), FileUtil.makeFileNameWithTimestamp("specimen_reload", "log"));
        setLogFile(logFile);
        _transformName = transformName;
    }

    @Override
    public void setSpecimenArchive(File archiveFile)
    {
        _definitionFile = archiveFile;
    }

    @Override
    public String getSpecimenTransform()
    {
        return _transformName;
    }

    public void setExternalImportConfig(SpecimenTransform.ExternalImportConfig config)
    {
        _importConfig = config;
    }

    @Override
    public SpecimenTransform.ExternalImportConfig getExternalImportConfig()
    {
        return _importConfig;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(SpecimenReloadJob.class));
    }
}
