/*----------------------------------------------------------------------------\
|                          Range Slider 1.00                                  |
|-----------------------------------------------------------------------------|
|          Original single-handle slider Created by Erik Arvidsson            |
|                  (http://webfx.eae.net/contact.html#erik)                   |
|                      For WebFX (http://webfx.eae.net/)                      |
|           Modified to have two handles, defining a range, by David Stearns  |
               for LabKey (http://www.labkey.com)                    |
|-----------------------------------------------------------------------------|
| A  slider  control that  degrades  to an  input control  for non  supported |
| browsers.                                                                   |
|-----------------------------------------------------------------------------|
|                Copyright (c) 2002, 2003, 2006 Erik Arvidsson                |
|                Copyright (c) 2007 LabKey Corporation                        |
|-----------------------------------------------------------------------------|
| Licensed under the Apache License, Version 2.0 (the "License"); you may not |
| use this file except in compliance with the License.  You may obtain a copy |
| of the License at http://www.apache.org/licenses/LICENSE-2.0                |
| - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - |
| Unless  required  by  applicable law or  agreed  to  in  writing,  software |
| distributed under the License is distributed on an  "AS IS" BASIS,  WITHOUT |
| WARRANTIES OR  CONDITIONS OF ANY KIND,  either express or implied.  See the |
| License  for the  specific language  governing permissions  and limitations |
| under the License.                                                          |
|-----------------------------------------------------------------------------|
| Dependencies: timer.js - an OO abstraction of timers                        |
|               range.js - provides the data model for the slider             |
|-----------------------------------------------------------------------------|
| 2002-10-14 | Original version released                                      |
| 2003-03-27 | Added a test in the constructor for missing oElement arg       |
| 2003-11-27 | Only use mousewheel when focused                               |
| 2006-05-28 | Changed license to Apache Software License 2.0.                |
| 2007-08-22 | Modified to be a range slider with lower and upper handles     |
|-----------------------------------------------------------------------------|
| Created 2002-10-14 | All changes are in the log above. | Updated 2007-08-22 |
\----------------------------------------------------------------------------*/

Slider.isSupported = typeof document.createElement != "undefined" &&
    typeof document.documentElement != "undefined" &&
    typeof document.documentElement.offsetWidth == "number";


function Slider(oElement, oInputLow, oInputHigh, sOrientation) {
    if (!oElement) return;
    this._orientation = sOrientation || "horizontal";
    this._range = new Range();
    this._range.setExtent(0);
    this._blockIncrement = 10;
    this._unitIncrement = 1;
    this._precision = 0;
    this._timer = new Timer(100);


    if (Slider.isSupported && oElement) {

        this.document = oElement.ownerDocument || oElement.document;

        this.element = oElement;
        this.element.slider = this;
        this.element.unselectable = "on";

        // add class name tag to class name
        this.element.className = this._orientation + " " + this.classNameTag + " " + this.element.className;

        // create line
        this.line = this.document.createElement("DIV");
        this.line.className = "line";
        this.line.unselectable = "on";
        this.line.appendChild(this.document.createElement("DIV"));
        this.element.appendChild(this.line);

        // create lower handle
        this.handleLow = this.document.createElement("DIV");
        this.handleLow.className = "handle";
        this.handleLow.unselectable = "on";
        this.handleLow.appendChild(this.document.createElement("DIV"));
        this.handleLow.firstChild.appendChild(
            this.document.createTextNode(String.fromCharCode(160)));
        this.element.appendChild(this.handleLow);

        // create upper handle
        this.handleHigh = this.document.createElement("DIV");
        this.handleHigh.className = "handle";
        this.handleHigh.unselectable = "on";
        this.handleHigh.appendChild(this.document.createElement("DIV"));
        this.handleHigh.firstChild.appendChild(
            this.document.createTextNode(String.fromCharCode(160)));
        this.element.appendChild(this.handleHigh);

        //create the selected range highlight line
        this.rangeline = this.document.createElement("DIV");
        this.rangeline.className = "range-line";
        this.rangeline.unselectable = "on";
        this.rangeline.appendChild(this.document.createElement("DIV"));
        this.element.appendChild(this.rangeline);
    }

    this.inputLow = oInputLow;
    this.inputHigh = oInputHigh;

    // events
    var oThis = this;
    this._range.onchange = function () {
        oThis.recalculate();
        if (typeof oThis.onchange == "function")
            oThis.onchange();
    };

    if (Slider.isSupported && oElement) {
        this.element.onfocus        = Slider.eventHandlers.onfocus;
        this.element.onblur         = Slider.eventHandlers.onblur;
        this.element.onmousedown    = Slider.eventHandlers.onmousedown;
        this.element.onmouseover    = Slider.eventHandlers.onmouseover;
        this.element.onmouseout     = Slider.eventHandlers.onmouseout;
        this.element.onkeydown      = Slider.eventHandlers.onkeydown;
        this.element.onkeypress     = Slider.eventHandlers.onkeypress;
        this.element.onmousewheel   = Slider.eventHandlers.onmousewheel;
        this.handleLow.onselectstart    =
        this.element.onselectstart  = function () { return false; };
        this.handleHigh.onselectstart   =
        this.element.onselectstart  = function () { return false; };

        this._timer.ontimer = function () {
            oThis.ontimer();
        };

        // extra recalculate for ie
        window.setTimeout(function() {
            oThis.recalculate();
        }, 1);
    }
    else {
        this.inputLow.onchange = function (e) {
            oThis.setValueLow(oThis.inputLow.value);};
        this.inputHigh.onchange = function (e) {
            oThis.setValueHigh(oThis.inputHigh.value);};
    }
}

