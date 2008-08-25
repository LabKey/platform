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
 * @param {boolean} [config.enableFilters] True to enable filtering of columns (default is false)
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
            enableFilters: false,
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
    },

    initFilterMenu : function()
    {
        var filterItem = new Ext.menu.Item({text:"Filter...", scope:this, handler:function() {this.handleFilter()}});
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
        }

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
                valueEditors[index].setVisible(filter != null && filter.isDataValueRequired())
            })

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
            new Ext.form.TextField({value:colFilters.length > 1 ? colFilters[1].getValue() : "",width:250, hidden:colFilters.length < 2, hideMode:'visibility'})]


        function validateEntry(index)
        {
            var filterType = findFilterType(index);
            if (!filterType.isDataValueRequired())
                return true;

            if (filterType == ft.IN)
                return validateMultiple(valueEditors[index].getValue());
            else
                return validate(valueEditors[index].getValue())
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
                    return undefined
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
                    handler:function() {win.close()}
                },
                {
                    text:"Clear Filter",
                    handler:function() {grid.setColumnFilters(colName, []); win.close()}
                },
                {
                    text:"Clear All Filters",
                    handler:function() {grid.getStore().setUserFilters([]); grid.getStore().load({params:{start:0, limit:grid.pageSize}}); win.close()}
                }
            ]
        });
        win.show();
        //Focus doesn't work right away (who knows why?) so defer it...
        function f() {valueEditors[0].focus()};
        f.defer(100);
    },

    getColumnFilters: function(colName)
    {
        var colFilters = [];
        Ext.each(this.getStore().getUserFilters(), function(filter) {
            if (filter.getColumnName() == colName)
                colFilters.push(filter)
        });
        return colFilters;
    },

    setColumnFilters: function(colName, filters)
    {
        var newFilters = [];
        Ext.each(this.getStore().getUserFilters(), function(filter) {
            if (filter.getColumnName() != colName)
                newFilters.push(filter)
        });
        if (filters)
            Ext.each(filters, function(filter) {newFilters.push(filter)});

        this.getStore().setUserFilters(newFilters);
        this.getStore().load({params:{start:0, limit:this.pageSize}});
    }
});
