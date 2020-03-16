/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {List, Map} from "immutable";
import {
    SchemaQuery,
    IGridLoader,
    LoadingSpinner,
    EditableColumnMetadata,
    EditableGridPanel,
    getQueryGridModel,
    getStateQueryGridModel,
    gridInit
} from "@labkey/components";

const columnMetadata = Map<string, EditableColumnMetadata>({
    "Name": {
        readOnly: true
    },
    "Description": {
        placeholder: "Enter a description"
    }
});

export class EditableGridPage extends React.Component<any, any> {

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

    render() {
        const editorModelWithData  = getQueryGridModel("edit-with-data|assay/assaylist");

        if (!editorModelWithData) {
            return <LoadingSpinner/>;
        }

        return <EditableGridPanel
            model={editorModelWithData}
            columnMetadata={columnMetadata}
            initialEmptyRowCount={0}
            bordered={true}
            condensed={false}
            striped={true}
            allowAdd={true}
            allowBulkAdd={true}
            allowBulkRemove={true}
            allowBulkUpdate={true}
            addControlProps={{
                placement: 'top',
                nounPlural: 'rows',
                nounSingular: 'row'
            }}
            bulkAddProps={{
                title: 'Bulk Add'
            }}
        />;
    }
}

