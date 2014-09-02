<%
/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("createFolder.css"));
        resources.add(ClientDependency.fromFilePath("clientapi/ext4"));
        return resources;
    }
%>
<%
    JspView<AdminController.SetFolderPermissionsForm> me = (JspView<AdminController.SetFolderPermissionsForm>) HttpView.currentView();
    Container c = getContainer();
%>
<%=formatMissedErrors("form")%>
<div id="folderPermissionsDiv"></div>
<script type="text/javascript">
    Ext4.onReady(function() {
        var isProject = <%=h(c.isProject())%>;
        var hasNext = isProject || <%= h(!c.getFolderType().getExtraSetupSteps(c).isEmpty()) %>;
        var containerNoun = isProject ? 'Project' : 'Folder';

        Ext4.FocusManager.enable(false);
        Ext4.tip.QuickTipManager.init();

        var checkSubmit = function(src, getFromSrc) {
            var f = getFromSrc ? src : src.up('form');
            if (f) {
                f = f.getForm();
                if (f) {
                    if(!f.submitInProgress)
                        f.submit();
                    f.submitInProgress = true;
                }
            }
        };

        var panel = Ext4.create('Ext.form.Panel', {
            renderTo: 'folderPermissionsDiv',
            border: false,
            autoHeight: true,
            defaults: {
                border: false
            },
            url: LABKEY.ActionURL.buildURL('admin','setFolderPermissions.view'),
            method: 'POST',
            standardSubmit: true,
            items: [{
                xtype: 'radiogroup',
                width: '100%',
                id: 'permissionType',
                name: 'permissionType',
                columns: 1,
                defaults: {
                    width: 400,
                    listeners: {
                        specialkey: function(field, e){
                            if (e.getKey() == e.ENTER) { checkSubmit(field); }
                        }
                    }
                },
                items: [
                    { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
                    {
                    //note: folders cant inherit permissions from a parent
                    boxLabel: 'Inherit From Parent ' + containerNoun,
                    checked: !isProject,
                    hidden: isProject,
                    name: 'permissionType',
                    inputValue: 'Inherit',
                    listeners: {
                        scope: this,
                        single: true,
                        afterrender: function(radio){
                            radio.boxLabelEl.set({
                                'data-qtip': 'If selected, all permissions will be inherited from the parent '+containerNoun+'.  Any changes made to the parent will automatically be reflected in the child.'
                            })
                        }
                    }
                },{
                    boxLabel: 'My User Only',
                    checked: isProject,
                    name: 'permissionType',
                    inputValue: 'CurrentUser',
                    listeners: {
                        scope: this,
                        single: true,
                        afterrender: function(radio){
                            radio.boxLabelEl.set({
                                'data-qtip': 'If selected, only the current user and site admins will have permissions in this folder.'
                            })
                        }
                    }
                },{
                    xtype: 'fieldcontainer',
                    layout: 'hbox',
                    itemId: 'hbox',
                    hidden: !isProject,
                    border: false,
                    style: 'padding-bottom: 5px;',
                    defaults: {
                        border: false
                    },
                    items: [{
                        xtype: 'radio',
                        boxLabel: 'Copy From Existing Project',
                        width: 200,
                        checked: false,
                        name: 'permissionType',
                        inputValue: 'CopyExistingProject',
                        listeners: {
                            scope: this,
                            afterrender: function(radio){
                                radio.boxLabelEl.set({
                                    'data-qtip': 'If selected, the permissions for all users and groups from the selected project will be applied to this project.  If this project has project groups, copies of all groups will be made in the new project.'
                                })
                            },
                            change: function(field, checked){
                                var form = field.up('form');
                                var formPanel = form.down('#usersArea');
                                if (checked)
                                {
                                    if(field.inputValue)
                                        form['render'+field.inputValue](formPanel);
                                }
                                else
                                    formPanel.removeAll();
                            }
                        }
                    },{
                        xtype: 'panel',
                        width: 400,
                        border: false,
                        autoHeight: true,
                        defaults: {
                            border: false
                        },
                        itemId: 'usersArea'
                    }]
                },{
                    xtype: 'radio',
                    boxLabel: 'Configure Later',
                    checked: false,
                    name: 'permissionType',
                    inputValue: 'Advanced',
                    listeners: {
                        scope: this,
                        single: true,
                        afterrender: function(radio){
                            radio.boxLabelEl.set({
                                'data-qtip': 'If selected, on the final page of the wizard there will be a link allowing you to set permissions using the default security policy editor.  This option is the most powerful, yet most complex to use.'
                            })
                        }
                    }
                }]
            }],
            buttons: [{
                xtype: 'button',
                cls: 'labkey-button',
                text: (hasNext ? 'Next' : 'Finish'),
                handler: function(b) { checkSubmit(b); }
            }],
            renderAdvanced: function(target){
                //nothing needed
            },
            renderCurrentUser: function(target){
                //nothing needed
            },
            renderInherit: function(target){
                //nothing needed
            },
            renderCopyExistingProject: function(target) {

                var store = Ext4.create('LABKEY.ext4.data.Store', {
                    containerPath: '/home',
                    schemaName: 'core',
                    queryName: 'containers',
                    columns: 'entityId,name',
                    sort: 'name',
                    autoLoad: true,
                    filterArray: [
                        LABKEY.Filter.create('containerType', 'project', LABKEY.Filter.Types.EQUAL)
                    ],
                    containerFilter: 'CurrentAndSiblings'
                });

                target.add({
                    xtype: 'combo',
                    id: 'targetProject',
                    name: 'targetProject',
                    style: 'padding-left: 20px;padding-top:3px;',
                    labelAlign: 'top',
                    editable: false,
                    displayField: 'Name',
                    valueField: 'EntityId',
                    store: store
                });
            }
        });

        var nav = new Ext4.util.KeyNav({
            target: Ext4.getBody(),
            enter: function() { checkSubmit(panel, true); }
        });
    });


</script>


