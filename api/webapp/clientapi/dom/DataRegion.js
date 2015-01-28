/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function($) {

    //
    // CONSTANTS
    //
    var ALL_FILTERS_SKIP_PREFIX = '.~';
    var PARAM_PREFIX = '.param.';
    var SORT_PREFIX = '.sort', SORT_ASC = '+', SORT_DESC = '-';
    var OFFSET_PREFIX = '.offset';
    var CONTAINER_FILTER_NAME = '.containerFilterName';

    var _alterSortString = function(region, current, fieldKey, direction /* optional */) {
        fieldKey = _resolveFieldKey(region, fieldKey);

        var columnName = fieldKey.toString(),
            newSorts = [];

        if (current != null) {
            var sorts = current.split(",");
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

    var _changeFilter = function(region, newParamValPairs, newQueryString) {

        var event = $.Event("beforefilterchange");

        var filterPairs = [], name, val;
        $.each(newParamValPairs, function(i, pair) {
            name = pair[0];
            val = pair[1];
            if (name.indexOf(region.name + '.') == 0 && name.indexOf('~') > -1) {
                filterPairs.push([name, val]);
            }
        });

        $(region).trigger("beforefilterchange", region, filterPairs);
        if (event.isDefaultPrevented()) {
            return;
        }

        var params = _getParameters(region, newQueryString, [region.name + '.offset']);
        region.setSearchString.call(region, region.name, _buildQueryString(region, params));
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

    var _onSelectionChange = function(region) {
        $(region).trigger('selectchange', [region, region.selectedCount]);
        _updateRequiresSelectionButtons(region, region.selectedCount);
    };

    var _removeParameters = function(region, skipPrefixes /* optional */) {
        return _setParameters(region, null, skipPrefixes);
    };

    var _resolveFieldKey = function(region, fieldKey) {
        var fk = fieldKey;
        if (!(fk instanceof LABKEY.FieldKey)) {
            fk = LABKEY.FieldKey.fromString('' + fk);
        }
        return fk;
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
                    return false;
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

        region.setSearchString.call(region, region.name, _buildQueryString(region, params));
    };

    var _showSelectMessage = function(region, msg) {
        if (region.showRecordSelectors) {
            msg += "&nbsp;" + "<span class='labkey-button select-none'>Select None</span>";
            var showOpts = [];
            if (this.showRows != "all")
                showOpts.push("<span class='labkey-button show-all'>Show All</span>");
            if (this.showRows != "selected")
                showOpts.push("<span class='labkey-button show-selected'>Show Selected</span>");
            if (this.showRows != "unselected")
                showOpts.push("<span class='labkey-button show-unselected'>Show Unselected</span>");
            msg += "&nbsp;" + showOpts.join(" ");
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

        return ids;
    };

    var _updateFilter = function(region, filter, skipPrefixes) {
        var params = _getParameters(region, region.requestURL, skipPrefixes);
        if (filter) {
            params.push([filter.getURLParameterName(region.name), filter.getURLParameterValue()]);
        }
        _changeFilter(region, params, _buildQueryString(region, params));
    };

    var _updateRequiresSelectionButtons = function(region, selectedCount) {

        var me = region;

        // 10566: for javascript perf on IE stash the requires selection buttons
        if (!me._requiresSelectionButtons) {
            // escape ', ", and \
            var escaped = me.name.replace(/('|"|\\)/g, "\\$1");
            me._requiresSelectionButtons = $("a[labkey-requires-selection='" + escaped + "']");
        }

        me._requiresSelectionButtons.each(function() {
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

            if (minCount <= selectedCount && (!maxCount || maxCount >= selectedCount))
            {
                el.addClass('labkey-button').removeClass('labkey-disabled-button');
            }
            else
            {
                el.addClass('labkey-disabled-button').removeClass('labkey-button');
            }
        });
    };

    LABKEY.DataRegion2 = function(config) {

        if (!config || !LABKEY.Utils.isString(config.name)) {
            console.error('"name" is required to contruct a LABKEY.DataRegion.');
        }

        /**
         * Config Options
         */
        var defaults = {
            /**
             * Name of the DataRegion. Should be unique within a given page. Read-only. This will also be used as the id.
             */
            name: name,

            /**
             * Id of the DataRegion. Same as name property.
             */
            id: name,

            /**
             * Schema name of the query to which this DataRegion is bound. Read-only.
             */
            schemaName: '',

            /**
             * Name of the query to which this DataRegion is bound. Read-only.
             */
            queryName: '',

            /**
             * Name of the custom view to which this DataRegion is bound, may be blank. Read-only.
             */
            viewName: null,

            /**
             * Starting offset of the rows to be displayed. 0 if at the beginning of the results. Read-only.
             */
            offset: 0,

            /**
             * Maximum number of rows to be displayed. 0 if the count is not limited. Read-only.
             */
            maxRows: 0,

            requestURL: undefined,

            selectedCount: 0,

            showRecordSelectors: false,

            /**
             * An enum declaring which set of rows to show. all | selected | unselected | paginated
             */
            showRows: "paginated",

            totalRows: undefined // totalRows isn't available when showing all rows.
        };

        var settings = $.extend({}, defaults, config);

        for (var s in settings) {
            if (settings.hasOwnProperty(s)) {
                this[s] = settings[s];
            }
        }

        /**
         * Non-configurable Options
         */
        this['selectionModified'] = false;
        this['panelButtonContents'] = [];

        if (!LABKEY.DataRegions) {
            LABKEY.DataRegions = {};
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
            maxRows: this.maxRows
        };
    };

    Proto._init = function() {

        var me = this;
        var nameSel = '#' + me.name;
        var baseSel = 'form' + nameSel;

        this.form = $(baseSel);
        this.table = $('dataregion_' + me.name);

        this._initSelection();
    };

//    /**
//     * Add a filter to this Data Region.
//     * @param filter
//     */
//    Proto.addFilter = function(filter) {
//        console.error('LABKEY.DataRegion.addFilter(filter) NYI.');
//    };
//
//    /**
//     * Remove a filter from this Data Region.
//     * @param filter
//     */
//    Proto.removeFilter = function(filter) {
//        console.error('LABKEY.DataRegion.removeFilter(filter) NYI.');
//    };
//
    /**
     * Refreshes the grid via a page reload. Can be prevented with a listener on the 'beforerefresh'
     * event.
     */
    Proto.refresh = function() {

        var event = $.Event("beforerefresh");

        $(this).trigger(event);

        if (event.isDefaultPrevented()) {
            return;
        }

        window.location.reload();
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
        var event = $.Event("beforeclearallfilters");

        $(this).trigger(event, this);

        if (event.isDefaultPrevented()) {
            console.log('We were stopped!');
            return;
        }

        _removeParameters(this, [ALL_FILTERS_SKIP_PREFIX, ".offset"]);
    };

    /**
     * Removes all the filters for a particular field
     * @param {string or FieldKey} fieldKey the name of the field from which all filters should be removed
     */
    Proto.clearFilter = function(fieldKey) {
        fieldKey = _resolveFieldKey(this, fieldKey);

        if (fieldKey) {
            var columnName = fieldKey.toString();

            var event = $.Event("beforeclearfilter");

            $(this).trigger(event, this, columnName);

            if (event.isDefaultPrevented()) {
                return;
            }

            _removeParameters(this, ["." + columnName + "~", ".offset"]);
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
                if (this.isPageSelected()) {
                    _getAllRowSelectors(this).each(function() { this.checked = true; });
                }
                _onSelectionChange(this);
            }
        }

        // Bind Events
        _getAllRowSelectors(this).on('click', function() { me.selectPage.call(me, this.checked); });
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
            _removeParameters(this, ['.showRows']);
        }
        else if (this.showRows == 'unselected') {
            // keep ".showRows=unselected" parameter
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
        if (!this.table) {
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
                    var count = data.count;
                    var msg;
                    if (me.totalRows) {
                        if (count == me.totalRows) {
                            msg = "Selected all " + this.totalRows + " rows.";
                        }
                        else {
                            msg = "Selected " + count + " of " + this.totalRows + " rows.";
                        }
                    }
                    else {
                        msg = "Selected " + count + " rows.";
                    }
                    _showSelectMessage(me, msg);
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

        var toggle = _getAllRowSelectors(this);
        if (el.checked) {
            if (this.isPageSelected()) {
                toggle.each(function() { this.checked = true; });
            }
        }
        else {
            toggle.each(function() { this.checked = false; });
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

        // Update the current selectedCount and fire 'selectchange' event
        var updateSelected = function(data) {
            me.selectionModified = true;
            me.selectedCount = data.count;
            _onSelectionChange(me);
        };

        // Chain updateSelected with the user-provided success callback
        var success = LABKEY.Utils.getOnSuccess(config);
        if ($.isFunction(success)) {
            success = updateSelected.createSequence(success, config.scope);
        } else {
            success = updateSelected;
        }
        config.success = success;

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
        var event = $.Event("beforeclearallparameters");

        $(this).trigger(event, this);

        if (event.isDefaultPrevented()) {
            return;
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
        var event = $.Event("beforesetparameters");

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

        //console.log(_params);
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
     * Show a message in the header of this DataRegion.
     * @param {String / Object} htmlOrConfig the HTML source of the message to be shown or a config object witht the following properties:
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
    Proto.addMessage = function(htmlOrConfig, part) {
        if (this.msgbox) {

            if (LABKEY.Utils.isString(htmlOrConfig)) {
                this.msgbox.addMessage(htmlOrConfig, part);
            }
            else if (typeof htmlOrConfig === "object") {
                this.msgbox.addMessage(htmlOrConfig.html, htmlOrConfig.part || part);

                // TODO: Implement This
                //if (htmlOrConfig.hideButtonPanel) {
                //    this.hideButtonPanel();
                //}
                //
                //if (htmlOrConfig.duration) {
                //    var dr = this;
                //    setTimeout(function(){dr.removeMessage(htmlOrConfig.part || part); dr.header.fireEvent('resize');}, htmlOrConfig.duration);
                //}
            }
        }
    };

    /**
     * If a message is currently showing, remove the specified part
     */
    Proto.removeMessage = function(part) {
        if (this.msgbox) {
            this.msgbox.removeMessage(part);
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

        $(this).trigger(event, this, columnName, sortDir);

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

        $(this).trigger(event, this, columnName);

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
    // Misc
    //

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

    Proto.on = function() {
        $(this).on.apply($, arguments);
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

})(jQuery);