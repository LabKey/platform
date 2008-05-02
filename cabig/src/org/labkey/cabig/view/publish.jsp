<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.cabig.caBIGController" %>
<%@ page import="org.labkey.cabig.caBIGManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext ctx = HttpView.currentContext();
    boolean isPublished = caBIGManager.get().isPublished(ctx.getContainer());
    String publishButton = PageFlowUtil.buttonLink(isPublished ? "Unpublish" : "Publish", caBIGController.getCaBigURL(isPublished ? "unpublish" : "publish", ctx.getContainer(), ctx.getActionURL()));
    ActionURL adminUrl = caBIGController.getCaBigURL("admin", ctx.getContainer(), ctx.getActionURL());

    if (isPublished)
    {
%>
This folder is published to the caBIG&trade; (cancer Biomedical Informatics Grid&trade;) interface.  If your caBIG&trade;
web application is running then all experiment data in this folder is publicly visible.<br><br>
<%
    }
    else
    {
%>
This folder is not published to the caBIG&trade; (cancer Biomedical Informatics Grid&trade;) interface.  Click the button below to publish
this folder to caBIG&trade;.  If you do this then all experiment data in this folder will be publicly visible via the caBIG&trade; web application.<br><br>
<%  }  %>

<%=publishButton%>&nbsp;<%=PageFlowUtil.buttonLink("Admin", adminUrl)%>
<br><br>For more information about publishing to caBIG&trade;, <a href="<%=h(new HelpTopic("cabig", HelpTopic.Area.CPAS).getHelpTopicLink())%>" target="cabig">click here</a>.

<%
    if (isPublished)
    {
%>
<br><br>If you've followed the default configuration and installed the caBIG&trade; web application (named "publish") on the same server as LabKey, then you can <a href="<%=h(ctx.getActionURL().getBaseServerURI())%>/publish/Happy.jsp">click here</a> to test it.
<%
    }
%>