/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2014 LabKey Corporation
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
            constructor: function (config)
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
                 *  ignoreFilter - true/false if this DataRegion was configured to ignore view filters
                 *                 (Most easily set by toggling 'Apply View Filter')
                 *  showInitialSelectMessage
                 *  selectionKey - Unique string used to associate the selected items with this DataRegion, schema, query, and view.
                 *  selectorCols
                 *  selectedCount - Count of currently selected rows. Updated as selection changes. Read-only.
                 *  requestURL
                 */
                Ext.apply(this, config, {
                    viewName: "",
                    offset: 0,
                    maxRows: 0
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
                    selectionModified: false,
                    currentPanelButton: null,
                    panelButtonContents: []
                });

                this.id = this.name;

                this._allowHeaderLock = this.allowHeaderLock;
                if (this._allowHeaderLock)
                {
                    if (this.plugins && Ext.isArray(this.plugins))
                    {
                        this.plugins.push(new LABKEY.DataRegion.plugins.HeaderLock());
                    }
                    else
                    {
                        this.plugins = [new LABKEY.DataRegion.plugins.HeaderLock()];
                    }
                }

                LABKEY.DataRegions[this.name] = this;

                this.addEvents(
                        /**
                         * @memberOf LABKEY.DataRegion#
                         * @name selectchange
                         * @event
                         * @description Fires when the selection has changed.
                         * @param {LABKEY.DataRegion} dataRegion this DataRegion object.
                         * @param {Number} selectedCount The number of selected rows in this DataRegion.
                         * @example Here's an example of subscribing to the DataRegion 'selectchange' event:
                         * Ext.ComponentMgr.onAvailable("dataRegionName", function (dataregion) {
                 *     dataregion.on('selectchange', function (dr, selectedCount) {
                 *         var btn = Ext.get('my-button-id');
                 *         if (selectedCount > 0) {
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
                        "beforeclearallparameters",
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
                        msg += "&nbsp;";
                        msg += "<span class='labkey-button unsavedview-revert' title='Revert'>Revert</span>";
                        msg += "&nbsp;";
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
            beforeDestroy: function ()
            {
                this.doDestroy();
                LABKEY.DataRegion.superclass.beforeDestroy.call(this);
            },

            doDestroy: function ()
            {
                this.disableHeaderLock();

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
            setParameters: function (params)
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
            getParameters: function (lowercase)
            {
                var results = {};

                if (this.getQWP())
                {
                    results = this.getQWP().getParameters();
                }
                else
                {
                    var paramValPairs = this.getParamValPairs(null, null);
                    var re = new RegExp('^' + Ext.escapeRe(this.name) + '\.param\.', 'i');
                    var name;
                    Ext.each(paramValPairs, function (pair)
                    {
                        if (pair[0].match(re))
                        {
                            name = pair[0].replace(re, '');
                            if (lowercase)
                                name = name.toLowerCase();
                            results[name] = pair[1];
                        }
                    }, this);
                }

                return results;
            },

            /**
             * Changes the current row offset for paged content
             * @param newoffset row index that should be at the top of the grid
             */
            setOffset: function (newoffset)
            {
                if (false === this.fireEvent("beforeoffsetchange", this, newoffset))
                    return;

                this._setParam(".offset", newoffset, [".offset", ".showRows"]);
            },

            _ensureFaceting: function ()
            {
                if (LABKEY.ActionURL.getParameter('showFacet'))
                {
                    if (!LABKEY.dataregion || !LABKEY.dataregion.panel || !LABKEY.dataregion.panel.Facet.LOADED)
                    {
                        this.showFaceting();
                    }
                }
                else
                {
                    this.loadFaceting();
                }
            },

            loadFaceting: function (cb, scope)
            {

                var dr = this;

                var initFacet = function ()
                {

                    dr.facetLoaded = true;

                    if (cb)
                    {
                        cb.call(scope);
                    }
                };

                LABKEY.requiresExt4Sandbox(true, function() {
                    LABKEY.requiresScript([
                        '/study/ReportFilterPanel.js',
                        '/study/ParticipantFilterPanel.js',
                        '/dataregion/panel/Facet.js'
                    ], true, initFacet);
                });
            },

            showFaceting: function ()
            {
                if (this.facetLoaded)
                {
                    if (!this.facet)
                    {
                        this.facet = Ext4.create('LABKEY.dataregion.panel.Facet', {
                            dataRegion: this
                        })
                    }
                    this.facet.toggleCollapse();
                    if (this.resizeTask)
                    {
                        this.resizeTask.delay(350);
                    }
                }
                else
                {
                    this.loadFaceting(this.showFaceting, this);
                }
            },

            setFacet: function (facet)
            {
                this.facet = facet;
                this.facetLoaded = true;
            },

            /**
             * Changes the maximum number of rows that the grid will display at one time
             * @param newmax the maximum number of rows to be shown
             */
            setMaxRows: function (newmax)
            {
                if (false === this.fireEvent("beforemaxrowschange", this, newmax))
                    return;

                this._setParam(".maxRows", newmax, [".offset", ".maxRows", ".showRows"]);
            },

            /**
             * Refreshes the grid, via AJAX if loaded through a QueryWebPart, and via a page reload otherwise.
             */
            refresh: function ()
            {
                if (false === this.fireEvent("beforerefresh", this))
                    return;

                window.location.reload(false);
            },

            /**
             * Forces the grid to do paging based on the current maximum number of rows
             */
            showPaged: function ()
            {
                if (false === this.fireEvent("beforeshowrowschange", this, null))
                    return;

                this._removeParams([".showRows"]);
            },

            /**
             * Looks for a column based on fieldKey, name, or caption (in that order)
             */
            getColumn: function (columnIdentifier)
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
            showAll: function ()
            {
                if (false === this.fireEvent("beforeshowrowschange", this, "all"))
                    return;

                this._setParam(".showRows", "all", [".offset", ".maxRows", ".showRows"]);
            },

            /**
             * Forces the grid to show only rows that have been selected
             */
            showSelected: function ()
            {
                if (false === this.fireEvent("beforeshowrowschange", this, "selected"))
                    return;

                this._setParam(".showRows", "selected", [".offset", ".maxRows", ".showRows"]);
            },

            /**
             * Forces the grid to show only rows that have not been selected
             */
            showUnselected: function ()
            {
                if (false === this.fireEvent("beforeshowrowschange", this, "unselected"))
                    return;

                this._setParam(".showRows", "unselected", [".offset", ".maxRows", ".showRows"]);
            },

            /** Displays the first page of the grid */
            pageFirst: function ()
            {
                this.setOffset(0);
            },

            selectRow: function (el)
            {
                this.setSelected({ids: [el.value], checked: el.checked});
                var toggle = this.form[".toggle"];
                if (el.checked)
                {
                    if (toggle && this.isPageSelected())
                        toggle.checked = true;
                }
                else
                {
                    if (toggle)
                        toggle.checked = false;
                    this.removeMessage('selection');
                }
            },

            /**
             * Get selected items on the current page of the DataRegion.  Selected items may exist on other pages.
             * @see LABKEY.DataRegion#getSelected
             */
            getChecked: function ()
            {
                var elementName = '.select';
                var elems = this.form.elements;
                var l = elems.length;
                var values = [];
                for (var i = 0; i < l; i++)
                {
                    var e = elems[i];
                    if (e.type == 'checkbox' && !e.disabled && (elementName == null || elementName == e.name) && e.checked)
                        values.push(e.value);
                }
                return values;
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
            getSelected: function (config)
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
            setSelected: function (config)
            {
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

                function failureCb(response, options)
                {
                    this.addMessage("Error sending selection.");
                }

                // Update the current selectedCount on this DataRegion and fire the 'selectchange' event
                var self = this;
                function updateSelected(data, response, options) {
                    this.selectionModified = true;
                    self.selectedCount = data.count;
                    self.onSelectChange();
                }

                // Chain updateSelected with the user-provided success callback
                var success = LABKEY.Utils.getOnSuccess(config);
                if (success) {
                    success = updateSelected.createSequence(success, config.scope);
                } else {
                    success = updateSelected;
                }
                config.success = success;

                config.failure = LABKEY.Utils.getOnFailure(config) || failureCb;

                if (this.selectionKey)
                {
                    LABKEY.DataRegion.setSelected(config);
                }
                else
                {
                    // Don't send the selection change to the server if there is no selectionKey.
                    // Call the success callback directly.
                    var count = this.getSelectionCount();
                    config.success.call(config.scope, {count: count});
                }
            },

            /**
             * Set the selection state for all checkboxes on the current page of the DataRegion.
             * @param checked whether all of the rows on the current page should be selected or unselected
             * @returns {Array} Array of ids that were selected or unselected.
             *
             * @see LABKEY.DataRegion#setSelected to set selected items on the current page of the DataRegion.
             * @see LABKEY.DataRegion#clearSelected to clear all selected.
             */
            selectPage: function (checked)
            {
                var ids = this._setAllCheckboxes(checked, '.select');
                if (ids.length > 0)
                {
                    var toggle = this.form[".toggle"];
                    if (toggle)
                        toggle.checked = checked;
                    this.setSelected({ids: ids, checked: checked, success: function (data, response, options)
                    {
                        if (data && data.count > 0)
                        {
                            var count = data.count;
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
            hasSelected: function ()
            {
                return this.getSelectionCount() > 0;
            },

            /**
             * Returns the number of selected rows on the current page of the DataRegion. Selected items may exist on other pages.
             * @returns {Integer} the number of selected rows on the current page of the DataRegion.
             * @see LABKEY.DataRegion#getSelected to get all selected rows.
             */
            getSelectionCount: function ()
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
            isPageSelected: function ()
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

            selectNone: function (config)
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
            clearSelected: function (config)
            {
                config = config || {};
                config.selectionKey = this.selectionKey;
                config.scope = config.scope || this;

                this.selectedCount = 0;
                this.onSelectChange();

                if (this.selectionKey) {
                    LABKEY.DataRegion.clearSelected(config);
                }

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
            changeSort: function (fieldKey, sortDirection)
            {
                if (!fieldKey)
                    return;

                if (!(fieldKey instanceof LABKEY.FieldKey))
                    fieldKey = LABKEY.FieldKey.fromString("" + fieldKey);

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
            clearSort: function (fieldKey)
            {
                if (!fieldKey)
                    return;

                if (!(fieldKey instanceof LABKEY.FieldKey))
                    fieldKey = LABKEY.FieldKey.fromString("" + fieldKey);

                var columnName = fieldKey.toString();
                if (false === this.fireEvent("beforeclearsort", this, columnName))
                    return;

                var newSortString = this.alterSortString(this.getParameter(this.name + ".sort"), fieldKey, null);
                if (newSortString.length > 0)
                    this._setParam(".sort", newSortString, [".sort", ".offset"]);
                else
                    this._removeParams([".sort", ".offset"]);
            },

            /**
             * Add a filter to this Data Region.
             * @param {LABKEY.Filter} filter
             */
            addFilter: function (filter)
            {
                this._updateFilter(filter);
            },

            /**
             * Replace a filter on this Data Region. Optionally, supply another filter to replace for cases when the filter
             * columns don't match exactly.
             * @param {LABKEY.Filter} filter
             * @param {LABKEY.Filter} [filterToReplace]
             */
            replaceFilter: function (filter, filterToReplace)
            {
                var target = filterToReplace ? filterToReplace : filter;
                this._updateFilter(filter, [this.name + '.' + target.getColumnName() + '~']);
            },

            replaceFilters: function (filters, column)
            {
                if (filters.length == 0)
                    return;
                else if (filters.length == 1)
                    return this.replaceFilter(filters[0]);
                else
                {
                    // use the first filter to skip prefixes
                    var target = filters[0];

                    var params = LABKEY.DataRegion.getParamValPairsFromString(this.requestURL, [this.name + '.' + column.fieldKey + '~']);
                    params.push([target.getURLParameterName(this.name), target.getURLParameterValue()]);

                    // stop skipping prefixes and add the rest of the params
                    for (var f = 1; f < filters.length; f++)
                    {
                        target = filters[f];
                        params.push([target.getURLParameterName(this.name), target.getURLParameterValue()]);
                    }

                    this.changeFilter(params, LABKEY.DataRegion.buildQueryString(params));
                }
            },

            /**
             * Remove a filter on this DataRegion.
             * @param {LABKEY.Filter} filter
             */
            removeFilter: function (filter)
            {
                if (filter && filter.getColumnName)
                {
                    this._updateFilter(null, [this.name + '.' + filter.getColumnName() + '~']);
                }
            },

            replaceFilterMatch: function (filter, filterMatch)
            {
                var params = LABKEY.DataRegion.getParamValPairsFromString(this.requestURL);
                var skips = [];

                for (var f = 0; f < params.length; f++)
                {
                    if (params[f][0].indexOf(this.name + '.') == 0)
                    {
                        if (params[f][0].indexOf(filterMatch) > -1)
                        {
                            skips.push(params[f][0]);
                        }
                    }
                }

                this._updateFilter(filter, skips);
            },

            // private
            // DO NOT CALL DIRECTLY. This method is private and only available for replacing advanced cohort filters
            // for this Data Region. Remove if advanced cohorts are removed.
            replaceAdvCohortFilter: function (filter)
            {
                var params = LABKEY.DataRegion.getParamValPairsFromString(this.requestURL);
                var skips = [];

                // build set of cohort filters to skip
                for (var i = 0; i < params.length; i++)
                {
                    if (params[i][0].indexOf(this.name + '.') == 0)
                    {
                        if (params[i][0].indexOf('/Cohort/Label') > -1 || params[i][0].indexOf('/InitialCohort/Label') > -1)
                        {
                            skips.push(params[i][0]);
                        }
                    }
                }
                this._updateFilter(filter, skips);
            },

            // private
            // DO NOT CALL DIRECTLY. Use addFilter, replaceFilter
            _updateFilter: function (filter, skipPrefixes)
            {
                var params = LABKEY.DataRegion.getParamValPairsFromString(this.requestURL, skipPrefixes);
                if (filter)
                {
                    params.push([filter.getURLParameterName(this.name), filter.getURLParameterValue()]);
                }
                this.changeFilter(params, LABKEY.DataRegion.buildQueryString(params));
            },

            // private
            changeFilter: function (newParamValPairs, newQueryString)
            {
                if (this.hasListener("beforefilterchange"))
                {
                    var filterPairs = [], paramName, paramVal, i;

                    // Issue 18303, 18448: Only include filter parameters (ignore .sort and others)
                    for (i=0; i < newParamValPairs.length; i++)
                    {
                        paramName = newParamValPairs[i][0];
                        paramVal = newParamValPairs[i][1];
                        if (paramName.indexOf(this.name + ".") == 0 && paramName.indexOf("~") > -1)
                            filterPairs.push([paramName, paramVal]);
                    }

                    if (false === this.fireEvent("beforefilterchange", this, filterPairs))
                        return;
                }

                // when filters change, remove offsets
                var params = LABKEY.DataRegion.getParamValPairsFromString(newQueryString, [this.name + '.offset']);

                this.setSearchString(this.name, LABKEY.DataRegion.buildQueryString(params));
            },

            /**
             * Removes all the filters for a particular field
             * @param {string or FieldKey} fieldKey the name of the field from which all filters should be removed
             */
            clearFilter: function (fieldKey)
            {
                if (!fieldKey)
                    return;

                if (!(fieldKey instanceof LABKEY.FieldKey))
                    fieldKey = LABKEY.FieldKey.fromString("" + fieldKey);

                var columnName = fieldKey.toString();
                if (false === this.fireEvent("beforeclearfilter", this, columnName))
                    return;
                this._removeParams(["." + columnName + "~", ".offset"]);
            },

            /** Removes all filters from the DataRegion */
            clearAllFilters: function ()
            {
                if (false === this.fireEvent("beforeclearallfilters", this))
                    return;
                this._removeParams([LABKEY.DataRegion.ALL_FILTERS_SKIP_PREFIX, ".offset"]);
            },

            /** Removes all parameters from the DataRegion */
            clearAllParameters: function ()
            {
                if (false === this.fireEvent("beforeclearallparameters", this))
                    return;
                this._removeParams([".param.", ".offset"]);
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
            getUserFilter: function ()
            {
                var userFilter = [];
                var filters = this.getUserFilterArray();

                for (var i = 0; i < filters.length; i++)
                {
                    var filter = filters[i];
                    userFilter.push({
                        fieldKey: filter.getColumnName(),
                        op: filter.getFilterType().getURLSuffix(),
                        value: filter.getValue()
                    });
                }
                return userFilter;
            },

            getUserFiltersByColumn: function (column)
            {
                var filters = this.getUserFilterArray();
                var ret = [];

                for (var i = 0; i < filters.length; i++)
                {
                    if (column.lookup && column.displayField && filters[i].getColumnName() == column.displayField)
                    {
                        ret.push(filters[i]);
                    }
                    else if (column.fieldKey && filters[i].getColumnName() == column.fieldKey)
                        ret.push(filters[i]);
                }

                return ret;
            },

            /**
             * Returns an Array of LABKEY.Filter instances constructed from the URL.
             * @returns {Array} Array of {@link LABKEY.Filter} objects that represent currently applied filters.
             */
            getUserFilterArray: function ()
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
            getUserContainerFilter: function ()
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
            getUserSort: function ()
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
            addMessage: function (htmlOrConfig, part)
            {
                if (this.msgbox)
                {
                    if (typeof htmlOrConfig === "string")
                    {
                        this.msgbox.addMessage(htmlOrConfig, part);
                    }

                    if (typeof htmlOrConfig === "object")
                    {
                        this.msgbox.addMessage(htmlOrConfig.html, htmlOrConfig.part || part);

                        if (htmlOrConfig.hideButtonPanel)
                            this.hideButtonPanel();

                        if (htmlOrConfig.duration)
                        {
                            var dr = this;
                            setTimeout(function(){dr.removeMessage(htmlOrConfig.part || part); dr.header.fireEvent('resize');}, htmlOrConfig.duration);
                        }
                    }
                }
            },

            /**
             * Show a message in the header of this DataRegion.
             * @param html the HTML source of the message to be shown
             * @return {Ext.Element} The Ext.Element of the newly created message div.
             * @deprecated use addMessage(html, msg) instead.
             */
            showMessage: function (html)
            {
                if (this.msgbox)
                    this.msgbox.addMessage(html);
            },

            showMessageArea: function ()
            {
                if (this.msgbox)
                    this.msgbox.render();
            },

            /**
             * Show a message in the header of this DataRegion with a loading indicator.
             * @param html the HTML source of the message to be shown
             */
            showLoadingMessage: function (html)
            {
                html = html || "Loading...";
                this.addMessage("<div><span class='loading-indicator'>&nbsp;</span><em>" + html + "</em></div>");
            },

            /**
             * Show a success message in the header of this DataRegion.
             * @param html the HTML source of the message to be shown
             */
            showSuccessMessage: function (html)
            {
                html = html || "Completed successfully.";
                this.addMessage("<div class='labkey-message'>" + html + "</div>");
            },

            /**
             * Show an error message in the header of this DataRegion.
             * @param html the HTML source of the message to be shown
             */
            showErrorMessage: function (html)
            {
                html = html || "An error occurred.";
                this.addMessage("<div class='labkey-error'>" + html + "</div>");
            },

            /**
             * Returns true if a message is currently being shown for this DataRegion. Messages are shown as a header.
             * @return {Boolean} true if a message is showing.
             */
            isMessageShowing: function ()
            {
                return this.msgbox && this.msgbox.getEl() && this.msgbox.isVisible();
            },

            /** If a message is currently showing, hide it and clear out its contents */
            hideMessage: function ()
            {
                if (this.msgbox)
                {
                    this.msgbox.setVisible(false);
                    this.msgbox.clear();
                    if (this.headerLock() && this.plugins)
                    {
                        for (var p = 0; p < this.plugins.length; p++)
                        {
                            if (Ext.isFunction(this.plugins[p]['onResize']))
                                this.plugins[p].onResize(); // 13498
                        }
                    }
                }
            },

            /** If a message is currently showing, remove the specified part*/
            removeMessage: function (part)
            {
                if (this.msgbox)
                {
                    this.msgbox.removeMessage(part);
                }
            },

            /** Clear the message box contents. */
            clearMessage: function ()
            {
                if (this.msgbox) this.msgbox.clear();
            },

            /**
             * Get the message area if it exists.
             * @return {LABKEY.DataRegion.MessageArea} The message area object.
             */
            getMessageArea: function ()
            {
                return this.msgbox;
            },

            /**
             * @private
             * @param currentSortString
             * @param fieldKey FieldKey or FieldKey encoded string.
             * @param direction
             */
            alterSortString: function (currentSortString, fieldKey, direction)
            {
                if (!(fieldKey instanceof LABKEY.FieldKey))
                    fieldKey = LABKEY.FieldKey.fromString("" + fieldKey);

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
            changeView: function (view, urlParameters)
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
                    skipPrefixes.push(LABKEY.DataRegion.ALL_FILTERS_SKIP_PREFIX);
                    skipPrefixes.push(".sort");
                    skipPrefixes.push(".columns");
                    skipPrefixes.push(".containerFilterName");
                }


                this._setParams(newParamValPairs, skipPrefixes);
            },

            // private
            _initElements: function ()
            {
                this.form = document.forms[this.name];
                this.table = Ext.get("dataregion_" + this.name);
                var msgEl = Ext.get("dataregion_msgbox_" + this.name);
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
                    }
                    while (el != null && el.tagName != "FORM");
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
                        }
                        this.onSelectChange();
                    }
                    else
                    {
                        this.updateRequiresSelectionButtons(false);
                    }
                }

                this.rendered = true; // prevent Ext.Component.render() from doing anything
                this.el = this.form && Ext.get(this.form) || this.table;
            },

            headerLock: function ()
            {
                return this._allowHeaderLock === true;
            },

            disableHeaderLock: function ()
            {
                if (this.headerLock() && Ext.isArray(this.plugins))
                {
                    for (var p = 0; p < this.plugins.length; p++)
                    {
                        if (Ext.isFunction(this.plugins[p]['disable']))
                        {
                            this.plugins[p].disable();
                        }
                    }
                }
            },

            // private
            _showPagination: function (el)
            {
                if (!el) return;
                var pagination = el.child("div[class='labkey-pagination']", true);
                if (pagination)
                    pagination.style.visibility = "visible";
            },

            // private
            _removeParams: function (skipPrefixes)
            {
                this._setParams(null, skipPrefixes);
            },

            _setParam: function (param, value, skipPrefixes)
            {
                this._setParams([
                    [param, value]
                ], skipPrefixes);
            },

            // private
            _setParams: function (newParamValPairs, skipPrefixes)
            {
                var i, param, value;

                for (i=0; i < skipPrefixes.length; i++)
                    skipPrefixes[i] = this.name + skipPrefixes[i];

                var paramValPairs = this.getParamValPairs(this.requestURL, skipPrefixes);
                if (newParamValPairs)
                {
                    for (i = 0; i < newParamValPairs.length; i++)
                    {
                        param = newParamValPairs[i][0];
                        value = newParamValPairs[i][1];

                        // Allow value to be null/undefined to support no-value filter types (Is Blank, etc)
                        if (null != param)
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
            _setAllCheckboxes: function (value, elementName)
            {
                var ids = [];

                if (this.table)
                {
                    var checkboxes = Ext.query('input[@type="checkbox"]', this.table.dom);
                    var len = checkboxes ? checkboxes.length : 0;
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
                }
                return ids;
            },

            // private
            _showSelectMessage: function (msg)
            {
                if (this.showRecordSelectors)
                {
                    msg += "&nbsp;<span class='labkey-button select-none'>Select None</span>";
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
                this.addMessage(msg, 'selection');
            },

            // private
            /**
             * render listener for the message area, to add handlers for the link targets.
             */
            _onRenderMessageArea: function (cmp, partName, el)
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
                        showCustomizeViewEl.on('click', function ()
                        {
                            this.showCustomizeView(undefined, true, true);
                        }, this);

                    var saveEl = el.child(".labkey-button.unsavedview-save");
                    if (saveEl)
                        saveEl.on('click', this.saveSessionCustomView, this);
                }
            },

            // private
            updateRequiresSelectionButtons: function (selectionCount)
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
                        Ext.get(buttonElement).replaceClass('labkey-disabled-button', 'labkey-button');
                    }
                    else
                    {
                        Ext.get(buttonElement).replaceClass('labkey-button', 'labkey-disabled-button');
                    }
                }
            },

            setQWP: function (qwp)
            {
                this.qwp = qwp;
            },

            getQWP: function ()
            {
                return this.qwp;
            },

            // private
            onSelectChange: function ()
            {
                this.fireEvent('selectchange', this, this.selectedCount);
                this.updateRequiresSelectionButtons(this.selectedCount);
            },

            onButtonClick: function (buttonId)
            {
                return this.fireEvent("buttonclick", buttonId, this);
            },

            /**
             * Show a ribbon panel. tabPanelConfig is an Ext config object for a TabPanel, the only required
             * value is the items array.
             */
            showButtonPanel: function (panelButton, tabPanelConfig)
            {
                this._showButtonPanel(this.header, panelButton.getAttribute("panelId"), true, tabPanelConfig, panelButton);
            },

            _showButtonPanel: function (headerOrFooter, panelId, animate, tabPanelConfig, button)
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
                    var callback = function ()
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

                                // New up the TabPanel if we haven't already
                                // Only create one per button, even if that button is rendered both above and below the grid
                                tabPanelConfig.cls = 'vertical-tabs';
                                tabPanelConfig.tabWidth = 80;
                                tabPanelConfig.renderTo = panelDiv;
                                tabPanelConfig.activeGroup = 0;
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

                            if (this.headerLock())
                            {
                                y = this.colHeaderRow.getY();
                                h = this.headerSpacer.getHeight();
                            }

                            if (animate)
                            {
                                panelToShow.getEl().slideIn('t', {
                                    callback: function ()
                                    {
                                        this.fireEvent('afterpanelshow');
                                    },
                                    concurrent: true,
                                    duration: _duration,
                                    scope: this
                                });
                            }
                            else
                            {
                                panelToShow.getEl().setVisible(true);
                                this.fireEvent('afterpanelshow');
                            }

                            if (this.headerLock())
                            {
                                this.headerSpacer.setHeight(h + panelToShow.getHeight());
                                this.colHeaderRow.shift({y: (y + panelToShow.getHeight()), duration: _duration, concurrent: true, scope: this});
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
                        if (this.headerLock())
                        {
                            y = this.colHeaderRow.getY();
                            h = this.headerSpacer.getHeight();
                        }

                        if (animate)
                        {
                            panelToHide.getEl().slideOut('t', {
                                callback: function ()
                                {
                                    this.fireEvent('afterpanelhide');
                                    callback.call(this);
                                },
                                concurrent: true,
                                duration: _duration,
                                scope: this
                            });
                        }
                        else
                        {
                            panelToHide.getEl().setVisible(false);
                            this.fireEvent('afterpanelhide');
                            callback.call(this);
                        }

                        if (this.headerLock())
                        {
                            this.headerSpacer.setHeight(h - panelToHide.getHeight());
                            this.colHeaderRow.shift({y: (y - panelToHide.getHeight()), duration: _duration, concurrent: true, scope: this});
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
             * Hide the ribbon panel. If visible the ribbon panel will be hidden.
             */
            hideButtonPanel: function()
            {
                this._hideButtonPanel(this.header, true);
            },

            _hideButtonPanel: function(headerOrFooter, animate)
            {
                var _duration = 0.4, y, h, panelDiv = headerOrFooter.child(".labkey-ribbon");

                if (this.currentPanelId)
                {
                    var panelToHide =  this.panelButtonContents[this.currentPanelId];

                    if (this.headerLock())
                    {
                        y = this.colHeaderRow.getY();
                        h = this.headerSpacer.getHeight();
                    }

                    var callback = function()
                    {
                        panelToHide.setVisible(false);
                        this.fireEvent('afterpanelhide');
                        this.currentPanelId = null;
                        // Close the panelDiv since we're not adding a new panel.
                        panelDiv.setDisplayed(true);
                        // Remove highlight from the button that triggered the menu. The button in question *should* be the
                        // only button below the dataregion that has the labkey-menu-button-active class on it.
                        var button = Ext.query('#' + this.getId() + ' .labkey-menu-button-active')[0];
                        if (button)
                        {
                            Ext.get(button).removeClass('labkey-menu-button-active');
                        }
                    };

                    if (animate)
                    {
                        panelToHide.getEl().slideOut('t', {
                            callback: callback,
                            concurrent: true,
                            duration: _duration,
                            scope: this
                        });
                    }
                    else
                    {
                        callback.call(this);
                    }

                    if (this.headerLock())
                    {
                        this.headerSpacer.setHeight(h - panelToHide.getHeight());
                        this.colHeaderRow.shift({y: (y - panelToHide.getHeight()), duration: _duration, concurrent: true, scope: this});
                    }
                }
            },

            /**
             * Show the customize view interface.
             * @param activeTab {[String]} Optional. One of "ColumnsTab", "FilterTab", or "SortTab".  If no value is specified (or undefined), the ColumnsTab will be shown.
             * @param hideMessage {[boolean]} Optional. True to hide the DataRegion message bar when showing.
             * @param animate {[boolean]} Optional. True to slide in the ribbon panel.
             */
            showCustomizeView: function (activeTab, hideMessage, animate)
            {
                if (hideMessage)
                    this.hideMessage();

                // UNDONE: when both header and footer are rendered, need to show the panel in the correct button bar
                var headerOrFooter = this.header || this.footer;

                if (!this.customizeView)
                {
                    var timerId = function ()
                    {
                        timerId = 0;
                        this.showLoadingMessage("Opening custom view designer...");
                    }.defer(500, this);

                    LABKEY.initializeViewDesigner(function ()
                    {
                        var additionalFields = {};
                        var userFilter = this.getUserFilter();
                        var userSort = this.getUserSort();
                        var userColumns = this.getParameter(this.name + ".columns");

                        for (var i = 0; i < userFilter.length; i++)
                            additionalFields[userFilter[i].fieldKey] = true;

                        for (i = 0; i < userSort.length; i++)
                            additionalFields[userSort[i].fieldKey] = true;

                        var fields = [];
                        for (var fieldKey in additionalFields)
                            fields.push(fieldKey);

                        var viewName = (this.view && this.view.name) || this.viewName || "";
                        LABKEY.Query.getQueryDetails({
                            containerPath : this.containerPath,
                            schemaName: this.schemaName,
                            queryName: this.queryName,
                            viewName: viewName,
                            fields: fields,
                            initializeMissingView: true,
                            success: function (json, response, options)
                            {
                                if (timerId > 0)
                                    clearTimeout(timerId);
                                else
                                    this.hideMessage();

                                // If there was an error parsing the query, we won't be able to render the customize view panel.
                                if (json.exception)
                                {
                                    var viewSourceUrl = LABKEY.ActionURL.buildURL('query', 'viewQuerySource.view', this.containerPath, {schemaName: this.schemaName, "query.queryName": this.queryName});
                                    var msg = Ext.util.Format.htmlEncode(json.exception) +
                                            " &nbsp;<a target=_blank class='labkey-button' href='" + viewSourceUrl + "'>View Source</a>";

                                    this.showErrorMessage(msg);
                                    return;
                                }

                                var minWidth = Math.max(Math.min(1000, headerOrFooter.getWidth(true)), 700); // >= 700 && <= 1000
                                var renderTo = Ext.getBody().createChild({tag: "div", customizeView: true, style: {display: "none"}});

                                this.customizeView = new LABKEY.DataRegion.ViewDesigner({
                                    renderTo: renderTo,
                                    width: minWidth,
                                    activeGroup: activeTab,
                                    dataRegion: this,
                                    containerPath : this.containerPath,
                                    schemaName: this.schemaName,
                                    queryName: this.queryName,
                                    viewName: viewName,
                                    query: json,
                                    userFilter: userFilter,
                                    userSort: userSort,
                                    userColumns: userColumns,
                                    userContainerFilter: this.getUserContainerFilter(),
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
            hideCustomizeView: function (animate)
            {
                if (this.customizeView && this.customizeView.getEl() && this.customizeView.getEl().dom && this.customizeView.isVisible())
                {
                    this._showButtonPanel(this.header || this.footer, "~~customizeView~~", animate, null);
                }
            },

            // private
            toggleShowCustomizeView: function ()
            {
                if (this.customizeView && this.customizeView.getEl() && this.customizeView.getEl().dom && this.customizeView.isVisible())
                    this.hideCustomizeView(true);
                else
                    this.showCustomizeView(undefined, null, true);
            },

            // private
            deleteCustomView: function ()
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
                Ext.Msg.confirm(title, msg, function (btnId)
                {
                    if (btnId == "yes")
                    {
                        this._deleteCustomView(true, "Deleting view...");
                    }
                }, this);
            },

            // private
            revertCustomView: function ()
            {
                this._deleteCustomView(false, "Reverting view...");
            },

            // private
            _deleteCustomView: function (complete, message)
            {
                var timerId = function ()
                {
                    timerId = 0;
                    this.showLoadingMessage(message);
                }.defer(500, this);

                Ext.Ajax.request({
                    url: LABKEY.ActionURL.buildURL("query", "deleteView", this.containerPath),
                    jsonData: {schemaName: this.schemaName, queryName: this.queryName, viewName: this.viewName, complete: complete},
                    method: "POST",
                    scope: this,
                    success: LABKEY.Utils.getCallbackWrapper(function (json, response, options)
                    {
                        if (timerId > 0)
                            clearTimeout(timerId);
                        this.showSuccessMessage();
                        // change view to either a shadowed view or the default view
                        var viewName = json.viewName;
                        this.changeView({type: 'view', viewName: viewName});
                    }, this),
                    failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options)
                    {
                        if (timerId > 0)
                            clearTimeout(timerId);
                        this.showErrorMessage(json.exception);
                    }, this, true)
                });
            },

            // private
            saveSessionCustomView: function ()
            {
                // Note: currently only will save session views. Future version could create a new view using url sort/filters.
                if (!(this.view && this.view.session))
                    return;

                var self = this;

                function showPrompt(queryDetails)
                {
                    var config = Ext.applyIf({
                        allowableContainerFilters: self.allowableContainerFilters,
                        targetContainers: queryDetails.targetContainers,
                        canEditSharedViews: queryDetails.canEditSharedViews,
                        canEdit: LABKEY.DataRegion._getCustomViewEditableErrors(config).length == 0,
                        success: function (win, o)
                        {
                            var timerId = function ()
                            {
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

                            if (o.inherit)
                                jsonData.containerPath = o.containerPath;

                            Ext.Ajax.request({
                                url: LABKEY.ActionURL.buildURL("query", "saveSessionView", self.containerPath),
                                method: "POST",
                                jsonData: jsonData,
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                scope: self,
                                success: LABKEY.Utils.getCallbackWrapper(function (json, response, options)
                                {
                                    if (timerId > 0)
                                        clearTimeout(timerId);
                                    win.close();
                                    Ext.Msg.hide();
                                    self.showSuccessMessage();
                                    self.changeView({type: 'view', viewName: o.name});
                                }, self),
                                failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options)
                                {
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

                // Get the canEditSharedViews permission and candidate targetContainers.
                var viewName = (this.view && this.view.name) || this.viewName || "";
                LABKEY.Query.getQueryDetails({
                    schemaName: this.schemaName,
                    queryName: this.queryName,
                    viewName: viewName,
                    initializeMissingView: false,
                    success: function (json, response, options)
                    {
                        // Display an error if there was an issue error getting the query details
                        if (json.exception)
                        {
                            var viewSourceUrl = LABKEY.ActionURL.buildURL('query', 'viewQuerySource.view', null, {schemaName: this.schemaName, "query.queryName": this.queryName});
                            var msg = Ext.util.Format.htmlEncode(json.exception) +
                                    " &nbsp;<a target=_blank class='labkey-button' href='" + viewSourceUrl + "'>View Source</a>";

                            this.showErrorMessage(msg);
                            return;
                        }

                        showPrompt(json);
                    },
                    scope: this
                });
            },

            onViewSave: function (designer, savedViewsInfo, urlParameters)
            {
                if (savedViewsInfo && savedViewsInfo.views.length > 0)
                {
                    this.hideCustomizeView(false);
                    this.changeView({
                        type: 'view',
                        viewName: savedViewsInfo.views[0].name}, urlParameters);
                }
            },

            getParamValPairs: function (queryString, skipPrefixes)
            {
                if (!queryString)
                {
                    queryString = this.getSearchString();
                }
                return LABKEY.DataRegion.getParamValPairsFromString(queryString, skipPrefixes);
            },

            getParameter: function (paramName)
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

            getSearchString: function ()
            {
                if (null == this.savedSearchString)
                    this.savedSearchString = document.location.search.substring(1) || "";
                return this.savedSearchString;
            },

            setSearchString: function (tableName, search)
            {
                this.savedSearchString = search || "";
                // If the search string doesn't change and there is a hash on the url, the page won't reload.
                // Remove the hash by setting the full path plus search string.
                window.location.assign(window.location.pathname + "?" + this.savedSearchString);
            }

        });

LABKEY.DataRegion.plugins = {};
LABKEY.DataRegion.plugins.HeaderLock = (function ()
{

    var ensurePaginationVisible = function (dr)
    {

        if (dr.paginationEl)
        {
            // in case header locking is not on
            if (!dr.headerLock() || !dr.hdrCoord || dr.hdrCoord.length == 0)
            {
                dr.hdrCoord = findPos(dr);
            }

            var measure = Ext.getBody().getBox().width - dr.hdrCoord[0];
            if (measure < (dr.headerRow.getWidth()))
            {
                dr.paginationEl.applyStyles('width: ' + measure + 'px;');
            }
        }
    };

    /**
     * Returns an array of containing the following values:
     * [0] - X-coordinate of the top of the object relative to the offset parent. See Ext.Element.getXY()
     * [1] - Y-coordinate of the top of the object relative to the offset parent. See Ext.Element.getXY()
     * [2] - Y-coordinate of the bottom of the object.
     * [3] - The height of the header for this Data Region. This includes the button bar if it is present.
     * This method assumes interaction with the Header of the Data Region.
     */
    var findPos = function (dr)
    {
        var o, xy, curbottom, hdrOffset = 0;

        if (dr.includeHeader)
        {
            o = (dr.hdrLocked ? dr.headerSpacer : dr.headerRow);
            hdrOffset = dr.headerSpacer.getComputedHeight();
        }
        else
        {
            o = (dr.hdrLocked ? dr.colHeaderRowSpacer : dr.colHeaderRow);
        }

        xy = o.getXY();
        curbottom = xy[1] + dr.table.getHeight() - (o.getComputedHeight() * 2);

        return [ xy[0], xy[1], curbottom, hdrOffset ];
    };

    var validBrowser = function ()
    {
        return (Ext.isIE9 || Ext.isIE10p || Ext.isWebKit || Ext.isGecko);
    };

    return {
        init: function (dr)
        {
            if (!dr.headerLock() || !validBrowser())
            {
                dr._allowHeaderLock = false;
                return;
            }

            this.dr = dr;

            // initialize constants
            dr.headerRow = Ext.get('dataregion_header_row_' + dr.name);
            if (!dr.headerRow)
            {
                dr._allowHeaderLock = false;
                return;
            }
            dr.headerRowContent = dr.headerRow.child('td');
            dr.headerSpacer = Ext.get('dataregion_header_row_spacer_' + dr.name);
            dr.colHeaderRow = Ext.get('dataregion_column_header_row_' + dr.name);
            dr.colHeaderRowSpacer = Ext.get('dataregion_column_header_row_spacer_' + dr.name);
            dr.paginationEl = Ext.get('dataregion_header_' + dr.name);

            // check if the header row is being used
            dr.includeHeader = dr.headerRow.isDisplayed();

            // initialize row contents
            // Check if we have colHeaderRow and colHeaderRowSpacer - they won't be present if there was an SQLException
            // during query execution, so we didn't get column metadata back
            if (dr.colHeaderRow)
            {
                dr.rowContent = Ext.query(" > td[class*=labkey-column-header]", dr.colHeaderRow.id);
            }
            if (dr.colHeaderRowSpacer)
            {
                dr.rowSpacerContent = Ext.query(" > td[class*=labkey-column-header]", dr.colHeaderRowSpacer.id);
            }
            dr.firstRow = Ext.query("tr[class=labkey-alternate-row]:first td", dr.table.id);

            // performance degradation
            var tooManyColumns = dr.rowContent.length > 80 || ((dr.rowContent.length > 40) && !Ext.isWebKit && !Ext.isIE10p);
            var tooManyRows = (dr.rowCount && dr.rowCount > 1000);

            if (tooManyColumns || tooManyRows)
            {
                dr._allowHeaderLock = false;
                return;
            }

            // If no data rows exist just turn off header locking
            if (dr.firstRow.length == 0)
            {
                dr.firstRow = Ext.query("tr[class=labkey-row]:first td", dr.table.id);
                if (dr.firstRow.length == 0)
                {
                    dr._allowHeaderLock = false;
                    return;
                }
            }

            // initialize additional listeners
            Ext.EventManager.on(window, 'load', this.onResize, this, {single: true});
            Ext.EventManager.on(window, 'scroll', this.onScroll, this);
            Ext.EventManager.on(document, 'DOMNodeInserted', this.onResize, this); // Issue #13121

            Ext.EventManager.on(window, 'resize', this.onResize, this);
            ensurePaginationVisible(dr);

            // initialize panel listeners
            // 13669: customize view jumping when using drag/drop to reorder columns/filters/sorts
            // must manage DOMNodeInserted Listeners due to panels possibly dynamically adding elements to page
            dr.on('afterpanelshow', function ()
            {
                Ext.EventManager.un(document, 'DOMNodeInserted', this.onResize, this); // suspend listener
                this.onResize();
            }, this);

            dr.on('afterpanelhide', function ()
            {
                Ext.EventManager.on(document, 'DOMNodeInserted', this.onResize, this); // resume listener
                this.onResize();
            }, this);

            // initialize timer task for resizing and scrolling
            dr.hdrCoord = [];
            dr.resizeTask = new Ext.util.DelayedTask(function ()
            {
                this.reset(true);
                ensurePaginationVisible(this.dr);
            }, this);

            this.reset(true);
        },

        calculateHeaderPosition: function (recalcPosition)
        {
            this.calculateLockPosition(recalcPosition);
            this.onScroll();
        },

        calculateLockPosition: function (recalcPosition)
        {
            var el, s, src, i = 0, dr = this.dr;

            for (; i < dr.rowContent.length; i++)
            {
                src = Ext.get(dr.firstRow[i]);
                el = Ext.get(dr.rowContent[i]);

                s = {width: src.getWidth(), height: el.getHeight()}; // note: width coming from data row not header
                el.setWidth(s.width); // 15420

                Ext.get(dr.rowSpacerContent[i]).setSize(s);  // must be done after 'el' is set (ext side-effect?)
            }

            if (recalcPosition === true) dr.hdrCoord = findPos(dr);
            dr.hdrLocked = false;
        },

        disable: function ()
        {
            var dr = this.dr;

            dr._allowHeaderLock = false;

            if (dr.resizeTask)
            {
                dr.resizeTask.cancel();
                delete dr.resizeTask;
            }

            Ext.EventManager.un(window, 'load', this.onResize, this);
            Ext.EventManager.un(window, 'resize', this.onResize, this);
            Ext.EventManager.un(window, 'scroll', this.onScroll, this);
            Ext.EventManager.un(document, 'DOMNodeInserted', this.onResize, this);
        },

        /**
         * WARNING: This function is called often. Performance implications for each line.
         * NOTE: window.pageYOffset and pageXOffset are not available in IE7-. For these document.documentElement.scrollTop
         * and document.documentElement.scrollLeft could be used. Additionally, position: fixed is not recognized by
         * IE7- and can be best approximated with position: absolute and explicit top/left.
         */
        onScroll: function ()
        {

            var dr = this.dr, hrStyle = '', chrStyle = '';

            // calculate Y scrolling
            if (window.pageYOffset >= dr.hdrCoord[1] && window.pageYOffset < dr.hdrCoord[2])
            {
                // The header has reached the top of the window and needs to be locked
                var tWidth = dr.table.getComputedWidth();
                dr.headerSpacer.dom.style.display = "table-row";
                dr.colHeaderRowSpacer.dom.style.display = "table-row";
                hrStyle += "top: 0; position: fixed; min-width: " + tWidth + "px; z-index: 9000; "; // 13229
                dr.headerRowContent.applyStyles("min-width: " + (tWidth - 3) + "px; ");
                chrStyle += "position: fixed; background: white; top: " + dr.hdrCoord[3] + "px; " +
                        "min-width: " + tWidth + "px; box-shadow: -2px 5px 5px #DCDCDC; z-index: 9000; "; // 13229
                dr.hdrLocked = true;
            }
            else if (dr.hdrLocked && window.pageYOffset >= dr.hdrCoord[2])
            {
                // The bottom of the Data Region is near the top of the window and the locked header
                // needs to start 'sliding' out of view.
                var top = dr.hdrCoord[2] - window.pageYOffset;
                hrStyle += "top: " + top + "px; ";
                chrStyle += "top: " + (top + dr.hdrCoord[3]) + "px; ";
            }
            else if (dr.hdrLocked)
            { // only reset if the header is locked
                // The header should not be locked
                this.reset();
            }

            // Calculate X Scrolling
            if (dr.hdrLocked)
            {
                hrStyle += "left: " + (dr.hdrCoord[0] - window.pageXOffset) + "px; ";
                chrStyle += "left: " + (dr.hdrCoord[0] - window.pageXOffset) + "px; ";
            }

            if (hrStyle)
            {
                dr.headerRow.applyStyles(hrStyle);
            }
            if (chrStyle)
            {
                dr.colHeaderRow.applyStyles(chrStyle);
            }
        },

        onResize: function ()
        {

            var dr = this.dr;

            if (!dr.table) return;

            if (dr.headerLock())
            {
                if (dr.resizeTask)
                {
                    dr.resizeTask.delay(110);
                }
            }
            else
            {
                ensurePaginationVisible(dr);
            }
        },

        /**
         * Adjusts the header styling to the best approximate of what the defaults are when the header is not locked
         */
        reset: function (recalc)
        {

            var dr = this.dr;

            dr.hdrLocked = false;
            dr.headerRow.applyStyles("top: auto; position: static; min-width: 0;");
            dr.headerRowContent.applyStyles("min-width: 0;");
            dr.colHeaderRow.applyStyles("top: auto; position: static; box-shadow: none; min-width: 0;");
            dr.headerSpacer.dom.style.display = "none";
            dr.headerSpacer.setHeight(dr.headerRow.getHeight());
            dr.colHeaderRowSpacer.dom.style.display = "none";
            this.calculateHeaderPosition(recalc);
        }
    };
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
            { 'key': config.selectionKey, 'checked': config.checked });
    var params = { id: config.ids || config.id };

    Ext.Ajax.request({
        url: url,
        method: "POST",
        params: params,
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
LABKEY.DataRegion.clearSelected = function (config)
{
    var url = LABKEY.ActionURL.buildURL("query", "clearSelected.api", config.containerPath,
            { 'key': config.selectionKey });

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
            { 'key': config.selectionKey });

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

    var containerData = [];
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
        items: [
            {
                ref: "defaultNameField",
                xtype: "radio",
                fieldLabel: "View Name",
                boxLabel: "Default view for this page",
                inputValue: "default",
                name: "saveCustomView_namedView",
                checked: canEdit && !viewName,
                disabled: hidden || !canEdit
            },
            {
                xtype: "compositefield",
                ref: "nameCompositeField",
                // Let the saveCustomView_name field display the error message otherwise it will render as "saveCustomView_name: error message"
                combineErrors: false,
                items: [
                    {
                        xtype: "radio",
                        fieldLabel: "",
                        boxLabel: "Named",
                        inputValue: "named",
                        name: "saveCustomView_namedView",
                        checked: !canEdit || viewName,
                        handler: function (radio, value)
                        {
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
                    },
                    {
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
                        validator: function (value)
                        {
                            if ("default" === value.trim())
                                return "The view name 'default' is not allowed";
                            return true;
                        },
                        selectOnFocus: true,
                        value: newViewName,
                        disabled: hidden || (canEdit && !viewName)
                    }
                ]
            },
            {
                xtype: "box",
                style: "padding-left: 122px; padding-bottom: 8px",
                html: "<em>The " + (!config.canEdit ? "current" : "shadowed") + " view is not editable.<br>Please enter an alternate view name.</em>",
                hidden: canEdit
            },
            {
                xtype: "spacer",
                height: "8"
            },
            {
                ref: "sharedField",
                xtype: "checkbox",
                name: "saveCustomView_shared",
                fieldLabel: "Shared",
                boxLabel: "Make this grid view available to all users",
                checked: shared,
                disabled: disableSharedAndInherit || !canEditSharedViews
            },
            {
                ref: "inheritField",
                xtype: "checkbox",
                name: "saveCustomView_inherit",
                fieldLabel: "Inherit",
                boxLabel: "Make this grid view available in child folders",
                checked: containerFilterable && inherit,
                disabled: disableSharedAndInherit || !containerFilterable,
                hidden: !containerFilterable,
                listeners: {
                    check: function (checkbox, checked)
                    {
                        Ext.ComponentMgr.get("saveCustomView_targetContainer").setDisabled(!checked);
                    }
                }
            },
            {
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
                disabled: disableSharedAndInherit || !containerFilterable || !inherit,
                listeners: {
                    select: function (combobox)
                    {
                        if (!warnedAboutMoving && combobox.getValue() != config.containerPath)
                        {
                            warnedAboutMoving = true;
                            Ext.Msg.alert("Moving a Saved View", "If you save, this view will be moved from '" + config.containerPath + "' to " + combobox.getValue());
                        }
                    }
                }
            }
        ],
        buttons: [
            {
                text: "Save",
                handler: function ()
                {
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
            },
            {
                text: "Cancel",
                handler: function ()
                {
                    win.close();
                }
            }
        ]
    });
    win.show();
};


LABKEY.DataRegion.ALL_FILTERS_SKIP_PREFIX = ".~";

// private
LABKEY.DataRegion.getParamValPairsFromString = function (queryString, skipPrefixes)
{
    if (queryString && queryString.indexOf("?") > -1)
    {
        queryString = queryString.substring(queryString.indexOf("?") + 1);
    }

    var paramValPairs = [];
    if (queryString != null && queryString.length > 0)
    {
        var pairs = queryString.split("&");
        var iNew = 0;
        PARAM_LOOP: for (var i = 0; i < pairs.length; i++)
        {
            var paramPair = pairs[i].split("=", 2);
            paramPair[0] = decodeURIComponent(paramPair[0]);

            // LastFilter parameters are prefixed with an optional scope. Look for a filter that ends with
            // ".lastFilter", and don't propagate it
            if (paramPair[0].indexOf(".lastFilter") >= 0 && paramPair[0].indexOf(".lastFilter") == paramPair[0].length - ".lastFilter".length)
                continue;

            if (skipPrefixes)
            {
                for (var j = 0; j < skipPrefixes.length; j++)
                {
                    var skipPrefix = skipPrefixes[j];
                    if (skipPrefix)
                    {
                        // Special prefix that should remove all filters, but no other parameters
                        if (skipPrefix.indexOf(LABKEY.DataRegion.ALL_FILTERS_SKIP_PREFIX) == skipPrefix.length - 2)
                        {
                            if (paramPair[0].indexOf("~") > 0)
                                continue PARAM_LOOP;
                        }
                        else if (paramPair[0].indexOf(skipPrefix) == 0)
                        {
                            // only skip filters, parameters, and sorts
                            if (paramPair[0] == skipPrefix)
                                continue PARAM_LOOP;
                            if (paramPair[0].indexOf("~") > 0)
                                continue PARAM_LOOP;
                            if (paramPair[0].indexOf(".param.") > 0)
                                continue PARAM_LOOP;
                            if (paramPair[0] == skipPrefix + "sort")
                                continue PARAM_LOOP;
                        }
                    }
                }
            }
            if (paramPair.length > 1)
                paramPair[1] = decodeURIComponent(paramPair[1]);
            paramValPairs[iNew] = paramPair;
            iNew++;
        }
    }
    return paramValPairs;
};

// private
LABKEY.DataRegion.buildQueryString = function (pairs)
{
    if (pairs == null || pairs.length == 0)
    {
        return "";
    }

    var queryString = [], key, value, i = 0;

    for (; i < pairs.length; i++)
    {
        key = pairs[i][0];
        value = pairs[i].length > 1 ? pairs[i][1] : undefined;

        queryString.push(encodeURIComponent(key));
        if (undefined != value)
        {
            if (Ext.isDate(value))
            {
                value = Ext.util.Format.date(value, 'Y-m-d');
                if (LABKEY.Utils.endsWith(value, "Z"))
                {
                    value = value.substring(0, value.length - 1);
                }
            }
            queryString.push("=");
            queryString.push(encodeURIComponent(value));
        }
        queryString.push("&");
    }

    if (queryString.length > 0)
    {
        queryString.pop();
    }

    return queryString.join("");
};

// private
// Migrated from util.js due to dependency on DataRegion
// generator function to create a function to call when flag field is clicked
// This is used in FlagColumnRenderer
LABKEY.DataRegion._showFlagDialog = function(config)
{
    config = Ext.apply({}, config, {
        url: LABKEY.ActionURL.buildURL('experiment', 'setFlag.api'),
        dataRegionName: null,
        defaultComment: "Flagged for review",
        dialogTitle: "Review",
        imgTitle: "Flag for review",
        imgSrcFlagged: LABKEY.contextPath + "/Experiment/flagDefault.gif",
        imgClassFlagged: "",
        imgSrcUnflagged: LABKEY.contextPath + "/Experiment/unflagDefault.gif",
        imgClassUnflagged: "",
        translatePrimaryKey: null
    });

    function getDataRegion()
    {
        if (LABKEY.DataRegions && typeof config.dataRegionName == 'string')
            return LABKEY.DataRegions[config.dataRegionName];
        return null;
    }

    var setFlag = function(flagId)
    {
        Ext.QuickTips.init();

        var clickedComment;
        var flagImages = Ext.DomQuery.select("IMG[flagId='" + flagId + "']");
        if (!flagImages || 0==flagImages.length)
            return;
        var img = flagImages[0];
        if (img.title != config.imgTitle)
            clickedComment = img.title;

        var checkedLsids = [];
        var dr = getDataRegion();
        if (dr && typeof config.translatePrimaryKey == 'function')
        {
            var pks = dr.getChecked() || [];
            for (var i=0 ; i<pks.length ; i++)
                checkedLsids.push(config.translatePrimaryKey(pks[i]));
        }

        var msg = 'Enter a comment';
        var comment = clickedComment || config.defaultComment;
        if (checkedLsids.length > 0)
        {
            msg = "Enter comment for " + checkedLsids.length + " selected " + (checkedLsids.length==1?"row":"rows");
            comment = config.defaultComment;        // consider inspect all for equal comments
        }

        var lsids = checkedLsids.length==0 ? [flagId] : checkedLsids;
        var successFn = function(response, options)
        {
            var comment = options.params.comment;
            for (var i=0 ; i<lsids.length ; i++)
            {
                var lsid = lsids[i];
                var flagImages = Ext.DomQuery.select("IMG[flagId='" + lsid + "']");
                if (!flagImages || 0==flagImages.length)
                    continue;
                el = Ext.get(flagImages[0]);
                if (comment)
                {
                    el.dom.src = config.imgSrcFlagged;
                    el.dom.title = comment;
                    if (config.imgClassUnflagged)
                        el.removeClass(config.imgClassUnflagged);
                    el.addClass(config.imgClassFlagged);
                }
                else
                {
                    el.dom.src = config.imgSrcUnflagged;
                    el.dom.title = config.imgTitle;
                    if (config.imgClassFlagged)
                        el.removeClass(config.imgClassFlagged);
                    el.addClass(config.imgClassUnflagged);
                }
            }
        };

        var el = Ext.get(img);
        Ext.MessageBox.show({
            title: config.dialogTitle,
            prompt: true,
            msg: msg,
            value: comment,
            width: 300,
            fn: function(btnId, value)
            {
                if (btnId == 'ok')
                {
                    Ext.Ajax.request({
                        url: config.url,
                        params: {
                            lsid: lsids,
                            comment: value,
                            unique: new Date().getTime()
                        },
                        success: successFn,
                        failure: function() { alert("Failure!"); }
                    });
                }
            },
            buttons: Ext.MessageBox.OKCANCEL
        });
    };

    if (Ext.isReady)
    {
        return setFlag;
    }

    return function(flagId)
    {
        Ext.onReady(function(){setFlag(flagId)});
    };
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

    constructor: function (config)
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

        this._renderTask = new Ext.util.DelayedTask(function ()
        {
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
        }, this);
    },

    // private
    destroy: function ()
    {
        if (this._renderTask)
        {
            this._renderTask.cancel();
            delete this._renderTask;
        }
        this.purgeListeners();
    },

    addMessage: function (msg, part)
    {

        part = part || 'info';
        this.parts[part] = msg;
        this.setVisible(true);
        this._refresh();
    },

    getMessage: function (part)
    {

        return this.parts[part];
    },

    removeMessage: function (part)
    {

        part = part || 'info';
        delete this.parts[part];
        this._refresh();
    },

    /**
     * Deletes all stored messages and clears the rendered area
     */
    removeAll: function ()
    {

        this.parts = {};
        this._refresh();
    },

    render: function ()
    {
        this._renderTask.delay(10);
    },

    setVisible: function (visible)
    {

        this.parentEl.setVisible(visible, false);
    },

    isVisible: function ()
    {
        if (!this.parentEl.dom)
        {
            return false;
        }
        return this.parentEl.isVisible();
    },

    /**
     * Clears the rendered DOM elements.
     */
    clear: function ()
    {

        var div = this.parentEl.child("div");
        if (div)
            div.dom.innerHTML = "";
    },

    /**
     * private
     */
    _refresh: function ()
    {

        if (this.isVisible())
        {
            this.clear();
            this.render();
        }
    }
});
