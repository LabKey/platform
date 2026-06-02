<%
/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.StudySnapshot" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<StudySnapshot> me = HttpView.currentView();
    StudySnapshot snapshot = me.getModelBean();
    Study study = StudyManager.getInstance().getStudy(getContainer());
    assert null != study;
%>
<labkey:form action="" method="post">
    <p>This page displays some of the settings associated with the publication of this study.</p>
<%
    if (snapshot.isRefresh())
    {
%>
        <p>This study is currently configured to refresh its specimens from the source study on a nightly basis. This can be changed below.</p>
<%
    }
%>
    <table class="lk-fields-table" style="width: 750px;">
        <tr>
            <td class="labkey-form-label" style="width: 150px;">Created By:</td>
            <td><%=h(UserManager.getUser(snapshot.getCreatedBy()).getDisplayName(getUser()))%></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Created:</td>
            <td><%=formatDate(snapshot.getCreated())%></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Modified By:</td>
            <td><%=h(UserManager.getUser(snapshot.getModifiedBy()).getDisplayName(getUser()))%></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Modified:</td>
            <td><%=formatDate(snapshot.getModified())%></td>
        </tr>
    </table>
    <br/>
    <%= button("Update").submit(true) %>&nbsp; <% %>
    <%= button("Done").href(ManageStudyAction.class, getContainer()) %>
</labkey:form>
