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
    this.totalRows = config.totalRows;

    this.selectionKey = config.selectionKey;
    this.selectorCols = config.selectorCols;

    LABKEY.DataRegions[this.name] = this;

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

    // private methods

    this._showPagination = function (el)
    {
        if (!el) return;
        //var pagination = Ext.lib.Dom.getElementsByClassName("labkey-pagination", "div", el)[0];
        var pagination = el.child("div[class='labkey-pagination']", true);
        if (pagination)
            pagination.style.visibility = "visible";
    }

    this._resizeContainer = function (onload)
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

        // on load, make the pagination visible
        if (onload)
        {
            this._showPagination(this.header);
            this._showPagination(this.footer);
        }
    };
    this._resizeContainer(true);
    Ext.EventManager.on(window, "load", this._resizeContainer, this, {single: true});
    Ext.EventManager.on(window, "resize", this._resizeContainer, this);

    this._setParam = function (param, value, skipPrefixes)
    {
        for (var i in skipPrefixes)
            skipPrefixes[i] = this.name + skipPrefixes[i];

        var paramValPairs = getParamValPairs(skipPrefixes);
        if (value)
        {
            paramValPairs[paramValPairs.length] = [this.name + param, value];
        }
        setSearchString(this.name, buildQueryString(paramValPairs));
    };

    this._setCheck = function (ids, checked, success)
    {
        if (!this.selectionKey || ids.length == 0)
            return;
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
    }

}

LABKEY.DataRegion.prototype = {
    setOffset : function (newoffset)
    {
        this._setParam(".offset", newoffset, [".offset", ".showAllRows"]);
    },

    setMaxRows : function (newmax)
    {
        this._setParam(".maxRows", newmax, [".offset", ".maxRows", ".showAllRows", ".showSelected"]);
    },

    showAll : function ()
    {
        this._setParam(".showAllRows", "true", [".offset", ".maxRows", ".showAllRows", ".showSelected"]);
    },

    showSelected : function ()
    {
        this._setParam(".showSelected", "true", [".offset", ".maxRows", ".showAllRows"]);
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
                if (checked && count > 0)
                {
                    if (count == this.totalRows)
                        this.showMessage("All rows selected.");
                    else
                        this.showMessage(count + " of " + this.totalRows + " rows selected.");
                }
                else
                {
                    this.hideMessage();
                }
            });
        }
    },

    selectAll : function ()
    {
    },

    selectNone : function ()
    {
        var url = LABKEY.ActionURL.buildURL("query", "selectNone.api", LABKEY.ActionURL.getContainer(),
            { 'key' : this.selectionKey });
        Ext.Ajax.request({ url: url });

        setAllCheckboxes(this.form, false);
        this.hideMessage();
    },

    showMessage : function (html)
    {
        var tr = this.msgbox.dom;
        var td = tr.getElementsByTagName("td")[0];
        td.innerHTML = html;
        this.msgbox.setVisible(true, true);
    },

    hideMessage : function ()
    {
        this.msgbox.setVisible(false, false);
        var tr = this.msgbox.dom;
        var td = tr.getElementsByTagName("td")[0];
        td.innerHTML = "";
    }
};

//function sendCheckboxes(el, value)
//{
//    var form = el;
//    do
//    {
//        form = form.parentNode;
//    } while (form.tagName != "FORM");
//
//    var ids = setAllCheckboxes(form, value, '.select');
//    if (ids.length > 0)
//    {
//        sendCheckbox(el, key, ids, value);
//        // XXX: get size of entire query. don't show the message if all are showing.
//        showDataRegionMessage(dataregion, "Selected " + ids.length + " rows on this page.  Select all 1000 rows?");
//    }
//    return false;
//}
//
//function sendCheckbox(el, key, ids, checked)
//{
//    if (!key || ids.length == 0)
//        return;
//    var url = LABKEY.ActionURL.buildURL("query", "setCheck.api", LABKEY.ActionURL.getContainer(), { 'key' : key, 'checked' : checked });
//    for (var i = 0; i < ids.length; i++)
//        url += "&id=" + ids[i];
//
//    var xmlhttp = new XMLRequest(url);
//    xmlhttp.get();
//}
//
//function selectNone(el, key)
//{
//    var url = LABKEY.ActionURL.buildURL("query", "selectNone.api", LABKEY.ActionURL.getContainer(), { 'key' : key });
//    var xmlhttp = new XMLRequest(url);
//    xmlhttp.get();
//
//    var form = el;
//    do
//    {
//        form = form.parentNode;
//    } while (form.tagName != "FORM");
//    setAllCheckboxes(form, false);
//}

