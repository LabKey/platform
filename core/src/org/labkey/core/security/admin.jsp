<%--
<style type="text/css">
.round {
  -webkit-border-radius: 3ex;
  -moz-border-radius: 3ex;
}
</style> --%>
<div id="adminFrame" class="extContainer"></div>
<script type="text/javascript">
LABKEY.requiresCss("SecurityAdmin.css");
LABKEY.requiresScript("SecurityAdmin.js");

var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper;

var $viewport = new Ext.Viewport();

// a frame that fits inside the labkey common template, and uses all the available space
var TemplateFrame = Ext.extend(Ext.Panel,{
    constructor : function(config)
    {
        TemplateFrame.superclass.constructor.call(this, config);
        $viewport.on("resize", this._resize, this);
    },
    _resize : function(v,w,h)
    {
        if (!this.rendered || !this.el)
            return;
        var xy = this.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-8),
            height : Math.max(100,h-xy[1]-8)};
        this.setSize(size);
        this.doLayout();
    }
});

var containerPanel = $('bodypanel');

Ext.onReady(function()
{
    var cache = new SecurityCache({});
    var policyEditor = new PolicyEditor({securityCache:cache, border:false});
    cache.onReady(function(){
        policyEditor.setResource(LABKEY.container.id);
    });

    var frame = new TemplateFrame({items:[policyEditor]});
    frame.render($('adminFrame'));
    var s = $viewport.getSize();
    $viewport.fireResize(s.width, s.height);

});
</script>
<div style="display:none;"><div id="lorem">Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer congue tristique est, rutrum luctus ante pellentesque id. Donec sit amet leo in arcu aliquam fringilla ut non eros. Duis vehicula varius lacus vulputate lacinia. Ut tempor cursus iaculis. Sed mollis purus in sem viverra id aliquam velit facilisis. Suspendisse sem nisl, imperdiet in rhoncus quis, dictum vel elit. Vestibulum aliquam ultricies pretium. Donec vitae urna eget sapien gravida mollis. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Duis eu mauris eget nulla gravida pellentesque non sit amet nulla. Nunc tempor lectus quis justo porttitor in consequat urna aliquam. Phasellus eu libero eget orci consectetur consectetur in a eros. Vivamus ligula orci, porta et accumsan ut, consequat non nunc.</div></div>
