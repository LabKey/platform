<%
/*
 * Copyright (c) 2015 LabKey Corporation
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
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.cas.CasController.CasConfigureForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CasConfigureForm> me = (JspView<CasConfigureForm>)HttpView.currentView();
    CasConfigureForm form = me.getModelBean();
%>
<labkey:form action="configure.post" method="post">
    <table>
        <%=formatMissedErrorsInTable("form", 2)%>
        <tr>
            <td class="labkey-form-label">Apereo CAS Server URL<%= PageFlowUtil.helpPopup("Server URL", "Enter a valid HTTP URL to your Apereo CAS server. The URL should end with \"/cas\", for example: http://test.org/cas")%></td>
            <td><input type="text" name="serverUrl" size="50" value="<%=h(form.getServerUrl())%>"></td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr>
            <td colspan=2>
                <%= button("Save").submit(true) %>
                <%= button("Cancel").href(form.getReturnActionURL(urlProvider(LoginUrls.class).getConfigureURL()))%>
            </td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
    </table>
</labkey:form>