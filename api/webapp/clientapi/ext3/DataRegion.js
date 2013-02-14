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
 * @class LABKEY.DataRegion
 * @extends Ext.Component
 * The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
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
//        this._ensureFaceting();

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

        LABKEY.DataRegion.superclass.constructor.call(this, config);
    },

    // private
    beforeDestroy : function ()
    {
        this.doDestroy();
        LABKEY.DataRegion.superclass.beforeDestroy.call(this);
    },

    doDestroy : function()
    {
        if (this.headerLock())
        {
            Ext.EventManager.un(window, 'load', this._resizeContainer, this);
            Ext.EventManager.un(window, 'resize', this._resizeContainer, this);
            Ext.EventManager.un(window, 'scroll', this._scrollContainer, this);
            Ext.EventManager.un(document, 'DOMNodeInserted', this._resizeContainer, this);
            if (this.resizeTask) {
                this.resizeTask.cancel();
                delete this.resizeTask;
            }
        }

        if (this.msgbox)
        {
            this.msgbox.destroy();
            delete this.msgbox;
        }

        if (this.customizeView)
        {
            this.customizeView.destroy();
            delete this.customizeView;
        }
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

    _ensureFaceting : function() {
        if (LABKEY.ActionURL.getParameter('showFacet')) {
            if (!LABKEY.dataregion || !LABKEY.dataregion.panel ||
                !LABKEY.dataregion.panel.Facet.LOADED) {
                this.showFaceting();
            }
        }
        else {
            this.loadFaceting();
        }
    },

    loadFaceting : function(cb, scope) {

        var dr = this;

        var initFacet = function() {

            dr.facetLoaded = true;

            if (cb) { cb.call(scope); }
        };

        LABKEY.requiresExt4Sandbox();  // Ext 4 might not be present
        LABKEY.requiresScript([
            '/study/ReportFilterPanel.js',
            '/study/ParticipantFilterPanel.js',
            '/dataregion/panel/Facet.js'
        ], true, initFacet);
    },

    showFaceting : function() {
        if (this.facetLoaded) {
            if (!this.facet) {
                this.facet = Ext4.create('LABKEY.dataregion.panel.Facet', {
                    dataRegion : this
                })
            }
            this.facet.toggleCollapse();
            if (this.resizeTask) {
                this.resizeTask.delay(350);
            }
        }
        else {
            this.loadFaceting(this.showFaceting, this);
        }
    },

    setFacet : function(facet) {
        this.facet = facet;
        this.facetLoaded = true;
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
            this.onSelectChange(this.getSelectionCount());
        }
        else
        {
            if (toggle)
                toggle.checked = false;
            this.removeMessage('selection');
            this.onSelectChange(this.getSelectionCount());
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
            this.onSelectChange(this.getSelectionCount());
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
                    if (this.totalRows)
                    {
                        if (count == this.totalRows)
                            msg = "Selected all " + this.totalRows + " rows.";
                        else
                            msg = "Selected " + count + " of " + this.totalRows + " rows.";
                    }
                    else
                    {
                        // totalRows isn't available when showing all rows.
                        msg = "Selected " + count + " rows.";
                    }
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
        return this.getSelectionCount() > 0;
    },

    /**
     * Returns the number of selected rows on the current page of the DataRegion. Selected items may exist on other pages.
     * @returns {Integer} the number of selected rows on the current page of the DataRegion.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    getSelectionCount : function ()
    {
        if (!this.table)
            return 0;
        var selectionCount = 0;
        var checkboxes = Ext.query('input[@type="checkbox"][@name=".select"]', this.table.dom);
        var len = checkboxes ? checkboxes.length : 0;
        for (var i = 0; i < len; i++)
        {
            if (checkboxes[i].checked)
                selectionCount++;
        }
        return selectionCount;
    },

    /**
     * Returns true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @returns {Boolean} true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    isPageSelected : function ()
    {
        if (!this.table)
            return false;

        var checkboxes = Ext.query('input[@type="checkbox"][@name=".select"]', this.table.dom);
        var len = checkboxes ? checkboxes.length : 0;
        var hasCheckbox = len > 0;
        for (var i = 0; i < len; i++)
        {
            if (!checkboxes[i].checked)
                return false;
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

        this.onSelectChange(0);

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
     * @param {string or LABKEY.FieldKey} fieldKey name of the column to be sorted
     * @param sortDirection either "+' for ascending or '-' for descending
     */
    changeSort : function (fieldKey, sortDirection)
    {
        if (!fieldKey)
            return;

        if (!(fieldKey instanceof LABKEY.FieldKey))
            fieldKey = LABKEY.FieldKey.fromString(""+fieldKey);

        var columnName = fieldKey.toString();
        if (false === this.fireEvent("beforesortchange", this, columnName, sortDirection))
            return;

        var newSortString = this.alterSortString(this.getParameter(this.name + ".sort"), fieldKey, sortDirection);
        this._setParam(".sort", newSortString, [".sort", ".offset"]);
    },

    /**
     * Removes the sort on a specified column
     * @param {string or LABKEY.FieldKey} fieldKey name of the column
     */
    clearSort : function (fieldKey)
    {
        if (!fieldKey)
            return;

        if (!(fieldKey instanceof LABKEY.FieldKey))
            fieldKey = LABKEY.FieldKey.fromString(""+fieldKey);

        var columnName = fieldKey.toString();
        if (false === this.fireEvent("beforeclearsort", this, columnName))
            return;

        var newSortString = this.alterSortString(this.getParameter(this.name + ".sort"), fieldKey, null);
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
     * @param {string or FieldKey} fieldKey the name of the field from which all filters should be removed
     */
    clearFilter : function (fieldKey)
    {
        if (!fieldKey)
            return;

        if (!(fieldKey instanceof LABKEY.FieldKey))
            fieldKey = LABKEY.FieldKey.fromString(""+fieldKey);

        var columnName = fieldKey.toString();
        if (false === this.fireEvent("beforeclearfilter", this, columnName))
            return;
        this._removeParams(["." + columnName + "~", ".offset"]);
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
     * @deprecated 12.2 Use getUserFilterArray instead
     */
    getUserFilter : function ()
    {
        var userFilter = [];
        var filters = this.getUserFilterArray();

        for (var i=0; i < filters.length; i++)
        {
            var filter = filters[i];
            userFilter.push({
                fieldKey : filter.getColumnName(),
                op       : filter.getFilterType().getURLSuffix(),
                value    : filter.getValue()
            });
        }
        return userFilter;
    },

    getUserFilterArray : function()
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
                var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);

                var value = pair[1];

                userFilter.push(LABKEY.Filter.create(fieldKey, value, filterType));
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

    /**
     * @private
     * @param currentSortString
     * @param fieldKey FieldKey or FieldKey encoded string.
     * @param direction
     */
    alterSortString : function(currentSortString, fieldKey, direction)
    {
        if (!(fieldKey instanceof LABKEY.FieldKey))
            fieldKey = LABKEY.FieldKey.fromString(""+fieldKey);

        var columnName = fieldKey.toString();

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
                    this.onSelectChange(this.getSelectionCount());
                }
                else
                {
                    this.onSelectChange(this.getSelectionCount());
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

        this.rendered = true; // prevent Ext.Component.render() from doing anything
        this.el = this.form && Ext.get(this.form) || this.table;
    },

    headerLock : function() {
        return this._allowHeaderLock === true;
    },

    disableHeaderLock : function() {
        if (this.headerLock())
        {
            this._allowHeaderLock = false;
            this.resizeTask.cancel();

            Ext.EventManager.un(window, 'load', this._resizeContainer, this);
            Ext.EventManager.un(window, 'resize', this._resizeContainer, this);
            Ext.EventManager.un(window, 'scroll', this._scrollContainer, this);
            Ext.EventManager.un(document, 'DOMNodeInserted', this._resizeContainer, this);
            delete this.resizeTask;
        }
    },

    _initHeaderLock : function() {
        // initialize constants
        this.headerRow          = Ext.get('dataregion_header_row_' + this.name);
        if (!this.headerRow) {
            console.log('header locking has been disabled on ' + this.name);
            this._allowHeaderLock = false;
            return;
        }
        this.headerRowContent   = this.headerRow.child('td');
        this.headerSpacer       = Ext.get('dataregion_header_row_spacer_' + this.name);
        this.colHeaderRow       = Ext.get('dataregion_column_header_row_' + this.name);
        this.colHeaderRowSpacer = Ext.get('dataregion_column_header_row_spacer_' + this.name);
        this.paginationEl       = Ext.get('dataregion_header_' + this.name);

        Ext.EventManager.on(window,   'resize', this._resizeContainer, this);
        this.ensurePaginationVisible();

        // check if the header row is being used
        this.includeHeader = this.headerRow.isDisplayed();

        // initialize row contents
        // Check if we have colHeaderRow and colHeaderRowSpacer - they won't be present if there was an SQLException
        // during query execution, so we didn't get column metadata back
        if (this.colHeaderRow)
        {
            this.rowContent = Ext.query(" > td[class*=labkey-column-header]", this.colHeaderRow.id);
        }
        if (this.colHeaderRowSpacer)
        {
            this.rowSpacerContent = Ext.query(" > td[class*=labkey-column-header]", this.colHeaderRowSpacer.id);
        }
        this.firstRow = Ext.query("tr[class=labkey-alternate-row]:first td", this.table.id);

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
        if (this.rowContent.length > 80 || ((this.rowContent.length > 40) && !Ext.isWebKit) || (this.rowCount && this.rowCount > 1000))
        {
            this._allowHeaderLock = false;
            return;
        }

        // initialize additional listeners
        Ext.EventManager.on(window,   'load',            this._resizeContainer, this, {single: true});
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
            this._resetHeader(true);
            this.ensurePaginationVisible();
        }, this);

        this._resetHeader(true);
    },

    ensurePaginationVisible : function() {

        if (this.paginationEl)
        {
            // in case header locking is not on
            if (!this.headerLock() || !this.hdrCoord || this.hdrCoord.length == 0)
            {
                this.hdrCoord = this._findPos();
            }

            var measure = Ext.getBody().getBox().width-this.hdrCoord[0];
            if (measure < (this.headerRow.getWidth()))
            {
                this.paginationEl.applyStyles('width: ' +  measure + 'px;');
            }
        }
    },

    _calculateHeader : function(recalcPosition) {
        this._calculateHeaderLock(recalcPosition);
        this._scrollContainer();
    },

    _calculateHeaderLock : function(recalcPosition) {
        var el, z, s, src, i;

        for (i=0; i < this.rowContent.length; i++) {
            src = Ext.get(this.firstRow[i]);
            el  = Ext.get(this.rowContent[i]);

            s = {width: src.getWidth(), height: el.getHeight()}; // note: width coming from data row not header
            el.setWidth(s.width); // 15420

            z = Ext.get(this.rowSpacerContent[i]); // must be done after 'el' is set (ext side-effect?)
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
        var o, xy, curbottom, hdrOffset=0;

        if (this.includeHeader) {
            o = (this.hdrLocked ? this.headerSpacer : this.headerRow);
            hdrOffset = this.headerSpacer.getComputedHeight();
        }
        else {
            o = (this.hdrLocked ? this.colHeaderRowSpacer : this.colHeaderRow);
        }

        xy = o.getXY();
        curbottom = xy[1] + this.table.getHeight() - (o.getComputedHeight()*2);

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
    _resetHeader : function(recalc) {
        this.hdrLocked = false;
        this.headerRow.applyStyles("top: auto; position: static; min-width: 0;");
        this.headerRowContent.applyStyles("min-width: 0;");
        this.colHeaderRow.applyStyles("top: auto; position: static; box-shadow: none; min-width: 0;");
        this.headerSpacer.dom.style.display = "none";
        this.headerSpacer.setHeight(this.headerRow.getHeight());
        this.colHeaderRowSpacer.dom.style.display = "none";
        this._calculateHeader(recalc);
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
        else {
            this.ensurePaginationVisible();
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
        for (var i = 0; i < skipPrefixes.length; i++)
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
        if (!this.table)
            return;

        var checkboxes = Ext.query('input[@type="checkbox"]', this.table.dom);
        var len = checkboxes ? checkboxes.length : 0;
        var ids = [];
        for (var i = 0; i < len; i++)
        {
            var e = checkboxes[i];
            if (!e.disabled && (elementName == null || elementName == e.name))
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
    updateRequiresSelectionButtons : function (selectionCount)
    {
//        var fn = selectionCount > 0 ? LABKEY.Utils.enableButton : LABKEY.Utils.disableButton;

        // 10566: for javascript perf on IE stash the requires selection buttons
        if (!this._requiresSelectionButtons)
        {
            // escape ', ", and \
            var escaped = this.name.replace(/('|"|\\)/g, "\\$1");
            this._requiresSelectionButtons = Ext.DomQuery.select("a[labkey-requires-selection='" + escaped + "']");
        }

        for (var i = 0; i < this._requiresSelectionButtons.length; i++)
        {
            var buttonElement = this._requiresSelectionButtons[i];
            var minCount = buttonElement.attributes['labkey-requires-selection-min-count'];
            if (minCount)
            {
                minCount = parseInt(minCount.value);
            }
            if (minCount === undefined)
            {
                minCount = 1;
            }
            var maxCount = buttonElement.attributes['labkey-requires-selection-max-count'];
            if (maxCount)
            {
                maxCount = parseInt(maxCount.value);
            }
            if (minCount <= selectionCount && (!maxCount || maxCount >= selectionCount))
            {
                LABKEY.Utils.enableButton(buttonElement);
            }
            else
            {
                LABKEY.Utils.disableButton(buttonElement);
            }
        }

//        Ext.each(this._requiresSelectionButtons, fn);
    },

    // private
    onSelectChange : function (selectionCount)
    {
        this.updateRequiresSelectionButtons(selectionCount);
        this.fireEvent('selectchange', this, selectionCount);
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

                        // If there was an error parsing the query, we won't be able to render the customize view panel.
                        if (json.exception) {
                            var viewSourceUrl = LABKEY.ActionURL.buildURL('query', 'viewQuerySource.view', null, {schemaName: this.schemaName, "query.queryName": this.queryName});
                            var msg = Ext.util.Format.htmlEncode(json.exception) +
                                    " &nbsp;<a target=_blank class='labkey-button' href='" + viewSourceUrl + "'>View Source</a>";

                            this.showErrorMessage(msg);
                            return;
                        }

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
                        Ext.Msg.progress("Saving...", "Saving custom view...");
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
                            win.close();
                            Ext.Msg.hide();
                            self.showSuccessMessage();
                            self.changeView({type:'view', viewName:o.name});
                        }, self),
                        failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                            if (timerId > 0)
                                clearTimeout(timerId);
                            Ext.Msg.hide();
                            Ext.Msg.alert("Error saving view", json.exception);
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
        // User can save this view if it is editable and the shadowed view is editable if present.
        var shadowedViewEditable = config.session && (!config.shadowed || config.shadowed.editable);
        var canEdit = config.canEdit && (!config.session || shadowedViewEditable);
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

        var disableSharedAndInherit = LABKEY.user.isGuest || hidden;
        var newViewName = viewName || "New View";
        if (!canEdit && viewName)
            newViewName = viewName + " Copy";

        var warnedAboutMoving = false;

        var win = new Ext.Window({
            title: "Save Custom View" + (viewName ? ": " + Ext.util.Format.htmlEncode(viewName) : ""),
            cls: "extContainer",
            bodyStyle: "padding: 6px",
            modal: true,
            width: 490,
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
                // Let the saveCustomView_name field display the error message otherwise it will render as "saveCustomView_name: error message"
                combineErrors: false,
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
                    msgTarget: "side",
                    allowBlank: false,
                    emptyText: "Name is required",
                    maxLength: 50,
                    width: 280,
                    autoCreate: {tag: 'input', type: 'text', size: '50'},
                    validator: function (value) {
                        if ("default" === value.trim())
                            return "The view name 'default' is not allowed";
                        return true;
                    },
                    selectOnFocus: true,
                    value: newViewName,
                    disabled: hidden || (canEdit && !viewName)
                }]
            },{
                xtype: "box",
                style: "padding-left: 122px; padding-bottom: 8px",
                html: "<em>The " + (!config.canEdit ? "current" : "shadowed") + " view is not editable.<br>Please enter an alternate view name.</em>",
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
                    if (!win.nameCompositeField.isValid())
                    {
                        Ext.Msg.alert("Invalid view name", "The view name must be less than 50 characters long and not 'default'.");
                        return;
                    }

                    var nameField = win.nameCompositeField.items.get(1);
                    if (!canEdit && viewName == nameField.getValue())
                    {
                        Ext.Msg.alert("Error saving", "This view is not editable.  You must save this view with an alternate name.");
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

                    // Callback is responsible for closing the save dialog window on success.
                    success.call(scope, win, o);
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


function doSort(tableName, fieldKey, sortDirection)
{
    if (!tableName || !fieldKey)
        return;

    var dr = LABKEY.DataRegions[tableName];
    if (!dr)
        return;
    dr.changeSort(fieldKey, sortDirection);
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

    // private
    destroy : function () {
        this.purgeListeners();
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

/**
 * If we shift to left-hand filtering next to dataregions (like kayak), this could be refacted to a LABKEY.FilterPanel
 * class, which does most of the work, and a FilterDialog class, which is a simple Ext.Window containing a
 * FilterPanel. This would allow us to either render a dialog like now, or a bunch of panerls (one per field)
 */
LABKEY.FilterDialog = Ext.extend(Ext.Window, {

    forceAdvancedFilters : false,  //provides a mechanism to hide the faceting UI

    itemDefaults: {
        border: false,
        msgTarget: 'under'
    },

    MAX_FILTER_CHOICES : 250, // 15565, Switch faceted limit to 250

    savedSearchString : null,

    validatorFn: Ext.emptyFn,

    initComponent : function()
    {
        Ext.QuickTips.init();

        var bound = this.boundColumn;

        if (!bound) {
            console.error('A boundColumn is required for LABKEY.FilterDialog');
            return;
        }

        Ext.apply(this, {

            // Either invoked from GWT, which will handle the commit itself.
            // Or invoked as part of a regular filter dialog on a grid
            changeFilterCallback : this.confirmCallback ? this.confirmCallback : this.changeFilter,

            filterType    : this.getInitialFilterType(),
            _fieldCaption : bound.caption,
            _fieldKey     : bound.fieldKey,
            _jsonType     : (bound.displayFieldJsonType ? bound.displayFieldJsonType : bound.jsonType) || 'string'
        });

        if (bound.lookup && bound.displayField) {
            // TODO: perhaps we could be smarter about resolving alternate fieldnames,
            // like the value field, into the displayField?
            this._fieldKey = this.boundColumn.displayField;
        }

        Ext.apply(this, {
            width: 410,
            autoHeight: true,
            title: this.title || "Show Rows Where " + this.boundColumn.caption + "...",
            modal: true,
            resizable: false,
            closeAction: 'destroy',
            itemId: 'filterWindow',
            cls: 'labkey-filter-dialog',
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
                destroy: function(){
                    if (this.focusTask) {
                        Ext.TaskMgr.stop(this.focusTask);
                    }
                },
                resize : function(panel) {
                    panel.syncShadow();
                },
                scope : this
            },
            bbarCfg : {
                bodyStyle : 'border-top: 1px solid black;'
            },
            buttons: [
                {text: 'OK', handler: this.okHandler, scope: this},
                {text: 'CANCEL', handler: this.cancelHandler, scope: this}
            ]
        });

        // 16684: Without a dataregion clearing filters doesn't make sense
        if (this.getDataRegion()) {
            this.buttons.push({text: 'CLEAR FILTER', handler: this.clearFilter, scope: this});
            this.buttons.push({text: 'CLEAR ALL FILTERS', handler: this.clearAllFilters, scope: this});
        }

        this.items = [this.getTabPanel()];

        LABKEY.FilterDialog.superclass.initComponent.call(this);

        // HACK: when creating the confirm MessageBox, this window was always sitting above it.
        // to get around, we make a WindowGroup and get the position slightly lower than the default
        this.windowGroup = new Ext.WindowGroup({
            zseed: 8999
        });
        this.windowGroup.register(this);

        this.facetingAvailable = undefined;

        this.prepareToShowPanel();
        this.preparePanelTask = new Ext.util.DelayedTask(this.prepareToShowPanel, this);
    },

    getTabPanel : function()
    {
        if (this.tabPanel) {
            return this.tabPanel;
        }

        if (this.isFacetingCandidate()) {

            this.tabPanel = new Ext.TabPanel({
                autoHeight: true,
                width     : this.width - 5,
                bodyStyle : 'padding: 5px;',
                activeTab : this.canShowFacetedUI() ? 1 : 0,
                border    : true,
                monitorValid : true,
                deferredRender : false,
                defaults : {
                    border : false,
                    msgTarget : 'under'
                },
                items : [this.getDefaultFilterPanel(), this.getFacetedFilterPanel()],
                listeners : {
                    beforetabchange : this.beforeTabChange,
                    tabchange : function() {
                        this.syncShadow();
                        this.tabPanel.getActiveTab().doLayout(); // required when facets return while on another tab
                    },
                    scope : this
                }
            });
        }
        else {
            var me = this;
            this.tabPanel = new Ext.Panel({
                autoHeight: true,
                width     : this.width - 5,
                bodyStyle : 'padding: 5px;',
                activeTab : 0,
                border    : true,
                monitorValid : true,
                deferredRender : false,
                defaults : {
                    border : false,
                    msgTarget : 'under'
                },
                items : [this.getDefaultFilterPanel(true)],
                getActiveTab : function() {
                    return me.getDefaultFilterPanel();
                },
                listeners : {
                    tabchange : function() {
                        this.syncShadow();
                        this.tabPanel.getActiveTab().doLayout(); // required when facets return while on another tab
                    },
                    scope : this
                }
            });
        }

        return this.tabPanel;
    },

    beforeTabChange : function(tp, newTab, oldTab) {
        if (newTab.filterType == 'default') {
            if (oldTab && oldTab.filterType == 'include') {
                var filter = [],
                    operator = 'in',
                    grid = Ext.getCmp(this.gridID);

                if (this.facetDirty) {
                    this.facetDirty = false;
                    var values = grid.getValues();
                    if (values.length > 0) {
                        if (values.length == 1)
                            operator = 'eq';
                        else if (values.length == values.max) {
                            values.values = '';
                            operator = 'startswith';
                        }
                        else if (values.length > Math.floor(values.max/2)) {
                            filter = LABKEY.Filter.getFilterTypeForURLSuffix(operator);
                            filter = filter.getOpposite();
                            values.values = this.getInverse(grid.getStore(), values.values);
                            operator = filter.getURLSuffix();

                            // check for single
                            if (values.values.split(';').length == 1) {
                                operator = 'neqornull';
                            }
                        }
                        filter = [{operator: operator, value : values.values}];
                    }
                    this.setValuesFromParams(newTab, filter);
                }
            }
        }
    },

    getDefaultFilterPanel : function(hideTitle)
    {
        if (this.defaultPanel)
            return this.defaultPanel;

        this.defaultPanel = new Ext.form.FormPanel({
            autoHeight : true,
            title : hideTitle ? false : 'Choose Filters',
            bodyStyle : 'padding: 5px;',
            filterType : 'default',
            bubbleEvents: ['add', 'remove', 'clientvalidation'],
            defaults : { border: false },
            items : this.getFilterInputPairConfig(2),
            listeners : {
                afterrender : function(p)
                {
                    var inputs = this.getInputFields(p);
                    if (inputs && inputs.length > 0) {

                        // create a task to set the input focus that will get started after layout is complete,
                        // the task will run for a max of 2000ms but will get stopped when the component receives focus
                        this.focusTask = this.focusTask || {interval:150, run: function(){
                            inputs[0].focus(null, 50);

                            Ext.TaskMgr.stop(this.focusTask);
                        }, scope: this, duration: 2000};
                    }
                },
                scope : this
            }
        });

        this.defaultPanel.on('afterlayout', function(p) {
            if (this.focusTask) {
                Ext.TaskMgr.start(this.focusTask);
            }
            this.setValuesFromParams(p, this.getFiltersFromParams(p.filterType));
        }, this, {single: true});

        return this.defaultPanel;
    },

    getFacetedFilterPanel : function()
    {
        if (this.facetPanel)
            return this.facetPanel;

        this.facetPanel = new Ext.form.FormPanel({
            title  : 'Choose Values',
            itemId : 'facetedPanel',
            filterType : 'include',
            border : false,
            height : 200,
            autoScroll: true,
            bubbleEvents: ['add', 'remove', 'clientvalidation'],
            defaults : {
                border : false
            },
            markDisabled : true,
            items: [{
                layout: 'hbox',
                style: 'padding-bottom: 5px;',
                width: 100,
                defaults: {
                    border: false
                },
                items: []
            }]
        });

        return this.facetPanel;
    },

    getDataRegion : function()
    {
        return LABKEY.DataRegions[this.dataRegionName];
    },

    /**
     * Some DataRegions can exist without a query/schema name (e.g. announcements). This function determines
     * whether the DataRegion is query-based.
     */
    isQueryDataRegion : function(dr) {
        return dr && dr.schemaName && dr.queryName;
    },

    isFacetingValid : function(store) {
        if (!store)
            return false;

        return !(store.getCount() > this.MAX_FILTER_CHOICES || (store.fields && store.getCount() === 0) || this.forceAdvancedFilters);
    },

    prepareToShowPanel : function()
    {
        if (this.facetingAvailable !== true && this.facetingAvailable !== false) {
            if(!this.isFacetingCandidate()){
                this.facetingAvailable = false;
                this.prepareToShowPanel();
            }
            else {
                //we need to load the store to determine if faceting is possible
                var _store = this.getLookupStore();

                this.facetingAvailable = this.isFacetingValid(_store);

                if (this.isStoreLoaded(_store)) {
                    this.prepareToShowPanel();
                }
                else {
                    this.on('afterrender', function(w) {
                        if (!this.isStoreLoaded(_store) || _store.isLoading) {
                            var fp = this.getFacetedFilterPanel();
                            if (fp && fp.getEl()) {
                                fp.getEl().mask('Loading...');
                            }
                        }
                    }, this, {single: true});
                }
            }
        }
        else {
            if (this.facetingAvailable) {
                var fp = this.getFacetedFilterPanel();
                if (fp && fp.getEl()) {
                    this.configureLookupPanel(fp);
                }
                else if (fp) {
                    fp.on('show', function(p) {
                        this.configureLookupPanel(p);
                    }, this, {single: true});
                }
            }
        }
    },

    isFacetingCandidate : function()
    {
        var dr = this.getDataRegion();
        if (!this.isQueryDataRegion(dr)) {
            return false;
        }

        var isFacetingCandidate = false;

        switch (this.boundColumn.facetingBehaviorType) {

            case 'ALWAYS_ON':
                isFacetingCandidate = true;
                break;
            case 'ALWAYS_OFF':
                break;
            case 'AUTOMATIC':
                // auto rules are if the column is a lookup or dimension
                // OR if it is of type : (boolean, int, date, text), multiline excluded

                if (this.boundColumn.lookup || this.boundColumn.dimension)
                    isFacetingCandidate = true;
                // 15156: disabled faceted filtering on date columns until we address issues described in this bug
                else if (this._jsonType == 'boolean' || this._jsonType == 'int' ||
                        (this._jsonType == 'string' && this.boundColumn.inputType != 'textarea'))
                    isFacetingCandidate = true;
                break;
        }

        return isFacetingCandidate;
    },

    getInitialFilterType : function()
    {
        if (this.getDataRegion() && this.isFacetingCandidate()) {
            return 'include';
        }
        return 'default';
    },

    canShowFacetedUI : function(filterType)
    {
        var dr = this.getDataRegion();
        if (!this.isQueryDataRegion(dr)) {
            return false;
        }

        filterType = filterType || this.filterType;

        var paramValPairs = this.getParamsForField(this._fieldKey);
        if (this.getInitialFilterType() == 'default' || paramValPairs.length > 1) {
            return false;
        }

        var shouldShow = true;
        Ext.each(paramValPairs, function(pair){
            var filter = LABKEY.Filter.getFilterTypeForURLSuffix(pair.operator);

            if(!filter.isMultiValued() && !filter.getMultiValueFilter()){
                shouldShow = false;
                return;
            }

            if(filter.isMultiValued() && ['in', 'notin'].indexOf(filter.getURLSuffix()) == -1 ){
                shouldShow = false;
            }

        }, this);

        if(shouldShow && !this.forceAdvancedFilters){
            var store = this.getLookupStore();
            if(
                (!store || store.getCount() > this.MAX_FILTER_CHOICES) ||
                (store && store.fields && store.getCount() === 0) || //Issue 13946: faceted filtering: loading mask doesn't go away if the lookup returns zero rows
                this.forceAdvancedFilters
            ){
                shouldShow = false;
            }
            else if (store.getCount()) {
                Ext.each(paramValPairs, function(pair, idx){
                    if(!Ext.isEmpty(pair.value)){
                        var values = pair.value.split(';');
                        Ext.each(values, function(v){
                            if(store.findExact('value', v) == -1){
                                shouldShow = false;
                                return false;
                            }
                        }, this);
                    }
                }, this);
            }
        }

        if(this.forceAdvancedFilters)
            shouldShow = false;

        return shouldShow;
    },

    configureLookupPanel : function(p)
    {
        var panel = this.find('itemId', 'facetedPanel')[0];
        if(!this.rendered || !panel){
            this.on('render', this.configureLookupPanel, this, {single: true});
            return;
        }

        var filterConfig = this.getComboConfig(-1);
        filterConfig.hidden = true;
        filterConfig.value = 'in';
        filterConfig.initialValue = 'in';

        var toAdd = [filterConfig, {
            xtype: 'panel',
            width: this.width - 40, //prevent horizontal scroll
            bodyStyle: 'padding-left: 5px;',
            items: [ this.getCheckboxGroupConfig(0) ]
        }];
        panel.removeAll();
        panel.add(toAdd);

        if (p) {
            p.getEl().unmask();
            var f = this.getFiltersFromParams(p.filterType);
            if (p.filterType != 'include' || f.length <= 1)
                this.setValuesFromParams(p, f);
        }
        this.doLayout();
    },

    getFilterCombos : function(panel)
    {
        return this._getFields(panel, /^filterComboBox/);
    },

    getInputFields : function(panel)
    {
        return this._getFields(panel, /^inputField/);
    },

    _getFields : function(panel, regex)
    {
        if (!this.rendered) {
            return [];
        }

        panel = panel || this.getTabPanel().getActiveTab();
        if (!panel) {
            return [];
        }

        return panel.findBy(function(item) {
            return item.itemId && regex.test(item.itemId);
        });
    },

    getFiltersFromParams : function(filterType)
    {
        var filterArray = this.getParamsForField(this._fieldKey),
            filters = [];

        if (filterType != 'default' && !this.forceAdvancedFilters) {

            var optimized, filterDef;
            Ext.each(filterArray, function(filter){
                var allowable = [];
                this.getLookupStore().each(function(rec){
                    allowable.push(rec.get('value'));
                });
                filterDef = LABKEY.Filter.getFilterTypeForURLSuffix(filter.operator);
                optimized = this.optimizeFilter(filterDef, filter.value, allowable);
                if(optimized){
                    filters.push({operator: optimized[0], value: optimized[1]});
                }

            }, this);
        }
        else {
            filters = filterArray;
        }

        return filters;
    },

    setValuesFromParams : function(target, values)
    {
        var tab = this.getTabPanel().getActiveTab();
        if(!this.rendered || !tab) {
            return;
        }

        target = target || tab;
        var combos      = this.getFilterCombos(target);
        var inputFields = this.getInputFields(target);

        if (!combos || !combos.length) {
            return;
        }

        this.filterType = target.filterType;
        var paramValPairs = values;
        this.hasLoaded = true;

        var filterIndex = 0;

        //reset the form
        if(this.filterType == 'default'){
            Ext.each(combos, function(field){
                field.reset();
            });

            Ext.each(inputFields, function(field){
                field.reset();
            });
        }

        var effectiveFilters = 0;
        Ext.each(paramValPairs, function(pair, idx){
            var combo = combos[filterIndex];
            if(!combo){
                console.log('no input found for idx: ' + idx);
                this.hasLoaded = false;
                return;
            }

            var input = inputFields[filterIndex];

            if (pair.operator) {
                var filter = LABKEY.Filter.getFilterTypeForURLSuffix(pair.operator);

                //attempt to convert single-value filters into multi-value for faceting
                if (this.filterType != 'default' && !filter.isMultiValued() && filter.getMultiValueFilter()) {
                    filter = filter.getMultiValueFilter();
                }

                if (this.filterType == 'include') {
                    if ('notin' == pair.operator || 'neqornull' == pair.operator || 'neq' == pair.operator) {
                        filter = filter.getOpposite();
                        pair.value = this.getInverse(this.getLookupStore(), pair.value);
                    }
                }

                if (this.filterType != 'default' && !filter.isMultiValued()) {
                    console.log('skipping filter: ' + pair.operator);
                    return;
                }
                combo.setValue(filter.getURLSuffix());

                if (filter.isDataValueRequired())
                    input.enable();

                if (Ext.isDefined(pair.value)) {
                    if (this.filterType == 'default') {
                        input.setValue(pair.value);
                    }
                    else {
                        if(filter.getURLSuffix() == 'in' && pair.value === '') {
                            input.defaultValue = true;
                            input.on('viewready', function(g) {
                                g.selectAll();
                            }, this, {single: true});
                        }
                        else {
                            input.defaultValue = false;
                            input.setValue(pair.value);
                            input.on('viewready', function(g) {
                                g.selectRequested();
                            }, this, {single: true});
                        }
                    }
                    effectiveFilters++;
                }
            }
            else if(idx > 0) {
                combo.setValue("");
            }

            filterIndex++;
        }, this);

        if(this.filterType == 'include' && !effectiveFilters){
            if (inputFields[0].requestedRecords) {
                input.on('viewready', function(g) {
                    g.selectRequested();
                }, this, {single: true});
            }
            else {
                inputFields[0].on('viewready', function(g) {
                    g.selectAll();
                }, this, {single: true});
            }
        }
    },

    getInverse : function(store, values)
    {
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

        if(values.indexOf('') == -1 && newValues.indexOf('') == -1 && newValues.length > 1)
            newValues.push('');

        return newValues.join(';');
    },

    getSkipPrefixes: function()
    {
        return this.dataRegionName + ".offset";
    },

    getXtype: function()
    {
        switch(this._jsonType){
            case "date":
                return "datefield";
            case "int":
            case "float":
                return "numberfield";
            case "boolean":
                return 'labkey-booleantextfield';
            default:
                return "textfield";
        }
    },

    getFieldXtype: function()
    {
        var xtype = this.getXtype();
        if(xtype == 'numberfield')
            xtype = 'textfield';

        return xtype;
    },

    getCheckboxGroupConfig: function(idx)
    {
        var sm = new Ext.grid.CheckboxSelectionModel({
            listeners: {
                selectionchange: {
                    fn: function(sm){
                        // NOTE: this will manually set the checked state of the header checkbox.  it would be better
                        // to make this a real tri-state (ie. selecting some records is different then none), but since this is still Ext3
                        // and ext4 will be quite different it doesnt seem worth the effort right now
                        var selections = sm.getSelections();
                        var headerCell = Ext.fly(sm.grid.getView().getHeaderCell(0)).first('div');
                        if(selections.length == sm.grid.store.getCount()){
                            headerCell.addClass('x-grid3-hd-checker-on');
                        }
                        else {
                            headerCell.removeClass('x-grid3-hd-checker-on');
                        }


                    },
                    buffer: 50
                }
            }
        });

        this.gridID = Ext.id();
        return {
            xtype: 'grid',
            id : this.gridID,
            border : true,
            bodyBorder: true,
            frame : false,
            autoHeight: true,
            itemId: 'inputField' + (idx || 0),
            filterIndex: idx,
            msgTarget: 'title',
            store: this.getLookupStore(),
            viewConfig: {
                headerTpl: new Ext.Template(
                    '<table border="0" cellspacing="0" cellpadding="0" style="{tstyle}">',
                        '<thead>',
                            '<tr class="x-grid3-row-table">{cells}</tr>',
                        '</thead>',
                    '</table>'
                )
            },
            sm: sm,
            cls: 'x-grid-noborder',
            columns: [
                sm,
                new Ext.grid.TemplateColumn({
                    header: '<a href="javascript:void(0);">[All]</a>',
                    dataIndex: 'value',
                    menuDisabled: true,
                    resizable: false,
                    width: 340,
                    tpl: new Ext.XTemplate('<tpl for=".">' +
                        '<span class="labkey-link" ext:qtip="Click the label to select only this row.  ' +
                        'Click the checkbox to toggle this row and preserve other selections.">' +
                        '{[!Ext.isEmpty(values["displayValue"]) ? values["displayValue"] : "[Blank]"]}' +
                        '</span></tpl>')
                }
            )],
            listeners: {
                afterrender : function(grid) {
                    var headerCell = Ext.fly(grid.getView().getHeaderCell(1)).first('div');
                    headerCell.on('click', grid.onHeaderCellClick, grid);

                    grid.getSelectionModel().on('selectionchange', function() {
                        this.facetDirty = true;
                    }, this);
                },
                scope : this
            },
            //this is a hack to extend toggle behavior to the header cell, not just the checkbox next to it
            onHeaderCellClick : function() {
                var sm = this.getSelectionModel();
                var selected = sm.getSelections();
                if(selected.length == this.store.getCount()){
                    this.selectNone();
                }
                else {
                    sm.selectAll();
                }
            },
            getValue : function() {
                return this.getValues().values;
            },
            getValues : function() {
                var values = [],
                    sels   = this.getSelectionModel().getSelections();

                Ext.each(sels, function(rec){
                    values.push(rec.get('value'));
                }, this);

                if(values.indexOf('') != -1 && values.length == 1)
                    values.push(''); //account for null-only filtering

                return {
                    values : values.join(';'),
                    length : values.length,
                    max    : this.getStore().getCount()
                };
            },
            setValue : function(values) {
                if (!this.rendered) {
                    this.on('render', function() {
                        this.setValue(values);
                    }, this, {single: true});
                }

                if (!Ext.isArray(values)) {
                    values = values.split(';');
                }

                if (this.store.isLoading) {
                    // need to wait for the store to load to ensure records
                    this.store.on('load', function() {
                        this._checkAndLoadValues(values);
                    }, this, {single: true});
                }
                else {
                    this._checkAndLoadValues(values);
                }
            },
            _checkAndLoadValues : function(values) {
                var records = [],
                    recIdx,
                    recordNotFound;

                // Iterate each value and record the index to be stored
                Ext.each(values, function(val) {
                    recIdx = this.store.findBy(function(rec){
                        return rec.get('value') === val;
                    });
                    if (recIdx != -1) {
                        records.push(recIdx);
                    }
                    else {
                        // Issue 14710: if the record isnt found, we wont be able to select it, so should reject.
                        // If it's null/empty, ignore silently
                        if (!Ext.isEmpty(val)) {
                            recordNotFound = true;
                            return false;
                        }
                    }
                }, this);

                if (recordNotFound) {
                    // NOTE: this is sort of a hack.  we allow users to pick values from the faceted UI, but also let them manually enter
                    // filters.  i think that's the right thing to do, but this means they can choose to filter on an invalid value.
                    // if we hit this situation, rather then just ignore it, we switch to advanced UI
                    var tabpanel = this.findParentByType('tabpanel'),
                        window   = tabpanel.ownerCt;

                    tabpanel.setActiveTab(0);
                    tabpanel.hasLoaded = false; // force reload from URL
                    window.setValuesFromParams(null, [{operator: 'in', value: values.join(';')}]);
                    return;
                }

                this.requestedRecords = records;
            },
            selectRequested : function() {
                if (this.requestedRecords) {
                    this.getSelectionModel().selectRows(this.requestedRecords);
                    this.requestedRecords = undefined;
                }
            },
            selectAll : function() {
                if(this.rendered) {
                    var sm = this.getSelectionModel();
                    sm.selectAll.defer(10, sm);
                }
                else {
                    this.on('render', this.selectAll, this, {single: true});
                }
            },
            selectNone : function() {
                if(this.rendered)
                    this.getSelectionModel().selectRows([]);
                else {
                    this.on('render', this.selectNone, this, {single: true});
                }
            },
            scope : this
        };
    },

    getActiveTab: function()
    {
        var panel = this.find('itemId', 'filterArea');
        if(!panel || !panel.length){
            return;
        }
        panel = panel[0];
        if(panel.xtype == 'tabpanel')
            return panel.getActiveTab();
        else
            return panel;
    },

    getComboConfig: function(idx)
    {
        return {
            xtype: 'combo',
            itemId: 'filterComboBox' + idx,
            filterIndex: idx,
            name: 'filterType_'+(idx + 1),   //for compatibility with tests...
            listWidth: (this._jsonType == 'date' || this._jsonType == 'boolean') ? null : 380,
            emptyText: idx === 0 ? 'Choose a filter:' : 'No other filter',
            autoSelect: false,
            width: 250,
            //allowBlank: 'false',
            triggerAction: 'all',
            fieldLabel: (idx === 0 ?'Filter Type' : 'and'),
            store: this.getComboStore(this.boundColumn.mvEnabled, this._jsonType, idx),
            displayField: 'text',
            valueField: 'value',
            typeAhead: 'false',
            forceSelection: true,
            mode: 'local',
            clearFilterOnReset: false,
            editable: false,
            value: idx === 0 ? LABKEY.Filter.getDefaultFilterForType(this.boundColumn.jsonType).getURLSuffix() : '',
            originalValue: idx === 0 ? LABKEY.Filter.getDefaultFilterForType(this.boundColumn.jsonType).getURLSuffix() : '',
            listeners:{
                select : function(combo) {
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
                },
                scope: this
            },
            scope: this
        }
    },

    getInputFieldConfig: function(idx)
    {
        idx = idx || 0;
        var jtype = this._jsonType;
        var config = {
            xtype         : this.getFieldXtype(),
            itemId        : 'inputField' + idx,
            filterIndex   : idx,
            id            : 'value_'+(idx + 1),   //for compatibility with tests...
            width         : 250,
            blankText     : 'You must enter a value.',
            validateOnBlur: true,
            disabled      : idx !== 0,
            value         : null,
            listeners     : {
                disable   : function(field){
                    //Call validate after disable so any pre-existing validation errors go away.
                    if(field.rendered)
                        field.validate();
                },
                focus : function(f) {
                    if (this.focusTask) {
                        Ext.TaskMgr.stop(this.focusTask);
                    }
                },
                scope : this
            },
            validator: function(value) {

                // support for filtering '∞'
                if (jtype == 'float' && value.indexOf('∞') > -1) {
                    value = value.replace('∞', 'Infinity');
                    this.setRawValue(value); // does not fire validation
                }

                var idx = this.filterIndex;
                var window = this.findParentBy(function(item){
                    return item.itemId == 'filterWindow';
                });

                if(!window)
                    return;

                var combos = window.find('itemId', 'filterComboBox'+idx);
                if(!combos.length)
                    return;

                return window.inputFieldValidator(this, combos[0]);
            }
        };

        if(this._jsonType == "date")
            config.altFormats = LABKEY.Utils.getDateAltFormats();

        return config;
    },

    getNextFilterIdx: function()
    {
        return this.getFilterCombos() ? this.getFilterCombos().length : 0;
    },

    getFilterInputPairConfig: function(quantity)
    {
        var idx = this.getNextFilterIdx(),
            items = [], i;

        for(i=0; i < quantity; i++){
            items.push({
                xtype: 'panel',
                layout: 'form',
                itemId: 'filterPair' + idx,
                border: false,
                defaults: this.itemDefaults,
                items: [this.getComboConfig(idx), this.getInputFieldConfig(idx)]
            });
            idx++;
        }
        return items;
    },

    okHandler: function(btn)
    {
        var tab = this.getTabPanel().getActiveTab();

        if(!tab.getForm().isValid()) {
            return;
        }

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
                var input = inputFields[idx];
                var value = input.getValue();

                var filter = LABKEY.Filter.getFilterTypeForURLSuffix(c.getValue());

                if(!filter){
                    alert('filter not found: ' + c.getValue());
                    return;
                }

                if(Ext.isEmpty(value) && filter.isDataValueRequired()){
                    if(tab.filterType == 'default'){
                        input.markInvalid('You must enter a value');
                        isValid = false;
                        return false;
                    }
                    else {
                        return;
                    }
                }

                var filterArray = [filter.getURLSuffix(), value];
                if(tab.filterType != 'default'){
                    var allowable = [];
                    this.getLookupStore().each(function(rec){
                        allowable.push(rec.get('value'))
                    });
                    filterArray = this.optimizeFilter(filter, value, allowable);
                }

                if(filterArray && (!Ext.isEmpty(filterArray[0]))) //ignore filters where both operator can value are blank
                    filters.push(filterArray);
            }
        }, this);

        if(this.validatorFn(filters) === false)
            return;

        if(isValid){
            this.setFilter(filters);
            this.close();
        }
    },

    optimizeFilter: function(filter, value, allowableValues)
    {
        value = value.split(';');
        if(filter.isMultiValued() && allowableValues && allowableValues.length){
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

            //Issue 15657: switch to single filter option correctly
            if(value.length == 1 && filter.getSingleValueFilter()){
                filter = filter.getSingleValueFilter();
            }
        }

        //b/c empty strings will be ignored, we add a second to force a delimiter.
        if(filter.isDataValueRequired() && value.length == 1 && value[0] == '')
            value.push('');

        //if the value is blank, do not apply this empty filter
        if(value.length != 0 || !filter.isDataValueRequired())
            return [filter.getURLSuffix(), value.join(';')]
    },

    cancelHandler: function()
    {
        this.close();
    },

    getComboStore : function(mvEnabled, jsonType, storeNum)
    {
        var fields = ['text', 'value',
            {name: 'isMulti', type: Ext.data.Types.BOOL},
            {name: 'isOperatorOnly', type: Ext.data.Types.BOOL}
        ];
        var store = new Ext.data.ArrayStore({
            fields: fields,
            idIndex: 1
        });
        var comboRecord = Ext.data.Record.create(fields);

        var filters = LABKEY.Filter.getFilterTypesForType(this._jsonType, mvEnabled);
        for (var i=0;i<filters.length;i++)
        {
            var filter = filters[i];
            store.add(new comboRecord({
                text: filter.getLongDisplayText(),
                value: filter.getURLSuffix(),
                isMulti: filter.isMultiValued(),
                isOperatorOnly: filter.isDataValueRequired()
            }));
        }

        if(storeNum > 0){
            store.removeAt(0);
            store.insert(0, new comboRecord({text:'No Other Filter', value: ''}));
        }

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
        var dr = this.getDataRegion();
        if (!dr)
            return;
        dr.clearFilter(this._fieldKey);
        this.close();
    },

    clearAllFilters : function()
    {
        var dr = this.getDataRegion();
        if (!dr)
            return;
        dr.clearAllFilters();
        this.close();
    },

    changeFilter : function(newParamValPairs, newQueryString)
    {
        var dr = this.getDataRegion();
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
        var dr = this.getDataRegion(),
            queryString = null;

        // 16684
        if (dr)
            queryString = dr.requestURL;
        var newParamValPairs = this.getParamValPairs(queryString, [this.dataRegionName + "." + this._fieldKey + "~", this.getSkipPrefixes()]);
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
            pair = [this.dataRegionName + "." + this._fieldKey + "~" + comparison];
        } else{
            pair = [this.dataRegionName + "." + this._fieldKey + "~" + comparison, input];
        }
        return pair;
    },

    clearSort : function(tableName, fieldKey)
    {
        if(!tableName || !fieldKey)
            return;

        var dr = LABKEY.DataRegions[tableName];
        if (!dr)
            return;
        dr.clearSort(fieldKey);
    },

    isStoreLoaded : function(store)
    {
        return (!(store && (!store.fields || !store.fields.length) && !this.forceAdvancedFilters && !store.isLoading));
    },

    getLookupStore : function()
    {
        var dataRegion = this.getDataRegion();
        var storeId = [dataRegion.schemaName, dataRegion.queryName, this._fieldKey].join('||');

        var store = Ext.StoreMgr.get(storeId);
        if (store) {
            return store;
        }

        return Ext.StoreMgr.add(new LABKEY.ext.Store({
            schemaName: dataRegion.schemaName,
            sql: this.getLookupValueSql(dataRegion, this.boundColumn),
            storeId: storeId,
            sort: "value",
            containerPath: dataRegion.container || dataRegion.containerPath || LABKEY.container.path,
            maxRows: this.MAX_FILTER_CHOICES, // Limit so that we don't overwhelm the user (or the browser itself) with too many checkboxes
            includeTotalCount: false,  // Don't bother getting the total row count, which might involve another query to the database
            containerFilter: dataRegion.containerFilter,
            autoLoad: true,
            listeners: {
                load : function(store) {
                    // Issue 15124: because the SQL will not necessarily match the display value,
                    // we reformat on the client.
                    // the total number of records is expected to be small
                    // technically this is probably better done with a convert() function on the field, but
                    // LABKEY.ext.Store doesnt make that very easy
                    var valMap = {};
                    store.each(function(rec){
                        rec.set('displayValue', this.formatValue(rec.get('value')));
                        rec.commit(true); //mark dirty = false

                        if(valMap[rec.data.displayValue]){
                            var dup = valMap[rec.data.displayValue];
                            // NOTE: because formatting the value could result in 2 distinct raw values having the
                            // same display value (ie. datetime), we track and save these
                            if(rec.get('value') !== dup.get('value'))
                                dup.get('valueArray').push(rec.get('value'));

                            store.remove(rec);
                        }
                        else {
                            rec.data.valueArray = [rec.get('value')];
                            valMap[rec.get('displayValue')] = rec;
                        }
                    }, this);
                    store.isLoading = false;
                    this.preparePanelTask.delay(50);
                },
                exception: function(){
                    this.forceAdvancedFilters = true;
                    this.preparePanelTask.delay(50);
                },
                scope : this
            }
        }));

    },

    formatValue: function(val)
    {
        if(this.boundColumn){
            if (this.boundColumn.extFormatFn){
                try {
                    this.boundColumn.extFormatFn = eval(this.boundColumn.extFormatFn);
                }
                catch (error){
                    console.log('improper extFormatFn: ' + this.boundColumn.extFormatFn);
                }

                if(Ext.isFunction(this.boundColumn.extFormatFn)){
                    val = this.boundColumn.extFormatFn(val);
                }
            }
            else if (this._jsonType == 'int'){
                val = parseInt(val);
            }
        }
        return val
    },

    // TODO: Migrate to use LABKEY.Query.selectDistinctRows
    getLookupValueSql: function(dataRegion, column)
    {
        // Build up a SELECT DISTINCT query to get all of the values that are currently in use
        //NOTE: empty string will be treated as NULL, which is b/c Ext checkboxes can be set to empty string, but not null
        var sql = 'SELECT CASE WHEN value IS NULL then \'\' ELSE cast(value as varchar) END as value, null as displayValue FROM (';
        sql += 'SELECT DISTINCT t.';

        var fieldKey;
        if(column.displayField){
            fieldKey = LABKEY.FieldKey.fromString(column.displayField);
        }
        else {
            fieldKey = LABKEY.FieldKey.fromParts(column.fieldKeyArray);
        }

        sql += fieldKey.toSQLString();

        sql += ' AS value FROM "' + dataRegion.queryName.replace("\"", "\"\"") + '" t';
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

    getParamsForField: function(fieldKey)
    {
        var dataRegion = this.getDataRegion();

        if (!this.queryString)
        {
            this.queryString = dataRegion ? dataRegion.requestURL : null;
        }

        //NOTE: if the dialog has loaded, we use the values from the inputs. otherwise we resort to the dataregion
        var paramValPairs = LABKEY.DataRegion.getParamValPairsFromString(this.queryString, [this.getSkipPrefixes()]);
        var results = [];
        var re = new RegExp('^' + Ext.escapeRe(this.dataRegionName + '.' + fieldKey), 'i');
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
                return this.validateEqOneOf(input.getValue());
            }

            return this.validateInputField(input.getValue());
        }
        return true;
    },

    validateEqOneOf: function(input)
    {
        // Used when "Equals One Of.." is selected. Calls validateInputField on each value entered.
        var values = input.split(';');
        var isValid = "";
        for(var i = 0; i < values.length; i++){
            isValid = this.validateInputField(values[i]);
            if(isValid !== true){
                return isValid;
            }
        }
        //If we make it out of the for loop we had no errors.
        return true;
    },

    _extTypeMap:  {
        'string': 'STRING',
        'int': 'INT',
        'float': 'FLOAT',
        'date': 'DATE',
        'boolean': 'BOOL'
    },

    //The fact that Ext3 ties validation to the editor is a little funny, but using this shifts the work to Ext
    validateInputField: function(value)
    {
        //the reason for this change is to try to shift more of the burden from our code into Ext
        var type = this._extTypeMap[this._jsonType];
        if(type){
            var field = new Ext.data.Field({
                type: Ext.data.Types[type],
                allowDecimals :  this._jsonType != "int",  //will be ignored by anything besides numberfield
                useNull: true
            });

            var convertedVal = field.convert(value);
            if(!Ext.isEmpty(value) && value != convertedVal){
                return "Invalid value: " + value;
            }
        }
        else {
            console.log('Unrecognized type: ' + this._jsonType);
        }

        return true;
    }

});


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
