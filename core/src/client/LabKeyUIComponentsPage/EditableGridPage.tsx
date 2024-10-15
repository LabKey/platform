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
    EditableGrid,
} from '@labkey/components';

import { SchemaQueryInputContext, SchemaQueryInputProvider } from './SchemaQueryInputProvider';

class Loader implements EditableGridLoader {
    columns: QueryColumn[];
    id: string;
    mode: EditorMode;
    queryInfo: QueryInfo;
    queryModel: QueryModel;

    constructor(queryModel: QueryModel) {
        this.mode = EditorMode.Insert;
        this.queryInfo = queryModel.queryInfo;
        this.queryModel = queryModel;
    }

    async fetch(): Promise<GridResponse> {
        return {
            data: fromJS(this.queryModel.rows),
            dataIds: fromJS(this.queryModel.orderedRows),
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
                const loader = new Loader(model);
                const em = await initEditorModel(loader);
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
        <div className="panel panel-default">
            <div className="panel-heading">EditableGridPanel</div>
            <div className="panel-body">
                <EditableGrid
                    allowAdd
                    allowBulkAdd
                    allowBulkRemove
                    allowBulkUpdate
                    bulkAddProps={{ title: 'Bulk Add' }}
                    editorModel={editorModel}
                    onChange={onGridChange}
                />
            </div>
        </div>
    );
});

export const EditableGridPageWithQueryModels = withQueryModels(EditableGridPageBody);

export const EditableGridPageImpl: FC<SchemaQueryInputContext> = ({ queryConfig }) => {
    if (!queryConfig) return null;
    return <EditableGridPageWithQueryModels autoLoad key={queryConfig.id} queryConfigs={{ model: queryConfig }} />;
};

export const EditableGridPage = SchemaQueryInputProvider(EditableGridPageImpl);
