<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.attachment.AttachmentServiceImpl" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AttachmentServiceImpl.ErrorView me = (AttachmentServiceImpl.ErrorView) HttpView.currentView();
%>
<%=me.errorHtml%>
<a href="<%=h(me.forwardURL)%>"><img border=0 src="<%=PageFlowUtil.buttonSrc("Continue")%>"></a>