Slider.eventHandlers = {

    // helpers to make events a bit easier
    getEvent:   function (e, el) {
        if (!e) {
            if (el)
                e = el.document.parentWindow.event;
            else
                e = window.event;
        }
        if (!e.srcElement) {
            var el = e.target;
            while (el != null && el.nodeType != 1)
                el = el.parentNode;
            e.srcElement = el;
        }
        if (typeof e.offsetX == "undefined") {
            e.offsetX = e.layerX;
            e.offsetY = e.layerY;
        }

        return e;
    },

    getDocument:    function (e) {
        if (e.target)
            return e.target.ownerDocument;
        return e.srcElement.document;
    },

    getSlider:  function (e) {
        var el = e.target || e.srcElement;
        while (el != null && el.slider == null) {
            el = el.parentNode;
        }
        if (el)
            return el.slider;
        return null;
    },

    getLine:    function (e) {
        var el = e.target || e.srcElement;
        while (el != null && el.className != "line")    {
            el = el.parentNode;
        }
        return el;
    },

    getHandle:  function (e) {
        var el = e.target || e.srcElement;
        var re = /handle/;
        while (el != null && !re.test(el.className))    {
            el = el.parentNode;
        }
        return el;
    },
    // end helpers

    onfocus:    function (e) {
        var s = this.slider;
        s._focused = true;
        s.handleLow.className = "handle hover";
        s.handleHigh.className = "handle hover";
    },

    onblur: function (e) {
        var s = this.slider
        s._focused = false;
        s.handleLow.className = "handle";
        s.handleHigh.className = "handle";
    },

    onmouseover:    function (e) {
        e = Slider.eventHandlers.getEvent(e, this);
        var s = this.slider;
        if (e.srcElement == s.handleLow)
            s.handleLow.className = "handle hover";
        if (e.srcElement == s.handleHigh)
            s.handleHigh.className = "handle hover";
    },

    onmouseout: function (e) {
        e = Slider.eventHandlers.getEvent(e, this);
        var s = this.slider;
        if (e.srcElement == s.handleLow && !s._focused)
            s.handleLow.className = "handle";
        if (e.srcElement == s.handleHigh && !s._focused)
            s.handleHigh.className = "handle";
    },

    onmousedown:    function (e) {
        e = Slider.eventHandlers.getEvent(e, this);
        var s = this.slider;
        if (s.element.focus)
            s.element.focus();

        Slider._currentInstance = s;
        var doc = s.document;

        if (doc.addEventListener) {
            doc.addEventListener("mousemove", Slider.eventHandlers.onmousemove, true);
            doc.addEventListener("mouseup", Slider.eventHandlers.onmouseup, true);
        }
        else if (doc.attachEvent) {
            doc.attachEvent("onmousemove", Slider.eventHandlers.onmousemove);
            doc.attachEvent("onmouseup", Slider.eventHandlers.onmouseup);
            doc.attachEvent("onlosecapture", Slider.eventHandlers.onmouseup);
            s.element.setCapture();
        }

        var handle = Slider.eventHandlers.getHandle(e);
        if (handle) {   // start drag
            Slider._sliderDragData = {
                screenX:    e.screenX,
                screenY:    e.screenY,
                dx:         handle == s.handleLow ? e.screenX - handle.offsetLeft : e.screenX - handle.offsetLeft + handle.offsetWidth,
                dy:         handle == s.handleLow ? e.screenY - handle.offsetTop : e.screenY - handle.offsetTop - handle.offsetHeight,
                startValue: handle == s.handleLow ? s.getValueLow() : s.getValueHigh(),
                slider:     s,
                handle:     handle
            };
        }
        else {
            var lineEl = Slider.eventHandlers.getLine(e);
            s._mouseX = e.offsetX + (lineEl ? s.line.offsetLeft : 0);
            s._mouseY = e.offsetY + (lineEl ? s.line.offsetTop : 0);
            s._increasing = null;
            //s.ontimer();  //there is a strange bug with this where it sometimes jumps
        }
    },

    onmousemove:    function (e) {
        e = Slider.eventHandlers.getEvent(e, this);

        if (Slider._sliderDragData) {   // drag
            var s = Slider._sliderDragData.slider;

            var boundSize = s.getMaximum() - s.getMinimum();
            var size, pos, reset;

            if (s._orientation == "horizontal") {
                size = s.line.offsetWidth; //s.element.offsetWidth - Slider._sliderDragData.handle.offsetWidth;
                pos = e.screenX - Slider._sliderDragData.dx;
                reset = Math.abs(e.screenY - Slider._sliderDragData.screenY) > 100;
            }
            else {
                size = s.line.offsetHeight; //s.element.offsetHeight - Slider._sliderDragData.handle.offsetHeight;
                pos = s.element.offsetHeight - Slider._sliderDragData.handle.offsetHeight -
                    (e.screenY - Slider._sliderDragData.dy);
                reset = Math.abs(e.screenX - Slider._sliderDragData.screenX) > 100;
            }

            if(Slider._sliderDragData.handle == s.handleLow)
                s.setValueLow(reset ? Slider._sliderDragData.startValue :
                            s.getMinimum() + boundSize * pos / size);
            else
                s.setValueHigh(reset ? Slider._sliderDragData.startValue :
                            s.getMinimum() + boundSize * pos / size);

            return false;
        }
        else {
            var s = Slider._currentInstance;
            if (s != null) {
                var lineEl = Slider.eventHandlers.getLine(e);
                s._mouseX = e.offsetX + (lineEl ? s.line.offsetLeft : 0);
                s._mouseY = e.offsetY + (lineEl ? s.line.offsetTop : 0);
            }
        }

    },

    onmouseup:  function (e) {
        e = Slider.eventHandlers.getEvent(e, this);
        var s = Slider._currentInstance;
        var doc = s.document;
        if (doc.removeEventListener) {
            doc.removeEventListener("mousemove", Slider.eventHandlers.onmousemove, true);
            doc.removeEventListener("mouseup", Slider.eventHandlers.onmouseup, true);
        }
        else if (doc.detachEvent) {
            doc.detachEvent("onmousemove", Slider.eventHandlers.onmousemove);
            doc.detachEvent("onmouseup", Slider.eventHandlers.onmouseup);
            doc.detachEvent("onlosecapture", Slider.eventHandlers.onmouseup);
            s.element.releaseCapture();
        }

        if (Slider._sliderDragData) {   // end drag
            Slider._sliderDragData = null;
        }
        else {
            s._timer.stop();
            s._increasing = null;
        }
        Slider._currentInstance = null;
    },

    onkeydown:  function (e) {
        e = Slider.eventHandlers.getEvent(e, this);
        //var s = Slider.eventHandlers.getSlider(e);
        var s = this.slider;
        var kc = e.keyCode;
        switch (kc) { //TODO: need to re-think keyboard mappings for a range slider
            case 33:    // page up
                s.setValueHigh(s.getValueHigh() + s.getBlockIncrement());
                break;
            case 34:    // page down
                s.setValueHigh(s.getValueHigh() - s.getBlockIncrement());
                break;
/*
            case 35:    // end
                s.setValue(s.getOrientation() == "horizontal" ?
                    s.getMaximum() :
                    s.getMinimum());
                break;
            case 36:    // home
                s.setValue(s.getOrientation() == "horizontal" ?
                    s.getMinimum() :
                    s.getMaximum());
                break;
*/
            case 38:    // up
                s.setValueHigh(s.getValueHigh() + s.getUnitIncrement());
                break;

            case 39:    // right
                s.setValueLow(s.getValueLow() + s.getUnitIncrement());
                break;

            case 37:    // left
                s.setValueLow(s.getValueLow() - s.getUnitIncrement());
                break;

            case 40:    // down
                s.setValueHigh(s.getValueHigh() - s.getUnitIncrement());
                break;
        }

        if (kc >= 33 && kc <= 40) {
            return false;
        }
    },

    onkeypress: function (e) {
        e = Slider.eventHandlers.getEvent(e, this);
        var kc = e.keyCode;
        if (kc >= 33 && kc <= 40) {
            return false;
        }
    },

    onmousewheel:   function (e) {
        e = Slider.eventHandlers.getEvent(e, this);
        var s = this.slider;
        if (s._focused) {
            s.setValueLow(s.getValueLow() + e.wheelDelta / 120 * s.getUnitIncrement());
            // windows inverts this on horizontal sliders. That does not
            // make sense to me
            return false;
        }
    }
};



