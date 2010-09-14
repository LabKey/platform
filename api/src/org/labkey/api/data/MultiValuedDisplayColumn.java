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
public class MultiValuedDisplayColumn extends DisplayColumnDecorator
{
    private final Set<FieldKey> _fieldKeys = new HashSet<FieldKey>();

    public MultiValuedDisplayColumn(DisplayColumn dc)
    {
        super(dc);
        _column.addQueryFieldKeys(_fieldKeys);
        assert _fieldKeys.contains(getColumnInfo().getFieldKey());
    }

    @Override         // TODO: Need similar for renderDetailsCellContents()
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, _fieldKeys);
        String sep = "";

        while (mvCtx.next())
        {
            out.append(sep);
            super.renderGridCellContents(mvCtx, out);
            sep = ", ";
        }

        // TODO: Call super in empty values case?
    }
}
