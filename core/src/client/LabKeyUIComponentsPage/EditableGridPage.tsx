/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, {PureComponent} from 'react'
import {List, Map} from "immutable";
import {
    SchemaQuery,
    IGridLoader,
    EditableColumnMetadata,
    EditableGridPanelDeprecated,
    getQueryGridModel,
    getStateQueryGridModel,
    gridInit,
    QueryGridModel
} from "@labkey/components";

import {SchemaQueryInputContext, SchemaQueryInputProvider} from "./SchemaQueryInputProvider";

const columnMetadata = Map<string, EditableColumnMetadata>({
    "Name": {
        readOnly: true
    },
    "Description": {
        placeholder: "Enter a description"
    }
});

const defaultProps = {
    title: 'EditableGridPanel',
    initialEmptyRowCount: 0,
    bordered: true,
    condensed: false,
    striped: true,
    allowAdd: true,
    allowBulkAdd: true,
    allowBulkRemove: true,
    allowBulkUpdate: true,
    addControlProps: {
        nounPlural: 'rows',
        nounSingular: 'row'
    },
    bulkAddProps: {
        title: 'Bulk Add'
    },
};

class EditableGridPageImpl extends PureComponent<SchemaQueryInputContext> {

    componentWillMount() {
        this.initEditableModel();
    }

    initEditableModel() {
        const loader: IGridLoader = {
            fetch: (model) => {
                return new Promise((resolve) => {
                    const data = Map<any, any>({
                        "123": Map<string, any>({
                            "Name": List([
                                {
                                    displayValue: "Amino Acid",
                                    value: "Amino Acid"
                                }
                            ])
                        }),
                        "243": Map<string, any>({
                            "Name": List([
                                {
                                    displayValue: "Flow",
                                    value: "Flow"
                                }
                            ])
                        })
                    });

                    resolve({
                        data,
                        dataIds: data.keySeq().toList(),
                    });
                });
            }
        };

        const editorModelWithData = getStateQueryGridModel('edit-with-data',
            SchemaQuery.create('assay', 'AssayList'), {
                loader,
                editable: true
            }
        );

        gridInit(editorModelWithData, true, this);
    }

    getEditorQueryGridModel = (): QueryGridModel => {
        const { model } = this.props;
        const editModel = getStateQueryGridModel('component-editormodel', SchemaQuery.create(model.schema, model.query), () => {
            return {
                editable: true,
                queryInfo: model.queryInfo,
                bindURL: false,
                loader: {
                    fetch: () => {
                        const gridData = Map<string, Map<string, any>>();
                        return new Promise(resolve => {
                            resolve({
                                data: gridData,
                                dataIds: gridData.keySeq().toList(),
                            });
                        });
                    },
                },
            };
        });

        return getQueryGridModel(editModel.getId()) || editModel;
    };

    render() {
        const { model } = this.props;
        const editorModelWithData  = getQueryGridModel("edit-with-data|assay/assaylist");

        // TODO hack to force rerender after model is loaded
        if (model && !model.isLoaded) {
            window.setTimeout(() => this.forceUpdate(), 500);
        }

        return (
            <>
                {model
                    ? <EditableGridPanelDeprecated
                        {...defaultProps}
                        model={this.getEditorQueryGridModel()}
                    />
                    : <EditableGridPanelDeprecated
                        {...defaultProps}
                        model={editorModelWithData}
                        columnMetadata={columnMetadata}
                    />
                }
            </>
        );
    }
}

export const EditableGridPage = SchemaQueryInputProvider(EditableGridPageImpl);

