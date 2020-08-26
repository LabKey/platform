<%
/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.EmailTestForm> me = (JspView<AdminController.EmailTestForm>) HttpView.currentView();
    AdminController.EmailTestForm form = me.getModelBean();
%>

<p>Use the form below to test your server's email configuration. This will attempt to send an email
message to the address specified in the 'To' text box containing the content specified in the
'Body' text box.</p>

<% if (null != form.getException()) { %>
<div class="labkey-status-error">Your message could not be sent for the following reason(s):<br/>
<%=h(form.getException().getMessage())%>
</div>
<% }%>

<labkey:form action="<%=new ActionURL(AdminController.EmailTestAction.class, getContainer()).getLocalURIString()%>" method="POST">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label"><label for="emailTo">To</label></td>
            <td><input type="text" name="to" id="emailTo" value="<%=h(StringUtils.trimToEmpty(form.getTo()))%>" size="20" style="width: 100%"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="emailBody">Body</label></td>
            <td>
                <textarea rows="10" cols="80" name="body" id="emailBody"><%=h(StringUtils.trimToEmpty(form.getBody()))%></textarea>
            </td>
        </tr>
        <tr>
            <td colspan="2" style="text-align:right">
                <%= button("Cancel").href(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
                <%= button("Send").submit(true) %>
            </td>
        </tr>
    </table>
</labkey:form>