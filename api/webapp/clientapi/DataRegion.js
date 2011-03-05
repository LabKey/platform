/**
* @fileOverview
* @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
* @license Copyright (c) 2008-2011 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
*/

/**
 * @namespace The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
 */
if (!LABKEY.DataRegions)
{
    LABKEY.DataRegions = {};
}

/**
 * The DataRegion constructor is private - to get a LABKEY.DataRegion object, use <code>Ext.ComponentMgr.get(<em>&lt;dataregionname&gt;</em>)</code> or <code>Ext.ComponentMgr.onAvailable(<em>&lt;dataregionname&gt;</em>, callback)</code>.
 * @class The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
 */
LABKEY.DataRegion = function (config)
{
    this.config = config || {};

    this.id = config.name; // XXX: may not be unique on the on page with webparts
    /** Name of the DataRegion. Should be unique within a given page */
    this.name = config.name;
    /** Schema name of the query to which this DataRegion is bound */
    this.schemaName = config.schemaName;
    /** Name of the query to which this DataRegion is bound */
    this.queryName = config.queryName;
    /** Name of the custom view to which this DataRegion is bound, may be blank */
    this.viewName = config.viewName || "";
    this.view = config.view;
    this.sortFilter = config.sortFilter;

    this.complete = config.complete;
    /** Starting offset of the rows to be displayed. 0 if at the beginning of the results */
    this.offset = config.offset || 0;
    /** Maximum number of rows to be displayed. 0 if the count is not limited */
    this.maxRows = config.maxRows || 0;
    this.totalRows = config.totalRows; // may be undefined
    this.rowCount = config.rowCount; // may be null
    this.showRows = config.showRows;

    this.selectionModified = false;

    this.showRecordSelectors = config.showRecordSelectors;
    this.showInitialSelectMessage = config.showSelectMessage;
    this.selectionKey = config.selectionKey;
    this.selectorCols = config.selectorCols;
    this.requestURL = config.requestURL;

    // The button for the ribbon panel that we're currently showing
    this.currentPanelButton = null;

    // All of the different ribbon panels that have been constructed for this data region
    this.panelButtonContents = [];

    LABKEY.DataRegions[this.name] = this;

    this.addEvents(
        /**
         * @memberOf LABKEY.DataRegion#
         * @name selectchange
         * @event
         * @description Fires when the selection has changed.
         * @param {LABKEY.DataRegion} this DataRegion object.
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
         "selectchange",
         "beforeoffsetchange",
         "beforemaxrowschange",
         "beforesortchange",
         "beforeclearsort",
         "beforeclearfilter",
         "beforeclearallfilters",
         "beforechangeview",
         "beforeshowrowschange",
         "buttonclick"
    );

    this.rendered = true; // prevent Ext.Component.render() from doing anything
    LABKEY.DataRegion.superclass.constructor.call(this, config);

    /**
     * Changes the current row offset for paged content
     * @param newoffset row index that should be at the top of the grid
     */
    this.setOffset = function (newoffset)
    {
        if (false === this.fireEvent("beforeoffsetchange", this, newoffset))
            return;

        this._setParam(".offset", newoffset, [".offset", ".showRows"]);
    };

    /**
     * Changes the maximum number of rows that the grid will display at one time
     * @param newmax the maximum number of rows to be shown
     */
    this.setMaxRows = function (newmax)
    {
        if (false === this.fireEvent("beforemaxrowschange", this, newmax))
            return;

        this._setParam(".maxRows", newmax, [".offset", ".maxRows", ".showRows"]);
    };

    /**
     * Forces the grid to do paging based on the current maximum number of rows
     */
    this.showPaged = function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, null))
            return;

        this._removeParams([".showRows"]);
    };

    /**
     * Forces the grid to show all rows, without any paging
     */
    this.showAll = function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "all"))
            return;

        this._setParam(".showRows", "all", [".offset", ".maxRows", ".showRows"]);
    };

    /**
     * Forces the grid to show only rows that have been selected
     */
    this.showSelected = function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "selected"))
            return;

        this._setParam(".showRows", "selected", [".offset", ".maxRows", ".showRows"]);
    };

    /**
     * Forces the grid to show only rows that have not been selected
     */
    this.showUnselected = function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "unselected"))
            return;

        this._setParam(".showRows", "unselected", [".offset", ".maxRows", ".showRows"]);
    };

    /** Displays the first page of the grid */
    this.pageFirst = function ()
    {
        this.setOffset(0);
    };

    this.selectRow = function (el)
    {
        this._setSelected([el.value], el.checked);
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
    };

    /**
     * Get selected items on the current page.
     * @see LABKEY.DataRegion.getSelected
     */
    this.getChecked = function ()
    {
        return getCheckedValues(this.form, '.select');
    };

    /**
     * Get all selected items.
     *
     * @param config A configuration object with the following properties:
     * @param {Function} config.success The function to be called upon success of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> an object with the property 'selected' that is an array of the primary keys for the selected rows.
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.failure] The function to call upon error of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
     * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
     */
    this.getSelected = function (config)
    {
        if (!this.selectionKey)
            return;

        config = config || { };
        config.selectionKey = this.selectionKey;
        LABKEY.DataRegion.getSelected(config);
    };

    /**
     * Set the selection state for all checkboxes on the current page of the data region.
     * @param checked whether all of the rows on the current page should be selected or unselected
     */
    this.selectPage = function (checked)
    {
        var ids = this._setAllCheckboxes(checked, '.select');
        if (ids.length > 0)
        {
            var toggle = this.form[".toggle"];
            if (toggle)
                toggle.checked = checked;
            this.onSelectChange(checked);
            this._setSelected(ids, checked, function (response, options) {
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
    };

    /** Returns true if any row is checked on this page. */
    this.hasSelected = function ()
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
    };

    /** Returns true if all rows are checked on this page and at least one row is present on the page. */
    this.isPageSelected = function ()
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
    };

    this.selectNone = function (config)
    {
        return this.clearSelected(config);
    };

    /**
     * Clear all selected items.
     *
     * @param config A configuration object with the following properties:
     * @param {Function} config.success The function to be called upon success of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> an object with the property 'count' of 0 to indicate an empty selection.
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.failure] The function to call upon error of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
     * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
     */
    this.clearSelected = function (config)
    {
        if (!this.selectionKey)
            return;

        this.onSelectChange(false);

        config = config || { };
        config.selectionKey = this.selectionKey;
        LABKEY.DataRegion.clearSelected(config);

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
    };

    /**
     * Replaces the sort on the given column, if present, or sets a brand new sort
     * @param columnName name of the column to be sorted
     * @param sortDirection either "+' for ascending or '-' for descending
     */
    this.changeSort = function (columnName, sortDirection)
    {
        if (false === this.fireEvent("beforesortchange", this, columnName, sortDirection))
            return;

        var newSortString = this.alterSortString(LABKEY.DataRegion._filterUI.getParameter(this.name + ".sort"), columnName, sortDirection);
        this._setParam(".sort", newSortString, [".sort", ".offset"]);
    };

    /**
     * Removes the sort on a specified column
     * @param columnName name of the column
     */
    this.clearSort = function (columnName)
    {
        if (!columnName)
            return;

        if (false === this.fireEvent("beforeclearsort", this, columnName))
            return;

        var newSortString = this.alterSortString(LABKEY.DataRegion._filterUI.getParameter(this.name + ".sort"), columnName, null);
        if (newSortString.length > 0)
            this._setParam(".sort", newSortString, [".sort", ".offset"]);
        else
            this._removeParams([".sort", ".offset"]);
    };

    // private
    this.changeFilter = function (newParamValPairs, newQueryString)
    {
        if (false === this.fireEvent("beforefilterchange", this, newParamValPairs))
            return;

        LABKEY.DataRegion._filterUI.setSearchString(this.name, newQueryString);
    };

    /**
     * Removes all the filters for a particular field
     * @param fieldName the name of the field from which all filters should be removed
     */
    this.clearFilter = function (fieldName)
    {
        if (false === this.fireEvent("beforeclearfilter", this, fieldName))
            return;
        this._removeParams(["." + fieldName + "~", ".offset"]);
    };

    /** Removes all filters from the DataRegion */
    this.clearAllFilters = function ()
    {
        if (false === this.fireEvent("beforeclearallfilters", this))
            return;
        this._removeParams([".", ".offset"]);
    };

    /**
     * Returns user filters from the URL as an Array of objects of the form:
     * <ul>
     *   <li><b>fieldKey</b>: {String} The field key of the filter.
     *   <li><b>op</b>: {String} The filter operator (eg. "eq" or "in")
     *   <li><b>value</b>: {String} Optional value to filter by.
     * </ul>.
     */
    this.getUserFilter = function ()
    {
        var userFilter = [];
        var paramValPairs = LABKEY.DataRegion._filterUI.getParamValPairs(this.requestURL, null);
        for (var i = 0; i < paramValPairs.length; i++)
        {
            var pair = paramValPairs[i];
            if (pair[0].indexOf(this.name + ".") == 0 && pair[0].indexOf('~') > -1)
            {
                var tilde = pair[0].indexOf('~');
                var fieldKey = pair[0].substring(this.name.length + 1, tilde);
                var op = pair[0].substring(tilde + 1);
                var value = pair[1];

                userFilter.push({fieldKey: fieldKey, op: op, value: value});
            }
        }

        return userFilter;
    };

    /**
     * Returns the user containerFilter from the URL.
     * Supported values include:
     * <ul>
     *   <li>"Current": Include the current folder only</li>
     *   <li>"CurrentAndSubfolders": Include the current folder and all subfolders</li>
     *   <li>"CurrentPlusProject": Include the current folder and the project that contains it</li>
     *   <li>"CurrentAndParents": Include the current folder and its parent folders</li>
     *   <li>"CurrentPlusProjectAndShared": Include the current folder plus its project plus any shared folders</li>
     *   <li>"AllFolders": Include all folders for which the user has read permission</li>
     * </ul>
     */
    this.getUserContainerFilter = function ()
    {
        return LABKEY.DataRegion._filterUI.getParameter(this.name + ".containerFilterName");
    };

    /**
     * Returns user sorts from the URL as an Array of objects of the form:
     * <ul>
     *   <li><b>fieldKey</b>: {String} The field key of the sort.
     *   <li><b>dir</b>: {String} The sort direction, either "+" or "-".
     * </ul>.
     */
    this.getUserSort = function ()
    {
        var userSort = [];
        var sortParam = LABKEY.DataRegion._filterUI.getParameter(this.name + ".sort");
        if (sortParam)
        {
            var sortArray = sortParam.split(",");
            for (var i = 0; i < sortArray.length; i++)
            {
                var sort = sortArray[i];
                var fieldKey = sort;
                var dir = "+";
                if (sort.charAt(0) == "-")
                {
                    fieldKey = fieldKey.substring(1);
                    dir = "-";
                }
                else if (sort.charAt(0) == "+")
                {
                    fieldKey = fieldKey.substring(1);
                }
                userSort.push({fieldKey: fieldKey, dir: dir});
            }
        }

        return userSort;
    };

    /**
     * Show a message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     * @return The Ext.Element of the newly created message div.
     */
    this.showMessage = function (html)
    {
        var div = this.msgbox.child("div");
        if (div.first())
            div.createChild({tag: 'hr'});
        var el = div.createChild({tag: 'div', cls: 'labkey-dataregion-msg', html: html});
        this.msgbox.setVisible(true);
        return el;
    };

    /**
     * Show a message in the header of this DataRegion with a loading indicator.
     * @param html the HTML source of the message to be shown
     * @return The Ext.Element of the newly created message div.
     */
    this.showLoadingMessage = function (html)
    {
        html = html || "Loading...";
        this.clearMessage();
        return this.showMessage("<div><span class='loading-indicator'>&nbsp;</span><em>" + html + "</em></div>");
    };

    /**
     * Show a success message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     * @return The Ext.Element of the newly created message div.
     */
    this.showSuccessMessage = function (html)
    {
        html = html || "Completed successfully.";
        this.clearMessage();
        return this.showMessage("<div class='labkey-message'>" + html + "</div>");
    };

    /**
     * Show an error message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     * @return The Ext.Element of the newly created message div.
     */
    this.showErrorMessage = function (html)
    {
        html = html || "An error occurred.";
        this.clearMessage();
        return this.showMessage("<div class='labkey-error'>" + html + "</div>");
    };

    /** Returns true if a message is currently being shown for this DataRegion. Messages are shown as a header. */
    this.isMessageShowing = function()
    {
        return this.msgbox.isVisible();
    };

    /** If a message is currently showing, hide it and clear out its contents */
    this.hideMessage = function ()
    {
        this.msgbox.setVisible(false, false);
        this.clearMessage();
    };

    /** Clear the message box contents. */
    this.clearMessage = function ()
    {
        var div = this.msgbox.child("div");
        div.dom.innerHTML = "";
    };

    this.alterSortString = function(currentSortString, columnName, direction)
    {
        var newSortArray = [];

        if (currentSortString != null)
        {
            var sortArray = currentSortString.split(",");
            for (var j = 0; j < sortArray.length; j++)
            {
                if (sortArray[j] != columnName && sortArray[j] != "+" + columnName && sortArray[j] != "-" + columnName)
                    newSortArray.push(sortArray[j]);
            }
        }

        if (direction == "+") //Easier to read without the encoded + on the URL...
            direction = "";

        if (null !== direction)
            newSortArray = [direction + columnName].concat(newSortArray);

        return newSortArray.join(",");
    };

    /**
     * Change the currently selected view to the named view
     * @param viewName the name of the saved view to display
     * @param urlParameters <b>NOTE: Experimental parameter; may change without warning.</b> A set of filter and sorts to apply as URL parameters when changing the view.
     */
    this.changeView = function(viewName, urlParameters)
    {
        if (false === this.fireEvent("beforechangeview", this, viewName, urlParameters))
            return;

        var skipPrefixes = [".offset", ".showRows", ".viewName", ".reportId"];
        var newParamValPairs = [];
        if (viewName)
            newParamValPairs.push([".viewName", viewName]);
        if (urlParameters)
        {
            if (urlParameters.filter && urlParameters.filter.length > 0)
            {
                for (var i = 0; i < urlParameters.filter.length; i++)
                {
                    var filter = urlParameters.filter[i];
                    newParamValPairs.push(["." + filter.fieldKey + "~" + filter.op, filter.value]);
                }
            }

            if (urlParameters.sort && urlParameters.sort.length > 0)
            {
                var newSortArray = [];
                for (var i = 0; i < urlParameters.sort.length; i++)
                {
                    var sort = urlParameters.sort[i];
                    newSortArray.push((sort.dir == "+" ? "" : sort.dir) + sort.fieldKey);
                }
                newParamValPairs.push([".sort", newSortArray.join(",")]);
            }

            if (urlParameters.containerFilter)
                newParamValPairs.push([".containerFilterName", urlParameters.containerFilter]);

            // removes all filter, sort, and container filter parameters
            skipPrefixes.push(".");
            skipPrefixes.push(".sort");
            skipPrefixes.push(".containerFilterName");
        }


        this._setParams(newParamValPairs, skipPrefixes);
    };

    this._initElements();
    Ext.EventManager.on(window, "load", this._resizeContainer, this, {single: true});
    Ext.EventManager.on(window, "resize", this._resizeContainer, this);
    this._showPagination(this.header);
    this._showPagination(this.footer);

    if (this.view && this.view.session)
    {
        var msg;
        if (this.view.savable)
        {
            msg = (this.viewName ? "The current view '<em>" + Ext.util.Format.htmlEncode(this.viewName) + "</em>'" : "The current <em>&lt;default&gt;</em> view") + " is unsaved.";
            msg += " &nbsp;";
            msg += "<span class='labkey-link unsavedview-revert'>Revert</span>";
            msg += ", &nbsp;";
            msg += "<span class='labkey-link unsavedview-edit'>Edit</span>";
            msg += ", &nbsp;";
            msg += "<span class='labkey-link unsavedview-save'>Save</span>";
        }
        else
        {
            msg = ("The current view has been customized.");
            msg += " &nbsp;";
            msg += "<span class='labkey-link unsavedview-revert' title='Revert'>Revert</span>";
            msg += ", &nbsp;";
            msg += "<span class='labkey-link unsavedview-edit'>Edit</span>";
        }

        var el = this.showMessage(msg);
        var revertEl = el.child(".labkey-link.unsavedview-revert");
        if (revertEl)
            revertEl.on('click', this.revertCustomView, this);

        var showCustomizeViewEl = el.child(".labkey-link.unsavedview-edit");
        if (showCustomizeViewEl)
            showCustomizeViewEl.on('click', function () { this.showCustomizeView(undefined, true); }, this);

        var saveEl = el.child(".labkey-link.unsavedview-save");
        if (saveEl)
            saveEl.on('click', this.saveSessionCustomView, this);
    }

    if (this.showInitialSelectMessage)
    {
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

        var headerWidth = this.table.getWidth(true);
        if (this.table.getRight(false) > viewportWidth)
            headerWidth = viewportWidth - this.table.getLeft(false);

        if (this.header)
        {
            var frameWidth = this.header.getFrameWidth("lr") + this.header.parent().getFrameWidth("lr");
            this.header.setWidth(headerWidth - frameWidth);
        }

        if (this.footer)
        {
            var frameWidth = this.footer.getFrameWidth("lr") + this.footer.parent().getFrameWidth("lr");
            this.footer.setWidth(headerWidth - frameWidth);
        }
    },

    // private
    _removeParams : function (skipPrefixes)
    {
        this._setParams(null, skipPrefixes);
    },

    _setParam : function (param, value, skipPrefixes)
    {
        this._setParams([[param, value]], skipPrefixes);
    },

    // private
    _setParams : function (newParamValPairs, skipPrefixes)
    {
        for (var i in skipPrefixes)
            skipPrefixes[i] = this.name + skipPrefixes[i];

        var paramValPairs = LABKEY.DataRegion._filterUI.getParamValPairs(this.requestURL, skipPrefixes);
        if (newParamValPairs)
        {
            for (var i = 0; i < newParamValPairs.length; i++)
            {
                var param = newParamValPairs[i][0],
                        value = newParamValPairs[i][1];
                if (null != param && null != value)
                    paramValPairs[paramValPairs.length] = [this.name + param, value];
            }
        }
        LABKEY.DataRegion._filterUI.setSearchString(this.name, LABKEY.DataRegion._filterUI.buildQueryString(paramValPairs));
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
    _setSelected : function (ids, checked, success)
    {
        if (!this.selectionKey || ids.length == 0)
            return;

        this.selectionModified = true;

        LABKEY.DataRegion.setSelected({
            selectionKey: this.selectionKey,
            ids: ids,
            checked: checked,
            scope: this,
            success: success,
            failure: function (response, options) { this.showMessage("Error sending selection."); }
        });
    },

    // private
    _showSelectMessage : function (msg)
    {
        if (this.showRecordSelectors)
        {
            msg += "&nbsp; Select: <span class='labkey-link select-none'>None</span>";
            var showOpts = new Array();
            if (this.showRows != "all")
                showOpts.push("<span class='labkey-link show-all'>All</span>");
            if (this.showRows != "selected")
               showOpts.push("<span class='labkey-link show-selected'>Selected</span>");
            if (this.showRows != "unselected")
               showOpts.push("<span class='labkey-link show-unselected'>Unselected</span>");
            msg += "&nbsp; Show: " + showOpts.join(", ");
        }

        var el = this.showMessage(msg);
        var selectNoneEl = el.child(".labkey-link.select-none");
        if (selectNoneEl)
            selectNoneEl.on('click', this.selectNone, this);

        var showAllEl = el.child(".labkey-link.show-all");
        if (showAllEl)
            showAllEl.on('click', this.showAll, this);

        var showSelectedEl = el.child(".labkey-link.show-selected");
        if (showSelectedEl)
            showSelectedEl.on('click', this.showSelected, this);

        var showUnselectedEl = el.child(".labkey-link.show-unselected");
        if (showUnselectedEl)
            showUnselectedEl.on('click', this.showUnselected, this);

    },

    onSelectChange : function (hasSelected)
    {
        var fn = hasSelected ? LABKEY.Utils.enableButton : LABKEY.Utils.disableButton;

        // 10566: for javascript perf on IE stash the requires selection buttons
        if (!this._requiresSelectionButtons)
        {
            // escape ' and \
            var escaped = this.name.replace(/('|\\)/g, "\\$1");
            this._requiresSelectionButtons = Ext.DomQuery.select("a[labkey-requires-selection='" + escaped + "']");
        }
        Ext.each(this._requiresSelectionButtons, fn);

        this.fireEvent('selectchange', this, hasSelected);
    },

    onButtonClick : function(buttonId)
    {
        return this.fireEvent("buttonclick", buttonId, this);
    },

    /**
     * Show a ribbon panel. tabPanelConfig is an Ext config object for a TabPanel, the only required
     * value is the items array.
     */
    showButtonPanel : function (panelButton, tabPanelConfig)
    {
        panelButton = Ext.get(panelButton);
        var regionHeader = panelButton.parent(".labkey-data-region-header");
        if (!regionHeader)
            return;

        this._showButtonPanel(regionHeader, panelButton.getAttribute("panelId"), true, tabPanelConfig);
    },

    _showButtonPanel : function(headerOrFooter, panelId, animate, tabPanelConfig)
    {
        var panelDiv = headerOrFooter.child(".labkey-ribbon");
        if (panelDiv)
        {
            var panelToHide = null;
            // If we find a spot to put the panel, check its current contents
            if (this.currentPanelId)
            {
                // We're currently showing a ribbon panel, so remember that we need to hide it
                panelToHide = this.panelButtonContents[this.currentPanelId];
            }

            // Create a callback function to render the requested ribbon panel
            var callback = function()
            {
                if (panelToHide)
                {
                    panelToHide.setVisible(false);
                }
                if (this.currentPanelId != panelId)
                {
                    panelDiv.setDisplayed(true);
                    if (!this.panelButtonContents[panelId])
                    {
                        var minWidth = 0;
                        var tabContentWidth = 0;
                        var VERTICAL_TAB_HEIGHT = 28; // pixels. Way to measure how tall the main panel should be
                        var height = VERTICAL_TAB_HEIGHT * 4;
                        if (tabPanelConfig.items.length > 4)
                            height = VERTICAL_TAB_HEIGHT * tabPanelConfig.items.length;

                        // New up the TabPanel if we haven't already
                        // Only create one per button, even if that button is rendered both above and below the grid
                        tabPanelConfig.cls ='vertical-tabs';
                        tabPanelConfig.tabWidth = 80;
                        tabPanelConfig.renderTo = panelDiv;
                        tabPanelConfig.activeGroup = 0;
                        tabPanelConfig.height = height;
                        var newItems = new Array(tabPanelConfig.items.length);
                        for (var i = 0; i < tabPanelConfig.items.length; i++)
                        {
                            newItems[i] = tabPanelConfig.items[i];
                            newItems[i].autoScroll = true;

                            //FF and IE won't auto-resize the tab panel to fit the content
                            //so we need to calculate the min size and set it explicitly
                            if (Ext.isGecko || Ext.isIE)
                            {
                                var item = newItems[i];
                                if (!item.events)
                                    newItems[i] = item = Ext.create(item, 'grouptab');
                                item.removeClass("x-hide-display");
                                if (item.items.getCount() > 0 && item.items.items[0].contentEl)
                                {
                                    tabContentWidth = Ext.get(item.items.items[0].contentEl).getWidth();
                                    item.addClass("x-hide-display");
                                    minWidth = Math.max(minWidth, tabContentWidth);
                                }
                            }
                        }
                        tabPanelConfig.items = newItems;
                        if ((Ext.isGecko || Ext.isIE) && minWidth > 0 && headerOrFooter.getWidth() < minWidth)
                            tabPanelConfig.width = minWidth;
                        this.panelButtonContents[panelId] = new Ext.ux.GroupTabPanel(tabPanelConfig);
                    }
                    else
                    {
                        // Otherwise, be sure that it's parented correctly - it might have been shown
                        // in a different button bar position
                        this.panelButtonContents[panelId].getEl().appendTo(Ext.get(panelDiv));
                    }

                    this.currentPanelId = panelId;

                    // Slide it into place
                    var panelToShow = this.panelButtonContents[panelId];
                    panelToShow.setVisible(true);
                    panelToShow.getEl().slideIn();

                    panelToShow.setWidth(panelToShow.getResizeEl().getWidth());
                }
                else
                {
                    this.currentPanelId = null;
                    panelDiv.setDisplayed(false);
                }
            };

            if (this.currentPanelId)
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
    },

    /**
     * Show the customize view interface.
     * @param activeTab {[String]} Optional. One of "ColumnsTab", "FilterTab", or "SortTab".  If no value is specified (or undefined), the ColumnsTab will be shown.
     * @param hideMessage {[boolean]} Optional. True to hide the DataRegion message bar when showing.
     * @param animate {[boolean]} Optional. True to slide in the ribbon panel.
     */
    showCustomizeView : function (activeTab, hideMessage, animate)
    {
        if (hideMessage)
            this.hideMessage();

        // UNDONE: when both header and footer are rendered, need to show the panel in the correct button bar
        var headerOrFooter = this.header || this.footer;

        if (!this.customizeView)
        {
            var timerId = function () {
                timerId = 0;
                this.showLoadingMessage("Opening custom view designer...");
            }.defer(500, this);

            LABKEY.initializeViewDesigner(function () {
                var additionalFields = {};
                var userFilter = this.getUserFilter();
                var userSort = this.getUserSort();

                for (var i = 0; i < userFilter.length; i++)
                    additionalFields[userFilter[i].fieldKey] = true;

                for (i = 0; i < userSort.length; i++)
                    additionalFields[userSort[i].fieldKey] = true;

                var fields = [];
                for (var fieldKey in additionalFields)
                    fields.push(fieldKey);

                var viewName = (this.view && this.view.name) || this.viewName || "";
                LABKEY.Query.getQueryDetails({
                    schemaName: this.schemaName,
                    queryName: this.queryName,
                    viewName: viewName,
                    fields: fields,
                    success: function (json, response, options) {
                        if (timerId > 0)
                            clearTimeout(timerId);
                        else
                            this.hideMessage();

                        var minWidth = Math.max(700, headerOrFooter.getWidth(true));
                        var renderTo = Ext.getBody().createChild({tag: "div", customizeView: true, style: {display: "none"}});

                        this.customizeView = new LABKEY.DataRegion.ViewDesigner({
                            renderTo: renderTo,
                            width: minWidth,
                            activeGroup: activeTab,
                            dataRegion: this,
                            schemaName: this.schemaName,
                            queryName: this.queryName,
                            viewName: viewName,
                            query: json,
                            allowableContainerFilters: this.allowableContainerFilters
                        });

                        this.customizeView.on("viewsave", this.onViewSave, this);

                        this.panelButtonContents["~~customizeView~~"] = this.customizeView;
                        this._showButtonPanel(headerOrFooter, "~~customizeView~~", animate, null);
                    },
                    scope: this
                });
            }, this);
        }
        else
        {
            if (activeTab)
            {
                this.customizeView.setActiveGroup(activeTab);
                var group = this.customizeView.activeGroup;
                if (!group.activeItem)
                    group.setActiveTab(group.getMainItem());
            }
            if (this.currentPanelId != "~~customizeView~~")
                this._showButtonPanel(headerOrFooter, "~~customizeView~~", animate, null);
        }
    },

    /**
     * Hide the customize view interface if it is showing.
     */
    hideCustomizeView : function ()
    {
        if (this.customizeView && this.customizeView.isVisible())
            this._showButtonPanel(this.header || this.footer, "~~customizeView~~", true, null);
    },

    // private
    toggleShowCustomizeView : function ()
    {
        if (this.customizeView && this.customizeView.isVisible())
            this.hideCustomizeView();
        else
            this.showCustomizeView();
    },

    // private
    deleteCustomView : function ()
    {
        var title = "Delete " +
                (this.view && this.view.shared ? "shared " : "your ") +
                (this.view && this.view.session ? "unsaved" : "") + "view";
        var msg = "Are you sure you want to delete the ";
        if (this.viewName)
            msg += " '<em>" + Ext.util.Format.htmlEncode(this.viewName) + "</em>'";
        else
            msg += "default";
        msg += " saved view?";
        Ext.Msg.confirm(title, msg, function (btnId) {
            if (btnId == "yes")
            {
                this._deleteCustomView(true, "Deleting view...");
            }
        }, this);
    },

    // private
    revertCustomView : function ()
    {
        this._deleteCustomView(false, "Reverting view...");
    },

    _deleteCustomView : function (complete, message)
    {
        var timerId = function () {
            timerId = 0;
            this.showLoadingMessage(message);
        }.defer(500, this);

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("query", "deleteView"),
            jsonData: {schemaName: this.schemaName, queryName: this.queryName, viewName: this.viewName, complete: complete},
            method: "POST",
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                if (timerId > 0)
                    clearTimeout(timerId);
                this.showSuccessMessage();
                // change view to either a shadowed view or the default view
                var viewName = json.viewName;
                this.changeView(viewName);
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                if (timerId > 0)
                    clearTimeout(timerId);
                this.showErrorMessage(json.exception);
            }, this, true)
        });
    },

    _getCustomViewEditableErrors : function (view)
    {
        var errors = [];
        if (view)
        {
            if (view.inherit)
            {
                if (view.containerPath && view.containerPath != LABKEY.ActionURL.getContainer())
                    errors.push("Inherited views can only be edited from the defining folder.");
            }

            if (!view.editable)
                errors.push("The view is read-only and cannot be edited.");
        }
        return errors;
    },

    // private
    saveSessionCustomView : function ()
    {
        // Note: currently only will save session views. Future version could create a new view using url sort/filters.
        if (!(this.view && this.view.session))
            return;

        var self = this;
        function showPrompt()
        {
            var config = Ext.applyIf({
                canEditSharedViews: self.canEditSharedViews,
                canEdit: self._getCustomViewEditableErrors(config).length == 0,
                success: function (win, o) {
                    var timerId = function () {
                        timerId = 0;
                        self.showLoadingMessage("Saving custom view...");
                    }.defer(500, self);

                    var jsonData = {
                        schemaName: self.schemaName,
                        "query.queryName": self.queryName,
                        "query.viewName": self.viewName,
                        newName: o.name,
                        inherit: o.inherit,
                        shared: o.shared
                    };

                    Ext.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("query", "saveSessionView"),
                        method: "POST",
                        jsonData: jsonData,
                        headers : {
                            'Content-Type' : 'application/json'
                        },
                        scope: self,
                        success: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                            if (timerId > 0)
                                clearTimeout(timerId);
                            self.showSuccessMessage();
                            self.changeView(o.name);
                        }, self),
                        failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                            if (timerId > 0)
                                clearTimeout(timerId);
                            self.showErrorMessage(json.exception);
                        }, self, true)
                    });
                },
                scope: self
            }, self.view);

            LABKEY.DataRegion.saveCustomizeViewPrompt(config);
        }

        // CONSIDER: moving into LABKEY.DataRegion constructor
        if (this.canEditSharedViews === undefined)
        {
            LABKEY.Security.getUserPermissions({
                userId: LABKEY.user.id,
                success: function (info) {
                    var canEditSharedViews = false;
                    if (info && info.container && info.container.effectivePermissions)
                        canEditSharedViews = info.container.effectivePermissions.indexOf("org.labkey.api.security.permissions.EditSharedViewPermission") != -1;

                    this.canEditSharedViews = canEditSharedViews;
                    showPrompt();
                },
                scope: this
            });
        }
        else
        {
            showPrompt();
        }

    },

    onViewSave : function (designer, savedViewsInfo, urlParameters) {
        if (savedViewsInfo && savedViewsInfo.views.length > 0)
        {
            this.hideCustomizeView();
            this.changeView(savedViewsInfo.views[0].name, urlParameters);
        }
    }

});


