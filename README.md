# splunk-ant-build-script

## What is this?

This is a build script that will take source-code for a Splunk app and make it into a package. It also has other tasks for things like deploying the app to a Splunk install.

## Why should I use this build script?

This build script will make developing and deploying Splunk apps easier by:

* *Creating installable Splunk packages* (from a directory with the source code)
* *Providing a simple method to deploy the changes to an app to a Splunk instance in seconds*; this is useful during development since it will tell the Splunk instance to refresh so that your new content can be tested in real-time
* *Providing mechanisms for deploying Splunk apps to a production box*. This allows you to install the app remotely and restart the Splunk install accordingly using a Continuous Deployment system.
* *Improving the performance of apps* with Javascript views by minifying the CSS and JS files

Here are some examples:

<pre>
ant package
</pre>

This will create a package of the app in the file "network_tools.tar.gz" (the network toolkit is used in this example):

<pre>
package:
      [tar] Building tar: /var/folders/61/4v1t_czj3m13m1wbhtlytc00r4lc79/T/lmurphey/network_tools.tar
     [gzip] Building: /Users/lmurphey/Downloads/network_tools.tar.gz
   [delete] Deleting directory /var/folders/61/4v1t_czj3m13m1wbhtlytc00r4lc79/T/lmurphey
     [echo] App network_tools build 1487971707 created: /Users/lmurphey/Downloads/network_tools.tar.gz

BUILD SUCCESSFUL
Total time: 0 seconds
</pre>

## Requirements

This build script is based around git (e.g. Github, Gitlab, Atlassian Bitbucket). Thus, you will need to customize it if you want to use another type of source code repository.

## How do I use it?

### Install Java and Ant

Install Java if you don't have it installed. There is a good chance you already have it so you may want to check.

Next, install Ant

### Download the build script

Download the file at https://gist.github.com/LukeMurphey/8fd02337805ae8762afb. Place the resulting file in the source-code repository (at the root of the directory).

### Create a project

Now, initialize the project but running the following from your source-code directory:

<pre>
ant -f basebuild.xml
</pre>

This will run through a short wizard to initialize your project. The script will prompt you for a name for your project. The name should be the folder name of the app (like "website_monitoring"), not the human readable name (which would be something like "Website Monitoring").

Once it is done, it will create a series of files for you.

Run the following command to test the building of your app:

<pre>
ant
</pre>

You should see something noting that your app package was created.

### Put in your source code, build your app

Now, make or place your source-code for app in the src/ directory. The code in here should correspond to the contents within your app folder. Thus, your source-code directories will eventually have a structure something like this:

* **lib/** (this is where build dependencies will go, they will be downloaded automatically for you)
* **src/** (this is where you app code will go)
   * **default/** (where conf files will go)
   * **appserver/** (where static web resources go)
   * **lookups/** (where lookup files go)
* **basebuild.xml** (contains the main targets you need for building your app that will be used by build.xml)
* **build.xml** (the main build script; place overrides of the build script in this file)
* **default.properties** (where properties go that need to be included in the source-code repository)
* **local.properties** (where properties go that should _not_ be included in the source-code repository)
