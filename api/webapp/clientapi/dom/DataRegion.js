/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if (!LABKEY.DataRegions) {
    LABKEY.DataRegions = {};
}

(function($) {

    //
    // CONSTANTS
    //
    var ALL_FILTERS_SKIP_PREFIX = '.~';
    var COLUMNS_PREFIX = '.columns';
    var DEFAULT_TIMEOUT = 30000;
    var PARAM_PREFIX = '.param.';
    var REPORTID_PREFIX = '.reportId';
    var SORT_PREFIX = '.sort', SORT_ASC = '+', SORT_DESC = '-';
    var OFFSET_PREFIX = '.offset';
    var MAX_ROWS_PREFIX = '.maxRows', SHOW_ROWS_PREFIX = '.showRows';
    var CONTAINER_FILTER_NAME = '.containerFilterName';
    var CUSTOM_VIEW_PANELID = '~~customizeView~~';
    var VIEWNAME_PREFIX = '.viewName';

    var VALID_LISTENERS = [
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name afterpanelhide
         * @event LABKEY.DataRegion.prototype#hidePanel
         * @description Fires after hiding a visible 'Customize Grid' panel.
         */
            'afterpanelhide',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name afterpanelshow
         * @event LABKEY.DataRegion.prototype.showPanel
         * @description Fires after showing 'Customize Grid' panel.
         */
            'afterpanelshow',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name beforechangeview
         * @event
         * @description Fires before changing grid/view/report.
         * @see LABKEY.DataRegion#changeView
         */
            'beforechangeview',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name beforeclearsort
         * @event
         * @description Fires before clearing sort applied to grid.
         * @see LABKEY.DataRegion#clearSort
         */
            'beforeclearsort',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name beforemaxrowschange
         * @event
         * @description Fires before change page size.
         * @see LABKEY.DataRegion#setMaxRows
         */
            'beforemaxrowschange',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name beforeoffsetchange
         * @event
         * @description Fires before change page number.
         * @see LABKEY.DataRegion#setPageOffset
         */
            'beforeoffsetchange',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name beforerefresh
         * @event
         * @description Fires before refresh grid.
         * @see LABKEY.DataRegion#refresh
         */
            'beforerefresh',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name beforesetparameters
         * @event
         * @description Fires before setting the parameterized query values for this query.
         * @see LABKEY.DataRegion#setParameters
         */
            'beforesetparameters',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name beforesortchange
         * @event
         * @description Fires before change sorting on the grid.
         * @see LABKEY.DataRegion#changeSort
         */
            'beforesortchange',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @member
         * @name render
         * @event
         * @description Fires when data region renders.
         */
            'render',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name selectchange
         * @event
         * @description Fires when data region selection changes.
         */
            'selectchange',
        /**
         * @memberOf LABKEY.DataRegion.prototype
         * @name success
         * @event
         * @description Fires when data region loads successfully.
         */
            'success'];

    // TODO: Update constants to not include '.' so mapping can be used easier
    var REQUIRE_NAME_PREFIX = {
        '~': true,
        'columns': true,
        'param': true,
        'reportId': true,
        'sort': true,
        'offset': true,
        'maxRows': true,
        'showRows': true,
        'containerFilterName': true,
        'viewName': true,
        'disableAnalytics': true
    };

    //
    // PRIVATE VARIABLES
    //
    var _paneCache = {};

    /**
     * The DataRegion constructor is private - to get a LABKEY.DataRegion object, use LABKEY.DataRegions['dataregionname'].
     * @class LABKEY.DataRegion
     * The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
     * @constructor
     */
    LABKEY.DataRegion = function(config) {
        _init.call(this, config, true);
    };

    LABKEY.DataRegion.prototype.toJSON = function() {
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

    /**
     *
     * @param {Object} config
     * @param {Boolean} [applyDefaults=false]
     * @private
     */
    var _init = function(config, applyDefaults) {

        // ensure name
        if (!config.dataRegionName) {
            if (!config.name) {
                this.name = LABKEY.Utils.id('aqwp');
            }
            else {
                this.name = config.name;
            }
        }
        else if (!config.name) {
            this.name = config.dataRegionName;
        }
        else {
            this.name = config.name;
        }

        if (!this.name) {
            throw '"name" is required to initialize a LABKEY.DataRegion';
        }

        // _useQWPDefaults is only used on initial construction
        var isQWP = config._useQWPDefaults === true;
        delete config._useQWPDefaults;

        var settings;

        if (applyDefaults) {

            // defensively remove, not allowed to be set
            delete config._userSort;

            /**
             * Config Options
             */
            var defaults = {

                _allowHeaderLock: isQWP,

                _failure: isQWP ? LABKEY.Utils.getOnFailure(config) : undefined,

                _success: isQWP ? LABKEY.Utils.getOnSuccess(config) : undefined,

                aggregates: undefined,

                allowChooseQuery: undefined,

                allowChooseView: undefined,

                async: isQWP,

                bodyClass: undefined,

                buttonBar: undefined,

                buttonBarPosition: undefined,

                chartWizardURL: undefined,

                /**
                 * All rows visible on the current page.
                 */
                complete: false,

                /**
                 * The currently applied container filter. Note, this is only if it is set on the URL, otherwise
                 * the containerFilter could come from the view configuration. Use getContainerFilter()
                 * on this object to get the right value.
                 */
                containerFilter: undefined,

                containerPath: undefined,

                /**
                 * @deprecated use region.name instead
                 */
                dataRegionName: this.name,

                detailsURL: undefined,

                domId: undefined,

                /**
                 * The faceted filter pane as been loaded
                 * @private
                 */
                facetLoaded: false,

                filters: undefined,

                frame: isQWP ? undefined : 'none',

                errorType: 'html',

                /**
                 * Id of the DataRegion. Same as name property.
                 */
                id: this.name,

                deleteURL: undefined,

                importURL: undefined,

                insertURL: undefined,

                linkTarget: undefined,

                /**
                 * Maximum number of rows to be displayed. 0 if the count is not limited. Read-only.
                 */
                maxRows: 0,

                metadata: undefined,

                /**
                 * Name of the DataRegion. Should be unique within a given page. Read-only. This will also be used as the id.
                 */
                name: this.name,

                /**
                 * The index of the first row to return from the server (defaults to 0). Use this along with the maxRows config property to request pages of data.
                 */
                offset: 0,

                parameters: undefined,

                /**
                 * Name of the query to which this DataRegion is bound. Read-only.
                 */
                queryName: '',

                disableAnalytics: false,

                removeableContainerFilter: undefined,

                removeableFilters: undefined,

                removeableSort: undefined,

                renderTo: undefined,

                reportId: undefined,

                requestURL: isQWP ? window.location.href : (document.location.search.substring(1) /* strip the ? */ || ''),

                returnURL: isQWP ? window.location.href : undefined,

                /**
                 * Schema name of the query to which this DataRegion is bound. Read-only.
                 */
                schemaName: '',

                /**
                 * An object to use as the callback function's scope. Defaults to this.
                 */
                scope: this,

                /**
                 * URL to use when selecting all rows in the grid. May be null. Read-only.
                 */
                selectAllURL: undefined,

                selectedCount: 0,

                shadeAlternatingRows: undefined,

                showBorders: undefined,

                showDeleteButton: undefined,

                showDetailsColumn: undefined,

                showExportButtons: undefined,

                showImportDataButton: undefined,

                showInsertNewButton: undefined,

                showPagination: undefined,

                showPaginationCount: undefined,

                showRecordSelectors: false,

                showReports: undefined,

                /**
                 * An enum declaring which set of rows to show. all | selected | unselected | paginated
                 */
                showRows: 'paginated',

                showSurroundingBorder: undefined,

                showUpdateColumn: undefined,

                /**
                 * Open the customize view panel after rendering. The value of this option can be "true" or one of "ColumnsTab", "FilterTab", or "SortTab".
                 */
                showViewPanel: undefined,

                sort: undefined,

                sql: undefined,

                /**
                 * If true, no alert will appear if there is a problem rendering the QueryWebpart. This is most often encountered if page configuration changes between the time when a request was made and the content loads. Defaults to false.
                 */
                suppressRenderErrors: false,

                /**
                 * A timeout for the AJAX call, in milliseconds.
                 */
                timeout: undefined,

                title: undefined,

                titleHref: undefined,

                totalRows: undefined, // totalRows isn't available when showing all rows.

                updateURL: undefined,

                userContainerFilter: undefined, // TODO: Incorporate this with the standard containerFilter

                userFilters: {},

                /**
                 * Name of the custom view to which this DataRegion is bound, may be blank. Read-only.
                 */
                viewName: null
            };

            settings = $.extend({}, defaults, config);
        }
        else {
            settings = $.extend({}, config);
        }

        // if 'filters' is not specified and 'filterArray' is, use 'filterArray'
        if (!$.isArray(settings.filters) && $.isArray(config.filterArray)) {
            settings.filters = config.filterArray;
        }

        // Any 'key' of this object will not be copied from settings to the region instance
        var blackList = {
            failure: true,
            success: true
        };

        for (var s in settings) {
            if (settings.hasOwnProperty(s) && !blackList[s]) {
                this[s] = settings[s];
            }
        }

        if (config.renderTo) {
            _convertRenderTo(this, config.renderTo);
        }

        if ($.isArray(this.removeableFilters)) {
            LABKEY.Filter.appendFilterParams(this.userFilters, this.removeableFilters, this.name);
            delete this.removeableFilters; // they've been applied
        }

        // initialize sorting
        if (this._userSort === undefined) {
            this._userSort = _getUserSort(this, true /* asString */);
        }

        if (LABKEY.Utils.isString(this.removeableSort)) {
            this._userSort = this.removeableSort + (this._userSort ? this._userSort : '');
            delete this.removeableSort;
        }

        this._allowHeaderLock = this.allowHeaderLock === true;

        if (!config.messages) {
            this.messages = {};
        }

        /**
         * @ignore
         * Non-configurable Options
         */
        this.selectionModified = false;
        this.panelConfigurations = {};

        if (isQWP && this.renderTo) {
            _load(this);
        }
        else if (!isQWP) {
            _initContexts.call(this);
            _initMessaging.call(this);
            _initSelection.call(this);
            _initPaging.call(this);
            _initHeaderLocking.call(this);
            _initCustomViews.call(this);
            _initPanes.call(this);
        }
        // else the user needs to call render

        // bind supported listeners
        if (isQWP) {
            var me = this;
            if (config.listeners) {
                var scope = config.listeners.scope || me;
                $.each(config.listeners, function(event, handler) {
                    if ($.inArray(event, VALID_LISTENERS) > -1) {

                        // support either "event: function" or "event: { fn: function }"
                        var callback;
                        if ($.isFunction(handler)) {
                            callback = handler;
                        }
                        else if ($.isFunction(handler.fn)) {
                            callback = handler.fn;
                        }
                        else {
                            throw 'Unsupported listener configuration: ' + event;
                        }

                        $(me).bind(event, function() {
                            callback.apply(scope, $(arguments).slice(1));
                        });
                    }
                    else if (event != 'scope') {
                        throw 'Unsupported listener: ' + event;
                    }
                });
            }
        }
    };

    LABKEY.DataRegion.prototype.destroy = function() {
        // currently a no-op, but should be used to clean-up after ourselves
        this.disableHeaderLock();
    };

    /**
     * Refreshes the grid, via AJAX region is in async mode (loaded through a QueryWebPart),
     * and via a page reload otherwise. Can be prevented with a listener
     * on the 'beforerefresh'
     * event.
     */
    LABKEY.DataRegion.prototype.refresh = function() {
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
     * @see LABKEY.DataRegion.addFilter static method.
     */
    LABKEY.DataRegion.prototype.addFilter = function(filter) {
        _updateFilter(this, filter);
    };

    /**
     * Removes all filters from the DataRegion
     */
    LABKEY.DataRegion.prototype.clearAllFilters = function() {
        if (this.async) {
            this.offset = 0;
            this.userFilters = {};
        }

        _removeParameters(this, [ALL_FILTERS_SKIP_PREFIX, OFFSET_PREFIX]);
    };

    /**
     * Removes all the filters for a particular field
     * @param {string|FieldKey} fieldKey the name of the field from which all filters should be removed
     */
    LABKEY.DataRegion.prototype.clearFilter = function(fieldKey) {
        var fk = _resolveFieldKey(this, fieldKey);

        if (fk) {
            var columnPrefix = '.' + fk.toString() + '~';

            if (this.async) {
                this.offset = 0;

                if (this.userFilters) {
                    var namePrefix = this.name + columnPrefix,
                        me = this;

                    $.each(this.userFilters, function(name, v) {
                        if (name.indexOf(namePrefix) >= 0) {
                            delete me.userFilters[name];
                        }
                    });
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
    LABKEY.DataRegion.prototype.getContainerFilter = function() {
        var cf;

        if (LABKEY.Utils.isString(this.containerFilter) && this.containerFilter.length > 0) {
            cf = this.containerFilter;
        }
        else if (LABKEY.Utils.isObject(this.view) && LABKEY.Utils.isString(this.view.containerFilter) && this.view.containerFilter.length > 0) {
            cf = this.view.containerFilter;
        }

        return cf;
    };

    LABKEY.DataRegion.prototype.getDataRegion = function() {
        return this;
    };

    /**
     * Returns the user {@link LABKEY.Query.containerFilter} parameter from the URL.
     * @returns {LABKEY.Query.containerFilter} The user container filter.
     */
    LABKEY.DataRegion.prototype.getUserContainerFilter = function() {
        return this.getParameter(this.name + CONTAINER_FILTER_NAME);
    };

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
    LABKEY.DataRegion.prototype.getUserFilter = function() {

        if (LABKEY.devMode) {
            console.warn([
                'LABKEY.DataRegion.getUserFilter() is deprecated since release 12.2.',
                'Consider using getUserFilterArray() instead.'
            ].join(' '));
        }

        var userFilter = [];

        $.each(this.getUserFilterArray(), function(i, filter) {
            userFilter.push({
                fieldKey: filter.getColumnName(),
                op: filter.getFilterType().getURLSuffix(),
                value: filter.getValue()
            });
        });

        return userFilter;
    };

    /**
     * Returns an Array of LABKEY.Filter instances constructed from the URL.
     * @returns {Array} Array of {@link LABKEY.Filter} objects that represent currently applied filters.
     */
    LABKEY.DataRegion.prototype.getUserFilterArray = function() {
        var userFilter = [], me = this;

        var pairs = _getParameters(this);
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
    LABKEY.DataRegion.prototype.removeFilter = function(filter) {
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
    LABKEY.DataRegion.prototype.replaceFilter = function(filter, filterToReplace) {
        var target = filterToReplace ? filterToReplace : filter;
        _updateFilter(this, filter, [this.name + '.' + target.getColumnName() + '~']);
    };

    /**
     * @ignore
     * @param filters
     * @param columnNames
     */
    LABKEY.DataRegion.prototype.replaceFilters = function(filters, columnNames) {
        var filterPrefixes = [],
            filterParams = [],
            me = this;

        if ($.isArray(filters)) {
            $.each(filters, function(i, filter) {
                filterPrefixes.push(me.name + '.' + filter.getColumnName() + '~');
                filterParams.push([filter.getURLParameterName(me.name), filter.getURLParameterValue()]);
            });
        }

        var fieldKeys = [];

        if ($.isArray(columnNames)) {
            fieldKeys = fieldKeys.concat(columnNames);
        }
        else if ($.isPlainObject(columnNames) && columnNames.fieldKey) {
            fieldKeys.push(columnNames.fieldKey.toString());
        }

        // support fieldKeys (e.g. ["ColumnA", "ColumnA/Sub1"])
        // A special case of fieldKey is "SUBJECT_PREFIX/", used by participant group facet
        if (fieldKeys.length > 0) {
            $.each(_getParameters(this), function(i, param) {
                var p = param[0];
                if (p.indexOf(me.name + '.') === 0 && p.indexOf('~') > -1) {
                    $.each(fieldKeys, function(j, name) {
                        var postfix = name && name.length && name[name.length - 1] == '/' ? '' : '~';
                        if (p.indexOf(me.name + '.' + name + postfix) > -1) {
                            filterPrefixes.push(p);
                        }
                    });
                }
            });
        }

        _setParameters(this, filterParams, [OFFSET_PREFIX].concat($.unique(filterPrefixes)));
    };

    /**
     * @private
     * @param filter
     * @param filterMatch
     */
    LABKEY.DataRegion.prototype.replaceFilterMatch = function(filter, filterMatch) {
        var skips = [], me = this;

        $.each(_getParameters(this), function(i, param) {
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
    var _initSelection = function() {

        var me = this,
            form = _getFormSelector(this);

        if (form && form.length) {
            // backwards compatibility -- some references use this directly
            // if you're looking to use this internally to the region use _getFormSelector() instead
            this.form = form[0];
        }

        if (form && this.showRecordSelectors) {
            _onSelectionChange(this);
        }

        // Bind Events
        _getAllRowSelectors(this).on('click', function(evt) {
            evt.stopPropagation();
            me.selectPage.call(me, this.checked);
        });
        _getRowSelectors(this).on('click', function() { me.selectRow.call(me, this); });

        // click row highlight
        var rows = form.find('.labkey-data-region > tbody > tr');
        rows.on('click', function(e) {
            if (e.target && e.target.tagName.toLowerCase() === 'td') {
                $(this).siblings('tr').removeClass('lk-row-hl');
                $(this).addClass('lk-row-hl');
            }
        });
        rows.on('mouseenter', function() {
            $(this).siblings('tr').removeClass('lk-row-over');
            $(this).addClass('lk-row-over');
        });
        rows.on('mouseleave', function() {
            $(this).removeClass('lk-row-over');
        });
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
    LABKEY.DataRegion.prototype.clearSelected = function(config) {
        config = config || {};
        config.selectionKey = this.selectionKey;
        config.scope = config.scope || this;

        this.selectedCount = 0;
        _onSelectionChange(this);

        if (config.selectionKey) {
            LABKEY.DataRegion.clearSelected(config);
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
    LABKEY.DataRegion.prototype.getChecked = function() {
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
    LABKEY.DataRegion.prototype.getSelected = function(config) {
        if (!this.selectionKey)
            return;

        config = config || {};
        config.selectionKey = this.selectionKey;
        LABKEY.DataRegion.getSelected(config);
    };

    /**
     * Returns the number of selected rows on the current page of the DataRegion. Selected items may exist on other pages.
     * @returns {Integer} the number of selected rows on the current page of the DataRegion.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    LABKEY.DataRegion.prototype.getSelectionCount = function() {
        if (!$('#' + this.domId)) {
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
    LABKEY.DataRegion.prototype.hasSelected = function() {
        return this.getSelectionCount() > 0;
    };

    /**
     * Returns true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @returns {Boolean} true if all rows are checked on the current page of the DataRegion and at least one row is present.
     * @see LABKEY.DataRegion#getSelected to get all selected rows.
     */
    LABKEY.DataRegion.prototype.isPageSelected = function() {
        var checkboxes = _getRowSelectors(this);
        var i=0;

        for (; i < checkboxes.length; i++) {
            if (!checkboxes[i].checked) {
                return false;
            }
        }
        return i > 0;
    };

    LABKEY.DataRegion.prototype.selectAll = function(config) {
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

            LABKEY.DataRegion.selectAll(config);

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
     * @deprecated use clearSelected instead
     * @function
     * @see LABKEY.DataRegion#clearSelected
     */
    LABKEY.DataRegion.prototype.selectNone = LABKEY.DataRegion.prototype.clearSelected;

    /**
     * Set the selection state for all checkboxes on the current page of the DataRegion.
     * @param checked whether all of the rows on the current page should be selected or unselected
     * @returns {Array} Array of ids that were selected or unselected.
     *
     * @see LABKEY.DataRegion#setSelected to set selected items on the current page of the DataRegion.
     * @see LABKEY.DataRegion#clearSelected to clear all selected.
     */
    LABKEY.DataRegion.prototype.selectPage = function(checked) {
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
     * @ignore
     * @param el
     */
    LABKEY.DataRegion.prototype.selectRow = function(el) {
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
     * @param {Function} [config.success] The function to be called upon success of the request.
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
    LABKEY.DataRegion.prototype.setSelected = function(config) {
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
            LABKEY.DataRegion.setSelected(config);
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
    LABKEY.DataRegion.prototype.clearAllParameters = function() {
        if (this.async) {
            this.offset = 0;
            this.parameters = undefined;
        }

        _removeParameters(this, [PARAM_PREFIX, OFFSET_PREFIX]);
    };

    /**
     * Returns the specified parameter from the URL. Note, this is not related specifically
     * to parameterized query values (e.g. setParameters()/getParameters())
     * @param {String} paramName
     * @returns {*}
     */
    LABKEY.DataRegion.prototype.getParameter = function(paramName) {
        var param = null;

        $.each(_getParameters(this), function(i, pair) {
            if (pair.length > 0 && pair[0] == paramName) {
                param = pair.length > 1 ? pair[1] : '';
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
    LABKEY.DataRegion.prototype.getParameters = function(toLowercase) {

        var params = this.parameters ? this.parameters : {},
            re = new RegExp('^' + LABKEY.Utils.escapeRe(this.name) + PARAM_PREFIX.replace(/\./g, '\\.'), 'i'),
            name;

        $.each(_getParameters(this), function(i, pair) {
            if (pair.length > 0 && pair[0].match(re)) {
                name = pair[0].replace(re, '');
                if (toLowercase === true) {
                    name = name.toLowerCase();
                }

                // URL parameters will override this.parameters values
                params[name] = pair[1];
            }
        });

        return params;
    };

    /**
     * Set the parameterized query values for this query.  These parameters
     * are named by the query itself.
     * @param {Mixed} params An Object or Array of Array key/val pairs.
     */
    LABKEY.DataRegion.prototype.setParameters = function(params) {
        var event = $.Event('beforesetparameters');

        $(this).trigger(event);

        if (event.isDefaultPrevented()) {
            return;
        }

        var paramPrefix = this.name + PARAM_PREFIX, _params = [];
        var newParameters = this.parameters ? this.parameters : {};

        function applyParameters(pKey, pValue) {
            var key = pKey;
            if (pKey.indexOf(paramPrefix) !== 0) {
                key = paramPrefix + pKey;
            }
            newParameters[key.replace(paramPrefix, '')] = pValue;
            _params.push([key, pValue]);
        }

        // convert Object into Array of Array pairs and prefix the parameter name if necessary.
        if (LABKEY.Utils.isObject(params)) {
            $.each(params, applyParameters);
        }
        else if (LABKEY.Utils.isArray(params)) {
            $.each(params, function(i, pair) {
                if (LABKEY.Utils.isArray(pair) && pair.length > 1) {
                    applyParameters(pair[0], pair[1]);
                }
            });
        }
        else {
            return; // invalid argument shape
        }

        this.parameters = newParameters;

        _setParameters(this, _params, [PARAM_PREFIX, OFFSET_PREFIX]);
    };

    /**
     * @ignore
     * @Deprecated
     */
    LABKEY.DataRegion.prototype.getSearchString = function() {
        if (!LABKEY.Utils.isString(this.savedSearchString)) {
            this.savedSearchString = document.location.search.substring(1) /* strip the ? */ || "";
        }
        return this.savedSearchString;
    };

    /**
     * @ignore
     * @Deprecated
     */
    LABKEY.DataRegion.prototype.setSearchString = function(regionName, search) {
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
    var _initMessaging = function() {
        if (!this.msgbox) {
            this.msgbox = new MessageArea(this);
            this.msgbox.on('rendermsg', function(evt, msgArea, parts) { _onRenderMessageArea(this, parts); }, this);
        }
        else {
            this.msgbox.bindRegion(this);
        }

        if (this.messages) {
            this.msgbox.setMessages(this.messages);
            this.msgbox.render();
        }
    };

    /**
     * Show a message in the header of this DataRegion.
     * @param {String / Object} config the HTML source of the message to be shown or a config object with the following properties:
     *      <ul>
     *          <li><strong>html</strong>: {String} the HTML source of the message to be shown.</li>
     *          <li><strong>part</strong>: {String} The part of the message area to render the message to.</li>
     *          <li><strong>duration</strong>: {Integer} The amount of time (in milliseconds) the message will stay visible.</li>
     *          <li><strong>hideButtonPanel</strong>: {Boolean} If true the button panel (customize view, export, etc.) will be hidden if visible.</li>
     *          <li><strong>append</strong>: {Boolean} If true the msg is appended to any existing content for the given part.</li>
     *      </ul>
     * @param part The part of the message area to render the message to. Used to scope messages so they can be added
     *      and removed without clearing other messages.
     */
    LABKEY.DataRegion.prototype.addMessage = function(config, part) {
        this.hidePanel();

        if (LABKEY.Utils.isString(config)) {
            this.msgbox.addMessage(config, part);
        }
        else if (LABKEY.Utils.isObject(config)) {
            this.msgbox.addMessage(config.html, config.part || part, config.append);

            if (config.hideButtonPanel) {
                this.hideButtonPanel();
            }

            if (config.duration) {
                var dr = this;
                setTimeout(function() {
                    dr.removeMessage(config.part || part);
                    _getHeaderSelector(dr).trigger('resize');
                }, config.duration);
            }
        }
    };

    /**
     * Clear the message box contents.
     */
    LABKEY.DataRegion.prototype.clearMessage = function() {
        if (this.msgbox) this.msgbox.clear();
    };

    /**
     * @param part The part of the message area to render the message to. Used to scope messages so they can be added
     *      and removed without clearing other messages.
     * @return {String} The message for 'part'. Could be undefined.
     */
    LABKEY.DataRegion.prototype.getMessage = function(part) {
        if (this.msgbox) { return this.msgbox.getMessage(part); } // else undefined
    };

    /**
     * @param part The part of the message area to render the message to. Used to scope messages so they can be added
     *      and removed without clearing other messages.
     * @return {Boolean} true iff there is a message area for this region and it has the message keyed by 'part'.
     */
    LABKEY.DataRegion.prototype.hasMessage = function(part) {
        return this.msgbox && this.msgbox.hasMessage(part);
    };

    LABKEY.DataRegion.prototype.hideContext = function() {
        _getContextBarSelector(this).hide();
        _getViewBarSelector(this).hide();
    };

    /**
     * If a message is currently showing, hide it and clear out its contents
     * @param keepContent If true don't remove the message area content
     */
    LABKEY.DataRegion.prototype.hideMessage = function(keepContent) {
        if (this.msgbox) {
            this.msgbox.hide();

            if (!keepContent)
                this.removeAllMessages();
        }
    };

    /**
     * Returns true if a message is currently being shown for this DataRegion. Messages are shown as a header.
     * @return {Boolean} true if a message is showing.
     */
    LABKEY.DataRegion.prototype.isMessageShowing = function() {
        return this.msgbox && this.msgbox.isVisible();
    };

    /**
     * Removes all messages from this Data Region.
     */
    LABKEY.DataRegion.prototype.removeAllMessages = function() {
        if (this.msgbox) { this.msgbox.removeAll(); }
    };

    /**
     * If a message is currently showing, remove the specified part
     */
    LABKEY.DataRegion.prototype.removeMessage = function(part) {
        if (this.msgbox) { this.msgbox.removeMessage(part); }
    };

    /**
     * Show a message in the header of this DataRegion with a loading indicator.
     * @param html the HTML source of the message to be shown
     */
    LABKEY.DataRegion.prototype.showLoadingMessage = function(html) {
        html = html || "Loading...";
        this.addMessage('<div><span class="loading-indicator">&nbsp;</span><em>' + html + '</em></div>', 'drloading');
    };

    LABKEY.DataRegion.prototype.hideLoadingMessage = function() {
        this.removeMessage('drloading');
    };

    /**
     * Show a success message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    LABKEY.DataRegion.prototype.showSuccessMessage = function(html) {
        html = html || "Completed successfully.";
        this.addMessage('<div class="labkey-message">' + html + '</div>');
    };

    /**
     * Show an error message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    LABKEY.DataRegion.prototype.showErrorMessage = function(html) {
        html = html || "An error occurred.";
        this.addMessage('<div class="labkey-error">' + html + '</div>');
    };

    LABKEY.DataRegion.prototype.showContext = function() {
        _initContexts();

        var ctx = _getContextBarSelector(this);
        if (ctx.html().trim() !== '') {
            ctx.show();
        }
        var view = _getViewBarSelector(this);
        if (view.html().trim() !== '') {
            view.show();
        }
    };

    /**
     * Show a message in the header of this DataRegion.
     * @param msg the HTML source of the message to be shown
     * @deprecated use addMessage(msg, part) instead.
     */
    LABKEY.DataRegion.prototype.showMessage = function(msg) {
        if (this.msgbox) {
            this.msgbox.addMessage(msg);
        }
    };

    LABKEY.DataRegion.prototype.showMessageArea = function() {
        if (this.msgbox && this.msgbox.hasContent()) {
            this.msgbox.show();
        }
    };

    //
    // Sections
    //

    LABKEY.DataRegion.prototype.displaySection = function(options) {
        var dir = options && options.dir ? options.dir : 'n';

        var sec = _getSectionSelector(this, dir);
        if (options && options.html) {
            options.append === true ? sec.append(options.html) : sec.html(options.html);
        }
        sec.show();
    };

    LABKEY.DataRegion.prototype.hideSection = function(options) {
        var dir = options && options.dir ? options.dir : 'n';
        var sec = _getSectionSelector(this, dir);

        sec.hide();

        if (options && options.clear === true) {
            sec.html('');
        }
    };

    LABKEY.DataRegion.prototype.writeSection = function(content, options) {
        var append = options && options.append === true;
        var dir = options && options.dir ? options.dir : 'n';

        var sec = _getSectionSelector(this, dir);
        append ? sec.append(content) : sec.html(content);
    };

    //
    // Sorting
    //

    /**
     * Replaces the sort on the given column, if present, or sets a brand new sort
     * @param {string or LABKEY.FieldKey} fieldKey name of the column to be sorted
     * @param {string} [sortDir=+] Set to '+' for ascending or '-' for descending
     */
    LABKEY.DataRegion.prototype.changeSort = function(fieldKey, sortDir) {
        if (!fieldKey)
            return;

        fieldKey = _resolveFieldKey(this, fieldKey);

        var columnName = fieldKey.toString();

        var event = $.Event("beforesortchange");

        $(this).trigger(event, [this, columnName, sortDir]);

        if (event.isDefaultPrevented()) {
            return;
        }

        this._userSort = _alterSortString(this, this._userSort, fieldKey, sortDir);
        _setParameter(this, SORT_PREFIX, this._userSort, [SORT_PREFIX, OFFSET_PREFIX]);
    };

    /**
     * Removes the sort on a specified column
     * @param {string or LABKEY.FieldKey} fieldKey name of the column
     */
    LABKEY.DataRegion.prototype.clearSort = function(fieldKey) {
        if (!fieldKey)
            return;

        fieldKey = _resolveFieldKey(this, fieldKey);

        var columnName = fieldKey.toString();

        var event = $.Event("beforeclearsort");

        $(this).trigger(event, [this, columnName]);

        if (event.isDefaultPrevented()) {
            return;
        }

        this._userSort = _alterSortString(this, this._userSort, fieldKey);
        if (this._userSort.length > 0) {
            _setParameter(this, SORT_PREFIX, this._userSort, [SORT_PREFIX, OFFSET_PREFIX]);
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
    LABKEY.DataRegion.prototype.getUserSort = function() {
        return _getUserSort(this);
    };

    //
    // Paging
    //

    var _initPaging = function() {
        if (this.showPagination) {
            var ct = _getBarSelector(this).find('.labkey-pagination');

            if (ct && ct.length) {
                var hasOffset = $.isNumeric(this.offset);
                var hasTotal = $.isNumeric(this.totalRows);

                // display the counts
                if (hasOffset) {

                    // small result set
                    if (hasTotal && this.totalRows < 5) {
                        return;
                    }

                    var low = this.offset + 1;
                    var high = this.offset + this.rowCount;

                    // user has opted to show all rows
                    if (hasTotal && (this.rowCount === null || this.rowCount < 1)) {
                        high = this.totalRows;
                    }

                    var showFirst = this.offset && this.offset > 0;
                    var showLast = !(low === 1 && high === this.totalRows) && (this.offset + this.maxRows <= this.totalRows);
                    var showAll = showFirst || showLast;
                    var showFirstID = showFirst && LABKEY.Utils.id();
                    var showLastID = showLast && LABKEY.Utils.id();
                    var showAllID = showAll && LABKEY.Utils.id();

                    var paginationText = low.toLocaleString() + ' - ' + high.toLocaleString();

                    if (hasTotal && this.showPaginationCount !== false) {
                        paginationText += ' of ' + this.totalRows.toLocaleString();
                    }

                    // If modifying this ensure it is consistent with DOM generated by PopupMenu.java
                    var elems = [
                        '<div class="lk-menu-drop dropdown paging-widget">',
                            '<a data-toggle="dropdown" class="unselectable">'+ paginationText + '</a>',
                            '<ul class="dropdown-menu dropdown-menu-left">',
                                (showFirst ? '<li><a id="' + showFirstID + '" tabindex="0">Show first</a></li>' : '<li aria-disabled="true" class="disabled"><a>Show first</a></li>'),
                                (showLast ? '<li><a id="' + showLastID + '" tabindex="0">Show last</a></li>' : '<li aria-disabled="true" class="disabled"><a>Show last</a></li>'),
                                (showAll ? '<li><a id="' + showAllID + '" tabindex="0">Show all</a></li>' : '<li aria-disabled="true" class="disabled"><a>Show all</a></li>'),
                                '<li class="dropdown-submenu"><a class="subexpand subexpand-icon" tabindex="0">Paging<i class="fa fa-chevron-right"></i></a>',
                                    '<ul class="dropdown-layer-menu">',
                                        '<li><a class="subcollapse" tabindex="3"><i class="fa fa-chevron-left"></i>Paging</a></li>',
                                        '<li class="divider"></li>'
                    ];

                    var offsets = [20, 40, 100, 250];
                    if (this.maxRows > 0 && offsets.indexOf(this.maxRows) === -1) {
                        offsets.push(this.maxRows);
                        offsets = offsets.sort(function(a, b) { return a - b; });
                    }

                    var offsetIds = {}; //"id-42": offset
                    for (var i = 0; i < offsets.length; i++) {
                        var id = LABKEY.Utils.id();
                        offsetIds[id] = offsets[i];

                        if (this.maxRows === offsets[i]) {
                            elems.push('<li><a id="'+ id + '" tabindex="0" style="padding-left: 0;"><i class="fa fa-check-square-o"></i>' + offsets[i] +' per page</a></li>')
                        }
                        else {
                            elems.push('<li><a id="'+ id + '" tabindex="0">' + offsets[i] +' per page</a></li>');
                        }
                    }

                    elems.push('</ul></ul></div>');
                    ct.append(elems.join(''));

                    //bind functions to menu items
                    if (showFirst) {
                        $('#' + showFirstID).click(_firstPage.bind(this, showFirst));
                    }
                    if (showLast) {
                        $('#' + showLastID).click(_lastPage.bind(this, showLast));
                    }
                    if (showAll) {
                        $('#' + showAllID).click(_showRows.bind(this, this, 'all'));
                    }

                    for (var key in offsetIds) {
                        if (offsetIds.hasOwnProperty(key)) {
                            $('#' + key).click(_setMaxRows.bind(this, offsetIds[key]));
                        }
                    }

                    // only display buttons if all the results are not shown
                    if (low === 1 && high === this.totalRows) {
                        _getBarSelector(this).find('.paging-widget').css("top", "4px");
                        return;
                    }

                    var canNext = this.maxRows > 0 && high !== this.totalRows,
                        canPrev = this.maxRows > 0 && low > 1,
                        prevId = LABKEY.Utils.id(),
                        nextId = LABKEY.Utils.id();

                    ct.append([
                        '<div class="btn-group" style="padding-left: 5px; display: inline-block">',
                        '<button id="' + prevId + '" class="btn btn-default"><i class="fa fa-chevron-left"></i></button>',
                        '<button id="' + nextId + '" class="btn btn-default"><i class="fa fa-chevron-right"></i></button>',
                        '</div>'
                    ].join(''));

                    var prev = $('#' + prevId);
                    prev.click(_page.bind(this, this.offset - this.maxRows, canPrev));
                    if (!canPrev) {
                        prev.addClass('disabled');
                    }

                    var next = $('#' + nextId);
                    next.click(_page.bind(this, this.offset + this.maxRows, canNext));
                    if (!canNext) {
                        next.addClass('disabled');
                    }
                }
            }
        }
        else {
            _getHeaderSelector(this).find('div.labkey-pagination').css('visibility', 'visible');
        }
    };

    var _page = function(offset, enabled) {
        if (enabled) {
            this.setPageOffset(offset);
        }
        return false;
    };

    var _firstPage = function(enabled) {
        if (enabled) {
            this.setPageOffset(0);
        }
        return false;
    };

    var _lastPage = function(enabled) {
        if (enabled) {
            var lastPageSize = this.totalRows % this.maxRows === 0 ? this.maxRows : this.totalRows % this.maxRows;
            this.setPageOffset(this.totalRows - lastPageSize);
        }
        return false;
    };

    var _setMaxRows = function(rows) {
        if (this.maxRows !== rows) {
            this.setMaxRows(rows);
        }
        return false;
    };

    /**
     * Forces the grid to show all rows, without any paging
     */
    LABKEY.DataRegion.prototype.showAllRows = function() {
        _showRows(this, 'all');
    };

    /**
     * @deprecated use showAllRows instead
     * @function
     * @see LABKEY.DataRegion#showAllRows
     */
    LABKEY.DataRegion.prototype.showAll = LABKEY.DataRegion.prototype.showAllRows;

    /**
     * Forces the grid to show only rows that have been selected
     */
    LABKEY.DataRegion.prototype.showSelectedRows = function() {
        _showRows(this, 'selected');
    };
    /**
     * @deprecated use showSelectedRows instead
     * @function
     * @see LABKEY.DataRegion#showSelectedRows
     */
    LABKEY.DataRegion.prototype.showSelected = LABKEY.DataRegion.prototype.showSelectedRows;

    /**
     * Forces the grid to show only rows that have not been selected
     */
    LABKEY.DataRegion.prototype.showUnselectedRows = function() {
        _showRows(this, 'unselected');
    };
    /**
     * @deprecated use showUnselectedRows instead
     * @function
     * @see LABKEY.DataRegion#showUnselectedRows
     */
    LABKEY.DataRegion.prototype.showUnselected = LABKEY.DataRegion.prototype.showUnselectedRows;

    /**
     * Forces the grid to do paging based on the current maximum number of rows
     */
    LABKEY.DataRegion.prototype.showPaged = function() {
        _removeParameters(this, [SHOW_ROWS_PREFIX]);
    };

    /**
     * Displays the first page of the grid
     */
    LABKEY.DataRegion.prototype.showFirstPage = function() {
        this.setPageOffset(0);
    };
    /**
     * @deprecated use showFirstPage instead
     * @function
     * @see LABKEY.DataRegion#showFirstPage
     */
    LABKEY.DataRegion.prototype.pageFirst = LABKEY.DataRegion.prototype.showFirstPage;

    /**
     * Changes the current row offset for paged content
     * @param rowOffset row index that should be at the top of the grid
     */
    LABKEY.DataRegion.prototype.setPageOffset = function(rowOffset) {
        var event = $.Event('beforeoffsetchange');

        $(this).trigger(event, [this, rowOffset]);

        if (event.isDefaultPrevented()) {
            return;
        }

        // clear sibling parameters
        this.showRows = undefined;

        if ($.isNumeric(rowOffset)) {
            _setParameter(this, OFFSET_PREFIX, rowOffset, [OFFSET_PREFIX, SHOW_ROWS_PREFIX]);
        }
        else {
            _removeParameters(this, [OFFSET_PREFIX, SHOW_ROWS_PREFIX]);
        }
    };
    /**
     * @deprecated use setPageOffset instead
     * @function
     * @see LABKEY.DataRegion#setPageOffset
     */
    LABKEY.DataRegion.prototype.setOffset = LABKEY.DataRegion.prototype.setPageOffset;

    /**
     * Changes the maximum number of rows that the grid will display at one time
     * @param newmax the maximum number of rows to be shown
     */
    LABKEY.DataRegion.prototype.setMaxRows = function(newmax) {
        var event = $.Event('beforemaxrowschange');
        $(this).trigger(event, [this, newmax]);
        if (event.isDefaultPrevented()) {
            return;
        }

        // clear sibling parameters
        this.showRows = undefined;
        this.offset = 0;

        _setParameter(this, MAX_ROWS_PREFIX, newmax, [OFFSET_PREFIX, MAX_ROWS_PREFIX, SHOW_ROWS_PREFIX]);
    };

    var _initContexts = function() {
        // clear old contents
        var ctxBar = _getContextBarSelector(this);
        ctxBar.find('.labkey-button-bar').remove();

        var numFilters = ctxBar.find('.fa-filter').length;
        var numParams = ctxBar.find('.fa-question').length;

        var html = [];

        if (numParams > 0) {
            html = html.concat([
                '<div class="labkey-button-bar" style="margin-top:10px;float:left;">',
                '<span class="labkey-button ctx-clear-var">Clear Variables</span>',
                '</div>'
            ])
        }

        if (numFilters >= 2) {
            html = html.concat([
                '<div class="labkey-button-bar" style="margin-top:10px;float:left;">',
                '<span class="labkey-button ctx-clear-all">' +
                (numParams > 0 ? 'Clear Filters' : 'Clear All') +
                '</span>',
                '</div>'
            ]);
        }

        if (html.length) {
            ctxBar.append(html.join(''));
            ctxBar.find('.ctx-clear-var').off('click').on('click', $.proxy(this.clearAllParameters, this));
            ctxBar.find('.ctx-clear-all').off('click').on('click', $.proxy(this.clearAllFilters, this));
        }
    };

    //
    // Customize View
    //
    var _initCustomViews = function() {
        if (this.view && this.view.session) {
            // clear old contents
            _getViewBarSelector(this).find('.labkey-button-bar').remove();

            _getViewBarSelector(this).append([
                '<div class="labkey-button-bar" style="margin-top:10px;float:left;">',
                    '<span style="padding:0 10px;">This grid view has been modified.</span>',
                    '<span class="labkey-button unsavedview-revert">Revert</span>',
                    '<span class="labkey-button unsavedview-edit">Edit</span>',
                    '<span class="labkey-button unsavedview-save">Save</span>',
                '</div>'
            ].join(''));
            _getViewBarSelector(this).find('.unsavedview-revert').off('click').on('click', $.proxy(function() {
                _revertCustomView(this);
            }, this));
            _getViewBarSelector(this).find('.unsavedview-edit').off('click').on('click', $.proxy(function() {
                this.showCustomizeView(undefined);
            }, this));
            _getViewBarSelector(this).find('.unsavedview-save').off('click').on('click', $.proxy(function() {
                _saveSessionCustomView(this);
            }, this));
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
    LABKEY.DataRegion.prototype.changeView = function(view, urlParameters) {
        var event = $.Event('beforechangeview');
        $(this).trigger(event, [this, view, urlParameters]);
        if (event.isDefaultPrevented()) {
            return;
        }

        var paramValPairs = [],
            newSort = [],
            skipPrefixes = [OFFSET_PREFIX, SHOW_ROWS_PREFIX, VIEWNAME_PREFIX, REPORTID_PREFIX];

        // clear sibling parameters
        this.viewName = undefined;
        this.reportId = undefined;

        if (view) {
            if (LABKEY.Utils.isString(view)) {
                paramValPairs.push([VIEWNAME_PREFIX, view]);
                this.viewName = view;
            }
            else if (view.type === 'report') {
                paramValPairs.push([REPORTID_PREFIX, view.reportId]);
                this.reportId = view.reportId;
            }
            else if (view.type === 'view' && view.viewName) {
                paramValPairs.push([VIEWNAME_PREFIX, view.viewName]);
                this.viewName = view.viewName;
            }
        }

        if (urlParameters) {
            $.each(urlParameters.filter, function(i, filter) {
                paramValPairs.push(['.' + filter.fieldKey + '~' + filter.op, filter.value]);
            });

            if (urlParameters.sort && urlParameters.sort.length > 0) {
                $.each(urlParameters.sort, function(i, sort) {
                    newSort.push((sort.dir === '+' ? '' : sort.dir) + sort.fieldKey);
                });
                paramValPairs.push([SORT_PREFIX, newSort.join(',')]);
            }

            if (urlParameters.containerFilter) {
                paramValPairs.push([CONTAINER_FILTER_NAME, urlParameters.containerFilter]);
            }

            // removes all filter, sort, and container filter parameters
            skipPrefixes = skipPrefixes.concat([
                ALL_FILTERS_SKIP_PREFIX, SORT_PREFIX, COLUMNS_PREFIX, CONTAINER_FILTER_NAME
            ]);
        }

        // removes all filter, sort, and container filter parameters
        _setParameters(this, paramValPairs, skipPrefixes);
    };

    LABKEY.DataRegion.prototype.getQueryDetails = function(success, failure, scope) {

        var userFilter = [],
            userSort = this.getUserSort(),
            userColumns = this.getParameter(this.name + COLUMNS_PREFIX),
            fields = [],
            viewName = (this.view && this.view.name) || this.viewName || '';

        $.each(this.getUserFilterArray(), function(i, filter) {
            userFilter.push({
                fieldKey: filter.getColumnName(),
                op: filter.getFilterType().getURLSuffix(),
                value: filter.getValue()
            });
        });

        $.each(userFilter, function(i, filter) {
            fields.push(filter.fieldKey);
        });

        $.each(userSort, function(i, sort) {
            fields.push(sort.fieldKey);
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
    LABKEY.DataRegion.prototype.hideCustomizeView = function() {
        if (this.activePanelId === CUSTOM_VIEW_PANELID) {
            this.hideButtonPanel();
        }
    };

    /**
     * Show the customize view interface.
     * @param activeTab {[String]} Optional. One of "ColumnsTab", "FilterTab", or "SortTab".  If no value is specified (or undefined), the ColumnsTab will be shown.
     */
    LABKEY.DataRegion.prototype.showCustomizeView = function(activeTab) {
        var region = this;

        var panelConfig = this.getPanelConfiguration(CUSTOM_VIEW_PANELID);

        if (!panelConfig) {

            // whistle while we wait
            var timerId = setTimeout(function() {
                timerId = 0;
                region.showLoadingMessage("Opening custom view designer...");
            }, 500);

            LABKEY.DataRegion.loadViewDesigner(function() {

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

                    this.customizeView = Ext4.create('LABKEY.internal.ViewDesigner.Designer', {
                        renderTo: Ext4.getBody().createChild({tag: 'div', customizeView: true, style: {display: 'none'}}),
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

                    this.customizeView.on({
                        beforedeleteview: function(cv, revert) {
                            _beforeViewDelete(region, revert);
                        },
                        deleteview: function(cv, success, json) {
                            _onViewDelete(region, success, json);
                        }
                    });

                    var first = true;

                    // Called when customize view needs to be shown
                    var showFn = function(id, panel, element, callback, scope) {
                        if (first) {
                            panel.hide();
                            panel.getEl().appendTo(Ext4.get(element[0]));
                            first = false;
                        }
                        panel.doLayout();
                        $(panel.getEl().dom).slideDown(undefined, function() {
                            panel.show();
                            callback.call(scope);
                        });
                    };

                    // Called when customize view needs to be hidden
                    var hideFn = function(id, panel, element, callback, scope) {
                        $(panel.getEl().dom).slideUp(undefined, function() {
                            panel.hide();
                            callback.call(scope);
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
     * @ignore
     * @private
     * Shows/Hides customize view depending on if it is currently shown
     */
    LABKEY.DataRegion.prototype.toggleShowCustomizeView = function() {
        if (this.activePanelId === CUSTOM_VIEW_PANELID) {
            this.hideCustomizeView();
        }
        else {
            this.showCustomizeView(undefined);
        }
    };

    var _defaultShow = function(panelId, panel, ribbon, cb, cbScope) {
        $('#' + panelId).slideDown(undefined, function() {
            cb.call(cbScope);
        });
    };

    var _defaultHide = function(panelId, panel, ribbon, cb, cbScope) {
        $('#' + panelId).slideUp(undefined, function() {
            cb.call(cbScope);
        });
    };

    LABKEY.DataRegion.prototype.publishPanel = function(panelId, panel, showFn, hideFn, scope) {
        this.panelConfigurations[panelId] = {
            panel: panel,
            show: $.isFunction(showFn) ? showFn : _defaultShow,
            hide: $.isFunction(hideFn) ? hideFn : _defaultHide,
            scope: scope
        };
        return this;
    };

    LABKEY.DataRegion.prototype.getPanelConfiguration = function(panelId) {
        return this.panelConfigurations[panelId];
    };

    /**
     * @ignore
     * Hides any panel that is currently visible. Returns a callback once the panel is hidden.
     */
    LABKEY.DataRegion.prototype.hidePanel = function(callback, scope) {
        if (this.activePanelId) {
            var config = this.getPanelConfiguration(this.activePanelId);
            if (config) {

                // find the ribbon container
                var ribbon = _getDrawerSelector(this);

                config.hide.call(config.scope || this, this.activePanelId, config.panel, ribbon, function() {
                    this.activePanelId = undefined;
                    ribbon.hide();
                    if ($.isFunction(callback)) {
                        callback.call(scope || this);
                    }
                    LABKEY.Utils.signalWebDriverTest("dataRegionPanelHide");
                    $(this).trigger($.Event('afterpanelhide'), [this]);
                }, this);
            }
        }
        else {
            if ($.isFunction(callback)) {
                callback.call(scope || this);
            }
        }
    };

    LABKEY.DataRegion.prototype.showPanel = function(panelId, callback, scope) {

        var config = this.getPanelConfiguration(panelId);

        if (!config) {
            console.error('Unable to find panel for id (' + panelId + '). Use publishPanel() to register a panel to be shown.');
            return;
        }

        this.hideContext();
        this.hideMessage(true);

        this.hidePanel(function() {
            this.activePanelId = panelId;

            // ensure the ribbon is visible
            var ribbon = _getDrawerSelector(this);
            ribbon.show();

            config.show.call(config.scope || this, this.activePanelId, config.panel, ribbon, function() {
                if ($.isFunction(callback)) {
                    callback.call(scope || this);
                }
                LABKEY.Utils.signalWebDriverTest("dataRegionPanelShow");
                $(this).trigger($.Event('afterpanelshow'), [this]);
           }, this);
        }, this);
    };

    //
    // Misc
    //

    /**
     * @private
     */
    var _initHeaderLocking = function() {
        if (this._allowHeaderLock === true) {
            this.hLock = new HeaderLock(this);
        }
    };

    /**
     * @private
     */
    var _initPanes = function() {
        var callbacks = _paneCache[this.name];
        if (callbacks) {
            var me = this;
            $.each(callbacks, function(i, config) {
                config.cb.call(config.scope || me, me);
            });
            delete _paneCache[this.name];
        }
    };

    // These study specific functions/constants should be moved out of Data Region
    // and into their own dependency.

    var COHORT_LABEL = '/Cohort/Label';
    var ADV_COHORT_LABEL = '/InitialCohort/Label';
    var COHORT_ENROLLED = '/Cohort/Enrolled';
    var ADV_COHORT_ENROLLED = '/InitialCohort/Enrolled';

    /**
     * DO NOT CALL DIRECTLY. This method is private and only available for removing cohort/group filters
     * for this Data Region.
     * @param subjectColumn
     * @param groupNames
     * @private
     */
    LABKEY.DataRegion.prototype._removeCohortGroupFilters = function(subjectColumn, groupNames) {
        var params = _getParameters(this);
        var skips = [], i, p, k;

        var keys = [
            subjectColumn + COHORT_LABEL,
            subjectColumn + ADV_COHORT_LABEL,
            subjectColumn + COHORT_ENROLLED,
            subjectColumn + ADV_COHORT_ENROLLED
        ];

        if ($.isArray(groupNames)) {
            for (k=0; k < groupNames.length; k++) {
                keys.push(subjectColumn + '/' + groupNames[k]);
            }
        }

        for (i = 0; i < params.length; i++) {
            p = params[i][0];
            if (p.indexOf(this.name + '.') == 0) {
                for (k=0; k < keys.length; k++) {
                    if (p.indexOf(keys[k] + '~') > -1) {
                        skips.push(p);
                        k = keys.length; // break loop
                    }
                }
            }
        }

        _updateFilter(this, undefined, skips);
    };

    /**
     * DO NOT CALL DIRECTLY. This method is private and only available for replacing advanced cohort filters
     * for this Data Region. Remove if advanced cohorts are removed.
     * @param filter
     * @private
     */
    LABKEY.DataRegion.prototype._replaceAdvCohortFilter = function(filter) {
        var params = _getParameters(this);
        var skips = [], i, p;

        for (i = 0; i < params.length; i++) {
            p = params[i][0];
            if (p.indexOf(this.name + '.') == 0) {
                if (p.indexOf(COHORT_LABEL) > -1 || p.indexOf(ADV_COHORT_LABEL) > -1 || p.indexOf(COHORT_ENROLLED) > -1 || p.indexOf(ADV_COHORT_ENROLLED)) {
                    skips.push(p);
                }
            }
        }

        _updateFilter(this, filter, skips);
    };

    /**
     * Looks for a column based on fieldKey, name, displayField, or caption (in that order)
     * @param columnIdentifier
     * @returns {*}
     */
    LABKEY.DataRegion.prototype.getColumn = function(columnIdentifier) {

        var column = null, // backwards compat
            isString = LABKEY.Utils.isString,
            cols = this.columns;

        if (isString(columnIdentifier) && $.isArray(cols)) {
            $.each(['fieldKey', 'name', 'displayField', 'caption'], function(i, key) {
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
    LABKEY.DataRegion.prototype.getQueryConfig = function() {
        var config = {
            dataRegionName: this.name,
            dataRegionSelectionKey: this.selectionKey,
            schemaName: this.schemaName,
            viewName: this.viewName,
            sort: this.getParameter(this.name + SORT_PREFIX),
            // NOTE: The parameterized query values from QWP are included
            parameters: this.getParameters(false),
            containerFilter: this.containerFilter
        };

        if (this.queryName) {
            config.queryName = this.queryName;
        }
        else if (this.sql) {
            config.sql = this.sql;
        }

        var filters = this.getUserFilterArray();
        if (filters.length > 0) {
            config.filters = filters;
        }

        return config;
    };

    /**
     * Hide the ribbon panel. If visible the ribbon panel will be hidden.
     */
    LABKEY.DataRegion.prototype.hideButtonPanel = function() {
        this.hidePanel();
        this.showContext();
        this.showMessageArea();
    };

    /**
     * Allows for asynchronous rendering of the Data Region. This region must be in "async" mode for
     * this to do anything.
     * @function
     * @param {String} [renderTo] - The element ID where to render the data region. If not given it will default to
     * the current renderTo target is.
     */
    LABKEY.DataRegion.prototype.render = function(renderTo) {
        if (!this.RENDER_LOCK && this.async) {
            _convertRenderTo(this, renderTo);
            this.refresh();
        }
    };

    /**
     * Show a ribbon panel.
     */
    LABKEY.DataRegion.prototype.showButtonPanel = function(panelButton) {

        var ribbon = _getDrawerSelector(this),
            panelId = $(panelButton).attr('panel-toggle'),
            panelSel;

        if (panelId) {

            panelSel = $('#' + panelId);

            // allow for toggling the state
            if (panelId === this.activePanelId) {
                this.hideButtonPanel();
            }
            else {
                // determine if the content needs to be moved to the ribbon
                if (ribbon.has(panelSel).length === 0) {
                    panelSel.detach().appendTo(ribbon);
                }

                // determine if this panel has been registered
                if (!this.getPanelConfiguration(panelId)) {
                    this.publishPanel(panelId);
                }

                this.showPanel(panelId);
            }
        }
    };

    LABKEY.DataRegion.prototype.loadFaceting = function(cb, scope) {

        var region = this;

        var onLoad = function() {
            region.facetLoaded = true;
            if ($.isFunction(cb)) {
                cb.call(scope || this);
            }
        };

        LABKEY.requiresExt4ClientAPI(function() {
            if (LABKEY.devMode) {
                // should match study/ParticipantFilter.lib.xml
                LABKEY.requiresScript([
                    '/study/ReportFilterPanel.js',
                    '/study/ParticipantFilterPanel.js'
                ], function() {
                    LABKEY.requiresScript('/dataregion/panel/Facet.js', onLoad);
                });
            }
            else {
                LABKEY.requiresScript('/study/ParticipantFilter.min.js', function() {
                    LABKEY.requiresScript('/dataregion/panel/Facet.js', onLoad);
                });
            }
        }, this);
    };

    LABKEY.DataRegion.prototype.showFaceting = function() {
        if (this.facetLoaded) {
            if (!this.facet) {
                this.facet = LABKEY.dataregion.panel.Facet.display(this);
            }
            this.facet.toggleCollapse();
        }
        else {
            this.loadFaceting(this.showFaceting, this);
        }
    };

    LABKEY.DataRegion.prototype.on = function(evt, callback, scope) {
        // Prevent from handing back the jQuery event itself.
        $(this).bind(evt, function() { callback.apply(scope || this, $(arguments).slice(1)); });
    };

    LABKEY.DataRegion.prototype._onButtonClick = function(buttonId) {
        var item = this.findButtonById(this.buttonBar.items, buttonId);
        if (item && $.isFunction(item.handler)) {
            try {
                return item.handler.call(item.scope || this, this);
            }
            catch(ignore) {}
        }
        return false;
    };

    LABKEY.DataRegion.prototype.findButtonById = function(items, id) {
        if (!items || !items.length || items.length <= 0) {
            return null;
        }

        var ret;
        for (var i = 0; i < items.length; i++) {
            if (items[i].id == id) {
                return items[i];
            }
            ret = this.findButtonById(items[i].items, id);
            if (null != ret) {
                return ret;
            }
        }
        
        return null;
    };

    LABKEY.DataRegion.prototype.headerLock = function() { return this._allowHeaderLock === true; };

    LABKEY.DataRegion.prototype.disableHeaderLock = function() {
        if (this.headerLock() && this.hLock) {
            this.hLock.disable();
            this.hLock = undefined;
        }
    };

    /**
     * Add or remove a summary statistic for a given column in the DataRegion query view.
     * @param viewName
     * @param colFieldKey
     * @param summaryStatName
     */
    LABKEY.DataRegion.prototype.toggleSummaryStatForCustomView = function(viewName, colFieldKey, summaryStatName) {
        this.getQueryDetails(function(queryDetails) {
            var view = _getViewFromQueryDetails(queryDetails, viewName);
            if (view && _queryDetailsContainsColumn(queryDetails, colFieldKey)) {
                var colProviderNames = [];
                $.each(view.analyticsProviders, function(index, existingProvider) {
                    if (existingProvider.fieldKey === colFieldKey)
                        colProviderNames.push(existingProvider.name);
                });

                if (colProviderNames.indexOf(summaryStatName) === -1) {
                    _addAnalyticsProviderToView.call(this, view, colFieldKey, summaryStatName, true);
                }
                else {
                    _removeAnalyticsProviderFromView.call(this, view, colFieldKey, summaryStatName, true);
                }
            }
        }, null, this);
    };

    /**
     * Get the array of selected ColumnAnalyticsProviders for the given column FieldKey in a view.
     * @param viewName
     * @param colFieldKey
     * @param callback
     * @param callbackScope
     */
    LABKEY.DataRegion.prototype.getColumnAnalyticsProviders = function(viewName, colFieldKey, callback, callbackScope) {
        this.getQueryDetails(function(queryDetails) {
            var view = _getViewFromQueryDetails(queryDetails, viewName);
            if (view && _queryDetailsContainsColumn(queryDetails, colFieldKey)) {
                var colProviderNames = [];
                $.each(view.analyticsProviders, function(index, existingProvider) {
                    if (existingProvider.fieldKey === colFieldKey) {
                        colProviderNames.push(existingProvider.name);
                    }
                });

                if ($.isFunction(callback)) {
                    callback.call(callbackScope, colProviderNames);
                }
            }
        }, null, this);
    };

    /**
     * Set the summary statistic ColumnAnalyticsProviders for the given column FieldKey in the view.
     * @param viewName
     * @param colFieldKey
     * @param summaryStatProviderNames
     */
    LABKEY.DataRegion.prototype.setColumnSummaryStatistics = function(viewName, colFieldKey, summaryStatProviderNames) {
        this.getQueryDetails(function(queryDetails) {
            var view = _getViewFromQueryDetails(queryDetails, viewName);
            if (view && _queryDetailsContainsColumn(queryDetails, colFieldKey)) {
                var newAnalyticsProviders = [];
                $.each(view.analyticsProviders, function(index, existingProvider) {
                    if (existingProvider.fieldKey !== colFieldKey || existingProvider.name.indexOf('AGG_') != 0) {
                        newAnalyticsProviders.push(existingProvider);
                    }
                });

                $.each(summaryStatProviderNames, function(index, providerName) {
                    newAnalyticsProviders.push({
                        fieldKey: colFieldKey,
                        name: providerName,
                        isSummaryStatistic: true
                    });
                });

                view.analyticsProviders = newAnalyticsProviders;
                _updateSessionCustomView.call(this, view, true);
            }
        }, null, this);
    };

    /**
     * Remove a column from the given DataRegion query view.
     * @param viewName
     * @param colFieldKey
     */
    LABKEY.DataRegion.prototype.removeColumn = function(viewName, colFieldKey) {
        this.getQueryDetails(function(queryDetails) {
            var view = _getViewFromQueryDetails(queryDetails, viewName);
            if (view && _queryDetailsContainsColumn(queryDetails, colFieldKey)) {
                var colFieldKeys = $.map(view.columns, function (c) {
                            return c.fieldKey;
                        }),
                        fieldKeyIndex = colFieldKeys.indexOf(colFieldKey);

                if (fieldKeyIndex > -1) {
                    view.columns.splice(fieldKeyIndex, 1);
                    _updateSessionCustomView.call(this, view, true);
                }
            }
        }, null, this);
    };

    /**
     * Add the enabled analytics provider to the custom view definition based on the column fieldKey and provider name.
     * In addition, disable the column menu item if the column is visible in the grid.
     * @param viewName
     * @param colFieldKey
     * @param providerName
     */
    LABKEY.DataRegion.prototype.addAnalyticsProviderForCustomView = function(viewName, colFieldKey, providerName) {
        this.getQueryDetails(function(queryDetails) {
            var view = _getViewFromQueryDetails(queryDetails, viewName);
            if (view && _queryDetailsContainsColumn(queryDetails, colFieldKey)) {
                _addAnalyticsProviderToView.call(this, view, colFieldKey, providerName, false);

                var elementId = this.name + ':' + colFieldKey + ':analytics-' + providerName;
                Ext4.each(Ext4.ComponentQuery.query('menuitem[elementId=' + elementId + ']'), function(menuItem) {
                    menuItem.disable();
                });
            }
        }, null, this);
    };

    /**
     * Remove an enabled analytics provider from the custom view definition based on the column fieldKey and provider name.
     * In addition, enable the column menu item if the column is visible in the grid.
     * @param viewName
     * @param colFieldKey
     * @param providerName
     */
    LABKEY.DataRegion.prototype.removeAnalyticsProviderForCustomView = function(viewName, colFieldKey, providerName) {
        this.getQueryDetails(function(queryDetails) {
            var view = _getViewFromQueryDetails(queryDetails, viewName);
            if (view && _queryDetailsContainsColumn(queryDetails, colFieldKey)) {
                _removeAnalyticsProviderFromView.call(this, view, colFieldKey, providerName, false);

                var elementId = this.name + ':' + colFieldKey + ':analytics-' + providerName;
                Ext4.each(Ext4.ComponentQuery.query('menuitem[elementId=' + elementId + ']'), function(menuItem) {
                    menuItem.enable();
                });
            }
        }, null, this);
    };

    /**
     * @private
     */
    LABKEY.DataRegion.prototype._openFilter = function(columnName, evt) {
        if (evt && $(evt.target).hasClass('fa-close')) {
            return;
        }

        var column = this.getColumn(columnName);

        if (column) {
            var show = function() {
                this._dialogLoaded = true;
                new LABKEY.FilterDialog({
                    dataRegionName: this.name,
                    column: this.getColumn(columnName),
                    cacheFacetResults: false // could have changed on Ajax
                }).show();
            }.bind(this);

            this._dialogLoaded ? show() : LABKEY.requiresExt3ClientAPI(show);
        }
        else {
            LABKEY.Utils.alert('Column not available', 'Unable to find column "' + columnName + '" in this view.');
        }
    };

    var _updateSessionCustomView = function(customView, requiresRefresh) {
        var viewConfig = $.extend({}, customView, {
            shared: false,
            inherit: false,
            session: true
        });

        LABKEY.Query.saveQueryViews({
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            views: [viewConfig],
            scope: this,
            success: function(info) {
                if (requiresRefresh) {
                    this.refresh();
                }
                else if (info.views.length === 1) {
                    this.view = info.views[0];
                    _initCustomViews.call(this);
                    this.showContext();
                }
            }
        });
    };

    var _addAnalyticsProviderToView = function(view, colFieldKey, providerName, isSummaryStatistic) {
        var colProviderNames = [];
        $.each(view.analyticsProviders, function(index, existingProvider) {
            if (existingProvider.fieldKey === colFieldKey)
                colProviderNames.push(existingProvider.name);
        });

        if (colProviderNames.indexOf(providerName) === -1) {
            view.analyticsProviders.push({
                fieldKey: colFieldKey,
                name: providerName,
                isSummaryStatistic: isSummaryStatistic
            });

            _updateSessionCustomView.call(this, view, isSummaryStatistic);
        }
    };

    var _removeAnalyticsProviderFromView = function(view, colFieldKey, providerName, isSummaryStatistic) {
        var indexToRemove = null;
        $.each(view.analyticsProviders, function(index, existingProvider) {
            if (existingProvider.fieldKey === colFieldKey && existingProvider.name === providerName) {
                indexToRemove = index;
                return false;
            }
        });

        if (indexToRemove != null)
        {
            view.analyticsProviders.splice(indexToRemove, 1);
            _updateSessionCustomView.call(this, view, isSummaryStatistic);
        }
    };

    //
    // PRIVATE FUNCTIONS
    //
    var _applyOptionalParameters = function(region, params, optionalParams) {
        $.each(optionalParams, function(i, p) {
            if (LABKEY.Utils.isObject(p)) {
                if (region[p.name] !== undefined) {
                    if (p.check && !p.check.call(region, region[p.name])) {
                        return;
                    }
                    if (p.prefix) {
                        params[region.name + '.' + p.name] = region[p.name];
                    }
                    else {
                        params[p.name] = region[p.name];
                    }
                }
            }
            else if (p && region[p] !== undefined) {
                params[p] = region[p];
            }
        });
    };

    var _alterSortString = function(region, current, fieldKey, direction /* optional */) {
        fieldKey = _resolveFieldKey(region, fieldKey);

        var columnName = fieldKey.toString(),
            newSorts = [];

        if (current != null) {
            var sorts = current.split(',');
            $.each(sorts, function(i, sort) {
                if (sort.length > 0 && (sort != columnName) && (sort != SORT_ASC + columnName) && (sort != SORT_DESC + columnName)) {
                    newSorts.push(sort);
                }
            });
        }

        if (direction === SORT_ASC) { // Easier to read without the encoded + on the URL...
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

    var _chainSelectionCountCallback = function(region, config) {

        var success = LABKEY.Utils.getOnSuccess(config);

        // On success, update the current selectedCount on this DataRegion and fire the 'selectchange' event
        config.success = function(data) {
            region.selectionModified = true;
            region.selectedCount = data.count;
            _onSelectionChange(region);

            // Chain updateSelected with the user-provided success callback
            if ($.isFunction(success)) {
                success.call(config.scope, data);
            }
        };

        return config;
    };

    var _convertRenderTo = function(region, renderTo) {
        if (renderTo) {
            if (LABKEY.Utils.isString(renderTo)) {
                region.renderTo = renderTo;
            }
            else if (LABKEY.Utils.isString(renderTo.id)) {
                region.renderTo = renderTo.id; // support 'Ext' elements
            }
            else {
                throw 'Unsupported "renderTo"';
            }
        }

        return region;
    };

    var _deleteTimer;

    var _beforeViewDelete = function(region, revert) {
        _deleteTimer = setTimeout(function() {
            _deleteTimer = 0;
            region.showLoadingMessage(revert ? 'Reverting view...' : 'Deleting view...');
        }, 500);
    };

    var _onViewDelete = function(region, success, json) {
        if (_deleteTimer) {
            clearTimeout(_deleteTimer);
        }

        if (success) {
            region.removeMessage.call(region, 'customizeview');
            region.showSuccessMessage.call(region);

            // change view to either a shadowed view or the default view
            var config = { type: 'view' };
            if (json.viewName) {
                config.viewName = json.viewName;
            }
            region.changeView.call(region, config);
        }
        else {
            region.removeMessage.call(region, 'customizeview');
            region.showErrorMessage.call(region, json.exception);
        }
    };

    // The view can be reverted without ViewDesigner present
    var _revertCustomView = function(region) {
        _beforeViewDelete(region, true);

        var config = {
            schemaName: region.schemaName,
            queryName: region.queryName,
            containerPath: region.containerPath,
            revert: true,
            success: function(json) {
                _onViewDelete(region, true /* success */, json);
            },
            failure: function(json) {
                _onViewDelete(region, false /* success */, json);
            }
        };

        if (region.viewName) {
            config.viewName = region.viewName;
        }

        LABKEY.Query.deleteQueryView(config);
    };

    var _getViewFromQueryDetails = function(queryDetails, viewName) {
        var matchingView;

        $.each(queryDetails.views, function(index, view) {
            if (view.name === viewName) {
                matchingView = view;
                return false;
            }
        });

        return matchingView;
    };

    var _queryDetailsContainsColumn = function(queryDetails, colFieldKey) {
        var keys = $.map(queryDetails.columns, function(c){ return c.fieldKey; }),
            exists = keys.indexOf(colFieldKey) > -1;

        if (!exists) {
            console.warn('Unable to find column in query: ' + colFieldKey);
        }

        return exists;
    };

    var _getAllRowSelectors = function(region) {
        return _getFormSelector(region).find('.labkey-selectors input[type="checkbox"][name=".toggle"]');
    };

    var _getBarSelector = function(region) {
        return $('#' + region.domId + '-headerbar');
    };

    var _getContextBarSelector = function(region) {
        return $('#' + region.domId + '-ctxbar');
    };

    var _getDrawerSelector = function(region) {
        return $('#' + region.domId + '-drawer');
    };

    var _getFormSelector = function(region) {
        var form = $('form#' + region.domId + '-form');

        // derived DataRegion's may not include the form id
        if (form.length === 0) {
            form = $('#' + region.domId).closest('form');
        }

        return form;
    };

    var _getHeaderSelector = function(region) {
        return $('#' + region.domId + '-header');
    };

    var _getRowSelectors = function(region) {
        return _getFormSelector(region).find('.labkey-selectors input[type="checkbox"][name=".select"]');
    };

    var _getSectionSelector = function(region, dir) {
        return $('#' + region.domId + '-section-' + dir);
    };

    // Formerly, LABKEY.DataRegion.getParamValPairsFromString / LABKEY.DataRegion.getParamValPairs
    var _getParameters = function(region, skipPrefixSet /* optional */) {

        var params = [];
        var qString = region.requestURL;

        if (LABKEY.Utils.isString(qString) && qString.length > 0) {

            var qmIdx = qString.indexOf('?');
            if (qmIdx > -1) {
                qString = qString.substring(qmIdx + 1);
            }

            if (qString.length > 1) {
                var pairs = qString.split('&'), p, key,
                    LAST = '.lastFilter', lastIdx, skip = $.isArray(skipPrefixSet);

                $.each(pairs, function(i, pair) {
                    p = pair.split('=', 2);
                    key = p[0] = decodeURIComponent(p[0]);
                    lastIdx = key.indexOf(LAST);

                    if (lastIdx > -1 && lastIdx == (key.length - LAST.length)) {
                        return;
                    }
                    else if (REQUIRE_NAME_PREFIX.hasOwnProperty(key)) {
                        // 26686: Black list known parameters, should be prefixed by region name
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
                                            key.indexOf('~') > 0 ||
                                            key.indexOf(PARAM_PREFIX) > 0 ||
                                            key == (skipPrefix + 'sort')) {
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
        }

        return params;
    };

    /**
     * 
     * @param region
     * @param {boolean} [asString=false]
     * @private
     */
    var _getUserSort = function(region, asString) {
        var userSort = [],
            sortParam = region.getParameter(region.name + SORT_PREFIX);

        if (asString) {
            userSort = sortParam || '';
        }
        else {
            if (sortParam) {
                var fieldKey, dir;
                $.each(sortParam.split(','), function(i, sort) {
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
        }

        return userSort;
    };

    var _getViewBarSelector = function(region) {
        return $('#' + region.domId + '-viewbar');
    };

    var _buttonSelectionBind = function(region, cls, fn) {
        var partEl = region.msgbox.getParent().find('div[data-msgpart="selection"]');
        partEl.find('.labkey-button' + cls).off('click').on('click', $.proxy(function() {
            fn.call(this);
        }, region));
    };

    var _onRenderMessageArea = function(region, parts) {
        var msgArea = region.msgbox;
        if (msgArea) {
            if (region.showRecordSelectors && parts['selection']) {
                _buttonSelectionBind(region, '.select-all', region.selectAll);
                _buttonSelectionBind(region, '.select-none', region.clearSelected);
                _buttonSelectionBind(region, '.show-all', region.showAll);
                _buttonSelectionBind(region, '.show-selected', region.showSelectedRows);
                _buttonSelectionBind(region, '.show-unselected', region.showUnselectedRows);
            }
            else if (parts['customizeview']) {
                _buttonSelectionBind(region, '.unsavedview-revert', function() { _revertCustomView(this); });
                _buttonSelectionBind(region, '.unsavedview-edit', function() { this.showCustomizeView(undefined); });
                _buttonSelectionBind(region, '.unsavedview-save', function() { _saveSessionCustomView(this); });
            }
        }
    };

    var _onSelectionChange = function(region) {
        $(region).trigger('selectchange', [region, region.selectedCount]);
        _updateRequiresSelectionButtons(region, region.selectedCount);
        LABKEY.Utils.signalWebDriverTest('dataRegionUpdate', region.selectedCount);
        LABKEY.Utils.signalWebDriverTest('dataRegionUpdate-' + region.name, region.selectedCount);
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
        var viewName = (region.view && region.view.name) || region.viewName || '';

        LABKEY.Query.getQueryDetails({
            schemaName: region.schemaName,
            queryName: region.queryName,
            viewName: viewName,
            initializeMissingView: false,
            containerPath: region.containerPath,
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
            canEdit: LABKEY.DataRegion.getCustomViewEditableErrors(config).length == 0,
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
                    },
                    success: function() {
                        region.showSuccessMessage.call(region);
                        region.changeView.call(region, {type: 'view', viewName: o.name});
                    },
                    failure: function(json) {
                        Ext4.Msg.alert('Error saving view', json.exception || json.statusText);
                    },
                    scope: region
                });
            },
            scope: region
        }, region.view);

        LABKEY.DataRegion.loadViewDesigner(function() {
            LABKEY.internal.ViewDesigner.Designer.saveCustomizeViewPrompt(config);
        });
    };

    var _setParameter = function(region, param, value, skipPrefixes /* optional */) {
        _setParameters(region, [[param, value]], skipPrefixes);
    };

    var _setParameters = function(region, newParamValPairs, skipPrefixes /* optional */) {

        // prepend region name
        // e.g. ['.hello', '.goodbye'] becomes ['aqwp19.hello', 'aqwp19.goodbye']
        if ($.isArray(skipPrefixes)) {
            $.each(skipPrefixes, function(i, skip) {
                if (skip && skip.indexOf(region.name + '.') !== 0) {
                    skipPrefixes[i] = region.name + skip;
                }
            });
        }

        var param, value,
            params = _getParameters(region, skipPrefixes);

        if ($.isArray(newParamValPairs)) {
            $.each(newParamValPairs, function(i, newPair) {
                if (!$.isArray(newPair)) {
                    throw new Error("DataRegion: _setParameters newParamValPairs improperly initialized. It is an array of arrays. You most likely passed in an array of strings.");
                }
                param = newPair[0];
                value = newPair[1];

                // Allow value to be null/undefined to support no-value filter types (Is Blank, etc)
                if (LABKEY.Utils.isString(param) && param.length > 1) {
                    if (param.indexOf(region.name) !== 0) {
                        param = region.name + param;
                    }

                    params.push([param, value]);
                }
            });
        }

        if (region.async) {
            _load(region, undefined, undefined, params);
        }
        else {
            region.setSearchString.call(region, region.name, _buildQueryString(region, params));
        }
    };

    var _showRows = function(region, showRowsEnum) {
        // clear sibling parameters, could we do this with events?
        this.maxRows = undefined;
        this.offset = 0;

        _setParameter(region, SHOW_ROWS_PREFIX, showRowsEnum, [OFFSET_PREFIX, MAX_ROWS_PREFIX, SHOW_ROWS_PREFIX]);
    };

    var _showSelectMessage = function(region, msg) {
        if (region.showRecordSelectors) {
            if (region.totalRows && region.totalRows != region.selectedCount) {
                msg += "&nbsp;<span class='labkey-button select-all'>Select All " + region.totalRows + " Rows</span>";
            }

            msg += "&nbsp;" + "<span class='labkey-button select-none'>Select None</span>";
            var showOpts = [];
            if (region.showRows !== 'all')
                showOpts.push("<span class='labkey-button show-all'>Show All</span>");
            if (region.showRows !== 'selected')
                showOpts.push("<span class='labkey-button show-selected'>Show Selected</span>");
            if (region.showRows !== 'unselected')
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

        _getAllRowSelectors(region).each(function() { this.checked = checked === true; });
        return ids;
    };

    var _load = function(region, callback, scope, newParams) {

        var params = _getAsyncParams(region, newParams ? newParams : _getParameters(region));
        var jsonData = _getAsyncBody(region, params);

        // TODO: This should be done in _getAsyncParams, but is not since _getAsyncBody relies on it. Refactor it.
        // ensure SQL is not on the URL -- we allow any property to be pulled through when creating parameters.
        if (params.sql) {
            delete params.sql;
        }

        /**
         * The target jQuery element that will be either written to or replaced
         */
        var target;

        /**
         * Flag used to determine if we should replace target element (default) or write to the target contents
         * (used during QWP render for example)
         * @type {boolean}
         */
        var useReplace = true;

        /**
         * The string identifier for where the region will render. Mainly used to display useful messaging upon failure.
         * @type {string}
         */
        var renderEl;

        if (region.renderTo) {
            useReplace = false;
            renderEl = region.renderTo;
            target = $('#' + region.renderTo);
        }
        else if (!region.domId) {
            throw '"renderTo" must be specified either upon construction or when calling render()';
        }
        else {
            renderEl = region.domId;
            target = $('#' + region.domId);

            // attempt to find the correct node to render to...
            var form = _getFormSelector(region);
            if (form.length && form.parent('div').length) {
                target = form.parent('div');
            }
            else {
                // next best render target
                throw 'unable to find a good target element. Perhaps this region is not using the standard renderer?'
            }
        }
        var timerId = setTimeout(function() {
            timerId = 0;
            if (target) {
                target.html("<div class=\"labkey-data-region-loading-mask-panel\">" +
                        "<div class=\"labkey-data-region-loading-mask-icon\"><i class=\"fa fa-spinner fa-pulse\"></i> loading...</div>" +
                        "</div>");
            }
        }, 500);

        LABKEY.Ajax.request({
            timeout: region.timeout === undefined ? DEFAULT_TIMEOUT : region.timeout,
            url: LABKEY.ActionURL.buildURL('project', 'getWebPart.api', region.containerPath),
            method: 'POST',
            params: params,
            jsonData: jsonData,
            success: function(response) {
                if (timerId > 0) {
                    clearTimeout(timerId);//load mask task no longer needed
                }
                this.hidePanel(function() {
                    if (target.length) {

                        this.destroy();

                        LABKEY.Utils.loadAjaxContent(response, target, function() {

                            if ($.isFunction(callback)) {
                                callback.call(scope);
                            }

                            if ($.isFunction(this._success)) {
                                this._success.call(this.scope || this, this, response);
                            }

                            $(this).trigger('success', [this, response]);

                            this.RENDER_LOCK = true;
                            $(this).trigger('render', this);
                            this.RENDER_LOCK = false;
                        }, this, useReplace);
                    }
                    else {
                        // not finding element considered a failure
                        if ($.isFunction(this._failure)) {
                            this._failure.call(this.scope || this, response /* json */, response, undefined /* options */, target);
                        }
                        else if (!this.suppressRenderErrors) {
                            LABKEY.Utils.alert('Rendering Error', 'The element "' + renderEl + '" does not exist in the document. You may need to specify "renderTo".');
                        }
                    }
                }, this);
            },
            failure: LABKEY.Utils.getCallbackWrapper(function(json, response, options) {

                if (target.length) {
                    if ($.isFunction(this._failure)) {
                        this._failure.call(this.scope || this, json, response, options);
                    }
                    else if (this.errorType === 'html') {
                        if (useReplace) {
                            target.replaceWith('<div class="labkey-error">' + LABKEY.Utils.encodeHtml(json.exception) + '</div>');
                        }
                        else {
                            target.html('<div class="labkey-error">' + LABKEY.Utils.encodeHtml(json.exception) + '</div>');
                        }
                    }
                }
                else if (!this.suppressRenderErrors) {
                    LABKEY.Utils.alert('Rendering Error', 'The element "' + renderEl + '" does not exist in the document. You may need to specify "renderTo".');
                }
            }, region, true),
            scope: region
        });
    };

    var _getAsyncBody = function(region, params) {
        var json = {};

        if (params.sql) {
            json.sql = params.sql;
        }

        _processButtonBar(region, json);

        // 10505: add non-removable sorts and filters to json (not url params).
        if (region.sort || region.filters || region.aggregates) {
            json.filters = {};

            if (region.filters) {
                LABKEY.Filter.appendFilterParams(json.filters, region.filters, region.name);
            }

            if (region.sort) {
                json.filters[region.dataRegionName + SORT_PREFIX] = region.sort;
            }

            if (region.aggregates) {
                LABKEY.Filter.appendAggregateParams(json.filters, region.aggregates, region.name);
            }
        }

        if (region.metadata) {
            json.metadata = region.metadata;
        }

        return json;
    };

    var _processButtonBar = function(region, json) {

        var bar = region.buttonBar;

        if (bar && (bar.position || (bar.items && bar.items.length > 0))) {
            _processButtonBarItems(region, bar.items);

            // only attach if valid
            json.buttonBar = bar;
        }
    };

    var _processButtonBarItems = function(region, items) {
        if ($.isArray(items) && items.length > 0) {
            for (var i = 0; i < items.length; i++) {
                var item = items[i];

                if (item && $.isFunction(item.handler)) {
                    item.id = item.id || LABKEY.Utils.id();
                    // TODO: A better way? This exposed _onButtonClick isn't very awesome
                    item.onClick = "return LABKEY.DataRegions['" + region.name + "']._onButtonClick('" + item.id + "');";
                }

                if (item.items) {
                    _processButtonBarItems(region, item.items);
                }
            }
        }
    };

    var _isFilter = function(region, parameter) {
        return parameter && parameter.indexOf(region.name + '.') === 0 && parameter.indexOf('~') > 0;
    };

    var _getAsyncParams = function(region, newParams) {

        var params = {};
        var name = region.name;

        //
        // Certain parameters are only included if the region is 'async'. These
        // were formerly a part of Query Web Part.
        //
        if (region.async) {
            params[name + '.async'] = true;

            if (LABKEY.Utils.isString(region.frame)) {
                params['webpart.frame'] = region.frame;
            }

            if (LABKEY.Utils.isString(region.bodyClass)) {
                params['webpart.bodyClass'] = region.bodyClass;
            }

            if (LABKEY.Utils.isString(region.title)) {
                params['webpart.title'] = region.title;
            }

            if (LABKEY.Utils.isString(region.titleHref)) {
                params['webpart.titleHref'] = region.titleHref;
            }

            if (LABKEY.Utils.isString(region.columns)) {
                params[region.name + '.columns'] = region.columns;
            }

            _applyOptionalParameters(region, params, [
                'allowChooseQuery',
                'allowChooseView',
                'allowHeaderLock',
                'buttonBarPosition',
                'detailsURL',
                'deleteURL',
                'importURL',
                'insertURL',
                'linkTarget',
                'updateURL',
                'shadeAlternatingRows',
                'showBorders',
                'showDeleteButton',
                'showDetailsColumn',
                'showExportButtons',
                'showImportDataButton',
                'showInsertNewButton',
                'showPagination',
                'showPaginationCount',
                'showReports',
                'showSurroundingBorder',
                'showUpdateColumn',
                'showViewPanel',
                'timeout',
                {name: 'disableAnalytics', prefix: true},
                {name: 'maxRows', prefix: true, check: function(v) { return v > 0; }},
                {name: 'showRows', prefix: true},
                {name: 'offset', prefix: true, check: function(v) { return v !== 0; }},
                {name: 'reportId', prefix: true},
                {name: 'viewName', prefix: true}
            ]);

            // Sorts configured by the user when interacting with the grid. We need to pass these as URL parameters.
            if (LABKEY.Utils.isString(region._userSort) && region._userSort.length > 0) {
                params[name + SORT_PREFIX] = region._userSort;
            }

            if (region.userFilters) {
                $.each(region.userFilters, function(filterExp, filterValue) {
                    if (params[filterExp] == undefined) {
                        params[filterExp] = [];
                    }
                    params[filterExp].push(filterValue);
                });
                region.userFilters = {}; // they've been applied
            }

            // TODO: Get rid of this and incorporate it with the normal containerFilter checks
            if (region.userContainerFilter) {
                params[name + CONTAINER_FILTER_NAME] = region.userContainerFilter;
            }

            if (region.parameters) {
                var paramPrefix = name + PARAM_PREFIX;
                $.each(region.parameters, function(parameter, value) {
                    var key = parameter;
                    if (parameter.indexOf(paramPrefix) !== 0) {
                        key = paramPrefix + parameter;
                    }
                    params[key] = value;
                });
            }
        }

        //
        // apply all parameters
        //

        if (newParams) {
            $.each(newParams, function(i, pair) {
                //
                // Filters may repeat themselves #25337
                //
                if (_isFilter(region, pair[0])) {
                    if (params[pair[0]] == undefined) {
                        params[pair[0]] = [];
                    }
                    else if (!$.isArray(params[pair[0]])) {
                        params[pair[0]] = [params[pair[0]]];
                    }
                    params[pair[0]].push(pair[1]);
                }
                else {
                    params[pair[0]] = pair[1];
                }
            });
        }

        //
        // Properties that cannot be modified
        //

        params.dataRegionName = region.name;
        params.schemaName = region.schemaName;
        params.viewName = region.viewName;
        params.reportId = region.reportId;
        params.returnURL = window.location.href;
        params['webpart.name'] = 'Query';

        if (region.queryName) {
            params.queryName = region.queryName;
        }
        else if (region.sql) {
            params.sql = region.sql;
        }

        var key = region.name + CONTAINER_FILTER_NAME;
        var cf = region.getContainerFilter.call(region);
        if (cf && !(key in params)) {
            params[key] = cf;
        }

        return params;
    };

    var _updateFilter = function(region, filter, skipPrefixes) {
        var params = [];
        if (filter) {
            params.push([filter.getURLParameterName(region.name), filter.getURLParameterValue()]);
        }
        _setParameters(region, params, [OFFSET_PREFIX].concat(skipPrefixes));
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
                minCount = parseInt(minCount);
            }
            if (minCount === undefined) {
                minCount = 1;
            }

            // handle max-count
            var maxCount = el.attr('labkey-requires-selection-max-count');
            if (maxCount) {
                maxCount = parseInt(maxCount);
            }

            if (minCount <= selectedCount && (!maxCount || maxCount >= selectedCount)) {
                el.removeClass('labkey-disabled-button');
            }
            else {
                el.addClass('labkey-disabled-button');
            }
        });
    };

    var HeaderLock = function(region) {

        // init
        if (!region.headerLock()) {
            region._allowHeaderLock = false;
            return;
        }

        this.region = region;

        var table = $('#' + region.domId);
        var firstRow = table.find('tr.labkey-alternate-row').first().children('td');

        // If no data rows exist just turn off header locking
        if (firstRow.length === 0) {
            firstRow = table.find('tr.labkey-row').first().children('td');
            if (firstRow.length === 0) {
                region._allowHeaderLock = false;
                return;
            }
        }

        var headerRowId = region.domId + '-column-header-row';
        var headerRow = $('#' + headerRowId);

        if (headerRow.length === 0) {
            region._allowHeaderLock = false;
            return;
        }

        var BOTTOM_OFFSET = 100;

        var me = this,
            timeout,
            locked = false,
            lastLeft = 0,
            pos = [ 0, 0, 0, 0 ];

        // init
        var floatRow = headerRow
                .clone()
                // TODO: Possibly namespace all the ids underneath
                .attr('id', headerRowId + '-float')
                .css({
                    'box-shadow': '0 4px 4px #DCDCDC',
                    display: 'none',
                    position: 'fixed',
                    top: 0,
                    'z-index': 2
                });

        floatRow.insertAfter(headerRow);

        // respect showPagination but do not use it directly as it may change
        var isPagingFloat = region.showPagination;
        var floatPaging, floatPagingWidth = 0;

        if (isPagingFloat) {
            var pageWidget = _getBarSelector(region).find('.labkey-pagination');
            if (pageWidget.children().length) {
                floatPaging = $('<div></div>')
                        .css({
                            'background-color': 'white',
                            'box-shadow': '0 4px 4px #DCDCDC',
                            display: 'none',
                            'min-width': pageWidget.width(),
                            opacity: 0.7,
                            position: 'fixed',
                            top: floatRow.height(),
                            'z-index': 1
                        })
                        .on('mouseover', function() {
                            $(this).css('opacity', '1.0');
                        })
                        .on('mouseout', function() {
                            $(this).css('opacity', '0.7')
                        });

                var floatingPageWidget = pageWidget.clone(true).css('padding', '4px 8px');

                // adjust padding when buttons aren't shown
                if (!pageWidget.find('.btn-group').length) {
                    floatingPageWidget.css('padding-bottom', '8px')
                }

                floatPaging.append(floatingPageWidget);
                table.parent().append(floatPaging);
                floatPagingWidth = floatPaging.width();
            } else {
                isPagingFloat = false;
            }
        }

        var disable = function() {
            me.region._allowHeaderLock = false;

            if (timeout) {
                clearTimeout(timeout);
            }

            $(window)
                    .unbind('load', domTask)
                    .unbind('resize', resizeTask)
                    .unbind('scroll', onScroll);
            $(document)
                    .unbind('DOMNodeInserted', domTask);
        };

        /**
         * Configures the 'pos' array containing the following values:
         * [0] - X-coordinate of the top of the object relative to the offset parent.
         * [1] - Y-coordinate of the top of the object relative to the offset parent.
         * [2] - Y-coordinate of the bottom of the object.
         * [3] - width of the object
         * This method assumes interaction with the Header of the Data Region.
         */
        var loadPosition = function() {
            var header = headerRow.offset() || {top: 0};
            var table = $('#' + region.domId);

            var bottom = header.top + table.height() - BOTTOM_OFFSET;
            var width = headerRow.width();
            pos = [ header.left, header.top, bottom, width ];
        };

        loadPosition();

        var onResize = function() {
            loadPosition();
            var sub_h = headerRow.find('th');

            floatRow.width(headerRow.width()).find('th').each(function(i, el) {
                $(el).width($(sub_h[i]).width());
            });

            isPagingFloat && floatPaging.css({
                left: pos[0] - window.pageXOffset + floatRow.width() - floatPaging.width(),
                top: floatRow.height()
            });
        };

        /**
         * WARNING: This function is called often. Performance implications for each line.
         */
        var onScroll = function() {
            if (window.pageYOffset >= pos[1] && window.pageYOffset < pos[2]) {
                var newLeft = pos[0] - window.pageXOffset;
                var newPagingLeft = isPagingFloat ? newLeft + pos[3] - floatPagingWidth : 0;

                var floatRowCSS = {
                    top: 0
                };
                var pagingCSS = isPagingFloat && {
                    top: floatRow.height()
                };

                if (!locked) {
                    locked = true;
                    floatRowCSS.display = 'table-row';
                    floatRowCSS.left = newLeft;

                    pagingCSS.display = 'block';
                    pagingCSS.left = newPagingLeft;
                }
                else if (lastLeft !== newLeft) {
                    floatRowCSS.left = newLeft;

                    pagingCSS.left = newPagingLeft;
                }

                floatRow.css(floatRowCSS);
                isPagingFloat && floatPaging.css(pagingCSS);

                lastLeft = newLeft;
            }
            else if (locked && window.pageYOffset >= pos[2]) {
                var newTop = pos[2] - window.pageYOffset;

                floatRow.css({
                    top: newTop
                });

                isPagingFloat && floatPaging.css({
                    top: newTop + floatRow.height()
                });
            }
            else if (locked) {
                locked = false;
                floatRow.hide();
                isPagingFloat && floatPaging.hide();
            }
        };

        var resizeTask = function(immediate) {
            clearTimeout(timeout);
            if (immediate) {
                onResize();
            }
            else {
                timeout = setTimeout(onResize, 110);
            }
        };

        var isDOMInit = false;

        var domTask = function() {
            if (!isDOMInit) {
                isDOMInit = true;
                // fire immediate to prevent flicker of components when reloading region
                resizeTask(true);
            }
            else {
                resizeTask();
            }
            onScroll();
        };

        $(window)
                .one('load', domTask)
                .on('resize', resizeTask)
                .on('scroll', onScroll);
        $(document)
                .on('DOMNodeInserted', domTask); // 13121

        // ensure that resize/scroll fire at the end of initialization
        domTask();

        return {
            disable: disable
        }
    };

    //
    // LOADER
    //
    LABKEY.DataRegion.create = function(config) {

        var region = LABKEY.DataRegions[config.name];

        if (region) {
            // region already exists, update properties
            $.each(config, function(key, value) {
                region[key] = value;
            });
            if (!config.view) {
                // when switching back to 'default' view, needs to clear region.view
                region.view = undefined;
            }
            _init.call(region, config);
        }
        else {
            // instantiate a new region
            region = new LABKEY.DataRegion(config);
            LABKEY.DataRegions[region.name] = region;
        }

        return region;
    };

    LABKEY.DataRegion.loadViewDesigner = function(cb, scope) {
        LABKEY.requiresExt4Sandbox(function() {
            LABKEY.requiresScript('internal/ViewDesigner', cb, scope);
        });
    };

    LABKEY.DataRegion.getCustomViewEditableErrors = function(customView) {
        var errors = [];
        if (customView && !customView.editable) {
            errors.push("The view is read-only and cannot be edited.");
        }
        return errors;
    };

    LABKEY.DataRegion.registerPane = function(regionName, callback, scope) {
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

    LABKEY.DataRegion.selectAll = function(config) {
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
    LABKEY.DataRegion.setSelected = function(config) {
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
    LABKEY.DataRegion.clearSelected = function(config) {
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
    LABKEY.DataRegion.getSelected = function(config) {
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
     * @param {String[]} [messages] - An initial messages object containing mappings of 'part' to 'msg'
     * @constructor
     */
    var MessageArea = function(dataRegion, messages) {
        this.bindRegion(dataRegion);

        if (messages) {
            this.setMessages(messages);
        }
    };

    var MsgProto = MessageArea.prototype;

    MsgProto.bindRegion = function(region) {
        this.parentSel = '#' + region.domId + '-msgbox';
    };

    MsgProto.toJSON = function() {
        return this.parts;
    };

    MsgProto.addMessage = function(msg, part, append) {
        part = part || 'info';

        var p = part.toLowerCase();
        if (append && this.parts.hasOwnProperty(p))
        {
            this.parts[p] += msg;
            this.render(p, msg);
        }
        else {
            this.parts[p] = msg;
            this.render(p);
        }
    };

    MsgProto.getMessage = function(part) {
        return this.parts[part.toLowerCase()];
    };

    MsgProto.hasMessage = function(part) {
        return this.getMessage(part) !== undefined;
    };

    MsgProto.hasContent = function() {
        return this.parts && Object.keys(this.parts).length > 0;
    };

    MsgProto.removeAll = function() {
        this.parts = {};
        this.render();
    };

    MsgProto.removeMessage = function(part) {
        var p = part.toLowerCase();
        if (this.parts.hasOwnProperty(p)) {
            this.parts[p] = undefined;
            this.render();
        }
    };

    MsgProto.setMessages = function(messages) {
        if (LABKEY.Utils.isObject(messages)) {
            this.parts = messages;
        }
        else {
            this.parts = {};
        }
    };

    MsgProto.getParent = function() {
        return $(this.parentSel);
    };

    MsgProto.render = function(partToUpdate, appendMsg) {
        var hasMsg = false,
            me = this,
            parent = this.getParent();

        $.each(this.parts, function(part, msg) {

            if (msg) {
                // If this is modified, update the server-side renderer in DataRegion.java renderMessages()
                var partEl = parent.find('div[data-msgpart="' + part + '"]');
                if (partEl.length === 0) {
                    parent.append([
                        '<div class="lk-region-bar" data-msgpart="' + part + '">',
                        msg,
                        '</div>'
                    ].join(''));
                }
                else if (partToUpdate !== undefined && partToUpdate === part) {
                    if (appendMsg !== undefined)
                        partEl.append(appendMsg);
                    else
                        partEl.html(msg)
                }

                hasMsg = true;
            }
            else {
                parent.find('div[data-msgpart="' + part + '"]').remove();
                delete me.parts[part];
            }
        });

        if (hasMsg) {
            this.show();
            $(this).trigger('rendermsg', [this, this.parts]);
        }
        else {
            this.hide();
            parent.html('');
        }
    };

    MsgProto.show = function() { this.getParent().show(); };
    MsgProto.hide = function() { this.getParent().hide(); };
    MsgProto.isVisible = function() { return $(this.parentSel + ':visible').length > 0; };
    MsgProto.find = function(selector) {
        return this.getParent().find('.dataregion_msgbox_ct').find(selector);
    };
    MsgProto.on = function(evt, callback, scope) { $(this).bind(evt, $.proxy(callback, scope)); };

    /**
     * @description Constructs a LABKEY.QueryWebPart class instance
     * @class The LABKEY.QueryWebPart simplifies the task of dynamically adding a query web part to your page.  Please use
     * this class for adding query web parts to a page instead of {@link LABKEY.WebPart},
     * which can be used for other types of web parts.
     *              <p>Additional Documentation:
     *              <ul>
     *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=webPartConfig">
     *  				        Web Part Configuration Properties</a></li>
     *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
     *                      How To Find schemaName, queryName &amp; viewName</a></li>
     *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
     *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
     *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
     *                      LabKey SQL Reference</a></li>
     *              </ul>
     *           </p>
     * @constructor
     * @param {Object} config A configuration object with the following possible properties:
     * @param {String} config.schemaName The name of the schema the web part will query.
     * @param {String} config.queryName The name of the query within the schema the web part will select and display.
     * @param {String} [config.viewName] the name of a saved view you wish to display for the given schema and query name.
     * @param {String} [config.reportId] the report id of a saved report you wish to display for the given schema and query name.
     * @param {Mixed} [config.renderTo] The element id, DOM element, or Ext element inside of which the part should be rendered. This is typically a &lt;div&gt;.
     * If not supplied in the configuration, you must call the render() method to render the part into the page.
     * @param {String} [config.errorType] A parameter to specify how query parse errors are returned. (default 'html'). Valid
     * values are either 'html' or 'json'. If 'html' is specified the error will be rendered to an HTML view, if 'json' is specified
     * the errors will be returned to the callback handlers as an array of objects named 'parseErrors' with the following properties:
     * <ul>
     *  <li><b>msg</b>: The error message.</li>
     *  <li><b>line</b>: The line number the error occurred at (optional).</li>
     *  <li><b>col</b>: The column number the error occurred at (optional).</li>
     *  <li><b>errorStr</b>: The line from the source query that caused the error (optional).</li>
     * </ul>
     * @param {String} [config.sql] A SQL query that can be used instead of an existing schema name/query name combination.
     * @param {Object} [config.metadata] Metadata that can be applied to the properties of the table fields. Currently, this option is only
     * available if the query has been specified through the config.sql option. For full documentation on
     * available properties, see <a href="https://www.labkey.org/download/schema-docs/xml-schemas/schemas/tableInfo_xsd/schema-summary.html">LabKey XML Schema Reference</a>.
     * This object may contain the following properties:
     * <ul>
     *  <li><b>type</b>: The type of metadata being specified. Currently, only 'xml' is supported.</li>
     *  <li><b>value</b>: The metadata XML value as a string. For example: <code>'&lt;tables xmlns=&quot;http://labkey.org/data/xml&quot;&gt;&lt;table tableName=&quot;Announcement&quot; tableDbType=&quot;NOT_IN_DB&quot;&gt;&lt;columns&gt;&lt;column columnName=&quot;Title&quot;&gt;&lt;columnTitle&gt;Custom Title&lt;/columnTitle&gt;&lt;/column&gt;&lt;/columns&gt;&lt;/table&gt;&lt;/tables&gt;'</code></li>
     * </ul>
     * @param {String} [config.title] A title for the web part. If not supplied, the query name will be used as the title.
     * @param {String} [config.titleHref] If supplied, the title will be rendered as a hyperlink with this value as the href attribute.
     * @param {String} [config.buttonBarPosition] DEPRECATED--see config.buttonBar.position
     * @param {boolean} [config.allowChooseQuery] If the button bar is showing, whether or not it should be include a button
     * to let the user choose a different query.
     * @param {boolean} [config.allowChooseView] If the button bar is showing, whether or not it should be include a button
     * to let the user choose a different view.
     * @param {String} [config.detailsURL] Specify or override the default details URL for the table with one of the form
     * "/controller/action.view?id=${RowId}" or "org.labkey.package.MyController$ActionAction.class?id=${RowId}"
     * @param {boolean} [config.showDetailsColumn] If the underlying table has a details URL, show a column that renders a [details] link (default true).  If true, the record selectors will be included regardless of the 'showRecordSelectors' config option.
     * @param {String} [config.updateURL] Specify or override the default updateURL for the table with one of the form
     * "/controller/action.view?id=${RowId}" or "org.labkey.package.MyController$ActionAction.class?id=${RowId}"
     * @param {boolean} [config.showUpdateColumn] If the underlying table has an update URL, show a column that renders an [edit] link (default true).
     * @param {String} [config.insertURL] Specify or override the default insert URL for the table with one of the form
     * "/controller/insertAction.view" or "org.labkey.package.MyController$InsertActionAction.class"
     * @param {String} [config.importURL] Specify or override the default bulk import URL for the table with one of the form
     * "/controller/importAction.view" or "org.labkey.package.MyController$ImportActionAction.class"
     * @param {String} [config.deleteURL] Specify or override the default delete URL for the table with one of the form
     * "/controller/action.view" or "org.labkey.package.MyController$ActionAction.class". The keys for the selected rows
     * will be included in the POST.
     * @param {boolean} [config.showImportDataButton] If the underlying table has an import URL, show an "Import Bulk Data" button in the button bar (default true).
     * @param {boolean} [config.showInsertNewButton] If the underlying table has an insert URL, show an "Insert New" button in the button bar (default true).
     * @param {boolean} [config.showDeleteButton] Show a "Delete" button in the button bar (default true).
     * @param {boolean} [config.showReports] If true, show reports on the Views menu (default true).
     * @param {boolean} [config.showExportButtons] Show the export button menu in the button bar (default true).
     * @param {boolean} [config.showBorders] Render the table with borders (default true).
     * @param {boolean} [config.showSurroundingBorder] Render the table with a surrounding border (default true).
     * @param {boolean} [config.showRecordSelectors] Render the select checkbox column (default undefined, meaning they will be shown if the query is updatable by the current user).
     *  If 'showDeleteButton' is true, the checkboxes will be  included regardless of the 'showRecordSelectors' config option.
     * @param {boolean} [config.showPagination] Show the pagination links and count (default true).
     * @param {boolean} [config.showPaginationCount] Show the total count of rows in the pagination information text (default true).
     * @param {boolean} [config.shadeAlternatingRows] Shade every other row with a light gray background color (default true).
     * @param {boolean} [config.suppressRenderErrors] If true, no alert will appear if there is a problem rendering the QueryWebpart. This is most often encountered if page configuration changes between the time when a request was made and the content loads. Defaults to false.
     * @param {Object} [config.buttonBar] Button bar configuration. This object may contain any of the following properties:
     * <ul>
     *  <li><b>position</b>: Configures where the button bar will appear with respect to the data grid: legal values are 'top', or 'none'. Default is 'top'.</li>
     *  <li><b>includeStandardButtons</b>: If true, all standard buttons not specifically mentioned in the items array will be included at the end of the button bar. Default is false.</li>
     *  <li><b>items</b>: An array of button bar items. Each item may be either a reference to a standard button, or a new button configuration.
     *                  to reference standard buttons, use one of the properties on {@link #standardButtons}, or simply include a string
     *                  that matches the button's caption. To include a new button configuration, create an object with the following properties:
     *      <ul>
     *          <li><b>text</b>: The text you want displayed on the button (aka the caption).</li>
     *          <li><b>url</b>: The URL to navigate to when the button is clicked. You may use LABKEY.ActionURL to build URLs to controller actions.
     *                          Specify this or a handler function, but not both.</li>
     *          <li><b>handler</b>: A reference to the JavaScript function you want called when the button is clicked.</li>
     *          <li><b>permission</b>: Optional. Permission that the current user must possess to see the button.
     *                          Valid options are 'READ', 'INSERT', 'UPDATE', 'DELETE', and 'ADMIN'.
     *                          Default is 'READ' if permissionClass is not specified.</li>
     *          <li><b>permissionClass</b>: Optional. If permission (see above) is not specified, the fully qualified Java class
     *                           name of the permission that the user must possess to view the button.</li>
     *          <li><b>requiresSelection</b>: A boolean value (true/false) indicating whether the button should only be enabled when
     *                          data rows are checked/selected.</li>
     *          <li><b>items</b>: To create a drop-down menu button, set this to an array of menu item configurations.
     *                          Each menu item configuration can specify any of the following properties:
     *              <ul>
     *                  <li><b>text</b>: The text of the menu item.</li>
     *                  <li><b>handler</b>: A reference to the JavaScript function you want called when the menu item is clicked.</li>
     *                  <li><b>icon</b>: A url to an image to use as the menu item's icon.</li>
     *                  <li><b>items</b>: An array of sub-menu item configurations. Used for fly-out menus.</li>
     *              </ul>
     *          </li>
     *      </ul>
     *  </li>
     * </ul>
     * @param {String} [config.columns] Comma-separated list of column names to be shown in the grid, overriding
     * whatever might be set in a custom view.
     * @param {String} [config.sort] A base sort order to use. This is a comma-separated list of column names, each of
     * which may have a - prefix to indicate a descending sort. It will be treated as the final sort, after any that the user
     * has defined in a custom view or through interacting with the grid column headers.
     * @param {String} [config.removeableSort] An additional sort order to use. This is a comma-separated list of column names, each of
     * which may have a - prefix to indicate a descending sort. It will be treated as the first sort, before any that the user
     * has defined in a custom view or through interacting with the grid column headers.
     * @param {Array} [config.filters] A base set of filters to apply. This should be an array of {@link LABKEY.Filter} objects
     * each of which is created using the {@link LABKEY.Filter.create} method. These filters cannot be removed by the user
     * interacting with the UI.
     * For compatibility with the {@link LABKEY.Query} object, you may also specify base filters using config.filterArray.
     * @param {Array} [config.removeableFilters] A set of filters to apply. This should be an array of {@link LABKEY.Filter} objects
     * each of which is created using the {@link LABKEY.Filter.create} method. These filters can be modified or removed by the user
     * interacting with the UI.
     * @param {Object} [config.parameters] Map of name (string)/value pairs for the values of parameters if the SQL
     * references underlying queries that are parameterized. For example, the following passes two parameters to the query: {'Gender': 'M', 'CD4': '400'}.
     * The parameters are written to the request URL as follows: query.param.Gender=M&query.param.CD4=400.  For details on parameterized SQL queries, see
     * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=paramsql">Parameterized SQL Queries</a>.
     * @param {Array} [config.aggregates] An array of aggregate definitions. The objects in this array should have the properties:
     * <ul>
     *     <li><b>column:</b> The name of the column to be aggregated.</li>
     *     <li><b>type:</b> The aggregate type (see {@link LABKEY.AggregateTypes})</li>
     *     <li><b>label:</b> Optional label used when rendering the aggregate row.
     * </ul>
     * @param {String} [config.showRows] Either 'paginated' (the default) 'selected', 'unselected', 'all', or 'none'.
     *        When 'paginated', the maxRows and offset parameters can be used to page through the query's result set rows.
     *        When 'selected' or 'unselected' the set of rows selected or unselected by the user in the grid view will be returned.
     *        You can programmatically get and set the selection using the {@link LABKEY.DataRegion.setSelected} APIs.
     *        Setting <code>config.maxRows</code> to -1 is the same as 'all'
     *        and setting <code>config.maxRows</code> to 0 is the same as 'none'.
     * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to 100).
     *        If you want to return all possible rows, set this config property to -1.
     * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
     *        Use this along with the maxRows config property to request pages of data.
     * @param {String} [config.dataRegionName] The name to be used for the data region. This should be unique within
     * the set of query views on the page. If not supplied, a unique name is generated for you.
     * @param {String} [config.linkTarget] The name of a browser window/tab in which to open URLs rendered in the
     * QueryWebPart. If not supplied, links will generally be opened in the same browser window/tab where the QueryWebPart.
     * @param {String} [config.frame] The frame style to use for the web part. This may be one of the following:
     * 'div', 'portal', 'none', 'dialog', 'title', 'left-nav'.
     * @param {String} [config.showViewPanel] Open the customize view panel after rendering.  The value of this option can be "true" or one of "ColumnsTab", "FilterTab", or "SortTab".
     * @param {String} [config.bodyClass] A CSS style class that will be added to the enclosing element for the web part.
     * Note, this may not be applied when used in conjunction with some "frame" types (e.g. 'none').
     * @param {Function} [config.success] A function to call after the part has been rendered. It will be passed two arguments:
     * <ul>
     * <li><b>dataRegion:</b> the LABKEY.DataRegion object representing the rendered QueryWebPart</li>
     * <li><b>request:</b> the XMLHTTPRequest that was issued to the server</li>
     * </ul>
     * @param {Function} [config.failure] A function to call if the request to retrieve the content fails. It will be passed three arguments:
     * <ul>
     * <li><b>json:</b> JSON object containing the exception.</li>
     * <li><b>response:</b> The XMLHttpRequest object containing the response data.</li>
     * <li><b>options:</b> The parameter to the request call.</li>
     * </ul>
     * @param {Object} [config.scope] An object to use as the callback function's scope. Defaults to this.
     * @param {int} [config.timeout] A timeout for the AJAX call, in milliseconds. Default is 30000 (30 seconds).
     * @param {String} [config.containerPath] The container path in which the schema and query name are defined. If not supplied, the current container path will be used.
     * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets the scope of this query. If not supplied, the current folder will be used.
     * @example
     * &lt;div id='queryTestDiv1'/&gt;
     * &lt;script type="text/javascript"&gt;
     var qwp1 = new LABKEY.QueryWebPart({

             renderTo: 'queryTestDiv1',
             title: 'My Query Web Part',
             schemaName: 'lists',
             queryName: 'People',
             buttonBarPosition: 'none',
             aggregates: [
                    {column: 'First', type: LABKEY.AggregateTypes.COUNT, label: 'Total People'},
                    {column: 'Age', type: LABKEY.AggregateTypes.MEAN}
             ],
             filters: [
                    LABKEY.Filter.create('Last', 'Flintstone')
             ],
                    sort: '-Last'
             });

             //note that you may also register for the 'render' event
             //instead of using the success config property.
             //registering for events is done using Ext event registration.
             //Example:
             qwp1.on("render", onRender);
             function onRender()
             {
                //...do something after the part has rendered...
             }

             ///////////////////////////////////////
             // Custom Button Bar Example

             var qwp1 = new LABKEY.QueryWebPart({
             renderTo: 'queryTestDiv1',
             title: 'My Query Web Part',
             schemaName: 'lists',
             queryName: 'People',
             buttonBar: {
                    includeStandardButtons: true,
                    items:[
                        LABKEY.QueryWebPart.standardButtons.views,
                        {text: 'Test', url: LABKEY.ActionURL.buildURL('project', 'begin')},
                        {text: 'Test Script', onClick: "alert('Hello World!'); return false;"},
                        {text: 'Test Handler', handler: onTestHandler},
                        {text: 'Test Menu', items: [
                        {text: 'Item 1', handler: onItem1Handler},
                        {text: 'Fly Out', items: [
                            {text: 'Sub Item 1', handler: onItem1Handler}
                            ]},
                            '-', //separator
                            {text: 'Item 2', handler: onItem2Handler}
                        ]},
                        LABKEY.QueryWebPart.standardButtons.exportRows
                    ]}
             });

             function onTestHandler(dataRegion)
             {
                 alert("onTestHandler called!");
                 return false;
             }

             function onItem1Handler(dataRegion)
             {
                 alert("onItem1Handler called!");
             }

             function onItem2Handler(dataRegion)
             {
                 alert("onItem2Handler called!");
             }

             &lt;/script&gt;
     */
    LABKEY.QueryWebPart = function(config) {
        config._useQWPDefaults = true;
        return LABKEY.DataRegion.create(config);
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
LABKEY.QueryWebPart.standardButtons = {
    query: 'query',
    views: 'grid views',
    insertNew: 'insert new',
    deleteRows: 'delete',
    exportRows: 'export',
    print: 'print',
    pageSize: 'paging'
};

/**
 * Requests the query web part content and renders it within the element identified by the renderTo parameter.
 * Note that you do not need to call this method explicitly if you specify a renderTo property on the config object
 * handed to the class constructor. If you do not specify renderTo in the config, then you must call this method
 * passing the id of the element in which you want the part rendered
 * @function
 * @param renderTo The id of the element in which you want the part rendered.
 */

LABKEY.QueryWebPart.prototype.render = LABKEY.DataRegion.prototype.render;

/**
 * @returns {LABKEY.DataRegion}
 */
LABKEY.QueryWebPart.prototype.getDataRegion = LABKEY.DataRegion.prototype.getDataRegion;

LABKEY.AggregateTypes = {
    /**
     * Displays the sum of the values in the specified column
     */
    SUM: 'sum',
    /**
     * Displays the mean of the values in the specified column
     */
    MEAN: 'mean',
    /**
     * Displays the count of the non-blank values in the specified column
     */
    COUNT: 'count',
    /**
     * Displays the maximum value from the specified column
     */
    MIN: 'min',
    /**
     * Displays the minimum values from the specified column
     */
    MAX: 'max',

    /**
     * Deprecated
     */
    AVG: 'mean'

    // TODO how to allow premium module additions to aggregate types?
};