/**
 * Add or remove items from the current selection.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey Unique string used by selection APIs as a key when storing or retrieving the selected items for a grid.
 * @param {Array} config.id Array of primary key ids for each row to select/unselect.
 * @param {Boolean} config.checked If true, the ids will be selected, otherwise unselected.
 * @param {Function} config.success The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'count' to indicate the updated selection count.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failure] The function to call upon error of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
 * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
 */
LABKEY.DataRegion.setSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "setSelected.api", config.containerPath,
        { 'key' : config.selectionKey, 'checked' : config.checked });
    var params = { id: config.ids };

    Ext.Ajax.request({
        url: url,
        method: "POST",
        params: params,
        scope: config.scope,
        success: LABKEY.Utils.getOnSuccess(config),
        failure: LABKEY.Utils.getOnFailure(config)
    });
};

/**
 * Clear all selected items.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey Unique string used by selection APIs as a key when storing or retrieving the selected items for a grid.
 * @param {Function} config.success The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'count' of 0 to indicate an empty selection.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failure] The function to call upon error of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
 * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
 */
LABKEY.DataRegion.clearSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "clearSelected.api", config.containerPath,
        { 'key' : config.selectionKey });

    Ext.Ajax.request({ url: url });
};

/**
 * Get all selected items.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey Unique string used by selection APIs as a key when storing or retrieving the selected items for a grid.
 * @param {Function} config.success The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'selected' that is an array of the primary keys for the selected rows.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failure] The function to call upon error of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
 * @param {string} [config.containerPath] An alternate container path. If not specified, the current container path will be used.
 */
