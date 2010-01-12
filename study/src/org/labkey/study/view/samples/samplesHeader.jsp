<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.util.Pair"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script>LABKEY.requiresScript('util.js');</script>
<script>LABKEY.requiresClientAPI();</script>
<%
    JspView<SpecimenController.SpecimenHeaderBean> me = (JspView<SpecimenController.SpecimenHeaderBean>) HttpView.currentView();
    SpecimenController.SpecimenHeaderBean bean = me.getModelBean();
    ActionURL createRequestURL = new ActionURL(SpecimenController.ShowAPICreateSampleRequestAction.class, getViewContext().getContainer());
    createRequestURL.addParameter("fromGroupedView", !bean.isShowingVials());
    createRequestURL.addParameter("returnUrl", getViewContext().getActionURL().toString());
    String subjectNounSingle = StudyService.get().getSubjectNounSingular(getViewContext().getContainer());
%>
<script>
    var CREATE_REQUEST_BASE_LINK = '<%= createRequestURL.getLocalURIString() %>';
    LABKEY.requiresScript('sampleRequest.js');
</script>
<%
   // boolean enableRequests = SampleManager.getInstance().getRepositorySettings(me.getViewContext().getContainer()).isEnableRequests();
    String vialLinkText = bean.isShowingVials() ? "Hide Vial Info" : "Show Vial Info";

    if (bean.getViewContext().getContainer().hasPermission(bean.getViewContext().getUser(), AdminPermission.class))
    {
%>
<%=this.textLink("Manage Study",
        new ActionURL(StudyController.ManageStudyAction.class, bean.getViewContext().getContainer()))%>&nbsp;
<%
    }
%>
<%= this.textLink(vialLinkText, bean.getOtherViewURL())%>&nbsp;
<%= this.textLink("Search", "showSearch.view?showVials=" + (bean.isShowingVials() ? "true" : "false"))%>&nbsp;
<%= this.textLink("Reports", "autoReportList.view") %>&nbsp;
<%= this.textLink("Customize View", bean.getCustomizeURL())%>
<%
    if (!bean.getFilteredPtidVisits().isEmpty())
    {
        // get the first visit label:
        StringBuilder filterString = new StringBuilder();
        filterString.append("This view is displaying specimens only from ");
        if (bean.isSingleVisitFilter())
        {
            filterString.append("particpant(s) ");
            for (Iterator<Pair<String, String>> it = bean.getFilteredPtidVisits().iterator(); it.hasNext();)
            {
                String ptid = it.next().getKey();
                filterString.append(ptid);
                if (it.hasNext())
                    filterString.append(", ");
            }
            filterString.append(" at visit \"").append(bean.getFilteredPtidVisits().iterator().next().getValue()).append("\".");
        }
        else
        {
            filterString.append(" the following ").append(subjectNounSingle.toLowerCase()).append("/visit pairs: ");
            for (Iterator<Pair<String, String>> it = bean.getFilteredPtidVisits().iterator(); it.hasNext();)
            {
                Pair<String, String> ptidVisit = it.next();
                filterString.append(ptidVisit.getKey()).append("/\"").append(ptidVisit.getValue()).append("\"");
                if (it.hasNext())
                    filterString.append(", ");
            }
            filterString.append(".");
        }
        ActionURL noFitlerUrl = getViewContext().cloneActionURL().setAction("samples");
%>
    <b><%= filterString %></b><br><%= textLink("Remove " + subjectNounSingle + "/Visit Filter", noFitlerUrl )%><br>
<%
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