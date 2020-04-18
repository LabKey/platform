import React from 'react';

import renderer from 'react-test-renderer';
import EnzymeToJson from 'enzyme-to-json';

import { shallow } from 'enzyme';

import {
    SSO_CONFIGURATIONS,
    FORM_CONFIGURATIONS,
    SECONDARY_CONFIGURATIONS,
    HELP_LINK,
    PRIMARY_PROVIDERS,
    SECONDARY_PROVIDERS,
} from '../../../test/data';

import AuthConfigMasterPanel from './AuthConfigMasterPanel';

describe('<AuthConfigMasterPanel/>', () => {
    test('Editable mode', () => {
        const onDragEnd = (result: {[key:string]: any}): void => {};
        const toggleModalOpen = (modalOpen: boolean): void => {};
        const onDelete = (configuration: number, configType: string): void => {};
        const updateAuthRowsAfterSave = (config: string, configType: string): void => {};
        const actionFns = {
            onDragEnd: onDragEnd, toggleModalOpen: toggleModalOpen,
            onDelete: onDelete, updateAuthRowsAfterSave: updateAuthRowsAfterSave
        };

        const component = (
            <AuthConfigMasterPanel
                formConfigurations={FORM_CONFIGURATIONS}
                ssoConfigurations={SSO_CONFIGURATIONS}
                secondaryConfigurations={SECONDARY_CONFIGURATIONS}
                primaryProviders={PRIMARY_PROVIDERS}
                secondaryProviders={SECONDARY_PROVIDERS}
                helpLink={HELP_LINK}
                canEdit={true}
                actions={actionFns}
            />
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('View-only mode', () => {
        const component = (
            <AuthConfigMasterPanel
                formConfigurations={FORM_CONFIGURATIONS}
                ssoConfigurations={SSO_CONFIGURATIONS}
                secondaryConfigurations={SECONDARY_CONFIGURATIONS}
                primaryProviders={PRIMARY_PROVIDERS}
                secondaryProviders={SECONDARY_PROVIDERS}
                helpLink={HELP_LINK}
                canEdit={false}
                actions={{onDragEnd: jest.fn, onDelete: jest.fn, updateAuthRowsAfterSave: jest.fn, toggleModalOpen: jest.fn}}
            />
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});
