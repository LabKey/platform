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
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="org.labkey.experiment.controllers.list.ListDefinitionForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    ListDefinitionForm form = (ListDefinitionForm) __form;
    ListDefinition list = form.getList();
%>
<form action="<%=list.urlFor(ListController.Action.insert)%>" method="POST">
    <table>
        <tr>
            <td class="labkey-form-label"><%=h(list.getKeyName())%></td>
            <td></td>
        </tr>
    </table>
</form>