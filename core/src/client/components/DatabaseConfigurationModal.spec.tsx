import React from 'react';
import DatabaseConfigurationModal from './DatabaseConfigurationModal';
import renderer from 'react-test-renderer';

describe("<DatabaseConfigurationModal/>", () => {

    test("View-only", () => {
        const component = (
            <DatabaseConfigurationModal canEdit={false}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("Editable", () => {
        const component = (
            <DatabaseConfigurationModal canEdit={true}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});