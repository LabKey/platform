package org.labkey.api.pipeline;

import java.util.List;
import java.util.ArrayList;

public class PipelineJobData
{
    private List<PipelineJob> _running;
    private List<PipelineJob> _pending;

    public PipelineJobData()
    {
        _running = new ArrayList<PipelineJob>();
        _pending = new ArrayList<PipelineJob>();
    }

    public List<PipelineJob> getRunningJobs()
    {
        return _running;
    }

    public void addRunningJob(PipelineJob job)
    {
        _running.add(job);
    }

    public List<PipelineJob> getPendingJobs()
    {
        return _pending;
    }

    public void addPendingJob(PipelineJob job)
    {
        _pending.add(job);
    }
}
