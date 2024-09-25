import React from 'react';

import { mount } from 'enzyme';

import { SSOFields, ImageAndFileAttachmentForm } from './SSOFields';

const testImageUrl = 'https://en.wikipedia.org/wiki/Moose#/media/File:Moose_superior.jpg';

describe('<SSOFields/>', () => {
    test('No images attached', () => {
        const wrapper = mount(
            <SSOFields
                canEdit={true}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );

        expect(wrapper.find('.sso-fields__file-attachment').exists()).toEqual(true);
        expect(wrapper.find('.sso-fields__image').exists()).toEqual(false);
        expect(wrapper.find('.sso-fields__delete-img-icon').exists()).toEqual(false);
    });

    test('With images attached', () => {
        const wrapper = mount(
            <SSOFields
                canEdit={true}
                headerLogoUrl="imgURLHeader"
                loginLogoUrl="imgURLLogin"
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );

        expect(wrapper.find('.sso-fields__file-attachment').exists()).toEqual(false);
        expect(wrapper.find('.sso-fields__image').exists()).toEqual(true);
        expect(wrapper.find('.sso-fields__delete-img-icon').exists()).toEqual(true);
    });

    test('Editable mode', () => {
        const wrapper = mount(
            <SSOFields
                canEdit={true}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );
        expect(wrapper.find('.sso-fields__file-attachment').exists()).toEqual(true);
        expect(wrapper.find('.sso-fields__image-holder--view-only').exists()).toEqual(false);
    });

    test('View-only mode', () => {
        const wrapper = mount(
            <SSOFields
                canEdit={false}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );
        expect(wrapper.find('.sso-fields__image-holder').exists()).toEqual(false);
        expect(wrapper.find('.sso-fields__image-holder--view-only').exists()).toEqual(true);
    });
});

describe('<ImageAndFileAttachmentForm/>', () => {
    test('Click remove-image button', () => {
        const component = (
            <ImageAndFileAttachmentForm
                text="Page Header Logo"
                imageUrl={testImageUrl}
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
                fileTitle="auth_header_logo"
                canEdit={true}
                index={1}
            />
        );
        const wrapper = mount(component);
        expect(wrapper.find('.sso-fields__file-attachment').exists()).toEqual(false);
        wrapper.find('.sso-fields__delete-img-icon').simulate('click');
        expect(wrapper.find('.sso-fields__file-attachment').exists()).toEqual(true);
    });
});
