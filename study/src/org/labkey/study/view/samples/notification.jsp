<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenUtils" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenUtils.NotificationBean> me =
            (JspView<SpecimenUtils.NotificationBean>) HttpView.currentView();
    SpecimenUtils.NotificationBean bean = me.getModelBean();
    String requestChangeString = bean.getStatus().compareToIgnoreCase("submitted") == 0 ? "submitted" : "updated";
%>
<div>
    <br>
    Specimen request #<%= bean.getRequestId() %> was <%= requestChangeString %> in <%= bean.getStudyName() %>.
    <br>
    <br>
</div>
<table width="500px">
    <tr>

    </tr>
    <tr>
        <td valign="top"><b>Request&nbsp;Details</b></td>
    </tr>
    <tr>
        <td valign="top"><b>Specimen&nbsp;Request</b></td>
        <td align="left"><%= bean.getRequestId() %></td>
    </tr>
    <tr>
        <td valign="top"><b>Destination</b></td>
        <td align="left"><%= h(bean.getRequestingSiteName()) %></td>
    </tr>
    <tr>
        <td valign="top"><b>Status</b></td>
        <td align="left"><%= h(bean.getStatus()) %></td>
    </tr>
    <tr>
        <td valign="top"><b>Modified&nbsp;by</b></td>
        <td align="left"><%= h(bean.getModifyingUser()) %></td>
    </tr>
    <tr>
        <td valign="top"><b>Action</b></td>
        <td align="left"><%= bean.getEventDescription() != null ? h(bean.getEventDescription()).replaceAll("\\n", "<br>\n") : "" %></td>
    </tr>
    <%
        List<Attachment> attachments = bean.getAttachments();
        if (!attachments.isEmpty())
        {
    %>
    <tr>
        <td valign="top"><b>Attachments</b></td>
        <td align="left">
            <%
                for (Attachment att : attachments) {
            %>
            <a href="<%= bean.getBaseServerURI() %><%= h(att.getDownloadUrl(SpecimenController.DownloadAction.class)) %>">
                <%= h(att.getName()) %>
            </a><br>
            <%
                }
            %>
        </td>
    </tr>
    <%
        }
    %>
</table>
    <%
        if (bean.getComments() != null && bean.getComments().length() > 0)
        {
    %>
<p>
    <b>Current&nbsp;Comments</b><br>
    <%= h(bean.getComments(), true) %>
</p>
    <%
        }
    %>
<p>
    <%= bean.getRequestDescription() != null ? h(bean.getRequestDescription(), true) : "" %>
</p>
    <%
        if (bean.getSpecimenList() != null)
        {
    %>
<p>
    <b>Specimen&nbsp;List</b> (<a href="<%= bean.getRequestURI() %>id=<%=bean.getRequestId()%>">Request Link</a>)<br><br>
    <%= bean.getSpecimenList() %>
</p>
    <%
        }
    %>
