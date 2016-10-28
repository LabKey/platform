/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2016 LabKey Corporation
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
 * @class <p><font color="red">DEPRECATED - </font> Consider using
 * <a href="http://www.extjs.com/deploy/dev/docs/?class=Ext.form.FormPanel">Ext.form.FormPanel</a> instead. </p>
 * <p>LabKey extension to the
 * <a href="http://www.extjs.com/deploy/dev/docs/?class=Ext.form.FormPanel">Ext.form.FormPanel</a>.
 * This class understands various LabKey metadata formats and can simplify generating basic forms.
 * When a LABKEY.ext.FormPanel is created with additional metadata, it will try to intelligently construct fields
 * of the appropriate type.</p>
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">Tutorial: Create Applications with the JavaScript API</a></li>
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

        function cancelHandler()
        {
            // Replace with real handler code
            Ext.MessageBox.alert("Cancelled", "The submission was cancelled.");
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
        if (Ext.isArray(config.items))
            config.items.push({ xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF });
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
                // Don't render URL values as a separate input field
                if (!existing[name] && name.indexOf(LABKEY.Query.URL_COLUMN_PREFIX) != 0)
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
                var d = defaults[name];
                defaults[name] = Ext.applyIf(defaults[name] || {}, field);

                items.push({name:name});
            }
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
                            defaults[id].required = false;
                            defaults[id].allowBlank = true;
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
                            defaults[id].required = false;
                            defaults[id].allowBlank = true;
                        }
                        else
                            defaults[id].value = v;
                    }
                }
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
        // First, get all dirty form field values
        var fieldValues = this.getForm().getFieldValues(true);
        for (var key in fieldValues)
        {
            if (typeof fieldValues[key] == "string")
                fieldValues[key] = fieldValues[key].trim();
        }

        // 10887: Include checkboxes that weren't included in the call to .getFieldValues(true).
        this.getForm().items.each(function (f) {
            if (f instanceof Ext.form.Checkbox && !f.isDirty())
            {
                var name = f.getName();
                var key = fieldValues[name];
                var val = f.getValue();
                if (Ext.isDefined(key)) {
                    if (Ext.isArray(key)) {
                        fieldValues[name].push(val);
                    } else {
                        fieldValues[name] = [key, val];
                    }
                } else {
                    fieldValues[name] = val;
                }
            }
        });

        // Finally, populate the data array with form values overriding the initial values.
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
     * @param {object} [config.lookup.store] advanced! Pass in your own custom store for a lookup field
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
            var ext = config.ext;
            if (arguments[i])
            {
                ext = Ext.apply(ext, arguments[i].ext);
                Ext.apply(config, arguments[i]);
            }
            config.ext = ext;
        }

        var h = Ext.util.Format.htmlEncode;
        var lc = function(s){return !s?s:Ext.util.Format.lowercase(s);};

        config.type = lc(config.jsonType) || lc(config.type) || lc(config.typeName) || 'string';

        var field =
        {
            //added 'caption' for assay support
            fieldLabel: h(config.label) || h(config.caption) || h(config.header) || h(config.name),
            name: config.name,
            originalConfig: config,
            allowBlank: !config.required,
            disabled: !config.editable
        };

        if (config.tooltip && !config.helpPopup)
            field.helpPopup = config.tooltip;

        if (config.lookup && false !== config.lookups)
        {
            var l = config.lookup;
            var store = config.lookup.store || config.store;
            if (store && store == config.store)
                console.debug("use config.lookup.store");

            if (Ext.isObject(store) && store.events)
                field.store = store;
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
                    field.altFormats = LABKEY.Utils.getDateAltFormats();
                    break;
                case "string":
                    if (config.inputType == 'file')
                    {
                        field.xtype = 'textfield';
                        field.inputType = 'file';
                        break;
                    }
                    else if (config.inputType=='textarea')
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
                    field.xtype = 'textfield';
            }

        }

        if (config.ext)
            Ext.apply(field,config.ext);
        
        // Ext.form.ComboBox defaults to mode=='remote', however, we often want to default to 'local'
        // We don't want the combo cause a requery (see Combo.doQuery()) when we expect the store
        // to be loaded exactly once.  Just treat like a local store in this case.
        // NOTE: if the user over-rides the field.store, they may have to explicitly set the mode to 'remote', even
        // though 'remote' is the Ext.form.ComboBox default
        if (field.xtype == 'combo' && Ext.isDefined(field.store) && field.store.autoLoad && field.triggerAction != 'query' && !Ext.isDefined(field.mode))
            field.mode = 'local';

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
            containerPath: l.container || l.containerPath || c.containerPath || LABKEY.container.path,
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
        else
            config.sort = l.displayColumn;
        
        if (!c.required)
        {
            config.nullRecord = {
                displayColumn: l.displayColumn,
                nullCaption: c.lookupNullCaption || "[none]"
            };
        }

        return config;
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


