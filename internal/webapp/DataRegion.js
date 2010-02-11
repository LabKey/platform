/*
 * Copyright (c) 2008-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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

    // The button for the ribbon panel that we're currently showing
    this.currentPanelButton = null;

    // All of the different ribbon panels that have been constructed for this data region
    this.panelButtonContents = [];

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
        if ('YAHOO' in window && YAHOO && YAHOO.util && YAHOO.util.Dom)
            viewportWidth = YAHOO.util.Dom.getViewportWidth();
        else if ('Ext' in window && Ext && Ext.lib && Ext.lib.Dom)
            viewportWidth = Ext.lib.Dom.getViewWidth() - 20;

        var tableRight = this.table.getWidth(true) + this.table.getLeft();
        if (tableRight < viewportWidth)
            viewportWidth = tableRight;

        var pagination;
        if (this.header)
        {
            pagination = this.header.child("div[class='labkey-pagination']");
            if (pagination)
                pagination.parent().setWidth(Math.max(0, viewportWidth - pagination.getLeft()));
        }

        if (this.footer)
        {
            pagination = this.footer.child("div[class='labkey-pagination']");
            if (pagination)
                pagination.parent().setWidth(Math.max(0, viewportWidth - pagination.getLeft()));
        }
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
        var params = { id: ids };
//        for (var i = 0; i < ids.length; i++)
//            url += "&id=" + ids[i];

        Ext.Ajax.request({
            url: url,
            method: "POST",
            params: params,
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
        this.msgbox.setVisible(true);
    },

    isMessageShowing : function()
    {
        return this.msgbox.isVisible();
    },

    hideMessage : function ()
    {
        this.msgbox.setVisible(false, false);
        var span = this.msgbox.dom.getElementsByTagName("span")[0];
        span.innerHTML = "";
    },

    /**
     * Show a ribbon panel. tabPanelConfig is an Ext config object for a TabPanel, the only required
     * value is the items array.
     * */
    showButtonPanel : function(panelButton, tabPanelConfig)
    {
        var parentDiv = panelButton.parentNode;
        // Find the button bar div
        while (parentDiv && parentDiv.className != 'labkey-button-bar')
        {
            parentDiv = parentDiv.parentNode;
        }
        var panelDiv;
        if (parentDiv)
        {
            // Find the next element, which should be a place to hang the TabPanel
            panelDiv = parentDiv.nextSibling;
            while (panelDiv && panelDiv.className.indexOf('extContainer') == -1)
            {
                panelDiv = panelDiv.nextSibling;
            }

            if (panelDiv)
            {
                var panelToHide = null;
                // If we find a spot to put the panel, check its current contents
                if (this.currentPanelButton)
                {
                    // We're currently showing a ribbon panel, so remember that we need to hide it
                    panelToHide = this.panelButtonContents[this.currentPanelButton.id];
                }

                // Create a callback function to render the requested ribbon panel
                var callback = function()
                {
                    if (panelToHide)
                    {
                        panelToHide.setVisible(false);
                    }
                    if (this.currentPanelButton != panelButton)
                    {
                        if (!this.panelButtonContents[panelButton.id])
                        {
                            // New up the TabPanel if we haven't already
                            // Only create one per button, even if that button is rendered both above and below the grid
                            tabPanelConfig.tabPosition = 'left';
                            tabPanelConfig.tabWidth = 100;
                            tabPanelConfig.renderTo = panelDiv;
                            var newItems = new Array(tabPanelConfig.items.length);
                            for (var i = 0; i < tabPanelConfig.items.length; i++)
                            {
                                newItems[i] = new Object();
                                newItems[i].contentEl = Ext.get(tabPanelConfig.items[i].contentEl);
                                newItems[i].title = tabPanelConfig.items[i].title;
                            }
                            tabPanelConfig.items = newItems;
                            this.panelButtonContents[panelButton.id] = new Ext.ux.VerticalTabPanel(tabPanelConfig);
                        }
                        else
                        {
                            // Otherwise, be sure that it's parented correctly - it might have been shown
                            // in a different button bar position
                            this.panelButtonContents[panelButton.id].getEl().appendTo(Ext.get(panelDiv));
                        }

                        this.currentPanelButton = panelButton;

                        // Slide it into place
                        this.panelButtonContents[panelButton.id].setVisible(true);
                        this.panelButtonContents[panelButton.id].getEl().slideIn();

                        this.panelButtonContents[panelButton.id].syncSize();
                    }
                    else
                    {
                        this.currentPanelButton = null;
                    }
                };

                if (this.currentPanelButton)
                {
                    // We're already showing a ribbon panel, so hide it before showing the new one
                    panelToHide.getEl().slideOut('t', { callback: callback, scope: this });
                }
                else
                {
                    // We're not showing another ribbon panel, so show the new one right away
                    callback.call(this);
                }
            }
        }
    }
    
});


