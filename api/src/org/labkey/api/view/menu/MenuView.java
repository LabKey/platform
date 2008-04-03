package org.labkey.api.view.menu;

import org.labkey.api.view.*;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintWriter;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:36:56 AM
 */
public class MenuView extends VBox
{
    public MenuView(List<? extends WebPartView> views)
    {
        _views = new ArrayList<ModelAndView>(views);
        setFrame(FrameType.NONE);
    }

    @Override
    public void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        boolean showFolders = HttpView.currentContext().isShowFolders();
        FolderDisplayMode displayMode = AppProps.getInstance().getFolderDisplayMode();
        boolean renderFolderExpander = !HttpView.currentContext().isAdminMode() && (displayMode == FolderDisplayMode.OPTIONAL_OFF || displayMode == FolderDisplayMode.OPTIONAL_ON);

        PrintWriter out = response.getWriter();

        out.println("<table class=\"ms-vb\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\" style=\"padding: 0px\">");
        if(showFolders)
        {
            List<HttpView> nonNullViews = new ArrayList<HttpView>();
            for (ModelAndView possibleView : _views)
            {
                if (possibleView instanceof HttpView && ((HttpView)possibleView).isVisible())
                    nonNullViews.add((HttpView)possibleView);
            }
            boolean expanderRendered = false;
            for (HttpView view : nonNullViews)
            {
                if (renderFolderExpander && ! expanderRendered)
                {
                    out.println("<tr><td><!-- menuview element -->");
                    include(view);
                    out.println("<!--/ menuview element --></td>");
                    renderExpandCollapseTD(request, out, showFolders);
                    out.println("</tr>");
                    expanderRendered = true;
                }
                else
                {
                    out.println("<tr><td colspan=" + (renderFolderExpander ? "2" : "1") + "><!-- menuview element -->");
                    include(view);
                    out.println("<!--/ menuview element --></td></tr>");
                }
            }
        }
        else if(renderFolderExpander) 
        {
            out.println("<tr>");
            renderExpandCollapseTD(request, out, showFolders);
            out.println("</tr>");
        }
        out.print("</table>");
    }

    private void renderExpandCollapseTD(HttpServletRequest request, PrintWriter out, boolean showFolders)
    {
        ActionURL hideShowLink = HttpView.currentContext().cloneActionURL();
        hideShowLink.deleteParameters();
        hideShowLink.setPageFlow("admin");
        hideShowLink.setAction("setShowFolders.view");
        hideShowLink.addParameter("showFolders", Boolean.toString(!showFolders));
        hideShowLink.addParameter("redir", HttpView.currentContext().getActionURL().toString());

        out.print("<td id=\"expandCollapseFolders\" align=left  style=\"padding-top:5px\" valign=top class=ms-navframe >\n");
        out.print("<a href=\"" + hideShowLink.getEncodedLocalURIString() + "\">");
        String img = (showFolders ? "collapse_" : "expand_") + "folders.gif";
        String title = "Click to " + (showFolders ? "hide" : "show") + " folders.";
        out.print("<img src=\"" + request.getContextPath() + "/_images/" + img + "\" title=\"");
        out.print(PageFlowUtil.filter(title));
        out.print("\">");
        out.print("</a></td>");
    }
}
