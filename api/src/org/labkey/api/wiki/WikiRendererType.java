package org.labkey.api.wiki;

import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;

/**
 * User: Tamra Myers
 * Date: Aug 17, 2006
 * Time: 6:09:38 PM
 */
public enum WikiRendererType
{
    RADEOX
        {
            public String getDisplayName() {return "Wiki Page";}

            @Override
            public HttpView getSyntaxHelpView()
            {
                return new JspView("/org/labkey/wiki/view/wikiRadeoxHelp.jsp");
            }
        },
    HTML
        {
            public String getDisplayName() {return "HTML";}

            @Override
            public HttpView getSyntaxHelpView()
            {
                HttpView view = new JspView("/org/labkey/wiki/view/wikiHtmlHelp.jsp");
                view.addObject("useVisualEditor", false);
                return view;
            }
        },
    TEXT_WITH_LINKS
        {
            public String getDisplayName() {return "Plain Text";}
        };

    public abstract String getDisplayName();
    public HttpView getSyntaxHelpView()
    {
        return new HtmlView("");  // No syntax help by default
    }
}
