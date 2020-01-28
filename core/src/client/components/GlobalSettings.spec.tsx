import React from 'react';
import { mount, shallow } from 'enzyme';

import renderer from 'react-test-renderer';

import GlobalSettings from './GlobalSettings';
import FACheckBox from './FACheckBox';

describe('<GlobalSettings/>', () => {
    test('Clicking a checkbox toggles the checkbox', () => {
        const checkGlobalAuthBox = jest.fn(() => wrapper.setProps({ SelfRegistration: false }));

        const props = {
            SelfRegistration: true,
            SelfServiceEmailChanges: true,
            AutoCreateAccounts: false,
            canEdit: true,
            checkGlobalAuthBox,
        };
        const component = <GlobalSettings {...props} />;
        const wrapper = mount(component);

        expect(wrapper.props()).toHaveProperty('SelfRegistration', true);

        // Click self registration checkbox
        const firstCheckBox = wrapper.find(FACheckBox).first();
        firstCheckBox.simulate('click');
        expect(props.checkGlobalAuthBox).toHaveBeenCalled();

        expect(wrapper.props()).toHaveProperty('SelfRegistration', false);
    });

    test('An authCount of 1 eliminates the option to auto-create authenticated users', () => {
        const props = { SelfRegistration: true, SelfServiceEmailChanges: true, AutoCreateAccounts: false };
        const component = <GlobalSettings {...props} />;
        const wrapper = mount(component);

        expect(wrapper.find(FACheckBox).length).toBe(3);
        wrapper.setProps({ authCount: 1 });
        expect(wrapper.find(FACheckBox).length).toBe(2);
        expect(wrapper.text()).not.toMatch(/Auto-create authenticated users/);
    });

    test('view-only mode', () => {
        const props = {
            SelfRegistration: true,
            SelfServiceEmailChanges: true,
            AutoCreateAccounts: false,
            canEdit: false,
        };
        const component = <GlobalSettings {...props} />;

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});
