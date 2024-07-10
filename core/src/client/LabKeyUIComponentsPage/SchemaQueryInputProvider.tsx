import React, { ChangeEvent } from 'react';
import { Alert, QueryConfig, SchemaQuery } from '@labkey/components';

const Context = React.createContext<SchemaQueryInputContext>(undefined);
const SchemaQueryInputContextProvider = Context.Provider;

interface Props {}

interface State {
    error: string;
    queryConfig: QueryConfig;
    queryName: string;
    schemaName: string;
}

export type SchemaQueryInputContext = State;

export const SchemaQueryInputProvider = (Component: React.ComponentType) => {
    return class SchemaQueryInputProviderImpl extends React.Component<Props, State> {
        readonly state: State = {
            queryName: undefined,
            schemaName: undefined,
            error: undefined,
            queryConfig: undefined,
        };

        onFormChange = (e: ChangeEvent<HTMLInputElement>) => {
            const { name, value } = e.target;
            this.setState(() => ({
                ...this.state,
                error: undefined,
                queryConfig: undefined,
                [name]: value,
            }));
        };

        onApply = () => {
            const { schemaName, queryName } = this.state;

            let error, queryConfig;
            if (!schemaName || !queryName) {
                error = 'You must enter a schema/query to view the grid panel.';
            } else {
                const schemaQuery = new SchemaQuery(schemaName, queryName);
                queryConfig = {
                    id: `components-queryconfig-${schemaName}-${queryName}`,
                    schemaQuery,
                    includeTotalCount: true,
                };
            }

            this.setState(() => ({ queryConfig, error }));
        };

        render() {
            const { error } = this.state;

            return (
                <SchemaQueryInputContextProvider value={this.state}>
                    <div className="row">
                        <div className="col-xs-4">
                            Schema:{' '}
                            <input
                                className="form-control"
                                name="schemaName"
                                type="text"
                                onChange={this.onFormChange}
                            />
                        </div>
                        <div className="col-xs-4">
                            Query:{' '}
                            <input className="form-control" name="queryName" type="text" onChange={this.onFormChange} />
                        </div>
                        <div className="col-xs-4">
                            <button className="btn btn-default" onClick={this.onApply} type="button">
                                Apply
                            </button>
                        </div>
                    </div>
                    <br />
                    {error && <Alert>{error}</Alert>}
                    <Component {...this.props} {...this.state} />
                </SchemaQueryInputContextProvider>
            );
        }
    };
};
