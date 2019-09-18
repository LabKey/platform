

import * as React from "react";
import {mount} from "enzyme";
import toJson from "enzyme-to-json";
import {App} from "./HelloWorld";

describe('HelloWorld', () => {

    test('HelloWorld Text', () => {

        const helloWorld  = mount(<App />);

        // Verify Hello World! text
        const helloWorldSpan = helloWorld.find({className: 'world-highlight'});
        expect(helloWorldSpan.length).toEqual(1);
        expect(helloWorldSpan.text()).toEqual("Hello World!");

        // Verify snapshot
        expect(toJson(helloWorld)).toMatchSnapshot();
        helloWorld.unmount();
    });
});