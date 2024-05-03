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
<%@ page import="org.labkey.api.assay.plate.Plate" %>
<%@ page import="org.labkey.api.assay.plate.PlateService" %>
<%@ page import="org.labkey.api.assay.security.DesignAssayPermission" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.Link" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Option" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.assay.PlateController" %>
<%@ page import="org.labkey.assay.plate.PlateManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<PlateController.PlateTemplateListBean> me = (JspView<PlateController.PlateTemplateListBean>) HttpView.currentView();
    Container c = getContainer();
    List<? extends Plate> plates = me.getModelBean().getTemplates();
    Map<Plate, Integer> plateRunCount = new HashMap<>();
    boolean isAssayDesigner = c.hasPermission(getUser(), DesignAssayPermission.class);
    for (Plate plate : plates)
    {
        int count = PlateService.get().getRunCountUsingPlate(c, getUser(), plate);
        plateRunCount.put(plate, count);
    }
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
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

        createPlateTemplate = function(){

            let template = document.querySelector('#plate_template');
            if (template){
                window.location = template.value;
            }
        };

    })(jQuery);
</script>

<labkey:form method="POST" id="deleteForm" action="<%=new ActionURL(PlateController.DeleteAction.class, getContainer())%>">
    <input type="hidden" name="templateName" id="templateName" value="">
    <input type="hidden" name="plateId" id="plateId" value="">
</labkey:form>

<%
    if (isAssayDesigner || c.hasPermission(getUser(), InsertPermission.class))
    {
        List<Option> options = new ArrayList<>();
        for (PlateManager.PlateLayout layout : PlateManager.get().getPlateLayouts())
        {
            ActionURL designerURL = new ActionURL(PlateController.DesignerAction.class, c);
            designerURL.addParameter("rowCount", layout.type().getRows());
            designerURL.addParameter("colCount", layout.type().getColumns());
            designerURL.addParameter("assayType", layout.assayType());

            if (layout.name() != null)
                designerURL.replaceParameter("templateType", layout.name());

            options.add(new Option.OptionBuilder()
                    .label("new " + layout.description() + " template")
                    .value(designerURL.toString())
                    .build());
        }
%>
<h4>Create New Plate</h4>
<labkey:form method="POST" layout="inline" id="qc_form">
    <%= new Select.SelectBuilder()
            .name("template")
            .id("plate_template")
            .layout(Input.Layout.INLINE)
            .required(true)
            .formGroup(true)
            .addOptions(options)
    %>
    <labkey:button text="create" submit="false" onclick="createPlateTemplate();" id="create-btn"/>
</labkey:form>
<%
    }
%>
<br/>
<h4>Available Plates</h4>
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">Usage Count</td>
        <td class="labkey-column-header">&nbsp;</td>
    </tr>
<%
    int index = 0;
    for (Plate plate : plates)
    {
        Integer runCount = plateRunCount.get(plate);

        Link.LinkBuilder editLink = new Link.LinkBuilder("edit");
        if (runCount > 0)
        {
            editLink.tooltip("Plate template is used by " + runCount + " runs and can't be edited")
                    .clearClasses()
                    .addClass("labkey-disabled-text-link");
        }
        else
        {
            editLink.href(plate.detailsURL());
        }
%>
    <tr class="<%=getShadeRowClass(index)%>">
        <td><%= h(plate.getName()) %></td>
        <td><%= h(plate.getAssayType()) %></td>
        <td><%= h(runCount) %></td>
        <td>
        <%
            if (isAssayDesigner || c.hasPermission(getUser(), UpdatePermission.class))
            {
        %>
            <%= editLink %>
        <%
            }
            if (isAssayDesigner || c.hasPermission(getUser(), InsertPermission.class))
            {
        %>
            <%= link("edit a copy", new ActionURL(PlateController.DesignerAction.class, getContainer()).
                addParameter("copy", true).
                addParameter("templateName", plate.getName()).
                addParameter("plateId", plate.getRowId())) %>
        <%
            }
            if (c.hasPermission(getUser(), InsertPermission.class))
            {
        %>
            <%= link("copy to another folder", new ActionURL(PlateController.CopyTemplateAction.class, getContainer()).
                addParameter("plateId", plate.getRowId())) %>
        <%
            }
            if (isAssayDesigner || c.hasPermission(getUser(), DeletePermission.class))
            {
                if (plates.size() > 1)
                {
                    Link.LinkBuilder deleteLink = new Link.LinkBuilder("delete");
                    if (runCount > 0)
                    {
                        deleteLink.tooltip("Plate template is used by " + runCount + " runs and can't be deleted")
                                .clearClasses()
                                .addClass("labkey-disabled-text-link");
                    }
                    else
                    {
                        deleteLink.onClick("deletePlate(" + q(plate.getName()) + "," + plate.getRowId() + ")");
                    }
        %>
                    <%= deleteLink %>
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

    if (plates == null || plates.isEmpty())
    {
%>
        <tr><td colspan="2" style="padding: 3px;">No plates available.</td></tr>
<%
    }
%>
</table>
