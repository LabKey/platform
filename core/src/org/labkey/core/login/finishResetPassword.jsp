<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String message = ((HttpView<String>) HttpView.currentView()).getModelBean();
%>
<%=message%>
<%=PageFlowUtil.buttonLink("Sign In", AuthenticationManager.getLoginURL((ActionURL)null))%>&nbsp;<%=PageFlowUtil.buttonLink("Home", AppProps.getInstance().getHomePageUrl())%>
