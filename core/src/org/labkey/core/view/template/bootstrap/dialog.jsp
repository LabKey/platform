<%--
/*
 * Copyright (c) 2017 LabKey Corporation
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
--%>
<%@ page buffer="none" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.core.view.template.bootstrap.PageTemplate" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PageConfig> me = (JspView<PageConfig>) HttpView.currentView();
    PageConfig pageConfig = me.getModelBean();
%>
<div class="container">
    <div class="row content-row">
        <div class="content-left">
            <%= text(PageTemplate.renderSiteMessages(pageConfig)) %>
            <div class="well">
                <% me.include(me.getBody(), out); %>
            </div>
        </div>
    </div>
</div>