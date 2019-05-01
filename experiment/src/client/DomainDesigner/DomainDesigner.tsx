/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import {Button, ButtonToolbar, Col, Row} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {LoadingSpinner, Alert} from "@glass/base";
import {DomainForm, DomainConfirm, DomainDesign, clearFieldDetails, fetchDomain, saveDomain} from "@glass/domainproperties"

interface IDomainDesignerState {
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

export class App extends React.PureComponent<any, IDomainDesignerState> {

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
            showConfirm: false,
            dirty: false
        };
    }

    componentDidMount() {
        const { schemaName, queryName, domainId } = this.state;

        if ((schemaName && queryName) || domainId) {
            fetchDomain(domainId, schemaName, queryName)
                .then(domain => {
                    this.setState({domain});
                })
                .catch(error => {
                    this.setState({message: error.exception, messageType: 'danger'})
                });
        }
        else {
            this.setState({domain: new DomainDesign()})
        }
    }

    submitHandler = () => {
        const { domain } = this.state;

        // Temp values for name and pk since those are not available inputs currently
        const name = 'list_' + Math.floor(Math.random() * 10000);
        const options = {
            keyName: domain.fields.get(0).name
        }

        this.setState(() => ({submitting: true}));

        saveDomain(domain, 'VarList', options, name )
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
        this.setState(() => ({
            domain: newDomain,
            dirty
        }));
    };

    dismissAlert = () => {
        this.setState({message: null, messageType: null})
    };

    onCancel = () => {
        if( this.state.dirty ) {
            this.setState({showConfirm: true})
        }
        else {
            this.onCancelConfirm();
        }
    };

    onCancelConfirm = () => {
        const { returnUrl } = this.state;
        window.location.href = returnUrl || ActionURL.buildURL('project', 'begin');
    };

    onCancelCancel = () => {
        this.setState({showConfirm: false})
    };

    render() {
        const { domain, message, messageType, submitting, showConfirm } = this.state;
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
                                onClick={this.onCancel}
                                disabled={submitting}>
                                Cancel
                            </Button>
                            <Button
                                type='button'
                                className={'domain-designer-button'}
                                bsClass='btn btn-success'
                                onClick={this.submitHandler}
                                disabled={submitting}>
                                Save Changes
                            </Button>
                        </ButtonToolbar>
                    </Col>
                </Row>}
                <DomainConfirm show={showConfirm} title='Confirm Leaving Page'
                               msg='You have unsaved data, leave page before saving data?'
                               onConfirm={this.onCancelConfirm}
                               onCancel={this.onCancelCancel}
                               confirmButtonText='Yes'
                               cancelButtonText='No'
                               confirmVariant='success'/>
                { message && <Alert bsStyle={messageType} onDismiss={this.dismissAlert}>{message}</Alert> }
                { domain && <DomainForm domain={domain}
                                        onChange={this.onChangeHandler}
                                        helpURL='https://www.labkey.org/Documentation/wiki-page.view?name=listDefineFields'
                                        helpNoun='list'
                            />}
            </>
        )
    }
}

