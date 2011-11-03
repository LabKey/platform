/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */



LABKEY.requiresExt4Sandbox(true);

Ext.namespace('LABKEY.ext4');

/**
 * A collection of static helper methods designed to interface between LabKey's metadata and Ext.
 * @class It is heavily used internally with LABKEY's Client API, such as LABKEY.ext4.FormPanel or LABKEY.ext4.Store;
 * however these methods can be called directly.  LABKEY.ext4.Store also contains convenience methods that wrap some of MetaHelper's methods.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 *
 */


LABKEY.requiresExt4Sandbox(true);

LABKEY.ext.MetaHelper = {
    /**
     * Constructs an ext field component based on the supplied metadata.  Same as getFormEditorConfig, but actually constructs the editor.
     * The resulting editor is tailored for usage in a form, as opposed to a grid. Unlike getGridEditorConfig or getEditorConfig, if the metadata
     * contains a formEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
     *
     * @name getFormEditor
     * @function
     * @returns {object} Returns an Ext field component
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    getFormEditor: function(meta, config){
        var editorConfig = LABKEY.ext.MetaHelper.getFormEditorConfig(meta, config);
        return Ext4.ComponentMgr.create(editorConfig);
    },

    /**
     * Constructs an ext field component based on the supplied metadata.  Same as getFormEditorConfig, but actually constructs the editor.
     * The resulting editor is tailored for usage in a grid, as opposed to a form. Unlike getFormEditorConfig or getEditorConfig, if the metadata
     * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
     *
     * @name getGridEditor
     * @function
     * @returns {object} Returns an Ext field component
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    getGridEditor: function(meta, config){
        var editorConfig = LABKEY.ext.MetaHelper.getGridEditorConfig(meta, config);
        return Ext4.ComponentMgr.create(editorConfig);
    },

    /**
     * Return an Ext config object to create an Ext field based on the supplied metadata.
     * The resulting config object is tailored for usage in a grid, as opposed to a form. Unlike getFormEditorConfig or getEditorConfig, if the metadata
     * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
     *
     * @name getGridEditorConfig
     * @function
     * @returns {object} Returns an Ext config object
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    getGridEditorConfig: function(meta, config){
        //this produces a generic editor
        var editor = LABKEY.ext.MetaHelper.getDefaultEditorConfig(meta);

        //for multiline fields:
        if(editor.editable && meta.inputType == 'textarea'){
            editor = new LABKEY.ext.LongTextField({
                columnName: editor.dataIndex
            });
        }

        //now we allow overrides of default behavior, in order of precedence
        if(meta.editorConfig)
            Ext4.Object.merge(editor, meta.editorConfig);

        //note: this will screw up cell editors
        delete editor.fieldLabel;

        if(meta.gridEditorConfig)
            Ext4.Object.merge(editor, meta.gridEditorConfig);
        if(config)
            Ext4.Object.merge(editor, config);

        return editor;
    },

    /**
     * Return an Ext config object to create an Ext field based on the supplied metadata.
     * The resulting config object is tailored for usage in a form, as opposed to a grid. Unlike getGridEditorConfig or getEditorConfig, if the metadata
     * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
     *
     * @name getFormEditorConfig
     * @function
     * @returns {object} Returns an Ext config object
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    getFormEditorConfig: function(meta, config){
        var editor = LABKEY.ext.MetaHelper.getDefaultEditorConfig(meta);

        //now we allow overrides of default behavior, in order of precedence
        if(meta.editorConfig)
            Ext4.Object.merge(editor, meta.editorConfig);
        if(meta.formEditorConfig)
            Ext4.Object.merge(editor, meta.formEditorConfig);
        if(config)
            Ext4.Object.merge(editor, config);

        return editor;
    },

    //this is designed to be called through either .getFormEditorConfig or .getGridEditorConfig
    /**
     * Uses the given meta-data to generate a field config object.
     *
     * This function accepts a mish-mash of config parameters to be easily adapted to
     * various different metadata formats.
     *
     * Note: you can provide any Ext config options using the editorConfig, formEditorConfig or gridEditorConfig objects
     * These config options can also be used to pass arbitrary config options used by your specific Ext component
     *
     * @param {string} [config.type] e.g. 'string','int','boolean','float', or 'date'. for consistency this will be translated into the property jsonType
     * @param {object} [config.editable]
     * @param {object} [config.required]
     * @param {string} [config.label] used to generate fieldLabel
     * @param {string} [config.name] used to generate fieldLabel (if header is null)
     * @param {string} [config.caption] used to generate fieldLabel (if label is null)
     * @param {integer} [config.cols] if input is a textarea, sets the width (style:width is better)
     * @param {integer} [config.rows] if input is a textarea, sets the height (style:height is better)
     * @param {string} [config.lookup.schemaName] the schema used for the lookup.  schemaName also supported
     * @param {string} [config.lookup.queryName] the query used for the lookup.  queryName also supported
     * @param {Array} [config.lookup.columns] The columns used by the lookup store.  If not set, the <code>[keyColumn, displayColumn]</code> will be used.
     * @param {string} [config.lookup.keyColumn]
     * @param {string} [config.lookup.displayColumn]
     * @param {string} [config.lookup.sort] The sort used by the lookup store.
     * @param {boolean} [config.lookups] use lookups=false to prevent creating default combobox for lookup columns
     * @param {object}  [config.editorConfig] is a standard Ext config object (although it can contain any properties) that will be merged with the computed field config
     *      e.g. editorConfig:{width:120, tpl:new Ext.Template(...), arbitraryOtherProperty: 'this will be applied to the editor'}
     *      this will be merged will all form or grid editors
     * @param {object}  [config.formEditorConfig] Similar to editorConfig; however, it will only be merged when getFormEditor() or getFormEditorConfig() are called.
     *      The intention is to provide a mechanism so the same metadata object can be used to generate editors in both a form or a grid (or other contexts).
     * @param {object}  [config.gridEditorConfig] similar to formEditorConfig; however, it will only be merged when getGridEditor() or getGridEditorConfig() are called.
     * @param {object}  [config.columnConfig] similar to formEditorConfig; however, it will only be merged when getColumnConfig() is getColumnsConfig() called.
     * @param {object} [config.lookup.store] advanced! Pass in your own custom store for a lookup field
     * @param {boolean} [config.lazyCreateStore] If false, the store will be created immediately.  If true, the store will be created when the component is created. (default true)
     * @param {boolean} [createIfDoesNotExist] If true, this field will be created in the store, even if it does not otherwise exist on the server. Can be used to force custom fields to appear in a grid or form or to pass additional information to the server at time of import
     //TODO
     * @param {function} [qtipRenderer] This function will be used to generate the qTip for the field when it appears in a grid instead of the default function.  It will be passed the following arguments: qtip, data, cellMetaData, record, rowIndex, colIndex, store. It should modify the qtip array
     //TODO
     * @param {function} [buildDisplayString] This function will be used to generate the qTip for the field when it appears in a grid instead of the default function.  See example below for usage.
     //TODO
     * @param {function} [buildUrl] This function will be used to generate the URL encapsulating the field
     * @param (boolean) [setValueOnLoad] If true, the store will attempt to set a value for this field on load.  This is determined by the defaultValue or setInitialValue function, if either is defined
     * @param {function} [setInitialValue] When a new record is added to this store, this function will be called on that field.  If setValueOnLoad is true, this will also occur on load.  It will be passed the record and metadata.  The advantage of using a function over defaultValue is that more complex and dynamic initial values can be created.  For example:
     *  //sets the value to the current date
     *  setInitialValue(val, rec, meta){
     *      return val || new Date()
     *  }
     *
     * Note: the follow Ext params are automatically defined based on the specified Labkey metadata property:
     * dataIndex -> name
     * editable -> userEditable && readOnly
     * header -> caption
     * xtype -> set within getDefaultEditorConfig(), unless otherwise provided

     *
     */
    getDefaultEditorConfig: function(meta){
        var field =
        {
            //added 'caption' for assay support
            fieldLabel: Ext4.util.Format.htmlEncode(meta.label || meta.shortCaption || meta.caption || meta.header || meta.name),
            originalConfig: meta,
            //we assume the store's translateMeta() will handle this
            allowBlank: meta.allowBlank!==false,
            disabled: meta.editable===false,
            name: meta.name,
            dataIndex: meta.dataIndex || meta.name,
            value: meta.value || meta.defaultValue,
            helpPopup: ['Type: ' + meta.friendlyType],
            width: meta.width,
            height: meta.height,
            msgTarget: 'qtip',
            labelableRenderTpl: LABKEY.ext.MetaHelper.labelableRenderTpl,
            validateOnChange: true
        };

        if(field.description)
            field.helpPopup.push('Description: '+meta.description);

        field.renderData = {
            helpPopup: field.helpPopup.join('<br>')
        };

        if (meta.hidden)
        {
            field.xtype = 'hidden';
        }
        else if (meta.lookup && meta.lookups !== false)
        {
            var l = meta.lookup;

            //test whether the store has been created.  create if necessary
            if (Ext4.isObject(meta.store) && meta.store.events)
                field.store = meta.store;
            else
//                field.store = LABKEY.ext.MetaHelper.getLookupStoreConfig(meta);
                field.store = LABKEY.ext.MetaHelper.getLookupStore(meta);

//            if (field.store && meta.lazyCreateStore === false){
//                field.store = LABKEY.ext.MetaHelper.getLookupStore(field);
//            }

            Ext4.apply(field, {
                //this purpose of this is to allow other editors like multiselect, checkboxGroup, etc.
                xtype: (meta.xtype || 'labkey-combo'),
                forceSelection: true,
                typeAhead: true,
                queryMode: 'local',
//                hiddenName: meta.name,
//                hiddenId : Ext4.id(),
//                triggerAction: 'all',
//                lazyInit: false,
                //NOTE: perhaps we do translation of the following names in store's translateMeta() method
                displayField: l.displayColumn,
                valueField: l.keyColumn,
                //NOTE: supported for non-combo components
                initialValue: field.value,
                showValueInList: meta.showValueInList,
//                listClass: 'labkey-grid-editor',
                lookupNullCaption: meta.lookupNullCaption
            });
        }
        else
        {
            switch (meta.jsonType)
            {
                case "boolean":
                    field.xtype = meta.xtype || 'checkbox';
                    break;
                case "int":
                    field.xtype = meta.xtype || 'numberfield';
                    field.allowDecimals = false;
                    break;
                case "float":
                    field.xtype = meta.xtype || 'numberfield';
                    field.allowDecimals = true;
                    break;
                //TODO: account for datetime vs date?
                case "date":
                    field.xtype = meta.xtype || 'datefield';
                    field.format = meta.format || Date.patterns.ISO8601Long;
                    field.altFormats = Date.patterns.ISO8601Short +
                            'n/j/y g:i:s a|n/j/Y g:i:s a|n/j/y G:i:s|n/j/Y G:i:s|' +
                            'n-j-y g:i:s a|n-j-Y g:i:s a|n-j-y G:i:s|n-j-Y G:i:s|' +
                            'n/j/y g:i a|n/j/Y g:i a|n/j/y G:i|n/j/Y G:i|' +
                            'n-j-y g:i a|n-j-Y g:i a|n-j-y G:i|n-j-Y G:i|' +
                            'j-M-y g:i a|j-M-Y g:i a|j-M-y G:i|j-M-Y G:i|' +
                            'n/j/y|n/j/Y|' +
                            'n-j-y|n-j-Y|' +
                            'j-M-y|j-M-Y|' +
                            'Y-n-d H:i:s|Y-n-d|' +
                            'j M Y G:i:s O|' + // 10 Sep 2009 11:24:12 -0700
                            'j M Y H:i:s';     // 10 Sep 2009 01:24:12
                    break;
                case "string":
                    if (meta.inputType=='textarea')
                    {
                        field.xtype = meta.xtype || 'textarea';
                        field.width = meta.width;
                        field.height = meta.height;
                        if (!this._textMeasure)
                        {
                            this._textMeasure = {};
                            var ta = Ext.DomHelper.append(document.body,{tag:'textarea', rows:10, cols:80, id:'_hiddenTextArea', style:{display:'none'}});
                            this._textMeasure.height = Math.ceil(Ext4.util.TextMetrics.measure(ta,"GgYyJjZ==").height * 1.2);
                            this._textMeasure.width  = Math.ceil(Ext4.util.TextMetrics.measure(ta,"ABCXYZ").width / 6.0);
                        }
                        if (meta.rows && !meta.height)
                        {
                            if (meta.rows == 1)
                                field.height = undefined;
                            else
                            {
                                // estimate at best!
                                var textHeight =  this._textMeasure.height * meta.rows;
                                if (textHeight)
                                    field.height = textHeight;
                            }
                        }
                        if (meta.cols && !meta.width)
                        {
                            var textWidth = this._textMeasure.width * meta.cols;
                            if (textWidth)
                                field.width = textWidth;
                        }

                    }
                    else
                        field.xtype = meta.xtype || 'textfield';
                    break;
                default:
                    field.xtype = meta.xtype || 'textfield';
            }
        }

        return field;
    },

    // private
    getLookupStore : function(storeId, c)
    {
        if (typeof(storeId) != 'string')
        {
            c = storeId;
            storeId = LABKEY.ext.MetaHelper.getLookupStoreId(c);
        }

        // Check if store has already been created.
        if (Ext4.isObject(c.store) && c.store.events)
            return c.store;

        var store = Ext4.StoreMgr.lookup(storeId);
        if (!store)
        {
            var config = c.store || LABKEY.ext.MetaHelper.getLookupStoreConfig(c);
            config.storeId = storeId;
            store = Ext4.create('LABKEY.ext4.Store', config);
        }
        return store;
    },

    // private
    getLookupStoreId : function (c)
    {
        if (c.store && c.store.storeId)
            return c.store.storeId;

        if (c.lookup)
            return [c.lookup.schemaName || c.lookup.schema , c.lookup.queryName || c.lookup.table, c.lookup.keyColumn, c.lookup.displayColumn].join('||');

        return c.name;
    },

    //private
    getLookupStoreConfig : function(c)
    {
        var l = c.lookup;

        // normalize lookup
        l.queryName = l.queryName || l.table;
        l.schemaName = l.schemaName || l.schema;

        if (l.schemaName == 'core' && l.queryName =='UsersData')
            l.queryName = 'Users';

        var config = {
            xtype: "labkey-store",
            storeId: LABKEY.ext.MetaHelper.getLookupStoreId(c),
            schemaName: l.schemaName,
            queryName: l.queryName,
            containerPath: l.container || l.containerPath || LABKEY.container.path,
            autoLoad: true
        };

        if (l.viewName)
            config.viewName = l.viewName;

        if (l.filterArray)
            config.filterArray = l.filterArray;

        if (l.columns)
            config.columns = l.columns;
        else
        {
            var columns = [];
            if (l.keyColumn)
                columns.push(l.keyColumn);
            if (l.displayColumn && l.displayColumn != l.keyColumn)
                columns.push(l.displayColumn);
            if (columns.length == 0){
                columns = ['*'];
            }
            config.columns = columns;
        }

        if (l.sort)
            config.sort = l.sort;
        else if (l.sort !== false)
            config.sort = l.displayColumn;

        if (!c.required && c.includeNullRecord !== false)
        {
            config.nullRecord = c.nullRecord || {
                displayColumn: l.displayColumn,
                nullCaption: (l.displayColumn==l.keyColumn ? null : (c.lookupNullCaption!==undefined ? c.lookupNullCaption : '[none]'))
            };
        }

        return config;
    },

    //private
    //TODO: reconsider how this works.  maybe merge into buildQtip()?
    setLongTextRenderer: function(col, meta){
        if(col.multiline || (undefined === col.multiline && col.scale > 255 && meta.jsonType === "string"))
        {
            col.renderer = function(data, metadata, record, rowIndex, colIndex, store)
            {
                //set quick-tip attributes and let Ext QuickTips do the work
                //Ext3
                metadata.tdAttr = "ext:qtip=\"" + Ext4.util.Format.htmlEncode(data || '') + "\"";
                //Ext4
                metadata.tdAttr += " data-qtip=\"" + Ext4.util.Format.htmlEncode(data || '') + "\"";
                return data;
            };
        }
    },

    //private
    getColumnsConfig: function(store, grid, config){
        config = config || {};

        var fields = store.getFields();
        var columns = store.getColumns();
        var cols = new Array();

        var col;
        fields.each(function(field, idx){
            var col;
            Ext4.each(columns, function(c){
                if(c.dataIndex == field.dataIndex){
                    col = c;
                    return false;
                }
            }, this);

            if(!col)
                col = {dataIndex: field.dataIndex};

            cols.push(LABKEY.ext.MetaHelper.getColumnConfig(store, col, config, grid));

        }, this);

        return cols;
    },

    //private
    getColumnConfig: function(store, col, config, grid){
        col = col || {};

        var meta = store.findFieldMetadata(col.dataIndex);
        col.customized = true;

        if((meta.hidden || meta.shownInGrid===false) && !meta.shownInGrid){
            col.hidden = true;
        }

        switch(meta.jsonType){
            //TODO: Ext has xtypes for these column types.  In Ext3 they did not prove terribly useful, but we should revisit in Ext4;
            // however, the fact that we utilize our own renderer might negate any value
//            case "boolean":
//                if(col.editable){
//                    col.xtype = 'booleancolumn';
//                }
//                break;
//            case "int":
//                col.xtype = 'numbercolumn';
//                col.format = '0';
//                break;
//            case "float":
//                col.xtype = 'numbercolumn';
//                break;
//            case "date":
//                col.xtype = 'datecolumn';
//                col.format = meta.format || Date.patterns.ISO8601Long;
        }

        //this.updatable can override col.editable
        col.editable = config.editable && col.editable && meta.userEditable;

        //will use custom renderer
        if(meta.lookup && meta.lookups!==false)
            delete col.xtype;

        if(col.editable && !col.editor)
            col.editor = LABKEY.ext.MetaHelper.getGridEditorConfig(meta);

        col.renderer = LABKEY.ext.MetaHelper.getDefaultRenderer(col, meta, grid);

        //HTML-encode the column header
        col.text = Ext4.util.Format.htmlEncode(meta.label || meta.name || col.header);

        if(meta.ignoreColWidths)
            delete col.width;

       //allow override of defaults
        if(meta.columnConfig)
            Ext4.Object.merge(col, meta.columnConfig);
        if(config && config[col.dataIndex])
            Ext4.Object.merge(col, config[col.dataIndex]);

        return col;

    },

    //private
    getDefaultRenderer : function(col, meta, grid) {
        return function(data, cellMetaData, record, rowIndex, colIndex, store)
        {
            LABKEY.ext.MetaHelper.buildQtip(data, cellMetaData, record, rowIndex, colIndex, store, col, meta);

            //NOTE: the labkey 9.1 API returns both the value of the field and the display value
            //this is accessing the raw JSON object to obtain the latter
            if(record.json && record.json[meta.name] && record.json[meta.name].displayValue)
                return record.json[meta.name].displayValue;

            var displayType = meta.type;

            //NOTE: this is substantially changed over LABKEY.ext.FormHelper
            if(meta.lookup && meta.lookups!==false){
                data = LABKEY.ext.MetaHelper.lookupRenderer(meta, data, grid, record, rowIndex);
                displayType = 'string';
            }

            if(null === data || undefined === data || data.toString().length == 0)
                return data;

            //format data into a string
            var displayValue;
            if(meta.buildDisplayString){
                displayValue = meta.buildDisplayString(data, col, meta, cellMetaData, record, rowIndex, colIndex, store);
            }
            else {
                switch (displayType)
                {
                    case "date":
                        var date = new Date(data);
                        var format = meta.format;
                        if(!format){
                            if (date.getHours() == 0 && date.getMinutes() == 0 && date.getSeconds() == 0)
                                format = "Y-m-d";
                            else
                                format = "Y-m-d H:i:s";
                        }
                        displayValue = date.format(format);
                        break;
                    case "int":
                        displayValue = (Ext4.util.Format.numberRenderer(this.format || '0'))(data);
                        break;
                    case "boolean":
                        var t = this.trueText || 'true', f = this.falseText || 'false', u = this.undefinedText || ' ';
                        if(data === undefined){
                            displayValue = u;
                        }
                        else if(!data || data === 'false'){
                            displayValue = f;
                        }
                        else {
                            displayValue = t;
                        }
                        break;
                    case "float":
                        displayValue = (Ext4.util.Format.numberRenderer(this.format || '0,000.00'))(data);
                        break;
                    case "string":
                    default:
                        displayValue = data.toString();
                }
            }

            //if meta.file is true, add an <img> for the file icon
            if(meta.file){
                displayValue = "<img src=\"" + LABKEY.Utils.getFileIconUrl(data) + "\" alt=\"icon\" title=\"Click to download file\"/>&nbsp;" + displayValue;
                //since the icons are 16x16, cut the default padding down to just 1px
                cellMetaData.tdAttr += " style=\"padding: 1px 1px 1px 1px\"";
            }

            //build the URL
            displayValue = LABKEY.ext.MetaHelper.buildColumnUrl(displayValue, data, col, meta, record);

            return displayValue;
        };
    },

    //private
    buildColumnUrl: function(displayValue, data, col, meta, record){
        //wrap in <a> if url is present in the record's original JSON
        if(col.showLink !== false){
            if(meta.buildUrl)
                return meta.buildUrl(displayValue, data, col, meta, record);
            else if(record.raw && record.raw[meta.name] && record.raw[meta.name].url)
                return "<a target=\"_blank\" href=\"" + record.raw[meta.name].url + "\">" + displayValue + "</a>";
            else
                return displayValue;
        }
        else {
            return displayValue;
        }
    },

    //private
    buildQtip: function(data, cellMetaData, record, rowIndex, colIndex, store, col, meta){
        var qtip = [];
        //NOTE: returned in the 9.1 API format
        if(record.raw && record.raw[meta.name] && record.raw[meta.name].mvValue){
            var mvValue = record.raw[meta.name].mvValue;

            //get corresponding message from qcInfo section of JSON and set up a qtip
            if(store.reader.rawData && store.reader.rawData.qcInfo && store.reader.rawData.qcInfo[mvValue])
            {
                qtip.push(store.reader.rawData.qcInfo[mvValue]);
                cellMetaData.css = "labkey-mv";
            }
            qtip.push(mvValue);
        }

        if(record.errors && record.getErrors().length){

            Ext4.each(record.getErrors(), function(e){
                if(e.field==meta.name){
                    qtip.push((e.severity || 'ERROR') +': '+e.message);
                    cellMetaData.css += ' x-grid3-cell-invalid';
                }
            }, this);
        }

        if(meta.qtipRenderer){
            meta.qtipRenderer(qtip, data, cellMetaData, record, rowIndex, colIndex, store);
        }

        if(qtip.length){
            //ext3
            cellMetaData.tdAttr = "ext:qtip=\"" + Ext4.util.Format.htmlEncode(qtip.join('<br>')) + "\"";
            //ext4
            cellMetaData.tdAttr += " data-qtip=\"" + Ext4.util.Format.htmlEncode(qtip.join('<br>')) + "\"";
        }
    },

    //private
    lookupRenderer : function(meta, data, grid, record, rowIndex) {
        var lookupStore = LABKEY.ext.MetaHelper.getLookupStore(meta);
        if(!lookupStore){
            return '';
        }

        var lookupRecord;
        var recIdx = lookupStore.find(meta.lookup.keyColumn, data);
        if(recIdx != -1)
            lookupRecord = lookupStore.getAt(recIdx);

        if (lookupRecord)
            return lookupRecord.get(meta.lookup.displayColumn);
        else {
            //if store not loaded yet, retry rendering on store load
            if(grid && !lookupStore.fields){
                this.lookupStoreLoadListeners = this.lookupStoreLoadListeners || [];
                if(Ext4.Array.indexOf(this.lookupStoreLoadListeners, lookupStore.storeId) == -1){
                    lookupStore.on('load', function(store){
                        this.lookupStoreLoadListeners.remove(store.storeId);

                        grid.getView().refresh();

                    }, this, {single: true});
                    this.lookupStoreLoadListeners.push(lookupStore.storeId);
                }
            }
            if (data!==null){
                return "[" + data + "]";
            }
            else {
                return Ext4.isDefined(meta.lookupNullCaption) ? meta.lookupNullCaption : "[none]";
            }
        }
    },

    /**
     * Identify the proper name of a field using an input string such as an excel column label.  This helper will
     * perform a case-insensitive comparison of the field name, label, shortCaption and aliases.
     *
     * @name resolveFieldNameFromLabel
     * @function
     * @param (string) fieldName The string to search
     * @param (array / Ext.util.MixedCollection) metadata The fields to search
     * @returns {string} Returns the normalized field name or null if not found
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    resolveFieldNameFromLabel: function(fieldName, meta){
        var fnMatch = [];
        var aliasMatch = [];
        if(meta.hasOwnProperty('each'))
            meta.each(testField, this);
        else
            Ext.each(meta, testField, this);

        function testField(fieldMeta){
            if (LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.name)
                || LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.caption)
                || LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.shortCaption)
                || LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.label)
            ){
                fnMatch.push(fieldMeta.name);
                return false;  //exit here because it should only match 1 name
            }

            if(fieldMeta.importAliases){
                var aliases;
                if(Ext4.isArray(fieldMeta.importAliases))
                    aliases = fieldMeta.importAliases;
                else
                    aliases = fieldMeta.importAliases.split(',');

                Ext4.each(aliases, function(alias){
                    if(LABKEY.Utils.caseInsensitiveEquals(fieldName, alias))
                        aliasMatch.push(fieldMeta.name);  //continue iterating over fields in case a fieldName matches
                }, this);
            }
        }

        if(fnMatch.length==1)
            return fnMatch[0];
        else if (fnMatch.length > 1){
            alert('Ambiguous Field Label: '+fieldName);
            return fieldName;
        }
        else if (aliasMatch.length==1){
            return aliasMatch[0];
        }
        else {
            //alert('Unknown Field Label: '+fieldName);
            return null;
        }
    },

    //Newer Ext
//    labelableRenderTpl: [
//        '<tpl if="!hideLabel && !(!fieldLabel && hideEmptyLabel)">',
//            '<label id="{id}-labelEl"<tpl if="inputId"> for="{inputId}"</tpl> class="{labelCls}"',
//                '<tpl if="labelStyle"> style="{labelStyle}"</tpl>>',
//                '<tpl if="fieldLabel">{fieldLabel}' +
//                    '{labelSeparator}' +
//                    '<tpl if="helpPopup"> <a href="#" data-qtip="{helpPopup}"><span class="labkey-help-pop-up">?</span></a></tpl>' +
//                '</tpl>',
//            '</label>',
//        '</tpl>',
//        '<div class="{baseBodyCls} {fieldBodyCls}" id="{id}-bodyEl" role="presentation">{subTplMarkup}</div>',
//        '<div id="{id}-errorEl" class="{errorMsgCls}" style="display:none"></div>',
//        '<div class="{clearCls}" role="presentation"><!-- --></div>',
//        {
//            compiled: true,
//            disableFormats: true
//        }
//    ],

    //Ext4.02a
    //@override
    labelableRenderTpl: [
        '<tpl if="!hideLabel && !(!fieldLabel && hideEmptyLabel)">',
            '<label<tpl if="inputId"> for="{inputId}"</tpl> class="{labelCls}"<tpl if="labelStyle"> style="{labelStyle}"</tpl>>',
                '<tpl if="fieldLabel">{fieldLabel}{labelSeparator}' +
                    '<tpl if="helpPopup"> <a href="#" data-qtip="{helpPopup}"><span class="labkey-help-pop-up">?</span></a></tpl>' +
                '</tpl>',
            '</label>',
        '</tpl>',
        '<div class="{baseBodyCls} {fieldBodyCls}"<tpl if="inputId"> id="{baseBodyCls}-{inputId}"</tpl> role="presentation">{subTplMarkup}</div>',
        '<div class="{errorMsgCls}" style="display:none"></div>',
        '<div class="{clearCls}" role="presentation"><!-- --></div>',
        {
            compiled: true,
            disableFormats: true
        }
    ],

    //private
    findJsonType: function(fieldObj){
        var type = fieldObj.type || fieldObj.typeName;

        if (type=='DateTime')
            return 'date';
        else if (type=='Double')
            return 'float';
        else if (type=='Integer' || type=='int')
            return 'int';
        //if(type=='String')
        else
            return 'string';
    },

    /**
     * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in a details view.
     * If any of the following are true, it will not appear: hidden, isHidden
     * If shownInDetailsView is defined, it will take priority
     *
     * @name shouldShowInDetailsView
     * @function
     * @param (object) metadata The field metadata object
     * @returns {boolean} Returns whether the field show appear
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    shouldShowInDetailsView: function(meta){
        return meta.shownInDetailsView || (!meta.isHidden && !meta.hidden && meta.shownInDetailsView!==false)
    },

    /**
     * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in an insert view.
     * If any of the following are false, it will not appear: userEditable and autoIncrement
     * If any of the follow are true, it will not appear: hidden, isHidden
     * If shownInInsertView is defined, this will take priority over all
     *
     * @name shouldShowInDetailsView
     * @function
     * @param (object) metadata The field metadata object
     * @returns {boolean} Returns whether the field show appear
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    shouldShowInInsertView: function(meta){
        return meta.shownInInsertView || (!meta.isHidden && !meta.hidden && meta.shownInInsertView!==false && meta.userEditable!==false && !meta.autoIncrement)
    },

    /**
     * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in an update view.
     * If any of the following are false, it will not appear: userEditable and autoIncrement
     * If any of the follow are true, it will not appear: hidden, isHidden, readOnly
     * If shownInUpdateView is defined, this will take priority over all
     *
     * @name shouldShowInUpdateView
     * @function
     * @param (object) metadata The field metadata object
     * @returns {boolean} Returns whether the field show appear
     * @memberOf LABKEY.ext.MetaHelper#
     *
     */
    shouldShowInUpdateView: function(meta){
        return meta.shownInUpdateView || (!meta.isHidden && !meta.hidden && meta.shownInUpdateView!==false && meta.userEditable!==false && !meta.autoIncrement && meta.readOnly!==false)
    },

    //private
    //a shortcut for LABKEY.ext.MetaHelper.getLookupStore that doesnt require as complex a config object
    simpleLookupStore: function(c) {
        c.lookup = {
            containerPath: c.containerPath,
            schemaName: c.schemaName,
            queryName: c.queryName,
            viewName: c.viewName,
//            sort: c.sort,
            displayColumn: c.displayColumn,
            keyColumn: c.keyColumn
        };

        return LABKEY.ext.MetaHelper.getLookupStore(c);
    }

};

