/*
 * Copyright (c) 2007-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (typeof LABKEY == "undefined")
{
    var LABKEY = {};
    LABKEY.contextPath = "must call init()";
    LABKEY.imagePath = "must call init()";
    LABKEY.devMode = false;
    LABKEY.yahooRoot = "_yui/build";
    LABKEY.extJsRoot = "ext-2.2";
    LABKEY.verbose = false;
    LABKEY.widget = {};
    LABKEY.hash = 0;
    LABKEY.dirty = false;
    LABKEY.submit = false;
    LABKEY.buttonBarMenu = false;
    LABKEY.fieldMarker = '@';

    LABKEY._requestedScriptFiles = [];
    LABKEY._loadedScriptFiles = {};
    LABKEY._emptyFunction = function(){};

    var nullConsole =
    {
        assert : LABKEY._emptyFunction,
        count : LABKEY._emptyFunction,
        debug : LABKEY._emptyFunction,
        dir : LABKEY._emptyFunction,
        dirxml: LABKEY._emptyFunction,
        error : LABKEY._emptyFunction,
        info : LABKEY._emptyFunction,
        group : LABKEY._emptyFunction,
        groupEnd : LABKEY._emptyFunction,
        log : LABKEY._emptyFunction,
        profile : LABKEY._emptyFunction,
        profileEnd : LABKEY._emptyFunction,
        time : LABKEY._emptyFunction,
        timeEnd : LABKEY._emptyFunction,
        trace : LABKEY._emptyFunction,
        warn : LABKEY._emptyFunction
    };
    if (!("console" in window))
    {
        window.console = nullConsole;
    }
    else
    {
        for (var f in nullConsole)
            if (!(f in console))
                console[f] = nullConsole[f];
        if (console.debug == LABKEY._emptyFunction)
            console.debug = console.warn;
        if (console.dir == LABKEY._emptyFunction)
            console.dir = function (o) {for (var p in o) console.debug(p + ": " + o[o]);};
    }
}


LABKEY.init = function(config)
{
    for (var p in config)
    {
        this[p] = config[p];
    }
};


LABKEY.requiresScript = function(file, immediate)
{
    if (arguments.length < 2)
        immediate = true;

//    console.log("requiresScript( " + file + " , " + immediate + " )");

    if (file.indexOf('/') == 0)
    {
        file = file.substring(1);
    }

    if (this._loadedScriptFiles[file])
        return;

    if (!immediate)
        this._requestedScriptFiles.push(file);
    else
    {
        this._loadedScriptFiles[file] = true;
//        console.log("<script href=" + file + ">");

        //although FireFox and Safari allow scripts to use the DOM
        //during parse time, IE does not. So if the document is
        //closed, use the DOM to create a script element and append it
        //to the head element. Otherwise (still parsing), use document.write()
        if(LABKEY.isDocumentClosed)
        {
            //create a new script element and append it to the head element
            LABKEY.addElemToHead("script", {
                src: LABKEY.contextPath + "/" + file + "?" + LABKEY.hash,
                type: "text/javascript"
            });
        }
        else
            document.write('\n<script type="text/javascript" language="javascript" src="' + LABKEY.contextPath + "/" + file + '?' + LABKEY.hash + '"></script>\n');
    }
};

LABKEY.addElemToHead = function(elemName, attributes, beforeFirst)
{
    var elem = document.createElement(elemName);
    for(var attr in attributes)
        elem[attr] = attributes[attr];
    var headElement = document.getElementsByTagName("head")[0];
    if (beforeFirst)
    {
        headElement.insertBefore(elem, headElement.firstChild);
    }
    else
    {
        headElement.appendChild(elem);
    }
};

LABKEY.addMarkup = function(html)
{
    if(LABKEY.isDocumentClosed)
    {
        var elem = document.createElement("div");
        elem.innerHTML = html;
        document.body.appendChild(elem.firstChild);
    }
    else
        document.write(html);
};


LABKEY.loadScripts = function()
{
    for (var i=0 ; i<this._requestedScriptFiles.length ; i++)
    {
        var file = this._requestedScriptFiles[i];
        LABKEY.requiresScript(file, true);
    }
    LABKEY.isDocumentClosed = true;
};


LABKEY.requiresCss = function(file, beforeFirst)
{
    var fullPath = LABKEY.contextPath + "/" + file;
    if (this._requestedScriptFiles[fullPath])
        return;
    //console.debug("<link href=" + fullPath);
    LABKEY.addElemToHead("link", {
        type: "text/css",
        rel: "stylesheet",
        href: fullPath
    }, beforeFirst);
    this._requestedScriptFiles[fullPath] = 1;
};


LABKEY.requiresYahoo = function(script, immediate)
{
    if (arguments.length < 2) immediate = true;

    var dir = script == "container_core" ? "container" : script;
    var base=LABKEY.yahooRoot + "/" + dir + "/" + script;
    var expanded = LABKEY.devMode ? (LABKEY.verbose ? base+"-debug.js" : base+".js") : base+"-min.js";
    LABKEY.requiresScript(expanded, immediate);
};

LABKEY.requiresExtJs = function(immediate)
{
    if (arguments.length < 1) immediate = true;
    // Require that these CSS files be placed first in the <head> block so that they can be overridden by user customizations
    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-patches.css', true);
    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-all.css', true);

    LABKEY.requiresScript(LABKEY.extJsRoot + "/adapter/ext/ext-base.js", immediate);
    if (LABKEY.devMode && false)
    {
        LABKEY.requiresScript("ext-exploded.js", immediate);
    }
    else
    {
        LABKEY.requiresScript(LABKEY.extJsRoot + "/ext-all" + (LABKEY.devMode ?  "-debug.js" : ".js"), immediate);
    }
    LABKEY.requiresScript(LABKEY.extJsRoot + "/ext-patches.js", immediate);
};

LABKEY.requiresClientAPI = function(immediate)
{
    if (arguments.length < 1) immediate = true;
    LABKEY.requiresExtJs(immediate);
    if (LABKEY.devMode)
    {
        //load individual scripts so that they get loaded from source tree
        LABKEY.requiresScript("clientapi/ExtJsConfig.js", immediate);
        LABKEY.requiresScript("clientapi/ActionURL.js", immediate);
        LABKEY.requiresScript("clientapi/Assay.js", immediate);
        LABKEY.requiresScript("clientapi/Chart.js", immediate);
        LABKEY.requiresScript("clientapi/Domain.js", immediate);
        LABKEY.requiresScript("clientapi/Experiment.js", immediate);
        LABKEY.requiresScript("clientapi/LongTextEditor.js", immediate);
        LABKEY.requiresScript("clientapi/EditorGridPanel.js", immediate);
        LABKEY.requiresScript("clientapi/Filter.js", immediate);
        LABKEY.requiresScript("clientapi/GridView.js", immediate);
        LABKEY.requiresScript("clientapi/NavTrail.js", immediate);
        LABKEY.requiresScript("clientapi/Query.js", immediate);
        LABKEY.requiresScript("clientapi/ExtendedJsonReader.js", immediate);
        LABKEY.requiresScript("clientapi/Store.js", immediate);
        LABKEY.requiresScript("clientapi/Utils.js", immediate);
        LABKEY.requiresScript("clientapi/WebPart.js", immediate);
        LABKEY.requiresScript("clientapi/QueryWebPart.js", immediate);
        LABKEY.requiresScript("clientapi/Security.js", immediate);
        LABKEY.requiresScript("clientapi/SecurityPolicy.js", immediate);
        LABKEY.requiresScript("clientapi/Specimen.js", immediate);
        LABKEY.requiresScript("clientapi/MultiRequest.js", immediate);
        LABKEY.requiresScript("clientapi/HoverPopup.js", immediate);
        LABKEY.requiresScript("clientapi/Form.js", immediate);
        LABKEY.requiresScript("clientapi/PersistentToolTip.js", immediate);
        LABKEY.requiresScript("clientapi/Message.js", immediate);
        LABKEY.requiresScript("clientapi/FormPanel.js", immediate);
        LABKEY.requiresScript("clientapi/Pipeline.js", immediate);
    }
    else
        LABKEY.requiresScript('clientapi/clientapi' + (LABKEY.devMode ? '.js' : '.min.js'), immediate);
    LABKEY.requiresScript('DataRegion.js', immediate);
};


/**
 * Event handler that can be attached to text areas to let them handle indent/outdent with TAB/SHIFT-TAB.
 * Handles region selection for multi-line indenting as well.
 * Note that this overrides the browser's standard focus traversal keystrokes.
 * Based off of postings from http://ajaxian.com/archives/handling-tabs-in-textareas
 * Wire it up with a call like:
 *     Ext.EventManager.on('queryText', 'keydown', handleTabsInTextArea);
 * @param event an Ext.EventObject for the keydown event
 */
