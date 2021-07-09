import React, { FC, memo } from 'react';
import { QuerySelect, SchemaQuery, } from '@labkey/components';

import './QuerySelectInput.scss';

export interface AppContext {
    name: string;
    value: string;
    containerPath: string;
    schemaName: string;
    queryName: string;
    disabled?: boolean;
}

interface Props {
    context: AppContext;
}

export const QuerySelectInput: FC<Props> = memo(props => {
    const { name, disabled, containerPath, value, schemaName, queryName } = props.context;

    return (
        <QuerySelect
            componentId={'query-select' + name}
            inputClass={'col-sm-8 col-xs-12'}
            formsy={false}
            name={name}
            schemaQuery={SchemaQuery.create(schemaName, queryName)}
            containerPath={containerPath}
            showLabel={false}
            disabled={disabled}
            value={value}
            loadOnFocus
            maxRows={100}
        />
    );
});
