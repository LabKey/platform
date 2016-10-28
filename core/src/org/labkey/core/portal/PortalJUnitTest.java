/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.core.portal;

import org.apache.commons.collections4.MultiValuedMap;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.WebPartFactory;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * User: marki
 * Date: May 31, 2011
 * Time: 3:18:15 PM
 */
@TestWhen(TestWhen.When.BVT)
public class PortalJUnitTest extends Assert
{
    private static final String _testDirName = "/_jUnitPortal";

    @Test
    public void testPortal() throws SQLException
    {
        User user = TestContext.get().getUser();
        assertTrue(null != user);

        // clean up if anything was left over from last time
        if (null != ContainerManager.getForPath(_testDirName))
            ContainerManager.deleteAll(ContainerManager.getForPath(_testDirName), user);

        Container proj = ContainerManager.ensureContainer(_testDirName);
        Container folder = ContainerManager.ensureContainer(_testDirName + "/Test");

        List<WebPart> defaultWebParts = Portal.getParts(folder);
        assertTrue(defaultWebParts.isEmpty());

        WebPartFactory wikiFactory = Portal.getPortalPart("Wiki");
        assertNotNull(wikiFactory);
        WebPartFactory searchFactory = Portal.getPortalPart("Search");
        assertNotNull(searchFactory);
        WebPartFactory pagesFactory = Portal.getPortalPart("Wiki Table of Contents");
        assertNotNull(pagesFactory);

        Portal.addPart(folder, wikiFactory, "body");
        Portal.addPart(folder, searchFactory, "body");
        Portal.addPart(folder, pagesFactory, WebPartFactory.LOCATION_RIGHT);

        List<WebPart> parts = Portal.getParts(folder);
        assertEquals(parts.size(), 3);

        MultiValuedMap<String, WebPart> locMap = Portal.getPartsByLocation(parts);
        WebPart[] bodyParts = locMap.get("body").toArray(new WebPart[locMap.get("body").size()]);
        assertEquals(bodyParts.length, 2);
        assertEquals(parts.get(0).getName(), "Wiki");
        assertEquals(parts.get(1).getName(), "Search");

        WebPart[] rightParts = locMap.get(WebPartFactory.LOCATION_RIGHT).toArray(new WebPart[locMap.get(WebPartFactory.LOCATION_RIGHT).size()]);
        assertEquals(1, rightParts.length);
        assertEquals("Wiki Table of Contents", rightParts[0].getName());

        //Delete a part
        List<WebPart> modifiedParts = new LinkedList<>();
        modifiedParts.add(parts.get(0));
        modifiedParts.add(parts.get(2));
        Portal.saveParts(folder, modifiedParts);

        parts = Portal.getParts(folder);
        assertEquals(parts.size(), 2);
        locMap = Portal.getPartsByLocation(parts);
        bodyParts = locMap.get("body").toArray(new WebPart[locMap.get("body").size()]);
        assertEquals(bodyParts.length, 1);
        assertEquals(parts.get(0).getName(), "Wiki");

        rightParts = locMap.get(WebPartFactory.LOCATION_RIGHT).toArray(new WebPart[locMap.get(WebPartFactory.LOCATION_RIGHT).size()]);
        assertEquals(rightParts.length, 1);
        assertEquals(rightParts[0].getName(), "Wiki Table of Contents");

        ///Now add it back at a specific position
        Portal.addPart(folder, searchFactory, "body", 0);
        parts = Portal.getParts(folder);
        assertEquals(parts.size(), 3);
        locMap = Portal.getPartsByLocation(parts);
        bodyParts = locMap.get("body").toArray(new WebPart[locMap.get("body").size()]);
        assertEquals(bodyParts.length, 2);
        assertEquals(parts.get(0).getName(), "Search");

        //Create some parts on a new page
        String newPageGuid = GUID.makeGUID();
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals(parts.size(), 0);
        Portal.addPart(folder, newPageGuid, wikiFactory, "body", -1, PageFlowUtil.map("pageName", "testPage"));
        //Make sure we have a part on our new page
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals(parts.size(), 1);
        Map<String,String> props = parts.get(0).getPropertyMap();
        assertEquals(props.get("pageName"), "testPage");
        Portal.addPart(folder,  newPageGuid, searchFactory, "body", -1, null);
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals(parts.size(), 2);
        assertEquals(parts.get(0).getName(), "Wiki");
        assertEquals(parts.get(1).getName(), "Search");
        //Now swap the parts
        //Should come back in index order
        parts.get(0).setIndex(1);
        parts.get(1).setIndex(0);
        Portal.saveParts(folder, newPageGuid, parts);
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals(parts.get(0).getName(), "Search");
        assertEquals(parts.get(1).getName(), "Wiki");

        //Check to see that the old page is still the same length
        parts = Portal.getParts(folder);
        assertEquals(parts.size(), 3);

        // clean up
        ContainerManager.deleteAll(proj, user);

        //
        parts = Portal.getParts(folder);
        assertEquals(parts.size(), 0);
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals(parts.size(), 0);
    }
}
