/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.vis = LABKEY.vis || {};

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

        var action;
        var convertTo = format ? format.toLowerCase() : this.FORMAT_PNG;
        if (this.FORMAT_PDF == convertTo)
            action = "exportPDF";
        else if (this.FORMAT_PNG == convertTo)
            action = "exportImage";
        else
            throw "Unknown format: " + format;

        // Insert a <form> into to page, with svg and title as hidden inputs, and submit it
        var newForm = document.createElement('form');
        newForm.method = 'POST';
        newForm.enctype = 'multipart/form-data'; // use multipat post in case the SVG source is large (i.e. > 2 MB)
        newForm.action = LABKEY.ActionURL.buildURL('visualization', action);
        newForm.target = '_blank';
        document.body.appendChild(newForm);

        var csrfInput = document.createElement('input');
        csrfInput.setAttribute('type', 'hidden');
        csrfInput.setAttribute('name', 'X-LABKEY-CSRF');
        csrfInput.setAttribute('value', LABKEY.CSRF);
        newForm.appendChild(csrfInput);

        var svgInput = document.createElement('input');
        svgInput.setAttribute('type', 'hidden');
        svgInput.setAttribute('name', 'svg');
        svgInput.setAttribute('value', svg);
        newForm.appendChild(svgInput);

        if (title)
        {
            var titleInput = document.createElement('input');
            titleInput.setAttribute('type', 'hidden');
            titleInput.setAttribute('name', 'title');
            titleInput.setAttribute('value', title.trim());
            newForm.appendChild(titleInput);
        }

        newForm.submit();
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
