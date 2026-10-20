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
package org.labkey.core.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.SiteSettingsProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.HttpsUtil;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.filters.ContentSecurityPolicyFilter;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;
import static org.labkey.api.settings.SiteSettingsProperties.*;

/** The one save path for site settings, shared by the update API and the site settings page */
public class SiteSettingsUpdater
{
    private final Map<SiteSettingsProperties, Object> _changes = new EnumMap<>(SiteSettingsProperties.class);
    private final Map<SiteSettingsProperties, Object> _current = new EnumMap<>(SiteSettingsProperties.class);
    private final Map<SiteSettingsProperties, Object> _merged = new EnumMap<>(SiteSettingsProperties.class);

    private SiteSettingsUpdater(Map<SiteSettingsProperties, Object> changes)
    {
        AppProps props = AppProps.getInstance();
        for (SiteSettingsProperties setting : SiteSettingsProperties.values())
            _current.put(setting, setting.getValue(props));
        _changes.putAll(changes);
        _merged.putAll(_current);
        _merged.putAll(_changes);
    }

    /** Current value of every site setting, keyed by name, with enums as their names */
    public static Map<String, Object> getSettings()
    {
        AppProps props = AppProps.getInstance();
        Map<String, Object> result = new LinkedHashMap<>();
        for (SiteSettingsProperties setting : SiteSettingsProperties.values())
        {
            Object value = setting.getValue(props);
            result.put(setting.name(), value instanceof Enum<?> e ? e.name() : value);
        }
        return result;
    }

    /**
     * Validates the requested settings against the current values and, if every check passes, saves them all.
     * @return false, with the reasons in errors, if anything was rejected; nothing is saved in that case
     */
    public static boolean update(@NotNull Map<String, Object> requested, @NotNull User user, @NotNull HttpServletRequest request, @NotNull BindException errors)
    {
        Map<SiteSettingsProperties, Object> changes = parse(requested, errors);
        if (errors.hasErrors())
            return false;

        SiteSettingsUpdater updater = new SiteSettingsUpdater(changes);
        updater.validate(errors);
        if (errors.hasErrors())
            return false;

        updater.testHttps(request, errors);
        if (errors.hasErrors())
            return false;

        return updater.save(user, errors);
    }

