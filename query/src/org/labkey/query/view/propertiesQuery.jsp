<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<% PropertiesForm form = (PropertiesForm) HttpView.currentModel(); %>

<labkey:errors />
<labkey:form method="POST" action="<%=form.urlFor(QueryAction.propertiesQuery)%>">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Name:</td>
            <td><input name="rename" value="<%=h(form.getQueryDef().getName())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description:</td>
            <td width="100%"><textarea style="width: 100%;" name="description" rows="5" cols="40"><%=h(form.description)%></textarea></td>
        </tr>
        <tr>
            <td class="labkey-form-label" nowrap="true">Available in child folders?</td>
            <td>
                <select name="inheritable">
                    <option value="true"<%=selected(form.inheritable)%>>Yes</option>
                    <option value="false"<%=selected(!form.inheritable)%>>No</option>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label" nowrap="true">Hidden from the user?</td>
            <td>
                <select name="hidden">
                    <option value="true"<%=selected(form.hidden)%>>Yes</option>
                    <option value="false"<%=selected(!form.hidden)%>>No</option>
                </select>
            </td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Save" /></td>
        </tr>
    </table>
</labkey:form>
