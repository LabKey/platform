<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
      resources.add(ClientDependency.fromFilePath("dataview/DataViewsPanel.css"));
      return resources;
  }
%>
<%
    ActionURL returnURL = getActionURL();

    Study study = StudyManager.getInstance().getStudy(getContainer());
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
    var _gridSC, _storeSC, _panelSV,  _storeSV, _storeV, _panelAP;
    var _configVisitMap = {};

    X.onReady(function(){

    //create a Store bound to the 'AssaySpecimen' table in the 'study' schema
    _storeSC = X.create('LABKEY.ext4.Store', {
        schemaName: 'study',
        queryName: 'AssaySpecimen',
        columns : ['RowId', 'AssayName', 'Description', 'Source', 'LocationId', 'TubeType', 'PrimaryTypeId', 'DerivativeTypeId'],
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
        maxWidth: 1250,
        autoHeight: true,
        selType: 'rowmodel',
        multiSelect: false,
        forceFit: true,
        title: 'Assay/Specimen Configurations',
        editable: true,
        emptyText: 'No assay/specimen configurations',
        dockedItems: [{
            xtype: 'toolbar',
            dock: 'top',
            border: false,
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
                        msg: "Are you sure you want to delete the selected assay/specimen configuration and all of its related <%=h(visitDisplayName.toLowerCase())%> mapping information?",
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
                                    failure : function(a,b,c)
                                    {
                                        console.log(arguments);
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
    _gridSC.on('itemdblclick', showUpdateConfigurationDialog);

    _panelSV = X.create('Ext.panel.Panel', {
        renderTo : 'AssaySpecimenVisitPanel',
        title : 'Assay/Specimen <%=h(visitDisplayName)%> Mapping',
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
            name: 'AssayPlan',
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
                    form.submit({
                        url     : LABKEY.ActionURL.buildURL('study', 'manageStudyProperties.view'),
                        success : function(response) { window.location.reload(); },
                        failure : function(response) { console.log(arguments); }
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
            formItem = column.editor;
            formItem.fieldLabel = column.header || column.text;
            formItem.name = column.dataIndex;
            formItem.value = record ? record.get(column.dataIndex) : null;
            formItem.helpPopup = null;
            formItems.push(formItem);
        }
    });

    var win = X.create('Ext.window.Window', {
        cls: 'data-window',
        border: false,
        modal: true,
        bodyStyle: 'padding: 5px;',
        title: record ? 'Edit Assay/Specimen Configuration' : ' Add Assay/Specimen Configuration',
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
            html.push('<td align="center" class=\"x4-grid-cell\" style=\"padding: 6px;\"><input class="scvCheckbox" name="' + name + '" id="' + id + '" configid="' + scRowId + '" visitid="' + vRowId + '" type=checkbox ' + (checked?"checked":"") + '></td>');
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
        failure : function(a,b,c)
        {
            console.log(arguments);
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
        failure : function(a,b,c)
        {
            console.log(arguments);
        }
    });
}


})();
</script>

<div id="AssaySpecimenConfigGrid"></div>
<span style='font-style: italic; font-size: smaller;'>* Double click to edit an assay/specimen configuration</span>
<br/><br/><br/>
<div id="AssaySpecimenVisitPanel"></div>
<%=textLink("Create New " + visitDisplayName, new ActionURL(StudyController.CreateVisitAction.class, getContainer()).addReturnURL(returnURL))%>
<%
    if (study != null && study.getTimepointType() == TimepointType.VISIT && study.getVisits(Visit.Order.DISPLAY).size() > 1)
    {
        %><%= textLink("Change Visit Order", new ActionURL(StudyController.VisitOrderAction.class, getContainer()).addReturnURL(returnURL)) %><%
    }
%>
<%=textLink("Manage " + visitDisplayName + "s", StudyController.ManageVisitsAction.class)%>
<br/><br/><br/>
<div id="AssayPlanPanel"></div>
