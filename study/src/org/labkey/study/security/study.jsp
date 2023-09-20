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
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.SecurityPolicy" %>
<%@ page import="org.labkey.api.security.SecurityPolicyManager" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadSomePermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController.SaveStudyPermissionsAction" %>
<%@ page import="org.labkey.study.model.GroupSecurityType" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="java.util.List" %>
<%@ page import="static org.labkey.api.util.PageFlowUtil.jsString" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    Pair<StudyImpl, ActionURL> pair = ((HttpView<Pair<StudyImpl, ActionURL>>) HttpView.currentView()).getModelBean();
    StudyImpl study = pair.first;
    ActionURL returnUrl = pair.second;
    boolean includeEditOption = study.getSecurityType() == SecurityType.ADVANCED_WRITE;
%>
Any user with READ access to this folder may view some summary data. However, access to detail data must be explicitly granted.
    <labkey:form id="groupUpdateForm" action="<%=urlFor(SaveStudyPermissionsAction.class)%>" onsubmit="LABKEY.setSubmit(true);" method="post">
<%
    if (returnUrl != null)
        out.print(generateReturnUrlFormField(returnUrl));
%>
    <table class="table-striped table-bordered labkey-data-region-header-lock" id="datasetSecurityGroupTable">
        <tr>
            <th style="min-width:200px;">&nbsp;</th>
            <% if (includeEditOption)
            {
            %><th style="min-width:120px; text-align:center;">EDIT&nbsp;ALL<%=helpPopup("EDIT ALL", "user/group may view and edit all rows in all datasets")%></th><%
            }
            %>
            <th style="min-width:120px; text-align:center;">READ&nbsp;ALL<%=helpPopup("READ ALL", "user/group may view all rows in all datasets")%></th>
            <th style="min-width:120px; text-align:center;">PER&nbsp;DATASET<%=helpPopup("PER DATASET", "user/group may view and/or edit rows in some datasets, configured per dataset")%></th>
            <th style="min-width:120px; text-align:center;">NONE<%=helpPopup("NONE", "user/group may not view or edit any detail data")%></th>
            <th style="min-width:24px; text-align:center;">&nbsp;</th>
        </tr>
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
        String warningShow = hasFolderRead ? "" : "document.getElementById(" + jsString(inputName+"$WARN") + ").style.display='inline';";
        String warningHide = hasFolderRead ? "" : "document.getElementById(" + jsString(inputName+"$WARN") + ").style.display='none';";
        %>
        <tr><td><%=h(name)%></td><%
        if (includeEditOption)
        {
        %><td style="text-align:center"><labkey:input onClick="<%=warningShow%>" formGroup="false" type="radio" name="<%=inputName%>" value="<%=h(GroupSecurityType.UPDATE_ALL.getParamName())%>" checked="<%=(GroupSecurityType.UPDATE_ALL == gt)%>" onChange="LABKEY.setDirty(true);"/></td><%
        }
        %>
        <td style="text-align:center"><labkey:input onClick="<%=warningShow%>" formGroup="false" type="radio" name="<%=inputName%>" value="<%=h(GroupSecurityType.READ_ALL.getParamName())%>" checked="<%=(GroupSecurityType.READ_ALL == gt)%>" onChange="LABKEY.setDirty(true);"/></td>
        <td style="text-align:center"><labkey:input onClick="<%=warningShow%>" formGroup="false" type="radio" name="<%=inputName%>" value="<%=h(GroupSecurityType.PER_DATASET.getParamName())%>" checked="<%=(GroupSecurityType.PER_DATASET == gt)%>" onChange="LABKEY.setDirty(true);"/></td>
        <td style="text-align:center"><labkey:input onClick="<%=warningHide%>" formGroup="false" type="radio" name="<%=inputName%>" value="<%=h(GroupSecurityType.NONE.getParamName())%>" checked="<%=(GroupSecurityType.NONE == gt)%>" onChange="LABKEY.setDirty(true);"/></td><%
        %><td style="text-align:center"><span id="<%=h(inputName+"$WARN")%>"><% if (!hasFolderRead && (hasReadAllPerm || hasReadSomePerm)) {%><img src="<%=getWebappURL("_images/exclaim.gif")%>" alt="This group does not have folder read permissions." title="This group does not have folder read permissions."><% } %></span></td><%
        %></tr><%
    }
    %></table>
    <%=button("Update").submit(true).id("groupUpdateButton")%>
    <%=button("Cancel").href(urlFor(ManageStudyAction.class)).onClick("LABKEY.setSubmit(true);")%>
    </labkey:form>