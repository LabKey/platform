/*
 * Copyright (c) 2009-2026 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlTokenSource;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.logging.LogHelper;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class XmlBeansUtil
{
    private static final Logger LOG = LogHelper.getLogger(XmlBeansUtil.class, "XML schema and validator XXE hardening");

    private XmlBeansUtil()
    {
    }

    // Standard options used by folder export
    public static XmlOptions getDefaultSaveOptions()
    {
        XmlOptions options = new XmlOptions();
        options.setSavePrettyPrint();
        options.setUseDefaultNamespace();
        options.setCharacterEncoding("UTF-8");
        options.setSaveCDataEntityCountThreshold(0);
        options.setSaveCDataLengthThreshold(0);
        options.setSaveAggressiveNamespaces(); // causes the saver to reduce the number of namespace declarations

        return options;
    }

    // Standard options used for parsing to enable validation.
    public static XmlOptions getDefaultParseOptions()
    {
        XmlOptions options = new XmlOptions();
        options.setLoadLineNumbers();

        return options;
    }

    @Deprecated  // Use the version below, and pass in details (filename, etc.)
    public static void validateXmlDocument(XmlObject doc) throws XmlValidationException
    {
        validateXmlDocument(doc, null);
    }

    // Details can be filename, etc. to help admin narrow down the source of the problem
    public static void validateXmlDocument(XmlObject doc, @Nullable String details) throws XmlValidationException
    {
        XmlOptions options = getDefaultParseOptions();
        Collection<XmlError> errorList = new LinkedList<>();
        options.setErrorListener(errorList);

        if (!doc.validate(options))
            throw new XmlValidationException(errorList, doc.schemaType().toString(), details);
    }

    public static String getErrorMessage(XmlException ex)
    {
        if (ex.getError() != null)
            return getErrorMessage(ex.getError());
        return ex.getMessage();
    }

    public static String getErrorMessage(XmlError error)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(error.toString());
        if (error.getLine() > 0)
        {
            sb.append(" (line ").append(error.getLine());
            if (error.getColumn() > 0)
                sb.append(", column ").append(error.getColumn());
            sb.append(")");
        }
        return sb.toString();
    }

    // Insert standard export comment explaining where the data lives, who exported it, and when
    public static void addStandardExportComment(XmlTokenSource doc, Container c, User user)
    {
        String urlString = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c).getURIString();
        if (urlString.endsWith("?"))
            urlString = urlString.substring(0, urlString.length() - 1);
        String shortName = LookAndFeelProperties.getInstance(c).getShortName();
        String comment = "Exported from " + shortName + " at " + urlString + " by " + user.getFriendlyName() + " on " + new Date();
        addComment(doc, comment);
    }

    public static void addComment(XmlTokenSource doc, String comment)
    {
        try (XmlCursor cursor = doc.newCursor())
        {
            cursor.insertComment(comment);
        }
    }

    /**
     * XML parsing factories preconfigured to prevent XML external entity references (XXE).
     * These are static and are unfortunately mutable. We could switch to a factory pattern to create
     * freshly configured factories.
     */
    public static final SAXParserFactory SAX_PARSER_FACTORY;
    public static final SAXParserFactory SAX_PARSER_FACTORY_ALLOWING_DOCTYPE;
    public static final XMLInputFactory XML_INPUT_FACTORY;
    public static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;
    public static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY_ALLOWING_DOCTYPE;

    static
    {
        //noinspection XMLInputFactory
        XML_INPUT_FACTORY = XMLInputFactory.newInstance();
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        try
        {
            SAX_PARSER_FACTORY = saxParserFactory(false);
            SAX_PARSER_FACTORY_ALLOWING_DOCTYPE = saxParserFactory(true);

            DOCUMENT_BUILDER_FACTORY = documentBuilderFactory(false);
            // Use the ALLOWING_DOCTYPE variant when parsing XML that contains a <!DOCTYPE> declaration (e.g. NCBI's eSummary responses)
            DOCUMENT_BUILDER_FACTORY_ALLOWING_DOCTYPE = documentBuilderFactory(true);
        }
        catch (ParserConfigurationException | SAXException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private static SAXParserFactory saxParserFactory(boolean allowDocType) throws SAXException, ParserConfigurationException
    {
        //noinspection XMLInputFactory
        SAXParserFactory result = SAXParserFactory.newInstance();
        result.setNamespaceAware(true);
        result.setFeature("http://xml.org/sax/features/validation", false);
        result.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        // Disable features that could lead to XXE or other vulnerabilities
        // Keep in sync with ModuleArchive.nameFromModuleXML()
        if (!allowDocType)
        {
            result.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        }
        result.setFeature("http://xml.org/sax/features/external-general-entities", false);
        result.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        result.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return result;
    }

    private static DocumentBuilderFactory documentBuilderFactory(boolean allowDocType) throws ParserConfigurationException
    {
        //noinspection XMLInputFactory
        DocumentBuilderFactory result = DocumentBuilderFactory.newInstance();
        result.setNamespaceAware(true);

        // Disable features that could lead to XXE or other vulnerabilities.
        // When allowDocType is true the DOCTYPE declaration is permitted. External entity
        // resolution remains disabled, so XXE protection is still in effect.
        if (!allowDocType)
        {
            result.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        }
        result.setFeature("http://xml.org/sax/features/external-general-entities", false);
        result.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        result.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        result.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        result.setXIncludeAware(false);
        result.setExpandEntityReferences(false);
        return result;
    }

    /** A {@link SchemaFactory} hardened against XXE (CWE-611). Not thread-safe, so a fresh instance per call. */
    public static SchemaFactory schemaFactory()
    {
        //noinspection SchemaFactory
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        require(() -> factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true));
        // Xerces rejects both, but set for the JDK implementation
        attempt(() -> factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""), XMLConstants.ACCESS_EXTERNAL_DTD);
        attempt(() -> factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar"), XMLConstants.ACCESS_EXTERNAL_SCHEMA);
        // Bundled schemas import sibling XSDs, so local references must still resolve
        factory.setResourceResolver(LOCAL_ONLY_RESOLVER);
        return factory;
    }

    // The Validator resolves entities in the instance document independently of the SchemaFactory, so it must be locked down separately.
    public static Validator hardenValidator(Validator validator)
    {
        require(() -> validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true));
        // Xerces accepts these but never applies them to StreamSource input: StreamValidatorHelper builds its own XML11Configuration and copies properties, not features
        attempt(() -> validator.setFeature("http://xml.org/sax/features/external-general-entities", false), "external-general-entities");
        attempt(() -> validator.setFeature("http://xml.org/sax/features/external-parameter-entities", false), "external-parameter-entities");
        attempt(() -> validator.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false), "load-external-dtd");
        attempt(() -> validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""), XMLConstants.ACCESS_EXTERNAL_DTD);
        attempt(() -> validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""), XMLConstants.ACCESS_EXTERNAL_SCHEMA);
        // Stored as the ENTITY_RESOLVER property, which that copy does carry across, so this is what actually blocks XXE under Xerces
        validator.setResourceResolver(REFUSE_ALL_RESOLVER);
        return validator;
    }

    /** Resolves to nothing, so a refused reference expands to the empty string instead of being fetched. */
    private static final LSInput EMPTY_INPUT = new LSInput()
    {
        @Override public Reader getCharacterStream() { return new StringReader(""); }
        @Override public void setCharacterStream(Reader characterStream) { }
        @Override public InputStream getByteStream() { return null; }
        @Override public void setByteStream(InputStream byteStream) { }
        @Override public String getStringData() { return ""; }
        @Override public void setStringData(String stringData) { }
        @Override public String getSystemId() { return null; }
        @Override public void setSystemId(String systemId) { }
        @Override public String getPublicId() { return null; }
        @Override public void setPublicId(String publicId) { }
        @Override public String getBaseURI() { return null; }
        @Override public void setBaseURI(String baseURI) { }
        @Override public String getEncoding() { return StandardCharsets.UTF_8.name(); }
        @Override public void setEncoding(String encoding) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean certifiedText) { }
    };

    /** The instance document is the attacker-supplied half of every call site, and never legitimately references anything external. */
    private static final LSResourceResolver REFUSE_ALL_RESOLVER = (_, _, _, systemId, _) -> {
        LOG.warn("Refused external reference to {} while validating XML", systemId);
        return EMPTY_INPUT;
    };

    /** Debug rather than warn: bundled xenc-schema-11.xsd still declares a w3.org DOCTYPE, so this fires on every SAML schema compile. */
    private static final LSResourceResolver LOCAL_ONLY_RESOLVER = (_, _, _, systemId, baseURI) -> {
        if (isLocal(systemId, baseURI))
            return null; // fall through to the default resolver
        LOG.debug("Refused non-local schema reference to {} while compiling XML schema", systemId);
        return EMPTY_INPUT;
    };

    /** A jar: base URI is opaque, so {@link URI#resolve} leaves a relative reference untouched -- it stays scheme-less, which counts as local. */
    private static boolean isLocal(@Nullable String systemId, @Nullable String baseURI)
    {
        if (systemId == null)
            return true;

        try
        {
            URI uri = URI.create(systemId);
            if (!uri.isAbsolute() && baseURI != null)
                uri = URI.create(baseURI).resolve(uri);
            String scheme = uri.getScheme();
            return scheme == null || "file".equalsIgnoreCase(scheme) || "jar".equalsIgnoreCase(scheme);
        }
        catch (IllegalArgumentException e)
        {
            return false; // unparseable, so not something we're willing to vouch for
        }
    }

    @FunctionalInterface
    private interface XmlSetting
    {
        void apply() throws SAXException;
    }

    // FEATURE_SECURE_PROCESSING is honored by every JAXP implementation, so failure to set it is fatal.
    private static void require(XmlSetting setting)
    {
        try
        {
            setting.apply();
        }
        catch (SAXException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    // Implementations recognize different subsets of these controls (Xerces rejects the accessExternal* properties, XERCESJ-1654), so skip a rejected setting rather than aborting the rest.
    private static void attempt(XmlSetting setting, String name)
    {
        try
        {
            setting.apply();
        }
        catch (SAXNotRecognizedException | SAXNotSupportedException e)
        {
            LOG.debug("XML implementation does not recognize {}; relying on the other hardening settings", name);
        }
        catch (SAXException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    /**
     * Covers the XXE (CWE-611) contract of {@link #schemaFactory()} and {@link #hardenValidator(Validator)}, asserting
     * on observable behavior rather than on which properties were set: a real server resolves Xerces instead of the
     * JDK's JAXP, the two honor different subsets of the controls, and {@link #attempt} swallows the rejections -- so
     * the hardening can compile, look correct, and do nothing.
     */
    public static class TestCase extends Assert
    {
        private static final String TRIVIAL_XSD =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
            "<xs:element name='r' type='xs:string'/>" +
            "</xs:schema>";

        // ---------- hardenValidator(): instance-document XXE ----------

        @Test
        public void hardenedValidatorRefusesExternalDtdSubset() throws Exception
        {
            assertHardenedValidatorRefuses("external DTD subset", probe ->
                "<!DOCTYPE r SYSTEM \"" + probe.url("/external.dtd", ExternalReferenceProbe.DTD_BODY) + "\">" +
                "<r>hello</r>");
        }

        @Test
        public void hardenedValidatorRefusesExternalGeneralEntity() throws Exception
        {
            assertHardenedValidatorRefuses("external general entity", probe ->
                "<!DOCTYPE r [<!ENTITY x SYSTEM \"" + probe.url("/general", ExternalReferenceProbe.ENTITY_BODY) + "\">]>" +
                "<r>&x;</r>");
        }

        @Test
        public void hardenedValidatorRefusesExternalParameterEntity() throws Exception
        {
            assertHardenedValidatorRefuses("external parameter entity", probe ->
                "<!DOCTYPE r [<!ENTITY % p SYSTEM \"" + probe.url("/param", "<!ENTITY unused \"x\">") + "\">%p;]>" +
                "<r>hello</r>");
        }

        /**
         * Harness self-check: without it a URL typo would make the three tests above pass with no protection in place.
         * If the platform default ever becomes safe on its own, delete this rather than weakening those assertions.
         */
        @Test
        public void probeDetectsFetchWhenValidatorIsNotHardened() throws Exception
        {
            try (ExternalReferenceProbe probe = ExternalReferenceProbe.start())
            {
                String xml = "<!DOCTYPE r SYSTEM \"" + probe.url("/external.dtd", ExternalReferenceProbe.DTD_BODY) + "\">" +
                    "<r>hello</r>";
                //noinspection SchemaFactory
                SchemaFactory raw = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Validator validator = raw.newSchema(new StreamSource(new StringReader(TRIVIAL_XSD))).newValidator();
                validateIgnoringErrors(validator, xml);

                assertTrue("Probe must observe a fetch from an unhardened validator, otherwise the hardening " +
                    "tests in this class prove nothing", probe.wasContacted());
            }
        }

        // ---------- schemaFactory(): schema-document XXE ----------

        @Test
        public void schemaFactoryRefusesExternalDoctypeInSchemaDocument() throws Exception
        {
            try (ExternalReferenceProbe probe = ExternalReferenceProbe.start())
            {
                String xsd = "<!DOCTYPE xs:schema SYSTEM \"" +
                    probe.url("/schema.dtd", "<!ELEMENT xs:schema ANY>") + "\">" + TRIVIAL_XSD;
                compileIgnoringErrors(schemaFactory(), new StreamSource(new StringReader(xsd)));

                probe.assertNotContacted("schemaFactory() must not fetch a DOCTYPE declared by a schema document");
            }
        }

        @Test
        public void schemaFactoryRefusesRemoteImport() throws Exception
        {
            try (ExternalReferenceProbe probe = ExternalReferenceProbe.start())
            {
                String remote = probe.url("/remote.xsd",
                    "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:probe'/>");
                String xsd = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                    "<xs:import namespace='urn:probe' schemaLocation='" + remote + "'/>" +
                    "<xs:element name='r' type='xs:string'/>" +
                    "</xs:schema>";
                compileIgnoringErrors(schemaFactory(), new StreamSource(new StringReader(xsd)));

                probe.assertNotContacted("schemaFactory() must not fetch a remote xs:import");
            }
        }

        // ---------- over-blocking guards ----------
        // Bundled schemas compose sibling XSDs by relative path, from a jar: URL when deployed and a file: URL in a dev build.

        @Test
        public void schemaFactoryCompilesLocalImportChainFromFileUrl() throws Exception
        {
            Path dir = Files.createTempDirectory("xmlBeansUtilFile");

            try
            {
                writeSchemaPair(dir);
                Schema schema = schemaFactory().newSchema(dir.resolve("main.xsd").toUri().toURL());

                assertNotNull("A local, relative xs:import must still resolve from a file: URL", schema);
            }
            finally
            {
                FileUtil.deleteDir(dir.toFile());
            }
        }

        @Test
        public void schemaFactoryCompilesLocalImportChainFromJarUrl() throws Exception
        {
            Path dir = Files.createTempDirectory("xmlBeansUtilJar");

            try
            {
                writeSchemaPair(dir);
                File jar = FileUtil.appendName(dir.toFile(), "schemas.jar");

                try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar)))
                {
                    for (String name : new String[]{"main.xsd", "imported.xsd"})
                    {
                        out.putNextEntry(new JarEntry("schemas/" + name));
                        out.write(Files.readAllBytes(dir.resolve(name)));
                        out.closeEntry();
                    }
                }

                URL url = URI.create("jar:" + jar.toURI() + "!/schemas/main.xsd").toURL();
                Schema schema = schemaFactory().newSchema(url);

                assertNotNull("A local, relative xs:import must still resolve from a jar: URL, which is how " +
                    "schemas are loaded in a deployed server", schema);
            }
            finally
            {
                FileUtil.deleteDir(dir.toFile());
            }
        }

        // ---------- helpers ----------

        private void assertHardenedValidatorRefuses(String vector, Function<ExternalReferenceProbe, String> instanceDoc) throws Exception
        {
            try (ExternalReferenceProbe probe = ExternalReferenceProbe.start())
            {
                String xml = instanceDoc.apply(probe);
                Validator validator = hardenValidator(schemaFactory()
                    .newSchema(new StreamSource(new StringReader(TRIVIAL_XSD)))
                    .newValidator());
                validateIgnoringErrors(validator, xml);

                probe.assertNotContacted("hardenValidator() must not resolve the " + vector + " of an instance document");
            }
        }

        /** Whether the document is schema-valid is beside the point; the fetch is the signal. */
        private void validateIgnoringErrors(Validator validator, String xml) throws IOException
        {
            try
            {
                validator.validate(new StreamSource(new StringReader(xml)));
            }
            catch (SAXException ignored)
            {
            }
        }

        /** Likewise: a refused reference may or may not surface as a compile error. */
        private void compileIgnoringErrors(SchemaFactory factory, StreamSource source)
        {
            try
            {
                factory.newSchema(source);
            }
            catch (SAXException ignored)
            {
            }
        }

        /** The relative-import shape every bundled LabKey schema chain uses. */
        private void writeSchemaPair(Path dir) throws IOException
        {
            Files.writeString(dir.resolve("imported.xsd"),
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:imported'>" +
                "<xs:element name='child' type='xs:string'/>" +
                "</xs:schema>", StandardCharsets.UTF_8);

            Files.writeString(dir.resolve("main.xsd"),
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:i='urn:imported'>" +
                "<xs:import namespace='urn:imported' schemaLocation='imported.xsd'/>" +
                "<xs:element name='r'><xs:complexType><xs:sequence>" +
                "<xs:element ref='i:child'/>" +
                "</xs:sequence></xs:complexType></xs:element>" +
                "</xs:schema>", StandardCharsets.UTF_8);
        }
    }
}
