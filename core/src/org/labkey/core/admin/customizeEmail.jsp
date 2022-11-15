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
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.collections.LabKeyCollectors" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplate" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplate.ContentType" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplate.ReplacementParam" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplateService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController.CustomEmailForm" %>
<%@ page import="org.labkey.core.admin.AdminController.CustomizeEmailAction" %>
<%@ page import="org.labkey.core.admin.AdminController.DeleteCustomEmailAction" %>
<%@ page import="java.util.Formatter" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }

    private JSONArray getReplacementJSON(List<ReplacementParam<?>> replacements)
    {
        return replacements.stream()
            .sorted()
            .map(param -> Map.of(
                "paramName", param.getName(),
                "format", param.getContentType().toString(),
                "valueType", param.getValueType().getSimpleName(),
                "paramDesc", param.getDescription(),
                "paramValue", param.getFormattedValue(getContainer(), null, ContentType.HTML)
            ))
            .collect(LabKeyCollectors.toJSONArray());
    }
%>
<%
    JspView<CustomEmailForm> me = (JspView<CustomEmailForm>) HttpView.currentView();
    CustomEmailForm bean = me.getModelBean();
    Container c = getContainer();

    List<EmailTemplate> emailTemplates = EmailTemplateService.get().getEditableEmailTemplates(c);
    String errorHTML = formatMissedErrorsStr("form");

    boolean showRootLookAndFeelLink = ContainerManager.getRoot().hasPermission(getUser(), AdminPermission.class);
    boolean showProjectSettingsLink = c.getProject() != null && c.getProject().hasPermission(getUser(), AdminPermission.class);
%>
<%=text(errorHTML)%>

<labkey:form action="<%=urlFor(CustomizeEmailAction.class)%>" method="post">
    <% if (bean.getReturnUrl() != null) { %>
    <%= generateReturnUrlFormField(bean.getReturnActionURL()) %>
    <% } %>
    <table class="lk-fields-table" width="85%">
        <tr><td class="labkey-form-label" style="width: 85px">Email Type:</td>
            <td><select id="templateClass" name="templateClass" onchange="changeEmailTemplate();">
<%
        for (EmailTemplate et : emailTemplates)
        {
%>
            <option value="<%=h(et.getClass().getName())%>"<%=selected(et.getClass().getName().equals(bean.getTemplateClass()))%>><%=h(et.getName())%></option>
<%
        }
