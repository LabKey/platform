<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script type="text/javascript">LABKEY.requiresScript('completion.js');</script>
<script type="text/javascript">
function setElementDisplayByCheckbox(checkbox, element)
{
    var target = document.getElementById(element);
    if (document.getElementById(checkbox).checked)
        target.style.display = "";
    else
        target.style.display = "none";
  }
</script>

<%
    JspView<SampleManager.RequestNotificationSettings> me =
            (JspView<SampleManager.RequestNotificationSettings>) HttpView.currentView();
    SampleManager.RequestNotificationSettings bean = me.getModelBean();
    Container container = HttpView.getRootContext().getContainer();

    String completionURLPrefix = urlProvider(SecurityUrls.class).getCompleteUserURLPrefix(container);
    boolean newRequestNotifyChecked = ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) ?
            bean.isNewRequestNotifyCheckbox() : (h(bean.getNewRequestNotify()) != null &&
            h(bean.getNewRequestNotify()).compareTo("") != 0));
    boolean ccChecked = ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) ?
            bean.isCcCheckbox() : (h(bean.getCc()) != null && h(bean.getCc()).compareTo("") != 0));
%>

<%=PageFlowUtil.getStrutsError(request, "main")%>
<form action="handleUpdateNotifications.post" method="POST">
    <table class="labkey-manage-display" width="500">
        <tr>
            <td colspan="2">The specimen request system sends emails as requested by the specimen administrator.
                Some properties of these email notifications can be configured here.</td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-form-label">Notification emails will be sent from the specified reply-to address.
            This is the address that will receive replies and error messages, so it should be a monitored address.</td>
        </tr>
        <tr>
            <th align="right">Reply-to Address:</th>
            <td>
                Replies to specimen request notications should go to:<br>
                <%
                    boolean replyToCurrentUser = SampleManager.RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(bean.getReplyTo());
                %>
                <input type='radio' value='true' id='replyToCurrentUser' name='replyToCurrentUser' value='true' <%= replyToCurrentUser ? "CHECKED" : "" %>
                        onclick="document.getElementById('replyTo').value = '<%= h(SampleManager.RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE) %>'; setElementDisplayByCheckbox('replyToFixedUser', 'replyTo');">
                The administrator who generated each notification<br>
                <input type='radio' value='true' id='replyToFixedUser'  name='replyToCurrentUser'  value='false' <%= !replyToCurrentUser ? "CHECKED" : "" %>
                        onclick="setElementDisplayByCheckbox('replyToFixedUser', 'replyTo'); document.getElementById('replyTo').value = '<%= !replyToCurrentUser ? h(bean.getReplyTo()) : "" %>';">
                A fixed email address<br><br>
                <input type="text" size="40" name="replyTo"
                       id='replyTo' value="<%= h(bean.getReplyTo()) %>" 
                       style="display:<%= replyToCurrentUser ? "none" : "" %>">
            </td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-form-label">All specimen request emails have the same subject line.  <b>%requestId%</b> may be used
                to insert the specimen request's study-specific ID number.  The format for the subject line is:
                <b><%= h(StudyManager.getInstance().getStudy(container).getLabel()) %>: [Subject Suffix]</b>
            </td>
        </tr>
        <tr>
            <th align="right">Subject Suffix:</th>
            <td>
                <input type="text" size="40" name="subjectSuffix" value="<%= h(bean.getSubjectSuffix()) %>">
            </td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-form-label">Notification can be sent whenever a new specimen request is submitted.</td>
        </tr>
        <tr>
            <td colspan="2"><input type='checkbox' value='true' id='newRequestNotifyCheckbox'
                        name='newRequestNotifyCheckbox'
                        onclick="setElementDisplayByCheckbox('newRequestNotifyCheckbox', 'newRequestNotifyArea');"
                        <%= newRequestNotifyChecked ? " checked" : ""%>>Send Notification of New Requests</td>
        </tr>
        <tr id="newRequestNotifyArea" style="display:<%= newRequestNotifyChecked ? "" : "none"%>">
            <th align="right">Notify of new requests<br>(one per line):</th>
            <td>
                <textarea name="newRequestNotify" id="newRequestNotify" cols="30" rows="3"
                        onKeyDown="return ctrlKeyCheck(event);"
                        onBlur="hideCompletionDiv();"
                        autocomplete="off"
                        onKeyUp="return handleChange(this, event, '<%= completionURLPrefix %>');"><%= h(bean.getNewRequestNotify()) %></textarea>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-form-label">Email addresses listed under "always CC" will receive a single copy of each email notification.
                Please keep security issues in mind when adding users to this list.</td>
        </tr>
        <tr>
            <td colspan="2"><input type='checkbox' value='true' id='ccCheckbox'
                        name='ccCheckbox'
                        onclick="setElementDisplayByCheckbox('ccCheckbox', 'ccArea');"
                        <%= ccChecked ? " checked" : ""%>>Always Send CC</td>
        </tr>
        <tr id="ccArea" style="display:<%= ccChecked ? "" : "none"%>">
            <th align="right">Always CC<br>(one per line):</th>
            <td>
                <textarea name="cc" id="cc" cols="30" rows="3"
                        onKeyDown="return ctrlKeyCheck(event);"
                        onBlur="hideCompletionDiv();"
                        autocomplete="off"
                        onKeyUp="return handleChange(this, event, '<%= completionURLPrefix %>');"><%= h(bean.getCc() )%></textarea>
            </td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= generateSubmitButton("Save") %>&nbsp;
                <%= generateButton("Cancel", ActionURL.toPathString("Study", "manageStudy", container))%>
            </td>
        </tr>
    </table>
</form>
