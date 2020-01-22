import React from 'react';
import DragAndDropPane from './DragAndDropPane';
import renderer from 'react-test-renderer';

describe("<DragAndDropPane/>", () => {

    const component = (
        <DragAndDropPane
            rowInfo={} // th
            primaryProviders={} // not really necessary?
            canEdit={true}
            isDragDisabled={false}
        />
    );


    test("Drag-and-drop call re-orders rows", () => {
        // call re-ordering function

    });

    test("Drag-and-drop outside of rows", () => {

    });

    test("Drag is disabled", () => {
        const component = (
            <DragAndDropPane isDragDisabled={true}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

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