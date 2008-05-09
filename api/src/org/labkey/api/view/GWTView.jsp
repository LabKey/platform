<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    HttpView me = (org.labkey.api.view.HttpView) HttpView.currentView();
    GWTView.GWTViewBean bean = (GWTView.GWTViewBean) me.getModelBean();
%>
<div id="<%= PageFlowUtil.filter(bean.getModuleName()) %>-Root"></div>
<%
String contextPath = request.getContextPath();
String jsPath = bean.getModuleName() + "/" + bean.getModuleName() + ".nocache.js";

%>
<script type="text/javascript">
    LABKEY.requiresScript("<%=jsPath%>", <%= bean.isImmediateLoad()%>);
    
    <%= GWTView.PROPERTIES_OBJECT_NAME %> = new Object();
<%
    for (Map.Entry<String, String> entry : bean.getProperties().entrySet())
    {%>
    <%= GWTView.PROPERTIES_OBJECT_NAME %>[<%= PageFlowUtil.jsString(entry.getKey()) %>] = <%= PageFlowUtil.jsString(entry.getValue()) %>;<%
    }
%>
</script>
