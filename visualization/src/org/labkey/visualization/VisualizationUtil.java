package org.labkey.visualization;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.StringReader;

/**
 * User: adam
 * Date: 10/14/11
 * Time: 3:10 PM
 */
public class VisualizationUtil
{
    // Factor this out of controller so thumbnail generation can use it too.  Could move this (and batik) to DocumentConversionService
    public static void svgToPng(String svg, OutputStream os) throws TranscoderException
    {
        svgToPng(svg, os, null);
    }

    // If height is provided, we'll auto-size keeping the aspect ratio; if null we'll use the dimensions in the SVG
    public static void svgToPng(String svg, OutputStream os, @Nullable Float height) throws TranscoderException
    {
        TranscoderInput xIn = new TranscoderInput(new StringReader(svg));
        TranscoderOutput xOut = new TranscoderOutput(os);

        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, java.awt.Color.WHITE);

        if (null != height)
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height);

        transcoder.transcode(xIn, xOut);
    }
}
