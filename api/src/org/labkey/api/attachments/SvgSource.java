package org.labkey.api.attachments;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NotFoundException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Wraps SVG source String or Reader and applies all our standard filtering. All SVG conversion methods should require
 * SvgSource parameters to ensure filtering is applied consistently. See Issue 46634.
 */
public class SvgSource
{
    private final String _filteredSvg;

    public SvgSource(String svg)
    {
        if (StringUtils.isBlank(svg))
            throw new NotFoundException("SVG source was empty");

        //svg MUST have right namespace for Batik, but svg generated by browser doesn't necessarily declare it
        //Since changing namespace recursively for all nodes not supported by DOM impl, just poke it in here.
        if (!svg.contains("xmlns=\"" + SVGDOMImplementation.SVG_NAMESPACE_URI + "\"") && !svg.contains("xmlns='" + SVGDOMImplementation.SVG_NAMESPACE_URI + "'"))
            svg = svg.replace("<svg", "<svg xmlns='" + SVGDOMImplementation.SVG_NAMESPACE_URI + "'");

        // remove xlink:title to prevent org.apache.batik.transcoder.TranscoderException (issue #16173)
        svg = svg.replaceAll("xlink:title", "title");

        // Reject hrefs. See #45819.
        if (StringUtils.containsIgnoreCase(svg, "xlink:href"))
            throw new RuntimeException(new TranscoderException("The security settings do not allow any external resources to be referenced from the document"));

        _filteredSvg = svg;
    }

    public static SvgSource of(String svg)
    {
        return new SvgSource(svg);
    }

    public static SvgSource of(Reader reader) throws IOException
    {
        try (BufferedReader bufferedReader = new BufferedReader(reader))
        {
            return of(PageFlowUtil.getReaderContentsAsString(bufferedReader));
        }
    }

    public Reader getReader()
    {
        return new StringReader(_filteredSvg);
    }
}