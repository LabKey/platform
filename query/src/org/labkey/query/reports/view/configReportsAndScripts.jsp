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
<%@ page import="org.labkey.api.reports.report.RReport"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ViewContext context = HttpView.currentContext();
%>

<style type="text/css">

    .bmenu {
        background-image: url(<%=context.getContextPath() + "/_icons/exe.png"%>) !important;
    }

</style>

<script type="text/javascript">
    LABKEY.requiresExtJs(true);

    function showEngines()
    {
        var con = new Ext.data.HttpProxy(new Ext.data.Connection({
                url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesSummary", '<%=context.getContainer().getPath()%>'),
                method: 'GET'
            }));

        var store = new Ext.data.Store({
            reader: new Ext.data.JsonReader({root:'views',id:'extensions'},
                    [
                        {name:'name'},
                        {name:'exePath'},
                        {name:'exeCommand'},
                        {name:'extensions'},
                        {name:'languageName'},
                        {name:'languageVersion'},
                        {name:'key'},
                        {name:'outputFileName'}]),
            proxy: con,
            autoLoad: true,
            sortInfo: {field:'name', direction:"ASC"}});

        var newMenu = new Ext.menu.Menu({
            id: 'mainMenu',
            items: [{
                id: 'add_rEngine',
                text:'New R Engine',
                listeners:{click:function(button, event) {editRecord(button, grid,{
                    name:'R Scripting Engine',
                    extensions:'R,r',
                    exeCommand:'<%=RReport.DEFAULT_R_CMD%>',
                    outputFileName: 'script.Rout',
                    languageName:'R'});}}
            },{
                id: 'add_perlEngine',
                text:'New Perl Engine',
                listeners:{click:function(button, event) {editRecord(button, grid,{
                    name:'Perl Scripting Engine',
                    extensions:'pl',
                    languageName:'Perl'});}}
            },{
                id: 'add_externalEngine',
                text:'New External Engine',
                listeners:{click:function(button, event) {editRecord(button, grid, {name:"External"});}}
            }] });

        var grid = new Ext.grid.GridPanel({
            el:'enginesGrid',
            autoScroll:false,
            autoHeight:true,
            width:800,
            store: store,
            listeners: {
                rowdblclick: function(g, rowIndex, event) {
                    event.stopEvent();
                    editSelected(event, g);
                }
            },
            columns:[
                {header:'Name', dataIndex:'name'},
                {header:'Language', dataIndex:'languageName'},
                {header:'Language Version', dataIndex:'languageVersion'},
                {header:'File Extensions', dataIndex:'extensions'},
                {header:'Application Location', dataIndex:'exePath', hidden:true},
                {header:'Run Command', dataIndex:'exeCommand', hidden:true},
                {header:'Output File Name', dataIndex:'outputFileName', hidden:true}
            ],
            view: new Ext.grid.GridView({
                forceFit:true
            }),
            buttons: [
                {text:'Add', id: 'btn_addEngine', iconCls: 'bmenu', menu: newMenu, tooltip: {text:'Configure a new external script engine', title:'Add Engine'}},
                {text:'Delete', id: 'btn_deleteEngine', tooltip: {text:'Delete the selected script engine', title:'Delete Engine'}, listeners:{click:function(button, event) {deleteSelected(grid);}}},
                {text:'Edit', id: 'btn_editEngine', tooltip: {text:'Edit an existing script engine', title:'Edit Engine'}, listeners:{click:function(button, event) {editSelected(button, grid);}}}
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
            Ext.Msg.alert("Delete Engine Configuration", "There is no engine selected");
            return false;
        }

        var record = selections[0].data;
        var params = [];

        if (record.exePath.length == 0)
        {
            Ext.Msg.alert("Delete Engine Configuration", "Java 6 script engines cannot be deleted but you can disable them.");
            return false;
        }
        params.push("key=" + record.key);
        params.push("extensions=" + record.extensions);

        Ext.Msg.confirm('Delete Engine Configuration', "Are you sure you wish to delete the selected Configuration?", function(btn, text) {
            if (btn == 'yes')
            {
                Ext.Ajax.request({

                    url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesDelete") + '?' + params.join('&'),
                    method: "POST",
                    success: function(){grid.store.load();},
                    failure: function(){Ext.Msg.alert("Delete Engine Configuration", "Deletion Failed");}
                });
            }
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

    function editSelected(button, grid)
    {
        var selections = grid.selModel.getSelections();
        if (selections.length == 0)
        {
            Ext.Msg.alert("Edit Engine Configuration", "There is no engine selected");
            return false;
        }

        editRecord(button, grid, selections[0].data);
    }

    function editRecord(button, grid, record)
    {
        var tabPanel = new Ext.Panel({
            plain: true,
            defaults: {autoHeight:true, bodyStyle:'padding:10px'}
        });

        var formPanel = new Ext.FormPanel({
            bodyStyle:'padding:5px',
            defaultType: 'textfield',
            items: [{
                fieldLabel: "Name",
                name: 'name',
                id: 'editEngine_name',
                allowBlank:false,
                value: record.name
            },{
                fieldLabel: 'Language',
                name: 'languageName',
                id: 'editEngine_languageName',
                allowBlank:false,
                value: record.languageName
            },{
                fieldLabel: 'Language Version',
                name: 'languageVersion',
                id: 'editEngine_languageVersion',
                value: record.languageVersion
            },{
                fieldLabel: 'File Extensions',
                name: 'extensions',
                id: 'editEngine_extensions',
                allowBlank:false,
                tooltip: {text:'The list of file extensions (separated by commas) that this engine is associated with', title:'File Extensions'},
                listeners: {render: setFormFieldTooltip},
                value: record.extensions
            },{
                fieldLabel: 'Program Path',
                name: 'exePath',
                id: 'editEngine_exePath',
                allowBlank:false,
                value: record.exePath,
                tooltip: {text:'Specify the absolute path to the program including the program itself', title:'Program Path'},
                listeners: {render: setFormFieldTooltip},
                width: 250
            },{
                fieldLabel: 'Program Command',
                name: 'exeCommand',
                id: 'editEngine_exeCommand',
                tooltip: {text:'The command used when the program is invoked', title:'Program Command'},
                listeners: {render: setFormFieldTooltip},
                value: record.exeCommand,
                width: 250
            },{
                fieldLabel: 'Output File Name',
                name: 'outputFileName',
                id: 'editEngine_outputFileName',
                value: record.outputFileName,
                tooltip: {text:'If the console output is written to a file, the name should be specified here', title:'Output File Name'},
                listeners: {render: setFormFieldTooltip}
            },{
                name: 'key',
                xtype: 'hidden',
                value: record.key
            }]
        });
        var win = new Ext.Window({
            title: 'Edit Engine Configuration',
            layout:'form',
            border: false,
            width: 450,
            height: 320,
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

    function submitForm(win, panel, grid)
    {
        var items = panel.items;

        // client side validation
        var form = panel.getForm();
        if (form && !form.isValid())
        {
            Ext.Msg.alert('Engine Definition', 'Not all fields have been properly completed');
            return false;
        }

        form.submit({
            url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesSave"),
            waitMsg:'Submiting Form...',
            method: 'POST',
            success: function(){
                win.close();
                grid.store.load();
            },
            failure: function(form, action){Ext.Msg.alert("Save Error", "An error occurred while saving the engine configuration");}
        });
    }

    Ext.onReady(function()
    {
        showEngines();
    });
</script>

<labkey:errors/>

<table>
    <tr class="labkey-wp-header"><th colspan=2>Scripting Engine Configurations</th></tr>
    <tr><td><i>A scripting engine enables the execution of scripting code in a report or a QC validation script.
        A scripting engine can be exposed as a <a href="https://scripting.dev.java.net/" target="_blank">Java 6 script engine implementation</a>,<br/>
        or as an external script engine. Java 6 script engine implementations are exposed by configuring the Java runtime the webserver is running
        against. External engine implementations are added in this view. For example scripting languages like R and Perl
        can be configured here in order to create and run scripts and reports using these languages.</i></td>
    </tr>
    <tr><td>&nbsp;</td></tr>
</table>

<div id="enginesGrid" class="extContainer"></div>


