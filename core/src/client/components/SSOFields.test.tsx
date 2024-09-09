import React from 'react';
import { render } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { SSOFields, ImageAndFileAttachmentForm } from './SSOFields';

const testImageUrl = 'https://en.wikipedia.org/wiki/Moose#/media/File:Moose_superior.jpg';

describe('<SSOFields/>', () => {
    test('No images attached', () => {
        render(
            <SSOFields
                canEdit={true}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );

        expect(document.querySelectorAll('.sso-fields__null-image')).toHaveLength(2);
        expect(document.querySelectorAll('.sso-fields__image')).toHaveLength(0);
        expect(document.querySelectorAll('.sso-fields__delete-img-icon')).toHaveLength(0);
    });

    test('With images attached', () => {
        render(
            <SSOFields
                canEdit={true}
                headerLogoUrl="imgURLHeader"
                loginLogoUrl="imgURLLogin"
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );

        expect(document.querySelectorAll('.sso-fields__null-image')).toHaveLength(0);
        expect(document.querySelectorAll('.sso-fields__image')).toHaveLength(2);
        expect(document.querySelectorAll('.sso-fields__delete-img-icon')).toHaveLength(2);
    });

    test('Editable mode', () => {
        render(
            <SSOFields
                canEdit={true}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );
        expect(document.querySelectorAll('.sso-fields__image-holder')).toHaveLength(2);
        expect(document.querySelectorAll('.sso-fields__file-attachment')).toHaveLength(2);
        expect(document.querySelectorAll('.sso-fields__image-holder--view-only')).toHaveLength(0);
    });

    test('View-only mode', () => {
        render(
            <SSOFields
                canEdit={false}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={jest.fn()}
                handleDeleteLogo={jest.fn()}
            />
        );
        expect(document.querySelectorAll('.sso-fields__image-holder')).toHaveLength(0);
        expect(document.querySelectorAll('.sso-fields__image-holder--view-only')).toHaveLength(2);
    });
});

describe('<ImageAndFileAttachmentForm/>', () => {
    test('Click remove-image button', async () => {
        render(
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
        expect(document.querySelectorAll('.sso-fields__null-image')).toHaveLength(0);

        await userEvent.click(document.querySelector('.sso-fields__delete-img-icon'));
        expect(document.querySelectorAll('.sso-fields__null-image')).toHaveLength(1);
    });
});
