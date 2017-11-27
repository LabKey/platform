<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("query/browser");
    }
%>
<%
    String renderId = "query-browser-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id="<%=h(renderId)%>"></div>
<!-- Fields required for history management -->
<form id="history-form" class="x4-hidden">
    <input type="hidden" id="x4-history-field" />
    <iframe id="x4-history-frame"></iframe>
</form>
<script type="text/javascript">
    Ext4.onReady(function() {
        Ext4.Ajax.timeout = 60 * 1000; // 1 minute
        Ext4.QuickTips.init();
        Ext4.History.init();

        Ext4.create('LABKEY.query.browser.Browser', {
            renderTo: <%=q(renderId)%>
        });
    });
</script>