    private static Map<SiteSettingsProperties, Object> parse(Map<String, Object> requested, BindException errors)
    {
        Map<SiteSettingsProperties, Object> changes = new EnumMap<>(SiteSettingsProperties.class);
        requested.forEach((name, json) -> {
            SiteSettingsProperties setting;
            try
            {
                setting = SiteSettingsProperties.valueOf(name);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, "Unknown site setting: " + name);
                return;
            }

            try
            {
                Object value = setting.parseJson(json);
                if (setting == administratorContactEmail && StringUtils.isNotBlank((String) value))
                    value = new ValidEmail((String) value).getEmailAddress();
                changes.put(setting, value);
            }
            catch (IllegalArgumentException e)
            {
                reject(errors, setting, e.getMessage());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                reject(errors, setting, "Invalid primary site administrator email: " + e.getMessage());
            }
        });
        return changes;
    }

    private Object merged(SiteSettingsProperties setting)
    {
        return _merged.get(setting);
    }

    private int mergedInt(SiteSettingsProperties setting)
    {
        return (Integer) merged(setting);
    }

    private boolean isChanged(SiteSettingsProperties setting)
    {
        return _changes.containsKey(setting) && !Objects.equals(_changes.get(setting), _current.get(setting));
    }

    private void validate(BindException errors)
    {
        if (isChanged(usageReportingLevel) && MothershipReport.shouldReceiveMarketingUpdates())
            reject(errors, usageReportingLevel, "Usage reporting is always on for this distribution and can't be changed");

        if (isChanged(administratorContactEmail) && UserManager.getSiteAdmins().stream().noneMatch(u -> u.getEmail().equals(merged(administratorContactEmail))))
            reject(errors, administratorContactEmail, "Primary site administrator must be a site administrator: " + merged(administratorContactEmail));

        // Rules run only when one of their inputs changed, so an already-invalid saved value doesn't block unrelated updates
        if ((isChanged(showRibbonMessage) || isChanged(ribbonMessage)) && (Boolean) merged(showRibbonMessage) && StringUtils.isEmpty((String) merged(ribbonMessage)))
            reject(errors, ribbonMessage, "Cannot enable the ribbon message without providing a message to show");

        if (isChanged(maxBLOBSize))
        {
            int maxBlobSize = mergedInt(maxBLOBSize);
            int hardCap = Math.max(WriteableAppProps.SOFT_MAX_BLOB_SIZE, (Integer) _current.get(maxBLOBSize));
            if (maxBlobSize < 0)
                reject(errors, maxBLOBSize, "Maximum BLOB size cannot be negative");
            else if (maxBlobSize > hardCap)
                reject(errors, maxBLOBSize, "Maximum BLOB size cannot be set higher than " + hardCap + " bytes");
        }

        if (isChanged(sslPort))
        {
            int port = mergedInt(sslPort);
            if (port < 1 || port > 65535)
                reject(errors, sslPort, "HTTPS port must be between 1 and 65,535");
        }
        if (isChanged(readOnlyHttpRequestTimeout) && mergedInt(readOnlyHttpRequestTimeout) < 0)
            reject(errors, readOnlyHttpRequestTimeout, "HTTP timeout must be non-negative");
        if (isChanged(memoryUsageDumpInterval) && mergedInt(memoryUsageDumpInterval) < 0)
            reject(errors, memoryUsageDumpInterval, "Memory logging frequency must be non-negative");
        if (isChanged(scriptExecutionTimeout) && mergedInt(scriptExecutionTimeout) < 0)
            reject(errors, scriptExecutionTimeout, "Script execution timeout must be non-negative; set to 0 to disable the timeout");
        if (isChanged(assayTransformScriptTimeout) && mergedInt(assayTransformScriptTimeout) < 0)
            reject(errors, assayTransformScriptTimeout, "Assay transform script timeout must be non-negative; set to 0 to disable the timeout");

        if (isChanged(baseServerURL) || isChanged(sslRequired))
        {
            String baseUrl = (String) merged(baseServerURL);
            if (StringUtils.isBlank(baseUrl))
                reject(errors, baseServerURL, "Base server URL is required");
            else if ((Boolean) merged(sslRequired) && !baseUrl.startsWith("https"))
                reject(errors, baseServerURL, "Invalid Base Server URL. SSL connection is required. Consider https://.");
        }
    }

    private void testHttps(HttpServletRequest request, BindException errors)
    {
        if (!isChanged(sslRequired) && !isChanged(sslPort))
            return;

        int port = mergedInt(sslPort);
        // We only need to check that SSL is running if the user isn't already using SSL
        if ((Boolean) merged(sslRequired) && !(request.isSecure() && port == request.getServerPort()))
        {
            try
            {
                URL testURL = new URI("https", null, request.getServerName(), port, AppProps.getInstance().getContextPath(), null, null).toURL();
                Pair<String, Integer> sslResponse = HttpsUtil.testHttpsUrl(testURL, "Ensure that the web server is configured for SSL and the port is correct. If SSL is enabled, try saving these settings while connected via SSL.");
                if (sslResponse != null)
                    reject(errors, sslRequired, sslResponse.first);
            }
            catch (MalformedURLException | URISyntaxException e)
            {
                reject(errors, sslPort, "Invalid HTTPS URL: " + e.getMessage());
            }
        }
    }

    private boolean save(User user, BindException errors)
    {
        WriteableAppProps props = AppProps.getWriteableInstance();
        // Unchanged values aren't written, so posting back a getter's fallback (e.g., administratorContactEmail) doesn't pin it
        _changes.keySet().stream().filter(this::isChanged).forEach(setting -> {
            try
            {
                setting.applyValue(props, _changes.get(setting));
            }
            catch (IllegalArgumentException e)
            {
                reject(errors, setting, setting == baseServerURL ? getBaseServerUrlMessage(e) : e.getMessage());
            }
        });
        if (errors.hasErrors())
            return false;

        props.save(user);

        // Report on every save, not just level changes, so accepting the default level after install still reports right away
        UsageReportingLevel.reportNow();
        if (isChanged(sslRequired))
            ContentSecurityPolicyFilter.regenerateSubstitutionMap();

        return true;
    }

    /** A field error, so the site settings page can mark the control that failed */
    private static void reject(BindException errors, SiteSettingsProperties setting, String message)
    {
        errors.addError(new FieldError(errors.getObjectName(), setting.name(), message));
    }

    public static class TestCase extends Assert
    {
        private Map<String, Object> _original;

        @Before
        public void captureSettings()
        {
            _original = getSettings();
        }

        // Restores directly, bypassing validation, so a server with odd settings (e.g., SSL off but required) still resets
        @After
        public void restoreSettings()
        {
            MothershipReport.setSelfTestMarketingUpdates(false);
            WriteableAppProps props = AppProps.getWriteableInstance();
            getSettings().forEach((name, value) -> {
                Object original = _original.get(name);
                if (!Objects.equals(value, original))
                {
                    SiteSettingsProperties setting = SiteSettingsProperties.valueOf(name);
                    setting.applyValue(props, setting.parseJson(original));
                }
            });
            props.save(TestContext.get().getUser());
        }

        private BindException update(Map<String, Object> requested, boolean expectSuccess)
        {
            BindException errors = new NullSafeBindException(new Object(), "form");
            boolean success = SiteSettingsUpdater.update(requested, TestContext.get().getUser(), TestContext.get().getRequest(), errors);
            assertEquals("Unexpected result. Errors: " + errors.getAllErrors(), expectSuccess, success);
            assertEquals(!expectSuccess, errors.hasErrors());
            return errors;
        }

        private void assertRejected(Map<String, Object> requested, String expectedMessage)
        {
            Map<String, Object> before = getSettings();
            BindException errors = update(requested, false);
            assertTrue("Expected an error containing '" + expectedMessage + "' but got " + errors.getAllErrors(),
                errors.getAllErrors().stream().anyMatch(e -> StringUtils.contains(e.getDefaultMessage(), expectedMessage)));
            assertEquals("A rejected update changed settings", before, getSettings());
        }

        @Test
        public void testGetSettings()
        {
            Map<String, Object> settings = getSettings();
            assertEquals(SiteSettingsProperties.values().length, settings.size());
            assertEquals(AppProps.getInstance().getScriptExecutionTimeout(), settings.get(scriptExecutionTimeout.name()));
            assertEquals(AppProps.getInstance().getExceptionReportingLevel().name(), settings.get(exceptionReportingLevel.name()));
        }

        @Test
        public void testUpdate()
        {
            int newTimeout = AppProps.getInstance().getScriptExecutionTimeout() + 7;
            boolean newNavAccessOpen = !AppProps.getInstance().isNavigationAccessOpen();
            update(Map.of(scriptExecutionTimeout.name(), String.valueOf(newTimeout), navAccessOpen.name(), newNavAccessOpen), true);
            assertEquals(newTimeout, AppProps.getInstance().getScriptExecutionTimeout());
            assertEquals(newNavAccessOpen, AppProps.getInstance().isNavigationAccessOpen());
        }

        @Test
        public void testRoundTrip()
        {
            update(_original, true);
            assertEquals(_original, getSettings());
        }

        @Test
        public void testClearedStringReadsAsNull()
        {
            Map<String, Object> cleared = new HashMap<>();
            cleared.put(showRibbonMessage.name(), false);
            cleared.put(ribbonMessage.name(), null);
            update(cleared, true);
            assertNull(getSettings().get(ribbonMessage.name()));

            update(Map.of(ribbonMessage.name(), ""), true);
            assertNull(getSettings().get(ribbonMessage.name()));
        }

        @Test
        public void testParsing()
        {
            assertRejected(Map.of("noSuchSetting", 1), "Unknown site setting: noSuchSetting");
            assertRejected(Map.of(navAccessOpen.name(), "yes"), "Expected true or false");
            assertRejected(Map.of(scriptExecutionTimeout.name(), "ten"), "Expected an integer");
            assertRejected(Map.of(scriptExecutionTimeout.name(), 1.5), "Expected an integer");
            assertRejected(Map.of(exceptionReportingLevel.name(), "LOUD"), "Expected one of");
        }

        @Test
        public void testNothingSavedOnError()
        {
            int newTimeout = AppProps.getInstance().getScriptExecutionTimeout() + 7;
            assertRejected(Map.of(scriptExecutionTimeout.name(), newTimeout, memoryUsageDumpInterval.name(), -1), "Memory logging frequency must be non-negative");
        }

        @Test
        public void testMergedValidation()
        {
            update(Map.of(showRibbonMessage.name(), false, ribbonMessage.name(), ""), true);
            assertRejected(Map.of(showRibbonMessage.name(), true), "Cannot enable the ribbon message");

            // A message that's already saved satisfies the check
            update(Map.of(ribbonMessage.name(), "Test ribbon message"), true);
            update(Map.of(showRibbonMessage.name(), true), true);
            assertRejected(Map.of(ribbonMessage.name(), ""), "Cannot enable the ribbon message");
        }

        @Test
        public void testInvalidSavedValueDoesNotBlockOtherUpdates()
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setSSLPort(0);
            props.save(TestContext.get().getUser());

            boolean newNavAccessOpen = !AppProps.getInstance().isNavigationAccessOpen();
            update(Map.of(navAccessOpen.name(), newNavAccessOpen), true);
            assertEquals(newNavAccessOpen, AppProps.getInstance().isNavigationAccessOpen());
        }

        @Test
        public void testRangeChecks()
        {
            assertRejected(Map.of(maxBLOBSize.name(), -1), "Maximum BLOB size cannot be negative");
            assertRejected(Map.of(maxBLOBSize.name(), Integer.MAX_VALUE), "Maximum BLOB size cannot be set higher than");
            assertRejected(Map.of(sslPort.name(), 70000), "HTTPS port must be between");
            assertRejected(Map.of(baseServerURL.name(), ""), "Base server URL is required");

            FieldError error = update(Map.of(sslPort.name(), 70000), false).getFieldError();
            assertNotNull("Expected a field error", error);
            assertEquals(sslPort.name(), error.getField());
        }

        @Test
        public void testAdministratorContactEmail()
        {
            assertRejected(Map.of(administratorContactEmail.name(), "not-a-site-admin@example.com"), "must be a site administrator");

            String adminEmail = UserManager.getSiteAdmins().getFirst().getEmail();
            update(Map.of(administratorContactEmail.name(), " " + adminEmail.toUpperCase() + " "), true);
            assertEquals(adminEmail, AppProps.getInstance().getAdministratorContactEmail(false));
        }

        @Test
        public void testUsageReportingLockedOnMarketingDistributions()
        {
            MothershipReport.setSelfTestMarketingUpdates(true);
            UsageReportingLevel current = AppProps.getInstance().getUsageReportingLevel();
            UsageReportingLevel other = current == UsageReportingLevel.NONE ? UsageReportingLevel.ON : UsageReportingLevel.NONE;
            assertRejected(Map.of(usageReportingLevel.name(), other.name()), "Usage reporting is always on");
            update(Map.of(usageReportingLevel.name(), current.name()), true);
        }
    }

    private static String getBaseServerUrlMessage(IllegalArgumentException e)
    {
        @Nullable Throwable cause = e.getCause();
        return "Invalid Base Server URL, \"" + (cause != null ? cause.getMessage() : e.getMessage()) + "\". " +
            "Please enter a valid base URL containing the protocol, hostname, and port if required. " +
            "The webapp context path should not be included. " +
            "For example: \"https://www.example.com\" or \"http://www.labkey.org:8080\" and not \"http://www.example.com/labkey/\"";
    }
}
