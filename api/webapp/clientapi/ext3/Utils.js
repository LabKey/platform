/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
 /**
    * Creates an Ext.data.Store that queries the LabKey Server database and can be used as the data source
    * for various components, including GridViews, ComboBoxes, and so forth.
    * @deprecated
    * @param {Object} config Describes the GridView's properties.
    * @param {String} config.schemaName Name of a schema defined within the current
    *                 container.  Example: 'study'.  See also: <a class="link"
                      href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                      How To Find schemaName, queryName &amp; viewName</a>.
    * @param {String} config.queryName Name of a query defined within the specified schema
    *                 in the current container.  Example: 'SpecimenDetail'. See also: <a class="link"
                      href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                      How To Find schemaName, queryName &amp; viewName</a>.
    * @param {String} [config.containerPath] The container path in which the schemaName and queryName are defined.
    * @param {String} [config.viewName] Name of a custom view defined over the specified query.
    *                 in the current container. Example: 'SpecimenDetail'.  See also: <a class="link"
                      href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
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
LABKEY.Utils.createExtStore = new function(){
    function createHttpProxyImpl(containerPath, errorListener)
    {
        var proxy = new Ext.data.HttpProxy(new Ext.data.Connection({
                //where to retrieve data
                url: LABKEY.ActionURL.buildURL("query", "selectRows", containerPath), //url to data object (server side script)
                method: 'GET'
            }));

        if (errorListener)
            proxy.on("loadexception", errorListener);

        proxy.on("beforeload", mapQueryParameters);

        return proxy;
    }

    function mapQueryParameters(store, options)
    {
        // map all parameters from ext names to labkey names:
        for (var param in options)
        {
            if (_extParamMapping[param])
                options[_extParamMapping[param]] = options[param];
        }

        // fix up any necessary parameter values:
        if ("DESC" == options['query.sortdir'])
        {
            var sortCol = options['query.sort'];
            options['query.sort'] = "-" + sortCol;
        }
    }

    return function (storeConfig)
    {
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
    }
}

/**
 * Ensure BoxComponent is visible on the page.
 * @param boxComponent
 * @deprecated
 */
LABKEY.Utils.ensureBoxVisible = function (boxComponent)
{
    //TODO
    var box = boxComponent.getBox(true);
    var viewportWidth = Ext.lib.Dom.getViewWidth();
    var scrollLeft = Ext.dd.DragDropMgr.getScrollLeft();

    var scrollBarWidth = 20;
    if (viewportWidth - scrollBarWidth + scrollLeft < box.width + box.x) {
        boxComponent.setPosition(viewportWidth + scrollLeft - box.width - scrollBarWidth);
    }
}