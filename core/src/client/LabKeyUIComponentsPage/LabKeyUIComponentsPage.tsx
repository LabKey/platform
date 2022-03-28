/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react';
import { Button, MenuItem, Panel } from 'react-bootstrap';
import { Link } from "react-router";
import { fromJS, List, Map } from 'immutable';
import {
    Alert,
    AppURL,
    Cards,
    ConfirmModal,
    FileAttachmentForm,
    Grid,
    LabelHelpTip,
    LoadingModal,
    LoadingSpinner,
    ManageDropdownButton,
    Progress,
    SCHEMAS,
    Tip,
    ToggleButtons,
    WizardNavButtons,
    Breadcrumb,
    BreadcrumbCreate,
    HeatMap,
    initQueryGridState,
    PageDetailHeader,
    SchemaListing,
    SearchResultCard,
    SearchResultsModel,
    SearchResultsPanel,
    ChangePasswordModal,
    UserDetailHeader,
    SelectInput,
    SelectInputOption,
    ServerContext,
    ServerContextProvider,
    withAppUser,
} from '@labkey/components';
import { getServerContext } from "@labkey/api";
import { CREATE_ROW, GRID_COLUMNS, GRID_DATA, SEARCH_RESULT_HITS } from './constants';
import { QueriesListingPage } from "./QueriesListingPage";
import { EditableGridPage } from "./EditableGridPage";
import { DetailPage } from "./DetailPage";
import { SampleInsertPage } from './SampleInsertPage';
import { NavigationBarPage } from "./NavigationBarPage";
import { AssayImportPage } from "./AssayImportPage";
import { LineagePage } from "./LineagePage";
import { UserProfilePage } from "./UserProfilePage";
import { PermissionAssignmentsPage } from "./PermissionAssignmentsPage";
import { UsersGridPanelPage } from "./UsersGridPanelPage";
import { GridPanelPage } from './GridPanelPage';

const COMPONENT_NAMES = List<SelectInputOption>([
    {value: 'Alert'},
    {value: 'AssayImportPanels'},
    {value: 'Breadcrumb'},
    {value: 'BreadcrumbCreate'},
    {value: 'Cards'},
    {value: 'ConfirmModal'},
    {value: 'DetailPanel'},
    {value: 'EditableDetailPanel'},
    {value: 'EditableGridPanel'},
    {value: 'EntityInsertPanel'},
    {value: 'FileAttachmentForm'},
    {value: 'Grid'},
    {value: 'GridPanel'},
    {value: 'HeatMap'},
    {value: 'LabelHelpTip'},
    {value: 'Lineage'},
    {value: 'LoadingModal'},
    {value: 'LoadingSpinner'},
    {value: 'NavigationBar'},
    {value: 'PageDetailHeader'},
    {value: 'PermissionAssignments'},
    {value: 'Progress'},
    {value: 'QueriesListing'},
    {value: 'SchemaListing'},
    {value: 'SearchResultCard'},
    {value: 'SearchResultsPanel'},
    {value: 'UsersGridPanel'},
    {value: 'Tip'},
    {value: 'ToggleButtons'},
    {value: 'UserDetailHeader'},
    {value: 'UserProfile'},
    {value: 'WizardNavButtons'},
]);

type State = {
    selected: string
    showProgress: boolean
    showConfirm: boolean
    showLoadingModal: boolean
    showChangePassword: boolean
    selectedToggleButton: string
    serverContext: ServerContext;
}

export class App extends React.Component<any, State> {

    constructor(props) {
        super(props);

        this.state = this.getInitialState();

        initQueryGridState(fromJS({
            schema: {
                'lists': {
                    queryDefaults: {
                        appEditableTable: true
                    }
                }
            }
        }));
    }

    getInitialState = (): State => {
        return {
            selected: undefined,
            selectedToggleButton: 'First Option',
            serverContext: withAppUser(getServerContext()),
            showChangePassword: false,
            showConfirm: false,
            showLoadingModal: false,
            showProgress: false,
        };
    };

    onSelectionChange = (id, selected) => {
        this.setState({ ...this.getInitialState(), selected });
    };

