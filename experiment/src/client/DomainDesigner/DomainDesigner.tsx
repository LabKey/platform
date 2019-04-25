/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import {Button, ButtonToolbar, Col, Row} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {LoadingSpinner, Alert} from "@glass/base";
import {DomainForm, DomainDesign, clearFieldDetails, fetchDomain, saveDomain} from "@glass/domainproperties"

interface IDomainDesignerState {
    schemaName?: string,
    queryName?: string,
    domainId?: number,
    returnUrl?: string,
    domain?: DomainDesign,
    submitting: boolean,
    message?: string,
    messageType?: string
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
            message: ((schemaName && queryName) || domainId) ? undefined : 'Missing required parameter: domainId or schemaName and queryName.'
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
    }

    submitHandler = () => {
        const { domain } = this.state;

        this.setState(() => ({submitting: true}));

        saveDomain(domain)
            .then((success) => {
                const newDomain = clearFieldDetails(domain);

                this.setState(() => ({
                    domain: newDomain,
                    submitting: false,
                    message: 'Domain saved successfully.',
                    messageType: 'success'
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

    onChangeHandler = (newDomain) => {
        this.setState(() => ({
            domain: newDomain
        }));
    };

    dismissAlert = () => {
        this.setState({message: null, messageType: null})
    };

    onCancel = () => {
        const { returnUrl } = this.state;
        window.location.href = returnUrl || ActionURL.buildURL('project', 'begin');
    };

    render() {
        const { domain, message, messageType, submitting } = this.state;
        const isLoading = domain === undefined && message === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                { domain && <Row>
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
                { message && <Alert bsStyle={messageType} onDismiss={this.dismissAlert}>{message}</Alert> }
                { domain && <DomainForm domain={domain} onChange={this.onChangeHandler}/>}
            </>
        )
    }
}

