//
// CUSTOMIZATION FOR IssuesController
//

var supportedActions =
{
    "details": true,
    "list" : true,
    "insert" : true,
    "update" : true,
    "admin" : true,
    "search" : true
};


function getActionHashBang(href)
{
    var uri = new URI(href);
    if (!startsWith(uri.pathname,LABKEY.contextPath + "/issues/"))
        return null;
    if (-1 != uri.search.indexOf("_print"))
        return null;
    if (!endsWith(uri.file,".view"))
        return null;
    var action = uri.file.substring(0, uri.file.length-".view".length);
    if (!(action in supportedActions))
        return null;
    var obj = LABKEY.ActionURL.getParameters("?" + uri.search);
    delete obj["_template"];
    delete obj["action"];
    var search = LABKEY.ActionURL.queryString(obj);
    return "#!" + search + (uri.search ? "&" : "") + "action=" + action;
}


function cacheableHash(hash)
{
    return -1 != hash.indexOf("action=list");
}



//
// some shared code that should be in LABKEY.Utils
//



var URI = Ext.extend(Object,
{
    constructor : function(u)
    {
        this.toString = function()
        {
            return this.protocol + "://" + this.host + this.pathname + this.search;
        };
        if (typeof u == "string")
            this.parse(u);
        else if (typeof u == "object")
            Ext.apply(this,u);

        this.options = Ext.apply({},this.options);  // clone
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


(function()
{

    var _pageBodyEl = null;

    function getPageBodyElement()
    {
        if (!_pageBodyEl)
            _pageBodyEl = Ext.get(Ext.DomQuery.selectNode("TD.labkey-body-panel[id=bodypanel]"));
        return _pageBodyEl;
    }



    var _cachedPage = {hashbang: null, html:null};


    function navigateTo(event, href)
    {
        var hashbang = getActionHashBang(href);
        if (!hashbang)
            return;

        if (event)
            event.stopEvent();

        var el = getPageBodyElement();
        window.location = hashbang;

        if (cacheableHash(hashbang) && _cachedPage.hashbang==hashbang && _cachedPage.html)
        {
            console.log("CACHED: " + hashbang);
            el.update(_cachedPage.html);
            hijackAnchorTags(el);
        }
        else
        {
            console.log("FETCH:  " + hashbang);
            if (-1 == href.indexOf('?'))
                href += '?';
            href += "&_template=None";
            el.mask();
            el.getUpdater().update(
            {
                url: href,
                callback : function(el)
                {
                    el.unmask();
                    if (cacheableHash(hashbang))
                    {
                        _cachedPage.hashbang = hashbang;
                        _cachedPage.html = el.dom.innerHTML;
                    }
                    hijackAnchorTags(el);
                }
            });
        }
    }


    function onHashBangAnchorClick(event)
    {
        try
        {
            var target = Ext.get(event.target);
            var anchor = target.findParent("A");
            var href = anchor.href;

            navigateTo(event, href);
        }
        catch (exception)
        {

        }
    }


    function hijackAnchorTag(a)
    {
        var hashbang = getActionHashBang(a.href);
        if (!hashbang)
            return;
        Ext.EventManager.addListener(a, 'click', onHashBangAnchorClick);
    }


    function hijackAnchorTags(el)
    {
        el = Ext.get(el);
        if (!el)
            return;
        var anchors = Ext.DomQuery.jsSelect("A", el.dom);
        Ext.each(anchors,hijackAnchorTag);
    }


    // are we rendered in a recognized URL? then hijack other recognized URLs
    if (getActionHashBang(window.location.href))
        Ext.onReady(function(){hijackAnchorTags(getPageBodyElement());});
})();
