package org.labkey.api.settings;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.GUID;
import org.labkey.api.util.TestContext;

public class LookAndFeelFolderPropertiesTest extends Assert
{
    private Container _project;
    private Container _folder;
    private Container _subFolder;
    private User _testUser;

    @Before
    public void setUp() throws ValidEmail.InvalidEmailException, org.labkey.api.security.SecurityManager.UserManagementException
    {
        _testUser = TestContext.get().getUser();
        _project = ContainerManager.createContainer(ContainerManager.getRoot(), GUID.makeGUID(), _testUser);
        _folder = ContainerManager.createContainer(_project, "Folder", _testUser);
        _subFolder = ContainerManager.createContainer(_folder, "SubFolder", _testUser);
        assertNotNull(_project);
        assertNotNull(_folder);
        assertNotNull(_subFolder);
    }

    @After
    public void cleanup() throws ValidEmail.InvalidEmailException, org.labkey.api.security.SecurityManager.UserManagementException
    {
        ContainerManager.deleteAll(_project, _testUser);
    }

    @Test
    public void testInheritance()
    {
        LookAndFeelFolderProperties rootProps = LookAndFeelProperties.getInstance(ContainerManager.getRoot());
        LookAndFeelFolderProperties projectProps = LookAndFeelProperties.getInstance(_project);
        LookAndFeelFolderProperties folderProps = LookAndFeelProperties.getInstance(_folder);
        LookAndFeelFolderProperties subFolderProps = LookAndFeelProperties.getInstance(_subFolder);

        // New project and folders should inherit all the way down to the subfolder
        testFolderPropertiesInherited(rootProps, projectProps);
        testFolderPropertiesInherited(projectProps, folderProps);
        testFolderPropertiesInherited(folderProps, subFolderProps);

        // Overwrite project properties and ensure those inherit down to the subfolder
        testWriteFolderProperties(_project, "ddMMMyyyy", "HH:mm:ss.SSS", "00.00", "yyyy.MM.dd", "HH.mm", !rootProps.areRestrictedColumnsEnabled());
        projectProps = LookAndFeelProperties.getInstance(_project);
        folderProps = LookAndFeelProperties.getInstance(_folder);
        subFolderProps = LookAndFeelProperties.getInstance(_subFolder);
        testFolderPropertiesInherited(projectProps, folderProps);
        testFolderPropertiesInherited(folderProps, subFolderProps);

        // Change project properties and ensure new values inherit down to the subfolder
        testWriteFolderProperties(_project, "MMMddyyyy", "HH.mm.ss.SSS", "##.##", "yyyy/MM/dd", "HH:mm", rootProps.areRestrictedColumnsEnabled());
        projectProps = LookAndFeelProperties.getInstance(_project);
        folderProps = LookAndFeelProperties.getInstance(_folder);
        subFolderProps = LookAndFeelProperties.getInstance(_subFolder);
        testFolderPropertiesInherited(projectProps, folderProps);
        testFolderPropertiesInherited(folderProps, subFolderProps);

        // Overwrite folder properties and ensure those inherit to the subfolder
        testWriteFolderProperties(_folder, "MMMyyyydd", "HH:mm:ss.SSS", "##.00", "yyyy:MM:dd", "HH.mm", !rootProps.areRestrictedColumnsEnabled());
        folderProps = LookAndFeelProperties.getInstance(_folder);
        subFolderProps = LookAndFeelProperties.getInstance(_subFolder);
        testFolderPropertiesInherited(folderProps, subFolderProps);

        // Overwrite subfolder properties and ensure those stick
        testWriteFolderProperties(_folder, "yyyy-MM-dd", "HH:mm", "#0.00", "yyyy-MM-dd", "HH:mm", rootProps.areRestrictedColumnsEnabled());

        // Clear properties in each container and ensure they then inherit from their parent
        testClearAndInherit(_subFolder, _folder);
        testClearAndInherit(_folder, _project);
        testClearAndInherit(_project, ContainerManager.getRoot());
    }

