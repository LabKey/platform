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

import React, {PureComponent} from "react";
import {LoadingSpinner} from "@labkey/components";
import { ActionURL, Ajax, Utils } from "@labkey/api";


export class App extends PureComponent<any, any> {
    constructor(props) {
        super(props);
    }

    render() {
        const datasetId = 5004;
        Ajax.request({
            url: ActionURL.buildURL('study', 'GetDataset'),
            method: 'GET',
            params: {datasetId},
            scope: this,
            success: Utils.getCallbackWrapper((data) => {
                console.log("success", data);
                // resolve(console.log("success", data));
            }),
            failure: Utils.getCallbackWrapper((error) => {
                console.log("failure", error);
                // reject(error);
            })
        });

        return (
            <LoadingSpinner/>
        )
    }
}