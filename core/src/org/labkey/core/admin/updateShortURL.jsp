<%
/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<labkey:errors/>

<%
    AdminController.ShortURLForm bean = (AdminController.ShortURLForm) HttpView.currentModel();
%>

<labkey:form action="<%=urlFor(AdminController.UpdateShortURLAction.class)%>" method="POST" id="updateShortUrlForm">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Short URL: </td>
            <td>
                <input type="hidden" name="shortURL" value="<%= h(bean.getShortURL()) %>"/>
                <%= h(bean.getShortURL()) %>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Target URL: </td>
            <td><textarea rows="3" cols="80" name="fullURL"><%= h(bean.getFullURL()) %></textarea></td>
        </tr>
    </table>
    <div style="margin-top: 10px;">
        <%= button("Update").submit(true) %>
        <%= button("Cancel").href(new ActionURL(AdminController.ShortURLAdminAction.class, getContainer())) %>
    </div>
</labkey:form>

<div style="margin-top: 20px;">
    <%= button("Delete")
            .usePost("Are you sure you want to delete the short URL " + bean.getShortURL() + "?")
            .href(urlFor(AdminController.UpdateShortURLAction.class).addParameter("shortURL", bean.getShortURL()).addParameter("delete", true)) %>
</div>