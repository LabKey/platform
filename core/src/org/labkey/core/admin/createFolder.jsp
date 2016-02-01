<%
/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.json.JSONArray"%>
<%@ page import="org.labkey.api.admin.FolderWriter" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.writer.Writer" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.writer.FolderSerializationRegistryImpl" %>
<%@ page import="org.labkey.core.portal.ProjectController" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.LinkedList" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext4"));
        resources.add(ClientDependency.fromPath("createFolder.css"));
        return resources;
    }
%>
<%
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    boolean userHasEnableRestrictedModulesPermission = getContainer().hasEnableRestrictedModules(getUser());
    boolean isContainerRoot = getContainer().isRoot();

    String name = form.getName();
    String title = form.getTitle();
    String folderTypeName = form.getFolderType() != null ? form.getFolderType() : "Collaboration"; //default to Collaboration

    JSONArray modulesOut = new JSONArray();
    String[] activeModules = form.getActiveModules();
    if(activeModules != null){
        for(String m : form.getActiveModules()){
            modulesOut.put(m);
        }
    }

    JSONArray templateWriterTypes = new JSONArray();
    if (form.getTemplateWriterTypes() != null)
    {
        for (String type : form.getTemplateWriterTypes())
        {
            templateWriterTypes.put(type);
        }
    }

