/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {List} from "immutable";
import * as React from 'react'
import {Button, Panel} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {
    LoadingSpinner,
    Alert,
    ConfirmModal,
    DomainForm,
    DomainDesign,
    fetchDomainDetails,
    saveDomain,
    IBannerMessage,
    getActionErrorMessage,
    SampleSetDetailsPanel,
    getHelpLink,
    LoadingPage,
    NotFound,
    getSampleTypeDetails,
    SchemaQuery,
    DomainDetails, SAMPLE_TYPE, IDomainField, User, hasAllPermissions, PermissionTypes, getSampleSet, initQueryGridState
} from "@labkey/components"

import "@labkey/components/dist/components.css"
import "./sampleTypeDesigner.scss";
import {ISampleSetDetails} from "@labkey/components/dist/components/samples/models";

interface IAppState {
    dirty: boolean
    sampleType: DomainDetails
    rowId: number
    domainId?: number
    messages?: List<IBannerMessage>,
    queryName: string
    returnUrl: string
    schemaName: string
    showConfirm: boolean
    submitting: boolean
    includeWarnings: boolean
    showWarnings: boolean
    badDomain: DomainDetails
}

//TODO should these be moved to a constants file? or shared through components?
export const NAME_EXPRESSION_TOPIC = 'sampleIDs#expression';
export const DEFAULT_SAMPLE_FIELD_CONFIG = {
    required: true,
    dataType: SAMPLE_TYPE,
    conceptURI: SAMPLE_TYPE.conceptURI,
    rangeURI: SAMPLE_TYPE.rangeURI,
    lookupSchema: 'exp',
    lookupQuery: 'Materials',
    lookupType: {...SAMPLE_TYPE},
    name: 'SampleId',
} as Partial<IDomainField>;

export class App extends React.PureComponent<any, Partial<IAppState>> {

    constructor(props) {
        super(props);

        initQueryGridState();
        //TODO case-sensitive?
        const { RowId, schemaName, queryName, returnUrl } = ActionURL.getParameters();

        let messages = List<IBannerMessage>();
        if ((!schemaName || !queryName) && !RowId) {
            let msg =  'Missing required parameter: rowId or schemaName and queryName.';
            let msgType = 'danger';
            let bannerMsg ={message : msg, messageType : msgType};
            messages = messages.push(bannerMsg);
        }

        this.state = {
            schemaName,
            queryName,
            rowId: RowId,
            returnUrl,
            submitting: false,
            messages,
            showConfirm: false,
            dirty: false,
            includeWarnings: true
        };
    }

