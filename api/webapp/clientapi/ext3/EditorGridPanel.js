/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2017 LabKey Corporation
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

/**
 * Constructs a new LabKey EditorGridPanel using the supplied configuration.
 * @class <p><font color="red">DEPRECATED</font> - Consider using
 * <a href="http://docs.sencha.com/extjs/3.4.0/#!/api/Ext.grid.EditorGridPanel">Ext.grid.EditorGridPanel</a> instead.</p>
 * <p>LabKey extension to the <a href="http://www.extjs.com/deploy/dev/docs/?class=Ext.grid.EditorGridPanel">Ext.grid.EditorGridPanel</a>,
 * which can provide editable grid views of data in the LabKey server. If the current user has appropriate permissions,
 * the user may edit data, save changes, insert new rows, or delete rows.</p>
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">Tutorial: Create Applications with the JavaScript API</a></li>
 *              </ul>      
 *           </p>
 * @constructor
 * @augments Ext.grid.EditorGridPanel
 * @param config Configuration properties. This may contain any of the configuration properties supported
 * by the <a href="http://www.extjs.com/deploy/dev/docs/?class=Ext.grid.EditorGridPanel">Ext.grid.EditorGridPanel</a>,
 * plus those listed here.
 * @param {boolean} [config.lookups] Set to false if you do not want lookup foreign keys to be resolved to the
 * lookup values, and do not want dropdown lookup value pickers (default is true).
 * @param {integer} [config.pageSize] Defines how many rows are shown at a time in the grid (default is 20).
 * If the EditorGridPanel is getting its data from Ext.Store, pageSize will override the value of maxRows on Ext.Store.
 * @param {boolean} [config.editable] Set to true if you want the user to be able to edit, insert, or delete rows (default is false).
 * @param {boolean} [config.autoSave] Set to false if you do not want changes automatically saved when the user leaves the row (default is true).
 * @param {boolean} [config.enableFilters] True to enable user-filtering of columns (default is false)
 * @param {string} [config.loadingCaption] The string to display in a cell when loading the lookup values (default is "[loading...]").
 * @param {string} [config.lookupNullCaption] The string to display for a null value in a lookup column (default is "[none]").
 * @param {boolean} [config.showExportButton] Set to false to hide the Export button in the toolbar. True by default.
 * @example Basic Example: <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
    var _grid;

    //Use the Ext.onReady() function to define what code should
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
&lt;div id='grid'/&gt; </pre>
 * @example Advanced Example:
 *
This snippet shows how to link a column in an EditorGridPanel to a details/update
page.  It adds a custom column renderer to the grid column model by hooking
the 'columnmodelcustomize' event.  Since the column is a lookup, it is helpful to
chain the base renderer so that it does the lookup magic for you.  <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
var _materialTemplate;
var _baseFormulationRenderer;

 function formulationRenderer(data, cellMetaData, record, rowIndex, colIndex, store)
{
    return _materialTemplate.apply(record.data) + _baseFormulationRenderer(data,
        cellMetaData, record, rowIndex, colIndex, store) + '&lt;/a&gt;';
}

function customizeColumnModel(colModel, index)
{
    if (colModel != undefined)
    {
        var col = index['Formulation'];
        var url = LABKEY.ActionURL.buildURL("experiment", "showMaterial");

        _materialTemplate = new Ext.XTemplate('&lt;a href="' + url +
            '?rowId={Formulation}"&gt;').compile();
        _baseFormulationRenderer = col.renderer;
        col.renderer = formulationRenderer;
    }
}

Ext.onReady(function(){
    _grid = new LABKEY.ext.EditorGridPanel({
        store: new LABKEY.ext.Store({
            schemaName: 'lists',
            queryName: 'FormulationExpMap'
        }),
        renderTo: 'gridDiv',
        width: 600,
        autoHeight: true,
        title: 'Formulations to Experiments',
        editable: true
    });
    _grid.on("columnmodelcustomize", customizeColumnModel);
});
&lt;/script&gt;</pre>
 */
