package org.labkey.api.data;

import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

/**
* User: adam
* Date: Sep 14, 2010
* Time: 1:09:32 PM
*/

// Wraps any DisplayColumn and causes it to render each value separately
class MultiValuedDisplayColumn extends DisplayColumnDecorator
{
    public MultiValuedDisplayColumn(DisplayColumn dc)
    {
        super(dc);
    }

    @Override         // TODO: Need similar for renderDetailsCellContents()
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
        _column.addQueryFieldKeys(fieldKeys);

        assert fieldKeys.contains(getColumnInfo().getFieldKey());

        MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, fieldKeys);
        String sep = "";

        while (mvCtx.next())
        {
            out.append(sep);
            super.renderGridCellContents(mvCtx, out);
            sep = ", ";
        }

        // TODO: Call super in empty values case?
    }

    @Override
    public String getURL()
    {
        return super.getURL();    //To change body of overridden methods use File | Settings | File Templates.
    }
}
