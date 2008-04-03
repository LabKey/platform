package org.labkey.api.data;

import org.apache.commons.lang.BooleanUtils;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Nov 15, 2007
 */
public class MenuButton extends ActionButton
{
    private class MenuItem
    {
        private boolean _separator;
        private boolean _checked;
        private String _caption;
        private String _url;
        private String _script;

        /** Inserts a separator line */
        private MenuItem()
        {
            _separator = true;
        }

        private MenuItem(String caption, String url, String script, boolean checked)
        {
            _caption = caption;
            _url = url;
            _script = script;
            _checked = checked;
        }

        private MenuItem(String caption, String url)
        {
            this(caption, url, null, false);
        }

        public String getCaption()
        {
            return _caption;
        }

        public String getUrl()
        {
            return _url;
        }

        public String getScript()
        {
            return _script;
        }

        public boolean isSeparator()
        {
            return _separator;
        }

        public boolean isChecked()
        {
            return _checked;
        }
    }

    private List<MenuItem> _menuItems = new ArrayList<MenuItem>();

    public MenuButton(String caption)
    {
        super("MenuButton", caption, DataRegion.MODE_GRID, ActionButton.Action.LINK);
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        String menuElementId = getCaption() + "Menu" + System.identityHashCode(this);
        out.write("<a href='javascript:void(0)' onclick=\"showMenu(this, '" + menuElementId + "');\">");
        out.write("<img border=0 src='" + PageFlowUtil.buttonSrc(getCaption(ctx), "shadedMenu") + "'>");
        out.write("</a>");

        if (!BooleanUtils.toBoolean((String)ctx.get(getCaption() + "MenuRendered")))
        {
            out.write("<script type=\"text/javascript\">LABKEY.requiresButtonBarMenu();</script>");
            ctx.put(getCaption() + "MenuRendered", "true");

            out.write("<div style=\"display:none\" id=\"" + menuElementId + "\" class=\"yuimenu\">");
            out.write("<div class=\"bd\">");
            out.write("<ul class=\"first-of-type\">");

            boolean hasCheck = false;
            for (MenuItem menuItem : _menuItems)
            {
                if (menuItem.isChecked())
                {
                    hasCheck = true;
                    break;
                }
            }

            for (MenuItem menuItem : _menuItems)
            {
                if (menuItem.isSeparator())
                {
                    out.write("</ul><ul>");
                }
                else
                {
                    out.write("<li class=\"yuimenuitem\">");
                    // XXX: use yui checkmarks
                    if (hasCheck)
                    {
                        out.write(menuItem.isChecked() ? "\u2714&nbsp;" : "&nbsp;&nbsp;&nbsp;");
                    }
                    out.write("<a href='" + menuItem.getUrl() + "'");
                    if (menuItem.getScript() != null)
                        out.write(" onClick='" + menuItem.getScript() + "'");
                    out.write(">" + PageFlowUtil.filter(menuItem.getCaption()) + "</a></li>");
                }
            }
            out.write("</ul></div></div>");
        }
    }

    public void addSeparator()
    {
        _menuItems.add(new MenuItem());
    }

    public void addMenuItem(String caption, String url)
    {
        _menuItems.add(new MenuItem(caption, url));
    }

    public void addMenuItem(String caption, String url, String onClickScript)
    {
        _menuItems.add(new MenuItem(caption, url, onClickScript, false));
    }

    public void addMenuItem(String caption, String url, String onClickScript, boolean checked)
    {
        _menuItems.add(new MenuItem(caption, url, onClickScript, checked));
    }
}
