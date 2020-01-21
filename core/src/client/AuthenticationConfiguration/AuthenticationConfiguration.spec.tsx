import React from 'react';
import { App } from './AuthenticationConfiguration';
import renderer from 'react-test-renderer';
import {mount} from "enzyme";
import AuthConfigMasterPanel from "../components/AuthConfigMasterPanel";

describe("<AuthenticationConfiguration/>", () => {

    test("Data lands on initial mount", () => {
        const component =
            <App/>;
    });

    test("Dirty fields brings up save prompt", () => {
        const component =
            <App/>;
    });
});