<%@ page import="org.labkey.api.view.template.BodyTemplate" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    BodyTemplate me = (BodyTemplate) HttpView.currentView();
    PageConfig pageConfig = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    User u = ctx.getUser();
    ThemeFont themeFont = ThemeFont.getThemeFont(c);

    if (pageConfig.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", pageConfig.getFrameOption().name());
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="X-UA-Compatible" content="IE=Edge" />
    <%if (pageConfig.getFrameOption() == PageConfig.FrameOption.DENY) {%> <script type="text/javascript">if (top != self) top.location.replace(self.location.href);</script><%}%>
    <title><%=h(pageConfig.getTitle())%></title>
    <%= pageConfig.getMetaTags(me.getViewContext().getActionURL()) %>
    <%= PageFlowUtil.getStandardIncludes(c, u, request.getHeader("User-Agent"), pageConfig.getClientDependencies()) %>
</head>
<body class="<%=themeFont.getClassName()%>">
    <% me.include(me.getBody(), out);%>
</body>
<script type="text/javascript">
    LABKEY.loadScripts();
</script>
</html>
