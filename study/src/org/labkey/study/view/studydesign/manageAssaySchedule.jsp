<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.security.permissions.ManageStudyPermission" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromPath("Ext4ClientApi"));
      resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.js"));
      resources.add(ClientDependency.fromPath("dataview/DataViewsPanel.css"));
      return resources;
  }
%>
<%
    JspView<StudyDesignController.AssayScheduleForm> me = (JspView<StudyDesignController.AssayScheduleForm>) HttpView.currentView();
    StudyDesignController.AssayScheduleForm form = me.getModelBean();

    Container c = getContainer();
    User user = getUser();
    ActionURL returnURL = getActionURL();

    Study study = StudyManager.getInstance().getStudy(getContainer());
    boolean canManageStudy = c.hasPermission(user, ManageStudyPermission.class);
    boolean isDataspace = c.isProject() && c.isDataspace();

    String visitDisplayName = "Visit";
    if (study != null && study.getTimepointType() == TimepointType.DATE)
        visitDisplayName = "Timepoint";
%>

<style>
   .rotated_table td.x4-grid-cell
   {
       background-color: transparent !important;
       border-style: none;
   }
   .rotated_table .x4-grid-row-alt
   {
       background-color: #EEEEEE;
   }

   .x4-panel-header-default
   {
       background-color: transparent;
       background-image: none !important;
       border: none;
   }
   .x4-panel-header-text-container-default
   {
       font-size: 15px;
       color: black;
   }
</style>

