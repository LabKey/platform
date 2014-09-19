<%
/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.FolderWriter" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.writer.Writer" %>
<%@ page import="org.labkey.core.admin.FolderManagementAction" %>
<%@ page import="org.labkey.core.admin.writer.FolderSerializationRegistryImpl" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.LinkedList" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext3"));
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

<labkey:errors/>
<div id="exportForm"></div>

<script type="text/javascript">

    Ext.onReady(function(){

        var formItems = [{xtype: "label", text: "Folder objects to export:"}];
<%
            Collection<FolderWriter> writers = new LinkedList<>(FolderSerializationRegistryImpl.get().getRegisteredFolderWriters());
            boolean showStudyOptions = false;
            for (FolderWriter writer : writers)
            {
                String parent = writer.getSelectionText();
                if (null != parent && writer.show(c) && !(c.isDataspace() && "Study".equals(parent)))
                {
                    boolean checked = writer.includeInType(form.getExportType());
                    %>formItems.push({xtype: "checkbox", hideLabel: true, boxLabel: "<%=parent%>", name: "types", itemId: "<%=parent%>", inputValue: "<%=parent%>", checked: <%=checked%>, objectType: "parent"});<%

                    Collection<Writer> children = writer.getChildren(true);
                    if (null != children && children.size() > 0)
                    {
                        for (Writer child : children)
                        {
                            if (null != child.getSelectionText())
                            {
                                String text = child.getSelectionText();
                                %>
                                formItems.push({xtype: "checkbox", style: {marginLeft: "20px"}, hideLabel: true, boxLabel: "<%=text%>", name: "types", itemId: "<%=text%>",
                                   inputValue: "<%=text%>", checked: <%=checked%>, objectType: "child", parentId: "<%=parent%>"});
                                <%
                            }
                        }
                    }

                    // if there is a study writer shown, set a boolean variable so we know whether or not the show the study related options
                    if ("Study".equals(parent))
                        showStudyOptions = true;
                }
            }
%>
        formItems.push({xtype: "spacer", height: 20});
        formItems.push({xtype: "label", text: "Options:"});
        formItems.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!c.hasChildren()%>, boxLabel: 'Include Subfolders<%=PageFlowUtil.helpPopup("Include Subfolders", "Recursively export subfolders.")%>', name: 'includeSubfolders', objectType: 'otherOptions'});
        formItems.push({xtype: 'checkbox', hideLabel: true, boxLabel: 'Remove All Columns Tagged as Protected<%=PageFlowUtil.helpPopup("Remove Protected Columns", "Selecting this option will exclude all dataset, list, and specimen columns that have been tagged as protected columns.")%>', name: 'removeProtected', objectType: 'otherOptions'});
        formItems.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!showStudyOptions%>, boxLabel: 'Shift <%=h(subjectNoun)%> Dates<%=PageFlowUtil.helpPopup("Shift Date Columns", "Selecting this option will shift selected date values associated with a " + h(subjectNounLowercase) + " by a random, " + h(subjectNounLowercase) + " specific, offset (from 1 to 365 days).")%>', name: 'shiftDates', objectType: 'otherOptions'});
        formItems.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!showStudyOptions%>, boxLabel: 'Export Alternate <%=h(subjectNoun)%> IDs<%=PageFlowUtil.helpPopup("Export Alternate " + h(subjectNoun) + " IDs", "Selecting this option will replace each " + h(subjectNounLowercase) + " id by an alternate randomly generated id.")%>', name: 'alternateIds', objectType: 'otherOptions'});
        formItems.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!showStudyOptions%>, boxLabel: 'Mask Clinic Names<%=PageFlowUtil.helpPopup("Mask Clinic Names", "Selecting this option will change the labels for clinics in the exported list of locations to a generic label (i.e. Clinic).")%>', name: 'maskClinic', objectType: 'otherOptions'});
        formItems.push({xtype: "spacer", height: 20});
        formItems.push({xtype: "label", text: "Export to:"});
        formItems.push({
            xtype: 'radiogroup',
            hideLabel: true,
            columns: 1,
            items: [
                {boxLabel: "Pipeline root <b>export</b> directory, as individual files", name: "location", inputValue: 0},
                {boxLabel: "Pipeline root <b>export</b> directory, as zip file", name: "location", inputValue: 1},
                {boxLabel: "Browser as zip file", name: "location", inputValue: 2, checked: true}
            ]
        });

        var exportForm = new LABKEY.ext.FormPanel({
            renderTo: 'exportForm',
            border: false,
            standardSubmit: true,
            items:formItems,
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
    });

</script>

