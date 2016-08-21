/*******************************************************************************
 * --------------------------------------------------------------------------- *
 * File: * @(#) MSXMLParser.java * Author: * Robert M. Hubley
 * rhubley@systemsbiology.org
 * ****************************************************************************** * * *
 * This software is provided ``AS IS'' and any express or implied * *
 * warranties, including, but not limited to, the implied warranties of * *
 * merchantability and fitness for a particular purpose, are disclaimed. * * In
 * no event shall the authors or the Institute for Systems Biology * * liable
 * for any direct, indirect, incidental, special, exemplary, or * *
 * consequential damages (including, but not limited to, procurement of * *
 * substitute goods or services; loss of use, data, or profits; or * * business
 * interruption) however caused and on any theory of liability, * * whether in
 * contract, strict liability, or tort (including negligence * * or otherwise)
 * arising in any way out of the use of this software, even * * if advised of
 * the possibility of such damage. * * *
 * ******************************************************************************
 * 
 * ChangeLog
 * 
 * $Log: MSXMLParser.java,v $
 * Revision 1.1  2004/10/19 05:19:33  ajordens
 * Modified the overall structure of the jrap module.  Introduced
 * an ant build process.
 *
 * ant all -> rebuilds everything including javadoc
 * ant run -> runs the mzxml viewer
 *
 * jrap-dist.jar includes all compiled class files and
 * dependent libraries.
 *
 * Code was updated to include the latest modifications from
 * Patrick and Mathijs.
 * Revision 1.1.1.1 2003/04/09 00:02:54 ppatrick
 * Initial import.
 * 
 *  
 ******************************************************************************/
package org.systemsbiology.jrap;

import org.apache.log4j.Logger;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;

/**
 * A generic utility class for reading an MSXML file in a random access fashion
 * and utilizing a stored scan index for fast reads.
 *  
 */
public final class MSXMLParser
{
    static Logger _log = Logger.getLogger(MSXMLParser.class);

    //
    // Protected class members
    //

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    protected static final String NAMESPACES_FEATURE_ID = "http://xml.org/sax/features/namespaces";

    /**
     * Namespace prefixes feature id
     * (http://xml.org/sax/features/namespace-prefixes).
     */
    protected static final String NAMESPACE_PREFIXES_FEATURE_ID = "http://xml.org/sax/features/namespace-prefixes";

    /** Validation feature id (http://xml.org/sax/features/validation). */
    protected static final String VALIDATION_FEATURE_ID = "http://xml.org/sax/features/validation";

    /**
     * Schema validation feature id
     * (http://apache.org/xml/features/validation/schema).
     */
    protected static final String SCHEMA_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/schema";

    /**
     * Schema full checking feature id
     * (http://apache.org/xml/features/validation/schema-full-checking).
     */
    protected static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";

    /**
     * Dynamic validation feature id
     * (http://apache.org/xml/features/validation/dynamic).
     */
    protected static final String DYNAMIC_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/dynamic";

    /** Continue after fatal error id. */
    protected static final String CONT_AFTER_FATAL_ERROR_ID = "http://apache.org/xml/features/continue-after-fatal-error";

    /** Default parser name. */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    /** Default namespaces support (false). */
    protected static final boolean DEFAULT_NAMESPACES = false;

    /** Default namespace prefixes (false). */
    protected static final boolean DEFAULT_NAMESPACE_PREFIXES = false;

    /** Default validation support (false). */
    protected static final boolean DEFAULT_VALIDATION = false;

    /** Default Schema validation support (false). */
    protected static final boolean DEFAULT_SCHEMA_VALIDATION = false;

    /** Default Schema full checking support (false). */
    protected static final boolean DEFAULT_SCHEMA_FULL_CHECKING = false;

    /** Default dynamic validation support (false). */
    protected static final boolean DEFAULT_DYNAMIC_VALIDATION = false;

    /** Default dynamic validation support (false). */
    protected static final boolean DEFAULT_CONT_AFTER_FATAL_ERROR = false;

    /** A handle to the XML parser */
    protected XMLReader parser = null;

    /** The file we are in charge of reading */
    protected File file = null;

    /** The MD5 signature found the last time we read the index */
    protected String fileMD5 = null;

    /** The indexHandler */
    protected SAX2IndexHandler indexHandler = null;

    /** The information contained in the header of the mzXML file. */
    protected MZXMLFileInfo info;

