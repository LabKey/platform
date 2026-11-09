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
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.experiment.controllers.exp.ExperimentController;

public class ExperimentRunDisplayColumn extends SimpleDisplayColumn
{
    private final ExpRun _run;

    public ExperimentRunDisplayColumn(ExpRun run)
    {
        this(run, "Experiment Run");
    }

    public ExperimentRunDisplayColumn(ExpRun run, String name)
    {
        _run = run;
        setCaption(name);
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        if (_run == null)
        {
            out.write("(Unknown)");
        }
        else
        {
            ActionURL url = ExperimentController.getRunGraphURL(ctx.getContainer(), _run.getRowId());

            out.write(LinkBuilder.simpleLink(_run.getName(), url));
        }
    }
}
