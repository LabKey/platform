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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.study.permissions.SharedParticipantGroupPermission" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3"); // nested query webpart
        dependencies.add("clientapi/ext4");
        dependencies.add("study/ParticipantGroup.js");
    }
%>
<%
    Container c = getContainer();
    Study s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounPlural = s.getSubjectNounPlural();
    String subjectNounColName = s.getSubjectColumnName();
    boolean isAdmin = c.hasPermission(getUser(), SharedParticipantGroupPermission.class) || c.hasPermission(getUser(), AdminPermission.class);
%>

<style type="text/css">
    div.labkey-filter-dialog {
        z-index: 20000 !important;
    }
    div.x-combo-list{
        z-index: 20020 !important;
    }
</style>

<p><%= h(subjectNounSingular) %> groups allow you to quickly filter data in a study to groups of <%= h(subjectNounPlural.toLowerCase()) %> you define.
    Use this page to define a group and add <%= h(subjectNounPlural.toLowerCase()) %> to it.</p>
<div id="participantCategoriesGrid"></div>

<script type="text/javascript">

    Ext4.onReady(function() {
        Ext4.QuickTips.init();

        Ext4.override(Ext4.menu.Menu, { zIndex : 20000 });

        if (!Ext4.ModelManager.isRegistered('ParticipantGroup')) {
            Ext4.define('ParticipantGroup', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'rowId',            type : 'int',       mapping : 'id'},
                    {name : 'label',            type : 'string'},
                    {name : 'type',             type : 'string'},
                    {name : 'category',         type : 'string',    convert : function(v) { return v; }, sortType: function(v) { return v.type == 'list' ? '' : v.label; }},
                    {name : 'shared',           type : 'boolean',   mapping : 'category.shared'},
                    {name : 'createdBy',        type : 'string',    convert : function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                    {name : 'modifiedBy',       type : 'string',    convert : function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                    {name : 'participantIds',   type : 'string',    convert : function(v, record){return v.join(', ');}},
                    {name : 'filters',          type : 'string',    mapping : 'category.filters'},
                    {name : 'canEdit',          type : 'boolean',   mapping : 'category.canEdit'},
                    {name : 'canDelete',        type : 'boolean',   mapping : 'category.canDelete'},
                    {name : 'categoryLabel',    type : 'string',    mapping : 'category.label'},
                    {name : 'categoryOwner',    type : 'string',    mapping : 'category.createdBy', convert : function(v, record){return (v.displayValue ? v.displayValue : v.value)}}
                ]
            });
        }

        var editParticipantGroup = function(row){
            Ext4.create('Study.window.ParticipantGroup', {
                subject: {
                    nounSingular: <%=q(subjectNounSingular)%>,
                    nounPlural: <%=q(subjectNounPlural)%>,
                    nounColumnName: <%=q(subjectNounColName)%>
                },
                isAdmin: <%=isAdmin%>,
                grid : grid,
                autoShow: true,
                category: (row ? row.get("category") : null),
                groupRowId: (row ? row.get("rowId") : null),
                groupLabel: (row ? row.get("label") : null),
                categoryParticipantIds: (row ? row.get("participantIds") : null),
                categoryShared : (row ? row.get("shared") : false),
                canEdit : (row ? row.get("canEdit") :  true), // TODO: Modify this to adhere to API to check (participant) group permission
                resizable: true
            });
        };

        var deleteParticipantGroup = function(row){
            // todo: do we need to handle deletion of a shared/public group differently?

            Ext4.Msg.show({
                id: 'delete_categories',
                title : 'Delete Group',
                msg : 'Delete Selected Group:<br/>' + (row.get("label")),
                buttons: Ext4.Msg.YESNO,
                icon: Ext4.Msg.QUESTION,
                fn: function(btn, text) {
                    if (btn == 'yes') {
                        Ext4.Ajax.request({
                            url: LABKEY.ActionURL.buildURL("participant-group", "deleteParticipantGroup"),
                            method: "POST",
                            success: function(){
                                grid.getStore().load();
                            },
                            failure: function(response, options){
                                LABKEY.Utils.displayAjaxErrorResponse(response, options);
                            },
                            jsonData: {rowId: row.get("rowId")}
                        });
                    }
                }
            });
        };

        var categoryRenderer = function(value){
            if (value.type == "list")
                return '';
            return Ext4.htmlEncode(value.label);
        };

        var grid = Ext4.create('Ext.grid.Panel', {
            renderTo: 'participantCategoriesGrid',
            cls: 'ptid-group-grid',
            width : 975,
            store: {
                xtype: 'store',
                model: 'ParticipantGroup',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("participant-group", "browseParticipantGroups", null, {distinctCategories : false}),
                    extraParams : {
                        type : 'participantGroup',
                        includeParticipantIds: true,
                        includeUnassigned : false
                    },
                    reader: {
                        type: 'json',
                        root: 'groups'
                    }
                },
                sorters: [
                    { property: 'category', direction: 'ASC' },
                    { property: 'label', direction: 'ASC' }
                ],
                autoLoad: true,
                listeners: {
                    load: function (store, records) {
                        // clear selection on load of the grid store
                        grid.getSelectionModel().deselectAll();

                        // TODO enable this code once we decide what we want to do for "Sent Subject Groups" for the study module
                        // show the Send button if any of the groups have saved filters
                        //Ext4.each(records, function(record)
                        //{
                        //    if (record.get('filters') && record.get('filters').length > 0)
                        //    {
                        //        Ext4.getCmp('sendSelectedButtonExt4').show();
                        //        return false; // break;
                        //    }
                        //});
                    }
                }
            },
            columns: [
                {header:'Label',       dataIndex:'label',    width: 270, renderer: Ext4.htmlEncode},
                {header:'Category',    dataIndex:'category', width: 270, renderer: categoryRenderer},
                {header:'Shared',      dataIndex:'shared'},
                {header:'Created By',  dataIndex:'createdBy'},
                {header:'Modified By', dataIndex:'modifiedBy'},
                {header:'Category Owner', dataIndex:'categoryOwner', flex : 1}
            ],
            dockedItems : [{
                xtype: 'toolbar',
                dock: 'top',
                style: 'border-color: #b4b4b4;',
                items: [{
                    text: 'Create',
                    handler: function() { editParticipantGroup(null); },
                    scope: this
                },{
                    id: 'editSelectedButtonExt4',
                    text: 'Edit Selected',
                    disabled: true,
                    handler: function() {
                        if (grid.getSelectionModel().hasSelection()) {
                            editParticipantGroup(grid.getSelectionModel().getLastSelected());
                        }
                    }
                },{
                    id: 'deleteSelectedButtonExt4',
                    text: 'Delete Selected',
                    disabled: true,
                    handler: function() {
                        if (grid.getSelectionModel().hasSelection()) {
                            deleteParticipantGroup(grid.getSelectionModel().getLastSelected());
                        }
                    },
                    scope : this
                },{
                    id: 'sendSelectedButtonExt4',
                    text: 'Send Selected',
                    tooltip: 'Send a copy of the filters for this participant group as an email notification.',
                    hidden: true,
                    disabled: true,
                    handler: function() {
                        if (grid.getSelectionModel().hasSelection()) {
                            window.location = LABKEY.ActionURL.buildURL('study', 'sendParticipantGroup', null, {
                                rowId: grid.getSelectionModel().getLastSelected().get('rowId')
                            })
                        }
                    },
                    scope : this
                }]
            }],
            listeners: {
                itemclick : function(grid, row) {
                    var editButton = Ext4.getCmp('editSelectedButtonExt4');
                    var deleteButton = Ext4.getCmp('deleteSelectedButtonExt4');
                    var sendButton = Ext4.getCmp('sendSelectedButtonExt4');

                    editButton.setDisabled(row.get('filters') != undefined && row.get('filters').length > 0);
                    editButton.setText(row.get('canEdit') ? 'Edit Selected' : 'View Selected');

                    deleteButton.setDisabled(row.get("canDelete") ? false : true);

                    // currently only allowing sending of groups with saved filters
                    if (sendButton.isVisible())
                        sendButton.setDisabled(row.get('filters') == undefined || row.get('filters').length == 0);
                },
                itemdblclick : function(g) {
                    if (g.getSelectionModel().hasSelection()) {
                        var filters = g.getSelectionModel().getSelection()[0].get('filters');
                        if (filters == undefined || filters.length == 0)
                            editParticipantGroup(g.getSelectionModel().getSelection()[0]);
                    }
                },
                scope: this
            }
        });
    });

</script>
