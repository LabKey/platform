/**
 * @class Ext.ux.form.field.DateTime
 * @extends Ext.form.FieldContainer
 * @version 0.2 (July 20th, 2011)
 * @author atian25 (http://www.sencha.com/forum/member.php?51682-atian25)
 * @author ontho (http://www.sencha.com/forum/member.php?285806-ontho)
 * @author jakob.ketterl (http://www.sencha.com/forum/member.php?25102-jakob.ketterl)
 * @link http://www.sencha.com/forum/showthread.php?134345-Ext.ux.form.field.DateTime
 */
Ext4.define('Ext.ux.form.field.DateTime', {
    extend:'Ext.form.FieldContainer',
    mixins:{
        field:'Ext.form.field.Field'
    },
    alias: 'widget.datetimefield',

    //configurables

    combineErrors: true,
    msgTarget: 'under',
    layout: 'hbox',
    readOnly: false,
    defaultHour: null,
    defaultMinutes: null,

    /**
     * @cfg {String} dateFormat
     * Convenience config for specifying the format of the date portion.
     * This value is overridden if format is specified in the dateConfig.
     * The default is 'Y-m-d'
     */
    dateFormat: LABKEY.extDefaultDateFormat || 'Y-m-d',
    /**
     * @cfg {String} timeFormat
     * Convenience config for specifying the format of the time portion.
     * This value is overridden if format is specified in the timeConfig.
     * The default is 'H:i:s'
     */
    timeFormat: 'H:i:s',
    /**
     * @cfg {Object} dateConfig
     * Additional config options for the date field.
     */
    dateConfig:{},
    /**
     * @cfg {Object} timeConfig
     * Additional config options for the time field.
     */
    timeConfig:{},


    // properties

    dateValue: null, // Holds the actual date
    /**
     * @property dateField
     * @type Ext.form.field.Date
     */
    dateField: null,
    /**
     * @property timeField
     * @type Ext.form.field.Time
     */
    timeField: null,

    initComponent: function(){
        var me = this
            ,i = 0
            ,key
            ,tab;

        me.items = me.items || [];
        me.dateField = Ext4.create('Ext.form.field.Date', Ext4.apply({
            format  : me.dateFormat,
            name    : this.name + '-date',
            bubbleEvents: ['change', 'dirtychange'],
            flex:1,
            isFormField:false, //exclude from field query's
            submitValue:false,
            listeners: {
                scope: this,
                change: function(field, val){
                    if (this.isDestroyed || field.isDestroyed){
                        return;
                    }

                    if (val && !this.timeField.getValue()){
                        var date = this.parseDate(val);
                        // NOTE: this field does let the user type any string, so this is a possible state.
                        // The bad value will get rejected downstream
                        if (date)
                        {
                            if (!Ext4.isEmpty(this.defaultHour))
                            {
                                date.setHours(this.defaultHour);
                            }

                            if (!Ext4.isEmpty(this.defaultMinutes))
                            {
                                date.setMinutes(this.defaultMinutes);
                            }

                            this.timeField.setValue(date);
                        }
                        else {
                            //try to set anyway
                            this.timeField.setValue(val);
                        }
                    }

                    this.fireEvent('change', this, this.getValue(), val);
                    this.fireEvent('dirtychange', this, true);
                }
            },
            getErrors: function(value){
                var errors = Ext4.form.field.Date.prototype.getErrors.apply(this, arguments);
                if (this.ownerCt && this.ownerCt.getErrors)
                    errors = errors.concat(this.ownerCt.getErrors());

                return errors;
            }
        }, me.dateConfig));
        me.items.push(me.dateField);

        me.items.push({
            xtype: 'splitter'
        });

        me.timeField = Ext4.create('Ext.form.field.Time', Ext4.apply({
            format  : me.timeFormat,
            name    : this.name + '-time',
            bubbleEvents: ['change', 'dirtychange'],
            flex:1,
            isFormField:false, //exclude from field query's
            submitValue:false,
            listeners: {
                scope: this,
                change: function(field, val, oldVal){
                    if (this.isDestroyed || field.isDestroyed){
                        return;
                    }

                    var date = this.parseDate(val);
                    if (date && !oldVal){
                        var defaultDate = this.getDefaultDate();
                        date = new Date(defaultDate.getFullYear(), defaultDate.getMonth(), defaultDate.getDate(), date.getHours(), date.getMinutes(), date.getSeconds());
                        field.value = date;
                    }

                    if (date && !this.dateField.getValue()){
                        this.dateField.setValue(date);
                    }

                    this.fireEvent('change', this, this.getValue(), val);
                    this.fireEvent('dirtychange', this, true);
                }
            },
            getErrors: function(value){
                var errors = Ext4.form.field.Time.prototype.getErrors.apply(this, arguments);
                if (this.ownerCt && this.ownerCt.getErrors)
                    errors = errors.concat(this.ownerCt.getErrors());

                return errors;
            }
        }, me.timeConfig));
        me.items.push(me.timeField);

        for (; i < me.items.length; i++) {
            if(me.items[i].xtype == 'splitter')
                continue;

            me.items[i].on('focus', Ext4.bind(me.onItemFocus, me));
            me.items[i].on('blur', Ext4.bind(me.onItemBlur, me));
            me.items[i].on('specialkey', function(field, event){
                //NOTE: important when used in a grid, to prevent blur when tabbing
                if (event.getKey() == event.TAB && !event.shiftKey && me.focussedItem == me.dateField) {
                    event.stopEvent();
                    me.timeField.focus();
                    return;
                }

                me.fireEvent('specialkey', field, event);
            });
        }

        me.callParent();
        me.initField();
    },

    getDefaultDate: function(){
        return new Date();
    },

    focus: function(){
        this.callParent(arguments);
        this.dateField.focus();
    },

    onItemFocus: function(item){
        if (this.blurTask){
            this.blurTask.cancel();
        }
        this.focussedItem = item;
    },

    onItemBlur: function(item, e){
        var me = this;
        if (item != me.focussedItem){ return; }
        // 100ms to focus a new item that belongs to us, otherwise we will assume the user left the field
        me.blurTask = new Ext4.util.DelayedTask(function(){
            me.fireEvent('blur', me, e);
        });
        me.blurTask.delay(100);
    },

    getValue: function(){
        var value = null
            ,date = this.dateField.getSubmitValue()
            ,time = this.timeField.getSubmitValue()
            ,format;

        if (date){
            if (time){
                format = this.getFormat();
                value = Ext4.Date.parse(date + ' ' + time, format);
            } else {
                value = this.dateField.getValue();
            }
        }
        return value;
    },

    getSubmitValue: function(){
        var me = this
            ,format = me.getFormat()
            ,value = me.getValue();

        return value ? Ext4.Date.format(value, format) : null;
    },

    setValue: function(value){
        if (Ext4.isString(value)){
            var orig = value;
            value = Ext4.Date.parse(value, this.getFormat());  //preferentially use format of this field
            if (orig && !value) {
                value = this.parseDate(orig);
                if (!value){
                    console.error('Unable to parse string in DateTimeField: [' + orig + ']');
                }
            }
        }
        this.dateField.setValue(value);
        this.timeField.setValue(value);
    },

    getFormat: function(){
        return (this.dateField.submitFormat || this.dateField.format) + " " + (this.timeField.submitFormat || this.timeField.format);
    },

    // Bug? A field-mixin submits the data from getValue, not getSubmitValue
    getSubmitData: function(){
        var me = this
            ,data = null;

        if (!me.disabled && me.submitValue && !me.isFileUpload()) {
            data = {};
            data[me.getName()] = '' + me.getSubmitValue();
        }
        return data;
    },

    isValid: function(){
        //ensure both will be called
        var v1 = this.dateField.isValid();
        var v2 = this.timeField.isValid();
        return v1 && v2
    },

    destroy: function(){
        this.dateField.destroy();
        this.timeField.destroy();
        this.callParent(arguments);
    },

    parseDate : function(value, format) {
        if(!value || Ext4.isDate(value)){
            return value;
        }

        var formats = [];
        if (format)
            formats.push(format);
        formats = formats.concat(DATEFORMATS);

        var val;
        for (var i=0; i < formats.length && !val; ++i) {
            val = this.safeParseDate(value, formats[i]);
        }

        // two digit years tend to get parsed as 1900, rather than 2000s, so we make assumptions about dates more than 90 in the past
        if (val && (val.getFullYear() < new Date().getFullYear() - 90))
            val.setFullYear(val.getFullYear() + 100);

        return val;
    },

    safeParseDate: function(value, format, useStrict) {
        var result = null,
            parsedDate;

        useStrict = Ext4.isDefined(useStrict) ? useStrict : false;

        if (Ext4.Date.formatContainsHourInfo(format)) {
            // if parse format contains hour information, no DST adjustment is necessary
            result = Ext4.Date.parse(value, format, useStrict);
        } else {
            // set time to 12 noon, then clear the time
            parsedDate = Ext4.Date.parse(value + ' ' + 12, format + ' ' + 'H', useStrict);
            if (parsedDate) {
                result = Ext4.Date.clearTime(parsedDate);
            }
        }
        return result;
    }
});