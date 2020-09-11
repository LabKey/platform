package org.labkey.api.util;

import org.labkey.api.jsp.LabKeyJspWriter;

/**
 * Marker interface that asserts that this class's {@code toString()} is safe to render in a browser. In other words, it
 * returns valid HTML with properly encoded text or well-formed JavaScript/JSON. This is used by {@link LabKeyJspWriter}
 * to validate attempts to render {@code Object}s.
 */
public interface SafeToRender
{
    /**
     * Must return well-formed HTML or JavaScript. This method definition is a no-op (Object implements toString()), but
     * it's included here as a reminder of this requirement and to provide easy inspection to verify well-formedness.
     */
    @Override
    String toString();
}
