/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.2
 * @license Copyright (c) 2009-2010 LabKey Corporation
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
Ext.namespace("LABKEY","LABKEY.ext");


/**
 * Constructs a new LabKey FormPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://www.extjs.com/deploy/dev/docs/?class=Ext.form.FormPanel">Ext.form.FormPanel</a>.
 * This class understands various LabKey metadata formats and can simplify generating basic forms.
 * When a LABKEY.ext.FormPanel is created with additional metadata, it will try to intelligently construct fields
 * of the appropriate type.
 * 
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 * @constructor
 * @augments Ext.form.FormPanel
 * @param config Configuration properties. This may contain any of the configuration properties supported
 * by the <a href="http://www.extjs.com/deploy/dev/docs/?class=Ext.form.FormPanel">Ext.form.FormPanel</a>,
 * plus those listed here.
 * Also, items may specify a ToolTip config in the helpPopup property to display a LabKey-style "?" help tip.
 * Note that the selectRowsResults object (see {@link LABKEY.Query.SelectRowsResults}) includes both columnModel and metaData, so you don't need to specify all three.
 * @param {object} [config.metaData] as returned by {@link LABKEY.Query.selectRows}. See {@link LABKEY.Query.SelectRowsResults}.
 * @param {object} [config.columnModel] as returned by {@link LABKEY.Query.selectRows}. See {@link LABKEY.Query.SelectRowsResults}.
 * @param {LABKEY.Query.SelectRowsResults} [config.selectRowsResults] as returned by {@link LABKEY.Query.selectRows}.
 * @param {boolean} [config.addAllFields='false'] If true, all fields specified in the metaData are automatically created.
 * @param {object} [config.values] Provides initial values to populate the form.
 * @param {object} [config.errorEl] If specified, form errors will be written to this element; otherwise, a MsgBox will be used.
 * @param {string} [config.containerPath] Alternate default container for queries (e.g. for lookups)
 * @param {boolean} [config.lazyCreateStore] If false, any lookup stores will be created immediately.  If true, any lookup stores will be created when the component is created. (default true)
 *
 * @example
&lt;script type="text/javascript"&gt;
    function onSuccess(data) // e.g. callback from Query.selectRows
    {
        function submitHandler(formPanel)
        {
            var form = formPanel.getForm();
            if (form.isValid())
            {
                var rows = formPanel.getFormValues();
                save(rows);
            }
            else
            {
                Ext.MessageBox.alert("Error Saving", "There are errors in the form.");
            }
        }

        var formPanel = new LABKEY.ext.FormPanel(
        {
            selectRowsResults:data,
            addAllFields:true,
            buttons:[{text:"Submit", handler: function (b, e) { submitHandler(formPanel); }}, {text: "Cancel", handler: cancelHandler}],
            items:[{name:'myField', fieldLabel:'My Field', helpPopup:{title:'help', html:'read the manual'}}]
        });
        formPanel.render('formDiv');
    }
&lt;/script&gt;
&lt;div id='formDiv'/&gt;
 */

