<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.GroovyView" %>
<%@ page import="org.labkey.api.security.ValidEmail" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<form action="updateMembers.post" method="POST">
<%
    SecurityController.UpdateMembersBean bean = ((JspView<SecurityController.UpdateMembersBean>)HttpView.currentView()).getModelBean();

//include hidden inputs for each name to be added.
for(ValidEmail email : bean.addnames)
{%>
    <input type="hidden" name="names" value="<%=email.toString()%>"><%
}

//include hidden inputs for each username to be deleted.
for(String username : bean.removenames)
{%>
    <input type="hidden" name="delete" value="<%=username%>"><%
}%>

<input type="hidden" name="group" value="<%= bean.groupName %>">
<input type="hidden" name="mailPrefix" value="<%= null != bean.mailPrefix ? h(bean.mailPrefix) : "" %>">
<input type="hidden" name="confirmed" value="1">

If you delete your own user account from the Administrators group, you will no longer <br>
have administrative privileges. Are you sure that you want to continue?
<br><br>
<input type=image src="<%=PageFlowUtil.buttonSrc("Delete This Account","large")%>" value="Delete">&nbsp;
<input type=image src="<%=PageFlowUtil.buttonSrc("Cancel","large")%>" value="Cancel" onclick="javascript:window.history.back(); return false;">
</form>