<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplate" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplateService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminControllerSpring" %>
<%
    JspView<AdminControllerSpring.CustomEmailForm> me = (JspView<AdminControllerSpring.CustomEmailForm>) HttpView.currentView();
    AdminControllerSpring.CustomEmailForm bean = me.getModelBean();

    EmailTemplate[] emailTemplates = EmailTemplateService.get().getEmailTemplates();
    String errors = PageFlowUtil.getStrutsError(request, "main");
%>
<span class="labkey-error"><%=errors%></span>

<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>
<script type="text/javascript">

<%
    // create the map of email template names to template properties
    out.write("var emailTemplates = [");
    String sep = "{";
    for (org.labkey.api.util.emailTemplate.EmailTemplate et : emailTemplates)
    {
        out.write(sep);
        out.write("\"name\":\"" + et.getClass().getName() + "\",");
        out.write("\"description\":" + PageFlowUtil.jsString(et.getDescription()) + ",");
        out.write("\"subject\":" + PageFlowUtil.jsString(et.getSubject()) + ",");
        out.write("\"message\":" + PageFlowUtil.jsString(et.getBody()) + ",");
        out.write("\"replacements\":[");

        String innerSep = "{";
        for (EmailTemplate.ReplacementParam param : et.getValidReplacements())
        {
            out.write(innerSep);
            out.write("\"paramName\":" + PageFlowUtil.jsString(param.getName()) + ",");
            out.write("\"paramDesc\":" + PageFlowUtil.jsString(param.getDescription()) + ",");
            out.write("\"paramValue\":" + PageFlowUtil.jsString("&nbsp;" + StringUtils.trimToEmpty(param.getValue())));
            out.write("}");

            innerSep = ",{";
        }
        out.write("]}");

        sep = ",{";
    }
    out.write("];");

%>
    function changeEmailTemplate()
    {
        var selection = YAHOO.util.Dom.get('templateClass');
        var subject = YAHOO.util.Dom.get('emailSubject');
        var message = YAHOO.util.Dom.get('emailMessage')
        var description = YAHOO.util.Dom.get('emailDescription')

        for (var i=0; i < this.emailTemplates.length; i++)
        {
            if (this.emailTemplates[i].name == selection.value)
            {
                subject.value = this.emailTemplates[i].subject;
                description.innerHTML = this.emailTemplates[i].description;
                message.value = this.emailTemplates[i].message;

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
        var table = YAHOO.util.Dom.get('validSubstitutions');
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

        if (record.replacements == undefined)
        {
            var selection = YAHOO.util.Dom.get('templateClass');
            for (var i=0; i < this.emailTemplates.length; i++)
            {
                if (this.emailTemplates[i].name == selection.value)
                {
                    record = this.emailTemplates[i];
                    break;
                }
            }
        }
        var table = YAHOO.util.Dom.get('validSubstitutions');
        var row;
        var cell;

        if (record.replacements != undefined)
        {
            for (var i = 0; i < record.replacements.length; i++)
            {
                row = table.insertRow(table.rows.length);
                cell = row.insertCell(0);
                cell.className = "ms-searchform";
                cell.innerHTML = record.replacements[i].paramName;

                cell = row.insertCell(1);
                cell.innerHTML = record.replacements[i].paramDesc;

                cell = row.insertCell(2);
                cell.innerHTML = record.replacements[i].paramValue;
            }
        }
    }
<%
    if (StringUtils.isEmpty(errors))
        out.write("YAHOO.util.Event.addListener(window, \"load\", changeEmailTemplate)");
    else
        out.write("YAHOO.util.Event.addListener(window, \"load\", changeValidSubstitutions)");
%>
</script>

<form action="customizeEmail.view" method="post">
    <table class="normal">
        <tr class="wpHeader"><th colspan=2 align=center>Custom Emails</th></tr>
        <tr><td class=normal colspan=2>Customize user emails:</td></tr>
        <tr><td></td></tr>
        <tr><td>Email Type:</td>
            <td><select id="templateClass" name="templateClass" onchange="changeEmailTemplate();">
<%
        for (EmailTemplate et : emailTemplates)
        {
%>
            <option value="<%=et.getClass().getName()%>" <%=et.getClass().getName().equals(bean.getTemplateClass()) ? "selected" : ""%>><%=et.getName()%></option>
<%
        }
%>
        </select></td></tr>
        <tr><td>Description:</td><td class="normal" width="600"><div id="emailDescription"></div></td><td></td></tr>
        <tr><td>Subject:</td><td class="normal" width="600"><input id="emailSubject" name="emailSubject" style="width:100%" value="<%=bean.getEmailSubject()%>"></td><td></td></tr>
        <tr><td>Message:</td><td class="normal"><textarea id="emailMessage" name="emailMessage" style="width:100%" rows="20"><%=bean.getEmailMessage()%></textarea></td></tr>
        <tr>
            <td></td><td>
            <%=PageFlowUtil.buttonLink("Cancel", new ActionURL("admin", "begin.view", ""))%>&nbsp;
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Reset to Default")%>" onclick="this.form.action='deleteCustomEmail.view'">&nbsp;
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>">&nbsp;
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><i>An email subject or message can contain a mixture of static text and substitution parameters.
            A substitution parameter is inserted into the text when the email is generated. The syntax of a
            substitution param is : %&lt;param name&gt;% where &lt;param name&gt; is the name of the substitution
            param.<br/><br/>
            The list of valid substitutions for this email type is (current values appear to the right, although
            some are not known until the email is generated):</i></td>
        </tr>
        <tr><td></td><td><table id="validSubstitutions"></table></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><i>The values of many of these parameters can be configured on
            the <a href="<%=new ActionURL("admin", "showCustomizeSite", "")%>">Site Settings Page</a>.</i>
        </tr>
    </table>
</form><br/><br/>

