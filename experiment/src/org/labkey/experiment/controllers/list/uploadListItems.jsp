<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="org.labkey.experiment.controllers.list.UploadListItemsForm" %>
<%@page extends="org.labkey.api.jsp.FormPage"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors />
<% UploadListItemsForm form = (UploadListItemsForm) __form;%>
<form action="<%=h(form.getList().urlFor(ListController.Action.uploadListItems))%>" method="POST">
    <table>
        <tr>
            <td class="ms-searchform" nowrap="true">List Data</td>
            <td class="ms-vb">
                Import data must formatted as tab separated values (TSV). Copy/paste from Microsoft Excel works well.<br>
                The first row should contain field names; subsequent rows should contain the data.<br>
                If your data includes rows with keys that already exist in the list then the rows will be replaced with the new data.<br>

                <textarea rows="25" style="width: 100%" cols="150" name="ff_data" wrap="off"><%=h(form.ff_data)%></textarea><br>
            </td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Submit" /></td>
        </tr>
    </table>
</form>
