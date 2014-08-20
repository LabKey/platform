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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController.PickReferrerForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PickReferrerForm> me = (JspView<PickReferrerForm>)HttpView.currentView();
    PickReferrerForm bean = me.getModelBean();
%>
<labkey:form action="pickReferrer.post" method="post">
    <table>
        <tr><td colspan="2">Enter an optional URL prefix (e.g., https://www.foo.org).  If an unauthenticated user attempts to retrieve
        a protected page and the referring site starts with this prefix then the user's browser will redirect to the OpenSSO
        URL you've set.  Use this setting to cause links from a partner's site to automatically attempt authentication using OpenSSO.</td></tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr><td class="labkey-form-label">URL prefix</td><td><input type="text" name="prefix" value="<%=h(bean.getPrefix())%>" style="width:400px;"></td></tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr><td colspan="2">
            <%= button("Save").submit(true) %>
            <%= button("Cancel").href(OpenSSOController.getCurrentSettingsURL()) %>
        </td></tr>
    </table><br>
</labkey:form>