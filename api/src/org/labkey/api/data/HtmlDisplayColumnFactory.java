package org.labkey.api.data;

import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;

/**
 * Created by matthew on 12/5/13.
 */
public class HtmlDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo col)
    {
        DataColumn dc = new HtmlDataColumn(col);
        dc.setHtmlFiltered(false);
        return dc;
    }

    static class HtmlDataColumn extends DataColumn
    {
        HtmlDataColumn(ColumnInfo col)
        {
            super(col,false);
            setHtmlFiltered(false);
        }

        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = ctx.get(getBoundColumn().getFieldKey());
            if (null == value)
                return "";
            String rawHtml = String.valueOf(value);
            ArrayList<String> errors = new ArrayList<>();
            String tidyHtml = PageFlowUtil.validateHtml(rawHtml, errors, false);
            if (errors.isEmpty())
                return tidyHtml;
            else
                return PageFlowUtil.filter(errors.get(0));
        }
    }
}
