<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.study.SpecimenManager" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.model.VisitMapKey" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistic" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.util.StringUtilsLabKey" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    BaseStudyController.StudyJspView<VisitImpl> me = (BaseStudyController.StudyJspView<VisitImpl>) HttpView.currentView();
    VisitImpl visit = me.getModelBean();
    StudyImpl study = getStudy();

    StudyManager manager = StudyManager.getInstance();
    VisitManager visitManager = manager.getVisitManager(study);
    Map<VisitMapKey, VisitManager.VisitStatistics> summaryMap = visitManager.getVisitSummary(me.getViewContext().getUser(), null, null, Collections.singleton(VisitStatistic.RowCount), true);
    int datasetRowCount = 0;

    for (Map.Entry<VisitMapKey, VisitManager.VisitStatistics> e : summaryMap.entrySet())
    {
        VisitMapKey key = e.getKey();

        if (key.visitRowId == visit.getRowId())
            datasetRowCount += e.getValue().get(VisitStatistic.RowCount);
    }

    int vialCount = SpecimenManager.getInstance().getSampleCountForVisit(visit);
%>
<labkey:errors/>

<labkey:form action="<%=urlFor(StudyController.DeleteVisitAction.class)%>" method="POST">
    Do you want to delete <%=h(visitManager.getLabel())%> <b><%=h(visit.getDisplayString())%></b>?<p/>
    <%
    if (datasetRowCount > 0 || vialCount > 0)
    {
        String dataCountMsg = "This " + visitManager.getLabel().toLowerCase() + " has ";
        String sep = "";
        if (datasetRowCount > 0)
        {
            dataCountMsg += StringUtilsLabKey.pluralize(datasetRowCount, "dataset result");
            sep = " and ";
        }
        if (vialCount > 0)
        {
            dataCountMsg += sep + StringUtilsLabKey.pluralize(vialCount, "specimen vial");
        }
        dataCountMsg += " which will also be deleted.";
        %><%=h(dataCountMsg)%><p/>
    <%
    }
    %>
    <%= button("Delete").submit(true) %>&nbsp;<%= generateBackButton("Cancel") %>
    <input type=hidden name=id value="<%=visit.getRowId()%>">
</labkey:form>
