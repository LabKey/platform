<%@ page import="org.labkey.core.admin.FileSettingsForm" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    org.labkey.core.admin.AdminController.NewInstallSiteSettingsForm bean = ((JspView<org.labkey.core.admin.AdminController.NewInstallSiteSettingsForm>) HttpView.currentView()).getModelBean();
%>

<labkey:errors/>

<form method="POST">
    <h3 style="margin-bottom: 2px;"><label for="rootPath">Files Location</label></h3>
    <div style="margin-bottom: 10px;">
        This is where LabKey Server stores and looks for data files. The server will
        automatically create subdirectories to match the organization within
        LabKey Server. You can later configure the server to look in other file locations.
        <br/>
        <input type="text" id="rootPath" name="rootPath" style="width: 40em;" value="<%=h(bean.getRootPath())%>">
    </div>

    <h3 style="margin-bottom: 2px;"><label for="siteName">Site Name</label></h3>
    <div style="margin-bottom: 10px;">
        This is displayed in the header of each page and in emails sent by the server.
        <br/>
        <input type="text" id="siteName" name="siteName" style="width: 40em;" value="<%=h(bean.getSiteName())%>">
    </div>

    <h3 style="margin-bottom: 2px;"><label for="notificationEmail">Notification Email Address</label></h3>
    <div style="margin-bottom: 10px;">
        This is the &quot;from&quot; address used when sending notification
        emails. You may wish to set this to an address other than your own.
        <br/>
        <input type="text" id="notificationEmail" name="notificationEmail" style="width: 40em;" value="<%=h(bean.getNotificationEmail())%>">
    </div>

    <h3 style="margin-bottom: 2px;"><label for="allowReporting">Error and Usage Reporting</label></h3>
    <div style="margin-bottom: 10px;">
        Each installation can report information about errors and basic usage to labkey.org. This
        helps the development team fix bugs and provides insight into LabKey Server usage.
        No confidential data is submitted.
        <br/>
        <input type="checkbox" id="allowReporting" name="allowReporting" <%= bean.isAllowReporting() ? "checked" : "" %>> Allow reporting
        <span style="font-size: smaller; font-style: italic;">we strongly encourage to you to allow this basic level of reporting</span>
    </div>
    <%=generateSubmitButton("Next")%>
</form>