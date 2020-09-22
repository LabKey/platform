import React from 'react';
import { mount, shallow } from 'enzyme';

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
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What went wrong?')).toBeTruthy();

        wrapper.unmount();
    });

    test('Configuration exception', () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.configuration,
            message: 'This is a configuration exception',
        };
        const wrapper = mount(<ErrorHandler context={{ errorDetails }} />);
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What went wrong?')).toBeTruthy();

        wrapper.unmount();
    });

    test('Permission exception', () => {
        const errorDetails: ErrorDetails = {
            errorType: ErrorType.permission,
            message: 'This is a permission exception',
        };
        const wrapper = shallow(<ErrorHandler context={{ errorDetails }} />);
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What is a permission error?')).toBeTruthy();

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
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith(expectedStackTrace)).toBeTruthy();

        wrapper.unmount();
    });
});
