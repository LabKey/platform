import React from 'react';

import '@labkey/components/dist/components.css';
import './errorHandler.scss';
import { ErrorTopSection } from '../components/ErrorTopSection';

import { ErrorType } from './ErrorType';

export interface AppContext {
    message: string;
    errorType: ErrorType;
}

interface ErrorHandlerProps {
    context: AppContext;
}

export class ErrorHandler extends React.Component<ErrorHandlerProps> {
    render() {
        const { errorType } = this.props.context;

        return (
            <>
                <ErrorTopSection errorType={errorType} />
            </>
        );
    }
}
