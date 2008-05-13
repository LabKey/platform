/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import org.labkey.api.util.AppProps;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;


/**
 * User: migra
 * Date: Aug 10, 2005
 * Time: 12:23:48 PM
 */
public class Lsid
{
    //A fairly loose pattern.
    //Matches urn:lsid:authority:namespace:id or urn:lsid:authority:namespace:id:version
    private static final Pattern LSID_REGEX = Pattern.compile("(?i)^urn:lsid:([^:]+):([^:]+):([^:]+)(?::(.*))?");
    protected String src;
    protected String authority = null;
    protected String namespace = null;
    protected String objectId = null;
    protected String version = null;
    protected boolean valid = false;
    private int hashCode = 0;
    private String prefix;
    private String suffix;

    public Lsid(String lsid)
    {
        src = lsid;
        lsid = decode(lsid);

        Matcher m = LSID_REGEX.matcher(lsid);

        if (!m.matches())
            return;

        authority = m.group(1).toLowerCase();
        namespace = m.group(2);
        objectId = m.group(3);
        version = m.group(4);
        valid = true;
        resetPrefix();
    }

    public Lsid(String namespace, String objectId)
    {
        valid = true;
        this.authority = AppProps.getInstance().getDefaultLsidAuthority();
        this.namespace = decode(namespace);
        this.objectId = decode(objectId);
        resetPrefix();
    }

    public Lsid(String namespacePrefix, String namespaceSuffix, String objectId)
    {
        valid = true;
        this.authority = AppProps.getInstance().getDefaultLsidAuthority();
        this.namespace = decode(namespacePrefix + "." + namespaceSuffix);
        this.objectId = decode(objectId);
        this.prefix = decode(namespacePrefix);
        this.suffix = decode(namespaceSuffix);
    }

    private String encode(String original)
    {
        try
        {
            return URLEncoder.encode(original, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalArgumentException("UTF-8 encoding not supported on this machine", e);
        }
    }

    private String decode(String original)
    {
        try
        {
            return URLDecoder.decode(original, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalArgumentException("UTF-8 encoding not supported on this machine", e);
        }
    }

    /**
     * Does lsid meet our (quite loose) requirements on what an LSID looks like
     * We do not care whether characters between : are actually legal or even if
     * the lsid is a valid URI.
     */
    public static boolean isLsid(String lsid)
    {
        return LSID_REGEX.matcher(lsid).matches();
    }

    public static String canonical(String lsid)
    {
        return new Lsid(lsid).toString();
    }

    public boolean equals(Object o)
    {
        if (null == o || !(o instanceof Lsid))
            return false;

        Lsid lsid = (Lsid) o;
        if (!valid)
            return src.equals(lsid.src);

        return authority.equals(lsid.authority) && namespace.equals(lsid.namespace) && objectId.equals(lsid.objectId)
                && (version == null ? lsid.version == null : version.equals(lsid.version));
    }

    public int hashCode()
    {
        if (0 == hashCode)
            hashCode = toString().hashCode();

        return hashCode;
    }

    /**
     * Converts this to a string. urn:lsid:authority prefix will always be lower case so may not
     * match original input. Use equals or canonical to be sure of equality.
     */
    public String toString()
    {
        if (!valid)
            return src;

        String encodedAuthority = encode(authority);
        String encodedNamespace = encode(namespace);
        String encodedObjectId = encode(objectId);
        String encodedVersion = version == null ? null : encode(version);

        String result = "urn:lsid:" + encodedAuthority + ":" + encodedNamespace + ":" + encodedObjectId + (null == encodedVersion ? "" : ":" + encodedVersion);
        // Need to not encode the last '#' since property descriptors use that in their URIs
        int index = result.lastIndexOf("%23");
        if (index != -1)
        {
            result = result.substring(0, index) + "#" + result.substring(index + "%23".length());
        }
        return result;
    }

    public String getAuthority()
    {
        return authority;
    }

    public void setAuthority(String authority)
    {
        this.authority = authority;
    }

    public String getNamespace()
    {
        return namespace;
    }

    public String getObjectId()
    {
        return objectId;
    }

    public void setObjectId(String objectId)
    {
        this.objectId = objectId;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }


    public void setNamespace(String namespace)
    {
        this.namespace = namespace;
        resetPrefix();
    }

    public String getNamespacePrefix()
    {
        return prefix;
    }

    public String getNamespaceSuffix()
    {
        return suffix;
    }

    public void setNamespacePrefix(String prefix)
    {
        this.prefix = prefix;
        namespace = (prefix == null ? "" : prefix) + (suffix == null ? "" : ("." + suffix));
    }

    public void setNamespaceSuffix(String suffix)
    {
        this.suffix = suffix;
        namespace = (prefix == null ? "" : prefix) + (suffix == null ? "" : ("." + suffix));
    }

    private void resetPrefix()
    {
        prefix = null;
        suffix = null;

        String ns = getNamespace();
        if (null == ns)
            return;

        int dotPos = ns.indexOf('.');
        if (-1 == dotPos)
        {
            prefix = ns;
            return;
        }

        prefix = ns.substring(0, dotPos);
        suffix = ns.substring(dotPos + 1);
    }

    static public String namespaceLikeString(String namespace)
    {
        return "urn:lsid:%:" + namespace + ".%:%";
    }

    static public String namespaceFilter(String columnName, String namespace)
    {
        return columnName + " LIKE '" + namespaceLikeString(namespace) + "'";
    }
}