<script type="text/javascript">
(function()
{
    var X = Ext4;
    var _gridSC, _storeSC, _panelSV,  _storeSV, _storeV, _panelAP, _addVisitWindow;
    var _configVisitMap = {};
    var isDataspace = <%=isDataspace%>;

    X.onReady(function(){

    var scColumns = ['RowId', 'AssayName', 'Description'];
<%
    if (form.isUseAlternateLookupFields())
    {
        %>scColumns.push('Source');<%
        %>scColumns.push('LocationId');<%
        %>scColumns.push('TubeType');<%
    }
    else
    {
        %>scColumns.push('Lab');<%
        %>scColumns.push('SampleType');<%
    }
%>

    //create a Store bound to the 'AssaySpecimen' table in the 'study' schema
    _storeSC = X.create('LABKEY.ext4.Store', {
        schemaName: 'study',
        queryName: 'AssaySpecimen',
        columns : scColumns,
        sort : ['Description', 'AssayName']
    });
    zz = _storeSC;

    _storeSV = X.create('LABKEY.ext4.Store', {
        schemaName: 'study',
        queryName: 'AssaySpecimenVisit',
        columns : ['RowId', 'VisitId', 'AssaySpecimenId']
    });

    _storeV = X.create('LABKEY.ext4.Store', {
        schemaName: 'study',
        queryName: 'Visit',
        sort: 'DisplayOrder,SequenceNumMin'
    });

    _onStoresLoaded(_renderVisitGrid, [_storeSC, _storeSV, _storeV]);
    _storeSC.load();
    _storeSV.load();
    _storeV.load();

    _gridSC = X.create('LABKEY.ext4.GridPanel', {
        store: _storeSC,
        renderTo: 'AssaySpecimenConfigGrid',
        maxWidth: 1000,
        autoHeight: true,
        selType: 'rowmodel',
        multiSelect: false,
        forceFit: true,
        title: 'Assay Configurations',
        editable: true,
        emptyText: 'No assay configurations',
        dockedItems: [{
            xtype: 'toolbar',
            dock: 'top',
            border: false,
            hidden: isDataspace,
            items: [
            {
                text: 'Insert New',
                handler : function(){ showUpdateConfigurationDialog(); }
            },
            {
                itemId: 'removeAssaySpecimen',
                text: 'Delete',
                handler: function()
                {
                    X.Msg.show({
                        cls: 'data-window',
                        title: "Confirm Deletion",
                        msg: "Are you sure you want to delete the selected assay configuration and all of its related <%=h(visitDisplayName.toLowerCase())%> mapping information?",
                        icon: X.Msg.QUESTION,
                        buttons: X.Msg.YESNO,
                        fn: function(button){
                                if (button === 'yes') {
                                    removeAssaySpecimen();
                                }
                            }
                    });

                    function removeAssaySpecimen()
                    {
                        // remove the record from the AssaySpecimen configuration grid
                        var sm = _gridSC.getSelectionModel();
                        if (sm.getSelection().length == 1)
                        {
                            var scRowId = sm.getSelection()[0].get("RowId");
                            _storeSC.remove(sm.getSelection());

                            // delete any related records from the visit configuration
                            if (_configVisitMap[scRowId])
                            {
                                var rowsToDelete = [];
                                X.each(_configVisitMap[scRowId], function(visit){
                                    rowsToDelete.push({RowId:visit.rowId, container:LABKEY.container.id});
                                });

                                LABKEY.Query.deleteRows({
                                    schemaName: 'study',
                                    queryName: 'AssaySpecimenVisit',
                                    rows: rowsToDelete,
                                    successCallback: function(data)
                                    {
                                        _storeSC.sync({success: function(){ window.location.reload(); }});
                                    },
                                    failure : function(response)
                                    {
                                        Ext4.Msg.alert('Error', response.exception);
                                    }
                                });
                            }
                            else
                                _storeSC.sync({success: function(){ window.location.reload(); }});
                        }
                    }
                },
                disabled: true
            }]
        }],
        listeners:
        {
            'selectionchange': function(view, records)
            {
                _gridSC.down('#removeAssaySpecimen').setDisabled(!records.length);
            }
        }
    });

    _gridSC.on("columnmodelcustomize", function(grid, columnModel)
    {
        X.each(columnModel, function(column){
            if (column.dataIndex.toLowerCase() == "rowid")
                column.hidden = true;
        });
    });

    // block the default LABKEY.ext4.GridPanel cellediting using beforeedit and add our own double click event
    _gridSC.on('beforeedit', function(){ return false; });
    if (!isDataspace)
        _gridSC.on('itemdblclick', showUpdateConfigurationDialog);

    _panelSV = X.create('Ext.panel.Panel', {
        renderTo : 'AssaySpecimenVisitPanel',
        title : 'Assay Schedule',
        bodyCls: "x-panel",
        bodyStyle: "background-color: transparent;"
    });

    // query the StudyProperties table for the initial assay plan value
    LABKEY.Query.selectRows({
        schemaName: 'study',
        queryName: 'StudyProperties',
        columns: 'AssayPlan',
        success: function(data) {
            if (data.rows.length == 1)
                createAssayPlanPanel(data.rows[0]["AssayPlan"]);
        }
    });

    // text link to open dialog to create a new visit
    Ext4.create('LABKEY.ext4.LinkButton', {
        renderTo: 'CreateNewVisitTextLink',
        text: 'Create New <%=h(visitDisplayName)%>',
        handler: function() {
            var win = Ext4.create('LABKEY.ext4.VaccineDesignAddVisitWindow', {
                title: 'Add <%=h(visitDisplayName)%>',
                visitNoun: <%=q(visitDisplayName)%>,
                allowSelectExistingVisit: false,
                listeners: {
                    scope : this,
                    closeWindow : function() { win.close(); },
                    newVisitCreated : function(newVisitData) {
                        // reload the page to update the visit map section
                        window.location.reload();
                    }
                }
            });
            win.show();
        }
    });

    var projectMenu = null;
    if (LABKEY.container.type != "project")
    {
        var projectPath = LABKEY.container.path.substring(0, LABKEY.container.path.indexOf("/", 1));
        projectMenu = {
            text: 'Project',
            menu: {
                items: [{
                    text: 'Assays',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignAssays'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'Labs',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignLabs'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'Sample Types',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignSampleTypes'}),
                    hrefTarget: '_blank'  // issue 19493
                }]
            }
        };
    }

    var folderMenu = {
        text: 'Folder',
        menu: {
            items: [{
                text: 'Assays',
                href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignAssays'}),
                hrefTarget: '_blank'  // issue 19493
            },{
                text: 'Labs',
                href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignLabs'}),
                hrefTarget: '_blank'  // issue 19493
            },{
                text: 'Sample Types',
                href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignSampleTypes'}),
                hrefTarget: '_blank'  // issue 19493
            }]
        }
    };

    var menu = Ext4.create('Ext.button.Button', {
        text: 'Configure',
        renderTo: 'config-dropdown-menu',
        menu: projectMenu ? {items: [projectMenu, folderMenu]} : folderMenu.menu
    });
});

function createAssayPlanPanel(value)
{
    _panelAP = X.create('Ext.form.Panel', {
        renderTo : 'AssayPlanPanel',
        title : 'Assay Plan',
        bodyStyle: "background-color: transparent; padding-top: 5px;",
        width: 500,
        border: false,
        items: [{
            xtype: 'textarea',
            name: 'assayPlan',
            value: value,
            width: 500,
            height: 100,
            listeners: {
                change: function() {
                    _panelAP.down('.button').enable();
                }
            }
        }],
        dockedItems: [{
            xtype: 'toolbar',
            dock: 'bottom',
            ui: 'footer',
            style : 'background-color: transparent;',
            items: [{
                xtype: 'button',
                text: 'Save',
                disabled: true,
                handler: function() {
                    var form = _panelAP.getForm();
                    var values = form.getValues();

                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('study-design', 'updateAssayPlan.api'),
                        method  : 'POST',
                        jsonData: values,
                        success: function(response) {
                            window.location.reload();
                        },
                        failure: function(response) {
                            Ext4.Msg.alert('Error', response.statusText);
                        },
                        scope   : this
                    });
                }
            }]
        }]
    });
}

