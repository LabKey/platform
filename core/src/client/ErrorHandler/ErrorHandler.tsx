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
            <h3>Hello world!</h3>
        );
    }
}