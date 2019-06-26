<%
/*
 * Copyright (c) 2019 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ExperimentController.CreateSampleSetForm> view = (JspView<ExperimentController.CreateSampleSetForm>) HttpView.currentView();
    ExperimentController.CreateSampleSetForm bean = view.getModelBean();
    String helpText = "Used for generating unique sample IDs (" + helpLink("sampleIDs#expression", "more info") + ")";
%>

<labkey:errors />
<labkey:form action="" method="POST" layout="horizontal">
    <labkey:input
        id="name" name="name" label="Name" value="<%=bean.getName()%>"
        contextContent="Name of sample set (required)." size="60" isDisabled="<%=bean.getNameReadOnly()%>"
    />
    <labkey:input
        id="nameExpression" name="nameExpression" label="Name Expression" value="<%=h(bean.getNameExpression())%>"
        placeholder="S-\${now:date}-\${batchRandomId}-\${randomId}"
        contextContent="<%=helpText%>" size="60"
    />
    <div class="form-group">
        <label class=" control-label col-sm-3 col-lg-2">
            Description
        </label>
        <div class="col-sm-9 col-lg-10">
            <textarea name="description" id="description" cols="60" rows="5"><%=text(bean.getDescription())%></textarea>
        </div>
    </div>

    <br/>
    <%=button("Create").submit(true)%>
    <%=button("Cancel").href(bean.getReturnURLHelper())%>
</labkey:form>



