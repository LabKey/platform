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
import * as React from 'react';
import {Panel} from "react-bootstrap";
import {Map, List} from 'immutable';
import {LoadingSpinner, FileAttachmentForm, Grid} from "@glass/base";

import {convertRowDataIntoPreviewData, getContentFromExpData, uploadDataFileAsExpData} from "./actions";

interface FileAttachmentFormWithPreviewProps
{
    previewRowCount: number
}

type FileAttachmentFormWithPreviewState = {
    errorMessage?: string
    previewData?: List<Map<string, any>>
    previewStatus?: string
}

export class App extends React.Component<FileAttachmentFormWithPreviewProps, FileAttachmentFormWithPreviewState> {

    static defaultProps = {
        previewRowCount: 3
    };

    constructor(props) {
        super(props);

        this.handleFileChange = this.handleFileChange.bind(this);
        this.handleFileRemoval = this.handleFileRemoval.bind(this);
        this.handleFileSubmit = this.handleFileSubmit.bind(this);

        this.state = {
            errorMessage: null,
            previewData: null,
            previewStatus: null
        }
    }

    updatePreviewStatus(status: string): void {
        this.setState((state) => {
            return {
                previewStatus: status
            };
        });
    }

    handleFileSubmit(files: Map<string, File>): void {
        // Not yet implemented
    }

    handleFileChange(files: Map<string, File>): void {
        this.updatePreviewStatus("Uploading file...");

        // just take the first file, since we only support 1 file at this time
        const file = files.get(files.keys().next().value);

        uploadDataFileAsExpData(file)
            .then((response) => {
                this.handleFileAsExpData(response);
            })
            .catch(reason => {
                this.updatePreviewStatus(null);
                this.updateErrors(reason);
            });
    }

    handleFileAsExpData(expData: any): void {
        this.updatePreviewStatus("Fetching file preview...");

        getContentFromExpData(expData)
            .then((response) => {
                this.handleContentFromExpData(response);
            })
            .catch(reason => {
                this.updatePreviewStatus(null);
                this.updateErrors(reason);
            });
    }

    handleContentFromExpData(response: any): void {
        this.updatePreviewStatus(null);

        if (Array.isArray(response.sheets) && response.sheets.length > 0) {
            const previewData = convertRowDataIntoPreviewData(response.sheets[0].data, this.props.previewRowCount);

            this.setState((state) => {
                return {
                    previewData
                }
            });

            this.updateErrors(null);
        }
        else {
            this.updateErrors('1. There is no data in the file to preview.');
        }
    }

    handleFileRemoval(attachmentName: string) {
        this.updatePreviewStatus(null);
        this.updateErrors(null);

        this.setState((state) => {
            return {
                previewData: null
            }
        });
    }

    updateErrors(error: string) {
        this.setState((state) => {
            return {
                errorMessage: error
            }
        });
    }

    renderFileUploadPanel() {
        const { previewData} = this.state;

        return (
            <>
                <FileAttachmentForm
                    acceptedFormats={".csv, .tsv, .txt, .xls, .xlsx"}
                    showAcceptedFormats={previewData === null}
                    allowDirectories={false}
                    allowMultiple={false}
                    label={"Upload a file:"}
                    onFileChange={this.handleFileChange}
                    onFileRemoval={this.handleFileRemoval}
                    onSubmit={this.handleFileSubmit}
                />
            </>
        )
    }

    renderPreviewGrid() {
        const { previewData, previewStatus } = this.state;

        if (previewData) {
            const numRows = previewData.size;

            return (
                <>
                    <div className={"margin-top"}>
                        <strong>File preview:</strong>
                    </div>
                    <p className={'margin-top'}>
                        The {numRows === 1 ? 'only row ' : 'first ' + numRows + ' rows '} of your data
                        file {numRows === 1 ? 'is' : 'are'} shown below.
                    </p>
                    <Grid
                        data={previewData}
                    />
                </>
            )
        }
        else if (previewStatus !== null) {
            return (
                <div className={"margin-top"}>
                    <LoadingSpinner msg={previewStatus}/>
                </div>
            )
        }
        else {
            return null
        }
    }

    render() {
        const { errorMessage } = this.state;

        return (
            <>
                {errorMessage && <div className={'alert alert-danger'}>{errorMessage + ' You may want to try a different file.'}</div>}
                <Panel>
                    <Panel.Heading>File Upload and Preview</Panel.Heading>
                    <Panel.Body>
                        {this.renderFileUploadPanel()}
                        {this.renderPreviewGrid()}
                    </Panel.Body>
                </Panel>
            </>
        )
    }
}