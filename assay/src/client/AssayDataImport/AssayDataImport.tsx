import React from 'react';
import { Button, ButtonToolbar, Panel } from "react-bootstrap";
import {Map, List, fromJS} from 'immutable';
import {ActionURL, Security, Utils, getServerContext} from '@labkey/api'
import {
    Alert,
    Cards,
    FileAttachmentForm,
    LoadingSpinner,
    Progress,
    AssayDefinitionModel,
    InferDomainResponse,
    QueryColumn,
    PermissionTypes,
    User,
    fetchAllAssays,
    importGeneralAssayRun,
    naturalSort,
    hasAllPermissions,
    AssayProtocolModel,
    AssayPropertiesPanel,
    DomainDesign,
    saveAssayDesign,
    fetchProtocol,
    setDomainFields
} from '@labkey/components'

import "@labkey/components/dist/components.css"
import "./assayDataImport.scss";

import {AssayRunForm} from "./AssayRunForm";

const FORM_IDS = {
    RUN_NAME: 'assay-run-name',
    RUN_COMMENT: 'assay-run-comment'
};

interface Props {}

interface State {
    user: User
    selected: number
    assays: List<AssayDefinitionModel>
    error?: string
    warning?: string
    file?: File
    inferredFields?: List<QueryColumn>
    protocolModel?: AssayProtocolModel,
    runProperties?: {}
    isSubmitting: boolean
}

