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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Object> me = (JspView<Object>) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    org.labkey.api.study.Study s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounPlural = s.getSubjectNounPlural();
    String subjectNounColName = s.getSubjectColumnName();
%>

<div id="participantClassificationsGrid" class="extContainer"></div>

<script type="text/javascript">
    Ext.QuickTips.init();

    var $h = Ext.util.Format.htmlEncode;
    var _grid;

    function renderParticipantClassificationsGrid()
    {
        var store = new Ext.data.JsonStore({
            proxy: new Ext.data.HttpProxy({
                url : LABKEY.ActionURL.buildURL("participant-list", "getParticipantClassifications"),
                method : 'POST'
            }),
            root: 'classifications',
            idProperty: 'rowId',
            fields: [
                {name: 'rowId', type: 'integer'},
                {name: 'label', type: 'string'},
                {name: 'type', type: 'string'},
                {name: 'createdBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'shared', type: 'string'},
                {name: 'participantIds', type: 'string', convert: function(v, record){return v.toString()}}
            ],
            autoLoad: true
        });

        var columnModel = new Ext.grid.ColumnModel({
            defaults: {
                width: 200,
                sortable: false
            },
            columns: [
                {header:'Label', dataIndex:'label', sortable: true, width: 300},
                {header:'Type', dataIndex:'type', width: 100},
                {header:'Created By', dataIndex:'createdBy'},
                {header:'Shared', dataIndex:'shared'}
            ]
        });

        var tbarButtons = [{
            text: 'Create',
            handler: function(){
                editParticipantClassifications(null);
            },
            scope: this
        },{
            id: 'editSelectedButton',
            text: 'Edit Selected',
            disabled: true,
            handler: function(){
                if (_grid.getSelectionModel().hasSelection())
                {
                    editParticipantClassifications(_grid.getSelectionModel().getSelected());
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
                    deleteParticipantClassifications(_grid.getSelectionModel().getSelected());
                }
            },
            scope: this
        }];

        // create a gridpanel with the list of classifications (one per row)
        _grid = new Ext.grid.GridPanel({
            renderTo: 'participantClassificationsGrid',
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

        // rowclick listener to enable/disable the edit and delete buttons based on selection
        _grid.on('rowclick', function(g, rowIdx, e){
            var topTB = g.getTopToolbar();

            if (g.getSelectionModel().getCount() == 1)
            {
                topTB.findById('editSelectedButton').enable();
                topTB.findById('deleteSelectedButton').enable();
            }
            else
            {
                topTB.findById('editSelectedButton').disable();
                topTB.findById('deleteSelectedButton').disable();
            }
        }, this);
    }

    function editParticipantClassifications(row){
        var win = new Ext.Window({
            cls: 'extContainer',
            title: 'Create <%= subjectNounSingular %> Classification',
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            closeAction:'close',
            items: new LABKEY.ParticipantClassificationPanel({
                classificationRowId: (row ? row.get("rowId") : null),
                classificationLabel: (row ? row.get("label") : null),
                classificationParticipantIds: (row ? row.get("participantIds") : null),
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

    function deleteParticipantClassifications(row){
        // todo: do we need to handle deletion of a shared/public classification differently?

        Ext.Msg.show({
            title : 'Delete Classification',
            msg : 'Delete Selected Classification:<br/>' + row.get("label"),
            buttons: Ext.Msg.YESNO,
            icon: Ext.Msg.QUESTION,
            fn: function(btn, text) {
                if (btn == 'yes')
                {
                    Ext.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("participant-list", "deleteParticipantClassification"),
                        method: "POST",
                        success: function(){
                            _grid.getStore().reload();
                        },
                        failure: function(){
                            Ext.Msg.alert("Delete Classification", "Deletion Failed");
                        },
                        jsonData: {rowId: row.get("rowId")},
                        headers : {'Content-Type' : 'application/json'}
                    });
                }},
            id: 'delete_classifications'
        });
    }

    /**
     * Panel to take user input for the label and list of participant ids for a classfication to be created or edited
     * @param classificationRowId = the rowId of the classification to be edited (null if create new)
     * @param classificationLabel = the current label of the classification to be edited (null if create new)
     * @param classificationParticipantIds = the string representation of the ptid ids array of the classification to be edited (null if create new) 
     */
    LABKEY.ParticipantClassificationPanel = Ext.extend(Ext.Panel, {
        constructor : function(config){
            Ext.apply(config, {
                layout: 'form',
                bodyStyle:'padding:20px;',
                border: false,
                autoScroll: true
            });

            this.addEvents('closeWindow');

            LABKEY.ParticipantClassificationPanel.superclass.constructor.call(this, config);
        },

        initComponent : function() {
            this.classificationPanel = new Ext.form.FormPanel({
                monitorValid: true,
                border: false,
                items: [{
                    id: 'classificationLabel',
                    xtype: 'textfield',
                    value: this.classificationLabel,
                    hideLabel: true,
                    emptyText: '<%= subjectNounSingular %> Set Label',
                    allowBlank: false,
                    selectOnFocus: true,
                    preventMark: true,
                    anchor: '100%'
                },{
                    id: 'classificationIdentifiers',
                    xtype: 'textarea',
                    value: this.classificationParticipantIds,
                    hideLabel: true,
                    emptyText: 'Enter <%= subjectNounSingular %> Identifiers Separated by Commas',
                    allowBlank: false,
                    preventMark: true,
                    height: 100,
                    anchor: '100%'
                }],
                buttons: [{
                    text:'Save',
                    formBind: true,
                    handler: this.saveClassification,
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
                labelStyle: 'width: 175px;',
                minListWidth : 300,
                listeners: {
                    scope: this,
                    'select': function(cmp, record, index){
                        this.getDemoQueryWebPart(record.get("Label"));
                    } 
                }
            });

            this.items = [
                this.classificationPanel,
                this.demoCombobox,
                {
                    xtype: 'panel',
                    id: 'demoQueryWebPartPanel',
                    border: false
                }
            ];

            LABKEY.ParticipantClassificationPanel.superclass.initComponent.call(this);
        },

        saveClassification: function()
        {
            // mask the panel
            this.getEl().mask("Saving classification...", "x-mask-loading");

            // get the label and ids from the form
            var fieldValues = this.classificationPanel.getForm().getFieldValues();
            var label = $h(fieldValues["classificationLabel"]);
            var idStr = $h(fieldValues["classificationIdentifiers"]);

            // split the ptid list into an array of strings
            var ids = idStr.split(",");
            for (var i = 0; i < ids.length; i++)
            {
                ids[i] = ids[i].trim();
            }

            // setup the data to be stored for the classification
            var classificationData = {
                label: label,
                type: 'list',
                participantIds: ids
            };
            
            if (this.classificationRowId)
            {
                classificationData.rowId = this.classificationRowId;
            }

            // save the participant classification ("create" if no prior rowId, "update" if rowId exists)
            Ext.Ajax.request(
            {
                url : (this.classificationRowId
                        ? LABKEY.ActionURL.buildURL("participant-list", "updateParticipantClassification")
                        : LABKEY.ActionURL.buildURL("participant-list", "createParticipantClassification")
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
                jsonData : classificationData,
                headers : {'Content-Type' : 'application/json'},
                scope: this
            });
        },

        getDemoQueryWebPart: function(queryName)
        {
            var ptidClassificationPanel = this;
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
                maskEl: ptidClassificationPanel.getEl(),
                buttonBarPosition: 'top',
                buttonBar: {
                    includeStandardButtons: false,
                    items:[{
                        text: 'Add Selected',
                        handler: function(){
                            ptidClassificationPanel.getSelectedDemoParticipants(queryName, "selected");
                        }
                    },{
                        text: 'Add All',
                        handler: function(){
                            // todo: fix this to only add the visible records (i.e. if filter applied to dataregion)
                            ptidClassificationPanel.getSelectedDemoParticipants(queryName, "all");
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
            var ptidClassfiicationPanel = this;

            // get the selected rows from the QWP data region
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: queryName,
                selectionKey: Ext.ComponentMgr.get('demoDataRegion').selectionKey,
                showRows: showStr,
                columns: '<%= subjectNounColName %>',
                success: function(data){
                    // get the current list of participant ids from the form
                    var classPanelValues = ptidClassfiicationPanel.classificationPanel.getForm().getFieldValues();
                    var tempIds = classPanelValues["classificationIdentifiers"];

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
                               tempIds += (tempIds.length > 0 ? "," : "") + data.rows[i]["<%= subjectNounColName %>"];
                               tempIdsArray.push(data.rows[i]["<%= subjectNounColName %>"]);
                           }
                       }
                    }

                    // put the new list of ids into the form
                    ptidClassfiicationPanel.classificationPanel.getForm().setValues({
                       classificationIdentifiers: tempIds
                    });
                }
            });
        }
    });

    Ext.onReady(renderParticipantClassificationsGrid);
</script>