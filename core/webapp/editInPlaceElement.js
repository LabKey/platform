/*
 * Copyright (c) 2010 LabKey Corporation
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

    editor: null,
    oldText: null,
    multiLine: false,
    growFactor: 20,
    border: true,
    updateHandler: null,
    emptyText: null,

    constructor: function(config){

        this.addEvents("beforecomplete", "canceledit");
        Ext.apply(this, config);

        LABKEY.ext.EditInPlaceElement.superclass.constructor.apply(this, arguments);

        if (!this.applyTo)
            throw "You must specify an applyTo property in your config!";

        this.el = Ext.get(this.applyTo);
        if (!this.el)
            throw "Could not find element '" + this.applyTo + "'!";

        this.el.set({
            title: "Click to Edit"
        });

        this.checkForEmpty();

        this.el.addClass("labkey-edit-in-place");
        this.el.on("mouseover", this.onMouseOver, this);
        this.el.on("mouseout", this.onMouseOut, this);
        this.el.on("click", function(){this.startEdit();}, this);
    },

    checkForEmpty: function(){
        if (this.emptyText && (this.el.dom.innerHTML.length == 0 || this.el.dom.innerHTML == "&nbsp;"))
        {
            this.el.update(this.emptyText);
            this.el.addClass("labkey-edit-in-place-empty");
        }
    },

    onMouseOver: function(){
        this.el.addClass("labkey-edit-in-place-hover");
    },

    onMouseOut: function(){
        this.el.removeClass("labkey-edit-in-place-hover");
    },

    startEdit: function(){
        if (this.editor || this.el.hasClass("labkey-edit-in-place-updating"))
            return;
        
        //create the editor as a peer to the bound element
        var config = {
            tag: (this.multiLine ? 'textarea' : 'input'),
            style: 'display:none;overflow:hidden;'
        };
        config.style += this.border ? "border:1px solid #C0C0C0;" : "border:0px";

        if (!this.multiLine)
            config.type = 'text';

        this.editor = this.el.parent().createChild(config);

        //create the offscreen sizing div
        this.sizingDiv = Ext.getBody().createChild({
            tag: 'div',
            style: 'position:absolute;left:-10000px;top:-10000px'
        });


        //make the editor's text styles match the element
        if (this.el.hasClass("labkey-edit-in-place-empty"))
        {
            this.el.removeClass("labkey-edit-in-place-empty");
            this.el.update("");
        }

        var styles = this.el.getStyles('padding-top', 'padding-bottom', 'padding-left',
              'padding-right', 'line-height', 'font-size',
              'font-family', 'font-weight', 'font-style');

        styles.width = this.el.getWidth() + "px";
        this.editor.setStyle(styles);
        this.sizingDiv.setStyle(styles);

        //determine the height of one line of text for the growfactor
        this.sizingDiv.update("Xg");
        this.growFactor = this.sizingDiv.getHeight() * 1.5;

        //set the start text
        var startText = this.el.dom.innerHTML;
        this.oldText = startText;
        if (this.multiLine)
            this.editor.update(startText);
        else
            this.editor.set({value: startText});

        if (this.multiLine)
            this.autoSize();

        this.editor.setDisplayed(true);
        this.el.setDisplayed(false);

        this.editor.focus();
        this.editor.dom.select();
        
        this.editor.on("blur", this.completeEdit, this);
        if (this.multiLine)
            this.editor.on("keyup", this.autoSize, this);

        this.editor.addKeyMap([
            {
                key: Ext.EventObject.ENTER,
                fn: this.completeEdit,
                scope: this
            },
            {
                key: Ext.EventObject.ESC,
                fn: this.cancelEdit,
                scope: this
            }
        ]);
    },

    autoSize: function(){
        var value = this.editor.getValue();
        value = Ext.util.Format.htmlEncode(value);
        value = value.replace(/\n/g, "<br/>");

        this.sizingDiv.update(value);

        var bw = this.editor.getBorderWidth("tb") || 2;
        var height = this.sizingDiv.getHeight() + bw + this.growFactor;
        this.editor.setHeight(height);
    },

    completeEdit: function(){
        var value = this.editor.getValue();
        this.endEdit();

        if (value != this.oldText && false !== this.fireEvent("beforecomplete", value, this.oldText))
        {
            this.el.update(value);
            this.processChange(value, this.oldText);
        }
        else
            this.checkForEmpty();
    },

    cancelEdit: function(){
        this.endEdit();
        this.fireEvent("canceledit", this.oldText);
    },

    endEdit: function(){
        this.editor.un("blur", this.completeEdit, this);

        this.el.setDisplayed(true);
        this.editor.setDisplayed(false);

        this.editor.remove();
        this.editor = null;

        this.sizingDiv.remove();
        this.sizingDiv = null;
    },

    processChange: function(value, oldValue){
        if (this.updateHandler)
        {
            this.el.addClass("labkey-edit-in-place-updating");
            this.el.update(value);
            if (typeof this.updateHandler == "function")
                this.updateHandler(value, oldValue, this.onUpdateComplete, this.onUpdateFailure, this);
            else
                this.updateHandler.fn.call(this.updateHandler.scope || this, value, oldValue, this.onUpdateComplete, this.onUpdateFailure, this);
        }
        else
            this.onUpdateComplete(value);
    },

    onUpdateComplete: function(value, oldValue){
        this.el.removeClass("labkey-edit-in-place-updating");
        this.el.update(value);
        this.checkForEmpty();
    },

    onUpdateFailure: function(value, oldValue) {
        this.el.removeClass("labkey-edit-in-place-updating");
        this.el.update(oldValue);
        this.checkForEmpty();
    }

});
