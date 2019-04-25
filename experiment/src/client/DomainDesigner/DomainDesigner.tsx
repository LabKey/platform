/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import {Panel} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {DomainDesign, DomainFieldsDisplay, fetchDomain} from "@glass/domainproperties";
import {Alert, LoadingSpinner, SchemaQuery} from "@glass/base";

type State = {
    schemaQuery: SchemaQuery,
    domain?: DomainDesign,
    message?: string
}

export class App extends React.Component<any, State> {

    constructor(props)
    {
        super(props);

        const schemaName = ActionURL.getParameter('schemaName');
        const queryName = ActionURL.getParameter('queryName');
        const schemaQuery = schemaName && queryName ? SchemaQuery.create(schemaName, queryName) : undefined;

        this.state = {
            schemaQuery,
            message: schemaQuery ? undefined : 'Missing required parameter: schemaName or queryName.'
        };
    }

    componentDidMount() {
        const { schemaQuery } = this.state;

        if (schemaQuery) {
            fetchDomain(schemaQuery)
                .then(domain => {
                    this.setState({domain});
                })
                .catch(error => {
                    this.setState({message: error})
                });
        }
    }

    render() {
        const { domain, message } = this.state;
        const isLoading = domain === undefined && message === undefined;

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
                        <div className={"panel-title"}>{domain.name}</div>
                    </Panel.Heading>
                    <Panel.Body>
                        <p>Description: {domain.description}</p>
                    </Panel.Body>
                </Panel>
                <DomainFieldsDisplay title={"Field Properties"} domain={domain} />
            </>
        )
    }
}

