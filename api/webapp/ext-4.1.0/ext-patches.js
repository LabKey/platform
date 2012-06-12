/* Apply patches found for Ext 4.1.0 here */

// Set USE_NATIVE_JSON so Ext.decode and Ext.encode use JSON.parse and JSON.stringify instead of eval
//Ext4.USE_NATIVE_JSON = true;

// set the default ajax timeout from 30's to 5 minutes
Ext4.Ajax.timeout = 5 * 60 * 1000;