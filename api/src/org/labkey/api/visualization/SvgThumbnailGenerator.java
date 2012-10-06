package org.labkey.api.visualization;

import org.labkey.api.thumbnail.DynamicThumbnailProvider;

/**
 * User: adam
 * Date: 10/4/12
 * Time: 5:27 AM
 */

// Primarily a marker interface that indicates the provider generates its thumbnail from provided SVG. Used by the
// VisualizationController to generate thumbnails for time charts (and soon box/scatter)
public interface SvgThumbnailGenerator extends DynamicThumbnailProvider
{
    public void setSvg(String svg);
}
