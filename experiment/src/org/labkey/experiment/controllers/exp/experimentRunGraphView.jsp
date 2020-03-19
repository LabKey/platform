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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    try
    {
        ViewContext context = getViewContext();
        ExperimentRunGraphModel model = (ExperimentRunGraphModel)HttpView.currentModel();

        ExperimentRunGraph.RunGraphFiles files = ExperimentRunGraph.generateRunGraph(context,
                                                                                     model.getRun(),
                                                                                     model.isDetail(),
                                                                                     model.getFocus(),
                                                                                     model.getFocusType());

        ActionURL imgSrc = ExperimentController.ExperimentUrlsImpl.get().getDownloadGraphURL(model.getRun(),
                                                                                             model.isDetail(),
                                                                                             model.getFocus(),
                                                                                             model.getFocusType());
%>
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
%>