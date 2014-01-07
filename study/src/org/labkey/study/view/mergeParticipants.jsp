<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    StudyImpl s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounColumnName = s.getSubjectColumnName();
    Integer aliasDatasetId = s.getParticipantAliasDatasetId();
    String aliasDatasetName = null;
    String aliasColumn = s.getParticipantAliasProperty();
    String aliasSourceColumn = s.getParticipantAliasSourceProperty();

    if (aliasDatasetId != null)
    {
        DataSetDefinition ds = s.getDataSet(aliasDatasetId);
        if (ds != null)
        {
            aliasDatasetName = ds.getName();
        }
    }
    // For first implementation, auto creation of alias records is only supported for date based studies.
    // We autofill the date field with today's date.
    boolean allowAliasCreation = aliasDatasetName != null && !s.getTimepointType().isVisitBased();
%>
<div style="max-width: 1000px">
    <p>
        If a(n) <%= h(subjectNounSingular) %>  in your study has been loaded with an incorrect <%= h(subjectNounColumnName) %>,
        you can change the identifier. If you change the <%= h(subjectNounColumnName) %> to one that is already in the study,
        data will be merged into a single <%= h(subjectNounSingular) %>.
    </p>
</div>
<div id="mergeParticipantsPanel-div"></div>
<div>
    <p></p>
