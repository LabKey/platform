<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.wiki.WikiService" %>
<%@ page import="org.labkey.workbook.WorkbookController" %>
<%@ page import="org.labkey.workbook.WorkbookModule" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView me = (JspView) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container proj = ctx.getContainer().getProject();
    Set<Container> containers = ContainerManager.getAllChildren(proj, ctx.getUser());
%>
<%
    WikiService wikiSvc = ServiceRegistry.get().getService(WikiService.class);
    for (Container c : containers)
    {
        FolderType ft = c.getFolderType();
        if (!"Workbook".equals(ft.getName()))
            continue;
        String html = wikiSvc.getHtml(c, WorkbookModule.EXPERIMENT_DESCRIPTION_WIKI_NAME, false);
        %>
<a href="<%=h(ft.getStartURL(c, ctx.getUser()))%>"><%=h(c.getName())%></a> <div><%=null == html ? "" : html%>
    </div>
<%
    }
%>
<br>
<%=generateButton("Create Workbook", new ActionURL(WorkbookController.CreateWorkbookAction.class, proj))%>