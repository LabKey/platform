import React from 'react';

import renderer from 'react-test-renderer';

import FACheckBox from './FACheckBox';

describe('<FACheckBox/>', () => {
    test('Checked, view-only', () => {
        const component = <FACheckBox checked={true} canEdit={false} />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test('Unchecked, view-only', () => {
        const component = <FACheckBox checked={false} canEdit={false} />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test('Unchecked, clickable', () => {
        const component = <FACheckBox checked={false} canEdit={true} />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test('Checked, clickable', () => {
        const component = <FACheckBox checked={true} canEdit={true} />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});