function showUpdateConfigurationDialog(grid, record, item, index)
{
    var formItems = [];
    X.each(_gridSC.columns, function(column){
        var formItem;
        if (column.editor)
        {
            // assay, lab, and sample type lookups should use container filter of CurrentPlusProject
            if (column.dataIndex == "Lab" || column.dataIndex == "SampleType")
            {
                var displayField = column.dataIndex == "SampleType" ? "Name" : "Label";
                var lookupTableName = 'StudyDesign' + column.dataIndex + 's';
                formItem = new LABKEY.ext4.VaccineDesignDisplayHelper().getStudyDesignFieldEditor(column.dataIndex, lookupTableName, false, column.header, true, displayField);
            }
            else if (column.dataIndex == "AssayName")
            {
                formItem = new LABKEY.ext4.VaccineDesignDisplayHelper().getStudyDesignFieldEditor(column.dataIndex, "StudyDesignAssays", false, column.header, true, "Label");
                formItem.editable = true; // Rho use case
            }
            else
            {
                formItem = column.editor;
                formItem.fieldLabel = column.header || column.text;
                formItem.name = column.dataIndex;
                formItem.helpPopup = null;
                formItem.tabIndex = undefined; // issue 19477
            }

            formItem.value = record ? record.get(column.dataIndex) : null;
            formItems.push(formItem);
        }
    });

    var win = X.create('Ext.window.Window', {
        cls: 'data-window',
        border: false,
        modal: true,
        minWidth: 310,  // for intermittent selenium test failure
        minHeight: 210,
        bodyStyle: 'padding: 5px;',
        title: record ? 'Edit Assay Configuration' : ' Add Assay Configuration',
        items: [{
            itemId: 'recordWindowFormPanel',
            xtype: 'form',
            border: false,
            bodyStyle: 'padding: 5px;',
            defaults: {labelWidth: 125},
            items: formItems
        }],
        buttonAlign: 'center',
        buttons: [{
            text: 'Submit',
            handler: function() {
                // either update the given record or add a new one
                var commands = [];
                var values = win.down('#recordWindowFormPanel').getForm().getValues();
                if (record)
                {
                    X.each(values, function(val) {
                        record.set(val, values[val]);
                    });
                    commands.push({
                        schemaName: 'study',
                        queryName: 'AssaySpecimen',
                        command: 'update',
                        rows: [record.data]
                    });
                }
                else
                {
                    commands.push({
                        schemaName: 'study',
                        queryName: 'AssaySpecimen',
                        command: 'insert',
                        rows: [values]
                    });
                }

                LABKEY.Query.saveRows({
                    commands: commands,
                    success: function(data) {
                        if (record) record.commit();
                        win.close();

                        // reload the page to update the visit map section
                        window.location.reload();
                    }
                });
            },
            scope: this
        },{
            text: 'Cancel',
            handler: function() { win.close(); }
        }]
    });

    // issue 19477: give focus to first field in form
    win.on('show', function(cmp) {
        cmp.down('.textfield').focus();
    });

    win.show();
}

