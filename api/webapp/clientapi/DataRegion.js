/**
* @fileOverview
* @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
* @version 10.2
* @license Copyright (c) 2008-2010 LabKey Corporation
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
 * The DataRegion constructor is private.
 * @class The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
 */
LABKEY.DataRegion = function (config)
{
    this.config = config || {};

    this.id = config.name; // XXX: may not be unique on the on page with webparts
    this.name = config.name;
    this.schemaName = config.schemaName;
    this.queryName = config.queryName;
    this.viewName = config.viewName || "";
    this.viewUnsaved = config.viewUnsaved;
    this.sortFilter = config.sortFilter;

    this.complete = config.complete;
    this.offset = config.offset || 0;
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
     * <b>This is an experimental API and is subject to change with out warning.</b>
     *
     * @param config A configuration object with the following properties:
     * @param {Function} config.successCallback The function to be called upon success of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> an object with the property 'selected' that is an array of the primary keys for the selected rows.
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.failureCallback] The function to call upon error of the request.
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
     * <b>This is an experimental API and is subject to change with out warning.</b>
     *
     * @param config A configuration object with the following properties:
     * @param {Function} config.successCallback The function to be called upon success of the request.
     * The callback will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> an object with the property 'count' of 0 to indicate an empty selection.
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.failureCallback] The function to call upon error of the request.
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
     * Show a message in the header of this DataRegion.
     * @param html the HTML source of the message to be shown
     */
    this.showMessage = function (html)
    {
        var div = this.msgbox.child("div");
        if (div.first())
            div.createChild({tag: 'hr'});
        div.createChild({tag: 'div', cls: 'labkey-dataregion-msg', html: html});
        this.msgbox.setVisible(true);
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
     */
    this.changeView = function(viewName)
    {
        if (false === this.fireEvent("beforechangeview", this, viewName))
            return;

        this._setParam(".viewName", viewName, [".offset", ".showRows", ".viewName", ".reportId"]);
    };

    this._initElements();
    Ext.EventManager.on(window, "load", this._resizeContainer, this, {single: true});
    Ext.EventManager.on(window, "resize", this._resizeContainer, this);
    this._showPagination(this.header);
    this._showPagination(this.footer);

    if (this.viewUnsaved)
    {
        if (this.viewName)
            this.showMessage("The custom view '" + escape(this.viewName) + "' is temporary.  <a href='#'>Save</a>.");
        else
            this.showMessage("This custom view is temporary.  <a href='#'>Save</a>.");
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

        var paramValPairs = getParamValPairs(this.requestURL, skipPrefixes);
        if (null != value)
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
            successCallback: success,
            failureCallback: function (response, options) { this.showMessage("Error sending selection."); }
        });
    },

    // private
    _showSelectMessage : function (msg)
    {
        if (this.showRecordSelectors)
        {
            msg += "&nbsp; Select: <span class='labkey-link' onclick='LABKEY.DataRegions[\"" + escape(this.name) + "\"].selectNone();' title='Clear selection from all rows'>None</span>";
            var showOpts = new Array();
            if (this.showRows != "all")
                showOpts.push("<span class='labkey-link' onclick='LABKEY.DataRegions[\"" + escape(this.name) + "\"].showAll();' title='Show all rows'>All</span>");
            if (this.showRows != "selected")
               showOpts.push("<span class='labkey-link' onclick='LABKEY.DataRegions[\"" + escape(this.name) + "\"].showSelected();' title='Show all selected rows'>Selected</span>");
            if (this.showRows != "unselected")
               showOpts.push("<span class='labkey-link' onclick='LABKEY.DataRegions[\"" + escape(this.name) + "\"].showUnselected();' title='Show all unselected rows'>Unselected</span>");
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

        // 10566: for javascript perf on IE stash the requires selection buttons
        if (!this._requiresSelectionButtons) {
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
     * */
    showButtonPanel : function(panelButton, tabPanelConfig)
    {
        //create the ribbon container if necessary
        if (!this.ribbonContainer)
        {
            var regionHeader = Ext.get(panelButton).parent(".labkey-data-region-header");
            if (!regionHeader || !regionHeader.parent())
                return;

            this.ribbonContainer = regionHeader.parent().createChild({tag:'div',cls:'extContainer'});
        }

        var panelDiv = this.ribbonContainer;
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
                        var minWidth = 0;
                        var tabContentWidth = 0;
                        
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
                            newItems[i].autoScroll = true;

                            //FF and IE won't auto-resize the tab panel to fit the content 
                            //so we need to calculate the min size and set it explicitly
                            if (Ext.isGecko || Ext.isIE)
                            {
                                newItems[i].contentEl.removeClass("x-hide-display");
                                tabContentWidth = newItems[i].contentEl.getWidth();
                                newItems[i].contentEl.addClass("x-hide-display");
                                minWidth = Math.max(minWidth, tabContentWidth);
                            }
                        }
                        tabPanelConfig.items = newItems;
                        if ((Ext.isGecko || Ext.isIE) && minWidth > 0 && regionHeader.getWidth() < minWidth)
                            tabPanelConfig.width = minWidth; 
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

                    this.panelButtonContents[panelButton.id].setWidth(this.panelButtonContents[panelButton.id].getResizeEl().getWidth());
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
    },

    showCustomizeView : function (chooseColumnsUrl)
    {
        window.location = chooseColumnsUrl;
        return;

        // If no schema/query, use old query view designer
        if (!this.schemaName && !this.queryName)
            window.location = chooseColumnsUrl;

        if (!this.customizeView)
        {
            LABKEY.requiresScript("query/queryDesigner.js", true);
            LABKEY.requiresScript("designer/designer2.js", true, function () {

                LABKEY.Query.getQueryDetails({
                    schemaName: this.schemaName,
                    queryName: this.queryName,
                    viewName: this.viewName,
                    successCallback: function (json, response, options) {
                        var el = Ext.get(this.form || this.table);
                        var renderTo = el.parent().insertFirst({tag: "div"});

                        this.customizeView = new LABKEY.DataRegion.ViewDesigner({
                            renderTo: renderTo,
                            style: {
                                float: "left",
                                "margin-right": "8px"
                            },
                            dataRegion: this,
                            schemaName: this.schemaName,
                            queryName: this.queryName,
                            viewName: this.viewName,
                            query: json
                        });

                        this.customizeView.on("viewsave", this.onViewSave, this);

                        this.customizeView.setVisible(true);
                        // XXX: animating the panel is too slow to render
                        //this.customizeView.getEl().slideIn('l', {duration:0.35});
                    },
                    scope: this
                });

            }, this);
        }
        else
        {
            this.customizeView.setVisible(true);
            // XXX: animating the panel is too slow to render
            //this.customizeView.getEl().slideIn('l', {duration:0.35});
        }
    },

    onViewSave : function (designer, newview) {
        this.changeView(newview.name);
    }

});


