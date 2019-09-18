import { configure } from 'enzyme'
import Adapter from 'enzyme-adapter-react-16'
import { JSDOM } from 'jsdom'

// Enzyme expects an adapter to be configured
// http://airbnb.io/enzyme/docs/installation/react-16.html
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