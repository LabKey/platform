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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Object> me = (JspView<Object>) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    org.labkey.api.study.Study s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounPlural = s.getSubjectNounPlural();
    String subjectNounColName = s.getSubjectColumnName();
    boolean isAdmin = c.hasPermission(getViewContext().getUser(), org.labkey.api.security.permissions.AdminPermission.class);
%>

<p><%= subjectNounSingular %> groups allow you to quickly filter data in a study to groups of <%= subjectNounPlural.toLowerCase() %> you define.
    Use this page to define a group and add <%= subjectNounPlural.toLowerCase() %> to it.</p>
<div id="participantCategoriesGrid" class="extContainer"></div>

<script type="text/javascript">
    var $h = Ext.util.Format.htmlEncode;
    var _grid;

    function renderParticipantCategoriesGrid()
    {
        Ext.QuickTips.init();
        
        var store = new Ext.data.JsonStore({
            proxy: new Ext.data.HttpProxy({
                url : LABKEY.ActionURL.buildURL("participant-group", "getParticipantCategories"),
                method : 'POST'
            }),
            root: 'categories',
            idProperty: 'rowId',
            fields: [
                {name: 'rowId', type: 'integer'},
                {name: 'label', type: 'string'},
                {name: 'type', type: 'string'},
                {name: 'createdBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'modifiedBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'shared', type: 'string'},
                {name: 'participantIds', type: 'string', convert: function(v, record){return v.toString().replace(/,/g,", ");}},
                {name: 'canEdit', type: 'boolean'},
                {name: 'canDelete', type: 'boolean'}
            ],
            autoLoad: true
        });
        store.on('load', toggleEditDeleteButtons);

        var columnModel = new Ext.grid.ColumnModel({
            defaults: {
                width: 200,
                sortable: false
            },
            columns: [
                {header:'Label', dataIndex:'label', sortable: true, width: 300, renderer: $h},
                {header:'Type', dataIndex:'type', width: 100},
                {header:'Shared', dataIndex:'shared'},
                {header:'Created By', dataIndex:'createdBy'},
                {header:'Modified By', dataIndex:'modifiedBy'}
            ]
        });

        var tbarButtons = [{
            text: 'Create',
            handler: function(){
                editParticipantCategories(null);
            },
            scope: this
        },{
            id: 'editSelectedButton',
            text: 'Edit Selected',
            disabled: true,
            handler: function(){
                if (_grid.getSelectionModel().hasSelection())
                {
                    editParticipantCategories(_grid.getSelectionModel().getSelected());
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
                    deleteParticipantCategories(_grid.getSelectionModel().getSelected());
                }
            },
            scope: this
        }];

        // create a gridpanel with the list of categories (one per row)
        _grid = new Ext.grid.GridPanel({
            renderTo: 'participantCategoriesGrid',
            autoScroll:false,
            autoHeight:true,
            width:800,
            loadMask:{msg:"Loading, please wait..."},
            store: store,
            colModel: columnModel,
            selModel: new Ext.grid.RowSelectionModel({singleSelect:true}),
            viewConfig: {forceFit: true},
            tbar: tbarButtons
        });
        
        _grid.on('rowclick', toggleEditDeleteButtons);
        _grid.on('rowdblclick', function(g, idx, e){
            if (_grid.getSelectionModel().hasSelection())
            {
                editParticipantCategories(_grid.getSelectionModel().getSelected());
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

    function editParticipantCategories(row){
        var win = new Ext.Window({
            cls: 'extContainer',
            title: 'Define <%= subjectNounSingular %> Group',
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            closeAction:'close',
            items: new LABKEY.ParticipantCategoryPanel({
                categoryRowId: (row ? row.get("rowId") : null),
                categoryLabel: (row ? row.get("label") : null),
                categoryParticipantIds: (row ? row.get("participantIds") : null),
                categoryShared : (row ? row.get("shared") : false),
                canEdit : (row ? row.get("canEdit") :  true), // TODO: Modify this to adhere to API to check (participant) group permission
                listeners: {
                    scope: this,
                    'closeWindow': function(){
                        win.close();
                    }
                }
            })
        });
        win.show(this);
    }

    function deleteParticipantCategories(row){
        // todo: do we need to handle deletion of a shared/public category differently?

        Ext.Msg.show({
            title : 'Delete Category',
            msg : 'Delete Selected Category:<br/>' + $h(row.get("label")),
            buttons: Ext.Msg.YESNO,
            icon: Ext.Msg.QUESTION,
            fn: function(btn, text) {
                if (btn == 'yes')
                {
                    Ext.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("participant-group", "deleteParticipantCategory"),
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

    /**
     * Panel to take user input for the label and list of participant ids for a classfication to be created or edited
     * @param categoryRowId = the rowId of the category to be edited (null if create new)
     * @param categoryLabel = the current label of the category to be edited (null if create new)
     * @param categoryParticipantIds = the string representation of the ptid ids array of the category to be edited (null if create new)
     */
    LABKEY.ParticipantCategoryPanel = Ext.extend(Ext.Panel, {
        constructor : function(config){
            Ext.apply(config, {
                layout: 'form',
                bodyStyle:'padding:20px;',
                border: false,
                autoScroll: true
            });

            this.addEvents('closeWindow');

            LABKEY.ParticipantCategoryPanel.superclass.constructor.call(this, config);
        },

        initComponent : function() {
            this.isAdmin = <%=isAdmin%>;
            this.categoryPanel = new Ext.form.FormPanel({
                border: false,
                defaults : {disabled : !this.canEdit},
                items: [{
                    id: 'categoryLabel',
                    xtype: 'textfield',
                    value: this.categoryLabel,
                    hideLabel: true,
                    emptyText: '<%= subjectNounSingular %> Group Label',
                    allowBlank: false,
                    selectOnFocus: true,
                    preventMark: true,
                    anchor: '100%'
                },{
                    id: 'categoryIdentifiers',
                    xtype: 'textarea',
                    value: this.categoryParticipantIds,
                    hideLabel: true,
                    emptyText: 'Enter <%= subjectNounSingular %> Identifiers Separated by Commas',
                    allowBlank: false,
                    preventMark: true,
                    height: 100,
                    anchor: '100%'
                }],
                buttons: [{
                    text:'Save',
                    handler: this.saveCategory,
                    disabled : !this.canEdit,
                    scope: this
                },{
                    text:'Cancel',
                    handler: function(){this.fireEvent('closeWindow');},
                    scope: this
                }]
            });

            var demoStore = new LABKEY.ext.Store({
                schemaName: 'study',
                queryName: 'DataSets',
                filterArray: [LABKEY.Filter.create('DemographicData', true)],
                columns: 'Label',
                sort: 'Label',
                listeners: {
                    scope: this,
                    'load': function(store, records, options)
                    {
                        // on load, select the first item in the combobox
                        if (records.length > 0)
                        {
                            this.demoCombobox.setValue(records[0].get("Label"));
                            this.demoCombobox.fireEvent('select', this.demoCombobox, records[0], 0);
                        }
                    }
                },
                autoLoad: true
            });

            this.demoCombobox = new Ext.form.ComboBox({
                triggerAction: 'all',
                mode: 'local',
                store: demoStore,
                valueField: 'Label',
                displayField: 'Label',
                fieldLabel: 'Select <%= subjectNounPlural %> from',
                labelStyle: 'width: 150px;',
                labelSeparator : '',
                minListWidth : 300,
                disabled : !this.canEdit,
                listeners: {
                    scope: this,
                    'select': function(cmp, record, index){
                        this.getDemoQueryWebPart(record.get("Label"));
                    } 
                }
            });

            console.info('category shared : ' + this.categoryShared);
            this.sharedfield = new Ext.form.Checkbox({
                fieldLabel     : 'Shared',
                labelSeparator : '',
                labelStyle     : 'width: 150px;',
                disabled       : !this.isAdmin || !this.canEdit
            });

            Ext.QuickTips.register({
                target : this.sharedfield,
                text   : 'Share this <%= subjectNounSingular %> group with all users'
            });
            
            if (!this.categoryShared || this.categoryShared == "false")
                this.sharedfield.setValue(false);
            else
                this.sharedfield.setValue(true);

            this.items = [
                this.categoryPanel,
                {
                    border : false, frame: false,
                    style  : 'margin-top: -22px;',
                    width  : 450,
                    items  : [{
                        layout : 'column',
                        border : false, frame : false,
                        defeaults : { border: false, frame: false },
                        items  : [{
                            layout      : 'form',
                            columnWidth : 1,
                            border : false, frame : false,
                            items       : [this.sharedfield, this.demoCombobox]
                        }]
                    }]
                },{
                    xtype: 'panel',
                    id: 'demoQueryWebPartPanel',
                    border: false
                }
            ];

            LABKEY.ParticipantCategoryPanel.superclass.initComponent.call(this);
        },

        saveCategory: function()
        {
            // get the label and ids from the form
            var fieldValues = this.categoryPanel.getForm().getFieldValues();
            var label = fieldValues["categoryLabel"];
            var idStr = fieldValues["categoryIdentifiers"];

            // make sure the label and idStr are not null
            if (!label)
            {
                Ext.Msg.alert("ERROR", "<%= subjectNounSingular %> Category Label required.");
                return;
            }
            if (!idStr)
            {
                Ext.Msg.alert("ERROR", "One or more <%= subjectNounSingular %> Identifiers required.");
                return;
            }            

            // mask the panel
            this.getEl().mask("Saving category...", "x-mask-loading");

            // split the ptid list into an array of strings
            var ids = idStr.split(",");
            for (var i = 0; i < ids.length; i++)
            {
                ids[i] = ids[i].trim();
            }

            // setup the data to be stored for the category
            var categoryData = {
                label: label,
                type: 'list',
                participantIds: ids,
                shared : this.sharedfield.getValue()
            };
            
            if (this.categoryRowId)
            {
                categoryData.rowId = this.categoryRowId;
            }

            // save the participant category ("create" if no prior rowId, "update" if rowId exists)
            Ext.Ajax.request(
            {
                url : (this.categoryRowId
                        ? LABKEY.ActionURL.buildURL("participant-group", "updateParticipantCategory")
                        : LABKEY.ActionURL.buildURL("participant-group", "createParticipantCategory")
                      ),
                method : 'POST',
                success: function(){
                    this.getEl().unmask();
                    this.fireEvent('closeWindow');
                    _grid.getStore().reload();
                },
                failure: function(response, options){
                    this.getEl().unmask();
                    LABKEY.Utils.displayAjaxErrorResponse(response, options);
                },
                jsonData : categoryData,
                headers : {'Content-Type' : 'application/json'},
                scope: this
            });
        },

        getDemoQueryWebPart: function(queryName)
        {
            var ptidCategoryPanel = this;
            var initialLoad = true;

            var demoQueryWebPart =  new LABKEY.QueryWebPart({
                renderTo: 'demoQueryWebPartPanel',
                dataRegionName: 'demoDataRegion',
                schemaName: 'study',
                queryName: queryName,
                frame: 'none',
                border: false,
                showRecordSelectors: true,
                showUpdateColumn: false,
                maskEl: ptidCategoryPanel.getEl(),
                buttonBar: {
                    position: (this.canEdit ? 'top' : 'none'),
                    includeStandardButtons: false,
                    items:[{
                        text: 'Add Selected',
                        requiresSelection: true,
                        handler: function(){
                            ptidCategoryPanel.getSelectedDemoParticipants(queryName, "selected");
                        }
                    },{
                        text: 'Add All',
                        handler: function(){
                            // todo: fix this to only add the visible records (i.e. if filter applied to dataregion)
                            ptidCategoryPanel.getSelectedDemoParticipants(queryName, "all");
                        }
                    }]
                },
                success: function(){
                    // clear any chached selections from the last time this dataRegion was used
                    if (initialLoad)
                    {
                        Ext.ComponentMgr.get('demoDataRegion').clearSelected();
                        initialLoad = false;
                    }
                }
            });
        },

        getSelectedDemoParticipants: function(queryName, showStr) {
            var ptidCategoryPanel = this;

            // convert user filters from data region to expected filterArray
            var filters = [];
            Ext.each(Ext.ComponentMgr.get('demoDataRegion').getUserFilter(), function(usrFilter){
                var filterType = this.getFilterTypeByURLSuffix(usrFilter.op);
                filters.push(LABKEY.Filter.create(usrFilter.fieldKey,  usrFilter.value, filterType));
            }, this);

            // get the selected rows from the QWP data region
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: queryName,
                selectionKey: Ext.ComponentMgr.get('demoDataRegion').selectionKey,
                showRows: showStr,
                filterArray: filters,
                columns: '<%= subjectNounColName %>',
                success: function(data){
                    // get the current list of participant ids from the form
                    var classPanelValues = ptidCategoryPanel.categoryPanel.getForm().getFieldValues();
                    var tempIds = classPanelValues["categoryIdentifiers"];

                    // also store the ids as an array to check if the ids to add already exist 
                    var tempIdsArray = tempIds.split(",");
                    for (var i = 0; i < tempIdsArray.length; i++)
                    {
                        tempIdsArray[i] = tempIdsArray[i].trim();
                    }

                    // append the selected ids to the current list
                    if (data.rows.length > 0)
                    {
                       for (var i = 0; i < data.rows.length; i++)
                       {
                           if (tempIdsArray.indexOf(data.rows[i]["<%= subjectNounColName %>"]) == -1)
                           {
                               tempIds += (tempIds.length > 0 ? ", " : "") + data.rows[i]["<%= subjectNounColName %>"];
                               tempIdsArray.push(data.rows[i]["<%= subjectNounColName %>"]);
                           }
                       }
                    }

                    // put the new list of ids into the form
                    ptidCategoryPanel.categoryPanel.getForm().setValues({
                       categoryIdentifiers: tempIds
                    });
                }
            });
        },

        getFilterTypeByURLSuffix: function(op)
        {
            // loop through the labkey filter types to match the URL suffix
            for (var type in LABKEY.Filter.Types)
            {
                if (LABKEY.Filter.Types[type].getURLSuffix() == op)
                {
                    return LABKEY.Filter.Types[type];
                }
            }
            return null;
        }
    });

    Ext.onReady(renderParticipantCategoriesGrid);
</script>