    renderPanel(title, body) {
        return (
            <Panel>
                <Panel.Heading>
                    {title}
                </Panel.Heading>
                <Panel.Body>
                    {body}
                </Panel.Body>
            </Panel>
        )
    }

    showProgress = () => {
        this.setState(() => ({showProgress: true}));
    };

    showConfirm = () => {
        this.setState(() => ({showConfirm: true}));
    };

    hideConfirm(msg: string) {
        this.setState(() => ({showConfirm: false}));
        console.log(msg + ' button has been clicked');
    }

    toggleLoadingModal = () => {
        this.setState((state) => ({showLoadingModal: !state.showLoadingModal}));
    };

    toggleChangePassword = () => {
        this.setState((state) => ({showChangePassword: !state.showChangePassword}));
    };

    onToggleButtonsClick = (selectedToggleButton: string) => {
        this.setState(() => ({selectedToggleButton}));
    };

    render() {
        const { selected, serverContext, showProgress, showConfirm, showLoadingModal, showChangePassword } = this.state;

        return (
            <ServerContextProvider initialContext={serverContext}>
                <p>
                    This page is setup to show examples of shared React components from
                    the <a href={'https://github.com/LabKey/labkey-ui-components'} target={'_blank'}>labkey-ui-components</a> repository.
                    To find more information about any of the components, check the <a href={'https://labkey.github.io/labkey-ui-components/'} target={'_blank'}>documentation</a> page.
                </p>

                <SelectInput
                    key={'labkey-ui-components-select'}
                    name={"labkey-ui-components-select"}
                    placeholder={"Select a component..."}
                    inputClass={'col-xs-4'}
                    value={selected}
                    valueKey={'value'}
                    labelKey={'value'}
                    onChange={this.onSelectionChange}
                    options={COMPONENT_NAMES.toArray()}
                />

                <br/>

                {selected === 'Alert' &&
                    this.renderPanel('Alert',
                        <>
                            <Alert>This is the default, error alert message.</Alert>
                            <Alert bsStyle={'info'}>This is an info alert message.</Alert>
                            <Alert bsStyle={'warning'}>This is an warning alert message.</Alert>
                        </>
                    )
                }
                {selected === 'AssayImportPanels' &&
                    <AssayImportPage/>
                }
                {selected === 'Breadcrumb' &&
                    this.renderPanel('Breadcrumb',
                        <Breadcrumb>
                            <Link to={AppURL.create('q').toString()}>Schemas</Link>
                            <Link to={AppURL.create('q', 'myschema').toString()}>{'My Schema'}</Link>
                            <Link to={AppURL.create('q', 'myschema', 'myquery').toString()}>{'My Query'}</Link>
                        </Breadcrumb>
                    )
                }
                {selected === 'BreadcrumbCreate' &&
                    this.renderPanel('BreadcrumbCreate',
                        <BreadcrumbCreate row={CREATE_ROW}>
                            <Link to={AppURL.create('q').toString()}>Schemas</Link>
                            <Link to={AppURL.create('q', 'myschema').toString()}>{'My Schema'}</Link>
                            <Link to={AppURL.create('q', 'myschema', 'myquery').toString()}>{'My Query'}</Link>
                        </BreadcrumbCreate>
                    )
                }
                {selected === 'Cards' &&
                    this.renderPanel('Cards',
                        <Cards cards={[{
                            title: 'First Card',
                            caption: 'The first card caption is where I will tell you about the first card.',
                            iconSrc: 'ingredients'
                        },{
                            title: 'Second Card',
                            iconSrc: 'molecule'
                        },{
                            title: 'Third Card',
                            caption: 'Third card is disabled so show you what that looks like.',
                            iconSrc: 'mixtures_gray',
                            disabled: true
                        }]}/>
                    )
                }
                {selected === 'ConfirmModal' &&
                    this.renderPanel('ConfirmModal',
                        <>
                            {showConfirm && (
                                <ConfirmModal
                                    title="Confirmation Modal Dialog"
                                    confirmButtonText="Confirm"
                                    onConfirm={() => {this.hideConfirm('Confirm')}}
                                    cancelButtonText="Cancel"
                                    onCancel={() => {this.hideConfirm('Cancel')}}
                                    confirmVariant="danger"
                                >
                                    <p>You are about to do something. Are you sure you want to continue?</p>
                                    <p><i>Don't worry clicking Confirm won't actually do anything.</i></p>
                                </ConfirmModal>
                            )}
                            <Button onClick={this.showConfirm} disabled={showConfirm}>Show Confirm Modal</Button>
                        </>
                    )
                }
                {selected === 'DetailPanel' &&
                    <DetailPage editable={false}/>
                }
                {selected === 'EditableDetailPanel' &&
                    <DetailPage editable={true}/>
                }
                {selected === 'EditableGridPanel' &&
                    <EditableGridPage/>
                }
                {selected === 'EntityInsertPanel' &&
                    this.renderPanel('EntityInsertPanel',
                        <SampleInsertPage/>
                    )
                }
                {selected === 'FileAttachmentForm' &&
                    <>
                        {this.renderPanel('FileAttachmentForm',
                            <FileAttachmentForm
                                label={'File Attachment'}
                                acceptedFormats={".csv, .tsv, .txt, .xls, .xlsx, .fasta, .png, .pdf"}
                                allowMultiple={false}
                                templateUrl={'#fileattachmentform?downloadtemplate=clicked'}
                                previewGridProps={{
                                    previewCount: 3,
                                    acceptedFormats: ".csv, .tsv, .txt, .xls, .xlsx, .fasta"
                                }}
                            />
                        )}
                        <p>
                            Note: this component also supports multiple file selection (in which case the preview grid options are not available),
                            showing an initial set of files on component mount, and a compact display format.
                        </p>
                    </>
                }
                {selected === 'Grid' &&
                    <>
                        {this.renderPanel('Grid',
                            <Grid data={GRID_DATA} columns={GRID_COLUMNS}/>
                        )}
                        {this.renderPanel('Grid - transposed',
                            <Grid data={GRID_DATA} columns={GRID_COLUMNS} transpose={true} striped={false}/>
                        )}
                    </>
                }
                {selected === 'GridPanel' &&
                    <GridPanelPage/>
                }
                {selected === 'HeatMap' &&
                    this.renderPanel('HeatMap',
                        <>
                            <Alert bsStyle={'info'}>Note: this currently pulls data from the exp.SampleSetHeatMap query.</Alert>
                            <HeatMap
                                schemaQuery={SCHEMAS.EXP_TABLES.SAMPLE_SET_HEAT_MAP}
                                nounSingular={'sample'}
                                nounPlural={'samples'}
                                yAxis={'protocolName'}
                                xAxis={'monthName'}
                                measure={'monthTotal'}
                                yInRangeTotal={'InRangeTotal'}
                                yTotalLabel={'12 month total samples'}
                                getCellUrl={() => AppURL.create()}
                                getHeaderUrl={() => AppURL.create()}
                                getTotalUrl={() => AppURL.create()}
                                headerClickUrl={AppURL.create()}
                            />
                        </>
                    )
                }
                {selected === 'LabelHelpTip' &&
                    this.renderPanel('LabelHelpTip',
                        <LabelHelpTip title="test">
                            <div>
                                Testing body of the LabelHelpTip, with a <a href="https://www.labkey.com" target="_blank">link</a> in it.
                            </div>
                        </LabelHelpTip>
                    )
                }
                {selected === 'Lineage' &&
                    this.renderPanel('Lineage',
                        <LineagePage/>
                    )
                }
                {selected === 'LoadingModal' &&
                    this.renderPanel('LoadingModal',
                        <>
                            {showLoadingModal && <LoadingModal
                                    title={'Loading Modal Dialog'}
                                    onCancel={() => {this.toggleLoadingModal()}}
                            />}
                            <Button onClick={this.toggleLoadingModal} disabled={showLoadingModal}>Show Loading Modal</Button>
                        </>
                    )
                }
                {selected === 'LoadingSpinner' &&
                    this.renderPanel('LoadingSpinner',
                        <LoadingSpinner msg={'Loading message goes here...'}/>
                    )
                }
                {selected === 'NavigationBar' &&
                    this.renderPanel('NavigationBar',
                        <NavigationBarPage/>
                    )
                }
                {selected === 'PageDetailHeader' &&
                    this.renderPanel('PageDetailHeader',
                        <PageDetailHeader
                            iconDir={'_images'}
                            title={'Page Detail Header'}
                            subTitle={'With a subtitle'}
                        >
                            <div className="btn-group">
                                <ManageDropdownButton id={'pagedetailheader1'} pullRight={true}>
                                    <MenuItem disabled={true}>Without collapse</MenuItem>
                                </ManageDropdownButton>
                                <ManageDropdownButton id={'pagedetailheader2'} pullRight={true} collapsed={true}>
                                    <MenuItem disabled={true}>With collapse</MenuItem>
                                </ManageDropdownButton>
                            </div>
                        </PageDetailHeader>
                    )
                }
                {selected === 'PermissionAssignments' &&
                    <PermissionAssignmentsPage/>
                }
                {selected === 'Progress' &&
                    this.renderPanel('Progress',
                        <>
                            <Progress toggle={showProgress} delay={0} updateIncrement={5}/>
                            <Button onClick={this.showProgress} disabled={showProgress}>Show Progress</Button>
                        </>
                    )
                }
                {selected === 'QueriesListing' &&
                    <QueriesListingPage/>
                }
                {selected === 'SchemaListing' &&
                    this.renderPanel('SchemaListing',
                        <SchemaListing asPanel={false}/>
                    )
                }
                {selected === 'SearchResultCard' &&
                    this.renderPanel('SearchResultCard',
                        <SearchResultCard
                            cardData={{title: 'Search Result Title'}}
                            summary={'Test search result summary text for the components page.'}
                            url={'#searchresultcard'}
                            iconUrl={'/labkey/_images/construct.svg'}
                        />
                    )
                }
                {selected === 'SearchResultsPanel' &&
                    this.renderPanel('SearchResultsPanel',
                        <SearchResultsPanel
                            model={SearchResultsModel.create({
                                entities: Map(fromJS(SEARCH_RESULT_HITS))
                            })}
                        />
                    )
                }
                {selected === 'UsersGridPanel' &&
                    <UsersGridPanelPage/>
                }
                {selected === 'Tip' &&
                    this.renderPanel('Tip',
                        <Tip caption={'This is a tooltip'}>
                            <Button>Hover Here</Button>
                        </Tip>
                    )
                }
                {selected === 'ToggleButtons' &&
                    this.renderPanel('ToggleButtons',
                        <ToggleButtons
                            first={'First Option'}
                            second={'Second Option'}
                            active={this.state.selectedToggleButton}
                            onClick={this.onToggleButtonsClick}
                        />
                    )
                }
                {selected === 'UserDetailHeader' &&
                    this.renderPanel('UserDetailHeader',
                        <>
                            <UserDetailHeader
                                title={'Welcome, ' + serverContext.user.displayName}
                                user={serverContext.user}
                                userProperties={fromJS({})}
                                dateFormat={serverContext.container.formats.dateFormat.toUpperCase()}
                                renderButtons={() => <Button onClick={this.toggleChangePassword} disabled={showChangePassword}>Change Password</Button>}
                            />
                            {showChangePassword &&
                            <ChangePasswordModal
                                    user={serverContext.user}
                                    onSuccess={() => {
                                        alert('Your password has been changed.');
                                    }}
                                    onHide={this.toggleChangePassword}
                            />
                            }
                        </>
                    )
                }
                {selected === 'UserProfile' &&
                    <UserProfilePage user={serverContext.user} />
                }
                {selected === 'WizardNavButtons' &&
                    this.renderPanel('WizardNavButtons',
                        <WizardNavButtons
                            cancel={() => console.log('WizardNavButtons cancel clicked')}
                            nextStep={() => console.log('WizardNavButtons finish clicked')}
                            previousStep={() => console.log('WizardNavButtons back clicked')}
                            finish={true}
                        />
                    )
                }
            </ServerContextProvider>
        )
    }
}
