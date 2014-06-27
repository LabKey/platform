<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.SpecimenRequestStatus" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.ViewRequestsHeaderBean> me = (JspView<SpecimenController.ViewRequestsHeaderBean>) HttpView.currentView();
    ViewContext context = getViewContext();
    SpecimenController.ViewRequestsHeaderBean bean = me.getModelBean();
    ActionURL userLink = context.cloneActionURL();
%>
<%= textLink("All User Requests", userLink.deleteParameter(SpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY)) %>
<%= textLink("My Requests", userLink.replaceParameter(SpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY,
        getUser().getDisplayName(getUser()))) %>
Filter by status: <select onChange="document.location=options[selectedIndex].value;">
<%
    ActionURL current = context.cloneActionURL();
    current.deleteParameter(SpecimenController.ViewRequestsHeaderBean.PARAM_STATUSLABEL);
%>
    <option value="<%= current.getLocalURIString() %>">All Statuses</option>
<%
    for (SpecimenRequestStatus status : bean.getStauses())
    {
        current.replaceParameter(SpecimenController.ViewRequestsHeaderBean.PARAM_STATUSLABEL, status.getLabel());
%>
    <option value="<%= current.getLocalURIString() %>" <%=selected(bean.isFilteredStatus(status))%>><%= h(status.getLabel()) %></option>
<%
    }
%>
</select>
<%
    String userFilter = getActionURL().getParameter(SpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY);
    if (userFilter != null)
    {
%>
<b>Showing requests from user <%= userFilter %></b>
<%
    }
%>
