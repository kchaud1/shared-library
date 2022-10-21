 /**
   * Search lookupMap to parse list of keyMap by searching all keys in keyList
   * This is useful when lookupMap has key prefix with dot (.) and you want to use values based on KeyList
   *         i.e if key is 'a', lookupMap may have key(s) defined as a.x, a.y and so on
   * Example:
   *        regions = ['us-east-1', 'us-east-2', 'us-west-1', 'ap-southeast-1', 'ap-southeast-2',""]
   *        config = {us-east-1.bucket=east-1-bucket, us-east-1.agent=centos, us-east-2.bucket=east-2-bucket, us-east-2.agent=windows, ap-southeast-1.bucket=apsoutheast1-bucket, sysID=1d564s, env=e1,e3}
   *
   *        Output:
   *        println(parseMapForKeyList(regions,config)) will return below LinkedHashMap
   *        {us-east-1={bucket=east-1-bucket, agent=centos}, us-east-2={bucket=east-2-bucket, agent=windows}, ap-southeast-1={bucket=apsoutheast1-bucket}}
   *
   *        println(parseMapForKeyList(regions,config)["us-east-2"]["bucket"]) will return east-2-bucket
   *
   * @param keyList - A List which is suppose to be searched for creating a list of keyMap"
   * @param lookupMap - A Map where search will happen hence lookupMap"
   */
def parseMapForKeyList(List keyList, Map lookupMap){
  def outputMap = [:]
  def keyMap = [:]
  keyList.each{ keyListItem -> 
    if (!(keyListItem.trim() == "")){
       keyMap = [:]
       lookupMap.keySet().each{ lookupMapKey->
         if (lookupMapKey.contains("${keyListItem}.")) {
             keyMap.put(lookupMapKey.replaceAll("${keyListItem}.",""), lookupMap["${lookupMapKey}"])
         }
       }
       if (keyMap.size() > 0){ outputMap.put(keyListItem,keyMap) }
    }
  }
  return outputMap
}

 /**
   * Return list of file properties i.e. "path", "dir", "filename", "name", "extn".
   * Note:  This function does not check if provided file path is valid or not.
   *
   * Example: 
   *        utilities.getFileProperties("./web server/ui/service/web db.properties")
   *        [path="./web server/ui/service/web db.properties", dir="./web server/ui/service", "filename"="web db.properties", "name"="web db", "extn"="properties"]
   *        
   *        utilities.getFileProperties("catalina.out")
   *        [path="./web server/ui/service/web db.properties", dir="./web server/ui/service", "filename"="web db.properties", "name"="web db", "extn"="properties"]
   *
   *        utilities.getFileProperties("$HOME/app-home/catalina.out").path      returns "$HOME/app-home/catalina.out"
   *        utilities.getFileProperties("$HOME/app-home/catalina.out").dir       returns "$HOME/app-home"
   *        utilities.getFileProperties("$HOME/app-home/catalina.out").filename  returns "catalina.out"
   *
   * @param filePath - A string representing file path i.e. "/path/to/file.txt"
   */
def getFileProperties(String filePath){
   try {
        def dirname  = filePath.contains("/")? filePath.substring(0,filePath.lastIndexOf("/")) : "./"
        def filename = filePath.contains("/")? filePath.substring(filePath.lastIndexOf("/")+1) : "${filePath}"
        def name=filename.substring(0,filename.lastIndexOf("."))
        def extension=filename.substring(filename.lastIndexOf(".")+1)
        def fileProperty = ["path": "${filePath}", "dir": "${dirname}", "filename": "${filename}", "name": "${name}", "extn": "${extension}"]
        return fileProperty
   } 
   catch(Exception error) {
        pipelineLogger.error("Invalid file path: '${filePath}'")
   }
}

  /**
    * Trim all value(s) and remove key(s) with null value(s) from config map 
    * Example: 
    *        employee = ["name": "Bob", "city": "Minneapolis ", "phone": "", "id": "   ", "role": " developer "]
    *        employee = trimConfig(employee)  
    *
    *        Output:
    *              # println employee
    *              ["name": "Bob", "city": "Minneapolis", "role": "developer"]
    *
    * @param mapName - A Map object name
    */
def trimMap(Map mapObj){
  def tmpMapObj = [:]
  mapObj.each{ key, value -> 
          if (!"$value".trim().isEmpty()){
              tmpMapObj.put(key, "$value".trim()) 
          } 
  }
  mapObj = [:];  mapObj << tmpMapObj
  return mapObj
}

 /**
   * Search all files recursively in a given path in shell environment and returns a list.
   *      - default path: current directory i.e. "."
   *      - default search type is "name". 
   *      - You may do search based on a shell pattern or regex. Possible values are 'name' or 'iname' or 'regex' or 'iregex'. 
   *      - You may also chose whether your search is case sesivite of not using patternType parameter
   *
   * Syntax: findFilesWithPattern(pattern, path, searchType, depth)
   *
   * Example:
   *      1. findFilesWithPattern("*pom.xml") - list all pom.xml files in current working directory recursively
   *      2. findFilesWithPattern("*pom.xml", "", "", 1) - list all pom.xml files in current working directory only (depth=1) 
   *      3. findFilesWithPattern(".*ya?ml", ".", "iregex") - list all files i.e. YAML, yaml, YML and yml in current working directory recursively
   *      4. findFilesWithPattern(".*ya?ml", ".", "iregex", 1) - list all files i.e. YAML, yaml, YML and yml in current working directory only (depth=1) 
   *      5. findFilesWithPattern(".*b.*k", "src/", "regex")  - list all files with backend or backend-web string under path src/ with search type 'regex'
   *      6. findFilesWithPattern("*foo*", "src/")  - list all files with foo string under path src/; default search type 'name'
   *      7. findFilesWithPattern("*foo*") or findFilesWithPattern("*foo*","") or findFilesWithPattern("*foo*", "","name")  - list all files with foo string in current directory
   * 
   * @param pattern - patter string to match/search
   * @param path - directory path, default is shell current directory i.e. "."
   * @param searchType - This decides what type of search you would be needing; possible values are 'name' or 'iname' or 'regex' or 'iregex'
   * @param maxDepth - String value for -maxdepth in find function. 
   */
  def findFilesWithPattern(def pattern, def path = ".", def searchType = "name", def maxDepth){
    path=path.trim(); if (path == "") {path = "."}
    if (isUnix()){
        try{
            if (searchType.trim() == "") {searchType = "name"}
            def maxDepthStr = ""; if ("${maxDepth}".isInteger()) { maxDepthStr = "-maxdepth ${maxDepth}" }
            def files = sh(returnStdout: true, script: "find '$path' $maxDepthStr -$searchType '$pattern' -type f")
            def filesList = parseListFromString(files).sort()
            //pipelineLogger.info("File Search result for '${pattern}' in path '${path}' " + ("${maxDepth}".isInteger()? "with depth ${maxDepth} " : "") + "is ${filesList}")
            return filesList
         } 
         catch(Exception error) {
            pipelineLogger.error("Could not find files with pattern '$pattern' under path '${path}'")
         }
    }
    else{
      pipelineLogger.warn("\nThe function utilities.findFilesWithPattern() should be used on only on Unix-like build system.\n")
    }   
  }

