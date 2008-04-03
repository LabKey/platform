<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SampleManager.DisplaySettings> me =
            (JspView<SampleManager.DisplaySettings>) HttpView.currentView();
    SampleManager.DisplaySettings bean = me.getModelBean();
    Container container = HttpView.getRootContext().getContainer();
%>

<%=PageFlowUtil.getStrutsError(request, "main")%>
<form action="handleUpdateDisplaySettings.post" method="POST">
    <table cellspacing="5" class="normal" width="500">
        <tr>
            <td colspan="2">The specimen request system can display warning icons when one or zero vials of any primary specimen are available for request.  The icon will appear next to all vials of that the primary specimen.</td>
        </tr>
        <tr>
            <th align="right">Display warning icon when available vial count reaches one:</th>
            <td>
                <select name="lastVial">
                    <option value="<%= SampleManager.DisplaySettings.DisplayOption.NONE.name() %>"
                            <%= bean.getLastVialEnum() == SampleManager.DisplaySettings.DisplayOption.NONE ? "SELECTED" : ""%>>Never</option>
                    <option value="<%= SampleManager.DisplaySettings.DisplayOption.ALL_USERS.name() %>"
                            <%= bean.getLastVialEnum() == SampleManager.DisplaySettings.DisplayOption.ALL_USERS ? "SELECTED" : ""%>>For all users</option>
                    <option value="<%= SampleManager.DisplaySettings.DisplayOption.ADMINS_ONLY.name() %>"
                            <%= bean.getLastVialEnum() == SampleManager.DisplaySettings.DisplayOption.ADMINS_ONLY ? "SELECTED" : ""%>>For administrators only</option>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Display warning icon when available vial count reaches zero:</th>
            <td>
                <select name="zeroVials">
                    <option value="<%= SampleManager.DisplaySettings.DisplayOption.NONE.name() %>"
                            <%= bean.getZeroVialsEnum() == SampleManager.DisplaySettings.DisplayOption.NONE ? "SELECTED" : ""%>>Never</option>
                    <option value="<%= SampleManager.DisplaySettings.DisplayOption.ALL_USERS.name() %>"
                            <%= bean.getZeroVialsEnum() == SampleManager.DisplaySettings.DisplayOption.ALL_USERS ? "SELECTED" : ""%>>For all users</option>
                    <option value="<%= SampleManager.DisplaySettings.DisplayOption.ADMINS_ONLY.name() %>"
                            <%= bean.getZeroVialsEnum() == SampleManager.DisplaySettings.DisplayOption.ADMINS_ONLY ? "SELECTED" : ""%>>For administrators only</option>
                </select>
            </td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= buttonImg("Save") %>&nbsp;
                <%= buttonLink("Cancel", ActionURL.toPathString("Study", "manageStudy", container))%>
            </td>
        </tr>
    </table>
</form>
