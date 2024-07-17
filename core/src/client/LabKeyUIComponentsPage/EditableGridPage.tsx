/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import {
    applyEditableGridChangesToModels,
    QueryModel,
    withQueryModels,
    EditableGridChange,
    EditableGridPanel,
    EditorModel,
    loadEditorModelData,
    InjectedQueryModels,
    LoadingSpinner,
    Alert,
    resolveErrorMessage,
} from '@labkey/components';

import { SchemaQueryInputContext, SchemaQueryInputProvider } from './SchemaQueryInputProvider';

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
                const editorModelData = await loadEditorModelData(model);
                const editorModel_ = new EditorModel({ id: model.id, ...editorModelData });
                setModels({ dataModel: model, editorModel: editorModel_ });
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
