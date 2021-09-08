/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { FC, memo, useCallback, useState, useMemo } from 'react';
import { Col, FormControl, Row, Button } from "react-bootstrap";
import {
    Alert,
    DetailPanel,
    EditableDetailPanel,
    InjectedQueryModels,
    LoadingSpinner,
    withQueryModels,
    QueryConfigMap,
    useServerContext,
    SchemaQuery,
} from '@labkey/components';

interface Props {
    editable: boolean
}

const DetailPageBody: FC<Props & InjectedQueryModels> = memo(props => {
    const { actions, queryModels: { model }, editable } = props;
    const { user } = useServerContext();

    const onUpdate = useCallback(() => {
        actions.loadModel(model.id);
    }, [actions, model]);

    if (!model) {
        return;
    }
    if (model.queryInfoError || model.rowsError) {
        return <Alert>{model.queryInfoError || model.rowsError}</Alert>;
    }
    if (model.isLoading) {
        return <LoadingSpinner/>;
    }
    if (!model.queryInfo.isAppEditable()) {
        return <Alert>This schema/query is not set as editable on this page.</Alert>;
    }

    return (
        <>
            {!editable && <DetailPanel asPanel actions={actions} model={model} />}
            {editable && <EditableDetailPanel onUpdate={onUpdate} canUpdate={user.canUpdate} actions={actions} model={model} />}
        </>
    )
});

const DetailPageWithModels = withQueryModels<Props>(DetailPageBody);

export const DetailPage: FC<Props> = memo(props => {
    const [error, setError] = useState<string>();
    const [queryConfigs, setQueryConfigs] = useState<QueryConfigMap>({});

    const [schemaName, setSchemaName] = useState<string>();
    const onSchemaNameChange = useCallback((evt) => {
        const value = evt.target.value;
        setSchemaName(value);
    }, []);

    const [queryName, setQueryName] = useState<string>();
    const onQueryNameChange = useCallback((evt) => {
        const value = evt.target.value;
        setQueryName(value);
    }, []);

    const [keyValue, setKeyValue] = useState<string>();
    const onKeyValueChange = useCallback((evt) => {
        const value = evt.target.value;
        setKeyValue(value);
    }, []);

    const onApply = useCallback(() => {
        if (!schemaName || !queryName || !keyValue) {
            setError('You must enter a schema/query/key to view the detail form.');
        }
        else {
            setError(undefined);
            setQueryConfigs({
                model: {
                    keyValue: keyValue,
                    schemaQuery: SchemaQuery.create(schemaName, queryName),
                }
            });
        }
    }, [schemaName, queryName, keyValue]);

    const key = useMemo(() => {
        if (queryConfigs.model) {
            const { schemaName, queryName } = queryConfigs.model.schemaQuery;
            return [schemaName, queryName, queryConfigs.model.keyValue].join('|');
        } else {
            return undefined;
        }
    }, [queryConfigs]);

    return (
        <>
            <Row>
                <Col xs={4}>Schema: <FormControl name={'schemaNameField'} type="text" onChange={onSchemaNameChange}/></Col>
                <Col xs={4}>Query: <FormControl name={'queryNameField'} type="text" onChange={onQueryNameChange}/></Col>
                <Col xs={3}>Row Key: <FormControl name={'keyValueField'} type="text" onChange={onKeyValueChange}/></Col>
                <Col xs={1}><Button onClick={onApply}>Apply</Button></Col>
            </Row>
            <br/>
            {error && <Alert>{error}</Alert>}
            {!error && <DetailPageWithModels autoLoad key={key} queryConfigs={queryConfigs} {...props} />}
        </>
    );
});
