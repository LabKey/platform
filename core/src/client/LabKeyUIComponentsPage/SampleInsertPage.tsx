/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import {
    Alert,
    EntityInsertPanel,
    initQueryGridState,
    SampleTypeDataType,
    Location,
} from '@labkey/components';

export const SampleInsertPage: FC = memo(() => {
    const [message, setMessage] = useState<string>(undefined);

    useEffect(() => {
        initQueryGridState();
    }, []);

    const afterSampleCreation = useCallback((sampleSetName: string, filter: any, sampleCount: number) => {
        setMessage(`Created ${sampleCount} samples in sample type '${sampleSetName}'.`);
    }, []);

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
                fileImportParameters={{
                    'importAlias.SampleId': 'Name', // accept both 'SampleId' and 'Name' as name
                    'importAlias.Sample Id': 'Name', // accept both 'SampleId' and 'Name' as name
                }}
                nounPlural="samples"
                nounSingular="sample"
                location={{ query: {} } as Location}
            />
        </>
    );
});
