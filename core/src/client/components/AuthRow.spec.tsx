import React from 'react';

import renderer from 'react-test-renderer';
import { mount, shallow } from 'enzyme';

import { CAS_MODAL_TYPE } from '../../../test/data';

import AuthRow from './AuthRow';

describe('<AuthRow/>', () => {
    let component;

    beforeEach(() => {
        component = (
            <AuthRow
                description="My Configuration Name"
                details="Some Url"
                provider="CAS"
                enabled={true}
                draggable={true}
                modalType={CAS_MODAL_TYPE}
                canEdit={true}
            />
        );
    });

    test('Modal opens on click', () => {
        const wrapper = shallow<AuthRow>(component);
        const toggleModalOpen = jest.fn(() => {});
        wrapper.setProps({ toggleModalOpen });

        wrapper.instance().onToggleModal = jest.fn(() => wrapper.setState({ editModalOpen: true }));
        wrapper.update();

        expect(wrapper.state()).toHaveProperty('editModalOpen', false);

        const editIcon = wrapper.find('.clickable').last();

        editIcon.simulate('click');
        expect(wrapper.instance().onToggleModal).toHaveBeenCalled();

        expect(wrapper.state()).toHaveProperty('editModalOpen', true);
    });

    test('Non-draggable', () => {
        const wrapper = shallow<AuthRow>(component);
        wrapper.setProps({ draggable: false });

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test('Editable', () => {
        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test('View-only', () => {
        const wrapper = shallow<AuthRow>(component);
        wrapper.setProps({ canEdit: false });

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});
