<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.list.view.ListController" %>
<%@ page import="org.labkey.list.view.UploadListItemsForm" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.FormPage"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors />
<%
    UploadListItemsForm form = (UploadListItemsForm) __form;
    ActionURL templateURL = new ActionURL("query", QueryAction.exportExcelTemplate.name(), getContainer()).addParameter("query.queryName", form.getList().getName()).addParameter("schemaName", "lists");
%>
<form action="<%=h(form.getList().urlFor(ListController.Action.uploadListItems))%>" method="POST">
    <table>
        <tr>
            <td class="labkey-form-label" nowrap="true">List Data</td>
            <td>
                Import data must be formatted as tab separated values (TSV). The first row should contain field names; subsequent rows should contain the data.<br>
                Copy/paste from Microsoft Excel works well; <a href="<%=h(templateURL)%>">click here to download an Excel template</a> you can use to insert into this list.<br>
                <textarea rows="25" id="listTsv" style="width: 100%" cols="150" name="ff_data" wrap="off"><%=h(form.ff_data)%></textarea><br>
                <script type="text/javascript">
                    Ext.EventManager.on('listTsv', 'keydown', handleTabsInTextArea);
                </script>
            </td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Submit" /></td>
        </tr>
    </table>
</form>
