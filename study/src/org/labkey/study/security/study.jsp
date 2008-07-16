<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.model.Study"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    HttpView<Study> me = (HttpView<Study>) HttpView.currentView();
    String contextPath = me.getViewContext().getContextPath();
    Study study = me.getModelBean();
%>
Any user with READ access to this folder may view some summary data.  However, access to detail data must be explicitly granted.
    <form id="groupUpdateForm" action="saveStudyPermissions.post" method="post">
<%
    String redir = (String)HttpView.currentContext().get("redirect");
    if (redir != null)
        out.write("<input type=\"hidden\" name=\"redirect\" value=\"" + h(redir) + "\">");
%>        
    <table>
        
        <tr>
            <th>&nbsp;</th>
            <th width=100>WRITE&nbsp;ALL<%=PageFlowUtil.helpPopup("WRITE ALL","user/group may view and edit all rows in all datasets")%></th>
            <th width=100>READ&nbsp;ALL<%=PageFlowUtil.helpPopup("READ ALL","user/group may view all rows in all datasets")%></th>
            <th width=100>PER&nbsp;DATASET<%=PageFlowUtil.helpPopup("PER DATASET","user/group may view and/or edit rows in some datasets, configured per dataset")%></th>
            <th width=100>NONE<%=PageFlowUtil.helpPopup("NONE","user/group may not view or edit any detail data")%></th></tr>
    <%
    ACL folderACL = me.getViewContext().getContainer().getAcl();
    ACL studyACL = study.getACL();
    Group[] groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
    for (Group group : groups)
    {
        if (group.getUserId() == Group.groupAdministrators)
            continue;
        String name = group.getName();
        if (group.getUserId() == Group.groupUsers)
            name = "All site users";
        int perm = studyACL.getPermissions(group.getUserId()) & (ACL.PERM_UPDATE | ACL.PERM_READ | ACL.PERM_READOWN);
        boolean hasFolderRead = folderACL.hasPermission(group, ACL.PERM_READ);
        boolean hasUpdatePerm = 0 != (perm & ACL.PERM_UPDATE);
        boolean hasReadAllPerm = (!hasUpdatePerm) && 0 != (perm & ACL.PERM_READ);
        String inputName = "group." + group.getUserId();
        %><tr><td><%=h(name)%></td><th><input type=radio name="<%=inputName%>" value="UPDATE" <%=hasUpdatePerm?"checked":""%>></th><th><input type=radio name="<%=inputName%>" value="READ" <%=hasReadAllPerm?"checked":""%>></th><th><input type=radio name="<%=inputName%>" value="READOWN" <%=perm==ACL.PERM_READOWN?"checked":""%>></th><th><input type=radio name="<%=inputName%>" value="NONE" <%=perm==0?"checked":""%>></th><%
        if (!hasFolderRead)
        {
            %><td><img src="<%=contextPath%>/_images/exclaim.gif" title="group does not have folder read permissions"></td><%
        }
        %></tr><%
    }
    %></table>
    <input id="groupUpdateButton" type=image src="<%=PageFlowUtil.buttonSrc("Update")%>" value="Update">
    </form>