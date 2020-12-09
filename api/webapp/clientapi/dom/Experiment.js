/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * @namespace The Experiment static class allows you to create hidden run groups and other experiment-related functionality.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href='https://www.labkey.org/Documentation/wiki-page.view?name=moduleassay'>LabKey File-Based Assays</a></li>
 *                  <li><a href="https://www.labkey.org/Documentation/wiki-page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.Experiment = new function (impl) {

    /**
     * Export a run as a XAR archive.
     * @memberOf LABKEY.Experiment
     * @function
     * @name exportRuns
     * @param config
     * @param {Number[]} config.runIds Array of ExpRun rowId to export
     * @param {String} config.lsidOutputType Determines how LSIDs will be translated in the XAR XML file. Defaults to 'FOLDER_RELATIVE'
     * <ul>
     *     <li><b>ABSOLUTE</b> Keeps the original LSID from the source server
     *     <li><b>FOLDER_RELATIVE</b> ?
     *     <li><b>PARTIAL_FOLDER_RELATIVE</b> ?
     * </ul>
     * @param {String} config.exportType Defaults to 'BROWSER_DOWNLOAD'
     * <ul>
     *     <li><b>BROWSER_DOWNLOAD</b> Download to web browser
     *     <li><b>PIPELIE_FILE</b> Write to exportedXars directory in pipeline
     * </ul>
     * @param {String} config.fileName The exported archive file name.  Defaults to 'export.xar'
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