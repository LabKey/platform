/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.3
 * @license Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

Ext.namespace('LABKEY', 'LABKEY.ext');
Ext.QuickTips.init();
Ext.apply(Ext.QuickTips.getQuickTip(), {
    dismissDelay: 15000
})
/**
 * Constructs a new LabKey EditorGridPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.grid.EditorGridPanel">Ext.grid.EditorGridPanel</a>,
 * which can provide editable grid views
 * of data in the LabKey server. If the current user has appropriate permissions, the user may edit
 * data, save changes, insert new rows, or delete rows.
 * @constructor
 * @augments Ext.grid.EditorGridPanel
 * @param config Configuration properties. This may contain any of the configuration properties supported
 * by the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.grid.EditorGridPanel">Ext.grid.EditorGridPanel</a>,
 * plus those listed here.
 * @param {boolean} [config.lookups] Set to false if you do not want lookup foreign keys to be resolved to the
 * lookup values, and do not want dropdown lookup value pickers (default is true).
 * @param {integer} [config.pageSize] Defines how many rows are shown at a time in the grid (default is 20).
 * @param {boolean} [config.editable] Set to true if you want the user to be able to edit, insert, or delete rows (default is false).
 * @param {boolean} [config.autoSave] Set to false if you do not want changes automatically saved when the user leaves the row (default is true).
 * @param {string} [config.loadingCaption] The string to display in a cell when loading the lookup values (default is "[loading...]").
 * @param {string} [config.lookupNullCaption] The string to display for a null value in a lookup column (default is "[none]").
 * @property {map} lookupStores  A map of lookup data stores where the key is the column name,
 * and the value is an LABKEY.ext.Store object containing records for the lookup values for the given column.
 * @example &lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
&lt;/script&gt;
&lt;script type="text/javascript"&gt;
    var _grid;
    Ext.onReady(function(){

        //initialize the Ext QuickTips support
        //which allows quick tips to be shown
        Ext.QuickTips.init();

        _store = new LABKEY.ext.Store({
            schemaName: 'lists',
            queryName: 'Kitchen Sink'
        });

        _grid = new LABKEY.ext.EditorGridPanel({
            store: new LABKEY.ext.Store({
                schemaName: 'lists',
                queryName: 'People'
            }),
            renderTo: 'grid',
            width: 800,
            autoHeight: true,
            title: 'Example',
            editable: true
        });
    });
&lt;/script&gt;
&lt;div id='grid'/&gt;
 */
