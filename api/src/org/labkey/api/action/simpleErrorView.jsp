<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%=formatMissedErrors("form")%><br><br>
<input type=image src="<%=PageFlowUtil.buttonSrc("Back", "large")%>" value="Back" onclick="window.history.back(); return false;"> 
<%=PageFlowUtil.buttonLink("Home", "large", AppProps.getInstance().getHomePageActionURL(), null)%>
