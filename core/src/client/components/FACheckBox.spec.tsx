import React from 'react';
import { FACheckBox } from './FACheckBox';
import renderer from 'react-test-renderer';

describe("<FACheckBox/>", () => {

    test("checked", () => {
        const component = (
            <FACheckBox checked={true}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("checked", () => {
        const component = (
            <FACheckBox checked={false}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    })
});