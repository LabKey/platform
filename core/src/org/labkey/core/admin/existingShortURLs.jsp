<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ShortURLRecord" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%
/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/clipboard/clipboard-1.5.9.min.js");
    }
%>

<%
    AdminController.ShortURLForm bean = (AdminController.ShortURLForm) HttpView.currentModel();
%>

<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Short URL</td>
        <td class="labkey-column-header">Test Link</td>
        <td class="labkey-column-header"></td>
        <td class="labkey-column-header">Target URL</td>
        <td class="labkey-column-header"></td>
    </tr>
    <% if (bean.getSavedShortURLs().isEmpty()) { %><tr><td colspan="5">No short URLs have been configured.</td></tr> <% } %>
    <% int index = 0;
        for (ShortURLRecord shortURLRecord : bean.getSavedShortURLs()) {
            index++; %>
        <tr>
            <td><%= h(shortURLRecord.getShortURL())%></td>
            <td><%= textLink("test", shortURLRecord.renderShortURL()) %></td>
            <td>
                <%= textLink("copy to clipboard", (URLHelper)null, "return false;", "copyToClipboardId" + index, Collections.singletonMap("data-clipboard-text", shortURLRecord.renderShortURL())) %>
                <script>new Clipboard('#copyToClipboardId' + <%= index %>)</script>
            </td>
            <td><labkey:form method="post"><input type="text" name="fullURL" value="<%= h(shortURLRecord.getFullURL())%>" size="40"/> <%= button("Update").submit(true) %><input type="hidden" name="shortURL" value="<%= h(shortURLRecord.getShortURL())%>" /></labkey:form></td>
            <td><labkey:form method="post"><input type="hidden" name="delete" value="true" /><%= button("Delete").submit(true) %><input type="hidden" name="shortURL" value="<%= h(shortURLRecord.getShortURL())%>" /></labkey:form></td>
        </tr>
    <% } %>
</table>
