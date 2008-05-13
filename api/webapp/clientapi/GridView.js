/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:support@labkey.com">support@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008 LabKey Software Foundation
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

 /**
  * @description GridView class to create and render grid views over server-side tables and lists.
  * @class GridView class to create and render grid views over server-side tables and lists.
  * @constructor
  * @param {Object} config Describes the GridView's properties.
  * @param {Object} config.schemaName Name of a schema defined within the current
  *                 container.  Example: 'study'.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.  
  * @param {Object} config.queryName Name of a query defined within the specified schema
  *                 in the current container.  Example: 'SpecimenDetail'. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.  
  * @param {Object} [config.viewName] Name of a custom view defined over the specified query.  
  *                 in the current container. Example: 'SpecimenDetail'.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.  
  * @param {String} config.renderTo Name of the div in which to place the grid.
  * @param {Bool} config.editable Whether the grid should be made editable.  Note that
  *                 not all tables and columns are editable, and not all users have
  *                 permission to edit.  For this reason, part or all of the grid may
  *                 degrade to being non-editable despite the 'editable' parameter.
  * @param {Object} [config.gridPanelConfig] Sets the display configuration for the new grid.  This 
  *					configuration is passed through to the underlying Ext.grid.GridPanel implementation,
  *                 so all <a href="http://extjs.com/deploy/dev/docs/?class=Ext.grid.GridPanel">
  *                 GridPanel config options</a> are valid. <p/>Note that providing this configuration
  *                 is optional. Further, if you do provide it, you take responsibility for
  *                 providing a valid and complete config object.  If you do not set the
  *                 GridPanel config, LabKey Server will use a default configuration option.
  * @param {Object} [config.storeConfig] Config object that is passed to the underlying Store.
  *                 This configuration is passed through to the underlying Ext.data.Store implementation,
  *                 so all <a href="http://extjs.com/deploy/dev/docs/?class=Ext.data.Store">
  *                 Store config options</a> are valid. <p/>Note that providing this configuration
  *                 is optional. Further, if you do provide it, you take responsibility for
  *                 providing a valid and complete config object.  If you do not set the
  *                 Store config, LabKey Server will use a default configuration option.
  * @param {Function(columnModel)} [config.columnModelListener] Callback function that allows
  *					you to adjust the column 
  *					model without providing a full GridPanel config.  The columnModel 
  *					element/object contains information about how one may interact with 
  *					the columns within a user interface. This format is generated to match 
  *					the requirements of the Ext grid component.  See 
  *					<a href=http://extjs.com/deploy/dev/docs/?class=Ext.grid.ColumnModel>
  *					Ext.grid.ColumnModel</a> for further information.
  * @param {Function(Ext.grid.GridPanel)} config.gridCustomizeCallback Function that should be called after the 
  *					grid has been constructed and populated with data. You can use this to 
  *					further customize the grid's appearance, add toolbar buttons, or call 
  *					any method on the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.grid.GridPanel">
  *                 Ext GridPanel object</a>.  The function passed as this config property 
  *					should look like this: 
  * @param {String} [config.containerPath] The container path in which the schemaName and queryName are defined.
  *                 If not supplied, the current container path will be used.
<pre><code>
function onGridCustomize(grid) 
{ 
   //customize the grid here 
   //for example: 
   grid.getTopToolbar().addButton({ 
            text: "Test Button", 
            handler: function(){window.alert("Test button clicked!");} 
        }); 
} 
</pre></code>
where 'grid' is a reference to the Ext.grid.GridPanel instance. 
  * @constructor
  * @example Example:
<pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
&lt;/script&gt;
&lt;script type="text/javascript"&gt;
    // create new grid over a list named 'People' 
	var gridView = new LABKEY.GridView({ 
            schemaName : 'lists', 
            queryName : 'People', 
            renderTo : 'testDiv', 
            editable : true, 
            gridPanelConfig : { 
                title :'My People Grid', 
                autoHeight : true, 
                width : 500
            } 
        }); 
	// place editable grid in 'testDiv': 
	gridView.render(); 
&lt;/script&gt;
&lt;div id='testDiv'/&gt;
</pre></code>
*/

