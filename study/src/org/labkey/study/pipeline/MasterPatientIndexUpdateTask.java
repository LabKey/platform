package org.labkey.study.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.study.MasterPatientIndexService;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;

public class MasterPatientIndexUpdateTask extends PipelineJob
{
    public static final String PIPELINE_PROVIDER = "MasterPatientIndexPipelineProvider";
    private MasterPatientIndexService _svc;

    // For serialization
    protected MasterPatientIndexUpdateTask() {}

    public MasterPatientIndexUpdateTask(ViewBackgroundInfo info, @NotNull PipeRoot root, MasterPatientIndexService service) throws IOException
    {
        super(PIPELINE_PROVIDER, info, root);

        _svc = service;
        File logFile = File.createTempFile("patientIndexUpdateJob", ".log", root.getRootPath());
        setLogFile(logFile);
    }

    @Override
    public boolean hasJacksonSerialization()
    {
        return true;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Master Patient Index Update job.";
    }

    public void run()
    {
        _svc.updateIndices(this);
    }
}