/**
 * Add or remove items from the current selection.
 * <b>This is an experimental API and is subject to change with out warning.</b>
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey The selection key.
 * @param {Array} config.id Array of primary key ids for each row to select/unselect.
 * @param {Boolean} config.checked If true, the ids will be selected, otherwise unselected.
 * @param {Function} config.successCallback The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'count' to indicate the updated selection count.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failureCallback] The function to call upon error of the request.
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
        success: config.successCallback,
        failure: config.failureCallback
    });
};

/**
 * Clear all selected items.
 * <b>This is an experimental API and is subject to change with out warning.</b>
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey The selection key.
 * @param {Function} config.successCallback The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'count' of 0 to indicate an empty selection.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failureCallback] The function to call upon error of the request.
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
 * <b>This is an experimental API and is subject to change with out warning.</b>
 *
 * @param config A configuration object with the following properties:
 * @param {String} config.selectionKey The selection key.
 * @param {Function} config.successCallback The function to be called upon success of the request.
 * The callback will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> an object with the property 'selected' that is an array of the primary keys for the selected rows.
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failureCallback] The function to call upon error of the request.
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
        success: LABKEY.Utils.getCallbackWrapper(config.successCallback, config.scope),
        failure: LABKEY.Utils.getCallbackWrapper(config.failureCallback, config.scope, true)
    });
};


// FILTER UI

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

    var queryString = LABKEY.DataRegions[tableName] ? LABKEY.DataRegions[tableName].requestURL : null;
    var paramValPairs = getParamValPairs(queryString, null);
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
    "SERIAL":"INT",
    "USERID":"INT"
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


function getParamValPairs(queryString, skipPrefixes)
{
    if (!queryString)
    {
        queryString = getSearchString();
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
    var paramValPairs = getParamValPairs(null, null);
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
            paramArray[i] = escape(pairs[i][0]) + "=" + escape(pairs[i][1]);
        else
            paramArray[i] = escape(pairs[i][0]);
    }

    // Escape doesn't encode '+' properly
    var queryString = paramArray.join("&").replace(/\+/g, "%2B");
    // alert("exit buildQueryString: " + queryString);
    return queryString;
}

function clearFilter()
{
    hideFilterDiv();
    var dr = LABKEY.DataRegions[_tableName];
    if (false === dr.fireEvent("beforeclearfilter", dr, _fieldName))
        return;

    var newParamValPairs = getParamValPairs(dr.requestURL, [_tableName + "." + _fieldName + "~", _tableName + ".offset"]);
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function clearAllFilters()
{
    hideFilterDiv();
    var dr = LABKEY.DataRegions[_tableName];
    if (false === dr.fireEvent("beforeclearallfilters", dr))
        return;

    var newParamValPairs = getParamValPairs(dr.requestURL, [_tableName + ".", _tableName + ".offset"]);
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function doFilter()
{
    hideFilterDiv();

    var queryString = LABKEY.DataRegions[_tableName] ? LABKEY.DataRegions[_tableName].requestURL : null;
    var newParamValPairs = getParamValPairs(queryString, [_tableName + "." + _fieldName + "~", _tableName + ".offset"]);
    var iNew = newParamValPairs.length;

    var comparisons = getValidCompares();
    if (null == comparisons)
        return;

    for (var i = 0; i < comparisons.length; i++)
    {
        newParamValPairs[iNew] = comparisons[i];
        iNew ++;
    }

    var dr = LABKEY.DataRegions[_tableName];
    if (false === dr.fireEvent("beforefilterchange", dr, newParamValPairs))
        return;

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
    var dr = LABKEY.DataRegions[tableName];
    if (!dr)
        return;
    if (false === dr.fireEvent("beforesortchange", dr, columnName, sortDirection))
        return;

    var newSortString = dr.alterSortString(getParameter(tableName + ".sort"), columnName, sortDirection);

    var paramValPairs = getParamValPairs(dr.requestURL, [tableName + ".sort", tableName + ".offset"]);
    paramValPairs[paramValPairs.length] = [tableName + ".sort", newSortString];

    setSearchString(tableName, buildQueryString(paramValPairs));
}

function clearSort(tableName, columnName)
{
    if(!tableName || !columnName)
        return;

    var dr = LABKEY.DataRegions[tableName];
    if (!dr)
        return;
    if (false === dr.fireEvent("beforeclearsort", dr, columnName))
        return;
    
    var newSortString = dr.alterSortString(getParameter(tableName + ".sort"), columnName, null);

    var paramValPairs = getParamValPairs(dr.requestURL, [tableName + ".sort", tableName + ".offset"]);
    if(newSortString.length > 0)
        paramValPairs.push([tableName + ".sort", newSortString]);

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
