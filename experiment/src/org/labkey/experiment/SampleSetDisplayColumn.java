/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Oct 4, 2007
 */
public class SampleSetDisplayColumn extends SimpleDisplayColumn
{
    private final ExpMaterial _material;

    public SampleSetDisplayColumn(ExpMaterial material)
    {
        _material = material;
        setCaption("Sample Set");
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        ExpSampleSet ss = _material.getSampleSet();

        if (ss == null)
        {
            out.write("Not a member of a sample set");
        }
        else
        {
            ActionURL url = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, ss.getContainer());
            url.addParameter("rowId", Integer.toString(ss.getRowId()));
            out.write("<a href=\"" + url.toString() + "\">" + PageFlowUtil.filter(ss.getName()) + "</a>");
        }
    }
}
