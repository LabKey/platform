import * as React from 'react';
import { Map, List } from 'immutable';

import { ActionURL, Ajax } from '@labkey/api'

import {TabbedImportPanel} from "./TabbedImportPanel";
import {ActionCenter} from "./ActionCenter";

const PREVIEW_ROW_COUNT = 3;

interface DataUploadPanelProps
{
    //leaving room for Assay id, etc.
}

export class DataUploadPanel extends React.Component<DataUploadPanelProps, any> {

    constructor(props) {
        super(props);
        this.handleFileChange = this.handleFileChange.bind(this);
        this.handleFileRemoval = this.handleFileRemoval.bind(this);
        this.handleFileSubmit = this.handleFileSubmit.bind(this);

        this.state = {
            hasDataToDisplay: false,
            actionsForUser: null
        }
    }

    handleFileSubmit(files: Map<string, File>): void {

    }

    handleFileChange(files: Map<string, File>): void {
        this.getPreviewDataFromFile(files.get(files.keys().next().value)) // just take the first file, since we only support 1 file at this time
            .then(previewData => {
                this.setState({
                    previewData,
                    actionsForUser: 'You need to do something' //TODO derive from future inference work
                });
            })
            .catch(reason => console.error(reason));
    }

    handleFileRemoval(attachmentName: string) {
        this.setState({
            previewData: null,
            actionsForUser: null
        });
    }

    uploadDataFileAsExpData(file: File) : Promise<any> {
        return new Promise((resolve, reject) => {
            let form = new FormData();
            form.append('file', file);
            Ajax.request({
                url: ActionURL.buildURL('assay', 'assayFileUpload'),
                method: 'POST',
                form,
                success: (response) => {
                    resolve(JSON.parse(response.responseText)); //gross
                },
                failure: (response) => {
                    reject("There was a problem uploading the data file for data preview.");
                    console.error(response);
                }
            });
        })
    }

    getContentFromExpData(expData: any) : Promise<any> { //todo define Exp.Data config interface
        return new Promise((resolve, reject) => {
            Ajax.request(
                {
                    url: ActionURL.buildURL("experiment", "showFile"),
                    method: 'GET',
                    params: {rowId: expData.id, format: 'jsonTSV'},
                    success: (response) => {
                        resolve(JSON.parse(response.responseText));
                    },
                    failure: (response) => {
                        reject("There was a problem getting information about the data file.");
                        console.error(response);
                    }
                });
        })
    }

    getPreviewDataFromFile(file: File) : Promise<List<Map<string, any>>> {
        return new Promise((resolve, reject) => {
            this.uploadDataFileAsExpData(file)
                .then((response) => {
                    return this.getContentFromExpData(response)
                }).then((response) => {
                    if (Array.isArray(response.sheets) && response.sheets.length) {
                        if (response.sheets[0].data) {
                            resolve(this.convertRowDataIntoPreviewData(response.sheets[0].data));
                        } else {
                            reject('There is no data in the file or in the first sheet of the file.');
                        }
                    }
            })
            .catch(reason => {
                console.error(reason);
                reject(reason);
            })
        })
    }

    // Converts the 2D array returned by Exp.ShowFile into a list of row maps that the grid understands
    convertRowDataIntoPreviewData(rowTsv: any): List<Map<string, any>> {
        const headerRow = rowTsv.shift();
        let rows = [];
        for (let i = 0; i < Math.min(PREVIEW_ROW_COUNT, rowTsv.length); i++) {
            let m = {};
            headerRow.forEach((column, j) => {
                m[column] = rowTsv[i][j];
            });
            rows.push(Map(m));
        }
        return List(rows);
    }

    render(): React.ReactNode {

        const { previewData, actionsForUser } = this.state;
        return (
            <>
                <TabbedImportPanel
                    handleFileSubmit={this.handleFileSubmit}
                    handleFileChange={this.handleFileChange}
                    handleFileRemoval={this.handleFileRemoval}
                    fileUploadPreviewData={previewData}
                />
                <ActionCenter actions={actionsForUser}/>
            </>
        );
    }
}


