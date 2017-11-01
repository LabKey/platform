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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ShortURLRecord" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    ShortURLRecord exampleRecord = new ShortURLRecord();
    exampleRecord.setShortURL("SHORT_URL");
    String exampleURL = exampleRecord.renderShortURL();
%>
<div style="width: 700px">
    <p>
        Short URLs allow you to create convenient, memorable links to specific content on your server.
        They are similar to TinyURL or bit.ly links. Users can access a short URL using a link like <%= h(exampleURL) %>
    </p>
    <p>
        If the server or port number are incorrect, you can correct the Base Server URL in <a href="<%= h(PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL()) %>">Site Settings</a>
    </p>
</div>

<labkey:form method="post">
    <table>
        <tr>
            <td class="labkey-form-label"><label for="shortURLTextField">Short URL</label><%= helpPopup("Short URL", "The unique name for this short URL")%></td>
            <td><input name="shortURL" id="shortURLTextField" size="40" /></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="targetURLTextField">Target URL</label><%= helpPopup("Target URL", "The URL on this server that will be the redirect target. The server portion of the URL will be stripped off - only the path portion will be retained")%></td>
            <td><input name="fullURL" id="targetURLTextField" size="40" /></td>
        </tr>
        <tr>
            <td></td>
            <td><%= button("Submit").submit(true) %></td>
        </tr>
    </table>
</labkey:form>
