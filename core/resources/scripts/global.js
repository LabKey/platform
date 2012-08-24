/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

function createStubs(o, props)
{
    for (var i = 0; i < props.length; i++)
    {
        let p = props[i];
        Object.defineProperty(o, p, {
            get: function () { throw new Error("'" + p + "' cannot be used in server-side scripts."); },
            set: function (value) { throw new Error("'" + p + "' cannot be used in server-side scripts."); }
        });
    }

    return o;
}

// minimal browser globals to compile and eval Ext.js server-side scripts.
window = { };
window.alert = alert = function (msg) {
    throw new Error(msg);
};

window.location = location = Object.create({}, {
    // returns a URLHelper
    url: {
        get: function () {
            return org.labkey.api.view.HttpView.contextURLHelper;
        }
    },

    host: {
        get: function () {
            return this.url.host + ":" + this.url.port;
        },
        set: function (value) { throw new Error("window.location.host cannot be set in server-side scripts"); }
    },

    port: {
        get: function () {
            return this.url.port;
        },
        set: function (value) { throw new Error("window.location.port cannot be set in server-side scripts"); }
    },

    hostname: {
        get: function () {
            return this.url.host;
        },
        set: function (value) { throw new Error("window.location.hostname cannot be set in server-side scripts"); }
    },

    href: {
        get: function () {
            return this.url.toString();
        },
        set: function (value) { throw new Error("window.location.href cannot be set in server-side scripts"); }
    },

    pathname: {
        get: function () {
            return this.url.path;
        },
        set: function (value) { throw new Error("window.location.pathname cannot be set in server-side scripts"); }
    },

    protocol: {
        get: function () {
            return this.url.scheme + ":";
        },
        set: function (value) { throw new Error("window.location.protocol cannot be set in server-side scripts"); }
    },

    hash: {
        get: function () {
            return '#' + this.url.fragment;
        },
        set: function (value) { throw new Error("window.location.hash cannot be set in server-side scripts"); }
    },

    search: {
        get: function () {
            return '?' + this.url.query;
        },
        set: function (value) { throw new Error("window.location.search cannot be set in server-side scripts"); }
    }

});
Object.freeze(window.location);

window.navigator = navigator = {
    userAgent:  "LabKey Server"
};

createStubs(navigator, ["appCodeName", "appName", "appVersion", "buildID", "cookieEnabled", "language", "mimeTypes", "onLine", "oscpu", "platform", "plugins", "product", "productStub", "vendor", "vendorStub"]);
Object.freeze(window.navigator);

window.document = document = {
    body: undefined,
    compatMode: "CSS1Compat",
    documentElement: undefined,
    documentMode : 8,

    getElementById: function () { return undefined; },
    getElementByClassName: function () { return undefined; },
    getElementByName: function () { return undefined; },
    getElementByTagName: function () { return undefined; },
    getElementByTagNameNS: function () { return undefined; },
    getElementsByTagName: function () { return undefined; },

    createElement: function () {
        throw new Error("Can't create elements in server-side script");
    }
};

createStubs(document, ["activeElement", "alinkColor", "all", "anchors", "applets", "async", "attributes", "baseURI", "baseURIObject", "bgColor", "characterSet", "childNodes", "cookie", "currentScript", "defaultView", "designMode", "doctype", "documentURI", "documentURIObject", "domain", "domConfig", "embeds", "fgColor", "firstChild", "forms", "height", "images", "implementation", "inputEncoding", "lastChild", "lastModified", "lastStyleSheetSet", "linkColor", "links", "location", "namespaceURI", "nodeName", "nodeType", "nodeValue", "nodePrincipal", "plugins", "popupNode", "preferredStyleSheetSet", "previousSibling", "readyState", "referrer", "selectedStyleSheetSet", "strictErrorChecking", "styleSheets", "styleSheetSets", "textContent", "title", "tooltipNode", "URL", "vlinkColor", "width", "xmlEncoding", "xmlStandalone", "xmlVersion"]);
Object.freeze(window.document);
Object.freeze(window);

function setTimeout() { throw new Error("setTimeout not supported in server-side script"); }
//function setInterval() { throw new Error("setInterval not supported in server-side script"); }
function setInterval() {
    //ignore
}

function clearTimeout() { throw new Error("clearTimeout not supported in server-side script"); }
function clearInterval() { throw new Error("clearInterval not supported in server-side script"); }

delete createStubs;
