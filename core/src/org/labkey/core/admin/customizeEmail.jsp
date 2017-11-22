<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplate" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplate.ContentType" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplateService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Formatter" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<AdminController.CustomEmailForm> me = (JspView<AdminController.CustomEmailForm>) HttpView.currentView();
    AdminController.CustomEmailForm bean = me.getModelBean();
    Container c = getContainer();

    List<EmailTemplate> emailTemplates = EmailTemplateService.get().getEditableEmailTemplates(c);
    String errorHTML = formatMissedErrorsStr("form");
%>
<%=text(errorHTML)%>

<labkey:form action="<%=h(buildURL(AdminController.CustomizeEmailAction.class))%>" method="post">
    <% if (bean.getReturnUrl() != null) { %>
        <input type="hidden" name="returnUrl" value="<%= h(bean.getReturnUrl()) %>" />
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
            <%= button("Reset to Default Template").submit(true).onClick("this.form.action=" + qh(buildURL(AdminController.DeleteCustomEmailAction.class)) + ";").attributes("id='siteResetButton' style='display: none;'")%>
            <%= button("Delete " + getContainer().getContainerNoun() + "-Level Template").submit(true).onClick("this.form.action=" + qh(buildURL(AdminController.DeleteCustomEmailAction.class)) + ";").attributes("id='folderResetButton' style='display: none;'")%>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td colspan="2"><hr></td></tr>
        <tr>
            <td align="justify" colspan="2">
                An email subject or message can contain a mix of static text and substitution parameters.
                A substitution parameter is inserted into the text when the email is generated. The syntax is:
                <pre>^&lt;param name&gt;^</pre>
                where &lt;param name&gt; is the name of the substitution parameter shown below. For example:
                <pre>^systemDescription^</pre>

                You may also supply an optional format string. If the value of the parameter is not blank, it
                will be used to format the value in the outgoing email. For the full set of format options available,
                see the <a target="_blank" href="<%= h(HelpTopic.getJDKJavaDocLink(Formatter.class)) %>">documentation for java.util.Formatter</a>. The syntax is:
                <pre>^&lt;param name&gt;|&lt;format string&gt;^</pre>
                For example:
                <pre>^currentDateTime|The current date is: %1$tb %1$te, %1$tY^</pre>
                <pre>^siteShortName|The site short name is not blank and its value is: %s^</pre>
            </td>
        </tr>
        <tr><td colspan="2"><hr></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td colspan="2"><table id="validSubstitutions" class="labkey-data-region-legacy labkey-show-borders"></table></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td align="justify" colspan="2"><i>The values of many of these parameters can be configured on
            the <a href="<%=urlProvider(AdminUrls.class).getProjectSettingsURL(c)%>">Look and Feel Settings page</a> and on the Project Settings page for each project.</i>
        </tr>
    </table>
    <input id="emailDescriptionFF" type="hidden" name="templateDescription" value="<%=h(bean.getTemplateDescription())%>"/>
</labkey:form><br/><br/>

<script type="text/javascript">

<%
    // create the map of email template names to template properties
    out.write("var emailTemplates = [");
    String sep = "{";
    for (EmailTemplate et : emailTemplates)
    {
        out.write(sep);
        out.write("\t\"name\":\"" + et.getClass().getName() + "\",\n");
        out.write("\t\"description\":" + PageFlowUtil.jsString(et.getDescription()) + ",\n");
        out.write("\t\"sender\":" + PageFlowUtil.jsString(et.getSenderName()) + ",\n");
        out.write("\t\"replyToEmail\":" + PageFlowUtil.jsString(et.getReplyToEmail()) + ",\n");
        out.write("\t\"subject\":" + PageFlowUtil.jsString(et.getSubject()) + ",\n");
        out.write("\t\"message\":" + PageFlowUtil.jsString(et.getBody()) + ",\n");
        // Let users delete the folder-scoped template only if it's been stored in the same folder they're in, and they're
        // not in the root where they'd be doing a site-level template
        out.write("\t\"showFolderReset\":" + (c.equals(et.getContainer()) && !c.isRoot()) + ",\n");
        // Let users delete a site-scoped template if they're in the root and the template is stored in the root
        out.write("\t\"showSiteReset\":" + (c.isRoot() && c.equals(et.getContainer())) + ",\n");
        out.write("\t\"replacements\":[\n");

        String innerSep = "\t{";
        List<EmailTemplate.ReplacementParam> replacements = new ArrayList<>(et.getValidReplacements());
        // Alphabetize for easier reference in UI
        Collections.sort(replacements);
        for (EmailTemplate.ReplacementParam param : replacements)
        {
            out.write(innerSep);
            out.write("\t\t\"paramName\":" + PageFlowUtil.jsString(param.getName()) + ",\n");
            out.write("\t\t\"valueType\":" + PageFlowUtil.jsString(param.getValueType().getSimpleName()) + ",\n");
            out.write("\t\t\"paramDesc\":" + PageFlowUtil.jsString(param.getDescription()) + ",\n");
            String formattedValue = param.getFormattedValue(c, null, ContentType.HTML);
            out.write("\t\t\"paramValue\":" + PageFlowUtil.jsString(formattedValue) + "\n");
            out.write("}");

            innerSep = "\t,{";
        }
        out.write("]}");

        sep = ",{";
    }
    out.write("];");

%>
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
        var table = Ext4.get('validSubstitutions').dom;
        if (table != undefined)
        {
            // delete all rows first
            var count = table.rows.length;
            for (var i = 0; i < count; i++)
            {
                table.deleteRow(0);
            }
        }
    }

    function changeValidSubstitutions(record)
    {
        // delete all rows first
        clearValidSubstitutions();

        if (record == undefined || record.replacements == undefined)
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
        var table = Ext4.get('validSubstitutions').dom;
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
        cell.innerHTML = "Description";

        cell = row.insertCell(3);
        cell.className = "labkey-column-header";
        cell.innerHTML = "Current Value";

        if (record && record.replacements != undefined)
        {
            for (var i = 0; i < record.replacements.length; i++)
            {
                row = table.insertRow(table.rows.length);
                row.className = i % 2 == 0 ? "labkey-alternate-row" : "labkey-row";

                cell = row.insertCell(0);
                cell.innerHTML = "<b>" + record.replacements[i].paramName + "</b>";

                cell = row.insertCell(1);
                cell.innerHTML = record.replacements[i].valueType;

                cell = row.insertCell(2);
                cell.innerHTML = record.replacements[i].paramDesc;

                cell = row.insertCell(3);
                var paramValue = record.replacements[i].paramValue;
                cell.innerHTML = paramValue != '' ? paramValue : "<em>not available in designer</em>";
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


