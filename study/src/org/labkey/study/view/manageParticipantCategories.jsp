<%
    /*
    * Copyright (c) 2011-2012 LabKey Corporation
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
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext3"));
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

<p><%= PageFlowUtil.filter(subjectNounSingular) %> groups allow you to quickly filter data in a study to groups of <%= PageFlowUtil.filter(subjectNounPlural.toLowerCase()) %> you define.
    Use this page to define a group and add <%= PageFlowUtil.filter(subjectNounPlural.toLowerCase()) %> to it.</p>
<div id="participantCategoriesGrid" class="extContainer"></div>

<script type="text/javascript">
(function(){

    var $h = Ext.util.Format.htmlEncode;
    var _grid;

    function renderParticipantCategoriesGrid()
    {
        Ext.QuickTips.init();

        Ext.data.Types.PARTICIPANTCATEGORY = {
            convert: function(v, rec) {
                return v;
            },
            sortType: function(v) {
                return v.type == "list" ? "" : v.label;
            },
            type: 'ParticpantCategory'
        };

        this.groupStore = new Ext.data.JsonStore({
            proxy: new Ext.data.HttpProxy({
                url : LABKEY.ActionURL.buildURL("participant-group", "browseParticipantGroups"),
                method : 'POST'
            }),
            baseParams: { type : 'participantGroup', includeParticipantIds: true},
            root: 'groups',
            fields: [
                {name: 'rowId', type: 'integer', mapping: 'id'},
                {name: 'label', type: 'string'},
                {name: 'type', type: 'string'},
                {name: 'createdBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'modifiedBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'shared', type: 'boolean', mapping: 'category.shared'},
                {name: 'participantIds', type: 'string', convert: function(v, record){return v.join(', ');}},
                {name: 'canEdit', type: 'boolean', mapping: 'category.canEdit'},
                {name: 'canDelete', type: 'boolean', mapping: 'category.canDelete'},
                {name: 'categoryLabel', type: 'string', mapping: 'category.label'},
                {name: 'category', type: Ext.data.Types.PARTICIPANTCATEGORY}
            ],
            listeners: {
                'load': function(store){
                    store.sort([{field: 'category', direction: 'ASC'}, {field:'label', direction: 'ASC'}]);
                }
            },
            autoLoad: true
        });
        this.groupStore.on('load', toggleEditDeleteButtons);

        var categoryRenderer = function(value){
            if(value.type == "list"){
                return '';
            } else {
                return value.label;
            }
        };

        var columnModel = new Ext.grid.ColumnModel({
            defaults: {
                width: 200,
                sortable: true
            },
            columns: [
                {header:'Label', dataIndex:'label', width: 300, renderer: $h},
                {header:'Category', dataIndex:'category', width: 300, renderer: categoryRenderer},
                {header:'Shared', dataIndex:'shared'},
                {header:'Created By', dataIndex:'createdBy'},
                {header:'Modified By', dataIndex:'modifiedBy'}
            ]
        });

        var tbarButtons = [{
            text: 'Create',
            handler: function(){
                editParticipantGroup(null);
            },
            scope: this
        },{
            id: 'editSelectedButton',
            text: 'Edit Selected',
            disabled: true,
            handler: function(){
                if (_grid.getSelectionModel().hasSelection())
                {
                    editParticipantGroup(_grid.getSelectionModel().getSelected());
                }
            },
            scope: this
        },{
            id: 'deleteSelectedButton',
            text: 'Delete Selected',
            disabled: true,
            handler: function(){
                if (_grid.getSelectionModel().hasSelection())
                {
                    deleteParticipantGroup(_grid.getSelectionModel().getSelected());
                }
            },
            scope: this
        }];

        // create a gridpanel with the list of categories (one per row)
        _grid = new Ext.grid.GridPanel({
            renderTo: 'participantCategoriesGrid',
            cls:'participantCategoriesGrid',
            autoScroll:false,
            autoHeight:true,
            width:800,
            loadMask:{msg:"Loading, please wait..."},
            store: this.groupStore,
            colModel: columnModel,
            selModel: new Ext.grid.RowSelectionModel({singleSelect:true}),
            viewConfig: {forceFit: true},
            tbar: tbarButtons
        });

        _grid.on('rowclick', toggleEditDeleteButtons);
        _grid.on('rowdblclick', function(g, idx, e){
            if (_grid.getSelectionModel().hasSelection())
            {
                editParticipantGroup(_grid.getSelectionModel().getSelected());
            }
        });
    }

    // enable/disable the edit and delete buttons based on selection
    function toggleEditDeleteButtons(){
        // exit if the grid has not yet been created
        if (!_grid)
            return;

        var topTB = _grid.getTopToolbar();

        if (_grid.getSelectionModel().getCount() == 1)
        {
            var row = _grid.getSelectionModel().getSelected();

            // enable the view/edit button and set the text based on the user's perms for the given selection
            topTB.findById('editSelectedButton').enable();
            if (row.get("canEdit"))
                topTB.findById('editSelectedButton').setText("Edit Selected");
            else
                topTB.findById('editSelectedButton').setText("View Selected");

            // enable/disable the delete button based on the user's perms for the given selection
            if (row.get("canDelete"))
                topTB.findById('deleteSelectedButton').enable();
            else
                topTB.findById('deleteSelectedButton').disable();
        }
        else
        {
            topTB.findById('editSelectedButton').disable();
            topTB.findById('deleteSelectedButton').disable();
        }
    }

    function editParticipantGroup(row){
        var dialog = new LABKEY.study.ParticipantGroupDialog({
            subject: {
                nounSingular: <%=q(subjectNounSingular)%>,
                nounPlural: <%=q(subjectNounPlural)%>,
                nounColumnName: <%=q(subjectNounColName)%>
            },
            isAdmin: <%=isAdmin%>,
            category: (row ? row.get("category") : null),
            groupRowId: (row ? row.get("rowId") : null),
            groupLabel: (row ? row.get("label") : null),
            categoryParticipantIds: (row ? row.get("participantIds") : null),
            categoryShared : (row ? row.get("shared") : false),
            canEdit : (row ? row.get("canEdit") :  true) // TODO: Modify this to adhere to API to check (participant) group permission
        });
        dialog.show(this);

    }

    function deleteParticipantGroup(row){
        // todo: do we need to handle deletion of a shared/public group differently?

        Ext.Msg.show({
            title : 'Delete Group',
            msg : 'Delete Selected Group:<br/>' + $h(row.get("label")),
            buttons: Ext.Msg.YESNO,
            icon: Ext.Msg.QUESTION,
            fn: function(btn, text) {
                if (btn == 'yes')
                {
                    Ext.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("participant-group", "deleteParticipantGroup"),
                        method: "POST",
                        success: function(){
                            _grid.getStore().reload();
                        },
                        failure: function(response, options){
                            LABKEY.Utils.displayAjaxErrorResponse(response, options);
                        },
                        jsonData: {rowId: row.get("rowId")},
                        headers : {'Content-Type' : 'application/json'}
                    });
                }},
            id: 'delete_categories'
        });
    }

    Ext.onReady(renderParticipantCategoriesGrid);

})();
</script>
