/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

/**
 * An implementation that renders an image in HTML, with a link, instead of a value from the results of the query.
 * User: jeckels
 * Date: Jan 11, 2008
 */
public class IconDisplayColumn extends DataColumn
{
    private int _height;
    private int _width;
    private String _imageTitle;
    @NotNull
    private final ActionURL _linkURL;
    private final String _parameterName;
    private final String _imageURL;

    public IconDisplayColumn(ColumnInfo col, int height, int width, @NotNull ActionURL linkURL, String parameterName, String imageURL)
    {
        super(col);
        _linkURL = linkURL;
        _parameterName = parameterName;
        _imageURL = imageURL;
        _imageTitle = getCaption();
        super.setCaption("");
        _height = height;
        _width = width;
        setWidth(Integer.toString(_width));
    }

    public void setCaption(String caption)
    {
        _imageTitle = caption;
    }

    public boolean isFilterable()
    {
        return false;
    }

    public boolean isSortable()
    {
        return false;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        ActionURL linkURL = _linkURL.clone();
        Object value = ctx.getRow().get(getColumnInfo().getAlias());
        if (value != null)
        {
            linkURL.addParameter(_parameterName, value.toString());
            out.write("<a href=\"" + linkURL.getLocalURIString() + "\" title=\"" + PageFlowUtil.filter(_imageTitle) + "\"><img src=\"" + _imageURL + "\" height=\"" + _height + "\" width=\"" + _width + "\"/></a>");
        }
    }
}