</div>
<div id="mergeResults-div"></div>
<div id="previewPanel-div" class="labkey-data-region-wrap"></div>
<script type="text/javascript">
    (function(){
        var jsSubjectNounColumnName = <%= PageFlowUtil.jsString(subjectNounColumnName) %>;
        var oldIdField;
        var newIdField;
        var createAliasCB;
        var aliasSourceField;
        var aliasDatasetName = <%= PageFlowUtil.jsString(aliasDatasetName) %>;
        var aliasColumn = <%= PageFlowUtil.jsString(aliasColumn) %>;
        var aliasSourceColumn = <%= PageFlowUtil.jsString(aliasSourceColumn) %>;
        var allowAliasCreation = <%= allowAliasCreation %>;
        var mergeButton = {};
        var globalError = false;

        var init = function()
        {
            Ext4.QuickTips.init();

            oldIdField = Ext4.create('Ext.form.field.Text', {
                id : 'oldIdField',
                fieldLabel: 'Change ' + jsSubjectNounColumnName,
                labelSeparator: '',
                value: "",
//                width : 310,
                labelWidth: 140,
                maxLength: 20,
                enforceMaxLength: true,
                listeners : {
                    change : function() {resetPreview(true);}
                }
            });

            newIdField = Ext4.create('Ext.form.field.Text', {
                id : 'newIdField',
                fieldLabel: 'to',
                labelSeparator: '',
                value: "",
//                width : 220,
                labelWidth: 40,
                maxLength: 20,
                enforceMaxLength: true,
                listeners : {
                    change : function() { resetPreview(true); }
                }

            });

            createAliasCB = Ext4.create('Ext.form.field.Checkbox', {
                id: 'createAliasCB',
                boxLabel: 'Create an Alias for the old ' + jsSubjectNounColumnName,
                checked: allowAliasCreation,
                listeners : {
                    change : function() {aliasSourceField.setDisabled(!createAliasCB.getValue()); resetPreview(true);}
                }
            });

            aliasSourceField = Ext4.create('Ext.form.field.Text', {
                id : 'aliasSourceField',
                fieldLabel: 'Source',
                labelSeparator: ' ',
                value: "",
                margin: "0 0 0 40",
                labelWidth: 40,
                maxLength: 20,
                enforceMaxLength: true,
                disabled: !allowAliasCreation,
                listeners : {
                    blur : function() {resetPreview(true);}
                }
            });

            mergeButton = Ext4.create('Ext.Button', {
                xtype: 'button',
                text: 'Merge',
                disabled : true,
                handler: function() {trimFields(); commitMerge(oldIdField.getValue(), newIdField.getValue(), createAliasCB.getValue(), aliasSourceField.getValue());}
            });

            var form = Ext4.create('Ext.form.FormPanel', {
                renderTo: 'mergeParticipantsPanel-div',
                bodyPadding: 10,
                bodyStyle: 'background: none',
                frame: false,
                border: false,
                width: 600,
                buttonAlign : 'left',
                items: [{
                    xtype : 'fieldcontainer',
                    layout : {
                        type : 'hbox',
                        defaultMargins : '2 25 2 0'
                    },
                    items : [oldIdField, newIdField]
                },
                    {xtype : 'fieldcontainer',
                        hidden : !allowAliasCreation,
                        layout : {
                            type : 'hbox'
                        },
                        items: [createAliasCB, aliasSourceField]
                    }
                ],
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui : 'footer',
                    style : 'background: none',
                    height : 30,
                    items: [{
                        xtype: 'button',
                        text: 'Preview',
                        handler: function() {trimFields(); previewMerge(oldIdField.getValue(), newIdField.getValue(), createAliasCB.getValue(), aliasSourceField.getValue());}
                    },
                    mergeButton,
                    {
                        xtype: 'button',
                        text: 'Cancel',
                        handler: function() {window.history.back();}
                    }]
                }]
            });
        };

        LABKEY.Query.selectRows({
            schemaName : 'study',
            queryName : 'Datasets',
            columns: 'Name',
            sort: 'Name',
            success : function(details){
                var rows = details.rows;
                this.tables = {};
                this.numDatasets = rows.length;
                for (var i = 0; i < rows.length; i++) {
                    var name = rows[i].Name;
                    this.tables[name] = {
                        name: name,
                        htmlName: Ext4.util.Format.htmlEncode(name),
                        datasetId : rows[i].DataSetId,
                        oldIds : [],
                        newIds : []
                    };
                }
                // add in the specimen detail table for status reporting, note that it does not have a datasetId
                // and that numDatasets does not include this
                this.tables['SpecimenDetail'] = {
                    name : 'SpecimenDetail',
                    htmlName : 'SpecimenDetail',
                    oldIds : [],
                    newIds : [],
                    isEditable : true}
            },
            scope : this
        });

        var trimFields = function() {
            oldIdField.setValue(Ext.util.Format.trim(oldIdField.getValue()));
            newIdField.setValue(Ext.util.Format.trim(newIdField.getValue()));
            aliasSourceField.setValue(Ext.util.Format.trim(aliasSourceField.getValue()));
        };

        var resetPreview = function(resetPreviewPanel)
        {
            mergeButton.setDisabled(true);
            emptyPreviewTableStatus();
            globalError = false;
            document.getElementById('mergeResults-div').innerHTML = "";
            if (resetPreviewPanel) {
                document.getElementById('previewPanel-div').innerHTML = "";
            }
        };

        var emptyPreviewTableStatus = function() {
            for (var tableName in this.tables) {
                var table = this.tables[tableName];
                table.oldIds = [];
                table.newIds = [];
                table.hasConflict = null;
            }
        };

        // gather the ids for either a dataset or the specimen
        // details table
        var gatherIds = function(table, rows, oldId, newId){
            var idColumn = table.datasetId ? 'lsid' : 'RowId';
            for (var i = 0; i < rows.length; i++) {
                // Figure out which participant the rows belong to
                var row = rows[i];
                if (row[jsSubjectNounColumnName] == oldId) {
                    table.oldIds.push(row[idColumn]);
                }
                if (row[jsSubjectNounColumnName] == newId) {
                    table.newIds.push(row[idColumn]);
                }
            }
        };

        var previewMerge = function(oldId, newId, createAlias, aliasSource) {
            var filters = [ LABKEY.Filter.create(jsSubjectNounColumnName, oldId + ';' + newId, LABKEY.Filter.Types.IN) ];
            this.datasetsToProcess = this.numDatasets;

            // wipe away our status
            resetPreview(false);
            renderPreviewTable(oldId, newId);

            // Iterate on datasets and skip the specimen detail table
            for (var tableName in this.tables) {

                if (!this.tables[tableName].datasetId)
                    continue;

                LABKEY.Query.selectRows( {
                    schemaName: 'study',
                    queryName: tableName,
                    scope: this,
                    filterArray : filters,
                    columns : jsSubjectNounColumnName + ', lsid',
                    success: function(data) {
                        var table = this.tables[data.queryName];
                        gatherIds(table, data.rows, oldId, newId);
                        //
                        // if table has both new and old ids, then make an update clause
                        // and check to see if we have any conflict errors
                        //
                        if (table.name != aliasDatasetName && table.oldIds.length > 0 && table.newIds.length > 0)
                        {
                            LABKEY.Query.saveRows({
                                commands : [buildUpdateCommand(table, newId)],
                                success : function(data) {
                                   checkMoveToSpecimenDetail(table, false, oldId, newId, filters);
                                },
                                failure : function(errorInfo, response) {
                                    checkMoveToSpecimenDetail(table, true, oldId, newId, filters);
                                },
                                validateOnly : true
                            });
                        }
                        else if (table.name == aliasDatasetName) {
                            table.hasConflict = false;
                            if (table.oldIds.length > 0) {
                                table.hasConflict = true;
                            }
                            if (createAlias) {
                                LABKEY.Query.saveRows({
                                    commands : [buildInsertCommand(oldId, newId, aliasSource)],
                                    success : function() {},
                                    failure : function(response) {updateMergeResults("Error creating alias. " + response.exception, true); },
                                    validateOnly : true
                                })
                            }
                            checkMoveToSpecimenDetail(table, table.hasConflict, oldId, newId, filters);
                        }
                        else {
                            checkMoveToSpecimenDetail(table, false, oldId, newId, filters);
                        }

                        // update progress
                        renderPreviewTable(oldId, newId);
                    }
                });
            }
        };

        var checkMoveToSpecimenDetail = function(table, hasConflict, oldId, newId, filters) {
            table.hasConflict = hasConflict;
            this.datasetsToProcess--;
            if (this.datasetsToProcess == 0)
                checkSpecimenDetail(oldId, newId, filters);
        };

        var checkSpecimenDetail = function(oldId, newId, filters) {
            var specimenDetail = this.tables['SpecimenDetail'];
            // now check the specimen details table
            LABKEY.Query.selectRows( {
                schemaName: 'study',
                queryName: 'SpecimenDetail',
                scope: this,
                filterArray : filters,
                columns : jsSubjectNounColumnName + ', RowId',
                success: function(data) {
                    gatherIds(specimenDetail, data.rows, oldId, newId);
                    //
                    // For the specimenDetail table we want to check to see if it is editable
                    // or whether conflicts exist.  Kill two birds with one stone if we detect that
                    // we need to update it because it has values for the oldId
                    //
                    if (specimenDetail.oldIds.length > 0) {
                        // add the SpecimenDetail to our list of tables since we need to update it
                        this.tables['SpecimenDetail'] = specimenDetail;
                        LABKEY.Query.saveRows({
                            commands : [buildUpdateCommand(specimenDetail, newId)],
                            validateOnly : true,
                            success : function(data) {
                                specimenDetail.hasConflict = false;
                                renderPreviewTable(oldId, newId);
                            },
                            failure : function(errorInfo, response) {
                                specimenDetail.hasConflict = true;
                                // we get an internal server error if the item is not editable
                                // consider: better way to check?
                                // we get a bad request error for a validation failure
                                if (response.status == 500){
                                    specimenDetail.isEditable = false;
                                }
                                renderPreviewTable(oldId, newId);
                            }
                        });
                    }
                    else {
                        specimenDetail.hasConflict = false;
                        renderPreviewTable(oldId, newId);
                    }
                },
              failure : function(response) {
                  // it's okay if no specimen details exist
                  specimenDetail.hasConflict = false;
                  renderPreviewTable(oldId, newId);
              }
            });
        };

        var buildInsertCommand = function(oldId, newId, aliasSource) {
            var rowsToInsert = [];
            var row = {};
            row[jsSubjectNounColumnName] = newId;
            row[aliasColumn] = oldId;
            row[aliasSourceColumn] = aliasSource;
            row['date'] =  new Date();
            rowsToInsert.push(row);

            return {schemaName : 'study', queryName : aliasDatasetName, command : 'insert', rows : rowsToInsert};

        };

        var buildUpdateCommand = function(table, newId) {
            var rowsToUpdate = [];
            var idColumnName = (table.datasetId ? 'lsid' :'RowId');
            for (var i = 0; i < table.oldIds.length; i++) {
                var row = {};
                row[idColumnName] = table.oldIds[i];
                row[jsSubjectNounColumnName] = newId;
                rowsToUpdate.push(row);
            }
            return {schemaName : 'study', queryName : table.name, command : 'update', rows : rowsToUpdate};
        };

        var buildDeleteCommand = function(table, ids) {
            var rowsToDelete = [];
            var idColumnName = (table.datasetId ? 'lsid' :'RowId');
            for (var i = 0; i < ids.length; i++) {
                var row = {};
                row[idColumnName] = ids[i];
                rowsToDelete.push(row);
            }
            return {schemaName : 'study',queryName : table.name, command : 'delete', rows : rowsToDelete};
        };

        var getConflictHintGroupName = function(table) {
            return "conflict_" + table.htmlName;
        };

        // old if we should use the old ids (delete new)
        // new if we should use the new ids (delete old)
        // null if the user didn't pick a conflict hint yet
        var getConflictHint = function(table){
            if (table.hasConflict) {
                var values = document.getElementsByName(getConflictHintGroupName(table));
                for (var i = 0; i < values.length; i++) {
                    if (values[i].checked) {
                        return values[i].value;
                    }
                }
            }
            return null;
        };

        var updateMergeResults = function(message, isError)
        {
            var statusEl = document.getElementById('mergeResults-div');
            if (isError)
            {
                statusEl.innerHTML = "<span style='color:red;padding-top: 8px; font-weight: bold;'>" + Ext4.util.Format.htmlEncode(message) + "<br/></span>";
                globalError = true;
            }
            else
                statusEl.innerHTML = "<span style='padding-top: 8px; font-weight: bold;'>" + Ext4.util.Format.htmlEncode(message) + "<br/></span>";
        };

        var commitMerge = function(oldId, newId, createAlias, aliasSource){
            var saveRowsCommands = [];
            for (var tableName in this.tables) {
                var table = this.tables[tableName];
                if (false == table.isEditable || tableName == aliasDatasetName)
                    continue;
                //
                // dataset had both old and new values (this is a conflict that we have to resolve)
                // dataset had only old values (we need to update these to the new values)
                // dataset had only new values (no need to do anything)
                // dataset had neither old or new values (no need to do anything)
                //
                if (table.hasConflict) {
                    // a conflict means that a dataset had both old and new values in the dataset
                    var hint = getConflictHint(table);
                    if (null == hint) {
                        updateMergeResults("You must choose whether to use old or new id values for every conflict", true);
                        return;
                    }
                    if (hint == 'old') {
                        // the user wants to use the old values which means we need to first delete values attached to the new
                        // id and then update the old id to the new id
                        saveRowsCommands.push(buildDeleteCommand(table, table.newIds));
                        saveRowsCommands.push(buildUpdateCommand(table, newId));
                    }
                    else {
                        // user wants to use the new values so just delete the old ones
                        saveRowsCommands.push(buildDeleteCommand(table, table.oldIds));
                    }
                }
                else
                if (table.oldIds.length > 0){
                    // just update the old values to the new value
                    saveRowsCommands.push(buildUpdateCommand(table, newId));
                }
            }
            if (createAlias) {
                saveRowsCommands.push(buildInsertCommand(oldId, newId, aliasSource));
            }
            resetPreview(true);
            updateMergeResults("Merging " + jsSubjectNounColumnName + " from " + oldId + " to " + newId + " ...", false);
            LABKEY.Query.saveRows({
                commands : saveRowsCommands,
                success : function(data) {
                    oldIdField.setValue("");
                    newIdField.setValue("");
                    createAliasCB.setValue(false);
                    aliasSourceField.setValue("");
                    resetPreview(true);
                    updateMergeResults("Successfully merged " + jsSubjectNounColumnName + " from " + oldId + " to " + newId, false);
                },
                failure : function(response) {
                    updateMergeResults(response.exception, true);
                }
            });
        };

        var enableMerge = function() {
            mergeButton.setDisabled(false);
        };

        // build links to filtered datasets
        // if both old and new ids are specified, use an in-clause
        // if only one id is used, use an equal clause
        var buildDataURL = function(table, oldId, newId) {
            var filter;
            if (oldId && newId) {
                filter = LABKEY.Filter.create(jsSubjectNounColumnName, oldId + ';' + newId, LABKEY.Filter.Types.IN);
            }
            else {
                filter = LABKEY.Filter.create(jsSubjectNounColumnName, oldId ? oldId : newId, LABKEY.Filter.Types.EQUAL);
            }

            var url;

            if (table.datasetId) {
                var params = {datasetId: table.datasetId};
                params[filter.getURLParameterName().replace('query.', 'Dataset.')] = filter.getURLParameterValue();
                url = LABKEY.ActionURL.buildURL('study', 'dataset.view', null, params);
            }
            else {
                // SpecimenDetail table
                var params = {};
                params[filter.getURLParameterName()] = filter.getURLParameterValue();
                params.schemaName = 'study';
                params.queryName = 'SpecimenDetail';
                url = LABKEY.ActionURL.buildURL("query", 'executeQuery.view', null, params);
            }
            url = "<a href='" + url + "' target='_blank'/>";
            return url;
        };

        var renderPreviewTable = function(oldId, newId){
            var html = [];
            var encodedOldId = Ext4.util.Format.htmlEncode(oldId);
            var encodedNewId = Ext4.util.Format.htmlEncode(newId);
            html.push("<table class='labkey-data-region labkey-show-borders'>");
            html.push("<tr><td class='labkey-column-header'>Data Source</td>");
            html.push("<td class='labkey-column-header'>" + encodedOldId + " Row Count</td>");
            html.push("<td class='labkey-column-header'>" + encodedNewId + " Row Count</td>");
            html.push("<td class='labkey-column-header'>Status</td>");
            html.push("<td class='labkey-column-header'>&nbsp;</td></tr>");

            var row = 0;
            var hasOldValues = false;
            var stillChecking = false;

            for (var tableName in this.tables) {
                var table = this.tables[tableName];
                var rowStyle = (1==row%2) ? "labkey-alternate-row" : "labkey-row";
                html.push("<tr class='" + rowStyle + "'>");
                // provide links to filtered data views for the columns
                var bothIdURL = buildDataURL(table, oldId, newId);
                var oldIdURL = buildDataURL(table, oldId, null);
                var newIdURL = buildDataURL(table, null, newId);
                html.push("<td>" + bothIdURL + tableName + "</td>");

                if (null == table.hasConflict) {
                    html.push("<td>Loading...</td>");
                    html.push("<td>Loading...</td>");
                    html.push("<td>Checking...</td>");
                    html.push("<td>&nbsp;</td></tr>");
                    stillChecking = true;
                }
                else
                {
                    html.push("<td>" + oldIdURL + table.oldIds.length + "</td>");
                    html.push("<td>" + newIdURL + table.newIds.length + "</td>");
                    if (true == table.hasConflict) {
                        if (false == table.isEditable) {
                            html.push("<td><span style='color:red;'>Warning:  Specimen data is not editable</span></td>");
                        }
                        else if (table.name == aliasDatasetName) {
                            html.push("<td>Aliases are not updated by this process<br/>" + oldIdURL + "<span style='color:red;'>Warning:  " + encodedOldId + " has existing aliases</span></td>");
                        }
                        else {
                            //
                            // build a radio group to allow the user to select old or new id values when merging
                            // TODO: is there a robust way to get a change notification on when these are clicked?  We only
                            // want to enable the merge button if the user has selected whether to keep old or new
                            // id values.  Right now we do this when the user hits an enabled merge button
                            //
                            var group = getConflictHintGroupName(table);
                            html.push("<td>" + bothIdURL + "Conflict!</td>");
                            html.push("<td>");
                            html.push("<input type='radio' name='" + group + "' value='old'>Retain '" + encodedOldId + "' rows ");
                            html.push("<input type='radio' name='" + group + "' value='new'>Retain '" + encodedNewId + "' rows ");

                            html.push("</td>");
                        }
                    } else {
                        if (table.name == aliasDatasetName) {
                            html.push("<td>Aliases are not updated by this process</td>");
                        }
                        else {
                            html.push("<td>" + bothIdURL + "No conflicts</td>");
                        }
                        html.push("<td>&nbsp;</td></tr>");
                    }
                }
                // if any editable dataset has any rows with the old value then there is work to do
                if ((table.oldIds.length > 0) && (false != table.isEditable))
                    hasOldValues = true;
                row++;
            }
            html.push("</table>");
            document.getElementById('previewPanel-div').innerHTML = html.join("");
            if (hasOldValues && !stillChecking && !globalError)
                enableMerge();
            return 0;
        };

        Ext4.onReady(init);

    })();
</script>
