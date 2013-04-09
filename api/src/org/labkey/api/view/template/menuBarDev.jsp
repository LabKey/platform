<%
    /*
     * Copyright (c) 2009-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.view.template.MenuBarView" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
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
    boolean folderMenu = folderMode.isShowInMenu();
    boolean customMenusEnabled = laf.isMenuUIEnabled();
    folderMode.isShowInMenu();

    if (null == c || null == c.getProject() || c.getProject().equals(ContainerManager.getHomeContainer()))
        homeLink = new NavTree(laf.getShortName() + " Home", AppProps.getInstance().getHomePageActionURL());
    else
        homeLink = new NavTree(c.getProject().getName(), c.getProject().getFolderType().getStartURL(c.getProject(), currentContext.getUser()));
%>
<div id="menubar" class="labkey-main-menu">
    <ul>
        <li id="projectBar" class="menu-projects"> </li>
        <li id="homeBar" class="menu-folders">Home</li>
    </ul>
</div>
<script type="text/javascript">
    Ext.onReady(function(){

        new LABKEY.HoverPopup({hoverElem:"projectBar", webPartName:"projectnav"});
        new LABKEY.HoverPopup({hoverElem:"homeBar", webPartName:"Folders"});

    });
</script>
