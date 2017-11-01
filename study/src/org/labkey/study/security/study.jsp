<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.SecurityPolicy" %>
<%@ page import="org.labkey.api.security.SecurityPolicyManager" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadSomePermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.GroupSecurityType" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    Pair<StudyImpl, ActionURL> pair = ((HttpView<Pair<StudyImpl, ActionURL>>) HttpView.currentView()).getModelBean();
    StudyImpl study = pair.first;
    ActionURL returnUrl = pair.second;
    boolean includeEditOption = study.getSecurityType() == SecurityType.ADVANCED_WRITE;
%>
Any user with READ access to this folder may view some summary data.  However, access to detail data must be explicitly granted.
    <labkey:form id="groupUpdateForm" action="<%=h(buildURL(SecurityController.SaveStudyPermissionsAction.class))%>" method="post">
<%
    if (returnUrl != null)
        out.write("<input type=\"hidden\" name=\"returnUrl\" value=\"" + h(returnUrl) + "\">");
%>
    <table class="lk-fields-table">

        <tr>
            <th>&nbsp;</th>
            <% if (includeEditOption)
            {
            %><th width=100>EDIT&nbsp;ALL<%=PageFlowUtil.helpPopup("EDIT ALL", "user/group may view and edit all rows in all datasets")%></th><%
            }
            %>
            <th width=100>READ&nbsp;ALL<%=PageFlowUtil.helpPopup("READ ALL", "user/group may view all rows in all datasets")%></th>
            <th width=100>PER&nbsp;DATASET<%=PageFlowUtil.helpPopup("PER DATASET", "user/group may view and/or edit rows in some datasets, configured per dataset")%></th>
            <th width=100>NONE<%=PageFlowUtil.helpPopup("NONE", "user/group may not view or edit any detail data")%></th></tr>
    <%
    SecurityPolicy folderPolicy = getContainer().getPolicy();
    SecurityPolicy studyPolicy = SecurityPolicyManager.getPolicy(study);
    List<Group> groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
    for (Group group : groups)
    {
        if (group.getUserId() == Group.groupAdministrators)
            continue;
        String name = group.getName();
        if (group.getUserId() == Group.groupUsers)
            name = "All site users";
        boolean hasFolderRead = folderPolicy.hasPermission(group, ReadPermission.class);
        boolean hasUpdatePerm = studyPolicy.hasNonInheritedPermission(group, UpdatePermission.class);
        boolean hasReadSomePerm = studyPolicy.hasNonInheritedPermission(group, ReadSomePermission.class);
        boolean hasReadAllPerm = (!hasUpdatePerm) && studyPolicy.hasNonInheritedPermission(group, ReadPermission.class);
        if (!includeEditOption && hasUpdatePerm)
            hasReadAllPerm = true;
        GroupSecurityType gt = GroupSecurityType.getTypeForGroup(group, study);
        String inputName = "group." + group.getUserId();
        String warning = hasFolderRead ? "" : "onclick=\"document.getElementById('" + inputName + "$WARN').style.display='inline';\"";
        String clear = hasFolderRead ? "" : "onclick=\"document.getElementById('" + inputName + "$WARN').style.display='none';\"";
        %><tr><td><%=h(name)%></td><%
        if (includeEditOption)
        {
        %><th><input <%=h(warning)%> type=radio name="<%=h(inputName)%>" value="<%=h(GroupSecurityType.UPDATE_ALL.getParamName())%>"<%=checked(GroupSecurityType.UPDATE_ALL == gt)%>></th><%
        }
        %>
        <th><input <%=h(warning)%> type=radio name="<%=h(inputName)%>" value="<%=h(GroupSecurityType.READ_ALL.getParamName())%>"<%=checked(GroupSecurityType.READ_ALL == gt)%>></th>
        <th><input <%=h(warning)%> type=radio name="<%=h(inputName)%>" value="<%=h(GroupSecurityType.PER_DATASET.getParamName())%>"<%=checked(GroupSecurityType.PER_DATASET == gt)%>></th>
        <th><input <%=h(clear)%> type=radio name="<%=h(inputName)%>" value="<%=h(GroupSecurityType.NONE.getParamName())%>"<%=checked(GroupSecurityType.NONE == gt)%>></th><%
        %><td id="<%=h(inputName)%>$WARN" style="display:<%=h(!hasFolderRead && (hasReadAllPerm || hasReadSomePerm)?"inline":"none")%>;"><img src="<%=getContextPath()%>/_images/exclaim.gif" alt="group does not have folder read permissions" title="group does not have folder read permissions"></td><%
        %></tr><%
    }
    %></table>
    <%= button("Update").submit(true).attributes("id=\"groupUpdateButton\"") %>
    </labkey:form>