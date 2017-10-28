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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.reports.Report"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.api.security.SecurityPolicy"%>
<%@ page import="org.labkey.api.security.SecurityPolicyManager"%>
<%@ page import="org.labkey.api.security.permissions.ReadPermission"%>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.RoleAssignment" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="java.util.ArrayList" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<Report> me = (JspView<Report>) HttpView.currentView();
    Report bean = me.getModelBean();

    Study study = StudyManager.getInstance().getStudy(getContainer());
    Container c = study != null ? study.getContainer() : getContainer();
    SecurityPolicy containerPolicy = c.getPolicy();
    SecurityPolicy reportPolicy = SecurityPolicyManager.getPolicy(bean.getDescriptor(), false);

    Container project = c.getProject();
    List<Group> globalGroups = SecurityManager.getGroups(null, false);
    List<Group> projectGroups = SecurityManager.getGroups(project, false);

    // see if the report SecurityPolicy has any individual user assignments
    List<User> reportSharedUsers = new ArrayList<>();
    for (RoleAssignment assignment : reportPolicy.getAssignments())
    {
        User user = UserManager.getUser(assignment.getUserId());
        if (user != null && assignment.getRole() != null && !reportSharedUsers.contains(user))
            reportSharedUsers.add(user);
    }
%>

<style type="text/css">
    table .labkey-form-label {
        white-space: nowrap;
    }
</style>

<script type="text/javascript">

    function setSelection(checked)
    {
        var form = document.getElementById("permissionsForm");
        var inputs = form.getElementsByTagName("INPUT");
        for (var i=0 ; i<inputs.length ; i++)
        {
            var input = inputs[i];
            // probably overkill, but double-check to ensure that the input is an enabled, visible checkbox:
            if (input.type == "checkbox" && !input.disabled && input.style.display == "block")
            {
                input.checked = checked;
            }
        }
    }

    function updateDisplay()
    {
        var form = document.getElementById("permissionsForm");
        var useCustomInput = document.getElementById("useCustom");
        var useCustom = useCustomInput.checked;
        var inputs = form.getElementsByTagName("INPUT");
        for (var i=0 ; i<inputs.length ; i++)
        {
            var input = inputs[i];
            if (input.type == "checkbox")
            {
                input.style.display = useCustom ? "block" : "none";
            }
        }

        var buttonDiv = document.getElementById("selectionButtons");
        buttonDiv.style.display = useCustom ? "inline" : "none";
    }

    Ext4.onReady(updateDisplay);

