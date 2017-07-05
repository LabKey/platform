package org.labkey.api.util.element;

import org.labkey.api.util.PageFlowUtil;

public class DisplayField extends Input
{
    private DisplayField(DisplayFieldBuilder builder)
    {
        super(builder);
    }

    @Override
    protected void doInput(StringBuilder sb)
    {
        sb.append("<p class=\"form-control-static\">");
        doValue(sb);
        sb.append("</p>");
    }

    @Override
    protected void doValue(StringBuilder sb)
    {
        if (getValue() != null && !"".equals(getValue()))
        {
            sb.append(PageFlowUtil.filter(getValue()));
        }
    }

    public static class DisplayFieldBuilder extends InputBuilder<DisplayFieldBuilder>
    {
        public DisplayField build()
        {
            return new DisplayField(this);
        }
    }
}
