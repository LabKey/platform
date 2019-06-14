/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {Ajax, Utils} from "@labkey/api";
import {buildURL} from "@glass/base";

import {AssayProtocolModel} from "./models";

// TODO remove as this is now in @glass/base
export function fetchProtocol(protocolId: number): Promise<AssayProtocolModel> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: buildURL('assay', 'getProtocol.api', { protocolId }),
            success: Utils.getCallbackWrapper((data) => {
                if (data.data) {
                    resolve(new AssayProtocolModel(data.data));
                }
                else {
                    reject('Unable to find assay protocol for id: ' + protocolId + '.');
                }
            }),
            failure: Utils.getCallbackWrapper((error) => {
                reject(error ? error.exception: 'An unknown error occurred getting the assay protocol data from the server.');
            })
        })
    });
}