<%
/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.query.controllers.PropertiesForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    PropertiesForm form = (PropertiesForm) HttpView.currentModel();
    // query properties are editable only if the SQL is editable, not the metadata
    boolean isEditable = form.getQueryDef().isSqlEditable();
    boolean hasPerms = form.getQueryDef().canEdit(getUser());
    boolean noEdit = !isEditable || !hasPerms;
%>

<labkey:errors />
<% if (!isEditable) { %>
<div class="alert alert-info">
    Query properties are not editable.
</div>
<% } else if (!hasPerms) { %>
<div class="alert alert-info">
    You do not have permission to edit the query properties.
</div>
<% } %>
<labkey:form method="POST" action="<%=noEdit ? form.urlFor(QueryAction.propertiesQuery) : null%>" >
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Name:</td>
            <td><input name="rename" value="<%=h(form.getQueryDef().getName())%>" <%=disabled(noEdit)%>></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description:</td>
            <td width="100%"><textarea style="width: 100%;" name="description" rows="5" cols="40" <%=disabled(noEdit)%>><%=h(form.description)%></textarea></td>
        </tr>
        <tr>
            <td class="labkey-form-label" nowrap="true">Available in child folders?</td>
            <td>
                <select name="inheritable" <%=disabled(noEdit)%>>
                    <option value="true"<%=selected(form.inheritable)%>>Yes</option>
                    <option value="false"<%=selected(!form.inheritable)%>>No</option>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label" nowrap="true">Hidden from the user?</td>
            <td>
                <select name="hidden" <%=disabled(noEdit)%>>
                    <option value="true"<%=selected(form.hidden)%>>Yes</option>
                    <option value="false"<%=selected(!form.hidden)%>>No</option>
                </select>
            </td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Save" enabled="<%=!noEdit%>"/></td>
        </tr>
    </table>
</labkey:form>