%>
        </select></td></tr>
        <tr><td class="labkey-form-label">Description:</td><td><div id="emailDescription"><%=h(bean.getTemplateDescription())%></div></td><td></td></tr>
        <tr><td class="labkey-form-label">From Name:</td><td><input id="emailSender" name="emailSender" style="width:100%" value="<%=h(bean.getEmailSender())%>"></td><td></td></tr>
        <tr><td class="labkey-form-label">Reply To Email:</td><td><input id="emailReplyTo" name="emailReplyTo" style="width:100%" value="<%=h(bean.getEmailReplyTo())%>"></td><td></td></tr>
        <tr><td class="labkey-form-label">Subject:</td><td><input id="emailSubject" name="emailSubject" style="width:100%" value="<%=h(bean.getEmailSubject())%>"></td><td></td></tr>
        <tr><td class="labkey-form-label">Message:</td><td><textarea id="emailMessage" name="emailMessage" style="width:100%" rows="20"><%=h(bean.getEmailMessage())%></textarea></td></tr>
        <tr>
            <td></td><td>
            <%= button("Save").submit(true) %>
            <%= button("Cancel").href(bean.getReturnURLHelper(urlProvider(AdminUrls.class).getAdminConsoleURL())) %>
            <%= button("Reset to Default Template").submit(true).onClick("this.form.action=" + q(urlFor(DeleteCustomEmailAction.class)) + ";").id("siteResetButton").style("display: none;")%>
            <%= button("Delete " + getContainer().getContainerNoun() + "-Level Template").submit(true).onClick("this.form.action=" + q(urlFor(DeleteCustomEmailAction.class)) + ";").id("folderResetButton").style("display: none;")%>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td colspan="2"><hr></td></tr>
        <tr>
            <td align="justify" colspan="2">
                An email subject or message can contain a mix of static text and substitution parameters.
                A substitution parameter is inserted into the text when the email is generated. The syntax is:
                <pre style="padding-left: 2em;">^&lt;param name&gt;^</pre>
                where &lt;param name&gt; is the name of the substitution parameter shown below. For example:
                <pre style="padding-left: 2em;">^systemDescription^</pre>

                You may also supply an optional format string. If the value of the parameter is not blank, it
                will be used to format the value in the outgoing email. For the full set of format options available,
                see the <a target="_blank" href="<%= h(HelpTopic.getJDKJavaDocLink(Formatter.class)) %>" rel="noopener noreferrer">documentation for java.util.Formatter</a>. The syntax is:
                <pre style="padding-left: 2em;">^&lt;param name&gt;|&lt;format string&gt;^</pre>
                For example:
                <pre style="padding-left: 2em;">^currentDateTime|The current date is: %1$tb %1$te, %1$tY^</pre>
                <pre style="padding-left: 2em;">^siteShortName|The site short name is not blank and its value is: %s^</pre>
            </td>
        </tr>
        <tr id="helpMultipleContentTypes">
            <td align="justify" colspan="2">
                This template can be used for emails with both HTML and plain text variants. If you wish to include both
                in emails, use the delimiter
                <pre style="padding-left: 2em;"><%= h(EmailTemplate.BODY_PART_BOUNDARY) %></pre>
                to separate the sections of the template, with HTML first and plain text after. If no delimiter is
                found, the entire template will be assumed to be HTML.<br><br>
            </td>
        </tr>
        <tr><td colspan="2"><b>Standard Parameters</b></td></tr>
        <tr><td align="justify" colspan="2"><i>The values of many of these parameters can be configured on the site
            <% if (showRootLookAndFeelLink) { %>
            <a href="<%=h(urlProvider(AdminUrls.class).getSiteLookAndFeelSettingsURL())%>">Look and Feel Settings</a>
            <% } else { %>
            Look and Feel Settings
            <% } %>
            page and on the
            <% if (showProjectSettingsLink) { %>
            <a href="<%=h(urlProvider(AdminUrls.class).getProjectSettingsURL(c))%>">Project Settings</a>
            <% } else { %>
            Project Settings
            <% } %> page for each project</i>
        </tr>
        <tr><td colspan="2"><table id="standardReplacements" class="labkey-data-region-legacy labkey-show-borders"></table></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td colspan="2"><b>Custom Parameters</b></td></tr>
        <tr><td colspan="2"><table id="customReplacements" class="labkey-data-region-legacy labkey-show-borders"></table></td></tr>
    </table>
    <input id="emailDescriptionFF" type="hidden" name="templateDescription" value="<%=h(bean.getTemplateDescription())%>"/>
</labkey:form><br/><br/>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

<%
    // create an array of email templates with their replacement parameters
    JSONArray array = emailTemplates.stream()
        // Some values could be null, so Map.of() is not an option for the top-level map
        .map(et->PageFlowUtil.map(
            "name", et.getClass().getName(),
            "description", et.getDescription(),
            "sender", et.getSenderName(),
            "replyToEmail", et.getReplyToEmail(),
            "subject", et.getSubject(),
            "message", et.getBody(),
            // Let users delete the folder-scoped template only if it's been stored in the same folder they're in, and they're
            // not in the root where they'd be doing a site-level template
            "showFolderReset", (c.equals(et.getContainer()) && !c.isRoot()),
            // Let users delete a site-scoped template if they're in the root and the template is stored in the root
            "showSiteReset", (c.isRoot() && c.equals(et.getContainer())),
            "hasMultipleContentTypes", (et.getContentType() == ContentType.HTML),
            "standardReplacements", getReplacementJSON(et.getStandardReplacements()),
            "customReplacements", getReplacementJSON(et.getCustomReplacements())
        ))
        .collect(LabKeyCollectors.toJSONArray());
