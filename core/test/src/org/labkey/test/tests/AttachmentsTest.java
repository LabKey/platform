package org.labkey.test.tests;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.announcements.InsertPage;
import org.labkey.test.pages.core.admin.ShowAdminPage;
import org.labkey.test.util.AuditLogHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.IssuesHelper;
import org.labkey.test.util.WikiHelper;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.labkey.test.util.PermissionsHelper.READER_ROLE;

/**
 * Regression coverage for attachment parent types in audit log and attachment queries. <a href="https://github.com/LabKey/platform/pull/7231">Related PR</a>
 * Create a few issues, wiki pages, and messages with attachments. Verify that rows containing the expected parent
 * types show up in the audit log and the attachment queries.
 */

@Category({Daily.class})
public class AttachmentsTest extends BaseWebDriverTest
{
    private static final String LIST_DEF_NAME = "Issues";

    // Keep these in-sync with doSetup() steps
    private static final int ISSUE_ATTACHMENTS = 4;
    private static final int WIKI_ATTACHMENTS = 3;
    private static final int MESSAGE_ATTACHMENTS = 3;

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("announcements", "issues", "wiki");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "AttachmentVerifyProject";
    }

    @BeforeClass
    public static void setupProject()
    {
        AttachmentsTest init = getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), null);

        IssuesHelper issuesHelper = new IssuesHelper(this);
        issuesHelper.createNewIssuesList(LIST_DEF_NAME, _containerHelper);
        issuesHelper.addIssue("Issue #1", getCurrentUserName(), Map.of(), TestFileUtils.getSampleData("fileTypes/docx_sample.docx"), TestFileUtils.getSampleData("fileTypes/png_sample.png"), TestFileUtils.getSampleData("fileTypes/pdf_sample.pdf"));
        issuesHelper.addIssue("Issue #2", getCurrentUserName(), Map.of(), TestFileUtils.getSampleData("fileTypes/xml_sample.xml"));

        WikiHelper wikiHelper = new WikiHelper(this);
        wikiHelper.createWikiPage("Page1", "RADEOX", "Page with sample.txt", "This is a test", TestFileUtils.getSampleData("fileTypes/sample.txt"));
        wikiHelper.createWikiPage("Page2", "HTML", "Page with jpg_sample.jpg", "This is a test", TestFileUtils.getSampleData("fileTypes/jpg_sample.jpg"));
        wikiHelper.createWikiPage("Page3", "MARKDOWN", "Page with xlsx_sample.xlsx", "This is a test", TestFileUtils.getSampleData("fileTypes/xlsx_sample.xlsx"));

        InsertPage.beginAt(this)
            .setTitle("Message #1")
            .addAttachments(TestFileUtils.getSampleData("fileTypes/doc_sample.doc"), TestFileUtils.getSampleData("fileTypes/png_sample.png"))
            .submit();
        InsertPage.beginAt(this)
            .setTitle("Message #2")
            .addAttachments(TestFileUtils.getSampleData("fileTypes/csv_sample.csv"))
            .submit();
    }

    @Test
    public void testParentTypesInAuditLog() throws IOException, CommandException
    {
        SelectRowsResponse auditResponse = new SelectRowsCommand("auditLog", AuditLogHelper.AuditEvent.ATTACHMENT_AUDIT_EVENT.getName()).execute(createDefaultConnection(), getProjectName());
        Assert.assertEquals(10, auditResponse.getRowCount());
        Bag<String> parentTypes = new HashBag<>();
        auditResponse.getRowset().forEach(row -> parentTypes.add((String)row.getValue("ParentType")));
        Assert.assertEquals(ISSUE_ATTACHMENTS, parentTypes.getCount("IssueComment"));
        Assert.assertEquals(WIKI_ATTACHMENTS, parentTypes.getCount("Wiki"));
        Assert.assertEquals(MESSAGE_ATTACHMENTS, parentTypes.getCount("Announcement"));
    }

    @Test
    public void testParentTypesInAttachmentQueries() throws ParseException
    {
        testQueries();
        impersonateRole("Troubleshooter");
        testQueries();
        stopImpersonating();
    }

    private void testQueries() throws ParseException
    {
        ShowAdminPage.beginAt(this);
        clickAndWait(Locator.linkWithText("Attachments"));
        DataRegionTable table = DataRegionTable.DataRegion(getDriver()).withName("core").waitFor();
        assertTextPresent("DocumentsGroupedByParentType");
        verifyRow(table, "IssueComment", ISSUE_ATTACHMENTS);
        int wikiCount = verifyRow(table, "Wiki", WIKI_ATTACHMENTS);
        verifyRow(table, "IssueComment", ISSUE_ATTACHMENTS);

        int wikiRowIndex = table.getRowIndex("ParentType", "Wiki");
        clickAndWait(table.link(wikiRowIndex, "Count"));
        table = DataRegionTable.DataRegion(getDriver()).withName("core").waitFor();
        assertTextPresent("Documents");
        if (wikiCount > 100)
            table.assertPaginationText(1, 100, wikiCount);
        else
            Assert.assertEquals(wikiCount, table.getDataRowCount());
    }

    private static final DecimalFormat df = new DecimalFormat("#,##0");

    private int verifyRow(DataRegionTable table, String parentType, int minimum) throws ParseException
    {
        Map<String, String> map = table.getRowDataAsMap("ParentType", parentType);
        String countString = map.get("Count");
        int count = df.parse(countString).intValue();
        Assert.assertTrue("Count for " + parentType + " was less than expected: " + count + " vs. " + minimum, count >= minimum);
        return count;
    }

    @Test
    public void testNoDocumentsTableForReaders()
    {
        try
        {
            // Readers should have no access to core.Documents
            goToProjectHome();
            impersonateRole(READER_ROLE);
            goToSchemaBrowser();
            viewQueryData("core", "Documents");
        }
        finally
        {
            stopImpersonating();
        }
    }
}