// FILTER UI

/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var _tableName = "";
var _fieldName = "";
var _fieldCaption = "";
var _fieldType = "text";
var _filterDiv = null;
var _filterWin = null;
var _filterQueryString = "";

function setFilterQueryString(s)
{
    _filterQueryString = s;
}


function getFilterDiv()
{
    if (!_filterDiv)
    {
        LABKEY.addMarkup('<div id="filterDiv" style="display:none;">' +
        '  <table onkeypress="handleKey(event);">' +
        '    <tr>' +
        '      <td colspan=2 style="padding: 5px" nowrap>' +
        '        <select id="compare_1" name="compare_1" onchange="doChange(this)">' +
        '            <option value="">&lt;has any value></option>' +
        '        </select><br>' +
        '        <input disabled id="value_1" style="visibility:hidden" type=text name=value_1><br>' +
        '        <span id="compareSpan_2" style="visibility:hidden">and<br>' +
        '        <select id="compare_2" name="compare_2" onchange="doChange(this)">' +
        '            <option value="">&lt;no other filter></option>' +
        '        </select><br>' +
        '        <input disabled style="visibility:hidden" id="value_2" type="text" name="value_2"><br><br>' +
        '        </span>' +
        '        <a class="labkey-button" href="#" onclick="doFilter();return false;"><span>OK</span> ' +
        '        <a class="labkey-button" href="#" onclick="hideFilterDiv();return false;"><span>Cancel</span> ' +
        '        <a class="labkey-button" href="#" onclick="clearFilter();return false;"><span>Clear Filter</span> ' +
        '        <a class="labkey-button" href="#" onclick="clearAllFilters();return false;"><span>Clear All Filters</span> ' +
        '      </td>' +
        '    </tr>' +
        '  </table>' +
        '</div>');
        _filterDiv = document.getElementById("filterDiv");
    }
    return _filterDiv;
}

function doChange(obj)
{
    var name = obj.name;
    var index = name.split("_")[1];
    var valueInput = document.getElementById("value_" + index);
    var compare = obj.options[obj.selectedIndex].value;
    if (compare == "" || compare == "isblank" || compare == "isnonblank" || compare == "nomvvalue" || compare == "hasmvvalue")
    {
        if (index == "1")
            document.getElementById("compareSpan_2").style.visibility = "hidden";
        valueInput.style.visibility = "hidden";
        valueInput.disabled = true;
    }
    else
    {
        if (index == "1")
            document.getElementById("compareSpan_2").style.visibility = "visible";
        valueInput.style.visibility = "visible";
        valueInput.disabled = false;
        valueInput.focus();
        valueInput.select();
    }
}


function showFilterPanel(elem, tableName, colName, caption, dataType, mvEnabled)
{
    _fieldName = colName;
    _fieldCaption = caption;
    _tableName = tableName;
    fillOptions(dataType, mvEnabled);

    var paramValPairs = getParamValPairs(null);
    //Fill in existing filters...
    var filterIndex = 1;
    for (var i = 0; i < paramValPairs.length; i++)
    {
        var textbox = document.getElementById("value_" + filterIndex);
        textbox.value = "";
        var pair = paramValPairs[i];
        if (pair[0].indexOf(_tableName + "." + _fieldName + "~") == 0)
        {
            var comparison = (pair[0].split("~"))[1];
            var select = document.getElementById("compare_" + filterIndex);
            for (var opt = 0; opt < select.options.length; opt++)
            {
                if (select.options[opt].value == comparison)
                {
                    select.selectedIndex = opt;
                    break;
                }
            }

            if (pair.length > 1)
            {
                textbox = document.getElementById("value_" + filterIndex);
                textbox.value = pair[1];
            }

            filterIndex++;
            if (filterIndex > 2)
                break;
        }
    }
    var div = getFilterDiv();
    div.style.display = "block";
    div.style.visibility = "visible";

    if (!_filterWin)
    {
        _filterWin = new Ext.Window({
            contentEl: div,
            width: 350,
            autoHeight: true,
            modal: true,
            resizable: false,
            closeAction: 'hide'
        });

        // 5975: Override focus behavior. Keeps Ext.Window from stealing focus after showing.
        _filterWin.focus = function () {
            doChange(document.getElementById("compare_1"));
            doChange(document.getElementById("compare_2"));
        };
    }
    else
    {
        _filterWin.center();
    }

    if (filterIndex == 2)
        document.getElementById("compare_2").selectedIndex = 0;

    _filterWin.setTitle("Show Rows Where " + caption);
    _filterWin.show();
}

