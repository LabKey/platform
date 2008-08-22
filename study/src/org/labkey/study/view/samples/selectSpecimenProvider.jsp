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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="org.labkey.study.model.Site" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.SelectSpecimenProviderBean> me = (JspView<SpringSpecimenController.SelectSpecimenProviderBean>) HttpView.currentView();
    SpringSpecimenController.SelectSpecimenProviderBean bean = me.getModelBean();
%>
<labkey:errors/>
<p>Vials from the selected speicmens can be shipped to you from multiple locations.  Please select your preferred location:</p>
<form action="<%= bean.getFormTarget().getLocalURIString() %>" method="POST">
<%= bean.getSourceForm().getHiddenFormInputs() %>
<p>
    <select name="preferredLocation">
    <%
        for (Site site : bean.getPossibleSites())
        {
    %>
    <option value="<%= site.getRowId() %>"><%= h(site.getLabel())%></option>
    <%
        }
    %>
</select>
</p>
<p>
    <%= generateButton("Cancel", "javascript:back()")%>
    <%= generateSubmitButton("Select") %>
</p>
</form>