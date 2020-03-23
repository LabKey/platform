import React from 'react';
import { getGlobal } from 'reactn';
import {
    initQueryGridState,
    LineageGraph,
    VisGraphNode,
} from '@labkey/components';

import { AppContext } from './util'

initQueryGridState();

// window['GG'] = () => {
//     return getGlobal()['QueryGrid_lineageResults'].toJS();
// };

interface RunGraphProps {
    context: AppContext
}

export class RunGraph extends React.Component<RunGraphProps> {

    navigate = (node: VisGraphNode): void => {};

    render() {
        return (
            <LineageGraph
                distance={1}
                filterIn={false}
                lsid={this.props.context.lsid}
                navigate={this.navigate}
            />
        );
    }
}