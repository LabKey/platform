<%
/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.ExportFolderForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("AdminWizardForm.js");
    }
%>
<%
ViewContext context = getViewContext();
Container c = context.getContainerNoTab();
ExportFolderForm form = (ExportFolderForm) HttpView.currentModel();

Study study = StudyService.get() != null ? StudyService.get().getStudy(c) : null;
String subjectNoun = study != null ? study.getSubjectNounSingular() : null;
String subjectNounLowercase = subjectNoun != null ? subjectNoun.toLowerCase() : null;

boolean isCloudRoot = FileContentService.get().isCloudRoot(c);
%>

<style type="text/css">
    .labkey-announcement-title {
        padding: 30px 0 5px 0 !important;
    }
    .labkey-title-area-line {
        margin-right: 15px !important;
    }
    .child-checkbox {
        margin-left: 20px;
    }
</style>

<labkey:errors/>
<div id="exportForm"></div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

Ext4.onReady(function(){
    var isCloudRoot = <%=isCloudRoot%>;
    var initExportForm = function(folderWriters) {
        var formItemsCol1 = [],
            formItemsCol2 = [],
            showStudyOptions = false;

        formItemsCol1.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Folder objects to export:</span>'});
        formItemsCol1.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});

        Ext4.each(folderWriters, function(writer) {
            var parentName = Ext4.util.Format.htmlEncode(writer['name']),
                checked = writer['selectedByDefault'],
                children = writer['children'];

            formItemsCol1.push({
                xtype: "checkbox",
                hideLabel: true,
                boxLabel: parentName,
                name: "types",
                itemId: parentName.replaceAll(',', ''),
                inputValue: parentName,
                checked: checked,
                objectType: "parent"
            });

            if (Ext4.isArray(children)) {
                Ext4.each(children, function(child) {
                    var childName = Ext4.util.Format.htmlEncode(child.name);

                    formItemsCol1.push({
                        xtype: "checkbox",
                        fieldCls : 'child-checkbox',
                        hideLabel: true,
                        boxLabel: childName,
                        name: "types",
                        itemId: childName.replaceAll(',', ''),
                        inputValue: childName,
                        checked: child.selectedByDefault,
                        objectType: "child",
                        parentId: parentName.replaceAll(',', '')
                    });
                });
            }

            // if there is a study writer shown, set a boolean variable so we know whether or not the show the study related options
            if (parentName == "Study") {
                showStudyOptions = true;
            }
        });

        const subjectNoun = <%=q(subjectNoun)%>;
        const subjectNounLowercase = <%=q(subjectNounLowercase)%>;
        const popupSubfolder = LABKEY.export.Util.helpPopup("Include Subfolders", "Recursively export subfolders.");
        const popupShiftDate = LABKEY.export.Util.helpPopup("Shift Date Columns", "Selecting this option will shift selected date values associated with a " + subjectNounLowercase + " by a random, " + subjectNounLowercase + " specific, offset (from 1 to 365 days).");
        const popupAlternate = LABKEY.export.Util.helpPopup("Export Alternate " + subjectNoun + " IDs", "Selecting this option will replace each " + subjectNounLowercase + " id by an alternate randomly generated id.");
        const popupClinic = LABKEY.export.Util.helpPopup("Mask Clinic Names", "Selecting this option will change the labels for clinics in the exported list of locations to a generic label (i.e. Clinic).");

        formItemsCol2.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Options:</span>'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!c.hasChildren()%>, boxLabel: 'Include Subfolders' + popupSubfolder.html, name: 'includeSubfolders', objectType: 'otherOptions'});

        // shared PHI option component
        formItemsCol2.push({xtype: 'labkey_phi_option', maxAllowedLevel: <%=q(form.getExportPhiLevel().name())%>});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Shift <%=h(subjectNoun)%> Dates' + popupShiftDate.html, fieldCls: 'shift-dates', name: 'shiftDates', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Export Alternate <%=h(subjectNoun)%> IDs' + popupAlternate.html, fieldCls: 'alternate-ids', name: 'alternateIds', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Mask Clinic Names' + popupClinic.html, name: 'maskClinic', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Export to:</span>'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});
        formItemsCol2.push({
            xtype: 'radiogroup',
            hideLabel: true,
            columns: 1,
            items: [
                {boxLabel: "Pipeline root <b>export</b> directory, as individual files", cls: 'export-location', name: "location", inputValue: <%= AdminController.ExportOption.PipelineRootAsFiles.ordinal() %>, style:"margin-left: 2px", disabled: isCloudRoot},
                {boxLabel: "Pipeline root <b>export</b> directory, as zip file", cls: 'export-location', name: "location", inputValue: <%= AdminController.ExportOption.PipelineRootAsZip.ordinal() %>, style:"margin-left: 2px"},
                {boxLabel: "Browser as zip file", cls: 'export-location', name: "location", inputValue: <%= AdminController.ExportOption.DownloadAsZip.ordinal() %>, checked: true, style:"margin-left: 2px"}
            ]
        });
        formItemsCol2.push({xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF });

        var exportForm = new Ext4.form.Panel({
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
            }, {
                text:'Clear All Objects',
                handler: function(btn) {
                    const leftColumnItems = exportForm.items.items[0].items.items;
                    for (const item of leftColumnItems)
                        if (item.xtype === 'checkbox')
                            item.setValue(false);
                }
            }, {
                text:'Reset',
                handler: function(btn) {
                    document.getElementById('exportForm').innerHTML = '';
                    initializeForm(initExportForm);
                }
            }],
            buttonAlign:'left',
            listeners : {
                render : {
                    fn : function(panel) {
                        Ext4.each(Ext4.ComponentQuery.query('checkbox[objectType=parent]'), function(cmp) {

                            cmp.on("change", function(cmp, checked) {
                                var children = Ext4.ComponentQuery.query('checkbox[parentId=' + cmp.getItemId() + ']')
                                Ext4.each(children, function(child) {
                                    child.setValue(checked);
                                    child.setDisabled(!checked);
                                });
                            });
                        });
                    }
                }
            }
        });

        // register event handlers
        popupSubfolder.callback();
        popupShiftDate.callback();
        popupAlternate.callback();
        popupClinic.callback();
    };

    initializeForm(initExportForm);
});

function initializeForm(initExportForm)
{
    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL("core", "getRegisteredFolderWriters"),
        method: 'POST',
        jsonData: {
            exportType: <%=q(form.getExportType().toString())%>
        },
        scope: this,
        success: function (response) {
            const responseText = Ext4.decode(response.responseText);
            initExportForm(responseText['writers']);
        }
    });
}

</script>

