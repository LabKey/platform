/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.experiment.controllers.exp.ExperimentController;

public class ProtocolDisplayColumn extends SimpleDisplayColumn
{
    private final ExpProtocol _protocol;

    public ProtocolDisplayColumn(ExpProtocol protocol)
    {
        this(protocol, "Protocol");
    }

    public ProtocolDisplayColumn(ExpProtocol protocol, String name)
    {
        _protocol = protocol;
        setCaption(name);
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        if (_protocol == null)
        {
            out.write("(Unknown)");
        }
        else
        {
            ActionURL url = new ActionURL(ExperimentController.ProtocolDetailsAction.class, ctx.getContainer());
            url.addParameter("rowId", Long.toString(_protocol.getRowId()));

            out.write(LinkBuilder.simpleLink(_protocol.getName(), url));
        }
    }
}