Slider.prototype.classNameTag = "dynamic-slider-control";

Slider.prototype.setValueLow = function (v) {
    this._range.setValueLow(v);
    this.inputLow.value = this.getValueLow().toFixed(this._precision);
};

Slider.prototype.setValueHigh = function (v) {
    this._range.setValueHigh(v);
    this.inputHigh.value = this.getValueHigh().toFixed(this._precision);
};

Slider.prototype.getValueLow = function () {
    return this._range.getValueLow();
};

Slider.prototype.getValueHigh = function () {
    return this._range.getValueHigh();
};

Slider.prototype.setMinimum = function (v) {
    this._range.setMinimum(v);
    this.inputLow.value = this.getValueLow().toFixed(this._precision);
    this.inputHigh.value = this.getValueHigh().toFixed(this._precision);
};

Slider.prototype.getMinimum = function () {
    return this._range.getMinimum();
};

Slider.prototype.setMaximum = function (v) {
    this._range.setMaximum(v);
    this.inputLow.value = this.getValueLow().toFixed(this._precision);
    this.inputHigh.value = this.getValueHigh().toFixed(this._precision);
};

Slider.prototype.getMaximum = function () {
    return this._range.getMaximum();
};

Slider.prototype.getPrecision = function() {
    return this._precision;
};

