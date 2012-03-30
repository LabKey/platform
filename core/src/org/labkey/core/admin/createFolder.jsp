<%
/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    final ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();

    String name = form.getName();
    String folderTypeName = form.getFolderType() != null ? form.getFolderType() : "Collaboration"; //default to Collaboration

    JSONArray modulesOut = new JSONArray();
    String[] activeModules = form.getActiveModules();
    if(activeModules != null){
        for(String m : form.getActiveModules()){
            modulesOut.put(m);
        }
    }

%>
<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresCss('createFolder.css');
</script>
<script type="text/javascript">
    Ext4.QuickTips.init();

    Ext4.onReady(function(){

        var request = new LABKEY.MultiRequest();
        var folderTypes;
        var moduleTypes;
        <%="var selectedModules = " + modulesOut + ";"%>
        <%="var hasLoaded = " + form.getHasLoaded() + ";"%>
        <%="var defaultTab = '" + form.getDefaultModule() + "';"%>

        request.add(LABKEY.Security.getFolderTypes, {
            success: function(data){
                var keys = Ext4.Object.getKeys(data);
                folderTypes = [];
                Ext4.each(keys, function(k){
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
            }
        });
        request.add(LABKEY.Security.getModules, {
            success: function(data){
                moduleTypes = data;
            }
        });

        request.send(onSuccess);

        function onSuccess(){

            var panel = Ext4.create('Ext.form.Panel', {
                border: false,
                autoHeight: true,
                width: 'auto',
                style: 'padding-top: 20px;',
                defaults: {
                    border: false
                },
                url: LABKEY.ActionURL.buildURL('admin','createFolder.view'),
                method: 'POST',
                standardSubmit: true,
                listeners: {
                    render: function(panel){
                        var target = panel.down('#folderType');
                        if(target.getValue() && target.getValue().folderType == 'None')
                            panel.renderModules();
                    }
                },
                items: [{
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
                    fieldCls: 'labkey-wizard-input',
                    style: 'padding-left: 5px;',
                    value: '<%=h(name)%>',
                    allowBlank: false,
                    maxLength: 255,
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
                    tag: 'span',
                    style: 'padding-bottom: 20px;'
                },{
                    html: 'Type:',
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
                        change: {fn: function(btn, val){this.onTypeChange(btn, val)}, buffer: 20}
                    },
                    onTypeChange: function(btn, val){
                        var parent = this.up('form');
                        parent.down('#modules').removeAll();
                        parent.doLayout();
                        if(val.folderType=='None'){
                            parent.renderModules();
                        }
                        parent.doLayout();
                    },
                    //NOTE: this could be shifted to use labkey-checkboxgroup with an arraystore
                    items: function(){
                        var items = [];
                        var ft;
                        Ext4.each(folderTypes, function(ft){
                            if(!ft.workbookType){
                                items.push({
                                    xtype: 'radio',
                                    name: 'folderType',
                                    width: 500,
                                    inputValue: ft.name,
                                    value: ft.name,
                                    boxLabel: ft.label,
                                    labelWidth: 500,
                                    checked: (ft.name == '<%=folderTypeName%>' ? true : false),
                                    autoEl: {
                                        'data-qtip': ft.description
                                    }
                                });
                            }
                        }, this);

                        return items;
                    }()
                },{
                    xtype: 'form',
                    itemId: 'modules',
                    autoHeight: true,
                    border: false,
                    defaults: {
                        border: false
                    },
                    style: 'padding-left: 15px;'
                }],
                buttons: [{
                    xtype: 'button',
                    cls: 'labkey-button',
                    text: 'Next',
                    handler: function(btn){
                        var f = btn.up('form').getForm();
                        if (f.isValid()){
                            if(!f.submitInProgress)
                                f.submit();
                            f.submitInProgress = true;
                        }
                    }
                },{
                    xtype: 'button',
                    cls: 'labkey-button',
                    text: 'Cancel',
                    handler: function(btn){
                        window.location = LABKEY.ActionURL.buildURL('project', 'begin', 'home');
                    }
                }],
                renderModules: function(){
                    var target = this.down('#modules');
                    target.add([
                        {
                            html: 'Default Tab:',
                            cls: 'labkey-wizard-header'
                        },{
                            xtype: 'combo',
                            name: 'defaultModule',
                            itemId: 'modulesCombo',
                            displayField: 'module',
                            valueField: 'module',
                            value: (hasLoaded ? defaultTab : 'Portal'),
                            queryMode: 'local',
                            store: {
                                fields: ['module'],
                                idIndex: 0,
                                data: (function(hasLoaded, selectedModules){
                                    var items = [];
                                    if(!hasLoaded)
                                        return [{module: 'Portal'}];
                                    else {
                                        if(selectedModules && selectedModules.length){
                                            items = Ext4.Array.map(selectedModules, function(e){
                                                return {module: e};
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
                            autoHeight: true,
                            name: 'activeModules',
                            listeners: {
                                change: {
                                    fn: function(btn, val){
                                        var combo = target.down('#modulesCombo');
                                        var oldVal = combo.getValue();

                                        var store = combo.store;
                                        store.removeAll();

                                        if (!val.activeModules){
                                            combo.reset();
                                            return;
                                        }

                                        var records;
                                        if(Ext4.isArray(val.activeModules)){
                                            records = Ext4.Array.map(val.activeModules, function(item){
                                                return {module: item};
                                            }, this);
                                        }
                                        else {
                                            records = [{module: val.activeModules}];
                                        }
                                        store.add(records);

                                        if(records.length==1)
                                            combo.setValue(records[0].get('module'));
                                        else if (oldVal)
                                            combo.setValue(oldVal);

                                    },
                                    scope: this,
                                    buffer: 20
                                }
                            },
                            items: function(){
                                var items = [];
                                if(moduleTypes){
                                    Ext4.each(moduleTypes.modules, function(m){
                                        //the effect of this is that by default, a new container inherits modules from the parent
                                        //if creating a project, there is nothing to inherit, so we set to Portal below
                                        if(m.active || m.enabled)
                                            items.push({
                                                xtype: 'checkbox',
                                                name: 'activeModules',
                                                inputValue:m.name,
                                                boxLabel: m.tabName,
                                                checked: (hasLoaded ? (selectedModules && selectedModules.indexOf(m.name)!=-1) : m.active)
                                                //disabled: !m.enabled
                                            });
                                    }, this);
                                }

                                return items;

                            }()
                        }
                    ]);

                    //default to portal if none selected
                    if(!hasLoaded && (!this.getForm().getValues().activeModules || !this.getForm().getValues().activeModules.length)){
                        this.getForm().findField('activeModules').setValue({activeModules: ['Portal']});
                    }
                }
            }).render('createFormDiv');

            Ext4.create('Ext.util.KeyNav', Ext4.getBody(), {
                scope: this,
                enter: function(){
                    var f = panel.getForm();
                    if (f.isValid()){
                        if(!f.submitInProgress)
                            f.submit();
                        f.submitInProgress = true;
                    }
                }
            });
        }
    });
</script>

<%=formatMissedErrors("form")%>
<div id="createFormDiv"></div>

