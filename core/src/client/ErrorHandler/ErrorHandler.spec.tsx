import React from 'react';
import { mount, shallow } from 'enzyme';

import { getServerContext } from '@labkey/api';

import { ErrorHandler } from './ErrorHandler';
import { ErrorDetails, ErrorType } from './model';

describe('ErrorHandler', () => {
    test('Not found exception', () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.notFound,
            errorCode: '123XYZ',
            message: 'This is a not found exception',
        };
        const wrapper = mount(<ErrorHandler context={{ errorDetails }} />);
        expect(wrapper.find('.labkey-error-heading').text().includes(errorDetails.message)).toBeTruthy();
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What went wrong?')).toBeTruthy();
        expect(question.text().includes('Incorrect URL:')).toBeTruthy();

        wrapper.unmount();
    });

    test('Configuration exception', () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.configuration,
            message: 'This is a configuration exception',
        };
        const subheading = 'The requested page cannot be found.';
        const wrapper = mount(<ErrorHandler context={{ errorDetails }} />);
        expect(wrapper.find('.labkey-error-subheading').text().includes(subheading)).toBeTruthy();
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What went wrong?')).toBeTruthy();
        expect(question.text().includes('Server Configuration Errors')).toBeTruthy();

        wrapper.unmount();
    });

    test('Permission exception', () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.permission,
            message: 'This is a permission exception',
        };

        const realUser = 'realUser';
        const impersonatedUser = 'impersonatedUser';
        getServerContext().impersonatingUser = { displayName: impersonatedUser };
        getServerContext().user = { displayName: realUser, isSignedIn: true };

        const wrapper = shallow(<ErrorHandler context={{ errorDetails }} />);
        expect(wrapper.find('.labkey-error-subheading').text().includes(errorDetails.message)).toBeTruthy();
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What is a permission error?')).toBeTruthy();
        expect(question.text().includes('You are currently logged in as: ' + impersonatedUser)).toBeTruthy();
        expect(question.text().includes('You are currently impersonating: ' + realUser)).toBeTruthy();

        wrapper.unmount();
    });

    test('Execution exception', () => {
        const expectedStackTrace = 'java.lang.NullPointerException: null';

        const errorDetails: ErrorDetails = {
            errorType: ErrorType.execution,
            errorCode: '456AAA',
            message: 'This is a execution exception',
            stackTrace: expectedStackTrace,
        };
        const wrapper = shallow(<ErrorHandler context={{ errorDetails }} />);
        expect(wrapper.find('.labkey-error-subheading').text().includes(errorDetails.message)).toBeTruthy();
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith(expectedStackTrace)).toBeTruthy();

        wrapper.unmount();
    });
});
