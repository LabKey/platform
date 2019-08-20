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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.assay.ModuleAssayProvider" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ModuleAssayProvider.AssayPageBean> me = (JspView<ModuleAssayProvider.AssayPageBean>) HttpView.currentView();
    ModuleAssayProvider.AssayPageBean bean = me.getModelBean();

    Map<String, Object> assay = AssayController.serializeAssayDefinition(bean.expProtocol, bean.provider, getContainer(), getUser());
%>
<script type="text/javascript">
LABKEY.page = LABKEY.page || {};
LABKEY.page.assay = <%= new JSONObject(assay).toString(2) %>;
</script>
<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