export class App extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);

        this.state = {
            user: new User(getServerContext().user),
            selected: undefined,
            assays: undefined,
            isSubmitting: false
        }
    }

    componentDidMount() {
        fetchAllAssays('General')
            .then((assays) => {
                const sortedAssays = assays.sortBy(assay => assay.name, naturalSort).toList();
                this.setState(() => ({assays: sortedAssays}));
                this.selectInitialAssay();
            })
            .catch((error) => {
                this.setErrorMsg(error);
            });

        // get the GPAT assay template protocol info for use with the "Create New Assay" option
        fetchProtocol(undefined, 'General')
            .then((protocolModel) => {
                this.setState(() => ({protocolModel}));
            })
            .catch((error) => {
                this.setErrorMsg(error.exception);
            });

        // get the user permissions to know if the user has DesignAssay perm
        Security.getUserPermissions({
            success: (response) => {
                const user = this.state.user.set('permissionsList', fromJS(response.container.effectivePermissions)) as User;
                this.setState(() => ({user}));
            },
            failure: (error) => {
                this.setErrorMsg(error);
            }
        });
    }

    userCanCreateAssay(): boolean {
        return hasAllPermissions(this.state.user, [PermissionTypes.DesignAssay]);
    }

    selectInitialAssay(): void {
        const { assays } = this.state;
        const urlRowId = ActionURL.getParameter('rowId');
        const rowId = urlRowId ? parseInt(urlRowId) : undefined;

        if (assays) {
            if (assays.size > 0 && Utils.isNumber(rowId)) {
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
            else if (assays.size === 0 && this.userCanCreateAssay()) {
                this.setState(() => ({selected: 0}));
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

    hasValidNewAssayName(): boolean {
        const { protocolModel } = this.state;
        return protocolModel && protocolModel.name && protocolModel.name.length > 0;
    }

    isValidNewAssay(): boolean {
        return this.isCreateNewAssay() && this.hasValidNewAssayName();
    }

    setSubmitting(isSubmitting: boolean): void {
        this.setState(() => ({isSubmitting}));
    }

    handleSubmit = () => {
        const { inferredFields, protocolModel } = this.state;
        const selectedAssay = this.getSelectedAssay();

        if (selectedAssay) {
            this.setErrorMsg(undefined);
            this.setSubmitting(true);
            this.importFileAsRun(selectedAssay.id);
        }
        else if (this.isCreateNewAssay() && protocolModel && inferredFields) {
            this.setErrorMsg(undefined);
            this.setSubmitting(true);

            if (!this.hasValidNewAssayName()) {
                this.setErrorMsg('You must provide a name for the new assay design.');
                return;
            }

            // get each domain as a variable we can set the results fields from inferredFields and clear the batch domain default fields
            const batchDomain = setDomainFields(protocolModel.getDomainByNameSuffix('Batch'), List<QueryColumn>());
            const runDomain = protocolModel.getDomainByNameSuffix('Run');
            const resultsDomain = setDomainFields(protocolModel.getDomainByNameSuffix('Data'), inferredFields);

            let newProtocol = AssayProtocolModel.create({
                ...protocolModel.toJS(),
                providerName: 'General',
                domains: List<DomainDesign>([batchDomain, runDomain, resultsDomain])
            });

            // the AssayProtocolModel.create call drops the name, so put it back
            newProtocol = newProtocol.set('name', protocolModel.name) as AssayProtocolModel;

            saveAssayDesign(newProtocol)
                .then((newAssay) => {
                    this.importFileAsRun(newAssay.protocolId);
                })
                .catch((errorModel) => {
                    this.setErrorMsg(errorModel.exception);
                });
        }
    };

    importFileAsRun(assayId: number): void {
        const { file, runProperties } = this.state;

        if (assayId && file) {
            const name = runProperties ? runProperties[FORM_IDS.RUN_NAME] : undefined;
            const comment = runProperties ? runProperties[FORM_IDS.RUN_COMMENT] : undefined;

            importGeneralAssayRun(assayId, file, name, comment)
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

    setErrorMsg(error: string): void {
        this.setState(() => ({
            error,
            isSubmitting: false
        }));
    }

    onAssayCardClick = (index: number) => {
        this.setState(() => ({
            selected: index,
            error: undefined,
            file: undefined,
            inferredFields: undefined
        }));
    };

    onFormChange = (evt) => {
        const id = evt.target.id;
        const value = evt.target.value;

        this.setState((state) => ({
            runProperties: {
                ...state.runProperties,
                [id]: value
            }
        }));
    };

    onAssayPropertiesChange = (protocolModel: AssayProtocolModel) => {
        this.setState(() => ({protocolModel}));
    };

    getCardsFromAssays(): List<any> {
        const { assays, protocolModel } = this.state;
        const selectedAssay = this.getSelectedAssay();
        let cards = List<any>(); // TODO should we be exporting ICardProps from @labkey/components and using here instead of any?

        if (selectedAssay) {
            cards = cards.push({
                title: selectedAssay.name,
                caption: 'Upload data to this assay.',
                iconSrc: 'assay'
            });
        }
        else if (this.isCreateNewAssay()) {
            cards = cards.push({
                title: 'Create a New Assay',
                caption: 'Upload data to a new assay',
                iconSrc: 'default'
            });
        }
        else if (assays) {
            cards = assays.map((assay, i) => {
                return {
                    title: assay.name,
                    caption: 'Click to select this assay for the data upload.',
                    iconSrc: 'assay',
                    onClick: this.onAssayCardClick
                };
            }).toList();

            if (protocolModel && this.userCanCreateAssay()) {
                cards = cards.push({
                    title: 'Create a New Assay',
                    caption: 'Click to select this option for creating a new assay design.',
                    iconSrc: 'default',
                    disabled: true,
                    onClick: this.onAssayCardClick
                });
            }
        }

        return cards;
    }

    renderWarning() {
        const { warning } = this.state;
        if (warning) {
            return <Alert bsStyle={'warning'}>{warning}</Alert>
        }
    }

    renderError() {
        const { error } = this.state;
        if (error) {
            return <Alert>{error}</Alert>
        }
    }

    renderAvailableAssays() {
        const { assays, selected } = this.state;
        const cards = this.getCardsFromAssays();
        const isCurrentStep = selected === undefined;

        return (
            <Panel className={isCurrentStep ? 'panel-active' : ''}>
                <Panel.Heading>
                    Step 1: Select an available assay{this.userCanCreateAssay() ? ' or the option to create a new one' : ''}&nbsp;
                    {assays && assays.size > 0 && selected !== undefined && <Button onClick={() => this.onAssayCardClick(undefined)}>Clear selection</Button>}
                </Panel.Heading>
                <Panel.Body>
                    {!assays && <LoadingSpinner msg={'Loading assay designs...'}/>}
                    {assays && cards.size === 0 && <Alert bsStyle={'info'}>There are no available assays of type General in this container.</Alert>}
                    {cards && <Cards cards={cards.toArray()}/>}
                </Panel.Body>
            </Panel>
        )
    }

    renderNewAssayProperties() {
        if (!this.isCreateNewAssay()) {
            return;
        }

        const { file, protocolModel } = this.state;
        const showStep = this.isCreateNewAssay() && file;
        const isCurrentStep = showStep && !this.hasValidNewAssayName();

        return (
            <Panel className={isCurrentStep ? 'panel-active' : ''}>
                <Panel.Heading>
                    Step 3: Enter properties for the new assay
                </Panel.Heading>
                {showStep &&
                    <Panel.Body>
                        <AssayPropertiesPanel
                            asPanel={false}
                            model={protocolModel}
                            appPropertiesOnly={true}
                            onChange={this.onAssayPropertiesChange}
                            panelStatus={'NONE'}
                            validate={false}
                            useTheme={true}
                            controlledCollapse={false}
                            initCollapsed={false}
                        >
                            <p>
                                Define basic properties for this new design. These and other advanced settings can always be
                                modified later on the assay runs list by choosing "Manage Assay Design".
                            </p>
                            <p>
                                By default, this assay design will include the column headers detected from your uploaded file.
                            </p>
                        </AssayPropertiesPanel>
                    </Panel.Body>
                }
            </Panel>
        )
    }

    renderRunDataUpload() {
        const showStep = (this.getSelectedAssay() || this.isCreateNewAssay());
        const isCurrentStep = showStep && !this.state.file;

        return (
            <Panel className={isCurrentStep ? 'panel-active' : ''}>
                <Panel.Heading>
                    Step 2: Upload a data file
                </Panel.Heading>
                {showStep &&
                    <Panel.Body>
                        {/*TODO add Download Template button*/}
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
                                    // TODO add info about if the assay has transform scripts, this preview does not reflect that
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
        const showStep = (this.getSelectedAssay() || this.isValidNewAssay()) && file;

        return (
            <Panel>
                <Panel.Heading>
                    Step {this.isCreateNewAssay() ? 4: 3}: Enter run properties for this import
                </Panel.Heading>
                {showStep &&
                    <Panel.Body>
                        {/*TODO add run properties form inputs (see Biologics)*/}
                        <AssayRunForm onChange={this.onFormChange}/>
                    </Panel.Body>
                }
            </Panel>
        )
    }

    renderButtons() {
        const { file, isSubmitting } = this.state;
        const isCurrentStep = (this.getSelectedAssay() || this.isValidNewAssay()) && file;

        return (
            <Panel className={isCurrentStep ? 'panel-active' : ''}>
                <Panel.Heading>
                    Step {this.isCreateNewAssay() ? 5: 4}: Submit
                </Panel.Heading>
                {isCurrentStep &&
                    <Panel.Body>
                        <ButtonToolbar>
                            <Button onClick={this.handleCancel} disabled={isSubmitting}>Cancel</Button>
                            <Button bsStyle={'primary'} onClick={this.handleSubmit} disabled={isSubmitting}>Save and Finish</Button>
                        </ButtonToolbar>
                    </Panel.Body>
                }
            </Panel>
        )
    }

    renderProgress() {
        const { file } = this.state;

        return (
            <Progress
                title={'Uploading file and saving assay run...'}
                toggle={this.state.isSubmitting}
                estimate={file ? file.size * .2 : undefined}
                modal={true}
            />
        )
    }

    render() {
        return (
            <>
                {this.renderWarning()}
                {this.renderAvailableAssays()}
                {this.renderRunDataUpload()}
                {this.renderNewAssayProperties()}
                {this.renderRunProperties()}
                {this.renderButtons()}
                {this.renderError()}
                {this.renderProgress()}
            </>
        )
    }
}