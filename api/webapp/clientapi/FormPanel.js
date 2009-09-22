/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY","LABKEY.ext");


/**
 * Constructs a new LabKey FormPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.form.FormPanel">Ext.form.FormPanel</a>,
 * which understands various labkey metadata formats and can simplify generating basic forms.
 * When a LABKEY.ext.FormPanel is created with additional metadata, it will try to intelligently construct fields
 * of the appropriate type.
 * @constructor
 * @augments Ext.form.FormPanel
 * @param config Configuration properties. This may contain any of the configuration properties supported
 * by the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.form.FormPanel">Ext.form.FormPanel</a>,
 * plus those listed here.
 * @param {object} [config.metaData] as returned by Query.selectRows
 * @param {object} [config.columnModel] as returned by Query.selectRows
 * @param {object} [config.selectRowsResults] as returned by Query.selectRows
 * @param {boolean} [config.addAllFields] default=false.  If true, automatically create all fields specified in the metaData.
 * @param {object} [config.values] initial values to populate the form
 * selectRowsResults includes both a columnModel and the metaData so you don't need to specify all three.
 *
 * @example
&lt;script type="text/javascript"&gt;
    function onSuccess(data) // e.g. callback from Query.selectRows
    {
        var form = new LABKEY.form.FormPanel(
        {
            selectRowsResults:data,
            addAllFields:true,
            buttons:["submit"]
        });
        form.render('formDiv);
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
    defaults : {width:200},
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


    onRender : function(ct, position)
    {
        LABKEY.ext.FormPanel.superclass.onRender.call(this, ct, position);
    },


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
        return LABKEY.ext.FormPanel.superclass.applyDefaults.call(this, c);
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
                this.values = config.selectRowsResults.rows[0];
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
            var v = config.values;
            for (var id in v)
            {
                if (typeof v[id] == 'function')
                    continue;
                defaults[id] = Ext.apply(defaults[id]||{}, {value:v[id]});
            }
        }

        return items;
    }
});


Ext.ns("Date.patterns");
Ext.applyIf(Date.patterns,{
    ISO8601Long:"Y-m-d H:i:s",
    ISO8601Short:"Y-m-d"
});


LABKEY.ext.FormHelper =
{
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
                typeAhead: false,
                triggerAction: 'all',
                displayField: l.displayColumn,
                valueField: l.keyColumn,
                tpl : '<tpl for="."><div class="x-combo-list-item">{[values["' + l.displayColumn + '"]]}</div></tpl>', //FIX: 5860
                listClass: 'labkey-grid-editor'
            });
            return field;
        }


        if (config.editable)
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
                                    'j M Y G:i:s O'; // 10 Sep 2009 11:24:12 -0700
                    break;
                case "string":
                    break;
                default:
            }
            field.allowBlank = !config.required;
        }
        else
        {
            field.xtype = 'label';
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
            //create the lookup store and kick off a load
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
            store = new LABKEY.ext.Store(config);
            this.lookupStores[uniqueName] = store;
        }
        return store;
    }
};