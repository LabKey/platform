<%
/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ExperimentController.CreateDataClassFromTemplateForm> me = (JspView<ExperimentController.CreateDataClassFromTemplateForm>) HttpView.currentView();
    ExperimentController.CreateDataClassFromTemplateForm bean = me.getModelBean();
    String returnUrl = bean.getReturnUrl();

    List<String> templateNames = new ArrayList<>(bean.getAvailableDomainTemplateNames());
%>

<style type="text/css">
    .form-item input {
        padding: 3px;
    }
    .form-item select {
        width: 360px;
        padding: 3px;
    }
    .form-label {
        width: 135px;
        display: inline-block;
    }
</style>

<labkey:errors/>
<labkey:form action="" method="POST">
    <div class="form-label"><label for="domainTemplate">Select template:</label></div>
    <select class="form-select" id="domainTemplate" name="domainTemplate">
        <option value=""></option>
        <%
            Collections.sort(templateNames);
            for (String templateName : templateNames)
            {
                %><option value="<%=h(templateName)%>"><%=h(templateName)%></option><%
            }
        %>
    </select>
    <br/>
    <br/>
    <div class="form-buttons">
        <%=button("Create").submit(true)%>
        <%=button("Cancel").href(returnUrl)%>
    </div>
</labkey:form>
<br/>
<br/>
<div style="display: <%=h(bean.getXmlParseErrors().isEmpty()?"none":"block")%>;">
<%=link("Show Template Parse Errors").onClick("document.getElementById('warnings').style.display = 'block'; return false;").id("showParseErrorsId")%>
</div>
<br/>
<div id="warnings" class="labkey-error" style="display: none;">
<%
    for (String parseError : bean.getXmlParseErrors())
    {
        %><div><%=h(parseError)%></div><br/><%
    }
%>
</div>
