<%
/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController.ExportFolderForm" %>
<%@ page import="org.labkey.api.data.PHI" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
ViewContext context = getViewContext();
Container c = context.getContainerNoTab();
ExportFolderForm form = (ExportFolderForm) HttpView.currentModel();

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
    .child-checkbox {
        margin-left: 20px;
    }
</style>

<labkey:errors/>
<div id="exportForm"></div>

<script type="text/javascript">

Ext4.onReady(function(){

    // Literals for PHI
    var restrictedPhi = <%=PHI.Restricted.ordinal()%>;
    var fullPhi = <%=PHI.PHI.ordinal()%>;
    var limitedPhi = <%=PHI.Limited.ordinal()%>;
    var notPhi = <%=PHI.NotPHI.ordinal()%>;

    var maxAllowedPhiLevel = <%=form.getExportPhiLevel().ordinal()%>;
    var isIncludePhiChecked = (maxAllowedPhiLevel > notPhi);

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
                itemId: parentName,
                inputValue: parentName,
                checked: checked,
                objectType: "parent"
            });

            if (Ext4.isArray(children)) {
                Ext4.each(children, function(childName) {
                    childName = Ext4.util.Format.htmlEncode(childName);

                    formItemsCol1.push({
                        xtype: "checkbox",
                        fieldCls : 'child-checkbox',
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
            if (parentName == "Study") {
                showStudyOptions = true;
            }
        });

        var phiStore = Ext4.create('Ext.data.Store', {
            fields: ['label', 'value']
        });

        if (maxAllowedPhiLevel === restrictedPhi)
            phiStore.add({value: 'Restricted', label: 'Restricted, Full and Limited PHI'});
        if (maxAllowedPhiLevel >= fullPhi)
            phiStore.add({value: 'PHI',  label: 'Full and Limited PHI'});
        if (maxAllowedPhiLevel >= limitedPhi)
            phiStore.add({value: 'Limited', label: 'Limited PHI'});

        formItemsCol2.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Options:</span>'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!c.hasChildren()%>, boxLabel: 'Include Subfolders<%=PageFlowUtil.helpPopup("Include Subfolders", "Recursively export subfolders.")%>', name: 'includeSubfolders', objectType: 'otherOptions'});

        formItemsCol2.push({
            xtype: 'container',
            layout: 'hbox',
            items:[{
                xtype: 'checkbox',
                hideLabel: true,
                boxLabel: 'Include PHI Columns:<%=PageFlowUtil.helpPopup("Include PHI Columns", "Include all dataset and list columns, study properties, and specimen data that have been tagged with this PHI level or below.")%>&nbsp&nbsp',
                itemId: 'includePhi',
                name: 'includePhi',
                objectType: 'otherOptions',
                checked: isIncludePhiChecked,
                listeners: {
                    change: function(cmp, checked){
                        var combo = cmp.ownerCt.getComponent('phi_level');
                        if (combo) {
                            combo.setValue(checked ? getPhiValue(maxAllowedPhiLevel) : 'NotPHI');
                            combo.setDisabled(!checked);
                        }
                    }
                }
            }, {
                xtype: 'combobox',
                hideLabel: true,
                disabled : !isIncludePhiChecked,
                itemId : 'phi_level',
                name: 'exportPhiLevel',
                store: phiStore,
                displayField: 'label',
                valueField: 'value',
                queryMode: 'local',
                margin:'0 0 0 2',
                matchFieldWidth: false,
                width: 194,
                valueNotFoundText: 'NotPHI',
                value: getPhiValue(maxAllowedPhiLevel)
            }
        ]});

        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Shift <%=h(subjectNoun)%> Dates<%=PageFlowUtil.helpPopup("Shift Date Columns", "Selecting this option will shift selected date values associated with a " + h(subjectNounLowercase) + " by a random, " + h(subjectNounLowercase) + " specific, offset (from 1 to 365 days).")%>', fieldCls: 'shift-dates', name: 'shiftDates', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Export Alternate <%=h(subjectNoun)%> IDs<%=PageFlowUtil.helpPopup("Export Alternate " + h(subjectNoun) + " IDs", "Selecting this option will replace each " + h(subjectNounLowercase) + " id by an alternate randomly generated id.")%>', fieldCls: 'alternate-ids', name: 'alternateIds', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'checkbox', hideLabel: true, hidden: !showStudyOptions, boxLabel: 'Mask Clinic Names<%=PageFlowUtil.helpPopup("Mask Clinic Names", "Selecting this option will change the labels for clinics in the exported list of locations to a generic label (i.e. Clinic).")%>', name: 'maskClinic', objectType: 'otherOptions'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-announcement-title', html: '<span>Export to:</span>'});
        formItemsCol2.push({xtype: 'box', cls: 'labkey-title-area-line', html: ''});
        formItemsCol2.push({
            xtype: 'radiogroup',
            hideLabel: true,
            columns: 1,
            items: [
                {boxLabel: "Pipeline root <b>export</b> directory, as individual files", cls: 'export-location', name: "location", inputValue: 0, style:"margin-left: 2px"},
                {boxLabel: "Pipeline root <b>export</b> directory, as zip file", cls: 'export-location', name: "location", inputValue: 1, style:"margin-left: 2px"},
                {boxLabel: "Browser as zip file", cls: 'export-location', name: "location", inputValue: 2, checked: true, style:"margin-left: 2px"}
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
    };

    var getPhiValue = function(ordinal) {
        return  (ordinal === restrictedPhi) ? 'Restricted' :
                (ordinal === fullPhi) ? 'PHI' :
                (ordinal === limitedPhi) ? 'Limited' :
                'NotPHI';
    };

    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL("core", "getRegisteredFolderWriters"),
        method: 'POST',
        jsonData: {
            exportType: <%=q(form.getExportType().toString())%>
        },
        scope: this,
        success: function (response) {
            var responseText = Ext4.decode(response.responseText);
            initExportForm(responseText['writers']);
        }
    });
});

</script>

