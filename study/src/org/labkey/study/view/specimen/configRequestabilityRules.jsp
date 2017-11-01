<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.template.ClientDependencies"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.importer.RequestabilityManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext3");
    }
%>
<%
    // TODO: This should use Selector.js
    Container c = getContainer();
%>
<script type="text/javascript">
    Ext.QuickTips.init();
    window.onbeforeunload = LABKEY.beforeunload();

    function reorder(grid, moveUp)
    {
        var store = grid.store;
        var selectionModel = grid.getSelectionModel();
        var record = selectionModel.getSelected();
        if (record)
        {
            var index = store.indexOf(record);
            if (moveUp)
            {
                if (index > 0)
                {
                    store.removeAt(index);
                    store.insert(index - 1, record);
                    selectionModel.selectRow(index - 1);
                    grid.getView().refresh();
                }
            }
            else
            {
                if (index < store.getCount() - 1)
                {
                    store.removeAt(index);
                    store.insert(index + 1, record);
                    selectionModel.selectRow(index + 1);
                    grid.getView().refresh();
                }
            }
        }
    }

    function removeRule(grid)
    {
        var store = grid.store;
        var selectionModel = grid.getSelectionModel();
        var record = selectionModel.getSelected();
        if (record)
        {
            // Move the selection to the next item without preserving the current selection:
            selectionModel.selectNext(false);
            store.remove([ record ]);
            grid.getView().refresh();
        }
    }

    var dataFieldName = 'name';
    var dataUrlFieldName = 'viewDataUrl';

    function populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo)
    {
        if (schemaCombo.store)
        {
            schemaCombo.store.removeAll();
            schemaCombo.store.loadData(getArrayArray(schemasInfo.schemas));
            schemaCombo.on("select", function(combo, record, index)
            {
                queryCombo.clearValue();
                viewCombo.clearValue();
                LABKEY.Query.getQueries({
                    schemaName: record.data[record.fields.first().name],
                    success: function(queriesInfo) { populateQueries(queryCombo, viewCombo, queriesInfo); }
                })
            });
        }
    }

    function populateQueries(queryCombo, viewCombo, queriesInfo)
    {
        if (queryCombo.store)
        {
            var records = [];
            for (var i = 0; i < queriesInfo.queries.length; i++)
            {
                var queryInfo = queriesInfo.queries[i];
                records[i] = [queryInfo.name, queryInfo.viewDataUrl];
            }

            queryCombo.store.removeAll();
            queryCombo.store.loadData(records);
            queryCombo.on("select", function(combo, record, index)
            {
                viewCombo.clearValue();
                LABKEY.Query.getQueryViews({
                    schemaName: queriesInfo.schemaName,
                    queryName: record.data[record.fields.first().name],
                    success: function(queriesInfo) { populateViews(viewCombo, queriesInfo); }
                })
            });
        }
    }

    var defaultViewLabel = "[default view]";

    function populateViews(viewCombo, queryViews)
    {
        if (viewCombo.store)
        {
            var records = [[defaultViewLabel]];
            for (var i = 0; i < queryViews.views.length; i++)
            {
                var viewInfo = queryViews.views[i];
                if (!viewInfo.hidden)
                {
                    var name =  viewInfo.name != null ? viewInfo.name : defaultViewLabel;
                    records[records.length] = [name, viewInfo.viewDataUrl];
                }
            }

            viewCombo.store.removeAll();
            viewCombo.store.loadData(records);
        }
    }

    function getArrayArray(simpleArray)
    {
        var arrayArray = [];
        for (var i = 0; i < simpleArray.length; i++)
        {
            arrayArray[i] = [];
            arrayArray[i][0] = simpleArray[i];
        }
        return arrayArray;
    }

    function setupUserQuery(grid, type)
    {
        var schemaCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore({
                    fields: [{
                        name: dataFieldName,
                        sortType: function(value) { return value.toLowerCase(); }
                    }],
                    sortInfo: { field: dataFieldName }
                }),
                valueField: dataFieldName,
                displayField: dataFieldName,
                fieldLabel: "Schema",
                name: 'schema',
                id: 'userQuery_schema',
                allowBlank:false,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        var queryCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore({
                    fields: [{
                        name: dataFieldName,
                        sortType: function(value) { return value.toLowerCase(); }
                    }, {
                        name: dataUrlFieldName
                    }],
                    sortInfo: { field: dataFieldName }
                }),
                valueField: dataFieldName,
                displayField: dataFieldName,
                fieldLabel: "Query",
                name: 'query',
                id: 'userQuery_query',
                allowBlank:false,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        var viewCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore({
                    fields: [{
                        name: dataFieldName,
                        sortType: function(value) { return value.toLowerCase(); }
                    }, {
                        name: dataUrlFieldName
                    }],
                    sortInfo: { field: dataFieldName }
                }),
                valueField: dataFieldName,
                displayField: dataFieldName,
                fieldLabel: "View",
                name: 'view',
                id: 'userQuery_view',
                allowBlank:true,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        var actionCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore(
                {
                    fields: ['option'],
                    data: [
                        ["<%= h(RequestabilityManager.MarkType.AVAILABLE.getLabel()) %>"],
                        ["<%= h(RequestabilityManager.MarkType.UNAVAILABLE.getLabel()) %>"]
                    ]
                }),
                valueField: 'option',
                displayField: 'option',
                fieldLabel: "Mark vials",
                name: 'action',
                id: 'userQuery_action',
                allowBlank:false,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        LABKEY.Query.getSchemas({
            success: function(schemasInfo) { populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo); }
        });

        var labelStyle = 'padding-bottom: 5px; font-weight: normal;';

        var queryHelpText = 'Select the query and view that identify vials affected by this rule.  The returned list must include a "GlobalUniqueId" column.';
        var queryLabel = new Ext.form.Label({
            html: '<div style="' + labelStyle +'">' + queryHelpText + '</div>'
        });

        var actionLabel = new Ext.form.Label({
            html: '<br><div style="' + labelStyle +'">Select whether vials identified by the query should be marked as available or unavailable.</div>'
        });

        var formPanel = new Ext.form.FormPanel({
            padding: 5,
            items: [queryLabel, schemaCombo, queryCombo, viewCombo, actionLabel, actionCombo]});
        
        var win = new Ext.Window({
            title: 'Add Custom Query Rule',
            layout:'fit',
            border: false,
            width: 475,
            height: 330,
            closeAction:'close',
            modal: true,
            items: formPanel,
            resizable: false,
            buttons: [{
                text: 'Cancel',
                handler: function() { win.close(); }
            },{
                text: 'Submit',
                handler: function(){
                    var form = formPanel.getForm();
                    if (form && !form.isValid())
                    {
                        Ext.Msg.alert('Add Custon Query Rule', 'Please complete all required fields.');
                        return false;
                    }

                    var viewName = viewCombo.getValue();
                    if (viewName == defaultViewLabel)
                        viewName = "";
                    var ruleName = "<%= h(RequestabilityManager.RuleType.CUSTOM_QUERY.getName()) %>: " + schemaCombo.getValue() + "." + queryCombo.getValue();
                    if (viewName)
                        ruleName += ", view " + viewName;
                    var testUrl = getSelectedURL(viewName ? viewCombo : queryCombo);
                    var markRequestable = actionCombo.getValue() == '<%= h(RequestabilityManager.MarkType.AVAILABLE.getLabel()) %>';
                    var ruleData = schemaCombo.getValue() + "<%= text(RequestabilityManager.CUSTOM_QUERY_DATA_SEPARATOR) %>" +
                                   queryCombo.getValue() + "<%= text(RequestabilityManager.CUSTOM_QUERY_DATA_SEPARATOR) %>" +
                                   viewName + "<%= text(RequestabilityManager.CUSTOM_QUERY_DATA_SEPARATOR) %>" +
                                   markRequestable;
                    if (addRule(grid, type, ruleName, actionCombo.getValue(), testUrl, ruleData))
                        win.close();
                }
            }],
            bbar: [{ xtype: 'tbtext', text: '',id:'statusTxt' }]
        });
        win.show();
    }

    function getSelectedURL(comboBox)
    {
        var value = comboBox.getValue();
        var record = comboBox.findRecord(comboBox.valueField, value);
        return record.data[dataUrlFieldName];
    }


    function addRule(grid, type, name, action, testURL, ruleData)
    {
        var store = grid.store;

        if (type == 'CUSTOM_QUERY' && !ruleData)
        {
            setupUserQuery(grid, type);
            return false;
        }

        // Don't add more than 1 of any rules except CUSTOM QUERY
        if (type != 'CUSTOM_QUERY' && ruleTypeAlreadyPresent(grid, type))
            return true;

        var ruleProperties = {
            type: type,
            ruleData: ruleData,
            name: name,
            action: action,
            testURL: testURL
        };

        // insert after current selection or last if none
        var insertIndex = store.getCount();
        var selectionModel = grid.getSelectionModel();
        var record = selectionModel.getSelected();
        if (record)
            insertIndex = Math.min(insertIndex, store.indexOf(record) + 1);

        var newRule = new store.recordType(ruleProperties); // create new record
        store.insert(insertIndex, newRule); // insert a new record into the store
        grid.getView().refresh();
        
        return true;
    }

    function ruleTypeAlreadyPresent(grid, type)
    {
        var store = grid.store;
        for (var i = 0; i < store.getCount(); i++)
        {
            var record = store.getAt(i);
            if (type == record.data.type)
                return true;
        }
        return false;
    }

    function actionColumnRenderer(value, p, record)
    {
        return 'Mark ' + record.data.action.toLowerCase();
    }

    function testColumnRenderer(value, p, record)
    {
        var txt = '';

        if (record.data.testURL){
            txt = LABKEY.Utils.textLink({
                href: record.data.testURL,
                text: "view affected vials",
                target: "_blank"
            });
        }

        return txt;
    }

    function saveComplete()
    {
        Ext.Msg.hide();
        LABKEY.setDirty(false);
        document.location = '<%= new ActionURL(StudyController.ManageStudyAction.class, c)%>';
    }

    function saveFailed(response, options)
    {
        Ext.Msg.hide();
        LABKEY.Utils.displayAjaxErrorResponse(response, options);
    }

    function save(rulesGrid)
    {
        if (!LABKEY.isDirty())
        {
            saveComplete();
            return;
        }

        var store = rulesGrid.store;
        var ruleTypes = [];
        var ruleDatas = [];
        for (var i = 0; i < store.getCount(); i++)
        {
            var record = store.getAt(i);
            ruleTypes[i] = record.data.type;
            ruleDatas[i] = record.data.ruleData ? record.data.ruleData : "";
        }

        Ext.Msg.wait("Saving...");
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("study-samples", "updateRequestabilityRules"),
            method : 'POST',
            success: saveComplete,
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.displayAjaxErrorResponse, this, true),
            jsonData : {
                ruleType : ruleTypes,
                ruleData : ruleDatas
            },
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function showRules()
    {
        Ext.QuickTips.init();

        var rulesGrid;

        var addMenuItems = [
            <%
                boolean first = true;
                for (RequestabilityManager.RuleType type : RequestabilityManager.RuleType.values())
                {
                    ActionURL defaultTestURL = type.getDefaultTestURL(c);
                    RequestabilityManager.MarkType defaultMarkType = type.getDefaultMarkType();
                %>
                    <%= text(!first ? "," : "") %>new Ext.menu.Item({
                                id: 'add_<%= h(type.name()) %>',
                                text:'<%= h(type.getName()) %>',
                                listeners:{
                                    click:function(button, event) {
                                        LABKEY.setDirty(true);
                                        addRule(rulesGrid,
                                           '<%= h(type.name()) %>', // type enum
                                           '<%= h(type.getName()) %>', // friendly name
                                           '<%= h(defaultMarkType != null ? defaultMarkType.getLabel() : "") %>', // default mark type
                                           '<%= h(defaultTestURL != null ? defaultTestURL.getLocalURIString() : "") %>'); // default mark type
                                    }
                                }
                            })
                <%
                    first = false;
                }
            %>
        ];

        var addMenu = new Ext.menu.Menu({
            id: 'mainMenu',
            cls:'extContainer',
            items:addMenuItems
        });

        var removeButton = new Ext.Button({
            text:'Remove Rule',
            id: 'btn_deleteEngine',
            tooltip: {
                text:'Delete the selected rule.',
                title:'Delete rule'
            },
            listeners: {
                click: function(button, event) { LABKEY.setDirty(true); removeRule(rulesGrid); }
            }
        });

        var moveUpButton = new Ext.Button({
            text:'Move Up',
            id: 'btn_moveUp',
            tooltip: {
                text:'Move the selected rule up.  Higher rules run earlier.',
                title:'Move Rule Up'
            },
            listeners: {
                click: function(button, event) { LABKEY.setDirty(true); reorder(rulesGrid, true); }
            }
        });

        var moveDownButton = new Ext.Button({
            text:'Move Down',
            id: 'btn_moveDown',
            tooltip: {
                text:'Move the selected rule down.  Lower rules run later.',
                title:'Move Rule Down'
            },
            listeners: {
                click: function(button, event) { LABKEY.setDirty(true); reorder(rulesGrid, false); }
            }
        });
        
        // shared reader
        var reader = new Ext.data.ArrayReader({}, [
            {name: 'type'},
            {name: 'ruleData'},
            {name: 'name'},
            {name: 'action'},
            {name: 'testURL'}
        ]);


        var initialData = [
        <%
        first = true;
        for (RequestabilityManager.RequestableRule rule : RequestabilityManager.getInstance().getRules(c))
        {
        %>
            <%= text(!first ? "," : "") %>
            ['<%= h(rule.getType().name()) %>', '<%= h(rule.getRuleData()) %>', '<%= h(rule.getName()) %>',
                '<%= h(rule.getMarkType().getLabel()) %>',
            '<%= h(null != rule.getTestURL(getUser()) ? rule.getTestURL(getUser()).getLocalURIString() : "") %>'
            ]
        <%
            first = false;
        }
        %>
        ];

        var rowSelectionModel = new Ext.grid.RowSelectionModel({singleSelect: true});
        rowSelectionModel.on("rowselect", function(model, rowIndex, record)
        {
            if (rowIndex == rulesGrid.getStore().getCount() - 1)
            {
                moveUpButton.enable();
                moveDownButton.disable();
                removeButton.enable();
            }
            else if (rowIndex == 0)
            {
                moveUpButton.disable();
                moveDownButton.enable();
                removeButton.enable();
            }
            else
            {
                moveUpButton.enable();
                moveDownButton.enable();
                removeButton.enable();
            }
        });

        rowSelectionModel.on("rowdeselect", function()
        {
            moveUpButton.disable();
            moveDownButton.disable();
            removeButton.disable();
        });

        // Set initial button state (no selection by default):
        moveUpButton.disable();
        moveDownButton.disable();
        removeButton.disable();

        rulesGrid = new Ext.grid.GridPanel({
            store: new Ext.data.Store({
                reader: reader,
                data: initialData
            }),
            cm: new Ext.grid.ColumnModel({
                columns: [
                    new Ext.grid.RowNumberer({ header: 'Order', width: 50}),
                    {header:'Type', dataIndex:'type', hidden: true},
                    {header:'RuleData', dataIndex:'ruleData', hidden: true},
                    {header:'Rule', dataIndex:'name'},
                    {header:'Action', dataIndex:'action', renderer: actionColumnRenderer, width: 60},
                    {header:'Test Link', dataIndex:'testURL', renderer: testColumnRenderer, width: 55}
                ],
                sortable: false
            }),
            viewConfig: {
                forceFit:true
            },
            columnLines: true,
            enableDragDrop: true,
            width:700,
            height:300,
            title:'Active Rules',
            iconCls:'icon-grid',
            renderTo: 'rulesGrid',
            buttonAlign:'center',
            sm: rowSelectionModel,
            buttons: [{
                text: 'Add Rule',
                menu: addMenu,
                tooltip: {
                    text: 'Adds a new rule for determining requestability.',
                    title: 'Add Rule'
                }
            }, removeButton, moveUpButton, moveDownButton, {
                text: 'Save',
                listeners: {
                    click: function() { save(rulesGrid); }
                }
            }, {
                text: 'Cancel',
                listeners: {
                    click: function() { document.location = '<%= new ActionURL(StudyController.ManageStudyAction.class, c)%>'; }
                }
            }]
        });
    }

    Ext.onReady(showRules);
