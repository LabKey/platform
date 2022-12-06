<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.pipeline.PipelineController.ResetEmailNotificationAction" %>
<%@ page import="org.labkey.pipeline.PipelineController.UpdateEmailNotificationAction" %>
<%@ page import="org.labkey.pipeline.api.PipelineEmailPreferences" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }

    private HtmlString getTitle(String pref, Container c, String title)
    {
        if (PipelineEmailPreferences.get().isInherited(c, pref))
            return HtmlStringBuilder.of(HtmlString.unsafe("<span class=\"labkey-error\">*</span>&nbsp;")).append(title).getHtmlString();
        return HtmlString.of(title);
    }
%>
<%
    Container c = getContainer();
    boolean notifyOwnerOnSuccess = PipelineEmailPreferences.get().getNotifyOwnerOnSuccess(c);
    boolean notifyOwnerOnError = PipelineEmailPreferences.get().getNotifyOwnerOnError(c);
    String notifyUsersOnSuccess = StringUtils.defaultString(PipelineEmailPreferences.get().getNotifyUsersOnSuccess(c)).replaceAll(";", "\n");
    String notifyUsersOnError = StringUtils.defaultString(PipelineEmailPreferences.get().getNotifyUsersOnError(c)).replaceAll(";", "\n");
    String successNotifyInterval = StringUtils.defaultString(PipelineEmailPreferences.get().getSuccessNotificationInterval(c));
    String failureNotifyInterval = StringUtils.defaultString(PipelineEmailPreferences.get().getFailureNotificationInterval(c));
    String successNotifyStart = StringUtils.defaultString(PipelineEmailPreferences.get().getSuccessNotifyStart(c), "12:00");
    String failureNotifyStart = StringUtils.defaultString(PipelineEmailPreferences.get().getFailureNotifyStart(c), "12:00");

    HtmlString displaySuccess = h(notifyOwnerOnSuccess || !StringUtils.isEmpty(notifyUsersOnSuccess) ? "" : "none");
    HtmlString displayError = h(notifyOwnerOnError || !StringUtils.isEmpty(notifyUsersOnError) ? "" : "none");

    ActionURL completeUserUrl = urlProvider(SecurityUrls.class).getCompleteUserURL(c);
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    function updateControls(selection)
    {
        var notifyOnSuccess = document.getElementById("notifyOnSuccess");
        var notifyOnError = document.getElementById("notifyOnError");

        if (notifyOnSuccess && notifyOnSuccess.checked)
        {
            var notifyOwnerOnSuccess = document.getElementById("notifyOwnerOnSuccess");
        }

        if (notifyOnError && notifyOnError.checked)
        {
            var notifyOwnerOnError = document.getElementById("notifyOwnerOnError");
        }
        LABKEY.Utils.collapseExpand(selection, false);
    }

    function updateSuccessNotifyInterval()
    {
        var interval = document.getElementById('successNotifyInterval');
        var notifyStart = document.getElementById('successNotifyStart');
        if (interval.value == "0")
        {
            notifyStart.disabled = true;
            return;
        }
        notifyStart.disabled = false;
    }

    function updateFailureNotifyInterval()
    {
        var interval = document.getElementById('failureNotifyInterval');
        var notifyStart = document.getElementById('failureNotifyStart');
        if (interval.value == "0")
        {
            notifyStart.disabled = true;
            return;
        }
        notifyStart.disabled = false;
    }

    +function($) {
        $(function() {
            updateSuccessNotifyInterval();
            updateFailureNotifyInterval();
        });
    }(jQuery)
</script>

