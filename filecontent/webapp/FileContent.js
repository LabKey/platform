/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This class extends the filebrowser widget to provide management and execution
 * of pipeline actions. The following configs are used by the base class:
 *
 * This class introduces a  optional config parameter, rootOffset which indicates the path between the root of
 * exposed in the container and the root of the WebDAV file system tree used for this webpart. This allows
 * display of a subtree rooted below the default root for the container.
 */
LABKEY.FilesWebPartPanel = Ext.extend(LABKEY.ext.FileBrowser, {

    containerPath : undefined,              // the containerPath of the files being shown
    path : undefined,

    adminOptions : undefined,               // LABKEY.FileContentConfig object which manages admin options
    toolbarActions : undefined,             // map of actionId to Ext.Action
    hasToolbarButtons : false,              // true if there are any pipeline actions on the toolbar
    tbarItemsConfig : [],                   // array of config options for the standard toolbar buttons

    pipelineActions : undefined,            // array of labkey pipeline actions
    actionMap : {},                         // map of actionId to labkey pipeline actions

    importDataEnabled : true,
    adminUser : false,
    isPipelineRoot : false,
    selectionProcessed : false,
    rootOffset : '',

    constructor : function(config)
    {
        config.fileFilter = {test: function(record){
            return record.name.indexOf('.') != 0;
        }};
        LABKEY.FilesWebPartPanel.superclass.constructor.call(this, config);
        if (this.rootOffset && this.rootOffset.length > 0)
            this.rootOffset = LABKEY.Utils.endsWith(this.rootOffset, "/") ? this.rootOffset : this.rootOffset + "/";
    },

    initComponent : function()
    {
        LABKEY.FilesWebPartPanel.superclass.initComponent.call(this);

        Ext.apply(this, {
            actionsURL : LABKEY.ActionURL.buildURL('pipeline', 'actions', this.containerPath, {path:''}),
            actionsConfigURL : LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', this.containerPath)
        });

        this.grid.store.on("datachanged", this.onGridDataChange, this);
        this.on(LABKEY.FileSystem.BROWSER_EVENTS.selectionchange,function(record){this.onSelectionChange(record);}, this);
        this.on(LABKEY.FileSystem.BROWSER_EVENTS.directorychange,function(record){this.onGridDataChanged(false);}, this);

        // message templates
        var typeTemplate = new Ext.XTemplate('<tpl if="icon == undefined">{type}</tpl><tpl if="icon != undefined"><img src="{icon}" alt="{type}"></tpl>').compile();

        this.shortMsg = new Ext.XTemplate('<span style="margin-left:5px;" class="labkey-mv">{msg}</span>').compile();
        this.shortMsgEnabled = new Ext.XTemplate('<span style="margin-left:5px;" class="labkey-mv">using {count} out of {total} file(s)</span>').compile();

        var fileListTemplate =
                '<tpl for="files">' +
                    '<tpl if="xindex &lt; 11">' +
                        '<span style="margin-left:8px;">{.}</span><br>' +
                    '</tpl>' +
                    '<tpl if="xindex == 11">' +
                        '<span style="margin-left:8px;">... too many files to display</span><br>' +
                    '</tpl>' +
                '</tpl>';

        this.longMsgEnabled = new Ext.XTemplate('This action will use the selected file(s):<br>', fileListTemplate).compile();
        this.longMsgNoMultiSelect = new Ext.XTemplate('This action can only operate on one file at a time from the selected list:<br>', fileListTemplate).compile();
        this.longMsgNoMatch = new Ext.XTemplate('This action can only operate on this list of file(s):<br>', fileListTemplate).compile();
        this.longMsgNoSelection = new Ext.XTemplate('This action requires selection from this list of file(s):<br>', fileListTemplate).compile();

        this.adminOptions = new LABKEY.FileContentConfig({
            inheritedFileConfig: {
                containerPath: this.containerPath
            }
        });

        var btnUpdateTask = new Ext.util.DelayedTask(this.updateToolbarButtons, this);
        this.adminOptions.on('actionConfigChanged', function(){btnUpdateTask.delay(250);});
        this.adminOptions.on('tbarConfigChanged', function(){btnUpdateTask.delay(250);});

        this.adminOptions.on('filePropConfigChanged', this.onFilePropConfigChanged, this);
        this.adminOptions.on('gridConfigChanged', this.onGridConfigChanged, this);

        this.on(LABKEY.FileSystem.BROWSER_EVENTS.transfercomplete, this.onCustomFileProperties, this);

        // get the initial admin configuration
        this.updateActionConfiguration(false, false);
    },

    /**
     * Create the set of available actions
     */
    createActions : function()
    {
        var actions = LABKEY.FilesWebPartPanel.superclass.createActions.call(this);

        actions.importData = new Ext.Action({
            cls: 'importDataBtn',
            text: 'Import Data',
            listeners: {click:function(button, event) {this.onImportData(button);}, scope:this},
            iconCls: 'iconDBCommit',
            disabledClass:'x-button-disabled',
            tooltip: 'Import data from files into the database, or analyze data files'
        });

        actions.customize = new Ext.Action({
            cls: 'customizeBtn',
            text: 'Admin',
            iconCls: 'iconConfigure',
            disabledClass:'x-button-disabled',
            disabled : true,
            tooltip: 'Configure the buttons shown on the toolbar',
            listeners: {click:function(button, event) {this.onAdmin(button);}, scope:this}
        });

        actions.editFileProps = new Ext.Action({
            cls: 'editFilePropsBtn',
            text: 'Edit Properties',
            iconCls: 'iconEditFileProps',
            disabledClass:'x-button-disabled',
            tooltip: 'Edit properties on the selected file(s)',
            listeners: {click:function(button, event) {this.onEditFileProps(button);}, scope:this},
            hideText: true
        });

        actions.emailPreferences = new Ext.Action({
            cls: 'emailPreferencesBtn',
            text: 'Email Preferences',
            iconCls: 'iconEmailSettings',
            disabledClass:'x-button-disabled',
            tooltip: 'Configure email notifications on file actions.',
            listeners: {click:function(button, event) {this.onEmailPreferences(button);}, scope:this},
            hideText: true
        });

        actions.auditLog = new Ext.Action({
            cls: 'auditLogBtn',
            text: 'Audit History',
            iconCls: 'iconAuditLog',
            disabledClass:'x-button-disabled',
            tooltip: 'View the files audit log for this folder.',
            listeners: {
                scope: this,
                click: function(button, event) {window.location = LABKEY.ActionURL.buildURL('filecontent', 'showFilesHistory', this.containerPath);}
            }
        });
        return actions;
    },

    createDefaultColumnModel : function(sm)
    {
        // mild convolution to pass fileSystem to the _attachPreview function
        var iconRenderer = LABKEY.FileSystem.Util.renderIcon.createDelegate(null,this.attachPreview.createDelegate(this.fileSystem,[],true),true);
        var cm = [sm,
                {header: "",               width: 20,  dataIndex: 'iconHref',    sortable: false, hidden: false, renderer:iconRenderer},
                {header: "Name",           width: 250, dataIndex: 'name',        sortable: true,  hidden: false, renderer:Ext.util.Format.htmlEncode},
                {header: "Last Modified",  width: 150, dataIndex: 'modified',    sortable: true,  hidden: false, renderer:LABKEY.FileSystem.Util.renderDateTime},
                {header: "Size",           width: 80,  dataIndex: 'size',        sortable: true,  hidden: false, align:'right', renderer:LABKEY.FileSystem.Util.renderFileSize},
                {header: "Created By",     width: 100, dataIndex: 'createdBy',   sortable: true,  hidden: false, renderer:Ext.util.Format.htmlEncode},
                {header: "Description",    width: 100, dataIndex: 'description', sortable: true,  hidden: false, renderer:Ext.util.Format.htmlEncode},
                {header: "Usages",         width: 100, dataIndex: 'actionHref',  sortable: true,  hidden: false, renderer:LABKEY.FileSystem.Util.renderUsage},
                {header: "Download Link",  width: 100, dataIndex: 'fileLink',    sortable: true,  hidden: true},
                {header: "File Extension", width: 80,  dataIndex: 'fileExt',     sortable: true,  hidden: true,  renderer:Ext.util.Format.htmlEncode}
            ];

        return cm;
    },

    createGrid : function()
    {
        var sm = new Ext.grid.CheckboxSelectionModel();
        var cm = new Ext.grid.ColumnModel({
            defaultSortable : true,
            columns : this.createDefaultColumnModel(sm)
        });
        var grid = new Ext.grid.GridPanel({
            store: this.store,
            cls: 'labkey-filecontent-grid',
            border:false,
            selModel : sm,
            loadMask:{msg:"Loading, please wait..."},
            colModel : cm
        });

        // hack to get the file input field to size correctly
        grid.on('render',   function(c){this.fileUploadField.setSize(350);}, this);
        grid.on('dblclick', function(e){this.renderFile(e);},                this);

        return grid;
    },

    /**
     * Called when the list of files is reloaded or is changed
     */
    onGridDataChange : function()
    {
        this.onGridDataChanged(false);
        this.enableImportData(false);
        this.path = this.currentDirectory.data.path;
        if (LABKEY.FileSystem.Util.startsWith(this.path,"/"))
            this.path = this.path.substring(1);

        if (this.isPipelineRoot)
        {
            Ext.Ajax.request({
                url:this.actionsURL + encodeURIComponent(this.rootOffset + this.path),
                method:'GET',
                disableCaching:false,
                success : function(resp, opt){
                    this.updatePipelineActions(resp, opt);
                    this.onGridDataChanged(true)
                },
                failure: function(resp, opt){
                    if (resp.status == 404) // see issue 14691
                        return;
                    this.onGridDataChanged(true);
                    LABKEY.Utils.displayAjaxErrorResponse(resp, opt);
                },
                updateSelection: true,
                scope: this
            });
        }
        else
            this.onGridDataChanged(true);
    },

    updateActionConfiguration : function(updatePipelineActions, updateSelection) {

        Ext.Ajax.request({
            url:this.actionsConfigURL,
            method:'GET',
            disableCaching:false,
            success : this.getActionConfiguration,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            scope: this,
            updatePipelineActions: updatePipelineActions,
            updateSelection: updateSelection
        });
    },

    // parse the configuration information
    getActionConfiguration : function(response, e)
    {
        var o = Ext.decode(response.responseText);
        var config = o.success ? o.config : {};

        // check whether the import data button is enabled
        this.importDataEnabled = config.importDataEnabled ? config.importDataEnabled : false;
        this.enableAdmin(false);

        var newActions = [];
        if ('object' == typeof config.actions)
        {
            for (var i=0; i < config.actions.length; i++)
            {
                newActions.push(config.actions[i]);
            }
        }
        this.adminOptions.setFilePropConfig(config.fileConfig);
        this.adminOptions.setTbarBtnConfig(config.tbarActions);
        this.adminOptions.setActionConfigs(newActions);
        this.adminOptions.inheritedFileConfig = config.inheritedFileConfig;

        if (o.success)
        {
            this.adminOptions.setFileFields(o.fileProperties);
            this.adminOptions.setGridConfig(config.gridConfig);
        }

        // update the expand collapse state of the file upload panel
        if (this.uploadEnabled && !this.disableGeneralAdminSettings && (config.expandFileUpload != undefined))
        {
            if (config.expandFileUpload)
                this.fileUploadPanel.expand();
            else
                this.fileUploadPanel.collapse();

            // push the new state into the action initial config because the toolbar buttons
            // need to be recreated from the original actions
            this.actions.upload.initialConfig.pressed = config.expandFileUpload;
        }

        if (e.updatePipelineActions && this.isPipelineRoot)
        {
            Ext.Ajax.request({
                url:this.actionsURL + encodeURIComponent(this.rootOffset + this.path),
                method:'GET',
                disableCaching:false,
                success : this.updatePipelineActions,
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                failure: function(resp, opt){
                    if (resp.status == 404) // see issue 14691
                        return;
                    LABKEY.Utils.displayAjaxErrorResponse(resp, opt);
                },
                scope: this,
                updateSelection: e.updateSelection
            });
        }
    },

    /**
     * Helper to enable and disable the import data action, marker classes
     * are used to help with automated tests.
     */
    enableImportData : function(enabled) {

        var el = this.getTopToolbar().getEl();

        if (enabled)
        {
            el.addClass('labkey-import-enabled');
            this.actions.importData.enable();
        }
        else
        {
            el.removeClass('labkey-import-enabled');
            this.actions.importData.disable();
        }
    },

    /**
     * Helper to enable and disable the import data action, marker classes
     * are used to help with automated tests.
     */
    enableAdmin : function(enabled) {

        var el = this.getTopToolbar().getEl();

        if (enabled)
        {
            el.addClass('labkey-admin-enabled');
            this.actions.customize.enable();
        }
        else
        {
            el.removeClass('labkey-admin-enabled');
            this.actions.customize.disable();
        }
    },

    updatePipelineActions : function(response, e)
    {
        var o = Ext.decode(response.responseText);
        var actions = o.success ? o.actions : [];

        this.pipelineActions = [];
        this.actionMap = {};
        this.actionGroups = {};

        if (actions && actions.length && this.canImportData())
        {
            var pipelineActions = LABKEY.util.PipelineActionUtil.parseActions(actions);
            for (var i=0; i < pipelineActions.length; i++)
            {
                var pa = pipelineActions[i];
                var isEnabled = false;

                if (!pa.link) continue;

                var config = this.adminOptions.getActionConfig(pa.groupId);
                if (pa.link.href)
                {
                    var display = 'enabled';
                    if (config)
                    {
                        var linkConfig = config.getLink(pa.link.id);
                        if (linkConfig)
                            display = linkConfig.display;
                    }

                    pa.link.enabled = (display != 'disabled');
                    if (pa.link.enabled)
                        isEnabled = true;
                }

                if (this.adminUser || isEnabled)
                {
                    this.pipelineActions.push(pa);
                    this.actionMap[pa.id] = pa;

                    if (pa.groupId in this.actionGroups)
                        this.actionGroups[pa.groupId].actions.push(pa);
                    else
                        this.actionGroups[pa.groupId] = {label: pa.groupLabel, actions: [pa]};
                }
            }
        }
        if (this.pipelineActions && this.pipelineActions.length)
            this.enableImportData(true);

        if (e.updateSelection)
        {
            // if there are toolbar buttons showing, refresh the current selection state, else
            // handle it lazily if/when the import data dialog is shown.
            
            if (this.hasToolbarButtons)
                this.onSelectionChange();
            else
                this.selectionProcessed = false;
        }
    },

    updateToolbarButtons : function()
    {
        this.enableAdmin(false);
        var toolbar = this.getTopToolbar();
        if (toolbar && toolbar.items)
        {
            toolbar.removeAll(true);
            toolbar.setAutoScroll(true);
        }

        // add the standard buttons to the toolbar
        var buttons = [];
        this.tbarItemsConfig = this.adminOptions.createStandardButtons(this.tbarItems);

        for (i=0; i < this.tbarItemsConfig.length; i++)
        {
            var cfg = this.tbarItemsConfig[i];
            var action = this.actions[cfg.id];
            if (typeof action == "object")
            {
                // don't add the import data button if there are no import data actions
                if (cfg.id == 'importData' && !this.showImportData())
                {
                    continue;
                }
                buttons.push(action);
                this.adjustAction(action, cfg.hideText,  cfg.hideIcon);
            }
        }

        // now add the configurable pipeline actions
        this.toolbarActions = {};
        this.hasToolbarButtons = false;

        var actionConfigs = this.adminOptions.getActionConfigs();
        for (var i=0; i < actionConfigs.length; i++)
        {
            var a = actionConfigs[i];
            if (a.isDisplayOnToolbar())
            {
                var action = this.createButtonAction(a);
                if (action)
                {
                    this.hasToolbarButtons = true;
                    buttons.push(action);
                }
            }
        }

        if (toolbar && buttons.length)
        {
            toolbar.addButton(buttons);
            // force a relayout on this component
            toolbar.doLayout();
        }
        this.enableAdmin(true);
    },

    createButtonAction : function(cfg)
    {
        if (!cfg.links && !cfg.links.length) return null;

        var items = [];
        for (var i=0; i < cfg.links.length; i++)
        {
            var link = cfg.links[i];
            if (link.display == 'toolbar')
            {
                var action = new Ext.Action({actionId: link.id, text: link.label, handler: this.executeToolbarAction, scope: this, tooltip: link.label});
                this.toolbarActions[link.id] = action;

                items.push(action);
            }
        }

        if (items.length > 1)
        {
            // an action with a menu button
            return new Ext.Action({text: cfg.label, tooltip: cfg.label, menu: {
                    cls: 'extContainer',
                    items: items
                }
            });
        }
        else if (items.length == 1)
        {
            // a single button, use the parent action label with the child action as a tooltip
            action = items[0];
            return action;
        }

        return null;
    },

    adjustAction : function(action, hideText, hideIcon)
    {
        if (hideText != undefined)
        {
            action.initialConfig.hideText = hideText;

            if (hideText)
                action.setText(undefined);
            else
                action.setText(action.initialConfig.prevText);
        }

        if (hideIcon != undefined)
        {
            action.initialConfig.hideIcon = hideIcon;
            if (hideIcon)
                action.setIconClass(undefined);
            else
                action.setIconClass(action.initialConfig.prevIconCls);
        }
    },

    showImportData : function()
    {
        if (this.adminUser)
            return true;

        if (this.importDataEnabled)
            return true;

        return false;
    },

    // selection change handler
    onSelectionChange : function(record)
    {
        this.enableImportData(false);
        if (this.pipelineActions)
        {
            var selections = this.grid.selModel.getSelections();
            var emptySelection = false;

            if (this.adminOptions.isCustomFileProperties())
                this.actions.editFileProps.setDisabled(!selections.length);
            else
                this.actions.editFileProps.setDisabled(true);
            
            if (!selections.length && this.grid.store.data)
            {
                emptySelection = true;
                selections = this.grid.store.data.items;
            }

            if (selections.length)
            {
                var selectionMap = {};

                for (var i=0; i < selections.length; i++)
                    selectionMap[selections[i].data.name] = true;

                for (i=0; i <this.pipelineActions.length; i++)
                {
                    var action = this.pipelineActions[i];
                    var files = action.getFiles();
                    var selectionCount = 0;
                    var selectedFiles = [];

                    if (emptySelection && !action.emptySelect)
                    {
                        action.setEnabled(false);
                        action.setMessage(
                                this.shortMsg.apply({msg: 'a file must be selected first'}),
                                this.longMsgNoSelection.apply({files: files}));
                    }
                    else if (files && files.length)
                    {
                        for (var j=0; j < files.length; j++)
                        {
                            if (files[j] in selectionMap)
                            {
                                selectionCount++;
                                selectedFiles.push(files[j]);
                            }
                            // special case for flow actions, TODO: get the flow guys to buy into the
                            // idea of either sending down all files in the folder or running the action
                            // from the parent folder (with the child folder selected)
                            else if (emptySelection && action.emptySelect)
                            {
                                if (files[j] == this.currentDirectory.data.name)
                                {
                                    selectionCount++;
                                    selectedFiles.push(files[j]);
                                }
                            }
                        }

                        if (selectionCount >= 1)
                        {
                            if (selectionCount == 1 || action.multiSelect)
                            {
                                action.setEnabled(true);
                                action.setMessage(
                                        this.shortMsgEnabled.apply({count: selectionCount, total: selections.length}),
                                        this.longMsgEnabled.apply({files: selectedFiles}));
                            }
                            else
                            {
                                action.setEnabled(false);
                                action.setMessage(
                                        this.shortMsg.apply({msg: 'only one file can be selected at a time'}),
                                        this.longMsgNoMultiSelect.apply({files: selectedFiles}));
                            }
                        }
                        else
                        {
                            action.setEnabled(false);
                            action.setMessage(
                                    this.shortMsg.apply({msg: 'none of the selected files can be used'}),
                                    this.longMsgNoMatch.apply({files: files}));
                        }
                    }
                }
            }
        }

        // disable or enable all toolbar actions
        for (var a in this.toolbarActions)
        {
            var action = this.actionMap[a];
            var tbarAction = this.toolbarActions[a];

            if ('object' == typeof tbarAction)
            {
                var msg = tbarAction.getText();
                if ('object' == typeof action)
                {
                    action.getEnabled() ? tbarAction.enable() : tbarAction.disable();
                    msg = action.getLongMessage();
                }
                else
                    tbarAction.disable();

                // update the button tooltip
                if (msg) {
                    tbarAction.each(function(c) {
                        if (c.buttonSelector) {
                            c.setTooltip(msg);
                        }
                    }, this);
                }
            }
        }

        if (this.pipelineActions && this.pipelineActions.length)
            this.enableImportData(true);

        this.selectionProcessed = true;
    },

    ensureSelection : function()
    {
        if (!this.selectionProcessed)
            this.onSelectionChange();
    },

    executeImportAction : function(action)
    {
        if (action)
        {
            var selections = this.grid.selModel.getSelections(), i;
            var link = action.link;

            // if there are no selections, treat as if all are selected
            if (selections.length == 0)
            {
                selections = [];
                var store = this.grid.getStore();

                for (i=0; i <store.getCount(); i++)
                {
                    selections.push(store.getAt(i));
                }
            }

            if (link && link.href)
            {
                if (selections.length == 0)
                {
                    Ext.Msg.alert("Execute Action", "There are no files selected");
                    return false;
                }

                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", link.href);

                for (i=0; i < selections.length; i++)
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
        }
    },

    executeToolbarAction : function(item, e)
    {
        var action = this.actionMap[item.actionId];

        if (action)
            this.executeImportAction(action);
    },

    onAdmin : function(btn)
    {
        var sm = new Ext.grid.CheckboxSelectionModel();
        var cm = this.createDefaultColumnModel(sm);

        if (this.adminOptions.isCustomFileProperties())
            cm = cm.concat(this.adminOptions.createColumnModelColumns());

        // Map the universe of available actions to the list of supported tbar actions
        // for this web part. The FMT will only create buttons for actions that are
        // supported so there is no need to offer anything outside of this set in the
        // admin tool.
        var availableActions = {};
        for (var i=0; i < this.tbarItems.length; i++)
        {
            var a = this.tbarItems[i];
            availableActions[a] = this.actions[a];
        }

        var configDlg = new LABKEY.ActionsAdminPanel({
            path            : this.currentDirectory.data.path,
            containerPath   : this.containerPath,
            isPipelineRoot  : this.isPipelineRoot,
            tbarItemsConfig : this.tbarItemsConfig,
            actions         : availableActions,
            columnModel     : cm,
            gridConfig      : this.adminOptions.getGridConfig(),
            disableGeneralAdminSettings : this.disableGeneralAdminSettings
        });

        configDlg.on('success', function(c){this.updateActionConfiguration(true, true);}, this, {single:true});
        configDlg.on('failure', function(){Ext.Msg.alert("Update Action Config", "Update Failed")});

        configDlg.show();
    },

    onImportData : function(btn)
    {
        var actionMap = [],
        actions = [],
        items   = [],
        alreadyChecked = false, // Whether we've already found an enabled action to make the default selection
        hasAdmin = false, pa, links;

        // make sure we have processed the current selection
        this.ensureSelection();
        for (var ag in this.actionGroups)
        {
            var group = this.actionGroups[ag];
            pa = group.actions[0];

            var radioGroup = new Ext.form.RadioGroup({
                fieldLabel : group.label + '<br>' + pa.getShortMessage(),
                itemCls    : 'x-check-group',
                columns    : 1,
                labelSeparator: '',
                items      : []
            });

            for (var i=0; i < group.actions.length; i++)
            {
                var action = group.actions[i];

                if (action.link.href && (action.link.enabled || this.adminUser))
                {
                    var label = action.link.text;

                    // administrators always see all actions
                    if (!action.link.enabled && this.adminUser)
                    {
                        label = label.concat(' <span class="labkey-error">*</span>');
                        hasAdmin = true;
                    }

                    actionMap[action.id] = action;
                    radioGroup.items.push({
                        xtype: 'radio',
                        checked: action.enabled && !alreadyChecked,
                        disabled: !action.enabled,
                        labelSeparator: '',
                        boxLabel: label,
                        name: 'importAction',
                        inputValue: action.id
                        //width: 250
                    });
                    if (action.enabled)
                    {
                        alreadyChecked = true;
                    }
                }
            }
            if (!pa.getEnabled())
            {
                radioGroup.disabled = true;
                radioGroup.tooltip = pa.getLongMessage();

                radioGroup.on('render', function(c){this.setFormFieldTooltip(c, 'warning-icon-alt.png');}, this);
            }
            else
            {
                radioGroup.tooltip = pa.getLongMessage();
                radioGroup.on('render', function(c){this.setFormFieldTooltip(c, 'info.png');}, this);
            }
            actions.push(radioGroup);
        }

        var actionPanel = new Ext.form.FormPanel({
            bodyStyle   : 'padding:10px;',
            labelWidth  : 250,
            defaultType : 'radio',
            items       : actions
        });
        items.push(actionPanel);

        if (hasAdmin)
        {
            items.push({
                html      : 'Actions marked with an asterisk <span class="labkey-error">*</span> are only visible to Administrators.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
        }

        if (!this.importDataEnabled)
        {
            items.push({
                html      : 'This dialog has been disabled from the admin panel and is only visible to Administrators.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
        }

        var win = new Ext.Window({
            title: 'Import Data',
            width: 725,
            height: 400,
            cls: 'extContainer',
            autoScroll: true,
            closeAction:'close',
            modal: true,
            items: items,
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

    submitForm : function(panel, actionMap)
    {
        // client side validation
        var selection = panel.getForm().getValues();
        var action = actionMap[selection.importAction];

        if ('object' == typeof action)
            this.executeImportAction(action);
    },

    canImportData : function()
    {
        for (var i=0; i < this.tbarItems.length; i++)
        {
            if (this.tbarItems[i] == 'importData')
                return true;
        }
        return false;
    },

    renderFile : function(e)
    {
        var selections = this.grid.selModel.getSelections();

        if (selections.length == 1)
        {
            var item = selections[0].data;

            if (item.file)
            {
                if (this.fileSystem.baseUrl.indexOf(encodeURIComponent('@pipeline')) != -1 && this.actions.download)
                {
                    // for pipeline roots, render is not currently supported, so just download the file
                    this.actions.download.execute();
                }
                else
                {
                    // just redirect to the files servlet to render the file, the files servlet handles URLs very
                    // similar to the webdav url.
                    var resourceUrl = item.uri;
                    resourceUrl = resourceUrl.replace('_webdav', 'files');
                    resourceUrl = resourceUrl.concat('?renderAs=DEFAULT');

                    window.location = resourceUrl;
                }
            }
        }
    },

    setFormFieldTooltip : function(component, icon)
    {
        var label = Ext.get('x-form-el-' + component.id).prev('label');
        if (label) {
            var helpImage = label.createChild({
                tag: 'img',
                src: LABKEY.contextPath + '/_images/' + icon,
                style: 'margin-bottom: 0px; margin-left: 8px; padding: 0px;',
                width: 12,
                height: 12
            });
            Ext.QuickTips.register({
                target: helpImage,
                text: component.tooltip,
                title: ''
            });
        }
    },

    onEditFileProps : function(btn)
    {
        // edit the file properties of the current selection
        var selections = this.grid.selModel.getSelections();

        if (selections.length && this.grid.store.data)
        {
            var files = [];

            for (var i=0; i < selections.length; i++)
            {
                var selection = selections[i].data;
                selection.id = selection.uri;
                files.push(selection);
            }
            this.onCustomFileProperties({files : files, showErrors : true});
        }
    },

    onCustomFileProperties : function(options)
    {
        if (!this.adminOptions.isCustomFileProperties())
            return;

        // no file fields specified yet
        if (this.adminOptions.fileFields.length == 0)
        {
            if (options.showErrors)
                Ext.Msg.alert("File Properties", "There are no file properties defined yet, please contact your administrator.");
            return;
        }

        var fileDlg = new LABKEY.FilePropertiesPanel({fileFields: this.adminOptions.fileFields,
            files: options.files,
            containerPath: this.adminOptions.getFilePropContainerPath()});

        fileDlg.on('success', function(c){this.refreshDirectory();}, this, {single:true});

        fileDlg.show();
    },

    onEmailPreferences : function(btn)
    {
        var prefDlg = new LABKEY.EmailPreferencesPanel({containerPath: this.containerPath});

        prefDlg.show();
    },

    onFilePropConfigChanged : function(config)
    {
        var sm = this.grid.getSelectionModel();
        var cm = this.createDefaultColumnModel(sm);
        
        if (config.isCustomFileProperties())
        {
            cm = cm.concat(config.createColumnModelColumns());
            var c = config.createFileSystemConfig(this.fileSystem.initialConfig);
            c.baseUrl = this.fileSystem.baseUrl;
            this.fileSystem.init(c);
        }
        else
            this.fileSystem.init(this.fileSystem.initialConfig);

        this.grid.getColumnModel().setConfig(cm);
        this.grid.getView().refresh(true);
        this.refreshDirectory();
    },

    onGridConfigChanged : function(config)
    {
        if (config.getGridConfig())
        {
            this.grid.applyState(config.getGridConfig());
            this.grid.getView().refresh(true);            
        }
    }
});
