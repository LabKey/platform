/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.labkey.api.util.FileUtil;

import java.io.IOException;

public class TransactionFilter implements Filter
{
    @Override
    public void destroy()
    {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException
    {
        // Is it worth creating another filter for this? It is in the spirit of per-request resource tracking.
        FileUtil.startRequest();
        try
        {
            chain.doFilter(req, resp);
        }
        finally
        {
            ConnectionWrapper.dumpLeaksForThread(Thread.currentThread());
            DbScope.closeAllConnectionsForCurrentThread();
        }
        FileUtil.stopRequest();
    }

    @Override
    public void init(FilterConfig arg0)
    {
    }
} 
