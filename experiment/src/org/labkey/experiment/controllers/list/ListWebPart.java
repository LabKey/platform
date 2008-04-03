package org.labkey.experiment.controllers.list;

import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.Portal;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.security.ACL;
import org.labkey.api.data.Container;

import java.io.PrintWriter;
import java.util.Map;

public class ListWebPart extends WebPartView<ViewContext>
{
    static public WebPartFactory FACTORY = new WebPartFactory("Lists")
    {
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new ListWebPart(portalCtx);
        }

        public boolean isAvailable(Container c, String location)
        {
            return location.equals(getDefaultLocation());
        }
    };
    public ListWebPart(ViewContext portalCtx)
    {
        super(new ViewContext(portalCtx));
        setTitle("Lists");
        if (getModelBean().hasPermission(ACL.PERM_UPDATE))
        {
            setTitleHref(ListController.getBeginURL(getViewContext().getContainer()).toString());
        }
    }

    protected void renderView(ViewContext model, PrintWriter out) throws Exception
    {
        Map<String, ListDefinition> lists = ListService.get().getLists(model.getContainer());
        out.write("<table class=\"normal\">");
        if (lists.isEmpty())
        {
            out.write("<tr><td>There are no user-defined lists in this folder.</td></tr>");
        }
        else
        {
            for (ListDefinition list : lists.values())
            {
                out.write("<tr><td><a href=\"");
                out.write(PageFlowUtil.filter(list.urlShowData()));
                out.write("\">");
                out.write(PageFlowUtil.filter(list.getName()));
                out.write("</a></td></tr>");
            }
        }
        out.write("</table>");
        if (model.hasPermission(ACL.PERM_UPDATE))
            out.write("[<a href=\"" + PageFlowUtil.filter(ListController.getBeginURL(model.getContainer())) + "\">manage lists</a>]<br>");
    }
}
