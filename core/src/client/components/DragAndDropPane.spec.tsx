import React from 'react';

import { shallow } from 'enzyme';
import EnzymeToJson from 'enzyme-to-json';

import { SSO_CONFIGURATIONS } from '../../../test/data';

import DragAndDropPane from './DragAndDropPane';

describe('<DragAndDropPane/>', () => {
    let component;

    beforeEach(() => {
        const basicFn = () => {};
        const actionFns = { name: basicFn };

        component = (
            <DragAndDropPane
                rowInfo={SSO_CONFIGURATIONS}
                canEdit={true}
                isDragDisabled={false}
                actionFunctions={actionFns}
            />
        );
    });

    test('Drag is disabled', () => {
        const wrapper = shallow<DragAndDropPane>(component);

        wrapper.setProps({ isDragDisabled: true });
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('View-only', () => {
        const wrapper = shallow<DragAndDropPane>(component);
        wrapper.setProps({ canEdit: false });

        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Editable', () => {
        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });
});
