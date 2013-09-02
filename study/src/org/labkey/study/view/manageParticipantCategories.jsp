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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.study.permissions.SharedParticipantGroupPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
        resources.add(ClientDependency.fromFilePath("study/ParticipantGroup.js"));
        return resources;
    }
%>
<%
    JspView<Object> me = (JspView<Object>) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    Study s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounPlural = s.getSubjectNounPlural();
    String subjectNounColName = s.getSubjectColumnName();
    boolean isAdmin = c.hasPermission(getViewContext().getUser(), SharedParticipantGroupPermission.class) || c.hasPermission(getViewContext().getUser(), AdminPermission.class);
%>

<style type="text/css">
    div.labkey-filter-dialog {
        z-index: 20000 !important;
    }
    div.x-combo-list{
        z-index: 20020 !important;
    }
</style>

<p><%= PageFlowUtil.filter(subjectNounSingular) %> groups allow you to quickly filter data in a study to groups of <%= PageFlowUtil.filter(subjectNounPlural.toLowerCase()) %> you define.
    Use this page to define a group and add <%= PageFlowUtil.filter(subjectNounPlural.toLowerCase()) %> to it.</p>
<div id="participantCategoriesGrid"></div>

<script type="text/javascript">

(function(){

    Ext4.onReady(function() {

        Ext.override(Ext.menu.Menu, {
            zIndex : 20000
        });

        var editParticipantGroup = function(row){
            var dialog = Ext4.create('Study.window.ParticipantGroup', {
                subject: {
                    nounSingular: <%=q(subjectNounSingular)%>,
                    nounPlural: <%=q(subjectNounPlural)%>,
                    nounColumnName: <%=q(subjectNounColName)%>
                },
                isAdmin: <%=isAdmin%>,
                grid : grid,
                category: (row ? row.get("category") : null),
                groupRowId: (row ? row.get("rowId") : null),
                groupLabel: (row ? row.get("label") : null),
                categoryParticipantIds: (row ? row.get("participantIds") : null),
                categoryShared : (row ? row.get("shared") : false),
                canEdit : (row ? row.get("canEdit") :  true) // TODO: Modify this to adhere to API to check (participant) group permission
            });

            dialog.show();


        }

        var deleteParticipantGroup = function(row){
            // todo: do we need to handle deletion of a shared/public group differently?

            Ext4.Msg.show({
                title : 'Delete Group',
                msg : 'Delete Selected Group:<br/>' + (row.get("label")),
                buttons: Ext4.Msg.YESNO,
                icon: Ext4.Msg.QUESTION,
                fn: function(btn, text) {
                    if (btn == 'yes')
                    {
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
                    }},
                id: 'delete_categories'
            });
        };

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
                {name : 'canEdit',          type : 'boolean',   mapping : 'category.canEdit'},
                {name : 'canDelete',        type : 'boolean',   mapping : 'category.canDelete'},
                {name : 'categoryLabel',    type : 'string',    mapping : 'category.label'}
            ]
        });

        var createButton = Ext4.create('Ext.button.Button',
            {
                text : 'Create',
                handler: function(){
                    editParticipantGroup(null);
                },
                scope: this
            });

         var editButton = Ext4.create('Ext.button.Button',
            {
                id : 'editSelectedButtonExt4',
                text : 'Edit Selected',
                disabled : true,
              handler: function(){
                  if (grid.getSelectionModel().hasSelection()){
                      editParticipantGroup(grid.getSelectionModel().getLastSelected());
                  }
              }
            });

        var deleteButton = Ext4.create('Ext.button.Button',
            {
                id : 'deleteSelectedButtonExt4',
                text : 'Delete Selected',
                disabled : 'true',
                handler: function(){
                    if (grid.getSelectionModel().hasSelection())
                    {
                        deleteParticipantGroup(grid.getSelectionModel().getLastSelected());
                    }
                },
                scope : this
            });



        var participantStore = Ext4.create('Ext.data.Store', {
            model : 'ParticipantGroup',
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
            autoLoad: true
        });

        var categoryRenderer = function(value){
            if (value.type == "list")
                return '';
            return Ext4.htmlEncode(value.label);
        };

        var grid = Ext4.create('Ext.grid.Panel', {
            store: participantStore,
            id : 'participantCategoriesGrid',
            cls: 'participantCategoriesGrid',
            width : 855,
            columns: [
                {header:'Label',       dataIndex:'label',    width: 275, renderer: Ext4.htmlEncode},
                {header:'Category',    dataIndex:'category', width: 275, renderer: categoryRenderer},
                {header:'Shared',      dataIndex:'shared'},
                {header:'Created By',  dataIndex:'createdBy'},
                {header:'Modified By', dataIndex:'modifiedBy'}
            ],
            dockedItems : [{
                xtype: 'toolbar',
                dock: 'top',
                style: 'border-color: #b4b4b4;',
                items: [createButton, editButton, deleteButton]
            }],
            renderTo: 'participantCategoriesGrid'
        });

        grid.on('itemclick', function(g, rec){
            var row = grid.getSelectionModel().getLastSelected();
            editButton.setDisabled(false);
            if(row.get("canEdit"))   {
                editButton.setText("Edit Selected");
            }
            else editButton.setText("View Selected");
            if(row.get("canDelete")) {
                deleteButton.setDisabled(false);
            }
            else deleteButton.setDisabled(true);
        }, this);
        grid.on('itemdblclick', function(g, idx, e){
            if (g.getSelectionModel().hasSelection()) {
                editParticipantGroup(g.getSelectionModel().getLastSelected());
            }
        });

    });
})();
</script>
