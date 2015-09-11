/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.namespace("LABKEY", "LABKEY.vis");

/**
 * A singleton class to convert SVG text to downloadable images
 * Note that this cannot be used to generate an image to be loaded into an image tag as a POST is required
 */
LABKEY.vis.SVGConverter = {
    FORMAT_PDF: "pdf",
    FORMAT_PNG: "png",

    /**
     * Converts the passed in svg string to a static image that the user will be prompted to save
     * @param svg SVG as String or Dom Node.
     * @param format Either LABKEY.vis.SVGConverter.FORMAT_PDF ("pdf") or LABKEY.vis.SVGConverter.FORMAT_PNG ("png")
     */
    convert: function(svg, format, title) {
        if (null != svg && typeof svg != "string")
            svg = this.svgToStr(svg);

        if (null == svg)
        {
            alert("Export not supported on this browser");
            return;
        }

        // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
        // will make the browser pop up a dialog
        var action;
        var convertTo = format ? format.toLowerCase() : this.FORMAT_PNG;
        if (this.FORMAT_PDF == convertTo)
            action = "exportPDF";
        else if (this.FORMAT_PNG == convertTo)
            action = "exportImage";
        else
            throw "Unknown format: " + format;

        // use multipart post (i.e fileuploadfield) in case the SVG source is large (i.e. > 2 MB)
        var exportForm = Ext4.create('Ext.form.Panel', {
            hidden: true,
            items: [
                {xtype : 'hidden', name : 'svg', value: svg},
                {xtype : 'hidden', name : 'title', value: Ext4.util.Format.trim(title)},
                {xtype : 'fileuploadfield', name : 'file'}
            ]
        });
        exportForm.submit({url: LABKEY.ActionURL.buildURL('visualization', action), target: '_blank', scope: this});
    },

    /** Transforms the given svg root node and all of its children into an XML string,
     *
     * @param node DOM node to turn into a string
     * @returns legal SVG string.
     */
    svgToStr: function(node)
    {

        var xml;
        var svgns = 'http://www.w3.org/2000/svg';
        var svgnsRegEx = new RegExp("xmlns=[\"']" + svgns + "[\"']");
        var svgnsRegExG = new RegExp("xmlns=[\"']" + svgns + "[\"']", 'g');

        if (typeof XMLSerializer != 'undefined')
        { // non-IE browsers
            xml = (new XMLSerializer().serializeToString(node));
        }
        else
        {
            xml = node.xml;
        }
        // add our namespace declarations
        var nsString = '';
        if (xml.indexOf('xmlns=') == -1)
        {
            nsString = 'xmlns="' + svgns + '" ';
        }
        xml = xml.replace(/<([^ ]+)/, '<$1 ' + nsString + ' ');
        
        // raphael may put the xmlns attr in the svg tag more than once (for IE)
        var nsMatches = xml.match(svgnsRegExG);
        if (nsMatches.length > 1)
        {
            for (var i = 1; i < nsMatches.length; i++)
                xml = xml.replace(svgnsRegEx, "");
        }

        return xml;
    }

};
