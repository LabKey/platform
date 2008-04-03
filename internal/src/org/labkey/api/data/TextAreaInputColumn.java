package org.labkey.api.data;

import org.labkey.api.util.PageFlowUtil;

import java.io.Writer;
import java.io.IOException;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Dec 5, 2005
 * Time: 5:03:15 PM
 */
public class TextAreaInputColumn extends SimpleInputColumn<String>
{
    public TextAreaInputColumn(String name, String value)
    {
        super(name, value, String.class);
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        out.write("<textarea name=\"");
        out.write(name);
        out.write("\" cols=\"150\" rows=\"5\" style=\"width:100%;\">");
        if (null != value)
            out.write(PageFlowUtil.filter(value.toString()));
        out.write("</textarea>");
    }
}
