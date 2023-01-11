/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace('Ext.ux.dd');

// UNDONE: could refactor this to use the GridPanel and DataView adapter and reuse this class for both types.
Ext.ux.dd.DataViewDragZone = Ext.extend(Ext.dd.DragZone, {
    constructor: function (dataView, config) {
        this.view = dataView;
        Ext.ux.dd.DataViewDragZone.superclass.constructor.call(this, this.view.getContentTarget(), config);
        this.ddel = document.createElement('div');
        this.ddel.className = 'x-grid-dd-wrap';
    },

    getDragData : function (e) {
        var t = e.getTarget(this.view.itemSelector, 10);
        if (!t)
            return false;
        var rowIndex = this.view.indexOf(t);
        if (rowIndex > -1) {
            if (!this.view.isSelected(rowIndex) || e.hasModifier()) {
                this.view.select(rowIndex);
            }
            var selectedRecords = this.view.getSelectedRecords();
            return { grid: this.view, ddel: this.ddel, rowIndex: rowIndex, selections: selectedRecords, repairXY: Ext.fly(t).getXY() };
        }
        return false;
    },

    onInitDrag : function (e) {
        var data = this.dragData;
        var count = data.selections.length;
        var text = this.view.ddText || "{0} selected row{1}";
        this.ddel.innerHTML = String.format(text, count, count == 1 ? "" : "s");
        this.proxy.update(this.ddel);
    },

    afterRepair : function () {
        this.dragging = false;
    },

    getRepairXY : function (e, data) {
        return data.repairXY;
    },

    onEndDrag : function (data, e) {

    },
    
    onValidDrop : function (dd, e, id) {
        this.hideProxy();
    },

    beforeInvalidDrop : function (e, id) {

    }
});

/**
 * Enable drag drop to reorder rows within a Ext.grid.GridPanel or Ext.DataView.
 *
 * @see http://www.sencha.com/forum/showthread.php?21913-SOLVED-Grid-Drag-and-Drop-reorder-rows
 * @example
 * {
 *     xtype: "grid", // or "dataview", "listview"
 *     plugins: [new Ext.ux.dd.GridDragDropRowOrder(
 *     {
 *         copy: true // false by default
 *         scrollable: true, // enable scrolling support (default is false)
 *         targetCfg: { ... } // any properties to apply to the actual DropTarget
 *     })]
 * }
 */
