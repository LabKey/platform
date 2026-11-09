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
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.experiment.controllers.exp.ExperimentController;

public class LineageGraphDisplayColumn extends SimpleDisplayColumn
{
    private final Long _runId;
    private final String _focus;
    private final String _linkText;

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
        _focus = typeCode + object.getRowId();
        _linkText = "Lineage for " + object.getName();
        _runId = run == null ? null : run.getRowId();

        setCaption("Lineage Graph");
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        if (_runId == null || _focus == null)
        {
            out.write("(Unknown)");
        }
        else
        {
            ActionURL url = new ActionURL(ExperimentController.ShowRunGraphDetailAction.class, ctx.getContainer());
            url.addParameter("rowId", Long.toString(_runId));
            url.addParameter("detail", "true");
            url.addParameter("focus", _focus);

            out.write(LinkBuilder.simpleLink(_linkText, url));
        }
    }
}
