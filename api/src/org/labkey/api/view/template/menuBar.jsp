<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.view.template.MenuBarView" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("ext3"));
      return resources;
  }
%>
<%
    List<Portal.WebPart> menus = ((MenuBarView) HttpView.currentView()).getModelBean();
    ViewContext currentContext = HttpView.currentContext();
    Container c = currentContext.getContainer();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    NavTree homeLink;

    FolderDisplayMode folderMode = LookAndFeelProperties.getInstance(c).getFolderDisplayMode();
    if (!laf.isShowMenuBar())
        return;
    boolean folderMenu = folderMode.isShowInMenu();
    String menuBarClass = "labkey-main-menu";
    if (null == c || null == c.getProject() || c.getProject().equals(ContainerManager.getHomeContainer()))
        homeLink = new NavTree(laf.getShortName() + " Home", AppProps.getInstance().getHomePageActionURL());
    else
        homeLink = new NavTree(c.getProject().getName(), c.getProject().getFolderType().getStartURL(c.getProject(), currentContext.getUser()));
%>
<script type="text/javascript">
    Ext.onReady(function(){
        <%
        for (Portal.WebPart part : menus)
        {
            if (null == Portal.getPortalPartCaseInsensitive(part.getName()))
                continue;

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
        new LABKEY.HoverPopup({hoverElem:"menuBarFolder", webPartName:"Folders"});<%
        }%>
    });
</script>
<table id="menubar"><tr>
    <td colspan=2 class="<%=menuBarClass%>"><span class="normal"><%
        if (folderMenu)
        {
    %>
            <a href="#" id="menuBarFolder" class="labkey-header" style="position:relative;padding-right:1em"><span><img src="<%=currentContext.getContextPath()%>/<%=PageFlowUtil.extJsRoot()%>/resources/images/default/tree/folder.gif" style="vertical-align:bottom" alt="Folders"></span></a><%
        }
        if (menus.size() > 0)
        {
            if (!folderMenu) //Make sure you can always get back to project home
            {%> 
                <a href="<%=h(homeLink.getHref())%>"><%=h(homeLink.getText())%></a><%
            }
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
                    <a id="<%=h(menuName)%>$Header" class="labkey-header" style="vertical-align:bottom;padding-right:1em;position:relative;z-index:1001;" href="#"><span><%=h(menuCaption)%></span></a><%
            }
            }
        else
            out.print("<img src='" + currentContext.getContextPath() + "/_.gif'>");
        %></span></td>
    </tr>
</table>
