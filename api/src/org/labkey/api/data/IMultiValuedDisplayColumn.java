package org.labkey.api.data;

import java.util.List;

/**
 * User: kevink
 * Date: 6/20/16
 */
public interface IMultiValuedDisplayColumn
{
    List<String> renderURLs(RenderContext ctx);

    List<Object> getDisplayValues(RenderContext ctx);

    List<String> getTsvFormattedValues(RenderContext ctx);

    List<Object> getJsonValues(RenderContext ctx);

}
