import React from 'react';
import {
    initQueryGridState,
    LineageFilter,
    LineageGraph,
    LineageURLResolvers,
} from '@labkey/components';

import '@labkey/components/dist/components.css';

initQueryGridState();

export interface AppContext {
    lsid: string;
    rowId: number;
}

interface RunGraphProps {
    context: AppContext;
}

export class RunGraph extends React.Component<RunGraphProps> {
    render() {
        return (
            <LineageGraph
                distance={1}
                filterIn={false}
                filters={[new LineageFilter('expType', null)]}
                lsid={this.props.context.lsid}
                request={{
                    includeInputsAndOutputs: true,
                    includeRunSteps: true,
                }}
                urlResolver={LineageURLResolvers.Server}
                navigate={(node) => {
                    if (node?.lineageNode?.links) {
                        let target = node.lineageNode.links.lineage ?? node.lineageNode.links.overview;

                        if (target) {
                            if (target.indexOf('showRunGraph.view') > -1) {
                                try {
                                    const url = new URL(target, location.origin);
                                    url.searchParams.append('betaGraph', '1');

                                    target = url.href;
                                } catch (e) {
                                    // whatever, I tried...
                                }
                            }

                            window.location.href = target;
                        }
                    }
                }}
            />
        );
    }
}