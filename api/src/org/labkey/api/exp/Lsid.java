/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.Builder;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Pair;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.repeat;

/**
 * Life-sciences identifier (LSID). A structured URI to describe things like samples, data files, assay runs, and protocols.
 * User: migra
 * Date: Aug 10, 2005
 */
public class Lsid
{
    private final String src;
    private final String authority;
    private final String namespace;
    private final String objectId;
    private final String version;
    private final boolean valid;
    private final int hashCode;


    /**
     *  consider using Lsid.parse()
     */
    public Lsid(String s)
    {
        LsidBuilder b = new LsidBuilder(s);
        this.src = b.src;
        this.authority = b.getAuthority();
        this.namespace = b.getNamespace();
        this.objectId = StringUtils.defaultString(b.getObjectId(),"");
        this.version = b.getVersion();
        this.valid = b.valid;
        this.hashCode = this.toString().hashCode();
    }


    /**
     * Assumes that the separate parts are not encoded
     *
     * Consider using LsidBuilder instead
     */
    public Lsid(String namespace, String objectId)
    {
        LsidBuilder b = new LsidBuilder()
                .setNamespace(namespace)
                .setObjectId(objectId);
        this.src = b.src;
        this.authority = b.getAuthority();
        this.namespace = b.getNamespace();
        this.objectId = StringUtils.defaultString(b.getObjectId(),"");
        this.version = b.getVersion();
        this.valid = true;
        this.hashCode = this.toString().hashCode();
    }

    /**
     * Assumes that the separate parts are not encoded
     *
     * Consider using LsidBuilder instead
     */
    public Lsid(String namespacePrefix, String namespaceSuffix, @NotNull String objectId)
    {
        LsidBuilder b = new LsidBuilder()
                .setNamespacePrefix(namespacePrefix)
                .setNamespaceSuffix(namespaceSuffix)
                .setObjectId(objectId);
        this.src = b.src;
        this.authority = b.getAuthority();
        this.namespace = b.getNamespace();
        this.objectId = StringUtils.defaultString(b.getObjectId(),"");
        this.version = b.getVersion();
        this.valid = true;
        this.hashCode = this.toString().hashCode();
    }


    private Lsid(String src, String authority, String namespace, String objectId, String version, boolean valid)
    {
        this.src = src;
        this.authority = authority;
        this.namespace = namespace;
        this.objectId = StringUtils.defaultString(objectId,"");
        this.version = version;
        this.valid = valid;
        this.hashCode = this.toString().hashCode();
    }

    // Keep in sync with getSqlExpressionToExtractObjectId() (below)
    private static final Pattern LSID_REGEX = Pattern.compile("(?i)^urn:lsid:([^:]+):([^:]+):([^:]+)(?::(.*))?");

    // Keep in sync with LSID_REGEX (above)
    public static Pair<String, String> getSqlExpressionToExtractObjectId(String lsidExpression, SqlDialect dialect)
    {
        if (dialect.isPostgreSQL())
        {
            // PostgreSQL SUBSTRING supports simple regular expressions. This captures all the text from the third
            // colon to the end of the string (or to the fourth colon, if present).
            String expression = "SUBSTRING(" + lsidExpression + " FROM '%urn:lsid:%:#\"%#\":?%' FOR '#')";
            String where = lsidExpression + " SIMILAR TO '%urn:lsid:%:[0-9a-f\\-]{36}:?%'";

            return new Pair<>(expression, where);
        }

        if (dialect.isSqlServer())
        {
            // SQL Server doesn't support regular expressions; this uses an unwieldy pattern to extract the objectid
            String d = "[0-9a-f]"; // pattern for a single digit
            String objectId = repeat(d, 8) + "-" + repeat(d, 4) + "-" + repeat(d, 4) + "-" + repeat(d, 4) + "-" + repeat(d, 12);

            String expression = "SUBSTRING(" + lsidExpression + ", PATINDEX('%:" + objectId + "%', " + lsidExpression + ") + 1, 36)";
            String where = lsidExpression + " LIKE '%urn:lsid:%:" + objectId + "%'";

            return new Pair<>(expression, where);
        }

        throw new IllegalStateException("Unsupported SqlDialect: " + dialect.getProductName());
    }