LABKEY.DataRegion.getSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "getSelected.api", config.containerPath,
        { 'key' : config.selectionKey });

    Ext.Ajax.request({
        url: url,
        success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
        failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
    });
};

LABKEY.DataRegion.saveCustomizeViewPrompt = function (config)
    {
        var success = config.success;
        var scope = config.scope;

        var viewName = config.name;
        var hidden = config.hidden;
        var session = config.session;
        var inherit = config.inherit;
        var shared = config.shared;
        var containerPath = config.containerPath;
        var canEdit = config.canEdit;
        var canEditSharedViews = config.canEditSharedViews;

        var disableSharedAndInherit = LABKEY.user.isGuest || hidden /*|| session*/ || (containerPath && containerPath != LABKEY.ActionURL.getContainer());
        var newViewName = viewName || "New View";
        if (!canEdit && viewName)
            newViewName = viewName + " Copy";

        var win = new Ext.Window({
            title: "Save Custom View" + (viewName ? ": " + Ext.util.Format.htmlEncode(viewName) : ""),
            cls: "extContainer",
            bodyStyle: "padding: 6px",
            modal: true,
            width: 460,
            height: 220,
            layout: "form",
            defaults: {
                tooltipType: "title"
            },
            items: [{
                ref: "defaultNameField",
                xtype: "radio",
                fieldLabel: "View Name",
                boxLabel: "Default view for this page",
                inputValue: "default",
                name: "saveCustomView_namedView",
                checked: canEdit && !viewName,
                disabled: hidden || !canEdit
            },{
                xtype: "compositefield",
                ref: "nameCompositeField",
                items: [{
                    xtype: "radio",
                    fieldLabel: "",
                    boxLabel: "Named",
                    inputValue: "named",
                    name: "saveCustomView_namedView",
                    checked: !canEdit || viewName,
                    handler: function (radio, value) {
                        // nameCompositeField.items will be populated after initComponent
                        if (win.nameCompositeField.items.get)
                        {
                            var nameField = win.nameCompositeField.items.get(1);
                            if (value)
                                nameField.enable();
                            else
                                nameField.disable();
                        }
                    },
                    scope: this
                },{
                    fieldLabel: "",
                    xtype: "textfield",
                    name: "saveCustomView_name",
                    tooltip: "Name of the custom view",
                    tooltipType: "title",
                    allowBlank: false,
                    emptyText: "Name is required",
                    maxLength: 50,
                    autoCreate: {tag: 'input', type: 'text', size: '50'},
                    selectOnFocus: true,
                    value: newViewName,
                    disabled: hidden || (canEdit && !viewName)
                }]
            },{
                xtype: "box",
                style: "padding-left: 122px; padding-bottom: 8px",
                html: "<em>The current view can't be saved over.<br>Please enter an alternate view name.</em>",
                hidden: canEdit
            },{
                xtype: "spacer",
                height: "8"
            },{
                ref: "sharedField",
                xtype: "checkbox",
                name: "saveCustomView_shared",
                fieldLabel: "Shared",
                boxLabel: "Make this grid view available to all users",
                checked: shared,
                disabled: disableSharedAndInherit || !canEditSharedViews
            },{
                ref: "inheritField",
                xtype: "checkbox",
                name: "saveCustomView_inherit",
                fieldLabel: "Inherit",
                boxLabel: "Make this grid view available in child folders",
                checked: inherit,
                disabled: disableSharedAndInherit
            }],
            buttons: [{
                text: "Save",
                handler: function () {
                    var nameField = win.nameCompositeField.items.get(1);
                    if (!canEdit && viewName == nameField.getValue())
                    {
                        Ext.Msg.alert("Error saving", "You must save this view with an alternate name.");
                        return;
                    }

                    var o = {};
                    if (hidden)
                    {
                        o = {
                            name: viewName,
                            shared: shared,
                            hidden: true,
                            session: session // set session=false for hidden views?
                        };
                    }
                    else
                    {
                        o.name = "";
                        if (!win.defaultNameField.getValue())
                            o.name = nameField.getValue();
                        o.session = false;
                        if (!o.session && canEditSharedViews)
                        {
                            o.shared = win.sharedField.getValue();
                            o.inherit = win.inheritField.getValue();
                        }
                    }

                    success.call(scope, win, o);
                    win.close();
                },
                scope: this
            },{
                text: "Cancel",
                handler: function () { win.close(); }
            }]
        });
        win.show();
    };

