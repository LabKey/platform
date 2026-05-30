/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.devtools.mascot;

import org.apache.commons.io.IOUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Mocks a minimal (incomplete) set of Mascot APIs to allow for rudimentary testing without a Mascot server. See MascotClientImpl.TestCase.
 */
@MultipartConfig
public class MockMascotServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        // Respond to GET mockservlet/cgi/client.pl?version
        if (req.getPathInfo().equals("/cgi/client.pl") && req.getQueryString().equals("version"))
        {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Server", "LabKey MockMascotServer 1.0");
            resp.getOutputStream().print("Hello");
            resp.flushBuffer();
        }
        else if (req.getPathInfo().equals("/cgi/login.pl"))
        {
            resp.getOutputStream().print("sessionID=1234");
            resp.flushBuffer();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException
    {
        if (req.getPathInfo().equals("/cgi/submit.pl"))
        {
            throwIfNotEqual("1+--taskID+5678+--sessionID+1234", req.getQueryString());
            throwIfNotEqual(40, req.getParts().size());
            testPart(req, "CHARGE", "1+, 2+ and 3+");
            testPart(req, "CLE", "Trypsin");
            testPart(req, "COM", "Comments on this Mascot search");
            testPart(req, "DB", "IPI_human_plus");
            testPart(req, "ERRORTOLERANT", "0");
            testPart(req, "FORMAT", "Mascot generic");
            testPart(req, "FORMVER", "1.01");
            testPart(req, "ICAT", "");
            testPart(req, "INSTRUMENT", "Default");
            testPart(req, "INTERMEDIATE", "");
            testPart(req, "IT_MODS", "");
            testPart(req, "MODS", "");
            testPart(req, "OVERVIEW", "");
            testPart(req, "PFA", "1");
            testPart(req, "PRECURSOR", "");
            testPart(req, "REPORT", "20");
            testPart(req, "REPTYPE", "peptide");
            testPart(req, "SEARCH", "MIS");
            testPart(req, "SEG", "");
            testPart(req, "TAXONOMY", "All entries");
            testPart(req, "TOLU", "Da");
            testPart(req, "USEREMAIL", "useremail@domain");
            testPart(req, "USERNAME", "");
            testPart(req, "IATOL", "0");
            testPart(req, "IASTOL", "0");
            testPart(req, "IA2TOL", "0");
            testPart(req, "IBTOL", "1");
            testPart(req, "IBSTOL", "0");
            testPart(req, "IB2TOL", "1");
            testPart(req, "IYTOL", "1");
            testPart(req, "IYSTOL", "0");
            testPart(req, "IY2TOL", "1");
            testPart(req, "PEAK", "auto");
            testPart(req, "LTOL", "");
            testPart(req, "SHOWALLMODS", "");
            testPart(req, "TOL", "2.0");
            testPart(req, "MASS", "Average");
            testPart(req, "ITOL", "0.8");
            testPart(req, "ITOLU", "Da");
            throwIfNotEqual(req.getPart("FILE").getSize(), 8403L);
            resp.setStatus(HttpServletResponse.SC_OK);
            ServletOutputStream os = resp.getOutputStream();
            os.println("Peptide #1: GWKEPA");
            os.println("Peptide #2: AQPPVTA");
            os.println("Finished uploading search details");
            resp.flushBuffer();
        }
    }

    private void testPart(HttpServletRequest req, String name, String expectedValue) throws IOException, ServletException
    {
        String value = IOUtils.toString(req.getPart(name).getInputStream(), StandardCharsets.US_ASCII);
        throwIfNotEqual(expectedValue, value);
    }

    private void throwIfNotEqual(Object expected, Object value)
    {
        if (!expected.equals(value))
            throw new IllegalStateException("Expected " + expected + ", but value was " + value.toString());
    }
}