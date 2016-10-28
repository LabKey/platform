/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
package org.labkey.api.exp.xar;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.security.UserManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: phussey
 * Date: Aug 17, 2005
 * Time: 10:54:53 AM
 */
public class LsidUtils
{
    private static Logger _log = Logger.getLogger(LsidUtils.class);

    private static Pattern REPLACEMENT_PATTERN = Pattern.compile("\\Q${\\E(.*?)\\Q}\\E");

    private static MapReplacer createMapReplacer(XarContext context, String declaredType, String baseType)
    {
        MapReplacer replacer = new MapReplacer(context.getSubstitutions());

        //todo:  check if there is an lsid handler for declared type.  this is a dummy example
        String nsPrefix;
        if (declaredType.equals("Fraction"))
            nsPrefix = declaredType;
        else
            nsPrefix = baseType;

        replacer.addReplacement("LSIDNamespace.Prefix", nsPrefix);

        String nsSuffix = replacer.getReplacement("LSIDNamespace.Suffix");
        if (nsSuffix == null)
        {
            replacer.addReplacement("LSIDNamespace", nsPrefix);
        }
        else
        {
            replacer.addReplacement("LSIDNamespace", nsPrefix + "." + nsSuffix);
        }
        return replacer;
    }


    public static String resolveLsidFromTemplate(String lsidTemplate,
                                                 XarContext context,
                                                 String declaredType,
                                                 String baseType) throws XarFormatException
    {
        Replacer replacer = createMapReplacer(context, declaredType, baseType);
        return resolveLsidFromTemplate(lsidTemplate, replacer);
    }

    public static String resolveLsidFromTemplate(String lsidTemplate,
                                                 XarContext context,
                                                 String declaredType,
                                                 String baseType, Replacer supplementalReplacer) throws XarFormatException
    {
        Replacer replacer = createMapReplacer(context, declaredType, baseType);
        return resolveLsidFromTemplate(lsidTemplate, new Replacer.CompoundReplacer(replacer, supplementalReplacer));
    }

    private static String resolveLsidFromTemplate(String lsidTemplate, Replacer replacer) throws XarFormatException
    {
        String resolvedLsid = resolveStringFromTemplate(lsidTemplate, replacer);
        if (resolvedLsid == null)
        {
            return null;
        }
        if (!resolvedLsid.toLowerCase().startsWith("urn:lsid:"))
            resolvedLsid = "urn:lsid:" + resolvedLsid;
        return Lsid.canonical(resolvedLsid);
    }

    private static String resolveStringFromTemplate(String template, Replacer replacer) throws XarFormatException
    {
        if (template == null)
        {
            return null;
        }

        template = template.trim();
        String originalTemplate = template;
        String resolved;
        boolean changed;

        // Need to allow templates to expand into other templates
        // Prevent us from replacing '${Value1}' with '${Value2}', then '${Value2}' with
        // '${Value1}' over and over again
        int replacementCount = 0;
        do
        {
            replacementCount++;
            if (replacementCount == 100)
            {
                throw new XarFormatException("Infinite replacement in template " + originalTemplate);
            }

            if (template.contains("${"))
            {
                resolved = doReplacements(template, replacer);
                changed = !resolved.equals(template);
            }
            else
            {
                changed = false;
                resolved = template;
            }
            template = resolved;
        }
        while (changed);

        return resolved;
    }


    private static String doReplacements(String template, Replacer replacer) throws XarFormatException
    {
        Matcher matcher = REPLACEMENT_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find())
        {
            String placeholder = matcher.group(1);
            String replacement = replacer.getReplacement(placeholder);
            if (replacement != null)
            {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            else
            {
                throw new XarFormatException("LSID template was not fully resolved. '" + template + "' was incompletely resolved, as '" + placeholder + "' could not be resolved");
            }
        }
        matcher.appendTail(sb);
        String result = sb.toString();

        if (!template.equals(result))
            _log.debug("template name " + template + " resolved to " + result);

        return result;
    }

    public static void addLsidPartsToMap(String inputLSID, XarContext context)
    {
        if (null != inputLSID)
        {
            Lsid pInputLSID = new Lsid(inputLSID);
            context.addSubstitution("InputLSID.objectid", pInputLSID.getObjectId());
            context.addSubstitution("InputLSID.version", pInputLSID.getVersion());
            context.addSubstitution("InputLSID.authority", pInputLSID.getAuthority());
            context.addSubstitution("InputLSID.namespace", pInputLSID.getNamespace());
            context.addSubstitution("InputLSID.namespacePrefix", pInputLSID.getNamespacePrefix());
            context.addSubstitution("InputLSID.namespaceSuffix", pInputLSID.getNamespaceSuffix());
            context.addSubstitution("InputLSID", pInputLSID.toString());
        }
    }

    public static String resolveNameFromTemplate(String template, XarContext context, Replacer replacer) throws XarFormatException
    {
        return resolveStringFromTemplate(template, new Replacer.CompoundReplacer(new MapReplacer(context.getSubstitutions()), replacer));
    }

    public static String resolveLsidFromTemplate(String lsidTemplate, XarContext context, String declaredType) throws XarFormatException
    {
        return resolveLsidFromTemplate(lsidTemplate, context, declaredType, declaredType);
    }

    public static String resolveLsidFromTemplate(String lsidTemplate, XarContext context, String declaredType, Replacer supplementalReplacer) throws XarFormatException
    {
        if (lsidTemplate == null)
        {
            return null;
        }
        Replacer mapReplacer = createMapReplacer(context, declaredType, declaredType);

        return resolveLsidFromTemplate(lsidTemplate, new Replacer.CompoundReplacer(mapReplacer, supplementalReplacer));
    }

    public static String resolveLsidFromTemplate(String template, XarContext context) throws XarFormatException
    {
        return resolveLsidFromTemplate(template, context, "${Fail}");
    }

    public static class TestCase extends Assert
    {
        private XarContext _context;

        @Before
        public void setUp() throws Exception
        {
            _context = new XarContext("TestCase", ContainerManager.createMockContainer(), UserManager.getGuestUser(), "localhost");
            _context.addSubstitution("Value1", "One");
            _context.addSubstitution("Value2", "Two");
            _context.addSubstitution("Reference1", "${Value1}");
            _context.addSubstitution("Infinite1", "${Infinite2}");
            _context.addSubstitution("Infinite2", "${Infinite1}");
        }

        @Test
        public void testInfiniteSubstitution()
        {
            try
            {
                resolveLsidFromTemplate("${Infinite1}", _context, "Test", "Test");
                fail("Should have gotten an exception");
            }
            catch (XarFormatException e)
            {
            }
        }

        @Test
        public void testDoubleSubstitution() throws XarFormatException
        {
            String result = resolveLsidFromTemplate("urn:lsid:${Reference1}", _context, "Test", "Test");
            assertEquals("urn:lsid:One", result);
        }

        @Test
        public void testInvalidSubstitution() throws XarFormatException
        {
            try
            {
                resolveLsidFromTemplate("${InvalidSub}", _context, "Test", "Test");
                fail("Should have gotten an exception");
            }
            catch (XarFormatException e)
            {
                assertTrue(e.getMessage().indexOf("not fully resolved") != -1);
            }
        }
    }
}
