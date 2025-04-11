package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

public class DisplayFieldBuilder extends InputBuilder<DisplayFieldBuilder>
{
    @Override
    public DisplayField build()
    {
        return new DisplayField(this);
    }

    public static class DisplayField extends Input
    {
        private DisplayField(DisplayFieldBuilder builder)
        {
            super(builder);
        }

        @Override
        protected void doInput(Appendable sb) throws IOException
        {
            sb.append("<p class=\"form-control-static\">");
            if (!HtmlString.isEmpty(getValue()))
                sb.append(h(getValue()));
            sb.append("</p>");
        }

        @Override
        protected void doLabel(Appendable sb) throws IOException
        {
            boolean needsLayoutWrapping = Layout.HORIZONTAL.equals(getLayout()) && needsWrapping();

            sb.append("<span");

            String cls = "";
            if (StringUtils.isNotEmpty(getLabelClassName()))
                cls += " " + getLabelClassName();
            if (needsLayoutWrapping)
                cls += " col-sm-3 col-lg-2";

            if (StringUtils.isNotEmpty(cls))
                sb.append(" class=\"").append(h(cls)).append("\"");

            sb.append(">");

            if (getLabel() != null)
                sb.append(h(getLabel())).append(":");

            if (Layout.INLINE.equals(getLayout()) && !HtmlString.isEmpty(getContextContent()))
                super.doContextField(sb);

            sb.append("</span> ");
        }
    }
}
