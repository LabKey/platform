<%
/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyController.ShowVisitImportMappingAction" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
    }
%>
<labkey:form action="" method="post">
    <table width="80%">
        <%=formatMissedErrorsInTable("form", 1)%>
        <tr>
            <td>Paste in a tab-delimited file that includes two columns, Name and SequenceNum. The mapping you provide
                will replace the existing mapping.</td>
        </tr>
        <tr>
            <td>
                <textarea id="tsv" name="tsv" rows="30" cols="40"></textarea>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <td><%= button("Submit").submit(true) %>&nbsp;<%= button("Cancel").href(ShowVisitImportMappingAction.class, getContainer()) %></td>
        </tr>
    </table>
</labkey:form>
<script type="text/javascript">
    Ext.EventManager.on('tsv', 'keydown', LABKEY.ext.Utils.handleTabsInTextArea);
</script>

