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
import {Button, Col, Panel, Row} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {LoadingSpinner, Alert, ConfirmModal, DomainForm, DomainDesign, fetchDomain, saveDomain, IBannerMessage} from "@labkey/components"

import "@labkey/components/dist/components.css"
import "./domainDesigner.scss";

interface IAppState {
    dirty: boolean
    domain: DomainDesign
    domainId: number
    messages?: List<IBannerMessage>,
    queryName: string
    returnUrl: string
    schemaName: string
    showConfirm: boolean
    submitting: boolean
    includeWarnings: boolean
    showWarnings: boolean
    badDomain : DomainDesign
}

export class App extends React.PureComponent<any, Partial<IAppState>> {

    constructor(props) {
        super(props);

        const { domainId, schemaName, queryName, returnUrl } = ActionURL.getParameters();

        let messages = List<IBannerMessage>();
        if ((!schemaName || !queryName) && !domainId) {
            let msg =  'Missing required parameter: domainId or schemaName and queryName.';
            let msgType = 'danger';
            let bannerMsg ={message : msg, messageType : msgType};
            messages = messages.push(bannerMsg);
        }

        this.state = {
            schemaName,
            queryName,
            domainId,
            returnUrl,
            submitting: false,
            messages,
            showConfirm: false,
            dirty: false,
            includeWarnings: true
        };
    }

    componentDidMount() {
        const { schemaName, queryName, domainId, messages } = this.state;

        if ((schemaName && queryName) || domainId) {
            fetchDomain(domainId, schemaName, queryName)
                .then(domain => {
                    this.setState(() => ({domain}));
                })
                .catch(error => {
                    this.setState(() => ({
                        messages : messages.set(0, {message: error.exception, messageType: 'danger'})
                    }));
                });
        }

        window.addEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    handleWindowBeforeUnload = (event) => {

        if (this.state.dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    componentWillUnmount() {
        window.removeEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    submitHandler = (navigate : boolean) => {
        const { domain, submitting, includeWarnings } = this.state;

        if (submitting) {
            return;
        }

        this.setState({
            submitting: true
        });

        saveDomain(domain, undefined, undefined, undefined,  includeWarnings)
            .then((savedDomain) => {

                this.setState(() => ({
                    domain: savedDomain,
                    submitting: false,
                    dirty: false
                }));

                this.showMessage("Save Successful", 'success', 0);

                if (navigate) {
                    this.navigate();
                }
            })
            .catch((badDomain) => {
                // if there are only warnings then show ConfirmModel
                if (badDomain.domainException.severity === "Warning") {
                    this.setState(() => ({
                        showWarnings : true,
                        badDomain: badDomain
                    }))
                }
                else {
                    this.setState(() => ({
                        domain: badDomain,
                        submitting: false
                    }));
                }
            });
    };

    submitAndNavigate = () => {
        this.submitHandler(true);
    };

    confirmWarningAndNavigate = () => {
        this.setState(() => ({
            includeWarnings : false,
            showWarnings : false,
            submitting : false
        }), () => {this.submitHandler(true)});
    };

    onSubmitWarningsCancel = () => {
        this.setState(() => ({
            showWarnings : false,
            submitting : false
        }))
    };

    onChangeHandler = (newDomain, dirty) => {

        this.setState((state) => ({
            domain: newDomain,
            dirty: state.dirty || dirty // if the state is already dirty, leave it as such
        }));
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

    renderNavigateConfirm() {
        return (
            <ConfirmModal
                title='Keep unsaved changes?'
                msg='You have made changes to this domain that have not yet been saved. Do you want to save these changes before leaving?'
                confirmVariant='success'
                onConfirm={this.submitAndNavigate}
                onCancel={this.navigate}
                cancelButtonText='No, Discard Changes'
                confirmButtonText='Yes, Save Changes'
            />
        )
    }

    renderWarningConfirm() {
        const { badDomain } = this.state;
        const rollupURI = LABKEY.helpLinkPrefix + 'specimenCustomProperties';

        let errors = badDomain.domainException.errors;
        let question = <p> {"There are issues with the following fields that you may wish to resolve:"} </p>;
        let warnings = errors.map((error) => {
            return <div> {error.message} </div>
        });
        let suggestion = <p> {"See the following documentation page for further details: "}<br/>
            <a href={rollupURI}> {"Specimen properties and rollup rules"}</a>
                        </p>;

        return (
            <ConfirmModal
                title='Save without resolving issues?'
                msg={<>{question}{warnings}{suggestion}</>}
                confirmVariant='success'
                onConfirm={this.confirmWarningAndNavigate}
                onCancel={this.onSubmitWarningsCancel}
                cancelButtonText='No, edit and resolve issues'
                confirmButtonText='Yes, save changes'
            />
        )
    }

    renderButtons() {
        const { submitting } = this.state;

        return (
                <div className={'domain-form-panel domain-designer-buttons'}>
                    <Button onClick={this.onCancelBtnHandler}>Cancel</Button>
                    <Button className='pull-right' bsStyle='success' disabled={submitting} onClick={this.submitAndNavigate}>Save</Button>
                </div>
        )
    }

    renderInstructionsPanel() {
        return (
            <Panel>
                <Panel.Heading>Instructions</Panel.Heading>
                <Panel.Body>{this.state.domain.instructions}</Panel.Body>
            </Panel>
        )
    }

    render() {
        const { domain, messages, showConfirm, showWarnings } = this.state;
        const isLoading = domain === undefined && messages === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                { showConfirm && this.renderNavigateConfirm() }
                { showWarnings && this.renderWarningConfirm() }
                { domain && domain.instructions && this.renderInstructionsPanel()}
                { domain &&
                    <DomainForm
                        headerTitle={'Fields'}
                        domain={domain}
                        onChange={this.onChangeHandler}
                        useTheme={true}
                    />
                }
                { messages && messages.size > 0 && messages.map((bannerMessage, idx) => {
                    return (<Alert key={idx} bsStyle={bannerMessage.messageType} onDismiss={() => this.dismissAlert(idx)}>{bannerMessage.message}</Alert>) })
                }
                { domain && this.renderButtons() }
            </>
        )
    }
}