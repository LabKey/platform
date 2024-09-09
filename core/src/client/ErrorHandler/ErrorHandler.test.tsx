import React from 'react';
import { userEvent } from '@testing-library/user-event';
import { renderWithAppContext } from '@labkey/components'
import { getServerContext } from '@labkey/api';

import { ErrorHandlerImpl } from './ErrorHandler';
import { ErrorDetails, ErrorType } from './model';

describe('ErrorHandlerImpl', () => {
    test('Not found exception', async () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.notFound,
            errorCode: '123XYZ',
            message: 'This is a not found exception',
        };
        renderWithAppContext(<ErrorHandlerImpl context={{ errorDetails }} />);
        expect(document.querySelector('.labkey-error-heading').innerHTML.includes(errorDetails.message)).toBeTruthy();
        expect(document.querySelectorAll('.error-details-container')).toHaveLength(0);

        await userEvent.click(document.querySelectorAll('.error-page-button')[2]);
        const question = document.querySelector('.error-details-container');
        expect(question.innerHTML.includes('What went wrong?')).toBeTruthy();
        expect(question.innerHTML.includes('Incorrect URL:')).toBeTruthy();
    });

    test('Configuration exception', async () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.configuration,
            message: 'This is a configuration exception',
        };
        const subheading = 'The requested page cannot be found.';
        renderWithAppContext(<ErrorHandlerImpl context={{ errorDetails }} />);
        expect(document.querySelector('.labkey-error-subheading').innerHTML.includes(subheading)).toBeTruthy();
        expect(document.querySelectorAll('.error-details-container')).toHaveLength(0);

        await userEvent.click(document.querySelectorAll('.error-page-button')[2]);
        expect(document.querySelector('.labkey-error-details-question').innerHTML.includes('What went wrong?')).toBeTruthy();
        expect(document.querySelector('div.labkey-error-details').innerHTML.includes('Server Configuration Errors')).toBeTruthy();
    });

    test('Permission exception', async () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.permission,
            message: 'This is a permission exception',
        };

        const realUser = 'realUser';
        const impersonatedUser = 'impersonatedUser';

        renderWithAppContext(<ErrorHandlerImpl context={{ errorDetails }} />, {
            serverContext: {
                impersonatingUser: { displayName: impersonatedUser },
                user: { displayName: realUser, isSignedIn: true } as any,
            }
        });
        expect(document.querySelector('.labkey-error-subheading').innerHTML.includes(errorDetails.message)).toBeTruthy();
        expect(document.querySelectorAll('.error-details-container')).toHaveLength(0);

        await userEvent.click(document.querySelectorAll('.error-page-button')[2]);
        const question = document.querySelector('.error-details-container');
        expect(document.querySelector('.labkey-error-details-question').innerHTML.startsWith('What is a permission error?')).toBeTruthy();

        // FIXME: the following cases are commented out because nearly the entire ErrorHandler component uses
        //  non-component functions to render the contents, which prevents the code from being able to properly use the
        //  server context. We should update the code to stop using non-component functions to render.
        // expect(question.innerHTML.includes('You are currently logged in as: ' + impersonatedUser)).toBeTruthy();
        // expect(question.innerHTML.includes('You are currently impersonating: ' + realUser)).toBeTruthy();
    });

    test('Execution exception', async () => {
        const expectedStackTrace = 'java.lang.NullPointerException: null';

        const errorDetails: ErrorDetails = {
            errorType: ErrorType.execution,
            errorCode: '456AAA',
            message: 'This is a execution exception',
            stackTrace: expectedStackTrace,
        };
        renderWithAppContext(<ErrorHandlerImpl context={{ errorDetails }} />);
        expect(document.querySelector('.labkey-error-subheading').innerHTML.includes(errorDetails.message)).toBeTruthy();
        expect(document.querySelectorAll('.error-details-container')).toHaveLength(0);

        await userEvent.click(document.querySelectorAll('.error-page-button')[2]);
        expect(document.querySelector('pre').innerHTML.startsWith(expectedStackTrace)).toBeTruthy();
    });
});