function handleTabsInTextArea(event)
{
    // Check if the user hit TAB or SHIFT-TAB
    if (event.getKey() == Ext.EventObject.TAB && !event.ctrlKey && !event.altKey)
    {
        var t = event.target;

        if (Ext.isIE)
        {
            var range = document.selection.createRange();
            var stored_range = range.duplicate();
            stored_range.moveToElementText(t);
            stored_range.setEndPoint('EndToEnd', range);
            t.selectionStart = stored_range.text.length - range.text.length;
            t.selectionEnd = t.selectionStart + range.text.length;
            t.setSelectionRange = function(start, end)
            {
                var range = this.createTextRange();
                range.collapse(true);
                range.moveStart("character", start);
                range.moveEnd("character", end - start);
                range.select();
            };
        }

        var ss = t.selectionStart;
        var se = t.selectionEnd;
        var newSelectionStart = ss;
        var scrollTop = t.scrollTop;

        if (ss != se)
        {
            // In case selection was not the entire line (e.g. selection begins in the middle of a line)
            // we need to tab at the beginning as well as at the start of every following line.
            var pre = t.value.slice(0,ss);
            var sel = t.value.slice(ss,se);
            var post = t.value.slice(se,t.value.length);

            // If our selection starts in the middle of the line, include the full line
            if (pre.length > 0 && pre.lastIndexOf('\n') != pre.length - 1)
            {
                // Add the beginning of the line to the indented area
                sel = pre.slice(pre.lastIndexOf('\n') + 1, pre.length).concat(sel);
                // Remove it from the prefix
                pre = pre.slice(0, pre.lastIndexOf('\n') + 1);
                if (!event.shiftKey)
                {
                    // Add one to the starting index since we're going to add a tab before it
                    newSelectionStart++;
                }
            }
            // If our last selected character is a new line, don't add a tab after it since that's
            // part of the next line
            if (sel.lastIndexOf('\n') == sel.length - 1)
            {
                sel = sel.slice(0, sel.length - 1);
                post = '\n' + post;
            }

            // Shift means remove indentation
            if (event.shiftKey)
            {
                // Remove one tab after each newline
                sel = sel.replace(/\n\t/g,"\n");
                if (sel.indexOf('\t') == 0)
                {
                    // Remove one leading tab, if present
                    sel = sel.slice(1, sel.length);
                    // We're stripping out a tab before the selection, so march it back one character
                    newSelectionStart--;
                }
            }
            else
            {
                pre = pre.concat('\t');
                sel = sel.replace(/\n/g,"\n\t");
            }

            var originalLength = t.value.length;
            t.value = pre.concat(sel).concat(post);
            t.setSelectionRange(newSelectionStart, se + (t.value.length - originalLength));
        }
        // No text is selected
        else
        {
            // Shift means remove indentation
            if (event.shiftKey)
            {
                // Figure out where the current line starts
                var lineStart = t.value.slice(0, ss).lastIndexOf('\n');
                if (lineStart < 0)
                {
                    lineStart = 0;
                }
                // Look for the first tab
                var tabIndex = t.value.slice(lineStart, ss).indexOf('\t');
                if (tabIndex != -1)
                {
                    // The line has a tab - need to remove it
                    tabIndex += lineStart;
                    t.value = t.value.slice(0, tabIndex).concat(t.value.slice(tabIndex + 1, t.value.length));
                    if (ss == se)
                    {
                        ss--;
                        se = ss;
                    }
                    else
                    {
                        ss--;
                        se--;
                    }
                }
            }
            else
            {
                // Shove a tab in at the cursor
                t.value = t.value.slice(0,ss).concat('\t').concat(t.value.slice(ss,t.value.length));
                if (ss == se)
                {
                    ss++;
                    se = ss;
                }
                else
                {
                    ss++;
                    se++;
                }
            }
            t.setSelectionRange(ss, se);
        }
        t.scrollTop = scrollTop;

        // Don't let the browser treat it as a focus traversal
        event.preventDefault();
    }
};

