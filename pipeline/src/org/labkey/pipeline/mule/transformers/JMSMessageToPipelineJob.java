/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
package org.labkey.pipeline.mule.transformers;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.mule.providers.jms.transformers.JMSMessageToObject;
import org.mule.transformers.AbstractEventAwareTransformer;
import org.mule.umo.UMOEventContext;
import org.mule.umo.transformer.TransformerException;

import javax.jms.TextMessage;

/**
 * <code>JMSMessageToPipelineJob</code> transforms a JMS message with PipelineJob
 * marshalled to XML as payload into the PipelineJob itself.
 */
public class JMSMessageToPipelineJob extends AbstractEventAwareTransformer
{
    private static final Logger LOG = Logger.getLogger(JMSMessageToPipelineJob.class);

    private JMSMessageToObject _transformerFromJMS;

    public JMSMessageToPipelineJob()
    {
        registerSourceType(TextMessage.class);
    }

    public Object transform(Object src, String encoding, UMOEventContext context) throws TransformerException
    {
        String xml = (String) getJMSTransformer().doTransform(src, encoding);
        PipelineJob result = PipelineJobService.get().getJobStore().fromXML(xml);
        LOG.debug("Transformed XML to job: " + result);
        return result;
    }

    protected JMSMessageToObject getJMSTransformer()
    {
        if (_transformerFromJMS == null)
        {
            _transformerFromJMS = new JMSMessageToObject();
            _transformerFromJMS.setEndpoint(getEndpoint());
        }

        return _transformerFromJMS;
    }
}
