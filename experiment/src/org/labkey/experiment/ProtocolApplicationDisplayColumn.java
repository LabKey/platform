/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
package org.labkey.experiment;

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Oct 19, 2005
 */
public class ProtocolApplicationDisplayColumn extends SimpleDisplayColumn
{
    private ExpProtocolApplication _protocolApplication;

    public ProtocolApplicationDisplayColumn(ExpProtocolApplication protocolApplication)
    {
        this(protocolApplication, "Protocol Application");
    }

    public ProtocolApplicationDisplayColumn(ExpProtocolApplication protocolApplication, String name)
    {
        _protocolApplication = protocolApplication;
        setCaption(name);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_protocolApplication == null)
        {
            out.write("(Unknown)");
        }
        else
        {
            ActionURL url = ExperimentController.getShowApplicationURL(ctx.getContainer(), _protocolApplication.getRowId());
            out.write("<a href=\"" + url.getEncodedLocalURIString() + "\">" + _protocolApplication.getName() + "</a>");
        }
    }
}
