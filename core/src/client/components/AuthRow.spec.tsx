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
    });

    test("Row deleted on click", () => {
        const wrapper = shallow<AuthRow>(component);

        wrapper.instance().onDeleteClick = jest.fn(() => true);
        wrapper.update();

        const editIcon = wrapper.find('.clickable').first();

        editIcon.simulate('click');
        expect(wrapper.instance().onDeleteClick).toHaveBeenCalled();
    });

    test("Highlight draggable handle on hover-over", () => {
        const wrapper = mount<AuthRow>(component);
        const row = wrapper.find('.auth-row');
        const spy = jest.spyOn(wrapper.instance(), 'setHighlight');
        wrapper.update();

        row.simulate("mouseOver");
        expect(wrapper.state("highlight")).toBe(true);
        expect(spy).toHaveBeenCalledTimes(1);

        row.simulate("mouseLeave");
        expect(wrapper.state("highlight")).toBe(false);
        expect(spy).toHaveBeenCalledTimes(2);
    });


    test("Non-draggable", () => {
        const wrapper = shallow<AuthRow>(component);
        wrapper.setProps({draggable: false});

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("Editable", () => {
        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });

    test("View-only", () => {
        const wrapper = shallow<AuthRow>(component);
        wrapper.setProps({canEdit: false});

        const tree = renderer.create(component).toJSON();
        expect(tree).toMatchSnapshot();
    });
});