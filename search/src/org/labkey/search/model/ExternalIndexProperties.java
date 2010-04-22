package org.labkey.search.model;

/**
 * User: adam
 * Date: Apr 20, 2010
 * Time: 7:08:55 PM
 */
public interface ExternalIndexProperties
{
    String getExternalIndexPath();

    String getAnalyzer();

    boolean hasProperties();
}
