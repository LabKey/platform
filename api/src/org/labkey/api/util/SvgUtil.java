/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SvgUtil
{
    public record Size(Float value, @Nullable String units)
    {
        @Override
        public @NotNull String toString()
        {
            return value + (null != units ? units : "");
        }
    }

    public static @Nullable String readAttribute(String svg, String attribute)
    {
        String ret = null;

        int idx = svg.indexOf(attribute + "=");
        if (idx != -1)
        {
            int start = idx + attribute.length() + 2;
            int end = svg.indexOf("\"", start + 1);
            ret = svg.substring(start, end);
        }

        return ret;
    }

    private static final Pattern floatingPointPattern = Pattern.compile("(?<value>(\\d*\\.)?\\d+)(?<units>[^\\d.]*)");

    public static @Nullable Size readSizeAttribute(String svg, String attribute)
    {
        Size ret = null;
        String value = readAttribute(svg, attribute);
        if (value != null && value.length() < 100) // Prevent DOS on regex
        {
            Matcher fbMatcher = floatingPointPattern.matcher(value);
            if (fbMatcher.find())
            {
                ret = new Size(Float.parseFloat(fbMatcher.group("value")), StringUtils.trimToNull(fbMatcher.group("units")));
            }
            else
            {
                throw new IllegalStateException("Couldn't match value " + value + " into a valid size pattern");
            }
        }

        return ret;
    }

    public static @Nullable Size readHeight(String svg)
    {
        return readSizeAttribute(svg, "height");
    }

    public static String setHeight(String svg, @Nullable Size height)
    {
        return setSizeAttribute(svg, "height", height);
    }

    public static @Nullable Size readWidth(String svg)
    {
        return readSizeAttribute(svg, "width");
    }

    public static String setWidth(String svg, @Nullable Size width)
    {
        return setSizeAttribute(svg, "width", width);
    }

    public static String scaleSize(String svg, float scale)
    {
        Size height = SvgUtil.readHeight(svg);
        if (height == null)
            throw new IllegalStateException("height is null");
        Size newHeight = new Size(height.value() * scale, height.units());
        String updated = setHeight(svg, newHeight);

        Size width = SvgUtil.readWidth(updated);
        if (width == null)
            throw new IllegalStateException("width is null");
        Size newWidth = new Size(width.value() * scale, width.units());
        return setWidth(updated, newWidth);
    }

    // Replaces the existing attribute value with size parameter. If "size" is null, removes the attribute completely.
    private static String setSizeAttribute(String svg, String attribute, @Nullable Size size)
    {
        String ret = svg;
        int idx = svg.indexOf(attribute + "=");

        if (idx == -1)
        {
            if (size != null)
            {
                int svgElement = svg.indexOf("<svg");
                if (svgElement == -1)
                {
                    throw new IllegalStateException("This is not an SVG!");
                }
                ret = svg.replace("<svg", "<svg " + attribute + "=\"" + size + "\"");
            }
        }
        else
        {
            int start = idx + attribute.length() + 1;
            int end = svg.indexOf("\"", start + 1);

            if (size != null)
            {
                ret = svg.substring(0, start + 1) + size + svg.substring(end);
            }
            else
            {
                ret = svg.substring(0, idx - 1) + svg.substring(end + 1);
            }
        }

        return ret;
    }

    private static final String svg = """
        <?xml version="1.0" encoding="UTF-8"?><!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
        <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" height="785.3512079386096pt" width="1776.9770105296589pt" viewBox="0.00 0.00 1776.9770105296589 785.3512079386096"><g id="graph_root" class="graph" transform="scale(1.0 1.0) rotate(0)"><g id="graph_0" class="graph"><polygon id="graph_0_polygon" fill="#ffffff" points="0.0,0.0 1776.9770105296589,0.0 1776.9770105296589,785.3512079386096 0.0,785.3512079386096 0.0,0.0"></polygon></g><g id="A12042" class="node"><a id="A12042_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-showRunGraphDetail.view?rowId=12042" xlink:title="drt/CAexample_mini (DRT2)"><polygon id="A12042_polygon" points="885.5468397176991,309.1896331314351 820.4980000700886,421.8575283744972 885.546839717699,534.5254236175592 1015.6445190129199,534.5254236175593 1080.6933586605305,421.85752837449735 1015.6445190129201,309.18963313143513 885.5468397176991,309.1896331314351 " fill="#FF7F50" stroke="#000000"></polygon><text id="A12042_text_0" x="950.5956793653095" y="417.8575283744972" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">drt/CAexample_mini</text><text id="A12042_text_1" x="950.5956793653095" y="441.8575283744972" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">(DRT2)</text></a></g><g id="D26458" class="node"><a id="D26458_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-resolveLSID.view?type=data&amp;lsid=urn%3Alsid%3Alabkey.com%3AData.Folder-4826-Xar-66c19a7d-0245-103b-9601-f6b68b19fc80%3A..%252F..%252FCAexample_mini.mzXML" xlink:title="Data: CAexample_mini.mzXML"><ellipse id="D26458_ellipse" cx="235.59567936530948" cy="204.70703125" rx="195.59567936530948" ry="24.605658703773443" fill="#BBE3E3" stroke="#000000"></ellipse><text id="D26458_text_0" x="235.59567936530948" y="212.70703125" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">CAexample_mini.mzXML</text></a></g><g id="D26460" class="node"><a id="D26460_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-resolveLSID.view?type=data&amp;lsid=urn%3Alsid%3Alabkey.com%3AData.Folder-4826-Xar-66c19a7d-0245-103b-9601-f6b68b19fc80%3A..%252F..%252F..%252Fdatabases%252FBovine_mini1.fasta" xlink:title="Data: Bovine_mini1.fasta"><ellipse id="D26460_ellipse" cx="616.5956793653095" cy="204.70703125" rx="148.4791657970277" ry="24.605658703773443" fill="#BBE3E3" stroke="#000000"></ellipse><text id="D26460_text_0" x="616.5956793653095" y="212.70703125" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">Bovine_mini1.fasta</text></a></g><g id="D26461" class="node"><a id="D26461_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-resolveLSID.view?type=data&amp;lsid=urn%3Alsid%3Alabkey.com%3AData.Folder-4826-Xar-66c19a7d-0245-103b-9601-f6b68b19fc80%3A..%252F..%252F..%252Fdatabases%252FBovine_mini2.fasta" xlink:title="Data: Bovine_mini2.fasta"><ellipse id="D26461_ellipse" cx="950.5956793653095" cy="204.70703125" rx="148.4791657970277" ry="24.605658703773443" fill="#BBE3E3" stroke="#000000"></ellipse><text id="D26461_text_0" x="950.5956793653095" y="212.70703125" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">Bovine_mini2.fasta</text></a></g><g id="D26462" class="node"><a id="D26462_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-resolveLSID.view?type=data&amp;lsid=urn%3Alsid%3Alabkey.com%3AData.Folder-4826-Xar-66c19a7d-0245-103b-9601-f6b68b19fc80%3A..%252F..%252F..%252Fdatabases%252FBovine_mini3.fasta" xlink:title="Data: Bovine_mini3.fasta"><ellipse id="D26462_ellipse" cx="1284.5956793653095" cy="204.70703125" rx="148.4791657970277" ry="24.605658703773443" fill="#BBE3E3" stroke="#000000"></ellipse><text id="D26462_text_0" x="1284.5956793653095" y="212.70703125" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">Bovine_mini3.fasta</text></a></g><g id="D26467" class="node"><a id="D26467_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-resolveLSID.view?type=data&amp;lsid=urn%3Alsid%3Alabkey.com%3AData.Folder-4826-Xar-66c19d4d-0245-103b-9601-f6b68b19fc80%3Atandem.xml" xlink:title="Data: Tandem Settings"><ellipse id="D26467_ellipse" cx="1603.5956793653095" cy="204.70703125" rx="133.38133116434938" ry="24.605658703773443" fill="#BBE3E3" stroke="#000000"></ellipse><text id="D26467_text_0" x="1603.5956793653095" y="212.70703125" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">Tandem&#160;Settings</text></a></g><g id="D26470" class="node"><a id="D26470_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-resolveLSID.view?type=data&amp;lsid=urn%3Alsid%3Alabkey.com%3AData.Run-12042%3AScoredPepXmlFile" xlink:title="Data: Scored Search Results (Run Output)"><ellipse id="D26470_ellipse" cx="811.5956793653095" cy="631.9664423136096" rx="117.34713247377158" ry="44.120148583003754" fill="#BBE3E3" stroke="#000000"></ellipse><text id="D26470_text_0" x="811.5956793653095" y="627.9664423136096" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">Scored&#160;Search</text><text id="D26470_text_1" x="811.5956793653095" y="651.9664423136096" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">Results</text></a></g><g id="D26471" class="node"><a id="D26471_a_" xlink:href="/MS2VerifyProject/ms2folder/experiment-resolveLSID.view?type=data&amp;lsid=urn%3Alsid%3Alabkey.com%3AData.Run-12042%3AProteinScoresFile" xlink:title="Data: Protein Prophet scores (Run Output)"><ellipse id="D26471_ellipse" cx="1089.5956793653095" cy="631.9664423136096" rx="123.01503526672008" ry="44.120148583003754" fill="#BBE3E3" stroke="#000000"></ellipse><text id="D26471_text_0" x="1089.5956793653095" y="627.9664423136096" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">Protein&#160;Prophet</text><text id="D26471_text_1" x="1089.5956793653095" y="651.9664423136096" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">scores</text></a></g><g id="line_0"><path id="line_0_path" d="M881.1296149121845,526.8606892224863 847.8687749836608,577.136940690722 " fill="none" stroke="#000000"></path><title id="line_0_title">drt/CAexample_mini
        (DRT2)-&gt;Scored&#160;Search
        Results</title><polygon id="line_0_polygonhead" fill="#000000" stroke="#000000" points="839.9456970321262,589.1132666148362 843.8876357416028,574.4551248768556 851.8941330424958,579.7519164501376 839.9456970321262,589.1132666148362"></polygon></g><g id="line_1"><path id="line_1_path" d="M1020.0617438184345,526.8606892224863 1053.2677431205302,577.0540449751932 " fill="none" stroke="#000000"></path><title id="line_1_title">drt/CAexample_mini
        (DRT2)-&gt;Protein&#160;Prophet
        scores</title><polygon id="line_1_polygonhead" fill="#000000" stroke="#000000" points="1061.1777574645603,589.0106242778331 1049.2293214541908,579.6492741131345 1057.2358187550838,574.3524825398525 1061.1777574645603,589.0106242778331"></polygon></g><g id="line_2"><path id="line_2_path" d="M294.82305188648627,228.15009587719908C330.5433339035102,242.93290990131763 380.3281239323632,265.7757675121335 433.5956793653095,275.80868872688677 433.5956793653095,275.80868872688677 488.2711413852426,286.1067885433266 488.2711413852426,286.1067885433266 498.0983476900751,287.9577386351067 517.5082868282666,292.66722531830294 527.0910196616255,295.5257619097192 527.0910196616255,295.5257619097192 621.8455641806761,323.7911155850072 825.8284482986727,384.6393671656083 " fill="none" stroke="#000000"></path><title id="line_2_title">CAexample_mini.mzXML-&gt;drt/CAexample_mini
        (DRT2)</title><polygon id="line_2_polygonhead" fill="#000000" stroke="#000000" points="839.6201931661012,388.7534552914693 824.4489603221847,389.2368743598423 827.1931554499441,380.0374508398178 839.6201931661012,388.7534552914693"></polygon><text id="line_2_text_0" x="462.2656012403095" y="268.40927466438677" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">mzXML</text></g><g id="line_3"><path id="line_3_path" d="M658.6138596405625,228.29619668521588C678.8790161706557,239.4357084423658 709.7896504979292,255.96393829794116 738.5956793653095,275.80868872688677 738.5956793653095,275.80868872688677 764.4731018859532,293.63589406750447 845.71846400296,349.6066056898571 " fill="none" stroke="#000000"></path><title id="line_3_title">Bovine_mini1.fasta-&gt;drt/CAexample_mini
        (DRT2)</title><polygon id="line_3_polygonhead" fill="#000000" stroke="#000000" points="857.5240510541673,357.73958819074244 842.9425357475303,353.52302426233933 848.3887771658996,345.6174278640378 857.5240510541673,357.73958819074244"></polygon><text id="line_3_text_0" x="765.2675543653095" y="268.40927466438677" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">FASTA</text></g><g id="line_4"><path id="line_4_path" d="M950.5956793653095,229.2937145822147C950.5956793653095,237.2877284002102 950.5956793653095,250.92899946407945 950.5956793653095,294.83126352545935 " fill="none" stroke="#000000"></path><title id="line_4_title">Bovine_mini2.fasta-&gt;drt/CAexample_mini
        (DRT2)</title><polygon id="line_4_polygonhead" fill="#000000" stroke="#000000" points="950.5956793653095,309.1870288354608 945.7956793653095,294.78702883546083 955.3956793653094,294.78702883546083 950.5956793653095,309.1870288354608"></polygon><text id="line_4_text_0" x="989.2675543653095" y="268.40927466438677" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">FASTA</text></g><g id="line_5"><path id="line_5_path" d="M1232.904932718206,227.76115789046526C1219.7176063787297,233.07802935158293 1194.01446764922,244.03156326740012 1171.5956793653095,260.40927466438677 1171.5956793653095,260.40927466438677 1142.0964990001185,281.95946088990564 1053.715124450905,346.52515582868244 " fill="none" stroke="#000000"></path><title id="line_5_title">Bovine_mini3.fasta-&gt;drt/CAexample_mini
        (DRT2)</title><polygon id="line_5_polygonhead" fill="#000000" stroke="#000000" points="1042.092082045779,355.0161960961312 1050.8883230457982,342.6458224449601 1056.5512990364969,350.3976417752058 1042.092082045779,355.0161960961312"></polygon><text id="line_5_text_0" x="1210.2675543653095" y="268.40927466438677" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">FASTA</text></g><g id="line_6"><path id="line_6_path" d="M1563.2746626868402,228.16106777741211C1548.8141644539207,237.1438185281768 1524.6810612848428,252.41496458764075 1497.5956793653095,260.40927466438677 1497.5956793653095,260.40927466438677 1348.5029081448713,304.4143341787209 1075.5025537949289,384.9909895206579 " fill="none" stroke="#000000"></path><title id="line_6_title">Tandem&#160;Settings-&gt;drt/CAexample_mini
        (DRT2)</title><polygon id="line_6_polygonhead" fill="#000000" stroke="#000000" points="1061.748812220436,389.05043661239836 1074.2010200785408,380.3704297311479 1076.9185826356702,389.5777558222608 1061.748812220436,389.05043661239836"></polygon><text id="line_6_text_0" x="1570.3046637403095" y="268.40927466438677" text-anchor="middle" font-size="24.0" fill="#000000" font-family="Arial">SearchConfig</text></g></g>
        </svg>""";

    public static class TestCase extends Assert
    {
        @Test
        public void testAttributes()
        {
            Size height = readHeight(svg);
            Assert.assertNotNull(height);
            Assert.assertEquals(785.3512f, height.value(), 0.0001f);
            Assert.assertEquals("pt", height.units());

            Size newHeight = new Size(123.4567f, "px");
            Assert.assertEquals(readHeight(setHeight(svg, newHeight)), newHeight);

            Size width = readWidth(svg);
            Assert.assertNotNull(width);
            Assert.assertEquals(1776.9770f, width.value(), 0.0001f);
            Assert.assertEquals("pt", width.units());

            Size newWidth = new Size(456.7890f, "px");
            Assert.assertEquals(readWidth(setWidth(svg, newWidth)), newWidth);

            // Now clear height and width, then test read and set scenarios when these attributes are missing

            String svgNoSize = setWidth(setHeight(svg, null), null);
            Assert.assertNull(readHeight(svgNoSize));
            Assert.assertNull(readWidth(svgNoSize));

            String changedSvg = setHeight(svgNoSize, new Size(375.1234f, "px"));
            height = readHeight(changedSvg);
            Assert.assertNotNull(height);
            Assert.assertEquals(375.1234f, height.value(), 0.0001f);
            Assert.assertEquals("px", height.units());

            changedSvg = setWidth(svgNoSize, new Size(1023.9876f, "px"));
            width = readWidth(changedSvg);
            Assert.assertNotNull(width);
            Assert.assertEquals(1023.9876f, width.value(), 0.0001f);
            Assert.assertEquals("px", width.units());
        }

        @Test
        public void testCornerCases()
        {
            String svg = "<svg width=\"50%\" height=\"75%\">";
            assertEquals(new Size(50f, "%"), readWidth(svg));
            assertEquals(new Size(75f, "%"), readHeight(svg));
            assertEquals("<svg width=\"83.0%\" height=\"75%\">", setWidth(svg, new Size(83f, "%")));
            assertEquals("<svg width=\"50%\" height=\"47.0%\">", setHeight(svg, new Size(47f, "%")));

            svg = "<svg height=\"100\">";
            assertNull(readWidth(svg));
            Size size = readHeight(svg);
            assertNotNull(size);
            assertEquals(new Size(100f, null), size);
            assertEquals("<svg height=\"200.0px\">", setHeight(svg, new Size(200f, "px")));
            assertEquals("<svg>", setHeight(svg, null));

            svg = "<svg width=\"200\">";
            assertNull(readHeight(svg));
            size = readWidth(svg);
            assertNotNull(size);
            assertEquals(new Size(200f, null), size);
            assertEquals("<svg width=\"300.0px\">", setWidth(svg, new Size(300f, "px")));
            assertEquals("<svg>", setWidth(svg, null));

            assertEquals("<svg/>", setWidth("<svg/>", null));
            assertEquals("<svg/>", setHeight("<svg/>", null));
        }
    }
}