/**
  * Utility function to find files in the current directory that match the given filename patterns, and return an array of them.
  * 
  * Returns a list of matching file paths starting with the path parameter (i.e. if path is the current directory, returned files will start with "./").  Returns
  * an empty array if no matching files are found, or if the search pattern is invalid.
  *
  * Examples:
  *   1. findFilesByName() - finds all plain files in the current directory
  *   2. findFilesByName(".", ["*.yml", "*.yaml"]) - finds all YAML files the current directory
  *   3. findFilesByName("somedir", ["*.txt"], 3) - finds all text files in the somedir directory and up to two subdirectory levels below somedir
  *   4. findFilesByName("somedir", ["file2.txt", file1.txt]) - finds file2.txt, then file1.txt in somedir directory (returned array order is file2.txt, file1.txt)
  *
  * @param path     - the path to search.
  * @param patterns - a list of patterns to be searched for, using the UNIX find operation's -name switch.  May use wildcards.  E.g. ["*.yml", "*.yaml"]
  * @param depth    - the search depth.  A depth of 1 searches the 'path' directory only.  A depth of 2 searches the path directory and immediate subdirectories, etc.
  *
  * Function behaviors:
  * - If the path argument is null or blank, the current directory is used
  * - Files are added to the returned array only once, even if multiple patterns match the file name
  * - Patterns are processed in the order given, and results for each pattern are sorted.  E.g. if the current directory contains a.yml, a.YAML, b.yml, b.YAML and
  *   given patterns are ["*.yml", "*.YAML"], the returned array will contain ['a.yml', 'b.yml', 'a.YAML', 'b.YAML'].
  * - Default argument values cause the function to find all plain files in the current directory only
  */
def findFilesByName(String path=".", List<String> patterns=["*"], int depth=1) {
   def files = []
   if (path == null || "".equals(path.trim())) {
      path = "."
   }
   for (pattern in patterns) {
      try {
         def findOutput = sh(returnStdout: true, script: "find '$path' -maxdepth $depth -name '$pattern' -type f -print | sort")
         if (!"".equals(findOutput)) {
            findOutput.split("\n").each { file ->
               if(!files.contains(file)) {
                  files.add(file)
               }
            }
         }
      }
      catch(err) {
         pipelineLogger.warn("findFilesByName: find operation failed for pattern $pattern with error: " + err)
      }
   }
   return files
}

/**
  * utility function that returns the base file name (withouth extension), given a path to a file.
  * 
  * Example:
  *    1. getBaseName('./mydir/myFile.txt') - returns "myFile"
  *
  * @param path - the path to the file
  */
def getBaseName(String path) {
   def filename = getFileName(path)
   def basename = filename.contains(".") ? filename.substring(0, filename.lastIndexOf(".")) : filename    // remove extension
   return basename
}

/**
  * utility function that returns the file name (with extension), given a full or relative path to a file.
  *
  * Example:
  *    1. getFileName('./mydir/myFile.txt') - returns "myFile.txt"
  *
  * @param path - the path to the file
  */
def getFileName(String path) {
   def filename = path.contains("/") ? path.substring(path.lastIndexOf("/")+1) : path                 // remove path part
   return filename
}
 
 /**
   * Search all directories recursively in a given path  in shell environment and returns a list.
   *      - default path: current directory i.e. "."
   *      - default search type is "name". 
   *      - You may do search based on a shell pattern or regex. Possible values are 'name' or 'iname' or 'regex' or 'iregex'. 
   *      - You may also chose whether your search is case sesivite of not using patternType parameter
   *
   * Syntax: findDirsWithPattern(pattern, path, searchType, depth)
   *
   * Example:
   *      1. findDirsWithPattern(".*b.*k", "src/", "regex")  - list all directories with backend or backend-web string under path src/ with search type 'regex'
   *      2. findDirsWithPattern("*foo*", "src/")  - list all directories with foo string under path src/; default search type 'name'
   *      3. findDirsWithPattern("*foo*", "src/", "", 2)  - list all directories with foo string under path src/ with maxdepth 2; default search type 'name'
   *      4. findDirsWithPattern("*foo*") or findFilesWithPattern("*foo*","") or findFilesWithPattern("*foo*", "","name")  - list all directories with foo string in current directory
   * 
   * @param pattern - patter string to match/search
   * @param path - directory path, default is shell current directory i.e. "."
   * @param searchType - This decides what type of search you would be needing; possible values are 'name' or 'iname' or 'regex' or 'iregex'
   * @param maxDepth - String value for -maxdepth in find function. 
   */
  def findDirsWithPattern(def pattern, def path = ".", def searchType = "name", def maxDepth){
    path=path.trim(); if (path == "") {path = "."}
    if (isUnix()){    
        try{
            if (searchType.trim() == "") {searchType = "name"}
            def maxDepthStr = ""; if ("${maxDepth}".isInteger()) { maxDepthStr = "-maxdepth ${maxDepth}" }
            def dirs = sh(returnStdout: true, script: "find '$path' $maxDepthStr -$searchType $pattern -type d")
            def dirsList = parseListFromString(dirs).sort()
            //pipelineLogger.info("Directory Search result for '${pattern}' in path '${path}' " + ("${maxDepth}".isInteger()? "with depth ${maxDepth} " : "") + "is ${dirsList}")
            return dirsList
        } 
        catch(Exception error) {
            pipelineLogger.error("Could not find directories with pattern '$pattern' under path '${path}'")
        }
    }
    else{
      pipelineLogger.warn("\nThe function utilities.findDirsWithPattern() should be used on only on Unix-like build system.\n")
    } 
  }

  /**
    * Single place to handle logic for parsing list from string defined in properties file
    *  String can be formatted any of the following ways:
    *   "element1 element2 element3" or
    *   "element1,element2, element3" or
    *   element1,element2 element3
    *
    *   Quotation marks (") will be removed, and elements can be delimited by comma (,) or whitespace or both.
    *   All the above strings would return a list formatted as:
    *   ["element1", "element2", "element3"]    
    *
    * @param inputString - String containing a list
    * @param serviceNames - List of service names parsed from config
    */
