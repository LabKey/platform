<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.pipeline.api.PipelineStatusFileImpl" %>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusFile" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PipelineStatusFileImpl> me = (JspView<PipelineStatusFileImpl>) HttpView.currentView();
    PipelineStatusFile bean = me.getModelBean();

    Container c = HttpView.currentContext().getContainer();

    String escalationUsers = StringUtils.defaultString(org.labkey.pipeline.api.PipelineEmailPreferences.get().getEscalationUsers(c));
    String[] users = escalationUsers.split(";");
    ActionURL cancelUrl = new ActionURL("Pipeline-Status", "details", c);

    String defaultSubject = "Re: failure for pipeline job: " + bean.getDescription();
%>
<script type="text/javascript">

    function updateControls(selection)
    {
        var escalationUser = document.getElementById("escalateUser");

        if (escalationUser)
        {
            escalationUser.disabled = selection.checked;
        }
    }
</script>

&nbsp;&nbsp;
<form action="escalate.view" method="post">
    <input type="hidden" name="detailsUrl" value="<%=cancelUrl.getURIString()%>"/>
    <table width="100%" class="normal">
        <tr class="wpHeader"><th class="wpTitle" colspan=2><b>Escalate Pipeline Job Failure</b></th></tr>
        <tr><td class="normal">Send an email message to a user or list of users regarding this pipeline job failure.</td></tr>
    </table>&nbsp;
    <table class="normal">
        <tr><td>Recipient:</td><td class="normal"><select id="escalateUser" name="escalateUser">
        <option></option>
<%
        for (String email : users)
        {
%>
        <option value="<%=email%>"><%=email%></option>
<%
        }
%>
        </select></td></tr>

        <tr><td></td><td class="normal"><input type=checkbox id="escalateAll" name="escalateAll" onclick="updateControls(this);">Send Escalation email to all users in the list</td></tr>

        <tr><td>Subject:</td><td class="normal" width="600"><input id="escalationSubject" name="escalationSubject" style="width:100%" value="<%=defaultSubject%>"></td></tr>
        <tr><td>Message:</td><td class="normal"><textarea id="escalationMessage" name="escalationMessage" style="width:100%" rows="10"></textarea></td></tr>
        <tr>
            <td></td><td><input type="image" src="<%=PageFlowUtil.buttonSrc("Send")%>">
            <%=buttonLink("Cancel", cancelUrl)%></td>    
        </tr>
    </table>
</form>