%>
    var emailTemplates = <%=json(array, 4)%>

    function changeEmailTemplate()
    {
        var selection = Ext4.get('templateClass').dom;
        var subject = Ext4.get('emailSubject').dom;
        var sender = Ext4.get('emailSender').dom;
        var replyTo = Ext4.get('emailReplyTo').dom;
        var message = Ext4.get('emailMessage').dom;
        var description = Ext4.get('emailDescription').dom;
        var descriptionFF = Ext4.get('emailDescriptionFF').dom;

        for (var i=0; i < this.emailTemplates.length; i++)
        {
            if (this.emailTemplates[i].name == selection.value)
            {
                subject.value = this.emailTemplates[i].subject;
                sender.value = this.emailTemplates[i].sender;
                replyTo.value = this.emailTemplates[i].replyToEmail;
                description.innerHTML = this.emailTemplates[i].description;
                descriptionFF.value = this.emailTemplates[i].description;
                message.value = this.emailTemplates[i].message;
                Ext4.get("siteResetButton").dom.style.display = this.emailTemplates[i].showSiteReset ? "" : "none";
                Ext4.get("folderResetButton").dom.style.display = this.emailTemplates[i].showFolderReset ? "" : "none";
                Ext4.get("helpMultipleContentTypes").dom.style.display = this.emailTemplates[i].hasMultipleContentTypes ? "" : "none";

                changeValidSubstitutions(this.emailTemplates[i]);
                return;
            }
        }
        subject.value = "";
        message.value = "";
        clearValidSubstitutions();
    }

    function clearValidSubstitutions()
    {
        for (const id of ['standardReplacements', 'customReplacements'])
        {
            var table = Ext4.get(id).dom;
            if (table != undefined) {
                // delete all rows first
                var count = table.rows.length;
                for (var i = 0; i < count; i++) {
                    table.deleteRow(0);
                }
            }
        }
    }

    function changeValidSubstitutions(record)
    {
        // delete all rows first
        clearValidSubstitutions();

        if (record == undefined || record.standardReplacements == undefined)
        {
            var selection = Ext4.get('templateClass').dom;
            for (var i=0; i < this.emailTemplates.length; i++)
            {
                if (this.emailTemplates[i].name == selection.value)
                {
                    record = this.emailTemplates[i];
                    break;
                }
            }
        }

        for (const ctx of [
                {id: 'standardReplacements', replacements: record.standardReplacements},
                {id: 'customReplacements', replacements: record.customReplacements},
            ])
        {
            var table = Ext4.get(ctx.id).dom;
            var row;
            var cell;

            row = table.insertRow(table.rows.length);
            cell = row.insertCell(0);
            cell.className = "labkey-column-header";
            cell.innerHTML = "Parameter Name";

            cell = row.insertCell(1);
            cell.className = "labkey-column-header";
            cell.innerHTML = "Type";

            cell = row.insertCell(2);
            cell.className = "labkey-column-header";
            cell.innerHTML = "Format";

            cell = row.insertCell(3);
            cell.className = "labkey-column-header";
            cell.innerHTML = "Description";

            cell = row.insertCell(4);
            cell.className = "labkey-column-header";
            cell.innerHTML = "Current Value";

            var replacements = ctx.replacements;

            if (replacements !== undefined) {
                for (var i = 0; i < replacements.length; i++) {
                    row = table.insertRow(table.rows.length);
                    row.className = i % 2 == 0 ? "labkey-alternate-row" : "labkey-row";

                    cell = row.insertCell(0);
                    cell.innerHTML = "<b>" + replacements[i].paramName + "</b>";

                    cell = row.insertCell(1);
                    cell.innerHTML = replacements[i].valueType;

                    cell = row.insertCell(2);
                    cell.innerHTML = replacements[i].format;

                    cell = row.insertCell(3);
                    cell.innerHTML = replacements[i].paramDesc;

                    cell = row.insertCell(4);
                    var paramValue = replacements[i].paramValue;
                    cell.innerHTML = paramValue != '' ? paramValue : "<em>not available in designer</em>";
                }
            }
        }
    }
<%
    if (StringUtils.isEmpty(errorHTML)) { %>
    Ext4.onReady(function()
    {
        if (LABKEY.ActionURL.getParameter('templateClass'))
        {
            Ext4.get('templateClass').dom.value = LABKEY.ActionURL.getParameter('templateClass');
        }
        changeEmailTemplate();
    });
<% } else { %>
    Ext4.onReady(changeValidSubstitutions);
<% } %>
</script>


