import React from 'react';
import { mount } from 'enzyme';
import { ActionURL } from '@labkey/api';

import {
    SSO_CONFIGURATIONS as ssoConfigurations,
    FORM_CONFIGURATIONS as formConfigurations,
    SECONDARY_CONFIGURATIONS as secondaryConfigurations,
    GLOBAL_SETTINGS as globalSettings,
    PRIMARY_PROVIDERS as primaryProviders,
    SECONDARY_PROVIDERS as secondaryProviders,
} from '../../../test/data';

import { App } from './AuthenticationConfiguration';

let component;
let wrapper;

beforeAll(() => {
    window['__react-beautiful-dnd-disable-dev-warnings'] = true;
});

describe('<AuthenticationConfiguration/>', () => {
    const { location } = window;

    beforeAll(() => {
        delete window.location;
        window.location = Object.assign(
            { ...location },
            {
                assign: jest.fn(),
            }
        );
    });

    afterAll(() => {
        window.location = location;
    });

    beforeEach(() => {
        component = <App />;
        wrapper = mount(component);
        const canEdit = true;
        const dirty = false;
        const someModalOpen = false;
        const authCount = 6;
        const dirtinessData = { globalSettings, formConfigurations, ssoConfigurations, secondaryConfigurations };

        wrapper.setState({
            ssoConfigurations,
            formConfigurations,
            secondaryConfigurations,
            globalSettings,
            primaryProviders,
            secondaryProviders,
            loading: false,
            canEdit,
            dirty,
            someModalOpen,
            authCount,
            dirtinessData,
        });
    });

    test('Cancel button triggers', () => {
        const cancelButton = wrapper.find('.parent-panel__cancel-button').at(0);
        cancelButton.simulate('click');

        expect(window.location.assign).toHaveBeenCalledWith(ActionURL.buildURL('admin', 'showAdmin'));
    });

    test('Making global checkbox fields dirty sets dirtiness flag, brings up alert message', () => {
        const checkbox = wrapper.find('input[type="checkbox"]').at(0);
        checkbox.simulate('change', { target: { checked: false, name: 'SelfRegistration' } });

        expect(wrapper.state()).toHaveProperty('dirty', true);
        expect(wrapper.find('.alert').text()).toContain(
            'You have unsaved changes to your authentication configurations.'
        );
    });
});
