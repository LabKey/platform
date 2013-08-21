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
<%@ page import="org.labkey.api.exp.ExperimentException"%>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.study.assay.AssayDataCollector" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm<? extends AssayProvider>> me = (JspView<AssayRunUploadForm<? extends AssayProvider>>) HttpView.currentView();
    AssayRunUploadForm<? extends AssayProvider> bean = me.getModelBean();
%>
<table>
    <%
        boolean first = true;
        List<AssayDataCollector> visibleCollectors = new ArrayList<>();
        Map<String, File> uploadedData = null;
        try
        {
            uploadedData = bean.getUploadedData();
        }
        // These exceptions are reported and handled in UploadWizardAction
        catch (ExperimentException e)
        {
        }

        for (AssayDataCollector collector : bean.getProvider().getDataCollectors(uploadedData, bean))
        {
            if (collector.isVisible())
            {
                visibleCollectors.add(collector);
            }
        }
        for (AssayDataCollector collector : visibleCollectors)
        { %>
        <tr>
            <% if (visibleCollectors.size() > 1)
            { %>
                <td><input value="<%= h(collector.getShortName()) %>" id="<%=h(collector.getShortName()).replace(" ", "")%>" type="radio" name="dataCollectorName"<%=checked(first)%> onclick="hideAllCollectors(); showCollector('<%= h(collector.getShortName()) %>')"></td>
            <% }
            else
            { %>
                <td><input value="<%= h(collector.getShortName()) %>" id="<%=h(collector.getShortName()).replace(" ", "")%>" type="hidden" name="dataCollectorName" /></td>
            <% } %>
            <td><label for="<%=h(collector.getShortName()).replace(" ", "")%>"><%= text(collector.getDescription(bean)) %></label></td>
        </tr>
        <tr style="visibility: <%= text(first ? "visible" : "collapse") %>;" id="collector-<%= h(collector.getShortName()) %>">
            <td></td>
            <td>
                <% include(collector.getView(bean), out); %>
            </td>
        </tr>
    <%
        first = false;
    } %>
</table>
<script type="text/javascript">
    function hideAllCollectors()
    {
        <%
        for (AssayDataCollector collector : visibleCollectors)
        { %>
            document.getElementById('collector-<%= h(collector.getShortName()) %>').style.visibility = 'hidden';
        <% } %>
    }

    function showCollector(collectorName)
    {
        document.getElementById('collector-' + collectorName).style.visibility = 'visible';
    }
</script>