    componentDidMount() {
        const { domainId, schemaName, queryName, rowId, messages } = this.state;

        if ((schemaName && queryName) || domainId ) {
            this.fetchSampleTypeDomain(domainId, schemaName, queryName);
        }
        else if (rowId) {
            getSampleSet({rowId})
                .then(results => {
                    const sampleSet = results.get('sampleSet');
                    const {domainId} = sampleSet;

                    this.fetchSampleTypeDomain(domainId);
                })
                .catch(error => {
                    this.setState(() => ({
                        messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                    }));
                });
        }

        window.addEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    /**
     * Look up full Sample Type domain, including fields
     **/
    private fetchSampleTypeDomain = (domainId, schemaName?, queryName?): void => {
        getSampleTypeDetails( SchemaQuery.create(schemaName, queryName), domainId)
            .then( results => {
                const sampleType = results;
                this.setState(()=> ({sampleType}));
            }).catch(error => {
                const {messages} = this.state;

                this.setState(() => ({
                    messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                }));
            }
        );
    };

    handleWindowBeforeUnload = (event) => {
        if (this.state.dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    componentWillUnmount() {
        window.removeEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    submitHandler = (navigate : boolean) => {
        // const { domain, submitting, includeWarnings } = this.state;
        //
        // if (submitting) {
        //     return;
        // }
        //
        // this.setState(() => ({submitting: true}));
        //
        // saveDomain(domain, undefined, undefined, undefined,  includeWarnings)
        //     .then((savedDomain) => {
        //         this.setState(() => ({
        //             domain: savedDomain,
        //             submitting: false,
        //             dirty: false
        //         }));
        //
        //         this.showMessage("Save Successful", 'success', 0);
        //
        //         if (navigate) {
        //             this.navigate();
        //         }
        //     })
        //     .catch((badDomain) => {
        //         // if there are only warnings then show ConfirmModel
        //         if (badDomain.domainException.severity === "Warning") {
        //             this.setState(() => ({
        //                 showWarnings : true,
        //                 badDomain: badDomain
        //             }))
        //         }
        //         else {
        //             this.setState(() => ({
        //                 domain: badDomain,
        //                 submitting: false
        //             }));
        //         }
        //     });
    };

    submitAndNavigate = () => {
        this.submitHandler(true);
    };

    confirmWarningAndNavigate = () => {
        this.setState(() => ({
            includeWarnings : false,
            showWarnings : false,
            submitting : false
        }), () => {
            this.submitHandler(true);
        });
    };

    onSubmitWarningsCancel = () => {
        this.setState(() => ({
            showWarnings : false,
            submitting : false
        }))
    };

    onChangeHandler = (newDomain, dirty) => {
        // this.setState((state) => ({
        //     domain: newDomain,
        //     dirty: state.dirty || dirty // if the state is already dirty, leave it as such
        // }));
    };

    dismissAlert = (index: any) => {
        this.setState(() => ({
            messages: this.state.messages.setIn([index], [{message: undefined, messageType: undefined}])
        }));
    };

    showMessage = (message: string, messageType: string, index: number, additionalState?: Partial<IAppState>) => {
        const { messages } = this.state;

        this.setState(Object.assign({}, additionalState, {
            messages : messages.set(index, {message: message, messageType: messageType})
        }));
    };

    onCancelBtnHandler = () => {
        if (this.state.dirty) {
            this.setState(() => ({showConfirm: true}));
        }
        else {
            this.navigate();
        }
    };

    navigate = () => {
        const { returnUrl } = this.state;
        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || ActionURL.buildURL('project', 'begin', LABKEY.container.path);
        });
    };

    //TODO move out?
    userCanDesignSampleSets = (user: User): boolean => {
        return hasAllPermissions(user, [PermissionTypes.DesignSampleSet]);
    };

    // renderNavigateConfirm() {
    //     return (
    //         <ConfirmModal
    //             title='Keep unsaved changes?'
    //             msg='You have made changes to this domain that have not yet been saved. Do you want to save these changes before leaving?'
    //             confirmVariant='success'
    //             onConfirm={this.submitAndNavigate}
    //             onCancel={this.navigate}
    //             cancelButtonText='No, Discard Changes'
    //             confirmButtonText='Yes, Save Changes'
    //         />
    //     )
    // }
    //
    // renderWarningConfirm() {
    //     const { badDomain } = this.state;
    //     const errors = badDomain.domainException.errors;
    //     const question = <p> {"There are issues with the following fields that you may wish to resolve:"} </p>;
    //     const warnings = errors.map((error) => {
    //         return <li> {error.message} </li>
    //     });
    //
    //     // TODO this doc link is specimen specific, we should find a way to pass this in via the domain kind or something like that
    //     const rollupURI = LABKEY.helpLinkPrefix + 'specimenCustomProperties';
    //     const suggestion = (
    //         <p>
    //             See the following documentation page for further details: <br/>
    //             <a href={rollupURI} target={'_blank'}> {"Specimen properties and rollup rules"}</a>
    //         </p>
    //     );
    //
    //     return (
    //         <ConfirmModal
    //             title='Save without resolving issues?'
    //             msg={
    //                 <>
    //                     {question}
    //                     <ul>{warnings}</ul>
    //                     {suggestion}
    //                 </>
    //             }
    //             confirmVariant='success'
    //             onConfirm={this.confirmWarningAndNavigate}
    //             onCancel={this.onSubmitWarningsCancel}
    //             cancelButtonText='No, edit and resolve issues'
    //             confirmButtonText='Yes, save changes'
    //         />
    //     )
    // }

    // renderButtons() {
    //     const { submitting } = this.state;
    //
    //     return (
    //         <div className={'domain-form-panel domain-designer-buttons'}>
    //             <Button onClick={this.onCancelBtnHandler}>Cancel</Button>
    //             <Button className='pull-right' bsStyle='success' disabled={submitting} onClick={this.submitAndNavigate}>Save</Button>
    //         </div>
    //     )
    // }
    //
    // renderInstructionsPanel() {
    //     return (
    //         <Panel>
    //             <Panel.Heading>Instructions</Panel.Heading>
    //             <Panel.Body>{this.state.domain.instructions}</Panel.Body>
    //         </Panel>
    //     )
    // }

    beforeFinish = ():void => {}; //TODO may need something here...

    render() {
        // const { domain, messages, showConfirm, showWarnings } = this.state;
        // const isLoading = domain === undefined && messages === undefined;
        //
        // if (isLoading) {
        //     return <LoadingSpinner/>
        // }

        const { sampleSetItem, menuLoading, user } = this.props;
        const { sampleType } = this.state;
        const subtitle = 'Edit Sample Type Details';

        // if (!this.userCanDesignSampleSets(user)) {
        //     return <InsufficientPermissionsPage title={subtitle}/>
        // }
        // else
        if (menuLoading) {
            return <LoadingPage title={subtitle}/>
        }
        // else if (!sampleSetItem || sampleSetItem.get('id') === undefined) {
        //     return <NotFound/>
        // }

        return (
            <>
                {/*{ showConfirm && this.renderNavigateConfirm() }*/}
                {/*{ showWarnings && this.renderWarningConfirm() }*/}
                {/*{ domain && domain.instructions && this.renderInstructionsPanel()}*/}
                {/*{ domain &&*/}
                {/*    <DomainForm*/}
                {/*        headerTitle={'Fields'}*/}
                {/*        domain={domain}*/}
                {/*        onChange={this.onChangeHandler}*/}
                {/*        useTheme={true}*/}
                {/*    />*/}
                {/*}*/}
                {/*{ messages && messages.size > 0 && messages.map((bannerMessage, idx) => {*/}
                {/*    return (<Alert key={idx} bsStyle={bannerMessage.messageType} onDismiss={() => this.dismissAlert(idx)}>{bannerMessage.message}</Alert>) })*/}
                {/*}*/}
                {/*{ domain && this.renderButtons() }*/}

                {/*{hasError &&*/}
                {/*<Alert>{getActionErrorMessage("There was a problem loading the sample type details.", 'sample type')}</Alert>*/}
                {/*}*/}
                {sampleType &&
                <SampleSetDetailsPanel
                        data={sampleType}
                        nameExpressionInfoUrl={getHelpLink(NAME_EXPRESSION_TOPIC)}
                        beforeFinish={this.beforeFinish}
                        onComplete={this.submitAndNavigate}
                        onCancel={this.onCancelBtnHandler}
                        defaultSampleFieldConfig={DEFAULT_SAMPLE_FIELD_CONFIG}
                />
                }
            </>
        )
    }
}

