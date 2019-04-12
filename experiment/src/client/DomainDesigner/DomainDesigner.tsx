/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import {Alert, Button, ButtonToolbar, Col, Row} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {LoadingSpinner} from "@glass/utils";
import {DomainForm, DomainDesign, clearFieldDetails, updateField, fetchDomain, saveDomain} from "@glass/domainproperties"

interface IDomainDesignerState {
    schemaName?: string,
    queryName?: string,
    domainId?: number,
    domain?: DomainDesign,
    message?: string,
    messageType?: string
}

const btnStyle = {
    margin: '10px 0 20px 10px',
    width: 120
};

export class App extends React.PureComponent<any, IDomainDesignerState> {

    constructor(props)
    {
        super(props);

        const schemaName = ActionURL.getParameter('schemaName');
        const queryName = ActionURL.getParameter('queryName');
        const domainId = ActionURL.getParameter('domainId');

        this.submitHandler = this.submitHandler.bind(this);
        this.getAlert = this.getAlert.bind(this);
        this.dismissAlert = this.dismissAlert.bind(this);
        this.onChangeHandler = this.onChangeHandler.bind(this);

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

    submitHandler() {

        saveDomain(this.state.domain)
            .then(domain => {
                let dd = clearFieldDetails(this.state.domain);
                this.setState(Object.assign({}, {domain: dd, message: 'Domain saved', messageType: 'success'}));
            })
            .catch(error => {
                this.setState({message: error.exception, messageType: 'danger'})
            });
    }

    onChangeHandler(evt) {
        let value = evt.target.value;
        if (evt.target.type === "checkbox") {
            value = evt.target.checked;
        }
        this.setState({domain: updateField(this.state.domain, evt.target.id, value)});
    }

    dismissAlert() {
        this.setState({message: null, messageType: null})
    }

    getAlert(message, messageType) {
        return (
            <Alert bsStyle={messageType} key={"domain-msg-" + Math.random()} onDismiss={this.dismissAlert}>{message}</Alert>
        )
    }

    onCancel() {
        location.reload();
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
                            <Button type='button' style={btnStyle} bsClass='btn' onClick={this.onCancel}>Cancel</Button>
                            <Button type='button' style={btnStyle} bsClass='btn btn-success' onClick={this.submitHandler} >Save Changes</Button>
                        </ButtonToolbar>
                    </Col>
                </Row>
                { message ? this.getAlert(message, messageType) : '' }
                <DomainForm domain={domain} onSubmit={this.submitHandler} onChange={this.onChangeHandler}/>
            </>
        )
    }
}

