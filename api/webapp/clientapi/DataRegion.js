/**
* @fileOverview
* @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
* @license Copyright (c) 2008-2012 LabKey Corporation
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

Ext.ns('LABKEY.ext');

/**
 * The DataRegion constructor is private - to get a LABKEY.DataRegion object,
 * use <code>Ext.ComponentMgr.get(<em>&lt;dataregionname&gt;</em>)</code> or <code>Ext.ComponentMgr.onAvailable(<em>&lt;dataregionname&gt;</em>, callback)</code>.
 * @class The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
 * @constructor
 */
LABKEY.DataRegion = Ext.extend(Ext.Component,
/** @lends LABKEY.DataRegion.prototype */
{
    constructor : function (config)
    {
        this.config = config || {};

        /**
         * Config Options
         *  name       - Name of the DataRegion. Should be unique within a given page. Read-only. This will also be used
         *               as the Id.
         *  schemaName - Schema name of the query to which this DataRegion is bound. Read-only.
         *  queryName  - Name of the query to which this DataRegion is bound. Read-only.
         *  viewName   - Name of the custom view to which this DataRegion is bound, may be blank. Read-only.
         *  view
         *  sortFilter
         *  complete
         *  offset     - Starting offset of the rows to be displayed. 0 if at the beginning of the results. Read-only.
         *  maxRows    - Maximum number of rows to be displayed. 0 if the count is not limited. Read-only.
         *  totalRows  - (may be undefined)
         *  rowCount   - (may be undefined)
         *  showRows
         *  showRecordSelectors
         *  showInitialSelectMessage
         *  selectionKey - Unique string used to associate the selected items with this DataRegion, schema, query, and view.
         *  selectorCols
         *  requestURL
         */
        Ext.apply(this, config, {
            viewName : "",
            offset   : 0,
            maxRows  : 0
        });

        /**
         * Non-Configurable Options
         *  selectionModified
         *  currentPanelButton  - The button for the ribbon panel that we're currently showing
         *  panelButtonContents - All of the different ribbon panels that have been constructed for this data region
         *  allowHeaderLock     - A partially configurable option that allows for lockable headers on scrolling. Only
         *                        includes "modern browsers" as of 9.8.2011
         */
        Ext.apply(this, {
            selectionModified  : false,
            currentPanelButton : null,
            panelButtonContents: []
        });

        this.id = this.name;
        this._allowHeaderLock = this.allowHeaderLock && (Ext.isIE9 || Ext.isWebKit || Ext.isGecko);

        LABKEY.DataRegions[this.name] = this;

        this.addEvents(
                /**
                 * @memberOf LABKEY.DataRegion#
                 * @name selectchange
                 * @event
                 * @description Fires when the selection has changed.
                 * @param {LABKEY.DataRegion} dataRegion this DataRegion object.
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
                "beforesetparameters",
                "buttonclick",
                "afterpanelhide",
                "afterpanelshow",
                /**
                 * @memberOf LABKEY.DataRegion#
                 * @name beforerefresh
                 * @event
                 * @description Fires when a refresh of the DataRegion has been requested. If no handler consumes the event,
                 * the whole page will be reloaded.
                 * @param {LABKEY.DataRegion} dataRegion this DataRegion object.
                 */
                "beforerefresh"
        );

        this._initElements();
        this._showPagination(this.header);
        this._showPagination(this.footer);

        if (this.view && this.view.session)
        {
            var msg;
            if (this.view.savable)
            {
                msg = (this.viewName ? "The current view '<em>" + Ext.util.Format.htmlEncode(this.viewName) + "</em>'" : "The current <em>&lt;default&gt;</em> view") + " is unsaved.";
                msg += " &nbsp;";
                msg += "<span class='labkey-button unsavedview-revert'>Revert</span>";
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-edit'>Edit</span>";
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-save'>Save</span>";
            }
            else
            {
                msg = ("The current view has been customized.");
                msg += " &nbsp;";
                msg += "<span class='labkey-button unsavedview-revert' title='Revert'>Revert</span>";
                msg += ", &nbsp;";
                msg += "<span class='labkey-button unsavedview-edit'>Edit</span>";
            }

            // add the customize view message, the link handlers will get added after render in _onRenderMessageArea
            var el = this.addMessage(msg, 'customizeview');
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

        this.rendered = true; // prevent Ext.Component.render() from doing anything
        LABKEY.DataRegion.superclass.constructor.call(this, config);
    },

    /**
     * Set the parameterized query values for this query.  These parameters
     * are named by the query itself.
     * @param {Mixed} params An Object or Array or Array key/val pairs.
     */
    setParameters : function (params)
    {
        if (false === this.fireEvent("beforesetparameters", this, params))
            return;

        // convert Object into Array of Array pairs and prefix the parameter name if necessary.
        if (Ext.isObject(params))
        {
            var values = params,
                params = [];
            for (var key in values)
            {
                if (key.indexOf(this.name + ".param.") !== 0)
                    key = this.name + ".param." + key;
                params.push([key, values[key]]);
            }
        }

        this._setParams(params, [".offset", ".param."]);
    },

    /**
     * Get the parameterized query values for this query.  These parameters
     * are named by the query itself.
     * @param {booelan} lowercase If true, all parameter names will be converted to lowercase
     * returns params An Object of key/val pairs.
     */
    getParameters : function (lowercase)
    {
        var results = {};

        var paramValPairs = this.getParamValPairs(null, null);
        var re = new RegExp('^' + Ext.escapeRe(this.name) + '\.param\.', 'i');
        var name;
        Ext.each(paramValPairs, function(pair){
            if(pair[0].match(re)){
                name = pair[0].replace(re, '');
                if(lowercase)
                    name = name.toLowerCase();
                results[name] = pair[1];
            }
        }, this);

        return results;
    },

    /**
     * Changes the current row offset for paged content
     * @param newoffset row index that should be at the top of the grid
     */
    setOffset : function (newoffset)
    {
        if (false === this.fireEvent("beforeoffsetchange", this, newoffset))
            return;

        this._setParam(".offset", newoffset, [".offset", ".showRows"]);
    },

    /**
     * Changes the maximum number of rows that the grid will display at one time
     * @param newmax the maximum number of rows to be shown
     */
    setMaxRows : function (newmax)
    {
        if (false === this.fireEvent("beforemaxrowschange", this, newmax))
            return;

        this._setParam(".maxRows", newmax, [".offset", ".maxRows", ".showRows"]);
    },

    /**
     * Refreshes the grid, via AJAX if loaded through a QueryWebPart, and via a page reload otherwise.
     */
    refresh : function ()
    {
        if (false === this.fireEvent("beforerefresh", this))
            return;

        window.location.reload(false);
    },

    /**
     * Forces the grid to do paging based on the current maximum number of rows
     */
    showPaged : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, null))
            return;

        this._removeParams([".showRows"]);
    },

    /**
     * Looks for a column based on fieldKey, name, or caption (in that order)
     */
    getColumn : function (columnIdentifier)
    {
        if (this.columns)
        {
            var i;
            var column;
            for (i = 0; i < this.columns.length; i++)
            {
                column = this.columns[i];
                if (column.fieldKey && column.fieldKey == columnIdentifier)
                {
                    return column;
                }
            }
            for (i = 0; i < this.columns.length; i++)
            {
                column = this.columns[i];
                if (column.name && column.name == columnIdentifier)
                {
                    return column;
                }
            }
            for (i = 0; i < this.columns.length; i++)
            {
                column = this.columns[i];
                if (column.caption && column.caption == columnIdentifier)
                {
                    return column;
                }
            }
        }

        return null;
    },

    /**
     * Forces the grid to show all rows, without any paging
     */
    showAll : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "all"))
            return;

        this._setParam(".showRows", "all", [".offset", ".maxRows", ".showRows"]);
    },

    /**
     * Forces the grid to show only rows that have been selected
     */
    showSelected : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "selected"))
            return;

        this._setParam(".showRows", "selected", [".offset", ".maxRows", ".showRows"]);
    },

    /**
     * Forces the grid to show only rows that have not been selected
     */
    showUnselected : function ()
    {
        if (false === this.fireEvent("beforeshowrowschange", this, "unselected"))
            return;

        this._setParam(".showRows", "unselected", [".offset", ".maxRows", ".showRows"]);
    },

    /** Displays the first page of the grid */
    pageFirst : function ()
    {
        this.setOffset(0);
    },

    selectRow : function (el)
    {
        this.setSelected({ids: [el.value], checked: el.checked});
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
            this.removeMessage('selection');
            this.onSelectChange(this.hasSelected());
        }
    },

    /**
     * Get selected items on the current page of the DataRegion.  Selected items may exist on other pages.
     * @see LABKEY.DataRegion#getSelected
     */
    getChecked : function ()
    {
        return getCheckedValues(this.form, '.select');
    },

    /**
     * Get all selected items for this DataRegion.
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
     *
     * @see LABKEY.DataRegion.getSelected static method.
     */
    getSelected : function (config)
    {
        if (!this.selectionKey)
            return;

        config = config || { };
        config.selectionKey = this.selectionKey;
        LABKEY.DataRegion.getSelected(config);
    },

    /**
     * Add or remove items from the selection associated with the this DataRegion.
     *
     * @param config A configuration object with the following properties:
     * @param {Array} config.ids Array of primary key ids for each row to select/unselect.
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
     *
     * @see LABKEY.DataRegion#getSelected to get the selected items for this DataRegion.
     * @see LABKEY.DataRegion#clearSelected to clear all selected items for this DataRegion.
     */
    setSelected : function (config)
    {
        if (!this.selectionKey)
            return;

        if (arguments.length > 1)
        {
            config = {
                ids: arguments[0],
                checked: arguments[1],
                success: arguments[2]
            };
        }

        config = config || {};
        if (!config.ids || config.ids.length == 0)
            return;

        config.selectionKey = this.selectionKey;
        config.scope = config.scope || this;

        function failureCb(response, options) { this.addMessage("Error sending selection."); }
        config.failure = LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config) || failureCb, this, true);

        this.selectionModified = true;
        LABKEY.DataRegion.setSelected(config);
    },

    /**
     * Set the selection state for all checkboxes on the current page of the DataRegion.
     * @param checked whether all of the rows on the current page should be selected or unselected
     * @returns {Array} Array of ids that were selected or unselected.
     *
     * @see LABKEY.DataRegion#setSelected to set selected items on the current page of the DataRegion.
     * @see LABKEY.DataRegion#clearSelected to clear all selected.
     */
    selectPage : function (checked)
    {
        var ids = this._setAllCheckboxes(checked, '.select');
        if (ids.length > 0)
        {
            var toggle = this.form[".toggle"];
            if (toggle)
                toggle.checked = checked;
            this.onSelectChange(checked);
            this.setSelected({ids: ids, checked: checked, success: function (response, options) {
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
                    this.removeMessage('selection');
                }
            }});
        }
        return ids;
    },

    /**
     * Returns true if any row is checked on the current page of the DataRegion. Selected items may exist on other pages.
     * @returns {Boolean} true if any row is checked on the current page of the DataRegion.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
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

    /**
     * Returns true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @returns {Boolean} true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
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

    selectNone : function (config)
    {
        return this.clearSelected(config);
    },

    /**
     * Clear all selected items for the current DataRegion.
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
     *
     * @see LABKEY.DataRegion#selectPage
     * @see LABKEY.DataRegion.clearSelected static method.
     */
    clearSelected : function (config)
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
            this.removeMessage('selection');
        }
    },

    /**
     * Replaces the sort on the given column, if present, or sets a brand new sort
     * @param columnName name of the column to be sorted
     * @param sortDirection either "+' for ascending or '-' for descending
     */
    changeSort : function (columnName, sortDirection)
    {
        if (false === this.fireEvent("beforesortchange", this, columnName, sortDirection))
            return;

        var newSortString = this.alterSortString(this.getParameter(this.name + ".sort"), columnName, sortDirection);
        this._setParam(".sort", newSortString, [".sort", ".offset"]);
    },

    /**
     * Removes the sort on a specified column
     * @param columnName name of the column
     */
    clearSort : function (columnName)
    {
        if (!columnName)
            return;

        if (false === this.fireEvent("beforeclearsort", this, columnName))
            return;

        var newSortString = this.alterSortString(this.getParameter(this.name + ".sort"), columnName, null);
        if (newSortString.length > 0)
            this._setParam(".sort", newSortString, [".sort", ".offset"]);
        else
            this._removeParams([".sort", ".offset"]);
    },

    // private
    changeFilter : function (newParamValPairs, newQueryString)
    {
        if (false === this.fireEvent("beforefilterchange", this, newParamValPairs))
            return;

        this.setSearchString(this.name, newQueryString);
    },

    /**
     * Removes all the filters for a particular field
     * @param fieldName the name of the field from which all filters should be removed
     */
    clearFilter : function (fieldName)
    {
        if (false === this.fireEvent("beforeclearfilter", this, fieldName))
            return;
        this._removeParams(["." + fieldName + "~", ".offset"]);
    },

    /** Removes all filters from the DataRegion */
    clearAllFilters : function ()
    {
        if (false === this.fireEvent("beforeclearallfilters", this))
            return;
        this._removeParams([".", ".offset"]);
    },

    /**
     * Returns the user filter from the URL. The filter is represented as an Array of objects of the form:
     * <ul>
     *   <li><b>fieldKey</b>: {String} The field key of the filter.
     *   <li><b>op</b>: {String} The filter operator (eg. "eq" or "in")
     *   <li><b>value</b>: {String} Optional value to filter by.
     * </ul>
     * @returns {Object} Object representing the user filter.
     */
    getUserFilter : function ()
    {
        var userFilter = [];
        var paramValPairs = this.getParamValPairs(this.requestURL, null);
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
    },

    /**
     * Returns the user {@link LABKEY.Query.containerFilter} parameter from the URL.
     * @returns {LABKEY.Query.containerFilter} The user container filter.
     */
    getUserContainerFilter : function ()
    {
        return this.getParameter(this.name + ".containerFilterName");
    },

    /**
     * Returns the user sort from the URL. The sort is represented as an Array of objects of the form:
     * <ul>
     *   <li><b>fieldKey</b>: {String} The field key of the sort.
     *   <li><b>dir</b>: {String} The sort direction, either "+" or "-".
     * </ul>
     * @returns {Object} Object representing the user sort.
     */
    getUserSort : function ()
    {
        var userSort = [];
        var sortParam = this.getParameter(this.name + ".sort");
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
    },

    /**
     * Show a message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     * @param part
     * @return {Ext.Element} The Ext.Element of the newly created message div.
     */
    addMessage : function (html, part)
    {
        if (this.msgbox)
            this.msgbox.addMessage(html, part);
    },

    /**
     * Show a message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     * @return {Ext.Element} The Ext.Element of the newly created message div.
     * @deprecated use addMessage(html, msg) instead.
     */
    showMessage : function (html)
    {
        if (this.msgbox)
            this.msgbox.addMessage(html);
    },

    showMessageArea : function()
    {
        if (this.msgbox)
            this.msgbox.render();
    },

    /**
     * Show a message in the header of this DataRegion with a loading indicator.
     * @param html the HTML source of the message to be shown
     */
    showLoadingMessage : function (html)
    {
        html = html || "Loading...";
        this.addMessage("<div><span class='loading-indicator'>&nbsp;</span><em>" + html + "</em></div>");
    },

    /**
     * Show a success message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    showSuccessMessage : function (html)
    {
        html = html || "Completed successfully.";
        this.addMessage("<div class='labkey-message'>" + html + "</div>");
    },

    /**
     * Show an error message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    showErrorMessage : function (html)
    {
        html = html || "An error occurred.";
        this.addMessage("<div class='labkey-error'>" + html + "</div>");
    },

    /**
     * Returns true if a message is currently being shown for this DataRegion. Messages are shown as a header.
     * @return {Boolean} true if a message is showing.
     */
    isMessageShowing : function()
    {
        return this.msgbox && this.msgbox.isVisible();
    },

    /** If a message is currently showing, hide it and clear out its contents */
    hideMessage : function ()
    {
        if (this.msgbox)
        {
            this.msgbox.setVisible(false);
            this.msgbox.clear();
            this._resizeContainer(); // #13498
        }
    },

    /** If a message is currently showing, remove the specified part*/
    removeMessage : function (part)
    {
        if (this.msgbox)
        {
            this.msgbox.removeMessage(part);
        }
    },

    /** Clear the message box contents. */
    clearMessage : function ()
    {
        if (this.msgbox) this.msgbox.clear();
    },

    /**
     * Get the message area if it exists.
     * @return {LABKEY.DataRegion.MessageArea} The message area object.
     */
    getMessageArea : function()
    {
        return this.msgbox;
    },

    alterSortString : function(currentSortString, columnName, direction)
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
    },

    /**
     * Change the currently selected view to the named view
     * @param {Object} view An object which contains the following properties.
     * @param {String} [view.type] the type of view, either a 'view' or a 'report'.
     * @param {String} [view.viewName] If the type is 'view', then the name of the view.
     * @param {String} [view.reportId] If the type is 'report', then the report id.
     * @param {Object} urlParameters <b>NOTE: Experimental parameter; may change without warning.</b> A set of filter and sorts to apply as URL parameters when changing the view.
     */
    changeView : function(view, urlParameters)
    {
        if (false === this.fireEvent("beforechangeview", this, view, urlParameters))
            return;

        var skipPrefixes = [".offset", ".showRows", ".viewName", ".reportId"];
        var newParamValPairs = [];        
        if (view)
        {
            if (view.type == 'report')
                newParamValPairs.push([".reportId", view.reportId]);
            else if (view.type == 'view')
                newParamValPairs.push([".viewName", view.viewName]);
            else
                newParamValPairs.push([".viewName", view]);
        }

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
    },

    // private
    _initElements : function ()
    {
        this.form  = document.forms[this.name];
        this.table = Ext.get("dataregion_" + this.name);
        var msgEl  = Ext.get("dataregion_msgbox_" + this.name);
        if (msgEl)
        {
            this.msgbox = new LABKEY.MessageArea({parent: msgEl});
            this.msgbox.on('rendermsg', this._onRenderMessageArea, this);
        }
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
            else
            {
                this.updateRequiresSelectionButtons(false);
            }
        }

        if (this.headerLock()) {
            this._initHeaderLock();
        }
    },

    headerLock : function() {
        return this._allowHeaderLock === true;
    },

    _initHeaderLock : function() {
        // initialize constants
        this.headerRow          = Ext.get('dataregion_header_row_' + this.name);
        this.headerRowContent   = this.headerRow.child('td');
        this.headerSpacer       = Ext.get('dataregion_header_row_spacer_' + this.name);
        this.colHeaderRow       = Ext.get('dataregion_column_header_row_' + this.name);
        this.colHeaderRowSpacer = Ext.get('dataregion_column_header_row_spacer_' + this.name);

        // check if the header row is being used
        this.includeHeader = this.headerRow.isDisplayed();

        // initialize row contents
        // Check if we have colHeaderRow and colHeaderRowSpacer - they won't be present if there was an SQLException
        // during query execution, so we didn't get column metadata back
        if (this.colHeaderRow)
        {
            this.rowContent         = Ext.query(" > td[class*=labkey-column-header]",      this.colHeaderRow.id);
        }
        if (this.colHeaderRowSpacer)
        {
            this.rowSpacerContent   = Ext.query(" > td[class*=labkey-column-header]",      this.colHeaderRowSpacer.id);
        }
        this.firstRow           = Ext.query("tr[class=labkey-alternate-row]:first td", this.table.id);

        // If no data rows exist just turn off header locking
        if (this.firstRow.length == 0)
        {
            this.firstRow = Ext.query("tr[class=labkey-row]:first td", this.table.id);
            if (this.firstRow.length == 0)
            {
                this._allowHeaderLock = false;
                return;
            }
        }

        // performance degradation
        if (((this.rowContent.length > 40) && !Ext.isWebKit) || (this.rowCount && this.rowCount > 1000))
        {
            this._allowHeaderLock = false;
            return;
        }

        // initialize listeners
        Ext.EventManager.on(window,   'load',            this._resizeContainer, this, {single: true});
        Ext.EventManager.on(window,   'resize',          this._resizeContainer, this);
        Ext.EventManager.on(window,   'scroll',          this._scrollContainer, this);
        Ext.EventManager.on(document, 'DOMNodeInserted', this._resizeContainer, this); // Issue #13121

        // initialize panel listeners
        // 13669: customize view jumping when using drag/drop to reorder columns/filters/sorts
        // must manage DOMNodeInserted Listeners due to panels possibly dynamically adding elements to page
        this.on('afterpanelshow', function() {
            Ext.EventManager.un(document, 'DOMNodeInserted', this._resizeContainer, this); // suspend listener
            this._resizeContainer();
        }, this);

        this.on('afterpanelhide', function() {
            Ext.EventManager.on(document, 'DOMNodeInserted', this._resizeContainer, this); // resume listener
            this._resizeContainer();
        }, this);

        // initialize timer task for resizing and scrolling
        this.hdrCoord = [];
        this.resizeTask = new Ext.util.DelayedTask(function(){
            this._resetHeader();
            this._calculateHeader(true);
        }, this);

        this._calculateHeader(true);
    },

    _calculateHeader : function(recalcPosition) {
        this._calculateHeaderLock(recalcPosition);
        this._scrollContainer();
    },

    /**
     *
     */
    _calculateHeaderLock : function(recalcPosition) {
        var el, z, s, src;

        for (var i=0; i < this.rowContent.length; i++) {
            src = Ext.get(this.firstRow[i]);
            el  = Ext.get(this.rowContent[i]);

            s = {width: src.getWidth(), height: el.getHeight()}; // note: width coming from data row not header
            el.setSize(s);

            z   = Ext.get(this.rowSpacerContent[i]); // must be done after 'el' is set (ext side-effect?)
            z.setSize(s);
        }

        if (recalcPosition === true) this.hdrCoord = this._findPos();
        this.hdrLocked = false;
    },

    /**
     * Returns an array of containing the following values:
     * [0] - X-coordinate of the top of the object relative to the offset parent. See Ext.Element.getXY()
     * [1] - Y-coordinate of the top of the object relative to the offset parent. See Ext.Element.getXY()
     * [2] - Y-coordinate of the bottom of the object.
     * [3] - The height of the header for this Data Region. This includes the button bar if it is present.
     * This method assumes interaction with the Header of the Data Region.
     * @param o - The Ext.Element object to be measured againt that is considered the top of the Data Region.
     */
    _findPos : function() {
        var o, xy;

        if (this.includeHeader) {
            o = (this.hdrLocked ? this.headerSpacer : this.headerRow);
        }
        else {
            o = (this.hdrLocked ? this.colHeaderRowSpacer : this.colHeaderRow);
        }

        xy = o.getXY();
        var curbottom = xy[1] + this.table.getHeight();
        curbottom    -= o.getComputedHeight()*2;
        var hdrOffset = this.includeHeader ? this.headerSpacer.getComputedHeight() : 0;

        return [ xy[0], xy[1], curbottom, hdrOffset ];
    },

    /**
     * WARNING: This function is called often. Performance implications for each line.
     * NOTE: window.pageYOffset and pageXOffset are not available in IE7-. For these document.documentElement.scrollTop
     * and document.documentElement.scrollLeft could be used. Additionally, position: fixed is not recognized by
     * IE7- and can be best approximated with position: absolute and explicit top/left.
     */
    _scrollContainer : function() {
        // calculate Y scrolling
        if (window.pageYOffset >= this.hdrCoord[1] && window.pageYOffset < this.hdrCoord[2]) {
            // The header has reached the top of the window and needs to be locked
            var tWidth = this.table.getComputedWidth();
            this.headerSpacer.dom.style.display = "table-row";
            this.colHeaderRowSpacer.dom.style.display = "table-row";
            this.headerRow.applyStyles("top: 0; position: fixed; " +
                    "min-width: " + tWidth + "px; z-index: 9000;"); // 13229
            this.headerRowContent.applyStyles("min-width: " + (tWidth-3) + "px; ");
            this.colHeaderRow.applyStyles("position: fixed; background: white; top: " + this.hdrCoord[3] + "px;" +
                    "min-width: " + tWidth + "px; box-shadow: -2px 5px 5px #DCDCDC; z-index: 9000;"); // 13229
            this.hdrLocked = true;
        }
        else if (this.hdrLocked && window.pageYOffset >= this.hdrCoord[2]) {
            // The bottom of the Data Region is near the top of the window and the locked header
            // needs to start 'sliding' out of view.
            var top = this.hdrCoord[2]-window.pageYOffset;
            this.headerRow.applyStyles("top: " + top + "px;");
            this.colHeaderRow.applyStyles("top: " + (top + this.hdrCoord[3]) + "px;");
        }
        else if (this.hdrLocked) { // only reset if the header is locked
            // The header should not be locked
            this._resetHeader();
        }

        // Calculate X Scrolling
        if (this.hdrLocked) {
            this.headerRow.applyStyles("left: " + (this.hdrCoord[0]-window.pageXOffset) + "px;");
            this.colHeaderRow.applyStyles("left: " + (this.hdrCoord[0]-window.pageXOffset) + "px;");
        }
    },

    /**
     * Adjusts the header styling to the best approximate of what the defaults are when the header is not locked
     */
    _resetHeader : function() {
        this.hdrLocked = false;
        this.headerRow.applyStyles("top: auto; position: static; min-width: 0;");
        this.headerRowContent.applyStyles("min-width: 0;");
        this.colHeaderRow.applyStyles("top: auto; position: static; box-shadow: none; min-width: 0;");
        this.headerSpacer.dom.style.display = "none";
        this.headerSpacer.setHeight(this.headerRow.getHeight());
        this.colHeaderRowSpacer.dom.style.display = "none";
        this._calculateHeader();
    },

    // private
    _showPagination : function (el)
    {
        if (!el) return;
        var pagination = el.child("div[class='labkey-pagination']", true);
        if (pagination)
            pagination.style.visibility = "visible";
    },

    // private
    _resizeContainer : function ()
    {
        if (!this.table) return;

        if (this.headerLock()) {
            if (this.resizeTask) this.resizeTask.delay(110);
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

        var paramValPairs = this.getParamValPairs(this.requestURL, skipPrefixes);
        if (newParamValPairs)
        {
            for (var i = 0; i < newParamValPairs.length; i++)
            {
                var param = newParamValPairs[i][0],
                    value = newParamValPairs[i][1];
                if (null != param && null != value)
                {
                    if (param.indexOf(this.name) !== 0)
                        param = this.name + param;

                    paramValPairs[paramValPairs.length] = [param, value];
                }
            }
        }
        this.setSearchString(this.name, LABKEY.DataRegion.buildQueryString(paramValPairs));
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
    _showSelectMessage : function (msg)
    {
        if (this.showRecordSelectors)
        {
            msg += "&nbsp;<span class='labkey-button select-none'>Select None</span>";
            var showOpts = new Array();
            if (this.showRows != "all")
                showOpts.push("<span class='labkey-button show-all'>Show All</span>");
            if (this.showRows != "selected")
               showOpts.push("<span class='labkey-button show-selected'>Show Selected</span>");
            if (this.showRows != "unselected")
               showOpts.push("<span class='labkey-button show-unselected'>Show Unselected</span>");
            msg += "&nbsp;" + showOpts.join(" ");
        }

        // add the record selector message, the link handlers will get added after render in _onRenderMessageArea
        var el = this.addMessage(msg, 'selection');
    },

    // private
    /**
     * render listener for the message area, to add handlers for the link targets.
     */
    _onRenderMessageArea : function (cmp, partName, el)
    {
        if (this.showRecordSelectors && partName == 'selection' && el)
        {
            var selectNoneEl = el.child(".labkey-button.select-none");
            if (selectNoneEl)
                selectNoneEl.on('click', this.selectNone, this);

            var showAllEl = el.child(".labkey-button.show-all");
            if (showAllEl)
                showAllEl.on('click', this.showAll, this);

            var showSelectedEl = el.child(".labkey-button.show-selected");
            if (showSelectedEl)
                showSelectedEl.on('click', this.showSelected, this);

            var showUnselectedEl = el.child(".labkey-button.show-unselected");
            if (showUnselectedEl)
                showUnselectedEl.on('click', this.showUnselected, this);
        }
        else if (partName == 'customizeview' && el)
        {
            var revertEl = el.child(".labkey-button.unsavedview-revert");
            if (revertEl)
                revertEl.on('click', this.revertCustomView, this);

            var showCustomizeViewEl = el.child(".labkey-button.unsavedview-edit");
            if (showCustomizeViewEl)
                showCustomizeViewEl.on('click', function () { this.showCustomizeView(undefined, true); }, this);

            var saveEl = el.child(".labkey-button.unsavedview-save");
            if (saveEl)
                saveEl.on('click', this.saveSessionCustomView, this);
        }
    },

    // private
    updateRequiresSelectionButtons : function (hasSelected)
    {
        var fn = hasSelected ? LABKEY.Utils.enableButton : LABKEY.Utils.disableButton;

        // 10566: for javascript perf on IE stash the requires selection buttons
        if (!this._requiresSelectionButtons)
        {
            // escape ', ", and \
            var escaped = this.name.replace(/('|"|\\)/g, "\\$1");
            this._requiresSelectionButtons = Ext.DomQuery.select("a[labkey-requires-selection='" + escaped + "']");
        }
        Ext.each(this._requiresSelectionButtons, fn);

    },

    // private
    onSelectChange : function (hasSelected)
    {
        this.updateRequiresSelectionButtons(hasSelected);
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
        this._showButtonPanel(this.header, panelButton.getAttribute("panelId"), true, tabPanelConfig, panelButton);
    },

    _showButtonPanel : function(headerOrFooter, panelId, animate, tabPanelConfig, button)
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
                if (panelToHide && panelToHide.button)
                {
                    var buttonToHideElement = Ext.get(panelToHide.button);
                    if (buttonToHideElement)
                    {
                        // Remove the highlight from the button that opened up the panel, since the panel is being closed 
                        buttonToHideElement.removeClass('labkey-menu-button-active');
                    }
                    panelToHide.button = undefined;
                }
            }

            var _duration = 0.4, y, h;

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
                        var minWidth = 700;
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
                                    minWidth = Math.min(minWidth, tabContentWidth);
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

                    var buttonElement = Ext.get(button);
                    if (buttonElement)
                    {
                        // Highlight the button that opened up the panel, if it's directly on the button bar
                        buttonElement.addClass('labkey-menu-button-active');
                    }

                    this.panelButtonContents[panelId].button = button;
                    
                    this.currentPanelId = panelId;

                    // Slide it into place
                    var panelToShow = this.panelButtonContents[panelId];
                    panelToShow.setVisible(true);

                    if (this.headerLock()) {
                        y = this.colHeaderRow.getY();
                        h = this.headerSpacer.getHeight();
                    }

                    panelToShow.getEl().slideIn('t',{
                        callback : function() {
                            this.fireEvent('afterpanelshow');
                        },
                        concurrent : true,
                        duration   : _duration,
                        scope      : this
                    });

                    if (this.headerLock()) {
                        this.headerSpacer.setHeight(h+panelToShow.getHeight());
                        this.colHeaderRow.shift({y:(y+panelToShow.getHeight()), duration : _duration, concurrent: true, scope: this});
                    }

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
                if (this.headerLock()) {
                    y = this.colHeaderRow.getY();
                    h = this.headerSpacer.getHeight();
                }

                panelToHide.getEl().slideOut('t',{
                    callback: function() {
                        this.fireEvent('afterpanelhide');
                        callback.call(this);
                    },
                    concurrent : true,
                    duration   : _duration,
                    scope      : this
                });

                if (this.headerLock()) {
                    this.headerSpacer.setHeight(h-panelToHide.getHeight());
                    this.colHeaderRow.shift({y:(y-panelToHide.getHeight()), duration : _duration, concurrent: true, scope: this});
                }
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
                    schemaName : this.schemaName,
                    queryName  : this.queryName,
                    viewName   : viewName,
                    fields     : fields,
                    initializeMissingView : true,
                    success    : function (json, response, options) {
                        if (timerId > 0)
                            clearTimeout(timerId);
                        else
                            this.hideMessage();

                        var minWidth = Math.max(Math.min(1000, headerOrFooter.getWidth(true)), 700); // >= 700 && <= 1000
                        var renderTo = Ext.getBody().createChild({tag: "div", customizeView: true, style: {display: "none"}});

                        this.customizeView = new LABKEY.DataRegion.ViewDesigner({
                            renderTo    : renderTo,
                            width       : minWidth,
                            activeGroup : activeTab,
                            dataRegion  : this,
                            schemaName  : this.schemaName,
                            queryName   : this.queryName,
                            viewName    : viewName,
                            query       : json,
                            userFilter  : userFilter,
                            userSort    : userSort,
                            userContainerFilter       : this.getUserContainerFilter(),
                            allowableContainerFilters : this.allowableContainerFilters
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
        msg += " saved view";
        if (this.view && this.view.containerPath && this.containerPath != LABKEY.ActionURL.getContainer())
        {
            msg += " from '" + this.view.containerPath + "'";
        }
        msg += "?";
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

    // private
    _deleteCustomView : function (complete, message)
    {
        var timerId = function () {
            timerId = 0;
            this.showLoadingMessage(message);
        }.defer(500, this);

        Ext.Ajax.request({
            url      : LABKEY.ActionURL.buildURL("query", "deleteView"),
            jsonData : {schemaName: this.schemaName, queryName: this.queryName, viewName: this.viewName, complete: complete},
            method   : "POST",
            scope    : this,
            success  : LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                if (timerId > 0)
                    clearTimeout(timerId);
                this.showSuccessMessage();
                // change view to either a shadowed view or the default view
                var viewName = json.viewName;
                this.changeView({type:'view', viewName: viewName});
            }, this),
            failure  : LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                if (timerId > 0)
                    clearTimeout(timerId);
                this.showErrorMessage(json.exception);
            }, this, true)
        });
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
                canEdit: LABKEY.DataRegion._getCustomViewEditableErrors(config).length == 0,
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
                            self.changeView({type:'view', viewName:o.name});
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
            this.changeView({
                type: 'view',
                viewName:savedViewsInfo.views[0].name}, urlParameters);
        }
    },

    getParamValPairs : function(queryString, skipPrefixes)
    {
        if (!queryString)
        {
            queryString = this.getSearchString();
        }
        return LABKEY.DataRegion.getParamValPairsFromString(queryString, skipPrefixes);
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

    getSearchString : function()
    {
        if (null == this.savedSearchString)
            this.savedSearchString = document.location.search.substring(1) || "";
        return this.savedSearchString;
    },

    setSearchString : function(tableName, search)
    {
        this.savedSearchString = search || "";
        // If the search string doesn't change and there is a hash on the url, the page won't reload.
        // Remove the hash by setting the full path plus search string.
        window.location.assign(window.location.pathname + "?" + this.savedSearchString);
    }

});


