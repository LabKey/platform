/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.audit.provider;

import jakarta.servlet.ServletContext;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.VersionNumber;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Records one event each time the server comes up running a different release version or build than the previous boot.
 * Written by the startup listener registered in ApiModule.doStartup, which runs after the audit providers have been
 * initialized.
 */
public class SystemUpgradeAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    private static final Logger LOG = LogHelper.getLogger(SystemUpgradeAuditProvider.class, "Recording of server version changes");

    public static final String AUDIT_EVENT_TYPE = "SystemUpgradeAuditEvent";

    public static final String COLUMN_NAME_RELEASE_VERSION = "ReleaseVersion";
    public static final String COLUMN_NAME_PREVIOUS_RELEASE_VERSION = "PreviousReleaseVersion";
    public static final String COLUMN_NAME_BUILD_TIME = "BuildTime";
    public static final String COLUMN_NAME_PREVIOUS_BUILD_TIME = "PreviousBuildTime";
    public static final String COLUMN_NAME_CHANGE_TYPE = "ChangeType";
    public static final String COLUMN_NAME_HAS_SCHEMA_UPGRADE = "HasSchemaUpgrade";
    public static final String COLUMN_NAME_HAS_EXTERNAL_SCHEMA_UPGRADE = "HasExternalSchemaUpgrade";

    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CHANGE_TYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PREVIOUS_RELEASE_VERSION));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_RELEASE_VERSION));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_BUILD_TIME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_HAS_SCHEMA_UPGRADE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_HAS_EXTERNAL_SCHEMA_UPGRADE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    public SystemUpgradeAuditProvider()
    {
        super(new SystemUpgradeAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "System Upgrade events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about changes to the server's release version and build.";
    }

    @Override
    public Class<SystemUpgradeAuditEvent> getEventClass()
    {
        return SystemUpgradeAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public enum ChangeType
    {
        /** First boot of a new installation */
        Install,
        /** Release version moved forward */
        Upgrade,
        /** Release version moved backward */
        Downgrade,
        /** Same release/snapshot version, different build */
        Rebuild,
        /** Baseline for a server that predates this feature, or a version string we can't parse */
        Unknown
    }

    /**
     * @return the change to record, or null if this boot matches the previous event and nothing should be recorded
     */
    static @Nullable ChangeType determineChangeType(@Nullable String prevVersion, @Nullable String prevBuildTime, @Nullable String newVersion, @Nullable String newBuildTime, boolean newInstall)
    {
        // No prior event: either a fresh install or an existing server picking up this feature. We don't backfill, so
        // the latter gets a baseline event with no direction.
        if (prevVersion == null && prevBuildTime == null)
            return newInstall ? ChangeType.Install : ChangeType.Unknown;

        if (Objects.equals(prevVersion, newVersion) && Objects.equals(prevBuildTime, newBuildTime))
            return null;

        Integer comparison = compareVersions(prevVersion, newVersion);

        if (comparison == null)
            return ChangeType.Unknown;
        if (comparison < 0)
            return ChangeType.Upgrade;
        if (comparison > 0)
            return ChangeType.Downgrade;

        return ChangeType.Rebuild;
    }

    /**
     * @return the sign of prevVersion compared to newVersion, or null if either is missing or unparseable
     */
    private static @Nullable Integer compareVersions(@Nullable String prevVersion, @Nullable String newVersion)
    {
        if (prevVersion == null || newVersion == null)
            return null;

        try
        {
            return new VersionNumber(prevVersion).compareTo(new VersionNumber(newVersion));
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    static String buildComment(ChangeType changeType, @Nullable String prevVersion, @Nullable String newVersion)
    {
        if (ChangeType.Install == changeType)
            return "Server installed at version " + newVersion;
        if (ChangeType.Rebuild == changeType)
            return "Server rebuilt at version " + newVersion;
        if (null == prevVersion)
            return "Server running version " + newVersion;

        return "Server version changed from " + prevVersion + " to " + newVersion;
    }

    /**
     * Must be registered from a Module.doStartup(), not init(). The provisioned audit table is created by
     * AuditLogImpl's own StartupListener, which registers itself from AuditModule.init(); registering here guarantees
     * we run after it, when the table exists and the queued events have been flushed.
     */
    public static class SystemUpgradeStartupListener implements StartupListener
    {
        @Override
        public String getName()
        {
            return "System Upgrade Audit";
        }

        @Override
        public void moduleStartupComplete(ServletContext servletContext)
        {
            try
            {
                recordVersionChange();
            }
            catch (Exception e)
            {
                // Treat a failure to record as a non-fatal startup problem
                LOG.error("Failed to record system upgrade audit event", e);
            }
        }
    }

    private static void recordVersionChange()
    {
        AuditLogService auditLog = AuditLogService.get();
        ModuleLoader moduleLoader = ModuleLoader.getInstance();

        if (!moduleLoader.shouldInsertData())
            return;

        // No audit module, so the service is DefaultAuditProvider, which discards whatever we hand it
        if (!auditLog.isViewable())
            return;

        User user = User.getAdminServiceUser();
        Container root = ContainerManager.getRoot();

        List<SystemUpgradeAuditEvent> priorEvents = auditLog.getAuditEvents(root, user, AUDIT_EVENT_TYPE, null, new Sort("-Created,-RowId"));
        SystemUpgradeAuditEvent prior = priorEvents.isEmpty() ? null : priorEvents.getFirst();

        String prevVersion = null != prior ? prior.getReleaseVersion() : null;
        String prevBuildTime = null != prior ? prior.getBuildTime() : null;
        String newVersion = AppProps.getInstance().getReleaseVersion();
        String newBuildTime = moduleLoader.getCoreModule().getBuildTime();

        ChangeType changeType = determineChangeType(prevVersion, prevBuildTime, newVersion, newBuildTime, moduleLoader.isNewInstall());

        // Same version and build as the last event: an ordinary restart
        if (null == changeType)
            return;

        SystemUpgradeAuditEvent event = new SystemUpgradeAuditEvent(root, buildComment(changeType, prevVersion, newVersion));
        event.setChangeType(changeType.name());
        event.setReleaseVersion(newVersion);
        event.setPreviousReleaseVersion(prevVersion);
        event.setBuildTime(newBuildTime);
        event.setPreviousBuildTime(prevBuildTime);
        event.setHasSchemaUpgrade(moduleLoader.hasSchemaUpgrade());
        event.setHasExternalSchemaUpgrade(moduleLoader.hasExternalSchemaUpgrade());

        auditLog.addEvent(user, event);
        LOG.info(event.getComment());
    }

    public static class SystemUpgradeAuditEvent extends AuditTypeEvent
    {
        private String _releaseVersion;
        private String _previousReleaseVersion;
        private String _buildTime;
        private String _previousBuildTime;
        private String _changeType;
        private boolean _hasSchemaUpgrade;
        private boolean _hasExternalSchemaUpgrade;

        /** Important for reflection-based instantiation */
        @SuppressWarnings("unused")
        public SystemUpgradeAuditEvent() {}

        public SystemUpgradeAuditEvent(Container container, String comment)
        {
            super(AUDIT_EVENT_TYPE, container, comment);
        }

        public String getReleaseVersion()
        {
            return _releaseVersion;
        }

        public void setReleaseVersion(String releaseVersion)
        {
            _releaseVersion = releaseVersion;
        }

        public String getPreviousReleaseVersion()
        {
            return _previousReleaseVersion;
        }

        public void setPreviousReleaseVersion(String previousReleaseVersion)
        {
            _previousReleaseVersion = previousReleaseVersion;
        }

        public String getBuildTime()
        {
            return _buildTime;
        }

        public void setBuildTime(String buildTime)
        {
            _buildTime = buildTime;
        }

        public String getPreviousBuildTime()
        {
            return _previousBuildTime;
        }

        public void setPreviousBuildTime(String previousBuildTime)
        {
            _previousBuildTime = previousBuildTime;
        }

        public String getChangeType()
        {
            return _changeType;
        }

        public void setChangeType(String changeType)
        {
            _changeType = changeType;
        }

        public boolean isHasSchemaUpgrade()
        {
            return _hasSchemaUpgrade;
        }

        public void setHasSchemaUpgrade(boolean hasSchemaUpgrade)
        {
            _hasSchemaUpgrade = hasSchemaUpgrade;
        }

        public boolean isHasExternalSchemaUpgrade()
        {
            return _hasExternalSchemaUpgrade;
        }

        public void setHasExternalSchemaUpgrade(boolean hasExternalSchemaUpgrade)
        {
            _hasExternalSchemaUpgrade = hasExternalSchemaUpgrade;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();

            elements.put("changeType", getChangeType());
            elements.put("previousReleaseVersion", getPreviousReleaseVersion());
            elements.put("releaseVersion", getReleaseVersion());
            elements.put("previousBuildTime", getPreviousBuildTime());
            elements.put("buildTime", getBuildTime());
            elements.put("hasSchemaUpgrade", isHasSchemaUpgrade());
            elements.put("hasExternalSchemaUpgrade", isHasExternalSchemaUpgrade());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class SystemUpgradeAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SystemUpgradeAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public SystemUpgradeAuditDomainKind()
        {
            super(AUDIT_EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_RELEASE_VERSION, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_PREVIOUS_RELEASE_VERSION, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_BUILD_TIME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_PREVIOUS_BUILD_TIME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_CHANGE_TYPE, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_HAS_SCHEMA_UPGRADE, PropertyType.BOOLEAN));
            fields.add(createPropertyDescriptor(COLUMN_NAME_HAS_EXTERNAL_SCHEMA_UPGRADE, PropertyType.BOOLEAN));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }

    public static class TestCase extends Assert
    {
        private static final String BUILD_1 = "Aug 11, 2026, 4:49:29 PM";
        private static final String BUILD_2 = "Aug 12, 2026, 9:01:02 AM";

        @Test
        public void testNoPriorEvent()
        {
            assertEquals(ChangeType.Install, determineChangeType(null, null, "26.9.0", BUILD_1, true));
            assertEquals(ChangeType.Unknown, determineChangeType(null, null, "26.9.0", BUILD_1, false));
        }

        @Test
        public void testNoChange()
        {
            assertNull(determineChangeType("26.9.0", BUILD_1, "26.9.0", BUILD_1, false));
            assertNull(determineChangeType("26.9-SNAPSHOT", BUILD_1, "26.9-SNAPSHOT", BUILD_1, false));
        }

        @Test
        public void testUpgrade()
        {
            assertEquals(ChangeType.Upgrade, determineChangeType("26.7.1", BUILD_1, "26.9.0", BUILD_2, false));
            assertEquals(ChangeType.Upgrade, determineChangeType("26.9.0", BUILD_1, "26.9.1", BUILD_2, false));
            assertEquals(ChangeType.Upgrade, determineChangeType("25.11.0", BUILD_1, "26.3.0", BUILD_2, false));
            assertEquals(ChangeType.Upgrade, determineChangeType("26.9-SNAPSHOT", BUILD_1, "26.9.0", BUILD_2, false));
        }

        @Test
        public void testDowngrade()
        {
            assertEquals(ChangeType.Downgrade, determineChangeType("26.9.0", BUILD_1, "26.7.1", BUILD_2, false));
            assertEquals(ChangeType.Downgrade, determineChangeType("26.9.1", BUILD_1, "26.9.0", BUILD_2, false));
            assertEquals(ChangeType.Downgrade, determineChangeType("26.9.0", BUILD_1, "26.9-SNAPSHOT", BUILD_2, false));
        }

        /** Regression guard for VersionNumber.getVersionInt(), which maps both 26.11 and 26.1 to 261 */
        @Test
        public void testDoubleDigitMinor()
        {
            assertEquals(ChangeType.Upgrade, determineChangeType("26.1.0", BUILD_1, "26.11.0", BUILD_2, false));
            assertEquals(ChangeType.Downgrade, determineChangeType("26.11.0", BUILD_1, "26.1.0", BUILD_2, false));
        }

        @Test
        public void testRebuild()
        {
            assertEquals(ChangeType.Rebuild, determineChangeType("26.9.0", BUILD_1, "26.9.0", BUILD_2, false));
            assertEquals(ChangeType.Rebuild, determineChangeType("26.9-SNAPSHOT", BUILD_1, "26.9-SNAPSHOT", BUILD_2, false));
            assertEquals(ChangeType.Rebuild, determineChangeType("26.9.0", null, "26.9.0", BUILD_2, false));
        }

        @Test
        public void testUnparseableVersion()
        {
            assertEquals(ChangeType.Unknown, determineChangeType("bogus", BUILD_1, "26.9.0", BUILD_2, false));
            assertEquals(ChangeType.Unknown, determineChangeType("26.9.0", BUILD_1, "", BUILD_2, false));
            assertEquals(ChangeType.Unknown, determineChangeType("26.9.0", BUILD_1, null, BUILD_2, false));
        }

        @Test
        public void testComments()
        {
            assertEquals("Server installed at version 26.9.0", buildComment(ChangeType.Install, null, "26.9.0"));
            assertEquals("Server running version 26.9.0", buildComment(ChangeType.Unknown, null, "26.9.0"));
            assertEquals("Server rebuilt at version 26.9.0", buildComment(ChangeType.Rebuild, "26.9.0", "26.9.0"));
            assertEquals("Server version changed from 26.7.1 to 26.9.0", buildComment(ChangeType.Upgrade, "26.7.1", "26.9.0"));
            assertEquals("Server version changed from 26.9.0 to 26.7.1", buildComment(ChangeType.Downgrade, "26.9.0", "26.7.1"));
        }
    }
}
