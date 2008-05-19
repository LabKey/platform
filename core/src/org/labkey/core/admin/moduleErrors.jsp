<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="java.io.PrintWriter" %>
<%@ page import="java.io.StringWriter" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    Map<String,Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();

%>
    <table cellspacing="0" cellpadding="1"
           id="dataregion_Gen Runs"
           style="border-top: 1px solid rgb(170, 170, 170);
           border-left: 1px solid rgb(170, 170, 170);
           border-bottom: 1px solid rgb(170, 170, 170);"
           class="grid">
        <tr><th><b>Module name</b></th><th><b>Stack trace</b></th></tr>

<%
    for(Map.Entry<String,Throwable> entry : moduleFailures.entrySet())
    {

%>
        <tr>
            <td valign="top"><pre><%=entry.getKey()%></pre></td>
            <td valign="top"><pre><%

                StringWriter writer = new StringWriter();
                entry.getValue().printStackTrace(new PrintWriter(writer));
                out.print(PageFlowUtil.filter(writer.toString()));

            %></pre></td>

        </tr>
<%

    }
%>

    </table>

