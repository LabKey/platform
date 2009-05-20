<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.writer.StudyWriter" %>
<%@ page import="org.labkey.study.writer.Writer" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    JspView<StudyController.ExportForm> me = (JspView<StudyController.ExportForm>) HttpView.currentView();
    StudyController.ExportForm form = me.getModelBean();
    String errors = PageFlowUtil.getStrutsError(request, "main");
%>
<%=errors%>
<form action="" method="post">
    <table>
        <tr>
            <td>Choose the study objects you wish to export:</td>
        </tr>
        <%
            List<Writer<Study>> writers = StudyWriter.WRITERS;

            for (Writer writer : writers)
            {
                String text = writer.getSelectionText();

                if (null != text)
                { %>
        <tr><td><input type="checkbox" name="types" value="<%=text%>" checked> <%=text%></td></tr><%
                }
            }
        %>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Export to:</td></tr>
        <tr><td><input type="radio" name="location" value="0">Pipeline root as individual files</td></tr>
        <tr><td><input type="radio" name="location" value="1">Pipeline root as zip file</td></tr>
        <tr><td><input type="radio" name="location" value="2" checked>Browser as zip file</td></tr>
        <tr>
            <td>
                <%=generateSubmitButton("Export")%>&nbsp;<%=generateButton("Cancel", "manageStudy.view")%>
            </td>
        </tr>
    </table>
</form>