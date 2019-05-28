/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import {Button, ButtonToolbar, Col, Row} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {LoadingSpinner, Alert, ConfirmModal} from "@glass/base";
import {DomainForm, DomainDesign, clearFieldDetails, fetchDomain, saveDomain} from "@glass/domainproperties"

interface StateProps {
    schemaName?: string,
    queryName?: string,
    domainId?: number,
    returnUrl?: string,
    domain?: DomainDesign,
    submitting: boolean,
    message?: string,
    messageType?: string,
    showConfirm: boolean,
    dirty: boolean
}

export class App extends React.PureComponent<any, StateProps> {

    constructor(props)
    {
        super(props);

        const schemaName = ActionURL.getParameter('schemaName');
        const queryName = ActionURL.getParameter('queryName');
        const domainId = ActionURL.getParameter('domainId');
        const returnUrl = ActionURL.getParameter('returnUrl');

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
        const { domain } = this.state;

        // NOTE: temp values for name and pk since those are not available inputs currently
        // const name = 'list_' + Math.floor(Math.random() * 10000);
        // const options = {
        //     keyName: domain.fields.size > 0 ? domain.fields.get(0).name : undefined
        // };

        this.setState(() => ({submitting: true}));

        // saveDomain(domain, 'VarList', options, name )
        saveDomain(domain)
            .then((savedDomain) => {
                const newDomain = clearFieldDetails(savedDomain);

                this.setState(() => ({
                    domain: newDomain,
                    submitting: false,
                    message: 'Domain saved successfully.',
                    messageType: 'success',
                    dirty: false
                }));

                window.setTimeout(() => {
                    this.dismissAlert();
                }, 5000);
            })
            .catch(error => {
                this.setState(() => ({
                    submitting: false,
                    message: error.exception,
                    messageType: 'danger'
                }));
            });
    };

    onChangeHandler = (newDomain, dirty) => {
        this.setState((state) => ({
            domain: newDomain,
            dirty: state.dirty || dirty // if the state is already dirty, leave it as such
        }));
    };

    dismissAlert = () => {
        this.setState(() => ({message: null, messageType: null}));
    };

    onCancelBtnHandler = () => {
        if (this.state.dirty) {
            this.setState(() => ({showConfirm: true}));
        }
        else {
            this.onConfirm();
        }
    };

    onConfirm = () => {
        const { returnUrl } = this.state;
        this.setState(() => ({dirty: false}), () => {
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
                onConfirm={this.onConfirm}
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

