import React from 'react';
import { mount } from 'enzyme';
import { ActionURL } from '@labkey/api';

import { App } from './AuthenticationConfiguration';

import {
    SSO_CONFIGURATIONS as ssoConfigurations,
    FORM_CONFIGURATIONS as formConfigurations,
    SECONDARY_CONFIGURATIONS as secondaryConfigurations,
    GLOBAL_SETTINGS as globalSettings,
    PRIMARY_PROVIDERS as primaryProviders,
    SECONDARY_PROVIDERS as secondaryProviders,
} from '../../../test/data';

let component;
let wrapper;

beforeAll(() => {
    window['__react-beautiful-dnd-disable-dev-warnings'] = true;
});

describe('<AuthenticationConfiguration/>', () => {
    const { location } = window;

    beforeAll(() => {
        delete window.location;
        window.location = Object.assign({ ...location }, {
            assign: jest.fn(),
        })
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
        let dirtinessData = { globalSettings, formConfigurations, ssoConfigurations, secondaryConfigurations };

        wrapper.setState({
            ssoConfigurations,
            formConfigurations,
            secondaryConfigurations,
            globalSettings,
            primaryProviders,
            secondaryProviders,
            canEdit,
            dirty,
            someModalOpen,
            authCount,
            dirtinessData
        });
    });

    test('Cancel button triggers', () => {
        wrapper.setState({loading: false});

        const cancelButton = wrapper.find('.parent-panel__cancel-button').at(0);
        cancelButton.simulate('click');

        expect(window.location.assign).toHaveBeenCalledWith(ActionURL.buildURL('admin', 'showAdmin'));
    });

    test('Making global checkbox fields dirty sets dirtiness flag, brings up alert message', () => {
        wrapper.setState({loading: false});

        let checkbox = wrapper.find('.fa-check-square').at(0);
        checkbox.simulate('click');

        expect(wrapper.state()).toHaveProperty('dirty', true);
        expect(wrapper.text()).toContain('You have unsaved changes to your authentication configurations.');
    });
});
