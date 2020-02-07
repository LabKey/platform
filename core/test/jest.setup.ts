/*
 * Copyright (c) 2017-2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import { configure } from 'enzyme'
import Adapter from 'enzyme-adapter-react-16'


import { JSDOM } from 'jsdom'
// Enzyme expects an adapter to be configured
// http://airbnb.io/enzyme/docs/installation/react-15.html
configure({ adapter: new Adapter() });


// http://airbnb.io/enzyme/docs/guides/jsdom.html
const jsdom = new JSDOM('<!doctype html><html><body></body></html>');
const { window } = jsdom;

function copyProps(src, target) {
    const props: any = Object.getOwnPropertyNames(src)
        .filter(prop => typeof target[prop] === 'undefined')
        .map(prop => Object.getOwnPropertyDescriptor(src, prop));
    Object.defineProperties(target, props);
}

global['window'] = window;
global['document'] = window.document;
global['navigator'] = {
    userAgent: 'node.js',
};
copyProps(window, global);