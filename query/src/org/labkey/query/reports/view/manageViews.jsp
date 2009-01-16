<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ViewContext context = HttpView.currentContext();

%>

<script type="text/javascript">
    LABKEY.requiresExtJs(true);

    function renderRow(value, p, record)
    {
        var txt = 'ext:qtip="';

        txt = txt.concat('<b>title:</b> ' + record.data.name + '<br>');
        if (record.data.description != undefined)
            txt = txt.concat('<b>description:</b> ' + record.data.description + '<br>');
        if (record.data.query != undefined)
            txt = txt.concat('<b>query:</b> ' + record.data.query + '<br>');
        if (record.data.schema != undefined)
            txt = txt.concat('<b>schema:</b> ' + record.data.schema + '<br>');
        txt = txt.concat('"');
        p.attr = txt;
        
        return value;
    }

    function showViews()
    {
        var con = new Ext.data.HttpProxy(new Ext.data.Connection({
                url: LABKEY.ActionURL.buildURL("reports", "manageViewsSummary", '<%=context.getContainer().getPath()%>'),
                method: 'GET'
            }));

        var store = new Ext.data.GroupingStore({
            reader: new Ext.data.JsonReader({root:'views',id:'reportId'},
                    [
                        {name:'query'},
                        {name:'schema'},
                        {name:'name'},
                        {name:'owner'},
                        {name:'public'},
                        {name:'displayName'},
                        {name:'description'},
                        {name:'editable'},
                        {name:'editUrl'},
                        {name:'reportId'},
                        {name:'type'}]),
            proxy: con,
            autoLoad: true,
            sortInfo: {field:'query', direction:"ASC"},
            listeners: {
                load: function(store, records) {
                    if (records.length == 0)
                        Ext.Msg.alert("Manage Views", "You have no views in this container.");
                }
            },
            groupField:'query'});

        var cbs = new Ext.grid.CheckboxSelectionModel();
        var grid = new Ext.grid.GridPanel({
            el:'viewsGrid',
            autoScroll:false,
            autoHeight:true,
            width:800,
            store: store,
            selModel: cbs,
            listeners: {
                rowdblclick: function(g, rowIndex, event) {
                    event.stopEvent();
                    editSelected(event, g);
                }
            },
            columns:[
                cbs,                    
                {header:'Type', dataIndex:'type', renderer:renderRow},
                {header:'Title', dataIndex:'displayName', width:200, renderer:renderRow},
                {header:'Description', dataIndex:'description', hidden:true},
                {header:'Created By', dataIndex:'owner', renderer:renderRow},
                {header:'Shared', dataIndex:'public', renderer:renderRow},
                {header:'Schema', dataIndex:'schema', hidden:true},
                {header:'Query', dataIndex:'query'}

            ],
            view: new Ext.grid.GroupingView({
                startCollapsed:false,
                hideGroupedColumn:true,
                forceFit:true,
                groupTextTpl: '{values.group}'
            }),
            buttons: [
                {text:'Expand All', tooltip: {text:'Expands all groups', title:'Expand All'}, listeners:{click:function(button, event) {grid.view.expandAllGroups();}}},
                {text:'Collapse All', tooltip: {text:'Collapses all groups', title:'Collapse All'}, listeners:{click:function(button, event) {grid.view.collapseAllGroups();}}},
                {text:'Delete Selected', id: 'btn_deleteView', tooltip: {text:'Delete selected view', title:'Delete Views'}, listeners:{click:function(button, event) {deleteSelected(grid);}}},
                {text:'Edit', id: 'btn_editView', tooltip: {text:'Edit an existing view (you can also double click on the view to edit)', title:'Edit View'}, listeners:{click:function(button, event) {editSelected(button, grid);}}}
            ],
            buttonAlign:'center'
        });

        grid.render();
    }

    function deleteSelected(grid)
    {
        var selections = grid.selModel.getSelections();

        if (selections.length == 0)
        {
            Ext.Msg.alert("Delete Views", "There are no views selected");
            return false;
        }
        else
        {
            var msg = "Delete Selected Views:<br/>";
            var params = [];

            for (var i=0; i < selections.length; i++)
            {
                msg = msg.concat(selections[i].data.name);
                msg = msg.concat('<br/>');

                params.push("reportId=" + selections[i].id);
            }
            
            Ext.Msg.confirm('Delete Views', msg, function(btn, text) {
                if (btn == 'yes')
                {
                    Ext.Ajax.request({

                        url: LABKEY.ActionURL.buildURL("reports", "manageViewsDeleteReports") + '?' + params.join('&'),
                        method: "POST",
                        success: function(){grid.store.load();},
                        failure: function(){Ext.Msg.alert("Delete Views", "Deletion Failed");}
                    });
                }
            });
        }
    }

    function editSelected(button, grid)
    {
        var selections = grid.selModel.getSelections();

        if (selections.length == 0)
        {
            Ext.Msg.alert("Edit Views", "There are no views selected");
            return false;
        }

        if (selections.length > 1)
        {
            Ext.Msg.alert("Edit Views", "Only one view can be edited at a time");
            return false;
        }

        editRecord(button, grid, selections[0].data);
    }

    function editRecord(button, grid, record)
    {
        var formPanel = new Ext.FormPanel({
            bodyStyle:'padding:5px',
            defaultType: 'textfield',
            items: [{
                fieldLabel: 'View Name',
                name: 'viewName',
                allowBlank:false,
                width: 250,
                value: record.name
            },{
                fieldLabel: 'Description',
                name: 'description',
                xtype: 'textarea',
                width: 250,
                value: record.description
            },{
                name: 'reportId',
                xtype: 'hidden',
                value: record.reportId
            },{
                name: 'editUrl',
                xtype: 'button',
                text: 'Edit Source...',
                dest: record.editUrl,
                tooltip: {text:'Some view types support advanced editing capabilities, if they do this button will navigate you to the alternate view', title:'Edit Source'},
                disabled: record.editUrl ? false : true,
                handler: function(){doAdvancedEdit(this);}
            }]

        });
        var win = new Ext.Window({
            title: 'Edit View',
            layout:'form',
            border: false,
            width: 450,
            height: 220,
            closeAction:'close',
            modal: false,
            items: formPanel,
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                handler: function(){submitForm(win, formPanel, grid);}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }]
        });

        win.show(button);
    }

    function doAdvancedEdit(config)
    {
        Ext.Msg.confirm('Edit Source', "Do you want to navigate away from your current view to the advanced editor?", function(btn, text) {
            if (btn == 'yes')
            {
                window.location = config.dest;
            }
        });
    }

    function submitForm(win, panel, grid)
    {
        var items = panel.items;

        // client side validation
        var form = panel.getForm();
        if (form && !form.isValid())
        {
            Ext.Msg.alert('Edit Views', 'Not all fields have been properly completed');
            return false;
        }

        form.submit({
            url: LABKEY.ActionURL.buildURL("reports", "manageViewsEditReports"),
            waitMsg:'Submiting Form...',
            method: 'POST',
            success: function(){
                win.close();
                grid.store.load();
            },
            failure: function(form, action){Ext.Msg.alert("Save Error", "An error occurred while saving the view");}
        });
    }

    function setFormFieldTooltip(component)
    {
        var label = Ext.get('x-form-el-' + component.id).prev('label');
        Ext.QuickTips.register({
            target: label,
            text: component.tooltip.text,
            title: ''
        });
    }

    Ext.onReady(function()
    {
        showViews();
    });
</script>

<labkey:errors/>

<div id="viewsGrid" class="extContainer"></div>
