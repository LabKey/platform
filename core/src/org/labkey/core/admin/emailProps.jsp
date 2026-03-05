<%
/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.util.MailHelper" %>
<%@ page import="org.labkey.api.util.EmailTransportProvider" %>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    EmailTransportProvider activeProvider = MailHelper.getActiveProvider();
    Properties emailProps = activeProvider != null ? activeProvider.getProperties() : new Properties();
    String providerName = activeProvider != null ? activeProvider.getName() : "None";
    Set<String> obscuredProps = new CaseInsensitiveHashSet(
            "mail.smtp.user",
            "mail.smtp.password",
            "mail.graph.clientSecret"
    );
%>

<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label">Transport</td>
        <td><%=h(providerName)%></td>
    </tr>
    <% for(Object key : emailProps.keySet()) { %>
    <tr>
        <td class="labkey-form-label"><%=h(key.toString())%></td>
        <td><%=h(obscuredProps.contains(key.toString()) ? "********" : emailProps.get(key))%></td>
    </tr>
    <% } %>
</table>