function _onStoresLoaded(fn, stores)
{
    var count = stores.length;
    var cb = function() { if (--count==0) fn(); };
    for (var i=0 ; i<count ; i++)
        stores[i].on("load", cb, window, {single:true});
}

function h(txt)
{
    return typeof txt == "number" ? String(txt) : txt ? X.util.Format.htmlEncode(txt) : "";
}


function _renderVisitGrid()
{
    function getItemData(item, name)
    {
        return item.data[name] || item.data[name.toLowerCase()];
    }

    _storeSV.each(function(item, index, count)
    {
        var sc = getItemData(item, 'AssaySpecimenId');
        var v = getItemData(item, 'VisitId');
        var rowid =  getItemData(item, 'RowId');
        if (sc && v && rowid)
        {
            if (!_configVisitMap[sc])
                _configVisitMap[sc] = [];
            _configVisitMap[sc].push({visitId: v, rowId: rowid});
        }
    });

    var html = [];

    html.push("<table id='assaySpecimenVisitMappingTable' class='x4-grid-table rotated_table'>");
    html.push("<tr><td>&nbsp;</td><td>&nbsp;</td>");

    var maxChars = 10;
    _storeV.each(function(item, index, count)
    {
        var label = item.data.Label||item.data.SequenceNumMin;
        maxChars = label.toString().length > maxChars ? label.toString().length : maxChars;
    });
    var height = maxChars * 7.5;

    _storeV.each(function(item, index, count)
    {
        var label = item.data.Label||item.data.SequenceNumMin;

        html.push('<td style="border: solid #EEEEEE 1px;">' +
                '<svg xmlns="http://www.w3.org/2000/svg" width="28" height="' + height + '">' +
                '<text transform="rotate(270, 12, 0) translate(-' + (height-15) + ',6)">' + h(label) + '</text>' +
                '</svg></td>');
    });
    html.push("</tr>");

    var rowcount = 0;
    _storeSC.each(function(item, index, count)
    {
        var scRowId = getItemData(item, "RowId");

        rowcount++;
        var rowstyle = rowcount % 2 ? "x4-grid-row x4-grid-row-alt" : "x4-grid-row";
        html.push("<tr class=\"" + rowstyle + "\">");

        var assayName = getItemData(item, "AssayName");
        var description = getItemData(item, "Description");
        html.push("<td width='175'>" + h(assayName) + h(description != null && assayName != description ? " (" + description + ")" : "") + "</td>");

        html.push("<td>" + h(getItemData(item, "Source")) + "</td>");

        _storeV.each(function(item, index, count)
        {
            var vRowId = getItemData(item, "RowId");
            var id = "sc" + scRowId + "v" + vRowId;
            var vLabel = item.data.Label || item.data.SequenceNumMin;
            var name = "sc" + scRowId + "v" + vLabel; // for selenium testing
            var checked = false;
            for (var i = 0; i < (_configVisitMap[scRowId] ? _configVisitMap[scRowId].length : 0); i++)
            {
                if (_configVisitMap[scRowId][i].visitId == vRowId)
                {
                    checked = true;
                    break;
                }
            }

            var isDataspace = <%=isDataspace%>;
            var checkboxInput = '<input class="scvCheckbox" name="' + name + '" id="' + id + '" configid="' + scRowId + '" visitid="' + vRowId + '" type=checkbox ' + (checked?"checked":"") + '>';
            var checkMark = checked ? '&#x2713' : '&nbsp;';

            html.push('<td align="center" class=\"x4-grid-cell\" style=\"padding: 6px;\">' + (!isDataspace ? checkboxInput : checkMark) + '</td>');
        });
        html.push("</tr>");
    });
    html.push("</table>");

    if (rowcount > 0)
    {
        _panelSV.show();
        _panelSV.body.update(html.join(''));
        _panelSV.setHeight(X.get('assaySpecimenVisitMappingTable').getHeight() + 30);

        // add handlers
        var nodes = X.dom.Query.select("input.scvCheckbox", _panelSV.body.dom);
        for (var i=0 ; i<nodes.length ; i++)
        {
            var node = nodes[i];
            var el = X.get(node);
            el.on("change", svcCheckBox_OnChange);
        }
    }
}

