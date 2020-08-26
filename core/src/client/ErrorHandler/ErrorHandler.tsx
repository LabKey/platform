import React from 'react';
import {
    initQueryGridState,
} from '@labkey/components';

import '@labkey/components/dist/components.css';

initQueryGridState();

export interface AppContext {
    message: string;
}

interface ErrorHandlerProps {
    context: AppContext;
}

export class ErrorHandler extends React.Component<ErrorHandlerProps> {
    render() {
        return (
            <div className='labkey-error'>
                <h3>{this.props.context.message}</h3>
            </div>
        );
    }
}