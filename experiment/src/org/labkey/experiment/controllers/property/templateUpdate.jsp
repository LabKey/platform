<%
/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
<%@ page import="org.labkey.experiment.controllers.property.PropertyController" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("Experiment/domainTemplate.js");
    }
%>
<%
    HttpView<PropertyController.CompareWithTemplateModel> me = HttpView.currentView();
    PropertyController.CompareWithTemplateModel model = me.getModelBean();
%>
<style type="text/css">
    DIV.comparetemplate TH
    {
        text-align:left;
    }
    DIV.comparetemplate TD.colA
    {
        min-width:80pt;
    }
    DIV.comparetemplate TD.colB
    {
        min-width:80pt;
    }
    DIV.comparetemplate TD.colSTATUS
    {
        min-width:80pt;
    }
</style>
<div id="updateDomainDiv"></div>
<script type="text/javascript">
(function($)
{
    var schemaName = <%=  q(model.schemaName) %>;
    var queryName = <%=  q(model.queryName) %>;
    var domain = <%= text(PropertyController.convertDomainToJson(model.domain)) %>;
    domain.schemaName = schemaName;
    domain.queryName = queryName;
    var template = <%= text(null==model.template ? "null" : PropertyController.convertDomainToJson(model.template)) %>;
    <% if (null == model.info) { %>
    var templateInfo = null;
    <% } else { %>
    var templateInfo = {module:<%=q(model.info.getModuleName())%>, group:<%=q(model.info.getTemplateGroupName())%>, template:<%=q(model.info.getTableName())%>};
    <% } %>
    renderUpdateDomainView("#updateDomainDiv", templateInfo, domain, template);
})(jQuery);
</script>