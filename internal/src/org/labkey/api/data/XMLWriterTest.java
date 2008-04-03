package org.labkey.api.data;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * User: adam
 * Date: Mar 14, 2008
 * Time: 9:02:48 PM
 */
public class XMLWriterTest
{
    private XMLWriter _writer;

    private enum Tag implements XMLWriter.Tag
    {
        Workspace(3), Symbols(0), Foo(2, 100), Bar(0, 2), Floom, Spices(4);

        private int _minAttributes;
        private int _maxAttributes;

        private Tag(int minAttributes, int maxAttributes)
        {
            _minAttributes = minAttributes;
            _maxAttributes = maxAttributes;
        }

        private Tag()
        {
            this(0, Integer.MAX_VALUE);
        }

        private Tag(int attributeCount)
        {
            this(attributeCount, attributeCount);
        }

        public int getMinimumAttributeCount()
        {
            return _minAttributes;
        }

        public int getMaximumAttributeCount()
        {
            return _maxAttributes;
        }
    }

    public void write(PrintWriter out)
    {
        _writer = new XMLWriter(out);
        writeDocument();
        _writer.close();
    }

    private void writeDocument()
    {
        _writer.startTag(Tag.Workspace, "this", "1", "that", "2", "theother", "3");
        _writer.startTag(Tag.Symbols);
        _writer.value("Weird symbols < & > | { } make me happy");
        _writer.endTag();
        _writer.startTag(Tag.Foo, "one", "1", "two", "2");
        _writer.attributes("this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3", "this", "1", "that", "2", "theother", "3");
        _writer.startTag(Tag.Bar, "fum", "flow");
        _writer.startTag(Tag.Floom, "this", "1", "that", "2", "theother", "3");
        _writer.value("Some value that is really long in order to test the word wrapping stuff we're doing");
        _writer.endTag();
        _writer.startTag(Tag.Spices);
        _writer.attributes("Cinnamon", "yum", "Nutmeg", "yum");
        _writer.attributes("Cumin", "yum", "Cardamon", "no thanks");
        _writer.endTag();
        _writer.endTag();
        _writer.endTag();
        _writer.endTag();
    }

    public static class TestCase extends junit.framework.TestCase
    {
        public void testXMLWriter()
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            XMLWriterTest writer = new XMLWriterTest();
            writer.write(pw);
            StringBuffer sb = sw.getBuffer();
            String lineEnding = System.getProperty("line.separator");
            String value = "<Workspace this=\"1\" that=\"2\" theother=\"3\">" + lineEnding +
                "  <Symbols>Weird symbols &lt; &amp; &gt; | { } make me happy</Symbols>" + lineEnding +
                "  <Foo one=\"1\" two=\"2\" this=\"1\" that=\"2\" theother=\"3\" this=\"1\" that=\"2\"" + lineEnding +
                "   theother=\"3\" this=\"1\" that=\"2\" theother=\"3\" this=\"1\" that=\"2\" theother=\"3\"" + lineEnding +
                "   this=\"1\" that=\"2\" theother=\"3\" this=\"1\" that=\"2\" theother=\"3\" this=\"1\"" + lineEnding +
                "   that=\"2\" theother=\"3\" this=\"1\" that=\"2\" theother=\"3\" this=\"1\" that=\"2\"" + lineEnding +
                "   theother=\"3\" this=\"1\" that=\"2\" theother=\"3\" this=\"1\" that=\"2\" theother=\"3\"" + lineEnding +
                "   this=\"1\" that=\"2\" theother=\"3\" this=\"1\" that=\"2\" theother=\"3\" this=\"1\"" + lineEnding +
                "   that=\"2\" theother=\"3\" this=\"1\" that=\"2\" theother=\"3\">" + lineEnding +
                "    <Bar fum=\"flow\">" + lineEnding +
                "      <Floom this=\"1\" that=\"2\" theother=\"3\">" + lineEnding +
                "      Some value that is really long in order to test the word wrapping stuff we&#039;re doing" + lineEnding +
                "      </Floom>" + lineEnding +
                "      <Spices Cinnamon=\"yum\" Nutmeg=\"yum\" Cumin=\"yum\" Cardamon=\"no thanks\" />" + lineEnding +
                "    </Bar>" + lineEnding +
                "  </Foo>" + lineEnding +
                "</Workspace>" + lineEnding;

            assertEquals("Text doesn't match", value, sb.toString());
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
