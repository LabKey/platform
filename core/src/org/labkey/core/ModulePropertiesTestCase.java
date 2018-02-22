/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.core;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
* User: adam
* Date: 12/1/12
* Time: 10:03 AM
*/
@TestWhen(TestWhen.When.BVT)
public class ModulePropertiesTestCase extends Assert
{
    private User _user;
    Module _module;
    Container _project;
    Container _subFolder;
    String PROP1 = "TestProp";
    String PROP2 = "TestPropContainer";
    String PROJECT_NAME = "__ModulePropsTestProject";
    String FOLDER_NAME = "subfolder";

    String LOOKANDFEEL_SYSTEM_DESCRIPTION = "Test System Description";
    String SITESETTINGS_MAX_BLOB_SIZE = "12345";

    @Before
    public void setUp()
    {
        TestContext ctx = TestContext.get();
        User loggedIn = ctx.getUser();
        assertTrue("login before running this test", null != loggedIn);
        assertFalse("login before running this test", loggedIn.isGuest());
        _user = ctx.getUser().cloneUser();

        _module = new TestModule();
        ((TestModule)_module).init();
    }

    /**
     * Make sure module properties can be set, and that the correct coalesced values is returned (ie. if value not set on
     * a container, coalesce backwards to the first parent container where the value is set).
     */
    @Test
    public void testModuleProperties() throws Exception
    {
        if (ContainerManager.getForPath(PROJECT_NAME) != null)
        {
            ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
        }

        _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, Container.TYPE.normal, _user);
        _subFolder = ContainerManager.createContainer(_project, FOLDER_NAME);

        Map<String, ModuleProperty> props = _module.getModuleProperties();
        ModuleProperty prop2 = props.get(PROP2);
        User saveUser = PropertyManager.SHARED_USER;

        String rootVal = "RootValue";
        String projectVal = "ProjectValue";
        String folderVal = "FolderValue";

        prop2.saveValue(_user, ContainerManager.getRoot(), rootVal);
        prop2.saveValue(_user, _project, projectVal);
        prop2.saveValue(_user, _subFolder, folderVal);

        assertEquals(rootVal, prop2.getEffectiveValue(ContainerManager.getRoot()));
        assertEquals(projectVal, prop2.getEffectiveValue(_project));
        assertEquals(folderVal, prop2.getEffectiveValue(_subFolder));

        prop2.saveValue(_user, _subFolder, null);
        assertEquals(projectVal, prop2.getEffectiveValue(_subFolder));

        prop2.saveValue(_user, _project, null);
        assertEquals(rootVal, prop2.getEffectiveValue(_subFolder));

        prop2.saveValue(_user, ContainerManager.getRoot(), null);
        assertEquals(prop2.getDefaultValue(), prop2.getEffectiveValue(_subFolder));

        String newVal = "NewValue";
        prop2.saveValue(_user, _project, newVal);
        assertEquals(prop2.getDefaultValue(), prop2.getEffectiveValue(ContainerManager.getRoot()));
        assertEquals(newVal, prop2.getEffectiveValue(_project));
        assertEquals(newVal, prop2.getEffectiveValue(_subFolder));

        ContainerManager.deleteAll(_project, _user);
    }

    /**
     * Test that the Look And Feel settings can be configured from startup properties
     */
    @Test
    public void testStartupPropertiesForLookAndFeel() throws Exception
    {
        // ensure that the site wide ModuleLoader has test startup property values in the _configPropertyMap
        prepareTestStartupProperties();

        // save the original Look And Feel server settings so that we can restore them when this test is done
        LookAndFeelProperties lookAndFeelProps = LookAndFeelProperties.getInstance(ContainerManager.getRoot());
        String originalSystemDescription = lookAndFeelProps.getDescription();

        // call the method that makes use of the test startup properties to change the Look And Feel settings on the server
        WriteableLookAndFeelProperties.populateLookAndFeelWithStartupProps();

        // now check that the expected changes occured to the Look And Feel settings on the server
        String newSystemDescription = lookAndFeelProps.getDescription();
        assertEquals("The expected change in Look And Feel settings was not found", LOOKANDFEEL_SYSTEM_DESCRIPTION, newSystemDescription);

        // restore the Look And Feel server settings to how they were originally
        WriteableLookAndFeelProperties writeablelookAndFeelProps = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
        writeablelookAndFeelProps.setSystemDescription(originalSystemDescription);
        writeablelookAndFeelProps.save();
    }

    /**
     * Test that the Site Settings can be configured from startup properties
     */
    @Test
    public void testStartupPropertiesForSiteSettings() throws Exception
    {
        // ensure that the site wide ModuleLoader had test startup property values in the _configPropertyMap
        prepareTestStartupProperties();

        // save the original Site Settings server settings so that we can restore them when this test is done
        AppProps siteSettingsProps = AppProps.getInstance();
        int originalMaxBlobSize = siteSettingsProps.getMaxBLOBSize();

        // call the method that makes use of the test startup properties to change the Look And Feel settings on the server
        WriteableAppProps.populateSiteSettingsWithStartupProps();

        // now check that the expected changes occured to the Site Settings settings on the server
        int newMaxBlobSize = siteSettingsProps.getMaxBLOBSize();
        assertEquals("The expected change in Site Settings was not found", SITESETTINGS_MAX_BLOB_SIZE, Integer.toString(newMaxBlobSize));

        // restore the Look And Feel server settings to how they were originally
        WriteableAppProps writeableSiteSettingsProps = AppProps.getWriteableInstance();
        writeableSiteSettingsProps.setMaxBLOBSize(originalMaxBlobSize);
        writeableSiteSettingsProps.save(_user);
    }

    private class TestModule extends CodeOnlyModule
    {
        @Override
        public void doStartup(ModuleContext c)
        {
        }

        @Override
        public void init()
        {
            setName("__JunitTestModule");
            ModuleProperty mp = new ModuleProperty(this, PROP1);
            mp.setCanSetPerContainer(false);
            addModuleProperty(mp);

            ModuleProperty mp2 = new ModuleProperty(this, PROP2);
            mp2.setCanSetPerContainer(true);
            mp2.setDefaultValue("Default");
            addModuleProperty(mp2);
        }

        @NotNull
        @Override
        protected Collection<WebPartFactory> createWebPartFactories()
        {
            return new HashSet<>();
        }
    }

    private void prepareTestStartupProperties()
    {
        // prepare a multimap of config properties to test with that has properties assigned for several scopes
        MultiValuedMap<String, ConfigProperty> testConfigPropertyMap = new HashSetValuedHashMap<>();

        // prepare test Look And Feel properties
        ConfigProperty testLookAndFeelProp1 =  new ConfigProperty("systemDescription", LOOKANDFEEL_SYSTEM_DESCRIPTION, "startup", ConfigProperty.SCOPE_LOOK_AND_FEEL_SETTINGS);
        testConfigPropertyMap.put(ConfigProperty.SCOPE_LOOK_AND_FEEL_SETTINGS, testLookAndFeelProp1);

        // prepare test Site Settings properties
        ConfigProperty testSiteSettingsProp1 =  new ConfigProperty("maxBLOBSize", SITESETTINGS_MAX_BLOB_SIZE, "startup", ConfigProperty.SCOPE_SITE_SETTINGS);
        testConfigPropertyMap.put(ConfigProperty.SCOPE_SITE_SETTINGS, testSiteSettingsProp1);

        // set these test startup test properties to be used by the entire server
        ModuleLoader.getInstance().setConfigProperties(testConfigPropertyMap);
    }

}