<labkey:form action="<%=urlFor(UpdateEmailNotificationAction.class)%>" method="post">
    <input type="hidden" name="<%=ActionURL.Param.returnUrl%>" value="<%= h(getViewContext().getActionURL())%>" />
    <table>
        <tr><td colspan=2>Check the appropriate box(es) to configure notification emails to be sent
            when a pipeline job succeeds and/or fails.<br/><%=text(c.isRoot() ? "" : "<span class=\"labkey-error\">*</span>&nbsp;Indicates that the field value has been inherited from the site wide configuration.")%>
        </td></tr>
    </table>
    <table>
        <tr>
            <td>
                <label>
                    <input type=checkbox id="notifyOnSuccess" name="notifyOnSuccess" onclick="return updateControls(this, false);"<%=checked(HtmlString.isBlank(displaySuccess))%>>Send email notifications if the pipeline job succeeds
                </label>
            </td>
        </tr>
        <tr style="display:<%=displaySuccess%>">
            <td style="padding-left: 20px;">
                <label>
                    <input value="true" type=checkbox id="notifyOwnerOnSuccess" name="notifyOwnerOnSuccess"<%=checked(notifyOwnerOnSuccess)%>><%=getTitle(PipelineEmailPreferences.PREF_NOTIFY_OWNER_ON_SUCCESS, c, "Send to owner")%>
                </label>
            </td>
        </tr>
        <tr style="display:<%=displaySuccess%>">
            <td style="padding-left: 20px;">
                <%=getTitle(PipelineEmailPreferences.PREF_NOTIFY_USERS_ON_SUCCESS, c, "Additional users to notify (enter one or more email addresses, each on its own line):")%>
            </td>
        </tr>
        <tr style="display:<%=displaySuccess%>">
            <td style="padding-left: 20px;">
                <labkey:autoCompleteTextArea
                    name="notifyUsersOnSuccess"
                    id="notifyUsersOnSuccess"
                    url="<%=completeUserUrl%>" rows="5" cols="60"
                    value="<%=notifyUsersOnSuccess%>"/>
            </td>
        </tr>
        <tr style="display:<%=displaySuccess%>">
            <td style="padding-left: 20px;">
                <%=getTitle(PipelineEmailPreferences.PREF_SUCCESS_INTERVAL, c, "Notification frequency:")%>&nbsp;
                <select id="successNotifyInterval" name="successNotifyInterval" onchange="updateSuccessNotifyInterval();">
                    <option value="0"<%=selected("0",successNotifyInterval)%>>every job</option>
                    <option value="1"<%=selected("1",successNotifyInterval)%>>1 hour</option>
                    <option value="2"<%=selected("2",successNotifyInterval)%>>2 hours</option>
                    <option value="3"<%=selected("3",successNotifyInterval)%>>3 hours</option>
                    <option value="4"<%=selected("4",successNotifyInterval)%>>4 hours</option>
                    <option value="5"<%=selected("5",successNotifyInterval)%>>5 hours</option>
                    <option value="6"<%=selected("6",successNotifyInterval)%>>6 hours</option>
                    <option value="12"<%=selected("12",successNotifyInterval)%>>12 hours</option>
                    <option value="24"<%=selected("24",successNotifyInterval)%>>24 hours</option>
                </select>&nbsp;&nbsp;
                <%=getTitle(PipelineEmailPreferences.PREF_SUCCESS_NOTIFY_START, c, "Starting at:")%><%=helpPopup("Notification start time", "Enter the starting time in 24-hour format (e.g., 0:30 for 12:30AM, 14:00 for 2:00PM).")%>&nbsp;<input type="text" name="successNotifyStart" id="successNotifyStart" value="<%=h(successNotifyStart)%>" size="4">
            </td>
        </tr>
        <tr style="display:<%=displaySuccess%>">
            <td>&nbsp;</td>
        </tr>
    </table>
    <table>
        <tr>
            <td>
                <label>
                    <input type=checkbox id="notifyOnError" name="notifyOnError" onclick="return updateControls(this, false);"<%=checked(HtmlString.isBlank(displayError))%>>Send email notification(s) if the pipeline job fails
                </label>
            </td>
        </tr>
        <tr style="display:<%=displayError%>">
            <td style="padding-left: 20px;">
                <label>
                    <input type=checkbox id="notifyOwnerOnError" name="notifyOwnerOnError"<%=checked(notifyOwnerOnError)%>><%=getTitle(PipelineEmailPreferences.PREF_NOTIFY_OWNER_ON_ERROR, c, "Send to owner")%>
                </label>
            </td>
        </tr>
        <tr style="display:<%=displayError%>">
            <td style="padding-left: 20px;">
                <%=getTitle(PipelineEmailPreferences.PREF_NOTIFY_USERS_ON_ERROR, c, "Additional users to notify (enter one or more email addresses, each on its own line):")%>
            </td>
        </tr>
        <tr style="display:<%=displayError%>">
            <td style="padding-left: 20px;">
                <labkey:autoCompleteTextArea
                    name="notifyUsersOnError"
                    id="notifyUsersOnError"
                    url="<%=completeUserUrl%>" rows="5" cols="60"
                    value="<%=notifyUsersOnError%>"/>
            </td>
        </tr>
        <tr style="display:<%=displayError%>">
            <td style="padding-left: 20px;">
                <%=getTitle(PipelineEmailPreferences.PREF_FAILURE_INTERVAL, c, "Notification frequency:")%>&nbsp;
                <select id="failureNotifyInterval" name="failureNotifyInterval" onchange="updateFailureNotifyInterval();">
                    <option value="0"<%=selected("0".equals(failureNotifyInterval))%>>every job</option>
                    <option value="1"<%=selected("1".equals(failureNotifyInterval))%>>1 hour</option>
                    <option value="2"<%=selected("2".equals(failureNotifyInterval))%>>2 hours</option>
                    <option value="3"<%=selected("3".equals(failureNotifyInterval))%>>3 hours</option>
                    <option value="4"<%=selected("4".equals(failureNotifyInterval))%>>4 hours</option>
                    <option value="5"<%=selected("5".equals(failureNotifyInterval))%>>5 hours</option>
                    <option value="6"<%=selected("6".equals(failureNotifyInterval))%>>6 hours</option>
                    <option value="12"<%=selected("12".equals(failureNotifyInterval))%>>12 hours</option>
                    <option value="24"<%=selected("24".equals(failureNotifyInterval))%>>24 hours</option>
                </select>&nbsp;&nbsp;
                <%=getTitle(PipelineEmailPreferences.PREF_FAILURE_NOTIFY_START, c, "Starting at:")%><%=helpPopup("Notification start time", "Enter the starting time in 24-hour format (e.g., 0:30 for 12:30AM, 14:00 for 2:00PM).")%>&nbsp;<input type="text" name="failureNotifyStart" id="failureNotifyStart" value="<%=h(failureNotifyStart)%>" size="4">
            </td>
        </tr>
        <tr style="display:<%=displayError%>">
            <td>&nbsp;</td>
        </tr>
    </table>
    <%= button("Update").submit(true) %>
    <%= button("Reset to Default").submit(true).onClick("this.form.action=" + q(urlFor(ResetEmailNotificationAction.class)) + ";") %></td>
</labkey:form>
