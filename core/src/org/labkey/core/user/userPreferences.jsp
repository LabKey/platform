<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<UserController.UserPreference> me = (JspView<UserController.UserPreference>) HttpView.currentView();
    UserController.UserPreference bean = me.getModelBean();

    ActionURL showGridLink = new ActionURL("User", "showUsers", "");
    showGridLink.addParameter(".lastFilter", "true");
%>
<form action="" method="post">
    <table class="normal">
        <tr class="wpHeader"><td colspan="2">Required Fields for User Information</td></tr>
    <%
        for (ColumnInfo info : bean.getColumns())
        {
    %>
        <tr><td><input type="checkbox" name="requiredFields" <%=isRequired(info.getName(), bean.getRequiredFields()) ? "checked " : ""%> value="<%=info.getName()%>"><%=info.getCaption()%></td></tr>
    <%
        }
    %>
        <tr><td></td></tr>
        <tr>
            <td><%=PageFlowUtil.buttonLink("Show Grid", showGridLink)%>&nbsp;
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>"></td>
        </tr>
    </table><br>
</form>

<%!
    public boolean isRequired(String name, String requiredFields)
    {
        if (requiredFields != null)
        {
            return requiredFields.indexOf(name) != -1;
        }
        return false;
    }
%>