</script>

    <h3>
        <a href="<%=bean.getRunReportURL(getViewContext())%>"><%= h(bean.getDescriptor().getReportName()) %></a>
    </h3>

    <p>This page enables you to fine-tune permissions for this view.</p>
    <p>You can choose the default behavior as described.  Alternately, you can set custom permissions for each group. As always, if you don't have read permission on this folder, you don't get to see anything, regardless of any other settings.</p>

    <labkey:form id="permissionsForm" action="" method="POST">
    <table class="lk-fields-table">
        <tr><td colspan=2><input id=useDefault name=permissionType type=radio value="<%=org.labkey.study.controllers.security.SecurityController.PermissionType.defaultPermission%>"<%=checked(getPermissionType(bean) == SecurityController.PermissionType.defaultPermission)%> onclick="updateDisplay()"></td><td><b>Default</b> :
          this dynamic view will be readable only by users who have permission to see the source datasets</td></tr>
        <tr><td colspan=2><input id=useCustom name=permissionType type=radio value="<%=SecurityController.PermissionType.customPermission%>"<%=checked(getPermissionType(bean) == SecurityController.PermissionType.customPermission)%> onclick="updateDisplay()"></td><td><b>Custom</b> : set permissions per group / user
    <%
        if (isOwner(bean )) {
    %>
        <tr><td colspan=2><input id=usePrivate name=permissionType type=radio value="<%=SecurityController.PermissionType.privatePermission%>"<%=checked(getPermissionType(bean) == SecurityController.PermissionType.privatePermission)%> onclick="updateDisplay()"></td><td><b>Private</b> : this view is only visible to you
    <%
        }
    %>
    </table>
    <br/>
    <table class="labkey-data-region-legacy labkey-show-borders" style="width: 350px;">
        <tr>
            <th class="labkey-column-header" align=left>Site Groups</th>
            <th class="labkey-column-header" style="white-space: nowrap;">Read Access</th>
        </tr><%
        for (Group g : globalGroups)
        {
            if (g.isAdministrators())
            {
            %>
                <tr style="display: none;"><td><input type="hidden" name="group" value="<%=g.getUserId()%>"></td></tr>
            <%
            }
            //if (g.isAdministrators()) continue;
            boolean checked = reportPolicy.hasPermission(g, ReadPermission.class) || g.isAdministrators();
            boolean disabled = !containerPolicy.hasPermission(g, ReadPermission.class) || g.isAdministrators();
            %><tr class="labkey-row">
                <td><font color=<%=text(disabled ? "gray" : "black")%>><%=h(g.getName())%></font></td>
                <td height="22" width="20" align="center"><input name=group value="<%=g.getUserId()%>" type=checkbox<%=checked(checked)%><%=disabled(disabled)%>></td>
            </tr><%
        }
    %></table><%

        if (projectGroups.size() > 0)
        {
            %><br/>
            <table class="labkey-data-region-legacy labkey-show-borders" style="width: 350px;">
                <tr>
                    <th class="labkey-column-header" align=left>Project Groups</th>
                    <th class="labkey-column-header" style="white-space: nowrap;">Read Access</th>
                </tr><%
            for (Group g : projectGroups)
            {
                boolean checked = reportPolicy.hasPermission(g, ReadPermission.class);
                boolean disabled = !containerPolicy.hasPermission(g, ReadPermission.class);
                %><tr class="labkey-row">
                    <td><font color=<%=text(disabled?"gray":"black")%>><%=h(g.getName())%></font></td>
                    <td height=22 width="20" align="center"><input name=group value="<%=g.getUserId()%>" type=checkbox<%=checked(checked)%><%=disabled(disabled)%>></td>
                </tr><%
            }
            %></table><%
        }

        if (reportSharedUsers.size() > 0)
        {
            %><br/>
            <table class="labkey-data-region-legacy labkey-show-borders" style="width: 350px;">
                <tr>
                    <th class="labkey-column-header" align=left>Specific Users</th>
                    <th class="labkey-column-header" style="white-space: nowrap;">Read Access</th>
                </tr><%
            for (User u : reportSharedUsers)
            {
                boolean checked = reportPolicy.hasPermission(u, ReadPermission.class);
                boolean disabled = !containerPolicy.hasPermission(u, ReadPermission.class);
                %><tr class="labkey-row">
                    <td><font color=<%=text(disabled?"gray":"black")%>><%=h(u.getName())%></font></td>
                    <td height=22 width="20" align="center"><input name=user value="<%=u.getUserId()%>" type=checkbox<%=checked(checked)%><%=disabled(disabled)%>></td>
                </tr><%
            }
            %></table><%
        }
        %>
    <br/>
    <%= button("Save").submit(true) %>
    <span id="selectionButtons">
        <%= PageFlowUtil.button("Select All").href("#").onClick("setSelection(true); return false;") %>
        <%= PageFlowUtil.button("Select None").href("#").onClick("setSelection(false); return false;") %>
    </span>
    </labkey:form>&nbsp;

<table>
    <tr><td colspan="2">An enabled group indicates that the group already has READ access to the dataset (and to this view) through
        the project permissions. If a group is disabled, the group does not have READ access to the dataset
        and cannot be granted access through this view. If the checkbox is selected, the group has been given explicit
        access through this view.<br/><br/>For more information on study security, consult the main documentation:
        <ul>
            <li><%=helpLink("security", "LabKey Security Documentation")%></li>
            <li><%=helpLink("studySecurity", "Study Security Documentation")%></li>
        </ul>
</table>

<%!
    SecurityController.PermissionType getPermissionType(Report report)
    {
        if (!report.getDescriptor().isShared())
            return SecurityController.PermissionType.privatePermission;
        if (!SecurityPolicyManager.getPolicy(report.getDescriptor(), false).isEmpty())
            return SecurityController.PermissionType.customPermission;
        return SecurityController.PermissionType.defaultPermission;
    }

    boolean isOwner(Report report)
    {
        return report.canEdit(getUser(), getContainer());
    }
%>
