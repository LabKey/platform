/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import {Panel} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {DomainFieldsDisplay} from "@glass/domainproperties";
import {Alert, AssayProtocolModel, fetchProtocol, LoadingSpinner} from "@glass/base";

type State = {
    protocolId: number,
    protocol?: AssayProtocolModel,
    message?: string
}

export class App extends React.Component<any, State> {

    constructor(props)
    {
        super(props);

        const protocolId = ActionURL.getParameter('rowId');

        this.state = {
            protocolId,
            message: protocolId ? undefined : 'No protocolId parameter provided.'
        };
    }

    componentDidMount() {
        const { protocolId } = this.state;

        fetchProtocol(protocolId)
            .then(protocol => {
                this.setState({protocol});
            })
            .catch(error => {
                this.setState({message: error})
            });
    }

    renderDomains() {
        const { protocol } = this.state;

        return (
            <>
                {protocol.domains.map((domain, index) => (
                    <DomainFieldsDisplay key={index} domain={domain} />
                ))}
            </>
        )
    }

    render() {
        const { protocol, message } = this.state;
        const isLoading = protocol === undefined && message === undefined;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                <Panel>
                    <Panel.Heading>
                        <div className={"panel-title"}>{protocol.name}</div>
                    </Panel.Heading>
                    <Panel.Body>
                        <p>Provider: {protocol.providerName}</p>
                        <p>Description: {protocol.description}</p>
                    </Panel.Body>
                </Panel>
                {this.renderDomains()}
            </>
        )
    }
}

