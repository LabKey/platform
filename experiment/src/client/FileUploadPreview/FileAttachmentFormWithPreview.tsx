import * as React from 'react';
import {Map, List} from 'immutable';
import {Grid} from '@glass/grid';
import {LoadingSpinner} from "@glass/base";

import {FileAttachmentForm} from "./FileAttachmentForm";
import {convertRowDataIntoPreviewData, getContentFromExpData, uploadDataFileAsExpData} from "./actions";

interface FileAttachmentFormWithPreviewProps
{
    handleErrors: (errorMessage: string) => any
    previewRowCount: number
}

type FileAttachmentFormWithPreviewState = {
    previewData?: List<Map<string, any>>
    previewStatus: string
}

export class FileAttachmentFormWithPreview extends React.Component<FileAttachmentFormWithPreviewProps, FileAttachmentFormWithPreviewState> {

    static defaultProps = {
        previewRowCount: 3
    };

    constructor(props) {
        super(props);

        this.handleFileChange = this.handleFileChange.bind(this);
        this.handleFileRemoval = this.handleFileRemoval.bind(this);
        this.handleFileSubmit = this.handleFileSubmit.bind(this);

        this.state = {
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
        const { handleErrors } = this.props;
        if (handleErrors) {
            handleErrors(error);
        }
    }

    renderFileUploadPanel() {
        const { previewData} = this.state;

        return (
            <>
                <FileAttachmentForm
                    acceptedFormats={".tsv, .xlsx, .xls, .csv"}
                    showAcceptedFormats={previewData === null}
                    allowDirectories={false}
                    allowMultiple={false}
                    showLabel={false}
                    onFileChange={this.handleFileChange}
                    onFileRemoval={this.handleFileRemoval}
                    onSubmit={this.handleFileSubmit}
                />
            </>
        )
    }

    renderPreviewGrid() {
        const { previewData, previewStatus } = this.state;

        if (previewData && previewData.size) {
            const numRows = previewData.size;

            return (
                <>
                    <div className={"margin-top"}>
                        <strong>Grid Preview:</strong>
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
        return (
            <>
                {this.renderFileUploadPanel()}
                {this.renderPreviewGrid()}
            </>
        )
    }
}


