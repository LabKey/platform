import React, { PureComponent, ChangeEvent } from 'react';
import { Panel, Button, Grid, Row, Col } from 'react-bootstrap';
import { GridPanelWithModel, SchemaQuery } from '@labkey/components';

interface State {
    schemaName?: string;
    queryName?: string;
    schemaQuery?: SchemaQuery;
    id?: string;
}

class GridPanelExample extends PureComponent<{}, State> {
    readonly state = {
        id: undefined,
        queryName: '',
        schemaName: '',
        schemaQuery: undefined,
    };

    onFormChange = (e: ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        this.setState(() => ({ [name]: value }));
    };

    applySchemaQuery = () => {
        let { schemaName, queryName } = this.state;
        schemaName = schemaName.trim() || undefined;
        queryName = queryName.trim() || undefined;

        if (schemaName === undefined || queryName === undefined) {
            console.warn('Cannot have empty schemaName or queryName');
            return;
        }

        this.setState({ id: `gpe-${schemaName}-${queryName}`, schemaQuery: SchemaQuery.create(schemaName, queryName) });
    };

    render() {
        const { id, queryName, schemaName, schemaQuery } = this.state;
        let body = (
            <div>
                Enter a Schema, Query, View
            </div>
        );

        if (id && schemaQuery) {
            body = <GridPanelWithModel queryConfig={{ id, schemaQuery }} />
        }

        return (
            <div>
                <Grid>
                    <Row>
                        <Col xs={4}>
                            <label htmlFor="schemaName">Schema</label>
                            <input
                                id="schemaName"
                                name="schemaName"
                                type="text"
                                value={schemaName}
                                onChange={this.onFormChange}
                            />
                        </Col>

                        <Col xs={4}>
                            <label htmlFor="queryName">Query</label>
                            <input
                                id="queryName"
                                name="queryName"
                                type="text"
                                value={queryName}
                                onChange={this.onFormChange}
                            />
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
