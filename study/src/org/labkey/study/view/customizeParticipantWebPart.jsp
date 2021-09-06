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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.CompletionType" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.util.element.Option.OptionBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.view.SubjectDetailsWebPartFactory" %>
<%@ page import="org.labkey.study.view.SubjectDetailsWebPartFactory.DataType" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart bean = me.getModelBean();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    ActionURL postUrl = bean.getCustomizePostURL(ctx);
    String participantId = bean.getPropertyMap().get(SubjectDetailsWebPartFactory.PARTICIPANT_ID_KEY);
    ActionURL ptidCompletionBase = urlProvider(StudyUrls.class).getCompletionURL(c, CompletionType.ParticipantId);

    String selectedData = bean.getPropertyMap().get(SubjectDetailsWebPartFactory.DATA_TYPE_KEY);
    if (selectedData == null)
        selectedData = DataType.ALL.name();
    
    String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
%>
<p>Each <%= h(subjectNoun.toLowerCase()) %> webpart will display datasets from a single <%= h(subjectNoun.toLowerCase()) %>.</p>

<labkey:form action="<%=postUrl%>" method="post">
<table>
    <tr>
        <td>
            <%=h(StudyService.get().getSubjectColumnName(getContainer()))%>:
        </td>
        <td>
            <labkey:autoCompleteText name="<%= SubjectDetailsWebPartFactory.PARTICIPANT_ID_KEY %>"
                                     url="<%=ptidCompletionBase%>"
                                     value="<%=participantId%>"/>
        </td>
    </tr>
    <tr>
        <td>Data type to display:</td>
        <td>
            <%=select().name(SubjectDetailsWebPartFactory.DATA_TYPE_KEY)
                .addOptions(Arrays.stream(DataType.values())
                    .map(dt->new OptionBuilder(dt.getDescription(), dt.name())))
                .selected(selectedData)
                .className(null)
            %>
        </td>
    </tr>
    <tr>
        <td>
            <%= button("Submit").submit(true) %>
            <%= button("Cancel").href(c.getStartURL(getUser())) %>
        </td>
    </tr>
</table>
</labkey:form>