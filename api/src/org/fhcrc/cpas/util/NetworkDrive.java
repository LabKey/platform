package org.fhcrc.cpas.util;

/**
 * Bean class for use with &lt;Resource> tag in labkey.xml.
 * It's here to upgrade from 1.7 and earlier installations - we used
 * to use JNDI to grab these properties, but now store them in the
 * database.
 * It reads from XML like the following:
 * <p/>
 * &lt;Resource name="drive/x" auth="Container"
 * type="org.labkey.api.util.NetworkDrive"/>
 * &lt;ResourceParams name="drive/x">
 * &lt;parameter>
 * &lt;name>path&lt;/name>
 * &lt;value>\\server\share&lt;value>
 * &lt;/parameter>
 * &lt;parameter>
 * &lt;name>user&lt;/name>
 * &lt;value>jdoe&lt;/value>
 * &lt;/parameter>
 * &lt;parameter>
 * &lt;name>password&lt;/name>
 * &lt;value>??????????&lt;/value>
 * &lt;/parameter>
 * <p/>
 * <p/>
 * This is necessary for connecting our NT tomcat web service to
 * Sun Unix SMB shares.
 */
public class NetworkDrive extends org.labkey.api.util.NetworkDrive
{
}
