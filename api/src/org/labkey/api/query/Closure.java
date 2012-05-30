package org.labkey.api.query;

/**
 * User: adam
 * Date: 5/29/12
 * Time: 10:22 PM
 */
// Like a runnable, but meant to be called synchronously. Can throw Exceptions to the caller.
public interface Closure
{
    void execute() throws Exception;
}
