/*
 * Copyright (c) 2006-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.element.AutoCompletionField', {

    extend : 'Ext.Component',

    constructor : function(config) {

        this.completionTask = new Ext4.util.DelayedTask(this.complete, this);
        this.optionSelectedIndex = 0;

        this.callParent([config]);
    },

    initComponent : function() {

        var completionDiv = Ext.id();
        var completionBodyDiv = Ext.id();
        this.fieldId = this.tagConfig.id || Ext.id();

        this.tagConfig['id'] = this.fieldId;

        this.html = Ext.DomHelper.createHtml(this.tagConfig);
        this.html = this.html.concat(Ext.DomHelper.createHtml({
            tag : 'div',
            id  : completionDiv,
            cls : 'labkey-completion',
            children: [{tag : 'div', id : completionBodyDiv}]
        }));

        this.listeners = {
            render  :  {fn : function(cmp){

                this.completionDiv = Ext.get(completionDiv);
                this.completionBody = Ext.get(completionBodyDiv);
                this.completionField = Ext.get(this.fieldId);

                Ext.EventManager.addListener(this.fieldId, 'keydown', this.onKeyDown, this);
                Ext.EventManager.addListener(this.fieldId, 'keyup', this.onKeyUp, this);
                Ext.EventManager.addListener(this.fieldId, 'blur', this.hideCompletionDiv, this);
                //Ext.EventManager.addListener(this.fieldId, 'change', function(){console.log('onChange');LABKEY.setDirty(true);});

            }, scope : this}
        };

        // create the store for the completion records
        Ext4.define('LABKEY.data.Completions', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'value'}
            ]
        });

        var config = {
            model   : 'LABKEY.data.Completions',
            autoLoad: true,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : this.completionUrl ,
                reader : {
                    type : 'json',
                    root : 'completions'
                }
            },
            listeners : {
                load : {
                    fn : function(s, recs, success, operation, ops) {
                        this.completionStoreLoaded = true;
                    },
                    scope : this
                }
            }
        }
        this.completionStore = Ext4.create('Ext.data.Store', config);

        this.callParent([arguments]);
    },

    isCtrlKey : function(event)
    {
        var keynum = event.getKey();

        return (keynum == Ext.EventObject.DOWN ||
                keynum == Ext.EventObject.UP ||
                keynum == Ext.EventObject.ENTER ||
                keynum == Ext.EventObject.TAB ||
                //keynum == Ext.EventObject.BACKSPACE ||
                keynum == Ext.EventObject.ESC);
    },

    onKeyDown : function(event, cmp)
    {
        if (!this.isCtrlKey(event) || !this.completionDiv.isVisible())
            return true;

        var keynum = event.getKey();
        var stopEvent = false;

        switch (keynum)
        {
            case Ext.EventObject.DOWN:
                this.changeSelectedOption(true);
                stopEvent = true;
                break;
            case Ext.EventObject.UP:
                this.changeSelectedOption(false);
                stopEvent = true;
                break;
            case Ext.EventObject.ENTER:
            case Ext.EventObject.TAB:
                this.selectOption(-1);
                stopEvent = true;
                break;
            case Ext.EventObject.ESC:
                this.hideCompletionDiv();
                stopEvent = true;
                break;
/*
            case Ext.EventObject.BACKSPACE:
                this.hideCompletionDiv();
                break;
*/
        }

        if (stopEvent)
            event.stopEvent();
    },

    selectOption : function(index)
    {
        if (index >= 0)
            this.optionSelectedIndex = index;
        var newlineIndex = this.element.value.lastIndexOf('\n');
        var newValue = document.getElementById("insertSpan" + this.optionSelectedIndex).innerHTML;
        if (newlineIndex >= 0)
            this.element.value = this.element.value.substring(0, newlineIndex + 1) + newValue;
        else
            this.element.value = newValue;
        this.hideCompletionDiv();
    },

    changeSelectedOption : function(forward)
    {
        var oldSpan = document.getElementById("completionTR" + this.optionSelectedIndex);
        var newIndex = -1;
        if (forward)
            this.optionSelectedIndex = this.optionSelectedIndex < this.optionCount - 1 ? this.optionSelectedIndex + 1 : 0;
        else
            this.optionSelectedIndex = this.optionSelectedIndex > 0 ? this.optionSelectedIndex - 1 : this.optionCount - 1;
        var newSpan = document.getElementById("completionTR" + this.optionSelectedIndex);
        oldSpan.className = "labkey-completion-nohighlight";
        newSpan.className = "labkey-completion-highlight";
    },

    onKeyUp : function(event, element, completionURLPrefix)
    {
        if (this.isCtrlKey(event))
            return false;

        this.element = element;
        this.completionTask.delay(150);
    },

    complete : function()
    {
        var typedString = this.element.value;
        var newline = typedString.lastIndexOf('\n');
        if (newline >= 0)
            typedString = typedString.substring(newline, typedString.length);
        if (!typedString)
            this.hideCompletionDiv();
        else if (this.completionStoreLoaded)
            this.onHandleChange(typedString);
    },

    onHandleChange : function(txt) {

        if (this.completionStore)
        {
            txt = txt.replace(/[^a-z0-9_+\-]+/gi, ' ');
            txt = Ext4.util.Format.trim(txt);
            txt = Ext4.escapeRe(txt);

            var regexp = new RegExp(txt, 'i');

            var matches = this.completionStore.queryBy(
                function(record){
                    var term = record.get('name');
                    if (term)
                        return term.match(regexp);

                    return false;
                }
            );
            var count = matches.getCount();

            if (!count)
            {
                this.hideCompletionDiv();
                return;
            }

            var completionText = "<table class='labkey-completion' width='100%'>";

            for (var i=0; i < count; i++)
            {
                var completion = matches.getAt(i);
                var display = completion.get("name");
                var insert = completion.get("value");
                var styleClass = "labkey-completion-nohighlight";
                if (i == this.optionSelectedIndex)
                    styleClass = "labkey-completion-highlight";

                completionText += "<tr class='" + styleClass + "' style='cursor:pointer'><td id='completionTR" + i + "'><span onclick='selectOption(" + i +
                        ")'>" + display +
                        "</span><span style='display:none' id='insertSpan" + i + "'>" + insert + "</span>" +
                        "</td></tr>\n";
            }
            this.optionCount = count;
            completionText += "</table>";
            this.completionBody.dom.innerHTML = completionText;
            this.showCompletionDiv(this.element);
        }
    },

    showCompletionDiv : function(elem)
    {
        if (this.completionDiv.isVisible())
            return;

        var div = this.completionDiv.dom;
        var posLeft = 0;
        var posTop = 0;
        var offsetElem = elem;
        if (offsetElem.tagName == "TEXTAREA")
        {
            var height = offsetElem.offsetHeight;
            if (height)
            {
                var rows = elem.rows;
                if (rows)
                {
                    var heightPerRow = (0.0 + height) / (0.0 + rows);
                    var lineCount = 1;
                    for (var i = 0; i < offsetElem.value.length; i++)
                    {
                        if (offsetElem.value.charAt(i) == '\n')
                            lineCount++;
                    }
                    //var textHeight = Math.min(lineCount * (heightPerRow), height);
                    // offset us back a sufficient number of lines if we're in a textarea:
                    //posTop = textHeight - height;
                    posTop = Math.min(lineCount * (heightPerRow), height);
                }
            }
        }
        else
            posTop += offsetElem.offsetHeight;

        posLeft += this.completionField.getX();
        posTop += this.completionField.getY();
        //posTop += elem.offsetHeight;

/*
        while (offsetElem && offsetElem.tagName != "BODY")
        {
            posLeft += offsetElem.offsetLeft;
            posTop += offsetElem.offsetTop;
            offsetElem = offsetElem.offsetParent;
        }
        if (!offsetElem)
            return false;

        posTop += elem.offsetHeight;
*/
/*
        div.style.top = posTop + 'px';
        div.style.left = posLeft + 'px';
*/
        //div.style.display = "block";
        this.completionDiv.setXY([posLeft, posTop]);
        this.completionDiv.setVisible(true, true);
        return false;
    },

    hideCompletionDiv : function()
    {
        this.completionDiv.hide();
    }
});

