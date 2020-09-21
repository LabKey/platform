import React from 'react';

import { mount, shallow } from 'enzyme';

import { ErrorHandler } from './ErrorHandler';
import { ErrorDetailsModel, IErrorDetailsModel } from './model';
import { ErrorType } from './ErrorType';

describe('ErrorHandler', () => {
    test('Not found exception', () => {
        const errDetails: IErrorDetailsModel = new ErrorDetailsModel({
            errorType: ErrorType.notFound,
            errorCode: '123XYZ',
            stackTrace: undefined,
            message: 'This is a not found exception',
        });
        const errorContext = { errorDetails: errDetails };
        const wrapper = mount(<ErrorHandler context={errorContext} />);
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What went wrong?')).toBeTruthy();

        wrapper.unmount();
    });

    test('Configuration exception', () => {
        const errDetails: IErrorDetailsModel = new ErrorDetailsModel({
            errorType: ErrorType.configuration,
            errorCode: undefined,
            stackTrace: undefined,
            message: 'This is a configuration exception',
        });
        const errorContext = { errorDetails: errDetails };
        const wrapper = mount(<ErrorHandler context={errorContext} />);
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What went wrong?')).toBeTruthy();

        wrapper.unmount();
    });

    test('Permission exception', () => {
        const errDetails: IErrorDetailsModel = new ErrorDetailsModel({
            errorType: ErrorType.permission,
            errorCode: undefined,
            stackTrace: undefined,
            message: 'This is a permission exception',
        });
        const errorContext = { errorDetails: errDetails };
        const wrapper = shallow(<ErrorHandler context={errorContext} />);
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('What is a permission error?')).toBeTruthy();

        wrapper.unmount();
    });

    test('Execution exception', () => {
        const sampleTrace = 'java.lang.NullPointerException: null';

        const errDetails: IErrorDetailsModel = new ErrorDetailsModel({
            errorType: ErrorType.execution,
            errorCode: '456AAA',
            stackTrace: sampleTrace,
            message: 'This is a execution exception',
        });
        const errorContext = { errorDetails: errDetails };
        const wrapper = shallow(<ErrorHandler context={errorContext} />);
        expect(wrapper.find('.error-details-container')).toHaveLength(0);

        wrapper.setState({ showDetails: true });
        const question = wrapper.find('.error-details-container');
        expect(question.text().startsWith('java.lang.NullPointerException: null')).toBeTruthy();

        wrapper.unmount();
    });
});
