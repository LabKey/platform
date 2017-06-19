package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class PopupFolderNavView extends PopupMenuView
{
    public PopupFolderNavView(NavTree navTree)
    {
        super(navTree);
    }

    public static void renderTree(ViewContext context, NavTree tree, Writer out) throws IOException
    {
        if (tree == null || !PageFlowUtil.useExperimentalCoreUI())
            return;

        for (NavTree child : tree.getChildren())
        {
            if (child.hasChildren())
            {
                String text = PageFlowUtil.filter(child.getText());

                out.write("<li class=\"dropdown-submenu\">");
                renderLink(child, "subexpand-link", out);
                out.write("<a class=\"subexpand subexpand-target\" tabindex=\"0\"><i class=\"fa fa-chevron-right\"></i></a>");
                out.write("<ul class=\"dropdown-layer-menu\">");
                out.write("<li><a class=\"subcollapse\" tabindex=\"0\"><i class=\"fa fa-chevron-left\"></i>" + text + "</a></li>");
                renderTreeDivider(out);
                renderTree(context, child, out);
                out.write("</ul>");
                out.write("</li>");
            }
            else
            {
                String cls = context.getContainer().getName().equalsIgnoreCase(child.getText()) ? "active" : null;
                renderTreeItem(child, cls, out);
            }
        }
    }

    public static void renderFolderNavTrail(ViewContext context, Writer out) throws IOException
    {
        List<Container> containers = ContainerManager.containersToRootList(context.getContainer());
        int size = containers.size();

        // Don't show the nav trail for the root
        if (size > 1)
        {
            String title = containers.get(size - 1).isWorkbook()
                    ? containers.get(size - 1).getName() : containers.get(size - 1).getTitle();

            out.write("<div class=\"lk-folder-nav-trail\">");
            if (size < 5)
            {
                for (int i = 0; i < size - 1; i++)
                    renderFolderNavTrailLink(containers.get(i), context.getUser(), out);
            }
            else
            {
                for (int i = 0; i < 2; i++)
                    renderFolderNavTrailLink(containers.get(i), context.getUser(), out);
                out.write("...<i class=\"fa fa-chevron-right\"></i>");
                for (int i = (size - 2); i < (size - 1); i++)
                    renderFolderNavTrailLink(containers.get(i), context.getUser(), out);
            }

            out.write("<span>" + PageFlowUtil.filter(title) + "</span>");
            out.write("</div>");
            out.write("<div class=\"divider lk-project-nav-divider\"></div>");
        }
    }

    private static void renderFolderNavTrailLink(Container container, User user, Writer out) throws IOException
    {
        if (container.hasPermission(user, ReadPermission.class))
            out.write("<a href=\"" + PageFlowUtil.filter(container.getStartURL(user)) +"\">" + PageFlowUtil.filter(container.getTitle()) + "</a>");
        else
            out.write("<span>" + PageFlowUtil.filter(container.getTitle()) + "</span>");

        out.write("<i class=\"fa fa-chevron-right\"></i>");
    }
}
