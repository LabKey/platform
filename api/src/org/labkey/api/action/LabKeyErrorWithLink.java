package org.labkey.api.action;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.view.ViewContext;

public class LabKeyErrorWithLink extends LabKeyError
{
    private final String _adviceText;
    private final @Nullable String _adviceHref;

    public LabKeyErrorWithLink(String message, String adviceText, @Nullable String adviceHref)
    {
        super(message);
        _adviceText = adviceText;
        _adviceHref = adviceHref;
    }

    @Override
    public HtmlString renderToHTML(ViewContext context)
    {
        HtmlStringBuilder builder = HtmlStringBuilder.of(super.renderToHTML(context));

        String adviceHref = getAdviceHref();

        if (adviceHref != null)
        {
            builder.append(" ");
            builder.append(new LinkBuilder(getAdviceText()).href(getAdviceHref()).clearClasses());
        }

        return builder.getHtmlString();
    }

    public String getAdviceText()
    {
        return _adviceText;
    }

    public @Nullable String getAdviceHref()
    {
        return _adviceHref;
    }
}