LABKEY.ext.EditorGridPanel = Ext.extend(Ext.grid.EditorGridPanel, {

    constructor : function(config) {
        //NOTE: I tried implementing initComponent instead of a constructor method (like Ext 2 recommends)
        //but it didn't work because the EditorGridPanel expects the column model to be provided before
        //rendering. Since the column model comes from the server in the initial data query, and since that
        //query is async, we need to delay construction of the base class until the store has finished
        //loading. I tried delaying initComponent, but that didn't work, as the base-class constructor
        //begins the rendering process directly after the derived initComponent method finishes.

        this.lookupStores = {};

        this.addEvents("beforedelete", "columnmodelcustomize");

        //apply the config with defaults
        Ext.apply(this, config, {
            lookups: true,
            store : new LABKEY.ext.Store({
                schemaName: config.schemaName,
                queryName: config.queryName,
                viewName: config.viewName,
                filterArray: config.filterArray,
                sort: config.sort,
                columns: config.columns
            }),
            pageSize: 20,
            editable: false,
            autoSave: true,
            loadingCaption: "[loading...]",
            lookupNullCaption : "[none]",
            selModel: new Ext.grid.CheckboxSelectionModel({
                moveEditorOnEnter: false
            }),
            viewConfig: {forceFit: true},
            id: Ext.id(undefined, 'labkey-ext-grid')
        });

        //need to setup the default panel config *before*
        //calling load on the store
        //for some reason, the paging toolbar seems to wait
        //for the load event from the store before adjusting
        //it's UI
        this.setupDefaultPanelConfig(this);

        //delay construction of the superclass until the load callback so
        //we can get the column model from the reader's jsonData
        this.store.on("loadexception", this.onStoreLoadException, this);
        this.store.on("load", this.onStoreLoad, this);
        this.store.load({ params : {
                start: 0,
                limit: this.pageSize
            }});
    },

    /**
     * Returns the LABKEY.ext.Store object used to hold the
     * lookup values for the specified column name. If the column
     * name is not a lookup column, this method will return null.
     * @name getLookupStore
     * @function
     * @memberOf LABKEY.ext.EditorGridPanel
     * @param {String} columnName The column name.
     * @return {LABKEY.ext.Store} The lookup store for the given column name, or null
     * if no lookup store exists for that column.
     */
    getLookupStore : function(columnName) {
        return this.store.getLookupStore(columnName);
    },

    /**
     * Saves all pending changes to the database. Note that if
     * the required fields for a given record does not have values,
     * that record will not be saved and will remain dirty until
     * values are supplied for all required fields.
     * @name saveChanges
     * @function
     * @memberOf LABKEY.ext.EditorGridPanel
     */
    saveChanges : function() {
        this.stopEditing();
        this.getStore().commitChanges();
    },

    /*-- Private Methods --*/

    onStoreLoad : function(store, records, options) {
        this.store.un("load", this.onStoreLoad, this);

        this.populateMetaMap(this);
        this.setupColumnModel(this);

        //construct the superclass
        LABKEY.ext.EditorGridPanel.superclass.constructor.call(this, this);

        //subscribe to events
        this.on("beforeedit", this.onBeforeEdit, this);
    },

    onStoreLoadException : function(proxy, options, response, error) {
        var msg = error;
        if(!msg && response.responseText)
        {
            var json = Ext.util.JSON.decode(response.responseText);
            if(json)
                msg = json.exception;
        }
        if(!msg)
            msg = "Unable to load data from the server!";

        Ext.Msg.alert("Error", msg);
    },

    populateMetaMap : function(config) {
        //the metaMap is a map from field name to meta data about the field
        //the meta data contains the following properties:
        // id, totalProperty, root, fields[]
        // fields[] is an array of objects with the following properties
        // name, type, lookup
        // lookup is a nested object with the following properties
        // schema, table, keyColumn, displayColumn
        this.metaMap = {};
        var fields = config.store.reader.jsonData.metaData.fields;
        for(var idx = 0; idx < fields.length; ++idx)
        {
            var field = fields[idx];
            this.metaMap[field.name] = field;
        }
    },

    setupColumnModel : function(config) {

        //set the columns property to the columnModel returned in the jsonData
        config.columns = config.store.reader.jsonData.columnModel;

        //set the renderers and editors for the various columns
        //build a column model index as we run the columns for the
        //customize event
        var colModelIndex = {};
        var col;
        for(var idx = 0; idx < config.columns.length; ++idx)
        {
            col = config.columns[idx];
            colModelIndex[col.dataIndex] = col;

            //set col.editable false unless the config.editable is true
            if(!config.editable)
                col.editable = false;

            if(col.editable && !col.editor)
                col.editor = this.getDefaultEditor(col, config.lookups);
            if(!col.renderer)
                col.renderer = this.getDefaultRenderer(col, config.lookups, config.id);

            //remember the first editable column (used during add record)
            if(!this.firstEditableColumn && col.editable)
                this.firstEditableColumn = idx;
        }

        //if a sel model has been set, and if it needs to be added as a column,
        //add it to the front of the list.
        //CheckBoxSelectionModel needs to be added to the selection model for
        //the check boxes to show up.
        //(not sure why its constructor doesn't do this autmatically).
        if(config.selModel && config.selModel.renderer)
            config.columns = [config.selModel].concat(config.columns);

        //register for the rowdeselect event if the selmodel supports events
        //and if autoSave is on
        if(config.selModel.on && config.autoSave)
            config.selModel.on("rowselect", this.onRowSelect, this);

        //fire the "columnmodelcustomize" event to allow clients
        //to modify our default configuration of the column model
        this.fireEvent("columnmodelcustomize", config.columns, colModelIndex);

        //add custom renderers for multiline/long-text columns
        this.setLongTextRenderers(config);
    },

    getDefaultRenderer : function(col, lookups, gridId) {
        var meta = this.metaMap[col.dataIndex];

        if(meta.lookup && lookups)
            return this.getLookupRenderer(col, meta);

        return function(data, cellMetaData, record, rowIndex, colIndex, store)
        {
            if(null == data)
                return data;

            //format data into a string
            var displayValue;
            switch (meta.type)
            {
                case "date":
                    var date = new Date(data);
                    if (date.getHours() == 0 && date.getMinutes() == 0 && date.getSeconds() == 0)
                        displayValue = date.format("Y-m-d");
                    else
                        displayValue = date.format("Y-m-d H:i:s")
                    break;
                case "string":
                case "boolean":
                case "int":
                case "float":
                default:
                    displayValue = data.toString();
            }

            //if meta.file is true, add an <img> for the file icon
            if(meta.file)
            {
                displayValue = "<img src=\"" + LABKEY.Utils.getFileIconUrl(data) + "\" alt=\"icon\" title=\"Click to download file\"/>&nbsp;" + displayValue;
                //since the icons are 16x16, cut the default padding down to just 1px
                cellMetaData.attr = "style=\"padding: 1px 1px 1px 1px\"";
            }

            //wrap in <a> if url is present in the record
            if(record.get("_labkeyurl_" + col.dataIndex))
                return "<a href=\"" + record.get("_labkeyurl_" + col.dataIndex) + "\">" + displayValue + "</a>";
            else
                return displayValue;
        }
    },

    getLookupRenderer : function(col, meta) {
        var store = this.store.getLookupStore(meta.name, !col.required);
        store.on("load", this.onLookupStoreLoad, this);
        store.on("loadexception", this.onLookupStoreError, this);
        return function(data)
        {
            if(store.loadError)
                return "ERROR: " + store.loadError.message;

            var record = store.getById(data);
            if (record)
                return record.data[meta.lookup.displayColumn];
            else if (data)
                return "[" + data + "]";
            else
                return this.lookupNullCaption || "[none]";
        };
    },

    onLookupStoreLoad : function(store, records, options) {
        if(this.view)
            this.view.refresh();
    },

    onLookupStoreError : function(proxy, options, response, error)
    {
        var message = error;
        var ctype = response.getResponseHeader["Content-Type"];
        if(ctype.indexOf("application/json") >= 0)
        {
            var errorJson = Ext.util.JSON.decode(response.responseText);
            if(errorJson && errorJson.exception)
                message = errorJson.exception;
        }
        Ext.Msg.alert("Load Error", "Error loading lookup data: " + message);

        if(this.view)
            this.view.refresh();
    },

    getDefaultEditor : function(col, lookups) {
        var editor;

        var meta = this.metaMap[col.dataIndex];
        if(!meta)
            return;

        //if this column is a lookup, return the lookup editor
        if(meta.lookup && lookups)
            return this.getLookupEditor(col, meta);

        switch(meta.type)
        {
            case "int":
                editor = new Ext.form.NumberField({
                    allowDecimals : false
                });
                break;
            case "float":
                editor = new Ext.form.NumberField({
                    allowDecimals : true
                });
                break;
            case "date":
                editor = new Ext.form.DateField({
                    format : "Y-m-d",
                    altFormats: "Y-m-d" +
                                'n/j/y g:i:s a|n/j/Y g:i:s a|n/j/y G:i:s|n/j/Y G:i:s|' +
                                'n-j-y g:i:s a|n-j-Y g:i:s a|n-j-y G:i:s|n-j-Y G:i:s|' +
                                'n/j/y g:i a|n/j/Y g:i a|n/j/y G:i|n/j/Y G:i|' +
                                'n-j-y g:i a|n-j-Y g:i a|n-j-y G:i|n-j-Y G:i|' +
                                'j-M-y g:i a|j-M-Y g:i a|j-M-y G:i|j-M-Y G:i|' +
                                'n/j/y|n/j/Y|' +
                                'n-j-y|n-j-Y|' +
                                'j-M-y|j-M-Y|' +
                                'Y-n-d H:i:s|Y-n-d'
                });
                break;
            case "boolean":
                editor = new Ext.form.Checkbox();
                break;
            case "string":
            default:
                editor = new Ext.form.TextField();
                break;
        }

        if (editor)
            editor.allowBlank = !col.required;

        return editor;
    },

    getLookupEditor : function(col, meta) {
        var store = this.store.getLookupStore(meta.name, !col.required);
        return new Ext.form.ComboBox({
            store: store,
            allowBlank: !col.required,
            typeAhead: false,
            triggerAction: 'all',
            mode: 'local', //use local mode since the lookup store will already be loaded
            editable: false,
            displayField: meta.lookup.displayColumn,
            valueField: meta.lookup.keyColumn,
            tpl : '<tpl for="."><div class="x-combo-list-item">{[values["' + meta.lookup.displayColumn + '"]]}</div></tpl>', //FIX: 5860
            listClass: 'labkey-grid-editor'
        });
    },

    setLongTextRenderers : function(config) {
        var col;
        for(var idx = 0; idx < config.columns.length; ++idx)
        {
            col = config.columns[idx];
            if(col.multiline || (undefined === col.multiline && col.scale > 255))
            {
                col.renderer = function(data, metadata, record, rowIndex, colIndex, store)
                {
                    //set quick-tip attributes and let Ext QuickTips do the work
                    metadata.attr = "ext:qtitle=\"Notes\" ext:qtip=\"" + Ext.util.Format.htmlEncode(data) + "\"";
                    return data;
                }

                if(col.editable)
                    col.editor = new LABKEY.ext.LongTextField({
                        columnName: col.dataIndex
                    });
            }
        }
    },

    setupDefaultPanelConfig : function(config) {
        if(!config.tbar)
        {
            config.tbar = [{
                text: 'Refresh',
                tooltip: 'Click to refresh the table',
                id: 'refresh-button',
                handler: this.onRefresh,
                scope: this
            }];

            if(config.editable && LABKEY.user && LABKEY.user.canUpdate && !config.autoSave)
            {
                config.tbar[config.tbar.length] = "-";
                config.tbar[config.tbar.length] = {
                    text: 'Save Changes',
                    tooltip: 'Click to save all changes to the database',
                    id: 'save-button',
                    handler: this.saveChanges,
                    scope: this
                }
            }

            if(config.editable &&LABKEY.user && LABKEY.user.canInsert)
            {
                config.tbar[config.tbar.length] = "-";
                config.tbar[config.tbar.length] = {
                    text: 'Add Record',
                    tooltip: 'Click to add a row',
                    id: 'add-record-button',
                    handler: this.onAddRecord,
                    scope: this
                };
            }
            if(config.editable &&LABKEY.user && LABKEY.user.canDelete)
            {
                config.tbar[config.tbar.length] = "-";
                config.tbar[config.tbar.length] = {
                    text: 'Delete Selected',
                    tooltip: 'Click to delete selected row(s)',
                    id: 'delete-records-button',
                    handler: this.onDeleteRecords,
                    scope: this
                };
            }
        }

        if(!config.bbar)
        {
            config.bbar = new Ext.PagingToolbar({
                    pageSize: config.pageSize, //default is 20
                    store: config.store,
                    emptyMsg: "No data to display" //display message when no records found
                });
        }

        if(!config.keys)
        {
            config.keys = [
                {
                    key: Ext.EventObject.ENTER,
                    handler: this.onEnter,
                    scope: this
                },
                {
                    key: 45, //insert
                    handler: this.onAddRecord,
                    scope: this
                },
                {
                    key: Ext.EventObject.ESC,
                    handler: this.onEsc,
                    scope: this
                },
                {
                    key: Ext.EventObject.TAB,
                    handler: this.onTab,
                    scope: this
                }
            ]
        }

    },

    onRefresh : function() {
        this.getStore().reload();
    },

    onAddRecord : function() {
        if(!this.store || !this.store.addRecord)
            return;

        this.stopEditing();
        this.store.addRecord({}, 0); //add a blank record in the first position
        this.getSelectionModel().selectFirstRow();
        this.startEditing(0, this.firstEditableColumn);
    },

    onDeleteRecords : function() {
        var records = this.getSelectionModel().getSelections();
        if (records && records.length)
        {
            if(this.fireEvent("beforedelete", {records: records}))
            {
                Ext.Msg.show({
                    title: "Confirm Delete",
                    msg: records.length > 1
                            ? "Are you sure you want to delete the "
                                + records.length + " selected records? This cannot be undone."
                            : "Are you sure you want to delete the selected record? This cannot be undone.",
                    icon: Ext.MessageBox.QUESTION,
                    buttons: {ok: "Delete", cancel: "Cancel"},
                    scope: this,
                    fn: function(buttonId) {
                        if(buttonId == "ok")
                            this.store.deleteRecords(records);
                    }
                });
            }
        }
    },

    onRowSelect : function(selmodel, rowIndex) {
        if(this.autoSave)
            this.saveChanges();
    },

    onBeforeEdit : function(evt) {
        if(this.getStore().isUpdateInProgress(evt.record))
            return false;

        if(!this.getSelectionModel().isSelected(evt.row))
            this.getSelectionModel().selectRow(evt.row);
    },

    onEnter : function() {
        this.stopEditing();

        //move selection down to the next row, or commit if on last row
        var selmodel = this.getSelectionModel();
        if(selmodel.hasNext())
            selmodel.selectNext();
        else if(this.autoSave)
            this.saveChanges();
    },

    onEsc : function() {
        //if the currently selected record is dirty,
        //reject the edits
        var record = this.getSelectionModel().getSelected();
        if(record && record.dirty)
        {
            if(record.isNew)
                this.getStore().remove(record);
            else
                record.reject();
        }
    },

    onTab : function() {
        if(this.autoSave)
            this.saveChanges();
    }
});
