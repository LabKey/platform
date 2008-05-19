<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
