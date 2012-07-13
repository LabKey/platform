/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * An extension to the Ext4 combobox.  The primary features this provides are more control over
 * how the display values are rendered and auto-resizing of the pick list based on the size of
 * the items.  Any extensions to this combo should be written to make sure it does not require this combo to use a LABKEY.ext4.Store, even
 * though in most cases this will be true.  If we we find we need this, we should extract the majority of extensions into either a separate
 * non-labkey dependent base class or into a plugin.
 * @cfg {Boolean} showValueInList If true, the underlying value will also be shown in the pick menu, in addition to the display value.
 * @cfg {String} lookupNullCaption A string that will be used at the display text if the displayField is blank.  Defaults to '[none]'
 * @cfg {Boolean} expandToFitContent If true, the pick list will automatically expand to the width of the longest item.  Defaults to true.
 * @cfg {Integer} maxExpandWidth If provided, the pick list will not expand beyond this value.  Defaults to null.
 */

//TODO: we should rename lookupNullCaption to nullCaption
Ext4.define('LABKEY.ext4.ComboBox', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-combo',
    lazyCreateStore: true,
    config: {
        expandToFitContent: true,
        maxExpandWidth: null
    },
    initComponent: function(){
        this.initConfig();

        this.listConfig = this.listConfig || {};
        Ext4.apply(this.listConfig, {
            innerTpl: [
                //allow a custom null caption, defaults to '[none]'
                '{[(typeof values === "string" ? values : (values["' + this.displayField + '"] ? values["' + this.displayField + '"] : '+(Ext4.isDefined(this.lookupNullCaption) ? '"' + this.lookupNullCaption + '"' : '"[none]"')+'))]}' +
                //allow a flag to display both display and value fields
                (this.showValueInList ? '{[values["' + this.valueField + '"] ? " ("+values["' + this.valueField + '"]+")" : ""]}' : '') +
                (this.multiSelect ? '<tpl if="xindex < xcount">' + '{[(values["' + this.displayField + '"] ? "'+this.delimiter+'" : "")]}' + '</tpl>' : '') +
                //space added so empty strings render with full height
                '&nbsp;'
            ],
            getInnerTpl: function(){
                return this.innerTpl;
            },
            style: 'border-top-width: 1px;' //this was added in order to restore the border above the boundList if it is wider than the field
        });

        this.callParent();

        //this is necessary to clear invalid fields, assuming the initial value is set asynchronously on store load
        this.mon(this.store, 'load', this.isValid, this, {single: true});
    },

    onLoad: function(){
        this.callParent(arguments);

        //will only run if picker has been created
        if(this.expandToFitContent){
            this.resizeToFitContent();
        }
    },

    /**
     * @private
     * This is designed to autosize the width of the BoundList, based on content.  The hope is that by measuring the list as a whole,
     * we avoid measuring each list item, which can be extremely expensive.
     */
    resizeToFitContent: function(){
        //NOTE: prematurely creating the picker can have bad consequences, so we wait until it has been loaded to resize
        if(this.picker && this.picker.el){
            var picker = this.getPicker();
            var el = this.getPicker().el;
            if(!this.metrics)
                this.metrics = new Ext4.util.TextMetrics(this.inputEl);

            var v = el.dom.innerHTML;
            var w = this.metrics.getWidth(v) + 10 + this.getTriggerWidth(); /* add extra padding, plus width of scroll bar */
            var fieldWidth = this.inputEl.getWidth() + this.getTriggerWidth();
            w = Math.max(w, fieldWidth); //always fill entire field
            if(this.maxExpandWidth)
                w = Math.min(w, this.maxExpandWidth);

            //if width is less than fieldwidth, expand.  otherwise turn off or Ext will coerce the list to match the field
            this.matchFieldWidth = fieldWidth >= w;
            picker.setWidth(w);
        }
    }

    //allows value to be set if store has not yet loaded
    //undone: In ext4.1, if the store has loading=true when trying to set the value, it should handle asyc loading properly
//    setValue: function(val){
//        if(!this.store || !this.store.model || !this.store.model.prototype.fields.getCount()){
//            this.value = val;
//            this.mon(this.store, 'load', function(){this.setValue(val);this.validate();}, this, {single: true});
//        }
//        else
//            this.callParent(arguments);
//    }
});