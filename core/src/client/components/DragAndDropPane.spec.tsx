import React from 'react';
import DragAndDropPane from './DragAndDropPane';
import renderer from 'react-test-renderer';

describe("<DragAndDropPane/>", () => {

    test("View-only", () => {
        const component = (
            <DragAndDropPane canEdit={false}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("Editable", () => {
        const component = (
            <DragAndDropPane canEdit={true}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});