Ext.ux.dd.GridDragDropRowOrder = Ext.extend(Ext.util.Observable,
{
    copy: false,

    scrollable: true,

    constructor : function(config)
    {
        // HACK : 13669 - customize view jumping when using drag/drop to reorder columns/filters/sorts
        Ext.dd.DDM.getLocation = function(oDD) {
            if (! this.isTypeOfDD(oDD)) {
                return null;
            }

            var el = oDD.getEl(), pos, x1, x2, y1, y2, t, r, b, l, region;

            try {
                pos= Ext.lib.Dom.getXY(el);
            } catch (e) { }

            if (!pos) {
                return null;
            }

            x1 = pos[0];
            x2 = x1 + el.offsetWidth;
            y1 = pos[1];
            y2 = y1 + el.offsetHeight;

            t = y1 - oDD.padding[0];
            r = x2 + oDD.padding[1];
            b = y2 + oDD.padding[2];
            l = x1 - oDD.padding[3];

            region = new Ext.lib.Region( t, r, b, l );
            /*
             * The code below is to ensure that large scrolling elements will
             * only have their visible area recognized as a drop target, otherwise it
             * can potentially erronously register as a target when the element scrolls
             * over the top of something below it.
             */
            el = Ext.get(el.parentNode);
            while (el && el.dom.tagName != "BODY" && region) {
                if (el.isScrollable()) {
                    // check whether our element is visible in the view port:
                    region = region.intersect(el.getRegion());
                }
                el = el.parent();
            }
            return region;
        }

        if (config)
            Ext.apply(this, config);

        this.addEvents(
        {
            beforerowmove: true,
            afterrowmove: true,
            beforerowcopy: true,
            afterrowcopy: true
        });

       Ext.ux.dd.GridDragDropRowOrder.superclass.constructor.call(this);
    },

    init : function (view)
    {
        this.view = view;
        if (this.view.getView)
        {
            // GridPanel adapter
            Ext.apply(this, {
                getRowIndex : function (t) {
                    return this.view.getView().findRowIndex(t);
                },
                getRow : function (n) { return this.view.getView.getRow(n); },
                selectRecords : function (records) { 
                    var sm = this.view.getView().getSelectionModel();
                    if (sm)
                        sm.selectRecords(records);
                },
                getScroller : function () { return this.view.getEditorParent(); }
            });
            view.enableDragDrop = true;
        }
        else
        {
            // DataView adapter
            Ext.apply(this, {
                getRowIndex : function (t) {
                    var t = Ext.fly(t).findParent(this.view.itemSelector);
                    if (!t)
                        return false;
                    var i = this.view.indexOf(t);
                    return i == -1 ? false : i;
                },
                getRow : function (n) { return this.view.getNode(n); },
                selectRecords : function (records) { return this.view.select(records); },
                getScroller : function () { return this.view.getContentTarget().dom; }
            });
        }

        view.on({
            afterrender: { fn: this.onViewRender, scope: this, single: true }
        });
    },

    getRowIndex : Ext.emptyFn,
    getRow : Ext.emptyFn,
    selectRecords : Ext.emptyFn,
    getScroller : Ext.emptyFn,

    onViewRender : function (view)
    {
        var self = this;

        if (!view.getView && !view.dragZone) {
            // initialize the DragZone for DataView
            view.dragZone = new Ext.ux.dd.DataViewDragZone(view, {
                ddGroup: view.ddGroup || 'GridDD'
            });
        }

        this.target = new Ext.dd.DropTarget(view.getEl(),
        {
            ddGroup: view.ddGroup || 'GridDD',
            view: view,
            viewDropTarget: this,

            notifyDrop: function(dd, e, data)
            {
                if (this.insertionEl)
                {
                    Ext.destroy(this.insertionEl);
                    delete this.insertionEl;
                }

                // determine the row
                var t = e.getTarget();
                var rindex = self.getRowIndex(t);

                if (rindex === false || rindex == data.rowIndex)
                {
                    return false;
                }
                // fire the before move/copy event
                if (this.viewDropTarget.fireEvent(self.copy ? 'beforerowcopy' : 'beforerowmove', this.viewDropTarget, data.rowIndex, rindex, data.selections, 123) === false)
                {
                    return false;
                }

                // update the store
                var ds = this.view.getStore();

                // Changes for multiselction by Spirit
                var selections = new Array();
                var keys = ds.data.keys;
                for (var key in keys)
                {
                    for (var i = 0; i < data.selections.length; i++)
                    {
                        if (keys[key] == data.selections[i].id)
                        {
                            // Exit to prevent drop of selected records on itself.
                            if (rindex == key)
                            {
                                return false;
                            }
                            selections.push(data.selections[i]);
                        }
                    }
                }

                // fix rowindex based on before/after move
                if (rindex > data.rowIndex && this.rowPosition < 0)
                {
                    rindex--;
                }
                if (rindex < data.rowIndex && this.rowPosition > 0)
                {
                    rindex++;
                }

                // fix rowindex for multiselection
                if (rindex > data.rowIndex && data.selections.length > 1)
                {
                    rindex = rindex - (data.selections.length - 1);
                }

                // we tried to move this node before the next sibling, we stay in place
                if (rindex == data.rowIndex)
                {
                    return false;
                }

                // fire the before move/copy event
                /* dupe - does it belong here or above???
                if (this.viewDropTarget.fireEvent(self.copy ? 'beforerowcopy' : 'beforerowmove', this.viewDropTarget, data.rowIndex, rindex, data.selections, 123) === false)
                {
                    return false;
                }
                */

                if (!self.copy)
                {
                    for (var i = 0; i < data.selections.length; i++)
                    {
                        ds.remove(ds.getById(data.selections[i].id));
                    }
                }

                for (var i = selections.length - 1; i >= 0; i--)
                {
                    var insertIndex = rindex;
                    ds.insert(insertIndex, selections[i]);
                }

                // re-select the row(s)
                self.selectRecords(data.selections);

                // fire the after move/copy event
                this.viewDropTarget.fireEvent(self.copy ? 'afterrowcopy' : 'afterrowmove', this.viewDropTarget, data.rowIndex, rindex, data.selections);
                return true;
            },

            notifyOver: function(dd, e, data)
            {
                var t = e.getTarget();
                var rindex = self.getRowIndex(t);

                // If on first row, remove upper line. Prevents negative index error as a result of rindex going negative.
                if (rindex < 0 || rindex === false)
                {
                    if (this.insertionEl)
                        this.insertionEl.addClass("grid-row-insertion-invalid");
                    return this.dropNotAllowed;
                }

                var invalid = false;

                // Similar to the code in notifyDrop. Filters for selected rows and quits function if any one row matches the current selected row.
                var ds = this.view.getStore();
                var keys = ds.data.keys;
                for (var key in keys)
                {
                    for (var i = 0; i < data.selections.length; i++)
                    {
                        if (keys[key] == data.selections[i].id)
                        {
                            if (rindex == key)
                            {
                                invalid = true;
                                break;
                            }
                        }
                    }
                }

                try
                {
                    // Find position of row relative to page (adjusting for grid's scroll position)
                    var currentRow = self.getRow(rindex);
                    var resolvedRow = new Ext.Element(currentRow).getY() - self.getScroller().scrollTop;
                    var rowHeight = currentRow.offsetHeight;

                    // Cursor relative to a row. -ve value implies cursor is above the row's middle and +ve value implues cursor is below the row's middle.
                    this.rowPosition = e.getPageY() - resolvedRow - (rowHeight/2);

                    // Create drag line.
                    if (!this.insertionEl)
                        this.insertionEl = this.createInsertionEl();

                    if (this.rowPosition > 0)
                    {
                        // If the pointer is on the bottom half of the row.
                        this.insertionEl.alignTo(currentRow, "l-bl");
                    }
                    else
                    {
                        // If the pointer is on the top half of the row.
                        this.insertionEl.alignTo(currentRow, "l-tl");
                        /*
                        if (rindex - 1 >= 0)
                        {
                            var previousRow = self.getRow(rindex - 1);
                            this.insertionEl.alignTo(previousRow, "l-bl");
                        }
                        else
                        {
                            // If the pointer is on the top half of the first row.
                            this.insertionEl.alignTo(currentRow, "l-tl");
                        }
                        */
                    }

                    if (invalid)
                        this.insertionEl.addClass("grid-row-insertion-invalid");
                    else
                        this.insertionEl.removeClass("grid-row-insertion-invalid");

                    this.insertionEl.show();
                }
                catch (err)
                {
                    console.warn(err);
                    rindex = false;
                }
                return (invalid || rindex === false)? this.dropNotAllowed : this.dropAllowed;
            },

            notifyOut: function(dd, e, data)
            {
                // Remove drag lines when pointer leaves the view.
                if (this.insertionEl)
                    this.insertionEl.addClass("grid-row-insertion-invalid");
            },

            createInsertionEl: function()
            {
                var el = Ext.getBody().createChild({tag:"div", cls: "grid-row-insertion", style: "display:none;"});
                el.setWidth(Ext.fly(self.getScroller()).getWidth(true));
                el.forwardMouseEvents();
                return el;
            }

        });

        if (this.targetCfg)
        {
            Ext.apply(this.target, this.targetCfg);
        }

        if (this.scrollable)
        {
            Ext.dd.ScrollManager.register(self.getScroller());
            view.on({
                beforedestroy: this.onBeforeDestroy,
                scope: this,
                single: true
            });
        }
    },

    getTarget: function()
    {
        return this.target;
    },

    getView: function()
    {
        return this.view;
    },

    getCopy: function()
    {
        return this.copy ? true : false;
    },

    setCopy: function(b)
    {
        this.copy = b ? true : false;
    },

    onBeforeDestroy : function (view)
    {
        // if we previously registered with the scroll manager, unregister
        // it (if we don't it will lead to problems in IE)
        Ext.dd.ScrollManager.unregister(this.getScroller());
    }
});
Ext.preg('grid-dd-row-order', Ext.ux.dd.GridDragDropRowOrder);
