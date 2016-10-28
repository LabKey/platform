<%
/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.exp.api.AssayJSONConverter" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.assay.ModuleAssayProvider" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    // TODO: --Ext3-- This should be declared as part of the included views
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext3");
    }
%>
<%
    JspView<ModuleAssayProvider.ResultDetailsBean> me = (JspView<ModuleAssayProvider.ResultDetailsBean>) HttpView.currentView();
    ModuleAssayProvider.ResultDetailsBean bean = me.getModelBean();
    ModuleAssayProvider provider = bean.provider;
    ExpProtocol protocol = bean.expProtocol;
    ExpData data = bean.expData;

    Map<String, Object> assay = AssayController.serializeAssayDefinition(bean.expProtocol, bean.provider, getContainer(), getUser());
    JSONArray dataRows = AssayJSONConverter.serializeDataRows(data, provider, protocol, getUser(), bean.objectId);
    JSONObject result = dataRows.length() > 0 ? (JSONObject)dataRows.get(0) : new JSONObject();
%>
<script type="text/javascript">
LABKEY.page = LABKEY.page || {};
LABKEY.page.assay = <%= new JSONObject(assay).toString(2) %>;
LABKEY.page.result = <%= result.toString(2) %>;
</script>
<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
