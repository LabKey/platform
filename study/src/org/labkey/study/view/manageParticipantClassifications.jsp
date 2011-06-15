<%
/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
    var _classificationData;

    function getParticipantClassificationsForGrid(){
        // get the classifications list from the given study container
        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("participant-list", "getParticipantClassifications"),
            method : 'POST',
            success: renderParticipantClassificationsGrid,
            failure: function(){Ext.Msg.alert("Delete Classification", "Deletion Failed");},
            headers : {'Content-Type' : 'application/json'}
        });
    }

    function renderParticipantClassificationsGrid(response)
    {
        // decode the JSON responseText
        _classificationData = Ext.util.JSON.decode(response.responseText);

        // if the grid has already been created, destroy it to create the new one
        if (_grid)
        {
            _grid.destroy();
        }

        var store = new Ext.data.JsonStore({
            root: 'classifications',
            idProperty: 'rowId',
            data: _classificationData,
            fields: [
                {name: 'rowId', type: 'integer'},
                {name: 'label', type: 'string'},
                {name: 'type', type: 'string'},
                {name: 'createdBy', type: 'string'},
                {name: 'shared', type: 'string'}
            ]
        });

        var columnModel = new Ext.grid.ColumnModel({
            defaults: {
                width: 200,
                sortable: false
            },
            columns: [
                {header:'Label', dataIndex:'label'},
                {header:'Type', dataIndex:'type'},
                {header:'Created By', dataIndex:'createdBy'},
                {header:'Shared', dataIndex:'shared'}
            ]
        });

        var selectionModel =  new Ext.grid.RowSelectionModel({
            singleSelect:true
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
                editParticipantClassifications(_grid);
            },
            scope: this
        },{
            id: 'deleteSelectedButton',
            text: 'Delete Selected',
            disabled: true,
            handler: function(){
                deleteParticipantClassifications(_grid);
            },
            scope: this
        }];

        // create a gridpanel with the list of classifications (one per row)
        _grid = new Ext.grid.GridPanel({
            renderTo: 'participantClassificationsGrid',
            autoScroll:false,
            autoHeight:true,
            width:800,
            loadMask:{msg:"Loading classifications..."},
            store: store,
            colModel: columnModel,
            selectionModel: selectionModel,            
            viewConfig: {forceFit: true},
            tbar: tbarButtons
        });

        // rowclick listener to enable/disable the edit and delete buttons based on selection
        _grid.on('rowclick', function(g, rowIdx, e){
            var count = g.getSelectionModel().getCount();
            var topTB = g.getTopToolbar();

            if (count == 1)
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

    function editParticipantClassifications(grid){
        // get the selected row from the gridpanel (in the case of "edit selected")
        var selectedRowId = null;
        if (grid)
        {
            var selectedRow = grid.getSelectionModel().getSelected();
            selectedRowId = selectedRow.get("rowId");
        }

        var win = new Ext.Window({
            cls: 'extContainer',
            title: 'Create <%= subjectNounSingular %> Classification',
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            closeAction:'close',
            items: new LABKEY.ParticipantClassificationPanel({
                classificationRowId: selectedRowId,
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

    function deleteParticipantClassifications(grid){
        if (grid)
        {
            // todo: do we need to handle deletion of a shared/public classification differently?

            var selectedRow = grid.getSelectionModel().getSelected();
            Ext.Msg.show({
                    title : 'Delete Classification',
                    msg : 'Delete Selected Classification:<br/>' + selectedRow.get("label"),
                    buttons: Ext.Msg.YESNO,
                    icon: Ext.Msg.QUESTION,
                    fn: function(btn, text) {
                        if (btn == 'yes')
                        {
                            Ext.Ajax.request({
                                url: LABKEY.ActionURL.buildURL("participant-list", "deleteParticipantClassification"),
                                method: "POST",
                                success: getParticipantClassificationsForGrid,
                                failure: function(){Ext.Msg.alert("Delete Classification", "Deletion Failed");},
                                jsonData: {rowId: selectedRow.get("rowId")},
                                headers : {'Content-Type' : 'application/json'}
                            });
                        }},
                    id: 'delete_classifications'
            });
        }
    }

    /**
     * get the index of the given classification from the results of the getParticipantClassifciations call
     * @param rowId
     */
    function getClassficationIndex(rowId)
    {
        var index = -1;
        for (var i = 0; i < _classificationData.classifications.length; i++)
        {
            if (_classificationData.classifications[i].rowId == rowId)
            {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * Panel to take user input for the label and list of participant ids for a classfication to be created or edited
     * @param classificationRowId = the rowId of the classification to be edited (null if create new) 
     */
    LABKEY.ParticipantClassificationPanel = Ext.extend(Ext.Panel, {
        constructor : function(config){
            Ext.apply(config, {
                layout: 'form',
                bodyStyle:'padding:20px;',
                border: false,
                autoScroll: true
            });

            // if this is an "edit selected" action, get the classification label and participant list
            if (config.classificationRowId && getClassficationIndex(config.classificationRowId) > -1)
            {
                var index = getClassficationIndex(config.classificationRowId);
                config.classificationLabel = _classificationData.classifications[index].label;
                config.classificationParticipnatIds = _classificationData.classifications[index].participantIds;
            }

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
                    value: (this.classificationParticipnatIds ? this.classificationParticipnatIds.toString() : null),
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
                }
            });

            this.demoCombobox = new Ext.form.ComboBox({
                triggerAction: 'all',
                mode: 'local',
                store: demoStore,
                valueField: 'Label',
                displayField: 'Label',
                fieldLabel: 'Select <%= subjectNounPlural %> from',
                labelStyle: 'width: 175px;',
                minListWidth : 300
            });

            this.demoCombobox.on('render', function(cmp)
            {
                cmp.getStore().load();
            }, this);

            this.demoCombobox.on('select', function(cmp, record, index)
            {
                this.getDemoQueryWebPart(record.get("Label"));
            }, this);

            this.items = [
                this.classificationPanel,
                this.demoCombobox,
                {
                    html: "<div id='demoQueryWebPartDiv' width='100%'></div>",
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
                    getParticipantClassificationsForGrid();
                    this.getEl().unmask();
                    this.fireEvent('closeWindow');
                },
                failure: function(response, options){
                    // todo: need to handle failure for ptid not in study
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
            var demoQueryWebPart =  new LABKEY.QueryWebPart({
                renderTo: 'demoQueryWebPartDiv',
                dataRegionName: 'demoDataRegion',
                schemaName: 'study',
                queryName: queryName,
                frame: 'none',
                border: false,
                showRecordSelectors: true,
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
                            ptidClassificationPanel.getSelectedDemoParticipants(queryName, "all");
                        }
                    }]
                }
            });

//            // on render, clear any cached selections from the dataregion
//            demoQueryWebPart.on('render', function(){
//                LABKEY.DataRegion.clearSelected({
//                    selectionKey: Ext.ComponentMgr.get('demoDataRegion').selectionKey,
//                    success: function(){}
//                });
//            });
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

    Ext.onReady(getParticipantClassificationsForGrid);
</script>