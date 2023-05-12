/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.action;

import java.io.IOException;

/**
 * Interface for client API responses
 */
public interface ApiResponse
{
    /* The general pattern for writing a complicated or streaming response should be:
     *
     * <pre>
     * writer.startResponse();
     * try
     * {
     *    ... write response ...
     * }
     * catch (Exception x)
     * {
     *     handleRenderException(writer, x);
     * }
     * finally
     * {
     *     write.endResponse();
     * }
     * </pre>
     *
     * A simple response can be rendered imply using writeResponse().
     *
     * e.g
     * <pre>
     *     writer.writeResponse(exception);
     * or
     *     writer.writeResponse(json);
     * </pre>
     */
    void render(ApiResponseWriter writer) throws Exception;

    /* render() should call this if an exception is hit inside a response (after startResponse() is called) */
    default void handleRenderException(ApiResponseWriter writer, Exception x) throws IOException
    {
        writer.handleRenderException(x);
    }
}
