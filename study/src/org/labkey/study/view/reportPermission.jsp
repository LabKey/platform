<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.reports.Report"%>
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.study.controllers.security.SecurityController"%>
<%@ page import="org.labkey.study.model.Study"%>
<%@ page import="org.labkey.study.model.StudyManager"%>
<%@ page import="org.labkey.study.reports.AttachmentReport" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.security.*" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>


<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>

<%
    JspView<Report> me = (JspView<Report>) HttpView.currentView();
    Report bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    Study study = StudyManager.getInstance().getStudy(context.getContainer());
    Container c = study.getContainer();
    SecurityPolicy containerPolicy = c.getPolicy();
    SecurityPolicy reportPolicy = SecurityManager.getPolicy(bean.getDescriptor());

    boolean isAttachmentReport = bean.getDescriptor().getReportType().equals(AttachmentReport.TYPE);

    Container project = study.getContainer().getProject();
    Group[] globalGroups = SecurityManager.getGroups(null, false);
    Group[] projectGroups = SecurityManager.getGroups(project, false);
    List<User> projectUsers = SecurityManager.getProjectMembers(project, false);
    Map mapPrincipals = new HashMap();
    for (Group g : globalGroups)
        mapPrincipals.put(g.getUserId(), g);
    for (Group g : projectGroups)
        mapPrincipals.put(g.getUserId(), g);
    for (User g : projectUsers)
        mapPrincipals.put(g.getUserId(), g);
%>

<script type="text/javascript">

    function updateDisplay()
    {
        var form = document.getElementById("permissionsForm");
        var useExplicitInput = document.getElementById("useExplicit");
        var useExplicit = useExplicitInput.checked;
        var inputs = form.getElementsByTagName("INPUT");
        for (i=0 ; i<inputs.length ; i++)
        {
            var input = inputs[i];
            if (input.type == "checkbox")
            {
                input.style.display = useExplicit ? "block" : "none";
            }
        }
    }
    YAHOO.util.Event.addListener(window, "load", updateDisplay);
</script>

<h3><%= bean.getDescriptor().getReportName() %></h3>

    <p>This page provides for explicitly setting permissions to access this <%=isAttachmentReport ? "report" : "view"%>.</p>
    <p>You can choose the default behavior as described.  Alternately, you can explicitly set permissions
    group by group.  As always, if you don't have read permission on this folder, you don't get to see anything, regardless of any other settings.</p>

    <form id=permissionsForm action="" method=POST>
        <table>
        <tr><td colspan=2><input id=useDefault name=permissionType type=radio value="<%=org.labkey.study.controllers.security.SecurityController.PermissionType.defaultPermission%>" <%= getPermissionType(bean) == SecurityController.PermissionType.defaultPermission ? "checked" : ""%> onclick="updateDisplay()"></td><td><b>Default</b> :<%
            if (isAttachmentReport)
            {
            %> this static report will be readable by all users with access to this study<%
            }
            else
            {
            %> this dynamic view will be readable only by users who have permission to see the source datasets<%
            }
        %></td></tr>
        <tr><td colspan=2><input id=useExplicit name=permissionType type=radio value="<%=SecurityController.PermissionType.explicitPermission%>" <%= getPermissionType(bean) == SecurityController.PermissionType.explicitPermission ? "checked" : ""%> onclick="updateDisplay()"></td><td><b>Explicit</b> : set permissions per group
    <%
        if (isOwner(bean)) {
    %>
        <tr><td colspan=2><input id=usePrivate name=permissionType type=radio value="<%=SecurityController.PermissionType.privatePermission%>" <%= getPermissionType(bean) == SecurityController.PermissionType.privatePermission ? "checked" : ""%> onclick="updateDisplay()"></td><td><b>Private</b> : this view is only visible to you
    <%
        }
    %>
        </table>
        <table>
        <tr><th class=labkey-form-label colspan=2 align=left>Site groups</th></tr><%
        for (Group g : globalGroups)
        {
            if (g.isAdministrators())
            {
            %>
                <tr><td><input type="hidden" name="group" value="<%=g.getUserId()%>"></td></tr>
            <%
            }
            //if (g.isAdministrators()) continue;
            boolean checked = reportPolicy.hasPermission(g, ReadPermission.class) || g.isAdministrators();
            boolean disabled = !containerPolicy.hasPermission(g, ReadPermission.class) || g.isAdministrators();
            %><tr><td><font color=<%=disabled?"gray":"black"%>><%=g.getName()%></font></td><td height="22" width=20><input name=group value="<%=g.getUserId()%>" type=checkbox <%=checked?"checked":""%> <%=disabled?"disabled":""%>></td></tr><%
        }

        if (projectGroups.length > 0)
        {
            %><tr><th class=labkey-form-label colspan=2 align=left>Project groups</th></tr><%
        }
        for (Group g : projectGroups)
        {
            boolean checked = reportPolicy.hasPermission(g, ReadPermission.class);
            boolean disabled = !containerPolicy.hasPermission(g, ReadPermission.class);
            %><tr><td><font color=<%=disabled?"gray":"black"%>><%=g.getName()%></font></td><td height=22 width=20><input name=group value="<%=g.getUserId()%>" type=checkbox <%=checked?"checked":""%> <%=disabled?"disabled":""%>></td></tr><%
        }
        %>
    </table>
    <%=PageFlowUtil.generateSubmitButton("save")%>
<%--
    <input type=hidden name=reportId value="<%=bean.getDescriptor().getReportId()%>">
--%>
    </form>&nbsp;

<table>
    <tr><td colspan="2">An enabled group indicates that the group already has READ access to the dataset (and to this <%=isAttachmentReport ? "report" : "view"%>) through
        the project permissions. If a group is disabled, the group does not have READ access to the dataset
        and cannot be granted access through this view. If the checkbox is selected, the group has been given explicit
        access through this view.<br/><br/>For more information on study security, consult the main documentation:
        <ul>
            <li><a href="<%=new HelpTopic("security", HelpTopic.Area.SERVER).getHelpTopicLink()%>" target="_blank">LabKey Security Documentation</a></li>
            <li><a href="<%=new HelpTopic("studySecurity", HelpTopic.Area.STUDY).getHelpTopicLink()%>" target="_blank">Study Security Documentation</a></li>
        </ul>
</table>

<%!
    SecurityController.PermissionType getPermissionType(Report report)
    {
        if (report.getDescriptor().getOwner() != null)
            return SecurityController.PermissionType.privatePermission;
        if (!SecurityManager.getPolicy(report.getDescriptor(), false).isEmpty())
            return SecurityController.PermissionType.explicitPermission;
        return SecurityController.PermissionType.defaultPermission;
    }

    boolean isOwner(Report report)
    {
        return report.getDescriptor().canEdit(HttpView.currentContext());
    }
%>