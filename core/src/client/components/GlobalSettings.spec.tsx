import React from 'react';
import { mount } from 'enzyme';

import { GLOBAL_SETTINGS } from '../../../test/data';

import { GlobalSettings } from './GlobalSettings';

describe('<GlobalSettings/>', () => {
    test('Clicking a checkbox toggles the checkbox', () => {
        const checkGlobalAuthBox = jest.fn(() => wrapper.setProps({ SelfRegistration: false }));
        const wrapper = mount(
            <GlobalSettings
                globalSettings={GLOBAL_SETTINGS}
                canEdit={true}
                authCount={3}
                onChange={checkGlobalAuthBox}
            />
        );

        const value = { AutoCreateAccounts: true, SelfRegistration: true, SelfServiceEmailChanges: false };
        expect(wrapper.props()).toHaveProperty('globalSettings', value);

        // Click self registration checkbox
        const firstCheckBox = wrapper.find('input[type="checkbox"]').first();
        firstCheckBox.simulate('change', { target: { checked: true, name: '' }});
        expect(checkGlobalAuthBox).toHaveBeenCalled();

        expect(wrapper.props()).toHaveProperty('SelfRegistration', false);
    });

    test('An authCount of 1 eliminates the option to auto-create authenticated users', () => {
        const checkGlobalAuthBox = jest.fn(() => wrapper.setProps({ SelfRegistration: false }));
        const wrapper = mount(
            <GlobalSettings
                globalSettings={GLOBAL_SETTINGS}
                canEdit={true}
                authCount={3}
                onChange={checkGlobalAuthBox}
            />
        );

        expect(wrapper.find('input[type="checkbox"]').length).toBe(3);
        wrapper.setProps({ authCount: 1 });
        expect(wrapper.find('input[type="checkbox"]').length).toBe(2);
        expect(wrapper.text()).not.toMatch(/Auto-create authenticated users/);
    });

    test('view-only mode', () => {
        const wrapper = mount(
            <GlobalSettings globalSettings={GLOBAL_SETTINGS} canEdit={false} authCount={3} onChange={jest.fn()} />
        );

        wrapper.find('input').forEach(element => {
            expect(element.props().disabled).toEqual(true);
        });
    });
});
