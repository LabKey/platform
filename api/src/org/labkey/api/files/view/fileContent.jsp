<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("applet.js");
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("FileUploadField.js");
    LABKEY.requiresScript("ActionsAdmin.js");
    LABKEY.requiresScript("PipelineAction.js");
</script>

<%
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();
    FilesWebPart me = (FilesWebPart) HttpView.currentView();

    AttachmentDirectory root = bean.getRoot();
    Container c = context.getContainer();

    // prefix is where we what the tree rooted
    // TODO: applet and fileBrowser could use more consistent configuration parameters
    //String rootName = c.getName();
    //String webdavPrefix = context.getContextPath() + "/" + WebdavService.getServletPath();

    //String rootPath = webdavPrefix + c.getEncodedPath();

    //if (bean.getDavLabel() != null)
    //    rootPath = webdavPrefix + c.getEncodedPath() + bean.getDavLabel();
//    String rootPath = webdavPrefix + c.getEncodedPath() + "%40myfiles/";
    //if (!rootPath.endsWith("/"))
    //    rootPath += "/";

    String startDir = "/";
//    if (me.getFileSet() != null)
//        startDir += "/@files/" + me.getFileSet();
    //String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + rootPath;
%>


<div class="extContainer">
    <table>
        <tr><td><div id="toolbar"></div></td></tr>
        <tr><td><div id="files"></div></td></tr>
    </table>
</div>

<script type="text/javascript">
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
Ext.QuickTips.init();

var autoResize = <%=bean.isAutoResize()%>;
var fileBrowser = null;
var fileSystem = null;
var actionsURL = <%=PageFlowUtil.jsString(PageFlowUtil.urlProvider(PipelineUrls.class).urlActions(context.getContainer()).getLocalURIString() + "path=")%>;
var buttonActions = [];

<%
    for (FilesWebPart.FilesForm.actions action  : bean.getButtonConfig())
    {
%>
        buttonActions.push('<%=action.name()%>');
<%
    }
