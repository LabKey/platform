<%@ page import="org.labkey.api.view.template.AppBarView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    AppBar bean = ((AppBarView) HttpView.currentView()).getModelBean();
    ViewContext currentContext = org.labkey.api.view.HttpView.currentContext();
    Container c = currentContext.getContainer();
    String contextPath = currentContext.getContextPath();
    ActionURL currentURL = currentContext.getActionURL();
    AppProps app = AppProps.getInstance();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(currentContext.getContainer());
    if (null == bean)
        return;
    NavTree extra;
%>
<table class="labkey-app-bar">
    <tr>
        <td ><%=h(bean.getAppTitle())%></td>
        <td>
            <table class="labkey-app-button-bar labkey-no-spacing">
                <td class="labkey-app-button-bar-left"><img alt="" src="<%=request.getContextPath()%>/_.gif" width="13"></td>
                <%
                    for (NavTree navTree : bean.getButtons())
                    {
                %>
                        <td class="labkey-app-button-bar-button"><a href="<%=h(navTree.getValue())%>"><%=h(navTree.getKey())%></a></td>
                <%
                    }
                %>
                <td class="labkey-app-button-bar-right"><img alt="" src="<%=request.getContextPath()%>/_.gif" width="13"></td>
            </table>
        </td>
    </tr>

</table>
