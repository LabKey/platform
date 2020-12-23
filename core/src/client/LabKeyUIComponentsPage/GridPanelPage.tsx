import React, { PureComponent } from 'react';
import { GridPanelWithModel } from '@labkey/components';
import {SchemaQueryInputContext, SchemaQueryInputProvider} from "./SchemaQueryInputProvider";

class GridPanelPageImpl extends PureComponent<SchemaQueryInputContext> {

    render() {
        const { queryConfig } = this.props;

        return (
            <div>
                {queryConfig &&
                    <GridPanelWithModel
                        title={'GridPanel'}
                        asPanel={true}
                        queryConfig={queryConfig}
                    />
                }
            </div>
        );
    }
}

export const GridPanelPage = SchemaQueryInputProvider(GridPanelPageImpl);
