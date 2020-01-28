import React from 'react';

import { shallow } from 'enzyme';
import EnzymeToJson from 'enzyme-to-json';

import DatabaseConfigurationModal from './DatabaseConfigurationModal';

describe('<DatabaseConfigurationModal/>', () => {
    test('View-only', () => {
        const component = <DatabaseConfigurationModal canEdit={false} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test('Editable', () => {
        const component = <DatabaseConfigurationModal canEdit={true} />;

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });
});
