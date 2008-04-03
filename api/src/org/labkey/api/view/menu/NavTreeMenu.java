package org.labkey.api.view.menu;

import org.labkey.api.data.Container;
import static org.labkey.api.util.PageFlowUtil.filter;
import org.labkey.api.view.*;

import java.io.PrintWriter;
import java.net.URISyntaxException;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 10:06:16 AM
 */
public class NavTreeMenu extends WebPartView implements Collapsible
{
    private NavTree[] _elements;
    private String _collapseId;
    private boolean _highlightSelection = true;

    public NavTreeMenu(ViewContext context, String collapseId, String title, ActionURL titleHref, boolean collapseByDefault, NavTree... elements)
    {
        super(title);
        setFrame(FrameType.LEFT_NAVIGATION);
        _collapseId = collapseId;
        if (titleHref != null)
            setTitleHref(titleHref.getLocalURIString());
        _elements = elements;
        // assume we're collapsed:
        setCollapsed(collapseByDefault);
        NavTreeManager.applyExpandState(this, context);
    }

    public NavTreeMenu(ViewContext context,String rootId, String title, boolean collapseByDefault, NavTree... elements)
    {
        this(context, rootId, title, null, collapseByDefault, elements);
    }

    public NavTreeMenu(ViewContext context, String rootId, NavTree... elements)
    {
        this(context, rootId, null, null, false, elements);
    }

    public boolean isCollapsed()
    {
        Object collapsed = getViewContext().get("collapsed");
        return (collapsed instanceof Boolean) && ((Boolean) collapsed).booleanValue();
    }

    public void setCollapsed(boolean collapsed)
    {
        enableExpandCollapse(_collapseId, collapsed);
    }
    
    public void setHighlightSelection(boolean highlightSelection)
    {
        _highlightSelection = highlightSelection;
    }

    protected void setId(String id)
    {
        _collapseId = id;
    }
    
    protected void setElements(ViewContext context, NavTree... elements)
    {
        _elements = elements;
        NavTreeManager.applyExpandState(this, context);
    }

    public NavTree[] getChildren()
    {
        return null;
    }

    public NavTree[] getElements()
    {
        return _elements;
    }

    public Collapsible findSubtree(String path)
    {
        if (path == null)
            return this;
        else
            return null;
    }

    public String getId()
    {
        return _collapseId;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        if (_elements != null)
        {
            boolean indentForExpansionGifs = false;
            for (NavTree element : _elements)
            {
                if (element.hasChildren())
                    indentForExpansionGifs = true;
            }
            out.print("<table cellSpacing=2 cellpadding=0 width=\"100%\">");
            for (NavTree element : _elements)
                renderLinks(element, 0, "", element.getId(), getViewContext(), out, indentForExpansionGifs);
            out.print("</table>");
        }
    }

    private void renderLinks(NavTree nav, int level, String pathToHere, String rootId,
                             ViewContext context, PrintWriter out, boolean indentForExpansionGifs) throws URISyntaxException
    {
        Container c = context.getContainer();
        ActionURL helper = new ActionURL("Project", "start.view", c);
        String pattern = helper.getLocalURIString();
        ActionURL currentUrl = context.getActionURL();

        String link = nav.getValue() == null ? null : nav.getValue();
        boolean selected = _highlightSelection && null != link && matchPath(link, currentUrl, pattern);
        if (level == 0 && null != nav.getKey())
            level = 1;

        boolean hasChildren = nav.getChildren() != null && nav.getChildren().length > 0;
        if (null != nav.getKey())
            //When we post the expanded path, we need to use the escaped key so that embedded
            // '/' characters in the key are not confused with path separators
            pathToHere = pathToHere + "/" + nav.getEscapedKey();

        boolean collapsed = nav.isCollapsed();

        if (level > 0)
        {
            out.printf("<tr class=\"%s\"><td style=\"padding-top: 0.5em;text-align:right\">\n", nav.getCanCollapse() ? "header" : "");

            if (hasChildren)
            {
                ActionURL expandCollapseUrl = new ActionURL("Project", "expandCollapse", getViewContext().getContainer());
                expandCollapseUrl.replaceParameter("path", pathToHere);
                expandCollapseUrl.replaceParameter("treeId", rootId);

                String image = collapsed ? "plus.gif" : "minus.gif";
                out.printf("<a href=\"%s\" onclick=\"return toggleLink(this, %s);\">",
                        filter(expandCollapseUrl.getLocalURIString()),
                        "true");

                out.printf("<img border=\"0\" src=\"%s/_images/%s\"></a>", context.getContextPath(), image);
            }
            else if (indentForExpansionGifs)
                out.printf("<img border=\"0\" width=\"9\" src=\"%s/_.gif\">", context.getContextPath());

            out.printf("</td><td style=\"padding: 3px; width: 100%%\">\n");

            if (null == link)
                out.print(filter(nav.getKey()));
            else
            {
                out.printf("<a href=\"%s\" style=\"%s\">", filter(link), selected ? "font-weight:bold;font-style:italic" : "");
                out.print(filter(nav.getKey()));
                out.print("</a>");
                if (selected)
                    out.printf("&nbsp;<img border=0 src=\"%s/_images/square.gif\">", context.getContextPath());

            }

            out.print("</td></tr>");
        }

        //Render children as nested table in a row...
        if (hasChildren)
        {
            for (NavTree element : nav.getChildren())
            {
                if (element.hasChildren())
                    indentForExpansionGifs = true;
            }
            out.printf("<tr %s>\n<td></td><td>\n<table cellspacing=\"0\" cellpadding=\"0\" width=\"100%%\">", collapsed ? "style=display:none" : "");
            for (NavTree child : nav.getChildren())
                renderLinks(child, level + 1, pathToHere, rootId, context, out, indentForExpansionGifs);
            out.println("</table>\n</td></tr>");
        }
    }

    protected boolean matchPath(String link, ActionURL currentUrl, String pattern)
    {
        if (link.endsWith(pattern))
        {
            ActionURL urlLink;
            urlLink = new ActionURL(link);
            return currentUrl.getExtraPath().equals(urlLink.getExtraPath());
        }
        return false;
    }
}
