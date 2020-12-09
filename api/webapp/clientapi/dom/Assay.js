/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2018 LabKey Corporation
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
 * @namespace Assay static class to retrieve read-only assay definitions.
 * @see LABKEY.Experiment
 */
LABKEY.Assay = new function(impl) {

    /**
     * Create an assay run and import results.
     * @memberOf LABKEY.Assay
     * @function
     * @name importRun
     * @param {Object} config An object which contains the following configuration properties.
     * @param {Number} config.assayId The assay protocol id.
     * @param {String} [config.containerPath] The path to the container in which the assay run will be imported,
     *       if different than the current container. If not supplied, the current container's path will be used.
     * @param {String} [config.name] The name of a run to create. If not provided, the run will be given the same name as the uploaded file or "[Untitled]".
     * @param {String} [config.comment] Run comments.
     * @param {Object} [config.properties] JSON formatted run properties.
     * @param {Number} [config.batchId] The id of an existing {LABKEY.Exp.RunGroup} to add this run into.
     * @param {Object} [config.batchProperties] JSON formatted batch properties.
     * Only used if batchId is not provided when creating a new batch.
     * @param {String} [config.runFilePath] Absolute or relative path to assay data file to be imported.
     * The file must exist under the file or pipeline root of the container.  Only one of 'files', 'runFilePath', or 'dataRows' can be provided.
     * @param {Array} [config.files] Array of <a href='https://developer.mozilla.org/en-US/docs/DOM/File'><code>File</code></a> objects
     * or form file input elements to import.  Only one of 'files', 'runFilePath', or 'dataRows' can be provided.
     * @param {Array} [config.dataRows] Array of assay results to import.  Only one of 'files', 'runFilePath', or 'dataRows' can be provided.
     * @param {Object} [config.plateMetadata] JSON formatted plate metadata contains properties to associate with well groups in the plate template run property.
     * @param {Function} config.success The success callback function will be called with the following arguments:
     * <ul>
     *     <li><b>json</b>: The success response object contains two properties:
     *         <ul>
     *             <li><b>success</b>: true</li>
     *             <li><b>successurl</b>: The url to browse the newly imported assay run.</li>
     *             <li><b>assayId</b>: The assay id.</li>
     *             <li><b>batchId</b>: The previously existing or newly created batch id.</li>
     *             <li><b>runId</b>: The newly created run id.</li>
     *         </ul>
     *     </li>
     *     <li><b>response</b>: The XMLHttpResponseObject used to submit the request.</li>
     * </ul>
     * @param {Function} config.failure The error callback function will be called with the following arguments:
     * <ul>
     *     <li><b>errorInfo:</b> an object describing the error with the following fields:
     *         <ul>
     *             <li><b>exception:</b> the exception message</li>
     *             <li><b>exceptionClass:</b> the Java class of the exception thrown on the server</li>
     *             <li><b>stackTrace:</b> the Java stack trace at the point when the exception occurred</li>
     *         </ul>
     *     </li>
     * <li><b>response:</b> the XMLHttpResponseObject used to submit the request.</li>
     *
     * @example Import a file that has been previously uploaded to the server:
     *         LABKEY.Assay.importRun({
     *             assayId: 3,
     *             name: "new run",
     *             runFilePath: "assaydata/2017-05-10/datafile.tsv",
     *             success: function (json, response) {
     *                 window.location = json.successurl;
     *             },
     *             failure: error (json, response) {
     *             }
     *         });
     *
     * @example Import JSON array of data rows:
     *         LABKEY.Assay.importRun({
     *             assayId: 3,
     *             name: "new run",
     *             dataRows: [{
     *                  sampleId: "S-1",
     *                  dataField: 100
     *             },{
     *                  sampleId: "S-2",
     *                  dataField: 200
     *             }]
     *             success: function (json, response) {
     *                 window.location = json.successurl;
     *             },
     *             failure: error (json, response) {
     *             }
     *         });
     *
     * @example Here is an example of retrieving one or more File objects from a form <code>&lt;input&gt;</code>
     * element and submitting them together to create a new run.
     * &lt;input id='myfiles' type='file' multiple>
     * &lt;a href='#' onclick='doSubmit()'>Submit&lt;/a>
     * &lt;script>
     *     function doSubmit() {
     *         LABKEY.Assay.importRun({
     *             assayId: 3,
     *             name: "new run",
     *             properties: {
     *                 "Run Field": "value"
     *             },
     *             batchProperties: {
     *                 "Batch Field": "value"
     *             },
     *             files: [ document.getElementById('myfiles') ],
     *             success: function (json, response) {
     *                 window.location = json.successurl;
     *             },
     *             failure: error (json, response) {
     *             }
     *         });
     *     }
     * &lt;/script>
     *
     * @example Alternatively, you may use an HTML form to submit the multipart/form-data without using the JavaScript API.
     * &lt;form action='./assay.importRun.api' method='POST' enctype='multipart/form-data'>
     *     &lt;input name='assayId' type='text' />
     *     &lt;input name='name' type='text' />
     *     &lt;input name='file' type='file' />
     *     &lt;input name='submit' type='submit' />
     * &lt;/form>
     */
    impl.importRun = function(config) {
        if (!window.FormData)
            throw new Error("modern browser required");

        if (!config.assayId)
            throw new Error("assayId required");

        var files = [];
        if (config.files) {
            for (var i = 0; i < config.files.length; i++) {
                var f = config.files[i];
                if (f instanceof window.File) {
                    files.push(f);
                }
                else if (f.tagName == "INPUT") {
                    for (var j = 0; j < f.files.length; j++) {
                        files.push(f.files[j]);
                    }
                }
            }
        }

        if (files.length == 0 && !config.runFilePath && !config.dataRows)
            throw new Error("At least one of 'file', 'runFilePath', or 'dataRows' is required");

        if ((files.length > 0 ? 1 : 0) + (config.runFilePath ? 1 : 0) + (config.dataRows ? 1 : 0) > 1)
            throw new Error("Only one of 'file', 'runFilePath', or 'dataRows' is allowed");

        var formData = new FormData();
        formData.append("assayId", config.assayId);
        if (config.name)
            formData.append("name", config.name);
        if (config.comment)
            formData.append("comment", config.comment);
        if (config.batchId)
            formData.append("batchId", config.batchId);

        if (config.properties) {
            for (var key in config.properties) {
                if (LABKEY.Utils.isObject(config.properties[key]))
                    formData.append("properties['" + key + "']", JSON.stringify(config.properties[key]));
                else
                    formData.append("properties['" + key + "']", config.properties[key]);
            }
        }

        if (config.batchProperties) {
            for (var key in config.batchProperties) {
                if (LABKEY.Utils.isObject(config.batchProperties[key]))
                    formData.append("batchProperties['" + key + "']", JSON.stringify(config.batchProperties[key]));
                else
                    formData.append("batchProperties['" + key + "']", config.batchProperties[key]);
            }
        }

        if (config.dataRows)
            formData.append("dataRows", JSON.stringify(config.dataRows));

        if (config.plateMetadata)
            formData.append("plateMetadata", JSON.stringify(config.plateMetadata));

        if (config.runFilePath)
            formData.append("runFilePath", config.runFilePath);

        if (files && files.length > 0) {
            formData.append("file", files[0]);
            for (var i = 1; i < files.length; i++) {
                formData.append("file" + i, files[i]);
            }
        }

        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL("assay", "importRun.api", config.containerPath),
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope, false),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
            form: formData
        });
    };

    return impl;

}(LABKEY.Assay || new function() { return {}; });