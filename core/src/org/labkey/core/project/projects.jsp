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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getRowId();
    String renderTarget = "project-" + me.getModelBean().getIndex();
    String x = "";
%>
<div>
    <div id='<%=renderTarget%>'></div>
</div>
<script type="text/javascript">

    LABKEY.requiresExt4ClientAPI(true);
    LABKEY.requiresCss("extWidgets/ext4.css");
    LABKEY.requiresScript("extWidgets/IconPanel.js");

</script>
<script type="text/javascript">

Ext4.onReady(function(){
    Ext4.create('LABKEY.ext.IconPanel', {
        id: 'projects-panel-<%=webPartId%>',
        iconField: 'iconurl',
        labelField: 'Name',
        urlField: 'url',
        iconSize: 'large',
        showMenu: false,
        width: '100%',
        border: false,
        frame: false,
        buttonAlign: 'left',
        buttons: [{
            text: 'Create New Project',
            target: '_self',
            hidden: !LABKEY.Security.currentUser.isAdmin,
            href: LABKEY.ActionURL.buildURL('admin', 'createFolder', '/')
        }],
        store: Ext4.create('LABKEY.ext4.Store', {
            containerPath: 'home',
            schemaName: 'core',
            queryName: 'Containers',
            title: 'Projects',
            sort: 'Name',
            containerFilter: 'CurrentAndSiblings',
            columns: 'name',
            autoLoad: true,
            filterArray: [
                LABKEY.Filter.create('containerType', 'project', LABKEY.Filter.Types.EQUALS)
            ],
            metadata: {
                iconurl: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    setInitialValue: function(val, rec){
                        return LABKEY.ActionURL.buildURL('project', 'downloadProjectIcon', rec.get('Name'))
                    }
                },
                url: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    setInitialValue: function(val, rec){
                        return LABKEY.ActionURL.buildURL('project', 'begin', rec.get('Name'))
                    }
                }
            }
        })
    }).render('<%=renderTarget%>');
});

        /**
     * Called by Server to handle cusomization actions.
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
                    title: 'Choose Icon Style',
                    width: 400,
                    items: [{
                        xtype: 'form',
                        bodyStyle: 'padding: 5px;',
                        items: [{
                            xtype: 'radiogroup',
                            name: 'style',
                            border: false,
                            columns: 1,
                            defaults: {
                                xtype: 'radio',
                                width: 300
                            },
                            itemId: 'style',
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
                        }]
                    }],
                    buttons: [{
                        text: 'Submit',
                        handler: function(btn){
                            var radio = btn.up('window').down('#style').getValue().style;
                            panel.resizeIcons.call(panel, radio);
                            btn.up('window').hide();
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