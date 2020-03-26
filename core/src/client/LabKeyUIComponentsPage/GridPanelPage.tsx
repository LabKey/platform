import React, {PureComponent} from 'react';
import {Panel} from 'react-bootstrap';
import {GridPanelWithModel, SchemaQuery} from '@labkey/components';

export class GridPanelPage extends PureComponent {
    render() {
        const configs = {
            'model': {
                schemaQuery: SchemaQuery.create('core', 'users'),
                maxRows: 5,
            },
        };
        return (
            <Panel>
                <Panel.Heading>
                    GridPanel
                </Panel.Heading>
                <Panel.Body>
                    <GridPanelWithModel queryConfigs={configs} />
                </Panel.Body>
            </Panel>
        );
    }
}
