<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.menu.HeaderMenu" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.MenuBarView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    MenuBarView me = ((MenuBarView) HttpView.currentView());
    MenuBarView.MenuBarBean bean = me.getModelBean();
    List<Portal.WebPart> menus = bean.menus;
    PageConfig pageConfig = bean.pageConfig;

    ViewContext context = getViewContext();
    Container c = getContainer();

    boolean showExperimentalNavigation = AppProps.getInstance().isExperimentalFeatureEnabled(MenuBarView.EXPERIMENTAL_NAV);
    boolean showProjectNavigation = showExperimentalNavigation || context.isShowFolders();
    boolean showFolderNavigation = !showExperimentalNavigation && c != null && !c.isRoot() && c.getProject() != null && context.isShowFolders();
    Container p = c.getProject();
    String projectTitle = "";
    if (null != p)
    {
        projectTitle = p.getTitle();
        if (null != projectTitle && projectTitle.equalsIgnoreCase("home"))
            projectTitle = "Home";
    }
%>
<!-- TOPMENU -->
<div id="topmenu" class="labkey-header-panel">
<div id="menubar" class="labkey-main-menu">
    <div class="labkey-menu-constraint">
        <ul>
<%
    if (showProjectNavigation)
    {
%>
        <li id="<%=text(showExperimentalNavigation ? "betaBar" : "projectBar")%>" class="menu-projects"></li>
<%
    }

    if (showFolderNavigation)
    {
%>
        <li id="folderBar" class="menu-folders"><%=h(projectTitle)%></li>
<%
    }
%>
        <%
            if(menus.size() > 0)
            {
                for (Portal.WebPart part : menus)
                {
                    String menuCaption = part.getName();
                    String menuName = part.getName() + part.getIndex();
                    menuName = menuName.replaceAll("\\s+","");
                    try
                    {
                        WebPartFactory factory = Portal.getPortalPart(part.getName());
                        if (null == factory)
                            continue;
                        WebPartView view = factory.getWebPartView(context, part);
                        if (view.isEmpty())
                            continue;       // Don't show folder/query if nothing to show
                        if (null != view.getTitle())
                            menuCaption = view.getTitle();
                    }
                    catch(Exception e)
                    {
                        //Use the part name...
                    }
        %>
        <li id="<%=h(menuName)%>-Header" class="labkey-main-menu-item">
            <a class="labkey-main-menu-link" href="#">
                <%=h(menuCaption)%>
            </a>
        </li>
        <%
                }
            }
        %>
    </ul>
<%
    include(new HeaderMenu(pageConfig), out);
%>
    </div>
</div>
<div class="labkey-main-menu main-menu-replicate"></div>
<script type="text/javascript">
    LABKEY.Utils.onReady({
        scripts: ['core/MenuBarHoverNavigation.js'],
        callback: function() {
            var loginUrl = <%= PageFlowUtil.jsString(PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(getContainer(), getActionURL()).toString()) %>;

            HoverNavigation._project = new HoverNavigation({
                hoverElem : '<%=text(showExperimentalNavigation ? "betaBar" : "projectBar")%>',
                webPartName : '<%=text(showExperimentalNavigation ? "betanav" : "projectnav")%>',
                webPartUrl: LABKEY.ActionURL.buildURL('project', 'getNavigationPart'),
                loginUrl: loginUrl
            });
<%
    if (showFolderNavigation)
    {
%>
            HoverNavigation._folder = new HoverNavigation({
                hoverElem : 'folderBar',
                webPartName : 'foldernav',
                webPartUrl: LABKEY.ActionURL.buildURL('project', 'getNavigationPart'),
                loginUrl: loginUrl
            });
<%
    }

    for (Portal.WebPart part : menus)
    {
        if (null == Portal.getPortalPartCaseInsensitive(part.getName()))
            continue;

        String menuName = part.getName() + part.getIndex();
        menuName = menuName.replaceAll("\\s+","");
%>
            HoverNavigation.Parts["_<%=text(menuName)%>"] = new HoverNavigation({
                hoverElem:"<%=text(menuName)%>-Header",
                webPartName: "<%=text(part.getName())%>",
                partConfig: { <%
                        String sep = "";
                        for (Map.Entry<String,String> entry : part.getPropertyMap().entrySet())
                        { %>
                            <%=text(sep)%><%=PageFlowUtil.jsString(entry.getKey())%>:<%=PageFlowUtil.jsString(entry.getValue())%><%
                            sep = ",";
                        }%>

                        <%=text(sep)%>hoverPartName:<%=PageFlowUtil.jsString("_" + menuName)%>
                },
                loginUrl: loginUrl
            });
<%
    }
%>
        }
    });
</script>
</div>
<!-- /TOPMENU -->