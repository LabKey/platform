import React from 'react';
import {
    initQueryGridState,
    LineageGraph,
    LineageURLResolvers,
} from '@labkey/components';

import { AppContext } from './util'

import '@labkey/components/dist/components.css';

initQueryGridState();

interface RunGraphProps {
    context: AppContext
}

export class RunGraph extends React.Component<RunGraphProps> {
    render() {
        return (
            <LineageGraph
                distance={1}
                filterIn={false}
                lsid={this.props.context.lsid}
                urlResolver={LineageURLResolvers.Server}
                navigate={(node) => {
                    if (node && node.lineageNode && node.lineageNode.links.lineage) {
                        window.location.href = node.lineageNode.links.lineage;
                    }
                }}
            />
        );
    }
}