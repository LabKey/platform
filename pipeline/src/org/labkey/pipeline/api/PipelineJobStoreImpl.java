package org.labkey.pipeline.api;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.XppDriver;

import java.util.concurrent.atomic.AtomicReference;
import java.sql.SQLException;

import org.labkey.pipeline.xstream.TaskIdXStreamConverter;
import org.labkey.pipeline.xstream.FileXStreamConverter;
import org.labkey.pipeline.xstream.URIXStreamConverter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.view.ViewBackgroundInfo;

/**
 * Implements serialization of a <code>PipelineJob</code> to and from XML,
 * and storage and retrieval from the <code>PipelineJobStatusManager</code>. 
 */
public class PipelineJobStoreImpl implements PipelineStatusFile.JobStore
{
    private final AtomicReference<XStream> _xstream = new AtomicReference<XStream>();

    public final XStream getXStream()
    {
        XStream instance = _xstream.get();

        if (instance == null)
        {
            try
            {
                instance = new XStream(new XppDriver());
                instance.registerConverter(new TaskIdXStreamConverter());
                instance.registerConverter(new FileXStreamConverter());
                instance.registerConverter(new URIXStreamConverter());
                if (!_xstream.compareAndSet(null, instance))
                    instance = _xstream.get();
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Failed to initialize XStream for pipeline jobs.", e);
            }
        }

        return instance;
    }

    public PipelineJob getJob(String jobId) throws SQLException
    {
        return fromXML(PipelineStatusManager.retreiveJob(jobId));
    }

    public void storeJob(ViewBackgroundInfo info, PipelineJob job) throws SQLException
    {
        PipelineStatusManager.storeJob(job.getJobGUID(), toXML(job));
    }

    public String toXML(PipelineJob job)
    {
        return getXStream().toXML(job);
    }

    public PipelineJob fromXML(String xml)
    {
        return (PipelineJob) getXStream().fromXML(xml);
    }
}
