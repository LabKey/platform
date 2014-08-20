<%
/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.LocationImpl" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.SelectSpecimenProviderBean> me = (JspView<SpecimenController.SelectSpecimenProviderBean>) HttpView.currentView();
    SpecimenController.SelectSpecimenProviderBean bean = me.getModelBean();
%>
<labkey:errors/>
<p>Vials from the selected specimens can be shipped to you from multiple locations.  Please select your preferred location:</p>
<labkey:form action="<%= h(bean.getFormTarget().getLocalURIString()) %>" method="POST">
<%= h(bean.getSourceForm().getHiddenFormInputs(getViewContext())) %>
<p>
    <select name="preferredLocation">
    <%
        for (LocationImpl location : bean.getPossibleLocations())
        {
    %>
    <option value="<%= location.getRowId() %>"><%= h(location.getLabel())%></option>
    <%
        }
    %>
</select>
</p>
<p>
    <%= generateBackButton() %>
    <%= button("Select").submit(true) %>
</p>
</labkey:form>