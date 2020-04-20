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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.exp.ExperimentException" %>
<%@ page import="org.labkey.experiment.ExperimentRunGraph" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentRunGraphModel" %>
<%@ page import="org.labkey.api.reader.Readers" %>
<%@ page import="org.apache.commons.io.IOUtils" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.io.Reader" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
         // dependencies.add("http://localhost:3001/runGraph.js");
         dependencies.add("experiment/gen/runGraph.js");
         dependencies.add("experiment/gen/runGraph.css");
    }
%>
<%
    ViewContext context = getViewContext();
    ExperimentRunGraphModel model = (ExperimentRunGraphModel)HttpView.currentModel();
    boolean isSummaryView = !model.isDetail();

    String uniqueId = "" + UniqueID.getServerSessionScopedUID();
    String appId = "run-graph-app-" + uniqueId;
    String toggleBtnId = "toggle-btn-" + uniqueId;
    String graphTabId = "graph-tab-" + uniqueId;
    String graphTabBetaId = "graph-tab-beta-" + uniqueId;

    try
    {
        ExperimentRunGraph.RunGraphFiles files = ExperimentRunGraph.generateRunGraph(context,
                                                                                     model.getRun(),
                                                                                     model.isDetail(),
                                                                                     model.getFocus(),
                                                                                     model.getFocusType());

        ActionURL imgSrc = ExperimentController.ExperimentUrlsImpl.get().getDownloadGraphURL(model.getRun(),
                                                                                             model.isDetail(),
                                                                                             model.getFocus(),
                                                                                             model.getFocusType());

        if (isSummaryView)
        {
%>
<%=button("Toggle Beta Graph (new!)").id(toggleBtnId).style("display: inline-block; float: right;")%>
<ul id="run-graph-tab-bar" class="nav nav-tab" role="tablist" style="display: none;">
    <li class="active"><a href="#<%=h(graphTabId)%>" role="tab" data-toggle="tab">Original</a></li>
    <li><a href="#<%=h(graphTabBetaId)%>" role="tab" data-toggle="tab">Beta</a></li>
</ul>
<div class="tab-content">
    <div class="tab-pane active" id="<%=h(graphTabId)%>">
<%
        }
%>
<p>Click on a node in the graph below for details. Run outputs have a bold outline.</p>
<img alt="Run Graph" src="<%=imgSrc%>" usemap="#graphmap"/>
<%
        if (files.getMapFile().exists())
        {
%>
<map name="graphmap">
<%
            try (Reader reader = Readers.getReader(files.getMapFile()))
            {
                IOUtils.copy(reader, out);
%>
</map>
<%
            }
            finally
            {
                files.release();
            }
        }
    }
    catch (ExperimentException | InterruptedException e)
    {
%><p><%=h(e.getMessage())%></p><%
    }
    catch (IOException e)
    {
%>
<p> Error in generating graph:</p>
<pre><%=h(e.getMessage())%></pre>
<%
    }

    if (isSummaryView)
    {
%>
    </div>
    <div class="tab-pane" id="<%=h(graphTabBetaId)%>">
        <div id="<%=h(appId)%>"></div>
    </div>
</div>
<script type="application/javascript">
    (function($) {
        $(function() {
            var nextIdx = 1;
            var tabIds = [<%=q(graphTabId)%>, <%=q(graphTabBetaId)%>];

            $(<%=q("#" + toggleBtnId)%>).click(function(e) {
                e.preventDefault();
                $('#run-graph-tab-bar a[href="#' + tabIds[nextIdx] + '"]').tab('show');
                nextIdx ^= 1;
            });
        });

        function loadApp(appName, appTarget, appContext) {
            window.dispatchEvent(new CustomEvent('initApp', {
                detail: {
                    appName: appName,
                    appContext: appContext,
                    appTarget: appTarget,
                }
            }));
        }

        loadApp('runGraph', <%=q(appId)%>, {
            lsid: <%=q(model.getRun().getLSID())%>,
            rowId: <%=model.getRun().getRowId()%>,
        });
    })(jQuery);
</script>
<%
    }
%>