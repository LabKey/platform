/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.view.Selection', {
    extend: 'Ext.view.View',

    ui: 'custom',

    cls: 'selectionfilter',

    itemSelector: 'div.selitem',

    statics : {
        hookButtons : function(v) {
            //
            // hook events for and/or selection
            //
            var selectEl = v.getEl().select('select');
            if (selectEl) {
                selectEl.on('change', function(evt, el) {
                    var value = selectEl.elements[0].value;
                    this.onOperatorChange(value, evt, el);
                }, v);
            }

            //
            // hook events for item close
            //
            var closes = v.getEl().select('.closeitem');
            if (closes && Ext.isArray(closes.elements)) {
                closes = closes.elements;
                for (var c=0; c < closes.length; c++) {

                    var el = Ext.get(closes[c]);
                    var recordId = el.getAttribute('data-id');

                    if (recordId) {
                        var rec = v.getStore().getById(recordId);
                        el.recid = recordId;

                        var members = rec.get('members');
                        if (members) {
                            // listen for each member
                            var memberIdx = el.getAttribute('member-index');
                            el.memberIndex = memberIdx;
                        }

                        el.on('click', function(xevt, xel) { this.onRemoveClick(Ext.get(xel)); }, v);
                    }
                }
            }
        }
    },

    onRemoveClick : function(element) {
        if (element) {
            var store = this.getStore();
            var rec = store.getById(element.recid);
            if (rec) {
                var memberIdx = parseInt(element.memberIndex);
                if (Ext.isNumber(memberIdx)) {
                    if (element.hasCls('measure')) {
                        // We're dealing with a plot selection.
                        this.fireEvent('removeplotselection', rec.id, memberIdx);
                    } else {
                        var members = rec.get('members');
                        this.fireEvent('removefilter', rec.id, rec.get('hierarchy'), members[memberIdx] ? members[memberIdx].uname : undefined);
                    }
                }
                else {
                    this.fireEvent('removefilter', rec.id);
                }
            }
            else {
                console.warn('Unable to find record for removal:', element.recid);
            }
        }
        else {
            console.warn('Unable to find element for removal');
        }
    },

    listeners: {
        viewready : function(v) {
            LABKEY.app.view.Selection.hookButtons(v);
        }
    },

    constructor : function(config) {
        this.callParent([config]);
        this.addEvents('clearhierarchy', 'operatorchange', 'removefilter');
    },

    onOperatorChange : function(value, evt, el) {
        this.fireEvent('operatorchange', {
            filterId: this.getStore().getAt(0).id,
            value: value
        });
    }
});