    public String getSrc()
    {
        return src;
    }

    public String getAuthority()
    {
        return authority;
    }

    public String getNamespace()
    {
        return namespace;
    }

    public String getNamespacePrefix()
    {
        int dotPos = namespace.indexOf('.');
        if (-1 == dotPos)
            return namespace;
        return namespace.substring(0, dotPos);
    }

    public String getNamespaceSuffix()
    {
        int dotPos = namespace.indexOf('.');
        if (-1 == dotPos)
            return null;
        return namespace.substring(dotPos + 1);
    }

    public String getObjectId()
    {
        return objectId;
    }

    public String getVersion()
    {
        return version;
    }

    public boolean isValid()
    {
        return valid;
    }

    public boolean equals(Object o)
    {
        if (null == o || !(o instanceof Lsid))
            return false;

        Lsid lsid = (Lsid) o;
        if (!valid)
            return StringUtils.equals(toString(),lsid.toString());

        return  StringUtils.equals(authority,lsid.authority) &&
                StringUtils.equals(namespace,lsid.namespace) &&
                StringUtils.equals(objectId, lsid.objectId) &&
                StringUtils.equals(version, lsid.version);
    }

    public int hashCode()
    {
        return hashCode;
    }

    /**
     * Converts this to a string. urn:lsid:authority prefix will always be lower case so may not
     * match original input. Use equals or canonical to be sure of equality.
     * <p>
     * TODO: Encoding is incorrect
     */
    public String toString()
    {
        if (!valid)
            return StringUtils.defaultString(src,"");

        String encodedAuthority = encodePart(authority);
        String encodedNamespace = encodePart(namespace);
        String encodedObjectId = encodePart(objectId);
        String encodedVersion = StringUtils.isEmpty(version) ? null : encodePart(version);

        String result = "urn:lsid:" + encodedAuthority + ":" + encodedNamespace + ":" + encodedObjectId + (null == encodedVersion ? "" : ":" + encodedVersion);
        // Need to not encode the last '#' since property descriptors use that in their URIs
        int index = result.lastIndexOf("%23");
        if (index != -1)
        {
            result = result.substring(0, index) + "#" + result.substring(index + "%23".length());
        }
        return result;
    }


    public static Lsid parse(String lsid)
    {
        return new LsidBuilder(lsid).build();
    }

    /**
     * URI encode a part of a URI. Colons will be encoded into %3A, for example
     */
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

    /**
     * URI decode a part of a URI.
     */
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

    public static boolean isLsid(String lsid)
    {
        return LSID_REGEX.matcher(lsid).matches();
    }

    public static String canonical(String lsid)
    {
        return new LsidBuilder(lsid).build().toString();
    }

    static public String namespaceLikeString(String namespace)
    {
        return "urn:lsid:%:" + namespace + ".%:%";
    }

    static public String namespaceFilter(String columnName, String namespace)
    {
        return columnName + " LIKE '" + namespaceLikeString(namespace) + "'";
    }

    /**
     * Handle an unencoded % in the property name part of a property URI (after the hash) by encoding it, if needed
     */
    public static
    @Nullable
    String fixupPropertyURI(@Nullable String uri)
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


    public static class LsidBuilder implements Builder
    {
        //A fairly loose pattern.
        //Matches urn:lsid:authority:namespace:id or urn:lsid:authority:namespace:id:version
        protected String src = null;
        protected String authority = "";
        protected String namespace = "";
        protected String objectId = "";
        protected String version = null;
        protected boolean valid = false;
        private String prefix = "";
        private String suffix = "";