LABKEY.GridView = function(config)
{
    Date.patterns = {
        ISO8601Long:"Y-m-d H:i:s",
        ISO8601Short:"Y-m-d"
    };

    if (!config.schemaName || !config.queryName)
    {
        alert("config.schemaName and config.queryName are required parameters");
        return;
    }

    var _primarySchemaName = config.schemaName;
    var _primaryQueryName = config.queryName;
    var _primaryViewName = config.viewName;
    var _renderTo = config.renderTo;
    var _gridPanelConfig = config.gridPanelConfig;
    var _storeConfig = config.storeConfig;
    var _selectionModel;
    var _editable = config.editable;
    var _errorsInGridData = false;

    // private member variables:
    var _ds;
    var _myReader;
    var _columnModelListener = config.columnModelListener;
	var _gridCustomizeCallback = config.gridCustomizeCallback;
    var _pageLimit = 20;
    var _grid;
    var _extParamMapping = {
        "start" : "query.offset",
        "limit" : "query.maxRows",
        "sort" : "query.sort",
        "dir" : "query.sortdir"
        }
    var _containerPath = config.containerPath;


    // private methods:
    function handleLoadError(This, o, arg, e)
    {
        var error;
        if (arg && arg.responseText)
        {
            var jsonResponse = Ext.util.JSON.decode(arg.responseText);
            if (jsonResponse && jsonResponse.exception)
            {
                error = "An error occurred trying to load grid data.\n" + jsonResponse.exception;
                error += "\n(" + (jsonResponse.exceptionClass ? jsonResponse.exceptionClass : "Exception class unknown") + ")";
            }
        }
        if (!error)
            error = "An error occurred trying to load grid data.\nStatus: " + arg.statusText + " (" + arg.status + ")";
        if (e && e.message)
            error += "\n" + e.name + ": " + e.message;
        alert (error);
    }

    function mapQueryParameters(store, options)
    {
        // map all parameters from ext names to labkey names:
        for (var param in options)
        {
            if (_extParamMapping[param])
                options[_extParamMapping[param]] = options[param];
        }

        // fix up any necessary parameter values:
        if ("DESC" == options['query.sortdir'])
        {
            var sortCol = options['query.sort'];
            options['query.sort'] = "-" + sortCol;
        }
    }

    function getDefaultRenderer(fieldColumn, displayColumn)
    {
        switch (fieldColumn.type)
        {
            case "date":
                return function(data)
                {
                    if (!data)
                        return;
                    var date = new Date(data);
                    if (date.getHours() == 0 && date.getMinutes() == 0 && date.getSeconds() == 0)
                        return date.format(Date.patterns.ISO8601Short);
                    else
                        return date.format(Date.patterns.ISO8601Long)
                };
                break;
            case "boolean":
            case "int":
            case "float":
            case "string":
            default:
        }
    }

    function getDefaultEditor(fieldColumn, displayColumn)
    {
        if (displayColumn.editable)
        {
            var editor;
            switch (fieldColumn.type)
            {
                case "boolean":
                    editor = new Ext.form.Checkbox();
                    break;
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
                        format : Date.patterns.ISO8601Long,
                        altFormats: Date.patterns.ISO8601Short +
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
                        /*
                    editor.on("change", function (thisEditor, newValue, oldValue)
                    {
                        var dateValue = new Date(newValue);
                        if (dateValue)
                        {
                            if (dateValue.getHours() == 0 && dateValue.getMinutes() == 0 && dateValue.getSeconds() == 0)
                                thisEditor.value = dateValue.format(Date.patterns.ISO8601Short);
                            else
                                thisEditor.value = dateValue.format(Date.patterns.ISO8601Long)
                        }
                    });*/
                    break;
                case "string":
                    editor = new Ext.form.TextField();
                    break;
                default:
            }
            if (editor)
            {
                editor.allowBlank = !displayColumn.required;
                registerEditorListeners(editor);
            }
            return editor;
        }
    }

    function getLookupEditor(dsLookup, lookupDef)
    {
        var editor = new Ext.form.ComboBox({ //dropdown based on server side data (from db)
                        typeAhead: false, //will be querying database so may not want typeahead consuming resources
                        triggerAction: 'all',
                        editable:false,
                        lazyRender: true,//prevents combo box from rendering until requested, should always be true for editor
                        store: dsLookup,//Industry,//where to get the data for our combobox
                        displayField: lookupDef.displayColumn,//the underlying data  field name to bind to this ComboBox
                                                     //(defaults to undefined if mode = 'remote' or 'text' if transforming a select)
                        valueField: lookupDef.keyColumn     //the underlying value field name to bind to this ComboBox
                    });
        registerEditorListeners(editor);
        return editor;
    }

    function registerEditorListeners(editor)
    {
        editor.addListener("blur", afterCellEdit);
        editor.addListener("beforeshow", function()
        {
            document.ActiveExtGridViewCellId = editor.id;
            return true;
        });
    }

    // this function creates a closure that allows the references to dsLookup and
    // lookupDef to stick to the render function:
    function getLookupRenderer(dsLookup, lookupDef)
    {
        var refreshed = false;
        dsLookup.on("load", function(store, recordArray, options)
        {
            if (_grid && !refreshed)
                _grid.getView().refresh();
            refreshed = true;
            store.un("load", this);
        });

        return function(data)
        {
            var record = dsLookup.getById(data);
            if(record)
                return record.data[lookupDef.displayColumn];
            else
                return '<' + data + '>';
        };
    }

    function initColumnUI()
    {
        var columnModelNameMap = {};
        for (var columnId in _myReader.jsonData.columnModel)
        {
            var column = _myReader.jsonData.columnModel[columnId];
            columnModelNameMap[column.dataIndex] = column;
        }

        for (var fieldId = 0; fieldId < _myReader.jsonData.metaData.fields.length; fieldId++)
        {
            var fieldColumn = _myReader.jsonData.metaData.fields[fieldId];
            var displayColumn = columnModelNameMap[fieldColumn.name];
            if (fieldColumn.lookup)
            {
                var lookupDef = fieldColumn.lookup;
                var dsLookup = createStoreImpl(lookupDef.schema, lookupDef.table);
                displayColumn.renderer = getLookupRenderer(dsLookup, lookupDef);
                if (_editable)
                    displayColumn.editor = getLookupEditor(dsLookup, lookupDef);
                dsLookup.load();
            }
            else
            {
                if (_editable)
                    displayColumn.editor = getDefaultEditor(fieldColumn, displayColumn);
                displayColumn.renderer = getDefaultRenderer(fieldColumn, displayColumn);
            }
        }
    }

    function handleGridData(r, options, success)
    {
        if (!success)
            return;

        if (!_myReader || !_myReader.jsonData || !_myReader.jsonData.columnModel)
            return;

        if (!_gridPanelConfig)
            _gridPanelConfig = {};

        _gridPanelConfig.store = _ds;

        if (_columnModelListener)
            _gridPanelConfig.columns = _columnModelListener(_myReader.jsonData.columnModel)
        else
            _gridPanelConfig.columns = _myReader.jsonData.columnModel;

        // double check to see if there are any editable columns in this col model.  If not,
        // degrade to a non-editable grid.
        if (_editable)
        {
            var anyEditable = false;
            for (var i = 0; i < _gridPanelConfig.columns.length && !anyEditable; i++)
                anyEditable = _gridPanelConfig.columns[i].editable;
            if (!anyEditable)
                _editable = false;
        }

        initColumnUI();

        if (!_gridPanelConfig.view)
        {
            _gridPanelConfig.view = new Ext.grid.GridView({
                    forceFit:true,
                    // custom grouping text template to display the number of items per group
                    groupTextTpl: '{text} ({[values.rs.length]} {[values.rs.length > 1 ? "Items" : "Item"]})'
                });
        }

        if (!_gridPanelConfig.selModel)
        {
            if (_editable)
            {
                _gridPanelConfig.selModel = new Ext.grid.CheckboxSelectionModel({singleSelect:false});
                _gridPanelConfig.columns = [_gridPanelConfig.selModel].concat(_gridPanelConfig.columns);
            }
            else
                _gridPanelConfig.selModel = new Ext.grid.RowSelectionModel({singleSelect:true});
        }
        _selectionModel = _gridPanelConfig.selModel;

        if (_editable)
        {
            if (!_gridPanelConfig.clicksToEdit)
                _gridPanelConfig.clicksToEdit = 2;

            if (!_gridPanelConfig.tbar)
            {
                _gridPanelConfig.tbar = [
                    {
                        text: 'Add Record',
                        tooltip: 'Click to add a row',
                        handler: addRecord, //what happens when user clicks on it
                        id: 'add-record-button'
                    }, '-', //add a separator
                    {
                        text: 'Delete Selected',
                        tooltip: 'Click to delete selected row(s)',
                        handler: handleDelete, //what happens when user clicks on it
                        id: 'delete-records-button'
                    }, '-', //add a separator
                    {
                        text: 'Refresh',
                        tooltip: 'Click to refresh the table',
                        id: 'refresh-button',
                        handler: refreshGrid //what happens when user clicks on it
                    }
                ];
            }
            _grid = new Ext.grid.EditorGridPanel(_gridPanelConfig);
            _ds.addListener("beforeload", commitEdits);
            _grid.addListener('afteredit', afterCellEdit);//give event name, handler (can use 'on' shorthand for addListener)
            _grid.addListener('beforeedit', beforeCellEdit);//give event name, handler (can use 'on' shorthand for addListener)
            _editCommitTimer = new Ext.util.DelayedTask(commitEdits, this)
        }
        else
        {
            if (!_gridPanelConfig.tbar)
            {
                _gridPanelConfig.tbar = [
                    {
                        text: 'Refresh',
                        tooltip: 'Click to Refresh the table',
                        handler: refreshGrid, //what happens when user clicks on it
                        iconCls:'refresh' //we create our own css with a class called 'add'
                    }
                ];
            }
            _grid = new Ext.grid.GridPanel(_gridPanelConfig);
        }

        //call the grid customize callback if any
        if(_gridCustomizeCallback)
            _gridCustomizeCallback(_grid);
    }

    function typeConvert(fieldColumn, value)
    {
        if (!value && !fieldColumn.required)
            return null;
        switch (fieldColumn.type)
        {
            case "boolean":
                // strange trick here to boolean-ify our value.  From
                // http://www.jibbering.com/faq/faq_notes/type_convert.html
                return value instanceof Boolean ? value : !!value;
            case "int":
                return value instanceof Number ? value : Math.round(Number(value));
            case "float":
                return value instanceof Number ? value : Number(value);
            case "date":
                return value instanceof Date ? value : Date(value);
            case "string":
                return value instanceof String ? value : "" + value;
            default:
                return value;
        }
    }

    function getDefaultValues(fields)
    {
        var record = {};
        for (var i = 0; i < fields.length; i++)
        {
            var field = fields[i];
            record[field.name] = null;
        }
        return record;
    }

    function addRecord()
    {
        var fields = _myReader.jsonData.metaData.fields;
        var recordCreator = Ext.data.Record.create(fields);
        var newRecord = new recordCreator(getDefaultValues(fields));
        newRecord.LABKEY$isNew = true;
        _grid.stopEditing();//stops any acitve editing
        _ds.insert(0, newRecord); //1st arg is index,
                         //2nd arg is Ext.data.Record[] records
        //very similar to ds.add, with ds.insert we can specify the insertion point
        _grid.startEditing(0, 1);//starts editing the specified rowIndex, colIndex
                                //make sure you pick an editable location in the line above
                                //otherwise it won't initiate the editor
    }

    function isNullRecord(record)
    {
        for (var field in record)
        {
            var value = record[field];
            if (value)
                return false;
        }
        return true;
    }

    var _currentEditRow;
    var _editCommitTimer;
    function beforeCellEdit(parameters)
    {
        if (_currentEditRow == parameters.row)
            _editCommitTimer.cancel();
        _currentEditRow = parameters.row;
        _selectionModel.selectRow(parameters.row);
        var record = _ds.getAt(parameters.row);
        record.saveNeeded = true;
    }

    function afterCellEdit(parameters)
    {
        _editCommitTimer.delay(250);
        _errorsInGridData = false;
    }

    function commitEdits()
    {
        var keyColumn = _myReader.jsonData.metaData.id;
        var records = _ds.getModifiedRecords();
        for (var i = 0; i < records.length; i++)
        {
            var record = records[i];
            if (!record.data.toBeDeleted && (record.data[keyColumn] || !isNullRecord(record.data)))
                updateDB(record)
        }
    }
    
    function handleDelete()
    {
        if (_selectionModel)
        {
            commitEdits();
            var records = _selectionModel.getSelections();
            if (records && records.length)
            {
                if (confirm("Permanently delete the selected records?"))
                {
                    var data = [];
                    var keyColumn = _myReader.jsonData.metaData.id;
                    var uncommittedRecords = false;
                    for (var i = 0; i < records.length; i++)
                    {
                        var recordData = records[i].data;
                        if (recordData[keyColumn])
                            data[data.length] = recordData;
                        else
                        {
                            uncommittedRecords = true;
                            recordData.toBeDeleted = true;
                        }
                    }
                    if (data.length > 0)
                    {
                        LABKEY.Query.deleteRows(_primarySchemaName, _primaryQueryName, data,
                                afterSuccessfulDelete, afterFailedEdit);
                    }
                    else if (uncommittedRecords)
                        refreshGrid();
                }
            }
        }
    }

    function refreshGrid()
    {
        _ds.reload();
    }

    function afterSuccessfulDelete(responseObj, options)
    {
        refreshGrid();
    }

    function afterSuccessfulEdit(responseObj, options)
    {
        commitSavedRows(responseObj);
        //_ds.commitChanges();//commit changes (removes the red triangle which indicates a 'dirty' field)
       // refreshGrid();
    }

    function commitSavedRows(responseObj)
    {
        if (!responseObj.rows || responseObj.rows.length == 0)
            return;

        for (var rowIndex = 0; rowIndex < responseObj.rows.length; rowIndex++)
        {
            var keyValue = responseObj.rows[rowIndex][_myReader.jsonData.metaData.id];
            var record = _ds.getById(keyValue);

            if(record)
            {
                //set all fields that are present in the rows[rowIndex] object
                var retRow = responseObj.rows[rowIndex];
                for(var field in record.data)
                {
                    if(retRow[field])
                        record.set(field, retRow[field]);
                }
                
                record.commit();
            }
        }
    }

    function getAfterSuccessfulInsert(newRecord)
    {
        return function(responseObj, options)
        {
            newRecord.operationPendingSinceLastEdit = false;
            newRecord.commit();

            //create a new record based on the fields returned from the server
            //and specify the newly-assigned id
            //and remove the temporary newRecord
            var row = responseObj.rows[0];
            var fields = _myReader.jsonData.metaData.fields;
            var recordCreator = Ext.data.Record.create(fields);
            var newNewRecord = new recordCreator(row, row[_myReader.jsonData.metaData.id]);
            _ds.insert(0, newNewRecord);
            _ds.remove(newRecord);
        }
    }

    function afterFailedEdit(jsonResponse, options)
    {
        _errorsInGridData = true;
        if (jsonResponse && jsonResponse.exception)
        {
            alert("Update failed: " + jsonResponse.exception + "\n(Exception class " + jsonResponse.exceptionClass + ")")
        }
        else
            alert("Update failed: " + jsonResponse.statusText + " (Response code " + jsonResponse.status + ")");
    }

    function updateDB(record)
    {
        if (_errorsInGridData)
            return;

        if (!record.saveNeeded)
            return;
        /*
        * editEvent has the following properties:
        * grid - This grid
        * record - The record being edited
        * field - The field name being edited
        * value - The value being set
        * originalValue - The original value for the field, before the edit.
        * row - The grid row index
        * column - The grid column index
        */
        var store = _grid.getStore();
        var fields = store.fields;
        var recordData = {};

        for (var fieldId = 0; fieldId < fields.length; fieldId++)
        {
            var field = fields.itemAt(fieldId);
            var value = record.data[field.name];
            if (value != null)
                value = typeConvert(field, value);
            recordData[field.name] = value;
        }

        var validRecord = true;
        for (var colId = 0; colId < _myReader.jsonData.columnModel.length && validRecord; colId++)
        {
            var col = _myReader.jsonData.columnModel[colId];
            // we allow a null key column, since that's always going to be the case for insert
            if (col.dataIndex != store.reader.jsonData.metaData.id)
            {
                if (recordData[col.dataIndex] == null && col.required)
                    validRecord = false;
            }
        }


        if (validRecord)
        {
            record.saveNeeded = false;
            record.operationPendingSinceLastEdit = true;
            if (record.LABKEY$isNew) //!recordData[store.reader.jsonData.metaData.id])
            {
                LABKEY.Query.insertRows(_primarySchemaName, _primaryQueryName, [recordData],
                        getAfterSuccessfulInsert(record), afterFailedEdit);
            }
            else
            {
                LABKEY.Query.updateRows(_primarySchemaName, _primaryQueryName, [recordData],
                        afterSuccessfulEdit, afterFailedEdit);
            }
        }
    }
    


    function createHttpProxyImpl(errorListener)
    {
        var proxy = new Ext.data.HttpProxy(new Ext.data.Connection({
                //where to retrieve data
                url: LABKEY.ActionURL.buildURL("query", "selectRows", _containerPath), //url to data object (server side script)
                method: 'GET'
            }));

        if (errorListener)
            proxy.on("loadexception", errorListener);

        proxy.on("beforeload", mapQueryParameters);

        return proxy;
    }

    function createStoreImpl(schemaName, queryName, viewName, storeConfig)
    {
        if (!storeConfig)
            storeConfig = {};
        if (!storeConfig.baseParams)
            storeConfig.baseParams = {};
        storeConfig.baseParams['query.queryName'] = (queryName ? queryName : _primaryQueryName);
        storeConfig.baseParams['schemaName'] = (schemaName ? schemaName : _primarySchemaName);
        if (viewName)
            storeConfig.baseParams['query.viewName'] = viewName;

        if (!storeConfig.proxy)
            storeConfig.proxy = createHttpProxyImpl();

        if (!storeConfig.remoteSort)
            storeConfig.remoteSort = true;

        if (!storeConfig.listeners || !storeConfig.listeners.loadexception)
            storeConfig.listeners = { loadexception : { fn : handleLoadError } };

        storeConfig.reader = new Ext.data.JsonReader();

        return new Ext.data.Store(storeConfig);
    }

    function createDefaultStoreImpl()
    {
        if (!_storeConfig)
            _storeConfig = {};
        return createStoreImpl(_primarySchemaName, _primaryQueryName, _primaryViewName, _storeConfig)
    }

    function displayGridImpl()
    {
        if (!_gridPanelConfig)
            _gridPanelConfig = {};
        _gridPanelConfig.renderTo = _renderTo;

        _ds = createDefaultStoreImpl();
        _myReader = _ds.reader;

        if (!_gridPanelConfig.bbar)
        {
            _gridPanelConfig.bbar = new Ext.PagingToolbar({
                    pageSize: _pageLimit,//default is 20
                    store: _ds,
                  //  paramNames : _extParamMapping,
                    emptyMsg: "No data to display"//display message when no records found
                });
        }

        _ds.load({ callback : handleGridData,
            params : {
                start: 0,
                limit: _pageLimit
            }});
    }

    // public methods:
    /** @scope LABKEY.GridView.prototype */
    return {
	  /**
	  *   Renders the grid view to the div specified in the renderTo config property.
	  */
        render : function()
        {
            return displayGridImpl();
        }
    }
};

