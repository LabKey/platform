<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.view.SubjectDetailsWebPartFactory" %>
<%@ page import="java.util.EnumSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart bean = me.getModelBean();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    ActionURL postUrl = bean.getCustomizePostURL(ctx);
    String participantId = bean.getPropertyMap().get(SubjectDetailsWebPartFactory.PARTICIPANT_ID_KEY);
    String ptidCompletionBase = SpecimenService.get().getCompletionURLBase(c, SpecimenService.CompletionType.ParticipantId);

    String selectedData = bean.getPropertyMap().get(SubjectDetailsWebPartFactory.DATA_TYPE_KEY);
    if (selectedData == null)
        selectedData = SubjectDetailsWebPartFactory.DataType.ALL.name();
    
    boolean includePrivateData = Boolean.parseBoolean(bean.getPropertyMap().get(SubjectDetailsWebPartFactory.QC_STATE_INCLUDE_PRIVATE_DATA_KEY));
    String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
%>
<p>Each <%= h(subjectNoun.toLowerCase()) %> webpart will display datasets from a single <%= h(subjectNoun.toLowerCase()) %>.</p>

<labkey:form action="<%=postUrl%>" method="post">
<table>
    <tr>
        <td>
            <%= StudyService.get().getSubjectColumnName(getContainer()) %>:
        </td>
        <td>
            <labkey:autoCompleteText name="<%= SubjectDetailsWebPartFactory.PARTICIPANT_ID_KEY %>"
                                     url="<%=ptidCompletionBase%>"
                                     value="<%=h(participantId)%>"/>
        </td>
    </tr>
    <tr>
        <td>Data type to display:</td>
        <td>
            <select name="<%=SubjectDetailsWebPartFactory.DATA_TYPE_KEY%>">
                <%
                    for (SubjectDetailsWebPartFactory.DataType type : EnumSet.allOf(SubjectDetailsWebPartFactory.DataType.class))
                    {
                        %>
                <option value="<%=type.name()%>"<%=selected(selectedData.equals(type.name()))%>><%=type.toString()%></option>
                        <%
                    }
                %>

            </select>
        </td>
    </tr>
    <%
        if (StudyManager.getInstance().showQCStates(c))
        {
    %>
    <tr>
        <td>QC state to display:</td>
        <td>
            <select name="<%=SubjectDetailsWebPartFactory.QC_STATE_INCLUDE_PRIVATE_DATA_KEY%>">
                <option value="false">Public data</option>
                <option value="true"<%=selected(includePrivateData)%>>All data</option>
            </select>
        </td>
    </tr>
    <%
        }
    %>
    <tr>
        <td>
            <%= button("Submit").submit(true) %>
            <%= button("Cancel").href(c.getStartURL(getUser())) %>
        </td>
    </tr>
</table>
</labkey:form>