        /**
         * Assume a fully qualified, correctly encoded LSID
         */
        public LsidBuilder(String lsid)
        {
            src = lsid;
            Matcher m = LSID_REGEX.matcher(lsid);
            if (!m.matches())
            {
                valid = false;
                return;
            }
            authority = decodePart(m.group(1).toLowerCase());
            namespace = decodePart(m.group(2));
            objectId = decodePart(m.group(3));
            version = decodePart(m.group(4));
            valid = true;
            resetPrefix();
        }

        public LsidBuilder(Lsid lsid)
        {
            this.authority = lsid.authority;
            this.namespace = lsid.namespace;
            this.objectId = lsid.objectId;
            this.version = lsid.version;
            this.valid = lsid.valid;
            this.resetPrefix();
        }

        public LsidBuilder()
        {
            this.authority = AppProps.getInstance().getDefaultLsidAuthority();
            this.valid = true;
        }

        /**
         * Assumes that the separate parts are not encoded
         */
        public LsidBuilder(String namespace, String objectId)
        {
            this(namespace, objectId, AppProps.getInstance());
        }

        private LsidBuilder(String namespace, String objectId, AppProps appProps)
        {
            valid = true;
            this.authority = appProps.getDefaultLsidAuthority();
            this.namespace = namespace;
            this.objectId = objectId;
            resetPrefix();
        }

        /**
         * Assumes that the separate parts are not encoded
         */
        public LsidBuilder(String namespacePrefix, String namespaceSuffix, @NotNull String objectId)
        {
            this(namespacePrefix, namespaceSuffix, objectId, AppProps.getInstance());
        }

        private LsidBuilder(String namespacePrefix, String namespaceSuffix, String objectId, AppProps appProps)
        {
            valid = true;
            this.authority = appProps.getDefaultLsidAuthority();
            this.namespace = namespacePrefix + "." + namespaceSuffix;
            this.objectId = objectId;
            this.prefix = namespacePrefix;
            this.suffix = namespaceSuffix;
        }

        @Override
        public Lsid build()
        {
            return new Lsid(src, getAuthority(), getNamespace(), getObjectId(), getVersion(), valid);
        }

        /**
         * Does lsid meet our (quite loose) requirements on what an LSID looks like
         * We do not care whether characters between : are actually legal or even if
         * the lsid is a valid URI.
         */


        /**
         * Converts this to a string. urn:lsid:authority prefix will always be lower case so may not
         * match original input. Use equals or canonical to be sure of equality.
         * <p>
         * TODO: Encoding is incorrect
         */
        public String toString()
        {
            if (!valid)
                return src;

            String encodedAuthority = encodePart(getAuthority());
            String encodedNamespace = encodePart(getNamespace());
            String encodedObjectId = encodePart(getObjectId());
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
            return StringUtils.defaultString(authority,"");
        }

        public LsidBuilder setAuthority(String authority)
        {
            this.authority = authority;
            return this;
        }

        public String getNamespace()
        {
            return StringUtils.defaultString(namespace, "");
        }

        public String getObjectId()
        {
            return StringUtils.defaultString(objectId, "");
        }

        public LsidBuilder setObjectId(String objectId)
        {
            this.objectId = objectId;
            return this;
        }

        public String getVersion()
        {
            return version;
        }

        public LsidBuilder setVersion(String version)
        {
            this.version = version;
            return this;
        }


        public LsidBuilder setNamespace(String namespace)
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

        public LsidBuilder setNamespacePrefix(String prefix)
        {
            this.prefix = prefix;
            namespace = (prefix == null ? "" : prefix) + (suffix == null ? "" : ("." + suffix));
            return this;
        }

        public LsidBuilder setNamespaceSuffix(String suffix)
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
    }


    public static class TestCase extends Assert
    {
        private static final String DEFAULT_LSID_AUTHORITY = "server.test";

        private static AppProps _mockAppProps;

