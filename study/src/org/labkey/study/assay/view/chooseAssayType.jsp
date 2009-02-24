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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<List<org.labkey.api.study.assay.AssayProvider>> me = (JspView<List<AssayProvider>>) HttpView.currentView();
    List<AssayProvider> providers = me.getModelBean();
%>
<p>
    Each assay is a customized version of a particular assay type. 
    The assay type defines things like how the data is parsed and what kinds of analysis are provided.
</p>
<p>
    Please choose which assay type you would like to customize with your own settings and input options.
</p>
<labkey:errors />
<form method="POST">
    <table>
        <% for (AssayProvider provider : providers) { %>
        <tr>
            <td><input name="providerName" type="radio" value="<%= h(provider.getName()) %>"/></td>
            <td><strong><%= h(provider.getName())%></strong></td>
        </tr>
        <tr>
            <td />
            <td><%= provider.getDescription() %></td>
        </tr>
        <% } %>
        <tr>
            <td />
            <td><%= generateSubmitButton("Next" )%><%= generateButton("Cancel", new ActionURL(AssayController.BeginAction.class, getViewContext().getContainer())) %></td>
        </tr>
    </table>
</form>