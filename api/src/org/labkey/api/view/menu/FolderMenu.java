package org.labkey.api.view.menu;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.io.PrintWriter;
import java.net.URISyntaxException;

import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * User: Nick
 * Date: 4/10/13
 */
public class FolderMenu extends NavTreeMenu
{
    public FolderMenu(ViewContext context)
    {
        super(context, "folder-nav-menu", null, false, getNavTree(context));
        setFrame(FrameType.NONE);
    }

    @Nullable
    private static NavTree getNavTree(ViewContext context)
    {
        if (context.getContainer().getProject() != null)
        {
            return ContainerManager.getFolderListForUser(context.getContainer().getProject(), context);
        }

        return null;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        NavTree[] _elements = getElements();
        if (_elements != null)
        {
            boolean indentForExpansionGifs = false;
            for (NavTree element : _elements)
            {
                if (element.hasChildren())
                    indentForExpansionGifs = true;
            }
            out.print("<div class=\"folder-nav\"><ul class=\"folder-nav-top\">");
            for (NavTree element : _elements)
                renderLinks(element, 0, "", element.getId(), getViewContext(), out, indentForExpansionGifs);
            out.print("</ul></div>");
        }
    }

    private void renderLinks(NavTree nav, int level, String pathToHere, String rootId,
                             ViewContext context, PrintWriter out, boolean indentForExpansionGifs) throws URISyntaxException
    {
        Container c = context.getContainer();
        ActionURL currentUrl = context.getActionURL();
        if (c.isWorkbookOrTab())
        {
            currentUrl = currentUrl.clone();
            currentUrl.setPath(currentUrl.getParsedPath().getParent());
        }
        if (c.isWorkbookOrTab())
            c = c.getParent();
        ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        String pattern = startURL.getLocalURIString();

        String link = nav.getHref() == null ? null : nav.getHref();
        String onClickScript = (null != nav.getScript()) ? PageFlowUtil.filter(nav.getScript()) : null;
        boolean selected = _highlightSelection && null != link && matchPath(link, currentUrl, pattern);
        if (level == 0 && null != nav.getText())
            level = 1;

        boolean hasChildren = nav.hasChildren();
        if (null != nav.getText())
            //When we post the expanded path, we need to use the escaped key so that embedded
            // '/' characters in the key are not confused with path separators
            pathToHere = pathToHere + "/" + nav.getEscapedKey();

        boolean collapsed = nav.isCollapsed();

        if (level > 0)
        {
            out.print("<li " + (hasChildren ? "class=\"clbl\"" : "") + ">");

            if (hasChildren)
                out.print("<span class=\"expand-folder marked\"> </span>");
            else
                out.print("<span> </span>");

            if (null == link)
                out.print(filter(nav.getText()));
            else
            {
                if (!StringUtils.isEmpty(nav.getId()))
                    out.printf("<a id=\"%s\" href=\"%s\"", filter(nav.getId()), filter(link));
                else
                    out.printf("<a href=\"%s\"", filter(link));

                if (selected)
                {
                    out.print(" class=\"nav-tree-selected\"");
                }
                out.print(">");
                out.print(filter(nav.getText()));
                out.print("</a>");
            }
//            out.print("<tr class=\"labkey-nav-tree-row labkey-header\">");
//
//            out.print("<td class=\"labkey-nav-tree-node\">\n");
//
//            if (hasChildren)
//            {
//                ActionURL expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(getViewContext().getContainer(), pathToHere, rootId);
//
//                String image = collapsed ? "plus.gif" : "minus.gif";
//                out.printf("<a href=\"%s\" onclick=\"return toggleLink(this, %s);\">",
//                        filter(expandCollapseUrl),
//                        "true");
//
//                out.printf("<img src=\"%s/_images/%s\" width=9 height=9></a>", context.getContextPath(), image);
//            }
//            else if (indentForExpansionGifs)
//                out.printf("<div class=\"labkey-nav-tree-indenter\"></div>");
//
//            out.print("</td><td class=\"labkey-nav-tree-text\"");
//            if(null != onClickScript)
//                out.print(" onclick=\"" + onClickScript + "\"");
//            out.println(">");
//
//            if (null == link)
//                out.print(filter(nav.getText()));
//            else
//            {
//                if (!StringUtils.isEmpty(nav.getId()))
//                    out.printf("<a id=\"%s\" href=\"%s\"", filter(nav.getId()), filter(link));
//                else
//                    out.printf("<a href=\"%s\"", filter(link));
//
//                if (nav.isNoFollow())
//                    out.print(" rel=\"nofollow\"");
//
//                // always open links to external sites in a new window or tab
//                if (null != nav.getTarget())
//                    out.print(" target=\"" + nav.getTarget() + "\"");
//                else if ((link.indexOf("http://") == 0) || (link.indexOf("https://") == 0))
//                    out.print(" target=\"_blank\"");
//
//                if (selected)
//                {
//                    out.print(" class=\"nav-tree-selected\"");
//                }
//                if (null != nav.getScript())
//                {
//                    out.print(" onclick=\"");
//                    out.print(filter(nav.getScript()));
//                    out.print("\"");
//                }
//                out.print(">");
//                out.print(filter(nav.getText()));
//                out.print("</a>");
//            }
//
//            out.print("</td></tr>");
        }

        //Render children as nested table in a row...
        if (hasChildren)
        {
//            for (NavTree element : nav.getChildren())
//            {
//                if (element.hasChildren())
//                    indentForExpansionGifs = true;
//            }
            out.print("<ul>");
//            out.printf("<tr%s>\n<td></td><td>\n<table class=\"labkey-nav-tree-child\">", collapsed ? " style=display:none" : "");
            for (NavTree child : nav.getChildren())
                renderLinks(child, level + 1, pathToHere, rootId, context, out, indentForExpansionGifs);
//            out.println("</table>\n</td></tr>");
            out.print("</ul>");
        }

        if (level > 0)
        {
            out.print("</li>");
        }
    }
}
