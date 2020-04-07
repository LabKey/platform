import React from 'react';

import { shallow } from 'enzyme';
import EnzymeToJson from 'enzyme-to-json';

import { CAS_MODAL_TYPE, DUO_MODAL_TYPE, CAS_CONFIG, DUO_CONFIG } from '../../../test/data';

import DynamicConfigurationModal from './DynamicConfigurationModal';

describe('<DynamicConfigurationModal/>', () => {
    test('CAS Modal', () => {
        const component =
            <DynamicConfigurationModal
                authConfig={CAS_CONFIG}
                configType="ssoConfigurations"
                modalType={CAS_MODAL_TYPE}
                canEdit={true}
                updateAuthRowsAfterSave={() => {}}
                closeModal={() => {}}
            />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('CAS Modal View-only', () => {
        const component =
            <DynamicConfigurationModal
                authConfig={CAS_CONFIG}
                configType="ssoConfigurations"
                modalType={CAS_MODAL_TYPE}
                canEdit={false}
                updateAuthRowsAfterSave={() => {}}
                closeModal={() => {}}
            />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Duo Modal', () => {
        const component = (
            <DynamicConfigurationModal
                authConfig={DUO_CONFIG}
                configType={"secondaryConfigurations"}
                modalType={DUO_MODAL_TYPE}
                canEdit={true}
                updateAuthRowsAfterSave={() => {}}
                closeModal={() => {}}
            />
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Duo Modal View-only', () => {
        const component = (
            <DynamicConfigurationModal
                authConfig={DUO_CONFIG}
                configType={"secondaryConfigurations"}
                modalType={DUO_MODAL_TYPE}
                canEdit={false}
                updateAuthRowsAfterSave={() => {}}
                closeModal={() => {}}
            />
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });
});
