/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function() {

    /**
     * @name LABKEY.ext4.Util
     * @class
     * Ext4 utilities, contains functions to return an Ext config object to create an Ext field based on
     * the supplied metadata.
     */
    Ext4.ns('LABKEY.ext4.Util');

    // TODO: Get these off the global 'Date' object
    Ext4.ns('Date.patterns');
    Ext4.applyIf(Date.patterns, {
        ISO8601Long:"Y-m-d H:i:s",
        ISO8601Short:"Y-m-d"
    });

    var warned = {};

    var Util = LABKEY.ext4.Util;

    var caseInsensitiveEquals = function(a, b) {
        a = String(a);
        b = String(b);
        return a.toLowerCase() == b.toLowerCase();
    };

    /**
     * This method takes an object that is/extends an Ext4.Container (e.g. Panels, Toolbars, Viewports, Menus) and
     * resizes it so the Container fits inside the its parent container.
     * @param extContainer - (Required) outer container which is the target to be resized
     * @param options - a set of options
     * @param options.skipWidth - true to skip updating width, default false
     * @param options.skipHeight - true to skip updating height, default false
     * @param options.paddingWidth - total width padding
     * @param options.paddingHeight - total height padding
     * @param options.offsetY - distance between bottom of page to bottom of component
     * @param options.overrideMinWidth - true to set ext container's minWidth value to calculated width, default false
     */
    // resizeToContainer -- defined in Ext4/ext-patches.js

    Ext4.apply(Util, {

        /**
         * A map to convert between jsonType and Ext type
         */
        EXT_TYPE_MAP: {
            'string': 'STRING',
            'int': 'INT',
            'float': 'FLOAT',
            'date': 'DATE',
            'boolean': 'BOOL'
        },
        /**
         * @lends LABKEY.ext4.Util
         */

        /**
         * @private
         * @param config
         */
        buildQtip: function(config) {
            var qtip = [];
            //NOTE: returned in the 9.1 API format
            if(config.record && config.record.raw && config.record.raw[config.meta.name] && config.record.raw[config.meta.name].mvValue){
                var mvValue = config.record.raw[config.meta.name].mvValue;

                //get corresponding message from qcInfo section of JSON and set up a qtip
                if(config.record.store && config.record.store.reader.rawData && config.record.store.reader.rawData.qcInfo && config.record.store.reader.rawData.qcInfo[mvValue])
                {
                    qtip.push(config.record.store.reader.rawData.qcInfo[mvValue]);
                    config.cellMetaData.css = "labkey-mv";
                }
                qtip.push(mvValue);
            }

            if (!config.record.isValid()){
                var errors = config.record.validate().getByField(config.meta.name);
                if (errors.length){
                    Ext4.Array.forEach(errors, function(e){
                        qtip.push(e.message);
                    }, this);
                }
            }

            if (config.meta.buildQtip) {
                config.meta.buildQtip({
                    qtip: config.qtip,
                    value: config.value,
                    cellMetaData: config.cellMetaData,
                    meta: config.meta,
                    record: config.record
                });
            }

            if (qtip.length) {
                qtip = Ext4.Array.unique(qtip);
                config.cellMetaData.tdAttr = config.cellMetaData.tdAttr || '';
                config.cellMetaData.tdAttr += " data-qtip=\"" + Ext4.util.Format.htmlEncode(qtip.join('<br>')) + "\"";
            }
        },

        /**
         * @private
         * @param store
         * @param fieldName
         */
        findFieldMetadata: function(store, fieldName) {
            var fields = store.model.prototype.fields;
            if(!fields)
                return null;

            return fields.get(fieldName);
        },

        /**
         * @private
         * @param fieldObj
         */
        findJsonType: function(fieldObj) {
            var type = fieldObj.type || fieldObj.typeName;

            if (type === 'DateTime')
                return 'date';
            else if (type === 'Double')
                return 'float';
            else if (type === 'Integer' || type === 'int')
                return 'int';
            return 'string';
        },

        /**
         * @private
         * @param store
         * @param col
         * @param config
         * @param grid
         */
        getColumnConfig: function(store, col, config, grid) {
            col = col || {};
            col.dataIndex = col.dataIndex || col.name;
            col.header = col.header || col.caption || col.label || col.name;

            var meta = Util.findFieldMetadata(store, col.dataIndex);
            if(!meta){
                return;
            }

            col.customized = true;

            col.hidden = meta.hidden;
            col.format = meta.extFormat;

            //this.updatable can override col.editable
            col.editable = config.editable && col.editable && meta.userEditable;

            if(col.editable && !col.editor)
                col.editor = Util.getGridEditorConfig(meta);

            col.renderer = Util.getDefaultRenderer(col, meta, grid);

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

        /**
         * @private
         * @param store
         * @param grid
         * @param config
         */
        getColumnsConfig: function(store, grid, config) {
            config = config || {};

            var fields = store.model.getFields();
            var columns = store.getColumns();
            var cols = [];

            Ext4.each(fields, function(field) {
                var col;

                if (field.shownInGrid === false)
                    return;

                for (var i=0;i<columns.length;i++){
                    var c = columns[i];
                    if (c.dataIndex == field.dataIndex) {
                        col = c;
                        break;
                    }
                }

                if (!col)
                    col = {dataIndex: field.dataIndex};

                //NOTE: In Ext4.1 if your store does not provide a key field, Ext will create a new column called 'id'
                //this is somewhat of a problem, since it is difficult to differentiate this as automatically generated
                var cfg = Util.getColumnConfig(store, col, config, grid);
                if (cfg)
                    cols.push(cfg);
            }, this);

            return cols;
        },

        /**
         * @private
         * @param displayValue
         * @param value
         * @param col
         * @param meta
         * @param record
         */
        getColumnUrl: function(displayValue, value, col, meta, record) {
            //wrap in <a> if url is present in the record's original JSON
            var url;
            if (meta.buildUrl) {
                url = meta.buildUrl({
                    displayValue: displayValue,
                    value: value,
                    col: col,
                    meta: meta,
                    record: record
                });
            }
            else if (record.raw && record.raw[meta.name] && record.raw[meta.name].url) {
                url = record.raw[meta.name].url;
            }
            return Ext4.util.Format.htmlEncode(url);
        },

        /**
         * This is designed to be called through either .getFormEditorConfig or .getFormEditor.
         * Uses the given meta-data to generate a field config object.
         *
         * This function accepts a collection of config parameters to be easily adapted to
         * various different metadata formats.
         *
         * Note: you can provide any Ext config options using the editorConfig or formEditorConfig objects
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
         * @param {boolean} [config.createIfDoesNotExist] If true, this field will be created in the store, even if it does not otherwise exist on the server. Can be used to force custom fields to appear in a grid or form or to pass additional information to the server at time of import
         * @param {function} [config.buildQtip] This function will be used to generate the qTip for the field when it appears in a grid instead of the default function.  It will be passed a single object as an argument.  This object has the following properties: qtip, data, cellMetaData, meta, record, store. Qtip is an array which will be merged to form the contents of the tooltip.  Your code should modify the array to alter the tooltip.  For example:
         * buildQtip: function(config){
         *      qtip.push('I have a tooltip!');
         *      qtip.push('This is my value: ' + config.value);
         * }
         * @param {function} [config.buildDisplayString] This function will be used to generate the display string for the field when it appears in a grid instead of the default function.  It will be passed the same argument as buildQtip()
         * @param {function} [config.buildUrl] This function will be used to generate the URL encapsulating the field
         * @param {string} [config.urlTarget] If the value is rendered in a LABKEY.ext4.EditorGridPanel (or any other component using this pathway), and it contains a URL, this will be used as the target of <a> tag.  For example, use _blank for a new window.
         * @param {boolean} [config.setValueOnLoad] If true, the store will attempt to set a value for this field on load.  This is determined by the defaultValue or getInitialValue function, if either is defined
         * @param {function} [config.getInitialValue] When a new record is added to this store, this function will be called on that field.  If setValueOnLoad is true, this will also occur on load.  It will be passed the record and metadata.  The advantage of using a function over defaultValue is that more complex and dynamic initial values can be created.  For example:
         *  //sets the value to the current date
         *  getInitialValue(val, rec, meta){
         *      return val || new Date()
         *  }
         * @param {boolean} [config.wordWrap] If true, when displayed in an Ext grid the contents of the cell will use word wrapping, as opposed to being forced to a single line
         *
         * Note: the follow Ext params are automatically defined based on the specified Labkey metadata property:
         * dataIndex -> name
         * editable -> userEditable && readOnly
         * header -> caption
         * xtype -> set within getDefaultEditorConfig() based on jsonType, unless otherwise provided
         */
        getDefaultEditorConfig: function(meta) {
            var field = {
                //added 'caption' for assay support
                fieldLabel       : Ext4.util.Format.htmlEncode(meta.label || meta.caption || meta.caption || meta.header || meta.name),
                originalConfig   : meta,
                //we assume the store's translateMeta() will handle this
                allowBlank       : (meta.allowBlank === true) || (meta.required !==true),
                //disabled: meta.editable===false,
                name             : meta.name,
                dataIndex        : meta.dataIndex || meta.name,
                value            : meta.value || meta.defaultValue,
                width            : meta.width,
                height           : meta.height,
                msgTarget        : 'qtip',
                validateOnChange : true
            };

            var helpPopup = meta.helpPopup || (function() {
                var array = [];

                if (meta.friendlyType)
                    array.push(meta.friendlyType);

                if (meta.description)
                    array.push(Ext4.util.Format.htmlEncode(meta.description));

                if (!field.allowBlank)
                    array.push("This field is required.");

                return array;
            }());

            if (Ext4.isArray(helpPopup))
                helpPopup = helpPopup.join('<br>');
            field.helpPopup = helpPopup;

            if (meta.hidden) {
                field.xtype = 'hidden';
                field.hidden = true;
            }
            else if (meta.editable === false) {
                field.xtype = 'displayfield';
            }
            else if (meta.lookup && meta.lookup['public'] !== false && meta.lookups !== false && meta.facetingBehaviorType != 'ALWAYS_OFF') {
                var l = meta.lookup;

                //test whether the store has been created.  create if necessary
                if (Ext4.isObject(meta.store) && meta.store.events) {
                    field.store = meta.store;
                }
                else {
                    field.store = Util.getLookupStore(meta);
                }

                Ext4.apply(field, {
                    // the purpose of this is to allow other editors like multiselect, checkboxGroup, etc.
                    xtype           : (meta.xtype || 'labkey-combo'),
                    forceSelection  : true,
                    typeAhead       : true,
                    queryMode       : 'local',
                    displayField    : l.displayColumn,
                    valueField      : l.keyColumn,
                    //NOTE: supported for non-combo components
                    initialValue    : field.value,
                    showValueInList : meta.showValueInList,
                    nullCaption     : meta.nullCaption
                });
            }
            else {
                switch (meta.jsonType) {
                    case "boolean":
                        field.xtype = meta.xtype || 'checkbox';
                            if (field.value === true){
                                field.checked = true;
                            }
                        break;
                    case "int":
                        field.xtype = meta.xtype || 'numberfield';
                        field.allowDecimals = false;
                        break;
                    case "float":
                        field.xtype = meta.xtype || 'numberfield';
                        field.allowDecimals = true;
                        break;
                    case "date":
                        field.xtype = meta.xtype || 'datefield';
                        field.format = meta.extFormat || Date.patterns.ISO8601Long;
                        field.altFormats = LABKEY.Utils.getDateAltFormats();
                        break;
                    case "string":
                        if (meta.inputType=='textarea') {
                            field.xtype = meta.xtype || 'textarea';
                            field.width = meta.width;
                            field.height = meta.height;
                            if (!this._textMeasure) {
                                this._textMeasure = {};
                                var ta = Ext4.DomHelper.append(document.body,{tag:'textarea', rows:10, cols:80, id:'_hiddenTextArea', style:{display:'none'}});
                                this._textMeasure.height = Math.ceil(Ext4.util.TextMetrics.measure(ta,"GgYyJjZ==").height * 1.2);
                                this._textMeasure.width  = Math.ceil(Ext4.util.TextMetrics.measure(ta,"ABCXYZ").width / 6.0);
                            }
                            if (meta.rows && !meta.height) {
                                if (meta.rows == 1) {
                                    field.height = undefined;
                                }
                                else {
                                    // estimate at best!
                                    var textHeight = this._textMeasure.height * meta.rows;
                                    if (textHeight) {
                                        field.height = textHeight;
                                    }
                                }
                            }
                            if (meta.cols && !meta.width) {
                                var textWidth = this._textMeasure.width * meta.cols;
                                if (textWidth) {
                                    field.width = textWidth;
                                }
                            }
                        }
                        else {
                            field.xtype = meta.xtype || 'textfield';
                        }
                        break;
                    default:
                        field.xtype = meta.xtype || 'textfield';
                }
            }

            return field;
        },

        /**
         * @private
         * @param col
         * @param meta
         * @param grid
         */
        getDefaultRenderer: function(col, meta, grid) {
            return function(value, cellMetaData, record, rowIndex, colIndex, store) {
                var displayValue = value;
                var cellStyles = [];
                var tdCls = [];

                //format value into a string
                if(!Ext4.isEmpty(value))
                    displayValue = Util.getDisplayString(value, meta, record, store);
                else
                    displayValue = value;

                displayValue = Ext4.util.Format.htmlEncode(displayValue);

                if(meta.buildDisplayString){
                    displayValue = meta.buildDisplayString({
                        displayValue: displayValue,
                        value: value,
                        col: col,
                        meta: meta,
                        cellMetaData: cellMetaData,
                        record: record,
                        store: store
                    });
                }

                //if meta.file is true, add an <img> for the file icon
                if (meta.file) {
                    displayValue = "<img src=\"" + LABKEY.Utils.getFileIconUrl(value) + "\" alt=\"icon\" title=\"Click to download file\"/>&nbsp;" + displayValue;
                    //since the icons are 16x16, cut the default padding down to just 1px
                    cellStyles.push('padding: 1px 1px 1px 1px');
                }

                //build the URL
                if(col.showLink !== false){
                    var url = Util.getColumnUrl(displayValue, value, col, meta, record);
                    if(url){
                        displayValue = "<a " + (meta.urlTarget ? "target=\""+meta.urlTarget+"\"" : "") + " href=\"" + url + "\">" + displayValue + "</a>";
                    }
                }

                if(meta.wordWrap && !col.hidden){
                    cellStyles.push('white-space:normal !important');
                }


                if (!record.isValid()){
                    var errs = record.validate().getByField(meta.name);
                    if (errs.length)
                        tdCls.push('labkey-grid-cell-invalid');
                }

                if ((meta.allowBlank === false || meta.nullable === false) && Ext4.isEmpty(value)){
                    tdCls.push('labkey-grid-cell-invalid');
                }

                if(cellStyles.length){
                    cellMetaData.style = cellMetaData.style ? cellMetaData.style + ';' : '';
                    cellMetaData.style += (cellStyles.join(';'));
                }

                if (tdCls.length){
                    cellMetaData.tdCls = cellMetaData.tdCls ? cellMetaData.tdCls + ' ' : '';
                    cellMetaData.tdCls += tdCls.join(' ');
                }

                Util.buildQtip({
                    displayValue: displayValue,
                    value: value,
                    meta: meta,
                    col: col,
                    record: record,
                    store: store,
                    cellMetaData: cellMetaData
                });

                return displayValue;
            }
        },

        /**
         * @private
         * @param value
         * @param meta
         * @param record
         * @param store
         */
        getDisplayString: function(value, meta, record, store) {
            var displayType = meta.displayFieldJsonType || meta.jsonType;
            var displayValue = value;
            var shouldCache;

            //NOTE: the labkey 9.1 API returns both the value of the field and the display value
            //the server is already doing the work, so we should rely on this
            //this does have a few problems:
            //if the displayValue equals the value, the API omits displayValue.  because we cant
            // count on the server returning the right value unless explicitly providing a displayValue,
            // we only attempt to use that
            if(record && record.raw && record.raw[meta.name]){
                if(Ext4.isDefined(record.raw[meta.name].displayValue))
                    return record.raw[meta.name].displayValue;
                // TODO: this needs testing before enabling.  would be nice if we could rely on this,
                // TODO: but i dont think we will be able to (dates, for example)
                // perhaps only try this for lookups?
                //else if(Ext4.isDefined(record.raw[meta.name].value))
                //    return record.raw[meta.name].value;
            }

            //NOTE: this is substantially changed over LABKEY.ext.FormHelper
            if(meta.lookup && meta.lookup['public'] !== false && meta.lookups!==false){
                //dont both w/ special renderer if the raw value is the same as the displayColumn
                if (meta.lookup.keyColumn != meta.lookup.displayColumn){
                    displayValue = Util.getLookupDisplayValue(meta, displayValue, record, store);
                    meta.usingLookup = true;
                    shouldCache = false;
                    displayType = 'string';
                }
            }

            if(meta.extFormatFn && Ext4.isFunction(meta.extFormatFn)){
                displayValue = meta.extFormatFn(displayValue);
            }
            else {
                if(!Ext4.isDefined(displayValue))
                    displayValue = '';
                switch (displayType){
                    case "date":
                        var date = new Date(displayValue);
                        //NOTE: java formats differ from ext
                        var format = meta.extFormat;
                        if(!format){
                            if (date.getHours() == 0 && date.getMinutes() == 0 && date.getSeconds() == 0)
                                format = "Y-m-d";
                            else
                                format = "Y-m-d H:i:s";
                        }
                        displayValue = Ext4.Date.format(date, format);
                        break;
                    case "int":
                        displayValue = (Ext4.util.Format.numberRenderer(meta.extFormat || this.format || '0'))(displayValue);
                        break;
                    case "boolean":
                        var t = this.trueText || 'true', f = this.falseText || 'false', u = this.undefinedText || ' ';
                        if(displayValue === undefined){
                            displayValue = u;
                        }
                        else if(!displayValue || displayValue === 'false'){
                            displayValue = f;
                        }
                        else {
                            displayValue = t;
                        }
                        break;
                    case "float":
                        displayValue = (Ext4.util.Format.numberRenderer(meta.extFormat || this.format || '0,000.00'))(displayValue);
                        break;
                    case "string":
                    default:
                        displayValue = !Ext4.isEmpty(displayValue) ? displayValue.toString() : "";
                }
            }

            // Experimental. cache the calculated value, so we dont need to recalculate each time.
            // This should get cleared by the store on update like any server-generated value
            if (shouldCache !== false) {
                record.raw = record.raw || {};
                if(!record.raw[meta.name])
                    record.raw[meta.name] = {};
                record.raw[meta.name].displayValue = displayValue;
            }

            return displayValue;
        },

        /**
         * Constructs an ext field component based on the supplied metadata.  Same as getFormEditorConfig, but actually constructs the editor.
         * The resulting editor is tailored for usage in a form, as opposed to a grid. Unlike getEditorConfig, if the metadata
         * contains a formEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         * @param {Object} meta as returned by {@link LABKEY.Query.selectRows}. See {@link LABKEY.Query.SelectRowsResults}.
         * @param {Object} config as returned by {@link LABKEY.Query.selectRows}. See {@link LABKEY.Query.SelectRowsResults}.
         * @return {Object} An Ext field component
         */
        getFormEditor: function(meta, config) {
            var editorConfig = Util.getFormEditorConfig(meta, config);
            return Ext4.ComponentMgr.create(editorConfig);
        },

        /**
         * Return an Ext config object to create an Ext field based on the supplied metadata.
         * The resulting config object is tailored for usage in a form, as opposed to a grid. Unlike getEditorConfig, if the metadata
         * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         * @param {Object} meta as returned by {@link LABKEY.Query.selectRows}. See {@link LABKEY.Query.SelectRowsResults}.
         * @param {Object} [config] as returned by {@link LABKEY.Query.selectRows}. See {@link LABKEY.Query.SelectRowsResults}.
         * @returns {Object} An Ext 4.x config object
         */
        getFormEditorConfig: function(meta, config) {
            var editor = Util.getDefaultEditorConfig(meta);

            // now we allow overrides of default behavior, in order of precedence
            if (meta.editorConfig)
                Ext4.Object.merge(editor, meta.editorConfig);
            if (meta.formEditorConfig)
                Ext4.Object.merge(editor, meta.formEditorConfig);
            if (config)
                Ext4.Object.merge(editor, config);

            return editor;
        },

        /**
         * Constructs an ext field component based on the supplied metadata.  Same as getFormEditorConfig, but actually constructs the editor.
         * The resulting editor is tailored for usage in a grid, as opposed to a form. Unlike getFormEditorConfig or getEditorConfig, if the metadata
         * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         * @private
         * @param meta
         * @param config
         * @return {Object} An Ext field component
         */
        getGridEditor: function(meta, config) {
            var editorConfig = Util.getGridEditorConfig(meta, config);
            return Ext4.ComponentMgr.create(editorConfig);
        },

        /**
         * @private
         * Return an Ext config object to create an Ext field based on the supplied metadata.
         * The resulting config object is tailored for usage in a grid, as opposed to a form. Unlike getFormEditorConfig or getEditorConfig, if the metadata
         * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         *
         * @name getGridEditorConfig
         * @function
         * @returns {object} Returns an Ext config object
         */
        getGridEditorConfig: function(meta, config) {
            //this produces a generic editor
            var editor = Util.getDefaultEditorConfig(meta);

            //now we allow overrides of default behavior, in order of precedence
            if (meta.editorConfig) {
                Ext4.Object.merge(editor, meta.editorConfig);
            }

            //note: this will screw up cell editors
            delete editor.fieldLabel;

            if (meta.gridEditorConfig) {
                Ext4.Object.merge(editor, meta.gridEditorConfig);
            }
            if (config) {
                Ext4.Object.merge(editor, config);
            }

            return editor;
        },

        /**
         * @private
         * NOTE: it would be far better if we did not need to pass the store. This is done b/c we need to fire the
         * 'datachanged' event once the lookup store loads. A better idea would be to force the store/grid to listen
         * for event fired by the lookupStore or somehow get the metadata to fire events itself.
         * @param meta
         * @param data
         * @param record
         * @param store
         */
        getLookupDisplayValue: function(meta, data, record, store) {
            var lookupStore = Util.getLookupStore(meta);
            if(!lookupStore){
                return '';
            }

            meta.lookupStore = lookupStore;
            var lookupRecord;

            //NOTE: preferentially used snapshot instead of data to allow us to find the record even if the store is currently filtered
            var records = lookupStore.snapshot || lookupStore.data;
            var matcher = records.createValueMatcher((data == null ? '' : data), false, true, true);
            var property = meta.lookup.keyColumn;
            var recIdx = records.findIndexBy(function(o){
                return o && matcher.test(o.get(property));
            }, null);

            if (recIdx != -1)
                lookupRecord = records.getAt(recIdx);

            if (lookupRecord){
                return lookupRecord.get(meta.lookup.displayColumn);
            }
            else {
                if (data!==null){
                    return "[" + data + "]";
                }
                else {
                    return Ext4.isDefined(meta.nullCaption) ? meta.nullCaption : "[none]";
                }
            }
        },

        /**
         * @private
         * @param storeId
         * @param c
         */
        getLookupStore: function(storeId, c) {
            if (!Ext4.isString(storeId)) {
                c = storeId;
                storeId = Util.getLookupStoreId(c);
            }

            if (Ext4.isObject(c.store) && c.store.events) {
                return c.store;
            }

            var store = Ext4.StoreMgr.lookup(storeId);
            if (!store) {
                var config = c.store || Util.getLookupStoreConfig(c);
                config.storeId = storeId;
                store = Ext4.create('LABKEY.ext4.data.Store', config);
            }
            return store;
        },

        /**
         * @private
         * @param c
         */
        getLookupStoreConfig: function(c) {
            var l = c.lookup;

            // normalize lookup
            l.queryName = l.queryName || l.table;
            l.schemaName = l.schemaName || l.schema;

            if (l.schemaName == 'core' && l.queryName =='UsersData') {
                l.queryName = 'Users';
            }

            var config = {
                xtype: "labkeystore",
                storeId: Util.getLookupStoreId(c),
                containerFilter: 'CurrentOrParentAndWorkbooks',
                schemaName: l.schemaName,
                queryName: l.queryName,
                containerPath: l.container || l.containerPath || LABKEY.container.path,
                autoLoad: true
            };

            if (l.viewName) {
                config.viewName = l.viewName;
            }

            if (l.filterArray) {
                config.filterArray = l.filterArray;
            }

            if (l.columns) {
                config.columns = l.columns;
            }
            else {
                var columns = [];
                if (l.keyColumn) {
                    columns.push(l.keyColumn);
                }
                if (l.displayColumn && l.displayColumn != l.keyColumn) {
                    columns.push(l.displayColumn);
                }
                if (columns.length == 0) {
                    columns = ['*'];
                }
                config.columns = columns;
            }

            if (l.sort) {
                config.sort = l.sort;
            }
            else if (l.sort !== false) {
                config.sort = l.displayColumn;
            }

            return config;
        },

        /**
         * @private
         * @param c
         */
        getLookupStoreId: function(c) {
            if (c.store && c.store.storeId) {
                return c.store.storeId;
            }

            if (c.lookup) {
                return c.lookup.storeId || [
                    c.lookup.schemaName || c.lookup.schema,
                    c.lookup.queryName || c.lookup.table,
                    c.lookup.keyColumn,
                    c.lookup.displayColumn
                ].join('||');
            }

            return c.name;
        },

        /**
         * @private
         * EXPERIMENTAL.  Returns the fields from the passed store
         * @param store
         * @returns {Ext.util.MixedCollection} The fields associated with this store
         */
        getStoreFields: function(store) {
            return store.proxy.reader.model.prototype.fields;
        },

        /**
         * @private
         * @param store
         * @return {boolean} Whether the store has loaded
         */
        hasStoreLoaded: function(store) {
            return store.proxy && store.proxy.reader && store.proxy.reader.rawData;
        },

        /**
         * @private
         * Identify the proper name of a field using an input string such as an excel column label.  This helper will
         * perform a case-insensitive comparison of the field name, label, caption, shortCaption and aliases.
         * @param {string} fieldName The string to search
         * @param {Array/Ext.util.MixedCollection} metadata The fields to search
         * @return {string} The normalized field name or null if not found
         */
        resolveFieldNameFromLabel: function(fieldName, meta) {
            var fnMatch = [];
            var aliasMatch = [];

            var testField = function(fieldMeta) {
                if (caseInsensitiveEquals(fieldName, fieldMeta.name)
                    || caseInsensitiveEquals(fieldName, fieldMeta.caption)
                    || caseInsensitiveEquals(fieldName, fieldMeta.shortCaption)
                    || caseInsensitiveEquals(fieldName, fieldMeta.label)
                ){
                    fnMatch.push(fieldMeta.name);
                    return false;  //exit here because it should only match 1 name
                }

                if (fieldMeta.importAliases) {
                    var aliases;
                    if(Ext4.isArray(fieldMeta.importAliases))
                        aliases = fieldMeta.importAliases;
                    else
                        aliases = fieldMeta.importAliases.split(',');

                    Ext4.each(aliases, function(alias){
                        if (caseInsensitiveEquals(fieldName, alias))
                            aliasMatch.push(fieldMeta.name);  //continue iterating over fields in case a fieldName matches
                    }, this);
                }
            };

            if (meta.hasOwnProperty('each')) {
                meta.each(testField, this);
            }
            else {
                Ext4.each(meta, testField, this);
            }

            if (fnMatch.length==1) {
                return fnMatch[0];
            }
            else if (fnMatch.length > 1) {
                return null;
            }
            else if (aliasMatch.length==1) {
                return aliasMatch[0];
            }
            return null;
        },

        /**
         * @private
         * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in a details view.
         * If any of the following are true, it will not appear: hidden, isHidden
         * If shownInDetailsView is defined, it will take priority
         * @param {Object} metadata The field metadata object
         * @return {boolean} Whether the field should appear in the default details view
         */
        shouldShowInDetailsView: function(metadata){
            return Ext4.isDefined(metadata.shownInDetailsView) ? metadata.shownInDetailsView :
                (!metadata.isHidden && !metadata.hidden && metadata.shownInDetailsView!==false);
        },

        /**
         * @private
         * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in an insert view.
         * If any of the following are false, it will not appear: userEditable and autoIncrement
         * If any of the follow are true, it will not appear: hidden, isHidden
         * If shownInInsertView is defined, this will take priority over all
         * @param {Object} metadata The field metadata object
         * @return {boolean} Whether the field should appear in the default insert view
         */
        shouldShowInInsertView: function(metadata){
            return Ext4.isDefined(metadata.shownInInsertView) ?  metadata.shownInInsertView :
                (!metadata.calculated && !metadata.isHidden && !metadata.hidden && metadata.userEditable!==false && !metadata.autoIncrement);
        },

        /**
         * @private
         * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in an update view.
         * If any of the following are false, it will not appear: userEditable and autoIncrement
         * If any of the follow are true, it will not appear: hidden, isHidden, readOnly
         * If shownInUpdateView is defined, this will take priority over all
         * @param {Object} metadata The field metadata object
         * @return {boolean} Whether the field should appear
         */
        shouldShowInUpdateView: function(metadata) {
            return Ext4.isDefined(metadata.shownInUpdateView) ? metadata.shownInUpdateView :
                (!metadata.calculated && !metadata.isHidden && !metadata.hidden && metadata.userEditable!==false && !metadata.autoIncrement && metadata.readOnly!==false)
        },

        /**
         * @private
         * Shortcut for LABKEY.ext4.Util.getLookupStore that doesn't require as complex a config object
         * @param {Object} config Configuration object for an Ext.data.Store
         * @return {Ext.data.Store} The store
         */
        simpleLookupStore: function(config) {
            config.lookup = {
                containerPath : config.containerPath,
                schemaName    : config.schemaName,
                queryName     : config.queryName,
                viewName      : config.viewName,
                displayColumn : config.displayColumn,
                keyColumn     : config.keyColumn
            };

            return Util.getLookupStore(config);
        },

        /**
         * @private
         * The intention of this method is to provide a standard, low-level way to translating Labkey metadata names into ext ones.
         * @param field
         */
        translateMetadata: function(field) {
            field.fieldLabel = Ext4.util.Format.htmlEncode(field.label || field.caption || field.header || field.name);
            field.dataIndex  = field.dataIndex || field.name;
            field.editable   = (field.userEditable!==false && !field.readOnly && !field.autoIncrement && !field.calculated);
            field.allowBlank = (field.nullable === true) || (field.required !== true);
            field.jsonType   = field.jsonType || Util.findJsonType(field);

            //this will convert values from strings to the correct type (such as booleans)
            if (!Ext4.isEmpty(field.defaultValue)){
                var type = Ext4.data.Types[LABKEY.ext4.Util.EXT_TYPE_MAP[field.jsonType]];
                if (type){
                    field.defaultValue = type.convert(field.defaultValue);
                }
            }
        },

        /**
         * This method takes an object that is/extends an Ext4.Container (e.g. Panels, Toolbars, Viewports, Menus) and
         * resizes it so the Container fits inside the viewable region of the window. This is generally used in the case
         * where the Container is not rendered to a webpart but rather displayed on the page itself (e.g. SchemaBrowser,
         * manageFolders, etc).
         * @param extContainer - (Required) outer container which is the target to be resized
         * @param width - (Required) width of the viewport. In many cases, the window width. If a negative width is passed than
         *                           the width will not be set.
         * @param height - (Required) height of the viewport. In many cases, the window height. If a negative height is passed than
         *                           the height will not be set.
         * @param paddingX - distance from the right edge of the viewport. Defaults to 35.
         * @param paddingY - distance from the bottom edge of the viewport. Defaults to 35.
         */
        resizeToViewport: function(extContainer, width, height, paddingX, paddingY, offsetX, offsetY)
        {
            LABKEY.ext4.Util.resizeToContainer.apply(this, arguments);
        }
    });
}());
