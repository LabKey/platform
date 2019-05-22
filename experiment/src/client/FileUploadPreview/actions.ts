/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
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