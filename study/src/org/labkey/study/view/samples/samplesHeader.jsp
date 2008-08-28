<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script>LABKEY.requiresScript('util.js')</script>
<%
    JspView<SpringSpecimenController.SpecimenHeaderBean> me = (JspView<SpringSpecimenController.SpecimenHeaderBean>) HttpView.currentView();
    SpringSpecimenController.SpecimenHeaderBean bean = me.getModelBean();
    boolean enableRequests = SampleManager.getInstance().getRepositorySettings(me.getViewContext().getContainer()).isEnableRequests();
    String vialLinkText;
    if (enableRequests)
    {
        vialLinkText = bean.isShowingVials() ? "Hide Vial and Request Options" : "Show Vial and Request Options";
    }
    else
    {
        vialLinkText = bean.isShowingVials() ? "Hide Vial Info" : "Show VialInfo";
    }
    if (bean.getViewContext().getContainer().hasPermission(bean.getViewContext().getUser(), ACL.PERM_ADMIN))
    {
%>
<%= this.textLink("Manage Study",
        ActionURL.toPathString("Study", "manageStudy.view", bean.getViewContext().getContainer()))%>&nbsp;
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
            filterString.append(" the following participant/visit pairs: ");
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
    <b><%= filterString %></b><br><%= textLink("Remove Participant/Visit Filter", noFitlerUrl )%><br>
<%
    }
%>
