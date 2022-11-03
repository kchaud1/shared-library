def call(Map config, List serviceNames=[], String role=null) {
    if (serviceNames.isEmpty()){serviceNames=utilities.generateServiceNamesListFromConfigMap(config)}

    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "hdfc" : config["dockerPushRegistryLocation"] 

    pipelineLogger.info("Entering Docker Push Stage")
    switch (dockerPushRegistryLocation){
        case "aws":
            pipelineLogger.info("Found dockerPushRegistryLocation as 'aws'")
        pipelineLogger.debug("mkkkikiiikimmmmmm")
            dockerPushEcr(config, serviceNames,role)
        pipelineLogger.debug("hhhhyyyyuutrutuuuuu")
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
    
    //List imagesToPush=[]
    //String imageName=utilities.generateDockerImageName(serviceNames, config)
    def awsAccountNumber=config["awsAccountNumber"]
    def awsRegion=config["awsRegion"]
    def awsUser=config["awsUser"]
    String dockerPushRegistryLocation=(config["dockerPushRegistryLocation"]==null) ? "hdfc" : config["dockerPushRegistryLocation"]  
    pipelineLogger.debug("AAAaaaaaaaaaaffffffffffffffff")
          
      docker.withRegistry("https://${pushRegistryUrl}") {
      pushImages(config, serviceNames, pushRegistryUrl)

    }

}
    def pushImages(config, serviceNames, pushRegistryUrl){
    def dockerImages=[]
    for (serviceName in serviceNames) {
        String imageName=utilities.generateDockerImageName(serviceNames, config)
        String imageNameWithTag="${imageName}:${env.tag}"
                    
        pipelineLogger.debug("Pushing image '${pushRegistryUrl}/${imageNameWithTag}'.")
        docker.image(imageNameWithTag).push()
        pipelineLogger.debug("Pushing image '${pushRegistryUrl}/${imageName}:latest'.")
        docker.image(imageNameWithTag).push("latest")
    }
    }
def getDockerPushUrl(Map config) {
    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "hdfc" : config["dockerPushRegistryLocation"]  
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

def dockerPushOnPrem(Map config, List serviceNames){
    pipelineLogger.debug("Code required for on prem deployment")
}
def getDockerPushUrlOnPrem(Map config) {
    def pushRegistryUrl = (config['releaseBranch'] == env.gitBranchName) ? "${env.dockerReleaseRegistryUrl}":"${env.dockerDevRegistryUrl}"
    pipelineLogger.info(" getDockerPushUrlOnPrem = ${pushRegistryUrl}")
    return pushRegistryUrl
}
