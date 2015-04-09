<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController.ConfigProperties" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ConfigProperties> me = (JspView<ConfigProperties>)HttpView.currentView();
    ConfigProperties bean = me.getModelBean();
%>
<table>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr><td colspan="2"><%=textLink("Enter a referrer URL prefix that will automatically redirect to the OpenSSO link", bean.pickRefererPrefixURL)%></td></tr>
    <tr><td colspan="2">&nbsp;</td></tr><%

    for (String key : bean.props.keySet())
    {
        String value = bean.props.get(key);
%>
<tr><td class="labkey-form-label"><%=key%></td><td><%=value%></td></tr><%
    }
%>
</table><br>
<%= button("Modify Settings").href(OpenSSOController.getConfigureURL()) %>
<%= button("Done").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
