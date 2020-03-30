import React, { PureComponent } from 'react';
import { Panel, Button, Grid, Row, Col } from 'react-bootstrap';
import { GridPanel, InjectedQueryModels, withQueryModels, SchemaQuery } from '@labkey/components';

interface State {
    schemaName?: string;
    queryName?: string;
}

class GridPanelExampleImpl extends PureComponent<{} & InjectedQueryModels, State> {
    constructor(props) {
        super(props);

        this.state = {
            schemaName: '',
            queryName: '',
        };
    }

    onFormChange = (e) => {
        const { name, value } = e.target;
        this.setState(() => ({ [name]: value }));
    };

    applySchemaQuery = () => {
        const { queryModels, actions } = this.props;
        const { model } = queryModels;
        let { schemaName, queryName } = this.state;
        schemaName = schemaName.trim() || undefined;
        queryName = queryName.trim() || undefined;

        if (schemaName === undefined || queryName === undefined) {
            console.warn('Cannot have empty schemaName or queryName');
            return;
        }

        const schemaQuery = SchemaQuery.create(schemaName, queryName);

        if (model !== undefined) {
            actions.setSchemaQuery(model.id, schemaQuery);
        } else {
            actions.addModel({ schemaQuery, id: 'model' }, true);
        }
    };

    render() {
        const { queryModels, actions } = this.props;
        const { model } = queryModels;
        const { schemaName, queryName } = this.state;
        let body = (
            <div>
                Enter a Schema, Query, View
            </div>
        );

        if (model !== undefined) {
            body = <GridPanel actions={actions} model={model} />
        }

        return (
            <div>
                <Grid>
                    <Row>
                        <Col xs={4}>
                            <label htmlFor="schemaName">Schema</label>
                            <input id="schemaName" name="schemaName" type="text" value={schemaName} onChange={this.onFormChange}/>
                        </Col>

                        <Col xs={4}>
                            <label htmlFor="queryName">Query</label>
                            <input id="queryName" name="queryName" type="text" value={queryName} onChange={this.onFormChange}/>
                        </Col>

                        <Col xs={4}>
                            <Button onClick={this.applySchemaQuery}>Apply</Button>
                        </Col>
                    </Row>
                </Grid>

                {body}
            </div>
        );
    }
}

const GridPanelExample = withQueryModels<{}>(GridPanelExampleImpl);

export class GridPanelPage extends PureComponent {
    render() {
        return (
            <Panel>
                <Panel.Heading>
                    GridPanel
                </Panel.Heading>
                <Panel.Body>
                    <GridPanelExample />
                </Panel.Body>
            </Panel>
        );
    }
}
