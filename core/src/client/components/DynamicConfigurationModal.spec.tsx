import React from 'react';

import { shallow } from 'enzyme';
import EnzymeToJson from 'enzyme-to-json';

import { CAS_MODAL_TYPE, DUO_MODAL_TYPE, CAS_CONFIG, DUO_CONFIG } from '../../../test/data';

import DynamicConfigurationModal from './DynamicConfigurationModal';

const noop = () => {};

describe('<DynamicConfigurationModal/>', () => {
    test('CAS Modal', () => {
        const wrapper = shallow(
            <DynamicConfigurationModal
                authConfig={CAS_CONFIG}
                configType="ssoConfigurations"
                modalType={CAS_MODAL_TYPE}
                canEdit={true}
                updateAuthRowsAfterSave={noop}
                closeModal={noop}
            />
        );
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('CAS Modal View-only', () => {
        const wrapper = shallow(
            <DynamicConfigurationModal
                authConfig={CAS_CONFIG}
                configType="ssoConfigurations"
                modalType={CAS_MODAL_TYPE}
                canEdit={false}
                updateAuthRowsAfterSave={noop}
                closeModal={noop}
            />
        );
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Duo Modal', () => {
        const wrapper = shallow(
            <DynamicConfigurationModal
                authConfig={DUO_CONFIG}
                configType="secondaryConfigurations"
                modalType={DUO_MODAL_TYPE}
                canEdit={true}
                updateAuthRowsAfterSave={noop}
                closeModal={noop}
            />
        );
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Duo Modal View-only', () => {
        const wrapper = shallow(
            <DynamicConfigurationModal
                authConfig={DUO_CONFIG}
                configType="secondaryConfigurations"
                modalType={DUO_MODAL_TYPE}
                canEdit={false}
                updateAuthRowsAfterSave={noop}
                closeModal={noop}
            />
        );
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });
});
