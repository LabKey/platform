/*
 * Copyright (c) 2018-2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import { Button } from 'react-bootstrap'
import { Map } from 'immutable'

import { FileAttachmentContainer } from './FileAttachmentContainer'

interface FileAttachmentFormProps {
    acceptedFormats?: string // comma-separated list of allowed extensions i.e. '.png, .jpg, .jpeg'
    allowDirectories?: boolean
    allowMultiple?: boolean
    cancelText?: string
    label?: string
    onCancel?: () => any
    onFileChange?: (files: Map<string, File>) => any
    onFileRemoval?: (attachmentName: string) => any
    onSubmit?: (files: Map<string, File>) => any
    isSubmitting?: boolean
    showButtons?: boolean
    showLabel?: boolean
    showProgressBar?: boolean
    submitText?: string
}

interface State {
    attachedFiles: Map<string, File>
}

export class FileAttachmentForm extends React.Component<FileAttachmentFormProps, State> {

    static defaultProps = {
        acceptedFormats: '',
        allowDirectories: true,
        allowMultiple: false,
        cancelText: 'Cancel',
        label: 'Select file or drag and drop here',
        onCancel: undefined,
        onSubmit: undefined,
        showButtons: false,
        showLabel: true,
        showProgressBar: false,
        submitText: 'Upload'
    };

    constructor(props?: FileAttachmentFormProps) {
        super(props);

        this.handleFileChange = this.handleFileChange.bind(this);
        this.handleFileRemoval = this.handleFileRemoval.bind(this);
        this.handleSubmit = this.handleSubmit.bind(this);

        this.state = {
            attachedFiles: Map<string, File>()
        };
    }

    handleFileChange(fileList: {[key: string]: File}) {
        const { onFileChange } = this.props;

        this.setState({
            attachedFiles: this.state.attachedFiles.merge(fileList)
        }, () => {
            if (onFileChange) {
                onFileChange(this.state.attachedFiles)
            }
        });
    }

    handleFileRemoval(attachmentName: string) {
        const { onFileRemoval } = this.props;

        this.setState({
            attachedFiles: this.state.attachedFiles.remove(attachmentName)
        }, () => {
            if (onFileRemoval) {
                onFileRemoval(attachmentName);
            }
        });
    }

    handleSubmit() {
        const { onSubmit } = this.props;

        if (onSubmit)
            onSubmit(this.state.attachedFiles);
        // clear out attached files once they have been submitted.
        this.setState({
            attachedFiles: Map<string, File>()
        });
    }

    renderButtons() { //TODO this is copied from biologics, requirements may change in future UX stories
        const { cancelText, onCancel, submitText } = this.props;

        return (
            <div className="row top-spacing bottom-spacing">
                <div className="col-md-7">
                    <Button
                        onClick={onCancel}
                        bsStyle="default"
                        title={cancelText}>
                        {cancelText}
                    </Button>
                </div>
                <div className="col-md-5">
                    <div className="pull-right">
                        <Button
                            onClick={this.handleSubmit}
                            bsStyle="success"
                            disabled={this.state.attachedFiles.size == 0}
                            title={submitText}>
                            {submitText}
                        </Button>
                    </div>
                </div>
            </div>
        )
    }

    render() {
        const {
            acceptedFormats,
            allowDirectories,
            allowMultiple,
            label,
            showButtons
        } = this.props;

        const { attachedFiles } = this.state;

        return (
            <>

                <FileAttachmentContainer
                    acceptedFormats={acceptedFormats}
                    allowDirectories={allowDirectories}
                    handleChange={this.handleFileChange}
                    handleRemoval={this.handleFileRemoval}
                    allowMultiple={allowMultiple}
                    label={label}/>
                {acceptedFormats && attachedFiles.size === 0 && (
                    <div className={"margin-top"}>
                        <strong>Supported formats include: </strong>{acceptedFormats}
                    </div>
                )}
                {showButtons && this.renderButtons()}
            </>
        )
    }
}