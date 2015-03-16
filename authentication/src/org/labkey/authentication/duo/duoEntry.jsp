<%@ page import="org.labkey.authentication.duo.DuoController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.authentication.duo.DuoController.DuoForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String message = "REPLACE ME!!!!";
    String sig_request = ((DuoForm) HttpView.currentView().getModelBean()).getSig_request();
%>
<p><%=h(message)%></p>
<p>Is that really you?</p>
<p><%=h(sig_request)%></p>
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
