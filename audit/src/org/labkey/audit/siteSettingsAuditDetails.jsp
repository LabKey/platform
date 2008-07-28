<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.audit.SiteSettingsAuditDetailsModel" %>
<%
    JspView<SiteSettingsAuditDetailsModel> me = (JspView<SiteSettingsAuditDetailsModel>) HttpView.currentView();
    SiteSettingsAuditDetailsModel model = me.getModelBean();
%>

<p>On <%=PageFlowUtil.filter(model.getWhen())%>,
    <b><%=null == model.getUser() ? "user id " + model.getEvent().getCreatedBy() : PageFlowUtil.filter(model.getUser().getFriendlyName())%></b>
    modified the site settings in the following way:</p>
<%=model.getDiff()%>
