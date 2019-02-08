<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">

    function deleteExisting(urlToDelete) {

        document.getElementById("delete").value = true;
        document.getElementById("saveAll").value = false;
        document.getElementById("existingExternalURL").value = urlToDelete;
        document.forms["existingRedirectURLS"].submit();
    }

    function saveAll() {

        //clicking on save will save all the values - changed and unchanged values
        var num = 1;
        var inputNameExisting = "existingExternalURL" + num;
        var redirectURLs = "";

        while (null != document.getElementById(inputNameExisting))
        {
            redirectURLs += (document.getElementById(inputNameExisting).value + "\n");
            num++;
            inputNameExisting = "existingExternalURL" + num;
        }

        document.getElementById("saveAll").value = true;
        document.getElementById("existingExternalRedirectURLs").value = redirectURLs;
        document.forms["existingRedirectURLS"].submit();
    }
</script>

<labkey:form method="post" name="existingRedirectURLS">

    <%
        AdminController.ExternalRedirectForm bean = (AdminController.ExternalRedirectForm) HttpView.currentModel();
    %>
    <table class="labkey-data-region-legacy labkey-show-borders">
        <tr>
            <th>External Host URL(s)</th>
            <th></th>
        </tr>
        <% if (bean.getExistingRedirectURLList().size() == 0) { %>
            <tr><td colspan="2">No External Redirect Host URLs have been configured.</td></tr>
        <% } %>

        <%
            int num = 1;
            for (String externalRedirectURL : bean.getExistingRedirectURLList()) {
                String inputNameExisting = "existingExternalURL" + num;
        %>
        <tr>

            <td><input type="text" id="<%=h(inputNameExisting)%>" name="<%=h(inputNameExisting)%>" value="<%= h(externalRedirectURL)%>" size="80"/></td>

            <td><%= button("Delete").primary(true).onClick("return deleteExisting(\"" + h(externalRedirectURL) + "\");") %>

            </td>
        </tr>
        <%
            num++;
            }
        %>
    </table>
        <% if (bean.getExistingRedirectURLList().size() > 0) { %>
            <input type="hidden" id="delete" name="delete" value="false" />
            <input type="hidden" id="existingExternalURL" name="existingExternalURL" value="" />
            <input type="hidden" id="existingExternalRedirectURLs" name="existingExternalRedirectURLs" value="" />
            <tr>
                <td></td>
                <td><br/><input type="hidden" id="saveAll" name="saveAll"><%= button("Save").primary(true).onClick("return saveAll();")%>
            </tr>
        <% } %>
</labkey:form>