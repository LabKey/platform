<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<String> me = (JspView<String>) HttpView.currentView();
    String message = me.getModelBean();
%>
<script type="text/javascript">
if (window.opener && window.opener != window)
    window.opener.location.reload();
</script><%
if (null != message)
    {
    %><%= message %>
<a href="#continue"><img border=0 onClick="window.close()" src='<%=PageFlowUtil.buttonSrc("Continue")%>'></a>
<%
    }
else
    {
    %>Close this window. (You shouldn't be seeing this...)
<script type="text/javascript">
window.close();
</script>
<% } %>
