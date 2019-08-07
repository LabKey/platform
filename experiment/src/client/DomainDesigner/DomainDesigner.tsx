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
import {Button} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {LoadingSpinner, Alert, ConfirmModal, WizardNavButtons} from "@glass/base";
import {DomainForm, DomainDesign, clearFieldDetails, fetchDomain, saveDomain, SEVERITY_LEVEL_ERROR, SEVERITY_LEVEL_WARN} from "@glass/domainproperties"

interface IAppState {
    dirty: boolean
    domain: DomainDesign
    domainId: number
    messages?: List<BannerMessage>,
    queryName: string
    returnUrl: string
    schemaName: string
    showConfirm: boolean
    submitting: boolean
}

interface BannerMessage {
    message?: string,
    messageType?: string,
}

export class App extends React.PureComponent<any, Partial<IAppState>> {

    constructor(props) {
        super(props);

        const { domainId, schemaName, queryName, returnUrl } = ActionURL.getParameters();

        let messages = List<BannerMessage>().asMutable();
        if (!((schemaName && queryName) || domainId)) {
            let msg =  'Missing required parameter: domainId or schemaName and queryName.';
            let msgType = 'danger';
            let bannerMsg ={message : msg, messageType : msgType};
            messages.push(bannerMsg);
        }

        this.state = {
            schemaName,
            queryName,
            domainId,
            returnUrl,
            submitting: false,
            messages: messages.asImmutable(),
            showConfirm: false,
            dirty: false
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
        const { domain, submitting } = this.state;

        if (submitting) {
            return;
        }

        this.setState({
            submitting: true
        });

        // saveDomain(domain, 'VarList', options, name )
        saveDomain(domain)
            .then((savedDomain) => {

                this.setState(() => ({
                    domain: savedDomain,
                    submitting: false,
                    dirty: false
                }));

                this.showMessage("Save Successful", 'info', 0);
                window.scrollTo(0, 0);

                if (navigate) {
                    this.navigate();
                }
            })
            .catch((badDomain) => {

                this.showBannerMessages(badDomain);

                window.scrollTo(0, 0);

                this.setState(() => ({
                    domain: badDomain,
                    submitting: false
                }));
            })
    };


    showBannerMessages = (domain: any) => {

        if (domain.domainException && domain.domainException.errors && domain.domainException.errors.size > 0) {

            let msgList = List<BannerMessage>().asMutable();
            let errMsg = this.getErrorBannerMessage(domain);
            if (errMsg !== undefined) {
                msgList.push({message: errMsg, messageType: 'danger'});
            }

            let warnMsg = this.getWarningBannerMessage(domain);
            if (warnMsg !== undefined) {
                msgList.push({message: warnMsg, messageType: 'warning'})
            }

            this.setState(() => ({
                messages: msgList.asImmutable()
            }));

        }
        else {

            this.setState(() => ({
                messages: List<BannerMessage>()
            }));
        }
    };

    getErrorBannerMessage = (domain: any) => {

        if (domain && domain.domainException && domain.domainException.errors) {
            let errors = domain.domainException.get('errors').filter(e => {
                return e && (e.severity === SEVERITY_LEVEL_ERROR)
            });

            if (errors && errors.size > 0) {
                if (errors.size > 1) {
                    return "Multiple fields contain issues that need to be fixed. Review the red highlighted fields below for more information.";
                }
                else {
                    return errors.get(0).message;
                }
            }
        }
        return undefined;
    };

    getWarningBannerMessage = (domain: any) => {

        if (domain && domain.domainException && domain.domainException.errors) {
            let warnings = domain.domainException.get('errors').filter(e => {return e && (e.severity === SEVERITY_LEVEL_WARN)});

            if (warnings && warnings.size > 0) {
                if (warnings.size > 1) {
                    return "Multiple fields may require your attention. Review the yellow highlighted fields below for more information.";
                }
                else {
                    return (warnings.get(0).fieldName + " : " + warnings.get(0).message);
                }
            }
        }
        return undefined;
    };

    onChangeHandler = (newDomain, dirty) => {
        this.setState((state) => ({
            domain: newDomain,
            dirty: state.dirty || dirty // if the state is already dirty, leave it as such
        }));

        this.showBannerMessages(newDomain);
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
            // TODO if we don't have a returnUrl, should we just do a goBack()?
            window.location.href = returnUrl || ActionURL.buildURL('project', 'begin');
        });
    };

    hideConfirm = () => {
        this.setState(() => ({showConfirm: false}));
    };

    renderNavigateConfirm() {
        return (
            <ConfirmModal
                title='Confirm Leaving Page'
                msg='You have unsaved changes. Are you sure you would like to leave this page before saving your changes?'
                confirmVariant='success'
                onConfirm={this.navigate}
                onCancel={this.hideConfirm}
            />
        )
    }

    renderButtons() {
        const { submitting, dirty } = this.state;

        return (
            <WizardNavButtons
                cancel={this.onCancelBtnHandler}
                containerClassName=""
                includeNext={false}>
                <Button
                    type='submit'
                    bsClass='btn'
                    onClick={() => this.submitHandler(false)}
                    disabled={submitting || !dirty}>
                    Save
                </Button>
                <Button
                    type='submit'
                    bsClass='btn btn-success'
                    onClick={() => this.submitHandler(true)}
                    disabled={submitting || !dirty}>
                    Save And Finish
                </Button>
            </WizardNavButtons>
        )
    }

    render() {
        const { domain, messages, showConfirm } = this.state;
        const isLoading = domain === undefined && messages === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                { showConfirm && this.renderNavigateConfirm() }
                { messages && messages.size > 0 && messages.map((bannerMessage, idx) => {
                    return (<Alert key={idx} bsStyle={bannerMessage.messageType} onDismiss={() => this.dismissAlert(idx)}>{bannerMessage.message}</Alert>) })
                }
                { domain &&
                <DomainForm
                    domain={domain}
                    onChange={this.onChangeHandler}
                />}
                { domain && this.renderButtons() }
            </>
        )
    }
}