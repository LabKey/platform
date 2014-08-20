<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.ExperimentRunType" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.ExperimentRunWebPartFactory" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExperimentRunWebPartFactory.Bean> me = (JspView<ExperimentRunWebPartFactory.Bean>) HttpView.currentView();
    ExperimentRunWebPartFactory.Bean bean = me.getModelBean();
%>
<labkey:form method="POST">

    <table>
        <tr>
            <td>Run type to show:</td>
            <td>
                <select name="<%=h(ExperimentRunWebPartFactory.EXPERIMENT_RUN_FILTER)%>">
                    <option value="">&lt;Automatically selected based on runs&gt;</option>
                    <% for (ExperimentRunType type : bean.getTypes()) { %>
                        <option value="<%=h(type.getDescription()) %>"<%=selected(type.getDescription().equals(bean.getDefaultRunFilterName()))%>><%=h(type.getDescription()) %></option>
                    <% } %>
                </select>
            </td>
        </tr>
        <tr>
            <td colspan=2 align="right">
                <%= button("Submit").submit(true) %>
                <%= button("Cancel").href(ExperimentController.BeginAction.class, getContainer()) %>
            </td>
        </tr>
    </table>
</labkey:form>