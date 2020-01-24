import React from 'react';
import DragAndDropPane from './DragAndDropPane';
import {shallow} from "enzyme";
import EnzymeToJson from 'enzyme-to-json';

describe("<DragAndDropPane/>", () => {
    let component;
    let rowInfo = [ {
        "IdPSsoUrl" : "IdP SSO URL",
        "configuration" : 95,
        "headerLogoUrl" : null,
        "loginLogoUrl" : null,
        "description" : "SAML Configuration",
        "NameIdFormat" : "transient",
        "IdPSigningCertificate" : "certificateText",
        "enabled" : true,
        "ForceAuth" : true,
        "IssuerUrl" : "",
        "provider" : "SAML",
        "details" : "IdP SSO URL",
        "SpEncryptCert" : ""
    }, {
        "provider" : "CAS",
        "configuration" : 106,
        "headerLogoUrl" : "/labkey/auth_header_logo.image?configuration=106&revision=69",
        "loginLogoUrl" : "/labkey/auth_login_page_logo.image?configuration=106&revision=69",
        "serverUrl" : "https://www.labkey.org/cas",
        "description" : "CAS Configurations",
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

    beforeEach(() => {
        let basicFn = () => {};
        let actionFns = {"name": basicFn};

        component = (
            <DragAndDropPane
                rowInfo={rowInfo}
                canEdit={true}
                isDragDisabled={false}
                actionFunctions={actionFns}
            />
        );
    });

    test("Drag is disabled", () => {
        const wrapper = shallow<DragAndDropPane>(component);

        wrapper.setProps({isDragDisabled: true});
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test("View-only", () => {
        const wrapper = shallow<DragAndDropPane>(component);
        wrapper.setProps({canEdit: false});

        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });

    test("Editable", () => {
        const wrapper = shallow(component);
        expect(EnzymeToJson(wrapper)).toMatchSnapshot();
    });
});