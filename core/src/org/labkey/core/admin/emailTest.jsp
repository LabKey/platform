<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%
    JspView<AdminController.EmailTestForm> me = (JspView<AdminController.EmailTestForm>) HttpView.currentView();
    AdminController.EmailTestForm form = me.getModelBean();
%>

<p>Use the form below to test your server's email configuration. This will attempt to send an email
message to the address specified in the 'To' text box containing the content specificed in the
'Body' text box.</p>

<% if(null != form.getException()) { %>
<div class="labkey-status-error">Your message could not be sent for the following reason(s):<br/>
<%=PageFlowUtil.filter(form.getException().getMessage())%>
</div>
<% } else if(null != form.getTo()) { %>
<div class="labkey-status-info">Your email was successfully sent to <%=PageFlowUtil.filter(form.getTo())%>.</div>
<% } %>

<form action="<%=new ActionURL(AdminController.EmailTestAction.class, me.getViewContext().getContainer()).getLocalURIString()%>" method="POST"/>
<table>
    <tr>
        <td class="labkey-form-label">To</td>
        <td><input type="text" name="to" value="<%=PageFlowUtil.filter(StringUtils.trimToEmpty(form.getTo()))%>" size="20"/></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Body</td>
        <td>
            <textarea rows="20" cols="60" name="body"><%=PageFlowUtil.filter(StringUtils.trimToEmpty(form.getBody()))%></textarea>
        </td>
    </tr>
    <tr>
        <td colspan="2" style="text-align:right">
            <%=PageFlowUtil.generateSubmitButton("Send")%>
        </td>
    </tr>
</table>