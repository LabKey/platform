/*
 * Copyright (c) 2017-2018 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import { Button } from 'react-bootstrap'
import classNames from 'classnames'
import { Map } from 'immutable'
import { Utils } from '@labkey/api'

import { cancelEvent } from './events'
import { Progress } from './Progress'

interface FileAttachmentFormProps {
    acceptedFormats?: string // comma-separated list of allowed extensions i.e. '.png, .jpg, .jpeg'
    allowDirectories?: boolean
    allowMultiple?: boolean
    cancelText?: string
    label?: string
    labelLong?: string
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
        allowMultiple: true,
        cancelText: 'Cancel',
        label: 'Attachments',
        labelLong: 'Select file or drag and drop here',
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

    determineFileSize(): number {
        const { attachedFiles } = this.state;

        return attachedFiles.reduce((total, file) =>( total += file.size), 0);
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

    renderButtons() {
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
            labelLong,
            //model, TODO figure out model isSubmitting replacement
            showButtons,
            showLabel,
            showProgressBar,
            isSubmitting
        } = this.props;

        const { attachedFiles } = this.state;

        return (
            <>
                <span className="translator--toggle__wizard">
                    <FileAttachmentContainer
                        acceptedFormats={acceptedFormats}
                        allowDirectories={allowDirectories}
                        handleChange={this.handleFileChange}
                        handleRemoval={this.handleFileRemoval}
                        allowMultiple={allowMultiple}
                        labelLong={labelLong}/>
                </span>
                {showProgressBar && (
                    <Progress
                    estimate={this.determineFileSize() * .1}
                    modal={true}
                    title="Uploading"
                    toggle={isSubmitting}/> //TODO handle submission
                )}
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

interface FileAttachmentContainerProps {
    acceptedFormats?: string // comma separated list of allowed extensions i.e. '.png, .jpg, .jpeg'
    allowMultiple: boolean
    allowDirectories: boolean
    handleChange?: any
    handleRemoval?: any

    labelLong?: string
}

interface FileAttachmentContainerState {
    errorMsg?: string
    files?: {[key:string]: File}
    isHover?: boolean
}

export class FileAttachmentContainer extends React.Component<FileAttachmentContainerProps, FileAttachmentContainerState> {

    fileInput: React.RefObject<HTMLInputElement>;

    constructor(props?: FileAttachmentContainerProps) {
        super(props);

        this.areValidFileTypes = this.areValidFileTypes.bind(this);
        this.handleChange = this.handleChange.bind(this);
        this.handleDrag = this.handleDrag.bind(this);
        this.handleDrop = this.handleDrop.bind(this);
        this.handleLeave = this.handleLeave.bind(this);

        this.fileInput = React.createRef();

        this.state = {
            files: {},
            isHover: false
        }
    }

    areValidFileTypes(fileList: FileList, transferItems?: DataTransferItemList) {
        const { acceptedFormats, allowDirectories } = this.props;

        if (!acceptedFormats && allowDirectories) {
            return true;
        }

        let isValid: boolean = true;
        const acceptedFormatArray: Array<string> = acceptedFormats.replace(/\s/g, '').split(',');

        Array.from(fileList).forEach((file, index) => {
            if (transferItems && transferItems[index].webkitGetAsEntry().isDirectory) {
                if (!allowDirectories) {
                    isValid = false;
                    this.setState({
                        errorMsg: 'Folders not yet supported.',
                        isHover: false
                    });
                    return false;
                }
            }
            else if (acceptedFormats) {
                const dotIndex = file.name.lastIndexOf('.');
                let extension = file.name.slice(dotIndex);
                if (acceptedFormatArray.indexOf(extension) < 0) {
                    isValid = false;
                    this.setState({
                        errorMsg: 'Invalid file type: ' + extension + '. Valid types are ' + acceptedFormats,
                        isHover: false
                    });
                    return false;
                }
            }
        });
        return isValid;
    }

    handleChange(evt: React.ChangeEvent<HTMLInputElement>) {
        cancelEvent(evt);
        if (evt.currentTarget && evt.currentTarget.files) {
            this.handleFiles(evt.currentTarget.files);
        }
    }

    handleDrag(evt: React.DragEvent<HTMLLabelElement>) {
        const { isHover } = this.state;

        cancelEvent(evt);
        if (!isHover) {
            this.setState({isHover: true});
        }
    }

    handleDrop(evt: React.DragEvent<HTMLLabelElement>) {
        cancelEvent(evt);
        if (evt.dataTransfer && evt.dataTransfer.files) {
            this.handleFiles(evt.dataTransfer.files, evt.dataTransfer.items);
        }
    }

    handleFiles(fileList: FileList, transferItems?: DataTransferItemList) {
        const { allowMultiple, handleChange } = this.props;

        if (!allowMultiple && fileList.length > 1) {
            this.setState({
                errorMsg: 'Only one file allowed',
                isHover: false
            });
            return;
        }

        let isValid = this.areValidFileTypes(fileList, transferItems);

        let files = this.state.files;

        if (isValid) {
            // iterate through the file list and set the names as the object key
            let newFiles = Object.keys(fileList).reduce((prev, next) => {
                const file = fileList[next];
                prev[file.name] = file;
                return prev;
            }, {});

            files = Object.assign({}, newFiles, this.state.files);
            this.setState({
                files,
                errorMsg: undefined,
                isHover: false
            });
        }

        if (Utils.isFunction(handleChange)) {
            handleChange(files);
        }
    }

    handleLeave(evt: React.DragEvent<HTMLLabelElement>) {
        const { isHover } = this.state;

        cancelEvent(evt);

        if (isHover) {
            this.setState({isHover: false});
        }
    }

    handleRemove(name: string) {
        const { handleRemoval } = this.props;

        const files = Object.keys(this.state.files)
            .filter(fileName => fileName !== name)
            .reduce((prev, next) => {
                const file = this.state.files[next];
                prev[file.name] = file;
                return prev;
            }, {});

        // NOTE: This will clear the field entirely so multiple file support
        // will need to account for this and rewrite this clearing mechanism
        if (this.fileInput.current) {
            this.fileInput.current.value = '';
        }

        this.setState({files});

        if (Utils.isFunction(handleRemoval)) {
            handleRemoval(name);
        }
    }

    renderErrorDetails() {
        const { errorMsg } = this.state;

        if (errorMsg !== '' && errorMsg !== undefined) {
            return (
                <div className="has-error">
                    <span className="error-message help-block">{errorMsg}</span>
                </div>
            )
        }
    }

    render() {
        const { acceptedFormats, allowMultiple, labelLong } = this.props;
        const { files } = this.state;

        return (
            <div>
                <div className="file-upload--container" style={{
                    display: (!allowMultiple && Object.keys(files).length > 0) ? 'none' : 'block'}}>
                    <label
                        className={classNames("file-upload--label", {'file-upload__is-hover': this.state.isHover})}
                        htmlFor="fileUpload"
                        onDragEnter={this.handleDrag}
                        onDragLeave={this.handleLeave}
                        onDragOver={this.handleDrag}
                        onDrop={this.handleDrop}>
                        <i className="fa fa-cloud-upload fa-2x cloud-logo" aria-hidden="true"/>
                        {labelLong}
                    </label>
                    <input
                        accept={acceptedFormats}
                        className="file-upload--input"
                        id="fileUpload"
                        multiple={allowMultiple}
                        name="fileUpload"
                        onChange={this.handleChange}
                        ref={this.fileInput}
                        type="file"/>
                </div>

                {this.renderErrorDetails()}

                {Object.keys(files).map((key: string) => {
                    const file = files[key];
                    return (
                        <div key={key} className="attached-file--container">
                            <span
                                className="fa fa-times-circle file-upload__remove--icon"
                                onClick={() => this.handleRemove(file.name)}
                                title={"Remove file"}/>
                            <span className="fa fa-file-text" style={{
                                color: 'darkgray',
                                fontSize: '20px',
                                marginRight: '7px',
                                marginBottom: '10px'}}/>
                            {file.name}
                        </div>
                    )
                })}
            </div>
        )
    }
}