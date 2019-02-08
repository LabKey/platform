<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<div style="width: 700px">
    <p>
        For security reasons, LabKey Server restricts the host names that can be used in returnUrl parameters.
        By default, only redirects to the same LabKey instance are allowed.
        Other server host names must be configured below to allow them to be automatically redirected.
        For more information on the security concern, please refer to the
        <a href="<%= h("https://www.owasp.org/index.php/Unvalidated_Redirects_and_Forwards_Cheat_Sheet") %>">OWASP advisory</a>.
    </p>
</div>

<labkey:form method="post">
    <table>
        <tr>
            <td class="labkey-form-label"><label for="newExternalRedirectURLTextField">Host</label><%= helpPopup("Host", "New external redirect host URL")%></td>
            <td><input name="newExternalRedirectURL" id="newExternalRedirectURLTextField" size="75" /></td>
        </tr>
        <tr>
            <td><br/><input type="hidden" id="saveNew" name="saveNew" value="true"><%= button("Save").submit(true) %></td>
        </tr>
    </table>
</labkey:form>