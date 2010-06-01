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
 *
 * @example
&lt;script type="text/javascript"&gt;
    function onSuccess(data) // e.g. callback from Query.selectRows
    {
        var form = new LABKEY.ext.FormPanel(
        {
            selectRowsResults:data,
            addAllFields:true,
            buttons:["submit"],
            items:[{name:'myField', fieldLabel:'My Field', helpPopup:{title:'help', html:'read the manual'}}]
        });
        form.render('formDiv');
    }
&lt;/script&gt;
&lt;div id='formDiv'/&gt;
 */

LABKEY.ext.FormPanel = Ext.extend(Ext.form.FormPanel,
{
    constructor : function(config)
    {
        this.allFields = this.initFieldDefaults(config);
        return LABKEY.ext.FormPanel.superclass.constructor.call(this,config);
    },

    defaultType : 'textfield',
    allFields : [],

    initComponent : function()
    {
        LABKEY.ext.FormPanel.superclass.initComponent.call(this);

        // add all fields that we're not added explicitly
        if (this.addAllFields && this.allFields.length)
        {
            // get a list of fields that were already constructed
            var existing = {};
            if (this.items)
                this.items.each(function(c)
                {
                    if (c.isFormField)
                        existing[c.name] = c.name;
                });
            for (var i=0;i<this.allFields.length;i++)
            {
                var c = this.allFields[i];
                if (!existing[c.name])
                    this.add(c);
            }
        }
    },


    /* called initComponent() before onRender() */
    applyDefaults : function(c)
    {
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
        return LABKEY.ext.FormPanel.superclass.applyDefaults.call(this, c);
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
                metaData = config.selectRowsResults.metaData;
            if (config.selectRowsResults.rowCount)
                config.values = config.selectRowsResults.rows[0];
        }
        var fields = metaData ? metaData.fields : null;

        var defaults = config.fieldDefaults = config.fieldDefaults || {};
        var items = [], i;

        if (fields || properties)
        {
            var count = fields ? fields.length : properties.length;
            for (i=0 ; i<count ; i++)
            {
                var field = LABKEY.ext.FormHelper.getFieldEditorConfig(
                        fields?fields[i]:{},
                        properties?properties[i]:{},
                        columnModel?columnModel[i]:{}
                        );
                var name = field.originalConfig.name;
                defaults[name] = field;
                items.push({name:name});
            }
        }

        if (config.values)
        {
            var values = config.values;
            for (var id in values)
            {
                var v = values[id];
                if (typeof v == 'function')
                    continue;
                if (v && typeof v == 'object' && 'value' in v)
                    v = v.value;
                if (!(id in defaults))
                    defaults[id] = {};
                if ('xtype' in defaults[id] && defaults[id].xtype == 'checkbox')
                {
                    var checked = v ? true : false;
                    if (v == "false")
                        v = false;
                    defaults[id].checked = v;
                }
                else
                    defaults[id].value = v;
            }
        }

        return items;
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
     * @param {string} [config.lookup.schema]
     * @param {string} [config.lookup.table]
     * @param {string} [config.lookup.keyColumn]
     * @param {string} [config.lookup.displayColumn]
     *
     * Will accept multiple config parameters which will be combined.
     */
    getFieldEditorConfig: function(c)
    {
        // Combine the metadata provided into one config object
        var config = {editable:true, required:false};
        for (var i=arguments.length-1 ; i>= 0 ; --i)
            Ext.apply(config, arguments[i]);

        var h = Ext.util.Format.htmlEncode;
        var lc = function(s){return !s?s:Ext.util.Format.lowercase(s);};

        config.type = lc(config.type) || lc(config.typeName) || 'string';

        var field = {
            fieldLabel: h(config.label) || config.header || h(config.name),
            originalConfig: config
        };

        if (config.tooltip && !config.helpPopup)
            field.helpPopup = { html: config.tooltip };

        if (config.lookup)
        {
            // UNDONE: avoid self-joins
            // UNDONE: core.UsersData
            // UNDONE: container column
            var l = config.lookup;
            if (l.schema == 'core' && l.table=='UsersData')
                l.table = 'Users';
            var lookupName = [l.schema,l.table,l.keyColumn,l.displayColumn].join('||');
            var store = LABKEY.ext.FormHelper.getLookupStore(lookupName, config);
            Ext.apply(field, {
                xtype: 'combo',
                store: store,
                allowBlank: !config.required,
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
            return field;
        }

        switch (config.jsonType)
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

                    if (config.ext)
                        Ext.apply(field,config.ext);
                }
                break;
            default:
        }

        field.allowBlank = !config.required;

        if (!config.editable)
        {
            field.disabled=true;
        }

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


    lookupStores : {},

    // private
    getLookupStore : function(uniqueName, c)
    {
        if (typeof(uniqueName) != 'string')
        {
            c = uniqueName;
            uniqueName = c.name;
        }

        var store = this.lookupStores[uniqueName];
        if (!store)
        {
            var config = {
                schemaName: c.lookup.schema,
                queryName: c.lookup.table,
                containerPath: c.lookup.container || LABKEY.container.path
            };
            var columns = [];
            if (c.lookup.keyColumn)
                columns.push(c.lookup.keyColumn);
            if (c.lookup.displayColumn)
                columns.push(c.lookup.displayColumn);
            if (columns.length < 2)
                columns = ['*'];
            config.columns = columns.join(',');
            if (!c.required)
            {
                config.nullRecord = {
                    displayColumn: c.lookup.displayColumn,
                    nullCaption: c.lookupNullCaption || "[none]"
                };
            }
            config.autoLoad = true;
            store = new LABKEY.ext.Store(config);
            this.lookupStores[uniqueName] = store;
        }
        return store;
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


LABKEY.ext.ComboBox = Ext.extend(Ext.form.ComboBox,
{                                
    constructor : function(c)
    {
        LABKEY.ext.ComboBox.superclass.constructor.call(this, c);
        if (this.mode == 'remote' && this.store && this.value && this.displayField && this.valueField && this.displayField != this.valueField)
        {
            this.initialValue = this.value;
            if (this.store.getCount())
                this.initialLoad();
            else
            {
                this.store.on('load', this.initialLoad, this);
                if (!this.store.proxy.activeRequest)
                    this.store.load();
            }
        }
    },

    initialLoad : function()
    {
        this.store.un('load', this.initialLoad, this);
        if (this.value === this.initialValue)
        {
            var v = this.value;
            this.setValue(v);
        }
    }
});



//Ext.reg('datepicker', LABKEY.ext.DatePicker);
Ext.reg('checkbox', LABKEY.ext.Checkbox);
Ext.reg('combo', LABKEY.ext.ComboBox);
Ext.reg('datefield',  LABKEY.ext.DateField);
Ext.reg('labkey-form', LABKEY.ext.FormPanel);