</script>

<labkey:errors/>

<table>
    <tr>
        <td class="labkey-announcement-title" colspan="2"><span>Requestability Rule Configuration</span></td>
    </tr>
    <tr>
        <td class="labkey-title-area-line" colspan="2"></td>
    </tr>
    <tr>
        <td>
            Whether a given vial is requestable is determined by running a series of configurable rules.  Each
            rule may change the requestability state of any vial(s).  Rules are run in order, so a vial's final state will be
            determined by the last rule to affect that vial.<br><br>
            <i>Note: if present, the <%= h(RequestabilityManager.RuleType.LOCKED_IN_REQUEST.getName()) %>
            ensures that a single vial can never be part of two simultaneous requests.
            </i>
        </td>
    </tr>
    <tr><td>&nbsp;</td></tr>
</table>

<div id="rulesGrid" class="extContainer"></div>
<table>
    <tr>
        <td class="labkey-announcement-title" colspan="2"><span>Available Rule Types</span></td>
    </tr>
    <tr>
        <td class="labkey-title-area-line" colspan="2"></td>
    </tr>
<%
    for (RequestabilityManager.RuleType type : RequestabilityManager.RuleType.values())
    {
%>
    <tr>
        <td><b><%= h(type.getName())%></b></td>
        <td><%= h(type.getDescription())%></td>
    </tr>
<%
    }
%>
</table>
