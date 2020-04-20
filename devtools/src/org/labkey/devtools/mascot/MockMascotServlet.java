package org.labkey.devtools.mascot;

import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            throw new IllegalStateException("Expected " + expected.toString() + ", but value was " + value.toString());
    }
}