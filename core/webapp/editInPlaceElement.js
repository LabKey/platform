/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
    updateConfig: null,
    enterCompletesEdit: true,

    constructor: function(config){

        this.addEvents("beforecomplete", "complete", "canceledit", "updatefail");
        Ext.apply(this, config);
        if (this.updateConfig)
        {
            this.updateConfig = Ext.applyIf(this.updateConfig, {
                method: 'POST',
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        }


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
        this.el.on("click", this.startEdit, this);

        this.editIcon = Ext.getBody().createChild({
            tag: 'div',
            cls: 'labkey-edit-in-place-icon',
            title: 'Click to Edit'
        });
        this.editIcon.anchorTo(this.el, 'tr-tr');
        this.editIcon.on("mouseover", function(){
            this.editIcon.addClass("labkey-edit-in-place-icon-hover");
        }, this);
        this.editIcon.on("mouseout", function(){
            this.editIcon.removeClass("labkey-edit-in-place-icon-hover");
            this.editIcon.removeClass("labkey-edit-in-place-icon-mouse-down");
        }, this);
        this.editIcon.on("mousedown", function(){
            this.editIcon.addClass("labkey-edit-in-place-icon-mouse-down");
        }, this);
        this.editIcon.on("mouseup", function(){
            this.editIcon.removeClass("labkey-edit-in-place-icon-mouse-down");
        }, this);
        this.editIcon.on("click", this.startEdit, this);
    },

    checkForEmpty: function(){
        if (this.emptyText && (this.el.dom.innerHTML.length == 0 || this.el.dom.innerHTML == "&nbsp;"))
        {
            this.el.update(this.emptyText);
            this.el.addClass("labkey-edit-in-place-empty");
        }
    },

    onMouseOver: function(){
        this.editIcon.addClass("labkey-edit-in-place-icon-hover");
    },

    onMouseOut: function(){
        this.editIcon.removeClass("labkey-edit-in-place-icon-hover");
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

        this.editor = this.el.parent().createChild(config, this.el.next());

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
        this.editIcon.setDisplayed(false);

        this.editor.focus();
        this.editor.dom.select();
        
        this.editor.on("blur", this.completeEdit, this);
        if (this.multiLine)
            this.editor.on("keyup", this.autoSize, this);

        var keyMap = [
        {
            key: Ext.EventObject.ESC,
            fn: this.cancelEdit,
            scope: this
        }];

        if (this.enterCompletesEdit)
        {
            keyMap.push({
                key: Ext.EventObject.ENTER,
                fn: this.completeEdit,
                scope: this
            });
        }

        this.editor.addKeyMap(keyMap);
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
        var value = Ext.util.Format.htmlEncode(this.editor.getValue());
        this.endEdit();

        if (value != this.oldText && false !== this.fireEvent("beforecomplete", value, this.oldText))
            this.processChange(value, this.oldText);
        else
            this.onUpdateComplete(this.oldText);
    },

    cancelEdit: function(){
        this.endEdit();
        this.checkForEmpty();
        this.fireEvent("canceledit", this.oldText);
    },

    endEdit: function(){
        this.editor.un("blur", this.completeEdit, this);

        this.el.setDisplayed(true);
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
            var reqConfig = Ext.apply({}, this.updateConfig);

            //set jsonData and handlers
            reqConfig.jsonData = {};
            reqConfig.jsonData[this.updateConfig.jsonDataPropName || "newValue"] = Ext.util.Format.htmlDecode(value);
            reqConfig.success = function(){
                this.onUpdateComplete(value);
            };
            reqConfig.failure = function(){
                this.onUpdateFailure(value, oldValue);
            };
            reqConfig.scope = this;

            //update the el and add the updating class
            this.el.addClass("labkey-edit-in-place-updating");
            this.el.update(value);

            //do the Ajax request
            Ext.Ajax.request(reqConfig);
        }
        else
            this.onUpdateComplete(value);
    },

    onUpdateComplete: function(value){
        this.el.removeClass("labkey-edit-in-place-updating");
        this.el.update(value);
        this.checkForEmpty();
        this.editIcon.alignTo(this.el, 'tr-tr');
        this.fireEvent("complete");
    },

    onUpdateFailure: function(value, oldValue) {
        alert("There was an error while updating the value!");
        this.el.removeClass("labkey-edit-in-place-updating");
        this.el.update(oldValue);
        this.checkForEmpty();
        this.fireEvent("updatefail");
    }

});
