/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import {
    Alert,
    EntityInsertPanel,
    importData,
    initQueryGridState,
    InsertOptions,
    QueryInfo,
    SampleTypeDataType
} from '@labkey/components';
import {ActionURL} from "@labkey/api";

export const SampleInsertPage: FC = memo(() => {
    const [message, setMessage] = useState<string>(undefined);

    useEffect(() => {
        initQueryGridState();
    }, []);

    const afterSampleCreation = useCallback((sampleSetName: string, filter: any, sampleCount: number) => {
        setMessage(`Created ${sampleCount} samples in sample type '${sampleSetName}'.`);
    }, []);

    const handleFileImport = useCallback(
        (queryInfo: QueryInfo, file: File, isMerge: boolean): Promise<any> => {
            return new Promise((resolve, reject) => {
                const { schemaQuery } = queryInfo;

                return importData({
                    schemaName: schemaQuery.getSchema(),
                    queryName: schemaQuery.getQuery(),
                    file,
                    importUrl: ActionURL.buildURL('experiment', 'ImportSamples', null, {
                        'schemaName': schemaQuery.getSchema(),
                        'query.queryName': schemaQuery.getQuery()
                    }),
                    importLookupByAlternateKey: true,
                    insertOption: InsertOptions[isMerge ? InsertOptions.MERGE : InsertOptions.IMPORT]
                }).then((response) => {
                    if (response.success) {
                        resolve(response);
                    } else {
                        reject({msg: response.errors._form})
                    }
                }).catch((error) => {
                    console.error(error);
                    reject({msg: error.exception})
                });
            });
        }, []
    );

    return (
        <>
            <Alert bsStyle="info">
                NOTE: if you have the proper permissions, this will actually insert samples into the selected target sample type.
            </Alert>
            <Alert bsStyle="success">{message}</Alert>
            <EntityInsertPanel
                afterEntityCreation={afterSampleCreation}
                entityDataType={SampleTypeDataType}
                importHelpLinkNode={<>Get help with your samples</>}
                handleFileImport={handleFileImport}
                nounPlural="samples"
                nounSingular="sample"
            />
        </>
    );
});
