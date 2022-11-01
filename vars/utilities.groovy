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
// this method will return the docker registry based on branch your are building an appliocation. 
def getDockerRegistry(String releaseBranch){
    def dockerRegistry = ""
   	def prodBranch = (releaseBranch == null) ? "${env.PROD_BRANCH_NAME}": releaseBranch
    dockerRegistry = ("${env.BRANCH_NAME}" == "${prodBranch}") ? "${env.dockerReleaseRegistryUrl}" : "${env.dockerDevRegistryUrl}"
    pipelineLogger.debug("Docker Build registry is set as ${dockerRegistry}")
    return dockerRegistry
}