// FILTER UI

// TODO convert completely to Ext
// NOTE filter UI is shared, but I still don't like all these global/single instance variables

// private
LABKEY.DataRegion._filterUI =
{
    _tableName : "",
    _fieldName : "",
    _fieldCaption : "",
    _fieldType : "text",
    _filterDiv : null,
    _filterWin : null,
    _filterQueryString : "",
    _valueComponent1 : null,
    _valueComponent2 : null,

    setFilterQueryString : function(s)
    {
        this._filterQueryString = s;
    },


    getFilterDiv : function ()
    {
        if (!this._filterDiv)
        {
            LABKEY.addMarkup('<div id="filterDiv" style="display:none;">' +
            '  <table onkeypress="LABKEY.DataRegion._filterUI.handleKey(event);">' +
            '    <tr>' +
            '      <td colspan=2 style="padding: 5px" nowrap>' +
            '        <select id="compare_1" name="compare_1" onchange="LABKEY.DataRegion._filterUI.doChange(this)">' +
            '            <option value="">&lt;has any value></option>' +
            '        </select><br>' +
            '        <div id="filterDivInput1"></div><br>' +
            '        <div id="compareDiv_2" style="visibility:hidden">and<br>' +
            '        <select id="compare_2" name="compare_2" onchange="LABKEY.DataRegion._filterUI.doChange(this)">' +
            '            <option value="">&lt;no other filter></option>' +
            '        </select><br>' +
            '        <div id="filterDivInput2"></div><br><br>' +
            '        </div>' +
            '        <a class="labkey-button" id="filterPanelOKButton" href="#" onclick="LABKEY.DataRegion._filterUI.doFilter();return false;"><span>OK</span> ' +
            '        <a class="labkey-button" id="filterPanelCancelButton" href="#" onclick="LABKEY.DataRegion._filterUI.hideFilterDiv();return false;"><span>Cancel</span> ' +
            '        <a class="labkey-button" id="filterPanelClearFilterButton" href="#" onclick="LABKEY.DataRegion._filterUI.clearFilter();return false;"><span>Clear Filter</span> ' +
            '        <a class="labkey-button" id="filterPanelClearAllFiltersButton" href="#" onclick="LABKEY.DataRegion._filterUI.clearAllFilters();return false;"><span>Clear All Filters</span> ' +
            '      </td>' +
            '    </tr>' +
            '  </table>' +
            '</div>');
            this._filterDiv = document.getElementById("filterDiv");
        }
        return this._filterDiv;
    },


    doChange : function(obj)
    {
        var index = obj.name.split("_")[1];

        var valueComponent = index == "1" ? this._valueComponent1 : this._valueComponent2;

        var compare = obj.options[obj.selectedIndex].value;
        if (compare == "" || compare == "isblank" || compare == "isnonblank" || compare == "nomvvalue" || compare == "hasmvvalue")
        {
            if (index == "1")
                document.getElementById("compareDiv_2").style.visibility = "hidden";
            if (valueComponent)
            {
                valueComponent.setVisible(false);
                valueComponent.setDisabled(true);
            }
        }
        else
        {
            if (index == "1")
                document.getElementById("compareDiv_2").style.visibility = "visible";
            if (valueComponent)
            {
                valueComponent.setDisabled(false);
                valueComponent.setVisible(true);
                valueComponent.focus();
                //TODO valueInput.select();
            }
        }
    },


    showFilterPanel : function(dataRegionName, colName, caption, dataType, mvEnabled, queryString, dialogTitle, confirmCallback)
    {
        this._fieldName = colName;
        this._fieldCaption = caption;
        this._tableName = dataRegionName;

        this.fillOptions(dataType, mvEnabled);
        this.createInputs(dataType);

        if (!queryString)
        {
            queryString = LABKEY.DataRegions[dataRegionName] ? LABKEY.DataRegions[dataRegionName].requestURL : null;
        }

        if (!confirmCallback)
        {
            // Invoked as part of a regular filter dialog on a grid
            this.changeFilterCallback = this.changeFilter;
            document.getElementById("filterPanelClearAllFiltersButton").style.display="";
            document.getElementById("filterPanelClearFilterButton").style.display="";
        }
        else
        {
            // Invoked from GWT, which will handle the commit itself
            this.changeFilterCallback = confirmCallback;
            document.getElementById("filterPanelClearAllFiltersButton").style.display="none";
            document.getElementById("filterPanelClearFilterButton").style.display="none";
        }

        var paramValPairs = this.getParamValPairs(queryString, null);
        //Fill in existing filters...
        var filterIndex = 1;
        for (var i = 0; i < paramValPairs.length; i++)
        {
            var pair = paramValPairs[i];
            var key = pair[0];
            var value = pair.length > 1 ? pair[1] : "";

            if (key.indexOf(this._tableName + "." + this._fieldName + "~") != 0)
                continue;

            // set dropdown
            var comparison = (key.split("~"))[1];
            var select = document.getElementById("compare_" + filterIndex);
            for (var opt = 0; opt < select.options.length; opt++)
            {
                if (select.options[opt].value == comparison)
                {
                    select.selectedIndex = opt;
                    break;
                }
            }

            // set input
            var valueComponent = filterIndex==1 ? this._valueComponent1 : this._valueComponent2;
            valueComponent.setValue(value);

            filterIndex++;
            if (filterIndex > 2)
                break;
        }
        var div = this.getFilterDiv();
        div.style.display = "block";
        div.style.visibility = "visible";

        if (!this._filterWin)
        {
            this._filterWin = new Ext.Window({
                contentEl: div,
                width: 350,
                autoHeight: true,
                modal: true,
                resizable: false,
                closeAction: 'hide'
            });

            // 5975: Override focus behavior. Keeps Ext.Window from stealing focus after showing.
            this._filterWin.focus = function () {
                LABKEY.DataRegion._filterUI.doChange(document.getElementById("compare_1"));
                LABKEY.DataRegion._filterUI.doChange(document.getElementById("compare_2"));
            };
        }
        else
        {
            this._filterWin.center();
        }

        if (filterIndex == 2)
            document.getElementById("compare_2").selectedIndex = 0;

        this._filterWin.setTitle(dialogTitle ? dialogTitle : "Show Rows Where " + caption);
        this._filterWin.show();
    },

    hideFilterDiv : function()
    {
        if (this._filterWin)
            this._filterWin.hide();
    },

    _typeMap : {
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
        "SERIAL":"INT",
        "USERID":"INT"
    },

    _mappedType : "TEXT",


    getMappedType : function(dataType)
    {
        var mappedType = this._typeMap[dataType.toUpperCase()];
        if (mappedType == undefined)
            mappedType = dataType.toUpperCase();
        return mappedType;
    },


    createInputs : function(dataType)
    {
        this.getFilterDiv();
        var mappedType = this.getMappedType(dataType);
        if (this._valueComponent1)
            this._valueComponent1.destroy();
        if (this._valueComponent2)
            this._valueComponent2.destroy();
        for (var i=1 ; i<=2 ; i++)
        {
            var id = "value_" + i; // still used by automated tests
            var inputDiv = Ext.get("filterDivInput" + i);
            var c;
            if ("DATE" == mappedType)
            {
                c = new Ext.form.DateField({id: id, width:180, renderTo:inputDiv});
            }
            else if ("INT" == mappedType)
            {
                c = new Ext.form.NumberField({id: id, width:180, renderTo:inputDiv, allowDecimals:false});
            }
            else if ("DECIMAL" == mappedType)
            {
                c = new Ext.form.NumberField({id: id, width:180, renderTo:inputDiv, decimalPrecision:15});
            }
            else
            {
                c = new Ext.form.TextField({id: id, width:180, renderTo:inputDiv});
            }
            if (i==1)
                this._valueComponent1 = c;
            else
                this._valueComponent2 = c;
        }
    },


    fillOptions : function(dataType, mvEnabled)
    {
        this.getFilterDiv();
        var mappedType = this.getMappedType(dataType);

        for (var i = 1; i <= 2; i++)
        {
            var select = document.getElementById("compare_" + i);
            var opt;
            select.options.length = 1;

            if (mappedType != "LONGTEXT")
            {
                opt = document.createElement("OPTION");
                opt.value = (mappedType == "DATE") ? "dateeq" : "eq";
                opt.text = "Equals";
                this.appendOption(select, opt);
                if (mappedType != "BOOL" && mappedType != "DATE")
                {
                    opt = document.createElement("OPTION");
                    opt.value = "in";
                    opt.text = "Equals One Of (e.g. 'a;b;c')";
                    this.appendOption(select, opt);
                }
                opt = document.createElement("OPTION");
                opt.value = (mappedType == "DATE") ? "dateneq" : "neqornull";
                opt.text = "Does not Equal";
                this.appendOption(select, opt);
            }

            opt = document.createElement("OPTION");
            opt.value = "isblank";
            opt.text = "Is Blank";
            this.appendOption(select, opt);

            opt = document.createElement("OPTION");
            opt.value = "isnonblank";
            opt.text = "Is Not Blank";
            this.appendOption(select, opt);

            if (mappedType != "LONGTEXT" && mappedType != "BOOL")
            {
                opt = document.createElement("OPTION");
                opt.value = (mappedType == "DATE") ? "dategt" : "gt";
                opt.text = "Is Greater Than";
                this.appendOption(select, opt);
                opt = document.createElement("OPTION");
                opt.value = (mappedType == "DATE") ? "datelt" : "lt";
                opt.text = "Is Less Than";
                this.appendOption(select, opt);
                opt = document.createElement("OPTION");
                opt.value = (mappedType == "DATE") ? "dategte" : "gte";
                opt.text = "Is Greater Than or Equal To";
                this.appendOption(select, opt);
                opt = document.createElement("OPTION");
                opt.value = (mappedType == "DATE") ? "datelte" : "lte";
                opt.text = "Is Less Than or Equal To";
                this.appendOption(select, opt);
            }

            if (mappedType == "TEXT" || mappedType == "LONGTEXT")
            {
                opt = document.createElement("OPTION");
                opt.value = "startswith";
                opt.text = "Starts With";
                this.appendOption(select, opt);
                opt = document.createElement("OPTION");
                opt.value = "doesnotstartwith";
                opt.text = "Does Not Start With";
                this.appendOption(select, opt);
                opt = document.createElement("OPTION");
                opt.value = "contains";
                opt.text = "Contains";
                this.appendOption(select, opt);
                opt = document.createElement("OPTION");
                opt.value = "doesnotcontain";
                opt.text = "Does Not Contain";
                this.appendOption(select, opt);
            }

            if (mvEnabled)
            {
                opt = document.createElement("OPTION");
                opt.value = "hasmvvalue";
                opt.text = "Has a missing value indicator";
                this.appendOption(select, opt);

                opt = document.createElement("OPTION");
                opt.value = "nomvvalue";
                opt.text = "Does not have a missing value indicator";
                this.appendOption(select, opt);
            }

            if (i == 1)
                this.selectDefault(select, mappedType);
        }

        _mappedType = mappedType;
    },

    appendOption : function(select, opt)
    {
        select.options[select.options.length] = opt;
    },

    selectDefault : function(select, mappedType)
    {
        if (mappedType == "LONGTEXT")
            this.selectByValue(select, "contains");
        else if (mappedType == "DECIMAL")
            this.selectByValue(select, "gte");
        else if (mappedType == "TEXT")
            this.selectByValue(select, "startswith");
        else if (select.options.length > 1)
            select.selectedIndex = 1;
    },

    selectByValue : function(select, value)
    {
        for (var i = 0; i < select.options.length; i++)
            if (select.options[i].value == value)
            {
                select.selectedIndex = i;
                return;
            }
    },

    savedSearchString : null,
    filterListeners : [],

    registerFilterListener : function(fn)
    {
        filterListeners.push(fn);
    },

    getSearchString : function()
    {
        if (null == this.savedSearchString)
            this.savedSearchString = document.location.search.substring(1) || "";
        return this.savedSearchString;
    },

    setSearchString : function(tableName, search)
    {
        this.hideFilterDiv();
        this.savedSearchString = search || "";
        for (var i=0; i < this.filterListeners.length; i++)
        {
            if (this.filterListeners[i](tableName, search))
            {
                this.hideFilterDiv();
                return;
            }
        }
        // If the search string doesn't change and there is a hash on the url, the page won't reload.
        // Remove the hash by setting the full path plus search string.
        window.location.assign(window.location.pathname + "?" + this.savedSearchString);
    },


    getParamValPairs : function(queryString, skipPrefixes)
    {
        if (!queryString)
        {
            queryString = this.getSearchString();
        }
        else
        {
            if (queryString.indexOf("?") > -1)
            {
                queryString = queryString.substring(queryString.indexOf("?") + 1);
            }
        }
        var iNew = 0;
        //alert("getparamValPairs: " + queryString);
        var newParamValPairs = new Array(0);
        if (queryString != null && queryString.length > 0)
        {
            var paramValPairs = queryString.split("&");
            PARAM_LOOP: for (var i = 0; i < paramValPairs.length; i++)
            {
                var paramPair = paramValPairs[i].split("=");
                paramPair[0] = decodeURIComponent(paramPair[0]);

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
                    paramPair[1] = decodeURIComponent(paramPair[1]);
                newParamValPairs[iNew] = paramPair;
                iNew++;
            }
        }
        return newParamValPairs;
    },


    getParameter : function(paramName)
    {
        var paramValPairs = this.getParamValPairs(null, null);
        for (var i = 0; i < paramValPairs.length; i++)
            if (paramValPairs[i][0] == paramName)
                if (paramValPairs[i].length > 1)
                    return paramValPairs[i][1];
                else
                    return "";

        return null;
    },


    buildQueryString : function(pairs)
    {
        if (pairs == null || pairs.length == 0)
            return "";

        var queryString = [];
        for (var i = 0; i < pairs.length; i++)
        {
            var key = pairs[i][0];
            var value = pairs[i].length > 1 ? pairs[i][1] : undefined;

            queryString.push(encodeURIComponent(key));
            if (undefined != value)
            {
                if (Ext.isDate(value))
                {
                    value = value.toISOString();
                    if (-1 != key.indexOf("~date"))
                        value = value.substring(0,10);
                    if (LABKEY.Utils.endsWith(value,"Z"))
                        value = value.substring(0,value.length-1);
                }
                queryString.push("=");
                queryString.push(encodeURIComponent(value));
            }
            queryString.push("&");
        }

        if (queryString.length > 0)
            queryString.pop();

        return queryString.join("");
    },

    clearFilter : function()
    {
        this.hideFilterDiv();
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.clearFilter(this._fieldName);
    },

    clearAllFilters : function()
    {
        this.hideFilterDiv();
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.clearAllFilters();
    },

    changeFilter : function(newParamValPairs, newQueryString)
    {
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.changeFilter(newParamValPairs, newQueryString);
    },

    doFilter : function()
    {
        var queryString = LABKEY.DataRegions[this._tableName] ? LABKEY.DataRegions[this._tableName].requestURL : null;
        var newParamValPairs = this.getParamValPairs(queryString, [this._tableName + "." + this._fieldName + "~", this._tableName + ".offset"]);
        var iNew = newParamValPairs.length;

        var comparisons = this.getValidCompares();
        if (null == comparisons)
            return;

        this.hideFilterDiv();

        for (var i = 0; i < comparisons.length; i++)
        {
            newParamValPairs[iNew] = comparisons[i];
            iNew ++;
        }

        var newQueryString = this.buildQueryString(newParamValPairs);
        var filterParamsString = this.buildQueryString(comparisons);

        this.changeFilterCallback.call(this, newParamValPairs, newQueryString, filterParamsString);
    },

    changeFilterCallback : null,

    getValidComparesFromForm : function(formIndex, newParamValPairs)
    {
        var obj = document.getElementById("compare_" + formIndex);
        var comparison = obj.options[obj.selectedIndex].value;
        var component = formIndex==1 ? this._valueComponent1 : this._valueComponent2;
        var compareTo = component.getValue();
        //alert("comparison: " + comparison + ", compareTo: " + compareTo);
        if (comparison != "")
        {
            var pair;
            if (comparison == "isblank" || comparison == "isnonblank" || comparison == "nomvvalue" || comparison == "hasmvvalue")
            {
                pair = [this._tableName + "." + this._fieldName + "~" + comparison];
            }
            else
            {
                var validCompareTo;
                if (comparison == 'in')
                {
                    validCompareTo = this.validateMultiple(compareTo);
                }
                else
                {
                    validCompareTo = this.validate(compareTo);
                }

                if (validCompareTo == undefined)
                    return false;
                pair = [this._tableName + "." + this._fieldName + "~" + comparison, validCompareTo];
            }
            newParamValPairs[newParamValPairs.length] = pair;
        }
        return true;
    },

    getValidCompares : function()
    {
        var newParamValPairs = new Array(0);

        var success = this.getValidComparesFromForm(1, newParamValPairs);
        if (!success)
        {
            return null;
        }
        success = this.getValidComparesFromForm(2, newParamValPairs);
        if (!success)
        {
            return null;
        }
        return newParamValPairs;
    },

    validateMultiple : function(allValues, mappedType, fieldName)
    {
        if (!mappedType) mappedType = _mappedType;
        if (!fieldName) fieldName = this._fieldCaption || this._fieldName;

        if (Ext.isEmpty(allValues))
        {
            alert("filter value for field '" + fieldName + "' cannot be empty.");
            return undefined;
        }
        var values = allValues.split(";");
        var result = '';
        var separator = '';
        for (var i = 0; i < values.length; i++)
        {
            var value = this.validate(values[i].trim(), mappedType, fieldName);
            if (value == undefined)
                return undefined;

            result = result + separator + value;
            separator = ";";
        }
        return result;
    },

    validate : function(value, mappedType, fieldName)
    {
        if (!mappedType) mappedType = this._mappedType;
        if (!fieldName) fieldName = this._fieldCaption || this._fieldName;

        if (Ext.isEmpty(value))
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
    },

    twoDigit : function(num)
    {
        if (num < 10)
            return "0" + num;
        else
            return "" + num;
    },

    clearSort : function(tableName, columnName)
    {
        if(!tableName || !columnName)
            return;

        var dr = LABKEY.DataRegions[tableName];
        if (!dr)
            return;
        dr.clearSort(columnName);
    },


    handleKey : function(event)
    {
        switch (event.keyCode)
        {
            case 13: // enter
                this.doFilter();
                break;

            case 27: // esc
                this.hideFilterDiv();
                break;
        }
    }
};


