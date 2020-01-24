import React from 'react';
import AuthConfigMasterPanel from './AuthConfigMasterPanel';
import renderer from 'react-test-renderer';

describe("<AuthConfigMasterPanel/>", () => {
    test("Editable mode", () => {
        let basicFn = () => {};
        let actionFns = {"name": basicFn};
        const component =
            <AuthConfigMasterPanel
                canEdit={true}
                actionFunctions={actionFns}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("View-only mode", () => {
        let basicFn = () => {};
        let actionFns = {"name": basicFn};
        const component =
            <AuthConfigMasterPanel
                canEdit={false}
                actionFunctions={actionFns}
            />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});