/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
    In-place editor widget. Create one of these per-page and use the startEdit() method to
    begin editing an element. Catch the "complete" event to process the edited content.
    Note that this class extends Ext.Editor. See the API documentation for more information
    on the base class. http://www.extjs.com/deploy/dev/docs/?class=Ext.Editor
*/

Ext.namespace('LABKEY', 'LABKEY.ext');

LABKEY.ext.EditInPlaceElement = Ext.extend(Ext.util.Observable, {

    constructor: function(config){
        LABKEY.ext.EditInPlaceElement.superclass.constructor.apply(this, arguments);
        Ext.apply(this, config);
        this.addEvents("beforestartedit", "beforecomplete", "complete", "canceledit");

        this.field = this.multiLine ? new Ext.form.TextArea({
            selectOnFocus: true,
            autoScroll: true,
            ctCls: 'extContainer'
        }) : new Ext.form.TextField({
            selectOnFocus: true,
            grow: true,
            cls: 'extContainer'
        });

        this.editor = new Ext.Editor(this.field,{
            autoSize: 'width',
            alignment: 'lt-lt',
            completeOnEnter: true,
            cancelOnEsc: true,
            ignoreNoChange: true,
            updateEl: false,
            cls: 'extContainer'
        });

        this.editor.on("beforestartedit", this.onBeforeStartEdit, this);
        this.editor.on("beforecomplete", this.onBeforeEditComplete, this);
        this.editor.on("complete", this.onEditComplete, this);
        this.editor.on("canceledit", this.onCancelEdit, this);

        if (!this.applyTo)
            throw "You must specify an applyTo property in your config!";

        this.elem = Ext.get(this.applyTo);
        if (!this.elem)
            throw "Could not find element '" + this.applyTo + "'!";

        this.elem.set({
            title: "Click to Edit"
        });
        this.elem.addClass("labkey-edit-in-place");
        this.elem.on("mouseover", this.onMouseOver, this);
        this.elem.on("mouseout", this.onMouseOut, this);
        this.elem.on("click", this.onClick, this);

        if(this.elem.dom.innerHTML.length == 0 && this.emptyText)
        {
            this.empty = true;
            this.elem.update(this.emptyText);
            this.elem.addClass("labkey-edit-in-place-empty");
        }
    },

    onMouseOver: function(){
        this.elem.addClass("labkey-edit-in-place-hover");
    },

    onMouseOut: function(){
        this.elem.removeClass("labkey-edit-in-place-hover");
    },

    onClick: function(){
        this.editor.startEdit(this.elem, this.empty ? "" : this.elem.innerHTML);
    },

    onBeforeStartEdit: function(editor, element, value) {
        this.fireEvent("beforestartedit", editor, element, value);
    },

    onBeforeEditComplete: function(editor, value, startValue){
        return this.fireEvent("beforecomplete", editor, value, startValue);
    },

    onEditComplete: function(editor, value, oldValue){
        if (value == oldValue)
            return;
        if (this.updateHandler)
        {
            this.elem.addClass("labkey-edit-in-place-updating");
            this.elem.update(value);
            if (typeof this.updateHandler == "function")
                this.updateHandler(value, oldValue, this.onUpdateComplete, this.onUpdateFailure, this);
            else
                this.updateHandler.fn.call(this.updateHandler.scope || this, value, oldValue, this.onUpdateComplete, this.onUpdateFailure, this);
        }
        else
        {
            this.fireEvent("complete", editor, value, oldValue);
            this.onUpdateComplete(value);
        }
    },

    onCancelEdit: function(editor, value, startValue){
        this.fireEvent("canceledit", editor, value, startValue);
    },

    onUpdateComplete: function(value, oldValue){
        this.elem.removeClass("labkey-edit-in-place-updating");
        this.empty = value.toString().length == 0;
        if (this.empty && this.emptyText)
        {
            this.elem.update(this.emptyText);
            this.elem.addClass("labkey-edit-in-place-empty");
        }
        else
        {
            this.elem.removeClass("labkey-edit-in-place-empty");
            this.elem.update(value);
        }
    },

    onUpdateFailure: function(value, oldValue) {
        this.elem.removeClass("labkey-edit-in-place-updating");
        this.elem.update(oldValue);
    }

});
