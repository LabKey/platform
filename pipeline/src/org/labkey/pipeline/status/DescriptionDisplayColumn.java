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
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.Map;

/**
 * DescriptionDisplayColumn class
 * <p/>
 * Created: Oct 25, 2005
 *
 * @author bmaclean
 */
public class DescriptionDisplayColumn extends SimpleDisplayColumn
{
    private URI uriRoot;

    public DescriptionDisplayColumn(URI uriRoot)
    {
        super();
        setName("Description");
        setCaption("Description");

        setWidth("500");
        this.uriRoot = uriRoot;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Map cols = ctx.getRow();
        String description = (String) cols.get("description");

        if (description == null || description.length() == 0)
        {
            String filePath = (String) cols.get("filePath");
            if (uriRoot != null && filePath != null && filePath.length() > 0)
            {
                filePath = filePath.replace('\\', '/');
                File fileRoot = new File(uriRoot);
                String filePathRoot = fileRoot.getAbsolutePath().replace('\\', '/');
                if (filePath.startsWith(filePathRoot))
                {
                    filePath = filePath.substring(filePathRoot.length());
                    if (filePath.charAt(0) != '/')
                        filePath = '/' + filePath;
                }
            }
            description = filePath;
        }

        out.write(PageFlowUtil.filter(description));
    }
}
