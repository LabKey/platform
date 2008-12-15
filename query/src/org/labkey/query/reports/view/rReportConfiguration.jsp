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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.reports.report.DefaultScriptRunner" %>
<%@ page import="org.labkey.api.reports.report.RServeScriptRunner" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<ReportsController.ConfigureRForm> me = (JspView<ReportsController.ConfigureRForm>) HttpView.currentView();
    ReportsController.ConfigureRForm bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();

    String options =
        "<option value=" + org.labkey.api.security.SecurityManager.PermissionSet.ADMIN.getPermissions() + ">Admin</option>" +
        "<option value=" + SecurityManager.PermissionSet.EDITOR.getPermissions() + ">" + SecurityManager.PermissionSet.EDITOR.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.AUTHOR.getPermissions() + ">" + SecurityManager.PermissionSet.AUTHOR.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.READER.getPermissions() + ">" + SecurityManager.PermissionSet.READER.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.RESTRICTED_READER.getPermissions() + ">" + SecurityManager.PermissionSet.RESTRICTED_READER.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.SUBMITTER.getPermissions() + ">" + SecurityManager.PermissionSet.SUBMITTER.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.NO_PERMISSIONS.getPermissions() + ">" + SecurityManager.PermissionSet.NO_PERMISSIONS.getLabel() + "</option>";

%>

<style type="text/css">

    .bmenu {
        background-image: url(<%=context.getContextPath() + "/_icons/exe.png"%>) !important;
    }

</style>
<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>
<script type="text/javascript">

    function onTempFolder()
    {
        var system = YAHOO.util.Dom.get('tempFolderSystem');
        var folderLocation = YAHOO.util.Dom.get('tempFolder');

        if (system.checked)
            folderLocation.style.display = "none";
        else
            folderLocation.style.display = "";

        var permissions = YAHOO.util.Dom.get('permissions');
        permissions.value = <%=bean.getPermissions()%>;
    }

    function validateForm()
    {
        var system = YAHOO.util.Dom.get('tempFolderSystem');
        var folderLocation = YAHOO.util.Dom.get('tempFolder');

        if (system.checked)
            folderLocation.value = "";
    }

    YAHOO.util.Event.addListener(window, "load", onTempFolder)
</script>

<table>
<%
    for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
    {
        %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<form action="" method="post" onsubmit="validateForm();">
    <table>
        <tr class="labkey-wp-header"><th colspan=2>R View Configuration</th></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><i>Specify the absolute path of the R program (R.exe on Windows, R for Unix and Mac) :</i><br/></td></tr>

        <tr><td class="labkey-form-label">R&nbsp;program:</td><td><input name="programPath" style="width:400px" value="<%=StringUtils.trimToEmpty(bean.getProgramPath())%>"></td><td></td></tr>
        <tr><td class="labkey-form-label">R&nbsp;command:</td><td><input name="command" style="width:400px" value="<%=StringUtils.trimToEmpty(h(bean.getCommand()))%>"></td><td></td></tr>

<%--
        <tr><td></td><td><i>Scripts can be executed by running R in batch mode or by using an RServe server:</i><br/></td></tr>
        <tr><td class="labkey-form-label">Script&nbsp;execution:</td><td><input name="scriptHandler" value="<%=DefaultScriptRunner.ID%>" type="radio" <%=DefaultScriptRunner.ID.equals(bean.getScriptHandler()) ? "checked" : ""%>>
            Batch mode.<%=PageFlowUtil.helpPopup("Batch mode", "A new instance of R is started up in batch mode each " +
                "time a script is executed. Because the instance of R is run using the same privileges as the LabKey server, " +
                "care must be taken to ensure that security settings below are set accordingly.")%></td><td></td></tr>
        <tr><td></td><td><input name="scriptHandler" value="<%=RServeScriptRunner.ID%>" type="radio" <%=RServeScriptRunner.ID.equals(bean.getScriptHandler()) ? "checked" : ""%>>
            RServe server.<img src="<%=HttpView.currentContext().getContextPath() + "/_images/beta.gif"%>"><%=PageFlowUtil.helpPopup("RServe server (Beta)", "RServe is a TCP/IP based server that can interact with R. " +
                "It can improve execution performance because the server does not need to be started for every script " +
                "that is run. Additionally, it can be configured on Unix systems to run under a specified group or user ID. RServe " +
                "is a separate R library that must be installed by your R administrator.")%></td><td></td></tr>
--%>
        <tr><td>&nbsp;</td></tr>

        <tr><td></td><td><i>Specify the permissions required in order to create R Views:</i><br/></td></tr>
        <tr><td class="labkey-form-label">Permissions:</td><td>
            <select name="permissions" id="permissions"><%=options%></select></td><td></td></tr>
        <tr>
            <td class="labkey-form-label">Temp&nbsp;directory:<%=PageFlowUtil.helpPopup("Temporary Folder", "In order to execute R scripts on the LabKey server, temporary files need to be created. The folder location specified " +
                "must be accesible by the LabKey server. Alternatively, the system temporary location will be used.")%>
            </td>
            <td><input name="tempFolderRadio" value="folder" type="radio" onclick="onTempFolder();" <%=StringUtils.isEmpty(bean.getTempFolder()) ? "" : "checked"%>>Specify a folder location&nbsp;&nbsp;<input name="tempFolder" id="tempFolder" style="width:200px;display:none" value="<%=StringUtils.trimToEmpty(bean.getTempFolder())%>"></td>
            <td></td>
        </tr>
        <tr><td></td><td><input name="tempFolderRadio" value="system" id="tempFolderSystem" type="radio" onclick="onTempFolder();" <%=StringUtils.isEmpty(bean.getTempFolder()) ? "checked" : ""%>>Use the system temporary folder</td><td></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>&nbsp;</td>
            <td><%=PageFlowUtil.generateSubmitButton("Submit")%>
            &nbsp;<%=PageFlowUtil.generateButton("Done", urlProvider(AdminUrls.class).getAdminConsoleURL())%></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><i>The configuration of this page is necessary to be able to create an R view. The location
            of the R program must be accessible by the LabKey server. The R command is the command used by the LabKey server
            to execute scripts created in an R view. The default command is sufficient for most cases and usually
            would not need to be modified.</i><br/><br/></td>
        </tr>
        <tr><td></td><td><i>Application downloads, documentation and tutorials about the R language can be found at
            the <a target="_blank" href="http://www.r-project.org/">R Project website</a>.</i>
        </tr>
    </table>
    <input name="scriptHandler" type="hidden" value="<%=DefaultScriptRunner.ID%>" >
</form>

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
                {text:'Edit', id: 'btn_editEngine', tooltip: {text:'Edit and existing script engine', title:'Edit Engine'}, listeners:{click:function(button, event) {editSelected(button, grid);}}}
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
    <tr><td><i>Configure external programs on this page to run Reports and Quality Control scripts. For example scripting languages like R and Perl
        can be configured here in order to create and run scripts and reports using these languages.</i></td>
    </tr>
    <tr><td>&nbsp;</td></tr>
</table>

<div id="enginesGrid" class="extContainer"></div>


