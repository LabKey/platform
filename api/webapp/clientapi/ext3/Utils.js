/*
 * Copyright (c) 2012-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace('LABKEY.ext');

// TODO: Get these off the global 'Date' object
Ext.ns("Date.patterns");
Ext.applyIf(Date.patterns,{
    ISO8601Long:"Y-m-d H:i:s",
    ISO8601Short:"Y-m-d"
});

LABKEY.ext.Utils = new function() {
    var createHttpProxyImpl = function(containerPath, errorListener) {
        var proxy = new Ext.data.HttpProxy(new Ext.data.Connection({
            //where to retrieve data
            url: LABKEY.ActionURL.buildURL("query", "selectRows", containerPath), //url to data object (server side script)
            method: 'GET'
        }));

        if (errorListener)
            proxy.on("loadexception", errorListener);

        proxy.on("beforeload", mapQueryParameters);

        return proxy;
    };

    var mapQueryParameters = function(store, options) {
        // map all parameters from ext names to labkey names:
        for (var p in options)
        {
            if (options.hasOwnProperty(p)) {
                if (_extParamMapping[p])
                    options[_extParamMapping[p]] = options[p];
            }
        }

        // fix up any necessary parameter values:
        if ("DESC" == options['query.sortdir'])
        {
            var sortCol = options['query.sort'];
            options['query.sort'] = "-" + sortCol;
        }
    };

    /**
     * This method takes an object that is/extends an Ext3.Container (e.g. Panels, Toolbars, Viewports, Menus) and
     * resizes it so the Container fits inside the its parent container.
     * @param extContainer - (Required) outer container which is the target to be resized
     * @param options - The set of options
     * @param options.skipWidth - true to skip updating width, default false
     * @param options.skipHeight - true to skip updating height, default false
     * @param options.paddingWidth - total width padding
     * @param options.paddingHeight - total height padding
     * @param options.offsetY - distance between bottom of page to bottom of component
     */
    var resizeToContainer = function(extContainer, options) {
        var config = {
            offsetY: 35,
            paddingHeight: 0,
            paddingWidth: 0,
            skipHeight: false,
            skipWidth: false
        };

        if (Ext.isObject(options)) {
            config = Ext.apply(config, options);
        }
        // else ignore the parameters

        if (!extContainer || !extContainer.rendered || (config.skipWidth && config.skipHeight)) {
            return;
        }

        var height = 0;
        var width = 0;

        if (!config.skipWidth) {
            width = extContainer.el.parent().getBox().width;
        }


        if (!config.skipHeight) {
            height = window.innerHeight - extContainer.el.getXY()[1];
        }

        var padding = [
            config.paddingWidth,
            config.paddingHeight
        ];

        var size = {
            width: Math.max(100, width - padding[0]),
            height: Math.max(100, height - padding[1] - config.offsetY)
        };

        if (config.skipWidth) {
            extContainer.setHeight(size.height);
        }
        else if (config.skipHeight) {
            extContainer.setWidth(size.width);
        }
        else {
            extContainer.setSize(size);
        }

        extContainer.doLayout();
    };

    return {
        /**
         * Creates an Ext.data.Store that queries the LabKey Server database and can be used as the data source
         * for various components, including GridViews, ComboBoxes, and so forth.
         * @deprecated
         * @param {Object} config Describes the GridView's properties.
         * @param {String} config.schemaName Name of a schema defined within the current
         *                 container.  Example: 'study'.  See also: <a class="link"
         href="https://www.labkey.org/Documentation/wiki-page.view?name=findNames">
         How To Find schemaName, queryName &amp; viewName</a>.
         * @param {String} config.queryName Name of a query defined within the specified schema
         *                 in the current container.  Example: 'SpecimenDetail'. See also: <a class="link"
         href="https://www.labkey.org/Documentation/wiki-page.view?name=findNames">
         How To Find schemaName, queryName &amp; viewName</a>.
         * @param {String} [config.containerPath] The container path in which the schemaName and queryName are defined.
         * @param {String} [config.viewName] Name of a custom view defined over the specified query.
         *                 in the current container. Example: 'SpecimenDetail'.  See also: <a class="link"
         href="https://www.labkey.org/Documentation/wiki-page.view?name=findNames">
         How To Find schemaName, queryName &amp; viewName</a>.
         * @param {Object} [config.allowNull] If specified, this configuration will be used to insert a blank
         *                 entry as the first entry in the store.
         * @param {String} [config.allowNull.keyColumn] If specified, the name of the column in the underlying database
         *                 that holds the key.
         * @param {String} [config.allowNull.displayColumn] If specified, the name of the column in the underlying database
         *                 that holds the value to be shown by default in the display component.
         * @param {String} [config.allowNull.emptyName] If specified, what to show in the list for the blank entry.
         *                 Defaults to '[None]'.
         * @param {String} [config.allowNull.emptyValue] If specified, the value to be used for the blank entry.
         *                 Defaults to the empty string.
         *
         * @return {Ext.data.Store} The initialized Store object
         */
        createExtStore: function(storeConfig) {
            if (!storeConfig)
                storeConfig = {};
            if (!storeConfig.baseParams)
                storeConfig.baseParams = {};
            storeConfig.baseParams['query.queryName'] = storeConfig.queryName;
            storeConfig.baseParams['schemaName'] = storeConfig.schemaName;
            if (storeConfig.viewName)
                storeConfig.baseParams['query.viewName'] = storeConfig.viewName;

            if (!storeConfig.proxy)
                storeConfig.proxy = createHttpProxyImpl(storeConfig.containerPath);

            if (!storeConfig.remoteSort)
                storeConfig.remoteSort = true;

            if (!storeConfig.listeners || !storeConfig.listeners.loadexception)
                storeConfig.listeners = { loadexception : { fn : handleLoadError } };

            storeConfig.reader = new Ext.data.JsonReader();

            var result = new Ext.data.Store(storeConfig);

            if (storeConfig.allowNull)
            {
                var emptyValue = storeConfig.allowNull.emptyValue;
                if (!emptyValue)
                {
                    emptyValue = "";
                }
                var emptyName = storeConfig.allowNull.emptyName;
                if (!emptyName)
                {
                    emptyName = "[None]";
                }
                result.on("load", function(store)
                {
                    var emptyRecordConstructor = Ext.data.Record.create([storeConfig.allowNull.keyColumn, storeConfig.allowNull.displayColumn]);
                    var recordData = {};
                    recordData[storeConfig.allowNull.keyColumn] = emptyValue;
                    recordData[storeConfig.allowNull.displayColumn] = emptyName;
                    var emptyRecord = new emptyRecordConstructor(recordData);
                    store.insert(0, emptyRecord);
                });
            }

            return result;
        },

        /**
         * Ensure BoxComponent is visible on the page.
         * @param boxComponent
         * @deprecated
         */
        ensureBoxVisible: function(boxComponent) {
            var box = boxComponent.getBox(true);
            var viewportWidth = Ext.lib.Dom.getViewWidth();
            var scrollLeft = Ext.dd.DragDropMgr.getScrollLeft();

            var scrollBarWidth = 20;
            if (viewportWidth - scrollBarWidth + scrollLeft < box.width + box.x) {
                boxComponent.setPosition(viewportWidth + scrollLeft - box.width - scrollBarWidth);
            }
        },

        /**
         * Use LABKEY.Utils.handleTabsInTextArea instead
         * @deprecated
         */
        handleTabsInTextArea: function(event){
            LABKEY.Utils.handleTabsInTextArea(event);
        },

        /**
         * This method takes an object that is/extends an Ext.Container (e.g. Panels, Toolbars, Viewports, Menus) and
         * resizes it so the Container fits inside the viewable region of the window. This is generally used in the case
         * where the Container is not rendered to a webpart but rather displayed on the page itself (e.g. SchemaBrowser,
         * manageFolders, etc).
         * @param extContainer - (Required) outer container which is the target to be resized
         * @param width - (Required) width of the viewport. In many cases, the window width. If a negative width is passed than
         *                           the width will not be set.
         * @param height - (Required) height of the viewport. In many cases, the window height. If a negative height is passed than
         *                           the height will not be set.
         * @param paddingX - distance from the right edge of the viewport. Defaults to 35.
         * @param paddingY - distance from the bottom edge of the viewport. Defaults to 35.
         */
        resizeToViewport: function(extContainer, width, height, paddingX, paddingY, offsetX, offsetY)
        {
            resizeToContainer.apply(this, arguments);
        }
    };
};