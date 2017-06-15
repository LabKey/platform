package org.labkey.test.tests.study;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by RyanS on 5/18/2017.
 */
public class StudyContinuousTest extends AbstractStudyTimeKeyFieldTest
{
    @Override
    protected int getDatasetCount(){return 35;}

    @Override
    protected String getProjectName()
    {
        return "ContinuousStudyVerifyProject";
    }

    @Override
    public void runApiTests(){}

    @Override
    protected String getHeaderName()
    {return "4b$PAsian";}

    protected void doCreateSteps()
    {
        importStudy(CONTINUOUS_ARCHIVE,null);
    }

    protected void doVerifySteps()
    {
        Map<String,String> kvp = new HashMap<>();
        kvp.put(SUBJECT_COL_NAME,"999321033");
        kvp.put("date","2006-02-14");
        testCannotInsertExactDuplicateNoTimeKey(kvp,"CPF-1: Follow-up Chemistry Panel",getFolderName());

        kvp.put(SUBJECT_COL_NAME, "999320016");
        kvp.put("date", "2005-01-17 03:36");
        testCannotInsertDifferingOnlyTimeNoTimeKey(kvp, "OTB-1: Outside Testing/Belief", getFolderName());

        kvp.put(SUBJECT_COL_NAME, "999320518");
        kvp.put("date", "2006-03-04 03:46");
        testCanInsertWithOnlyTimeDifferentIfTimeKey(kvp, "AE-1:(VTN) AE Log", getFolderName());

        //TODO: Uncomment and fix after sprint 17.2.3 has branched
        //testCannotUploadDuplicateIfTimeKey(DUPLICATE_DATASET, "APX-1: Abbreviated Physical Exam", getFolderName());

        testCanUploadWithOnlyTimeDifferentIfTimeKey(DIFFERENT_TIME, "APX-1: Abbreviated Physical Exam", getFolderName());

        testCanChangeExtraKeyFromTimeIfDoesNotViolateUnique(getFolderName(), "RCB-1: Reactogenicity-Baseline", DIFFERENT_DATES_DIFFERENT_TIMES);

        testDateFieldDisplaysTimeIfTimeKey();

        testCannotTurnOffExtraTimeKeyIfViolatesUnique(getFolderName(), "RCB-1: Reactogenicity-Baseline");

        testCannotSetAdditionalKeyForDemographics();

        //TODO: no idea how these are meant to work or be set up
        //verifyAliasReplacement();

        //verifyStudyAndDatasets(false);

        //verifyPermissionsRestrictions();

        verifyParticipantReports(26);

        manageSubjectClassificationTest();

        verifyStudyAndDatasets(false);

    }
}
