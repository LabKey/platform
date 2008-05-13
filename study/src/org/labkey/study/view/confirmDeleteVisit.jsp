<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.Visit" %>
<%@ page import="org.labkey.study.model.VisitMapKey" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    BaseStudyController.StudyJspView<Visit> me = (BaseStudyController.StudyJspView<Visit>) HttpView.currentView();
    Visit visit = me.getModelBean();
    Study study = getStudy();

    StudyManager manager = StudyManager.getInstance();
    VisitManager visitManager = manager.getVisitManager(study);
    Map<VisitMapKey,Integer> summaryMap = visitManager.getVisitSummary(null);
    int count = 0;
    for (Map.Entry<VisitMapKey,Integer> e : summaryMap.entrySet())
    {
        VisitMapKey key = e.getKey();
        if (key.visitRowId == visit.getRowId())
            count += e.getValue().intValue();
    }
%>
<labkey:errors/>

<form action="deleteVisit.view" method=POST>
    Do you want to delete <%=visitManager.getLabel() %> <b><%=visit.getDisplayString()%></b>?<p/>
    <%if (count != 0)
    {
        %>This <%=visitManager.getLabel()%> has <%=count%> dataset results which will also be deleted.<p/><%
    }%>
    <input type=image src="<%=PageFlowUtil.buttonSrc("Delete","large")%>">&nbsp;<input type=image src="<%=PageFlowUtil.buttonSrc("Cancel","large")%>" value="Cancel" onclick="javascript:window.history.back(); return false;">
    <input type=hidden name=id value="<%=visit.getRowId()%>">
</form>
