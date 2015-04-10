<%
    /*
     * Copyright (c) 2015 LabKey Corporation
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
<%@ page import="org.labkey.api.study.StudyReloadSource" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Collection<StudyReloadSource> reloadSources = StudyService.get().getStudyReloadSources(getContainer());
%>

<p/><div class="labkey-title-area-line"></div>
<div><span><strong>Reload from an External Repository</strong></span></div><p/>
<div>A study can be configured to reload its data from an external, 3rd party repository, either manually or automatically at preset intervals. Each
    external repository can be configured separately if the repository source has a configuration link next to it. It is generally assumed that only one
    external repository source is configured and active for each study.
</div><p/>
<table>
    <%
        for (StudyReloadSource source : reloadSources)
        {
            ActionURL manageAction = source.getManageAction(getContainer(), getUser());
            if (source.isEnabled(getContainer()) && manageAction != null)
            {
    %>
    <tr>
        <td><span style="padding-right: 8px"><strong><%=h(source.getName())%> repository</strong></span></td>
        <td><%=textLink("Configure " + source.getName(), manageAction)%></td>
    </tr>
    <%
            }
        }
    %>

</table>
