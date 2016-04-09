/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.springframework.web.servlet.mvc.Controller;

import java.util.List;

/**
 * Category of {@link ExpData}, extended by a Domain with custom properties. Data version of an {@link ExpSampleSet}
 * User: kevink
 * Date: 9/15/15
 */
public interface ExpDataClass extends ExpObject
{
    String getDataLsidPrefix();

    @Nullable
    @Override
    ActionURL detailsURL();

    /** Get all ExpData that are members of the ExpDataClass. */
    List<? extends ExpData> getDatas();

    ExpData getData(Container c, String name);

    /** Get the SampleSet related to this ExpDataClass. */
    @Nullable
    ExpSampleSet getSampleSet();

    Domain getDomain();

    void setDomain(Domain d);

    String getDescription();

    String getNameExpression();

    //
    // URLS
    //

    ActionURL urlShowDefinition(ContainerUser cu);

    ActionURL urlEditDefinition(ContainerUser cu);

    ActionURL urlShowData();

    ActionURL urlShowData(Container c);

    ActionURL urlUpdate(User user, Container container, @Nullable URLHelper cancelUrl);

    ActionURL urlDetails();

    ActionURL urlShowHistory();

    ActionURL urlFor(Class<? extends Controller> actionClass);

    ActionURL urlFor(Class<? extends Controller> actionClass, Container c);

}
