/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.api.*;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Oct 19, 2005
 */
public class LineageGraphDisplayColumn extends SimpleDisplayColumn
{
    private Integer _runId;
    private String _focus;
    private String _linkText;

    public LineageGraphDisplayColumn(ExpMaterial material, ExpRun run)
    {
        this(DotGraph.TYPECODE_MATERIAL, material, run);
    }

    public LineageGraphDisplayColumn(ExpData data, ExpRun run)
    {
        this(DotGraph.TYPECODE_DATA, data, run);
    }

    public LineageGraphDisplayColumn(ExpProtocolApplication app, ExpRun run)
    {
        this(DotGraph.TYPECODE_PROT_APP, app, run);
    }

    private LineageGraphDisplayColumn(String typeCode, ExpObject object, ExpRun run)
    {
        _linkText = "Lineage for ";
        _focus = typeCode + object.getRowId();
        _linkText += object.getName();
        _runId = run == null ? null : run.getRowId();

        setCaption("Lineage Graph");
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_runId == null || _focus == null)
        {
            out.write("(Unknown)");
        }
        else
        {
            ActionURL url = new ActionURL(ExperimentController.ShowRunGraphDetailAction.class, ctx.getContainer());
            url.addParameter("rowId", Integer.toString(_runId));
            url.addParameter("detail", "true");
            url.addParameter("focus", _focus);
            out.write("<a href=\"" + url.toString() + "\">" + _linkText + "</a>");
        }
    }
}
