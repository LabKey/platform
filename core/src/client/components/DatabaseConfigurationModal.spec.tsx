import React from 'react';
import DatabaseConfigurationModal from './DatabaseConfigurationModal';
import {shallow} from "enzyme";
import EnzymeToJson from 'enzyme-to-json';

describe("<DatabaseConfigurationModal/>", () => {

    test("View-only", () => {
        const component = (
            <DatabaseConfigurationModal canEdit={false}/>
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test("Editable", () => {
        const component = (
            <DatabaseConfigurationModal canEdit={true}/>
        );

        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });
});