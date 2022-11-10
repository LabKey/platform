import React from 'react';

import renderer from 'react-test-renderer';
import { mount, shallow } from 'enzyme';

import { CAS_MODAL_TYPE, CAS_CONFIG } from '../../../test/data';

import AuthRow from './AuthRow';

describe('<AuthRow/>', () => {
    let component;

    beforeEach(() => {
        component = (
            <AuthRow
                draggable={true}
                authConfig={CAS_CONFIG}
                canEdit={true}
                configType="ssoConfigurations"
                modalType={CAS_MODAL_TYPE}
                toggleModalOpen={jest.fn}
                updateAuthRowsAfterSave={jest.fn}
                onDelete={jest.fn}
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
