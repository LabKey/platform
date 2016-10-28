<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.security.SecurityUrls"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.model.SecurityType"%>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    HttpView<StudyImpl> me = (HttpView<StudyImpl>) HttpView.currentView();
    StudyImpl study = me.getModelBean();

    boolean isSharedStudy = study.isDataspaceStudy();
    Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
    boolean isInSharedStudy = sharedStudy != null && sharedStudy.getShareDatasetDefinitions() == Boolean.TRUE;
%>
<p>Before setting up security for your study please read the documentation on LabKey security and
    on setting up study security.</p>
<ul>
    <li><%=helpLink("security", "LabKey Security Documentation")%></li>
    <li><%=helpLink("studySecurity", "Study Security Documentation")%></li>
</ul>

<p>All users must have READ permissions on this folder to access anything in this study. You can also
    <%=PageFlowUtil.textLink("Configure Folder Security", urlProvider(SecurityUrls.class).getBeginURL(getContainer()))%></p>
<%
    if (isSharedStudy)
    {
        %><p>All datasets in a dataspace (shared) study are read-only.  To add/update data you must create a study in a sub-folder.</p><%
    }
    else
    {
%>
<p>If you want to set permissions on individual datasets within the study, you must select one of the custom study security options below.</p>

<labkey:form action="<%=h(buildURL(SecurityController.StudySecurityAction.class))%>" method="post" name="studySecurityForm">
    <p>Study Security Type<%=PageFlowUtil.helpPopup("Study Security", SecurityType.getHTMLDescription(), true, 400)%>:
    <select name="securityString" onchange="document.getElementById('securityTypeWarning').style.display = 'block';">
        <%
            for (SecurityType securityType : SecurityType.values())
            {
                // disallow per-dataset permissions in studies with shared datasets
                if (isInSharedStudy && securityType.isSupportsPerDatasetPermissions())
                    continue;
                %>
                <option value="<%=h(securityType.name())%>"<%=selected(study.getSecurityType() == securityType)%>><%=h(securityType.getLabel())%></option>
                <%
            }
        %>
    </select>
    <labkey:button text="Update Type" />
        <div id="securityTypeWarning" style="display: none"><em>Changing the security type can significantly alter who can view and modify data.</em></div>
    </p>
</labkey:form>
<%
    }
%>