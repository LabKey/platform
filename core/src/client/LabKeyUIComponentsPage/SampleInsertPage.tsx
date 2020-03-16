/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import { Alert, EntityInsertPanel, initQueryGridState, SampleTypeDataType } from "@labkey/components";

interface State {
    message: string
}

export class SampleInsertPage extends React.Component<any, State> {

    constructor(props: any) {
        super(props);

        initQueryGridState();

        this.state = {
            message: undefined
        }
    }

    afterSampleCreation = (sampleSetName: string, filter: any, sampleCount: number) => {
        this.setState(() => ({message: "Created " + sampleCount + " samples in sample type '" + sampleSetName + "'."}));
    };

    render() {
        const { message } = this.state;

        return (
            <>
                <Alert bsStyle={'info'}>NOTE: if you have the proper permissions, this will actually insert samples into the selected target sample type.</Alert>
                {message && <Alert bsStyle={'success'}>{message}</Alert>}
                <EntityInsertPanel
                    nounSingular={'sample'}
                    nounPlural={'samples'}
                    entityDataType={SampleTypeDataType}
                    afterEntityCreation={this.afterSampleCreation}
                />
            </>
        )
    }
}

