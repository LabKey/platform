package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;

import java.util.List;

public interface AssayHeaderLinkProvider
{
    String PARAM_PREFIX = "assay";

    @NotNull
    List<NavTree> getLinks(ExpProtocol protocol, Container container, User user);
}
