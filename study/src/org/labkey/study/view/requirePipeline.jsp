<%
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
Before uploading datasets, an administrator must set up a "pipeline"
directory where uploaded data will be stored.<br><br>

<%
    ViewContext context = getViewContext();

    if (context.hasPermission(AdminPermission.class))
    {
        ActionURL pipelineUrl = urlProvider(PipelineUrls.class).urlSetup(getContainer());
        pipelineUrl.addParameter("referer", getActionURL().getLocalURIString());
        out.print(textLink("Pipeline Setup", pipelineUrl));
        out.print(" ");
    }

    if (null == HttpView.currentModel() || (Boolean) HttpView.currentModel())
        out.print(textLink("Go Back", "#", "window.history.back();return false;", "goback"));
%>