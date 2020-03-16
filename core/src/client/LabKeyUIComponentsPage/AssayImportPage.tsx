/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {List} from "immutable";
import {ActionURL} from "@labkey/api";
import {
    fetchAllAssays,
    AssayDefinitionModel,
    LoadingSpinner,
    naturalSort,
    Alert,
    AssayImportPanels,
    AssayUploadResultModel,
    SelectInput
} from "@labkey/components";

const BASE_FILE_TYPES = ['.csv', '.tsv', '.txt', '.xlsx', '.xls'];
const SELECT_ID = 'assay-import-select';

interface State {
    assayDefinitions: List<AssayDefinitionModel>
    selected: AssayDefinitionModel,
    message: string
}

export class AssayImportPage extends React.Component<any, State> {

    constructor(props: any) {
        super(props);

        this.state = {
            assayDefinitions: undefined,
            selected: undefined,
            message: undefined
        };
    }

    componentDidMount() {
        fetchAllAssays('General')
            .then((response) => {
                const assayDefinitions = response
                    .sortBy(assay => assay.name, naturalSort)
                    .toList();

                this.setState(() => ({assayDefinitions}));
            });
    }

    onAssaySelectChange = (id, val, selected) => {
        this.setState(() => ({selected}));
    };

    getAssaySelectInput() {
        const { assayDefinitions, selected } = this.state;

        return (
            <SelectInput
                key={'assay-import-panels-select'}
                name={'assay-import-panels-select'}
                placeholder={"Select an assay..."}
                inputClass={'col-xs-4'}
                showLabel={false}
                formsy={false}
                multiple={false}
                required={true}
                valueKey={'id'}
                labelKey={'name'}
                value={selected ? selected.id : undefined}
                onChange={this.onAssaySelectChange}
                options={assayDefinitions.toArray()}
            />
        )
    }

    render() {
        const { assayDefinitions, selected, message } = this.state;

        if (assayDefinitions === undefined) {
            return <LoadingSpinner msg={'Loading GPAT assay definitions...'}/>
        }

        return (
            <>
                <div>
                    GPAT Assays: {this.getAssaySelectInput()}
                </div>
                {selected &&
                    <>
                        <Alert bsStyle={'info'}>NOTE: if you have the proper permissions, this will actually import an assay run.</Alert>
                        {message && <Alert bsStyle={'success'}>{message}</Alert>}
                        {/*TODO why doesn't the on file change handler get called with the FileAttachmentForm here but it does in the app?*/}
                        <AssayImportPanels
                            assayDefinition={selected}
                            onCancel={() => {
                                this.setState(() => ({
                                    selected: undefined,
                                    message: undefined
                                }));
                            }}
                            onSave={(response: AssayUploadResultModel) => {
                                this.setState(() => ({
                                    message: 'Assay run imported: #' + response.runId
                                }));
                            }}
                            onComplete={(response: AssayUploadResultModel) => {
                                window.location.href = ActionURL.buildURL('assay', 'assayResults', null, {
                                    rowId: response.assayId,
                                    "Data.Run/RowId~eq": response.runId
                                });
                            }}
                            acceptedPreviewFileFormats={BASE_FILE_TYPES.join(', ')}
                            allowBulkRemove={true}
                            allowBulkInsert={true}
                        />
                    </>
                }
            </>
        )
    }
}
