package org.labkey.api.util;

import org.labkey.api.data.Container;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Sep 27, 2009
 * Time: 3:10:17 PM
 *
 * This is a helper class for DetailsURL.  Rather than needing so subclass DetailsURL to provide a
 * container value, you may provide a ContainerContext instead.
 */
public interface ContainerContext
{
    public Container getContainer(Map context);
}
