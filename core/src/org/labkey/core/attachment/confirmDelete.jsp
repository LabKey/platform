<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.attachment.AttachmentServiceImpl" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AttachmentServiceImpl.ConfirmDeleteView me = (AttachmentServiceImpl.ConfirmDeleteView) HttpView.currentView();
%>
<table>
  <tr>
    <td class="normal">Delete <%=me.name%>?</td>
  </tr>
  <tr>
    <td class="normal">
      <a href="<%=h(me.deleteURL)%>"><img border=0 src="<%=PageFlowUtil.buttonSrc("OK")%>"></a>
      <a href="#cancel"><img border=0 onClick="window.close()" src="<%=PageFlowUtil.buttonSrc("Cancel")%>"></a>
    </td>
  </tr>
</table>