def parseListFromString(String inputString) {
    def array = inputString.replaceAll("\"\\\\","").replaceAll(","," ").split()
    def list =[]
    for (element in array) {
        list.add(element)
    }
    return list
}

  /**
    * Check whether the defined awsVars are defined in properties.  Will fail the pipeline if any do not exist
    *
    * @param config - Map of properties read in from properties file
    */
def checkAwsVars(Map config) {
    def awsVars = ["awsAccountNumber","awsRegion","awsUser"] 
    for ( var in awsVars) {
        checkConfigVarExistsNotEmpty(config, var)
    }
}

  /**
    * Check whether the specified var is defined in properties.  Will fail the pipeline if it is not defined
    *
    * @param config - Map of properties read in from properties file
    */
def checkConfigVarExistsNotEmpty(Map config, String var) {

    try {
        assert config.containsKey(var) : ".env does not contain '${var}'.  Pipeline cannot proceed."
        assert config[var] != ".env contains '${var}', but it is set to ''.  Pipeline cannot proceed."
    }
    catch (Exception e) {
        pipelineLogger.error("Based on your pipeline, the variable '${var}' is required but is not defined.  Please define this in your .env")
        throw e
    }
    return config[var]
}

  /**
    * Generate the standard docker images name for a specific service.  
    *  Image name format is as follows:
    *   <bitbucket-project-name>/<bitbucket-repo-name>/<branch-name>/<service-name>
    *  This will evaluate to something like:
    *   brc/dpe-bloomreachexperience/develop/cms
    *
    *   Notes:
    *   -By default, when application team defines services in properties file as follows:
    *       serviceNames=cms,site
    *   Then docker images would be named:
    *       -brc/dpe-bloomreachexperience/develop/cms
    *      -brc/dpe-bloomreachexperience/develop/site
    *   However, application team can override <service-name> by setting the following variable(s):
    *       cms_serviceName=cms-test
    *       site_servicename=abcxyz
    *   Then docker images would be respectively named:
    *       brc/dpe-bloomreachexperience/develop/cms-test
    *       brc/dpe-bloomreachexperience/develop/abcxyz  
    *
    *   -Neither Tag nor registry is included in image name, as these are calculated separately.
    *
    *
    * @param serviceName - Name of service defined in config
    * @param config - Map of properties read in from properties file
    * @return imagename - String formatted as proper docker image name
    */
def generateDockerImageName(String serviceName, Map config = [:]) {

    def dockerPushRegistryUrl=runDockerPush.getDockerPushUrl(config)
    def imagePath=generateDockerImagePath(config)
    def imageName=getServiceName(serviceName, config)
    //QuickFIx for ECR images, this is causing duplicate name 07162021
    //def dockerImageName="${dockerPushRegistryUrl}/${imagePath}/${imageName}".toLowerCase()
    def dockerImageName="${imagePath}/${imageName}".toLowerCase()

    withFolderProperties{
		if ( env.baseImage ) {
            parseMapForKeyList([serviceName],config).each{ service, image ->
                dockerImageName = image['imageName'].toLowerCase()
            }
        }
    }

    pipelineLogger.debug("dockerImageName = ${dockerImageName}")
    return dockerImageName
}

/*
// Since devs can set custom names for the services, this is required to translate them
// e.g: 
serviceNames = a,b,c
a_serviceName=xyz
*/
def getServiceName(String serviceName, Map config = [:]){
    def final CUSTOM_SERVICENAME_STRING = "_serviceName"
    def imageServiceName=serviceName

    def customServiceNameString="${serviceName}${CUSTOM_SERVICENAME_STRING}"
    def customServiceName=config[customServiceNameString]

    if (customServiceName != null && customServiceName != "") {
        imageServiceName = customServiceName
        pipelineLogger.debug("found '${serviceName}${CUSTOM_SERVICENAME_STRING}' in config - imageServiceName set to ${imageServiceName}")

    }
    return imageServiceName.toLowerCase()
}


def generateProjectNameVar(Map config) {
    String projectNameVar = env.gitProjectName
    if (projectNameVar.contains("~")){
        //This is building from personal repo
        //"~stata -> "personal-repos/stata"
        projectNameVar="personal-repos/${projectNameVar}".replaceAll("~","")
    }
    return projectNameVar
}

/*
//This generates the custom path mentioning bitbucket properties to ensure all images have unique names
*/
def generateDockerImagePath(Map config = [:]) {
    String projectNameVar = generateProjectNameVar(config)
    def path="${projectNameVar}/${env.gitRepoName}/${env.gitBranchName}".toLowerCase()
    //pipelineLogger.debug("generateDockerImagePath = ${path}")
    return path
}

/*
//Prefix includes <registry>/<custom-path>
// All images built will be pushed with the same prefix so this is not unique to a specific service
*/
def generateDockerImagePrefix(Map config = [:]) {

    def dockerRegistryUrl=runDockerPush.getDockerPushUrl(config)
    def dockerImagePath=generateDockerImagePath(config)

    def imagePrefix="${dockerRegistryUrl}/${dockerImagePath}"
    pipelineLogger.debug("generateDockerImagePrefix = ${imagePrefix}")
    return imagePrefix
}

  /**
    * Not currently used.  Could be used in future to support different upstream registries 
    *   (i.e. dockerfiles referencing images in different registries) within the same branch
    *
    * @param config - Map of properties read in from properties file
    * @param serviceNames - List of service names parsed from properties
    */
