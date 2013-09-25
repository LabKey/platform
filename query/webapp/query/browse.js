// Specific implementation for browse.jsp
Ext.onReady(function(){
    var onSchemasLoaded = function(browser)
    {
        var params = LABKEY.ActionURL.getParameters();

        var schemaName = params.schemaName;
        var queryName = params['queryName'] || params['query.queryName'];
        if (queryName && schemaName)
        {
            browser.selectQuery(schemaName, queryName, function(){
                browser.showQueryDetails(schemaName, queryName);
            });
        }
        else if (schemaName)
            browser.selectSchema(schemaName, queryName);

        if (window.location.hash && window.location.hash.length > 1)
        {
            //window.location.hash returns an decoded value, which
            //is different from what Ext.History.getToken() returns
            //so use the same technique Ext does for getting the hash
            var href = top.location.href;
            var idx = href.indexOf("#");
            var hash = idx >= 0 ? href.substr(idx + 1) : null;
            if (hash)
                browser.onHistoryChange(hash);
        }
    };

    var browser = new LABKEY.ext.SchemaBrowser({
        renderTo: 'browser',
        boxMinHeight: 600,
        boxMinWidth: 900,
        useHistory: true,
        listeners: {
            schemasloaded: {
                fn: onSchemasLoaded,
                scope: this
            }
        }
    });
});