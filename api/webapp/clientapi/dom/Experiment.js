/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.Experiment = new function (impl) {

    /**
     * Documentation specified in core/Experiment.js -- search for "@name exportRuns"
     */
    impl.exportRuns = function (config) {
        if (!config.runIds || !LABKEY.Utils.isArray(config.runIds))
            throw new Error('runIds array required');

        var form = new FormData();
        for (var i = 0; i < config.runIds.length; i++) {
            form.append('runIds', config.runIds[i]);
        }

        if (config.lsidOutputType)
            form.append('lsidOutputType', config.lsidOutputType);

        if (config.exportType)
            form.append('exportType', config.exportType);

        if (config.fileName)
            form.append('xarFileName', config.fileName)

        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('experiment', 'exportRuns.api'),
            form: form,
            downloadFile: true,
            success: LABKEY.Utils.getCallbackWrapper(config.success, config.scope, false),
            failure: LABKEY.Utils.getCallbackWrapper(config.failure, config.scope, true),
        });
    };

    return impl;
}(LABKEY.Experiment || new function() { return {}; });