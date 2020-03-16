/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {Col, FormControl, Row, Button} from "react-bootstrap";
import {
    SchemaQuery,
    Alert,
    QueryGridModel,
    ManageDropdownButton,
    SelectionMenuItem,
    getStateQueryGridModel,
    QueryGridPanel
} from "@labkey/components";

interface StateProps {
    schemaName: string
    queryName: string,
    error: string,
    model: QueryGridModel
}

export class QueryGridPage extends React.Component<any, StateProps> {

    constructor(props: any) {
        super(props);

        this.state = {
            schemaName: undefined,
            queryName: undefined,
            error: undefined,
            model: undefined
        };
    }

    onSchemaNameChange = (evt) => {
        const value = evt.target.value;
        this.setState(() => ({schemaName: value}));
    };

    onQueryNameChange = (evt) => {
        const value = evt.target.value;
        this.setState(() => ({queryName: value}));
    };

    onApply = () => {
        const { schemaName, queryName } = this.state;
        let error;
        let model;

        if (!schemaName || !queryName) {
            error = 'You must enter a schema/query to view the QueryGridPanel.'
        }
        else {
            model = getStateQueryGridModel('querygrid', SchemaQuery.create(schemaName, queryName), {isPaged: true});
        }

        this.setState(() => ({model, error}));
    };

    render() {
        const { error, model } = this.state;

        return (
            <>
                <Row>
                    <Col xs={4}>Schema: <FormControl name={'schemaNameField'} type="text" onChange={this.onSchemaNameChange}/></Col>
                    <Col xs={4}>Query: <FormControl name={'queryNameField'} type="text" onChange={this.onQueryNameChange}/></Col>
                    <Col xs={4}><Button onClick={this.onApply}>Apply</Button></Col>
                </Row>
                <br/>
                {error && <Alert>{error}</Alert>}
                {model &&
                    <QueryGridPanel
                        header={'QueryGridPanel'}
                        model={model}
                        buttons={(updatedModel: QueryGridModel) => {
                            if (updatedModel) {
                                return (
                                    <ManageDropdownButton id={'componentmanage'}>
                                        <SelectionMenuItem
                                            id={'componentselectionmenu'}
                                            model={updatedModel}
                                            text={'Selection Based Menu Item'}
                                            onClick={() => console.log('SelectionMenuItem click: ' + updatedModel.selectedQuantity + ' selected.')}
                                        />
                                    </ManageDropdownButton>
                                )
                            }
                        }}
                    />
                }
            </>
        )
    }
}

