package org.labkey.api.data;

import java.io.IOException;
import java.io.Writer;

/**
 * User: kevink
 * Date: 10/21/12
 *
 * Renders a bound ColumnInfo as a form input in a grid view.
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