function hideFilterDiv()
{
    if (_filterWin)
        _filterWin.hide();
}

var _typeMap = {
    "BIGINT":"INT",
    "BIGSERIAL":"INT",
    "BIT":"BOOL",
    "BOOL":"BOOL",
    "BOOLEAN":"BOOL",
    "CHAR":"TEXT",
    "CLOB":"LONGTEXT",
    "DATE":"DATE",
    "DECIMAL":"DECIMAL",
    "DOUBLE":"DECIMAL",
    "DOUBLE PRECISION":"DECIMAL",
    "FLOAT":"DECIMAL",
    "INTEGER":"INT",
    "LONGVARCHAR":"LONGTEXT",
    "NTEXT":"LONGTEXT",
    "NUMERIC":"DECIMAL",
    "REAL":"DECIMAL",
    "SMALLINT":"INT",
    "TIME":"TEXT",
    "TIMESTAMP":"DATE",
    "TINYINT":"INT",
    "VARCHAR":"TEXT",
    "INT":"INT",
    "INT IDENTITY":"INT",
    "DATETIME":"DATE",
    "TEXT":"TEXT",
    "NVARCHAR":"TEXT",
    "INT2":"INT",
    "INT4":"INT",
    "INT8":"INT",
    "FLOAT4":"DECIMAL",
    "FLOAT8":"DECIMAL",
    "SERIAL":"INT"
};
var _mappedType = "TEXT";

function fillOptions(dataType, mvEnabled)
{
    getFilterDiv();
    var mappedType = _typeMap[dataType.toUpperCase()];
    if (mappedType == undefined)
        mappedType = dataType.toUpperCase();

    for (var i = 1; i <= 2; i++)
    {
        var select = document.getElementById("compare_" + i);
        var opt;
        select.options.length = 1;

        if (mappedType != "LONGTEXT")
        {
            opt = document.createElement("OPTION");
            if (mappedType == "DATE")
                opt.value = "dateeq";
            else
                opt.value = "eq";
            opt.text = "Equals";
            appendOption(select, opt);
            if (mappedType != "BOOL")
            {
                opt = document.createElement("OPTION");
                opt.value = "in";
                opt.text = "Equals One Of (e.g. 'a;b;c')";
                appendOption(select, opt);
            }
            opt = document.createElement("OPTION");
            if (mappedType == "DATE")
                opt.value = "dateneq";
            else
                opt.value = "neqornull";
            opt.text = "Does not Equal";
            appendOption(select, opt);
        }

        opt = document.createElement("OPTION");
        opt.value = "isblank";
        opt.text = "Is Blank";
        appendOption(select, opt);

        opt = document.createElement("OPTION");
        opt.value = "isnonblank";
        opt.text = "Is Not Blank";
        appendOption(select, opt);

        if (mappedType != "LONGTEXT" && mappedType != "BOOL")
        {
            opt = document.createElement("OPTION");
            opt.value = "gt";
            opt.text = "Is Greater Than";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "lt";
            opt.text = "Is Less Than";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "gte";
            opt.text = "Is Greater Than or Equal To";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "lte";
            opt.text = "Is Less Than or Equal To";
            appendOption(select, opt);
        }

        if (mappedType == "TEXT" || mappedType == "LONGTEXT")
        {
            opt = document.createElement("OPTION");
            opt.value = "startswith";
            opt.text = "Starts With";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "doesnotstartwith";
            opt.text = "Does Not Start With";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "contains";
            opt.text = "Contains";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "doesnotcontain";
            opt.text = "Does Not Contain";
            appendOption(select, opt);
        }

        if (mvEnabled)
        {
            opt = document.createElement("OPTION");
            opt.value = "hasmvvalue";
            opt.text = "Has a missing value indicator";
            appendOption(select, opt);

            opt = document.createElement("OPTION");
            opt.value = "nomvvalue";
            opt.text = "Does not have a missing value indicator";
            appendOption(select, opt);
        }

        if (i == 1)
            selectDefault(select, mappedType);
    }

    _mappedType = mappedType;
}

