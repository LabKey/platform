<%@ page import="org.labkey.experiment.ExperimentRunWebPartFactory" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.exp.ExperimentRunType" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
/*
 * Copyright (c) 2010 LabKey Corporation
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

<%
    JspView<ExperimentRunWebPartFactory.Bean> me = (JspView<ExperimentRunWebPartFactory.Bean>) HttpView.currentView();
    ExperimentRunWebPartFactory.Bean bean = me.getModelBean();
%>
<form method="POST">

    <table>
        <tr>
            <td>Run type to show:</td>
            <td>
                <select name="<%= ExperimentRunWebPartFactory.EXPERIMENT_RUN_FILTER %>">
                    <option value="">&lt;Automatically selected based on runs&gt;</option>
                    <% for (ExperimentRunType type : bean.getTypes()) { %>
                        <option value="<%= PageFlowUtil.filter(type.getDescription()) %>" <%= type.getDescription().equals(bean.getDefaultRunFilterName()) ? "selected" : "" %>><%= PageFlowUtil.filter(type.getDescription()) %></option>
                    <% } %>
                </select>
            </td>
        </tr>
        <tr>
            <td colspan=2 align="right">
                <%=PageFlowUtil.generateSubmitButton("Submit")%>
                <%=PageFlowUtil.generateButton("Cancel", "begin.view")%>
            </td>
        </tr>
    </table>
</form>