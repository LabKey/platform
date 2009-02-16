<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.study.assay.AssayUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView me = (JspView) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container proj = ctx.getContainer().getProject();

    List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(ctx.getContainer());
    for (ExpProtocol protocol : protocols)
    {
        ActionURL url;
        if (protocol.getContainer().equals(proj))
        {
            url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(proj, protocol);
            url.addParameter(protocol.getName() + " Runs.containerFilterName", ContainerFilter.Filters.ALL_IN_PROJECT.name());
        }
        else
            url = new ActionURL(AssayController.SummaryRedirectAction.class, ctx.getContainer());

        url.replaceParameter("rowId", ""+ protocol.getRowId());
        %>
<b><a href="<%=url%>"><%=h(protocol.getName())%></a></b>
<%      if (null != protocol.getDescription())
        {   %>
            <br><%=h(protocol.getDescription())%>
     <% }  %>
        <br>
<%  }
    if (ctx.getContainer().getProject().hasPermission(ctx.getUser(), ACL.PERM_ADMIN))
    {
        ActionURL actionURL = new ActionURL(AssayController.BeginAction.class,  ctx.getContainer().getProject());
%>
<%=generateButton("Manage Assays", actionURL)%>
<%
    }
%>
