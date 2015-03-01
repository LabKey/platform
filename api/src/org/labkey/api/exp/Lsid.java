/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.settings.AppProps;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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

    /** Assume a fully qualified, correctly encoded LSID */
    public Lsid(String lsid)
    {
        src = lsid;

        Matcher m = LSID_REGEX.matcher(lsid);

        if (!m.matches())
            return;

        authority = decodePart(m.group(1).toLowerCase());
        namespace = decodePart(m.group(2));
        objectId = decodePart(m.group(3));
        version = decodePart(m.group(4));
        valid = true;
        resetPrefix();
    }

    /** Assumes that the separate parts are not encoded  */
    public Lsid(String namespace, String objectId)
    {
        this(namespace, objectId, AppProps.getInstance());
    }

    private Lsid(String namespace, String objectId, AppProps.Interface appProps)
    {
        valid = true;
        this.authority = appProps.getDefaultLsidAuthority();
        this.namespace = namespace;
        this.objectId = objectId;
        resetPrefix();
    }

    /** Assumes that the separate parts are not encoded  */
    public Lsid(String namespacePrefix, String namespaceSuffix, @NotNull String objectId)
    {
        this(namespacePrefix, namespaceSuffix, objectId, AppProps.getInstance());
    }

    private Lsid(String namespacePrefix, String namespaceSuffix, String objectId, AppProps.Interface appProps)
    {
        valid = true;
        this.authority = appProps.getDefaultLsidAuthority();
        this.namespace = namespacePrefix + "." + namespaceSuffix;
        this.objectId = objectId;
        this.prefix = namespacePrefix;
        this.suffix = namespaceSuffix;
    }

    /** URI encode a part of a URI. Colons will be encoded into %3A, for example */
    public static String encodePart(String original)
    {
        try
        {
            String result = URLEncoder.encode(original, "UTF-8");
            result = result.replace(":", "%3A");
            return result;
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalArgumentException("UTF-8 encoding not supported on this machine", e);
        }
    }

    /** URI decode a part of a URI. */
    public static String decodePart(String original)
    {
        if (original == null)
        {
            return null;
        }
        
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
     *
     * TODO: Encoding is incorrect
     */
    public String toString()
    {
        if (!valid)
            return src;

        String encodedAuthority = encodePart(authority);
        String encodedNamespace = encodePart(namespace);
        String encodedObjectId = encodePart(objectId);
        String encodedVersion = version == null ? null : encodePart(version);

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

    public Lsid setAuthority(String authority)
    {
        this.authority = authority;
        return this;
    }

    public String getNamespace()
    {
        return namespace;
    }

    public String getObjectId()
    {
        return objectId;
    }

    public Lsid setObjectId(String objectId)
    {
        this.objectId = objectId;
        return this;
    }

    public String getVersion()
    {
        return version;
    }

    public Lsid setVersion(String version)
    {
        this.version = version;
        return this;
    }


    public Lsid setNamespace(String namespace)
    {
        this.namespace = namespace;
        resetPrefix();
        return this;
    }

    public String getNamespacePrefix()
    {
        return prefix;
    }

    public String getNamespaceSuffix()
    {
        return suffix;
    }

    public Lsid setNamespacePrefix(String prefix)
    {
        this.prefix = prefix;
        namespace = (prefix == null ? "" : prefix) + (suffix == null ? "" : ("." + suffix));
        return this;
    }

    public Lsid setNamespaceSuffix(String suffix)
    {
        this.suffix = suffix;
        namespace = (prefix == null ? "" : prefix) + (suffix == null ? "" : ("." + suffix));
        return this;
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

    public static class TestCase extends Assert
    {
        private static final String DEFAULT_LSID_AUTHORITY = "server.test";

        private static AppProps.Interface _mockAppProps;

        @BeforeClass
        public static void setUp()
        {
            Mockery context = new Mockery();
            _mockAppProps = context.mock(AppProps.Interface.class);
            context.checking(new Expectations() {{
                allowing(_mockAppProps).getDefaultLsidAuthority();
                will(returnValue(DEFAULT_LSID_AUTHORITY));
            }});
        }

        @Test
        public void testSimpleDecode()
        {
            Lsid simpleLsid = new Lsid("urn:lsid:labkey.com:SampleSet.Folder-4:ReproSet");
            assertEquals("urn:lsid:labkey.com:SampleSet.Folder-4:ReproSet", simpleLsid.toString());
            assertEquals("labkey.com", simpleLsid.getAuthority());
            assertEquals("SampleSet", simpleLsid.getNamespacePrefix());
            assertEquals("Folder-4", simpleLsid.getNamespaceSuffix());
            assertEquals("ReproSet", simpleLsid.getObjectId());
            assertEquals(null, simpleLsid.getVersion());
        }

        @Test
        public void testSimpleEncode()
        {
            Lsid simpleLsid = new Lsid("SampleSet.Folder-4", "ReproSet", _mockAppProps);
            assertEquals("urn:lsid:" + DEFAULT_LSID_AUTHORITY + ":SampleSet.Folder-4:ReproSet", simpleLsid.toString());
            assertEquals(DEFAULT_LSID_AUTHORITY, simpleLsid.getAuthority());
            assertEquals("SampleSet", simpleLsid.getNamespacePrefix());
            assertEquals("Folder-4", simpleLsid.getNamespaceSuffix());
            assertEquals("ReproSet", simpleLsid.getObjectId());
            assertEquals(null, simpleLsid.getVersion());
        }

        @Test
        public void testDecodeWithColon()
        {
            Lsid lsid = new Lsid("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%3ASet");
            assertEquals("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%3ASet", lsid.toString());
            assertEquals("labkey.com", lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro:Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testEncodeWithColon()
        {
            Lsid lsid = new Lsid("SampleSet.Folder-4", "Repro:Set", _mockAppProps);
            assertEquals("urn:lsid:" + DEFAULT_LSID_AUTHORITY + ":SampleSet.Folder-4:Repro%3ASet", lsid.toString());
            assertEquals(_mockAppProps.getDefaultLsidAuthority(), lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro:Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testDecodeWithPercent()
        {
            Lsid lsid = new Lsid("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%25Set");
            assertEquals("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%25Set", lsid.toString());
            assertEquals("labkey.com", lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro%Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testDecodeWithSpace()
        {
            Lsid lsid = new Lsid("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%20Set");
            assertEquals("urn:lsid:labkey.com:SampleSet.Folder-4:Repro+Set", lsid.toString());
            assertEquals("labkey.com", lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());

            lsid = new Lsid("urn:lsid:labkey.com:SampleSet.Folder-4:Repro+Set");
            assertEquals("urn:lsid:labkey.com:SampleSet.Folder-4:Repro+Set", lsid.toString());
            assertEquals("labkey.com", lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testEncodeWithSpace()
        {
            Lsid lsid = new Lsid("SampleSet.Folder-4", "Repro Set", _mockAppProps);
            assertEquals("urn:lsid:" + DEFAULT_LSID_AUTHORITY + ":SampleSet.Folder-4:Repro+Set", lsid.toString());
            assertEquals(DEFAULT_LSID_AUTHORITY, lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testDecodeWithPlus()
        {
            Lsid lsid = new Lsid("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%2BSet");
            assertEquals("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%2BSet", lsid.toString());
            assertEquals("labkey.com", lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro+Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testEncodeWithPlus()
        {
            Lsid lsid = new Lsid("SampleSet.Folder-4", "Repro+Set", _mockAppProps);
            assertEquals("urn:lsid:" + DEFAULT_LSID_AUTHORITY + ":SampleSet.Folder-4:Repro%2BSet", lsid.toString());
            assertEquals(DEFAULT_LSID_AUTHORITY, lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro+Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testFixupPropertyURI()
        {
            assertEquals("urn:lsid:labkey.com:AssayDomain-Run.Folder-18698:WithPercent#%25IDs", fixupPropertyURI("urn:lsid:labkey.com:AssayDomain-Run.Folder-18698:WithPercent#%IDs"));
            assertEquals("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#Bad%25Name", fixupPropertyURI("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#Bad%Name"));
            assertEquals("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#TargetStudy", fixupPropertyURI("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#TargetStudy"));
        }
    }

    /** Handle an unencoded % in the property name part of a property URI (after the hash) by encoding it, if needed */
    public static @Nullable String fixupPropertyURI(@Nullable String uri)
    {
        if (uri == null)
        {
            return null;
        }
        try
        {
            new URI(uri);
        }
        catch (URISyntaxException e)
        {
            int hashIndex = uri.indexOf("#");
            if (hashIndex > -1 && uri.substring(hashIndex).contains("%"))
            {
                return uri.substring(0, hashIndex + 1) + Lsid.encodePart(uri.substring(hashIndex + 1));
            }
        }
        return uri;
    }

}
