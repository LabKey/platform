<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    
%>
<form action="" name="import" enctype="multipart/form-data" method="post">
<table cellpadding=0>
    <%=formatMissedErrorsInTable("form", 1)%>
<tr>
    <td><input type="radio" name="source" value="zip" checked>Import from a .zip file</td>
    <td><input type="file" name="studyZip" size="50"></td>
</tr>
<tr>
    <td><input type="radio" name="source" value="pipeline">Import from the pipeline root</td>
    <td>&nbsp;</td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td><%=PageFlowUtil.generateSubmitButton("Import Study")%></td>
</tr>

</table>
</form>