LABKEY.ext.EditorGridPanel = Ext.extend(Ext.grid.EditorGridPanel, {
    initComponent : function() {

        Ext.QuickTips.init();
        Ext.apply(Ext.QuickTips.getQuickTip(), {
            dismissDelay: 15000
        });

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
            selModel: new Ext.grid.CheckboxSelectionModel({moveEditorOnEnter: false}),
            showExportButton: true
        });
        this.setupDefaultPanelConfig();

        LABKEY.ext.EditorGridPanel.superclass.initComponent.apply(this, arguments);

        /**
         * @memberOf LABKEY.ext.EditorGridPanel#
         * @name columnmodelcustomize
         * @event
         * @description Use this event to customize the default column model config generated by the server.
         * For details on the column model config, see the Ext API documentation for Ext.grid.ColumnModel
         * (http://www.extjs.com/deploy/dev/docs/?class=Ext.grid.ColumnModel)
         * @param {Ext.grid.ColumnModel} columnModel The default ColumnModel config generated by the server.
         * @param {Object} index An index map where the key is column name and the value is the entry in the column
         * model config for that column. Since the column model config is a simple array of objects, this index helps
         * you get to the specific columns you need to modify without doing a sequential scan.
         */
        /**
         * @memberOf LABKEY.ext.EditorGridPanel#
         * @name beforedelete
         * @event
         * @description Use this event to cancel the deletion of a row in the grid. If you return false
         * from this event, the row will not be deleted
         * @param {array} records An array of Ext.data.Record objects that the user wishes to delete
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
                    limit: this.pageSize,
                    'X-LABKEY-CSRF': LABKEY.CSRF
                }});
        }
    },

    /**
     * Returns the LABKEY.ext.Store object used to hold the
     * lookup values for the specified column name. If the column
     * name is not a lookup column, this method will return null.
     * @name getLookupStore
     * @function
     * @memberOf LABKEY.ext.EditorGridPanel#
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
     * @memberOf LABKEY.ext.EditorGridPanel#
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

            if (this.showExportButton)
            {
                this.tbar.push("-");
                this.tbar.push({
                    text: 'Export',
                    tooltip: 'Click to Export the data to Excel',
                    id: 'export-records-button',
                    handler: function(){
                        if (this.store)
                            this.store.exportData("excel");
                    },
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
        if (!msg && response.responseText)
        {
            try
            {
                var json = Ext.util.JSON.decode(response.responseText);
                if (json)
                    msg = json.exception;
            }
            catch (err)
            {}
        }
        if (!msg)
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
//        this.getView().hmenu.getEl().addClass("extContainer");
//        this.getView().colMenu.getEl().addClass("extContainer");

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
            meta = this.metaMap[col.dataIndex];

            //this.editable can override col.editable
            col.editable = this.editable && col.editable;

            //if column type is boolean, substitute an Ext.grid.CheckColumn
            if(meta.type == "boolean" || meta.type == "bool")
            {
                col = this.columns[idx] = new Ext.grid.CheckColumn(col);
                if(col.editable)
                    col.init(this);
                col.editable = false; //check columns apply edits immediately, so we don't want to go into edit mode
            }

            if(meta.hidden || meta.isHidden)
                col.hidden = true; 

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

            colModelIndex[col.dataIndex] = col;
        }

        //if a sel model has been set, and if it needs to be added as a column,
        //add it to the front of the list.
        //CheckBoxSelectionModel needs to be added to the column model for
        //the check boxes to show up.
        //(not sure why its constructor doesn't do this automatically).
        if(this.getSelectionModel() && this.getSelectionModel().renderer)
            this.columns = [this.getSelectionModel()].concat(this.columns);

        //register for the rowdeselect event if the selmodel supports events
        //and if autoSave is on
        if(this.getSelectionModel().on && this.autoSave)
            this.getSelectionModel().on("rowselect", this.onRowSelect, this);

        //add custom renderers for multiline/long-text columns
        this.setLongTextRenderers();

        //fire the "columnmodelcustomize" event to allow clients
        //to modify our default configuration of the column model
        this.fireEvent("columnmodelcustomize", this.columns, colModelIndex);

        //reset the column model
        this.reconfigure(this.store, new Ext.grid.ColumnModel(this.columns));
    },

    getDefaultRenderer : function(col, meta) {
        if(meta.lookup && this.lookups && col.editable) //no need to use a lookup renderer if column is not editable
            return this.getLookupRenderer(col, meta);

        return function(data, cellMetaData, record, rowIndex, colIndex, store)
        {
            if(record.json && record.json[meta.name] && record.json[meta.name].mvValue)
            {
                var mvValue = record.json[meta.name].mvValue;
                //get corresponding message from qcInfo section of JSON and set up a qtip
                if(store.reader.jsonData.qcInfo && store.reader.jsonData.qcInfo[mvValue])
                {
                    cellMetaData.attr = "ext:qtip=\"" + Ext.util.Format.htmlEncode(store.reader.jsonData.qcInfo[mvValue]) + "\"";
                    cellMetaData.css = "labkey-mv";
                }
                return mvValue;
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

    onLookupStoreError : function(proxy, type, action, options, response)
    {
        var message = "";
        if (type == 'response')
        {
            var ctype = response.getResponseHeader("Content-Type");
            if(ctype.indexOf("application/json") >= 0)
            {
                var errorJson = Ext.util.JSON.decode(response.responseText);
                if(errorJson && errorJson.exception)
                    message = errorJson.exception;
            }
        }
        else
        {
            if (response && response.exception)
            {
                message = response.exception;
            }
        }
        Ext.Msg.alert("Load Error", "Error loading lookup data");

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
                                'Y-n-d H:i:s|Y-n-d|' +
                                'j M Y H:i:s' // 10 Sep 2009 01:24:12
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
//        hmenu.getEl().addClass("extContainer");
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

        var colFilters = this.getColumnFilters(colName);
        var dropDowns = [
            LABKEY.ext.EditorGridPanel.createFilterCombo(filterColType, colFilters.length >= 1 ? colFilters[0].getFilterType().getURLSuffix() : null, true),
            LABKEY.ext.EditorGridPanel.createFilterCombo(filterColType, colFilters.length >= 2 ? colFilters[1].getFilterType().getURLSuffix() : null)];
        var valueEditors = [
            new Ext.form.TextField({value:colFilters.length > 0 ? colFilters[0].getValue() : "",width:250}),
            new Ext.form.TextField({value:colFilters.length > 1 ? colFilters[1].getValue() : "",width:250, hidden:colFilters.length < 2, hideMode:'visibility'})];

        dropDowns[0].valueEditor = valueEditors[0];
        dropDowns[1].valueEditor = valueEditors[1];

        function validateEntry(index)
        {
            var filterType = dropDowns[index].getFilterType();
            return filterType.validate(valueEditors[index].getValue(), filterColType, colName);
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

                        var filterType = dropDowns[0].getFilterType();
                        filters.push(LABKEY.Filter.create(filterColName, value, filterType));
                        filterType = dropDowns[1].getFilterType();
                        if (filterType && filterType.getURLSuffix().length > 0)
                        {
                            value = validateEntry(1);
                            if (!value)
                                return;
                            filters.push(LABKEY.Filter.create(filterColName, value, filterType));
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

LABKEY.ext.EditorGridPanel.createFilterCombo = function (type, filterOp, first)
{
    var ft = LABKEY.Filter.Types;
    var defaultFilterTypes = {
        "int":ft.EQUAL, "string":ft.STARTS_WITH, "boolean":ft.EQUAL, "float":ft.GTE,  "date":ft.DATE_EQUAL
    };

    //Option lists for drop-downs. Filled in on-demand based on filter type
    var dropDownOptions = [];
    Ext.each(LABKEY.Filter.getFilterTypesForType(type), function (filterType) {
        dropDownOptions.push([filterType.getURLSuffix(), filterType.getDisplayText()]);
    });

    //Do the ext magic for the options. Gets easier in ext 2.2
    var options = (!first) ? [['', 'no other filter']].concat(dropDownOptions) : dropDownOptions;
    var store = new Ext.data.SimpleStore({'id': 0, fields: ['value', 'text'], data: options });
    var combo = new Ext.form.ComboBox({
        store:store,
        forceSelection:true,
        valueField:'value',
        displayField:'text',
        mode:'local',
        allowBlank:false,
        triggerAction:'all',
        value:filterOp ? filterOp : ((!first) ? '' : defaultFilterTypes[type].getURLSuffix())
    });
    combo.on("select", function(combo, record, itemNo) {
        var filter = this.getFilterType();
        if (this.valueEditor)
            this.valueEditor.setVisible(filter != null && filter.isDataValueRequired());
    });

    combo.getFilterType = function () {
        return LABKEY.Filter.getFilterTypeForURLSuffix(this.getValue());
    };

    return combo;
};


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
        if(grid.getView() && grid.getView().mainBody)
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
