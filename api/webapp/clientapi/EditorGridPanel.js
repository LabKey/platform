/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.3
 * @license Copyright (c) 2008-2009 LabKey Corporation
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
});
/**
 * Constructs a new LabKey EditorGridPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.grid.EditorGridPanel">Ext.grid.EditorGridPanel</a>,
 * which can provide editable grid views
 * of data in the LabKey server. If the current user has appropriate permissions, the user may edit
 * data, save changes, insert new rows, or delete rows.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
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
 * @param {boolean} [config.enableFilters] True to enable user-filtering of columns (default is false)
 * @param {string} [config.loadingCaption] The string to display in a cell when loading the lookup values (default is "[loading...]").
 * @param {string} [config.lookupNullCaption] The string to display for a null value in a lookup column (default is "[none]").
 * @example &lt;script type="text/javascript"&gt;
    var _grid;

    //use the Ext.onReady() function to define what code should
    //be executed once the page is fully loaded.
    //you must use this if you supply a renderTo config property
    Ext.onReady(function(){
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
    initComponent : function() {
        //set config defaults
        Ext.applyIf(this, {
            lookups: true,
            pageSize: 20,
            editable: false,
            enableFilters: false,
            autoSave: true,
            loadingCaption: "[loading...]",
            lookupNullCaption: "[none]",
            viewConfig: {forceFit: true},
            id: Ext.id(undefined, "labkey-ext-grid"),
            loadMask: true,
            colModel: new Ext.grid.ColumnModel([]),
            selModel: new Ext.grid.CheckboxSelectionModel({moveEditorOnEnter: false})
        });
        this.setupDefaultPanelConfig();

        LABKEY.ext.EditorGridPanel.superclass.initComponent.apply(this, arguments);

        /**
         * @memberOf LABKEY.ext.EditorGridPanel
         * @event columnmodelcustomize Use this event to customize the column model
         */
        this.addEvents("beforedelete, columnmodelcustomize");

        //subscribe to superclass events
        this.on("beforeedit", this.onBeforeEdit, this);
        this.on("render", this.onGridRender, this);

        //subscribe to store events and start loading it
        if(this.store)
        {
            this.store.on("loadexception", this.onStoreLoadException, this);
            this.store.on("load", this.onStoreLoad, this);
            this.store.on("beforecommit", this.onStoreBeforeCommit, this);
            this.store.on("commitcomplete", this.onStoreCommitComplete, this);
            this.store.on("commitexception", this.onStoreCommitException, this);
            this.store.load({ params : {
                    start: 0,
                    limit: this.pageSize
                }});
        }
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

    setupDefaultPanelConfig : function() {
        if(!this.tbar)
        {
            this.tbar = [{
                text: 'Refresh',
                tooltip: 'Click to refresh the table',
                id: 'refresh-button',
                handler: this.onRefresh,
                scope: this
            }];

            if(this.editable && LABKEY.user && LABKEY.user.canUpdate && !this.autoSave)
            {
                this.tbar.push("-");
                this.tbar.push({
                    text: 'Save Changes',
                    tooltip: 'Click to save all changes to the database',
                    id: 'save-button',
                    handler: this.saveChanges,
                    scope: this
                });
            }

            if(this.editable &&LABKEY.user && LABKEY.user.canInsert)
            {
                this.tbar.push("-");
                this.tbar.push({
                    text: 'Add Record',
                    tooltip: 'Click to add a row',
                    id: 'add-record-button',
                    handler: this.onAddRecord,
                    scope: this
                });
            }
            if(this.editable &&LABKEY.user && LABKEY.user.canDelete)
            {
                this.tbar.push("-");
                this.tbar.push({
                    text: 'Delete Selected',
                    tooltip: 'Click to delete selected row(s)',
                    id: 'delete-records-button',
                    handler: this.onDeleteRecords,
                    scope: this
                });
            }
        }

        if(!this.bbar)
        {
            this.bbar = new Ext.PagingToolbar({
                    pageSize: this.pageSize, //default is 20
                    store: this.store,
                    displayInfo: true,
                    emptyMsg: "No data to display" //display message when no records found
                });
        }

        if(!this.keys)
        {
            this.keys = [
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
                },
                {
                    key: Ext.EventObject.F2,
                    handler: this.onF2,
                    scope: this
                }
            ];
        }
    },

    onStoreLoad : function(store, records, options) {
        this.store.un("load", this.onStoreLoad, this);

        this.populateMetaMap();
        this.setupColumnModel();
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

    onStoreBeforeCommit : function(records, rows) {
        //disable the refresh button so that it will animate
        var pagingBar = this.getBottomToolbar();
        if(pagingBar && pagingBar.loading)
            pagingBar.loading.disable();
        if(!this.savingMessage)
            this.savingMessage = pagingBar.addText("Saving Changes...");
        else
            this.savingMessage.setVisible(true);
    },

    onStoreCommitComplete : function() {
        var pagingBar = this.getBottomToolbar();
        if(pagingBar && pagingBar.loading)
            pagingBar.loading.enable();
        if(this.savingMessage)
            this.savingMessage.setVisible(false);
    },

    onStoreCommitException : function(message) {
        var pagingBar = this.getBottomToolbar();
        if(pagingBar && pagingBar.loading)
            pagingBar.loading.enable();
        if(this.savingMessage)
            this.savingMessage.setVisible(false);
    },

    onGridRender : function() {
        //add the extContainer class to the view's hmenu
        //NOTE: there is no public API to get to hmenu and colMenu
        //so this might break in future versions of Ext. If you get
        //a JavaScript error on these lines, look at the API docs for
        //a method or property that returns the sort and column hide/show
        //menus shown from the column headers
        this.getView().hmenu.getEl().addClass("extContainer");
        this.getView().colMenu.getEl().addClass("extContainer");

        //set up filtering
        if (this.enableFilters)
            this.initFilterMenu();

    },

    populateMetaMap : function() {
        //the metaMap is a map from field name to meta data about the field
        //the meta data contains the following properties:
        // id, totalProperty, root, fields[]
        // fields[] is an array of objects with the following properties
        // name, type, lookup
        // lookup is a nested object with the following properties
        // schema, table, keyColumn, displayColumn
        this.metaMap = {};
        var fields = this.store.reader.jsonData.metaData.fields;
        for(var idx = 0; idx < fields.length; ++idx)
        {
            var field = fields[idx];
            this.metaMap[field.name] = field;
        }
    },

    setupColumnModel : function() {

        //set the columns property to the columnModel returned in the jsonData
        this.columns = this.store.reader.jsonData.columnModel;

        //set the renderers and editors for the various columns
        //build a column model index as we run the columns for the
        //customize event
        var colModelIndex = {};
        var col;
        var meta;
        for(var idx = 0; idx < this.columns.length; ++idx)
        {
            col = this.columns[idx];
            colModelIndex[col.dataIndex] = col;
            meta = this.metaMap[col.dataIndex];

            //this.editable can override col.editable
            col.editable = this.editable && col.editable;

            //if column type is boolean, substitute an Ext.grid.CheckColumn
            if(meta.type == "boolean")
            {
                col = this.columns[idx] = new Ext.grid.CheckColumn(col);
                if(col.editable)
                    col.init(this);
                col.editable = false; //check columns apply edits immediately, so we don't want to go into edit mode
            }

            if(col.editable && !col.editor)
                col.editor = this.getDefaultEditor(col, meta);
            if(!col.renderer)
                col.renderer = this.getDefaultRenderer(col, meta);

            //remember the first editable column (used during add record)
            if(!this.firstEditableColumn && col.editable)
                this.firstEditableColumn = idx;

            //HTML-encode the column header
            if(col.header)
                col.header = Ext.util.Format.htmlEncode(col.header);
        }

        //if a sel model has been set, and if it needs to be added as a column,
        //add it to the front of the list.
        //CheckBoxSelectionModel needs to be added to the selection model for
        //the check boxes to show up.
        //(not sure why its constructor doesn't do this autmatically).
        if(this.getSelectionModel() && this.getSelectionModel().renderer)
            this.columns = [this.getSelectionModel()].concat(this.columns);

        //register for the rowdeselect event if the selmodel supports events
        //and if autoSave is on
        if(this.getSelectionModel().on && this.autoSave)
            this.getSelectionModel().on("rowselect", this.onRowSelect, this);

        //fire the "columnmodelcustomize" event to allow clients
        //to modify our default configuration of the column model
        this.fireEvent("columnmodelcustomize", this.columns, colModelIndex);

        //add custom renderers for multiline/long-text columns
        this.setLongTextRenderers();

        //reset the column model
        this.reconfigure(this.store, new Ext.grid.ColumnModel(this.columns));
    },

    getDefaultRenderer : function(col, meta) {
        if(meta.lookup && this.lookups)
            return this.getLookupRenderer(col, meta);

        return function(data, cellMetaData, record, rowIndex, colIndex, store)
        {
            if(record.json && record.json[meta.name] && record.json[meta.name].qcValue)
            {
                var qcValue = record.json[meta.name].qcValue;
                //get corresponding message from qcInfo section of JSON and set up a qtip
                if(store.reader.jsonData.qcInfo && store.reader.jsonData.qcInfo[qcValue])
                {
                    cellMetaData.attr = "ext:qtip=\"" + Ext.util.Format.htmlEncode(store.reader.jsonData.qcInfo[qcValue]) + "\"";
                    cellMetaData.css = "labkey-mv";
                }
                return qcValue;
            }

            if(record.json && record.json[meta.name] && record.json[meta.name].displayValue)
                return record.json[meta.name].displayValue;
            
            if(null == data || undefined == data || data.toString().length == 0)
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
                        displayValue = date.format("Y-m-d H:i:s");
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

            //wrap in <a> if url is present in the record's original JSON
            if(col.showLink !== false && record.json && record.json[meta.name] && record.json[meta.name].url)
                return "<a href=\"" + record.json[meta.name].url + "\">" + displayValue + "</a>";
            else
                return displayValue;
        };
    },

    getLookupRenderer : function(col, meta) {
        var lookupStore = this.store.getLookupStore(meta.name, !col.required);
        lookupStore.on("loadexception", this.onLookupStoreError, this);
        lookupStore.on("load", this.onLookupStoreLoad, this);

        return function(data, cellMetaData, record, rowIndex, colIndex, store)
        {
            if(record.json && record.json[meta.name] && record.json[meta.name].displayValue)
                return record.json[meta.name].displayValue;
            
            if(null == data || undefined == data || data.toString().length == 0)
                return data;

            if(lookupStore.loadError)
                return "ERROR: " + lookupStore.loadError.message;

            if(0 === lookupStore.getCount() && !lookupStore.isLoading)
            {
                lookupStore.load();
                return "loading...";
            }

            var lookupRecord = lookupStore.getById(data);
            if (lookupRecord)
                return lookupRecord.data[meta.lookup.displayColumn];
            else if (data)
                return "[" + data + "]";
            else
                return this.lookupNullCaption || "[none]";
        };
    },

    onLookupStoreLoad : function(store, records, options) {
        if(this.view && !this.activeEditor)
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

    getDefaultEditor : function(col, meta) {
        var editor;

        //if this column is a lookup, return the lookup editor
        if(meta.lookup && this.lookups)
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
                //HACK: the DateMenu is created by the DateField
                //and there's no config on DateField that lets you specify
                //a CSS class to add to the DateMenu. If we create it now,
                //their code will just use the one we create.
                //See DateField.js in the Ext source
                editor.menu = new Ext.menu.DateMenu({cls: 'extContainer'});
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
            editable: false,
            displayField: meta.lookup.displayColumn,
            valueField: meta.lookup.keyColumn,
            tpl : '<tpl for="."><div class="x-combo-list-item">{[values["' + meta.lookup.displayColumn + '"]]}</div></tpl>', //FIX: 5860
            listClass: 'labkey-grid-editor'
        });
    },

    setLongTextRenderers : function() {
        var col;
        for(var idx = 0; idx < this.columns.length; ++idx)
        {
            col = this.columns[idx];
            if(col.multiline || (undefined === col.multiline && col.scale > 255 && this.metaMap[col.dataIndex].type === "string"))
            {
                col.renderer = function(data, metadata, record, rowIndex, colIndex, store)
                {
                    //set quick-tip attributes and let Ext QuickTips do the work
                    metadata.attr = "ext:qtip=\"" + Ext.util.Format.htmlEncode(data) + "\"";
                    return data;
                };

                if(col.editable)
                    col.editor = new LABKEY.ext.LongTextField({
                        columnName: col.dataIndex
                    });
            }
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

        var editor = this.getColumnModel().getCellEditor(evt.column, evt.row);
        var displayValue = (evt.record.json && evt.record.json[evt.field]) ? evt.record.json[evt.field].displayValue : undefined;

        //set the value not found text to be the display value if there is one
        if(editor && editor.field && editor.field.displayField && displayValue)
            editor.field.valueNotFoundText = displayValue;

        //reset combo mode to local if the lookup store is already populated
        if(editor && editor.field && editor.field.displayField && editor.field.store && editor.field.store.getCount() > 0)
            editor.field.mode = "local";
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
    },

    onF2 : function() {
        var record = this.getSelectionModel().getSelected();
        if(record)
        {
            var index = this.getStore().findBy(function(recordComp, id){return id == record.id;});
            if(index >= 0 && undefined !== this.firstEditableColumn)
                this.startEditing(index, this.firstEditableColumn);
        }

    },

    initFilterMenu : function()
    {
        var filterItem = new Ext.menu.Item({text:"Filter...", scope:this, handler:function() {this.handleFilter();}});
        var hmenu = this.getView().hmenu;
        hmenu.getEl().addClass("extContainer");
        hmenu.addItem(filterItem);
    },

    handleFilter :function ()
    {
        var view = this.getView();
        var col = view.cm.config[view.hdCtxIndex];

        this.showFilterWindow(col);
    },

    showFilterWindow: function(col)
    {
        var colName = col.dataIndex;
        var meta = this.getStore().findFieldMeta(colName);
        var grid = this; //Stash for later use in callbacks.

        var filterColName = meta.lookup ? colName + "/" + meta.lookup.displayColumn : colName;
        var filterColType;
        if (meta.lookup)
        {
            var lookupStore = this.store.getLookupStore(filterColName);
            if (null != lookupStore)
            {
                meta = lookupStore.findFieldMeta(meta.lookup.displayColumn);
                filterColType = meta ? meta.type : "string";
            }
            else
                filterColType = "string";
        }
        else
            filterColType = meta.type;

        var ft = LABKEY.Filter.Types;
        var filterTypes = {
            "int":[ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN],
            "string":[ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.CONTAINS, ft.DOES_NOT_CONTAIN, ft.DOES_NOT_START_WITH, ft.STARTS_WITH, ft.IN],
            "boolean":[ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK],
            "float":[ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN],
            "date":[ft.DATE_EQUAL, ft.DATE_NOT_EQUAL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN]
        };
        var defaultFilterTypes = {
            "int":ft.EQUAL, "string":ft.STARTS_WITH, "boolean":ft.EQUAL, "float":ft.GTE,  "date":ft.DATE_EQUAL
        };

        //Option lists for drop-downs. Filled in on-demand based on filter type
        var dropDownOptions = [];
        var colFilters = this.getColumnFilters(colName);
        function createFilterDropDown(index, dataType, curFilter)
        {
            //Do the ext magic for the options. Gets easier in ext 2.2
            if (dropDownOptions.length == 0)
                Ext.each(filterTypes[dataType], function (filterType) {
                    dropDownOptions.push([filterType.getURLSuffix(), filterType.getDisplayText()]);
                });
            var options = (index > 0) ? [['', 'no other filter']].concat(dropDownOptions) : dropDownOptions;
            var store = new Ext.data.SimpleStore({'id': 0, fields: ['value', 'text'], data :options });
            var combo = new Ext.form.ComboBox({
                store:store,
                forceSelection:true,
                valueField:'value',
                displayField:'text',
                mode:'local',
                allowBlank:false,
                triggerAction:'all',
                value:curFilter ? curFilter.getFilterType().getURLSuffix() : ((index > 0) ? '' : defaultFilterTypes[dataType].getURLSuffix())
            });
            combo.on("select", function(combo, record, itemNo) {
                var filter = findFilterType(index);
                valueEditors[index].setVisible(filter != null && filter.isDataValueRequired());
            });

            return combo;
        }


        function findFilterType(index)
        {
            var abbrev = dropDowns[index].getValue();
            for (var key in ft)
                if (ft[key].getURLSuffix && ft[key].getURLSuffix() == abbrev)
                    return ft[key];

            return null;
        }

        var dropDowns = [createFilterDropDown(0, filterColType, colFilters.length >= 1 ? colFilters[0] : null), createFilterDropDown(1, filterColType, colFilters.length >= 2 ? colFilters[1] : null)];
        var valueEditors = [
            new Ext.form.TextField({value:colFilters.length > 0 ? colFilters[0].getValue() : "",width:250}),
            new Ext.form.TextField({value:colFilters.length > 1 ? colFilters[1].getValue() : "",width:250, hidden:colFilters.length < 2, hideMode:'visibility'})];


        function validateEntry(index)
        {
            var filterType = findFilterType(index);
            if (!filterType.isDataValueRequired())
                return true;

            if (filterType == ft.IN)
                return validateMultiple(valueEditors[index].getValue());
            else
                return validate(valueEditors[index].getValue());
        }

        function validateMultiple(allValues, mappedType, fieldName)
        {
            var values = allValues.split(";");
            var result = '';
            var separator = '';
            for (var i = 0; i < values.length; i++)
            {
                var value = validate(values[i].trim(), mappedType, fieldName);
                if (value == undefined)
                    return undefined;

                result = result + separator + value;
                separator = ";";
            }
            return result;
        }

        function validate(value)
        {
            if (filterColType == "int")
            {
                var intVal = parseInt(value);
                if (isNaN(intVal))
                {
                    alert(value + " is not a valid integer for field '" + colName + "'.");
                    return undefined;
                }
                else
                    return "" + intVal;
            }
            else if (filterColType == "float")
            {
                var decVal = parseFloat(value);
                if (isNaN(decVal))
                {
                    alert(value + " is not a valid decimal number for field '" + colName + "'.");
                    return undefined;
                }
                else
                    return "" + decVal;
            }
            else if (filterColType == "date")
            {
                var year, month, day, hour, minute;
                hour = 0;
                minute = 0;

                //Javascript does not parse ISO dates, but if date matches we're done
                if (value.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*$/) ||
                    value.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*(\d\d):(\d\d)\s*$/))
                {
                    return value;
                }
                else
                {
                    var dateVal = new Date(value);
                    if (isNaN(dateVal))
                    {
                        alert(value + " is not a valid date for field '" + colName + "'.");
                        return undefined;
                    }
                    //Try to do something decent with 2 digit years!
                    //if we have mm/dd/yy (but not mm/dd/yyyy) in the date
                    //fix the broken date parsing
                    if (value.match(/\d+\/\d+\/\d{2}(\D|$)/))
                    {
                        if (dateVal.getFullYear() < new Date().getFullYear() - 80)
                            dateVal.setFullYear(dateVal.getFullYear() + 100);
                    }
                    year = dateVal.getFullYear();
                    month = dateVal.getMonth() + 1;
                    day = dateVal.getDate();
                    hour = dateVal.getHours();
                    minute = dateVal.getMinutes();
                }
                var str = "" + year + "-" + twoDigit(month) + "-" + twoDigit(day);
                if (hour != 0 || minute != 0)
                    str += " " + twoDigit(hour) + ":" + twoDigit(minute);

                return str;
            }
            else if (filterColType == "boolean")
            {
                var upperVal = value.toUpperCase();
                if (upperVal == "TRUE" || value == "1" || upperVal == "Y" || upperVal == "ON" || upperVal == "T")
                    return "1";
                if (upperVal == "FALSE" || value == "0" || upperVal == "N" || upperVal == "OFF" || upperVal == "F")
                    return "0";
                else
                {
                    alert(value + " is not a valid boolean for field '" + colName + "'. Try true,false; yes,no; on,off; or 1,0.");
                    return undefined;
                }
            }
            else
                return value;
        }

        function twoDigit(num)
        {
            if (num < 10)
                return "0" + num;
            else
                return "" + num;
        }

        var win = new Ext.Window({
            title:"Show Rows Where " + colName,
            width:400,
            autoHeight:true,
            modal:true,
            items:[dropDowns[0], valueEditors[0], new Ext.form.Label({text:" and"}),
                    dropDowns[1], valueEditors[1]],
            //layout:'column',
            buttons:[
                {
                    text:"OK",
                    handler:function() {
                        var filters = [];
                        var value;
                        value = validateEntry(0);
                        if (!value)
                            return;

                        var filterType = findFilterType(0);
                        filters.push(LABKEY.Filter.create(filterColName, value, filterType));
                        filterType = findFilterType(1);
                        if (filterType)
                        {
                            value = validateEntry(1);
                            if (!value)
                                return;
                            filters.push(LABKEY.Filter.create(filterColName, ""+tf.getValue(), filterType));
                        }
                        grid.setColumnFilters(colName, filters);
                        win.close();
                    }
                },
                {
                    text:"Cancel",
                    handler:function() {win.close();}
                },
                {
                    text:"Clear Filter",
                    handler:function() {grid.setColumnFilters(colName, []); win.close();}
                },
                {
                    text:"Clear All Filters",
                    handler:function() {grid.getStore().setUserFilters([]); grid.getStore().load({params:{start:0, limit:grid.pageSize}}); win.close()}
                }
            ]
        });
        win.show();
        //Focus doesn't work right away (who knows why?) so defer it...
        function f() {valueEditors[0].focus();};
        f.defer(100);
    },

    getColumnFilters: function(colName)
    {
        var colFilters = [];
        Ext.each(this.getStore().getUserFilters(), function(filter) {
            if (filter.getColumnName() == colName)
                colFilters.push(filter);
        });
        return colFilters;
    },

    setColumnFilters: function(colName, filters)
    {
        var newFilters = [];
        Ext.each(this.getStore().getUserFilters(), function(filter) {
            if (filter.getColumnName() != colName)
                newFilters.push(filter);
        });
        if (filters)
            Ext.each(filters, function(filter) {newFilters.push(filter);});

        this.getStore().setUserFilters(newFilters);
        this.getStore().load({params:{start:0, limit:this.pageSize}});
    }
});

// Check column plugin
Ext.grid.CheckColumn = function(config){
    Ext.apply(this, config);
    if(!this.id){
        this.id = Ext.id();
    }
    this.renderer = this.renderer.createDelegate(this);
};

Ext.grid.CheckColumn.prototype ={
    init : function(grid){
        this.grid = grid;
        if(grid.getView())
        {
            grid.getView().mainBody.on('mousedown', this.onMouseDown, this);
        }
        else
        {
            this.grid.on('render', function(){
                var view = this.grid.getView();
                view.mainBody.on('mousedown', this.onMouseDown, this);
            }, this);
        }
    },

    onMouseDown : function(e, t){
        if(t.className && t.className.indexOf('x-grid3-cc-'+this.id) != -1){
            e.stopEvent();
            var index = this.grid.getView().findRowIndex(t);
            var record = this.grid.store.getAt(index);
            this.grid.getSelectionModel().selectRow(index);
            record.set(this.dataIndex, !record.data[this.dataIndex]);
        }
    },

    renderer : function(v, p, record){
        p.css += ' x-grid3-check-col-td';
        return '<div class="x-grid3-check-col'+(v?'-on':'')+' x-grid3-cc-'+this.id+'">&#160;</div>';
    }
};