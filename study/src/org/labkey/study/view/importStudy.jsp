<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<form action="" name="import" type="multipart/form-data" method="post">

<table cellpadding=0>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Import a study.  Select a .zip file.</td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<tr>
    <td class="labkey-form-label">Header logo (appears in every page header; 147 x 56 pixels)</td>
    <td><input type="file" name="logoImage" size="50"></td>
</tr>

<tr>
    <td><%=PageFlowUtil.generateSubmitButton("Import Study")%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

</table>
</form>
