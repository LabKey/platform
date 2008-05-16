<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController.*" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ConfigProperties> me = (JspView<ConfigProperties>)HttpView.currentView();
    ConfigProperties bean = me.getModelBean();
%>
<form action="configure.post" method="post">
    <table><%

    for (String key : bean.props.keySet())
    {
        String value = bean.props.get(key);
%>
        <tr><td class="ms-searchform"><%=key%></td><td><input type="text" name="<%=key%>" value="<%=value%>" style="width:400px;"></td></tr><%
    }
%>
    </table><br>
    <input type=image src="<%=PageFlowUtil.submitSrc()%>" value="Configure">
    <%=PageFlowUtil.buttonLink("Cancel", OpenSSOController.getCurrentSettingsURL())%>
</form>