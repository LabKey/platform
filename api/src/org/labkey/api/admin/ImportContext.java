package org.labkey.api.admin;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.writer.ContainerUser;

import java.io.File;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public interface ImportContext<XmlType extends XmlObject> extends ContainerUser
{
    public XmlType getXml() throws ImportException;
    public File getDir(String xmlNodeName) throws ImportException;
    public Logger getLogger();
}
