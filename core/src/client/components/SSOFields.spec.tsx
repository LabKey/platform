import React from 'react';
import {SSOFields, ImageAndFileAttachmentForm} from './SSOFields';
import renderer from 'react-test-renderer';
import {shallow} from "enzyme";

const testImageUrl = "https://en.wikipedia.org/wiki/Moose#/media/File:Moose_superior.jpg";

describe("<SSOFields/>", () => {
    test("No images attached", () => {
        const component =
            <SSOFields
                canEdit={true}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={() => {}}
                handleDeleteLogo={() => {}}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("Editable mode", () => {
        const component =
            <SSOFields
                canEdit={true}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={() => {}}
                handleDeleteLogo={() => {}}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("View-only mode", () => {
        const component =
            <SSOFields
                canEdit={false}
                headerLogoUrl={null}
                loginLogoUrl={null}
                onFileChange={() => {}}
                handleDeleteLogo={() => {}}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});

describe("<ImageAndFileAttachmentForm/>", () => {
    test("Click remove-image button", () => {
        const component =
            <ImageAndFileAttachmentForm
                text={"Page Header Logo"}
                imageUrl={testImageUrl}
                onFileChange={() => {}}
                handleDeleteLogo={() => {}}
                fileTitle={"auth_header_logo"}
                canEdit={true}
                index={1}
            />;
        const wrapper = shallow<SSOFields>(component);

        const deleteImgButton = wrapper.find('.sso-fields__delete-img-div');
        deleteImgButton.simulate('click');

        expect(wrapper.state()).toHaveProperty('imageUrl', null)
    });
});