LABKEY.ext.FormPanel = Ext.extend(Ext.form.FormPanel,
{
    constructor : function(config)
    {
        this.allFields = this.initFieldDefaults(config);
        return LABKEY.ext.FormPanel.superclass.constructor.call(this, config);
    },

    defaultType : 'textfield',
    allFields : [],

    initComponent : function()
    {
        LABKEY.ext.FormPanel.superclass.initComponent.call(this);
        this.addEvents(
            'beforeapplydefaults',
            'applydefaults'
        );

        // add all fields that we're not added explicitly
        if (this.addAllFields && this.allFields.length)
        {
            // get a list of fields that were already constructed
            var existing = {};
            if (this.items)
            {
                this.items.each(function(c)
                {
                    if (c.isFormField)
                    {
                        var name = c.hiddenName || c.name;
                        existing[name] = name;
                    }
                });
            }
            for (var i=0;i<this.allFields.length;i++)
            {
                var c = this.allFields[i];
                var name = c.hiddenName || c.name;
                if (!existing[name])
                    this.add(c);
            }
        }
    },


    /* called from Ext.Container.initComponent() when adding items, before onRender() */
    applyDefaults : function(c)
    {
        this.fireEvent('beforeapplydefaults', this, c);
        if (this.fieldDefaults)
        {
            if (typeof c == 'string')
            {
            }
            else if (!c.events)
            {
                var name = c.name;
                if (name && this.fieldDefaults[name])
                    Ext.applyIf(c, this.fieldDefaults[name]);
            }
            else
            {
            }
        }
        // check for helpPopup
        if (c.fieldLabel && c.helpPopup)
        {
            var help;
            var id = "helpPopup-" + (++Ext.Component.AUTO_ID);
            if (typeof c.helpPopup.show != "function")
                c.helpPopup = new Ext.ToolTip(Ext.applyIf(c.helpPopup,{autoHide:true, closable:false, minWidth:200}));
            c.helpPopup.target = id;
            c.labelSeparator = "<a id=" + id + " tabindex=\"-1\" href=\"javascript:void(0);\"><span class=\"labkey-help-pop-up\" style=\"font-size:10pt;\"><sup>?</sup></span></a>";
        }

        var applied = LABKEY.ext.FormPanel.superclass.applyDefaults.call(this, c);
        this.fireEvent('applydefaults', this, applied);
        return applied;
    },

    /* gets called before doLayout() */
    onRender : function(ct, position)
    {
        LABKEY.ext.FormPanel.superclass.onRender.call(this, ct, position);
        this.el.addClass('extContainer');
    },


    /* labels are rendered as part of layout */
    doLayout : function()
    {
        LABKEY.ext.FormPanel.superclass.doLayout.call(this);
        var fn = function(c)
        {
            if (c.helpPopup && c.helpPopup.target)
            {
                // First line: open on click; Second line: open on hover
                //Ext.get(c.helpPopup.target).on("click", c.helpPopup.onTargetOver, c.helpPopup);
                c.helpPopup.initTarget(c.helpPopup.target);
            }
            if (c.items)
                c.items.each(fn);
        };
        this.items.each(fn);
    },


    // private
    initFieldDefaults : function(config)
    {
        var columnModel = config.columnModel;
        var metaData = config.metaData;
        var properties = config.properties;

        if (config.selectRowsResults)
        {
            if (!columnModel)
                columnModel = config.selectRowsResults.columnModel;
            if (!metaData)
                config.metaData = config.selectRowsResults.metaData;
            if (config.selectRowsResults.rowCount)
                config.values = config.selectRowsResults.rows;
        }
        var fields = config.metaData ? config.metaData.fields : null;

        var defaults = config.fieldDefaults = config.fieldDefaults || {};
        var items = [], i;

        function findColumn(id) {
            if (columnModel)
                for (var i = 0; i < columnModel.length; i++)
                    if (columnModel[i].dataIndex == id)
                        return columnModel[i];
            return null;
        }

        if (config.values)
        {
            if (!Ext.isArray(config.values))
                config.values = [ config.values ];

            var values = config.values;

            // UNDONE: primary keys should be readonly when editing multiple rows.

            var multiRowEdit = values.length > 1;
            for (i = 0; i < values.length; i++)
            {
                var vals = values[i];
                for (var id in vals)
                {
                    if (!(id in defaults))
                        defaults[id] = {};

                    // In multi-row edit case: skip if we've already discovered the values for this id are different across rows.
                    if (multiRowEdit && defaults[id].allRowsSameValue === false)
                        continue;

                    var v = vals[id];
                    if (typeof v == 'function')
                        continue;
                    if (v && typeof v == 'object' && 'value' in v)
                        v = v.value;

                    if ('xtype' in defaults[id] && defaults[id].xtype == 'checkbox')
                    {
                        var checked = v ? true : false;
                        if (v == "false")
                            v = false;
                        if (multiRowEdit && i > 0 && v != defaults[id].checked)
                        {
                            defaults[id].checked = false;
                            defaults[id].allRowsSameValue = false;

                            // UNDONE: Ext checkboxes don't have an 'unset' state
                            // Don't require a value for this field.
                            var col = findColumn(id);
                            if (col)
                                col.required = false;
                        }
                        else
                            defaults[id].checked = v;
                    }
                    else
                    {
                        if (multiRowEdit && i > 0 && v != defaults[id].value)
                        {
                            defaults[id].value = undefined;
                            defaults[id].allRowsSameValue = false;
                            defaults[id].emptyText = "Selected rows have different values for this field.";

                            // Don't require a value for this field. Allows a '[none]' entry for ComboBox and empty text fields.
                            var col = findColumn(id);
                            if (col)
                                col.required = false;
                        }
                        else
                            defaults[id].value = v;
                    }
                }
            }
        }

        if (fields || properties)
        {
            var count = fields ? fields.length : properties.length;
            for (i=0 ; i<count ; i++)
            {
                var field = this.getFieldEditorConfig(
                        {
                            containerPath: (config.containerPath || LABKEY.container.path),
                            lazyCreateStore: config.lazyCreateStore
                        },
                        fields?fields[i]:{},
                        properties?properties[i]:{},
                        columnModel?columnModel[i]:{}
                        );
                var name = field.originalConfig.name;
                defaults[name] = Ext.applyIf(defaults[name] || {}, field);
                items.push({name:name});
            }
        }

        return items;
    },

    getFieldEditorConfig : function ()
    {
        return LABKEY.ext.FormHelper.getFieldEditorConfig.apply(null, arguments);
    },

    // we want to hook error handling to provide a mechanism to show form errors (vs field errors)
    // form errors are reported on imaginary field named "_form"
    createForm : function()
    {
        var f = LABKEY.ext.FormPanel.superclass.createForm.call(this);
        f.formPanel = this;
        f.findField = function(id)
        {
            if (id == "_form")
                return this.formPanel;
            return Ext.form.BasicForm.prototype.findField.call(this,id);
        };
        return f;
    },

    // Look for form level errors that BasicForm won't handle
    // CONSIDER: move implementation to getForm().markInvalid()
    // CONSIDER: find 'unbound' errors and move to form level
    markInvalid : function(errors)
    {
        var formMessage;

        if (typeof errors == "string")
        {
            formMessage = errors;
            errors = null;
        }
        else if (Ext.isArray(errors))
        {
           for(var i = 0, len = errors.length; i < len; i++)
           {
               var fieldError = errors[i];
               if (!("id" in fieldError) || "_form" == fieldError.id)
                   formMessage = fieldError.msg;
           }
        }
        else if (typeof errors == "object" && "_form" in errors)
        {
            formMessage = errors._form;
        }

        if (errors)
        {
            this.getForm().markInvalid(errors);
        }

        if (formMessage)
        {
            if (this.errorEl)
                Ext.get(this.errorEl).update(Ext.util.Format.htmlEncode(formMessage));
            else
               Ext.Msg.alert("Error", formMessage);
        }
    },

    /**
     * Returns an Array of form value Objects.  The returned values will first be populated with
     * with {@link #values} or {@link #selectRowsResponse.values} then with the form's values.
     * If the form was not initially populated with {@link #values}, a signle element Array with
     * just the form's values will be returned.
     */
    getFormValues : function ()
    {
        var fieldValues = this.getForm().getFieldValues(true);
        for (var key in fieldValues)
        {
            if (typeof fieldValues[key] == "string")
                fieldValues[key] = fieldValues[key].trim();
        }

        var initialValues = this.initialConfig.values || [];
        var len = initialValues.length || 1;
        var result = [];
        for (var i = 0; i < len; i++)
        {
            var data = {};
            var initialVals = initialValues[i];
            if (initialVals)
            {
                for (var key in initialVals)
                {
                    var v = initialVals[key];
                    if (v && typeof v == 'object' && 'value' in v)
                        v = v.value;
                    data[key] = v;
                }
            }
            Ext.apply(data, fieldValues);
            result.push(data);
        }
        return result;
    }
});


