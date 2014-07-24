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

        supportMemberClose: true,

        hookButtons : function(v) {

            if (!v || !v.getEl()) {
                return;
            }

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

                    var childEl = {};
                    if (closes[c].children.length > 0)
                        childEl = Ext.get(closes[c].children[0]);

                    if (recordId) {
                        var rec = v.getStore().getById(recordId);
                        el.recid = recordId;
                        childEl.recid = recordId;

                        if (rec.get('isPlot') === true || LABKEY.app.view.Selection.supportMemberClose) {
                            var members = rec.get('members');
                            if (members) {
                                // listen for each member
                                var memberIdx = el.getAttribute('member-index');
                                el.memberIndex = memberIdx;
                                childEl.memberIndex = memberIdx;
                            }
                        }

                        el.un('click', v._wrapClick, v);
                        el.on('click', v._wrapClick, v);
                    }
                }
            }
        },

        uniqueNameAsArray : function(uniqueName) {
            var init = uniqueName.split('].');
            for (var i=0; i < init.length; i++) {
                init[i] = init[i].replace('[', '');
                init[i] = init[i].replace(']', '');
            }
            return init;
        }
    },

    _wrapClick : function(xevt, xel) {
        xevt.stopPropagation();
        this.onRemoveClick(Ext.get(xel));
    },

    onRemoveClick : function(element) {
        if (element) {
            var store = this.getStore();
            if (store) {
                var rec = store.getById(element.recid);
                if (rec) {
                    var memberIdx = parseInt(element.memberIndex);
                    if (Ext.isNumber(memberIdx)) {
                        if (element.hasCls('measure')) {
                            // We're dealing with a plot selection.
                            this.fireEvent('removeplotselection', rec.id, memberIdx);
                        }
                        else {
                            var members = rec.get('members');
                            this.fireEvent('removefilter', rec.id, rec.get('hierarchy'), members[memberIdx] ? members[memberIdx].uniqueName : undefined);
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
                console.warn('Unable to find selection store:', element.recid);
            }
        }
        else {
            console.warn('Unable to find element for removal');
        }
    },

    constructor : function(config) {
        this.callParent([config]);
        this.addEvents('clearhierarchy', 'operatorchange', 'itemselect', 'removefilter');
    },

    initComponent : function() {

        this.callParent();

        this.hookTask = new Ext.util.DelayedTask(function() {
            LABKEY.app.view.Selection.hookButtons(this);
        }, this);

        this.on('viewready', this.doHook, this);
        this.on('itemupdate', this.doHook, this);
        this.on('refresh', this.doHook, this);

        /* NOTE: This will render any itemclick listeners useless */
        this.on('itemclick', function(v, f, is, idx, evt) {
            if (!Ext.isDefined(evt.target.type) || evt.target.type.indexOf('select') === -1) {
                this.fireEvent('itemselect', v, f, is, idx);
            }
            return false;
        }, this);
    },

    onOperatorChange : function(value, evt, el) {
        var ns = LABKEY.app.model.Filter;
        var valType = ns.dynamicOperatorTypes ? ns.convertOperator(value) : value;
        this.fireEvent('operatorchange', {
            filterId: this.getStore().getAt(0).id,
            value: valType
        });
    },

    doHook : function() {
        this.hookTask.delay(50);
    }
});