/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.internal)
    LABKEY.internal = {};

LABKEY.internal.FileDrop = new function () {

    var registered = [];
    var registeredEvents = false;
    var shouldShowDropzones = false;
    var showingDropzones = false;
    var timeout = -1;

    function showDropzones()
    {
        if (showingDropzones)
            return;

        showingDropzones = true;

        // show all dropzones
        // CONSIDER: iterate Dropzone.instances instead of keeping our own list of registered Dropzone
        for (var i = 0, len = registered.length; i < len; i++)
        {
            var dropzone = registered[i];

            var peer = dropzone.peer;
            if (peer instanceof Function)
                peer = peer();

            // get location of peer
            var peerEl = Ext4.Element.get(peer);
            var r = peerEl.getViewRegion();

            dropzone.element.style.top = r.top + "px";
            dropzone.element.style.left = r.left + "px";
            dropzone.element.style.height = (r.bottom - r.top) + "px";
            dropzone.element.style.width = (r.right - r.left) + "px";

            // Use table-cell to vertically align the inner content
            dropzone.element.style.display = 'table';
        }
    }

    function hideDropzones()
    {
        if (!showingDropzones)
            return;

        showingDropzones = false;

        // hide all dropzones
        for (var i = 0, len = registered.length; i < len; i++)
        {
            var dropzone = registered[i];

            dropzone.element.style.display = 'none';
        }
    }

    function docDragStart(e)
    {
        //console.log("document.dragstart:", e);
    }

    function docDragEnter(e)
    {
        //console.log("document.dragenter:", e);
        e.stopEvent();

        shouldShowDropzones = true;
        showDropzones();
    }

    function docDragOver(e)
    {
        //console.log("document.dragover:", e);
        // NOTE: important to cancel this event -- stops the browser from navigating on file drop
        e.stopEvent();
        shouldShowDropzones = true;
    }

    function docDragLeave(e)
    {
        //console.log("document.dragleave: ", e);
        e.stopEvent();

        shouldShowDropzones = false;
        clearTimeout(timeout);
        timeout = setTimeout(function () {
            if (!shouldShowDropzones) {
                hideDropzones();
            }
        }, 200);
    }

    function docDragEnd(e)
    {
        //console.log("document.dragend:", e);
    }

    function docDrop(e)
    {
        //console.log("document.drop:", e);
        e.stopEvent();

        shouldShowDropzones = false;
        hideDropzones();
    }

    function zoneDragEnter()
    {
        //console.log("zone.dragenter");
        shouldShowDropzones = true;
    }

    function zoneDragOver()
    {
        //console.log("zone.dragover");
        shouldShowDropzones = true;
    }

    function zoneDrop()
    {
        //console.log("zone.drop");
        shouldShowDropzones = false;
        hideDropzones();
    }

    function registerEvents()
    {
        if (registeredEvents)
            return;

        registeredEvents = true;
        Ext4.EventManager.addListener(document, "dragenter", docDragEnter, this);
        Ext4.EventManager.addListener(document, "dragover", docDragOver, this);
        Ext4.EventManager.addListener(document, "dragleave", docDragLeave, this);
        Ext4.EventManager.addListener(document, "dragend", docDragEnd, this);
        Ext4.EventManager.addListener(document, "drop", docDrop, this);
    }

    function createDefaultZone()
    {
        var zone = document.createElement("div");
        zone.style.display = "none";
        zone.style.verticalAlign = "middle";
        zone.style.position = "absolute";
        zone.style.zIndex = "1000";

        // TODO: move the presentation styles into the dropzone/css/basic.css
        zone.style.borderSpacing = "6px";
        zone.style.background = "white";
        zone.style.opacity = "0.8";

        zone.classList.add("dropzone");
        zone.classList.add("dz-clickable");
        zone.innerHTML =
                "<div class='dz-message' style='display:table-cell;vertical-align:middle;" +
                    "background: rgba(10, 10, 10, 0.1);" +
                    "opacity: 0.8;" +
                    "border: 6px dashed rgba(10, 10, 10, 0.4);" +
                    "color: rgba(10, 10, 10, 0.4);" +
                    "font-size: 40px;" +
                    "text-align:center;'>" +
                "<span style='font-weight: bold'>" +
                (Ext4.isChrome ? "Drop files or folders here" : "Drop files here</br>Folder upload requires Google Chrome") +
                "</span>" +
                "</div>";

        return zone;
    }

    function _registerDropzone(config)
    {
        Dropzone.autoDiscover = false;

        var peer = config.peer;
        if (!peer)
            throw new Error("peer required");

        var el = config.el || createDefaultZone();
        // TODO: listen for el removed event to cleanup the Dropzone

        // this (passing the dropzone) is silly but it works.
        Dropzone.prototype.setEnabled = function(bool) {
            if (bool)
            {
                this.enable();
                document.body.appendChild(this.element);
            }
            else
            {
                this.disable();
                try {
                    document.body.removeChild(this.element);
                }
                catch (e) {
                    if (e.name != 'NotFoundError') throw e;
                }
            }
        };

        var dropzone = new Dropzone(el, config);
        dropzone.peer = peer;
        dropzone.on("dragenter", zoneDragEnter);
        dropzone.on("dragover", zoneDragOver);
        dropzone.on("drop", zoneDrop);

        if (config.disabled)
            dropzone.disable();
        else
            document.body.appendChild(dropzone.element);

        registered.push(dropzone);

        registerEvents();

        //Show dropzone after registration
        if (config.show)
            showDropzones();

        return dropzone;
    }


    /** @scope LABKEY.internal.FileDrop */
    return {
        isSupported : function () {
            return window.Dropzone && window.Dropzone.isBrowserSupported();
        },

        registerDropzone : function (config) {
            if (this.isSupported()) {
                return _registerDropzone(config);
            }
        },

        showDropzones: function(){
            shouldShowDropzones = true;
            showDropzones();
        },

        hideDropzones: function() {
            shouldShowDropzones = false;
            hideDropzones();
        }
    }
};