Slider.prototype.setPrecision = function(p) {
    this._precision = p;
    this.inputLow.value = this.getValueLow().toFixed(this._precision);
    this.inputHigh.value = this.getValueHigh().toFixed(this._precision);
};

Slider.prototype.setUnitIncrement = function (v) {
    this._unitIncrement = v;
};

Slider.prototype.getUnitIncrement = function () {
    return this._unitIncrement;
};

Slider.prototype.setBlockIncrement = function (v) {
    this._blockIncrement = v;
};

Slider.prototype.getBlockIncrement = function () {
    return this._blockIncrement;
};

Slider.prototype.getOrientation = function () {
    return this._orientation;
};

Slider.prototype.setOrientation = function (sOrientation) {
    if (sOrientation != this._orientation) {
        if (Slider.isSupported && this.element) {
            // add class name tag to class name
            this.element.className = this.element.className.replace(this._orientation,
                                    sOrientation);
        }
        this._orientation = sOrientation;
        this.recalculate();

    }
};

Slider.prototype.recalculate = function() {
    if (!Slider.isSupported || !this.element) return;

    var w = this.element.offsetWidth;
    var h = this.element.offsetHeight;
    var hw = this.handleLow.offsetWidth;
    var hh = this.handleLow.offsetHeight;
    var lw = this.line.offsetWidth;
    var lh = this.line.offsetHeight;

    // this assumes a border-box layout

    if (this._orientation == "horizontal") {
        this.line.style.top = (h - lh) / 2 + "px";
        this.line.style.left = hw + "px";
        this.line.style.width = Math.max(0, w - (hw * 2) - 2)+ "px";
        this.line.firstChild.style.width = Math.max(0, w - (hw * 2) - 4)+ "px";

        lw = this.line.offsetWidth;

        var hlowl = lw * (this.getValueLow() - this.getMinimum()) /
            (this.getMaximum() - this.getMinimum());
        this.handleLow.style.left = hlowl + "px";
        this.handleLow.style.top = (h - hh) / 2 + "px";

        var hhighl = (lw * (this.getValueHigh() - this.getMinimum()) /
            (this.getMaximum() - this.getMinimum())) + hw;
        this.handleHigh.style.left = hhighl + "px";
        this.handleHigh.style.top = (h - hh) / 2 + "px";

        this.rangeline.style.left = hlowl + hw + "px";
        this.rangeline.style.width = Math.max(0, hhighl - hlowl - hw) + "px";
        this.rangeline.style.top = ((h- lh) / 2) + 1 + "px";
    }
    else {
        this.line.style.left = (w - lw) / 2 + "px";
        this.line.style.top = hh + "px";
        this.line.style.height = Math.max(0, h - (hh * 2) - 2) + "px";    //hard coded border width
        //this.line.style.bottom = hh / 2 + "px";
        this.line.firstChild.style.height = Math.max(0, h - (hh * 2) - 4) + "px"; //hard coded border width

        lh = this.line.offsetHeight;

        var hlowt = h - hh - lh * (this.getValueLow() - this.getMinimum()) /
            (this.getMaximum() - this.getMinimum());
        this.handleLow.style.left = (w - hw) / 2 + "px";
        this.handleLow.style.top = hlowt + "px";

        var hhight = (h - hh - lh * (this.getValueHigh() - this.getMinimum()) /
            (this.getMaximum() - this.getMinimum())) - hh;
        this.handleHigh.style.left = (w - hw) / 2 + "px";
        this.handleHigh.style.top = hhight + "px";

        this.rangeline.style.left = ((w - lw) / 2) + 1 + "px";
        this.rangeline.style.top = hhight + hh + "px";
        this.rangeline.style.height = Math.max(0, hlowt - hhight - hh) + "px";
    }
};

