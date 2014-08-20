<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.specimen.settings.RequestNotificationSettings" %>
<%@ page import="org.labkey.study.specimen.settings.RequestNotificationSettings.DefaultEmailNotifyEnum" %>
<%@ page import="org.labkey.study.specimen.settings.RequestNotificationSettings.SpecimensAttachmentEnum" %>
<%@ page import="org.labkey.study.view.specimen.SpecimenRequestNotificationEmailTemplate" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

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
    JspView<RequestNotificationSettings> me = (JspView<RequestNotificationSettings>) HttpView.currentView();
    RequestNotificationSettings bean = me.getModelBean();
    Container container = getContainer();

    String completionURLPrefix = urlProvider(SecurityUrls.class).getCompleteUserURLPrefix(container);
    boolean newRequestNotifyChecked = ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) ?
            bean.isNewRequestNotifyCheckbox() : (h(bean.getNewRequestNotify()) != null &&
            h(bean.getNewRequestNotify()).compareTo("") != 0));
    boolean ccChecked = ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) ?
            bean.isCcCheckbox() : (h(bean.getCc()) != null && h(bean.getCc()).compareTo("") != 0));
    DefaultEmailNotifyEnum defaultEmailNotifyEnum = (bean.getDefaultEmailNotifyEnum());         // Checking getMethod bot needed because one of the radio buttons will always POST
    SpecimensAttachmentEnum specimensAttachmentEnum = (bean.getSpecimensAttachmentEnum());
%>

<style type="text/css">
    .local-text-block
    {
        border-top:1px solid;
        padding-top: 10px;
    }

    .local-left-label-width-th
    {
        width:175px;
    }
</style>

<labkey:errors/>

