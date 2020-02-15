<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.plate.PlateTemplate" %>
<%@ page import="org.labkey.api.assay.plate.PlateTypeHandler" %>
<%@ page import="org.labkey.api.assay.security.DesignAssayPermission" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.assay.PlateController" %>
<%@ page import="org.labkey.assay.plate.PlateManager" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<PlateController.PlateTemplateListBean> me = (JspView<PlateController.PlateTemplateListBean>) HttpView.currentView();
    Container c = getContainer();
    List<? extends PlateTemplate> plateTemplates = me.getModelBean().getTemplates();
%>

<script type="application/javascript">

    (function($){

        deletePlate = function(templateName, plateId){

            LABKEY.Utils.modal("Delete plate", null, function(){
                // set the form field values and then submit the delete form after confirmation
                $('#templateName').val(templateName);
                $('#plateId').val(plateId);
                showDialog();

            }, null);
        };

        showDialog = function() {
            var html = [
                "<div style='margin-bottom: 20px;'>",
                "<div id='message'>",
                "Permanently delete this plate template?",
                "</div>",
                "<a class='btn btn-default' style='float: right' id='cancelSubmitBtn'>No</a>",
                "<a class='btn btn-default' style='float: right; margin-right: 8px' id='confirmSubmitBtn'>Yes</a>",
                "</div>"
            ].join("");

            $("#modal-fn-body").html(html);

            $("#confirmSubmitBtn").on("click", function() {
                $('#deleteForm').submit();
            });

            $("#cancelSubmitBtn").on("click", function() {
                $('#lk-utils-modal').modal('hide');
            });
        };

    })(jQuery);
</script>

<labkey:form method="POST" id="deleteForm" action="<%=new ActionURL(PlateController.DeleteAction.class, getContainer())%>">
    <input type="hidden" name="templateName" id="templateName" value="">
    <input type="hidden" name="plateId" id="plateId" value="">
</labkey:form>

<h4>Available Plate Templates</h4>
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">&nbsp;</td>
    </tr>
<%
    int index = 0;
    boolean isAssayDesigner = c.hasPermission(getUser(), DesignAssayPermission.class);
    for (PlateTemplate template : plateTemplates)
    {
%>
    <tr class="<%=getShadeRowClass(index)%>">
        <td><%= h(template.getName()) %></td>
        <td><%= h(template.getType()) %></td>
        <td>
        <%
            if (isAssayDesigner || c.hasPermission(getUser(), UpdatePermission.class))
            {
        %>
            <%= link("edit", new ActionURL(PlateController.DesignerAction.class, getContainer()).
                addParameter("templateName", template.getName()).
                addParameter("plateId", template.getRowId())) %>
        <%
            }
            if (isAssayDesigner || c.hasPermission(getUser(), InsertPermission.class))
            {
        %>
            <%= link("edit a copy", new ActionURL(PlateController.DesignerAction.class, getContainer()).
                addParameter("copy", true).
                addParameter("templateName", template.getName()).
                addParameter("plateId", template.getRowId())) %>
        <%
            }
            if (c.hasPermission(getUser(), InsertPermission.class))
            {
        %>
            <%= link("copy to another folder", new ActionURL(PlateController.CopyTemplateAction.class, getContainer()).
                addParameter("templateName", template.getName()).
                addParameter("plateId", template.getRowId())) %>
        <%
            }
            if (isAssayDesigner || c.hasPermission(getUser(), DeletePermission.class))
            {
                if (plateTemplates.size() > 1)
                {
        %>
                    <a href="javascript:{}" onclick="deletePlate(<%=q(template.getName())%>, <%=template.getRowId()%>);" class="labkey-text-link">delete</a>
        <%
                }
                else
                {
        %>
                    Cannot delete the final template.
        <%
                }
            }
        %>
        </td>
    </tr>
<%
        index++;
    }

    if (plateTemplates == null || plateTemplates.isEmpty())
    {
%>
        <tr><td colspan="2" style="padding: 3px;">No plate templates available.</td></tr>
<%
    }
%>
</table>

<%
    if (isAssayDesigner || c.hasPermission(getUser(), InsertPermission.class))
    {
%>
    <br/>
    <h4>Create New Plate Templates</h4>
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
                <%= link("new " + sizeDesc + handler.getAssayType() + " template", designerURL)%><br/>
        <%
            }
            for (String template : types)
            {
                designerURL.replaceParameter("templateType", template);
            %>
                <%= link("new " + sizeDesc + handler.getAssayType() + " " + template + " template", designerURL)%><br/>
        <%  }
        }
    }%>
<%
    }
%>
