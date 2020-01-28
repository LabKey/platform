import React from 'react';

import { shallow } from 'enzyme';
import EnzymeToJson from 'enzyme-to-json';

import { CAS_MODAL_TYPE, LDAP_MODAL_TYPE, SAML_MODAL_TYPE, DUO_MODAL_TYPE } from '../../../test/data';

import DynamicConfigurationModal from './DynamicConfigurationModal';

describe('<DynamicConfigurationModal/>', () => {
    test('CAS Modal', () => {
        const component = <DynamicConfigurationModal modalType={CAS_MODAL_TYPE} provider="CAS" canEdit={true} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('CAS Modal View-only', () => {
        const component = <DynamicConfigurationModal modalType={CAS_MODAL_TYPE} provider="CAS" canEdit={false} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('LDAP Modal', () => {
        const component = <DynamicConfigurationModal modalType={LDAP_MODAL_TYPE} provider="LDAP" canEdit={true} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('LDAP Modal View-only', () => {
        const component = <DynamicConfigurationModal modalType={LDAP_MODAL_TYPE} provider="LDAP" canEdit={false} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('SAML Modal', () => {
        const component = <DynamicConfigurationModal modalType={SAML_MODAL_TYPE} provider="SAML" canEdit={true} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('SAML Modal View-only', () => {
        const component = <DynamicConfigurationModal modalType={SAML_MODAL_TYPE} provider="SAML" canEdit={false} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Duo Modal', () => {
        const component = (
            <DynamicConfigurationModal modalType={DUO_MODAL_TYPE} provider="Duo 2 Factor" canEdit={true} />
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Duo Modal View-only', () => {
        const component = (
            <DynamicConfigurationModal modalType={DUO_MODAL_TYPE} provider="Duo 2 Factor" canEdit={false} />
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });
});
