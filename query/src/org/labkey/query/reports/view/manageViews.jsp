<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
/*
            listeners: {
                rowcontextmenu: function(g, rowIndex, event) {
                    event.stopEvent();
                    var menu = new Ext.menu.Menu({items:[{text:'rename'},{text:'edit description'},{text:'edit view...',disabled:'true'}]});
                    menu.showAt(event.getXY());
                }
            },
*/
            columns:[
                cbs,                    
                {header:'Type', dataIndex:'type'},
                {header:'Title', dataIndex:'displayName', width:200},
                {header:'Description', dataIndex:'description', hidden:true},
                {header:'Created By', dataIndex:'owner'},
                {header:'Shared', dataIndex:'public'},
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
                {text:'Expand All', listeners:{click:function(button, event) {grid.view.expandAllGroups();}}},
                {text:'Collapse All', listeners:{click:function(button, event) {grid.view.collapseAllGroups();}}},
                {text:'Delete Selected', listeners:{click:function(button, event) {deleteSelected(grid);}}},
                {text:'Edit Selected', listeners:{click:function(button, event) {editSelected(button, grid);}}}
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

        var tabPanel = new Ext.TabPanel({
            activeTab: 0,
            plain: true,
            enableTabScroll: true,
            defaults: {autoHeight:true, bodyStyle:'padding:10px'}
        });
        var win = new Ext.Window({
            title: 'Edit Views',
            layout:'form',
            border: false,
            width: 650,
            height: 300,
            closeAction:'close',
            //plain: true,
            modal: false,
            items: tabPanel,
            buttons: [{
                text: 'Submit',
                handler: function(){submitForm(win, tabPanel, grid);}
            },{
                text: 'Finished',
                handler: function(){win.close();}
            }]
        });

        for (var i=0; i < selections.length; i++)
        {
            tabPanel.add(new Ext.FormPanel({
                title: selections[i].data.name,
                layout:'form',
                autoHeight: 'true',
                defaults: {width: 230},
                defaultType: 'textfield',

                items: [{
                    fieldLabel: 'View Name',
                    name: 'viewName',
                    allowBlank:false,
                    value: selections[i].data.name
                },{
                    fieldLabel: 'Description',
                    name: 'description',
                    xtype: 'textarea',
                    value: selections[i].data.description
                },{
                    name: 'reportId',
                    xtype: 'hidden',
                    value: selections[i].id
                },{
                    name: 'editUrl',
                    xtype: 'button',
                    text: 'Edit Source...',
                    dest: selections[i].data.editUrl,
                    disabled: selections[i].data.editUrl ? false : true,
                    handler: function(){doAdvancedEdit(this);}
                }]
            }));
        }
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

    function submitForm(win, tabPanel, grid)
    {
        var items = tabPanel.items;

        // client side validation
        for (var i=0; i < items.getCount(); i++)
        {
            var form = items.get(i).getForm();
            if (form && !form.isValid())
            {
                Ext.Msg.alert('Edit Views', 'Invalid Form Parameter(s)');
                tabPanel.setActiveTab(items.get(i).getId());
                return false;
            }
        }

        Ext.Msg.confirm('Edit Views', 'Are you sure you wish to update the selected views?', function(btn, text) {
            if (btn == 'yes')
            {
                for (var i=0; i < items.getCount(); i++)
                {
                    var form = items.get(i).getForm();
                    if (form && form.getEl())
                    {
                        form.submit({
                            url: LABKEY.ActionURL.buildURL("reports", "manageViewsEditReports"),
                            waitMsg:'Submiting Form...',
                            method: 'POST',
                            success: function(){grid.store.load();},
                            failure: function(form, action){LABKEY.Utils.displayAjaxErrorResponse(action.response);}
                        });
                    }
                }
            }
        });
    }

    Ext.onReady(function()
    {
        showViews();
    });
</script>

<labkey:errors/>

<div id="viewsGrid" class="extContainer"></div>