        @BeforeClass
        public static void setUp()
        {
            Mockery context = new Mockery();
            _mockAppProps = context.mock(AppProps.class);
            context.checking(new Expectations()
            {{
                allowing(_mockAppProps).getDefaultLsidAuthority();
                will(returnValue(DEFAULT_LSID_AUTHORITY));
            }});
        }

        @Test
        public void testSimpleDecode()
        {
            Lsid simpleLsid = Lsid.parse("urn:lsid:labkey.com:SampleSet.Folder-4:ReproSet");
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
            Lsid simpleLsid = new LsidBuilder("SampleSet.Folder-4", "ReproSet", _mockAppProps).build();
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
            Lsid lsid = Lsid.parse("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%3ASet");
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
            Lsid lsid = new LsidBuilder("SampleSet.Folder-4", "Repro:Set", _mockAppProps).build();
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
            Lsid lsid = Lsid.parse("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%25Set");
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
            Lsid lsid = Lsid.parse("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%20Set");
            assertEquals("urn:lsid:labkey.com:SampleSet.Folder-4:Repro+Set", lsid.toString());
            assertEquals("labkey.com", lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());

            lsid = Lsid.parse("urn:lsid:labkey.com:SampleSet.Folder-4:Repro+Set");
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
            Lsid lsid = new LsidBuilder("SampleSet.Folder-4", "Repro Set", _mockAppProps).build();
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
            Lsid lsid = Lsid.parse("urn:lsid:labkey.com:SampleSet.Folder-4:Repro%2BSet");
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
            Lsid lsid = new LsidBuilder("SampleSet.Folder-4", "Repro+Set", _mockAppProps).build();
            assertEquals("urn:lsid:" + DEFAULT_LSID_AUTHORITY + ":SampleSet.Folder-4:Repro%2BSet", lsid.toString());
            assertEquals(DEFAULT_LSID_AUTHORITY, lsid.getAuthority());
            assertEquals("SampleSet", lsid.getNamespacePrefix());
            assertEquals("Folder-4", lsid.getNamespaceSuffix());
            assertEquals("Repro+Set", lsid.getObjectId());
            assertEquals(null, lsid.getVersion());
        }

        @Test
        public void testBuilder()
        {
            Lsid.LsidBuilder b = new Lsid.LsidBuilder()
                    .setNamespacePrefix("prefix")
                    .setNamespaceSuffix("suffix")
                    .setObjectId("id")
                    .setVersion("1");
            Lsid lsid1 = b.build();
            Lsid lsid2 = b.build();
            assertEquals(lsid1.hashCode(), lsid2.hashCode());
            assertEquals(lsid1,lsid2);
            Lsid lsid3 = b.setVersion("3").build();
            assertEquals(lsid1,lsid2);
            assertNotEquals(lsid1,lsid3);
            assertEquals(b.toString(), lsid3.toString());
            Lsid lsid4 = b.setObjectId("OBJ").build();

            Lsid.LsidBuilder t = new Lsid.LsidBuilder(lsid1);
            assertEquals(lsid1,t.build());
            assertEquals(lsid1.toString(),t.toString());
            t.setVersion("3");
            assertEquals(lsid3,t.build());
            assertEquals(lsid3.toString(), t.toString());
            t.setObjectId("OBJ");
            assertEquals(lsid4,t.build());
            assertEquals(lsid4.toString(), t.toString());
        }

        @Test
        public void testFixupPropertyURI()
        {
            assertEquals("urn:lsid:labkey.com:AssayDomain-Run.Folder-18698:WithPercent#%25IDs", fixupPropertyURI("urn:lsid:labkey.com:AssayDomain-Run.Folder-18698:WithPercent#%IDs"));
            assertEquals("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#Bad%25Name", fixupPropertyURI("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#Bad%Name"));
            assertEquals("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#TargetStudy", fixupPropertyURI("urn:lsid:labkey.com:AssayDomain-Batch.Folder-18698:WithPercent#TargetStudy"));
        }
    }
}