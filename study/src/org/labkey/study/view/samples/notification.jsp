<%@ page import="org.labkey.study.controllers.samples.SpecimenUtils" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenUtils.NotificationBean> me =
            (JspView<SpecimenUtils.NotificationBean>) HttpView.currentView();
    SpecimenUtils.NotificationBean bean = me.getModelBean();
%>
<table width="500px">
    <tr>
        <td valign="top"><b>Specimen&nbsp;Request</b></td>
        <td align="left"><%= bean.getRequestId() %></td>
    </tr>
    <tr>
        <td valign="top"><b>Requesting&nbsp;Location</b></td>
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
        if (bean.getAttachments() != null && bean.getAttachments().length > 0)
        {
    %>
    <tr>
        <td valign="top"><b>Attachments</b></td>
        <td align="left">
            <%
                for (Attachment att : bean.getAttachments()) {
            %>
            <a href="<%= bean.getBaseServerURI() %><%= PageFlowUtil.filter(att.getDownloadUrl("Study-Samples")) %>">
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
    <b>Request&nbsp;Description</b><br>
    <%= bean.getRequestDescription() != null ? h(bean.getRequestDescription(), true) : "" %>
</p>
    <%
        if (bean.getSpecimenList() != null)
        {
    %>
<p>
    <b>Specimen&nbsp;List</b><br>
    <%= bean.getSpecimenList() %>
</p>
    <%
        }
    %>
