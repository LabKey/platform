/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jgarms
 * Date: Dec 19, 2008
 */
public class RunDataLinkDisplayColumn extends SimpleDisplayColumn
{
    private final ExpProtocol protocol;
    private final ColumnInfo runIdCol;

    public RunDataLinkDisplayColumn(ExpProtocol protocol, ColumnInfo runIdCol)
    {
        this.protocol = protocol;
        this.runIdCol = runIdCol;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        int runId = ((Integer)runIdCol.getValue(ctx)).intValue();
        ExpRun run = ExperimentService.get().getExpRun(runId);
        ActionURL runURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(run.getContainer(), protocol, runId);
        out.write("<a href=\"");
        out.write(runURL.getLocalURIString());
        out.write("\" onclick=\"return confirm('Are you sure you wish to leave this page?\\n\\nAny changes you have made will be lost.');\">View Run</a>");
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        out.write("Originating Run");
    }
}
