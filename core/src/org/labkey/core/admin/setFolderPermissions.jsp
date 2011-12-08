<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.SetFolderPermissionsForm> me = (JspView<AdminController.SetFolderPermissionsForm>) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    ViewContext ctx = me.getViewContext();
    User user = ctx.getUser();
%>
<script type="text/javascript">
    //LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresExt4ClientAPI();
    LABKEY.requiresCss('/createFolder.css');
</script>
<script type="text/javascript">
    Ext4.onReady(function(){
        var isProject = <%=h(c.isProject())%>;
        var containerNoun = isProject ? 'Project' : 'Folder';

        Ext4.FocusManager.enable(false);
        Ext4.QuickTips.init();

        var panel = Ext4.create('Ext.form.Panel', {
            border: false,
            autoHeight: true,
            defaults: {
                border: false
            },
            url: 'setFolderPermissions.view',
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
                            if(e.getKey() == e.ENTER){
                                var f = field.up('form').getForm();
                                if(!f.submitInProgress)
                                    f.submit();
                                f.submitInProgress = true;
                            }
                        }
                    }
                },
                listeners: {
                    change: {
                        fn: function(btn, newVal, oldVal){
                            var form = btn.up('form');
                            var panel = form.down('#usersArea');
                            panel.removeAll();

                            if(newVal.permissionType)
                                form['render'+newVal.permissionType](panel);

                            form.doLayout();
                        },
                        buffer: 20,
                        scope: this
                    }
                },
                items: [{
                    //note: folders cant inherit permissions from a parent
                    boxLabel: 'Inherit From Parent ' + containerNoun,
                    checked: !isProject,
                    hidden: isProject,
                    name: 'permissionType',
                    inputValue: 'Inherit',
                    autoEl: {
                        'data-qtip': 'If selected, all permissions will be inherited from the parent '+containerNoun+'.  Any changes made to the parent will automatically be reflected in the child.'
                    }
                },{
                    boxLabel: 'My User Only',
                    checked: isProject,
                    name: 'permissionType',
                    inputValue: 'CurrentUser',
                    autoEl: {
                        'data-qtip': 'If selected, only the current user and site admins will have permissions in this folder.'
                    }
                },{
                    xtype: 'form',
                    layout: 'hbox',
                    width: '100%',
                    itemId: 'hbox',
                    hidden: !isProject,
                    border: false,
                    defaults: {
                        border: false
                    },
                    items: [{
                        xtype: 'radio',
                        boxLabel: 'Copy From Existing Project',
                        checked: false,
                        name: 'permissionType',
                        inputValue: 'CopyExistingProject',
                        autoEl: {
                            'data-qtip': 'If selected, the permissions for all users and groups from the selected project will be applied to this project.  If this project has project groups, copies of all groups will be made in the new project.'
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
                    autoEl: {
                        'data-qtip': 'If selected, on the final page of the wizard there will be a link allowing you to set permissions using the default security policy editor.  This option is the most powerful, yet most complex to use.'
                    }
                }]
            }],
            buttons: [{
                xtype: 'button',
                cls: 'labkey-button',
                text: (isProject ? 'Next' : 'Finish'),
                handler: function(btn){
                    var f = btn.up('form').getForm();
                    if(!f.submitInProgress)
                        f.submit();
                    f.submitInProgress = true;
                }
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
            renderCopyExistingProject: function(target){
                target.add({
                    xtype: 'combo',
                    id: 'targetProject',
                    name: 'targetProject',
                    style: 'padding-left: 20px;padding-top:3px;',
                    //fieldLabel: 'Choose Project',
                    labelAlign: 'top',
                    editable: false,
                    displayField: 'Name',
                    valueField: 'EntityId',
                    store: Ext4.create('LABKEY.ext4.Store', {
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
                    })
                });
            }
        }).render('folderPermissionsDiv');

        Ext4.create('Ext.util.KeyNav', Ext4.getBody(), {
            scope: this,
            enter: function(){
                var f = panel.getForm();
                if(!f.submitInProgress)
                    f.submit();
                f.submitInProgress = true;
            }
        });
    });


</script>


<%=formatMissedErrors("form")%>

<div id="folderPermissionsDiv"></div>


