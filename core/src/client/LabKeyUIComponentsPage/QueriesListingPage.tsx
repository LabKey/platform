/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {Col, FormControl, Row} from "react-bootstrap";
import {Alert, QueriesListing} from "@labkey/components";

interface StateProps {
    schemaName: string
}

export class QueriesListingPage extends React.Component<any, StateProps> {

    constructor(props: any) {
        super(props);

        this.state = {
            schemaName: undefined
        };
    }

    onSchemaNameChange = (evt) => {
        const value = evt.target.value;
        this.setState(() => ({schemaName: value}));
    };

    render() {
        const { schemaName } = this.state;

        let body;
        if (!schemaName) {
            body = <Alert>You must enter a schema name to view the listing.</Alert>;
        }
        else {
            body = <QueriesListing schemaName={schemaName} asPanel={true}/>;
        }

        return (
            <>
                <Row>
                    <Col xs={6}>Schema: <FormControl name={'schemaNameField'} type="text" onChange={this.onSchemaNameChange}/></Col>
                </Row>
                <br/>
                {body}
            </>
        )
    }
}

