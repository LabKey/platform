/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import { fromJS } from 'immutable';
import {
    applyEditableGridChangesToModels,
    initEditableGridModel,
    QueryModel,
    withQueryModels,
    EditableGridChange,
    EditableGridPanel,
    EditableGridLoader,
    EditorModel,
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

type EditableGridModels = {
    dataModel: QueryModel;
    editorModel: EditorModel;
};

const EditableGridPageBody: FC<InjectedQueryModels> = memo(props => {
    const { queryModels } = props;
    const { model } = queryModels;
    const [models, setModels] = useState<EditableGridModels>();
    const [error, setError] = useState<string>();
    const isLoaded = !model.isLoading;

    useEffect(() => {
        if (model.isLoading) return;

        (async () => {
            try {
                const loader = new Loader(model.queryInfo);
                const editorModelData = await initEditableGridModel(
                    model,
                    new EditorModel({ id: model.id }),
                    loader,
                    model
                );
                setModels(editorModelData);
            } catch (err) {
                console.error(err);
                setError(resolveErrorMessage(err));
            }
        })();
    }, [model]);

    const onGridChange = useCallback<EditableGridChange>((event, changes, dataKeys?, data?) => {
        setModels(models_ => {
            const { dataModels, editorModels } = applyEditableGridChangesToModels(
                [models_.dataModel],
                [models_.editorModel],
                changes,
                undefined,
                dataKeys,
                data
            );

            return { dataModel: dataModels[0], editorModel: editorModels[0] };
        });
    }, []);

    if (error || model.hasLoadErrors) {
        return <Alert>{error ?? model.loadErrors.join(' ')}</Alert>;
    }
    if (!isLoaded || !models) {
        return <LoadingSpinner />;
    }

    return (
        <EditableGridPanel
            allowAdd
            allowBulkAdd
            allowBulkRemove
            allowBulkUpdate
            bulkAddProps={{ title: 'Bulk Add' }}
            model={models.dataModel}
            editorModel={models.editorModel}
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
