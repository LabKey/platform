/*
 * $Id: FilteringListMessageSplitter.java 7976 2007-08-21 14:26:13Z dirk.olmes $
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc. All rights reserved. http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.labkey.pipeline.mule.routing;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.mule.EPipelineQueueImpl;
import org.mule.impl.MuleMessage;
import org.mule.routing.outbound.FilteringListMessageSplitter;
import org.mule.umo.UMOMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * <code>PipelineJobMessageSplitter</code> accepts a PipelineJob as a message payload
 * then routes list elements as messages over an endpoint where the endpoint's filter
 * accepts the payload.
 */
public class EPipelineQueueMessageSplitter extends FilteringListMessageSplitter
{
    @Override
    protected void initialise(UMOMessage message)
    {
        List<PipelineJob> outboundJobs = EPipelineQueueImpl.getOutboundJobs();
        EPipelineQueueImpl.resetOutboundJobs();
        if (outboundJobs == null)
        {
            outboundJobs = new ArrayList<>();
            outboundJobs.add((PipelineJob) message.getPayload());
        }
        super.initialise(new MuleMessage(outboundJobs, message));
    }
}