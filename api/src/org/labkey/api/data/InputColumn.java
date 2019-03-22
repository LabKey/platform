/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.data;

import java.io.IOException;
import java.io.Writer;

/**
 * Renders a bound ColumnInfo as an HTML form input in a grid view.
 *
 * User: kevink
 * Date: 10/21/12
 */
public class InputColumn extends DataColumn
{
    public InputColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderInputHtml(ctx, out, getInputValue(ctx));
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        renderInputHtml(ctx, out, getInputValue(ctx));
    }
}