%>
<%=formatMissedErrors("form")%>
<div id="createFormDiv"></div>
<script type="text/javascript">
    Ext4.QuickTips.init();

    Ext4.onReady(function(){

        var request = new LABKEY.MultiRequest();
        var folderTypes;
        var moduleTypes;
        var moduleTypesMap = {};
        var templateFolders = [];
        var selectedModules = <%=modulesOut%>;
        var hasLoaded = <%=text(form.getHasLoaded()?"true":"false")%>;
        var defaultTab = <%=q(form.getDefaultModule())%>;
        var selectedTemplateFolder = <%=q(form.getTemplateSourceId())%>;
        var selectedTemplateWriters = <%=templateWriterTypes%>;
        var userHasEnableRestrictedModulesPermission = <%=userHasEnableRestrictedModulesPermission%>;
        var isParentRoot = <%=isContainerRoot%>;

        request.add(LABKEY.Security.getFolderTypes, {
            success: function(data){
                var keys = Ext4.Object.getKeys(data);
                folderTypes = [];
                Ext4.each(keys, function(k){
                    if ((userHasEnableRestrictedModulesPermission || !data[k].hasRestrictedModule) && (isParentRoot || !data[k].isProjectOnlyType))
                        folderTypes.push(data[k]);
                });
                folderTypes = folderTypes.sort(function(a,b){
                    //force custom to appear at end
                    return b.label == 'Custom' ? -1
                        : a.label == 'Custom' ? 1
                        : (a.label > b.label) ? 1
                        : (a.label < b.label) ? -1
                        : 0
                });
                
                // add option for create from template
                folderTypes.push({
                    name: 'Template',
                    label: 'Create From Template Folder',
                    workbookType: false,
                    activeModules: [],
                    defaultModule: null,
                    description: 'Create a new folder based on a template folder that you have created somewhere on the current server.',
                    preferredWebParts: [],
                    requiredWebParts: []
                });
            }
        });
        request.add(LABKEY.Security.getModules, {
            success: function(data) {
                moduleTypes = data;
            }
        });

        var onSuccess = function() {

            var doSubmit = function() {
                var f = panel.getForm();
                if (f.isValid()) {
                    if (!f.submitInProgress) {
                        f.submit();
                    }
                    f.submitInProgress = true;
                }
            };

            var scrollToBottom = function() {
                Ext4.defer(function() {
                    if (Ext4.isChrome) {
                        Ext4.getBody().scroll('b', 10000, true);
                    }
                    else {
                        // sadly, cannot get other browsers to animate properly
                        window.scrollTo(0, document.body.scrollHeight);
                    }
                }, 200);
            };

            var panel = Ext4.create('Ext.form.Panel', {
                renderTo: 'createFormDiv',
                border: false,
                style: 'padding-top: 20px;',
                defaults: {
                    border: false
                },

                /* Ext.form.Basic configurations */
                url: LABKEY.ActionURL.buildURL('admin', 'createFolder.view'),
                method: 'POST',
                standardSubmit: true,

                listeners: {
                    render: function(panel) {
                        var value = panel.down('#folderType').getValue();
                        if (value) {
                            if (value.folderType === 'None') {
                                panel.renderModules();
                            }
                            else if (value.folderType === 'Template') {
                                panel.renderTemplateInfo();
                            }
                        }
                    },
                    boxready: {
                        fn: function(panel) {
                            panel.doLayout();

                            var el = panel.getEl().parent();
                            Ext4.EventManager.onWindowResize(function() {
                                panel.setWidth(el.getBox().width);
                            }, panel, {delay: 200});
                        },
                        single: true
                    }
                },
                items: [{
                    xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                },{
                    html: 'Name:',
                    cls: 'labkey-wizard-header',
                    style: 'padding-bottom: 5px;'
                },{
                    xtype: 'hidden',
                    name: 'hasLoaded',
                    value: true
                },{
                    xtype: 'textfield',
                    name: 'name',
                    width: 400,
                    style: 'padding-left: 5px;',
                    value: '<%=h(name)%>',
                    allowBlank: false,
                    maxLength: 255,
                    validateOnBlur: false,
                    regex: /^[^@\/\\;:?<>*|"^][^\/\\;:?<>*|"^]*$/,
                    regexText: "Folder must be a legal filename and not start with '@' or contain one of '/', '\\', ';', ':', '?', '<', '>', '*', '|', '\"', or '^'",
                    listeners: {
                        render: function(field){
                            field.focus('', 10);
                        },
                        specialkey: function(field, e){
                            if(e.getKey() == e.ENTER){
                                var f = field.up('form').getForm();
                                if(!f.submitInProgress)
                                    f.submit();
                                f.submitInProgress = true;
                            }
                        }
                    }
                },{
                    xtype: 'checkbox',
                    boxLabel: 'Use name as title',
                    checked: <%=title == null%>,
                    listeners: {
                        change: function(cb){
                            cb.up('panel').down('[name=titleHeader]').setVisible(!cb.checked);
                            var tf = cb.up('panel').down('textfield[name="title"]');
                            if (cb.checked){
                                tf.setValue(null);
                            }
                            else {
                                tf.setValue(cb.up('panel').down('textfield[name="name"]').getValue());
                            }

                            tf.setVisible(!cb.checked);
                        }
                    }
                },{
                    html: 'Title:',
                    name: 'titleHeader',
                    cls: 'labkey-wizard-header',
                    style: 'padding-bottom: 5px;',
                    hidden: <%=title == null%>
                },{
                    xtype: 'textfield',
                    name: 'title',
                    hidden: <%=title == null%>,
                    width: 400,
                    style: 'padding-left: 5px;',
                    value: '<%=h(title)%>',
                    maxLength: 255,
                    validateOnBlur: false
                },{
                    tag: 'span',
                    style: 'padding-bottom: 20px;'
                },{
                    html: 'Folder Type:',
                    cls: 'labkey-wizard-header'
                },{
                    xtype: 'radiogroup',
                    columns: 1,
                    name: 'folderType',
                    itemId: 'folderType',
                    layoutConfig: {
                        minWidth: 400
                    },
                    listeners: {
                        change: {
                            fn: function(btn, val) {
                                var parent = this.up('form');
                                parent.down('#additionalTypeInfo').removeAll();
                                parent.doLayout();
                                if (val.folderType == 'None') {
                                    parent.renderModules();
                                }
                                else if (val.folderType == 'Template') {
                                    parent.renderTemplateInfo();
                                }
                                parent.doLayout();
                            },
                            buffer: 20
                        }
                    },
                    //NOTE: this could be shifted to use labkey-checkboxgroup with an arraystore
                    items: function() {
                        var items = [];
                        Ext4.each(folderTypes, function(ft) {
                            if (!ft.workbookType) {
                                items.push({
                                    xtype: 'radio',
                                    name: 'folderType',
                                    width: 500,
                                    inputValue: ft.name,
                                    value: ft.name,
                                    boxLabel: ft.label,
                                    labelWidth: 500,
                                    checked: ft.name == <%=PageFlowUtil.jsString(folderTypeName)%>,
                                    listeners: {
                                        scope: this,
                                        single: true,
                                        afterrender: function(radio){
                                            radio.boxLabelEl.set({
                                                'data-qtip': ft.description
                                            })
                                        }
                                    }
                                });
                            }
                        }, this);

                        return items;
                    }()
                },{
                    xtype: 'form',
                    itemId: 'additionalTypeInfo',
                    autoHeight: true,
                    border: false,
                    defaults: {
                        border: false
                    },
                    style: 'padding-left: 15px;'
                }],
                buttons: [{
                    text: 'Next',
                    cls: 'labkey-button',
                    handler: doSubmit
                },{
                    text: 'Cancel',
                    cls: 'labkey-button',
                    handler: function(btn) {
                        window.location = <%= PageFlowUtil.jsString(form.getReturnURLHelper(new ActionURL(ProjectController.StartAction.class, ContainerManager.getHomeContainer())).toString()) %>;
                    }
                }],
                renderModules: function() {
                    var target = this.down('#additionalTypeInfo');
                    target.add([
                        {
                            html: 'Default Tab:',
                            cls: 'labkey-wizard-header'
                        },{
                            xtype: 'combo',
                            name: 'defaultModule',
                            itemId: 'modulesCombo',
                            displayField: 'tabName',
                            valueField: 'module',
                            value: (hasLoaded ? defaultTab : 'Portal'),
                            queryMode: 'local',
                            store: {
                                fields: ['module', 'tabName'],
                                idIndex: 0,
                                data: (function(hasLoaded, selectedModules){
                                    var items = [];
                                    if(!hasLoaded)
                                        return [{module: 'Core', tabName: 'Portal'}];
                                    else {
                                        if(selectedModules && selectedModules.length){
                                            items = Ext4.Array.map(selectedModules, function(e){
                                                return {module: e, tabName: moduleTypesMap[e]};
                                            }, this);
                                        }
                                    }
                                    return items;
                                })(hasLoaded, selectedModules)
                            }
                        },{
                            html: 'Choose Modules:',
                            cls: 'labkey-wizard-header'
                        },{
                            xtype: 'checkboxgroup',
                            columns: 3,
                            vertical: true,
                            autoHeight: true,
                            name: 'activeModules',
                            listeners: {
                                change: {
                                    fn: function(btn, val){
                                        var combo = target.down('#modulesCombo');
                                        var oldVal = combo.getValue();

                                        var store = combo.store;
                                        store.removeAll();

                                        if (!val.activeModules) {
                                            combo.reset();
                                            return;
                                        }

                                        var records;
                                        if (Ext4.isArray(val.activeModules)) {
                                            records = Ext4.Array.map(val.activeModules, function(item) {
                                                return {module: item, tabName: moduleTypesMap[item]};
                                            }, this);
                                        }
                                        else {
                                            records = [{module: val.activeModules, tabName: moduleTypesMap[val.activeModules]}];
                                        }
                                        store.add(records);

                                        if (records.length == 1)
                                            combo.setValue(records[0].module);
                                        else if (oldVal)
                                            combo.setValue(oldVal);

                                    },
                                    scope: this,
                                    buffer: 20
                                }
                            },
                            items: function(){
                                var items = [];
                                if (moduleTypes) {
                                    Ext4.each(moduleTypes.modules, function(m) {
                                        // keep a map from the module name to the display/tab name
                                        moduleTypesMap[m.name] = m.tabName;

                                        //the effect of this is that by default, a new container inherits modules from the parent
                                        //if creating a project, there is nothing to inherit, so we set to Portal below
                                        if ((m.active || m.enabled) && (userHasEnableRestrictedModulesPermission || !m.requireSitePermission)) {
                                            items.push({
                                                xtype: 'checkbox',
                                                name: 'activeModules',
                                                inputValue: m.name,
                                                boxLabel: m.tabName,
                                                checked: (hasLoaded ? (selectedModules && selectedModules.indexOf(m.name) != -1) : m.active)
                                            });
                                        }
                                    }, this);
                                }

                                return items;

                            }()
                        }
                    ]);

                    //default to portal (i.e., "Core" module) if none selected
                    var form = this.getForm(),
                        values = form.getValues();

                    if (!hasLoaded && (!values.activeModules || !values.activeModules.length)) {
                        form.findField('activeModules').setValue({activeModules: ['Core']});
                    }

                    scrollToBottom();
                },
                renderTemplateInfo : function() {
                    var target = this.down('#additionalTypeInfo');
                    target.add([
                        {
                            html: 'Choose Template Folder:',
                            cls: 'labkey-wizard-header'
                        },{
                            xtype: 'combo',
                            name: 'templateSourceId',
                            itemId: 'sourceFolderCombo',
                            allowBlank: false,
                            displayField: 'path',
                            valueField: 'id',
                            value: hasLoaded ? selectedTemplateFolder : null,
                            editable: false,
                            validateOnBlur: false,
                            width: 400, 
                            store: Ext4.create('Ext.data.ArrayStore', {
                                fields: ['id', 'path'],
                                data: templateFolders
                            })
                        },{
                            html: 'Folder objects to copy:',
                            cls: 'labkey-wizard-header'
                        },{
                            xtype: 'panel',
                            border: false,
                            items: function(){
                                Ext4.each(folderTemplateWriters, function(writer){
                                    if (hasLoaded)
                                        writer.checked = selectedTemplateWriters && selectedTemplateWriters.indexOf(writer.itemId) > -1;
                                }, this);

                                return folderTemplateWriters;
                            }()
                        },{
                            html: 'Options:',
                            cls: 'labkey-wizard-header'
                        },{
                            xtype: 'checkbox',
                            hideLabel: true,
                            boxLabel: 'Include Subfolders',
                            name: 'templateIncludeSubfolders',
                            itemId: 'templateIncludeSubfolders'
                        }
                    ]);

                    if (templateFolders.length == 0) {
                        // mask the combo while loading the container list
                        var combo = this.down('#sourceFolderCombo');
                        combo.setLoading(true);

                        LABKEY.Security.getContainers({
                            containerPath: '/',
                            includeSubfolders: true,
                            success: initTemplateFolders(combo)
                        });
                    }

                    scrollToBottom();
                }
            });

            Ext4.create('Ext.util.KeyNav', Ext4.getBody(), {
                enter: doSubmit,
                scope: this
            });
        };

        var initTemplateFolders = function(combo) {
            return function(data)
            {
                getTemplateFolders(data);
                combo.setLoading(false);
            }
        };

        var getTemplateFolders = function(data) {
            // add the container itself to the templateFolder object if it is not the root and the user has admin perm to it
            // and if it is not a workbook or container tab folder
            if (data.path != "/" && LABKEY.Security.hasPermission(data.userPermissions, LABKEY.Security.permissions.admin)
                    && !data.isWorkbook && !data.isContainerTab)
            {
                templateFolders.push([data.id, data.path]);
            }

            // add the container's children to the templateFolder object
            if (data.children.length > 0)
            {
                for (var i = 0; i < data.children.length; i++)
                    getTemplateFolders(data.children[i]);
            }
        };

        request.send(onSuccess);

        var folderTemplateWriters = [];
        <%
            // TODO: see exportFolder.jsp, this should be converted to use core/getRegisteredFolderWriters API
            Collection<FolderWriter> writers = new LinkedList<>(FolderSerializationRegistryImpl.get().getRegisteredFolderWriters());
            for (FolderWriter writer : writers)
            {
                String parent = writer.getDataType();
                if (null != parent && writer.supportsVirtualFile())
                {
                    %>folderTemplateWriters.push({xtype: "checkbox", hideLabel: true, boxLabel: "<%=parent%>", name: "templateWriterTypes", itemId: "<%=parent%>", inputValue: "<%=parent%>", checked: true, objectType: "parent"});<%

                    Collection<Writer> children = writer.getChildren(true);
                    if (null != children && children.size() > 0)
                    {
                        for (Writer child : children)
                        {
                            if (null != child.getDataType())
                            {
                                String text = child.getDataType();
                                %>
                                folderTemplateWriters.push({xtype: "checkbox", style: {marginLeft: "20px"}, hideLabel: true, boxLabel: "<%=text%>", name: "templateWriterTypes", itemId: "<%=text%>",
                                    inputValue: "<%=text%>", checked: true, objectType: "child", parentId: "<%=parent%>"});
                                <%
                            }
                        }
                    }
                }
            }
        %>
    });
</script>
