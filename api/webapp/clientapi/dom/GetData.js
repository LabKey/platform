/*
 * Copyright (c) 2014-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.Query.GetData = new function(impl) {

    /**
     * Documentation specified in core/GetData.js -- search for "@name renderQueryWebPart"
     */
    impl.renderQueryWebPart = function(config) {
        var jsonData = validateGetDataConfig(config);
        jsonData.renderer.type = 'json';
        jsonData.renderer.maxRows = 0;

        if (!config.webPartConfig) {
            throw new Error("A webPartConfig object is required.");
        }

        var requestConfig = {
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('query', 'getData', config.source.containerPath),
            jsonData: jsonData,
            success: function(response){
                var json = LABKEY.Utils.decode(response.responseText);
                config.webPartConfig.schemaName = config.source.schemaName;
                config.webPartConfig.queryName = json.queryName;
                new LABKEY.QueryWebPart(config.webPartConfig);
            },
            failure: function(response, options) {
                if (response.status != 0) {
                    LABKEY.Utils.displayAjaxErrorResponse(response, null, true, "Error during GetData call");
                }
            }
        };

        LABKEY.Ajax.request(requestConfig);
    };
    return impl;

}(LABKEY.Query.GetData);
