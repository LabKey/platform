/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.compliance;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Activity;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.PHI;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.ColumnInfoFilter;
import org.labkey.api.query.column.ColumnInfoTransformer;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

/**
 * Created by davebradlee on 7/27/17.
 *
 * ComplianceService: ALL METHODS MUST CHECK IF ComplianceModule is enabled in container (if appropriate); callers don't check
 *
 */
public interface ComplianceService
{
    static @NotNull ComplianceService get()
    {
        // Return default service if module not registered
        ComplianceService service = ServiceRegistry.get().getService(ComplianceService.class);
        if (null == service)
            service = new DefaultComplianceService();
        return service;
    }

    static void setInstance(ComplianceService instance)
    {
        ServiceRegistry.get().registerService(ComplianceService.class, instance);
    }

    ColumnInfoTransformer NOOP_COLUMN_INFO_TRANSFORMER = columnInfo -> columnInfo;

    boolean isComplianceSupported();
    String getModuleName();

    // Next three methods are signing-related, BUT the implementation is closely tied to the compliance controller
    // and the functionality is part of our full compliance capabilities. (As opposed to ELN which just needs the
    // signing service.) So they remain on ComplianceService.
    ActionURL urlFor(Container container, QueryAction action, ActionURL queryBasedUrl);
    boolean hasElecSignPermission(@NotNull Container container, @NotNull User user);
    boolean hasViewSignedSnapshotsPermission(@NotNull Container container, @NotNull User user);

    default @NotNull PHI getMaxAllowedPhi(@NotNull Container container, @NotNull User user)
    {
        return PHI.Restricted;
    }
    default Activity getCurrentActivity(ViewContext viewContext)
    {
        return null;
    }

    /**
     * Get the compliance folder settings.
     * @throws org.labkey.api.view.UnauthorizedException if the user doesn't have sufficient permissions.
     */
    ComplianceFolderSettings getFolderSettings(@NotNull Container container, @NotNull User user) throws UnauthorizedException;

    /**
     * Update the compliance folder settings.
     * @throws org.labkey.api.view.UnauthorizedException if the user doesn't have sufficient permissions.
     */
    void setFolderSettings(@NotNull Container container, @NotNull User user, @NotNull ComplianceFolderSettings settings);

    JSONObject getPageContextJson();

    ColumnInfoFilter filter(@NotNull PhiColumnBehavior behavior, @NotNull PHI maxAllowedPhi);

    ColumnInfoTransformer transformer(@NotNull PhiColumnBehavior behavior, @NotNull PHI maxAllowedPhi);

    // Signing module manages the compliance schema, but TermsOfUseTableInfo is intertwined with the Compliance module
    // code, so keep it in Compliance
    TableInfo createTermsOfUseTable(UserSchema schema, ContainerFilter containerFilter);

    class DefaultComplianceService implements ComplianceService
    {
        @Override
        public boolean isComplianceSupported()
        {
            return false;
        }

        @Override
        public String getModuleName()
        {
            return ComplianceService.class.getName();
        }
        @Override
        public ActionURL urlFor(Container container, QueryAction action, ActionURL queryBasedUrl)
        {
            return null;
        }
        @Override
        public boolean hasElecSignPermission(@NotNull Container container, @NotNull User user)
        {
            return false;
        }
        @Override
        public boolean hasViewSignedSnapshotsPermission(@NotNull Container container, @NotNull User user)
        {
            return false;
        }
        @Override
        @NotNull public PHI getMaxAllowedPhi(@NotNull Container container, @NotNull User user)
        {
            return PHI.Restricted;
        }
        @Override
        public Activity getCurrentActivity(ViewContext viewContext)
        {
            return null;
        }

        @Override
        public ComplianceFolderSettings getFolderSettings(@NotNull Container container, @NotNull User user) throws UnauthorizedException
        {
            return null;
        }

        @Override
        public void setFolderSettings(@NotNull Container container, @NotNull User user, @NotNull ComplianceFolderSettings settings)
        {
        }

        @Override
        public JSONObject getPageContextJson()
        {
            return null;
        }

        @Override
        public ColumnInfoFilter filter(@NotNull PhiColumnBehavior behavior, @NotNull PHI maxAllowedPhi)
        {
            // no-op filter
            return (col) -> true;
        }

        @Override
        public ColumnInfoTransformer transformer(@NotNull PhiColumnBehavior behavior, @NotNull PHI maxAllowedPhi)
        {
            return NOOP_COLUMN_INFO_TRANSFORMER;
        }

        @Override
        public TableInfo createTermsOfUseTable(UserSchema schema, ContainerFilter containerFilter)
        {
            return null;
        }
    }
}
