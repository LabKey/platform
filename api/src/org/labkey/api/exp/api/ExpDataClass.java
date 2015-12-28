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
