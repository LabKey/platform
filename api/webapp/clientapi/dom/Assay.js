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
LABKEY.Assay = new function(impl) {

    /**
     * Documentation specified in core/Assay.js -- search for "@name importRun"
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