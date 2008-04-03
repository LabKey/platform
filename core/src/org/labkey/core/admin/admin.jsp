<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.currentContext();
    org.labkey.api.security.User user = context.getUser();
    String contextPath = context.getContextPath();
    DbSchema schema = (DbSchema)context.get("schema");
%>
<table cellspacing="10"><tr>
<td valign=top>

<table class="dataRegion">
    <tr><td colspan="2"><b>Configuration</b></td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("customizeSiteUrl") %>">site settings</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("authenticationUrl") %>">authentication</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("FlowAdminUrl") %>">flow cytometry</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("customizeEmailUrl") %>">email customization</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("reorderProjectsUrl") %>">project display order</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("configureRReportUrl") %>">R view configuration</a>]</td></tr>

    <tr><td>&nbsp;</td></tr>

    <tr><td colspan="2"><b>Management</b></td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("MS1AdminUrl") %>">ms1</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("MS2AdminUrl") %>">ms2</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("PipeAdminUrl") %>">pipeline</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("pipelineSetupUrl") %>">pipeline email notification</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("ProteinAdminUrl") %>">protein databases</a>]</td></tr>

    <%if (AuditLogService.get().isViewable()){%>
        <tr><td colspan="2">[<a href="<%= context.get("auditLogUrl") %>">audit log</a>]</td></tr><%}%>

    <tr><td>&nbsp;</td></tr>

    <tr><td colspan="2"><b>Diagnostics</b></td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("threadsUrl") %>">running threads</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("memoryUrl") %>">memory usage</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("actionsUrl") %>">actions</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("scriptsUrl") %>">scripts</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("groovyUrl") %>">groovy templates</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("allErrorsUrl") %>">view all site errors</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("recentErrorsUrl") %>">view site errors since reset</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("resetErrorsUrl") %>">reset site errors</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("testLdapUrl") %>">test ldap</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("dbCheck") %>">check database</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%= context.get("creditsUrl") %>">credits</a>]</td></tr>

    <tr><td>&nbsp;</td></tr>

    <tr><td>
    <form method="get" action="impersonate.view">
        <table>
        <tr><td colspan="2"><b>Impersonate User</b></td></tr>
        <tr><td colspan="2">
            <select id="email" name="email" style="width:200"><%
                for (String email : (List<String>) context.get("emails"))
                {%>
                <option value="<%=h(email)%>" <%=(email.equals(user.getEmail())) ? "selected" : ""%>><%=h(email)%></option ><%
                }
            %>
            </select><br>
            <input style="vertical-align:bottom" type=image src="<%=PageFlowUtil.buttonSrc("Impersonate")%>">
        </td></tr>
        </table>
    </form>
    </td></tr>

    <tr><td>&nbsp;</td></tr>

    <tr><td colspan="2"><b>Active Users in the Last Hour</b></td></tr><%

    for (Pair<String, Long> pair : (List<Pair<String, Long>>) context.get("active"))
    {
        %><tr><td><%=h(pair.getKey())%></td><td><%=pair.getValue()%> minutes ago</td></tr><%
    }
%></table>
</td>

<td valign=top>
<table>
    <tr><td colspan="2"><b>Core Database Configuration</b></td></tr>
    <tr><td>Server URL</td><td><%= schema.getURL() %></td></tr>
    <tr><td>Product Name</td><td><%= schema.getDatabaseProductName() %></td></tr>
    <tr><td>Product Version</td><td><%= schema.getDatabaseProductVersion() %></td></tr>
    <tr><td>JDBC Driver Name</td><td><%= schema.getDriverName() %></td></tr>
    <tr><td>JDBC Driver Version</td><td><%= schema.getDriverVersion() %></td></tr>

    <tr><td>&nbsp;</td></tr>

    <tr><td colspan="2"><b>Runtime Information</b></td></tr>
    <tr><td>Mode</td><td><%= context.get("mode")%></td></tr>
    <tr><td>Servlet Container</td><td><%= context.get("servletContainer")%></td></tr>
    <tr><td>Java Runtime</td><td><%= context.get("javaVersion")%></td></tr>
    <tr><td>Username</td><td><%= context.get("userName")%></td></tr>
    <tr><td>OS</td><td><%= context.get("osName")%></td></tr>

    <tr><td>&nbsp;</td></tr>
</table>
<table class="dataRegion">
    <tr><td colspan="4"><b>Module Information</b></td></tr><%

    for (Module module : (List<Module>) context.get("Modules"))
    {%>
    <tr class="header">
        <td style="vertical-align:middle">
            <a href="#" onclick="return toggleLink(this, false);">
                <img border="0" src="<%= contextPath %>/_images/plus.gif">
            </a>
        </td>
        <td><%=h(module.getName())%></td>
        <td>Version</td>
        <td><%=module.getFormattedVersion()%></td>
    </tr><%
        for (String property : module.getMetaData().keySet())
        {%>
    <tr style="display:none">
        <td></td>
        <td></td>
        <td><%=h(property)%></td>
        <td><%=h(module.getMetaData().get( property ))%></td>
    </tr><%
        }
    }%>
</table>

</td>
</tr></table>