LABKEY.ext.DatePicker = Ext.extend(Ext.DatePicker, { });


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
                this.combo.mon(this.combo.store, {
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

            if (Ext.isObject(this.combo.store) && this.combo.store.events)
            {
                this.combo.initialValue = this.combo.value;
                if (this.combo.store.getCount())
                {
                    this.initialLoad();
                    this.resizeList();
                }
                else
                {
                    this.combo.mon(this.combo.store, 'load', this.initialLoad, this, {single: true});
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
            var tpl = null;
            if (this.combo.rendered)
            {
                if (this.combo.view && this.combo.view.tpl instanceof Ext.Template)
                    tpl = this.combo.view.tpl;
                else if (this.combo.tpl instanceof Ext.Template)
                    tpl = this.combo.tpl;
            }

            this.combo.store.each(function (r) {
                var html;
                if (tpl)
                    html = tpl.apply(r.data);
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
    },

    initList : function () {
        // Issue 18401: Customize view folder picker truncates long folder paths
        // Add displayField as the qtip text for item that are likely to be truncated.
        if (!this.tpl) {
            var cls = 'x-combo-list';
            this.tpl = '<tpl for="."><div ext:qtip="{[values[\'' + this.displayField + '\'] && values[\'' + this.displayField + '\'].length > 50 ? values[\'' + this.displayField + '\'] : \'\']}" class="'+cls+'-item">{' + this.displayField + ':htmlEncode}</div></tpl>';
        }

        LABKEY.ext.ComboBox.superclass.initList.call(this);
    }
});

/**
 * The following overwrite allows tooltips on labels within form layouts.
 * The field have to be a property named "gtip" in the corresponding
 * config object.
 */
Ext.override(Ext.layout.FormLayout, {
    setContainer: Ext.layout.FormLayout.prototype.setContainer.createSequence(function(ct) {
        // the default field template used by all form layouts
        var t = new Ext.Template(
            '<div class="x-form-item {itemCls}" tabIndex="-1">',
                '<label for="{id}" style="{labelStyle}" class="x-form-item-label {guidedCls}"><span {guidedTip}>{label}{labelSeparator}</span></label>',
                '<div class="x-form-element" id="x-form-el-{id}" style="{elementStyle}">',
                '</div><div class="{clearCls}"></div>',
            '</div>'
        );
        t.disableFormats = true;
        t.compile();
        Ext.layout.FormLayout.prototype.fieldTpl = t;
    }),

    getTemplateArgs : function(field) {
        var noLabelSep = !field.fieldLabel || field.hideLabel,
                itemCls = (field.itemCls || this.container.itemCls || '') + (field.hideLabel ? ' x-hide-label' : '');

        // IE9 quirks needs an extra, identifying class on wrappers of TextFields
        if (Ext.isIE9 && Ext.isIEQuirks && field instanceof Ext.form.TextField) {
            itemCls += ' x-input-wrapper';
        }

        return {
            id            : field.id,
            label         : field.fieldLabel,
            itemCls       : itemCls,
            clearCls      : field.clearCls || 'x-form-clear-left',
            labelStyle    : this.getLabelStyle(field.labelStyle),
            elementStyle  : this.elementStyle||'',
            labelSeparator: noLabelSep ? '' : (Ext.isDefined(field.labelSeparator) ? field.labelSeparator : this.labelSeparator),
            guidedTip     : (field.gtip === undefined ? '' : ' ext:gtip="'+field.gtip+'"'),
            guidedCls     : (field.gtip === undefined ? '' : 'g-tip-label')
        };
    }
});

Ext.reg('checkbox', LABKEY.ext.Checkbox);
Ext.reg('combo', LABKEY.ext.ComboBox);
Ext.reg('datefield',  LABKEY.ext.DateField);
Ext.reg('labkey-form', LABKEY.ext.FormPanel);

