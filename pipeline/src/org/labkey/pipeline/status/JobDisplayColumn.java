/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
import org.labkey.pipeline.api.PipelineStatusFileImpl;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.labkey.pipeline.api.PipelineStatusManager.getJobStatusFile;
import static org.labkey.pipeline.api.PipelineStatusManager.getSplitStatusFiles;

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
    private List<? extends PipelineStatusFile> _jobStatus;

    public JobDisplayColumn(boolean split)
    {
        super();
        _split = split;
        if (_split)
            setCaption("Split jobs");
        else
            setCaption("Join job");
    }

    public boolean isVisible(RenderContext ctx)
    {
        return !getJobStatus(ctx).isEmpty();
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_jobStatus == null || _jobStatus.isEmpty())
            out.write("&nbsp;");
        else
        {
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

    public List<? extends PipelineStatusFile> getJobStatus(RenderContext ctx)
    {
        if (_jobStatus == null)
        {
            if (_split)
            {
                String jobId = (String) ctx.get("Job");
                if (jobId != null)
                {
                    // If we're being rendered from the Admin Console, we won't be in the right container,
                    // so don't specify one
                    _jobStatus = getSplitStatusFiles(jobId);
                }
            }
            else if (ctx.get("JobParent") != null)
            {
                PipelineStatusFileImpl parent = getJobStatusFile((String) ctx.get("JobParent"));
                if (parent != null)
                {
                    _jobStatus = Collections.singletonList(parent);
                }
            }
            if (_jobStatus == null)
                _jobStatus = Collections.emptyList();
            // Make a copy of the immutable list so we can sort as needed
            _jobStatus = new ArrayList<>(_jobStatus);

            _jobStatus.sort(Comparator.comparing(PipelineStatusFile::getDescription, String.CASE_INSENSITIVE_ORDER));
        }

        return _jobStatus;
    }
}
