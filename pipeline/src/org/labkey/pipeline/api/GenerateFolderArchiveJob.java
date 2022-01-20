package org.labkey.pipeline.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

public class GenerateFolderArchiveJob extends PipelineJob
{
    private String _reloadSourceName;

    @SuppressWarnings("unused")
    // For serialization
    protected GenerateFolderArchiveJob()
    {
    }

    public GenerateFolderArchiveJob(@Nullable String provider, ViewBackgroundInfo info, @NotNull PipeRoot root, @NotNull String reloadSourceName)
    {
        super(provider, info, root);
        setupLocalDirectoryAndJobLog(root, "GenerateFolderArchive", FileUtil.makeFileNameWithTimestamp("generate_folder_archive", ".log"));
        _reloadSourceName = reloadSourceName;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return getActionURL(); //??
    }

    @Override
    public String getDescription()
    {
        return "Export folder archive";
    }

    @Override
    public void run()
    {
        info("Generating folder archive");
        StudyReloadSource reloadSource = StudyService.get().getStudyReloadSource(_reloadSourceName);
        reloadSource.generateReloadSource(this, StudyService.get().getStudy(getContainer()));
        info("Successfully generated folder archive");
        setStatus(PipelineJob.TaskStatus.complete);
    }
}
