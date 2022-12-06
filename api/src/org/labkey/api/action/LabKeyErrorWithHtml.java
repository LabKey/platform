package org.labkey.api.action;

import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.view.ViewContext;

public class LabKeyErrorWithHtml extends LabKeyError
{
    private final HtmlString _html;

    public LabKeyErrorWithHtml(String message, HtmlString html)
    {
        super(message);
        _html = html;
    }

    @Override
    public HtmlString renderToHTML(ViewContext context)
    {
        HtmlStringBuilder builder = HtmlStringBuilder.of(super.renderToHTML(context));
        builder.append(_html);

        return builder.getHtmlString();
    }

    public HtmlString getHtml()
    {
        return _html;
    }
}
