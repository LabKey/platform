/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

import java.util.List;

/**
 * User: jeckels
 * Date: Jul 13, 2007
 */
public class AssayService
{
    static private Interface INSTANCE;

    public static final String ASSAY_SCHEMA_NAME = "assay";

    static public synchronized Interface get()
    {
        return INSTANCE;
    }

    static public synchronized void setInstance(Interface impl)
    {
        INSTANCE = impl;
    }

    public interface Interface
    {
        public void registerAssayProvider(AssayProvider provider);
        public AssayProvider getProvider(String providerName);
        public AssayProvider getProvider(ExpProtocol protocol);
        public List<AssayProvider> getAssayProviders();
        public QueryView createAssayListView(ViewContext context, boolean portalView);

        TableInfo createRunTable(String alias, ExpProtocol protocol, AssayProvider provider, User user, Container container);

        public UserSchema createSchema(User user, Container container);

        List<ExpProtocol> getAssayProtocols(Container container);

        boolean hasAssayProtocols(Container container);

        public ActionURL getProtocolURL(Container container, ExpProtocol protocol, String action);

        ActionURL getPublishConfirmURL(Container container, ExpProtocol protocol);
        ActionURL getDesignerURL(Container container, String providerName);
        ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy);
        ActionURL getAssayListURL(Container container);
        ActionURL getAssayRunsURL(Container container, ExpProtocol protocol);
        ActionURL getAssayDataURL(Container container, ExpProtocol protocol, int... runIds);
        ActionURL getUploadWizardURL(Container container, ExpProtocol protocol);

    }
}