    private void testFolderPropertiesInherited(LookAndFeelFolderProperties parent, LookAndFeelFolderProperties child)
    {
        assertEquals(parent.getDefaultDateFormat(), child.getDefaultDateFormat());
        assertEquals(parent.getDefaultDateTimeFormat(), child.getDefaultDateTimeFormat());
        assertEquals(parent.getDefaultTimeFormat(), child.getDefaultTimeFormat());
        assertEquals(parent.getDefaultNumberFormat(), child.getDefaultNumberFormat());
        assertEquals(parent.getExtraDateParsingPattern(), child.getExtraDateParsingPattern());
        assertEquals(parent.getExtraDateTimeParsingPattern(), child.getExtraDateTimeParsingPattern());
        assertEquals(parent.getExtraTimeParsingPattern(), child.getExtraTimeParsingPattern());
        assertEquals(parent.areRestrictedColumnsEnabled(), child.areRestrictedColumnsEnabled());
        assertNull(child.getDefaultDateFormatStored());
        assertNull(child.getDefaultDateTimeFormatStored());
        assertNull(child.getDefaultTimeFormatStored());
        assertNull(child.getDefaultNumberFormatStored());
        assertNull(child.getExtraDateParsingPatternStored());
        assertNull(child.getExtraDateTimeParsingPatternStored());
        assertNull(child.getExtraTimeParsingPatternStored());
        assertNull(child.areRestrictedColumnsEnabledStored());
    }

    private void testWriteFolderProperties(Container c, String dateDisplay, String timeDisplay, String numberDisplay, String dateParsing, String timeParsing, boolean restricted)
    {
        WriteableFolderLookAndFeelProperties writeable = new WriteableFolderLookAndFeelProperties(c);
        writeable.setDefaultDateFormat(dateDisplay);
        writeable.setDefaultDateTimeFormat(dateDisplay + " " + timeDisplay);
        writeable.setDefaultTimeFormat(timeDisplay);
        writeable.setDefaultNumberFormat(numberDisplay);
        writeable.setExtraDateParsingPattern(dateParsing);
        writeable.setExtraDateTimeParsingPattern(dateParsing + " " + timeParsing);
        writeable.setExtraTimeParsingPattern(timeParsing);
        writeable.setRestrictedColumnsEnabled(restricted);
        writeable.save();

        LookAndFeelFolderProperties props = new LookAndFeelFolderProperties(c);
        assertEquals(dateDisplay, props.getDefaultDateFormat());
        assertEquals(dateDisplay, props.getDefaultDateFormatStored());
        assertEquals(dateDisplay + " " + timeDisplay, props.getDefaultDateTimeFormat());
        assertEquals(dateDisplay + " " + timeDisplay, props.getDefaultDateTimeFormatStored());
        assertEquals(timeDisplay, props.getDefaultTimeFormat());
        assertEquals(timeDisplay, props.getDefaultTimeFormatStored());
        assertEquals(numberDisplay, props.getDefaultNumberFormat());
        assertEquals(numberDisplay, props.getDefaultNumberFormatStored());
        assertEquals(dateParsing, props.getExtraDateParsingPattern());
        assertEquals(dateParsing, props.getExtraDateParsingPatternStored());
        assertEquals(dateParsing + " " + timeParsing, props.getExtraDateTimeParsingPattern());
        assertEquals(dateParsing + " " + timeParsing, props.getExtraDateTimeParsingPattern());
        assertEquals(timeParsing, props.getExtraTimeParsingPattern());
        assertEquals(timeParsing, props.getExtraTimeParsingPattern());
        assertEquals(restricted, props.areRestrictedColumnsEnabled());
        assertEquals(restricted, props.areRestrictedColumnsEnabledStored());
    }

    private void testClearAndInherit(Container child, Container parent)
    {
        WriteableFolderLookAndFeelProperties writeable = new WriteableFolderLookAndFeelProperties(child);
        writeable.clear(true);
        writeable.save();
        LookAndFeelProperties parentProps = LookAndFeelProperties.getInstance(parent);
        LookAndFeelProperties childProps = LookAndFeelProperties.getInstance(child);
        testFolderPropertiesInherited(parentProps, childProps);
    }
}
