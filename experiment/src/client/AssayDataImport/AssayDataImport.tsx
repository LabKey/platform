import * as React from 'react';
import {Panel} from "react-bootstrap";
import {Map} from 'immutable';
import {Alert, FileAttachmentForm, LoadingSpinner, AssayProtocolModel, fetchProtocol} from "@glass/base";
import {ActionURL, AssayDOM} from '@labkey/api'

interface Props {}

interface State {
    assayId: number
    assayModel: AssayProtocolModel
    error: string
}

export class App extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);

        const assayId = ActionURL.getParameter('assayId');

        this.state = {
            assayId,
            assayModel: undefined,
            error: assayId ? undefined : 'Missing required property: assayId.'
        }
    }

    componentWillMount() {
        fetchProtocol(this.state.assayId)
            .then((assayModel) => {
                if (assayModel.providerName !== 'General') {
                    this.setErrorMsg('This assay run upload page is currently only supported for the General assay provider.');
                }
                else {
                    this.setState(() => ({assayModel}));
                }
            })
            .catch((error) => {
                this.setErrorMsg(error);
            });
    }

    setErrorMsg(error: string) {
        this.setState(() => ({error}));
    }

    handleFileSubmit = (files: Map<string, File>) => {
        this.setErrorMsg(undefined);

        AssayDOM.importRun({
            assayId: this.state.assayId,
            files: files.toArray(),
            success: (response) => {
                window.location = response.successurl;
            },
            failure: (response) => {
                this.setErrorMsg(response.exception);
            }
        });
    };

    handleFileChange = (files: Map<string, File>) => {
        this.setErrorMsg(undefined);
    };

    handleFileRemoval = (attachmentName: string) => {
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

    renderAssayHeader() {
        const { assayModel } = this.state;

        if (assayModel) {
            return (
                <Panel>
                    <Panel.Heading>Assay Properties</Panel.Heading>
                    <Panel.Body>
                        <p>Name: {assayModel.name}</p>
                        <p>Description: {assayModel.description}</p>
                    </Panel.Body>
                </Panel>
            )
        }
    }

    renderRunDataUpload() {
        const { assayModel } = this.state;

        if (assayModel) {
            return (
                <Panel>
                    <Panel.Heading>Data Upload</Panel.Heading>
                    <Panel.Body>
                        <FileAttachmentForm
                            acceptedFormats={".csv, .tsv, .txt, .xls, .xlsx"}
                            showAcceptedFormats={true}
                            allowDirectories={false}
                            allowMultiple={false}
                            label={'Import from Local File'}
                            showPreviewGrid={true}
                            previewRowCount={3}
                            previewHeader={'Previewing Data for Import'}
                            previewInfoMsg={'If the data does not look as expected, check you source file for errors and re-upload.'}
                            showButtons={true}
                            submitText={'Save and Finish'}
                            onFileChange={this.handleFileChange}
                            onFileRemoval={this.handleFileRemoval}
                            onSubmit={this.handleFileSubmit}
                            onCancel={this.handleCancel}
                        />
                    </Panel.Body>
                </Panel>
            )
        }
    }

    render() {
        const { error, assayModel } = this.state;

        if (!assayModel && !error) {
            return (
                <LoadingSpinner/>
            )
        }

        return (
            <>
                {this.renderError()}
                {this.renderAssayHeader()}
                {this.renderRunDataUpload()}
            </>
        )
    }
}