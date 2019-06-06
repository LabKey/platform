import * as React from 'react';
import {Button, ButtonToolbar, Col, Form, FormControl, Panel, Row} from "react-bootstrap";
import {Map, List} from 'immutable';
import {ActionURL, Utils} from '@labkey/api'
import {
    Alert,
    Cards,
    FileAttachmentForm,
    LoadingSpinner,
    AssayDefinitionModel,
    InferDomainResponse,
    QueryColumn,
    createGeneralAssayDesign,
    fetchAllAssays,
    importGeneralAssayRun,
    naturalSort
} from "@glass/base";

const NEW_ASSAY_NAME_ID = 'new-assay-design-name';
const NEW_ASSAY_DESC_ID = 'new-assay-design-description';

interface Props {}

interface State {
    selected: number,
    assays: List<AssayDefinitionModel>
    error: string
    warning: string
    file: File
    inferredFields: List<QueryColumn>
    newAssayProps: {}
}

export class App extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);

        this.state = {
            selected: undefined,
            assays: undefined,
            error: undefined,
            warning: undefined,
            file: undefined,
            inferredFields: undefined,
            newAssayProps: undefined
        }
    }

    componentWillMount() {
        fetchAllAssays('General')
            .then((assays) => {
                const sortedAssays = assays.sortBy(assay => assay.name, naturalSort).toList();
                this.setState(() => ({assays: sortedAssays}));
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
                    warning: 'Unable to find an available General type assay for rowId ' + rowId + '. Please select an assay from the available list below.'
                }));
            }
        }
    }

    getSelectedAssay(): AssayDefinitionModel {
        const { selected, assays } = this.state;

        if (assays && selected !== undefined && selected < assays.size) {
            return assays.get(selected);
        }
    }

    isCreateNewAssay(): boolean {
        const { selected, assays } = this.state;
        return assays && selected !== undefined && selected === assays.size;
    }

    isValidAvailableAssay() {
        return this.getSelectedAssay() || this.isCreateNewAssay();
    }

    handleSubmit = () => {
        const { inferredFields, newAssayProps } = this.state;
        const selectedAssay = this.getSelectedAssay();

        // TODO add progress indicator and disabled the buttons

        if (selectedAssay) {
            this.setErrorMsg(undefined);
            this.importFileAsRun(selectedAssay.id);
        }
        else if (this.isCreateNewAssay() && inferredFields) {
            this.setErrorMsg(undefined);

            const name = newAssayProps ? newAssayProps[NEW_ASSAY_NAME_ID] : undefined;
            const descr = newAssayProps ? newAssayProps[NEW_ASSAY_DESC_ID] : undefined;

            if (!name || name.length === 0) {
                this.setErrorMsg('You must provide a name for the new assay design.');
                return;
            }

            createGeneralAssayDesign(name, descr, inferredFields)
                .then((newAssay) => {
                    this.importFileAsRun(newAssay.protocolId);
                })
                .catch((reason) => {
                    this.setErrorMsg(reason);
                });
        }
    };

    importFileAsRun(assayId: number) {
        const { file } = this.state;

        if (assayId && assayId) {
            importGeneralAssayRun(assayId, file)
                .then((response) => {
                    window.location = response.successurl;
                })
                .catch((reason) => {
                    this.setErrorMsg(reason);
                })
        }
    }

    handlePreviewLoad = (response: InferDomainResponse) => {
        this.setState(() => ({inferredFields: response.fields}));
    };

    handleFileChange = (files: Map<string, File>) => {
        this.setState(() => ({
            error: undefined,
            file: files.first(),
            inferredFields: undefined
        }));
    };

    handleFileRemoval = (attachmentName: string) => {
        this.setState(() => ({
            error: undefined,
            file: undefined,
            inferredFields: undefined
        }));
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

    onNewAssayFormChange = (evt) => {
        const id = evt.target.id;
        const value = evt.target.value;

        this.setState((state) => ({
            newAssayProps: {
                ...state.newAssayProps,
                [id]: value
            }
        }));
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

            // TODO check if user has design assay permissions
            cards.push({
                title: 'Create a New Assay',
                caption: this.isCreateNewAssay() ? 'Upload data to a new assay' : 'Click to select this option for creating a new assay design.',
                iconSrc: 'default',
                disabled: !this.isCreateNewAssay(),
                onClick: this.onAssayCardClick
            })
        }

        return (
            <Panel>
                <Panel.Heading>
                    Step 1: Select an available assay or the option to create a new one.
                </Panel.Heading>
                <Panel.Body>
                    {!assays && <LoadingSpinner msg={'Loading assays designs...'}/>}
                    {assays && cards.length === 0 && <Alert bsStyle={'info'}>There are no available assays of type General in this container.</Alert>}
                    {cards && <Cards cards={cards}/>}
                </Panel.Body>
            </Panel>
        )
    }

    renderNewAssayProperties() {
        if (!this.isCreateNewAssay()) {
            return;
        }

        return (
            <Panel>
                <Panel.Heading>
                    Step 2: Enter properties for the new assay.
                </Panel.Heading>
                <Panel.Body>
                    <Form>
                        <Row>
                            <Col xs={3}>Name *</Col>
                            <Col xs={9}>
                                <FormControl
                                    id={NEW_ASSAY_NAME_ID}
                                    type="text"
                                    placeholder={'Enter a name for this assay'}
                                    onChange={this.onNewAssayFormChange}
                                />
                            </Col>
                        </Row>
                        <br/>
                        <Row>
                            <Col xs={3}>Description</Col>
                            <Col xs={9}>
                                <textarea
                                    className="form-control"
                                    id={NEW_ASSAY_DESC_ID}
                                    placeholder={'Add a description'}
                                    onChange={this.onNewAssayFormChange}
                                />
                            </Col>
                        </Row>
                    </Form>
                </Panel.Body>
            </Panel>
        )
    }

    renderRunDataUpload() {
        return (
            <Panel>
                <Panel.Heading>
                    Step {this.isCreateNewAssay() ? 3: 2}: Upload a data file.
                </Panel.Heading>
                {this.isValidAvailableAssay() &&
                    <Panel.Body>
                        <FileAttachmentForm
                                acceptedFormats={".csv, .tsv, .txt, .xls, .xlsx"}
                                showAcceptedFormats={true}
                                allowDirectories={false}
                                allowMultiple={false}
                                label={'Import from Local File'}
                                onFileChange={this.handleFileChange}
                                onFileRemoval={this.handleFileRemoval}
                                onCancel={this.handleCancel}
                                previewGridProps={{
                                    previewCount: 3,
                                    header: 'Previewing Data for Import',
                                    infoMsg: 'If the data does not look as expected, check you source file for errors and re-upload.',
                                    onPreviewLoad: this.handlePreviewLoad
                                }}
                        />
                    </Panel.Body>
                }
            </Panel>
        )
    }

    renderRunProperties() {
        const { file } = this.state;

        return (
            <Panel>
                <Panel.Heading>
                    Step {this.isCreateNewAssay() ? 4: 3}: Enter run properties for this import.
                </Panel.Heading>
                {file &&
                    <Panel.Body>
                        Not yet implemented...
                    </Panel.Body>
                }
            </Panel>
        )
    }

    renderButtons() {
        const { file } = this.state;

        return (
            <Panel>
                <Panel.Heading>
                    Step {this.isCreateNewAssay() ? 5: 4}: Submit
                </Panel.Heading>
                {this.isValidAvailableAssay() && file &&
                    <Panel.Body>
                        <ButtonToolbar>
                            <Button onClick={this.handleCancel}>Cancel</Button>
                            <Button bsStyle={'success'} onClick={this.handleSubmit}>Save and Finish</Button>
                        </ButtonToolbar>
                    </Panel.Body>
                }
            </Panel>
        )
    }

    render() {
        return (
            <>
                {this.renderWarning()}
                {this.renderError()}
                {this.renderAvailableAssays()}
                {this.renderNewAssayProperties()}
                {this.renderRunDataUpload()}
                {this.renderRunProperties()}
                {this.renderButtons()}
            </>
        )
    }
}