/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {Col, FormControl, Row} from "react-bootstrap";
import {SchemaQuery, Alert, QueryGridModel, ManageDropdownButton, SelectionMenuItem, getStateQueryGridModel, QueryGridPanel} from "@labkey/components";

interface StateProps {
    schemaName: string
    queryName: string
}

export class QueryGridPage extends React.Component<any, StateProps> {

    constructor(props: any) {
        super(props);

        this.state = {
            schemaName: undefined,
            queryName: undefined
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

    render() {
        const { schemaName, queryName } = this.state;

        let body;
        if (!schemaName || !queryName) {
            body = <Alert>You must enter a schema/query to view the grid.</Alert>;
        }
        else {
            const model = getStateQueryGridModel('querygrid', SchemaQuery.create(schemaName, queryName), {isPaged: true});
            body = <QueryGridPanel
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
            />;
        }

        return (
            <>
                <Row>
                    <Col xs={6}>Schema: <FormControl name={'schemaNameField'} type="text" onChange={this.onSchemaNameChange}/></Col>
                    <Col xs={6}>Query: <FormControl name={'queryNameField'} type="text" onChange={this.onQueryNameChange}/></Col>
                </Row>
                <br/>
                {body}
            </>
        )
    }
}

