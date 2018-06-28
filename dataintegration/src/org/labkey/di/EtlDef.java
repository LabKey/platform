package org.labkey.di;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Entity;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.XmlValidationException;
import org.labkey.di.pipeline.DescriptorCacheHandler;
import org.labkey.di.pipeline.TransformDescriptor;
import org.labkey.di.pipeline.TransformManager;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

/**
 * User: tgaluhn
 * Date: 6/1/2018
 * <p>
 * Represents a row in the {@link DataIntegrationQuerySchema#getEtlDefTableInfo()} table.
 *
 */
public class EtlDef extends Entity
{
    public static final String DECLARING_MODULE_NAME = DataIntegrationModule.NAME;
    private int _etlDefId = -1;
    private String _name;
    private String _description;
    private String _definition;
    private String _configId = null;

    public enum Change {Insert, Update, Delete};

    public int getEtlDefId()
    {
        return _etlDefId;
    }

    public void setEtlDefId(int etlDefId)
    {
        _etlDefId = etlDefId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getDefinition()
    {
        return _definition;
    }

    public void setDefinition(String definition)
    {
        _definition = definition;
    }

    @Nullable
    public TransformDescriptor getDescriptor()
    {
        TransformDescriptor descriptor = TransformManager.get().parseETL(getResource(), getModule());
        if (null != descriptor)
            descriptor.setUserDefined(true);
        return descriptor;
    }

    public TransformDescriptor getDescriptorThrow() throws XmlValidationException, XmlException, IOException
    {
        TransformDescriptor descriptor =  TransformManager.get().parseETLThrow(getResource(), getModule());
        descriptor.setUserDefined(true);
        return descriptor;
    }

    @NotNull
    private EtlDefResource getResource()
    {
        return new EtlDefResource(getConfigName(), getDefinition());
    }

    public Module getModule()
    {
        return ModuleLoader.getInstance().getModule(DECLARING_MODULE_NAME);
    }

    public String getConfigName()
    {
        return "User_Defined_" + (getEtlDefId() > 0 ? ("EtlDefId_" + getEtlDefId()) : "Edit In Progress");
    }

    public String getConfigId()
    {
        if (null == _configId)
            _configId = TransformManager.get().createConfigId(getModule(), getConfigName());
        return _configId;
    }

    public String getPrettyPrintDefinition()
    {
        String formattedXml = getDefinition();
        try
        {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(getDefinition().getBytes(StringUtilsLabKey.DEFAULT_CHARSET))));

            // Remove whitespaces outside tags
            document.normalize();
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']",
                    document,
                    XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); ++i)
            {
                Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }
            // Pretty print xml
            DOMSource xmlInput = new DOMSource(document);
            StreamResult xmlOutput = new StreamResult(new StringWriter());
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(xmlInput, xmlOutput);
            formattedXml = xmlOutput.getWriter().toString();
        }
        catch (IOException | ParserConfigurationException | SAXException | TransformerException | XPathExpressionException e)
        {
            // Oh well, we tried. Worst is the xml string is no better than it was before.
        }
        return formattedXml;
    }


    /**
     * Facade to present the db persisted etl xml as a module resource
     */
    private static class EtlDefResource extends AbstractResource
    {
        private final String _defXml;

        private EtlDefResource(String configName, String defXml)
        {
            super(new Path(configName + DescriptorCacheHandler.DESCRIPTOR_EXTENSION), null);
            _defXml = defXml;
        }

        @Override
        public Resource parent()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream()
        {
            return IOUtils.toInputStream(_defXml, StringUtilsLabKey.DEFAULT_CHARSET);
        }

        @Override
        public String toString()
        {
            return FileUtil.getBaseName(getPath().getName());
        }
    }

}