function showMenu(parent, menuElementId, align) {
    if (!align)
        align = "tl-bl?";
    Ext.menu.MenuMgr.get(menuElementId).show(parent, align);
}

LABKEY.requiresMenu = function()
{
    //LABKEY.requiresCss(LABKEY.yahooRoot + '/menu/assets/menu.css');
    LABKEY.requiresYahoo('yahoo', true);
    LABKEY.requiresYahoo('event', true);
    if (LABKEY.devMode)
        LABKEY.requiresYahoo('logger', true);
    LABKEY.requiresYahoo('dom', true);
    LABKEY.requiresYahoo('container', true);
    LABKEY.requiresYahoo('menu', true);
};

LABKEY.setSubmit = function (submit)
{
    this.submit = submit;
};

LABKEY.setDirty = function (dirty)
{
    this.dirty = dirty;
};

LABKEY.isDirty = function () { return this.dirty; };
LABKEY.unloadMessage = "You will lose any changes made to this page.";

LABKEY.beforeunload = function (dirtyCallback, scope)
{
    return function () {
        if (!LABKEY.submit &&
            (LABKEY.isDirty() || (dirtyCallback && dirtyCallback.call(scope)))) {
            return LABKEY.unloadMessage;
        }
    };
};

//
// language extensions, global functions
//

function byId(id)
{
    return document.getElementById(id);
}


function trim(s)
{
  return s.replace(/^\s+/, '').replace(/\s+$/, '');
}

String.prototype.trim = function () {return trim(this);};


LABKEY.createElement = function(tag, innerHTML, attributes)
{
    var e = document.createElement(tag);
    if (innerHTML)
        e.innerHTML = innerHTML;
    if (attributes)
        for (var att in attributes)
            e[att] = attributes[att];
    return e;
};

LABKEY.toHTML = function(elem)
{
    if ('htmlText' in elem)
        return elem.htmlText;
    var y = document.createElement("SPAN");
    y.appendChild(elem);
    return y.innerHTML;
};

LABKEY.showNavTrail = function()
{
    var elem = document.getElementById("navTrailAncestors");
    if(elem)
        elem.style.visibility = "visible";
    elem = document.getElementById("labkey-nav-trail-current-page");
    if(elem)
        elem.style.visibility = "visible";
};