function svcCheckBox_OnChange(event)
{
    var el = X.get(event.target);
    var scRowId = el.dom.getAttribute('configid');
    var vRowId = el.dom.getAttribute('visitid');
    var checked = el.dom.checked; //"on" == el.getValue();
    if (!scRowId || !vRowId)
        return;
    if (checked)
    {
        insertSVC(el, scRowId, vRowId);
    }
    else
    {
        removeSVC(el, scRowId, vRowId);
    }
}

function insertSVC(el, scRowId, vRowId)
{
    LABKEY.Query.insertRows({
        schemaName: 'study',
        queryName: 'AssaySpecimenVisit',
        rows: [{AssaySpecimenId:scRowId, VisitId:vRowId}],
        success: function(data)
        {
            if (1 == data.rowsAffected)
            {
                if (!_configVisitMap[scRowId])
                    _configVisitMap[scRowId] = [];
                _configVisitMap[scRowId].push({visitId: vRowId, rowId: (data.rows[0].RowId || data.rows[0].rowid)});

                el.frame();
            }
        },
        failure : function(response)
        {
            Ext4.Msg.alert('Error', response.exception);
        }
    });
}

function removeSVC(el, scRowId, vRowId)
{
    var rowid = null;
    for (var i = 0; i < _configVisitMap[scRowId].length; i++)
    {
        if (_configVisitMap[scRowId][i].visitId == vRowId)
        {
            rowid = _configVisitMap[scRowId][i].rowId;
            break;
        }
    }

    if (!rowid)
        return;
    LABKEY.Query.deleteRows({
        schemaName: 'study',
        queryName: 'AssaySpecimenVisit',
        rows: [{RowId:rowid, container:LABKEY.container.id}],
        successCallback: function(data)
        {
            el.frame();
        },
        failure : function(response)
        {
            Ext4.Msg.alert('Error', response.exception);
        }
    });
}


})();
</script>

Enter assay schedule information in the grids below.
<div style="width: 810px;">
    <ul>
        <li <%=form.isUseAlternateLookupFields() ? "style='display:none;'" : ""%>>
            Configure dropdown options for assays, labs, and sample types at the project level to be shared across study designs or within this folder for
            study specific properties: <span id='config-dropdown-menu'></span>
        </li>
        <li>Use the "Insert New" button in the assay configurations grid to add a new assay.</li>
        <li>Select the visits for each assay in the assay schedule grid to define the expected assay schedule for the study.</li>
    </ul>
</div>
<div id="AssaySpecimenConfigGrid"></div>
<span style='font-style: italic; font-size: smaller; display: <%=h(isDataspace ? "none" : "inline")%>;'>* Double click to edit an assay configuration</span>
<br/><br/>
<%
    if (canManageStudy && form.isUseAlternateLookupFields())
    {
        %><%= textLink("Manage Locations", StudyController.ManageLocationsAction.class) %><br/><%
    }
%>
<br/>
<div id="AssaySpecimenVisitPanel"></div>
<span id="CreateNewVisitTextLink"></span>
<%
    if (canManageStudy)
    {
        if (study != null && study.getTimepointType() == TimepointType.VISIT && study.getVisits(Visit.Order.DISPLAY).size() > 1)
        {
            %><%= textLink("Change Visit Order", new ActionURL(StudyController.VisitOrderAction.class, getContainer()).addReturnURL(returnURL)) %><%
        }
%>
        <%=textLink("Manage " + visitDisplayName + "s", StudyController.ManageVisitsAction.class)%>
<%
    }
%>
<br/><br/><br/>
<div id="AssayPlanPanel"></div>
