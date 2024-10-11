/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import { fromJS } from 'immutable';
import {
    QueryModel,
    withQueryModels,
    EditableGridChange,
    EditableGridPanel,
    EditableGridLoader,
    EditorModel,
    initEditorModel,
    InjectedQueryModels,
    LoadingSpinner,
    Alert,
    resolveErrorMessage,
    GridResponse,
    EditorMode,
    QueryInfo,
    QueryColumn,
} from '@labkey/components';

import { SchemaQueryInputContext, SchemaQueryInputProvider } from './SchemaQueryInputProvider';

class Loader implements EditableGridLoader {
    columns: QueryColumn[];
    id: string;
    mode: EditorMode;
    queryInfo: QueryInfo;

    constructor(queryInfo: QueryInfo) {
        this.mode = EditorMode.Insert;
        this.queryInfo = queryInfo;
    }

    async fetch(model: QueryModel): Promise<GridResponse> {
        return {
            data: fromJS(model.rows),
            dataIds: fromJS(model.orderedRows),
        };
    }
}

const EditableGridPageBody: FC<InjectedQueryModels> = memo(props => {
    const { queryModels } = props;
    const { model } = queryModels;
    const [editorModel, setEditorModel] = useState<EditorModel>();
    const [error, setError] = useState<string>();
    const isLoaded = !model.isLoading;

    useEffect(() => {
        if (model.isLoading) return;

        (async () => {
            try {
                const loader = new Loader(model.queryInfo);
                const em = await initEditorModel(model, loader);
                setEditorModel(em);
            } catch (err) {
                console.error(err);
                setError(resolveErrorMessage(err));
            }
        })();
    }, [model]);

    const onGridChange = useCallback<EditableGridChange>((event, changes) => {
        setEditorModel(current => current.applyChanges(changes));
    }, []);

    if (error || model.hasLoadErrors) {
        return <Alert>{error ?? model.loadErrors.join(' ')}</Alert>;
    }
    if (!isLoaded || !editorModel) {
        return <LoadingSpinner />;
    }

    return (
        <EditableGridPanel
            allowAdd
            allowBulkAdd
            allowBulkRemove
            allowBulkUpdate
            bulkAddProps={{ title: 'Bulk Add' }}
            editorModel={editorModel}
            onChange={onGridChange}
            title="EditableGridPanel"
        />
    );
});

export const EditableGridPageWithQueryModels = withQueryModels(EditableGridPageBody);

export const EditableGridPageImpl: FC<SchemaQueryInputContext> = ({ queryConfig }) => {
    if (!queryConfig) return null;
    return <EditableGridPageWithQueryModels autoLoad key={queryConfig.id} queryConfigs={{ model: queryConfig }} />;
};

export const EditableGridPage = SchemaQueryInputProvider(EditableGridPageImpl);
