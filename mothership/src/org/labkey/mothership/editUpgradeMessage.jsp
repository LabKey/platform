<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.mothership.MothershipController"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<MothershipController.UpgradeMessageForm> me = (JspView<MothershipController.UpgradeMessageForm>) HttpView.currentView();
    MothershipController.UpgradeMessageForm form = me.getModelBean();
%>

<form action="saveUpgradeMessage.post" method="post">
    <table class="normal">
        <tr>
            <td>
                Current SVN revision:
            </td>
            <td>
                <input type="text" size="6" name="currentRevision" value="<%= form.getCurrentRevision() %>"/>
            </td>
        </tr>
        <tr>
            <td>
                Upgrade message (HTML allowed):
            </td>
            <td>
                <textarea rows="5" cols="50" name="message"><%= PageFlowUtil.filter(form.getMessage())%></textarea>
            </td>
        </tr>
        <tr>
            <td>
                Create issue URL:
            </td>
            <td>
                <input type="text" size="50" name="createIssueURL" value="<%= PageFlowUtil.filter(form.getCreateIssueURL())%>"/>
            </td>
        </tr>
        <tr>
            <td>
                Issues container path:
            </td>
            <td>
                <input type="text" size="50" name="issuesContainer" value="<%= PageFlowUtil.filter(form.getIssuesContainer())%>"/>
            </td>
        </tr>
        <tr>
            <td></td>
            <td>
                <%= buttonImg("Save") %>
            </td>
        </tr>
    </table>
</form>