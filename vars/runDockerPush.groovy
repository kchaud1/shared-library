def call(Map config, List serviceNames=[], String role=null) {
    if (serviceNames.isEmpty()){serviceNames=utilities.generateServiceNamesListFromConfigMap(config)}

    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "hdfc" : config["dockerPushRegistryLocation"] 

    pipelineLogger.info("Entering Docker Push Stage")
    switch (dockerPushRegistryLocation){
        case "aws":
            pipelineLogger.info("Found dockerPushRegistryLocation as 'aws'")
            dockerPushEcr(config, serviceNames,role)
            break
        case "hdfc":
            pipelineLogger.info("Found dockerPushRegistryLocation as 'hdfc'")
            dockerPushOnPrem(config, serviceNames)
            break
        default:
            pipelineLogger.error("dockerPushRegistryLocation found in .env as '${dockerPushRegistryLocation}' which does not match any of the known registry types")
            break
    }    
}
def dockerPushEcr(Map config, List serviceNames,String role) {
    utilities.checkAwsVars(config)
    String pushRegistryUrl
    String credSetName
    //(pushRegistryUrl, credSetName) = getRegistryProperties(config)
    //def imageNameToTagList = generateImageNameToTagList(config, serviceNames)
    
    //['brc/dpe-bloomreachexperience/develop/cms':['akjllsdfjc9i3j2mx','latest']]
    //def filteredImageNameToTagListMap = validateEcr(config, imageNameToTagList)
    //List serviceNamesToPush=validateEcr(config, serviceNames,role)----------------------------
    List imagesToPush=[]
  
    def awsAccountNumber=config["awsAccountNumber"]
    def awsRegion=config["awsRegion"]
    def awsUser=config["awsUser"]
    String dockerPushRegistryLocation=(config["dockerPushRegistryLocation"]==null) ? "hdfc" : config["dockerPushRegistryLocation"]  
   
   //def serviceName = config['serviceName']
    //pushRegistryUrl = "${awsAccountNumber}.dkr.ecr.${awsRegion}.amazonaws.com"
    pushRegistryUrl = getDockerPushUrlEcr(config)
        //sh(script: "aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 898815416447.dkr.ecr.ap-south-1.amazonaws.com")
        sh(script:""" aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${awsAccountNumber}.dkr.ecr.${awsRegion}.amazonaws.com""", returnStdout: true)
         docker.withRegistry("https://${pushRegistryUrl}") {
                    pushImages(config, serviceNames, pushRegistryUrl)

    }
}
    def pushImages(config, serviceNames, pushRegistryUrl){
    def dockerImages=[]
    for (serviceName in serviceNames) {
        String imageName=utilities.generateDockerImageName(serviceName, config)
        String imageNameWithTag="${imageName}:${env.tag}"
                    
        pipelineLogger.debug("Pushing image '${pushRegistryUrl}/${imageNameWithTag}'.")
        docker.image(imageNameWithTag).push()
        pipelineLogger.debug("Pushing image '${pushRegistryUrl}/${imageName}:latest'.")
        docker.image(imageNameWithTag).push("latest")
    }
    }
def getDockerPushUrl(Map config) {
    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "ampf" : config["dockerPushRegistryLocation"]  
    //utilities.checkConfigVarExistsNotEmpty(config,"dockerPushRegistryLocation")
    String dockerPushUrl=""
    switch (dockerPushRegistryLocation){
        case "aws":
            dockerPushUrl= getDockerPushUrlEcr(config)
            break
        case "hdfc":
            dockerPushUrl= getDockerPushUrlOnPrem(config)
            break
        default:
            pipelineLogger.error("dockerPushRegistryLocation found in .env as '${dockerPushRegistryLocation}' which does not match any of the known registry types")
            break
    }    
    return dockerPushUrl
}

def getDockerPushUrlEcr(Map config) {
    def awsAccountNumber=config["awsAccountNumber"]
    def awsRegion=config["awsRegion"]
    def pushRegistryUrl = "${awsAccountNumber}.dkr.ecr.${awsRegion}.amazonaws.com"
    return pushRegistryUrl
}
