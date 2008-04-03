/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.pipeline.mule;

import org.apache.log4j.Logger;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ViewBackgroundInfo;
import org.mule.extras.client.MuleClient;
import org.mule.umo.UMOException;

/**
 * <code>EPipelineStatusWriter</code>
 *
 * @author brendanx
 */
public class EPipelineStatusWriter implements PipelineStatusFile.StatusWriter
{
    private static Logger _log = Logger.getLogger(EPipelineStatusWriter.class);

    public void setStatusFile(ViewBackgroundInfo info, PipelineJob job,
                              String status, String statusInfo) throws Exception
    {
        _log.info("STATUS = " + status);
    }

    public void setStatusFileJms(ViewBackgroundInfo info, PipelineStatusFile sf) throws Exception
    {
        try
        {
            MuleClient client = new MuleClient();
            client.dispatch("StatusSetter", new EPipelineStatus(info, sf), null);
        }
        catch (UMOException e)
        {
            // TODO: Throw something?
            _log.error(e);
        }
    }
}
