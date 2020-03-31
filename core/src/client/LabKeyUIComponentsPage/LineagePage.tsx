/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, { PureComponent } from 'react'
import { FormControl, Tab, Tabs } from "react-bootstrap";
import {Alert, LINEAGE_GROUPING_GENERATIONS, LineageFilter, LineageGraph, LineageGrid, VisGraphNode} from "@labkey/components";

interface StateProps {
    lsid: string
}

export class LineagePage extends PureComponent<any, StateProps> {

    constructor(props: any) {
        super(props);

        this.state = {
            lsid: undefined
        };
    }

    onLsidChange = (evt) => {
        const value = evt.target.value;
        this.setState(() => ({lsid: value}));
    };

    onLineageNodeDblClick = (node: VisGraphNode) => {
        // TODO have this set the lsidField value instead of setState directly
        if (node.kind === 'node') {
            this.setState(() => ({lsid: node.id}));
        }
    };

    render() {
        const { lsid } = this.state;

        let body;
        if (!lsid) {
            body = <Alert>You must enter a sample identifier (i.e. lsid) to view the Lineage Graph.</Alert>;
        }
        else {
            body = (
                <Tabs defaultActiveKey={'graph'} id={'lineagepage-tabs'}>
                    <Tab title={'Graph'} eventKey={'graph'}>
                        <div className={'margin-top'}>
                            <LineageGraph
                                lsid={lsid}
                                grouping={{generations: LINEAGE_GROUPING_GENERATIONS.Specific}}
                                filters={[new LineageFilter('type', ['Sample', 'Data'])]}
                                navigate={this.onLineageNodeDblClick}
                            />
                        </div>
                    </Tab>
                    <Tab title={'Grid'} eventKey={'grid'}>
                        <div className={'margin-top'}>
                            <LineageGrid
                                lsid={lsid}
                            />
                        </div>
                    </Tab>
                </Tabs>
            )
        }

        return (
            <>
                LSID: <FormControl name={'lsidField'} type="text" onChange={this.onLsidChange}/>
                <br/>
                {body}
            </>
        )
    }
}

