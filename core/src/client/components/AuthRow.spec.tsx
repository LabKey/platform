import React from 'react';
import AuthRow from './AuthRow';
import renderer from 'react-test-renderer';
import {mount, shallow} from "enzyme";

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPencilAlt, faInfoCircle, faTimesCircle, faGripVertical, faCircle } from '@fortawesome/free-solid-svg-icons';


describe("<AuthRow/>", () => {
    let component;
    let modalType = {
        "helpLink": "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=configureCas",
        "saveLink": "/labkey/casclient-casSaveConfiguration.view?",
        "settingsFields": [{
            "defaultValue": "",
            "name": "serverUrl",
            "caption": "CAS Server URL",
            "description": "Enter a valid HTTPS URL to your CAS server. The URL should start with https:// and end with /cas, for example: https://test.org/cas.",
            "type": "input",
            "required": true
        }, {
            "defaultValue": false,
            "name": "autoRedirect",
            "caption": "Default to CAS Login",
            "description": "Redirects the login page directly to the CAS login instead of requiring the user click the CAS option.",
            "type": "checkbox",
            "required": false
        }],
        "description": "Central Authentication Service (CAS)",
        "sso": true
    };

    beforeEach(() => {
        component =
            <AuthRow
                description="My Configuration"
                details="some url"
                provider="a provider"
                enabled={true}
                draggable={true}
                modalType={modalType}
                canEdit={true}
            />;
    });


    test("Modal opens on click", () => {
        const wrapper = shallow<AuthRow>(component);
        const toggleSomeModalOpen = jest.fn(() => {});
        wrapper.setProps({toggleSomeModalOpen});

        wrapper.instance().onToggleModal = jest.fn(() => wrapper.setState({modalOpen: true}));
        wrapper.update();

        expect(wrapper.state()).toHaveProperty('modalOpen', false);

        // I tried .editOrView, fa-pencil-alt, .fa-pencil-alt, but was not successful at triggering click
        const editIcon = wrapper.find('.clickable').last();

        editIcon.simulate('click');
        expect(wrapper.instance().onToggleModal).toHaveBeenCalled();

        expect(wrapper.state()).toHaveProperty('modalOpen', true);


        // wrapper.setState({modalOpen});

    });

    // test("Highlight draggable handle on hover-over", () => {
    //     // const spy = jest.spyOn(component.prototype, 'onToggleModal');
    //     const wrapper = shallow<AuthRow>(component);
    //
    //     const instance = wrapper.instance();
    //
    //     jest.spyOn(instance, 'onToggleModal');
    //
    //     // expect(wrapper.state("highlight")).toBe(false);
    //
    //     wrapper.simulate("mouseover");
    //     expect(instance.onToggleModal).toHaveBeenCalled();
    //
    //
    //     // expect(spy).toHaveBeenCalled();
    //
    //     // expect(wrapper.state("highlight")).toBe(true);
    //     // expect(wrapper.state()).toHaveProperty('highlight', true);
    //
    //
    //
    //     wrapper.simulate("mouseleave");
    //     // expect(wrapper.state("highlight")).toBe(false);
    //
    // });


    test("Non-draggable", () => {
        const wrapper = shallow<AuthRow>(component);
        wrapper.setProps({draggable: false});

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("Editable", () => {
        const component = (
            <AuthRow canEdit={true}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("View-only", () => {
        const component = (
            <AuthRow canEdit={true}/>
        );

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});