<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    org.labkey.api.admin.AdminUrls adminURLs = PageFlowUtil.urlProvider(org.labkey.api.admin.AdminUrls.class);

    org.labkey.api.view.ViewContext viewContext = org.labkey.api.view.HttpView.currentContext();
%>

<p>Congratulations! Your LabKey Server installation is ready to use.</p>

<p>What would you like to do next?</p>

<ul>
    <li>
        <a href="<%= h(adminURLs.getAdminConsoleURL()) %>">Customize LabKey Server</a>
        (<a href="<%= h(adminURLs.getProjectSettingsURL(org.labkey.api.data.ContainerManager.getRoot())) %>">appearance</a>,
        security, <a href="<%= h(adminURLs.getCustomizeSiteURL()) %>">site settings</a>, etc)
    </li>

    <li>
        <a href="<%= h(adminURLs.getCreateProjectURL()) %>">Create a new project</a>
        (analyze MS1, MS2, flow cytometry, plate-based assay data; organize data in a study;
        create messages boards, wikis, or issue trackers, etc)
    </li>

    <li>
        <a href="<%= h(ContainerManager.getHomeContainer().getStartURL(viewContext.getUser())) %>">Skip these steps and go to the Home page</a>
    </li>
</ul>