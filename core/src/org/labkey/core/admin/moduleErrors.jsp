<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="java.io.PrintWriter" %>
<%@ page import="java.io.StringWriter" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Map<String,Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
%>
    <table id="dataregion_Gen Runs" class="labkey-data-region-legacy labkey-show-borders">
        <tr>
            <td class="labkey-column-header">Module name</td>
            <td class="labkey-column-header">Stack trace</td>
        </tr>

<%
    int rowIndex = 0;

    for(Map.Entry<String,Throwable> entry : moduleFailures.entrySet())
    {
        Throwable throwable = entry.getValue();
        String message = throwable.getMessage();
        while (throwable.getCause() != null)
        {
            throwable = throwable.getCause();
            if (throwable.getMessage() != null)
            {
                message = throwable.getMessage();
            }
        }
        if (throwable instanceof org.springframework.beans.PropertyBatchUpdateException)
        {
            org.springframework.beans.PropertyBatchUpdateException batchException = (org.springframework.beans.PropertyBatchUpdateException)throwable;
            if (batchException.getMostSpecificCause() != null && batchException.getMostSpecificCause().getMessage() != null)
            {
                message = batchException.getMostSpecificCause().getMessage();
            }
        }
%>
        <tr class="<%=getShadeRowClass(rowIndex % 1 == 0)%>">
            <td valign="top"><strong><pre><%=entry.getKey()%></pre></strong></td>
            <td valign="top"><% if (message != null) { %>
                <strong class="labkey-error"><pre><%= h(message) %></pre></strong>
                <pre>Full details:</pre>
                <% } %>
                <pre><%

                StringWriter writer = new StringWriter();
                entry.getValue().printStackTrace(new PrintWriter(writer));
                out.print(h(writer.toString()));

            %></pre></td>

        </tr>
<%
        rowIndex++;
    }
%>

    </table>