<labkey:form action="<%=h(urlFor(SpecimenController.ManageNotificationsAction.class))%>" method="POST">
    <table class="labkey-manage-display" width="90%">
        <tr>
            <td colspan="2" style="font-size: 14px;padding-bottom: 10px">The specimen request system sends emails as requested by the specimen administrator.
                Some properties of these email notifications can be configured here.</td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block">Notification emails will be sent from the specified reply-to address.
            This is the address that will receive replies and error messages, so it should be a monitored address.</td>
        </tr>
        <tr>
        <tr>
            <th align="right" rowspan="4" class="labkey-form-label local-left-label-width-th">Reply-to Address:</th>
            <td>Replies to specimen request notifications should go to:</td>
                <%
                    boolean replyToCurrentUser = RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(bean.getReplyTo());
                %>
        </tr>
        <tr>
            <td>
                <input type='radio' id='replyToCurrentUser' name='replyToCurrentUser' value='true'<%=checked(replyToCurrentUser)%>
                        onclick="document.getElementById('replyTo').value = '<%= h(RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE) %>'; setElementDisplayByCheckbox('replyToFixedUser', 'replyTo');">
                The administrator who generated each notification
            </td>
        </tr>
        <tr>
            <td>
                <input type='radio' id='replyToFixedUser'  name='replyToCurrentUser'  value='false'<%=checked(!replyToCurrentUser)%>
                        onclick="setElementDisplayByCheckbox('replyToFixedUser', 'replyTo'); document.getElementById('replyTo').value = '<%= text(!replyToCurrentUser ? h(bean.getReplyTo()) : "") %>';">
                A fixed email address:
            </td>
        </tr>
        <tr>
            <td>
                <input type="text" size="40" name="replyTo"
                       id='replyTo' value="<%= h(bean.getReplyTo()) %>"
                       style="display:<%= text(replyToCurrentUser ? "none" : "") %>">
            </td>
        </tr>

        <tr>
            <td colspan="2" class="local-text-block">Specimen request emails have a configurable template, which controls
                the subject line and body of the email.
                <% if (getContainer().hasPermission(getUser(), AdminPermission.class)) { %><%= textLink("Edit Email Template", PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeEmailURL(getContainer(), SpecimenRequestNotificationEmailTemplate.class, getActionURL()))%><% }
                else { %>You must have administrator permissions to edit the template.<% } %>
            </td>
        </tr>
        <tr>
            <td colspan="2"  class="local-text-block">All specimen request emails have the same subject line format. <b>%requestId%</b> may be used
                to insert the specimen request's study-specific ID number.  The default format for the subject line is:
                <b><%= h(StudyManager.getInstance().getStudy(container).getLabel()) %>: [Subject Suffix]</b>
            </td>
        </tr>
        <tr>
            <th class="labkey-form-label local-left-label-width-th" align="right">Subject Suffix:</th>
            <td>
                <input type="text" size="40" name="subjectSuffix" value="<%= h(bean.getSubjectSuffix()) %>">
            </td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block">Notification can be sent whenever a new specimen request is submitted.</td>
        </tr>
        <tr>
            <td colspan="2"><input type='checkbox' value='true' id='newRequestNotifyCheckbox'
                        name='newRequestNotifyCheckbox'
                        onclick="setElementDisplayByCheckbox('newRequestNotifyCheckbox', 'newRequestNotifyArea');"
                        <%=checked(newRequestNotifyChecked)%>>Send Notification of New Requests</td>
        </tr>
        <tr id="newRequestNotifyArea" style="display:<%= text(newRequestNotifyChecked ? "" : "none")%>">
            <th align="right" class="labkey-form-label local-left-label-width-th">Notify of new requests<br>(one per line):</th>
            <td>
                <labkey:autoCompleteTextArea name="newRequestNotify"
                                             id="newRequestNotify"
                                             url="<%=h(completionURLPrefix)%>"
                                             cols="30" rows="3"
                                             value="<%=h(bean.getNewRequestNotify())%>"/>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block">Email addresses listed under "always CC" will receive a single copy of each email notification.
                Please keep security issues in mind when adding users to this list.</td>
        </tr>
        <tr>
            <td colspan="2"><input type='checkbox' value='true' id='ccCheckbox'
                        name='ccCheckbox'
                        onclick="setElementDisplayByCheckbox('ccCheckbox', 'ccArea');"
                        <%=checked(ccChecked)%>>Always Send CC</td>
        </tr>

        <tr id="ccArea" style="display:<%= text(ccChecked ? "" : "none")%>">
            <th align="right"class="labkey-form-label local-left-label-width-th">Always CC<br>(one per line):</th>
            <td>
                <labkey:autoCompleteTextArea name="cc"
                                             id="cc"
                                             url="<%=h(completionURLPrefix)%>"
                                             cols="30" rows="3"
                                             value="<%=h(bean.getCc())%>"/>
            </td>
        </tr>

        <tr>
            <td colspan="2" class="local-text-block">Each request requirement notification email allows you to specify which actors will receive the email.
                The selection below indicates which actors will receive the email if the coordinator does not explicitly override.</td>
        </tr>
        <tr>
            <th align="right" rowspan="3" class="labkey-form-label local-left-label-width-th">Default Email Recipients:</th>
            <td><input type='radio' value='<%=DefaultEmailNotifyEnum.All%>'
                       name='defaultEmailNotify'
                       <%=checked(defaultEmailNotifyEnum == DefaultEmailNotifyEnum.All)%>>All</input>
            </td>
        </tr>
        <tr>
            <td><input type='radio' value='<%=DefaultEmailNotifyEnum.None%>'
                       name='defaultEmailNotify'
                       <%=checked(defaultEmailNotifyEnum == DefaultEmailNotifyEnum.None)%>>None</input>
            </td>
        </tr>
        <tr>
            <td><input type='radio' value='<%=DefaultEmailNotifyEnum.ActorsInvolved%>'
                       name='defaultEmailNotify'
                       <%=checked(defaultEmailNotifyEnum == DefaultEmailNotifyEnum.ActorsInvolved)%>>Notify Actors Involved</input>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block">In each notification email, a table of requested specimens can be included in the email body or as an attachment or not at all. To customize
            that table, create a custom view called SpecimenEmail on the SpecimenDetail table.</td>
        </tr>
        <tr>
            <th align="right" rowspan="4" class="labkey-form-label local-left-label-width-th">Include Requested Specimens Table:</th>
            <td><input type='radio' value='<%=SpecimensAttachmentEnum.InEmailBody%>'
                       name='specimensAttachment'
                <%=checked(specimensAttachmentEnum == SpecimensAttachmentEnum.InEmailBody)%>>In the email body</input>
            </td>
        </tr>
        <tr>
            <td><input type='radio' value='<%=SpecimensAttachmentEnum.ExcelAttachment%>'
                       name='specimensAttachment'
                <%=checked(specimensAttachmentEnum == SpecimensAttachmentEnum.ExcelAttachment)%>>As Excel attachment</input>
            </td>
        </tr>
        <tr>
            <td><input type='radio' value='<%=SpecimensAttachmentEnum.TextAttachment%>'
                       name='specimensAttachment'
                <%=checked(specimensAttachmentEnum == SpecimensAttachmentEnum.TextAttachment)%>>As text attachment</input>
            </td>
        </tr>
        <tr>
            <td><input type='radio' value='<%=SpecimensAttachmentEnum.Never%>'
                       name='specimensAttachment'
                <%=checked(specimensAttachmentEnum == SpecimensAttachmentEnum.Never)%>>Never</input>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block"></td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= button("Save").submit(true) %>&nbsp;
                <%= button("Cancel").href(new ActionURL(StudyController.ManageStudyAction.class, container)) %>
            </td>
        </tr>

    </table>
</labkey:form>



