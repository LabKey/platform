import React from 'react';

import { ErrorTopSection } from '../components/ErrorTopSection';
import { ErrorType } from './ErrorType';

import './errorHandler.scss';

export interface AppContext {
    message: string;
    errorType: ErrorType;
}

interface ErrorHandlerProps {
    context: AppContext;
}

interface ErrorHandlerState {
    showDetails: boolean;
}

export class ErrorHandler extends React.PureComponent<ErrorHandlerProps, ErrorHandlerState> {
    constructor(props) {
        super(props);

        this.state = {
            showDetails: false,
        };
    }

    onBackClick = (): void => {
        window.history.back();
    };

    onViewDetailsClick = (): void => {
        this.setState(() => ({
            showDetails: true,
        }));
    };

    render() {
        const { errorType, message } = this.props.context;
        const { showDetails } = this.state;

        return (
            <>
                <ErrorTopSection
                    errorType={errorType}
                    onBackClick={this.onBackClick}
                    onViewDetailsClick={this.onViewDetailsClick}
                />
                {/* TODO : ErrorPage, following section in next story*/}
                {showDetails && <h3 className="labkey-error">{message}</h3>}
            </>
        );
    }
}
