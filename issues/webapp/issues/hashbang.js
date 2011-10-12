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




//
// HashBang in-place navigation
//


LABKEY.NavigateInPlaceStrategy = Ext.extend(Object,
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

        Ext.onReady(this.init, this);
    },

    _pageBodyEl : null,

    getPageBodyElement : function()
    {
        if (!this._pageBodyEl)
            this._pageBodyEl = Ext.get(Ext.DomQuery.selectNode(this.bodySelector));
        return this._pageBodyEl;
    },


    getActionHashBang : function(href)
    {
        var uri = new URI(href);
        if (!startsWith(uri.pathname,LABKEY.contextPath + "/" + this.controller + "/"))
            return null;
        if (-1 != uri.search.indexOf("_print="))
            return null;
        if (!endsWith(uri.file,".view"))
            return null;
        var action = uri.file.substring(0, uri.file.length-".view".length);
        if (!(action in this.actions))
            return null;
        var obj = LABKEY.ActionURL.getParameters("?" + uri.search);
        delete obj["_template"];
        obj["_action"] = action;
        var search = LABKEY.ActionURL.queryString(obj);
        return "#!" + search;
    },

    _emptyPage : {hashbang: null, html:null},
    _cachedPage : null,


    _pushState : function(state)
    {
        console.log("PUSHSTATE: " + state.hashbang);
        window.history.pushState(state, '', state.hashbang);
    },


    /** private */
    _navigateInPlaceComplete : function(hashbang, href, pushNewState)
    {
        this._cachedPage = this._emptyPage;
        var el = this.getPageBodyElement();
        var page = {hashbang : hashbang, href : href};
        if (this.cacheable(hashbang, el))
            this._cachedPage = {hashbang : hashbang, href : href, html : el.dom.innerHTML};
        this._hijackAnchorTags(el);
        if (pushNewState)
            this._pushState(page);
    },


    /** private */
    _navigateInPlace : function(event, href, pushNewState)
    {
        var hashbang = this.getActionHashBang(href);
        if (!hashbang)
            return;

        if (event)
            event.stopEvent();

        var el = this.getPageBodyElement();
        Ext.EventManager.purgeElement(el, true);
        el.update("");

        if (this.cacheable(hashbang) && this._cachedPage.hashbang==hashbang && this._cachedPage.html)
        {
            console.log("CACHED: " + hashbang);
            el.update(this._cachedPage.html);
            this._hijackAnchorTags(el);
            if (pushNewState)
                pushState(this._cachedPage, "", hashbang);
        }
        else
        {
            console.log("FETCH:  " + hashbang);
            if (-1 == href.indexOf("_template=None"))
                href += ((-1 == href.indexOf('?')) ? '?' : '&') + "_template=None";
            el.getUpdater().update(
            {
                url: href,
                scripts: true,
                headers : {"X-ONUNAUTHORIZED":"UNAUTHORIZED"},  // hint: BASIC, UNAUTHORIZED, LOGIN
                callback : function(el, success, response, options)
                {
                    if (success)
                    {
                        this._navigateInPlaceComplete(hashbang, href, pushNewState);
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
            this._navigateInPlace(null, event.state.href, false);
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
        this._navigateInPlace(event, href, true);
    },


    _translateHashBangToHref : function(href)
    {
        href = href || window.location.href;
        var uri = new URI(window.location.href);

        var controller = LABKEY.ActionURL.getController();
        var action = LABKEY.ActionURL.getAction();
        var container = LABKEY.ActionURL.getContainer();
        //var parameters = LABKEY.ActionURL.getParameters(uri.search);
        var hash = {};
        var hashStr = uri.hash;
        if (startsWith(hashStr,"#!"))
            hash = LABKEY.ActionURL.getParameters("?" + hashStr.substring(2));
        if (hash["_action"])
            action = hash["_action"];
        delete hash["_action"];
        if (hash["_controller"])
            controller = hash["_controller"];
        //Ext.apply(parameters,hash);
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

        // verify that we're in an expected action (e.g. not a portal page) by trying to parse the current location
        // if not, then don't wire up in-place navigation
        var hashbang = this.getActionHashBang(window.location.href);
        if (!hashbang)
            return;

        window.addEventListener("hashchange", this.onWindowHashChange.createDelegate(this), false);
        window.addEventListener("popstate",   this.onWindowPopState.createDelegate(this), false);

        // If there is a #! on the URL at load time handle it now
        if (startsWith(window.location.hash, "#!"))
        {
            var url = this._translateHashBangToHref();
            if (this.getActionHashBang(url))
            {
                this._navigateInPlace(null, url, true);
                return;
            }
        }
        // otherwise fake up the initial state for this page (as if we called navigateInPlace())
        {
            this._navigateInPlaceComplete(hashbang, window.location.href, true);
        }
    }
});
