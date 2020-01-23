import React from 'react';
import SSOFields from './SSOFields';
import renderer from 'react-test-renderer';

describe("<SSOFields/>", () => {

    test("No images attached", () => {
        const component =
            <SSOFields
                canEdit={true}
                headerLogoUrl={null}
                loginLogoUrl={null}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("Editable mode", () => {
        const component =
            <SSOFields
                canEdit={true}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("View-only mode", () => {
        const component =
            <SSOFields
                canEdit={false}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});