package org.labkey.api.view.menu;

import org.labkey.api.view.JspView;
import org.labkey.api.view.template.PageConfig;

/**
 * User: Nick
 * Date: 5/6/13
 * Time: 12:52 PM
 */
public class HeaderMenu extends JspView<PageConfig>
{
    public HeaderMenu(PageConfig page)
    {
        super(HeaderMenu.class, "headerMenu.jsp", page);
    }
}
