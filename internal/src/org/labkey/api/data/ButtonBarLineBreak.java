package org.labkey.api.data;

import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Nov 7, 2006
 */
public class ButtonBarLineBreak extends DisplayElement
{
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write("<br>");
    }
}
