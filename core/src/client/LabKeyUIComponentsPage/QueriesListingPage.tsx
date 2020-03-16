/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {Col, FormControl, Row, Button} from "react-bootstrap";
import {Alert, QueriesListing} from "@labkey/components";

interface StateProps {
    inputValue: string
    schemaName: string
}

export class QueriesListingPage extends React.Component<any, StateProps> {

    constructor(props: any) {
        super(props);

        this.state = {
            inputValue: undefined,
            schemaName: undefined
        };
    }

    onSchemaNameChange = (evt) => {
        const value = evt.target.value;
        this.setState(() => ({inputValue: value}));
    };

    onApply = () => {
        this.setState((state) => ({schemaName: state.inputValue}));
    };

    render() {
        const { schemaName } = this.state;

        return (
            <>
                <Row>
                    <Col xs={6}>Schema: <FormControl name={'schemaNameField'} type="text" onChange={this.onSchemaNameChange}/></Col>
                    <Col xs={6}><Button onClick={this.onApply}>Apply</Button></Col>
                </Row>
                <br/>
                {schemaName && <QueriesListing schemaName={schemaName} asPanel={true}/>}
            </>
        )
    }
}

