/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Sep 29, 2005
 */
public class DataFileURLDisplayColumn extends SimpleDisplayColumn
{
    private ExpData _data;

    public DataFileURLDisplayColumn(ExpData data)
    {
        super();
        setCaption("Data File");
        _data = data;
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String dataFileURL = _data.getDataFileUrl();
        if (dataFileURL == null || dataFileURL.trim().length() == 0)
        {
            out.write("(Unknown)<br>\n");
            return;
        }

        ActionURL contentURL = _data.findDataHandler().getContentURL(_data);
        if (contentURL == null && _data.isFileOnDisk())
        {
            contentURL = ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(_data, false);
        }

        if (contentURL != null)
        {
            out.write("<a href=\"");
            out.write(contentURL.toString());
            out.write("\">");
        }
        out.write(PageFlowUtil.filter(dataFileURL));
        out.write("</a>");

        if (!_data.isFileOnDisk())
        {
            out.write(" (Not available on disk)\n");
        }
    }
}
