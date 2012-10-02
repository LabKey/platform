package org.labkey.api.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * Convenience class for creating <code>FileType</code>s for xml files.
 * The XMLFileType matches by parsing the root XML element namespace and name.
 *
 * User: kevink
 * Date: 10/1/12
 */
public class XMLFileType extends FileType
{
    private String _namespace;
    private Collection<String> _rootElements;

    public XMLFileType(String namespaceURI, String rootElement)
    {
        this(namespaceURI, Collections.singleton(rootElement));
    }

    public XMLFileType(String namespaceURI, Collection<String> rootElements)
    {
        super("xml");
        _namespace = namespaceURI;
        _rootElements = rootElements;
    }

    @Override
    public boolean isHeaderMatch(@NotNull byte[] header)
    {
        ByteArrayInputStream bais = null;
        XMLInputFactory factory = XMLInputFactory.newFactory();
        XMLStreamReader reader = null;
        try
        {
            bais = new ByteArrayInputStream(header);
            reader = factory.createXMLStreamReader(bais);
            return isHeaderMatch(reader);
        }
        catch (XMLStreamException e)
        {
            return false;
        }
        finally
        {
            if (reader != null)
                try { reader.close(); } catch (Exception e) { }
            
            IOUtils.closeQuietly(bais);
        }
    }

    private boolean isHeaderMatch(XMLStreamReader reader) throws XMLStreamException
    {
        while (reader.hasNext())
        {
            switch (reader.next())
            {
                case XMLStreamConstants.START_ELEMENT:
                    QName name = reader.getName();
                    return isQNameMatch(name);

                // We only consider the root element
                case XMLStreamConstants.END_DOCUMENT:
                case XMLStreamConstants.END_ELEMENT:
                    break;
            }
        }

        return false;
    }

    private boolean isQNameMatch(QName name)
    {
        return ObjectUtils.equals(_namespace, name.getNamespaceURI()) &&
               _rootElements.contains(name.getLocalPart());
    }
}
