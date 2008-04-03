<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String message = ((HttpView<String>) HttpView.currentView()).getModelBean();
%>
<%=message%>
<%=PageFlowUtil.buttonLink("Sign In", LoginController.getLoginURL())%>&nbsp;<%=PageFlowUtil.buttonLink("Home", AppProps.getInstance().getHomePageUrl())%>
