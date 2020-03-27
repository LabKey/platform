/*
 * Copyright (c) 2020 LabKey Corporation
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

import {buildURL, DomainDesign} from "@labkey/components";
import {Ajax, Utils} from "@labkey/api";

export function fetchQueryMetadata(schemaName: string, queryName: string): Promise<any> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: buildURL('query', 'getQueryEditorMetadata.api'),
            method: 'POST',
            success: Utils.getCallbackWrapper((data) => resolve(data)),
            failure: Utils.getCallbackWrapper((error) => reject(error)),
            params : {
                schemaName : schemaName,
                queryName : queryName
            }
        });
    });
}

export function saveQueryMetadata(domain: DomainDesign, schemaName: string, userDefinedQuery: boolean): Promise<void> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: buildURL('query', 'saveQueryMetadata.api'),
            method: 'POST',
            success: () => resolve(),
            failure: Utils.getCallbackWrapper((error) => reject(error)),
            jsonData: {
                domain: DomainDesign.serialize(domain),
                schemaName: schemaName,
                userDefinedQuery: userDefinedQuery
            }
        });
    });
}

export function resetQueryMetadata(schemaName: string, queryName: string): Promise<DomainDesign>{
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: buildURL('query', 'resetQueryMetadata.api'),
            method: 'POST',
            success: Utils.getCallbackWrapper((data) => resolve(DomainDesign.create(data, undefined))),
            failure: Utils.getCallbackWrapper((error) => reject(error)),
            params : {
                schemaName : schemaName,
                queryName : queryName
            }
        });
    });
}
