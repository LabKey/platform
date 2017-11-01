<%
/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ExperimentController.InsertDataClassForm> me = (JspView<ExperimentController.InsertDataClassForm>) HttpView.currentView();
    ExperimentController.InsertDataClassForm bean = me.getModelBean();
    String returnUrl = getViewContext().getActionURL().getParameter("returnUrl");

    List<String> templateNames = new ArrayList<>(bean.getAvailableDomainTemplateNames());

    List<? extends ExpSampleSet> sets = ExperimentService.get().getSampleSets(getContainer(), getUser(), true);
    Map<Integer, String> sampleSets = new LinkedHashMap<>();
    sampleSets.put(null, "");
    for (ExpSampleSet ss : sets)
        sampleSets.put(ss.getRowId(), ss.getName());
%>

<style type="text/css">
    .form-item {
        padding: 5px 0 0 30px;
    }
    .form-item input {
        padding: 3px;
    }
    .form-item select {
        width: 360px;
        padding: 3px;
    }
    .form-longinput {
        width: 350px;
    }
    .form-label {
        width: 135px;
        display: inline-block;
    }
    .form-radio, .form-buttons {
        padding-top: 15px;
    }
    .form-disabled {
        opacity: 0.5;
    }
</style>

<labkey:errors/>
<labkey:form action="" method="POST">
    <input type="radio" id="useTemplate1" name="useTemplate" value="false" checked="checked">
    <label for="useTemplate1">From Scratch</label>

    <div id="scratch-div">
        <div class="form-item">
            <div class="form-label"><label for="name">Name:</label></div>
            <input type="text" id="name" name="name" value="<%=bean.getName() == null ? "" : bean.getName()%>">
        </div>
        <div class="form-item">
            <div class="form-label"><label for="description">Description:</label></div>
            <input type="text" id="description" name="description" class="form-longinput" value="<%=bean.getDescription() == null ? "" : bean.getDescription()%>">
        </div>
        <div class="form-item">
            <div class="form-label"><label for="nameExpression">Name Expression:</label></div>
            <input type="text" id="nameExpression" name="nameExpression" class="form-longinput" value="<%=bean.getNameExpression() == null ? "" : bean.getNameExpression()%>">
        </div>
        <div class="form-item">
            <div class="form-label"><label for="materialSourceId">Material Source ID:</label></div>
            <select id="materialSourceId" name="materialSourceId">
                <labkey:options value="<%=bean.getMaterialSourceId()%>" map="<%=sampleSets%>"/>
            </select>
        </div>
    </div>

    <div class="form-radio">
        <input type="radio" id="useTemplate2" name="useTemplate" value="true" <%=h(templateNames.size()==0?"disabled":"")%>>
        <label for="useTemplate2" class="<%=h(templateNames.size()==0?"form-disabled":"")%>">Using Template</label>
    </div>

    <div id='template-div' class="form-item form-disabled">
        <div class="form-label"><label for="domainTemplate">Select template:</label></div>
        <select class="form-select" id="domainTemplate" name="domainTemplate" disabled="disabled">
            <option value=""></option>
            <%
                Collections.sort(templateNames);
                for (String templateName : templateNames)
                {
                    %><option value="<%=h(templateName)%>"><%=h(templateName)%></option><%
                }
            %>
        </select>
    </div>

    <div class="form-buttons">
        <%=button("Create").submit(true)%>
        <%=button("Cancel").href(returnUrl)%>
    </div>
</labkey:form>
<br/>
<br/>
<div style="display: <%=h(bean.getXmlParseErrors().isEmpty()?"none":"block")%>;">
<%=textLink("Show Template Parse Errors", (URLHelper)null, "document.getElementById('warnings').style.display = 'block'; return false;", "showParseErrorsId")%>
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

<script type="text/javascript">
    (function($) {
        $('input:radio[name="useTemplate"]').change(function(event)
        {
            var useTempalte = this.value === "true";

            $(useTempalte ? '#scratch-div' : '#template-div').addClass('form-disabled');
            $(!useTempalte ? '#scratch-div' : '#template-div').removeClass('form-disabled');

            $('#name').prop('disabled', useTempalte);
            $('#description').prop('disabled', useTempalte);
            $('#nameExpression').prop('disabled', useTempalte);
            $('#materialSourceId').prop('disabled', useTempalte);
            $('#domainTemplate').prop('disabled', !useTempalte);
        });
    })(jQuery);
</script>
