import * as React from 'react';
import {Panel} from "react-bootstrap";
import {Map} from 'immutable';
import {FileAttachmentForm} from "@glass/base";

export class App extends React.Component<any, any> {

    handleFileSubmit = (files: Map<string, File>) => {
        console.log('handleFileSubmit', files.toJS());
    };

    handleFileChange = (files: Map<string, File>) => {
        console.log('handleFileChange', files.toJS());
    };

    handleFileRemoval = (attachmentName: string) => {
        console.log('handleFileRemoval', attachmentName);
    };

    render() {
        return (
            <>
                <Panel>
                    <Panel.Heading>File Upload and Preview</Panel.Heading>
                    <Panel.Body>
                        <FileAttachmentForm
                            acceptedFormats={".csv, .tsv, .txt, .xls, .xlsx"}
                            showAcceptedFormats={true}
                            allowDirectories={false}
                            allowMultiple={false}
                            label={"Upload a file:"}
                            showButtons={true}
                            showPreviewGrid={true}
                            previewRowCount={3}
                            onFileChange={this.handleFileChange}
                            onFileRemoval={this.handleFileRemoval}
                            onSubmit={this.handleFileSubmit}
                        />
                    </Panel.Body>
                </Panel>
            </>
        )
    }
}