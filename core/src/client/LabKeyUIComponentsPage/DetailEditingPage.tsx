/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {Col, FormControl, Row} from "react-bootstrap";
import {
    SchemaQuery,
    Alert,
    QueryGridModel,
    DetailEditing,
    getQueryGridModel,
    getStateQueryGridModel,
    gridInit, gridInvalidate
} from "@labkey/components";

interface StateProps {
    schemaName: string
    queryName: string
    keyValue: string
}

export class DetailEditingPage extends React.Component<any, StateProps> {

    constructor(props: any) {
        super(props);

        this.state = {
            schemaName: undefined,
            queryName: undefined,
            keyValue: undefined
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

    onKeyValueChange = (evt) => {
        const value = evt.target.value;
        this.setState(() => ({keyValue: value}));
    };

    getQueryGridModel(): QueryGridModel {
        const { schemaName, queryName, keyValue } = this.state;
        const model = getStateQueryGridModel('detailediting', SchemaQuery.create(schemaName, queryName), {}, keyValue);
        const stateModel = getQueryGridModel(model.getId()) || model;

        gridInit(stateModel, true, this);

        return stateModel;
    }

    onUpdate = () => {
        gridInvalidate(this.getQueryGridModel(), true, this);
    };

    render() {
        const { schemaName, queryName, keyValue } = this.state;
        let body;
        let message;

        if (!schemaName || !queryName || !keyValue) {
            body = <Alert>You must enter a schema/query/key to view the detail form.</Alert>;
        }
        else {
            const model = this.getQueryGridModel();
            if (model.isLoaded && model.queryInfo && !model.queryInfo.isAppEditable()) {
                message = <Alert bsStyle={'info'}>This schema/query is not set as editable on this page.</Alert>
            }

            body = <DetailEditing
                queryModel={model}
                canUpdate={LABKEY.user.canUpdate}
                onUpdate={this.onUpdate}
            />
        }

        return (
            <>
                <Row>
                    <Col xs={4}>Schema: <FormControl name={'schemaNameField'} type="text" onChange={this.onSchemaNameChange}/></Col>
                    <Col xs={4}>Query: <FormControl name={'queryNameField'} type="text" onChange={this.onQueryNameChange}/></Col>
                    <Col xs={4}>Row Key: <FormControl name={'keyValueField'} type="text" onChange={this.onKeyValueChange}/></Col>
                </Row>
                <br/>
                {message}
                {body}
            </>
        )
    }
}

