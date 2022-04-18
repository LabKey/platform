<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.exp.api.AssayJSONConverter" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExperimentJSONConverter" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.assay.AssayController" %>
<%@ page import="org.labkey.assay.ModuleAssayProvider" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ModuleAssayProvider.RunDetailsBean> me = (JspView<ModuleAssayProvider.RunDetailsBean>) HttpView.currentView();
    ModuleAssayProvider.RunDetailsBean bean = me.getModelBean();
    ModuleAssayProvider provider = bean.provider;
    ExpProtocol protocol = bean.expProtocol;
    ExpRun run = bean.expRun;

    Map<String, Object> assay = AssayController.serializeAssayDefinition(bean.expProtocol, bean.provider, getContainer(), getUser());
    JSONObject runJson = AssayJSONConverter.serializeRun(run, provider, protocol, getUser(), ExperimentJSONConverter.DEFAULT_SETTINGS);
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
LABKEY.page = LABKEY.page || {};
LABKEY.page.assay = <%= new JSONObject(assay).getJavaScriptFragment(2) %>;
LABKEY.page.run = new LABKEY.Exp.Run(<%= runJson.getJavaScriptFragment(2) %>);
</script>
<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
