package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class PopupFolderNavView extends PopupMenuView
{
    private ViewContext _context;
    private Map<Container, NavTree> projectNavTreeMap = new HashMap<>();;

    public PopupFolderNavView(ViewContext context)
    {
        super();
        _context = context;
    }

    public void render(Writer out) throws IOException
    {
        Container c = _context.getContainer();

        if (c.isRoot() || c.isProject())
            renderFolderNavTree(ContainerManager.getProjectList(_context), out);
        else if (c.getProject() != null)
            renderFolderNavTree(getProjectNavTree(c.getProject()), out);
    }

    private NavTree getProjectNavTree(Container project)
    {
        if (!projectNavTreeMap.containsKey(project))
            projectNavTreeMap.put(project, ContainerManager.getFolderListForUser(project, _context));

        return projectNavTreeMap.get(project);
    }

    private void renderFolderNavTree(NavTree tree, Writer out) throws IOException
    {
        if (tree == null || !PageFlowUtil.useExperimentalCoreUI())
            return;

        //String text = PageFlowUtil.filter(tree.getText());
        //out.write("<li><a class=\"projectcollapse\" tabindex=\"0\"><i class=\"fa fa-chevron-left\"></i>" + text + "</a></li>");
        //renderTreeDivider(out);

        for (NavTree child : tree.getChildren())
        {
            String text = PageFlowUtil.filter(child.getText());

            if (child.hasChildren())
            {
                out.write("<li class=\"dropdown-submenu\">");
                renderLink(child, "subexpand-link", out);
                out.write("<a class=\"subexpand subexpand-target\" tabindex=\"0\"><i class=\"fa fa-chevron-right\"></i></a>");
                out.write("<ul class=\"dropdown-layer-menu\">");
                out.write("<li><a class=\"subcollapse\" tabindex=\"0\"><i class=\"fa fa-chevron-left\"></i>" + text + "</a></li>");
                renderTreeDivider(out);
                renderFolderNavTree(child, out);
                out.write("</ul>");
                out.write("</li>");
            }
            else
            {
                String cls = null;//TODO context.getContainer().getName().equalsIgnoreCase(child.getText()) ? "selected" : null;
                renderTreeItem(child, cls, out);
            }
        }
    }

    public static void renderFolderNavTrailLink(Container container, User user, Writer out) throws IOException
    {
        if (container.hasPermission(user, ReadPermission.class))
            out.write("<a href=\"" + PageFlowUtil.filter(container.getStartURL(user)) +"\">" + PageFlowUtil.filter(container.getTitle()) + "</a>");
        else
            out.write("<span>" + PageFlowUtil.filter(container.getTitle()) + "</span>");

        out.write("<i class=\"fa fa-chevron-right\"></i>");
    }
}
