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

LABKEY.DataRegion.prototype = {
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

        // set the 'select all on page' checkbox state
        if (this.form && this.showRecordSelectors && this.isPageSelected())
        {
            var toggle = this.form[".toggle"];
            if (toggle)
                toggle.checked = true;
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
        var newWidth = Math.min(this.table.getWidth(true),
                YAHOO.util.Dom.getViewportWidth() - YAHOO.util.Dom.getX(this.table.dom.parentNode));
                // The Ext version doesn't account for the scrollbar width or something
                //Ext.lib.Dom.getViewWidth() - Ext.lib.Dom.getX(this.table.dom.parentNode));

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
        if (!el.checked)
        {
            this.hideMessage();
        }
    },

    getChecked : function()
    {
        return getCheckedValues(this.form, '.select');
    },

    selectPage : function (checked)
    {
        var ids = setAllCheckboxes(this.form, checked, '.select');
        if (ids.length > 0)
        {
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
    },

    /** Returns true if all rows are checked on this page and at least one row is present on the page. */
    isPageSelected : function ()
    {
        if (!this.form)
            return false;
        var elems = this.form.elements;
        var len = elems.length;
        var hasCheckbox = false;
        for (var i = 0; i < len; i++)
        {
            var e = elems[i];
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
            setAllCheckboxes(this.form, false);
            this.hideMessage();
        }
    },

    clearFilter : function (fieldName)
    {
        this._removeParams(["." + fieldName + "~", ".offset"]);
    },

    clearAllFilters : function ()
    {
        this._removeParams(getParamValPairs([".", ".offset"]));
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
};
