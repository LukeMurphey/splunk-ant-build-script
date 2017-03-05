package net.lukemurphey.splunkbuild;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// For SSL overriding
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

public class SplunkTask extends Task {

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
     * Get the bytes for a form object.
     */
    public byte[] getFormBytes(HashMap<String, String> params) throws UnsupportedEncodingException{

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
     * Get the encoded data for the Splunk login form.
     */
    public byte[] getLoginFormBytes(String cval, String username, String password) throws UnsupportedEncodingException{
    	
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("cval", cval);
        params.put("username", username);
        params.put("password", password);
        params.put("set_has_logged_in", "false");
        params.put("return_to", "/en-US/");
        
        return this.getFormBytes(params);
    }

    /**
     * Get the form key from Splunk.
     */
     public String getFormKey(String url) throws IOException, MalformedURLException, UnsupportedEncodingException{

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

        return formKey;
     }

    /**
     * Output the result that came from an HTTP connection. This is useful when the response indicates a reason for the failure.
     */
    public void outputResponse(HttpURLConnection connection) throws UnsupportedEncodingException, IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
        
        for (String line = null; (line = reader.readLine()) != null;) {
        	log(line);
        }
    }

    /**
     * Get the cval token from Splunk.
     */
    public String getCval(String url) throws IOException, MalformedURLException, UnsupportedEncodingException{

    	String cval = null;
    	
        // Make sure cookies are on
        CookieHandler.setDefault( new CookieManager( null, CookiePolicy.ACCEPT_ALL ) );
    	
        // Make the connection
    	HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    	
    	List<String> cookies = connection.getHeaderFields().get("Set-Cookie");
    	
    	// Stop if we couldn't get a connection
    	if(cookies == null){
    		handleErrorOutput("Connection to Splunk failed");
    		return "";
    	}
    	
    	int responseCode = connection.getResponseCode();
        
        if(responseCode != 200){
        	handleErrorOutput("Connection to Splunk failed (response code was " + responseCode + ")");
        	return "";
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

        return cval;
    }

    /**
     * Log into SplunkWeb. This has a side-effect of having HttpURLConnection class retain the cookie necessary to be considered logged in.
     **/
    public void loginToSplunk(String url, String username, String password) throws BuildException, IOException, MalformedURLException {

    	// Step 1: Perform a request in order to get the cval cookie
    	String cval = this.getCval(url);
    	
    	// Step 2: Make the parameters that are necessary for doing the login post
        HttpURLConnection loginConnection = (HttpURLConnection) new URL(url + "/en-US/account/login").openConnection();
        loginConnection.setRequestMethod("POST");
        
        loginConnection.setRequestProperty( "Content-type", "application/x-www-form-urlencoded");
        loginConnection.setRequestProperty( "Accept", "*/*" );
        loginConnection.setDoOutput(true);
        loginConnection.getOutputStream().write(getLoginFormBytes(cval, username, password));
        
        int responseCode = loginConnection.getResponseCode();

        if(responseCode == 401){
        	throw new BuildException("Bump request could not be done since the account could not be authenticated (returned " + responseCode + "); make sure the account \"" + username + "\" can authenticate to " + url);
        }
    }

    /**
     * Remove the trailing slash from the URL (if necessary)
     */
    public String removeTrailingSlash(String url){
        if(url.substring(url.length() - 1) == "/"){
    		return url.substring(url.length() - 2);
    	}
        else{
            return url;
        }
    }

    /**
     * Install SSL validator.
     */
    public void installSSLValidator() throws NoSuchAlgorithmException, KeyManagementException{

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };
 
        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
 
        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }



}