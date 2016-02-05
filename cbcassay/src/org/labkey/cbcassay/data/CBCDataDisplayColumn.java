/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.cbcassay.data;

import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnDecorator;
import org.labkey.api.data.RenderContext;

import java.io.Writer;
import java.io.IOException;

/**
 * User: kevink
 * Date: Nov 21, 2008 12:49:37 PM
 */
public class CBCDataDisplayColumn extends DisplayColumnDecorator
{
    private CBCDataProperty _dataprop;

    public CBCDataDisplayColumn(DisplayColumn column, CBCDataProperty dataprop)
    {
        super(column);
        _dataprop = dataprop;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);
        boolean inRange = true;
        if (o instanceof Double)
            inRange = _dataprop.inRange((Double)o);

        if (!inRange)
            out.write("<div style='font-style:italic;' class='labkey-form-label'>");
        super.renderGridCellContents(ctx, out);
        if (!inRange)
            out.write("</div>");
    }
}
