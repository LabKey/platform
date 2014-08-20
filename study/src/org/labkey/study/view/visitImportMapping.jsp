<%
/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
    boolean hasCustomMapping = !bean.getCustomMapping().isEmpty();
%>
<labkey:form action="" method="post">
    <table id="customMapping" width="80%">
        <tr><th colspan="2" align="left">Custom Mapping</th></tr>
        <tr><td colspan="2">&nbsp;</td></tr><%
            if (!hasCustomMapping)
            {
                %><tr><td colspan="2">The custom mapping is currently empty</td></tr><%
            }
            else
            {
                %><tr><th align="left">Name</th><th align="left">Sequence Number Mapping</th></tr><%

                for (VisitAlias alias : bean.getCustomMapping())
                {
        %>
            <tr><td><%=h(alias.getName())%></td><td><%=h(alias.getSequenceString())%></td></tr><%
                }
            }
        %>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td colspan="2"><%= button((hasCustomMapping ? "Replace" : "Import") + " Custom Mapping").href(StudyController.ImportVisitAliasesAction.class, getContainer()) %><%=text(hasCustomMapping ? "&nbsp;" + button("Clear Custom Mapping").href(StudyController.ClearVisitAliasesAction.class, getContainer()) : "")%></td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr><th colspan="2" align="left">Standard Mapping</th></tr>
        <tr><td colspan="2">&nbsp;</td></tr><%
            if (bean.getStandardMapping().isEmpty())
            {
                %><tr><td colspan="2">The standard mapping is currently empty</td></tr><%
            }
            else
            {
                boolean gray = false;

                for (VisitAlias alias : bean.getStandardMapping())
                {
                    if (alias.isOverridden())
                    {
                        gray = true;
                        break;
                    }
                }

                if (gray)
                {
                    %><tr><td colspan="3">Grayed out rows below represent mappings that are overridden by custom mappings or previous standard mappings.</td></tr>
                    <tr><td colspan="3">&nbsp;</td></tr><%
                }

                %><tr><th align="left">Label</th><th align="left">Sequence Number Mapping</th><th align="left">Sequence Number Range</th></tr><%

                for (VisitAlias alias : bean.getStandardMapping())
                {
        %>
            <tr>
                <td<%=text(alias.isOverridden() ? " class=\"labkey-mv\"" : "")%>><%=h(alias.getName())%></td>
                <td<%=text(alias.isOverridden() ? " class=\"labkey-mv\"" : "")%>><%=h(alias.getSequenceNumString())%></td>
                <td<%=text(alias.isOverridden() ? " class=\"labkey-mv\"" : "")%>><%=h(alias.getSequenceString())%></td>
            </tr><%
                }
            }
        %>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td colspan="2"><%= button("Done").href(ManageVisitsAction.class, getContainer()) %></td>
        </tr>
    </table>
</labkey:form>
