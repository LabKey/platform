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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.study.model.SampleRequestStatus" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ViewRequestsHeaderBean> me = (JspView<SpringSpecimenController.ViewRequestsHeaderBean>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    SpringSpecimenController.ViewRequestsHeaderBean bean = me.getModelBean();
    ActionURL userLink = context.cloneActionURL();
    if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_ADMIN) || context.getUser().isAdministrator())
    {
%>
<%= textLink("Customize View", bean.getView().getCustomizeURL()) %>
<%
    }
%>
<%= textLink("All User Requests", userLink.deleteParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY)) %>
<%= textLink("My Requests", userLink.replaceParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY,
        context.getUser().getDisplayName(context))) %>
Filter by status: <select onChange="document.location=options[selectedIndex].value">
<%
    ActionURL current = context.cloneActionURL();
    current.deleteParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_STATUSLABEL);
%>
    <option value="<%= current.getLocalURIString() %>">All Statuses</option>
<%
    for (SampleRequestStatus status : bean.getStauses())
    {
        current.replaceParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_STATUSLABEL, status.getLabel());
%>
    <option value="<%= current.getLocalURIString() %>" <%= bean.isFilteredStatus(status) ? "SELECTED" : "" %>><%= h(status.getLabel()) %></option>
<%
    }
%>
</select>
<%
    String userFilter = context.getActionURL().getParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY);
    if (userFilter != null)
    {
%>
<b>Showing requests from user <%= userFilter %></b>
<%
    }
%>
