/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import {Alert, Button, ButtonToolbar, Col, Panel, Row} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {DOMAIN_FORM_ID, DomainDesign} from "./models";
import {LoadingSpinner} from "@glass/utils";
import {clearFieldDetails, fetchDomain, saveDomain, updateField} from "./actions";
import DomainForm from "./DomainForm";

type State = {
    schemaName,
    queryName,
    domainId: number,
    domain?: DomainDesign,
    message?: string,
    messageType?: string
}

const btnStyle = {
    margin: '10px 0 20px 10px',
    width: 120
};

export class App extends React.Component<any, State> {

    constructor(props)
    {
        super(props);

        const schemaName = ActionURL.getParameter('schemaName');
        const queryName = ActionURL.getParameter('queryName');
        const domainId = ActionURL.getParameter('domainId');

        this.submitHandler = this.submitHandler.bind(this);
        this.getAlert = this.getAlert.bind(this);
        this.dismissAlert = this.dismissAlert.bind(this);

        this.state = {
            schemaName,
            queryName,
            domainId,
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

    submitHandler(evt: any) {

        saveDomain(this.state.domain)
            .then(domain => {
                let dd = clearFieldDetails(this.state.domain);
                this.setState(Object.assign({}, {domain: dd, message: 'Domain saved', messageType: 'success'}));
            })
            .catch(error => {
                this.setState({message: error.exception, messageType: 'danger'})
            });
    }

    dismissAlert() {
        this.setState({message: null, messageType: null})
    }

    getAlert(message, messageType) {
        return (
            <Alert bsStyle={messageType} key={"domain-msg-" + Math.random()} onDismiss={this.dismissAlert}>{message}</Alert>
        )
    }

    render() {
        const { domain, message, messageType } = this.state;
        const isLoading = domain === undefined && message === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                <Row>
                    <Col xs={12}>
                        <ButtonToolbar>
                            <Button type='button' style={btnStyle} bsClass='btn'>Cancel</Button>
                            <Button type='button' style={btnStyle} bsClass='btn btn-success' onClick={this.submitHandler} >Save Changes</Button>
                        </ButtonToolbar>
                    </Col>
                </Row>
                { message ? this.getAlert(message, messageType) : '' }
                <DomainForm domainDesign = {domain} id={DOMAIN_FORM_ID} />
            </>
        )
    }
}