Ext.ns("Date.patterns");
Ext.applyIf(Date.patterns,{
    ISO8601Long:"Y-m-d H:i:s",
    ISO8601Short:"Y-m-d"
});


LABKEY.ext.FormHelper =
{
    _textMeasure : null,

    /**
     * Uses the given meta-data to generate a field config object.
     *
     * This function accepts a mish-mash of config parameters to be easily adapted to
     * various different metadata formats.
     *
     * @param {string} [config.type] e.g. 'string','int','boolean','float', or 'date'
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
     * @param {object}  [config.ext] is a standard Ext config object that will be merged with the computed field config
     *      e.g. ext:{width:120, tpl:new Ext.Template(...)}
     * @param {object} [config.store] advanced! Pass in your own custom store for a lookup field
     * @param {boolean} [config.lazyCreateStore] If false, the store will be created immediately.  If true, the store will be created when the component is created. (default true)
     *
     * Will accept multiple config parameters which will be combined.
     *
     * @private
     */
    getFieldEditorConfig: function(c)
    {
        /* CONSIDER for 10.3, new prototype (with backward compatibility check)
         * getFieldEditorConfig(extConfig, [metadata,...])
         */

        // Combine the metadata provided into one config object
        var config = {editable:true, required:false, ext:{}};
        for (var i=arguments.length-1 ; i>= 0 ; --i)
        {
            var ext = Ext.apply(config.ext, arguments[i].ext);
            Ext.apply(config, arguments[i]);
            config.ext = ext;
        }

        var h = Ext.util.Format.htmlEncode;
        var lc = function(s){return !s?s:Ext.util.Format.lowercase(s);};

        config.type = lc(config.jsonType) || lc(config.type) || lc(config.typeName) || 'string';

        var field =
        {
            //added 'caption' for assay support
            fieldLabel: h(config.label) || h(config.caption) || h(config.header) || h(config.name),
            originalConfig: config,
            allowBlank: !config.required,
            disabled: !config.editable
        };

        if (config.tooltip && !config.helpPopup)
            field.helpPopup = { html: config.tooltip };

        if (config.lookup && false !== config.lookups)
        {
            var l = config.lookup;

            if (Ext.isObject(config.store) && config.store.events)
                field.store = config.store;
            else
                field.store = LABKEY.ext.FormHelper.getLookupStoreConfig(config);

            if (field.store && config.lazyCreateStore === false)
                field.store = LABKEY.ext.FormHelper.getLookupStore(field);

            Ext.apply(field, {
                xtype: 'combo',
                forceSelection:true,
                typeAhead: false,
                hiddenName: config.name,
                hiddenId : (new Ext.Component()).getId(),
                triggerAction: 'all',
                displayField: l.displayColumn,
                valueField: l.keyColumn,
                tpl : '<tpl for="."><div class="x-combo-list-item">{[values["' + l.displayColumn + '"]]}</div></tpl>', //FIX: 5860
                listClass: 'labkey-grid-editor'
            });
        }
        else if (config.hidden)
        {
            field.xtype = 'hidden';
        }
        else
        {
            switch (config.type)
            {
                case "boolean":
                    field.xtype = 'checkbox';
                    break;
                case "int":
                    field.xtype = 'numberfield';
                    field.allowDecimals = false;
                    break;
                case "float":
                    field.xtype = 'numberfield';
                    field.allowDecimals = true;
                    break;
                case "date":
                    field.xtype = 'datefield';
                    field.format = Date.patterns.ISO8601Long;
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
                    if (config.inputType=='textarea')
                    {
                        field.xtype = 'textarea';
                        field.width = 500;
                        field.height = 60;
                        if (!this._textMeasure)
                        {
                            this._textMeasure = {};
                            var ta = Ext.DomHelper.append(document.body,{tag:'textarea', rows:10, cols:80, id:'_hiddenTextArea', style:{display:'none'}});
                            this._textMeasure.height = Math.ceil(Ext.util.TextMetrics.measure(ta,"GgYyJjZ==").height * 1.2);
                            this._textMeasure.width  = Math.ceil(Ext.util.TextMetrics.measure(ta,"ABCXYZ").width / 6.0);
                        }
                        if (config.rows)
                        {
                            if (config.rows == 1)
                                field.height = undefined;
                            else
                            {
                                // estimate at best!
                                var textHeight =  this._textMeasure.height * config.rows;
                                if (textHeight)
                                    field.height = textHeight;
                            }
                        }
                        if (config.cols)
                        {
                            var textWidth = this._textMeasure.width * config.cols;
                            if (textWidth)
                                field.width = textWidth;
                        }

                    }
                    break;
                default:
            }

        }

        if (config.ext)
            Ext.apply(field,config.ext);

        return field;
    },

    /**
     * same as getFieldEditorConfig, but actually constructs the editor
     */
    getFieldEditor : function(config, defaultType)
    {
        var field = LABKEY.ext.FormHelper.getFieldEditorConfig(config);
        return Ext.ComponentMgr.create(field, defaultType || 'textfield');
    },

    // private
    getLookupStore : function(storeId, c)
    {
        if (typeof(storeId) != 'string')
        {
            c = storeId;
            storeId = LABKEY.ext.FormHelper.getLookupStoreId(c);
        }

        // Check if store has already been created.
        if (Ext.isObject(c.store) && c.store.events)
            return c.store;

        var store = Ext.StoreMgr.lookup(storeId);
        if (!store)
        {
            var config = c.store || LABKEY.ext.FormHelper.getLookupStoreConfig(c);
            config.storeId = storeId;
            store = Ext.create(config, 'labkey-store');
        }
        return store;
    },

    // private
    // Ext.StoreMgr uses 'storeId' to lookup stores.  A store will add itself to the Ext.StoreMgr when constructed.
    getLookupStoreId : function (c)
    {
        if (c.store && c.store.storeId)
            return c.store.storeId;

        if (c.lookup)
            return [c.lookup.schemaName || c.lookup.schema , c.lookup.queryName || c.lookup.table, c.lookup.keyColumn, c.lookup.displayColumn].join('||');

        return c.name;
    },

    // private
    getLookupStoreConfig : function(c)
    {
        // UNDONE: avoid self-joins
        // UNDONE: core.UsersData
        // UNDONE: container column
        var l = c.lookup;
        // normalize lookup
        l.queryName = l.queryName || l.table;
        l.schemaName = l.schemaName || l.schema;

        if (l.schemaName == 'core' && l.queryName =='UsersData')
            l.queryName = 'Users';
        
        var config = {
            xtype: "labkey-store",
            storeId: LABKEY.ext.FormHelper.getLookupStoreId(c),
            schemaName: l.schemaName,
            queryName: l.queryName,
            containerPath: l.container || c.containerPath || LABKEY.container.path,
            autoLoad: true
        };

        if (l.viewName)
            config.viewName = l.viewName;

        if (l.columns)
            config.columns = l.columns;
        else
        {
            var columns = [];
            if (l.keyColumn)
                columns.push(l.keyColumn);
            if (l.displayColumn && l.displayColumn != l.keyColumn)
                columns.push(l.displayColumn);
            if (columns.length == 0)
                columns = ['*'];
            config.columns = columns;
        }

        if (l.sort)
            config.sort = l.sort;
        
        if (!c.required)
        {
            config.nullRecord = {
                displayColumn: l.displayColumn,
                nullCaption: c.lookupNullCaption || "[none]"
            };
        }

        return config;
    },

    /**
     * Note: this is an experimental API that may change unexpectedly in future releases.
     * Validate a form value against the json type.  Error alerts will be displayed.
     * @param type The json type ("int", "float", "date", or "boolean")
     * @param value The value to test.
     * @param colName The column name to use in error messages.
     * @return undefined if not valid otherwise a normalized string value for the type.
     */
    validate : function (type, value, colName)
    {
        if (type == "int")
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
        else if (type == "float")
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
        else if (type == "date")
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
        else if (type == "boolean")
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
};


LABKEY.ext.Checkbox = Ext.extend(Ext.form.Checkbox,
{
    onRender : function(ct, position)
    {
        LABKEY.ext.Checkbox.superclass.onRender.call(this, ct, position);
        if (this.name)
        {
            var marker = LABKEY.fieldMarker + this.name;
            Ext.DomHelper.insertAfter(this.el, {tag:"input", type:"hidden", name:marker});
        }
    }
});


LABKEY.ext.DatePicker = Ext.extend(Ext.DatePicker,
{
// Ext.DatePicker does not render properly on WebKit (safari,chrome)
// fixed in Ext 3
//    update : function(date, forceRefresh)
//    {
//        Ext.DatePicker.prototype.update.call(this, date, forceRefresh);
//        if (Ext.isSafari || ('isWebKit' in Ext && Ext.isWebKit))
//        {
//            var w = 180;
//            this.el.setWidth(w + this.el.getBorderWidth("lr"));
//            Ext.fly(this.el.dom.firstChild).setWidth(w);
//        }
//    }
});

LABKEY.ext.DateField = Ext.extend(Ext.form.DateField,
{
    onTriggerClick : function(){
        if(this.disabled)
        {
            return;
        }
        if(this.menu == null)
        {
            this.menu = new Ext.menu.DateMenu({
                cls:'extContainer',     // NOTE change from super.onTriggerClick()
                hideOnClick: false,
                focusOnSelect: false
            });
        }
        this.onFocus();
        Ext.apply(this.menu.picker,
        {
            minDate : this.minValue,
            maxDate : this.maxValue,
            disabledDatesRE : this.disabledDatesRE,
            disabledDatesText : this.disabledDatesText,
            disabledDays : this.disabledDays,
            disabledDaysText : this.disabledDaysText,
            format : this.format,
            showToday : this.showToday,
            minText : String.format(this.minText, this.formatDate(this.minValue)),
            maxText : String.format(this.maxText, this.formatDate(this.maxValue))
        });
        this.menu.picker.setValue(this.getValue() || new Date());
        this.menu.show(this.el, "tl-bl?");
        this.menuEvents('on');
    }
});


LABKEY.ext.ComboPlugin = function () {
    var combo = null;

    return {
        init : function (combo) {
            this.combo = combo;
            if (this.combo.store)
            {
                this.combo.store.on({
                    load: this.resizeList,
                    // fired when the store is filtered or sorted
                    //datachanged: this.resizeList,
                    add: this.resizeList,
                    remove: this.resizeList,
                    update: this.resizeList,
                    buffer: 100,
                    scope: this
                });
            }

            if (this.combo.store && this.combo.value && (this.combo.displayField || this.combo.valueField))
            {
                this.combo.initialValue = this.combo.value;
                if (this.combo.store.getCount())
                {
                    this.initialLoad();
                    this.resizeList();
                }
                else
                {
                    this.combo.store.on('load', this.initialLoad, this, {single: true});
                }
            }
        },

        initialLoad : function()
        {
            if (this.combo.initialValue)
            {
                this.combo.setValue(this.combo.initialValue);
            }
        },

        resizeList : function ()
        {
            // bail early if ComboBox was set to an explicit width
            if (Ext.isDefined(this.combo.listWidth))
                return;

            // CONSIDER: set maxListWidth or listWidth instead of calling .doResize(w) below?
            var w = this.measureList();

            // NOTE: same as Ext.form.ComboBox.onResize except doesn't call super.
            if(!isNaN(w) && this.combo.isVisible() && this.combo.list){
                this.combo.doResize(w);
            }else{
                this.combo.bufferSize = w;
            }
        },

        measureList : function ()
        {
            if (!this.tm)
            {
                // XXX: should we share a TextMetrics instance across ComboBoxen using a hidden span?
                var el = this.combo.el ? this.combo.el : Ext.DomHelper.append(document.body, {tag:'span', style:{display:'none'}});
                this.tm = Ext.util.TextMetrics.createInstance(el);
            }

            var w = this.combo.el ? this.combo.el.getWidth(true) : 0;
            this.combo.store.each(function (r) {
                var html;
                if (this.combo.tpl && this.combo.rendered)
                    html = this.combo.tpl.apply(r.data);
                else
                    html = r.get(this.combo.displayField);
                w = Math.max(w, Math.ceil(this.tm.getWidth(html)));
            }, this);

            if (this.combo.list)
                w += this.combo.list.getFrameWidth('lr');

            // for vertical scrollbar
            w += 20;

            return w;
        }
    }
};
Ext.preg('labkey-combo', LABKEY.ext.ComboPlugin);

LABKEY.ext.ComboBox = Ext.extend(Ext.form.ComboBox, {
    constructor: function (config) {
        config.plugins = config.plugins || [];
        config.plugins.push(LABKEY.ext.ComboPlugin);
        LABKEY.ext.ComboBox.superclass.constructor.call(this, config);
    }
});


//Ext.reg('datepicker', LABKEY.ext.DatePicker);
Ext.reg('checkbox', LABKEY.ext.Checkbox);
Ext.reg('combo', LABKEY.ext.ComboBox);
Ext.reg('datefield',  LABKEY.ext.DateField);
Ext.reg('labkey-form', LABKEY.ext.FormPanel);



