<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.specimen.actions.ShowSearchAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.PtidVisit" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.SpecimensAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenHeaderBean" %>
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
    JspView<SpecimenHeaderBean> me = HttpView.currentView();
    SpecimenHeaderBean bean = me.getModelBean();
    ActionURL createRequestURL = new ActionURL(ShowSearchAction.class, getContainer());
    createRequestURL.addParameter("fromGroupedView", !bean.isShowingVials());
    createRequestURL.addReturnUrl(getActionURL());
    String subjectNounSingle = StudyService.get().getSubjectNounSingular(getContainer());
    String subjectNounPlural = StudyService.get().getSubjectNounPlural(getContainer());
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    var CREATE_REQUEST_BASE_LINK = <%=q(createRequestURL)%>;
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
<%=link("Manage Study", urlProvider(StudyUrls.class).getManageStudyClass())%>&nbsp;
<%
    }
%>
<%=link(vialLinkText, bean.getOtherViewURL())%>&nbsp;
<%=link("Search", ShowSearchAction.getShowSearchURL(getContainer(), bean.isShowingVials()))%>&nbsp;
<%=link("Reports", urlFor(SpecimenController.AutoReportListAction.class)) %>
<%
    if (!bean.getPtidVisits().isEmpty())
    {
        // get the first visit label:
        HtmlStringBuilder builder = HtmlStringBuilder.of()
            .unsafeAppend("<b>")
            .append("This view is displaying specimens only from ");
        boolean usePlural = bean.getPtidVisits().size() != 1;
        if (bean.isSingleVisitFilter())
        {
            builder.append((usePlural ? subjectNounPlural : subjectNounSingle).toLowerCase())
                .append(" ");
            for (Iterator<PtidVisit> it = bean.getPtidVisits().iterator(); it.hasNext();)
            {
                String ptid = it.next().ptid();
                builder.append(ptid);
                if (it.hasNext())
                    builder.append(", ");
            }
            String visit = bean.getPtidVisits().iterator().next().visit();
            if (visit != null)
                builder.append(" at visit ").append(visit);

            builder.append(".")
                .unsafeAppend("</b><br>");
        }
        else
        {
            builder.append(" the following ")
                .append(subjectNounSingle.toLowerCase())
                .append("/visit ").append(usePlural ? "pairs" : "pair")
                .append(":")
                .unsafeAppend("</b><br>");
            for (Iterator<PtidVisit> it = bean.getPtidVisits().iterator(); it.hasNext();)
            {
                PtidVisit ptidVisit = it.next();
                builder.append(ptidVisit.ptid())
                    .append("/")
                    .append(ptidVisit.visit());
                if (it.hasNext())
                    builder.append(", ");
            }
            builder.append(".");
        }
        ActionURL noFilterUrl = getViewContext().cloneActionURL().setAction(SpecimensAction.class);
%>
    <p>
        <table width="700px">
            <tr><td><%=builder%></td></tr>
        </table>
    </p>
<%= link("Remove " + subjectNounSingle + "/Visit Filter", noFilterUrl)%><%
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
                        <td><%=link("Create new request").onClick("createRequest(); return false;").id("sample-request-create-link")%></td>
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
