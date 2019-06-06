import * as React from 'react';
import {Panel} from "react-bootstrap";
import {Map} from 'immutable';
import {Alert, FileAttachmentForm} from "@glass/base";

interface Props {}

interface State {
    error: string
}

export class App extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);

        this.state = {
            error: undefined
        }
    }

    setErrorMsg(error: string) {
        this.setState(() => ({error}));
    }

    handleFileChange = (files: Map<string, File>) => {
        console.log('handleFileChange', files.toJS());
        this.setErrorMsg(undefined);
    };

    handleFileRemoval = (attachmentName: string) => {
        console.log('handleFileRemoval', attachmentName);
        this.setErrorMsg(undefined);
    };

    handleCancel = () => {
        window.history.back();
    };

    renderError() {
        const { error } = this.state;
        if (error) {
            return <Alert>{error}</Alert>
        }
    }

    render() {
        return (
            <>
                {this.renderError()}
                <Panel>
                    <Panel.Heading>Data Upload</Panel.Heading>
                    <Panel.Body>
                        <FileAttachmentForm
                            acceptedFormats={".csv, .tsv, .txt, .xls, .xlsx"}
                            showAcceptedFormats={true}
                            allowDirectories={false}
                            allowMultiple={false}
                            label={"Upload a file:"}
                            onFileChange={this.handleFileChange}
                            onFileRemoval={this.handleFileRemoval}
                            onCancel={this.handleCancel}
                            previewGridProps={{
                                previewCount: 3
                            }}
                        />
                    </Panel.Body>
                </Panel>
            </>
        )
    }
}