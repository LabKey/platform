/*
 * Copyright (c) 2007 LabKey Corporation
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
