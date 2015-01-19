<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<labkey:form action="" name="import" enctype="multipart/form-data" method="post">
    <table cellpadding=0>
        <tr><td class="labkey-announcement-title" align=left><span>Validate Queries</span></td></tr>
        <tr><td class="labkey-title-area-line"></td></tr>
        <tr><td>By default, queries will be validated upon import of a study archive or study reload and any failure to validate will cause the import job to raise an error.
            To suppress this validation step, uncheck the option below before clicking Start Import. </td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td><label><input type="checkbox" name="validateQueries" checked value="true">&nbsp;Validate All Queries After Import</label></td></tr>
        <tr><td><%= button("Start Import").submit(true) %></td></tr>
    </table>
</labkey:form>

