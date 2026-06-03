/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.query.olap.metadata;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.query.FieldKey;
import org.olap4j.impl.Named;
import org.olap4j.metadata.MetadataElement;

import java.util.Collections;
import java.util.Map;

/**
* Created by matthew on 9/4/14.
*/
public abstract class MetadataElementBase implements MetadataElement, Named
{
    final UniqueName uniqueName;
    final String name;                        // name is _usually_, but not always == uniqueName.getName()
    Map<String, String> annotations = null;


    MetadataElementBase(@Nullable CachedCube cc, MetadataElement mde, MetadataElementBase parent)
    {
        if (null == parent)
            this.uniqueName = UniqueName.parse(cc, mde.getUniqueName());
        else
            this.uniqueName = new UniqueName(parent.uniqueName, null==cc ? mde.getName() : cc.intern(mde.getName()));

        // NOTE Properties don't always use the [ ] syntax
        assert Strings.CI.equals(uniqueName.toString(), mde.getUniqueName())
                || null==uniqueName.getParent() && Strings.CI.equals(getName(), mde.getName());

        name = uniqueName.getName().equals(mde.getName()) ? uniqueName.getName() : mde.getName();
    }


    MetadataElementBase(@Nullable CachedCube cc, String name, MetadataElementBase parent)
    {
        if (null == parent)
            this.uniqueName = new UniqueName(null, name);
        else
            this.uniqueName = new UniqueName(parent.uniqueName,name);
        this.name = uniqueName.getName();
    }


    @Override
    public String getName()
    {
        return null != name ? name : uniqueName.getName();
    }

    @Override
    public String getUniqueName()
    {
        return uniqueName.toString();
    }

    @Override
    public String getCaption()
    {
        return getName();
    }

    @Override
    public String getDescription()
    {
        return getName();
    }

    @Override
    public boolean isVisible()
    {
        return true;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " " + getUniqueName();
    }


    public Map<String, String> getAnnotationMap()
    {
        return null==annotations ? (Map<String,String>)(Map)Collections.emptyMap() : annotations;
    }


    public static class UniqueName extends FieldKey
    {
        UniqueName(UniqueName p, String name)
        {
            super(p,name);
        }

        static UniqueName parse(String s)
        {
            return parse(null, s);
        }

        static UniqueName parse(@Nullable CachedCube cc, String s)
        {
             // Split is so unhelpful split(\\]\\.\\]) doesn't work
             String[] parts = StringUtils.split(s,'.');
             UniqueName u = null;
             String p = "";
             for (int i=0 ; i<parts.length ; i++)
             {
                 p += parts[i];
                 if (p.endsWith("]"))
                 {
                     String n = p.substring(1, p.length() - 1);
                     u = new UniqueName(u, null==cc ? n : cc.intern(n));
                     p = "";
                 }
                 else if (i < parts.length-1)
                     p += ".";
             }
             if (StringUtils.isNotEmpty(p))
             {
                 if (p.startsWith("[") && p.endsWith("]"))
                     p = p.substring(1,p.length()-1);
                 u = new UniqueName(u, null==cc ? p : cc.intern(p));
             }
             return u;
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            toStringBuilder(sb);
            return sb.toString();
        }

        private void toStringBuilder(StringBuilder sb)
        {
            if (null == getParent())
            {
                sb.append("[").append(getName()).append("]");
            }
            else
            {
                ((UniqueName)getParent()).toStringBuilder(sb);
                sb.append(".[").append(getName()).append("]");
            }
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testUniqueName()
        {
            assertEquals(new UniqueName(null,"PropertyName"), UniqueName.parse("PropertyName"));
            assertEquals("PropertyName", UniqueName.parse("PropertyName").getName());

            assertEquals(new UniqueName(null, "a"), UniqueName.parse("[a]"));
            assertEquals("[a]", UniqueName.parse("[a]").toString());

            assertEquals(new UniqueName(null, "a.b"), UniqueName.parse("[a.b]"));
            assertEquals("[a.b]", UniqueName.parse("[a.b]").toString());

            assertEquals(new UniqueName(new UniqueName(null, "a"), "b"), UniqueName.parse("[a].[b]"));
            assertEquals("[a].[b]", UniqueName.parse("[a].[b]").toString());

            assertEquals(new UniqueName(new UniqueName(new UniqueName(null, "a"), "b"), "c"), UniqueName.parse("[a].[b].[c]"));
            assertEquals("[a].[b].[c]", UniqueName.parse("[a].[b].[c]").toString());

            assertEquals(new UniqueName(new UniqueName(null, "a.b"), ""), UniqueName.parse("[a.b].[]"));
            assertEquals("[a.b].[]", UniqueName.parse("[a.b].[]").toString());
        }
    }
}
