/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public void testPortal()
    {
        User user = TestContext.get().getUser();
        assertNotNull(user);

        // clean up if anything was left over from last time
        if (null != ContainerManager.getForPath(_testDirName))
            ContainerManager.deleteAll(ContainerManager.getForPath(_testDirName), user);

        Container proj = ContainerManager.ensureContainer(_testDirName);
        Container folder = ContainerManager.ensureContainer(_testDirName + "/Test");

        List<WebPart> defaultWebParts = Portal.getParts(folder);
        assertTrue(defaultWebParts.isEmpty());

        WebPartFactory wikiFactory = Portal.getPortalPart("Wiki");
        assertNotNull(wikiFactory);
        WebPartFactory filesFactory = Portal.getPortalPart("Files");
        assertNotNull(filesFactory);
        WebPartFactory pagesFactory = Portal.getPortalPart("Wiki Table of Contents");
        assertNotNull(pagesFactory);

        final String location_body = "body";
        Portal.addPart(folder, wikiFactory, location_body);
        Portal.addPart(folder, filesFactory, location_body);
        Portal.addPart(folder, pagesFactory, WebPartFactory.LOCATION_RIGHT);

        List<WebPart> parts = Portal.getParts(folder);
        assertEquals(parts.size(), 3);

        assertEquals("Wrong body webparts", Arrays.asList("Wiki", "Files"), getWebPartNames(location_body, parts));
        assertEquals("Wrong side webparts", Arrays.asList("Wiki Table of Contents"), getWebPartNames(WebPartFactory.LOCATION_RIGHT, parts));

        //Delete a part
        List<WebPart> modifiedParts = new LinkedList<>();
        modifiedParts.add(parts.get(0));
        modifiedParts.add(parts.get(2));
        Portal.saveParts(folder, modifiedParts);

        parts = Portal.getParts(folder);
        assertEquals(parts.size(), 2);
        assertEquals("Wrong body webparts", Arrays.asList("Wiki"), getWebPartNames(location_body, parts));
        assertEquals("Wrong side webparts", Arrays.asList("Wiki Table of Contents"), getWebPartNames(WebPartFactory.LOCATION_RIGHT, parts));

        ///Now add it back at a specific position
        Portal.addPart(folder, filesFactory, location_body, 0);
        parts = Portal.getParts(folder);
        assertEquals(parts.size(), 3);
        assertEquals("Wrong body webparts", Arrays.asList("Files", "Wiki"), getWebPartNames(location_body, parts));
        assertEquals("Wrong side webparts", Arrays.asList("Wiki Table of Contents"), getWebPartNames(WebPartFactory.LOCATION_RIGHT, parts));

        //Create some parts on a new page
        String newPageGuid = GUID.makeGUID();
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals(parts.size(), 0);
        Portal.addPart(folder, newPageGuid, wikiFactory, location_body, -1, PageFlowUtil.map("pageName", "testPage"));
        //Make sure we have a part on our new page
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals(parts.size(), 1);
        Map<String,String> props = parts.get(0).getPropertyMap();
        assertEquals(props.get("pageName"), "testPage");
        Portal.addPart(folder,  newPageGuid, filesFactory, location_body, -1, null);
        parts = Portal.getEditableParts(folder, newPageGuid);
        assertEquals(parts.size(), 2);
        assertEquals("Wrong body webparts", Arrays.asList("Wiki", "Files"), getWebPartNames(location_body, parts));
        //Now swap the parts
        //Should come back in index order
        parts.get(0).setIndex(1);
        parts.get(1).setIndex(0);
        Portal.saveParts(folder, newPageGuid, parts);
        parts = Portal.getParts(folder, newPageGuid);
        assertEquals("Wrong body webparts", Arrays.asList("Files", "Wiki"), getWebPartNames(location_body, parts));

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

    private List<String> getWebPartNames(String body, List<WebPart> parts)
    {
        MultiValuedMap<String, WebPart> lfocMap = Portal.getPartsByLocation(parts);
        List<String> bodyParts;
        bodyParts = lfocMap.get(body).stream().map(WebPart::getName).collect(Collectors.toList());
        return bodyParts;
    }
}
