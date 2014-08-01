/*
 * Copyright (c) 2006-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.element.AutoCompletionField', {

    extend : 'Ext.Component',

    constructor : function(config) {

        Ext4.applyIf(config, {
            sharedStore     : false,
            sharedStoreId   : 'autocomplete-shared-store' + config.completionUrl,
            maxDivHeight    : 190                         // max height of the completion div before overflow
        });

        this.completionTask = new Ext4.util.DelayedTask(this.complete, this);
        this.hideCompletionTask = new Ext4.util.DelayedTask(this.hideCompletionDiv, this);

        this.optionSelectedIndex = 0;

        this.callParent([config]);
    },

    initComponent : function() {

        var completionDiv = Ext4.id();
        var completionBodyDiv = Ext4.id();

        // the tagConfig includes the input tag specification, also wire up
        // divs for the completion elements
        if (this.tagConfig)
        {
            this.fieldId = this.tagConfig.id || Ext4.id();
            this.tagConfig['id'] = this.fieldId;

            this.html = Ext4.DomHelper.createHtml(this.tagConfig);
            this.html = this.html.concat(Ext4.DomHelper.createHtml({
                tag : 'div',
                id  : completionDiv,
                cls : 'labkey-completion',
                children: [{tag : 'div', id : completionBodyDiv}]
            }));
        }
        else if (this.fieldId)
        {
            // wire up the completions to an existing input element, the fieldId
            // should identify the input element
            this.html = Ext4.DomHelper.createHtml({
                tag : 'div',
                id  : completionDiv,
                cls : 'labkey-completion',
                children: [{tag : 'div', id : completionBodyDiv}]
            });
        }

        this.listeners = {
            render  :  {fn : function(cmp){

                var wrapper = this.getEl().up('div.' + Ext4.resetCls);
                if (wrapper)
                    wrapper.removeCls(Ext4.resetCls);
                this.completionDiv = Ext4.get(completionDiv);
                this.completionBody = Ext4.get(completionBodyDiv);
                this.completionField = Ext4.get(this.fieldId);

                Ext4.EventManager.addListener(this.fieldId, 'keydown', this.onKeyDown, this);
                Ext4.EventManager.addListener(this.fieldId, 'keyup', this.onKeyUp, this);
                Ext4.EventManager.addListener(this.fieldId, 'blur', function(){this.hideCompletionTask.delay(250);}, this);
                //Ext4.EventManager.addListener(this.fieldId, 'change', function(){console.log('onChange');LABKEY.setDirty(true);});

            }, scope : this}
        };

        this.tpl = new Ext4.XTemplate(
            '<table class="labkey-completion">',
                '<tpl for=".">',
                '<tr style="cursor:pointer">',
                    '<td  class="{style}" id="{["completionTR" + (xindex-1)]}">',
                        '<span onclick="{[this.getClickHandler(values, (xindex-1))]}">{name}</span>',
                        '<span style="display:none" id="{["insertSpan" + (xindex-1)]}">{value}</span>',
                    '</td></tr>',
            '</tpl></table>',
            {
                getClickHandler : function(data, idx) {

                    var fnTxt = "(function(cmp, id){" +
                        "var cmp = Ext4.getCmp(cmp);" +
                        "if (cmp)" +
                            "cmp.selectOption(id);" +
                    "})('" + data.cmpId + "'," + idx + ")";

                    return fnTxt;
                }
            }
        );
        this.createCompletionStore();

        this.callParent([arguments]);
    },

    createCompletionStore : function() {

        var storeId;
        if (this.sharedStore)
        {
            storeId = this.sharedStoreId;
            this.completionStore = Ext4.data.StoreManager.lookup(storeId);
        }

        if (!this.completionStore)
        {
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
                storeId : storeId,
                proxy   : {
                    type   : 'ajax',
                    url : this.completionUrl ,
                    reader : {
                        type : 'json',
                        root : 'completions'
                    }
                }
            }
            this.completionStore = Ext4.create('Ext.data.Store', config);
        }
    },

    isCtrlKey : function(event)
    {
        var keynum = event.getKey();

        return (keynum == Ext4.EventObject.DOWN ||
                keynum == Ext4.EventObject.UP ||
                keynum == Ext4.EventObject.ENTER ||
                keynum == Ext4.EventObject.TAB ||
                //keynum == Ext4.EventObject.BACKSPACE ||
                keynum == Ext4.EventObject.ESC);
    },

    onKeyDown : function(event, cmp)
    {
        if (!this.isCtrlKey(event) || !this.completionDiv.isVisible())
            return true;

        var keynum = event.getKey();
        var stopEvent = false;

        switch (keynum)
        {
            case Ext4.EventObject.DOWN:
                this.changeSelectedOption(true);
                stopEvent = true;
                break;
            case Ext4.EventObject.UP:
                this.changeSelectedOption(false);
                stopEvent = true;
                break;
            case Ext4.EventObject.ENTER:
            case Ext4.EventObject.TAB:
                this.selectOption(-1);
                stopEvent = true;
                break;
            case Ext4.EventObject.ESC:
                this.hideCompletionDiv();
                stopEvent = true;
                break;
/*
            case Ext4.EventObject.BACKSPACE:
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
        var prevIdx = this.optionSelectedIndex;
        if (forward)
            this.optionSelectedIndex = this.optionSelectedIndex < this.optionCount - 1 ? this.optionSelectedIndex + 1 : this.optionCount-1;
        else
            this.optionSelectedIndex = this.optionSelectedIndex > 0 ? this.optionSelectedIndex - 1 : 0;

        var el = Ext4.fly("completionTR" + prevIdx);
        if (el)
            el.replaceCls('labkey-completion-highlight', 'labkey-completion-nohighlight');

        el = Ext4.fly("completionTR" + this.optionSelectedIndex);
        if (el)
            el.replaceCls('labkey-completion-nohighlight', 'labkey-completion-highlight');

        // scroll the hilighted element into view if necessary
        var delta = el.dom.offsetTop - this.completionDiv.getScroll().top;
        if (delta < 0)
            this.completionDiv.scroll('up', Math.abs(delta));
        else if (delta >= this.maxDivHeight)
            this.completionDiv.scroll('down', el.getHeight() + (delta - this.maxDivHeight));
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
        else
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

            var data = [];
            for (var i=0; i < count; i++)
            {
                var completion = matches.getAt(i).data;

                completion.style = (this.optionSelectedIndex == i) ? 'labkey-completion-highlight' : 'labkey-completion-nohighlight';
                completion.cmpId = this.getId();

                data.push(completion);
            }

            this.optionCount = count;
            this.completionBody.update(this.tpl.apply(data));
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

        //posTop += elem.offsetHeight;
*/
/*
        div.style.top = posTop + 'px';
        div.style.left = posLeft + 'px';
*/
        //div.style.display = "block";
        this.completionDiv.setLeftTop(posLeft, posTop);
        this.completionDiv.setVisible(true, true);
        return false;
    },

    hideCompletionDiv : function()
    {
        this.completionDiv.hide();
        this.completionBody.update('');
    }
});