def generateUpstreamImageNameToRegistryMap(Map config, List serviceNames) {
    def upstreamRegistryKey="upstreamRegistry"
    def customUpstreamRegistryKey="_${upstreamRegistryKey}"

    def defaultUpstreamRegistry="dockerdev.hdfc.com"


    def upstreamRegistry=""
    if (config.containsKey(upstreamRegistryKey)) {
        upstreamRegistry=config[upstreamRegistryKey]
    }

    serviceNameToUpstreamRegistryMap =[:]
    for (serviceName in serviceNames) {
        def customUpstreamRegistry=upstreamRegistry
        if (config.containsKey("${serviceName}${customUpstreamRegistryString}")){
            customUpstreamRegistry=config["${serviceName}${customUpstreamRegistryString}"]
        }
        if ( customUpstreamRegistry == "" ){
            customUpstreamRegistry = defaultUpstreamRegistry
        }
        serviceNameToUpstreamRegistryMap.put(serviceName,customUpstreamRegistry)
    }    

    return serviceNameToUpstreamRegistryMap
}

  /**
    * Generates string for Jenkins to login to ECR
    *
    * @param config - Map of properties read in from properties file
    * @return credsString - String formatted as docker registry credentials
    */
def getAwsEcrCredsString(Map config){
    //checkRequiredAwsVariables(config)  funtion is not yet implemented, this was just an place holder
    def credsString = "ecr:${config["awsRegion"]}:${config["awsUser"]}"
    return credsString
}


  /**
    * Finds location to push docker images to and generates the applicable credential string for Jenkins
    *
    * @param config - Map of properties read in from properties file
    * @return credsString - String formatted as docker registry credentials
    */
def getDockerPushCredentialsSetName(Map config) {

    def credsString=""
    assert config.containsKey("dockerRegistryPushType") : "ERROR: dockerRegistryPushType is not defined.  Pipeline will exit."

    switch (config["dockerRegistryPushType"]){
        case "ecr":
            credsString=getAwsEcrCredsString(config)
            break
        case "hdfc":
            //check branch or config to figure out whether to push to dockerdev or dockerrelease
            //if dockerdev -> credsString=env.DOCKERDEV_CREDS_RW
            //if dockerrelease -> credsString=env.DOCKERRELEASE_CREDS_RW

            //Disregard above comments, both dockerdev and dockerrelease are using the same credentials
            credsString=env.JENKINS_CREDENTIALS_RO
            break
        default:
            echo "[ERROR] dockerRegistryPushType found in .env but it does not match any of the known registry types"
            break
    }

    assert credsString != "" : "ERROR: Jenkins credentials string for pushing to docker registry is empty.  This should not happen, pipeline will exit."
    return credsString
}


  /**
    * Based on the registryUrl, calculates the appropriate credential string based on properties
    * @param config - Map of properties read in from properties file
    * @param registryUrl - Docker registry Url.  Can be private on prem registry or ECR url
    * @param user - If set (and not equal to ""), that credential set will be used
    * @return credsString - String formatted as docker registry credentials
    */
def getDockerPullCredentialsSetName(Map config, String registryUrl, String user="") {

    def AWS_REGISTRY_PATTERN = ~/^\d*\.dkr\.ecr\.\w{2}-.*-\d{1,2}\.amazonaws\.com/
    def globalVar = readYaml text: libraryResource('globalvars.yml')
    def registryUrlToCredsSetMap=[
        'dockerdev.qa.hdfc.com':"${env.JENKINS_CREDENTIALS_RO}",
        'dockerdev.hdfc.com':"${env.JENKINS_CREDENTIALS_RO}",
        'dockerrelease.hdfc.com':"${env.JENKINS_CREDENTIALS_RO}",
        // revisit this. Quick fix for empty values - gourav 02/08/2021
        //"${globalVar.docker.registry.qa}":"${env.JENKINS_CREDENTIALS_RO}",
        //"${globalVar.docker.registry.develop}":"${env.JENKINS_CREDENTIALS_RO}",
        //"${globalVar.docker.registry.release}":"${env.JENKINS_CREDENTIALS_RO}",
        "${env.dockerReleaseRegistryUrl}":"${env.JENKINS_CREDENTIALS_RO}",
        "${env.dockerDevRegistryUrl}":"${env.JENKINS_CREDENTIALS_RO}"
    ]
    def credsString=""

    if (user != ""){
        //User credentials already defined
        credsString=user
    }
    else {
        if (registryUrlToCredsSetMap.containsKey(registryUrl)){
            //This is on prem registry
            credsString=registryUrlToCredsSetMap[registryUrl]
        }
        else if (registryUrl ==~ AWS_REGISTRY_PATTERN) {
            credsString=getAwsEcrCredsString(config)
        }
    }
    assert credsString != "" : "ERROR: Jenkins credentials string for pulling from docker registry is empty.  This should not happen, pipeline will exit."
    return credsString
}

  /**
    * Parse list of servicesNames from properties.
    *   Supports new format (servicesNames=a,b,c) and legacy format (a_serviceName=abc)
    *
    * @param config - Map of properties read in from properties file
    * @return serviceNames - List of services names parsed from properties
    */
def generateServiceNamesListFromConfigMap(Map config ) {
    def final SERVICE_NAME_SUFFIX="_serviceName"
    def final SERVICE_NAME_LIST_STRING="serviceNames"

    def serviceNames = []
    if (config.containsKey(SERVICE_NAME_LIST_STRING)) {
        serviceNames = parseListFromString(config[SERVICE_NAME_LIST_STRING])
    }
    else {
        if (config.containsKey("SERVICE_NAME_SUFFIX")){
            SERVICE_NAME_SUFFIX=config["SERVICE_NAME_SUFFIX"]
        }
        serviceNames = []
        for ( envVar in config) {
            def envVarName=envVar.key
            def envVarValue=envVar.value
            if ( envVarName.endsWith(SERVICE_NAME_SUFFIX) ) {
                def serviceName=envVarName-SERVICE_NAME_SUFFIX
                serviceNames.add(serviceName)
            }
        }
    }
    return serviceNames
}

  /**
    * Prints out each pipeline parameter defined
    *   
    */
def printParams(){
    for (param in params.keySet()){
        pipelineLogger.debug("${param}=${params[param]}")
    }
}


  /**
    * Get username from Jenkins
    *   
    */
