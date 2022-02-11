/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { FC, memo, useCallback, useEffect, useState } from 'react'
import { List, Map } from 'immutable';
import {
    isLoading,
    QueryModel,
    withQueryModels,
    EditableGridPanel,
    EditorModelProps,
    EditorModel,
    loadEditorModelData,
    InjectedQueryModels,
    LoadingSpinner,
    Alert,
    resolveErrorMessage,
} from "@labkey/components";

import {SchemaQueryInputContext, SchemaQueryInputProvider} from "./SchemaQueryInputProvider";

const defaultProps = {
    title: 'EditableGridPanel',
    bordered: true,
    striped: true,
    allowAdd: true,
    allowBulkAdd: true,
    allowBulkRemove: true,
    allowBulkUpdate: true,
    bulkAddProps: {
        title: 'Bulk Add'
    },
};

const EditableGridPageBody: FC<InjectedQueryModels> = memo(props => {
    const { queryModels, actions } = props;
    const { model } = queryModels;
    const [dataModel, setDataModel] = useState<QueryModel>();
    const [editorModel, setEditorModel] = useState<EditorModel>();
    const [error, setError] = useState<String>();

    useEffect(() => {
        (async () => {
            if (!isLoading(model.rowsLoadingState) && !editorModel) {
                try {
                    const editorModelData = await loadEditorModelData(model);
                    setEditorModel(new EditorModel({
                        id: model.id,
                        ...editorModelData,
                    }));
                    setDataModel(model);
                } catch (err) {
                    console.error(err);
                    setError(resolveErrorMessage(err))
                }
            }
        })();
    }, [model, editorModel]);

    const onGridChange = useCallback((
        editorModelChanges: Partial<EditorModelProps>,
        dataKeys?: List<any>,
        data?: Map<any, Map<string, any>>
    ) => {
        const orderedRows = dataKeys?.toJS();
        const rows = data?.toJS();
        if (orderedRows !== undefined && rows !== undefined) {
            setDataModel(dataModel.mutate({ orderedRows, rows }));
        }

        // console.log('editorModelChanges', editorModelChanges?.cellValues?.toJS());
        setEditorModel(editorModel.merge(editorModelChanges) as EditorModel);
    }, [editorModel, dataModel]);

    if (model.loadErrors?.length > 0) {
        return <Alert>{model.loadErrors.join(' ')}</Alert>;
    }
    if (!editorModel || !dataModel) {
        return <LoadingSpinner />
    }
    if (error) {
        return <Alert>{error}</Alert>;
    }
    // console.log('editorModel', editorModel?.cellValues?.toJS());

    return (
        <EditableGridPanel
            {...defaultProps}
            model={dataModel}
            editorModel={editorModel}
            onChange={onGridChange}
        />
    )
});

export const EditableGridPageWithQueryModels = withQueryModels(EditableGridPageBody);

export const EditableGridPageImpl: FC<SchemaQueryInputContext> = props => {
    const { queryConfig } = props;
    const queryConfigs = { model: queryConfig };

    if (!queryConfig)
        return null;

    return <EditableGridPageWithQueryModels autoLoad key={queryConfig.id} queryConfigs={queryConfigs}/>
}

export const EditableGridPage = SchemaQueryInputProvider(EditableGridPageImpl);

