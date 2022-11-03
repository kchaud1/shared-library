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
    * Prints out each pipeline parameter defined
    *   
    */
def printParams(){
    for (param in params.keySet()){
        pipelineLogger.debug("${param}=${params[param]}")
    }
}



def cleanup() {
    pipelineLogger("Entering Cleanup Stage")
    sh 'docker image prune -f > /dev/null 2>&1'
    sh 'docker container prune -f > /dev/null 2>&1'
    cleanWs()
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


// this method will return the docker registry based on branch your are building an appliocation. 
def getDockerRegistry(String releaseBranch){
    def dockerRegistry = ""
   	def prodBranch = (releaseBranch == null) ? "${env.PROD_BRANCH_NAME}": releaseBranch
    dockerRegistry = ("${env.BRANCH_NAME}" == "${prodBranch}") ? "${env.dockerReleaseRegistryUrl}" : "${env.dockerDevRegistryUrl}"
    pipelineLogger.debug("Docker Build registry is set as ${dockerRegistry}")
    return dockerRegistry
}
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

