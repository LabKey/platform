/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if (!LABKEY.DataRegions)
{
    LABKEY.DataRegions = {};
}

(function($) {

    //
    // CONSTANTS
    //
    var ALL_FILTERS_SKIP_PREFIX = '.~';
    var PARAM_PREFIX = '.param.';
    var SORT_PREFIX = '.sort', SORT_ASC = '+', SORT_DESC = '-';
    var OFFSET_PREFIX = '.offset';
    var MAX_ROWS_PREFIX = '.maxRows', SHOW_ROWS_PREFIX = '.showRows';
    var CONTAINER_FILTER_NAME = '.containerFilterName';
    var CUSTOM_VIEW_PANELID = '~~customizeView~~';
    var VIEWNAME_PREFIX = '.viewName';

    //
    // PRIVATE VARIABLES
    //
    var _paneCache = {};

    LABKEY.DataRegion2 = function(config) {

        if (!config || !LABKEY.Utils.isString(config.name)) {
            throw '"name" is required to contruct a LABKEY.DataRegion.';
        }

        /**
         * Config Options
         */
        var defaults = {

            _allowHeaderLock: false,

            /**
             * All rows visible on the curreng page.
             */
            complete: false,

            /**
             * The currently applied container filter. Note, this is only if it is set on the URL, otherwise
             * the containerFilter could come from the view configuration. Use getContainerFilter()
             * on this object to get the right value.
             */
            containerFilter: undefined,

            /**
             * Id of the DataRegion. Same as name property.
             */
            id: name,

            /**
             * Maximum number of rows to be displayed. 0 if the count is not limited. Read-only.
             */
            maxRows: 0,

            /**
             * Name of the DataRegion. Should be unique within a given page. Read-only. This will also be used as the id.
             */
            name: name,

            /**
             * Starting offset of the rows to be displayed. 0 if at the beginning of the results. Read-only.
             */
            offset: 0,

            /**
             * Name of the query to which this DataRegion is bound. Read-only.
             */
            queryName: '',

            requestURL: undefined,

            /**
             * Schema name of the query to which this DataRegion is bound. Read-only.
             */
            schemaName: '',

            /**
             * URL to use when selecting all rows in the grid. May be null. Read-only.
             */
            selectAllURL: undefined,

            selectedCount: 0,

            showRecordSelectors: false,

            /**
             * An enum declaring which set of rows to show. all | selected | unselected | paginated
             */
            showRows: "paginated",

            totalRows: undefined, // totalRows isn't available when showing all rows.

            /**
             * Name of the custom view to which this DataRegion is bound, may be blank. Read-only.
             */
            viewName: null,

            //
            // Asyncronous properties
            //
            async: false,
            buttonBar: undefined,
            frame: 'none',
            metadata: undefined,
            quickChartDisabled: false,
            renderTo: undefined,
            reportId: undefined,
            timeout: undefined,

            removeableContainerFilter: undefined,
            userContainerFilter: undefined, // TODO: Incorporate this with the standard containerFilter

            filters: undefined,
            removeableFilters: undefined,
            userFilters: {},

            parameters: undefined,
            userSort: undefined
        };

        var settings = $.extend({}, defaults, config);

        for (var s in settings) {
            if (settings.hasOwnProperty(s)) {
                this[s] = settings[s];
            }
        }

        if (!this.messages) {
            this.messages = {};
        }

        // Only while this is an experimental feature
        var adminLink = LABKEY.Utils.textLink({text: 'Experimental Features', href: LABKEY.ActionURL.buildURL('admin', 'experimentalFeatures', '/')});
        this.messages['experimental'] = '<span class="labkey-strong">Warning!</span>&nbsp;<span>This is an experimental Data Region. ' + adminLink + '</span>';

        /**
         * Non-configurable Options
         */
        this.selectionModified = false;
        this.panelConfigurations = {}; // formerly, panelButtonContents

        if (!LABKEY.DataRegions) {
            LABKEY.DataRegions = {};
        }
        else {
            // here we can copy properties from our former self
            var ancestor = LABKEY.DataRegions[this.name];
            if (ancestor) {
                this.async = ancestor.async;
            }
        }

        LABKEY.DataRegions[this.name] = this;

        this._init();
    };

    var Proto = LABKEY.DataRegion2.prototype;

    Proto.toJSON = function() {
        return {
            name: this.name,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            offset: this.offset,
            maxRows: this.maxRows,
            messages: this.msgbox.toJSON() // hmm, unsure exactly how this works
        };
    };

    Proto._init = function() {

        var me = this;
        var nameSel = '#' + me.name;
        var baseSel = 'form' + nameSel;

        this.form = $(baseSel);
        //this.table = $('dataregion_' + me.name);

        // derived DataRegion's may not include the form id
        //if (!this.form && this.table)
        //{
        //    var el = this.table.dom;
        //    do
        //    {
        //        el = el.parentNode;
        //    }
        //    while (el != null && el.tagName != "FORM");
        //    if (el) this.form = el;
        //}

        this._initMessaging();
        this._initSelection();
        this._initPaging();
        this._initCustomViews();
        this._initPanes();
    };

    /**
     * Refreshes the grid via a page reload. Can be prevented with a listener on the 'beforerefresh'
     * event.
     */
    Proto.refresh = function() {

        //var event = $.Event("beforerefresh");
        //
        //$(this).trigger(event);
        //
        //if (event.isDefaultPrevented()) {
        //    return;
        //}
        $(this).trigger('beforerefresh', this);

        if (this.async) {
            _load(this);
        }
        else {
            window.location.reload();
        }
    };

    //
    // Filtering
    //

    /**
     * Add a filter to this Data Region.
     * @param {LABKEY.Filter} filter
     */
    Proto.addFilter = function(filter) {
        _updateFilter(this, filter);
    };

    /**
     * Removes all filters from the DataRegion
     */
    Proto.clearAllFilters = function() {
        //var event = $.Event("beforeclearallfilters");
        //
        //$(this).trigger(event, this);
        //
        //if (event.isDefaultPrevented()) {
        //    return;
        //}

        if (this.async) {
            this.offset = 0;
            this.userFilters = {};
        }

        _removeParameters(this, [ALL_FILTERS_SKIP_PREFIX, OFFSET_PREFIX]);
    };

    /**
     * Removes all the filters for a particular field
     * @param {string or FieldKey} fieldKey the name of the field from which all filters should be removed
     */
    Proto.clearFilter = function(fieldKey) {
        fieldKey = _resolveFieldKey(this, fieldKey);

        if (fieldKey) {
            var columnPrefix = '.' + fieldKey.toString() + '~';

            //var event = $.Event("beforeclearfilter");
            //
            //$(this).trigger(event, this, columnName);
            //
            //if (event.isDefaultPrevented()) {
            //    return;
            //}

            if (this.async) {
                this.offset = 0;

                if (this.userFilters) {
                    var namePrefix = this.name + columnPrefix;
                    $.each(this.userFilters, function(name, v) {
                        if (name.indexOf(namePrefix) >= 0) {
                            delete this.userFilters[name];
                        }
                    }, this);
                }
            }

            _removeParameters(this, [columnPrefix, OFFSET_PREFIX]);
        }
    };

    /**
     * Returns the {@link LABKEY.Query.containerFilter} currently applied to the DataRegion. Defaults to LABKEY.Query.containerFilter.current.
     * @returns {String} The container filter currently applied to this DataRegion. Defaults to 'undefined' if a container filter is not specified by the configuration.
     * @see LABKEY.DataRegion#getUserContainerFilter to get the containerFilter value from the URL.
     */
    Proto.getContainerFilter = function() {
        var cf;

        if (LABKEY.Utils.isString(this.containerFilter) && this.containerFilter.length > 0) {
            cf = this.containerFilter;
        }
        else if (LABKEY.Utils.isObject(this.view) && LABKEY.Utils.isString(this.view.containerFilter) && this.view.containerFilter.length > 0) {
            cf = this.view.containerFilter;
        }

        return cf;
    };

    /**
     * Returns the user {@link LABKEY.Query.containerFilter} parameter from the URL.
     * @returns {LABKEY.Query.containerFilter} The user container filter.
     */
    Proto.getUserContainerFilter = function() {
        return this.getParameter(this.name + CONTAINER_FILTER_NAME);
    };

    /**
     * Returns an Array of LABKEY.Filter instances constructed from the URL.
     * @returns {Array} Array of {@link LABKEY.Filter} objects that represent currently applied filters.
     */
    Proto.getUserFilterArray = function() {
        var userFilter = [], me = this;

        var pairs = _getParametersSearch(this, this.requestURL);
        $.each(pairs, function(i, pair) {
            if (pair[0].indexOf(me.name + '.') == 0 && pair[0].indexOf('~') > -1) {
                var tilde = pair[0].indexOf('~');
                var fieldKey = pair[0].substring(me.name.length + 1, tilde);
                var op = pair[0].substring(tilde + 1);
                userFilter.push(LABKEY.Filter.create(fieldKey, pair[1], LABKEY.Filter.getFilterTypeForURLSuffix(op)));
            }
        });

        return userFilter;
    };

    /**
     * Remove a filter on this DataRegion.
     * @param {LABKEY.Filter} filter
     */
    Proto.removeFilter = function(filter) {
        if (LABKEY.Utils.isObject(filter) && LABKEY.Utils.isFunction(filter.getColumnName)) {
            _updateFilter(this, null, [this.name + '.' + filter.getColumnName() + '~']);
        }
    };

    /**
     * Replace a filter on this Data Region. Optionally, supply another filter to replace for cases when the filter
     * columns don't match exactly.
     * @param {LABKEY.Filter} filter
     * @param {LABKEY.Filter} [filterToReplace]
     */
    Proto.replaceFilter = function(filter, filterToReplace) {
        var target = filterToReplace ? filterToReplace : filter;
        _updateFilter(this, filter, [this.name + '.' + target.getColumnName() + '~']);
    };

    Proto.replaceFilters = function(filters, column) {
        if (!LABKEY.Utils.isArray(filters) || filters.length == 0) {
            return;
        }

        if (filters.length == 1) {
            return this.replaceFilter(filters[0]);
        }

        // use the first filter to skip prefixes
        var target = filters[0];

        var params = _getParameters(this, this.requestURL, [this.name + '.' + column.fieldKey + '~']);
        params.push([target.getURLParameterName(this.name), target.getURLParameterValue()]);

        // stop skipping prefixes and add the rest of the params
        for (var f = 1; f < filters.length; f++)
        {
            target = filters[f];
            params.push([target.getURLParameterName(this.name), target.getURLParameterValue()]);
        }

        _changeFilter(this, params, LABKEY.DataRegion.buildQueryString(params));
    };

    /**
     * @private
     * @param filter
     * @param filterMatch
     */
    Proto.replaceFilterMatch = function(filter, filterMatch) {
        var params = _getParameters(this, this.requestURL);
        var skips = [], me = this;

        $.each(params, function(param) {
            if (param[0].indexOf(me.name + '.') == 0 && param[0].indexOf(filterMatch) > -1) {
                skips.push(param[0]);
            }
        });

        _updateFilter(this, filter, skips);
    };

    //
    // Selection
    //

    /**
     * @private
     */
    Proto._initSelection = function() {

        var me = this;

        if (this.form) {
            if (this.showRecordSelectors) {
                _onSelectionChange(this);
            }
        }

        // Bind Events
        _getAllRowSelectors(this).on('click', function(evt) {
            evt.stopPropagation();
            me.selectPage.call(me, this.checked);
        });
        _getRowSelectors(this).on('click', function() { me.selectRow.call(me, this); });
    };

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
    Proto.clearSelected = function(config) {
        config = config || {};
        config.selectionKey = this.selectionKey;
        config.scope = config.scope || this;

        this.selectedCount = 0;
        _onSelectionChange(this);

        if (config.selectionKey) {
            LABKEY.DataRegion2.clearSelected(config);
        }

        if (this.showRows == 'selected') {
            _removeParameters(this, [SHOW_ROWS_PREFIX]);
        }
        else if (this.showRows == 'unselected') {
            // keep "SHOW_ROWS_PREFIX=unselected" parameter
            window.location.reload(true);
        }
        else {
            _toggleAllRows(this, false);
            this.removeMessage('selection');
        }
    };

    /**
     * Get selected items on the current page of the DataRegion.
     * Note, if the region is paginated, selected items may exist on other pages.
     * @see LABKEY.DataRegion#getSelected
     */
    Proto.getChecked = function() {
        var values = [];
        _getRowSelectors(this).each(function() {
            if (this.checked) {
                values.push(this.value);
            }
        });
        return values;
    };

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
    Proto.getSelected = function(config) {
        if (!this.selectionKey)
            return;

        config = config || {};
        config.selectionKey = this.selectionKey;
        LABKEY.DataRegion2.getSelected(config);
    };

    /**
     * Returns the number of selected rows on the current page of the DataRegion. Selected items may exist on other pages.
     * @returns {Integer} the number of selected rows on the current page of the DataRegion.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    Proto.getSelectionCount = function() {
        if (!$('dataregion_' + this.name)) {
            return 0;
        }

        var count = 0;
        _getRowSelectors(this).each(function() {
            if (this.checked === true) {
                count++;
            }
        });

        return count;
    };

    /**
     * Returns true if any row is checked on the current page of the DataRegion. Selected items may exist on other pages.
     * @returns {Boolean} true if any row is checked on the current page of the DataRegion.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    Proto.hasSelected = function() {
        return this.getSelectionCount() > 0;
    };

    /**
     * Returns true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @returns {Boolean} true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    Proto.isPageSelected = function() {
        var checkboxes = _getRowSelectors(this);
        var i=0;

        for (; i < checkboxes.length; i++) {
            if (!checkboxes[i].checked) {
                return false;
            }
        }
        return i > 0;
    };

    Proto.selectAll = function(config) {
        if (this.selectionKey) {
            config = config || {};
            config.scope = config.scope || this;

            // Either use the selectAllURL provided or create a query config
            // object that can be used with the generic query/selectAll.api action.
            if (this.selectAllURL) {
                config.url = this.selectAllURL;
            }
            else {
                config = LABKEY.Utils.apply(config, this.getQueryConfig());
            }

            config = _chainSelectionCountCallback(this, config);

            LABKEY.DataRegion2.selectAll(config);

            if (this.showRows === "selected") {
                // keep "SHOW_ROWS_PREFIX=selected" parameter
                window.location.reload(true);
            }
            else if (this.showRows === "unselected") {
                _removeParameters(this, [SHOW_ROWS_PREFIX]);
            }
            else {
                _toggleAllRows(this, true);
            }
        }
    };

    /**
     * @see LABKEY.DataRegion#clearSelected
     */
    Proto.selectNone = Proto.clearSelected;

    /**
     * Set the selection state for all checkboxes on the current page of the DataRegion.
     * @param checked whether all of the rows on the current page should be selected or unselected
     * @returns {Array} Array of ids that were selected or unselected.
     *
     * @see LABKEY.DataRegion#setSelected to set selected items on the current page of the DataRegion.
     * @see LABKEY.DataRegion#clearSelected to clear all selected.
     */
    Proto.selectPage = function(checked) {
        var _check = (checked === true);
        var ids = _toggleAllRows(this, _check);
        var me = this;

        if (ids.length > 0) {
            _getAllRowSelectors(this).each(function() { this.checked = _check});
            this.setSelected({
                ids: ids,
                checked: _check,
                success: function(data) {
                    if (data && data.count > 0 && !this.complete) {
                        var count = data.count;
                        var msg;
                        if (me.totalRows) {
                            if (count == me.totalRows) {
                                msg = 'All <span class="labkey-strong">' + this.totalRows + '</span> rows selected.';
                            }
                            else {
                                msg = 'Selected <span class="labkey-strong">' + count + '</span> of ' + this.totalRows + ' rows.';
                            }
                        }
                        else {
                            // totalRows isn't available when showing all rows.
                            msg = 'Selected <span class="labkey-strong">' + count + '</span> rows.';
                        }
                        _showSelectMessage(me, msg);
                    }
                    else {
                        this.removeMessage('selection');
                    }
                }
            });
        }

        return ids;
    };

    /**
     *
     * @param el
     */
    Proto.selectRow = function(el) {
        this.setSelected({
            ids: [el.value],
            checked: el.checked
        });

        if (!el.checked) {
            this.removeMessage('selection');
        }
    };

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
    Proto.setSelected = function(config) {
        if (!config || !LABKEY.Utils.isArray(config.ids) || config.ids.length == 0) {
            return;
        }

        var me = this;
        config = config || {};
        config.selectionKey = this.selectionKey;
        config.scope = config.scope || me;

        config = _chainSelectionCountCallback(this, config);

        var failure = LABKEY.Utils.getOnFailure(config);
        if ($.isFunction(failure)) {
            config.failure = failure;
        }
        else {
            config.failure = function() { me.addMessage('Error sending selection.'); };
        }

        if (config.selectionKey) {
            LABKEY.DataRegion2.setSelected(config);
        }
        else if ($.isFunction(config.success)) {
            // Don't send the selection change to the server if there is no selectionKey.
            // Call the success callback directly.
            config.success.call(config.scope, {count: this.getSelectionCount()});
        }
    };

    //
    // Parameters
    //

    /**
     * Removes all parameters from the DataRegion
     */
    Proto.clearAllParameters = function() {
        //var event = $.Event('beforeclearallparameters');
        //
        //$(this).trigger(event, this);
        //
        //if (event.isDefaultPrevented()) {
        //    return;
        //}

        if (this.async) {
            this.offset = 0;
            this.parameters = undefined;
        }

        _removeParameters(this, [PARAM_PREFIX, OFFSET_PREFIX]);
    };

    Proto.getParameter = function(paramName) {
        var pairs = _getParameters(this, this.getSearchString()), param = null;
        $.each(pairs, function(i, pair) {
            if (pair.length > 0 && pair[0] == paramName) {
                if (pair.length > 1) {
                    param = pair[1];
                }
                else {
                    param = '';
                }
                return false;
            }
        });
        return param;
    };

    /**
     * Get the parameterized query values for this query.  These parameters
     * are named by the query itself.
     * @param {boolean} toLowercase If true, all parameter names will be converted to lowercase
     * returns params An Object of key/val pairs.
     */
    Proto.getParameters = function(toLowercase) {

        var results = {};

        if (this.qwp) {
            results = this.qwp.getParameters();
        }
        else {
            var params = _getParameters(this, this.getSearchString()),
                re = new RegExp('^' + LABKEY.Utils.escapeRe(this.name) + PARAM_PREFIX.replace(/\./g, '\\.'), 'i'),
                name;

            $.each(params, function(i, pair) {
                if (pair.length > 0 && pair[0].match(re)) {
                    name = pair[0].replace(re, '');
                    if (toLowercase) {
                        name = name.toLowerCase();
                    }
                    results[name] = pair[1];
                }
            });
        }

        return results;
    };

    /**
     * Set the parameterized query values for this query.  These parameters
     * are named by the query itself.
     * @param {Mixed} params An Object or Array of Array key/val pairs.
     */
    Proto.setParameters = function(params) {
        var event = $.Event('beforesetparameters');

        $(this).trigger(event);

        if (event.isDefaultPrevented()) {
            return;
        }

        var me = this, _params;

        // convert Object into Array of Array pairs and prefix the parameter name if necessary.
        if (LABKEY.Utils.isObject(params)) {
            _params = [];
            $.each(params, function(key, value) {
                if (key.indexOf(me.name + PARAM_PREFIX) !== 0) {
                    key = me.name + PARAM_PREFIX + key;
                }
                _params.push([key, value]);
            });
        }
        else {
            _params = params;
        }

        _setParameters(this, _params, [PARAM_PREFIX, OFFSET_PREFIX]);
    };

    /**
     * @Deprecated
     */
    Proto.getSearchString = function() {
        if (!LABKEY.Utils.isString(this.savedSearchString)) {
            this.savedSearchString = document.location.search.substring(1) /* strip the ? */ || "";
        }
        return this.savedSearchString;
    };

    /**
     * @Deprecated
     */
    Proto.setSearchString = function(regionName, search) {
        this.savedSearchString = search || "";
        // If the search string doesn't change and there is a hash on the url, the page won't reload.
        // Remove the hash by setting the full path plus search string.
        window.location.assign(window.location.pathname + "?" + this.savedSearchString);
    };

    //
    // Messaging
    //

    /**
     * @private
     */
    Proto._initMessaging = function() {
        this.msgbox = new MessageArea(this, this.messages);
        this.msgbox.on('rendermsg', function(evt, msgArea, parts) { _onRenderMessageArea(this, parts); }, this);
        if (this.messages) {
            this.msgbox.render();
        }
    };

    /**
     * Show a message in the header of this DataRegion.
     * @param {String / Object} config the HTML source of the message to be shown or a config object witht the following properties:
     *      <ul>
     *          <li><strong>html</strong>: {String} the HTML source of the message to be shown.</li>
     *          <li><strong>part</strong>: {String} The part of the message area to render the message to.</li>
     *          <li><strong>duration</strong>: {Integer} The amount of time (in milliseconds) the message will stay visible.</li>
     *          <li><strong>hideButtonPanel</strong>: {Boolean} If true the button panel (customize view, export, etc.) will be hidden if visible.</li>
     *      </ul>
     * @param part The part of the message are to render the message to. Used to scope messages so they can be added
     *      and removed without clearing other messages.
     * @return {Ext.Element} The Ext.Element of the newly created message div.
     */
    Proto.addMessage = function(config, part) {
        if (LABKEY.Utils.isString(config)) {
            this.msgbox.addMessage(config, part);
        }
        else if (LABKEY.Utils.isObject(config)) {
            this.msgbox.addMessage(config.html, config.part || part);

            if (config.hideButtonPanel) {
                this.hideButtonPanel();
            }

            if (config.duration) {
                var dr = this; var timeout = config.duration;
                setTimeout(function() {
                    dr.removeMessage(config.part || part);
                    _getHeaderSelector(dr).trigger('resize');
                }, timeout);
            }
        }
    };

    /**
     * Clear the message box contents.
     */
    Proto.clearMessage = function() {
        if (this.msgbox) this.msgbox.clear();
    };

    /**
     * If a message is currently showing, hide it and clear out its contents
     */
    Proto.hideMessage = function() {
        if (this.msgbox) { this.msgbox.hide(); }
    };

    /**
     * Returns true if a message is currently being shown for this DataRegion. Messages are shown as a header.
     * @return {Boolean} true if a message is showing.
     */
    Proto.isMessageShowing = function() {
        return this.msgbox && this.msgbox.isVisible();
    };

    /**
     * Removes all messages from this Data Region.
     */
    Proto.removeAllMessages = function() {
        if (this.msgbox) { this.msgbox.removeAll(); }
    };

    /**
     * If a message is currently showing, remove the specified part
     */
    Proto.removeMessage = function(part) {
        if (this.msgbox) { this.msgbox.removeMessage(part); }
    };

    /**
     * Show a message in the header of this DataRegion with a loading indicator.
     * @param html the HTML source of the message to be shown
     */
    Proto.showLoadingMessage = function(html) {
        html = html || "Loading...";
        this.addMessage('<div><span class="loading-indicator">&nbsp;</span><em>' + html + '</em></div>', 'drloading');
    };

    Proto.hideLoadingMessage = function() {
        this.removeMessage('drloading');
    };

    /**
     * Show a success message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    Proto.showSuccessMessage = function(html) {
        html = html || "Completed successfully.";
        this.addMessage('<div class="labkey-message">' + html + '</div>');
    };

    /**
     * Show an error message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    Proto.showErrorMessage = function(html) {
        html = html || "An error occurred.";
        this.addMessage('<div class="labkey-error">' + html + '</div>');
    };

    /**
     * Show a message in the header of this DataRegion.
     * @param msg the HTML source of the message to be shown
     * @deprecated use addMessage(msg, part) instead.
     */
    Proto.showMessage = function(msg) {
        if (this.msgbox) {
            this.msgbox.addMessage(msg);
        }
    };

    Proto.showMessageArea = function() {
        if (this.msgbox) {
            this.msgbox.render();
        }
    };

    //
    // Sorting
    //

    /**
     * Replaces the sort on the given column, if present, or sets a brand new sort
     * @param {string or LABKEY.FieldKey} fieldKey name of the column to be sorted
     * @param sortDirection either "+' for ascending or '-' for descending
     */
    Proto.changeSort = function(fieldKey, sortDir) {
        if (!fieldKey)
            return;

        fieldKey = _resolveFieldKey(this, fieldKey);

        var columnName = fieldKey.toString();

        var event = $.Event("beforesortchange");

        $(this).trigger(event, [this, columnName, sortDir]);

        if (event.isDefaultPrevented()) {
            return;
        }

        var sortString = _alterSortString(this, this.getParameter(this.name + SORT_PREFIX), fieldKey, sortDir);
        _setParameter(this, SORT_PREFIX, sortString, [SORT_PREFIX, OFFSET_PREFIX]);
    };

    /**
     * Removes the sort on a specified column
     * @param {string or LABKEY.FieldKey} fieldKey name of the column
     */
    Proto.clearSort = function(fieldKey) {
        if (!fieldKey)
            return;

        fieldKey = _resolveFieldKey(this, fieldKey);

        var columnName = fieldKey.toString();

        var event = $.Event("beforeclearsort");

        $(this).trigger(event, [this, columnName]);

        if (event.isDefaultPrevented()) {
            return;
        }

        var sortString = _alterSortString(this, this.getParameter(this.name + SORT_PREFIX), fieldKey);
        if (sortString.length > 0) {
            _setParameter(this, SORT_PREFIX, sortString, [SORT_PREFIX, OFFSET_PREFIX]);
        }
        else {
            _removeParameters(this, [SORT_PREFIX, OFFSET_PREFIX]);
        }
    };

    /**
     * Returns the user sort from the URL. The sort is represented as an Array of objects of the form:
     * <ul>
     *   <li><b>fieldKey</b>: {String} The field key of the sort.
     *   <li><b>dir</b>: {String} The sort direction, either "+" or "-".
     * </ul>
     * @returns {Object} Object representing the user sort.
     */
    Proto.getUserSort = function() {
        var userSort = [];
        var sortParam = this.getParameter(this.name + SORT_PREFIX);
        if (sortParam) {
            var sorts = sortParam.split(','), fieldKey, dir;
            $.each(sorts, function(i, sort) {
                fieldKey = sort;
                dir = SORT_ASC;
                if (sort.charAt(0) == SORT_DESC) {
                    fieldKey = fieldKey.substring(1);
                    dir = SORT_DESC;
                }
                else if (sort.charAt(0) == SORT_ASC) {
                    fieldKey = fieldKey.substring(1);
                }
                userSort.push({fieldKey: fieldKey, dir: dir});
            });
        }

        return userSort;
    };

    //
    // Paging
    //

    Proto._initPaging = function() {
        _getHeaderSelector(this).find('div.labkey-pagination').css('visibility', 'visible');
    };

    /**
     * Forces the grid to show all rows, without any paging
     */
    Proto.showAllRows = function() {
        _showRows(this, 'all');
    };
    Proto.showAll = Proto.showAllRows;

    /**
     * Forces the grid to show only rows that have been selected
     */
    Proto.showSelectedRows = function() {
        _showRows(this, 'selected');
    };
    Proto.showSelected = Proto.showSelectedRows;

    /**
     * Forces the grid to show only rows that have not been selected
     */
    Proto.showUnselectedRows = function() {
        _showRows(this, 'unselected');
    };
    Proto.showUnselected = Proto.showUnselectedRows;

    /**
     * Forces the grid to do paging based on the current maximum number of rows
     */
    Proto.showPaged = function() {
        if (_beforeRowsChange(this, null)) { // lol, what? Handing in null is so lame
            _removeParameters(this, [SHOW_ROWS_PREFIX]);
        }
    };

    /**
     * Displays the first page of the grid
     */
    Proto.showFirstPage = function() {
        this.setPageOffset(0);
    };
    Proto.pageFirst = Proto.showFirstPage;

    /**
     * Changes the current row offset for paged content
     * @param rowOffset row index that should be at the top of the grid
     */
    Proto.setPageOffset = function(rowOffset) {
        var event = $.Event('beforeoffsetchange');

        $(this).trigger(event, [this, rowOffset]);

        if (event.isDefaultPrevented()) {
            return;
        }

        _setParameter(this, OFFSET_PREFIX, rowOffset, [OFFSET_PREFIX, SHOW_ROWS_PREFIX]);
    };
    Proto.setOffset = Proto.setPageOffset;

    /**
     * Changes the maximum number of rows that the grid will display at one time
     * @param newmax the maximum number of rows to be shown
     */
    Proto.setMaxRows = function(newmax) {
        var event = $.Event('beforemaxrowschange'); // Can't this just be a variant of _beforeRowsChange with an extra param?
        $(this).trigger(event, [this, newmax]);
        if (event.isDefaultPrevented()) {
            return;
        }

        _setParameter(this, MAX_ROWS_PREFIX, newmax, [OFFSET_PREFIX, MAX_ROWS_PREFIX, SHOW_ROWS_PREFIX]);
    };

    //
    // Customize View
    //
    Proto._initCustomViews = function() {
        if (this.view && this.view.session) {
            var msg;
            if (this.view.savable) {
                msg = (this.viewName ? "The current view '<em>" + LABKEY.Utils.encodeHtml(this.viewName) + "</em>'" : "The current <em>&lt;default&gt;</em> view") + " is unsaved.";
                msg += " &nbsp;";
                msg += "<span class='labkey-button unsavedview-revert'>Revert</span>";
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-edit'>Edit</span>";
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-save'>Save</span>";
            }
            else {
                msg = ("The current view has been customized.");
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-revert' title='Revert'>Revert</span>";
                msg += "&nbsp;";
                msg += "<span class='labkey-button unsavedview-edit'>Edit</span>";
            }

            // add the customize view message, the link handlers will get added after render in _onRenderMessageArea
            this.addMessage(msg, 'customizeview');
        }
    };

    /**
     * Change the currently selected view to the named view
     * @param {Object} view An object which contains the following properties.
     * @param {String} [view.type] the type of view, either a 'view' or a 'report'.
     * @param {String} [view.viewName] If the type is 'view', then the name of the view.
     * @param {String} [view.reportId] If the type is 'report', then the report id.
     * @param {Object} urlParameters <b>NOTE: Experimental parameter; may change without warning.</b> A set of filter and sorts to apply as URL parameters when changing the view.
     */
    Proto.changeView = function(view, urlParameters) {
        var event = $.Event('beforechangeview');
        $(this).trigger(event, [this, view, urlParameters]);
        if (event.isDefaultPrevented()) {
            return;
        }

        var paramValPairs = [], newSort = [];

        if (view) {
            if (view.type == 'report')
                paramValPairs.push([".reportId", view.reportId]);
            else if (view.type == 'view')
                paramValPairs.push([VIEWNAME_PREFIX, view.viewName]);
            else
                paramValPairs.push([VIEWNAME_PREFIX, view]);
        }

        if (urlParameters) {
            $.each(urlParameters.filter, function(i, filter) {
                paramValPairs.push(['.' + filter.fieldKey + '~' + filter.op, filter.value]);
            });

            if (urlParameters.sort && urlParameters.sort.length > 0) {
                $.each(urlParameters.sort, function(i, sort) {
                    newSort.push((sort.dir == "+" ? "" : sort.dir) + sort.fieldKey);
                });
                paramValPairs.push([SORT_PREFIX, newSort.join(',')]);
            }

            if (urlParameters.containerFilter) {
                paramValPairs.push([CONTAINER_FILTER_NAME, urlParameters.containerFilter]);
            }
        }

        // removes all filter, sort, and container filter parameters
        _setParameters(this, paramValPairs, [OFFSET_PREFIX, SHOW_ROWS_PREFIX, VIEWNAME_PREFIX, ".reportId", ALL_FILTERS_SKIP_PREFIX, SORT_PREFIX, ".columns", CONTAINER_FILTER_NAME]);
    };

    Proto.revertCustomView = function() {
        _revertCustomView(this);
    };

    Proto.deleteCustomView = function() {
        var title = "Delete " +
                (this.view && this.view.shared ? "shared " : "your ") +
                (this.view && this.view.session ? "unsaved" : "") + "view";

        var msg = "Are you sure you want to delete the ";
        msg += (this.viewName ? " '<em>" + LABKEY.Utils.encodeHtml(this.viewName) + "</em>'" : "default");
        msg += " saved view";

        if (this.view && this.view.containerPath && this.containerPath != LABKEY.ActionURL.getContainer()) {
            msg += " from '" + this.view.containerPath + "'";
        }
        msg += "?";
        // Assume that customize view is already present -- along with Ext
        Ext.Msg.confirm(title, msg, function (btnId) {
            if (btnId == "yes") {
                _deleteCustomView(this, true, "Deleting view...");
            }
        }, this);
    };

    Proto.getQueryDetails = function(success, failure, scope) {

        var additionalFields = {},
            userFilter = [],
            userSort = this.getUserSort(),
            userColumns = this.getParameter(this.name + '.columns'),
            fields = [],
            viewName = (this.view && this.view.name) || this.viewName || '';

        $.each(this.getUserFilterArray(), function(i, filter) {
            userFilter.push({
                fieldKey: filter.getColumnName(),
                op: filter.getFilterType().getURLSuffix(),
                value: filter.getValue()
            });
        });

        $.each(userSort, function(i, sort) {
            additionalFields[sort.fieldKey] = true;
        });

        $.each(additionalFields, function(fieldKey) {
            fields.push(fieldKey);
        });

        LABKEY.Query.getQueryDetails({
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: viewName,
            fields: fields,
            initializeMissingView: true,
            success: function(queryDetails) {
                success.call(scope || this, queryDetails, viewName, userColumns, userFilter, userSort);
            },
            failure: failure,
            scope: scope
        });
    };

    /**
     * Hides the customize view interface if it is visible.
     */
    Proto.hideCustomizeView = function() {
        if (this.activePanelId === CUSTOM_VIEW_PANELID) {
            this.hidePanel();
        }
    };

    /**
     * Show the customize view interface.
     * @param activeTab {[String]} Optional. One of "ColumnsTab", "FilterTab", or "SortTab".  If no value is specified (or undefined), the ColumnsTab will be shown.
     * @param {Boolean} [hideMessage=false] True to hide the DataRegion message bar when showing.
     */
    Proto.showCustomizeView = function(activeTab, hideMessage) {
        var region = this;
        if (hideMessage) {
            region.hideMessage();
        }

        var panelConfig = this.getPanelConfiguration(CUSTOM_VIEW_PANELID);

        if (!panelConfig) {

            // whistle while we wait
            var timerId = setTimeout(function() {
                timerId = 0;
                region.showLoadingMessage("Opening custom view designer...");
            }, 500);

            LABKEY.DataRegion2.loadViewDesigner(function() {

                var success = function(queryDetails, viewName, userColumns, userFilter, userSort) {
                    timerId > 0 ? clearTimeout(timerId) : this.hideLoadingMessage();

                    // If there was an error parsing the query, we won't be able to render the customize view panel.
                    if (queryDetails.exception) {
                        var viewSourceUrl = LABKEY.ActionURL.buildURL('query', 'viewQuerySource.view', this.containerPath, {
                            schemaName: this.schemaName,
                            'query.queryName': this.queryName
                        });
                        var msg = LABKEY.Utils.encodeHtml(queryDetails.exception) +
                                " &nbsp;<a target=_blank class='labkey-button' href='" + viewSourceUrl + "'>View Source</a>";

                        this.showErrorMessage(msg);
                        return;
                    }

                    var minWidth = Math.max(700, Math.min(1000, _getHeaderSelector(this).width())); // >= 700 && <= 1000

                    this.customizeView = Ext4.create('LABKEY.internal.ViewDesigner.Designer', {
                        renderTo: Ext4.getBody().createChild({tag: 'div', customizeView: true, style: {display: 'none'}}),
                        width: minWidth,
                        activeTab: activeTab,
                        dataRegion: this,
                        containerPath : this.containerPath,
                        schemaName: this.schemaName,
                        queryName: this.queryName,
                        viewName: viewName,
                        query: queryDetails,
                        userFilter: userFilter,
                        userSort: userSort,
                        userColumns: userColumns,
                        userContainerFilter: this.getUserContainerFilter(),
                        allowableContainerFilters: this.allowableContainerFilters
                    });

                    this.customizeView.on('viewsave', function(designer, savedViewsInfo, urlParameters) {
                        _onViewSave.apply(this, [this, designer, savedViewsInfo, urlParameters]);
                    }, this);

                    var first = true;

                    // Called when customize view needs to be shown
                    var showFn = function(id, panel, element, callback, scope) {
                        element.removeClass('extContainer');
                        if (first) {
                            panel.getEl().appendTo(Ext4.get(element[0]));
                            first = false;
                        }
                        panel.doLayout();
                        panel.setVisible(true);
                        Ext4.get(element[0]).slideIn('t', {
                            callback: function() {
                                callback.call(scope);
                            },
                            duration: 400,
                            scope: this
                        });
                    };

                    // Called when customize view needs to be hidden
                    var hideFn = function(id, panel, element, callback, scope) {
                        Ext4.get(element[0]).slideOut('t', {
                            callback: function() {
                                panel.setVisible(false);
                                callback.call(scope);
                            },
                            concurrent: true,
                            duration: 400,
                            scope: this
                        });
                    };

                    this.publishPanel(CUSTOM_VIEW_PANELID, this.customizeView, showFn, hideFn, this);
                    this.showPanel(CUSTOM_VIEW_PANELID);
                };
                var failure = function() {
                    timerId > 0 ? clearTimeout(timerId) : this.hideLoadingMessage();
                };

                this.getQueryDetails(success, failure, this);
            }, region);
        }
        else {
            if (activeTab) {
                panelConfig.panel.setActiveDesignerTab(activeTab);
            }
            this.showPanel(CUSTOM_VIEW_PANELID);
        }
    };

    /**
     * Shows/Hides customize view depending on if it is currently shown
     */
    Proto.toggleShowCustomizeView = function() {
        if (this.activePanelId === CUSTOM_VIEW_PANELID) {
            this.hideCustomizeView();
        }
        else {
            this.showCustomizeView(undefined);
        }
    };

    Proto.publishPanel = function(panelId, panel, showFn, hideFn, scope) {
        this.panelConfigurations[panelId] = {
            panel: panel,
            show: showFn,
            hide: hideFn,
            scope: scope
        };
        return this;
    };

    Proto.getPanelConfiguration = function(panelId) {
        return this.panelConfigurations[panelId];
    };

    /**
     * Hides any panel that is currently visible. Returns a callback once the panel is hidden.
     */
    Proto.hidePanel = function(callback, scope) {
        if (this.activePanelId) {
            var config = this.getPanelConfiguration(this.activePanelId);
            if (config) {

                // find the ribbon container
                var ribbon = _getHeaderSelector(this).find('.labkey-ribbon');

                config.hide.call(config.scope || this, this.activePanelId, config.panel, ribbon, function() {
                    this.activePanelId = undefined;
                    ribbon.hide();
                    if ($.isFunction(callback)) {
                        callback.call(scope || this);
                    }
                }, this);
            }
        }
        else {
            if ($.isFunction(callback)) {
                callback.call(scope || this);
            }
        }
    };

    Proto.showPanel = function(panelId, callback, scope) {

        var config = this.getPanelConfiguration(panelId);

        if (!config) {
            console.error('Unable to find panel for id (' + panelId + '). Use publishPanel() to register a panel to be shown.');
            return;
        }

        // find the ribbon container
        var ribbon = _getHeaderSelector(this).find('.labkey-ribbon');

        this.hidePanel(function() {
            this.activePanelId = panelId;
            ribbon.show();
            config.show.call(config.scope || this, this.activePanelId, config.panel, ribbon, function() {
                if ($.isFunction(callback)) {
                    callback.call(scope || this);
                }
           }, this);
        }, this);
    };

    //
    // Misc
    //

    Proto._initPanes = function() {
        var callbacks = _paneCache[this.name];
        if (callbacks) {
            var me = this;
            $.each(callbacks, function(i, config) {
                config.cb.call(config.scope || me, me);
            });
            delete _paneCache[this.name];
        }
    };

    /**
     * Looks for a column based on fieldKey, name, or caption (in that order)
     * @param columnIdentifier
     * @returns {*}
     */
    Proto.getColumn = function(columnIdentifier) {

        var column = null, // backwards compat
            isString = LABKEY.Utils.isString,
            cols = this.columns;

        if (isString(columnIdentifier) && $.isArray(cols)) {
            $.each(['fieldKey', 'name', 'caption'], function(i, key) {
                $.each(cols, function(c, col) {
                    if (isString(col[key]) && col[key] == columnIdentifier) {
                        column = col;
                        return false;
                    }
                });
                if (column) {
                    return false;
                }
            });
        }

        return column;
    };

    /**
     * Returns a query config object suitable for passing into LABKEY.Query.selectRows() or other LABKEY.Query APIs.
     * @returns {Object} Object representing the query configuration that generated this grid.
     */
    Proto.getQueryConfig = function() {
        var config = {
            dataRegionName: this.name,
            dataRegionSelectionKey: this.selectionKey,
            schemaName: this.schemaName,
            // TODO: handle this.sql ?
            queryName: this.queryName,
            viewName: this.viewName,
            sort: this.getParameter(this.name + SORT_PREFIX),
            // NOTE: The parameterized query values from QWP are included
            parameters: this.getParameters(false),
            containerFilter: this.containerFilter
        };

        var filters = this.getUserFilterArray();
        if (filters.length > 0) {
            config.filters = filters;
        }

        // NOTE: need to account for non-removeable filters and sort in a QWP
        if (this.qwp) {
            if (this.qwp.sort) {
                config.sort = config.sort + ',' + this.qwp.sort;
            }

            if (this.qwp.filters && this.qwp.filters.length) {
                config.filters = config.filters.concat(this.qwp.filters);
            }
        }

        return config;
    };

    /**
     * Hide the ribbon panel. If visible the ribbon panel will be hidden.
     */
    Proto.hideButtonPanel = function() {
        this.hidePanel();
    };

    /**
     * Show a ribbon panel. tabPanelConfig is an ExtJS 3.4 config object for a TabPanel.
     * The only required value is the items array.
     */
    Proto.showButtonPanel = function(panelButton, tabPanelConfig) {

        var panelId = panelButton.getAttribute('panelId');
        if (panelId) {
            if (panelId === this.activePanelId) {
                this.hidePanel();
            }
            else {
                var config = this.getPanelConfiguration(panelId);
                if (!config) {
                    this.publishPanel(panelId, tabPanelConfig, _showExt3Panel, _hideExt3Panel, this);
                }
                this.showPanel(panelId);
            }
        }
    };

    Proto.on = function(evt, callback, scope) {
        // Prevent from handing back the jQuery event itself.
        $(this).bind(evt, function() { callback.apply(scope || this, $(arguments).slice(1)); });
    };

    Proto.headerLock = function() { return this._allowHeaderLock === true; };

    /**
     * @private
     */
    Proto._openFilter = function(columnName) {
        var me = this;
        LABKEY.requiresExt3ClientAPI(true, function() {
            new LABKEY.FilterDialog({
                dataRegionName: me.name,
                column: me.getColumn(columnName)
            }).show();
        });
    };

    //
    // PRIVATE FUNCTIONS
    //
    var _alterSortString = function(region, current, fieldKey, direction /* optional */) {
        fieldKey = _resolveFieldKey(region, fieldKey);

        var columnName = fieldKey.toString(),
                newSorts = [];

        if (current != null) {
            var sorts = current.split(',');
            $.each(sorts, function(i, sort) {
                if ((sort != columnName) && (sort != SORT_ASC + columnName) && (sort != SORT_DESC + columnName)) {
                    newSorts.push(sort);
                }
            });
        }

        if (direction == SORT_ASC) { // Easier to read without the encoded + on the URL...
            direction = '';
        }

        if (LABKEY.Utils.isString(direction)) {
            newSorts = [direction + columnName].concat(newSorts);
        }

        return newSorts.join(',');
    };

    var _beforeRowsChange = function(region, rowChangeEnum) {
        var event = $.Event('beforeshowrowschange');
        $(region).trigger(event, [region, rowChangeEnum]);
        if (event.isDefaultPrevented()) {
            return false;
        }
        return true;
    };

    var _buildQueryString = function(region, pairs) {
        if (!$.isArray(pairs)) {
            return '';
        }

        var queryParts = [], key, value;

        $.each(pairs, function(i, pair) {
            key = pair[0];
            value = pair.length > 1 ? pair[1] : undefined;

            queryParts.push(encodeURIComponent(key));
            if (LABKEY.Utils.isDefined(value)) {

                if (LABKEY.Utils.isDate(value)) {
                    value = $.format.date(value, 'yyyy-MM-dd');
                    if (LABKEY.Utils.endsWith(value, 'Z')) {
                        value = value.substring(0, value.length - 1);
                    }
                }
                queryParts.push('=');
                queryParts.push(encodeURIComponent(value));
            }
            queryParts.push('&');
        });

        if (queryParts.length > 0) {
            queryParts.pop();
        }

        return queryParts.join("");
    };

    var _chainSelectionCountCallback = function(region, config) {
        // On success, update the current selectedCount on this DataRegion and fire the 'selectchange' event
        var updateSelected = function(data) {
            region.selectionModified = true;
            region.selectedCount = data.count;
            _onSelectionChange(region);
        };

        // Chain updateSelected with the user-provided success callback
        var success = LABKEY.Utils.getOnSuccess(config);
        if (success) {
            // TODO: Fix this, it is a feature of ExtJS... http://docs.sencha.com/extjs/3.4.0/#!/api/Ext.util.Functions-method-createSequence
            success = updateSelected.createSequence(success, config.scope);
        }
        else {
            success = updateSelected;
        }

        config.success = success;
        return config;
    };

    var _changeFilter = function(region, newParamValPairs, newQueryString) {

        //var event = $.Event('beforefilterchange');

        var filterPairs = [], name, val;
        $.each(newParamValPairs, function(i, pair) {
            name = pair[0];
            val = pair[1];
            if (name.indexOf(region.name + '.') == 0 && name.indexOf('~') > -1) {
                filterPairs.push([name, val]);
            }
        });

        //$(region).trigger(event, region, filterPairs);
        //if (event.isDefaultPrevented()) {
        //    return;
        //}
        if (region.async) {
            region.offset = 0;

            // reset the user filters
            region.userFilters = {};
            $.each(filterPairs, function(i, fp) {
                region.userFilters[fp[0]] = fp[1];
            });

            _load(region);
        }
        else {
            var params = _getParameters(region, newQueryString, [region.name + OFFSET_PREFIX]);
            region.setSearchString.call(region, region.name, _buildQueryString(region, params));
        }
    };

    var _deleteCustomView = function(region, complete, message) {
        var timerId = setTimeout(function() {
            timerId = 0;
            region.showLoadingMessage(message);
        }, 500);

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'deleteView', region.containerPath),
            jsonData: {schemaName: region.schemaName, queryName: region.queryName, viewName: region.viewName, complete: complete},
            method: 'POST',
            callback: function() {
                if (timerId > 0) { clearTimeout(timerId); }
            },
            success: LABKEY.Utils.getCallbackWrapper(function(json) {
                region.showSuccessMessage.call(region);
                // change view to either a shadowed view or the default view
                region.changeView.call(region, {type: 'view', viewName: json.viewName})
            }, region),
            failure: LABKEY.Utils.getCallbackWrapper(function(json) {
                region.showErrorMessage.call(json.exception);
            }, region, true),
            scope: region
        });
    };

    var _getAllRowSelectors = function(region) {
        var nameSel = '#' + region.name;
        var baseSel = 'form' + nameSel;
        return $(baseSel + ' .labkey-selectors input[type="checkbox"][name=".toggle"]');
    };

    var _getRowSelectors = function(region) {
        var nameSel = '#' + region.name;
        var baseSel = 'form' + nameSel;
        return $(baseSel + ' .labkey-selectors input[type="checkbox"][name=".select"]');
    };

    var _getHeaderSelector = function(region) {
        return $('#dataregion_header_' + region.name);
    };

    // Formerly, LABKEY.DataRegion.getParamValPairs
    var _getParametersSearch = function(region, qString, skipPrefixSet /* optional */) {
        if (!qString) {
            qString = region.getSearchString.call(region);
        }
        return _getParameters(region, qString, skipPrefixSet);
    };

    // Formerly, LABKEY.DataRegion.getParamValPairsFromString
    var _getParameters = function(region, qString, skipPrefixSet /* optional */) {

        var params = [];

        if (LABKEY.Utils.isString(qString) && qString.length > 0) {

            var qmIdx = qString.indexOf('?');
            if (qmIdx > -1) {
                qString = qString.substring(qmIdx + 1);
            }

            var pairs = qString.split('&'), p, key,
                    LAST = '.lastFilter', lastIdx, skip = $.isArray(skipPrefixSet);

            $.each(pairs, function(i, pair) {
                p = pair.split('=', 2);
                key = p[0] = decodeURIComponent(p[0]);
                lastIdx = key.indexOf(LAST);

                if (lastIdx > -1 && lastIdx == (key.length - LAST.length)) {
                    return;
                }

                var stop = false;
                if (skip) {
                    $.each(skipPrefixSet, function(j, skipPrefix) {
                        if (LABKEY.Utils.isString(skipPrefix)) {

                            // Special prefix that should remove all filters, but no other parameters
                            if (skipPrefix.indexOf(ALL_FILTERS_SKIP_PREFIX) == (skipPrefix.length - 2)) {
                                if (key.indexOf('~') > 0) {
                                    stop = true;
                                    return false;
                                }
                            }
                            else if (key.indexOf(skipPrefix) == 0) {
                                // only skip filters, parameters, and sorts
                                if (key == skipPrefix ||
                                        key.indexOf("~") > 0 ||
                                        key.indexOf(PARAM_PREFIX) > 0 ||
                                        key == (skipPrefix + "sort")) {
                                    stop = true;
                                    return false;
                                }
                            }
                        }
                    });
                }

                if (!stop) {
                    if (p.length > 1) {
                        p[1] = decodeURIComponent(p[1]);
                    }
                    params.push(p);
                }
            });
        }

        return params;
    };

    var _buttonBind = function(region, cls, fn) {
        region.msgbox.find('.labkey-button' + cls).on('click', $.proxy(function() {
            fn.call(this);
        }, region));
    };

    var _onRenderMessageArea = function(region, parts) {
        var msgArea = region.msgbox;
        if (msgArea) {
            if (region.showRecordSelectors && parts['selection']) {
                _buttonBind(region, '.select-all', region.selectAll);
                _buttonBind(region, '.select-none', region.clearSelected);
                _buttonBind(region, '.show-all', region.showAll);
                _buttonBind(region, '.show-selected', region.showSelected);
                _buttonBind(region, '.show-unselected', region.showUnselected);
            }
            else if (parts['customizeview']) {
                _buttonBind(region, '.unsavedview-revert', function() { _revertCustomView(this); });
                _buttonBind(region, '.unsavedview-edit', function() { this.showCustomizeView(undefined, true); });
                _buttonBind(region, '.unsavedview-save', function() { _saveSessionCustomView(this); });
            }
        }
    };

    var _onSelectionChange = function(region) {
        $(region).trigger('selectchange', [region, region.selectedCount]);
        _updateRequiresSelectionButtons(region, region.selectedCount);
    };

    var _onViewSave = function(region, designer, savedViewsInfo, urlParameters) {
        if (savedViewsInfo && savedViewsInfo.views.length > 0) {
            region.hideCustomizeView.call(region);
            region.changeView.call(region, {
                type: 'view',
                viewName: savedViewsInfo.views[0].name
            }, urlParameters);
        }
    };

    var _removeParameters = function(region, skipPrefixes /* optional */) {
        return _setParameters(region, null, skipPrefixes);
    };

    var _revertCustomView = function(region) {
        _deleteCustomView(region, false, 'Reverting view...');
    };

    var _resolveFieldKey = function(region, fieldKey) {
        var fk = fieldKey;
        if (!(fk instanceof LABKEY.FieldKey)) {
            fk = LABKEY.FieldKey.fromString('' + fk);
        }
        return fk;
    };

    var _saveSessionCustomView = function(region) {
        // Note: currently only will save session views. Future version could create a new view using url sort/filters.
        if (!(region.view && region.view.session)) {
            return;
        }

        // Get the canEditSharedViews permission and candidate targetContainers.
        var viewName = (this.view && this.view.name) || this.viewName || '';
        LABKEY.Query.getQueryDetails({
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: viewName,
            initializeMissingView: false,
            success: function (json) {
                // Display an error if there was an issue error getting the query details
                if (json.exception) {
                    var viewSourceUrl = LABKEY.ActionURL.buildURL('query', 'viewQuerySource.view', null, {schemaName: this.schemaName, "query.queryName": this.queryName});
                    var msg = LABKEY.Utils.encodeHtml(json.exception) + " &nbsp;<a target=_blank class='labkey-button' href='" + viewSourceUrl + "'>View Source</a>";

                    this.showErrorMessage.call(this, msg);
                    return;
                }

                _saveSessionShowPrompt(this, json);
            },
            scope: region
        });
    };

    var _saveSessionShowPrompt = function(region, queryDetails) {
        var config = Ext4.applyIf({
            allowableContainerFilters: region.allowableContainerFilters,
            targetContainers: queryDetails.targetContainers,
            canEditSharedViews: queryDetails.canEditSharedViews,
            canEdit: this.getCustomViewEditableErrors(config).length == 0,
            success: function (win, o) {
                var timerId = setTimeout(function() {
                    timerId = 0;
                    Ext4.Msg.progress("Saving...", "Saving custom view...");
                }, 500);

                var jsonData = {
                    schemaName: region.schemaName,
                    "query.queryName": region.queryName,
                    "query.viewName": region.viewName,
                    newName: o.name,
                    inherit: o.inherit,
                    shared: o.shared
                };

                if (o.inherit) {
                    jsonData.containerPath = o.containerPath;
                }

                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('query', 'saveSessionView', region.containerPath),
                    method: 'POST',
                    jsonData: jsonData,
                    callback: function() {
                        if (timerId > 0)
                            clearTimeout(timerId);
                        win.close();
                        Ext4.Msg.hide();
                    },
                    success: function() {
                        region.showSuccessMessage.call(region);
                        region.changeView({type: 'view', viewName: o.name});
                    },
                    failure: function(json) {
                        Ext4.Msg.alert('Error saving view', json.exception);
                    },
                    scope: region
                });
            },
            scope: region
        }, region.view);

        LABKEY.DataRegion2.saveCustomizeViewPrompt(config);
    };

    var _setParameter = function(region, param, value, skipPrefixes /* optional */) {
        _setParameters(region, [[param, value]], skipPrefixes);
    };

    var _setParameters = function(region, newParamValPairs, skipPrefixes /* optional */) {

        if ($.isArray(skipPrefixes)) {
            $.each(skipPrefixes, function(i, skip) {
                skipPrefixes[i] = region.name + skip;
            });
        }

        var param, value,
                params = _getParametersSearch(region, region.requestURL, skipPrefixes);

        if ($.isArray(newParamValPairs)) {
            $.each(newParamValPairs, function(i, newPair) {
                if (!$.isArray(newPair)) {
                    throw new Error("DataRegion: _setParameters newParamValPairs improperly initialized. It is an array of arrays. You most likely passed in an array of strings.");
                }
                param = newPair[0];
                value = newPair[1];

                // Allow value to be null/undefined to support no-value filter types (Is Blank, etc)
                if (LABKEY.Utils.isString(param)) {
                    if (param.indexOf(region.name) !== 0) {
                        param = region.name + param;
                    }

                    params.push([param, value]);
                }
            });
        }

        if (region.async) {
            _load(region);
        }
        else {
            region.setSearchString.call(region, region.name, _buildQueryString(region, params));
        }
    };

    var _showRows = function(region, showRowsEnum) {
        if (_beforeRowsChange(region, showRowsEnum)) {
            _setParameter(region, SHOW_ROWS_PREFIX, showRowsEnum, [OFFSET_PREFIX, MAX_ROWS_PREFIX, SHOW_ROWS_PREFIX]);
        }
    };

    var _showSelectMessage = function(region, msg) {
        if (region.showRecordSelectors) {
            if (region.totalRows && region.totalRows != region.selectedCount) {
                msg += "&nbsp;<span class='labkey-button select-all'>Select All " + region.totalRows + " Rows</span>";
            }

            msg += "&nbsp;" + "<span class='labkey-button select-none'>Select None</span>";
            var showOpts = [];
            if (region.showRows != "all")
                showOpts.push("<span class='labkey-button show-all'>Show All</span>");
            if (region.showRows != "selected")
                showOpts.push("<span class='labkey-button show-selected'>Show Selected</span>");
            if (region.showRows != "unselected")
                showOpts.push("<span class='labkey-button show-unselected'>Show Unselected</span>");
            msg += "&nbsp;&nbsp;" + showOpts.join(" ");
        }

        // add the record selector message, the link handlers will get added after render in _onRenderMessageArea
        region.addMessage.call(region, msg, 'selection');
    };

    var _toggleAllRows = function(region, checked) {
        var ids = [];

        _getRowSelectors(region).each(function() {
            if (!this.disabled) {
                this.checked = checked;
                ids.push(this.value);
            }
        });

        _getAllRowSelectors(region).each(function() { this.checked = (checked == true)});
        return ids;
    };

    var _showExt3Panel = function(id, tabPanelConfig, element, callback, scope) {
        LABKEY.requiresExt3ClientAPI(true, function() {
            // can assume Ext 3 exists as 'Ext'
            element.addClass('extContainer');
            var panelDiv = Ext.get(element[0]);

            // determine if the tab panel needs to be constructed
            if (!$.isFunction(tabPanelConfig.getEl)) {

                var minWidth = 700,
                    tabContentWidth = 0,
                    newItems = [];

                // New up the TabPanel if we haven't already
                // Only create one per button, even if that button is rendered both above and below the grid
                tabPanelConfig.cls = 'vertical-tabs';
                tabPanelConfig.tabWidth = 80;
                tabPanelConfig.renderTo = panelDiv;
                tabPanelConfig.activeGroup = 0;

                // create the newItems
                $.each(tabPanelConfig.items, function(i, item) {
                    item.autoScroll = true;

                    //FF and IE won't auto-resize the tab panel to fit the content
                    //so we need to calculate the min size and set it explicitly
                    if (Ext.isGecko || Ext.isIE) {
                        if (!item.events) {
                            item = Ext.create(item, 'grouptab');
                        }
                        item.removeClass('x-hide-display');
                        if (item.items.getCount() > 0 && item.items.items[0].contentEl) {
                            tabContentWidth = Ext.get(item.items.items[0].contentEl).getWidth();
                            item.addClass('x-hide-display');
                            minWidth = Math.min(minWidth, tabContentWidth);
                        }
                    }

                    newItems.push(item);
                });

                tabPanelConfig.items = newItems;
                if ((Ext.isGecko || Ext.isIE) && minWidth > 0 && header.getWidth() < minWidth) {
                    tabPanelConfig.width = minWidth;
                }

                // re-publish the panel as the panel rather than the panel configuration
                tabPanelConfig = new Ext.ux.GroupTabPanel(tabPanelConfig);
                this.publishPanel(id, tabPanelConfig, _showExt3Panel, _hideExt3Panel, this);
            }

            tabPanelConfig.getEl().setVisible(true);
            tabPanelConfig.show();
            tabPanelConfig.getEl().slideIn('t', {
                callback: function() {
                    callback.call(scope);
                },
                duration: 0.4,
                scope: this
            });

            callback.call(scope);
        }, this);
    };

    var _hideExt3Panel = function(id, panel, element, callback, scope) {
        var region = this;

        var doHide = function() {
            panel.hide();
            panel.getEl().setVisible(false);
            $(region).trigger('afterpanelhide');
            callback.call(scope);
        };

        panel.getEl().slideOut('t', {
            callback: doHide,
            concurrent: true,
            duration: 0.4,
            scope: region
        });
    };

    var _load = function(region, callback, scope) {

        var params = _getAsyncParams(region);
        var jsonData = _getAsyncBody(region, params);

        // TODO: This should be done in _getAsyncParams, but is not since _getAsyncBody relies on it. Refactor it.
        // ensure SQL is not on the URL -- we allow any property to be pulled through when creating parameters.
        if (params.sql) {
            delete params.sql;
        }

        var renderTo = region.renderTo || region.name;

        LABKEY.Ajax.request({
            timeout: (region.timeout == undefined) ? 30000 : region.timeout,
            url: LABKEY.ActionURL.buildURL('project', 'getWebPart', region.containerPath),
            method: 'POST',
            params: params,
            jsonData: jsonData,
            success: function(response) {

                //var target;
                //if (LABKEY.Utils.isString(this.renderTo)) {
                //    target = this.renderTo;
                //}
                //else {
                //    target = this.renderTo.id;
                //}

                var target = $('#' + renderTo);
                console.log('target:', '#' + renderTo);
                if (target.length > 0) {

                    //if (dr) {
                    //    dr.destroy();
                    //}

                    LABKEY.Utils.loadAjaxContent(response, target, function() {

                        if (LABKEY.Utils.isFunction(callback)) {
                            callback.call(scope);
                        }
                    });
                }
            },
            scope: region
        });
    };

    var _getAsyncBody = function(region, params) {
        var json = {};

        if (params.sql) {
            json.sql = params.sql;
        }

        if (region.buttonBar && (region.buttonBar.position || (region.buttonBar.items && region.buttonBar.items.length > 0))) {
            json.buttonBar = _processButtonBar(region);
        }

        // 10505: add non-removable sorts and filters to json (not url params).  These will be handled in QueryWebPart.java
        json.filters = {};
        if (region.filters) {
            LABKEY.Filter.appendFilterParams(json.filters, region.filters, region.name);
        }

        if (region.metadata) {
            json.metadata = region.metadata;
        }

        return json;
    };

    var _processButtonBar = function(region) {

    };

    var _getAsyncParams = function(region) {
        var params = {
            dataRegionName: region.name,
            "webpart.name": 'Query',
            schemaName: region.schemaName,
            queryName: region.queryName
        }, name = region.name;

        if (region.viewName) {
            params[name + VIEWNAME_PREFIX] = region.viewName;
        }
        if (region.reportId) {
            params[name + '.reportId'] = region.reportId;
        }

        var cf = region.getContainerFilter.call(region);
        if (cf) {
            params[name + CONTAINER_FILTER_NAME] = cf;
        }

        if (region.showRows) {
            params[name + SHOW_ROWS_PREFIX] = region.showRows;
        }

        if (region.maxRows > 0) { // lol, what?
            params[name + MAX_ROWS_PREFIX] = region.maxRows;
        }

        if (region.offset) {
            params[name + OFFSET_PREFIX] = region.offset;
        }

        if (region.quickChartDisabled) {
            params[name + '.quickChartDisabled'] = region.quickChartDisabled;
        }

        //
        // Certain parameters are only included if the region is 'async'. These
        // were formerly a part of Query Web Part.
        //
        if (region.async) {
            params[name + '.async'] = true;

            if (LABKEY.Utils.isString(region.frame)) {
                params["webpart.frame"] = region.frame
            }

            // Sorts configured by the user when interacting with the grid. We need to pass these as URL parameters.
            if (LABKEY.Utils.isString(region.userSort) && region.userSort.length > 0) {
                params[name + SORT_PREFIX] = region.userSort;
            }

            if (region.userFilters) {
                $.each(region.userFilters, function(filterExp, filterValue) {
                    params[filterExp] = filterValue;
                });
            }

            // TODO: Get rid of this and incorporate it with the normal containerFilter checks
            if (region.userContainerFilter) {
                params[name + CONTAINER_FILTER_NAME] = region.userContainerFilter;
            }

            if (region.parameters) {
                $.each(region.parameters, function(parameter, value) {
                    var p = parameter;
                    if (parameter.indexOf(name + PARAM_PREFIX) !== 0) {
                        p = name + PARAM_PREFIX + parameter;
                    }
                    params[p] = value;
                });
            }
        }

        // Ext uses a param called _dc to defeat caching, and it may be
        // on the URL if the Query web part has done a sort or filter
        // strip it if it's there so it's not included twice (Ext always appends one)
        delete params['_dc'];

        return params;
    };

    var _updateFilter = function(region, filter, skipPrefixes) {
        var params = _getParameters(region, region.requestURL, skipPrefixes);
        if (filter) {
            params.push([filter.getURLParameterName(region.name), filter.getURLParameterValue()]);
        }
        _changeFilter(region, params, _buildQueryString(region, params));
    };

    var _updateRequiresSelectionButtons = function(region, selectedCount) {

        // update the 'select all on page' checkbox state
        _getAllRowSelectors(region).each(function() {
            if (region.isPageSelected.call(region)) {
                this.checked = true;
                this.indeterminate = false;
            }
            else if (region.selectedCount > 0) {
                // There are rows selected, but the are not visible on this page.
                this.checked = false;
                this.indeterminate = true;
            }
            else {
                this.checked = false;
                this.indeterminate = false;
            }
        });

        // If all rows have been selected (but not all rows are visible), show selection message
        if (region.totalRows && region.selectedCount == region.totalRows && !region.complete) {
            _showSelectMessage(region, 'All <span class="labkey-strong">' + region.totalRows + '</span> rows selected.');
        }

        // 10566: for javascript perf on IE stash the requires selection buttons
        if (!region._requiresSelectionButtons) {
            // escape ', ", and \
            var escaped = region.name.replace(/('|"|\\)/g, "\\$1");
            region._requiresSelectionButtons = $("a[labkey-requires-selection='" + escaped + "']");
        }

        region._requiresSelectionButtons.each(function() {
            var el = $(this);

            // handle min-count
            var minCount = el.attr('labkey-requires-selection-min-count');
            if (minCount) {
                minCount = parseInt(minCount.value);
            }
            if (minCount === undefined) {
                minCount = 1;
            }

            // handle max-count
            var maxCount = el.attr('labkey-requires-selection-max-count');
            if (maxCount) {
                maxCount = parseInt(maxCount.value);
            }

            if (minCount <= selectedCount && (!maxCount || maxCount >= selectedCount)) {
                el.addClass('labkey-button').removeClass('labkey-disabled-button');
            }
            else {
                el.addClass('labkey-disabled-button').removeClass('labkey-button');
            }
        });
    };

    LABKEY.DataRegion2.loadViewDesigner = function(cb, scope) {
        LABKEY.requiresExt4Sandbox(true, function() {
            LABKEY.requiresScript([
                'internal/ViewDesigner/data/Cache.js',
                'internal/ViewDesigner/ux/ComponentDataView.js',
                'internal/ViewDesigner/button/PaperclipButton.js',
                'internal/ViewDesigner/field/FilterOpCombo.js',
                'internal/ViewDesigner/field/FilterTextValue.js',
                'internal/ViewDesigner/tab/BaseTab.js',
                'internal/ViewDesigner/tab/ColumnsTab.js',
                'internal/ViewDesigner/tab/FilterTab.js',
                'internal/ViewDesigner/tab/SortTab.js',
                'internal/ViewDesigner/FieldMetaRecord.js',
                'internal/ViewDesigner/FieldMetaStore.js',
                //'internal/ViewDesigner/FieldTreeLoader.js',
                'internal/ViewDesigner/Designer.js'
            ], true, cb, scope);
        });
    };

    LABKEY.DataRegion2.getCustomViewEditableErrors = function(customView) {
        var errors = [];
        if (customView && !customView.editable) {
            errors.push("The view is read-only and cannot be edited.");
        }
        return errors;
    };

    LABKEY.DataRegion2.registerPane = function(regionName, callback, scope) {
        var region = LABKEY.DataRegions[regionName];
        if (region) {
            callback.call(scope || region, region);
            return;
        }
        else if (!_paneCache[regionName]) {
            _paneCache[regionName] = [];
        }

        _paneCache[regionName].push({cb: callback, scope: scope});
    };

    LABKEY.DataRegion2.selectAll = function(config) {
        var params = {};
        if (!config.url) {
            // DataRegion doesn't have selectAllURL so generate url and query parameters manually
            config.url = LABKEY.ActionURL.buildURL('query', 'selectAll.api', config.containerPath);

            config.dataRegionName = config.dataRegionName || 'query';

            params = LABKEY.Query.buildQueryParams(
                    config.schemaName,
                    config.queryName,
                    config.filters,
                    null,
                    config.dataRegionName
            );

            if (config.viewName)
                params[config.dataRegionName + VIEWNAME_PREFIX] = config.viewName;

            if (config.containerFilter)
                params.containerFilter = config.containerFilter;

            if (config.selectionKey)
                params[config.dataRegionName + '.selectionKey'] = config.selectionKey;

            $.each(config.parameters, function(propName, value) {
                params[config.dataRegionName + PARAM_PREFIX + propName] = value;
            });

            if (config.ignoreFilter) {
                params[config.dataRegionName + '.ignoreFilter'] = true;
            }

            // NOTE: ignore maxRows, showRows, and offset
        }

        LABKEY.Ajax.request({
            url: config.url,
            method: 'POST',
            params: params,
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    };

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
    LABKEY.DataRegion2.setSelected = function(config) {
        // Formerly LABKEY.DataRegion.setSelected
        var url = LABKEY.ActionURL.buildURL("query", "setSelected.api", config.containerPath,
                { 'key': config.selectionKey, 'checked': config.checked });

        LABKEY.Ajax.request({
            url: url,
            method: "POST",
            params: { id: config.ids || config.id },
            scope: config.scope,
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
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
    LABKEY.DataRegion2.clearSelected = function(config) {
        var url = LABKEY.ActionURL.buildURL('query', 'clearSelected.api', config.containerPath,
                { 'key': config.selectionKey });

        LABKEY.Ajax.request({ url: url });
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
    LABKEY.DataRegion2.getSelected = function(config) {
        var url = LABKEY.ActionURL.buildURL('query', 'getSelected.api', config.containerPath,
                { 'key': config.selectionKey });

        LABKEY.Ajax.request({
            url: url,
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    };

    /**
     * MessageArea wraps the display of messages in a DataRegion.
     * @param dataRegion - The dataregion that the MessageArea will bind itself to.
     * @param messages - An initial messages object containing mappings of 'part' to 'msg'
     * @constructor
     */
    var MessageArea = function(dataRegion, messages) {
        this.parentSel = '#dataregion_msgbox_' + dataRegion.name;
        this.parent = $(this.parentSel);

        // prepare containing div
        this.parent.find('td.labkey-dataregion-msgbox').append('<div class="dataregion_msgbox_ct"></div>');

        if (LABKEY.Utils.isObject(messages)) {
            this.parts = messages;
        }
        else {
            this.parts = {};
        }
    };

    var MsgProto = MessageArea.prototype;

    MsgProto.toJSON = function() {
        return this.parts;
    };

    MsgProto.addMessage = function(msg, part) {
        part = part || 'info';
        this.parts[part.toLowerCase()] = msg;
        this.render();
    };

    MsgProto.getMessage = function(part) {
        return this.parts[part.toLowerCase()];
    };

    MsgProto.removeAll = function() {
        this.parts = {};
        this.render();
    };

    MsgProto.removeMessage = function(part) {
        var p = part.toLowerCase();
        if (this.parts.hasOwnProperty(p)) {
            delete this.parts[p];
            this.render();
        }
    };

    MsgProto.render = function() {
        var hasMsg = false, html = '';
        $.each(this.parts, function(part, msg) {
            if (msg) {
                if (hasMsg) {
                    html += '<hr>';
                }
                hasMsg = true;
                html += '<div class="labkey-dataregion-msg">' + msg + '</div>';
            }
        });

        if (hasMsg) {
            this.parent.find('.dataregion_msgbox_ct').html(html);
            this.show();
            $(this).trigger('rendermsg', [this, this.parts]);
        }
        else {
            this.hide();
            this.parent.find('.dataregion_msgbox_ct').html('');
        }
    };

    MsgProto.show = function() { this.parent.show(); };
    MsgProto.hide = function() { this.parent.hide(); };
    MsgProto.isVisible = function() { return $(this.parentSel + ':visible').length > 0; };
    MsgProto.find = function(selector) {
        return this.parent.find('.dataregion_msgbox_ct').find(selector);
    };
    MsgProto.on = function(evt, callback, scope) { $(this).bind(evt, $.proxy(callback, scope)); };


    // TODO: Make this work
    //var qwp1 = new LABKEY.QueryWebPart({
    //    renderTo: '<%=h(queryWebPartDivId)%>',
    //    frame: 'none',
    //    schemaName: 'genotyping',
    //    sql: this.getAssignmentPivotSQL(idArr, searchId, displayId),
    //    showDetailsColumn: false,
    //    dataRegionName: 'report',
    //    buttonBar: {
    //        includeStandardButtons: false,
    //        items:[
    //            LABKEY.QueryWebPart.standardButtons.exportRows,
    //            LABKEY.QueryWebPart.standardButtons.print,
    //            LABKEY.QueryWebPart.standardButtons.pageSize
    //        ]
    //    }
    //});

    LABKEY.QueryWebPart2 = function(config) {

        var _config = config || {};

        var defaults = {
            renderTo: undefined,
            dataRegionName: LABKEY.Utils.id('aqwp'),
            returnURL: window.location.href,
            _success: LABKEY.Utils.getOnSuccess(_config),
            _failure: LABKEY.Utils.getOnFailure(_config),
            filters: [],
            errorType: 'html',
            parameters: undefined
        };

        var settings = $.extend({}, defaults, _config);

        for (var s in settings) {
            if (settings.hasOwnProperty(s)) {
                this[s] = settings[s];
            }
        }

        // Get/Construct the Data Region based on the current configuration
        var region = LABKEY.DataRegions[this.dataRegionName];
        if (region) {
            this.region = region;
        }
        else {
            this.region = new LABKEY.DataRegion2({
                name: this.dataRegionName,
                schemaName: this.schemaName,
                queryName: this.queryName
            })
        }

        // QWP's are setup to be "in-place"
        this.region.async = true;

        if (this.renderTo) {
            this.render();
        }
    };

    LABKEY.QueryWebPart2.prototype.render = function(renderTo) {

        if (renderTo) {
            this.renderTo = renderTo;
        }

        if (this.renderTo) {
            this.region.renderTo = this.renderTo;
            _load(this.region, function() {
                if (LABKEY.DataRegions[this.dataRegionName]) {
                    this.region = LABKEY.DataRegions[this.dataRegionName];
                }
            }, this);
        }
        else {
            throw '"renderTo" must be specified either upon construction or when calling LABKEY.QueryWebpart2.render.';
        }
    };

    LABKEY.QueryWebPart2.prototype.getDataRegion = function() {
        return this.region;
    };

})(jQuery);

/**
 * A read-only object that exposes properties representing standard buttons shown in LabKey data grids.
 * These are used in conjunction with the buttonBar configuration. The following buttons are currently defined:
 * <ul>
 *  <li>LABKEY.QueryWebPart.standardButtons.query</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.views</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.insertNew</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.deleteRows</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.exportRows</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.print</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.pageSize</li>
 * </ul>
 * @name standardButtons
 * @memberOf LABKEY.QueryWebPart#
 */
LABKEY.QueryWebPart2.standardButtons = {
    query: 'query',
    views: 'views',
    insertNew: 'insert new',
    deleteRows: 'delete',
    exportRows: 'export',
    print: 'print',
    pageSize: 'page size'
};

LABKEY.AggregateTypes = {
    /**
     * Displays the sum of the values in the specified column
     */
    SUM: 'sum',
    /**
     * Displays the average of the values in the specified column
     */
    AVG: 'avg',
    /**
     * Displays the count of the values in the specified column
     */
    COUNT: 'count',
    /**
     * Displays the maximum value from the specified column
     */
    MIN: 'min',
    /**
     * Displays the minimum values from the specified column
     */
    MAX: 'max'
};
