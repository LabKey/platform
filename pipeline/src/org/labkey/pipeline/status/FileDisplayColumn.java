/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

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

    @Override
    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        List<Path> files = null;

        Map<String, Object> cols = ctx.getRow();
        Integer rowIdI = (Integer) cols.get("RowId");
        String filePath = (String) cols.get("FilePath");
        String providerName = (String) cols.get("Provider");
        String containerId = (String) cols.get("Container");

        if (rowIdI != null && filePath != null && filePath.length() > 0)
        {
            PipelineProvider provider = PipelineService.get().getPipelineProvider(providerName);
            Container container = ContainerManager.getForId(containerId);
            Path path = FileUtil.stringToPath(container, filePath, false);
            files = listFiles(path, container, provider);
        }

        if (files == null || files.isEmpty())
        {
            out.write("&nbsp;");
        }
        else
        {
            for (final Path file : files)
            {
                // make sure the files can be open for read
                String fileName = file.getFileName().toString();
                if (Files.isReadable(file))
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
                else
                {
                    out.write(PageFlowUtil.filter(fileName));
                    out.write("<br>\n");
                }
            }
        }
    }

    public static List<Path> listFiles(Path p, Container c, @Nullable PipelineProvider provider)
    {
        Path parent = p.getParent();

        if (NetworkDrive.exists(parent))
        {
            // calculate base name of the .status file
            String statusName = p.getFileName().toString();

            // remove .status
            final String basename = statusName.substring(0, statusName.lastIndexOf('.'));

            // get files with .log, or same basename and .out
            try (Stream<Path> stream = Files.list(parent))
            {
                return stream
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            if (provider != null)
                                return provider.isStatusViewableFile(c, name, basename);

                            return StatusController.isVisibleFile(name, basename);
                        })
                        .sorted(Path::compareTo)
                        .collect(toList());
            }
            catch (IOException e)
            {
                // ignore, just return an empty file list
            }
        }

        return Collections.emptyList();
    }
}
