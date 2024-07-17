<%
/*
 * Copyright (c) 2011-2018 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.settings.OptionalFeatureService" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NotFoundException" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.designer.StudyDesignManager" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.view.StudyGWTView" %>
<%@ page import="org.labkey.study.view.VaccineStudyWebPart" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/StudyVaccineDesign.css");
    }
%>
<%
    HttpView me = HttpView.currentView();
    ViewContext context = getViewContext();
    Container c = getContainer();
    VaccineStudyWebPart.Model bean = (VaccineStudyWebPart.Model) me.getModelBean();

    Map<String, String> params = new HashMap<>();
    params.put("studyId", Integer.toString(bean.getStudyId()));

    //In the web part we always show the latest revision
    Integer revInteger = StudyDesignManager.get().getLatestRevisionNumber(c, bean.getStudyId());
    if (revInteger == null)
        throw new NotFoundException("No revision found for Study ID: " + bean.getStudyId());

    params.put("revision", Integer.toString(revInteger));
    params.put("edit", context.hasPermission(UpdatePermission.class) && bean.isEditMode() ? "true" : "false");
    boolean canEdit = OptionalFeatureService.get().isFeatureEnabled(Study.GWT_STUDY_DESIGN) && context.hasPermission(UpdatePermission.class);
    params.put("canEdit",  Boolean.toString(canEdit));
    //Can't create repository from web part
    params.put("canCreateRepository", Boolean.FALSE.toString());

    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
    boolean canAdmin = context.hasPermission(AdminPermission.class) && null != study;
    params.put("canAdmin", Boolean.toString(canAdmin));
    params.put("canCreateTimepoints", Boolean.toString(canAdmin && study.getVisits(Visit.Order.DISPLAY).isEmpty()));

    params.put("panel", bean.getPanel());  //bean.getPanel());
    if (null != bean.getFinishURL())
        params.put("finishURL", bean.getFinishURL());

    StudyGWTView innerView = new StudyGWTView(gwt.client.org.labkey.study.designer.client.Designer.class, params);

    response.setContentType("text/css");
%>
<%
include(innerView, out);
%>