def getUserName(){
    username = (currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')).userName
  	return username
}

  /**
    * Runs cleanup for and docker images created/run during build
    *  -docker image prune: will remove all images not attached to a container (running or stopped)
    *  -docker container prune: will remove all non-running containers
    *
    *   Setup in this order so it would remove images that were built but did not run during the build.
    *   This should theoretically cache the compile image(s) which don't make sense to download each time. 
    *   
    **/    
def cleanup() {
    pipelineLogger("Entering Cleanup Stage")
    sh 'docker image prune -f > /dev/null 2>&1'
    sh 'docker container prune -f > /dev/null 2>&1'
    cleanWs()
}

def cleanupWs(Map config) {
    def compileEnabled = config["stageCompile"]
    if (compileEnabled != null && compileEnabled == "true") {
        compileEnabled = true
    }
    if ( !compileEnabled ) {
        return
    }
    def compileType = config["compileType"]
    def cleanupArg = ""
    switch(compileType) {
        case "maven":
            cleanupArg = "clean"    
        break
        case "node":
            cleanupArg = ""
        break
        default:
        pipelineLogger.warn("Unable to cleanup workspace since compileType '${compileType}' does not have defined cleanup args")
        return
    }
    def compileVars = runCompileInDocker.calculateCompileVarsMap(config)
    compileVars.put("compileArgs", cleanupArg)
    runCompileInDocker.runCompileImage(compileVars)
}

  /**
    * Calculates the URL and credential set for Jenkins to connect to the ECR docker registry
    *   where upstream images are stored for the different docker builds
    *
    *   It will first look at the "upstreamRegistry" variables, but if they are not defined it will 
    *   default to the "aws" variables:
    *       -if upstreamRegistryAccountNumber is defined, that value will be used.  If not, 
    *       awsAccountNumber would be used.
    *   
    * @param config - Map of properties read in from properties file
    * @return upstreamRegistryUrl - URL of ECR Docker registry
    * @return upstreamRegistryCredsString - credentials string for Jenkins to login to above registry
    */
def getUpstreamRegistryPropertiesAws(Map config){

    def upstreamRegistryAccountNumber=""
    def upstreamRegistryRegion=""
    def upstreamRegistryUser=""

    //Find Account Number
    if (config.containsKey("upstreamRegistryAccountNumber")){
        upstreamRegistryAccountNumber=config["upstreamRegistryAccountNumber"]
    }
    else if (config.containsKey("awsAccountNumber")){
        upstreamRegistryAccountNumber=config["awsAccountNumber"]
    }
    assert upstreamRegistryAccountNumber != "" : "ERROR: accountNumber for upstream registry was not found.  Please define either 'upstreamRegistryAccountNumber' or 'awsAccountNumber'.  Pipeline will now exit."

    //Find Region
    if (config.containsKey("upstreamRegistryRegion")){
        upstreamRegistryRegion=config["upstreamRegistryRegion"]
    }
    else if (config.containsKey("awsRegion")){
        upstreamRegistryRegion=config["awsRegion"]
    } 
    assert upstreamRegistryRegion != "" : "ERROR: AWS region for upstream registry was not found.  Please define either 'upstreamRegistryRegion' or 'awsRegion'.  Pipeline will now exit."


    //Find Cred Set Name
    if (config.containsKey("upstreamRegistryUser")){
        upstreamRegistryUser=config["upstreamRegistryUser"]
    }
    else if (config.containsKey("awsUser")){
        upstreamRegistryUser=config["awsUser"]
    } 
    assert upstreamRegistryUser != "" : "ERROR: AWS User for upstream registry was not found.  Please define either 'upstreamRegistryUser' or 'awsUser'.  Pipeline will now exit."

    

    def upstreamRegistryUrl = "${upstreamRegistryAccountNumber}.dkr.ecr.${upstreamRegistryRegion}.amazonaws.com"
    def upstreamRegistryCredsString = "ecr:${upstreamRegistryRegion}:${upstreamRegistryUser}"
    pipelineLogger("Calculated the following upstreamRegistry properties:", "DEBUG")
    pipelineLogger("Upstream registry URL=${upstreamRegistryUrl}", "DEBUG")
    pipelineLogger("Upstream registry credentials=${upstreamRegistryCredsString}", "DEBUG")

    return [upstreamRegistryUrl,upstreamRegistryCredsString]

}



  /**
    * Calculates the URL and credential set for Jenkins to connect to an on-prem docker registry
    *   where upstream images are stored for the different docker builds
    *
    *   It will require variable "upstreamRegistryUrl", and based on the value will grab the correct 
    *   credentials string from environment variables
    *   
    * @param config - Map of properties read in from properties file
    * @return upstreamRegistryUrl- URL of on-prem Docker registry
    * @return upstreamRegistryCredsString - credentials string for Jenkins to login to above registry
    */
def getUpstreamRegistryPropertiesHdfc(Map config){

   // def UPSTREAM_REGISTRY_URL_SUFFIX="_upstreamRegistryUrl"
    def registryProperties = [:]
    def globalVar = readYaml text: libraryResource('globalvars.yml')
    pipelineLogger("registries are globalVar.docker.registry.develop :${globalVar.docker.registry.develop}", "INFO")
    pipelineLogger("dockerDevRegistryUrl:${env.dockerDevRegistryUrl}", "INFO")
    def registryUrlToCredsSetMap=[
        'dockerdev.qa.hdfc.com':"${env.JENKINS_CREDENTIALS_RO}",
        'dockerdev.hdfc.com':"${env.JENKINS_CREDENTIALS_RO}",
        'dockerrelease.hdfc.com':"${env.JENKINS_CREDENTIALS_RO}",
        // revisit this. Quick fix for empty values - gourav 02/08/2021
        //"${globalVar.docker.registry.qa}":"${env.JENKINS_CREDENTIALS_RO}",
        //"${globalVar.docker.registry.develop}":"${env.JENKINS_CREDENTIALS_RO}",
        //"${globalVar.docker.registry.release}":"${env.JENKINS_CREDENTIALS_RO}",
        "${env.dockerReleaseRegistryUrl}":"${env.JENKINS_CREDENTIALS_RO}",
        "${env.dockerDevRegistryUrl}":"${env.JENKINS_CREDENTIALS_RO}"
    ]
    
    def upstreamRegistryUrl = config["upstreamRegistryUrl"]
    def upstreamRegistryCredsString=""

    pipelineLogger.debug("upstreamRegistryUrl=${upstreamRegistryUrl}")
    assert upstreamRegistryUrl != null && upstreamRegistryUrl != "" : "ERROR: upstreamRegistryUrl is not set or is empty.  Pipeline will exit."

    pipelineLogger.debug("Checking for upstreamRegistryUrl '${upstreamRegistryUrl}' in '${registryUrlToCredsSetMap.keySet()}''")

    assert registryUrlToCredsSetMap.containsKey(upstreamRegistryUrl) : "Could not find upstreamRegistryUrl '${upstreamRegistryUrl}' in the possible values '${registryUrlToCredsSetMap.keySet()}'"

    upstreamRegistryCredsString=registryUrlToCredsSetMap[upstreamRegistryUrl]

    return [upstreamRegistryUrl, upstreamRegistryCredsString]

}

  /**
    * Returns the URL and credential set for Jenkins to connect to a docker registry
    *   based on the upstreamRegistryType defined in properties file
    *
    *   
    * @param config - Map of properties read in from properties file
    * @return upstreamRegistryUrl - URL of Docker registry, can be on prem or ecr url
    * @return upstreamRegistryCredsString - credentials string for Jenkins to login to above registry
    */
def getUpstreamRegistryProperties(Map config) {
    final UPSTREAM_REGISTRY_TYPE_STRING="upstreamRegistryType"

    pipelineLogger("Finding upstream Docker registry properties in .env")

    def upstreamRegistryUrl=""
    def upstreamRegistryCreds=""
    assert config.containsKey(UPSTREAM_REGISTRY_TYPE_STRING) :"ERROR: ${UPSTREAM_REGISTRY_TYPE_STRING} not found in properties.  Pipeline will exit."
    switch(config[UPSTREAM_REGISTRY_TYPE_STRING]) {
        case "ecr":
            (upstreamRegistryUrl,upstreamRegistryCreds)= getUpstreamRegistryPropertiesAws(config)
            break
        case "hdfc":
            (upstreamRegistryUrl,upstreamRegistryCreds)= getUpstreamRegistryPropertiesHdfc(config)
            break
        default:
            break
    }
    return [upstreamRegistryUrl,upstreamRegistryCreds]
}

    /*
    *   If retries are enabled, ask for user input if a step has failed.  User can choose whether to retry or simply fail now.
    *   This will not require an executor, so no agents will be tied up during this wait.
    *   By default it will timeout (and therefore fail the build) after 5 minutes.
    *   
    *
    * @param config - map of properties read in from .env
    * @param serviceNames - List of services parsed from properties        

    */ 
def getUserInputFailedBuild(Map config, String failedStage, Integer waitMinutes=5){

    def message = "Build failed while running ${failedStage}. To retry, click \"Proceed\". To abort build now, click \"Abort\"."
    def retryTimeoutMins=config["retryTimeoutMins"]
    if ( retryTimeoutMins==null || retryTimeoutMins =="" || !retryTimeoutMins.isNumber() ){
        retryTimeoutMins=waitMinutes
    }     
    else {
        retryTimeoutMins=retryTimeoutMins.toInteger()
    }
    return getUserInput(config, message, retryTimeoutMins)
}

def getUserInputConfirmDeploy(Map config, String deployLocation) {
    def message = "Please confirm you would like to deploy to ${deployLocation}. To continue, click \"Proceed\". To abort build now, click \"Abort\"."
    def deployConfirmTimeoutMins=getDeployConfirmTimeoutMins(config)
    return getUserInput(config, message, deployConfirmTimeoutMins)
}

def getDeployConfirmTimeoutMins(Map config){
    def deployConfirmTimeoutMins=config["deployConfirmTimeoutMins"]
    if ( deployConfirmTimeoutMins == null || deployConfirmTimeoutMins =="" || !deployConfirmTimeoutMins.isNumber()  ){
        deployConfirmTimeoutMins=60
    } 
    else {
        deployConfirmTimeoutMins=deployConfirmTimeoutMins.toInteger()
    }
    return deployConfirmTimeoutMins
}

def getUserInput(Map config, String message, Integer waitMinutes){
    pipelineLogger.debug("${message}\n(Will timeout and automatically abort after ${waitMinutes} minutes)")
    timeout (waitMinutes) {
        def userInput = input(
            id: 'userInput', message: "${message}\n(Will timeout and automatically abort after ${waitMinutes} minutes)", 
        )
        return true
    }
}

def parseUtcDateFromString(String dateString){
    def datePattern="yyyy-MM-dd hh:mm:ss z"
    Date utcDate=null
    try{
        utcDate=Date.parse(dateFormat, dateString)
    }
    catch(Exception e){
        pipelineLogger.error("Failed to parse date.  Date entered was '${dateString}'.\nEntry needs to match the format: '${datePattern}'\nEx. 2020-12-31 12:00:00 CST")
        throw e
    }
    return utcDate
}

def setPipelineProperties(List parametersList, config){
    propertiesList=[]
    propertiesList.add(buildDiscarder(logRotator(daysToKeepStr: '7', numToKeepStr: '25')))
    
    String disableConcurrent=config["disableConcurrentBuilds"]
    if (disableConcurrent == null || disableConcurrent.toBoolean() ) {
        propertiesList.add(disableConcurrentBuilds())
    }
    //to simplyfy the builds for rebuilding. no custom UI... by default enabled
    String customRegistry=config["customRegistry"]
    if (customRegistry == null || customRegistry.toBoolean() ) {
        
        propertiesList.add(parameters(parametersList))
        //GS : add properties only in case of default pipeline. else this will not allowed any custom properties added 
        // later. this was bad design and limitation at jenkins
        properties(propertiesList)
    }
   
    
   

}


def getCustomPipelineScriptLocation() {
    def pipelineFileLocation=""
    if (fileExists("CICD/${env.gitBranchName}/Jenkinsfile")){
        pipelineFileLocation="CICD/${env.gitBranchName}/Jenkinsfile"
    }
    else if ( env.gitBranchName ==~ /^(feature|develop|release)-.*/) { 
        
        def branchPrefix = env.gitBranchName.split("-")[0]
        if (fileExists("CICD/${branchPrefix}-*/Jenkinsfile")){
            pipelineFileLocation = "CICD/${branchPrefix}-*/Jenkinsfile"
        }
    }
    return pipelineFileLocation
}

/**
*This Method can be used to get the input parameters from user. 
*by passing parameterList
*/
import java.time.*
import java.time.format.DateTimeFormatter

def getInput(String message, List parameterList, String ok="Ok", id="", Boolean runAsStage=false, String stageName="Input", Integer timeout=30) {
    //pipelineLogger.info("Getting User input")
    def inputResponse=""
    if (runAsStage){
        stage(stageName){
            inputResponse = input (
                id: id,
                message: message, 
                ok: ok,
                parameters: parameterList
            )
        }
    }
    else {
        inputResponse = input (
            id: id,
            message: message, 
            ok: ok,
            parameters: parameterList
        )
    }
    pipelineLogger.info("User selected options:\n"+inputResponse)
    return inputResponse
}

/**
*This Method can be used to Convert Zoned Time to UTC. 
*by passing date, time, timeZone
*/

def convertZonedTimeToUtc(String date, String time, String timeZone) {
    String dateFormat="yyyy-MM-dd"
    String timeFormat="hh:mm:ss a"

    LocalDate localDate=LocalDate.parse(date, DateTimeFormatter.ofPattern(dateFormat)) 
    LocalTime localTime=LocalTime.parse(time, DateTimeFormatter.ofPattern(timeFormat))

    ZonedDateTime inputZdt = ZonedDateTime.of(localDate, localTime, ZoneId.of(timeZone))
    ZonedDateTime inputZdtConvertedToUtc=inputZdt.withZoneSameInstant(ZoneOffset.UTC)
    return inputZdtConvertedToUtc
}

/**
*This Method can be used while getting input value as timezone ids from user. 
* this has to be passed as one of the parameter
* For Example:
* String rfcTimeZoneParameterName="Time Zone"
* String rfcTimeZoneParameterDescription="Timezone of selected RFC Start Time"
* String rfcTimeZoneParameterChoices=getTimeZoneInputString()
*/

def getTimeZoneInputString(){
    //Get full set of possible timezones
    Set<String> zoneIdsSet = ZoneId.getAvailableZoneIds()

    //Prepend list with commonly selected timezones
    List<String> zoneIdsList= ["America/Chicago", "Asia/Kolkata", "Europe/London"]

    //Sort timezone list alphabetically
    List<String> sortedZoneIdsList= new ArrayList<>(zoneIdsSet).sort()
    
    //Remove these options as they are already at the beginning
    sortedZoneIdsList.removeAll(zoneIdsList)
    
    zoneIdsList.addAll(sortedZoneIdsList)
    String zoneIdsString=zoneIdsList.join("\n")
    return zoneIdsString
}

/**
This method will be used to setup global env variable to LOB base variable,
 by doing this all the container\shared-lib can have same code but will work as per defined LOB
*/
def setLOBBaseEnv(Map config, globalVar)
{
    def LOB = (config['LOB'] == null) ? "afi":config['LOB']  
    if (LOB == "afi" )
    {
        env.lobName = "${globalVar.lobName}"
        env.mavenSettingFile="${globalVar.maven.settings}"
        env.cranRepo="${globalVar.cran.repo.release}"
        buildBranch=(config['releaseBranch'] == null ) ? "${env.PROD_BRANCH_NAME}":config['releaseBranch']
        env.staticContentRepo=(buildBranch == "${env.BRANCH_NAME}") ? "${globalVar.staticcontent.repo.release}":"${globalVar.staticcontent.repo.develop}"
        env.nugetRepo="${globalVar.nuget.repo}"
        env.npmRepo="${globalVar.npm.repo}"
        env.pythonRepo="${globalVar.python.repo}"
        env.mavenReleaseRepo="${globalVar.maven.repo.liblocal.release}"
        env.mavenSnapshotRepo="${globalVar.maven.repo.liblocal.develop}"
        env.mavenAppReleaseRepo="${globalVar.maven.repo.app.release}"
        env.mavenAppSnapshotRepo="${globalVar.maven.repo.app.develop}"
        env.dbRepo="${globalVar.db.repo.release}"
        env.msbuildReleaseRepo="${globalVar.msbuild.repo.release}"
        env.msbuildSnapshotRepo="${globalVar.msbuild.repo.develop}"
        env.apacheReleaseRepo="${globalVar.staticcontent.repo.release}"
        env.apacheSnapshotRepo="${globalVar.staticcontent.repo.develop}"
        env.dockerReleaseRegistryUrl="${globalVar.docker.registry.release}"
        env.dockerDevRegistryUrl="${globalVar.docker.registry.develop}"
        
        pipelineLogger.info("LOB is not defined in file '.env'. Using default LOB as 'afi'.")   
    }
    else
    {
        pipelineLogger.info("This job is for ${LOB}. Setting up all env ")
        env.lobName = config['LOB']
        
        env.mavenSettingFile="${globalVar.maven.settings_lob}"
        env.staticContentRepo=(config['staticContentRepo'] == null) ? "${globalVar.staticcontent.repo.develop}":config['staticContentRepo']
        env.cranRepo=(config['cranRepo'] == null) ? "${globalVar.cran.repo.release}":config['cranRepo']
        env.nugetRepo=(config['nugetRepo'] == null) ? "${globalVar.nuget.repo}":config['nugetRepo']
        env.npmRepo=(config['npmRepo'] == null) ? "${globalVar.npm.repo}":config['npmRepo']
        env.pythonRepo=(config['pythonRepo'] == null) ? "${globalVar.python.repo}":config['pythonRepo']
        env.mavenReleaseRepo=(config['mavenReleaseRepo'] == null) ? "${globalVar.maven.repo.liblocal.release}":config['mavenReleaseRepo']
        env.mavenSnapshotRepo=(config['mavenSnapshotRepo'] == null) ? "${globalVar.maven.repo.liblocal.develop}":config['mavenSnapshotRepo']
        env.mavenAppReleaseRepo=(config['mavenReleaseRepo'] == null) ? "${globalVar.maven.repo.app.release}":config['mavenReleaseRepo']
        env.mavenAppSnapshotRepo=(config['mavenSnapshotRepo'] == null) ? "${globalVar.maven.repo.app.develop}":config['mavenSnapshotRepo']
        env.dbRepo=(config['dbRepo'] == null) ? "${globalVar.db.repo.release}":config['dbRepo']
        env.msbuildReleaseRepo=(config['msbuildReleaseRepo'] == null) ? "${globalVar.msbuild.repo.release}":config['msbuildReleaseRepo']
        env.msbuildSnapshotRepo=(config['msbuildSnapshotRepo'] == null) ? "${globalVar.msbuild.repo.develop}":config['msbuildSnapshotRepo']
        env.apacheReleaseRepo=(config['apacheReleaseRepo'] == null) ? "${globalVar.staticcontent.repo.release}":config['apacheReleaseRepo']
        env.apacheSnapshotRepo=(config['apacheSnapshotRepo'] == null) ? "${globalVar.staticcontent.repo.develop}":config['apacheSnapshotRepo']
        // Docker setup
        // option 1 :single docker key dockerRepo in .env.
        // and value for dockerDevRegistryUrl and dockerReleaseRegistryUrl will remain same 
        // option 2 : seperate docker repo for dev and release. 
        // this will cover both the conditions + in case no docker repo defined.
        if (config['dockerRepo'] == null) 
        {
            env.dockerReleaseRegistryUrl = (config['dockerReleaseRepo'] == null) ? "${env.lobName}-docker-p-virtual.artifacts.hdfc.com" : config['dockerReleaseRepo'] + ".artifacts.hdfc.com"
            env.dockerDevRegistryUrl     = (config['dockerDevRepo'] == null)     ? "${env.lobName}-docker-virtual.artifacts.hdfc.com"   : config['dockerDevRepo'] + ".artifacts.hdfc.com"
        }
        else
        {
            env.dockerReleaseRegistryUrl = (config['dockerRepo'] == null) ? "${env.lobName}-docker-p-virtual.artifacts.hdfc.com" :  config['dockerRepo'] + ".artifacts.hdfc.com"
            env.dockerDevRegistryUrl     = (config['dockerRepo'] == null) ? "${env.lobName}-docker-virtual.artifacts.hdfc.com"   :  config['dockerRepo'] + ".artifacts.hdfc.com"
        }

        pipelineLogger.info("docker repo are : release = ${env.dockerReleaseRegistryUrl} & dev = ${env.dockerDevRegistryUrl} ")
    }

}

def getRepo(Map repoConfig) {
  promoteFromRepo  = "${repoConfig["lob"]}-${repoConfig["packageType"]}-virtual"
  promoteToRepo    = "${repoConfig["lob"]}-${repoConfig["packageType"]}-p-virtual"
  return [promoteFromRepo, promoteToRepo]
}

def setLOBEnvDocker(Map config) {
    config = trimMap(config)
    if (config.containsKey("LOB")) {
        env.lobName = config["LOB"]
        Map repoConfig = ["lob": env.lobName, "packageType": "docker"]
        env.dockerDevRegistryUrl     = (config.containsKey("dockerDevRepo"))     ? config['dockerDevRepo']     + ".artifacts.hdfc.com" : getRepo(repoConfig)[0] + ".artifacts.hdfc.com"
        env.dockerReleaseRegistryUrl = (config.containsKey("dockerReleaseRepo")) ? config['dockerReleaseRepo'] + ".artifacts.Hdfc.com" : getRepo(repoConfig)[1] + ".artifacts.hdfc.com" 
        pipelineLogger.info("Docker registries: '${env.dockerDevRegistryUrl}', '${env.dockerReleaseRegistryUrl}'") 
    } else {
        pipelineLogger.error("Unable to get LOB specific repositories as 'LOB' variable is not defined in '.env' file.")
        return false
    } 
}

def setLOBEnvNpm(Map config) {
    config = trimMap(config)
    if (config.containsKey('LOB')) {
        env.lobName = config["LOB"]
        Map repoConfig = ["lob": env.lobName, "packageType": "npm"]
        env.npmRepo =  ( config.containsKey('npmRepo') )       ?  config['npmRepo']       :  getRepo(repoConfig)[0]
        env.npmPRepo = ( config.containsKey('promotionRepo') ) ?  config['promotionRepo'] :  getRepo(repoConfig)[1]
        pipelineLogger.info("NPM repositories: '${env.npmRepo}', '${env.npmPRepo}'")
    } else {
        pipelineLogger.error("Unable to get LOB specific repositories as 'LOB' variable is not defined in '.env' file.")
        return false
    } 
}

def setLOBEnvDotNet(Map config) {
    config = trimMap(config)
    if (config.containsKey('LOB')) {
        env.lobName = config["LOB"]
        Map repoConfig = ["lob": env.lobName, "packageType": "nuget"]
        env.nugetRepo =  ( config.containsKey('nugetRepo') )     ?  config['nugetRepo']     :  getRepo(repoConfig)[0]
        env.nugetPRepo = ( config.containsKey('promotionRepo') ) ?  config['promotionRepo'] :  getRepo(repoConfig)[1]
        pipelineLogger.info("NUGET repositories: '${env.npmRepo}', '${env.npmPRepo}'")
    } else {
        pipelineLogger.error("Unable to get LOB specific repositories as 'LOB' variable is not defined in '.env' file.")
        return false
    } 
}

// return production branch where promotion can happen
def getProdBranch(Map config) {
    def prodBranch = ( env.PROD_BRANCH_NAME != null ) ?  env.PROD_BRANCH_NAME : config['releaseBranch']
    return prodBranch
}

// return promotion branch which can be used for promotion
def getPromoteBranch(Map config) {
    def promoteBranch = ( config.containsKey('promoteBranch') ) ? config['promoteBranch'] : 'develop'
    return promoteBranch
}

// this method will return the docker registry based on branch your are building an appliocation. 
def getDockerRegistry(String releaseBranch){
    def dockerRegistry = ""
   	def prodBranch = (releaseBranch == null) ? "${env.PROD_BRANCH_NAME}": releaseBranch
    dockerRegistry = ("${env.BRANCH_NAME}" == "${prodBranch}") ? "${env.dockerReleaseRegistryUrl}" : "${env.dockerDevRegistryUrl}"
    pipelineLogger.debug("Docker Build registry is set as ${dockerRegistry}")
    return dockerRegistry
}

