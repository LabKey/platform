/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import { Col, FormControl, Row, Button } from "react-bootstrap";
import {
    SchemaQuery,
    Alert,
    QueryGridModel,
    Detail,
    getQueryGridModel,
    getStateQueryGridModel,
    gridInit,
    DetailEditing,
    gridInvalidate
} from "@labkey/components";
import { getServerContext } from "@labkey/api";

interface Props {
    editable: boolean
}

interface State {
    schemaName: string
    queryName: string
    keyValue: string,
    model: QueryGridModel,
    error: string
}

export class DetailPage extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);

        this.state = {
            schemaName: undefined,
            queryName: undefined,
            keyValue: undefined,
            model: undefined,
            error: undefined
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

    onApply = () => {
        const { schemaName, queryName, keyValue } = this.state;
        let error;
        let model;

        if (!schemaName || !queryName || !keyValue) {
            error = 'You must enter a schema/query/key to view the detail form.'
        }
        else {
            model = getStateQueryGridModel('detail', SchemaQuery.create(schemaName, queryName), {}, keyValue);
            const stateModel = getQueryGridModel(model.getId()) || model;
            gridInit(stateModel, true, this);
        }

        this.setState(() => ({model, error}));
    };

    getQueryGridModel(): QueryGridModel {
        const { model } = this.state;
        return model ? getQueryGridModel(model.getId()) || model : undefined;
    }

    onUpdate = () => {
        gridInvalidate(this.getQueryGridModel(), true, this);
    };

    render() {
        const { editable } = this.props;
        const { error } = this.state;
        const queryModel = this.getQueryGridModel();

        let message = error;
        if (queryModel) {
            if (queryModel.message) {
                message = queryModel.message;
            }
            else if (queryModel.isLoaded && queryModel.queryInfo && !queryModel.queryInfo.isAppEditable()) {
                message = 'This schema/query is not set as editable on this page.'
            }
        }

        return (
            <>
                <Row>
                    <Col xs={4}>Schema: <FormControl name={'schemaNameField'} type="text" onChange={this.onSchemaNameChange}/></Col>
                    <Col xs={4}>Query: <FormControl name={'queryNameField'} type="text" onChange={this.onQueryNameChange}/></Col>
                    <Col xs={3}>Row Key: <FormControl name={'keyValueField'} type="text" onChange={this.onKeyValueChange}/></Col>
                    <Col xs={1}><Button onClick={this.onApply}>Apply</Button></Col>
                </Row>
                <br/>
                {message && <Alert>{message}</Alert>}
                {queryModel && !editable && <Detail queryModel={queryModel} asPanel={true}/>}
                {queryModel && editable && <DetailEditing queryModel={queryModel} canUpdate={getServerContext().user.canUpdate} onUpdate={this.onUpdate}/>}
            </>
        )
    }
}

