<%@ page import="org.labkey.issue.IssuesController"%>
<%@ page import="org.labkey.issue.model.Issue"%>
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.issue.IssuePage" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    final Container c = context.getContainer();
    final String focusId = (0 == issue.getIssueId() ? "title" : "comment");
    int emailPrefs = IssueManager.getUserEmailPreferences(context.getContainer(), context.getUser().getUserId());
    final String popup = getNotifyHelpPopup(emailPrefs, issue.getIssueId());

    BindException errors = bean.getErrors();
%>

<form style="margin:0" method="POST" action="<%=IssuesController.issueURL(context.getContainer(),bean.getAction()+".post")%>">

    <table border=0 cellspacing=2 cellpadding=0>
    <%
        if (null != errors && 0 != errors.getErrorCount())
        {
            for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
            {
                %><tr><td colspan=3><font color="red" class="error"><%=h(context.getMessage(e))%></font></td></tr><%
            }
        }
    if (!StringUtils.isEmpty(bean.getRequiredFields()))
        out.print("<tr><td>Fields marked with an asterisk <span class=\"labkey-error\">*</span> are required.</td></tr>");
    %>
    </table>
    <table border=0 cellspacing=2 cellpadding=0><tr>
        <td><input name="<%=bean.getAction()%>" type="image" value="Submit" onClick="LABKEY.setSubmit(true); return true;" src="<%=ButtonServlet.buttonSrc("Submit")%>"></td>
        <td><%= buttonLink("View Grid", IssuesController.issueURL(context.getContainer(), "list").addParameter(DataRegion.LAST_FILTER_PARAM, "true"))%></td>
    </tr></table>

    <table width=640>
        <tr><td colspan=3><table><tr>
<%
            if (0 == issue.getIssueId())
            {
%>
                <td class="ms-searchform" width="69"><%=bean.getLabel("Title")%></td>
<%
            } else {
%>
                <td class="wpTitle"><%=issue.getIssueId()%></td>
<%
            }
%>
                <td class="normal" width="571">
                <%=bean.writeInput("title", issue.getTitle(), "id=title style=\"width:100%;\"")%>
                </td></tr>
            </table></td></tr>
        <tr>
            <td valign="top" width="34%"><table>
                <tr><td class="ms-searchform"><%=bean.getLabel("Status")%></td><td class="normal"><%=h(issue.getStatus())%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("AssignedTo")%></td><td class="normal"><%=bean.writeSelect("assignedTo", "" + issue.getAssignedTo(), issue.getAssignedToName(context), bean.getUserOptions(c, issue, context))%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("Type")%></td><td class="normal"><%=bean.writeSelect("type", issue.getType(), bean.getTypeOptions(c.getId()))%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("Area")%></td><td class="normal"><%=bean.writeSelect("area", issue.getArea(), bean.getAreaOptions(c.getId()))%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("Priority")%></td><td class="normal"><%=bean.writeSelect("priority", "" + bean._toString(issue.getPriority()), bean.getPriorityOptions(c))%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("Milestone")%></td><td class="normal"><%=bean.writeSelect("milestone", issue.getMilestone(), bean.getMilestoneOptions(c.getId()))%></td></tr>
            </table></td>
            <td valign="top" width="33%"><table>
                <tr><td class="ms-searchform"><%=bean.getLabel("Opened&nbsp;By")%></td><td class="normal"><%=h(issue.getCreatedByName(context))%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("Opened")%></td><td class="normal"><%=bean.writeDate(issue.getCreated())%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("ResolvedBy")%></td><td class="normal"><%=h(issue.getResolvedByName(context))%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("Resolved")%></td><td class="normal"><%=bean.writeDate(issue.getResolved())%></td></tr>
                <tr><td class="ms-searchform"><%=bean.getLabel("Resolution")%></td><td class="normal"><%=bean.writeSelect("resolution", issue.getResolution(), bean.getResolutionOptions(c))%></td></tr>
