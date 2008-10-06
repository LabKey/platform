/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.pipeline.status;

import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.util.PageFlowUtil;
import static org.labkey.pipeline.api.PipelineStatusManager.*;
import org.labkey.pipeline.api.PipelineStatusFileImpl;


import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Arrays;
import java.util.Comparator;
import java.sql.SQLException;

/**
 * SplitDisplayColumn class
 * <p/>
 * Created: Oct 25, 2005
 *
 * @author bmaclean
 */
public class JobDisplayColumn extends SimpleDisplayColumn
{
    private boolean _split;
    private PipelineStatusFile[] _jobStatus;

    public JobDisplayColumn(boolean split)
    {
        super();
        _split = split;
        if (_split)
            setCaption("Split jobs");
        else
            setCaption("Join job");
    }

    public boolean getVisible(Map ctx)
    {
        return getJobStatus(ctx).length > 0;
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_jobStatus == null || _jobStatus.length == 0)
            out.write("&nbsp;");
        else
        {
            Arrays.sort(_jobStatus, new Comparator<PipelineStatusFile>() {
                public int compare(PipelineStatusFile sf1, PipelineStatusFile sf2)
                {
                    return sf1.getDescription().compareToIgnoreCase(sf2.getDescription());
                }
            });
            int rowIndex = 0;
            out.write("<table class=\"labkey-data-region labkey-show-borders\">\n" +
                    "<colgroup><col width=\"100\"/><col width=\"400\"/></colgroup>\n" +
                    "<thead><tr>\n" +
                    "    <th class=\"labkey-col-header-filter\" align=\"left\"><div>Status</div></th>\n" +
                    "    <th class=\"labkey-col-header-filter\" align=\"left\"><div>Description</div></th>\n" +
                    "</tr></thead>");
            for (PipelineStatusFile sf : _jobStatus)
            {
                if (rowIndex++ % 2 == 0)
                    out.write("<tr class=\"labkey-alternate-row\">");
                else
                    out.write("<tr class=\"labkey-row\">");

                out.write("<td nowrap><a href=\"");
                out.write(StatusController.urlDetails(ctx.getContainer(), sf.getRowId()).getLocalURIString());
                out.write("\">");
                out.write(PageFlowUtil.filter(sf.getStatus()));
                out.write("</a></td>");
                out.write("<td>");
                out.write(PageFlowUtil.filter(sf.getDescription()));
                out.write("</td>");
                out.write("</tr>\n");
            }
            out.write("</table>\n");
        }
    }

    public PipelineStatusFile[] getJobStatus(Map ctx)
    {
        if (_jobStatus == null)
        {
            try
            {
                if (_split)
                    _jobStatus = getSplitStatusFiles((String) ctx.get("Job"));
                else if (ctx.get("JobParent") != null)
                {
                    PipelineStatusFileImpl parent = getJobStatusFile((String) ctx.get("JobParent"));
                    if (parent != null)
                    {
                        _jobStatus = new PipelineStatusFile[] {parent};
                    }
                }
            }
            catch (SQLException e)
            {
            }
            if (_jobStatus == null)
                _jobStatus = new PipelineStatusFile[0];
        }

        return _jobStatus;
    }
}