<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.labkey.api.util.Pair"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.controllers.specimen.ShowSearchAction" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController.AutoReportListAction" %>
<%@ page import="java.util.Iterator" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("study/sampleRequest.js");
    }
%>
<%
    JspView<SpecimenController.SpecimenHeaderBean> me = (JspView<SpecimenController.SpecimenHeaderBean>) HttpView.currentView();
    SpecimenController.SpecimenHeaderBean bean = me.getModelBean();
    ActionURL createRequestURL = new ActionURL(SpecimenController.ShowAPICreateSampleRequestAction.class, getContainer());
    createRequestURL.addParameter("fromGroupedView", !bean.isShowingVials());
    createRequestURL.addParameter("returnUrl", getActionURL().toString());
    String subjectNounSingle = StudyService.get().getSubjectNounSingular(getContainer());
    String subjectNounPlural = StudyService.get().getSubjectNounPlural(getContainer());
%>
<script>
    var CREATE_REQUEST_BASE_LINK = '<%= createRequestURL.getLocalURIString() %>';
    <%
    if (bean.getSelectedRequest() != null)
    {
        // We only want to overwrite the selected request with the most recently created request if no other request
        // selection has taken place via the shopping cart.  We ensure this by storing the timestamp of the last selection:
    %>
        LABKEY.Utils.setCookie("selectedRequest", <%= bean.getSelectedRequest() %>, true);
    <%
    }
    %>
</script>
<%
   // boolean enableRequests = SampleManager.getInstance().getRepositorySettings(getContainer()).isEnableRequests();
    String vialLinkText = bean.isShowingVials() ? "Group vials" : "Show individual vials";

    if (getContainer().hasPermission(getUser(), AdminPermission.class))
    {
%>
<%=textLink("Manage Study",
        new ActionURL(ManageStudyAction.class, getContainer()))%>&nbsp;
<%
    }
%>
<%=textLink(vialLinkText, bean.getOtherViewURL())%>&nbsp;
<%=textLink("Search", buildURL(ShowSearchAction.class, "showVials=" + (bean.isShowingVials() ? "true" : "false")))%>&nbsp;
<%=textLink("Reports", AutoReportListAction.class) %>
<%
    if (!bean.getFilteredPtidVisits().isEmpty())
    {
        // get the first visit label:
        StringBuilder filterString = new StringBuilder();
        filterString.append("<b>This view is displaying specimens only from ");
        boolean usePlural = bean.getFilteredPtidVisits().size() != 1;
        if (bean.isSingleVisitFilter())
        {
            filterString.append((usePlural?subjectNounPlural:subjectNounSingle).toLowerCase()).append(" ");
            for (Iterator<Pair<String, String>> it = bean.getFilteredPtidVisits().iterator(); it.hasNext();)
            {
                String ptid = it.next().getKey();
                filterString.append(ptid);
                if (it.hasNext())
                    filterString.append(", ");
            }
            String visit = bean.getFilteredPtidVisits().iterator().next().getValue();
            if (visit != null)
                filterString.append(" at visit ").append(visit);
            filterString.append(".</b><br>");
        }
        else
        {
            filterString.append(" the following ").append(h(subjectNounSingle.toLowerCase())).append("/visit ").append(usePlural?"pairs":"pair").append(":</b><br>");
            for (Iterator<Pair<String, String>> it = bean.getFilteredPtidVisits().iterator(); it.hasNext();)
            {
                Pair<String, String> ptidVisit = it.next();
                filterString.append(ptidVisit.getKey()).append("/").append(ptidVisit.getValue()).append("");
                if (it.hasNext())
                    filterString.append(", ");
            }
            filterString.append(".");
        }
        ActionURL noFitlerUrl = getViewContext().cloneActionURL().setAction(SpecimenController.SamplesAction.class);
%>
    <p>
        <table width="700px">
            <tr><td><%= filterString %></td></tr>
        </table>
    </p>
<%= textLink("Remove " + subjectNounSingle + "/Visit Filter", noFitlerUrl )%><%
    }
%>
<div id="specimen-request-div" class="x-hidden">
    <table>
        <tr>
            <td>
                <table>
                    <tr>
                        <td>Select request:</td>
                        <td style="width:12em" ><span id="sample-request-list"></span></td>
                        <td><%= textLink("Create new request", "#", "createRequest(); return false;", "sample-request-create-link")%></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <span id="sample-request-details"></span>
            </td>
        </tr>
        <tr>
            <td>
                <span id="request-vial-details"></span>
            </td>
        </tr>
    </table>
</div>
