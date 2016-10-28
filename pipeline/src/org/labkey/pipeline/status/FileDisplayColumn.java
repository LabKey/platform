/*
 * Copyright (c) 2005-2016 LabKey Corporation
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

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;

import java.io.*;
import java.util.Map;
import java.util.Arrays;

/**
 * FileDisplayColumn class
 * <p/>
 * Created: Oct 25, 2005
 *
 * @author bmaclean
 */
public class FileDisplayColumn extends SimpleDisplayColumn
{
    public FileDisplayColumn()
    {
        super();
        setCaption("Files");
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String[] fileNames = null;
        File dir = null;

        Map cols = ctx.getRow();
        Integer rowIdI = (Integer) cols.get("RowId");
        String filePath = (String) cols.get("FilePath");
        String providerName = (String) cols.get("Provider");
        String containerId = (String) cols.get("Container");

        if (rowIdI != null && filePath != null && filePath.length() > 0)
        {
            File f = new File(filePath);
            dir = f.getParentFile();

            if (NetworkDrive.exists(dir))
            {
                // calculate base name of the .status file
                String statusName = f.getName();

                // remove .status
                final String basename = statusName.substring(0, statusName.lastIndexOf('.'));
                final PipelineProvider provider = PipelineService.get().getPipelineProvider(providerName);
                final Container container = ContainerManager.getForId(containerId);

                // get files with .log, or same basename and .out
                fileNames = dir.list(
                    new FilenameFilter()
                    {
                        public boolean accept(File dir, String name)
                        {
                            if (provider != null)
                                return provider.isStatusViewableFile(container, name, basename);

                            return StatusController.isVisibleFile(name, basename);
                        }
                    }
                );
            }
        }

        if (fileNames == null || fileNames.length == 0)
        {
            out.write("&nbsp;");
        }
        else
        {
            Arrays.sort(fileNames);

            for (final String fileName : fileNames)
            {
                // make sure the files can be open for read
                try (FileInputStream ignored = new FileInputStream(new File(dir, fileName)))
                {
                    out.write("<a href=\"");
                    out.write(StatusController.urlShowFile(ctx.getContainer(), rowIdI.intValue(), fileName, false).getLocalURIString());
                    out.write("\">");
                    out.write(PageFlowUtil.filter(fileName));
                    out.write("</a>\n");

                    out.write("&nbsp;&nbsp;");
                    out.write(PageFlowUtil.textLink("view", StatusController.urlShowFile(ctx.getContainer(), rowIdI.intValue(), fileName, false)));
                    out.write(PageFlowUtil.textLink("download", StatusController.urlShowFile(ctx.getContainer(), rowIdI.intValue(), fileName, true)));
                    out.write("<br>\n");
                }
                catch (IOException e)
                {
                    out.write(PageFlowUtil.filter(fileName));
                    out.write("<br>\n");
                }
            }
        }
    }
}
