import React from 'react';
import { App } from './AuthenticationConfiguration';
import renderer from 'react-test-renderer';
import {mount, shallow} from "enzyme";
import AuthConfigMasterPanel from "../components/AuthConfigMasterPanel";

let component;
let wrapper;
let ssoConfigurations : [ {
    "IdPSsoUrl" : "IdP SSO URL",
    "configuration" : 95,
    "headerLogoUrl" : null,
    "loginLogoUrl" : null,
    "description" : "SAML Configuration",
    "NameIdFormat" : "transient",
    "IdPSigningCertificate" : "certificateText"
    "enabled" : true,
    "ForceAuth" : true,
    "IssuerUrl" : "",
    "provider" : "SAML",
    "details" : "IdP SSO URL",
    "SpEncryptCert" : ""
}, {
    "provider" : "CAS",
    "configuration" : 106,
    "headerLogoUrl" : "imageUrl1",
    "loginLogoUrl" : "imageUrl2",
    "serverUrl" : "https://www.labkey.org/cas",
    "description" : "CAS Configuration",
    "details" : "https://www.labkey.org/cas",
    "autoRedirect" : false,
    "enabled" : true
}, {
    "provider" : "CAS",
    "configuration" : 108,
    "headerLogoUrl" : null,
    "loginLogoUrl" : null,
    "serverUrl" : "https://www.labkey.org/cas",
    "description" : "CAS Configuration 2",
    "details" : "https://www.labkey.org/cas",
    "autoRedirect" : false,
    "enabled" : true
} ];
let updatedConfig = {
        "provider" : "CAS",
        "configuration" : 108,
        "headerLogoUrl" : null,
        "loginLogoUrl" : null,
        "serverUrl" : "https://www.labkey.org/cas",
        "description" : "CAS Configuration X",
        "details" : "https://www.labkey.org/cas",
        "autoRedirect" : false,
        "enabled" : true };

describe("<AuthenticationConfiguration/>", () => {
    beforeEach(() => {
        component = (<App/>);
        wrapper = shallow(component);

        // wrapper.instance().componentDidMount();

    });

    test("Save button triggers", () => {
        const instance = wrapper.instance();
        instance.saveChanges = jest.fn(() => true);
        wrapper.update();

        let saveButton = wrapper.find(".parent-panel__save-button");
        saveButton.simulate('click');
        expect(instance.saveChanges).toHaveBeenCalled();
    });


    test("Updating an auth config will change row-level display", () => {
        // invoke updateAuthRowsAfterSave
        wrapper.setState({ ssoConfigurations });

        wrapper.instance().onDeleteClick = jest.fn(() => true);
        wrapper.update();

        // expect(spy).toHaveBeenCalledWith(108, updatedConfig);
        // expect(spy).toHaveBeenCalled();
    });

    test("Drag-and-drop call reorders rows", () => {
        // call re-ordering function

    });

    test("Drag-and-drop outside of rows doesn't reorder rows", () => {

    });

    test("Making draggable fields dirty sets dirtiness flag", () => {
        const component =
            <App

            />;
    });

    test("Making global checkbox fields dirty sets dirtiness flag", () => {

    });

    test("Dirty fields brings up save prompt", () => {

    });

    test("Leaving page while fields are dirty brings up warning alert", () => {

    });
});