<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.specimen.SpecimenRequestStatus" %>
<%@ page import="org.labkey.specimen.actions.ViewRequestsHeaderBean" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ViewRequestsHeaderBean> me = (JspView<ViewRequestsHeaderBean>) HttpView.currentView();
    ViewContext context = getViewContext();
    ViewRequestsHeaderBean bean = me.getModelBean();
    ActionURL userLink = context.cloneActionURL();
%>
<%= link("All User Requests", userLink.deleteParameter(ViewRequestsHeaderBean.PARAM_CREATEDBY)) %>
<%= link("My Requests", userLink.replaceParameter(ViewRequestsHeaderBean.PARAM_CREATEDBY, getUser().getDisplayName(getUser()))) %>
<% addHandler("viewRequestsSelect", "change", "document.location=options[selectedIndex].value;"); %>
Filter by status: <select id="viewRequestsSelect">
<%
    ActionURL current = context.cloneActionURL();
    current.deleteParameter(ViewRequestsHeaderBean.PARAM_STATUSLABEL);
%>
    <option value="<%= h(current.getLocalURIString()) %>">All Statuses</option>
<%
    for (SpecimenRequestStatus status : bean.getStauses())
    {
        current.replaceParameter(ViewRequestsHeaderBean.PARAM_STATUSLABEL, status.getLabel());
%>
    <option value="<%= h(current.getLocalURIString()) %>" <%=selected(bean.isFilteredStatus(status))%>><%= h(status.getLabel()) %></option>
<%
    }
%>
</select>
<%
    String userFilter = getActionURL().getParameter(ViewRequestsHeaderBean.PARAM_CREATEDBY);
    if (userFilter != null)
    {
%>
<b>Showing requests from user <%= h(userFilter) %></b>
<%
    }
%>
