/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { PureComponent } from 'react';
import { ActionURL } from '@labkey/api';
import {
    Alert,
    AssayDefinitionModel,
    AssayImportPanels,
    AssayUploadResultModel,
    getAssayDefinitions,
    LoadingSpinner,
    naturalSortByProperty,
    SelectInput,
} from '@labkey/components';

const BASE_FILE_TYPES = ['.csv', '.tsv', '.txt', '.xlsx', '.xls'];

interface State {
    assayDefinitions: AssayDefinitionModel[];
    message: string;
    selected: AssayDefinitionModel;
}

export class AssayImportPage extends PureComponent<any, State> {
    state: Readonly<State> = {
        assayDefinitions: undefined,
        message: undefined,
        selected: undefined,
    };

    componentDidMount(): void {
        getAssayDefinitions({ type: 'General' }).then(response => {
            this.setState({ assayDefinitions: response.sort(naturalSortByProperty('name')) });
        });
    }

    onCancel = (): void => {
        this.setState({ message: undefined, selected: undefined });
    };

    onChange = (id, val, selected) => {
        this.setState({ selected });
    };

    onComplete = (response: AssayUploadResultModel): void => {
        window.location.href = ActionURL.buildURL('assay', 'assayResults', undefined, {
            rowId: response.assayId,
            'Data.Run/RowId~eq': response.runId,
        });
    };

    onSave = (response: AssayUploadResultModel): void => {
        this.setState({ message: 'Assay run imported: #' + response.runId });
    };

    render() {
        const { assayDefinitions, selected, message } = this.state;

        if (assayDefinitions === undefined) {
            return <LoadingSpinner msg="Loading GPAT assay definitions..." />;
        }

        return (
            <>
                <div>
                    GPAT Assays:{' '}
                    <SelectInput
                        inputClass="col-xs-4"
                        labelKey="name"
                        name="assay-import-panels-select"
                        onChange={this.onChange}
                        options={assayDefinitions}
                        placeholder="Select an assay..."
                        required
                        value={selected?.id}
                        valueKey="id"
                    />
                </div>
                {selected && (
                    <>
                        <Alert bsStyle="info">
                            NOTE: if you have the proper permissions, this will actually import an assay run.
                        </Alert>
                        {message && <Alert bsStyle="success">{message}</Alert>}
                        <AssayImportPanels
                            acceptedPreviewFileFormats={BASE_FILE_TYPES.join(', ')}
                            allowBulkInsert
                            allowBulkRemove
                            assayDefinition={selected}
                            onCancel={this.onCancel}
                            onComplete={this.onComplete}
                            onSave={this.onSave}
                        />
                    </>
                )}
            </>
        );
    }
}
