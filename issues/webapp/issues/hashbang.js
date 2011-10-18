/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
//
// some shared code that maybe should be in LABKEY.Utils
//

var URI = Ext.extend(Object,
{
    constructor : function(u)
    {
        if (typeof u == "string")
            this.parse(u);
        else if (typeof u == "object")
            Ext.apply(this,u);

        this.options = Ext.apply({},this.options);  // clone
    },
    toString: function()
    {
        return this.protocol + "://" + this.host + this.pathname + this.search;
    },
    parse: function(str)
    {
        var	o   = this.options;
        var m   = o.parser[o.strictMode ? "strict" : "loose"].exec(str);
        var uri = this || {};
        var i   = 14;

        while (i--)
            uri[o.key[i]] = m[i] || "";

        if (!uri.protocol)
        {
            var l = window.location;
            uri.protocol = uri.protocol || l.protocol;
            uri.port = uri.port || l.port;
            uri.hostname = uri.hostname || l.hostname;
            uri.host = uri.host || l.host;
        }
        if (uri.protocol && uri.protocol.charAt(uri.protocol.length-1) == ":")
            uri.protocol = uri.protocol.substr(0,uri.protocol.length - 1);

        uri[o.q.name] = {};
        uri[o.key[12]].replace(o.q.parser, function ($0, $1, $2)
        {
            if ($1) uri[o.q.name][$1] = $2;
        });
        uri.href = this.protocol + "://" + this.host + this.pathname + this.search;
        return uri;
    },
    options:
    {
        strictMode: false,
        key: ["source","protocol","host","userInfo","user","password","hostname","port","relative","pathname","directory","file","search","hash"],
        q:
        {
            name:   "query",
            parser: /(?:^|&)([^&=]*)=?([^&]*)/g
        },
        parser:
        {
            strict: /^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,
            loose:  /^(?:(?![^:@]+:[^:@\/]*@)([^:\/?#.]+):)?(?:\/\/)?((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?)(((\/(?:[^?#](?![^?#\/]*\.[^?#\/.]+(?:[?#]|$)))*\/?)?([^?#\/]*))(?:\?([^#]*))?(?:#(.*))?)/
        }
    }
});
function startsWith(s, f)
{
    var len = f.length;
    if (s.length < len) return false;
    if (len == 0)
        return true;
    return s.charAt(0) == f.charAt(0) && s.charAt(len-1) == f.charAt(len-1) && s.indexOf(f) == 0;
}
function endsWith(s, f)
{
    var len = f.length;
    var slen = s.length;
    if (slen < len) return false;
    if (len == 0)
        return true;
    return s.charAt(slen-len) == f.charAt(0) && s.charAt(slen-1) == f.charAt(len-1) && s.indexOf(f) == slen-len;
}
function moveChildren(from,to)
{
    to = Ext.getDom(to);
    from = Ext.getDom(from);
    var childNodes = from.childNodes;
    var length = childNodes.length;
    var copy = [];
    for (var i=0 ; i < length ; i++)
        copy.push(childNodes[i]);
    for (var i=0 ; i < length ; i++)
        to.appendChild(copy[i]);
}
function clearChildren(el)
{
    el = Ext.getDom(el);
    var childNodes =  el.childNodes;
    var length = childNodes.length;
    for (var i=0 ; i < length ; i++)
        Ext.EventManager.purgeElement(childNodes[i], true);
    el.innerHTML = "";
}


//
// Navigation strategies
//


LABKEY.DefaultNavigationStrategy = Ext.extend(Object,
{
    navigateTo : function(href)
    {
        window.location = href;
    }
});



LABKEY.NavigateInPlaceStrategy = Ext.extend(LABKEY.DefaultNavigationStrategy,
{
    // config defaults
    bodySelector : "TD.labkey-body-panel[id=bodypanel]",
    cacheable : function() {return false;},
    controller : "",
    actions : {},

    constructor: function(config)
    {
        config = config || {};
        Ext.apply(this, config);
        this._cachedPage = this._emptyPage;
        this._currentPage = this._emptyPage;

        Ext.onReady(this.init, this);
    },


    /* private */
    _superNavigateTo : function(href)
    {
        LABKEY.NavigateInPlaceStrategy.prototype.superclass.navigateTo.call(this, href);
    },


    _pageBodyEl : null,

    getPageBodyElement : function()
    {
        if (!this._pageBodyEl)
        {
            this._pageBodyEl = Ext.get(Ext.DomQuery.selectNode(this.bodySelector));
        }
        return this._pageBodyEl;
    },


    getActionHashBang : function(href)
    {
        var uri = new URI(href);
        if (!startsWith(uri.pathname,LABKEY.contextPath + "/" + this.controller + "/"))
            return null;
        if (-1 != uri.search.indexOf("_print=") || -1 != uri.search.indexOf("_template"))
            return null;
        if (!endsWith(uri.file,".view"))
            return null;
        var action = uri.file.substring(0, uri.file.length-".view".length);
        if (!(action in this.actions))
            return null;
        var obj = LABKEY.ActionURL.getParameters("?" + uri.search);
        obj["_action"] = action;
        var search = LABKEY.ActionURL.queryString(obj);
        return "#!" + search;
    },


    _emptyPage : {hashbang: null, html:null},
    _cachedPage : null,
    _currentPage : null,
    _cachedElParent : null,


    _pushState : function(state)
    {
        console.log("PUSHSTATE: " + state.hashbang);
        window.history.pushState(state, '', state.hashbang);
    },


    /** private */
    _navigateInPlaceComplete : function(href, hashbang, pushNewState)
    {
        var pageEl = this.getPageBodyElement();
        this._currentPage = {hashbang : hashbang, href : href};
        if (this.cacheable(hashbang))
        {
            this._cachedPage = this._emptyPage;
            clearChildren(this._cachedElParent);
        }
        this._hijackAnchorTags(pageEl);
        if (pushNewState !== false)
            this._pushState(this._currentPage);
    },


    /** private */
    _navigateInPlace : function(href, event, pushNewState)
    {
        pushNewState = (pushNewState !== false);
        var hashbang = this.getActionHashBang(href);
        if (!hashbang)
        {
            this._superNavigateTo(href);
            return;
        }

        if (event)
            event.stopEvent();

        var el = this.getPageBodyElement();

        // if the current page is cacheable save it
        if (this.cacheable(this._currentPage.hashbang))
        {
            this._cachedPage = this._emptyPage;
            if (this.cacheable(this._currentPage.hashbang, el))
            {
                clearChildren(this._cachedElParent);
                moveChildren(el, this._cachedElParent);
                this._cachedPage = this._currentPage;
            }
        }
        clearChildren(el);


        // is the target page cacheable? does it match the cached page?
        if (this.cacheable(hashbang) && this._cachedPage.hashbang==hashbang)
        {
            console.log("CACHED: " + hashbang);
            moveChildren(this._cachedElParent, el);
            if (pushNewState)
                this._pushState(this._cachedPage);
            this._currentPage = this._cachedPage;
        }
        else
        {
            console.log("FETCH:  " + hashbang);
            el.getUpdater().update(
            {
                url: href,
                scripts: true,
                headers : {"X-ONUNAUTHORIZED":"UNAUTHORIZED", "X-TEMPLATE":"None"},  // hint: BASIC, UNAUTHORIZED, LOGIN
                callback : function(el, success, response, options)
                {
                    if (success)
                    {
                        this._navigateInPlaceComplete(href, hashbang, pushNewState);
                        return;
                    }
                    else if (401 == response.status) // unauthorized
                    {
                        if (LABKEY.user.isGuest)
                        {
                            var login = LABKEY.ActionURL.buildURL("login", "login", LABKEY.ActionURL.getContainer(), {returnUrl: window.location});
                            window.location = login;
                            return;
                        }
                    }
                    else
                    {
                        el.update(Ext.Util.Format.htmlEncode(response.status + " " + response.statusText));
                    }
                },
                scope : this
            });
        }
    },


    onWindowPopState : function(event)
    {
        if (event.state && event.state.href)
        {
            this._navigateInPlace(event.state.href, event, false);
        }
    },

    onWindowHashChange : function()
    {
        console.log("HASHCHANGE: " + window.location.hash);
    },


    onHashBangAnchorClick : function(event)
    {
        var target = Ext.get(event.target);
        var anchor = target.findParent("A");
        var href = anchor.href;
        this._navigateInPlace(href, event, true);
    },


    _translateHashBangToHref : function(href)
    {
        href = href || window.location.href;
        var uri = new URI(window.location.href);

        var controller = LABKEY.ActionURL.getController();
        var action = LABKEY.ActionURL.getAction();
        var container = LABKEY.ActionURL.getContainer();
        var hash = {};
        var hashStr = uri.hash;
        if (startsWith(hashStr,"#!"))
            hash = LABKEY.ActionURL.getParameters("?" + hashStr.substring(2));
        if (hash["_action"])
            action = hash["_action"];
        delete hash["_action"];
        if (hash["_controller"])
            controller = hash["_controller"];
        delete hash["_controller"];
        var url = LABKEY.ActionURL.buildURL(controller, action, container, hash);
        return url;
    },


    /* private */
    _hijackAnchorTag : function(a)
    {
        var el = Ext.fly(a);
        if (el.hasClass("no-hijack"))
            return;
        var hashbang = this.getActionHashBang(el.dom.href);
        if (!hashbang)
            return;
        Ext.EventManager.addListener(el.dom, 'click', this.onHashBangAnchorClick, this);
    },


    /* private */
    _hijackAnchorTags : function(el)
    {
        el = Ext.get(el);
        if (!el)
            return;
        var anchors = Ext.DomQuery.jsSelect("A", el.dom);
        Ext.each(anchors, this._hijackAnchorTag, this);
    },


    /* public */
    init : function()
    {
	    if (!this.getPageBodyElement())
		    return;

        //create a place to stached cached nodes
        var d = Ext.DomHelper.append(Ext.getBody(), {tag:'div', class:'x-hidden'});
        this._cachedElParent = Ext.get(d);

        // verify that we're in an expected action (e.g. not a portal page) by trying to parse the current location
        // if not, then don't wire up in-place navigation
        var hashbang = this.getActionHashBang(window.location.href);
        if (!hashbang)
            return;

        window.addEventListener("hashchange", this.onWindowHashChange.createDelegate(this), false);
        window.addEventListener("popstate",   this.onWindowPopState.createDelegate(this), false);

        // if there is a #! on the URL at load time handle it now
        if (startsWith(window.location.hash, "#!"))
        {
            // TODO if url is unchanged don't call _navigateInPlace()
            var url = this._translateHashBangToHref();
            this._navigateInPlace(url, null, true);
            return
        }
        this._navigateInPlaceComplete(window.location.href, hashbang, true);
    },


    /* public */
    navigateTo : function(href)
    {
        try
        {
            var uri = new URI(href);
            if (startsWith(uri.hash, "#!"))
                href = this._translateHashBangToHref();
        }
        catch (x)
        {
        }
        this._navigateInPlace(href, null, true);
    }
});
