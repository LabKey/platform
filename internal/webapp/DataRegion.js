/*
 * Copyright (c) 2008-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExtJs();
LABKEY.requiresScript("filter.js");

if (!LABKEY.DataRegions)
{
    LABKEY.DataRegions = {};
}

LABKEY.DataRegion = function (config)
{
    this.config = config || {};

    this.id = config.name; // XXX: may not be unique on the on page with webparts
    this.name = config.name;
    this.schemaName = config.schemaName;
    this.queryName = config.queryName;
    this.viewName = config.viewName;
    this.sortFilter = config.sortFilter;

    this.complete = config.complete;
    this.offset = config.offset || 0;
    this.maxRows = config.maxRows || 0;
    this.totalRows = config.totalRows; // may be undefined
    this.rowCount = config.rowCount; // may be null
    this.showRows = config.showRows;

    this.selectionModified = false;

    this.showRecordSelectors = config.showRecordSelectors;
    this.showStatusBar = config.showStatusBar;
    this.selectionKey = config.selectionKey;
    this.selectorCols = config.selectorCols;

    LABKEY.DataRegions[this.name] = this;

    this.addEvents(
        /**
         * @event selectchange
         * Fires when the selection has changed.
         * @param {DataRegion} this DataRegion object.
         * @param {Boolean} hasSelection true if the DataRegion has at least one selected item.
         * @example Here's an example of subscribing to the DataRegion 'selectchange' event:
         * Ext.ComponentMgr.onAvailable("dataRegionName", function (dataregion) {
         *     dataregion.on('selectchange', function (dr, selected) {
         *         var btn = Ext.get('my-button-id');
         *         if (selected) {
         *             btn.replaceClass('labkey-disabled-button', 'labkey-button');
         *         }
         *         else {
         *             btn.replaceClass('labkey-button', 'labkey-disabled-button');
         *         }
         *     });
         *  });
         */
         "selectchange"
    );

    this.rendered = true; // prevent Ext.Component.render() from doing anything
    LABKEY.DataRegion.superclass.constructor.call(this, config);

    this._initElements();
    Ext.EventManager.on(window, "load", this._resizeContainer, this, {single: true});
    Ext.EventManager.on(window, "resize", this._resizeContainer, this);
    this._showPagination(this.header);
    this._showPagination(this.footer);

    switch (this.showRows)
    {
        case "all":
            this._showSelectMessage("Showing all " + this.totalRows + " rows.");
            break;
        case "selected":
            this._showSelectMessage("Showing only <em>selected</em> rows.");
            break;
        case "unselected":
            this._showSelectMessage("Showing only <em>unselected</em> rows.");
            break;
    }
};

