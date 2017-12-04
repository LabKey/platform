<%
/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4ClientApi"); // needed for labkey-combo
        dependencies.add("/extWidgets/IconPanel.js");
        dependencies.add("extWidgets/IconPanel.css");
    }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getRowId();
    JSONObject jsonProps = new JSONObject(me.getModelBean().getPropertyMap());
    String renderTarget = "project-" + me.getModelBean().getIndex();
    boolean isRootAdmin = getUser().hasRootAdminPermission();
    boolean hasPermission;

    Container target;
    String containerPath = (String)jsonProps.get("containerPath");
    if(containerPath == null || "".equals(containerPath))
    {
        hasPermission = true; //this means current container
        target = getContainer();
    }
    else
    {
        target = ContainerManager.getForPath(containerPath);
        if (target == null)
        {
            // Could also be an entityId
            target = ContainerManager.getForId(containerPath);
        }
        hasPermission = target != null && target.hasPermission(getUser(), ReadPermission.class);

        //normalize entityId vs path.
        if (target != null)
        {
            jsonProps.put("containerPath", target.getPath());
        }
    }
%>
<div id="<%=text(renderTarget)%>"></div>
<script type="text/javascript">

Ext4.onReady(function() {

    if (<%=target == null%>) {
        Ext4.get('<%=text(renderTarget)%>').update('The target project/folder has been deleted. To reset, remove the webpart and re-add it');
        return;
    }

    if (!<%=hasPermission%>) {
        Ext4.get('<%=text(renderTarget)%>').update('You do not have permission to view this folder');
        return;
    }

    //assume server-supplied webpart config
    var config = <%= PageFlowUtil.jsString(jsonProps.toString()) %>;
    config = Ext4.decode(config);
    config.hideCreateButton = config.hideCreateButton === 'true';
    if (config.containerTypes) {
        config.noun = config.containerTypes.match(/project/) ? 'Project' : 'Subfolder';
    }

    Ext4.applyIf(config, {
        containerTypes: 'project',
        containerFilter: 'CurrentAndSiblings',
        containerPath: LABKEY.Security.getHomeContainer(),
        hideCreateButton: false,
        iconSize: 'large',
        labelPosition: 'bottom',
        noun: 'Project'
    });

    function getFilterArray(panel) {
        var filterArray = [];
        if (panel.containerTypes)
            filterArray.push(LABKEY.Filter.create('containerType', panel.containerTypes, LABKEY.Filter.Types.EQUALS_ONE_OF));

        //exclude system-generated containers
        if (LABKEY.Security.getHomeContainer())
            filterArray.push(LABKEY.Filter.create('name', LABKEY.Security.getHomeContainer(), LABKEY.Filter.Types.NOT_EQUAL));
        if (LABKEY.Security.getSharedContainer())
            filterArray.push(LABKEY.Filter.create('name', LABKEY.Security.getSharedContainer(), LABKEY.Filter.Types.NOT_EQUAL));

        if (panel.containerFilter == 'CurrentAndFirstChildren') {
            //NOTE: path is not directly filterable, so we settle for Client-side filtering
            panel.store.on('load', function() {
                var path = this.containerPath;
                this.filterBy(function(rec) {
                    return rec.get('Path') != path;
                })
            }, null, {single: true});
        }

        return filterArray;
    }

    var store = Ext4.create('LABKEY.ext4.Store', {
        containerPath: config.containerPath,
        schemaName: 'core',
        queryName: 'Containers',
        sort: 'SortOrder,DisplayName',
        containerFilter: config.containerFilter,
        columns: 'Name,DisplayName,EntityId,Path,ContainerType,iconurl',
        autoLoad: false,
        metadata: {
            url: {
                createIfDoesNotExist: true,
                setValueOnLoad: true,
                getInitialValue: function(val, rec) {
                    return LABKEY.ActionURL.buildURL('project', 'start', rec.get('Path'))
                }
            }
        }
    });

    var panelCfg = {
        id: 'projects-panel-<%=webPartId%>',
        iconField: 'iconurl',
        iconCls: 'fa-folder-open',
        labelField: 'DisplayName',
        urlField: 'url',
        region : 'center',
        overflowY : 'auto',
        sizeContainer : true,
        iconSize: config.iconSize,
        labelPosition: config.labelPosition,
        hideCreateButton: config.hideCreateButton,
        noun: config.noun,
        showMenu: false,
        width: '100%',
        border: false,
        frame: false,
        header : false,
        buttonAlign: 'left',
        emptyText: (LABKEY.Security.currentUser.isGuest ? 'Please log in to view the ' + config.noun.toLowerCase() + ' list.' : 'No ' + config.noun.toLowerCase() + 's to display.'),
        deferEmptyText: false,
        store: store
    };

    //NOTE: separated to differentiate site/app admins from those w/ admin permission in this container
    if (<%=isRootAdmin%>) {
        panelCfg.buttons = [{
            text: 'Create New ' + config.noun,
            hidden: !LABKEY.Security.currentUser.isAdmin || config.hideCreateButton,
            handler: function() {
                var isProject = panel.containerTypes && panel.containerTypes.match(/project/),
                    params = { returnUrl: <%= q(getActionURL().toString()) %> };

                window.location = LABKEY.ActionURL.buildURL('admin', 'createFolder', (isProject ? '/' : config.containerPath), params);
            }
        }]
    }

    var panel = Ext4.create('LABKEY.ext.IconPanel', panelCfg);
    Ext4.apply(panel, config);
    panel.getFilterArray = getFilterArray;

    panel.store.filterArray = getFilterArray(panel);
    panel.store.load();

    var container = Ext4.create('Ext.container.Container', {
        renderTo : <%=PageFlowUtil.jsString(renderTarget)%>,
        layout   : 'border',
        height   : 400,
        border   : false, frame : false,
        style    : 'background-color: transparent;',
        cls      : 'labkey-projects-container',
        items    : [panel]
    });
});

    /**
     * Called by Server to handle customization actions.
     */
    function customizeProjectWebpart(webpartId, pageId, index) {

        Ext4.onReady(function(){
            var panel = Ext4.getCmp('projects-panel-' + webpartId);

            if (panel) {
                function shouldCheck(btn){
                    var data = panel.down('#dataView').renderData;
                    return (btn.iconSize==data.iconSize && btn.labelPosition==data.labelPosition)
                }
                Ext4.create('Ext.window.Window', {
                    title: 'Customize Webpart',
                    modal: true,
                    width: 400,
                    border: false,
                    layout: 'fit',
                    items: [{
                        xtype: 'form',
                        border: false,
                        bodyStyle: 'padding: 5px;',
                        items: [{
                            xtype: 'textfield',
                            name: 'title',
                            fieldLabel: 'Title',
                            itemId: 'title',
                            value: panel.title || 'Projects'
                        },{
                            xtype: 'radiogroup',
                            name: 'style',
                            itemId: 'style',
                            fieldLabel: 'Icon Style',
                            border: false,
                            columns: 1,
                            defaults: {
                                xtype: 'radio',
                                width: 300
                            },
                            items: [{
                                boxLabel: 'Details',
                                inputValue: {iconSize: 'small',labelPosition: 'side'},
                                checked: shouldCheck({iconSize: 'small',labelPosition: 'side'}),
                                name: 'style'
                            },{
                                boxLabel: 'Medium',
                                inputValue: {iconSize: 'medium',labelPosition: 'bottom'},
                                checked: shouldCheck({iconSize: 'medium',labelPosition: 'bottom'}),
                                name: 'style'
                            },{
                                boxLabel: 'Large',
                                inputValue: {iconSize: 'large',labelPosition: 'bottom'},
                                checked: shouldCheck({iconSize: 'large',labelPosition: 'bottom'}),
                                name: 'style'
                            }]
                        },{
                            xtype: 'radiogroup',
                            name: 'folderTypes',
                            itemId: 'folderTypes',
                            fieldLabel: 'Folders To Display',
                            border: false,
                            columns: 1,
                            defaults: {
                                xtype: 'radio',
                                width: 300
                            },
                            items: [{
                                boxLabel: 'All Projects',
                                inputValue: 'project',
                                checked: panel.containerTypes && panel.containerTypes.match(/project/),
                                name: 'folderTypes'
                            },{
                                boxLabel: 'Specific Folder',
                                inputValue: 'folder',
                                checked: panel.containerTypes && !panel.containerTypes.match(/project/),
                                name: 'folderTypes'
                            },{
                                xtype: 'labkey-combo',
                                itemId: 'containerPath',
                                width: 200,
                                disabled: panel.containerTypes && panel.containerTypes.match(/project/),
                                displayField: 'Path',
                                valueField: 'EntityId',
                                initialValue: panel.store.containerPath,
                                value: panel.store.containerPath,
                                store: Ext4.create('LABKEY.ext4.Store', {
                                    schemaName: 'core',
                                    queryName: 'Containers',
                                    containerFilter: 'AllFolders',
                                    columns: 'Name,Path,EntityId',
                                    autoLoad: true,
                                    //sort: '-Path',
                                    filterArray: [
                                        LABKEY.Filter.create('type', 'workbook', LABKEY.Filter.Types.NOT_EQUAL),
                                        LABKEY.Filter.create('name', LABKEY.Security.getHomeContainer(), LABKEY.Filter.Types.NOT_EQUAL),
                                        LABKEY.Filter.create('name', LABKEY.Security.getSharedContainer(), LABKEY.Filter.Types.NOT_EQUAL)
                                    ],
                                    listeners: {
                                        load: function(store){
                                            //NOTE: the raw value of the path column is name, so we sort locally
                                            store.sort('Path', 'ASC');
                                            store.fireEvent('datachanged');
                                        }
                                    }
                                })
                            },{
                                xtype: 'checkbox',
                                boxLabel: 'Include Direct Children Only',
                                disabled: panel.containerTypes && panel.containerTypes.match(/project/),
                                checked: (panel.store.containerFilter == 'CurrentAndFirstChildren'),
                                itemId: 'directDescendants'
                            },{
                                xtype: 'checkbox',
                                boxLabel: 'Include Workbooks',
                                disabled: panel.containerTypes && panel.containerTypes.match(/project/),
                                checked: (panel.containerTypes.match(/project/) || panel.containerTypes.match(/workbook/)),
                                itemId: 'includeWorkbooks'
                            },{
                                xtype: 'checkbox',
                                boxLabel: 'Hide Create Button',
                                checked: panel.hideCreateButton,
                                itemId: 'hideCreateButton'
                            }],
                            listeners: {
                                buffer: 20,
                                change: function(field, val){
                                    var window = field.up('form');
                                    window.down('#containerPath').setDisabled(val.folderTypes != 'folder');
                                    window.down('#includeWorkbooks').setDisabled(val.folderTypes != 'folder');
                                    window.down('#directDescendants').setDisabled(val.folderTypes != 'folder');

                                    window.doLayout();
                                    field.up('window').doLayout();

                                }
                            }
                        }]
                    }],
                    buttons: [{
                        text: 'Submit',
                        handler: function(btn) {
                            var mode = btn.up('window').down('#folderTypes').getValue().folderTypes;

                            if(mode == 'project'){
                                panel.store.containerFilter = 'CurrentAndSiblings';
                                panel.containerTypes = 'project';
                                panel.store.containerPath = LABKEY.Security.getHomeContainer();
                                panel.store.filterArray = panel.getFilterArray(panel);
                                panel.noun = 'Project';
                            }
                            else {
                                var container = btn.up('window').down('#containerPath').getValue();
                                if(!container){
                                    alert('Must choose a folder');
                                    return;
                                }
                                panel.store.containerPath = container;

                                panel.store.containerFilter = 'Current'; //null;  //use default
                                panel.containerTypes = ['folder'];
                                if(btn.up('window').down('#includeWorkbooks').getValue())
                                    panel.containerTypes.push('workbook');
                                panel.containerTypes = panel.containerTypes.join(';');
                                panel.noun = 'Subfolder';

                                var directDescendants = btn.up('window').down('#directDescendants').getValue();
                                panel.store.containerFilter = directDescendants ? 'CurrentAndFirstChildren' : 'CurrentAndSubfolders';
                                panel.store.filterArray = panel.getFilterArray(panel);
                            }

                            panel.store.load();

                            var hideCreateButton = btn.up('window').down('#hideCreateButton').getValue();
                            panel.hideCreateButton = hideCreateButton;

                            if (panel.getDockedItems().length > 0) {
                                var createBtn = panel.getDockedItems()[0].down('button');
                                if (createBtn) {
                                    createBtn.setVisible(!hideCreateButton);
                                    createBtn.setText('Create New ' + panel.noun);
                                }
                            }

                            var styleField = btn.up('window').down('#style').getValue().style;
                            panel.resizeIcons.call(panel, styleField);
                            btn.up('window').hide();

                            var title = btn.up('window').down('#title').getValue();
                            panel.title = title;
                            LABKEY.Utils.setWebpartTitle(title, webpartId);

                            var values = {
                                containerPath: panel.store.containerPath,
                                title: title,
                                containerTypes: panel.containerTypes,
                                containerFilter: panel.store.containerFilter,
                                webPartId: webpartId,
                                hideCreateButton: panel.hideCreateButton,
                                iconSize: panel.iconSize,
                                labelPosition: panel.labelPosition
                            };

                            Ext4.Ajax.request({
                                url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, values),
                                method : 'POST',
                                failure : LABKEY.Utils.onError,
                                scope : this
                            });
                        },
                        scope: this
                    },{
                        text: 'Cancel',
                        handler: function(btn){
                            btn.up('window').hide();
                        },
                        scope: this
                    }]
                }).show();
            }
        });
    }

</script>
