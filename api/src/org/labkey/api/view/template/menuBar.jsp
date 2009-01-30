<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.view.template.MenuBarView" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    List<Portal.WebPart> menus = ((MenuBarView) HttpView.currentView()).getModelBean();
    ViewContext currentContext = org.labkey.api.view.HttpView.currentContext();
    Container c = currentContext.getContainer();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    AppProps appProps = AppProps.getInstance();
    NavTree homeLink;

    FolderDisplayMode folderMode = LookAndFeelProperties.getInstance(c).getFolderDisplayMode();
    boolean folderMenu = folderMode == FolderDisplayMode.OPTIONAL_ON || folderMode == FolderDisplayMode.OPTIONAL_OFF;
    if (menus.size() == 0 && !currentContext.hasPermission(ACL.PERM_ADMIN) && !folderMenu)
    {
        //out.print("<div style='width:100%;height:1' class='labkey-title-area-line'></div>");
        return;
    }
    String menuBarClass = menus.size() == 0 ? "normal" : "labkey-main-menu";
    if (null == c || null == c.getProject() || c.getProject().equals(ContainerManager.getHomeContainer()))
        homeLink = new NavTree(laf.getShortName() + " Home", appProps.getHomePageActionURL());
    else
        homeLink = new NavTree(c.getProject().getName() + " Home", c.getProject().getFolderType().getStartURL(c, currentContext.getUser()));
%>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
    Ext.onReady(function(){
        <%
        for (Portal.WebPart part : menus)
        {
            String menuName = part.getName() + part.getIndex();
        %>
            new LABKEY.HoverPopup({hoverElem:"<%=menuName%>$Header", webPartName:"<%=part.getName()%>",
                    partConfig: { <%
                    String sep = "";
                    for (Map.Entry<String,String> entry : part.getPropertyMap().entrySet())
                    { %>
                        <%=sep%><%=PageFlowUtil.jsString(entry.getKey())%>:<%=PageFlowUtil.jsString(entry.getValue())%><%
                        sep = ",";
                    }%>
                    }});<%
        }
        if (folderMenu)
        {%>
        new LABKEY.HoverPopup({hoverElem:"MenuBarFolders", webPartName:"Folders"});<%
        }%>
    });
</script>
<table id="menubar"><tr>
    <td colspan=2 class="<%=menuBarClass%>"><span class="normal"><%
        if (folderMenu)
        {
    %>
            <a href="#" id="MenuBarFolders"><img src="<%=currentContext.getContextPath()%>/ext-2.2/resources/images/default/tree/folder.gif" style="vertical-align:bottom" alt="Folders"></a><%
        }
        if (menus.size() > 0)
        {%>
            <a href="<%=h(homeLink.getValue())%>"><%=h(homeLink.getKey())%></a><%
            for (Portal.WebPart part : menus)
            {
                String menuCaption = part.getName();
                String menuName = part.getName() + part.getIndex();
                try
                {
                    WebPartFactory factory = Portal.getPortalPart(part.getName());
                    if (null == factory)
                        continue;
                    WebPartView view = factory.getWebPartView(currentContext, part);
                    if (null != view.getTitle())
                        menuCaption = view.getTitle();
                }
                catch(Exception e)
                {
                    //Use the part name...
                }
                %>
                    <a id="<%=h(menuName)%>$Header" style="position:relative;z-index:1001;" href="#"><%=h(menuCaption)%></a><%
            }
            }
        else
            out.print("<img src='" + currentContext.getContextPath() + "/_.gif'>");
        %></span></td>
    <td class="<%=menuBarClass%>" align="right"><%
        if (currentContext.hasPermission(ACL.PERM_ADMIN))
                include(new PopupAdminView(currentContext), out);
            else
            out.print("<img src='" + currentContext.getContextPath() + "/_.gif'>");
  %></td>
</tr>
</table>