Ext.extend(LABKEY.DataRegion, Ext.Component, {
    // private
    _initElements : function ()
    {
        this.form = document.forms[this.name];
        this.table = Ext.get("dataregion_" + this.name);
        this.msgbox = Ext.get("dataregion_msgbox_" + this.name);
        this.msgbox.enableDisplayMode();
        this.header = Ext.get("dataregion_header_" + this.name);
        this.footer = Ext.get("dataregion_footer_" + this.name);

        // derived DataRegion's may not include the form id
        if (!this.form && this.table)
        {
            var el = this.table.dom;
            do
            {
                el = el.parentNode;
            } while (el != null && el.tagName != "FORM");
            if (el) this.form = el;
        }

        if (this.form)
        {
            this.form.dataRegion = this;
            if (this.showRecordSelectors)
            {
                if (this.isPageSelected())
                {
                    // set the 'select all on page' checkbox state
                    var toggle = this.form[".toggle"];
                    if (toggle)
                        toggle.checked = true;
                    this.onSelectChange(true);
                }
                else
                {
                    this.onSelectChange(this.hasSelected());
                }
            }
        }

        this._resizeContainer(true);
    },

    // private
    _showPagination : function (el)
    {
        if (!el) return;
        //var pagination = Ext.lib.Dom.getElementsByClassName("labkey-pagination", "div", el)[0];
        var pagination = el.child("div[class='labkey-pagination']", true);
        if (pagination)
            pagination.style.visibility = "visible";
    },

    // private
    _resizeContainer : function ()
    {
        if (!this.table) return;
        var viewportWidth = Number.MAX_VALUE;
        //yahoo version seems to handle vertical scrollbar, while Ext does not, so favor that one
        if (YAHOO && YAHOO.util && YAHOO.util.Dom)
            viewportWidth = YAHOO.util.Dom.getViewportWidth() - YAHOO.util.Dom.getX(this.table.dom.parentNode);
        else if (Ext && Ext.lib && Ext.lib.Dom)
            viewportWidth = Ext.lib.Dom.getViewWidth() - Ext.lib.Dom.getX(this.table.dom.parentNode);
        var newWidth = Math.min(this.table.getWidth(true), viewportWidth);

        // ensure contents of header and footer fit into width
        if (this.header)
            this.header.setWidth(newWidth);
        if (this.footer)
            this.footer.setWidth(newWidth);
    },

    // private
    _removeParams : function (skipPrefixes)
    {
        this._setParam(null, null, skipPrefixes);
    },

    // private
    _setParam : function (param, value, skipPrefixes)
    {
        for (var i in skipPrefixes)
            skipPrefixes[i] = this.name + skipPrefixes[i];

        var paramValPairs = getParamValPairs(skipPrefixes);
        if (value)
        {
            paramValPairs[paramValPairs.length] = [this.name + param, value];
        }
        setSearchString(this.name, buildQueryString(paramValPairs));
    },

    // private
    _setAllCheckboxes : function (value, elementName)
    {
        var elems = this.form.elements;
        var l = elems.length;
        var ids = [];
        for (var i = 0; i < l; i++)
        {
            var e = elems[i];
            if (e.type == 'checkbox' && !e.disabled && (elementName == null || elementName == e.name))
            {
                e.checked = value;
                if (e.name != ".toggle")
                    ids.push(e.value);
            }
        }
        return ids;
    },

    // private
    _setCheck : function (ids, checked, success)
    {
        if (!this.selectionKey || ids.length == 0)
            return;
        this.selectionModified = true;
        var url = LABKEY.ActionURL.buildURL("query", "setCheck.api", LABKEY.ActionURL.getContainer(),
            { 'key' : this.selectionKey, 'checked' : checked });
        for (var i = 0; i < ids.length; i++)
            url += "&id=" + ids[i];

        Ext.Ajax.request({
            url: url,
            scope: this,
            success: success,
            failure: function (response, options) { this.showMessage("Error sending selection."); }
        });
    },

    // private
    _showSelectMessage : function (msg)
    {
        if (!this.showStatusBar)
            return;
        if (this.showRecordSelectors)
        {
            msg += "&nbsp; Select: <span class='labkey-link' onclick='LABKEY.DataRegions[\"" + this.name + "\"].selectNone();' title='Clear selection from all rows'>None</span>";
            var showOpts = new Array();
            if (this.showRows != "all")
                showOpts.push("<span class='labkey-link' onclick='LABKEY.DataRegions[\"" + this.name + "\"].showAll();' title='Show all rows'>All</span>");
            if (this.showRows != "selected")
               showOpts.push("<span class='labkey-link' onclick='LABKEY.DataRegions[\"" + this.name + "\"].showSelected();' title='Show all selected rows'>Selected</span>");
            if (this.showRows != "unselected")
               showOpts.push("<span class='labkey-link' onclick='LABKEY.DataRegions[\"" + this.name + "\"].showUnselected();' title='Show all unselected rows'>Unselected</span>");
            msg += "&nbsp; Show: " + showOpts.join(", ");
        }
        this.showMessage(msg);
    },

    onSelectChange : function (hasSelected)
    {
        var fn;
        if (hasSelected) {
            fn = function (item, index) {
                Ext.get(item).replaceClass("labkey-disabled-button", "labkey-button");
            };
        }
        else {
            fn = function (item, index) {
                Ext.get(item).replaceClass("labkey-button", "labkey-disabled-button");
            };
        }

        var btns = Ext.DomQuery.select("*[labkey-requires-selection=" + this.name + "]");
        Ext.each(btns, fn);

        this.fireEvent('selectchange', this, hasSelected);
    },

    setOffset : function (newoffset)
    {
        this._setParam(".offset", newoffset, [".offset", ".showRows"]);
    },

    setMaxRows : function (newmax)
    {
        this._setParam(".maxRows", newmax, [".offset", ".maxRows", ".showRows"]);
    },

    showPaged : function ()
    {
        this._removeParams([".showRows"]);
    },

    showAll : function ()
    {
        this._setParam(".showRows", "all", [".offset", ".maxRows", ".showRows"]);
    },

    showSelected : function ()
    {
        this._setParam(".showRows", "selected", [".offset", ".maxRows", ".showRows"]);
    },

    showUnselected : function ()
    {
        this._setParam(".showRows", "unselected", [".offset", ".maxRows", ".showRows"]);
    },

    pageFirst : function ()
    {
        this.setOffset(0);
    },

    pageLast : function ()
    {
//        if (!(this.totalRows == undefined) && this.maxRows > 0)
//        {
//            var remaining = this.totalRows - this.offset;
//            var lastPageSize = this.totalRows % this.maxRows;
//            if (lastPageSize == 0)
//                lastPageSize = this.maxRows;
//            var lastPageOffset = this.totalRows - lastPageSize;
//            this.setOffset(lastPageSize);
//        }
    },

    pageNext : function ()
    {

    },

    pagePrev : function ()
    {

    },

    selectRow : function (el)
    {
        this._setCheck([el.value], el.checked);
        var toggle = this.form[".toggle"];
        if (el.checked)
        {
            if (toggle && this.isPageSelected())
                toggle.checked = true;
            this.onSelectChange(true);
        }
        else
        {
            if (toggle)
                toggle.checked = false;
            this.hideMessage();
            this.onSelectChange(this.hasSelected());
        }
    },

    getChecked : function()
    {
        return getCheckedValues(this.form, '.select');
    },

    /** Select all checkboxes on in the current page of the data region. */
    selectPage : function (checked)
    {
        var ids = this._setAllCheckboxes(checked, '.select');
        if (ids.length > 0)
        {
            var toggle = this.form[".toggle"];
            if (toggle)
                toggle.checked = checked;
            this.onSelectChange(checked);
            this._setCheck(ids, checked, function (response, options) {
                var count = 0;
                try {
                    var json = Ext.util.JSON.decode(response.responseText);
                    if (json)
                        count = json.count;
                }
                catch (e) {
                    // ignore
                }
                if (count > 0)
                {
                    var msg;
                    if (count == this.totalRows)
                        msg = "Selected all " + this.totalRows + " rows.";
                    else
                        msg = "Selected " + count + " of " + this.totalRows + " rows.";
                    this._showSelectMessage(msg);
                }
                else
                {
                    this.hideMessage();
                }
            });
        }
        return ids;
    },

    /** Returns true if any row is checked on this page. */
    hasSelected : function ()
    {
        if (!this.form)
            return false;
        var len = this.form.length;
        for (var i = 0; i < len; i++)
        {
            var e = this.form[i];
            if (e.type == 'checkbox' && e.name != ".toggle")
            {
                if (e.checked)
                    return true;
            }
        }
        return false;
    },

    /** Returns true if all rows are checked on this page and at least one row is present on the page. */
    isPageSelected : function ()
    {
        if (!this.form)
            return false;
        var len = this.form.length;
        var hasCheckbox = false;
        for (var i = 0; i < len; i++)
        {
            var e = this.form[i];
            if (e.type == 'checkbox' && e.name != ".toggle")
            {
                hasCheckbox = true;
                if (!e.checked)
                    return false;
            }
        }
        return hasCheckbox;
    },

    selectAll : function ()
    {
    },

    selectNone : function ()
    {
        this.onSelectChange(false);
        var url = LABKEY.ActionURL.buildURL("query", "selectNone.api", LABKEY.ActionURL.getContainer(),
            { 'key' : this.selectionKey });
        Ext.Ajax.request({ url: url });

        if (this.showRows == "selected")
        {
            this._removeParams([".showRows"]);
        }
        else if (this.showRows == "unselected")
        {
            // keep ".showRows=unselected" parameter
            window.location.reload(true);
        }
        else
        {
            this._setAllCheckboxes(false);
            this.hideMessage();
        }
    },

    clearFilter : function (fieldName)
    {
        this._removeParams(["." + fieldName + "~", ".offset"]);
    },

    clearAllFilters : function ()
    {
        this._removeParams([".", ".offset"]);
    },

    showMessage : function (html)
    {
        var span = this.msgbox.dom.getElementsByTagName("span")[0];
        span.innerHTML = html;
        this.msgbox.setVisible(true, true);
    },

    hideMessage : function ()
    {
        this.msgbox.setVisible(false, false);
        var span = this.msgbox.dom.getElementsByTagName("span")[0];
        span.innerHTML = "";
    }
});