function appendOption(select, opt)
{
    select.options[select.options.length] = opt;
}

function selectDefault(select, mappedType)
{
    if (mappedType == "LONGTEXT")
        selectByValue(select, "contains");
    else if (mappedType == "DECIMAL")
        selectByValue(select, "gte");
    else if (mappedType == "TEXT")
        selectByValue(select, "startswith");
    else if (select.options.length > 1)
        select.selectedIndex = 1;
}

function selectByValue(select, value)
{
    for (var i = 0; i < select.options.length; i++)
        if (select.options[i].value == value)
        {
            select.selectedIndex = i;
            return;
        }
}


var savedSearchString = null;
var filterListeners = [];

function registerFilterListener(fn)
{
    filterListeners.push(fn);
}

function getSearchString()
{
    if (null == savedSearchString)
        savedSearchString = document.location.search.substring(1) || "";
    return savedSearchString;
}

function setSearchString(tableName, search)
{
    hideFilterDiv();
    savedSearchString = search || "";
    for (var i=0; i < filterListeners.length; i++)
    {
        if (filterListeners[i](tableName, search))
        {
            hideFilterDiv();
            return;
        }
    }
    window.location.search = "?" + savedSearchString;
}


function getParamValPairs(skipPrefixes)
{
    var queryString = getSearchString();
    var iNew = 0;
    //alert("getparamValPairs: " + queryString);
    var newParamValPairs = new Array(0);
    if (queryString != null && queryString.length > 0)
    {
        var paramValPairs = queryString.split("&");
        PARAM_LOOP: for (var i = 0; i < paramValPairs.length; i++)
        {
            var paramPair = paramValPairs[i].split("=");
            paramPair[0] = unescape(paramPair[0]);

            if (paramPair[0] == ".lastFilter")
                continue;

            if (skipPrefixes)
            {
                for (var j = 0; j < skipPrefixes.length; j++)
                {
                    var skipPrefix = skipPrefixes[j];
                    if (skipPrefix && paramPair[0].indexOf(skipPrefix) == 0)
                    {
                        // only skip filter params and sort.
                        if (paramPair[0] == skipPrefix)
                            continue PARAM_LOOP;
                        if (paramPair[0].indexOf("~") > 0)
                            continue PARAM_LOOP;
                        if (paramPair[0] == skipPrefix + "sort")
                            continue PARAM_LOOP;
                    }
                }
            }
            if (paramPair.length > 1)
            {
                // unescape doesn't handle '+' correctly, so swap them with ' ' first
                paramPair[1] = unescape(paramPair[1].replace(/\+/g, " "));
            }
            newParamValPairs[iNew] = paramPair;
            iNew++;
        }
    }
    return newParamValPairs;
}

function getParameter(paramName)
{
    var paramValPairs = getParamValPairs(null);
    for (var i = 0; i < paramValPairs.length; i++)
        if (paramValPairs[i][0] == paramName)
            if (paramValPairs[i].length > 1)
                return paramValPairs[i][1];
            else
                return "";

    return null;
}

function buildQueryString(pairs)
{
    if (pairs == null || pairs.length == 0)
        return "";

    //alert("enter buildQueryString");
    var paramArray = new Array(pairs.length);
    for (var i = 0; i < pairs.length; i++)
    {
        // alert("pair" + pairs[i]);
        if (pairs[i].length > 1)
            paramArray[i] = pairs[i][0] + "=" + escape(pairs[i][1]);
        else
            paramArray[i] = pairs[i][0];
    }

    // Escape doesn't encode '+' properly
    var queryString = paramArray.join("&").replace(/\+/g, "%2B");
    // alert("exit buildQueryString: " + queryString);
    return queryString;
}

