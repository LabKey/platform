import * as React from 'react';
import {Panel} from "react-bootstrap";
import {Map, List} from 'immutable';
import {Alert, Cards, FileAttachmentForm, LoadingSpinner, AssayDefinitionModel, fetchAllAssays} from "@glass/base";
import {ActionURL, AssayDOM, Utils} from '@labkey/api'

interface Props {}

interface State {
    selected: number,
    assays: List<AssayDefinitionModel>
    error: string
    warning: string
}

export class App extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);

        this.state = {
            selected: undefined,
            assays: undefined,
            error: undefined,
            warning: undefined
        }
    }

    componentWillMount() {
        fetchAllAssays('General')
            .then((assays) => {
                this.setState(() => ({assays}));
                this.selectInitialAssay();
            })
    }

    selectInitialAssay() {
        const { assays } = this.state;
        const urlRowId = ActionURL.getParameter('rowId');
        const rowId = urlRowId ? parseInt(urlRowId) : undefined;

        if (assays && Utils.isNumber(rowId)) {
            let selected;
            assays.forEach((assay, i) => {
                if (assay.id === rowId) {
                    selected = i;
                }
            });

            if (selected !== undefined) {
                this.setState(() => ({selected}));
            }
            else {
                this.setState(() => ({
                    warning: 'Unable to find an available General type assay for rowId ' + rowId + '.'
                }));
            }
        }
    }

    getSelectedAssay(): AssayDefinitionModel {
        const { selected, assays } = this.state;

        if (assays && selected !== undefined) {
            return assays.get(selected);
        }
    }

    handleFileSubmit = (files: Map<string, File>) => {
        const selectedAssay = this.getSelectedAssay();

        if (selectedAssay) {
            this.setErrorMsg(undefined);

            AssayDOM.importRun({
                assayId: selectedAssay.id,
                files: files.toArray(),
                success: (response) => {
                    window.location = response.successurl;
                },
                failure: (response) => {
                    this.setErrorMsg(response.exception);
                }
            });
        }
    };

    handleFileChange = (files: Map<string, File>) => {
        this.setErrorMsg(undefined);
    };

    handleFileRemoval = (attachmentName: string) => {
        this.setErrorMsg(undefined);
    };

    handleCancel = () => {
        const returnUrl = ActionURL.getParameter('returnUrl');
        window.location.href = returnUrl || ActionURL.buildURL('project', 'begin');
    };

    renderWarning() {
        const { warning } = this.state;
        if (warning) {
            return <Alert bsStyle={'warning'}>{warning}</Alert>
        }
    }

    setErrorMsg(error: string) {
        this.setState(() => ({error}));
    }

    renderError() {
        const { error } = this.state;
        if (error) {
            return <Alert>{error}</Alert>
        }
    }

    onAssayCardClick = (index: number) => {
        this.setState(() => ({selected: index}));
    };

    renderAvailableAssays() {
        const { assays, selected } = this.state;

        let cards;
        if (assays) {
            cards = assays.map((assay, i) => {
                const isSelected = i === selected;

                return {
                    title: assay.name,
                    caption: isSelected ? 'Upload data to this assay.' : 'Click to select this assay for the data upload.',
                    iconSrc: 'assay',
                    disabled: !isSelected,
                    onClick: this.onAssayCardClick
                };
            }).toArray();
        }

        // TODO add a card for creating a new assay design (if user has proper permissions)

        return (
            <Panel>
                <Panel.Heading>Available Assays</Panel.Heading>
                <Panel.Body>
                    {!assays && <LoadingSpinner msg={'Loading assays designs...'}/>}
                    {assays && cards.length === 0 && <Alert bsStyle={'info'}>There are no available assays of type General in this container.</Alert>}
                    {cards && <Cards cards={cards}/>}
                </Panel.Body>
            </Panel>
        )
    }

    renderRunDataUpload() {
        if (this.getSelectedAssay()) {
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
                            showButtons={true}
                            submitText={'Save and Finish'}
                            onFileChange={this.handleFileChange}
                            onFileRemoval={this.handleFileRemoval}
                            onSubmit={this.handleFileSubmit}
                            onCancel={this.handleCancel}
                            previewGridProps={{
                                previewCount: 3,
                                header: 'Previewing Data for Import',
                                infoMsg: 'If the data does not look as expected, check you source file for errors and re-upload.'
                            }}
                        />
                    </Panel.Body>
                </Panel>
            )
        }
    }

    render() {
        return (
            <>
                {this.renderWarning()}
                {this.renderError()}
                {this.renderAvailableAssays()}
                {this.renderRunDataUpload()}
            </>
        )
    }
}