/**
 * Static method to add or remove items from the selection for a given {@link #selectionKey}.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey See {@link #selectionKey}.
 * @param {Array} config.ids Array of primary key ids for each row to select/unselect.
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
 *
 * @see LABKEY.DataRegion#getSelected
 * @see LABKEY.DataRegion#clearSelected
 */
LABKEY.DataRegion.setSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "setSelected.api", config.containerPath,
        { 'key' : config.selectionKey, 'checked' : config.checked });
    var params = { id: config.ids || config.id };

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
 * Static method to clear all selected items for a given {@link #selectionKey}.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey See {@link #selectionKey}.
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
 *
 * @see LABKEY.DataRegion#setSelected
 * @see LABKEY.DataRegion#getSelected
 */
LABKEY.DataRegion.clearSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "clearSelected.api", config.containerPath,
        { 'key' : config.selectionKey });

    Ext.Ajax.request({ url: url });
};

/**
 * Static method to get all selected items for a given {@link #selectionKey}.
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey See {@link #selectionKey}.
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
 *
 * @see LABKEY.DataRegion#setSelected
 * @see LABKEY.DataRegion#clearSelected
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

// private
LABKEY.DataRegion._getCustomViewEditableErrors = function (view)
{
    var errors = [];
    if (view)
    {
        if (!view.editable)
            errors.push("The view is read-only and cannot be edited.");
    }
    return errors;
};

// private
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
        var targetContainers = config.targetContainers;
        var allowableContainerFilters = config.allowableContainerFilters;
        var containerFilterable = (allowableContainerFilters && allowableContainerFilters.length > 1);

        var containerData = new Array();
        if (targetContainers)
        {
            for (var i = 0; i < targetContainers.length; i++)
            {
                var targetContainer = targetContainers[i];
                containerData[i] = [targetContainers[i].path];
            }
        }
        else
        {
            // Assume view should be saved to current container
            containerData[0] = LABKEY.ActionURL.getContainer();
        }

        var containerStore = new Ext.data.ArrayStore({
            fields: [ 'path' ],
            data: containerData
        });

        var disableSharedAndInherit = LABKEY.user.isGuest || hidden /*|| session*/ || (containerPath && containerPath != LABKEY.ActionURL.getContainer());
        var newViewName = viewName || "New View";
        if (!canEdit && viewName)
            newViewName = viewName + " Copy";

        var warnedAboutMoving = false;

        var win = new Ext.Window({
            title: "Save Custom View" + (viewName ? ": " + Ext.util.Format.htmlEncode(viewName) : ""),
            cls: "extContainer",
            bodyStyle: "padding: 6px",
            modal: true,
            width: 480,
            height: 260,
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
                html: "<em>The current view is not editable.<br>Please enter an alternate view name.</em>",
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
                checked: containerFilterable && inherit,
                disabled: disableSharedAndInherit || !containerFilterable,
                hidden: !containerFilterable,
                listeners: {
                    check: function(checkbox, checked) {
                        Ext.ComponentMgr.get("saveCustomView_targetContainer").setDisabled(!checked);
                    }
                }
            },{
                ref: "targetContainer",
                xtype: "combo",
                name: "saveCustomView_targetContainer",
                id: "saveCustomView_targetContainer",
                fieldLabel: "Save in Folder",
                store: containerStore,
                value: config.containerPath,
                displayField: 'path',
                valueField: 'path',
                width: 300,
                triggerAction: 'all',
                mode: 'local',
                editable: false,
                hidden: !containerFilterable,
                disabled: disableSharedAndInherit || !containerFilterable,
                listeners: {
                    select: function(combobox) {
                        if (!warnedAboutMoving && combobox.getValue() != config.containerPath)
                        {
                            warnedAboutMoving = true;
                            Ext.Msg.alert("Moving a Saved View", "If you save, this view will be moved from '" + config.containerPath + "' to " + combobox.getValue());
                        }
                    }
                }
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
                            // Issue 13594: disallow setting inherit bit if query view has no available container filters
                            o.inherit = containerFilterable && win.inheritField.getValue();
                        }
                    }

                    if (o.inherit)
                    {
                        o.containerPath = win.targetContainer.getValue();
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



// private
LABKEY.DataRegion.getParamValPairsFromString = function(queryString, skipPrefixes)
{
    if (queryString && queryString.indexOf("?") > -1)
    {
        queryString = queryString.substring(queryString.indexOf("?") + 1);
    }

    var iNew = 0;
    var newParamValPairs = new Array(0);
    if (queryString != null && queryString.length > 0)
    {
        var paramValPairs = queryString.split("&");
        PARAM_LOOP: for (var i = 0; i < paramValPairs.length; i++)
        {
            var paramPair = paramValPairs[i].split("=", 2);
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

// private
LABKEY.DataRegion.buildQueryString = function(pairs)
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
};


// NOTE filter UI is shared, but I still don't like all these global/single instance variables

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

LABKEY.MessageArea = Ext.extend(Ext.util.Observable, {

    constructor : function(config)
    {
        this.parentEl = config.parent;
        this.parentEl.enableDisplayMode();

        this.parts = {};

        LABKEY.MessageArea.superclass.constructor.call(this, config);

        this.addEvents(
            /**
             * @event rendermsg
             * Fires after an individual message part is rendered.
             * @param {LABKEY.MessageArea} this
             * @param {String} the name of the message part
             * @param {Ext.Element} the rendered element
             */
            'rendermsg',
            'clearmsg'
        );
    },

    addMessage : function(msg, part) {

        part = part || 'info';
        this.parts[part] = msg;
        this.setVisible(true);
        this._refresh();
    },

    getMessage : function(part) {

        return this.parts[part];
    },

    removeMessage : function(part) {

        delete this.parts[part];
        this._refresh();
    },

    /**
     * Deletes all stored messages and clears the rendered area
     */
    removeAll : function() {

        this.parts = {};
        this._refresh();
    },

    render : function() {

        this.clear();
        var hasMsg = false;
        for (var name in this.parts)
        {
            var msg = this.parts[name];
            if (msg)
            {
                var div = this.parentEl.child("div");
                if (div.first())
                    div.createChild({tag: 'hr'});
                var el = div.createChild({tag: 'div', cls: 'labkey-dataregion-msg', html: msg});

                this.fireEvent('rendermsg', this, name, el);
                hasMsg = true;
            }
        }
        this.setVisible(hasMsg);
    },

    setVisible : function(visible) {

        this.parentEl.setVisible(visible, false);
    },
    
    isVisible : function() {

        return this.parentEl.isVisible();
    },

    /**
     * Clears the rendered DOM elements.
     */
    clear : function() {

        var div = this.parentEl.child("div");
        if (div)
            div.dom.innerHTML = "";
    },

    /**
     * private
     */
    _refresh : function() {

        if (this.isVisible())
        {
            this.clear();
            this.render();
        }
    }
});

// NOTE: a lot of this was copied direct from LABKEY._FilterUI.  I did some reworking to make it more ext-like,
// but there probably could be more done.

//ALSO: this is currently written by extending Ext.Window.  If we shift to left-hand filtering next to dataregions (like kayak), this could be
//refacted to a LABKEY.FilterPanel class, which does most of the work, and a FilterDialog class, which is a simple Ext.Window contianing a FilterPanel
//this would allow us to either render a dialog like now, or render a bunch of panels (one per field)
LABKEY.FilterDialog = Ext.extend(Ext.Window, {
    forceAdvancedFilters: false,  //provides a mechanism to hide the faceting UI
    initComponent: function(){
        this._fieldCaption = this.boundColumn.caption;
        this._fieldName = this.boundColumn.name;
        this._tableName = this.dataRegionName;
        this._mappedType = this.getMappedType(this.boundColumn.displayFieldSqlType ? this.boundColumn.displayFieldSqlType : this.boundColumn.sqlType);
        this.MAX_FILTER_CHOICES = 75;

        //determine the type of filter UI to show
        var dataRegion = LABKEY.DataRegions[this.dataRegionName];

        if (this.boundColumn.lookup && dataRegion && dataRegion.schemaName && dataRegion.queryName){
            if (this.boundColumn.displayField){
                //TODO: perhaps we could be smarter about resolving alternate fieldnames, like the value field, into the displayField?
                this._fieldName = this.boundColumn.displayField;
            }
        }

        this.filterType = this.getInitialFilterType();

        Ext.apply(this, {
            width: 410,
            //autoHeight: true,
            title: this.title || "Show Rows Where " + this.boundColumn.caption + "...",
            modal: true,
            resizable: false,
            closeAction: 'destroy',
            itemId: 'filterWindow',
            bodyStyle: 'padding: 5px;',
            defaults: this.itemDefaults,
            keys:[{
                 key:Ext.EventObject.ENTER,
                 scope: this,
                 handler: this.okHandler
            },{
                 key:Ext.EventObject.ESC,
                 scope: this,
                 handler: function(btn){
                     this.close();
                 }
            }],
            listeners: {
                scope: this,
                destroy: function(){
                    if(this.focusTask){
                        Ext.TaskMgr.stop(this.focusTask);
                    }
                }
            },
            buttons: [
                {text: 'OK', handler: this.okHandler, scope: this},
                {text: 'CANCEL', handler: this.cancelHandler, scope: this},
                {text: 'CLEAR FILTER', handler: this.clearFilter, scope: this},
                {text: 'CLEAR ALL FILTERS', handler: this.clearAllFilters, scope: this}
            ],
            items: [{
                xtype: 'radiogroup',
                style: 'padding-left: 5px;',
                itemId: 'filterType',
                columns: 1,
                hidden: this.filterType == 'default',
                items: [{
                    xtype: 'radio',
                    name: 'filterType',
                    inputValue: 'include',
                    checked: this.filterType == 'include',
                    boxLabel: 'Choose Values To Display'
                },{
                    xtype: 'radio',
                    name: 'filterType',
                    inputValue: 'default',
                    checked: this.filterType == 'default',
                    boxLabel: 'Advanced'
                }],
                listeners: {
                    scope: this,
                    change: function(bc, val){
                        if(val.inputValue != 'default' && !this.shouldShowLookupUI(val.inputValue)){
                            Ext.Msg.confirm('Confirm', 'This will cause one or more filters to be lost.  Are you sure you want to do this?', function(v){
                                if(v == 'yes'){
                                    this.filterType = val.inputValue;
                                    this.configurePanel();
                                    // Marker classes for tests
                                    this.removeClass('filterTestMarker-' + this.prevValue);
                                    this.addClass('filterTestMarker-' + val.inputValue);
                                    this.prevValue = val.inputValue;
                                }
                            }, this);
                        }
                        else {
                            this.filterType = val.inputValue;
                            this.configurePanel();
                            // Marker classes for tests
                            this.removeClass('filterTestMarker-' +  this.prevValue);
                            this.addClass('filterTestMarker-' + val.inputValue);
                            this.prevValue = val.inputValue;
                        }
                        this.syncShadow();
                    }
                }
            },{
                xtype: 'form',
                defaults: this.itemDefaults,
                style: 'padding: 5px;',
                itemId: 'filterArea',
                monitorValid: true,
                listeners: {
                    scope: this,
                    clientvalidation: function(form, val){
                        var btn = this.buttons[0];  //kinda fragile...
                        btn.setDisabled(!val);
                    }
                }
                //autoScroll: true,
                //boxMaxHeight: 200
            }]
        });

        LABKEY.FilterDialog.superclass.initComponent.call(this);

        //NOTE: we should just change the name of one of these
        if (!this.confirmCallback)
        {
            // Invoked as part of a regular filter dialog on a grid
            this.changeFilterCallback = this.changeFilter;
        }
        else
        {
            // Invoked from GWT, which will handle the commit itself
            this.changeFilterCallback = this.confirmCallback;
        }

        this.configurePanel();
    },

    itemDefaults: {
        border: false,
        msgTarget: 'under'
    },

    configurePanel: function(){
        var panel = this.find('itemId', 'filterArea')[0];

        //this seems to be an Ext3 bug.  ignoring since Ext4 will replace this soon enough
        //panel.removeAll();
        panel.items.each(function(item){
            item.destroy();
            panel.remove(item);
        }, this);

        var dataRegion = LABKEY.DataRegions[this.dataRegionName];

        if (!this.queryString){
            this.queryString = dataRegion ? dataRegion.requestURL : null;
        }

        var items = [];

        //identify and render the correct UI
        if(this.shouldShowLookupUI()){
            //start loading the store
            var store = this.getLookupStore();

            if(store.fields && store.fields.length){
                items.push(this.getLookupFilterPanel());
            }
            else {
                store.on('load', this.configurePanel, this);
                store.on('exception', function(){
                    this.forceAdvancedFilters = true;
                    this.configurePanel();
                }, this);
                items.push({
                    html: 'Loading...'
                });
            }
        }
        else
            items.push(this.getDefaultFilterPanel());

        panel.add(items);
        panel.doLayout();
        this.setValuesFromParams();
    },

    getInitialFilterType: function(){
        var filterType = 'default';
        var dataRegion = LABKEY.DataRegions[this.dataRegionName];
        if (this.boundColumn.lookup && dataRegion && dataRegion.schemaName && dataRegion.queryName )
        {
            if(!this.shouldShowLookupUI()){
                filterType = 'default';
            }
            else {
                var paramValPairs = this.getParamsForField(this._fieldName);

                if(!paramValPairs.length){
                    filterType = 'include';
                }
                else {
                    switch(paramValPairs[0].operator){
                        case 'notin':
                            filterType = 'include';
                            break;
                        default:
                            filterType = 'include';
                    }
                }
            }
        }
        return filterType;
    },

    shouldShowLookupUI: function(filterType){
        filterType = filterType || this.filterType;

        var paramValPairs = this.getParamsForField(this._fieldName);
        if (filterType == 'default')
            return false;

        if(paramValPairs.length > 1)
            return false;

        var shouldShow = true;
        Ext.each(paramValPairs, function(pair, idx){
            var filter = LABKEY.Filter.getFilterTypeForURLSuffix(pair.operator);

            if(!filter.isMultiValued() && !filter.getMultiValueFilter()){
                shouldShow = false;
                return;
            }

            if(filter.isMultiValued() && ['in', 'notin'].indexOf(filter.getURLSuffix()) == -1 ){
                shouldShow = false;
            }

        }, this);

        if(shouldShow){
            var store = this.getLookupStore();
            if(
                (!store || store.getCount() >= this.MAX_FILTER_CHOICES) ||
                (store && store.fields && store.getCount() === 0) || //Issue 13946: faceted filtering: loading mask doesn't go away if the lookup returns zero rows
                this.forceAdvancedFilters
            ){
                var field = this.findByType('radiogroup')[0];
                if(field)
                    field.hide();
                shouldShow = false;
                console.log('either too many for zero filter options, switching to default UI');
            }
        }

        if(this.forceAdvancedFilters)
            shouldShow = false;

        return shouldShow;
    },

    getDefaultFilterPanel: function(){
        // create a task to set the input focus that will get started after layout is complete, the task will
        // run for a max of 2000ms but will get stopped when the component receives focus
        this.focusTask = this.focusTask || {interval:150, run: function(){
            var field = this.find('itemId', 'inputField0')[0];
            field.focus(null,50);

            Ext.TaskMgr.stop(this.focusTask);
        }, scope: this, duration: 2000};

        var form = {
            autoWidth: true,
            defaults: {
                border: false
            },
            items: []
        };

        //NOTE: currently we always render 2 inputs, but in theory we could allow any number
        //it would be easy to give an 'Add Filter' button, which adds a new input pair
        form.items.push(this.getFilterInputPairConfig(2));

        return form;
    },

    getLookupFilterPanel: function(){
        var panel = {
            border: false,
            defaults: {
                border: false
            },
            //autoHeight: true,
            items: [{
                layout: 'hbox',
                style: 'padding-bottom: 5px;',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'Choose Items:',
                    style: 'padding-right: 15px;'
                }]
            }]
        };

        var filterConfig = this.getComboConfig(0);
        filterConfig.hidden = true;
        if(this.filterType == 'include'){
            filterConfig.value = 'in';
        }
        else{
            filterConfig.value = 'notin';
        }

        panel.items.push(filterConfig);
        panel.items.push({
            xtype: 'panel',
            autoScroll: true,
            height: 200,
            //autoHeight: true,
            bodyStyle: 'padding-left: 5px;',
            items: [{
                xtype: 'checkbox',
                name: 'toggleCheckbox',
                itemId: 'toggleCheckbox',
                boxLabel: 'Select All',
                toggleMode: 'all',
                checked: true,
                listeners: {
                    scope: this,
                    check: function(cb, value){
                        var window = cb.findParentBy(function(item){
                            return item.itemId == 'filterWindow';
                        });
                        var field = window.findByType('labkey-remotecheckboxgroup')[0];
                        if(field.getStore().getCount() == field.getValue().length){
                            cb.suspendEvents();
                            cb.setValue(false);
                            cb.resumeEvents();

                            field.suspendEvents();
                            field.selectNone();
                            field.resumeEvents();
                        }
                        else {
                            field.selectAll();
                        }
                    }
                }
            },{
                html: '',
                border: false,
                bodyStyle: 'padding-bottom: 3px;'
            },
                this.getCheckboxGroupConfig(0)
            ]
    });

        return panel;
    },

    getFilterCombos: function()
    {
        var re = /^filterComboBox/;
        return this.findBy(function(item){
            return item.itemId && re.test(item.itemId);
        });
    },

    getInputFields: function()
    {
        var re = /^inputField/;
        return this.findBy(function(item){
            return item.itemId && re.test(item.itemId);
        });
    },

    setValuesFromParams: function(){
        var paramValPairs = this.getParamsForField(this._fieldName);
        var combos = this.getFilterCombos();
        var inputFields = this.getInputFields();

        var filterIndex = 0;
        Ext.each(paramValPairs, function(pair, idx){
            var combo = combos[filterIndex];
            if(!combo)
                return;

            var input = inputFields[filterIndex];

            if(pair.operator){
                var filter = LABKEY.Filter.getFilterTypeForURLSuffix(pair.operator);

                //attempt to convert single-value filters into multi-value for faceting
                if(this.filterType != 'default' && !filter.isMultiValued() && filter.getMultiValueFilter()){
                    filter = filter.getMultiValueFilter();
                }

                if(this.filterType == 'include'){
                    if('notin' == pair.operator){
                        filter = filter.getOpposite();
                        pair.value = this.getInverse(this.getLookupStore(), pair.value);
                    }
                }

                if(this.filterType != 'default' && !filter.isMultiValued()){
                    console.log('skipping filter: ' + pair.operator);
                    return;
                }
                combo.setValue(filter.getURLSuffix());

                if(filter.isDataValueRequired())
                    input.enable();

                if(Ext.isDefined(pair.value)){
                    if(this.filterType == 'default'){
                        input.setValue(pair.value);
                    }
                    else {
                        if(filter.getURLSuffix() == 'in' && pair.value === ''){
                            input.defaultValue = true;
                            input.selectAll();  //select all by default
                        }
                        else {
                            var values = pair.value.split(';');
                            input.selectNone();
                            input.defaultValue = false;
                            input.setValue(values.join(','));
                        }
                    }
                }
            }
            else if(idx > 0) {
                combo.setValue("");
            }

            filterIndex++;
        }, this);
    },

    getInverse: function(store, values){
        var newValues = [];
        if(!Ext.isArray(values))
            values = values.split(';');

        var val;
        store.each(function(rec){
            val = rec.get('value');
            if(values.indexOf(val) == -1){
                newValues.push(val);
            }
        }, this);

        if(values.indexOf('') == -1 && newValues.indexOf('') == -1)
            newValues.push('');

        return newValues.join(';');
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
        "USERID":"INT",
        "VARCHAR2":"TEXT" // Oracle
    },

    //NOTE: i think we really should be able ot just change the values in TypeMap to match ext
    _extTypeMap: {
        'LONGTEXT': 'STRING',
        'TEXT': 'STRING',
        'INT': 'INT',
        'DECIMAL': 'FLOAT',
        'DATE': 'DATE',
        'BOOL': 'BOOL'
    },

    _mappedType : "TEXT",

    savedSearchString : null,

    changeFilterCallback : null,

    getSkipPrefixes: function()
    {
        return this._tableName + ".offset";
    },

    getXtype: function()
    {
        switch(this._mappedType){
            case "DATE":
                return "datefield";
            case "INT":
            case "DECIMAL":
                return "numberfield";
            case "BOOL":
                return 'labkey-booleantextfield';
            default:
                return "textfield";
        }
    },

    getFieldXtype: function(){
        var xtype = this.getXtype();
        if(xtype == 'numberfield')
            xtype = 'textfield';

        return xtype;
    },

    getCheckboxGroupConfig: function(idx)
    {
        var dataRegion = LABKEY.DataRegions[this.dataRegionName];

        return {
            xtype: 'labkey-remotecheckboxgroup',
            itemId: 'inputField' + (idx || 0),
            filterIndex: idx,
            msgTarget: 'title',
            lookupNullCaption: '[blank]',
            autoSelect: false,
            store: this.getLookupStore(),
            displayField: 'value',
            valueField: 'value',
            defaultValue: this.filterType == 'include',
            clearFilterOnReset: false,
            listeners: {
                scope: this,
                change: function(field, val){
                    var window = field.findParentBy(function(item){
                        return item.itemId == 'filterWindow';
                    });
                    var cb = window.find('itemId', 'toggleCheckbox')[0];

                    if(field.getStore().getCount() == val.length || val.length == 0){
                        cb.getEl().removeClass('x-item-disabled');
                    }
                    else {
                        cb.getEl().addClass('x-item-disabled');
                    }

                    cb.suspendEvents();
                    cb.setValue(val.length > 0);
                    cb.resumeEvents();
                }
            }
        }

    },

    getComboConfig: function(idx)
    {
        return {
            xtype: 'combo',
            itemId: 'filterComboBox' + idx,
            filterIndex: idx,
            listWidth: (this._mappedType == 'DATE' || this._mappedType == 'BOOL') ? null : 380,
            emptyText: idx === 0 ? 'Choose a filter:' : 'No other filter',
            autoSelect: false,
            width: 250,
            //allowBlank: 'false',
            triggerAction: 'all',
            fieldLabel: (idx === 0 ?'Filter Type' : 'and'),
            store: this.getComboStore(this.boundColumn.mvEnabled, this._mappedType, idx),
            displayField: 'text',
            valueField: 'value',
            typeAhead: 'false',
            forceSelection: true,
            mode: 'local',
            clearFilterOnReset: false,
            editable: false,
            value: idx === 0 ? this.getComboDefaultValue() : '',
            listeners:{
                scope: this,
                select: function(combo){
                    var idx = combo.filterIndex;
                    var inputField = this.find('itemId', 'inputField'+idx)[0];

                    var filter = LABKEY.Filter.getFilterTypeForURLSuffix(combo.getValue());
                    var selectedValue = filter ? filter.getURLSuffix() : '';

                    var re = /^filterComboBox/;
                    var combos = this.getFilterCombos();
                    var inputFields = this.getInputFields();

                    if(filter && !filter.isDataValueRequired()){
                        //Disable the field and allow it to be blank for values 'isblank' and 'isnonblank'.
                        inputField.disable();
                        inputField.setValue();
                    }
                    else {
                        inputField.enable();
                        inputFields[idx].validate();
                        inputField.focus('', 50)
                    }

                    //if the value is null, this indicates no filter chosen.  if it lacks an operator (ie. isBlank)
                    //in either case, this means we should disable all other filters
                    if(selectedValue == '' || !filter.isDataValueRequired()){
                        //Disable all subsequent combos
                        Ext.each(combos, function(combo, idx){
                            //we enable the next combo in the series
                            if(combo.filterIndex == this.filterIndex + 1){
                                combo.setValue();
                                inputFields[idx].setValue();
                                inputFields[idx].enable();
                                inputFields[idx].validate();
                            }
                            else if (combo.filterIndex > this.filterIndex){
                                combo.setValue();
                                inputFields[idx].disable();
                            }

                        }, this);
                    }
                    else{
                        //enable the other filterComboBoxes.
                        combos = this.findBy(function(item){
                            return item.itemId && item.itemId.match(/filterComboBox/) && item.filterIndex > (combo.filterIndex + 1);
                        }, this);
                        Ext.each(combos, function(combo, idx){
                            combo.enable();
                            combo.enable();
                        }, this);

                        if(combos.length){
                            combos[0].focus('', 50);
                        }
                    }
                },
                //enable/disable the input based on the
                disable: function(combo)
                {
                    var input = combo.findParentByType('panel').find('itemId', 'inputField'+combo.filterIndex)[0];
                    input.disable();
                },
                enable: function(combo)
                {
                    var input = combo.findParentByType('panel').find('itemId', 'inputField'+combo.filterIndex)[0];
                    var filter = LABKEY.Filter.getFilterTypeForURLSuffix(combo.getValue());

                    if(filter && filter.isDataValueRequired())
                        input.enable();
                }
            },
            scope: this
        }
    },

    getComboDefaultValue: function()
    {
        //afterRender of combobox we set the default value.
        if(this._mappedType == 'LONGTEXT' || this._mappedType == 'TEXT'){
            return 'startswith';
        }
        else if(this._mappedType == 'DATE'){
            return 'dateeq';
        }
        else{
            return 'eq';
        }
    },

    getInputFieldConfig: function(idx)
    {
        idx = idx || 0;
        var config = {
            xtype         : this.getFieldXtype(),
            itemId        : 'inputField' + idx,
            //msgTarget     : 'under',
            filterIndex   : idx,
            id            : 'value_'+(idx + 1),   //for compatibility with tests...
            //allowBlank    : false,
            width         : 250,
            blankText     : 'You must enter a value.',
            validateOnBlur: false,
            disabled      : idx !== 0,
            value         : null,
            listeners     : {
                scope     : this,
                disable   : function(field){
                    //Call validate after disable so any pre-existing validation errors go away.
                    if(field.rendered)
                        field.validate();
                },
                focus: function(){
                    Ext.TaskMgr.stop(this.focusTask);
                }
            },
            validator: function(value){
                var window = this.findParentBy(function(item){
                    return item.itemId == 'filterWindow';
                });

                var idx = this.filterIndex;
                var combo = window.find('itemId', 'filterComboBox'+idx)[0];

                return window.inputFieldValidator(this, combo)
            }
        };

        if(this._mappedType == "DATE")
            config.altFormats = LABKEY.Utils.getDateAltFormats();

        if(idx === 0){
            config.listeners.afterrender = function(cmp){
                if(this.focusTask)
                    Ext.TaskMgr.start(this.focusTask);
            }
        }
        return config;

    },

    getNextFilterIdx: function(){
        return this.getFilterCombos().length;
    },

    getFilterInputPairConfig: function(quantity)
    {
        var idx = this.getNextFilterIdx();
        var items = [];

        for(var i=0;i<quantity;i++){
            var combo = this.getComboConfig(idx);
            var input = this.getInputFieldConfig(idx);
            items.push({
                xtype: 'panel',
                layout: 'form',
                itemId: 'filterPair' + idx,
                border: false,
                defaults: {
                    border: false,
                    msgTarget: 'under'
                },
                items: [combo, input]
            });
            idx++;
        }
        return items;
    },

    okHandler: function(btn)
    {
        if(!this.findByType('form')[0].getForm().isValid())
            return;

        var inputFields = this.getInputFields();
        var combos = this.getFilterCombos();

        //Step 1: validate
        var isValid = true;
        var filters = [];
        Ext.each(combos, function(c, idx){
            if(!c.isValid()){
                isValid = false;
                return false;
            }
            else {
                var value;
                var input = inputFields[idx];
                if(input instanceof LABKEY.ext.RemoteCheckboxGroup){
                    var cbks = input.getValue();
                    var values = [];
                    Ext.each(cbks, function(cb){
                        values.push(cb.inputValue);
                    }, this);

                    if(values.indexOf('') != -1 && values.length == 1)
                        values.push(''); //account for null-only filtering

                    value = values.join(';');
                }
                else
                    value = input.getValue();

                var filter = LABKEY.Filter.getFilterTypeForURLSuffix(c.getValue());

                if(!filter){
                    alert('filter not found: ' + c.getValue());
                    return;  //'No Other Filter'
                }

                if(Ext.isEmpty(value) && filter.isDataValueRequired()){
                    if(this.filterType == 'default'){
                        input.markInvalid('You must enter a value');
                        isValid = false;
                        return false;
                    }
                    else {
                        return;
                    }
                }

                var filterArray = [filter.getURLSuffix(), value];
                if(this.filterType != 'default'){
                    var allowable = [];
                    this.getLookupStore().each(function(rec){
                        allowable.push(rec.get('value'))
                    });
                    filterArray = this.optimizeFilter(filter, value, allowable);
                }

                if(filterArray)
                    filters.push(filterArray);
            }
        }, this);

        if(isValid){
            this.setFilter(filters);
            this.close();
        }
    },

    optimizeFilter: function(filter, value, allowableValues){
        value = value.split(';');
        if(filter.isMultiValued() && allowableValues){
            //determine if we should invert filter
            if(Ext.unique(value).length > (allowableValues.length / 2)){
                var newValues = [];
                filter = filter.getOpposite();
                Ext.each(allowableValues, function(item){
                    if(value.indexOf(item) == -1){
                        newValues.push(item);
                    }
                }, this);
                value = newValues;
            }

            //NOTE: removed b/c null makes this tricky
            //switch to single filter option correctly
//            if(value.length == 1){
//                filter = filter.getSingleValueFilter();
//            }
        }

        //b/c empty strings will be ignored, we add a second to force a delimiter.
        if(value.length == 1 && value[0] == '')
            value.push('');

        //if the value is blank, do not apply this empty filter
        if(value.length != 0 || !filter.isDataValueRequired())
            return [filter.getURLSuffix(), value.join(';')]
    },

    cancelHandler: function()
    {
        this.close();
    },

    getMappedType : function(dataType)
    {
        var mappedType = this._typeMap[dataType.toUpperCase()];
        if (mappedType == undefined)
            mappedType = dataType.toUpperCase();
        return mappedType;
    },

    getComboStore : function(mvEnabled, mappedType, storeNum)
    {
        var fields      = ['text', 'value', {name: 'isMulti', type: Ext.data.Types.BOOL}, 'mappedType', {name: 'isOperatorOnly', type: Ext.data.Types.BOOL}];
        var store       = new Ext.data.ArrayStore({
            fields: fields,
            idIndex: 1
        });
        var comboRecord = Ext.data.Record.create(fields);

        if(storeNum == 0){
            store.add(new comboRecord({text:'Has Any Value', value: ''}));
        } else{
            store.add(new comboRecord({text:'No Other Filter', value: ''}));
        }

        if (mappedType != "LONGTEXT")
        {
            if(mappedType == "DATE"){
                store.add(new comboRecord({text:'Equals', value: 'dateeq'}))
            } else {
                store.add(new comboRecord({text:'Equals', value: 'eq'}));
            }

            if(mappedType == "DATE"){
                store.add(new comboRecord({text:'Does Not Equal', value: 'dateneq'}));
            } else {
                store.add(new comboRecord({text:'Does Not Equal', value: 'neqornull'}));
            }

            if (mappedType != "BOOL" && mappedType != "DATE"){
                store.add(new comboRecord({text:"Equals One Of (e.g. \"a;b;c\")", value: 'in', isMulti: true}));
                store.add(new comboRecord({text:"Does Not Equal Any Of (e.g. \"a;b;c\")", value: 'notin', isMulti: true}));
            }
        }

        if (mappedType != "LONGTEXT" && mappedType != "BOOL")
        {
            if(mappedType == "DATE"){
                store.add(new comboRecord({text:'Is Greater Than',             value: 'dategt'}));
                store.add(new comboRecord({text:'Is Less Than',                value: 'datelt'}));
                store.add(new comboRecord({text:'Is Greater Than Or Equal To', value: 'dategte'}));
                store.add(new comboRecord({text:'Is Less Than Or Equal To',    value: 'datelte'}));
            } else {
                store.add(new comboRecord({text:'Is Greater Than',             value: 'gt'}));
                store.add(new comboRecord({text:'Is Less Than',                value: 'lt'}));
                store.add(new comboRecord({text:'Is Greater Than Or Equal To', value: 'gte'}));
                store.add(new comboRecord({text:'Is Less Than Or Equal To',    value: 'lte'}));
            }
        }

        if (mappedType == "TEXT" || mappedType == "LONGTEXT")
        {
            store.add(new comboRecord({text:'Starts With',         value: 'startswith'}));
            store.add(new comboRecord({text:'Does Not Start With', value: 'doesnotstartwith'}));
            store.add(new comboRecord({text:'Contains',            value: 'contains'}));
            store.add(new comboRecord({text:'Does Not Contain',    value: 'doesnotcontain'}));
            store.add(new comboRecord({text:"Contains One Of (e.g. \"a;b;c\")", value: 'containsoneof', isMulti: true}));
            store.add(new comboRecord({text:"Does Not Contain Any Of (e.g. \"a;b;c\")", value: 'containsnoneof', isMulti: true}));
        }

        //All mappedTypes will have these:
        store.add(new comboRecord({text:'Is Blank',     value: 'isblank', isOperatorOnly: true}));
        store.add(new comboRecord({text:'Is Not Blank', value: 'isnonblank', isOperatorOnly: true}));

        if (mvEnabled)
        {
            store.add(new comboRecord({text:'Has A Missing Value Indicator',           value: 'hasmvvalue', isOperatorOnly: true}));
            store.add(new comboRecord({text:'Does Not Have A Missing Value Indicator', value: 'nomvvalue', isOperatorOnly: true}));
        }

        store.each(function(rec){
            rec.set('mappedType', mappedType);
        }, this);

        return store;
    },

    getSearchString : function()
    {
        if (null == this.savedSearchString)
            this.savedSearchString = document.location.search.substring(1) || "";
        return this.savedSearchString;
    },

    clearFilter : function()
    {
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.clearFilter(this._fieldName);
        this.close();
    },

    clearAllFilters : function()
    {
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.clearAllFilters();
        this.close();
    },

    changeFilter : function(newParamValPairs, newQueryString)
    {
        var dr = LABKEY.DataRegions[this._tableName];
        if (!dr)
            return;
        dr.changeFilter(newParamValPairs, newQueryString);
        this.close();
    },

    setFilter: function(filters)
    {
        //This is a replacement for doFilter. Will probably be renamed to doFilter.
        //input1 and input2 have already been validated, no need to do that here.
        //We do however need to modify the date if it's not in the proper format, and parse ints/floats.

        var queryString = LABKEY.DataRegions[this._tableName] ? LABKEY.DataRegions[this._tableName].requestURL : null;
        var newParamValPairs = this.getParamValPairs(queryString, [this._tableName + "." + this._fieldName + "~", this.getSkipPrefixes()]);
        var comparisons = new Array(0);

        Ext.each(filters, function(filter){
            if(filter[0] !=''){
                comparisons.push(this.getCompares(filter[1], filter[0]));
                newParamValPairs.push(this.getCompares(filter[1], filter[0]));
            }
        }, this);

        var newQueryString = LABKEY.DataRegion.buildQueryString(newParamValPairs);
        var filterParamsString = LABKEY.DataRegion.buildQueryString(comparisons);

        this.changeFilterCallback.call(this, newParamValPairs, newQueryString, filterParamsString);
    },

    getCompares: function(input, comparison)
    {
        //Used to be getValidComparesFromForm, but since we validate before setting a filter we got rid of the validation here.
        var pair;
        if (comparison == "isblank" || comparison == "isnonblank" || comparison == "nomvvalue" || comparison == "hasmvvalue")
        {
            pair = [this._tableName + "." + this._fieldName + "~" + comparison];
        } else{
            pair = [this._tableName + "." + this._fieldName + "~" + comparison, input];
        }
        return pair;
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

    hideFilterPanel : function ()
    {
        this.close();
    },

    getLookupStore : function()
    {
        var dataRegion = LABKEY.DataRegions[this.dataRegionName];
        var storeId = [dataRegion.schemaName, dataRegion.queryName, this._fieldName].join('||');
        var column = this.boundColumn;

        if(Ext.StoreMgr.get(storeId)){
            return Ext.StoreMgr.get(storeId);
        }

        var store = Ext.StoreMgr.add(new LABKEY.ext.Store({
            schemaName: dataRegion.schemaName,
            sql: this.getLookupValueSql(dataRegion, column),
            storeId: storeId,
            //TODO: add sort on client??
            sort: "value",
            containerPath: dataRegion.container || dataRegion.containerPath || LABKEY.container.path,
            maxRows: this.MAX_FILTER_CHOICES, // Limit so that we don't overwhelm the user (or the browser itself) with too many checkboxes
            includeTotalCount: false,  // Don't bother getting the total row count, which might involve another query to the database
            containerFilter: dataRegion.containerFilter,
            autoLoad: true
        }));

        return store;
    },

    getLookupValueSql: function(dataRegion, column)
    {
        // Build up a SELECT DISTINCT query to get all of the values that are currently in use
        //NOTE: empty string will be treated as NULL, which is b/c Ext checkboxes can be set to empty string, but not null
        var sql = 'SELECT CASE WHEN value IS NULL then \'\' ELSE cast(value as varchar) END as value FROM (';
        sql += 'SELECT DISTINCT t.';
        for (var i = 0; i < column.fieldKeyArray.length; i++)
        {
            sql += "\"" + column.fieldKeyArray[i].replace("\"", "\"\"") + "\".";
        }
        sql += "\"" + column.lookup.displayColumn.replace("\"", "\"\"") + "\"";
        sql += ' AS value FROM "' + dataRegion.schemaName.replace("\"", "\"\"") + '"."' + dataRegion.queryName.replace("\"", "\"\"") + '" t';
        sql += ') s';

        return sql;
    },

    getParamValPairs : function(queryString, skipPrefixes)
    {
        if (!queryString)
        {
            queryString = this.getSearchString();
        }
        return LABKEY.DataRegion.getParamValPairsFromString(queryString, skipPrefixes);
    },

    getParamsForField: function(fieldName)
    {
        var dataRegion = LABKEY.DataRegions[this._tableName];
//        if(!dataRegion)
//            return;

        if (!this.queryString)
        {
            this.queryString = dataRegion ? dataRegion.requestURL : null;
        }

        var paramValPairs = LABKEY.DataRegion.getParamValPairsFromString(this.queryString, [this.getSkipPrefixes()]); //this._tableName + "." + this._fieldName + "~",

        var results = [];
        var re = new RegExp('^' + Ext.escapeRe(this._tableName) + '\.' + fieldName, 'i');
        Ext.each(paramValPairs, function(pair){
            if(pair[0].match(re)){
                var operator = pair[0].split('~')[1];
                if(LABKEY.Filter.getFilterTypeForURLSuffix(operator))
                    results.push({
                        operator: operator,
                        value: pair[1]
                    });
                else
                    console.log('Unrecognized filter: ' + operator)
            }
        }, this);

        return results;
    },

    inputFieldValidator: function(input, cb)
    {
        var rec = cb.getStore().getAt(cb.getStore().find('value', cb.getValue()));
        var filter = LABKEY.Filter.getFilterTypeForURLSuffix(cb.getValue());

        if(rec){
            if(filter.isMultiValued()){
                return this.validateEqOneOf(input.getValue(), rec.get('mappedType'));
            }

            return this.validateInputField(input.getValue(), rec.get('mappedType'));
        }
        return true;
    },

    validateEqOneOf: function(input, mappedType)
    {
        // Used when "Equals One Of.." is selected. Calls validateInputField on each value entered.
        var values = input.split(';');
        var isValid = "";
        for(var i = 0; i < values.length; i++){
            isValid = this.validateInputField(values[i], mappedType);
            if(isValid !== true){
                return isValid;
            }
        }
        //If we make it out of the for loop we had no errors.
        return true;
    },

    //The fact that Ext3 ties validation to the editor is a little funny, but using this shifts the work to Ext
    validateInputField: function(value, mappedType){
        //the reason for this change is to try to shift more of the burden from our code into Ext
        var type = this._extTypeMap[this._mappedType];
        if(type){
            var field = new Ext.data.Field({
                type: Ext.data.Types[type],
                allowDecimals :  this._mappedType != "INT",  //will be ignored by anything besides numberfield
                useNull: true
            });

            var convertedVal = field.convert(value);
            if(!Ext.isEmpty(value) && value != convertedVal){
                return "Invalid value: " + value;
            }
        }
        else {
            console.log('Unrecognized type: ' + this._mappedType);
        }

        return true;
    }

});


/**
 * Contructs a CheckboxGroup where each radio is populated from a LabKey store.
 * This is an alternative to a combobox.
 * @class
 * @augments Ext.form.CheckboxGroup
 * @param {object} config The configuation object.  Will accept all config options from Ext.form.CheckboxGroup along with those listed here.
 * @param {object} [config.store] A LABKEY.ext.Store.  Each record will be used to
 * @param {string} [config.valueField] The name of the field to use as the inputValue
 * @param {string} [config.displayField] The name of the field to use as the label
 */
LABKEY.ext.RemoteCheckboxGroup = Ext.extend(Ext.form.CheckboxGroup,
{
    separator: ';',
    defaultValue: false,
    initComponent: function()
    {
        Ext.QuickTips.init();

        Ext.apply(this, {
            name: this.name || Ext.id(),
            storeLoaded: false,
            items: [
                {name: 'placeholder', fieldLabel: 'Loading..'}
            ],
            buffered: true,
//            data: {
//                other: 't'
//            },
            tpl : new Ext.XTemplate('<tpl for=".">' +
                '<span '+'{[values["qtip"] ? "ext:qtip=\'" + values["qtip"] + "\'" : ""]}>' +
//                    '<span>' +
                '{[values["' + this.valueField + '"] ? values["' + this.displayField + '"] : "'+ (this.lookupNullCaption ? this.lookupNullCaption : '[none]') +'"]}' +
                //allow a flag to display both display and value fields
                '<tpl if="'+this.showValueInList+'">{[values["' + this.valueField + '"] ? " ("+values["' + this.valueField + '"]+")" : ""]}</tpl>'+
                '</span>' +
                '</tpl>').compile()
        });

        if(this.value){
            this.value = [this.value];
        }

        LABKEY.ext.RemoteCheckboxGroup.superclass.initComponent.call(this, arguments);

        //we need to test whether the store has been created
        if(!this.store){
            console.log('LABKEY.ext.RemoteCheckboxGroup requires a store');
            return;
        }

        if(this.store && !this.store.events){
            this.store = Ext.create(this.store, 'labkey-store');
        }

        if(!this.store.getCount()) {
            this.store.on('load', this.onStoreLoad, this, {single: true});
            this.store.on('exception', this.onStoreException, this, {single: true});
        }
        else {
            //NOTE: if this is called too quickly, the layout can be screwed up. this isnt a great fix, but we will convert to Ext4 shortly, so it'll change anyway
            this.onStoreLoad.defer(10, this);
        }
    }

    ,onStoreException : function() {
        //remove the placeholder checkbox
        if(this.rendered) {
            var item = this.items.first();
            this.items.remove(item);
            this.panel.getComponent(0).remove(item, true);
            this.ownerCt.doLayout();
        }
        else
            this.items.remove(this.items[0]);
    }

    ,onStoreLoad : function() {
        var item;
        this.store.each(function(record, idx){
            item = this.newItem(record);

            if(this.rendered){
                this.items.add(item);
                var col = (idx+this.columns.length) % this.columns.length;
                var chk = this.panel.getComponent(col).add(item);
                this.fireEvent('add', this, chk);
            }
            else {
                this.items.push(item)
            }
        }, this);

        //remove the placeholder checkbox
        if(this.rendered) {
            var item = this.items.first();
            this.items.remove(item);
            this.panel.getComponent(0).remove(item, true);
            this.ownerCt.doLayout();
        }
        else
            this.items.remove(this.items[0]);

        this.storeLoaded = true;
        this.buffered = false;

        if(this.bufferedValue){
            this.setValue(this.bufferedValue);
        }
    }
    ,newItem: function(record){
        return new Ext.form.Checkbox({
            xtype: 'checkbox',
            boxLabel: (this.tpl ? this.tpl.apply(record.data) : record.get(this.displayField)),
            inputValue: record.get(this.valueField),
            name: record.get(this.valueField),
            disabled: this.disabled,
            readOnly: this.readOnly || false,
            itemId: record.get(this.valueField),
            checked: this.defaultValue,
            listeners: {
                scope: this,
                change: function(self, val){
                    this.fireEvent('change', this, this.getValue());
                },
                check: function(self, val){
                    this.fireEvent('change', this, this.getValue());
                },
                afterrender: function(field){
                    var id = field.wrap.query('label.x-form-cb-label')[0];

                    var tooltip = new Ext.ToolTip({
                        xtype: 'tooltip',
                        target: id,
                        items: [{
                            html: '<a>Click to select only: ' + field.boxLabel + '</a>',
                            itemId: 'link',
                            border: false
                        }],
                        listeners: {
                            scope: this,
                            render: function(panel){
                                panel.getEl().on('click', function(){
                                    this.selectNone();
                                    this.setValue(field.inputValue);
                                }, this);
                            }
                        },
                        hideDelay: 800,
                        trackMouse: false,
                        dismissDelay: 2000,
                        mouseOffset: [1,1]
                    }) ;
                }
            }
        });

    }
    ,setValue: function(v)
    {
        //NOTE: we need to account for an initial value if store not loaded.
        if(!this.storeLoaded){
            //this.mon(this.store, 'load', this.setValue.createDelegate(this, arguments), null, {single: true, delay: 50});
            this.buffered = true;
            this.bufferedValue = v;
        }
        else {
            LABKEY.ext.RemoteCheckboxGroup.superclass.setValue.apply(this, arguments);
        }
    }
    ,setReadOnly : function(readOnly){
        LABKEY.ext.RemoteCheckboxGroup.superclass.setReadOnly.apply(this, arguments);
        this.setDisabled(readOnly);
    }
    ,getStore: function(){
        return this.store;
    }
    ,getStringValue: function(){
        var value = [];
        Ext.each(this.getValue(), function(item){
            value.push(item.inputValue);
        }, this);
        return value.join(this.separator);
    }
    ,selectAll: function(){
        if(this.rendered)
            this.items.each(function(item){
                item.setValue(true);
            });
        else
            this.defaultValue = true;
    }
    ,selectNone: function(){
        if(this.rendered){
            this.items.each(function(item){
                item.setValue(false);
            });
            this.defaultValue = false;
        }
        else {
            this.setValue([]);
            this.defaultValue = false;
        }
    }
});
Ext.reg('labkey-remotecheckboxgroup', LABKEY.ext.RemoteCheckboxGroup);



LABKEY.ext.BooleanTextField = Ext.extend(Ext.form.TextField,
{
    initComponent: function()
    {
        Ext.apply(this, {
            validator: function(val){
                if(!val)
                    return true;

                return LABKEY.Utils.isBoolean(val) ? true : val + " is not a valid boolean. Try true/false; yes/no; on/off; or 1/0.";
            }
        });
        LABKEY.ext.BooleanTextField.superclass.initComponent.call(this);
    }
});
Ext.reg('labkey-booleantextfield', LABKEY.ext.BooleanTextField);