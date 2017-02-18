import org.apache.tools.ant.Task;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class performs a bump operation against SplunkWeb in order to invalidate the cached web-resources.
 * 
 * @author luke.murphey
 *
 */
public class SplunkWebBump extends Task {

	String username = "admin";
	String password = "changeme";
    String splunkweburl = "http://localhost:8000";
    
    /**
     * Set the URL for SplunkWeb
     * 
     * @param url The URL for SplunkWeb.
     */
    public void setURL(String url) {
    	this.splunkweburl = url;
    }
    
    /**
     * Sets the username for authenticating to Splunk.
     * 
     * @param username The Splunk username
     */
    public void setUsername(String username) {
    	this.username = username;
    }
    
    /**
     * Sets the password for authenticating to Splunk.
     * 
     * @param password The Splunk password
     */
    public void setPassword(String password) {
    	this.password = password;
    }

    /**
     * Perform the bump operation.
     */
    public void execute() {
    	
        try{
        	bumpVersion(this.splunkweburl, this.username, this.password);
        }
        catch(Exception e){
        	handleErrorOutput("Unable to bump Splunk web");
        }
        
    }
    
    /**
     * Get the encoded data for the Splunk login form.
     */
    public byte[] getLoginFormBytes(String cval, String username, String password) throws UnsupportedEncodingException{
    	
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("cval", cval);
        params.put("username", username);
        params.put("password", password);
        params.put("set_has_logged_in", "false");
        params.put("return_to", "/en-US/");
        
        StringBuilder postData = new StringBuilder();
        
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
        }
        
        return postData.toString().getBytes("UTF-8");
    }
    
    /**
     * Get the encoded data for the Splunk bump form.
     */
    public byte[] getBumpFormBytes(String formKey) throws UnsupportedEncodingException{
    	
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("splunk_form_key", formKey);
        
        StringBuilder postData = new StringBuilder();
        
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
        }
        
        return postData.toString().getBytes("UTF-8");
    }
    
    /**
     * Perform a bump operation against Splunk.
     */
    public void bumpVersion(String url, String username, String password) throws Exception {
    	
    	// Remove the trailing slash on the URL if necessary
    	if(url.substring(url.length() - 1) == "/"){
    		url = url.substring(url.length() - 2);
    	}
    	
    	// Step 1: Perform a request in order to get the cval cookie
    	String cval = null;
    	
        // Make sure cookies are on
        CookieHandler.setDefault( new CookieManager( null, CookiePolicy.ACCEPT_ALL ) );
    	
        // Make the connection
    	HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    	
    	List<String> cookies = connection.getHeaderFields().get("Set-Cookie");
    	
    	// Stop if we couldn't get a connection
    	if(cookies == null){
    		handleErrorOutput("Connection to Splunk failed");
    		return;
    	}
    	
    	int responseCode = connection.getResponseCode();
        
        if(responseCode != 200){
        	handleErrorOutput("Connection to Splunk failed (response code was " + responseCode + ")");
        	return;
        }
    	
    	// Get the cval from the cookie
    	Pattern cvalRegex = Pattern.compile("cval=([0-9]+); Path=/en-US/account/");
    	
    	for(int c = 0; c < cookies.size(); c++){
    		
    		// Get the cval value
        	Matcher m = cvalRegex.matcher(cookies.get(c));
        	
            if (m.find()) {
            	cval = m.group(1);
            }
    	}
    	
    	// Step 2: Make the parameters that are necessary for doing the login post
        HttpURLConnection loginConnection = (HttpURLConnection) new URL(url + "/en-US/account/login").openConnection();
        loginConnection.setRequestMethod("POST");
        
        loginConnection.setRequestProperty( "Content-type", "application/x-www-form-urlencoded");
        loginConnection.setRequestProperty( "Accept", "*/*" );
        loginConnection.setDoOutput(true);
        loginConnection.getOutputStream().write(getLoginFormBytes(cval, username, password));
        
        responseCode = loginConnection.getResponseCode();
        
        if(responseCode == 401){
        	handleErrorOutput("Login to Splunk failed");
        	return;
        }
        
    	// Step 3: Do a GET to retrieve the form key
        HttpURLConnection bumpConnection = (HttpURLConnection) new URL(url + "/en-US/_bump").openConnection();
        
        Pattern formKeyRegex = Pattern.compile("name=\"splunk_form_key\" value=\"([0-9]+)\"");
        String formKey = null;
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(bumpConnection.getInputStream(), "UTF-8"));
        
        for (String line = null; (line = reader.readLine()) != null;) {
        	
        	// Get the form-key value
        	Matcher m = formKeyRegex.matcher(line);
        	
            if (m.find()) {
            	formKey = m.group(1);
            }
        }
        
        // Step 4: Do the actual bump
        bumpConnection = (HttpURLConnection) new URL(url + "/en-US/_bump").openConnection();
        bumpConnection.setRequestMethod("POST");
        
        bumpConnection.setRequestProperty( "Content-type", "application/x-www-form-urlencoded");
        bumpConnection.setRequestProperty( "Accept", "*/*" );
        bumpConnection.setDoOutput(true);
        bumpConnection.getOutputStream().write(getBumpFormBytes(formKey));
        
        responseCode = bumpConnection.getResponseCode();
        
        if(responseCode == 401){
        	handleErrorOutput("Bump request failed (unauthorized)");
        	return;
        }
        
        // Get the new bumped value
        Pattern bumpValueRegex = Pattern.compile("Current version: ([0-9]+)");
        String bumpValue = null;
        
        reader = new BufferedReader(new InputStreamReader(bumpConnection.getInputStream(), "UTF-8"));
        
        for (String line = null; (line = reader.readLine()) != null;) {
        	
        	// Get the bump value
        	Matcher m = bumpValueRegex.matcher(line);
        	
            if (m.find()) {
            	bumpValue = m.group(1);
            }
        }
        
        log("Splunk Web bumped to " + bumpValue);
    	
    }
    
    /**
     * Perform a bump operation against Splunk.
     * 
     * @throws Exception
     */
    public void bumpVersion() throws Exception {
    	bumpVersion("http://localhost:8000", "admin", "changeme");
    }
}