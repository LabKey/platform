import React from 'react';

import renderer from 'react-test-renderer';
import EnzymeToJson from 'enzyme-to-json';

import { shallow } from 'enzyme';

import {
    SSO_CONFIGURATIONS,
    FORM_CONFIGURATIONS,
    SECONDARY_CONFIGURATIONS,
    PRIMARY_PROVIDERS,
    SECONDARY_PROVIDERS,
    HELP_LINK,
} from '../../../test/data';

import AuthConfigMasterPanel from './AuthConfigMasterPanel';

describe('<AuthConfigMasterPanel/>', () => {
    test('Editable mode', () => {
        const basicFn = () => {};
        const actionFns = { name: basicFn };
        const component = (
            <AuthConfigMasterPanel
                formConfigurations={FORM_CONFIGURATIONS}
                ssoConfigurations={SSO_CONFIGURATIONS}
                secondaryConfigurations={SECONDARY_CONFIGURATIONS}
                primaryProviders={PRIMARY_PROVIDERS}
                secondaryProviders={SECONDARY_PROVIDERS}
                helpLink={HELP_LINK}
                canEdit={true}
                isDragDisabled={false}
                actionFunctions={actionFns}
            />
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('View-only mode', () => {
        const basicFn = () => {};
        const actionFns = { name: basicFn };
        const component = (
            <AuthConfigMasterPanel
                canEdit={false}
                actionFunctions={actionFns}
                ssoConfigurations={SSO_CONFIGURATIONS}
                formConfigurations={FORM_CONFIGURATIONS}
                secondaryConfigurations={SECONDARY_CONFIGURATIONS}
            />
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});