Slider.prototype.ontimer = function () {
    var hlw = this.handleLow.offsetWidth;
    var hlh = this.handleLow.offsetHeight;
    var hll = this.handleLow.offsetLeft;
    var hlt = this.handleLow.offsetTop;

    var hhw = this.handleHigh.offsetWidth;
    var hhh = this.handleHigh.offsetHeight;
    var hhl = this.handleHigh.offsetLeft;
    var hht = this.handleHigh.offsetTop;

    if (this._orientation == "horizontal") {
        if (this._mouseX > hhl + hhw &&
            (this._increasing == null || this._increasing)) {
            this.setValueHigh(this.getValueHigh() + this.getBlockIncrement());
            this._increasing = true;
        }
        else if (this._mouseX < hll &&
            (this._increasing == null || !this._increasing)) {
            this.setValueLow(this.getValueLow() - this.getBlockIncrement());
            this._increasing = false;
        }
    }
    else {
        if (this._mouseY > hht + hhh &&
            (this._increasing == null || this._increasing)) {
            this.setValueHigh(this.getValueHigh() - this.getBlockIncrement());
            this._increasing = true;
        }
        else if (this._mouseY < hlt &&
            (this._increasing == null || !this._increasing)) {
            this.setValueLow(this.getValueLow() + this.getBlockIncrement());
            this._increasing = false;
        }
    }

    this._timer.start();
};