%>
function renderBrowser(rootPath, dir)
{
    var configureAction = new Ext.Action({text: 'Configure', handler: function()
    {
        window.location = <%=PageFlowUtil.jsString(PageFlowUtil.urlProvider(FileUrls.class).urlShowAdmin(c).getLocalURIString())%>;
    }});

    var dropAction = new Ext.Action({text: 'Upload multiple files', scope:this, disabled:false, handler: function()
    {
        var dropUrl = <%=PageFlowUtil.jsString((new ActionURL("ftp","drop",c)).getEncodedLocalURIString() + (null == me.getFileSet() ? "" : "fileSetName=" + PageFlowUtil.encode(root.getLabel())))%>;
        window.open(dropUrl, '_blank', 'height=600,width=1000,resizable=yes');
    }});

    // TODO even better just refresh/rerender the browser not the whole page
    var combo = new Ext.form.ComboBox({
        name: 'filesetComboBox',
        store: fileSets,
        typeAhead: true,
        mode: 'local',
        triggerAction: 'all',
        selectOnFocus:true,
        width:135,
        value:selectedValue
    });
    combo.on("select",function(){
        var value = combo.getValue();
        if (value.indexOf("showAdmin.view") != -1)
            window.location=value;
        else
            fileBrowser.changeDirectory(value);
    });

    /**
     * A version of a tab panel that doesn't render the tab strip, used to swap
     * in panels programmatically
     * @param w
     * @param h
     */
    TinyTabPanel = Ext.extend(Ext.TabPanel, {

        adjustBodyWidth : function(w){
            if(this.header){
                this.header.setWidth(w);
                this.header.setHeight(1);
            }
            if(this.footer){
                this.footer.setWidth(w);
                this.header.setHeight(1);
            }
            return w;
        }
    });

    // subclass the filebrowser panel
    FilesWebPartPanel = Ext.extend(LABKEY.FileBrowser, {

        // collapsible tab panel used to display dialog-like content
        collapsibleTabPanel : undefined,

        // import data tab
        importDataTab : undefined,

        // panel for the drop applet
        appletPanel : undefined,

        actionsConnection : new Ext.data.Connection({autoAbort:true}),

        // pipeline actions
        pipelineActions : undefined,
        importActions : undefined,

        // file upload form field
        fileInputField : undefined,

        // toolbar buttons
        toolbarButtons : [],
        toolbar : undefined,

        constructor : function(config)
        {
            FilesWebPartPanel.superclass.constructor.call(this, config);
        },

        initComponent : function()
        {
            FilesWebPartPanel.superclass.initComponent.call(this);

            this.on(BROWSER_EVENTS.directorychange,function(record){this.onDirectoryChange(record);}, this);
            this.grid.getSelectionModel().on(BROWSER_EVENTS.selectionchange,function(record){this.onSelectionChange(record);}, this);

            this.on(BROWSER_EVENTS.transfercomplete, function(result) {
                if (this.appletStatusBar)
                    this.appletStatusBar.setVisible(false);
            }, this);
            this.on(BROWSER_EVENTS.transferstarted, function(result) {
                if (this.appletStatusBar)
                    this.appletStatusBar.setVisible(true);
            }, this);
//            this.on(BROWSER_EVENTS.transferstarted, function(result) {showTransfer("transferstarted", result)});

        },

        getTbarConfig : function()
        {
            // no toolbar on the filebrowser grid, we'll display our own so we can insert a ribbon panel
            return [];
        },

        uploadFile : function(fb, v)
        {
            if (this.currentDirectory)
            {
                var form = this.collapsibleTabPanel.getActiveTab().getForm();
                var path = this.fileInputField.getValue();
                var i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                var name = path.substring(i+1);
                var target = this.fileSystem.concatPaths(this.currentDirectory.data.path,name);
                var id = this.fileSystem.concatPaths(this.currentDirectory.data.uri, target);
                var file = this.fileSystem.recordFromCache(target);
                if (file)
                {
                    alert('file already exists on server: ' + name);
                }
                else
                {
                    var options = {method:'POST', url:this.currentDirectory.data.uri, record:this.currentDirectory, name:this.fileInputField.getValue()};
                    // set errorReader, so that handleResponse() doesn't try to eval() the XML response
                    // assume that we've got a WebdavFileSystem
                    form.errorReader = this.fileSystem.transferReader;
                    form.doAction(new Ext.form.Action.Submit(form, options));
                    this.fireEvent(BROWSER_EVENTS.transferstarted, {uploadType:"webform", files:[{name:name, id:id}]});
                    Ext.getBody().dom.style.cursor = "wait";                
                }
            }
        },

        uploadSuccess : function(f, action)
        {
            this.fileInputField.reset();
            Ext.getBody().dom.style.cursor = "pointer";
            console.log("upload actioncomplete");
            console.log(action);
            var options = action.options;
            // UNDONE: update data store directly
            this.toggleTabPanel();
            this.refreshDirectory();
            this.selectFile(this.fileSystem.concatPaths(options.record.data.path, options.name));
            this.fireEvent(BROWSER_EVENTS.transfercomplete, {uploadType:"webform", files:[{name:options.name, id:this.fileSystem.concatPaths(options.record.data.uri, options.name)}]});
        },

        uploadFailed : function(f, action)
        {
            this.fileInputField.reset();
            Ext.getBody().dom.style.cursor = "pointer";
            console.log("upload actionfailed");
            console.log(action);
            this.refreshDirectory();
        },

        /**
         * Initialize additional actions and components
         */
        initializeActions : function()
        {
            this.actions.upload = new Ext.Action({
                text: 'Upload',
                iconCls: 'iconUpload',
                tooltip: 'Upload files or folders from your local machine to the server',
                listeners: {click:function(button, event) {this.toggleTabPanel('uploadFileTab');}, scope:this}
            });

            this.actions.importData = new Ext.Action({
                text: 'Import Data',
                listeners: {click:function(button, event) {this.onImportData(button);}, scope:this},
                iconCls: 'iconDBCommit',
                tooltip: 'Import data from files into the database, or analyze data files'
            });

            this.actions.customize = new Ext.Action({
                text: 'Admin',
                iconCls: 'iconConfigure',
                tooltip: 'Configure the buttons shown on the toolbar',
                listeners: {click:function(button, event) {this.onAdmin(button);}, scope:this}
            });

            this.actions.appletFileAction = new Ext.Action({
                text:'Choose File...', scope:this, disabled:false, iconCls:'iconFileNew',
                handler:function(){
                    if (this.applet)
                    {
                        var a = this.applet.getApplet();
                        if (a) a.showFileChooser();
                    }
                }
            });

            this.actions.appletDirAction = new Ext.Action({
                text:'Choose Folder...', scope:this, disabled:false,  iconCls:'iconFileOpen',
                handler:function(){
                    if (this.applet)
                    {
                        var a = this.applet.getApplet();
                        if (a) a.showDirectoryChooser();
                    }
                }
            });

            this.toolbar = new Ext.Panel({
                id: 'toolbarPanel',
                renderTo: 'toolbar'
            });
        },

        /**
         * Override base class to add components to the file browser
         * @override
         */
        getItems : function()
        {
            var items = FilesWebPartPanel.superclass.getItems.call(this);

            this.initializeActions();

            this.importDataTab = new Ext.Panel({
                id: 'importDataTab'
            });

            this.fileInputField = new Ext.form.FileUploadField(
            {
                id: this.id ? this.id + 'Upload' : 'fileUpload',
                buttonText: "Browse...",
                fieldLabel: 'Choose a file'
            });

            var uploadPanel_rb1 = new Ext.form.Radio({
                style: 'background-color:#f0f0f0;',
                boxLabel: 'Single file', name: 'rb-auto', inputValue: 1, checked: true
            });
            var uploadPanel_rb2 = new Ext.form.Radio({
                boxLabel: 'Multiple file', name: 'rb-auto', inputValue: 2,
                listeners:{check:function(button, checked) {
                    if (checked)
                        this.onMultipleFileUpload();
                }, scope:this}
            });

            var uploadPanel = new Ext.FormPanel({
                id: 'uploadFileTab',
                formId : this.id ? this.id + 'Upload-form' : 'fileUpload-form',
                method : 'POST',
                fileUpload: true,
                enctype:'multipart/form-data',
                border:false,
                bodyStyle : 'background-color:#f0f0f0; padding:10px;',
                items: [
                    {
                    xtype: 'radiogroup',
                    fieldLabel: 'File Upload Type',
                    items: [
                        uploadPanel_rb1,
                        uploadPanel_rb2
                    ]},
                    this.fileInputField,
                    {xtype: 'textfield', fieldLabel: 'Description', width: 350}
                ],
                buttons:[
                    {text: 'Submit', handler:this.uploadFile, scope:this},
                    {text: 'Cancel', listeners:{click:function(button, event) {this.toggleTabPanel('uploadFileTab');}, scope:this}}
                ],
                listeners: {
                    "actioncomplete" : {fn: this.uploadSuccess, scope: this},
                    "actionfailed" : {fn: this.uploadFailed, scope: this}
                }
            });
            uploadPanel.on('beforeshow', function(c){uploadPanel_rb1.setValue(true); uploadPanel_rb2.setValue(false);}, this);

            var uploadMultiPanel_rb1 = new Ext.form.Radio({
                boxLabel: 'Single file', name: 'rb-auto', inputValue: 1,
                listeners:{check:function(button, checked) {
                    if (checked)
                        this.toggleTabPanel('uploadFileTab');
                }, scope:this}
            });
            var uploadMultiPanel_rb2 = new Ext.form.Radio({
                boxLabel: 'Multiple file', name: 'rb-auto', inputValue: 2, checked: true
            });

            this.progressBar = new Ext.ProgressBar({id:'appletStatusProgressBar'});
            this.appletStatusBar = new Ext.StatusBar({
                id:'appletStatusBar', defaultText:'', busyText:'Copying...',
                width: 200,
                hidden: true,
                statusAlign: 'right',
                style : 'background-color:#f0f0f0;',
                items:[{
                    xtype:'panel', layout:'fit', border:false, items:this.progressBar, width:120, minWidth:120
                }]
            });

            this.appletPanel = new Ext.Panel({
                fieldLabel: 'File and Folder Drop Target',
                isFormField: true,
                height: 60,
                width: 325
            });

            var uploadMultiPanel = new Ext.FormPanel({
                id: 'uploadMultiFileTab',
                layout: 'form',
                border:false,
                bodyStyle : 'background-color:#f0f0f0; padding:10px;',
                items: [{
                    xtype: 'radiogroup',
                    fieldLabel: 'File Upload Type',
                    items: [
                        uploadMultiPanel_rb1,
                        uploadMultiPanel_rb2
                    ]},
                    this.appletPanel
/*
                    new Ext.Panel({
                        layout: 'table',
                        border: false,
                        layoutConfig: {
                            columns:2
                        },
                        fieldLabel: 'File and Folder Drop Target',
                        isFormField: true,
                        items: [
                            this.appletPanel
                        ]
                    })
*/
                ],
                buttons:[
                    new Ext.Button(this.actions.appletFileAction),
                    new Ext.Button(this.actions.appletDirAction),
                    {text: 'Cancel', listeners:{click:function(button, event) {this.toggleTabPanel('uploadMultiFileTab');}, scope:this}},
                        this.appletStatusBar
                ]
            });
            uploadMultiPanel.on('beforeshow', function(c){uploadMultiPanel_rb1.setValue(false); uploadMultiPanel_rb2.setValue(true);}, this);

            this.collapsibleTabPanel = new TinyTabPanel({
                region: 'north',
                collapseMode: 'mini',
                height: 130,
                header: false,
                margins:'1 1 1 1',
                bodyStyle: 'background-color:#f0f0f0;',
                cmargins:'1 1 1 1',
                collapsible: true,
                collapsed: true,
                hideCollapseTool: true,
                activeTab: 'uploadFileTab',
                deferredRender: false,
                items: [
                    uploadPanel,
                    uploadMultiPanel
                ]});

            items.push(this.collapsibleTabPanel);
            return items;
        },

        onMultipleFileUpload : function()
        {
            this.toggleTabPanel('uploadMultiFileTab');            
            if (!this.applet)
            {
                var uri = new URI(this.fileSystem.prefixUrl);  // implementation leaking here
                var url = uri.toString();
                this.applet = new TransferApplet({url:url, directory:this.currentDirectory.data.path});

                this.applet.on(TRANSFER_EVENTS.update, this.updateProgressBar, this);
                this.applet.on(TRANSFER_EVENTS.update, this.fireUploadEvents, this);
                this.applet.getTransfers().on(STORE_EVENTS.update, this.updateProgressBarRecord, this);

                // make sure that the applet still matches the current directory when it appears
                this.applet.onReady(function()
                {
                    this.updateAppletState(this.currentDirectory);
                }, this);

                this.appletPanel.add(this.applet);
                this.appletPanel.doLayout();
            }
            else
            {
                var task = {
                    interval:100,
                    applet:this.applet,
                    run : function()
                    {
                        if (this.applet.isActive())
                        {
                            this.applet.setText("Drop files and folders here");
                            Ext.TaskMgr.stop(this);
                        }
                    }
                };
                Ext.TaskMgr.start(task);
            }
        },

        toggleTabPanel : function(tabId)
        {
            if (!tabId)
                this.collapsibleTabPanel.collapse();
            
            if (this.collapsibleTabPanel.isVisible())
            {
                var activeTab = this.collapsibleTabPanel.getActiveTab();

                if (activeTab && activeTab.getId() == tabId)
                    this.collapsibleTabPanel.collapse();
                else
                    this.collapsibleTabPanel.setActiveTab(tabId);
            }
            else
            {
                this.collapsibleTabPanel.setActiveTab(tabId);
                this.collapsibleTabPanel.expand();
            }
        },

        createGrid : function()
        {
            // mild convolution to pass fileSystem to the _attachPreview function
            var iconRenderer = renderIcon.createDelegate(null,_attachPreview.createDelegate(this.fileSystem,[],true),true);
            var sm = new Ext.grid.CheckboxSelectionModel();

            var grid = new Ext.grid.GridPanel(
            {
                store: this.store,
                border:false,
                selModel : sm,
                loadMask:{msg:"Loading, please wait..."},
                columns: [
                    sm,
                    {header: "", width:20, dataIndex: 'iconHref', sortable: false, hidden:false, renderer:iconRenderer},
                    {header: "Name", width: 250, dataIndex: 'name', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode},
                    {header: "Modified", width: 150, dataIndex: 'modified', sortable: true, hidden:false, renderer:renderDateTime},
                    {header: "Size", width: 80, dataIndex: 'size', sortable: true, hidden:false, align:'right', renderer:renderFileSize},
                    {header: "Usages", width: 150, dataIndex: 'actionHref', sortable: true, hidden:false, renderer:renderUsage},
                    {header: "Created By", width: 150, dataIndex: 'createdBy', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode}
                ]
            });
            // hack to get the file input field to size correctly
            grid.on('render', function(c){this.fileInputField.setSize(350);}, this);
            return grid;
        },

        onDirectoryChange : function(record)
        {
            var path = record.data.path;
            if (startsWith(path,"/"))
                path = path.substring(1);
            var requestid = this.actionsConnection.request({
                autoAbort:true,
                url:actionsURL + encodeURIComponent(path),
                method:'GET',
                disableCaching:false,
                success : this.updatePipelineActions,
                scope: this
            });
        },

        updatePipelineActions : function(response)
        {
            var o = eval('var $=' + response.responseText + ';$;');
            var actions = o.success ? o.actions : [];

            var toolbarActions = [];
            var importActions = [];

            if (actions && actions.length)
            {
                for (var i=0; i < actions.length; i++)
                {
                    var pUtil = new LABKEY.PipelineActionUtil(actions[i]);
                    var links = pUtil.getLinks();

                    if (!links) continue;

                    for (var j=0; j < links.length; j++)
                    {
                        var link = links[j];

                        if (link.display != 'disabled' && link.href)
                        {
                            link.handler = this.executePipelineAction;
                            link.scope = this;
                            if (link.display == 'toolbar')
                                this.addActionLink(toolbarActions, pUtil, link);
                            else
                                this.addActionLink(importActions, pUtil, link);
                        }
                    }
                }
            }

            this.displayPipelineActions(toolbarActions, importActions)
        },

        /**
         * Helper to add pipeline action items to an object array
         */
        addActionLink : function(list, parent, link)
        {
            var action= list[parent.getText()];

            if (!action)
            {
                // create a new data object to hold this link
                action = new LABKEY.PipelineActionUtil({
                    multiSelect: parent.multiSelect,
                    description: parent.description,
                    files: parent.getFiles(),
                    links: {
                        id: parent.links.id,
                        text: parent.links.text,
                        href: parent.links.href
                    }
                });
                action.clearLinks();
                list[parent.getText()] = action;
            }
            action.addLink(link);
        },
        
        displayPipelineActions : function(toolbarActions, importActions)
        {
            // delete the actions container
            if (this.importDataTab && this.importDataTab.items)
                this.importDataTab.remove('importDataPanel');

            if (this.toolbar && this.toolbar.items)
                this.toolbar.remove('toolbarPanelToolbar');

            var tbarButtons = [];
            var importDataButtons = [];

            this.pipelineActions = [];
            this.importActions = [];

            // add any import data actions
            for (action in importActions)
            {
                var a = importActions[action];
                if ('object' == typeof a )
                {
                    var importAction = new LABKEY.PipelineAction(a.getActionConfig());
                    importDataButtons.push(importAction);

                    this.importActions.push(importAction);
                    this.pipelineActions.push(importAction);
                }
            }

            // add the standard buttons to the toolbar
            if (this.buttonCfg && this.buttonCfg.length)
            {
                for (var i=0; i < this.buttonCfg.length; i++)
                {
                    var item = this.buttonCfg[i];
                    if (typeof item == "string" && typeof this.actions[item] == "object")
                    {
                        // don't add the import data button if there are no import data actions
                        if (this.importActions.length || item != 'importData')
                            tbarButtons.push(new Ext.Button(this.actions[item]));
                    }
                    else
                        tbarButtons.push(item);
                }
            }

            // now add the configurable pipleline actions
            for (action in toolbarActions)
            {
                var a = toolbarActions[action];
                if ('object' == typeof a )
                {
                    var tbarAction = new LABKEY.PipelineAction(a.getActionConfig());
                    tbarButtons.push(new Ext.Button(tbarAction));

                    this.pipelineActions.push(tbarAction);
                }
            }

            // add the appropriate import actions to the import data tabpanel
            var importData = new Ext.Panel({
                id: 'importDataPanel',
                bodyStyle : 'background-color:#f0f0f0; padding:10px;',
                items: new Ext.Toolbar({items:importDataButtons}),
                buttons:[
                    {text: 'Cancel', listeners:{click:function(button, event) {this.toggleTabPanel('importDataTab');}, scope:this}}
                ],
                buttonAlign: 'center'
            });
            this.importDataTab.add(importData);
            this.importDataTab.doLayout();

            var toolbar = new Ext.Toolbar({
                id: 'toolbarPanelToolbar',
                //renderTo: 'toolbar',
                border: false,
                items: tbarButtons
            });
            this.toolbar.add(toolbar);
            this.toolbar.doLayout();
        },

        // selection change handler
        onSelectionChange : function(record)
        {
            if (this.pipelineActions)
            {
                var selections = this.grid.selModel.getSelections();
                if (!selections.length && this.grid.store.data)
                {
                    selections = this.grid.store.data.items;
                }

                if (selections.length)
                {
                    var selectionMap = {};

                    for (var i=0; i < selections.length; i++)
                        selectionMap[selections[i].data.name] = true;

                    for (var i=0; i <this.pipelineActions.length; i++)
                    {
                        var action = this.pipelineActions[i];
                        if (action.initialConfig.files && action.initialConfig.files.length)
                        {
                            var selectionCount = 0;
                            for (var j=0; j <action.initialConfig.files.length; j++)
                            {
                                if (action.initialConfig.files[j] in selectionMap)
                                {
                                    selectionCount++;
                                }
                            }
                            if (action.initialConfig.multiSelect)
                            {
                                selectionCount > 0 ? action.enable() : action.disable();
                            }
                            else
                            {
                                selectionCount == 1 ? action.enable() : action.disable();
                            }
                        }
                    }
                }
            }

            this.actions.importData.disable();
            if (this.importActions && this.importActions.length)
            {
                for (var i=0; i < this.importActions.length; i++)
                {
                    var action = this.importActions[i];
                    if (!action.isDisabled())
                        this.actions.importData.enable();
                }
            }
        },

        executePipelineAction : function(item, e)
        {
            var selections = this.grid.selModel.getSelections();
            var action = item.isAction ? item : new LABKEY.PipelineAction(item);

            // if there are no selections, treat as if all are selected
            if (selections.length == 0)
            {
                var selections = [];
                var store = this.grid.getStore();

                for (var i=0; i <store.getCount(); i++)
                {
                    var record = store.getAt(i);
                    if (record.data.file)
                        selections.push(record);
                }
            }

            if (action && action.initialConfig.href)
            {
                if (selections.length == 0)
                {
                    Ext.Msg.alert("Rename Views", "There are no views selected");
                    return false;
                }

                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", item.initialConfig.href);

                for (var i=0; i < selections.length; i++)
                {
                    var files = action.getFiles();
                    for (var j = 0; j < files.length; j++)
                    {
                        if (files[j] == selections[i].data.name)
                        {
                            var fileField = document.createElement("input");
                            fileField.setAttribute("name", "file");
                            fileField.setAttribute("value", selections[i].data.name);
                            form.appendChild(fileField);
                            break;
                        }
                    }
                }

                document.body.appendChild(form);    // Not entirely sure if this is necessary
                form.submit();
            }
        },

        onAdmin : function(btn)
        {
            var configDlg = new LABKEY.ActionsAdminPanel({path: this.currentDirectory.data.path});

            configDlg.on('success', function(c){this.onDirectoryChange(this.currentDirectory);}, this);
            configDlg.on('failure', function(){Ext.Msg.alert("Update Action Config", "Update Failed")});

            configDlg.show();
        },

        onImportData : function(btn)
        {
            var actionMap = [];
            var actions = [];
            var checked = true;

            for (var i=0; i < this.importActions.length; i++)
            {
                var pa = this.importActions[i];

                if (!pa.isDisabled())
                {
                    var links = pa.getLinks();
                    var fieldLabel = pa.getText();
                    for (var j=0; j < links.length; j++)
                    {
                        var link = links[j];

                        actionMap[link.id] = link;
                        actions.push({
                            //xtype: 'radio',
                            fieldLabel: fieldLabel,
                            checked: checked,
                            labelSeparator: '',
                            boxLabel: link.text,
                            name: 'importAction',
                            inputValue: link.id
                            //width: 250
                        });
                        fieldLabel = '';
                        checked = false;
                    }
                }
            }
            var actionPanel = new Ext.form.FormPanel({
                bodyStyle : 'padding:10px;',
                defaultType: 'radio',
                items: actions
            });

            var win = new Ext.Window({
                title: 'Import Data',
                width: 400,
                height: 300,
                cls: 'extContainer',
                autoScroll: true,
                closeAction:'close',
                modal: true,
                items: actionPanel,
                buttons: [{
                    text: 'Import',
                    id: 'btn_submit',
                    listeners: {click:function(button, event) {
                        this.submitForm(actionPanel, actionMap);
                        win.close();
                    }, scope:this}
                },{
                    text: 'Cancel',
                    id: 'btn_cancel',
                    handler: function(){win.close();}
                }]
            });
            win.show();
        },

        submitForm : function (panel, actionMap)
        {
            // client side validation
            var form = panel.getForm();
            var selection = form.getValues();
            var action = actionMap[selection.importAction];

            if ('object' == typeof action)
            {
                var a = new LABKEY.PipelineAction(action);
                a.execute(a);
            }
        }
    });

    if (!fileSystem)
        fileSystem = new LABKEY.WebdavFileSystem({
            baseUrl:rootPath,
            rootName:'fileset'
        });

    fileBrowser = new FilesWebPartPanel({
        fileSystem: fileSystem,
        helpEl:null,
        showAddressBar: <%=bean.isShowAddressBar()%>,
        showFolderTree: <%=bean.isShowFolderTree()%>,
        showProperties: false,
        showFileUpload: false,
        showDetails: <%=bean.isShowDetails()%>,
        allowChangeDirectory: true,
        actions: {drop:dropAction, configure:configureAction},
        buttonCfg: buttonActions
/*
        buttonCfg:['download','deletePath','refresh'
        <%=c.hasPermission(context.getUser(), InsertPermission.class)?",'uploadTool'":""%>
        ,'->'
        , new Ext.form.Label({html:'File Set:&nbsp;'}), combo
        <%=c.hasPermission(context.getUser(), AdminPermission.class)?",'configure'":""%>
        ]
*/
    });

    fileBrowser.height = 300;
/*
    fileBrowser.on("doubleclick", function(record){
        var contentType = record.data.contentType || "attachment";
        var location = "<%=PageFlowUtil.encodePath(request.getContextPath())%>/files<%=c.getEncodedPath()%>" + encodeURI(record.data.name) + "?renderAs=DEFAULT<%=me.getFileSet()==null ? "" : "&fileSet=" + PageFlowUtil.encode(me.getFileSet())%>";
        if (0 == contentType.indexOf("image/") || 0 == contentType.indexOf("text/"))
            window.open(location,"_blank");
        else
            window.location = location;
        });
*/

//    var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
//    resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));

    fileBrowser.render('files');

    var _resize = function(w,h)
    {
        if (!fileBrowser.rendered)
            return;
        var padding = [20,20];
        var xy = fileBrowser.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-padding[0]),
            height : Math.max(100,h-xy[1]-padding[1])};
        fileBrowser.setSize(size);
        fileBrowser.doLayout();
    };

    if (autoResize)
    {
        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();
    }

    fileBrowser.start(dir);

    <% //Temporary code for testing events
    if (AppProps.getInstance().isDevMode())
    {
    %>
        fileBrowser.on(BROWSER_EVENTS.transfercomplete, function(result) {showTransfer("transfercomplete", result)});
        fileBrowser.on(BROWSER_EVENTS.transferstarted, function(result) {showTransfer("transferstarted", result)});
        function showTransfer(heading, result)
        {
            console.log("Transfer event: " + heading);
            for (var fileIndex = 0; fileIndex <result.files.length; fileIndex++)
                console.log("  name: " + result.files[fileIndex].name + ", id: " + result.files[fileIndex].id);
        }
    <%
    }
    %>
}
    var fileSets = [
<%
        boolean navigate = false;
        String selectedValue = null;
        ActionURL url = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c);
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        AttachmentDirectory main = svc.getMappedAttachmentDirectory(c, false);
        if (null != main && null != main.getFileSystemDirectory())
        {
            String value = navigate ? url.getLocalURIString() : "/";
            out.write("[" + q(value) + ",'Default']");
            if (StringUtils.isEmpty(me.getFileSet()) || StringUtils.equals(me.getFileSet(),"Default"))
                selectedValue = value;
        }
        for (AttachmentDirectory attDir : svc.getRegisteredDirectories(c))
        {
            String name = attDir.getLabel();
            url.replaceParameter("fileSetName",name);
            String value = navigate ? url.getLocalURIString() : "/@files/" + name;
            out.write(",[" + q(value) + "," + q(name) + "]");
            if (StringUtils.equals(me.getFileSet(),name))
                selectedValue = value;
        }
        if (c.hasPermission(context.getUser(), AdminPermission.class))
        {
    //        out.write(",[" + q(new ActionURL(FileContentController.ShowAdminAction.class,c).getLocalURIString()) + ",'[configure]']");
        }
%>
    ];

    var selectedValue = <%=q(selectedValue)%>;

    Ext.onReady(function(){renderBrowser(<%=q(bean.getRootPath())%>, <%=q(startDir)%>);});
</script>