function showFilterPanel(dataRegionName, colName, caption, dataType, mvEnabled, queryString, dialogTitle, confirmCallback)
{
    LABKEY.DataRegion._filterUI.showFilterPanel(dataRegionName, colName, caption, dataType, mvEnabled, queryString, dialogTitle, confirmCallback);
}


// If at least one checkbox on the form is selected then GET/POST url.  Otherwise, display an error.
function verifySelected(form, url, method, pluralNoun, pluralConfirmText, singularConfirmText)
{
    var checked = 0;
    var elems = form.elements;
    var l = elems.length;

    for (var i = 0; i < l; i++)
    {
        var e = elems[i];

        if (e.type == 'checkbox' && e.checked && e.name == '.select')
        {
            checked++;
        }
    }

    if (checked > 0)
    {
        if ((window.parent == window) && (null != pluralConfirmText))
        {
            var confirmText = (1 == checked && null != singularConfirmText ? singularConfirmText : pluralConfirmText);

            if (!window.confirm(confirmText.replace("${selectedCount}", checked)))
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


function doSort(tableName, columnName, sortDirection)
{
    if (!tableName || !columnName)
        return;

    var dr = LABKEY.DataRegions[tableName];
    if (!dr)
        return;
    dr.changeSort(columnName, sortDirection);
}
