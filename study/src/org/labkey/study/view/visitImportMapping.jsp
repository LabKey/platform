<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageVisitsAction" %>
<%@ page import="org.labkey.study.model.StudyManager.VisitAlias" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<StudyController.ImportMappingBean> me = (JspView<StudyController.ImportMappingBean>) HttpView.currentView();
    StudyController.ImportMappingBean bean = me.getModelBean();
%>
<form action="" method="post">
    <table width="80%">
        <tr><th colspan="2" align="left">Custom Mapping</th></tr>
        <tr><td>&nbsp;</td></tr><%
            if (bean.getCustomMapping().isEmpty())
            {
                out.print("<tr><td>The custom mapping is currently empty</td></tr>");
            }
            else
            {
                out.print("<tr><th align=\"left\">Alias</th><th align=\"left\">Sequence Number</th></tr>");

                for (VisitAlias alias : bean.getCustomMapping())
                {
        %>
            <tr><td><%=h(alias.getName())%></td><td><%=alias.getSequenceNum()%></td></tr><%
                }
            }
        %>
        <tr><td>&nbsp;</td></tr>
        <tr><th colspan="2" align="left">Standard Mapping</th></tr>
        <tr><td>&nbsp;</td></tr><%
            if (bean.getStandardMapping().isEmpty())
            {
                out.print("<tr><td>The standard mapping is currently empty</td></tr>");
            }
            else
            {
                out.print("<tr><th align=\"left\">Label</th><th align=\"left\">Minimum Sequence Number</th></tr>");

                for (VisitAlias alias : bean.getStandardMapping())
                {
        %>
            <tr><td<%=alias.isOverridden() ? " class=\"labkey-mv\"" : ""%>><%=h(alias.getName())%></td><td<%=alias.isOverridden() ? " class=\"labkey-mv\"" : ""%>><%=alias.getSequenceNum()%></td></tr><%=alias.isOverridden() ? "</span>" : ""%><%
                }
            }
        %>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td><%=generateButton("Import Visit Aliases", StudyController.ImportVisitAliasesAction.class)%>&nbsp;<%=generateButton("Done", ManageVisitsAction.class)%></td>
        </tr>
        <tr><td>&nbsp;</td></tr>
    </table>
</form>
