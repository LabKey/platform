<%
/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleType" %>
<%@ page import="org.labkey.api.study.SamplesUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.experiment.api.ExpSampleTypeImpl" %>
<%@ page import="org.labkey.experiment.api.SampleTypeServiceImpl" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container proj = getContainer().getProject();
    if (proj == null || proj.isRoot())
    {
        out.print("No current project.");
    }
    else
    {
        List<ExpSampleTypeImpl> sampleSets = SampleTypeServiceImpl.get().getSampleTypes(getContainer(), getUser(), true);

        int i = 0;
    %> <table style="width:50px;margin-right:1em" ><tr><td style="vertical-align:top;white-space:nowrap;margin:1em"> <%
        for (ExpSampleType sampleSet : sampleSets)
        {
            ActionURL url;
            boolean isStudySample = SpecimenService.SAMPLE_TYPE_NAME.equals(sampleSet.getName());
            if (isStudySample)
                url = urlProvider(SamplesUrls.class).getSamplesURL(sampleSet.getContainer());
            else
                url = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, sampleSet.getContainer()).replaceParameter("rowId", "" + sampleSet.getRowId());
            %>
    <a style="font-weight:bold" href="<%=url%>"><%=h(isStudySample ? sampleSet.getContainer().getName() : sampleSet.getName())%></a>
                <br><%=h(sampleSet.getDescription() != null ? sampleSet.getDescription() : sampleSet.getContainer().getPath())%>
            <br>
    <%
            if (sampleSets.size() > 1 && ++i == sampleSets.size() / 2)
            { %>
                </td><td style="vertical-align:top;white-space:nowrap;margin:1em">
        <%  }
        }
    %></td></tr></table>
<%
    }
%>
