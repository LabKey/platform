/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.devtools.authentication;

import org.labkey.api.data.Container;
import org.labkey.api.security.BaseSecondaryAuthenticationConfiguration;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

public class TestSecondaryConfiguration extends BaseSecondaryAuthenticationConfiguration<TestSecondaryProvider>
{
    public TestSecondaryConfiguration(TestSecondaryProvider provider, Map<String, Object> standardSettings, Map<String, Object> props)
    {
        super(provider, standardSettings, props);
    }

    @Override
    public ActionURL getRedirectURL(User candidate, Container c)
    {
        return TestSecondaryController.getTestSecondaryURL(c, getRowId());
    }
}
