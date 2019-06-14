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
import {Map, List} from "immutable";
import {Ajax, ActionURL} from "@labkey/api";

export function getContentFromExpData(expData: any) : Promise<any> { //todo define Exp.Data config interface
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: ActionURL.buildURL("experiment", "showFile"),
            method: 'GET',
            params: {rowId: expData.id, format: 'jsonTSV'},
            success: (response) => {
                resolve(JSON.parse(response.responseText));
            },
            failure: (response) => {
                reject("There was a problem getting preview information about the data file.");
                console.error(response);
            }
        });
    });
}

export function uploadDataFileAsExpData(file: File) : Promise<any> {
    return new Promise((resolve, reject) => {
        let form = new FormData();
        form.append('file', file);

        Ajax.request({
            url: ActionURL.buildURL('assay', 'assayFileUpload'),
            method: 'POST',
            form,
            success: (response) => {
                resolve(JSON.parse(response.responseText));
            },
            failure: (response) => {
                reject("There was a problem uploading the data file for data preview.");
                console.error(response);
            }
        });
    })
}

// Converts the 2D array returned by Exp.ShowFile into a list of row maps that the grid understands
export function convertRowDataIntoPreviewData(rowTsv: any, previewRowCount: number): List<Map<string, any>> {
    const headerRow = rowTsv.shift();
    let rows = List<Map<string, any>>();

    for (let i = 0; i < Math.min(previewRowCount, rowTsv.length); i++) {
        let m = {};
        headerRow.forEach((column, j) => {
            m[column] = rowTsv[i][j];
        });

        rows = rows.push(Map(m));
    }

    return rows;
}