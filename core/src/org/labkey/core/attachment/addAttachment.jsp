<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.attachment.AttachmentServiceImpl" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    String entityId = ((AttachmentServiceImpl.AddAttachmentView) HttpView.currentView()).getModelBean();
%>
<script type="text/javascript" language="javascript">
    LABKEY.requiresScript('util.js');
</script>
<labkey:errors />
Browse for file and then click submit<br>
<form method="POST" action="addAttachment.post" enctype="multipart/form-data">
  <input type="file" name="formFiles[0]" size="50" onChange="showPathname(this, 'filename')"><br>
  <input type="hidden" name="entityId" value="<%= entityId %>" /><br>
  <table>
    <tr>
      <td><input type="image" src="<%=PageFlowUtil.buttonSrc("Submit")%>"></td>
      <td><img onClick="window.close();" alt="Cancel" src="<%=PageFlowUtil.buttonSrc("Cancel")%>"></td>
      <td style="padding-left:5px;"><label class="normal" id="filename"></label></td>
    </tr>
  </table>
</form>
