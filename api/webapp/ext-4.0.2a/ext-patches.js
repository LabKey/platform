// 12939: Data Browser - Column header filter menus end up at top of screen
// Fix from : http://www.sencha.com/forum/showthread.php?138131
Ext4.override(Ext4.menu.Menu, {
    doConstrain : function() {
        var me = this,
                y = me.el.getY(),
                max, full,
                vector,
                returnY = y, normalY, parentEl, scrollTop, viewHeight;

        delete me.height;
        me.setSize();
        full = me.getHeight();
        if (me.floating) {
            parentEl = Ext.fly(me.el.dom.parentNode);

            /* Begin fix for 12939 */
            if(me.resetEl && me.resetEl.dom == parentEl.dom) {
                parentEl = Ext.getBody();
            }
            /* End fix for 12939 */

            scrollTop = parentEl.getScroll().top;
            viewHeight = parentEl.getViewSize().height;
            //Normalize y by the scroll position for the parent element.  Need to move it into the coordinate space
            //of the view.
            normalY = y - scrollTop;
            max = me.maxHeight ? me.maxHeight : viewHeight - normalY;
            if (full > viewHeight) {
                max = viewHeight;
                //Set returnY equal to (0,0) in view space by reducing y by the value of normalY
                returnY = y - normalY;
            } else if (max < full) {
                returnY = y - (full - max);
                max = full;
            }
        }else{
            max = me.getHeight();
        }
        // Always respect maxHeight
        if (me.maxHeight){
            max = Math.min(me.maxHeight, max);
        }
        if (full > max && max > 0){
            me.layout.autoSize = false;
            me.setHeight(max);
            if (me.showSeparator){
                me.iconSepEl.setHeight(me.layout.getRenderTarget().dom.scrollHeight);
            }
        }
        vector = me.getConstrainVector(me.el.dom.parentNode);
        if (vector) {
            me.setPosition(me.getPosition()[0] + vector[0]);
        }
        me.el.setY(returnY);
    }
});