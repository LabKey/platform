<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminControllerSpring" %>
<%@ page import="org.labkey.core.admin.AdminControllerSpring.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ManageFoldersForm> view = (JspView<ManageFoldersForm>)HttpView.currentView();
    ManageFoldersForm f = view.getModelBean();
    Container c = view.getViewContext().getContainer();
    ActionURL cancelURL = AdminControllerSpring.getManageFoldersURL(c);
%>

<form action="moveFolder.post" method="post">
<p>
You are moving folder '<%=h(c.getName())%>' from one project into another.
This will remove all permission settings from this folder, any subfolders, and any contained objects.
</p>
<p>
This action cannot be undone.
</p>
    <input type="hidden" name="addAlias" value="<%=h(f.isAddAlias())%>">
    <input type="hidden" name="target" value="<%=h(f.getTarget())%>">
    <input type="hidden" name="confirmed" value="1">
    <input type="image" src="<%= PageFlowUtil.buttonSrc("Confirm Move") %>" />
    <a href="<%=h(cancelURL)%>"><img src="<%= PageFlowUtil.buttonSrc("Cancel") %>" /></a>
</form>