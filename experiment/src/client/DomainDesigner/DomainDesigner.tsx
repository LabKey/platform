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
import * as React from 'react'
import {Button, ButtonToolbar, Col, Row} from "react-bootstrap";
import {ActionURL, Utils} from "@labkey/api";
import {LoadingSpinner, Alert, ConfirmModal} from "@glass/base";
import {DomainForm, DomainDesign, fetchDomain, saveDomain} from "@glass/domainproperties"

interface IAppState {
    dirty: boolean
    domain: DomainDesign
    domainId: number
    message: string
    messageType: string
    queryName: string
    returnUrl: string
    schemaName: string
    showConfirm: boolean
    submitting: boolean
}

export class App extends React.PureComponent<any, Partial<IAppState>> {

    constructor(props) {
        super(props);

        const { domainId, schemaName, queryName, returnUrl } = ActionURL.getParameters();

        this.state = {
            schemaName,
            queryName,
            domainId,
            returnUrl,
            submitting: false,
            message: ((schemaName && queryName) || domainId) ? undefined : 'Missing required parameter: domainId or schemaName and queryName.',
            showConfirm: false,
            dirty: false
        };
    }

    componentDidMount() {
        const { schemaName, queryName, domainId } = this.state;

        if ((schemaName && queryName) || domainId) {
            fetchDomain(domainId, schemaName, queryName)
                .then(domain => {
                    this.setState(() => ({domain}));
                })
                .catch(error => {
                    this.setState(() => ({message: error.exception, messageType: 'danger'}));
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

    submitHandler = () => {
        const { domain, submitting } = this.state;

        if (submitting) {
            return;
        }

        this.setState({
            submitting: true
        });

        saveDomain(domain)
            .then((savedDomain) => {
                this.navigate();
            })
            .catch((error) => {
                const msg = Utils.isObject(error) ? error.exception : error;
                this.showMessage(msg, 'danger', {
                    submitting: false
                });
            })
    };

    onChangeHandler = (newDomain, dirty) => {
        this.setState((state) => ({
            domain: newDomain,
            dirty: state.dirty || dirty // if the state is already dirty, leave it as such
        }));
    };

    dismissAlert = () => {
        this.setState({
            message: undefined,
            messageType: undefined
        });
    };

    showMessage = (message: string, messageType: string, additionalState?: Partial<IAppState>) => {
        this.setState(Object.assign({}, additionalState, {
            message,
            messageType
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

    render() {
        const { domain, message, messageType, submitting, showConfirm, dirty } = this.state;
        const isLoading = domain === undefined && message === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                { domain &&
                <Row>
                    <Col xs={12}>
                        <ButtonToolbar>
                            <Button
                                type='button'
                                className={'domain-designer-button'}
                                bsClass='btn'
                                onClick={this.onCancelBtnHandler}
                                disabled={submitting}>
                                Cancel
                            </Button>
                            <Button
                                type='button'
                                className={'domain-designer-button'}
                                bsClass='btn btn-success'
                                onClick={this.submitHandler}
                                disabled={submitting || !dirty}>
                                Save Changes
                            </Button>
                        </ButtonToolbar>
                    </Col>
                </Row>}
                { showConfirm && this.renderNavigateConfirm() }
                { message && <Alert bsStyle={messageType} onDismiss={this.dismissAlert}>{message}</Alert> }
                { domain &&
                <DomainForm
                    domain={domain}
                    onChange={this.onChangeHandler}
                />}
            </>
        )
    }
}

