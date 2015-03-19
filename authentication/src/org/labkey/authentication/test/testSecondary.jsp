<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<p>Is that really you?</p>

<labkey:form method="POST">
    <table>
        <tr>
            <td valign="top"><input type="radio" name="valid" value="1"></td>
            <td>Yes!</td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="valid" value="0" checked></td>
            <td>No</td>
        </tr>
    </table>
    <input type="submit" value="Validate">
</labkey:form>