<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.admin.FolderManagementAction" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext3"));
        return resources;
    }
%>
<%
ViewContext context = getViewContext();
Container c = context.getContainerNoTab();
FolderManagementAction.FolderManagementForm form = (FolderManagementAction.FolderManagementForm) HttpView.currentModel();

Study study = StudyService.get() != null ? StudyService.get().getStudy(c) : null;
String subjectNoun = study != null ? study.getSubjectNounSingular() : null;
String subjectNounLowercase = subjectNoun != null ? subjectNoun.toLowerCase() : null;
%>

<style type="text/css">
    .labkey-announcement-title {
        padding: 30px 0 5px 0 !important;
    }
    .labkey-title-area-line {
        margin-right: 15px !important;
    }
</style>

<labkey:errors/>
<div id="exportForm"></div>

<script type="text/javascript">

Ext.onReady(function(){

    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL("core", "getRegisteredFolderWriters"),
        method: 'POST',
        jsonData: {
            exportType: <%=q(form.getExportType().toString())%>
        },
        scope: this,
        success: function (response)
        {
            var responseText = Ext.decode(response.responseText);
            initExportForm(responseText['writers']);
        }
    });

    var initExportForm = function(folderWriters)
    {
        var formItemsCol1 = [],
            formItemsCol2 = [],
            showStudyOptions = false;

        formItemsCol1.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Folder objects to export:</span>'});
        formItemsCol1.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});

        Ext.each(folderWriters, function(writer)
        {
            var parentName = Ext.util.Format.htmlEncode(writer['name']),
                checked = writer['selectedByDefault'],
                children = writer['children'];

            formItemsCol1.push({
                xtype: "checkbox",
                hideLabel: true,
                boxLabel: parentName,
                name: "types",
                itemId: parentName,
                inputValue: parentName,
                checked: checked,
                objectType: "parent"
            });

            if (Ext.isArray(children))
            {
                Ext.each(children, function(childName)
                {
                    childName = Ext.util.Format.htmlEncode(childName);

                    formItemsCol1.push({
                        xtype: "checkbox",
                        style: {marginLeft: "20px"},
                        hideLabel: true,
                        boxLabel: childName,
                        name: "types",
                        itemId: childName,
                        inputValue: childName,
                        checked: checked,
                        objectType: "child",
                        parentId: parentName
                    });
                });
            }

            // if there is a study writer shown, set a boolean variable so we know whether or not the show the study related options
            if (parentName == "Study")
            {
                showStudyOptions = true;
            }
        });

        formItemsCol1.push({xtype: "spacer", height: 20});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Options:</span>'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!c.hasChildren()%>, boxLabel: 'Include Subfolders<%=PageFlowUtil.helpPopup("Include Subfolders", "Recursively export subfolders.")%>', name: 'includeSubfolders', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, boxLabel: 'Remove All Columns Tagged as Protected<%=PageFlowUtil.helpPopup("Remove Protected Columns", "Selecting this option will exclude all dataset, list, and specimen columns that have been tagged as protected columns.")%>', name: 'removeProtected', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Shift <%=h(subjectNoun)%> Dates<%=PageFlowUtil.helpPopup("Shift Date Columns", "Selecting this option will shift selected date values associated with a " + h(subjectNounLowercase) + " by a random, " + h(subjectNounLowercase) + " specific, offset (from 1 to 365 days).")%>', name: 'shiftDates', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Export Alternate <%=h(subjectNoun)%> IDs<%=PageFlowUtil.helpPopup("Export Alternate " + h(subjectNoun) + " IDs", "Selecting this option will replace each " + h(subjectNounLowercase) + " id by an alternate randomly generated id.")%>', name: 'alternateIds', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Mask Clinic Names<%=PageFlowUtil.helpPopup("Mask Clinic Names", "Selecting this option will change the labels for clinics in the exported list of locations to a generic label (i.e. Clinic).")%>', name: 'maskClinic', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Export to:</span>'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});
        formItemsCol2.push({
            xtype: 'radiogroup',
            hideLabel: true,
            columns: 1,
            items: [
                {boxLabel: "Pipeline root <b>export</b> directory, as individual files", name: "location", inputValue: 0, style:"margin-left: 2px"},
                {boxLabel: "Pipeline root <b>export</b> directory, as zip file", name: "location", inputValue: 1, style:"margin-left: 2px"},
                {boxLabel: "Browser as zip file", name: "location", inputValue: 2, checked: true, style:"margin-left: 2px"}
            ]
        });
        formItemsCol2.push({xtype: "spacer", height: 20});

        var exportForm = new LABKEY.ext.FormPanel({
            renderTo: 'exportForm',
            border: false,
            standardSubmit: true,
            layout: 'column',
            defaults: {
                xtype: 'container',
                layout: 'form'
            },
            items: [{
                items: formItemsCol1,
                width: 450
            },{
                items: formItemsCol2,
                width: 450
            }],
            buttons:[{
                text:'Export',
                type:'submit',
                handler: function(btn) {
                    // disable the export button if we are exporting to the pipeline root
                    if (exportForm.getForm().getValues().location != 2)
                        btn.disable();

                    exportForm.getForm().submit();
                }
            }],
            buttonAlign:'left'
        });

        // add listeners to each of the parent checkboxes
        var parentCbs = exportForm.find("objectType", "parent");
        Ext.each(parentCbs, function(cb) {
            cb.on("check", function(cmp, checked) {
                var children = exportForm.find("parentId", cb.getItemId());
                Ext.each(children, function(child) {
                    child.setValue(checked);
                    child.setDisabled(!checked);
                });
            });
        });
    }
});

</script>

