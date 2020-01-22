import React from 'react';
import { App } from './AuthenticationConfiguration';
import renderer from 'react-test-renderer';
import {mount} from "enzyme";
import AuthConfigMasterPanel from "../components/AuthConfigMasterPanel";

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

describe("<AuthenticationConfiguration/>", () => {

    test("Data lands on initial mount", () => {
        const component =
            <App/>;
    });

    test("Updating an auth config will change row-level display", () => {

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