function clearFilter()
{
    var newParamValPairs = getParamValPairs([_tableName + "." + _fieldName + "~", _tableName + ".offset"]);
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function clearAllFilters()
{
    var newParamValPairs = getParamValPairs([_tableName + ".", _tableName + ".offset"]);
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function doFilter()
{
    var newParamValPairs = getParamValPairs([_tableName + "." + _fieldName + "~", _tableName + ".offset"]);
    var iNew = newParamValPairs.length;

    var comparisons = getValidCompares();
    if (null == comparisons)
        return;

    for (var i = 0; i < comparisons.length; i++)
    {
        newParamValPairs[iNew] = comparisons[i];
        iNew ++;
    }

    //alert("new: " +buildQueryString(newParamValPairs));
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function getValidComparesFromForm(formIndex, newParamValPairs)
{
    var obj = document.getElementById("compare_" + formIndex);
    var comparison = obj.options[obj.selectedIndex].value;
    var compareTo = document.getElementById("value_" + formIndex).value;
    //alert("comparison: " + comparison + ", compareTo: " + compareTo);
    if (comparison != "")
    {
        var pair;
        if (comparison == "isblank" || comparison == "isnonblank" || comparison == "nomvvalue" || comparison == "hasmvvalue")
        {
            pair = [_tableName + "." + _fieldName + "~" + comparison];
        }
        else
        {
            var validCompareTo;
            if (comparison == 'in')
            {
                validCompareTo = validateMultiple(compareTo);
            }
            else
            {
                validCompareTo = validate(compareTo);
            }

            if (validCompareTo == undefined)
                return false;
            pair = [_tableName + "." + _fieldName + "~" + comparison, validCompareTo];
        }
        newParamValPairs[newParamValPairs.length] = pair;
    }
    return true;
}

function getValidCompares()
{
    var newParamValPairs = new Array(0);

    var success = getValidComparesFromForm(1, newParamValPairs);
    if (!success)
    {
        return null;
    }
    success = getValidComparesFromForm(2, newParamValPairs);
    if (!success)
    {
        return null;
    }
    return newParamValPairs;
}

function validateMultiple(allValues, mappedType, fieldName)
{
    if (!mappedType) mappedType = _mappedType;
    if (!fieldName) fieldName = _fieldCaption || _fieldName;

    if (!allValues)
    {
        alert("filter value for field '" + fieldName + "' cannot be empty.");
        return undefined;
    }
    var values = allValues.split(";");
    var result = '';
    var separator = '';
    for (var i = 0; i < values.length; i++)
    {
        var value = validate(values[i].trim(), mappedType, fieldName);
        if (value == undefined)
            return undefined;

        result = result + separator + value;
        separator = ";";
    }
    return result;
}

function validate(value, mappedType, fieldName)
{
    if (!mappedType) mappedType = _mappedType;
    if (!fieldName) fieldName = _fieldCaption || _fieldName;

    if (!value)
    {
        alert("filter value for field '" + fieldName + "' cannot be empty.");
        return undefined
    }

    if (mappedType == "INT")
    {
        var intVal = parseInt(value);
        if (isNaN(intVal))
        {
            alert(value + " is not a valid integer for field '" + fieldName + "'.");
            return undefined;
        }
        else
            return "" + intVal;
    }
    else if (mappedType == "DECIMAL")
    {
        var decVal = parseFloat(value);
        if (isNaN(decVal))
        {
            alert(value + " is not a valid decimal number for field '" + fieldName + "'.");
            return undefined;
        }
        else
            return "" + decVal;
    }
    else if (mappedType == "DATE")
    {
        var year, month, day, hour, minute;
        hour = 0;
        minute = 0;

        //Javascript does not parse ISO dates, but if date matches we're done
        if (value.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*$/) ||
            value.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*(\d\d):(\d\d)\s*$/))
        {
            return value;
        }
        else
        {
            var dateVal = new Date(value);
            if (isNaN(dateVal))
            {
                alert(value + " is not a valid date for field '" + fieldName + "'.");
                return undefined;
            }
            //Try to do something decent with 2 digit years!
            //if we have mm/dd/yy (but not mm/dd/yyyy) in the date
            //fix the broken date parsing
            if (value.match(/\d+\/\d+\/\d{2}(\D|$)/))
            {
                if (dateVal.getFullYear() < new Date().getFullYear() - 80)
                    dateVal.setFullYear(dateVal.getFullYear() + 100);
            }
            year = dateVal.getFullYear();
            month = dateVal.getMonth() + 1;
            day = dateVal.getDate();
            hour = dateVal.getHours();
            minute = dateVal.getMinutes();
        }
        var str = "" + year + "-" + twoDigit(month) + "-" + twoDigit(day);
        if (hour != 0 || minute != 0)
            str += " " + twoDigit(hour) + ":" + twoDigit(minute);

        return str;
    }
    else if (mappedType == "BOOL")
    {
        var upperVal = value.toUpperCase();
        if (upperVal == "TRUE" || value == "1" || upperVal == "Y" || upperVal == "YES" || upperVal == "ON" || upperVal == "T")
            return "1";
        if (upperVal == "FALSE" || value == "0" || upperVal == "N" || upperVal == "NO" || upperVal == "OFF" || upperVal == "F")
            return "0";
        else
        {
            alert(value + " is not a valid boolean for field '" + fieldName + "'. Try true,false; yes,no; on,off; or 1,0.");
            return undefined
        }
    }
    else
        return value;
}

function twoDigit(num)
{
    if (num < 10)
        return "0" + num;
    else
        return "" + num;
}

function doSort(tableName, columnName, sortDirection)
{
    var newSortArray = new Array(1);
    //sort forward
    var sortString = getParameter(tableName + ".sort");
    var currentSort;

    if (sortString != null)
    {
        var sortArray = sortString.split(",");
        for (var j = 0; j < sortArray.length; j++)
        {
            if (sortArray[j] == columnName || sortArray[j] == "+" + columnName)
                currentSort = "+";
            else if (sortArray[j] == "-" + columnName)
                currentSort = "-";
            else if (newSortArray.length <= 2)
                newSortArray[newSortArray.length] = sortArray[j];
        }
    }

    if (sortDirection == "+") //Easier to read without the encoded + on the URL...
        sortDirection = "";
    newSortArray[0] = sortDirection + columnName;

    var paramValPairs = getParamValPairs([tableName + ".sort", tableName + ".offset"]);
    paramValPairs[paramValPairs.length] = [tableName + ".sort", newSortArray.join(",")];

    setSearchString(tableName, buildQueryString(paramValPairs));
}

function clearSort(tableName, columnName)
{
    if(!tableName || !columnName)
        return;

    var sortString =  getParameter(tableName + ".sort");
    if(!sortString)
        return;

    var sortArray = sortString.split(",");
    var newSortArray = [];

    for(var idx = 0; idx < sortArray.length; ++idx)
    {
        if(sortArray[idx] != columnName && sortArray[idx] != "-" + columnName)
            newSortArray.push(sortArray[idx]);
    }

    var paramValPairs = getParamValPairs([tableName + ".sort", tableName + ".offset"]);
    if(newSortArray.length > 0)
        paramValPairs.push([tableName + ".sort", newSortArray.join(",")]);

    setSearchString(tableName, buildQueryString(paramValPairs));
}

// If at least one checkbox on the form is selected then GET/POST url.  Otherwise, display an error.
function verifySelected(form, url, method, pluralNoun, confirmText)
{
    var checked = false;
    var elems = form.elements;
    var l = elems.length;
    for (var i = 0; i < l; i++)
    {
        var e = elems[i];
        if (e.type == 'checkbox' && e.checked && e.name == '.select')
        {
            checked = true;
            break;
        }
    }
    if (checked)
    {
        if ((window.parent == window) && (null != confirmText))
        {
            if (!window.confirm(confirmText))
                return false;
        }
        form.action = url;
        form.method = method;
        return true;
    }
    else
    {
        window.alert('Please select one or more ' + pluralNoun + '.');
        return false;
    }
}

function handleKey(event)
{
    switch (event.keyCode)
    {
        case 13: // enter
            doFilter();
            break;

        case 27: // esc
            hideFilterDiv();
            break;
    }
}
