package org.labkey.study.assay.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public class TypeDisplayColumn extends DataColumn
{
    public TypeDisplayColumn(ColumnInfo colInfo)
    {
        super(colInfo);
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
        renderDetailsCellContents(ctx, out);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String lsid = (String)ctx.getRow().get(getColumnInfo().getAlias());
        if (lsid != null)
        {
            AssayProvider provider = AssayService.get().getProvider(ExperimentService.get().getExpProtocol(lsid));
            if (provider != null)
            {
                out.write(PageFlowUtil.filter(provider.getName()));
            }
        }

    }
}
