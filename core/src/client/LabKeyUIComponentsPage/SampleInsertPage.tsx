/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import { Alert, EntityInsertPanel, initQueryGridState, SampleTypeDataType } from "@labkey/components";


export class  SampleInsertPage extends React.Component<any, any> {

    constructor(props: any) {
        super(props);

        this.afterSampleCreation = this.afterSampleCreation.bind(this);


        initQueryGridState();
    }

    afterSampleCreation(sampleSetName: string, filter: any, sampleCount: number)  {
        window.alert("Created " + sampleCount + " samples in sample type '" + sampleSetName + "'");
    }

    render() {
        return (
            <>
                <Alert bsStyle={'info'}>NOTE: if you have the proper permissions, this will actually insert samples into the selected target sample type.</Alert>
                <EntityInsertPanel entityDataType={SampleTypeDataType} afterSampleCreation={this.afterSampleCreation}/>
            </>
        )
    }
}

