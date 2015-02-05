/*
 * Copyright (c) 2008-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.pipeline.api;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.labkey.api.pipeline.NoSuchJobException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.impersonation.AbstractImpersonationContextFactory;
import org.labkey.pipeline.xstream.FileXStreamConverter;
import org.labkey.pipeline.xstream.TaskIdXStreamConverter;
import org.labkey.pipeline.xstream.URIXStreamConverter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <code>PipelineJobMarshaller</code> handles saving a <code>PipelineJob</code> to XML,
 * and restoring it from XML.
 *
 * todo: probably want to have 2 different interfaces here, rather than implementing
 *          JobStore, an throwing UnsupportedOperationException on most of its
 *          methods.
 */
public class PipelineJobMarshaller implements PipelineStatusFile.JobStore
{
    private final AtomicReference<XStream> _xstream = new AtomicReference<>();

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
                // Don't need to remember HTTP session attributes in serialized jobs. They can be quite large.
                // We do want to make sure that we keep tracking other impersonation details for auditing, etc
                // This is set based on the declaring class for the field - see http://xstream.codehaus.org/javadoc/com/thoughtworks/xstream/XStream.html#omitField(java.lang.Class, java.lang.String)
                instance.omitField(AbstractImpersonationContextFactory.class, "_adminSessionAttributes");
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

    public String toXML(PipelineJob job)
    {
        return getXStream().toXML(job);
    }

    public PipelineJob fromXML(String xml)
    {
        return (PipelineJob) getXStream().fromXML(xml);
    }

    /* CONSIDER: create a separate interface? */
    public void storeJob(PipelineJob job) throws NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    public PipelineJob getJob(String jobId)
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    public PipelineJob getJob(int rowId)
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    public void retry(String jobId) throws IOException, NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    public void retry(PipelineStatusFile sf) throws IOException, NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");        
    }

    public void split(PipelineJob job) throws IOException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    public void join(PipelineJob job) throws IOException, NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }
}