<%
            if (bean.isEditable("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {
%>
                <tr><td class="ms-searchform">Duplicate</td><td class="normal">
                <%=bean.writeInput("duplicate", null == issue.getDuplicate() ? null : issue.getDuplicate().toString())%>
                </td></tr>
<%
            }
%>
                <%=bean.writeCustomColumn(c.getId(), "int1", bean._toString(issue.getInt1()), IssuesController.ISSUE_NONE)%>
                <%=bean.writeCustomColumn(c.getId(), "int2", bean._toString(issue.getInt2()), IssuesController.ISSUE_NONE)%>
            </table></td>
            <td valign="top" width="33%"><table>
                <tr><td class="ms-searchform">Changed&nbsp;By</td><td class="normal"><%=h(issue.getModifiedByName(context))%></td></tr>
                <tr><td class="ms-searchform">Changed</td><td class="normal"><%=bean.writeDate(issue.getModified())%></td></tr>
                <tr><td class="ms-searchform">Closed&nbsp;By</td><td class="normal"><%=h(issue.getClosedByName(context))%></td></tr>
                <tr><td class="ms-searchform">Closed</td><td class="normal"><%=bean.writeDate(issue.getClosed())%></td></tr>
<%
            if (bean.isEditable("notifyList"))
            {
%>
                <tr>
                    <td class="ms-searchform"><%=bean.getLabel("NotifyList") + popup%><br/><br/>
<%
                    if (issue.getIssueId() == 0)
                    {
%>
                        <%= textLink("email&nbsp;prefs", IssuesController.issueURL(context.getContainer(), "emailPrefs"))%>
<%
                    } else {
%>
                        <%= textLink("email&nbsp;prefs", IssuesController.issueURL(context.getContainer(), "emailPrefs").addParameter("issueId", issue.getIssueId()))%>
<%
                    }
%>
                    </td>
                    <td class="normal"><%=bean.getNotifyList(c, issue)%></td>
                </tr>
<%
            } else {
%>
                <tr><td class="ms-searchform">Notify</td><td class="normal"><%=bean.getNotifyList(c, issue)%></td></tr>
<%
            }
%>
                <%=bean.writeCustomColumn(c.getId(), "string1", issue.getString1(), IssuesController.ISSUE_STRING1)%>
                <%=bean.writeCustomColumn(c.getId(), "string2", issue.getString2(), IssuesController.ISSUE_STRING2)%>
            </table></td>
        </tr>
    </table>
<%
    if (bean.getBody() != null)
    {
%>
    <textarea id="comment" name="comment" cols="150" rows="20" style="width:100%" onchange="LABKEY.setDirty(true);return true;"><%=PageFlowUtil.filter(bean.getBody())%></textarea>
<%
    } else {
%>
    <textarea id="comment" name="comment" cols="150" rows="20" style="width:100%" onchange="LABKEY.setDirty(true);return true;"></textarea>
<%
    }

    if (bean.getCallbackURL() != null)
    {
%>
    <input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/>
<%
    }

    for (Issue.Comment comment : issue.getComments())
    {
%>
        <hr><table width="100%"><tr><td align="left" class="normal"><b>
        <%=bean.writeDate(comment.getCreated())%>
        </b></td><td align="right" class="normal"><b>
        <%=h(comment.getCreatedByName(context))%>
        </b></td></tr></table>
        <%=comment.getComment()%>
<%
    }
%>
    <input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(issue)%>">
    <input type="hidden" name="action" value="<%=bean.getAction()%>">
</form>
<script type="text/javascript" for="window" event="onload">try {document.getElementById("<%=focusId%>").focus();} catch (x) {}</script>
<script type="text/javascript">

var origComment = byId("comment").value;
var origNotify = byId("notifyList").value;

function isDirty()
{
    var comment = byId("comment");
    if (comment && origComment != comment.value)
        return true;
    var notify = byId("notifyList");
    if (notify && origNotify != notify.value)
        return true;
    return false;
}

window.onbeforeunload = LABKEY.beforeunload(isDirty);
</script>

<%!
    String getNotifyHelpPopup(int emailPrefs, int issueId)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Email notifications can be controlled via either this notification list (one email address per line) ");
        sb.append("or your user <a href=\"emailPrefs.view");
        if (issueId != 0)
        {
            sb.append("?issueId=").append(issueId);
        }
        sb.append("\">email preferences</a>. ");
        if (emailPrefs != 0)
        {
            sb.append("Your current preferences to notify are:<br>");
            sb.append("<ul>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                sb.append("<li>when an issue is opened and assigned to me</li>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                sb.append("<li>when an issue that's assigned to me is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                sb.append("<li>when an issue I opened is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SELF_SPAM) != 0)
                sb.append("<li>when I enter/edit an issue</li>");
            sb.append("</ul>");
        }
        return PageFlowUtil.helpPopup("Email Notifications", sb.toString(), true);
    }
%>