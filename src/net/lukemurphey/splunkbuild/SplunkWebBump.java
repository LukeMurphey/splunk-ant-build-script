package net.lukemurphey.splunkbuild;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tools.ant.BuildException;

import net.lukemurphey.splunkbuild.SplunkTask;

/**
 * This class performs a bump operation against SplunkWeb in order to invalidate the cached web-resources.
 * 
 * @author luke.murphey
 *
 */
public class SplunkWebBump extends SplunkTask {

    /**
     * Perform the bump operation.
     */
    public void execute() {
    	
        try{
        	bumpVersion(this.splunkweburl, this.username, this.password);
        }
        catch(BuildException e){
        	throw e;
        }
        catch(Exception e){
        	handleErrorOutput("Unable to bump Splunk web: " + e.toString());
        }
        
    }

    /**
     * Get the encoded data for the Splunk bump form.
     */
    public byte[] getBumpFormBytes(String formKey) throws UnsupportedEncodingException{
    	
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("splunk_form_key", formKey);
        
        return this.getFormBytes(params);
    }

    /**
     * Perform a bump operation against Splunk.
     */
    public void bumpVersion(String url, String username, String password) throws Exception {
    	
        installSSLValidator();

        try{
            // Remove the trailing slash on the URL if necessary
            url = removeTrailingSlash(url);
            
            // Step 1: Log into Splunk
            loginToSplunk(url, username, password);
            
            // Step 3: Do a GET to retrieve the form key
            String formKey = getFormKey(url);
            
            // Step 4: Do the actual bump
            HttpURLConnection bumpConnection = (HttpURLConnection) new URL(url + "/en-US/_bump").openConnection();
            bumpConnection.setRequestMethod("POST");
            
            bumpConnection.setRequestProperty( "Content-type", "application/x-www-form-urlencoded");
            bumpConnection.setRequestProperty( "Accept", "*/*" );
            bumpConnection.setDoOutput(true);
            bumpConnection.getOutputStream().write(getBumpFormBytes(formKey));
            
            int responseCode = bumpConnection.getResponseCode();
            
            if(responseCode == 401){
                handleErrorOutput("Bump request failed (unauthorized)");
                return;
            }
            
            // Get the new bumped value
            Pattern bumpValueRegex = Pattern.compile("Current version: ([0-9]+)");
            String bumpValue = null;
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(bumpConnection.getInputStream(), "UTF-8"));
            
            for (String line = null; (line = reader.readLine()) != null;) {
                
                // Get the bump value
                Matcher m = bumpValueRegex.matcher(line);
                
                if (m.find()) {
                    bumpValue = m.group(1);
                }
            }
            
            log("Splunk Web bumped to " + bumpValue);

        }
        catch(SocketException e){
            throw new BuildException("Unable to connect to Splunk (connection failed)");
        }
    	
    }
    
    /**
     * Perform a bump operation against Splunk.
     * 
     * @throws Exception
     */
    public void bumpVersion() throws Exception {
    	bumpVersion("https://localhost:8089", "admin", "changeme");
    }
}