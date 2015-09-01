<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.study.PlateTemplate" %>
<%@ page import="org.labkey.api.study.PlateTypeHandler" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.plate.PlateController" %>
<%@ page import="org.labkey.study.plate.PlateManager" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.study.permissions.DesignAssayPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PlateController.PlateTemplateListBean> me = (JspView<PlateController.PlateTemplateListBean>) HttpView.currentView();
    Container c = getContainer();
    List<? extends PlateTemplate> plateTemplates = me.getModelBean().getTemplates();
%>
<h4>Available Plate Templates</h4>
<table>
<%
    boolean isAssayDesigner = c.hasPermission(getUser(), DesignAssayPermission.class);
    for (PlateTemplate template : plateTemplates)
    {
%>
    <tr>
        <td><%= h(template.getName()) %></td>
        <%
            if (isAssayDesigner || c.hasPermission(getUser(), UpdatePermission.class))
            {
        %>
        <td><%= textLink("edit", new ActionURL(PlateController.DesignerAction.class, getContainer()).
                addParameter("templateName", template.getName()).
                addParameter("plateId", template.getRowId())) %></td>
        <%
            }
            if (isAssayDesigner || c.hasPermission(getUser(), InsertPermission.class))
            {
        %>
        <td><%= textLink("edit a copy", new ActionURL(PlateController.DesignerAction.class, getContainer()).
                addParameter("copy", true).
                addParameter("templateName", template.getName()).
                addParameter("plateId", template.getRowId())) %></td>
        <%
            }
            if (c.hasPermission(getUser(), InsertPermission.class))
            {
        %>
        <td><%= textLink("copy to another folder", new ActionURL(PlateController.CopyTemplateAction.class, getContainer()).
                addParameter("templateName", template.getName()).
                addParameter("plateId", template.getRowId())) %></td>
        <%
            }
            if (isAssayDesigner || c.hasPermission(getUser(), DeletePermission.class))
            {
        %>
        <td><%= text(((plateTemplates.size() > 1) ?
                textLink("delete", new ActionURL(PlateController.DeleteAction.class, getContainer()).
                        addParameter("templateName", template.getName()).
                        addParameter("plateId", template.getRowId()),
                        "return confirm('Permanently delete this plate template?')", null) :
                "Cannot delete the final template.")) %></td>
        <%
            }
        %>
    </tr>
<%
    }
    if (isAssayDesigner || c.hasPermission(getUser(), InsertPermission.class))
    {
%>
    <tr><td><br></td></tr>
    <% for (PlateTypeHandler handler : PlateManager.get().getPlateTypeHandlers())
    {
        for (Pair<Integer, Integer> size : handler.getSupportedPlateSizes())
        {
            int rows = size.getKey();
            int cols = size.getValue();
            int wellCount = rows * cols;
            String sizeDesc = wellCount + " well (" + rows + "x" + cols + ") ";
            ActionURL designerURL = new ActionURL(PlateController.DesignerAction.class, c);
            designerURL.addParameter("rowCount", rows);
            designerURL.addParameter("colCount", cols);
            designerURL.addParameter("assayType", handler.getAssayType());
            List<String> types = handler.getTemplateTypes(size);
            if (types == null || types.isEmpty())
            {
        %>
            <tr>
                <td colspan="4"><%= textLink("new " + sizeDesc + handler.getAssayType() + " template", designerURL)%></td>
            </tr>
        <%
            }
            for (String template : types)
            {
                designerURL.replaceParameter("templateType", template);
            %>
            <tr>
                <td colspan="4"><%= textLink("new " + sizeDesc + handler.getAssayType() + " " + template + " template", designerURL)%></td>
            </tr>
        <%  }
        }
    }%>
<%
    }
%>
</table>
