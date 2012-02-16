package org.labkey.api.pipeline;

/**
 * Indicates that a pipeline job has been cancelled and no further work should be done for it.
 *
 * It would be reasonable to convert this to a checked exception, but it would involve changing a lot of method
 * signatures.
 * User: jeckels
 * Date: Feb 15, 2012
 */
public class CancelledException extends RuntimeException
{
}
