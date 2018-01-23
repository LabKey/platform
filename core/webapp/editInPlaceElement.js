/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
    In-place editor widget. Create one of these per-page and use the startEdit() method to
    begin editing an element. Catch the "complete" event to process the edited content.
    Note that this class extends Ext.Editor. See the API documentation for more information
    on the base class. http://www.extjs.com/deploy/dev/docs/?class=Ext.Editor
*/

Ext4.define('LABKEY.ext.EditInPlaceElement', {
    extend: 'Ext.util.Observable',

    editor: null,
    editWidth : null,
    oldText: null,
    multiLine: false,
    growFactor: 20,
    border: true,
    updateHandler: null,
    emptyText: null,
    updateConfig: null,
    widthBuffer: 50,
    enterCompletesEdit: true,
    minLengthText: "The minimum length for this field is {0}",
    maxLengthText: "The maximum length for this field is {0}",

    constructor: function(config){

        this.addEvents("beforecomplete", "complete", "canceledit", "editstarted", "updatefail", "validitychange");
        Ext4.apply(this, config);
        if (this.updateConfig){
            this.updateConfig = Ext4.applyIf(this.updateConfig, {
                method: 'POST',
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        }


        this.callParent(arguments);

        if (!this.applyTo)
            throw "You must specify an applyTo property in your config!";

        this.el = Ext4.get(this.applyTo);
        if (!this.el)
            throw "Could not find element '" + this.applyTo + "'!";

        this.el.set({
            title: "Click to Edit"
        });

        this.checkForEmpty();

        this.el.addCls("labkey-edit-in-place");
        this.el.on("mouseover", this.onMouseOver, this);
        this.el.on("mouseout", this.onMouseOut, this);
        this.el.on("click", this.startEdit, this);

        this.editIcon = Ext4.getBody().createChild({
            tag: 'div',
            cls: 'labkey-edit-in-place-icon',
            title: 'Click to Edit'
        });
        this.editIcon.anchorTo(this.el, 'tr-tr');
        this.editIcon.on("mouseover", function(){
            this.editIcon.addCls("labkey-edit-in-place-icon-hover");
        }, this);
        this.editIcon.on("mouseout", function(){
            this.editIcon.removeCls("labkey-edit-in-place-icon-hover");
            this.editIcon.removeCls("labkey-edit-in-place-icon-mouse-down");
        }, this);
        this.editIcon.on("mousedown", function(){
            this.editIcon.addCls("labkey-edit-in-place-icon-mouse-down");
        }, this);
        this.editIcon.on("mouseup", function(){
            this.editIcon.removeCls("labkey-edit-in-place-icon-mouse-down");
        }, this);
        this.editIcon.on("click", this.startEdit, this);
    },

    checkForEmpty: function(){
        if (this.emptyText && (this.el.dom.innerHTML.length == 0 || this.el.dom.innerHTML == "&nbsp;"))
        {
            this.el.update(this.emptyText);
            this.el.addCls("labkey-edit-in-place-empty");
        }
    },

    onMouseOver: function(){
        this.editIcon.addCls("labkey-edit-in-place-icon-hover");
    },

    onMouseOut: function(){
        this.editIcon.removeCls("labkey-edit-in-place-icon-hover");
    },

    startEdit: function(){
        if (this.editor || this.el.hasCls("labkey-edit-in-place-updating"))
            return;

        this.fireEvent("editstarted", this.oldText);

        //create the editor as a peer to the bound element
        var config = {
            tag: (this.multiLine ? 'textarea' : 'input'),
            style: 'display:none;overflow:hidden;'
        };
        config.style += this.border ? "border:1px solid #C0C0C0;" : "border:0px";

        if (!this.multiLine)
            config.type = 'text';

        if (this.maxLength)
            config.maxlength = this.maxLength;

        this.editor = this.el.parent().createChild(config, this.el.next());

        //create the offscreen sizing div
        this.sizingDiv = Ext4.getBody().createChild({
            tag: 'div',
            style: 'position:absolute;left:-10000px;top:-10000px'
        });


        //make the editor's text styles match the element
        if (this.el.hasCls("labkey-edit-in-place-empty"))
        {
            this.el.removeCls("labkey-edit-in-place-empty");
            this.el.update("");
        }

        var styles = this.el.getStyles('padding-top', 'padding-bottom', 'padding-left',
              'padding-right', 'line-height', 'font-size',
              'font-family', 'font-weight', 'font-style');

        styles.width = (this.el.getWidth() - this.widthBuffer) + "px";
        this.editor.setStyle(styles);
        this.sizingDiv.setStyle(styles);

        //determine the height of one line of text for the growfactor
        this.sizingDiv.update("Xg");
        this.growFactor = this.sizingDiv.getHeight() * 1.5;

        //set the start text
        var startText = Ext4.String.trim(this.el.dom.innerHTML);
        this.oldText = startText;
        if (this.multiLine)
            this.editor.update(startText);
        else
            this.editor.set({value: startText});

        if (this.multiLine)
            this.autoSize();

        this.orginalDisplayVal = this.el.getStyle('display');
        this.editor.setDisplayed(this.orginalDisplayVal || true);
        this.el.setDisplayed(false);
        this.editIcon.setDisplayed(false);

        this.editor.focus();
        this.editor.dom.select();
        
        this.editor.on("blur", this.completeEdit, this);
        if (this.multiLine)
            this.editor.on("keyup", this.autoSize, this);

        var keyMap = [{
            key: Ext4.EventObject.ESC,
            fn: this.cancelEdit,
            scope: this
        }];

        if (this.enterCompletesEdit){
            keyMap.push({
                key: Ext4.EventObject.ENTER,
                fn: this.completeEdit,
                scope: this
            });
        }

        this.editor.addKeyMap({binding: keyMap});
    },

    autoSize: function(){
        var value = this.editor.getValue();
        value = Ext4.util.Format.htmlEncode(value);
        value = value.replace(/\n/g, "<br/>");

        this.sizingDiv.update(value);

        var bw = this.editor.getBorderWidth("tb") || 2;
        var height = this.sizingDiv.getHeight() + bw + this.growFactor;
        this.editor.setHeight(height);
        if(this.editWidth) {
            this.editor.setWidth(this.editWidth);
        }
    },

    getValue : function () {
        if (this.editor) {
            this.currentValue = Ext4.util.Format.htmlEncode(this.editor.getValue());
        }
        return this.currentValue;
    },

    completeEdit: function(){
        var value = this.getValue();
        this.endEdit();

        if (value != this.oldText && this.validate() && false !== this.fireEvent("beforecomplete", value, this.oldText))
            this.processChange(value, this.oldText);
        else
            this.onUpdateCancel(this.oldText);
    },

    cancelEdit: function(){
        this.endEdit();
        this.checkForEmpty();
        this.fireEvent("canceledit", this.oldText);
    },

    endEdit: function(){
        this.editor.un("blur", this.completeEdit, this);

        this.el.setDisplayed(this.orginalDisplayVal || true);
        if (this.orginalDisplayVal){
            delete this.orginalDisplayVal;
        }

        this.editor.setDisplayed(false);
        this.editIcon.setDisplayed(true);

        this.editor.remove();
        this.editor = null;

        this.sizingDiv.remove();
        this.sizingDiv = null;
    },

    processChange: function(value, oldValue){
        if (this.updateConfig)
        {
            var reqConfig = Ext4.apply({}, this.updateConfig);

            //set jsonData and handlers
            reqConfig.jsonData = {};
            reqConfig.jsonData[this.updateConfig.jsonDataPropName || "newValue"] = Ext4.util.Format.htmlDecode(value);
            reqConfig.success = function(){
                this.onUpdateComplete(value);
            };
            reqConfig.failure = function(){
                this.onUpdateFailure(value, oldValue);
            };
            reqConfig.scope = this;

            //update the el and add the updating class
            this.el.addCls("labkey-edit-in-place-updating");
            this.el.update(value);

            //do the Ajax request
            Ext4.Ajax.request(reqConfig);
        }
        else
            this.onUpdateComplete(value);
    },

    /**
     * Returns whether or not the field value is currently valid by {@link #getErrors validating} the field's current
     * value.
     *
     * Implementations are encouraged to ensure that this method does not have side-effects such as triggering error
     * message display.
     *
     * @returns {Boolean} True if the value is valid, else false.
     */
    isValid : function () {
        var me = this;
        return me.disabled || Ext4.isEmpty(me.getErrors());
    },

    /**
     * Runs this field's validators and returns an array of error messages for any validation failures.
     * This is called internally during validation and would not usually need to be used manually.
     *
     * @return {String[]} All error messages for this field; an empty Array if none.
     */
    getErrors : function () {
        var value = this.getValue();

        var errors = [];
        var msg;

        if (Ext4.isFunction(this.validator)) {
            msg = this.validator.call(this, value);
            if (msg !== true) {
                errors.push(msg);
            }
        }

        if (value.length < this.minLength) {
            errors.push(String.format(this.minLengthText, this.minLength));
        }

        if (value.length > this.maxLength) {
            errors.push(String.format(this.maxLengthText, this.maxLength));
        }

        return errors;
    },

    /**
     * Returns whether or not the field value is currently valid by {@link #getErrors validating} the field's current
     * value, and fires the {@link #validitychange} event if the field's validity has changed since the last validation.
     *
     * Custom implementations of this method are allowed to have side-effects such as triggering error message display.
     * To validate without side-effects, use {@link #isValid}.
     *
     * @return {Boolean} True if the value is valid, else false
     */
    validate : function () {
        var me = this,
            isValid = me.isValid();
        if (isValid !== me.wasValid) {
            me.wasValid = isValid;
            me.fireEvent('validitychange', me, isValid);
        }
        return isValid;
    },

    onUpdateCancel: function (value) {
        this.el.removeCls("labkey-edit-in-place-updating");
        this.el.update(value);
        this.checkForEmpty();
        this.editIcon.alignTo(this.el, 'tr-tr');
        this.fireEvent("canceledit", this.oldText);
    },

    onUpdateComplete: function(value){
        this.el.removeCls("labkey-edit-in-place-updating");
        this.el.update(value);
        this.checkForEmpty();
        this.editIcon.alignTo(this.el, 'tr-tr');
        this.fireEvent("complete");
    },

    onUpdateFailure: function(value, oldValue) {
        alert("There was an error while updating the value!");
        this.el.removeCls("labkey-edit-in-place-updating");
        this.el.update(oldValue);
        this.checkForEmpty();
        this.fireEvent("updatefail");
    }

});
