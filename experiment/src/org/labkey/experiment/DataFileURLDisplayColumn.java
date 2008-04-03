/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.MimeMap;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.view.ActionURL;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URI;

/**
 * User: jeckels
 * Date: Sep 29, 2005
 */
public class DataFileURLDisplayColumn extends SimpleDisplayColumn
{
    private ExpData _data;
    private final String _relativePipelinePath;

    public DataFileURLDisplayColumn(ExpData data, String relativePipelinePath)
    {
        super();
        setCaption("Data File");
        _data = data;
        _relativePipelinePath = relativePipelinePath;
    }



    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String dataFileURL = _data.getDataFileUrl();
        if (dataFileURL == null || dataFileURL.trim().length() == 0)
        {
            out.write("(Unknown)<br>\n");
            return;
        }
        try
        {
            File dataFile = new File(new URI(dataFileURL));
            String viewURL = null;
            ExperimentDataHandler handler = _data.findDataHandler();

            URLHelper urlHelper = handler.getContentURL(ctx.getContainer(), _data);
            if (urlHelper != null)
            {
                viewURL = urlHelper.toString();
            }

            String downloadURL = "showFile.view?rowId=" + _data.getRowId();

            if (_data.isFileOnDisk())
            {
                MimeMap mimeMap = new MimeMap();
                if (mimeMap.isInlineImageFor(dataFile.getName()))
                {
                    out.write(dataFileURL + " (See image below)"); // The image will be rendered on the page itself later
                }
                else
                {
                    out.write(dataFileURL + " [<a href=\"" + downloadURL + "\">download</a>]\n");
                }
                if (viewURL != null)
                {
                    out.write(" [<a href=\"" + viewURL + "\">view</a>]");
                }
                if (_relativePipelinePath != null)
                {
                    ActionURL url = ctx.getViewContext().cloneActionURL();
                    url.setPageFlow("Pipeline");
                    url.setAction("browse.view");
                    url.deleteParameters();
                    url.addParameter("path", _relativePipelinePath);
                    out.write(" [<a href=\"" + url + "\">browse in pipeline</a>]");
                }
                out.write("<br>");
            }
            else
            {
                out.write(dataFileURL + " (Not available on disk)\n");
            }
        }
        catch (URISyntaxException e)
        {
            out.write(dataFileURL + " (Not available on disk)\n");
        }
    }
}
