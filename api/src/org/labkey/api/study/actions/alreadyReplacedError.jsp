<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.study.assay.AssayUrls" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%
/*
 * Copyright (c) 2012 LabKey Corporation
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

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpRun> me = (JspView<ExpRun>) HttpView.currentView();
    ExpRun replacedRun = me.getModelBean();
    ExpRun replacedByRun = replacedRun.getReplacedByRun();
    ExpProtocol protocol = replacedRun.getProtocol();
    AssayProvider provider = AssayService.get().getProvider(protocol);
    ActionURL reimportURL = provider.getImportURL(replacedByRun.getContainer(), protocol);
    reimportURL.addParameter("reRunId", replacedByRun.getRowId());
%>
<span class="labkey-error">
    Error: The run
    '<a href="<%= h(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(replacedRun.getContainer(), protocol, replacedRun.getRowId())) %>"><%= h(replacedRun.getName()) %></a>'
    has already been replaced by the run
    '<a href="<%= h(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(replacedByRun.getContainer(), protocol, replacedByRun.getRowId())) %>"><%= h(replacedByRun.getName()) %></a>'.
    You may wish to <a href="<%= h(reimportURL)%>">re-import the replacement run</a> instead.
</span>