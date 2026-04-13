<%
/*
 * Copyright (c) 2020 LabKey Corporation
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
<%@ page import="org.graphper.draw.ExecuteException" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.experiment.ExperimentRunGraph" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentRunGraphModel" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
         // dependencies.add("http://localhost:3001/runGraph.js");
         dependencies.add("gen/runGraph");
    }
%>
<%
    ExperimentRunGraphModel model = (ExperimentRunGraphModel)HttpView.currentModel();
    boolean isSummaryView = !model.isDetail();
    boolean isBetaViewEnabled = getActionURL().getParameter("betaGraph") != null;

    String uniqueId = "" + UniqueID.getServerSessionScopedUID();
    String appId = "run-graph-app-" + uniqueId;
    String toggleBtnId = "toggle-btn-" + uniqueId;
    String graphTabId = "graph-tab-" + uniqueId;
    String graphTabBetaId = "graph-tab-beta-" + uniqueId;

    if (isSummaryView)
    {
%>
<%=button("Toggle Beta Graph (new!)").id(toggleBtnId).style("display: inline-block; float: right;")%>
<ul id="run-graph-tab-bar" class="nav nav-tab" role="tablist" style="display: none;">
    <li class="<%=h(isBetaViewEnabled ? "" : "active")%>>"><a href="#<%=h(graphTabId)%>" role="tab" data-toggle="tab">Original</a></li>
    <li class="<%=h(isBetaViewEnabled ? "active" : "")%>>"><a href="#<%=h(graphTabBetaId)%>" role="tab" data-toggle="tab">Beta</a></li>
</ul>
<div class="tab-content">
    <div class="tab-pane <%=h(isBetaViewEnabled ? "" : "active")%>" id="<%=h(graphTabId)%>">
<%
    }
%>
<p>Click on a node in the graph below for details.</p>
<%
    try
    {
        ExperimentRunGraph.renderSvg(
            out,
            getContainer(),
            model.getRun(),
            model.isDetail(),
            model.getFocus(),
            model.getFocusType()
        );
    }
    catch (ExecuteException e)
    {
        throw new RuntimeException(e);
    }

    if (isSummaryView)
    {
%>
    </div>
    <div class="tab-pane <%=h(isBetaViewEnabled ? "active" : "")%>" id="<%=h(graphTabBetaId)%>">
        <div id="<%=h(appId)%>"></div>
    </div>
</div>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    (function($) {
        $(function() {
            var nextIdx = <%=isBetaViewEnabled ? 0 : 1%>;
            var tabIds = [<%=q(graphTabId)%>, <%=q(graphTabBetaId)%>];

            $(<%=q("#" + toggleBtnId)%>).click(function(e) {
                e.preventDefault();
                $('#run-graph-tab-bar a[href="#' + tabIds[nextIdx] + '"]').tab('show');
                nextIdx ^= 1;
            });
        });

        LABKEY.App.loadApp('runGraph', <%=q(appId)%>, {
            lsid: <%=q(model.getRun().getLSID())%>,
            rowId: <%=model.getRun().getRowId()%>,
        });
    })(jQuery);
</script>
<%
    }
%>