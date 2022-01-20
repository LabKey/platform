package org.labkey.pipeline.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.study.FolderArchiveSource;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

public class GenerateFolderArchiveJob extends PipelineJob
{
    private String _archiveSourceName;

    @SuppressWarnings("unused")
    // For serialization
    protected GenerateFolderArchiveJob()
    {
    }

    public GenerateFolderArchiveJob(@Nullable String provider, ViewBackgroundInfo info, @NotNull PipeRoot root, @NotNull String archiveSourceName)
    {
        super(provider, info, root);
        setupLocalDirectoryAndJobLog(root, "GenerateFolderArchive", FileUtil.makeFileNameWithTimestamp("generate_folder_archive", ".log"));
        _archiveSourceName = archiveSourceName;
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
        StudyService ss = StudyService.get();
        if (null == ss)
        {
            setStatus(TaskStatus.error, "StudyService is not available");
        }
        else
        {
            Study study = ss.getStudy(getContainer());

            if (null == study)
            {
                setStatus(TaskStatus.error, "No study is available in this folder");
            }
            else
            {
                FolderArchiveSource folderArchiveSource = PipelineService.get().getFolderArchiveSource(_archiveSourceName);

                if (null == folderArchiveSource)
                {
                    setStatus(TaskStatus.error, "Folder archive source named \"" + _archiveSourceName + "\" is not registered");
                }
                else
                {
                    info("Generating folder archive");
                    folderArchiveSource.generateFolderArchive(this, study);
                    info("Successfully generated folder archive");
                    setStatus(PipelineJob.TaskStatus.complete);
                }
            }
        }
    }
}