    public MSXMLParser(File file) {
        this.file = file;

        // The defaults for the parser
        boolean namespaces = DEFAULT_NAMESPACES;
        boolean namespacePrefixes = DEFAULT_NAMESPACE_PREFIXES;
        boolean validation = DEFAULT_VALIDATION;
        boolean schemaValidation = DEFAULT_SCHEMA_VALIDATION;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean dynamicValidation = DEFAULT_DYNAMIC_VALIDATION;

        // Create a new index handler
        indexHandler = new SAX2IndexHandler();

        // Create parser
        try
        {
            parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
        } catch (Exception e)
        {
            System.err.println("error: Unable to instantiate parser ("
                    + DEFAULT_PARSER_NAME + ")");
        }

        // Set parser features
        try
        {
            parser.setFeature(NAMESPACES_FEATURE_ID, namespaces);
        } catch (SAXException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + NAMESPACES_FEATURE_ID + ")");
        }
        try
        {
            parser.setFeature(NAMESPACE_PREFIXES_FEATURE_ID, namespacePrefixes);
        } catch (SAXException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + NAMESPACE_PREFIXES_FEATURE_ID + ")");
        }
        try
        {
            parser.setFeature(VALIDATION_FEATURE_ID, validation);
        } catch (SAXException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + VALIDATION_FEATURE_ID + ")");
        }
        try
        {
            parser.setFeature(SCHEMA_VALIDATION_FEATURE_ID, schemaValidation);
        } catch (SAXNotRecognizedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + SCHEMA_VALIDATION_FEATURE_ID + ")");

        } catch (SAXNotSupportedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + SCHEMA_VALIDATION_FEATURE_ID + ")");
        }
        try
        {
            parser.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID,
                    schemaFullChecking);
        } catch (SAXNotRecognizedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + SCHEMA_FULL_CHECKING_FEATURE_ID + ")");

        } catch (SAXNotSupportedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + SCHEMA_FULL_CHECKING_FEATURE_ID + ")");
        }
        try
        {
            parser.setFeature(DYNAMIC_VALIDATION_FEATURE_ID, dynamicValidation);
        } catch (SAXNotRecognizedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + DYNAMIC_VALIDATION_FEATURE_ID + ")");

        } catch (SAXNotSupportedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + DYNAMIC_VALIDATION_FEATURE_ID + ")");
        }
        try
        {
            parser.setFeature(CONT_AFTER_FATAL_ERROR_ID, false);
        } catch (SAXNotRecognizedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + CONT_AFTER_FATAL_ERROR_ID + ")");

        } catch (SAXNotSupportedException e)
        {
            System.err.println("warning: Parser does not support feature ("
                    + CONT_AFTER_FATAL_ERROR_ID + ")");
        }

        // Seek to the index;
        FileInputStream fileIN = null;
        long indexPosition = -1;
        try
        {
            fileIN = new FileInputStream(this.file);
            long fileLength = this.file.length();
            fileIN.skip(fileLength - 500);
            byte[] bytes = new byte[500];
            int bytesRead = fileIN.read(bytes);
            String footer = new String(bytes, 0, bytesRead);
            int offset;
            if ((offset = footer.indexOf("<indexOffset>")) == -1)
            {
                _log.info("<indexOffset> not found!!!");
            }
            footer = footer.substring(offset + "<indexOffset>".length());
            int endIndex = footer.indexOf("</indexOffset>");
            if (endIndex == -1)
            {
                _log.info("</indexOffset> not found!!!");
            }

            footer = footer.substring(0, endIndex);
            indexPosition = Long.parseLong(footer);
        } catch (Exception e)
        {
            _log.info("exception:" + e, e);
        }

        // Parse to locate indexOffset tag
        parser.setContentHandler(indexHandler);
        parser.setErrorHandler(indexHandler);

        // Now that we located the offset to the file index
        // we have to go back and read in the index.
        try
        {
            fileIN.close();
            fileIN = new FileInputStream(this.file);
            fileIN.skip(indexPosition);
        } catch (Exception e)
        {
            _log.info("File exception:" + e, e);
        }
        try
        {
            parser.parse(new InputSource(fileIN));
        } catch (Exception e)
        {
            if (!"IdxReadException".equals(e.getMessage()))
            {
                System.err.println("error: Parse error occurred - "
                        + e.getMessage());
                Exception se = e;
                if (e instanceof SAXException)
                {
                    se = ((SAXException) e).getException();
                }
                if (se != null)
                    se.printStackTrace(System.err);
                else
                    e.printStackTrace(System.err);
            }
        }
        try
        {
            if (null != fileIN)
                fileIN.close();
            fileIN = new FileInputStream(this.file);
        } catch (Exception e)
        {
            System.out.println("File exception:" + e);
            e.printStackTrace();
        }

        SAX2HeaderHandler headerHandler = new SAX2HeaderHandler();
        parser.setContentHandler(headerHandler);

        try
        {
            parser.parse(new InputSource(fileIN));
        } catch (Exception e)
        {
            if (e.getMessage() == null || !e.getMessage().equals(
                    SAX2HeaderHandler.SUCCESSFUL_COMPLETION_MESSAGE))
            {
                System.err.println("error: Parse error occurred - "
                        + e.getMessage());
                Exception se = e;
                if (e instanceof SAXException)
                {
                    se = ((SAXException) e).getException();
                }
                if (se != null)
                    se.printStackTrace(System.err);
                else
                    e.printStackTrace(System.err);
            }
        }

        info = headerHandler.getInfo();

        try
        {
            fileIN.close();
        } catch (Exception e)
        {
            System.out.println("File exception:" + e);
            e.printStackTrace();
        }
    }


    public ScanHeader rapHeader(int scanNumber)
    {
    FileInputStream fileIN = null;
    try
        {
        long scanOffset = -1;
        try
        {
            fileIN = new FileInputStream(file);
            scanOffset = indexHandler.getScanOffset(scanNumber);
            if (scanOffset == -1)
            {
                return null;
            }
            fileIN.skip(scanOffset);
        } catch (Exception e)
        {
            _log.error("Error opening file", e);
        }

        SAX2ScanHeaderHandler scanHeaderHandler = new SAX2ScanHeaderHandler();
        try
        {
            parser.setContentHandler(scanHeaderHandler);
            parser.setErrorHandler(scanHeaderHandler);
            parser.parse(new InputSource(fileIN));
        } catch (SAXParseException e)
        {
            _log.error("Error parsing file", e);
        } catch (Exception e)
        {
            if (e.getMessage() == null || !e.getMessage().equals("ScanHeaderEndFoundException"))
            {
                if (e instanceof SAXException)
                {
                    e = ((SAXException) e).getException();
                }
                _log.error("Error parsing file", e);
            }
        }
        ScanHeader s = scanHeaderHandler.getScanHeader();
        if (null != s)
            s.scanOffset = scanOffset;
        return s;
        }
    finally
        {
        if (null != fileIN)
            try {fileIN.close();}catch (IOException x){}
        }
    }

    /**
     * Read a particular scan from a MSXML file and return a generic Scan object
     * with its data. Note: scanNumbers are 1-based, so scanNumber must be at
     * least 1
     */
    public Scan rap(int scanNumber)
    {
        FileInputStream fileIN = null;
    try
        {
        long scanOffset = -1;

        try
        {
            fileIN = new FileInputStream(file);
            scanOffset = indexHandler.getScanOffset(scanNumber);
            if (scanOffset == -1)
            {
                return null;
            }
            fileIN.skip(scanOffset);
        } catch (Exception e)
        {
            System.out.println("File exception:" + e);
            e.printStackTrace();
        }

        SAX2ScanHandler scanHandler = new SAX2ScanHandler();
        try
        {
            parser.setContentHandler(scanHandler);
            parser.setErrorHandler(scanHandler);
            parser.parse(new InputSource(fileIN));
        } catch (SAXParseException e)
        {
            // ignore
        } catch (Exception e)
        {
            if (!"ScanEndFoundException".equals(e.getMessage()))
            {
                System.err.println("error: Parse error occurred - "
                        + e.getMessage());
                Exception se = e;
                if (e instanceof SAXException)
                {
                    se = ((SAXException) e).getException();
                }
                if (se != null)
                    se.printStackTrace(System.err);
                else
                    e.printStackTrace(System.err);
            }
        }
        Scan s = scanHandler.getScan();
        if (null != s)
            s.header.scanOffset = scanOffset;
        return s;
        }
    finally
        {
        if (null != fileIN)
            try {fileIN.close();}catch (IOException x){}
        }
    }

    /**
     * Get the total number of scans in the mzXMLfile handled by this parser.
     *
     * @return The number of scans.
     */
    public int getScanCount()
    {
        return indexHandler.offsets.size();
    }

    public int getMaxScanNumber()
    {
        return indexHandler.getMaxScan();
    }

    /**
     * Get the header information contained in the mzXML file, which is constant
     * for every scan.
     * @return An instance of MZXMLFileInfo
     */
    public MZXMLFileInfo getHeaderInfo()
    {
        return info;
    }
}
