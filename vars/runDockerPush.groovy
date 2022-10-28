def push(config, serviceNames,role=null){
    def retry=true
    def retries=0
    def numAllowedRetries=2
    String pushRegistryUrl
    String pushRegistryCredsString
    (pushRegistryUrl, pushRegistryCredsString) = getRegistryProperties(config)
    //If this fails, retry in case of temporary issue with connection
    while ( retry && retries < numAllowedRetries ) {
        try {
            if(role == null){
                // def pushRegistryUrl = (config['releaseBranch'] == env.gitBranchName) ? "${env.dockerPushReleaseRegistryUrl}":"${env.dockerPushDevRegistryUrl}"
   
                docker.withRegistry("https://${pushRegistryUrl}", pushRegistryCredsString) {
                    pushImages(config, serviceNames, pushRegistryUrl)
                }                
            }else{
               ecrLogin(config,role)
                docker.withRegistry("https://${pushRegistryUrl}") {
                    pushImages(config, serviceNames, pushRegistryUrl)
                }
            }
            retry=false
        }
        catch (Exception e) {
            retries +=1
            if ( retries >= numAllowedRetries ) {
                throw e
            }
            sleep(10)
        }
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

        //artifactoryServer = Artifactory.server "${env.artifactoryServer}" 
        try {
            uploadArtifactoryMetadata(imageName, pushRegistryUrl)
        } catch(Exception ex) {
            pipelineLogger.error("Failed while setting up the package properties at artifactory.")
            pipelineLogger.info(ex.getMessage())
        }

        dockerImages.add("${imageNameWithTag}")
    }
    env.DOCKER_IMAGES=dockerImages.join(",")
}

def validateOnPrem(Map config, List serviceNames) {
    /*

    */
    filteredImageNameToTagListMap=serviceNames
    return serviceNames
}

    

def getRegistryProperties(Map config){
    String pushRegistryUrl=""
    String credsString=""
    String dockerPushRegistryLocation=(config["dockerPushRegistryLocation"]==null) ? "bank" : config["dockerPushRegistryLocation"]  
    switch (dockerPushRegistryLocation){
        case "aws":
            def awsAccountNumber=config["awsAccountNumber"]
            def awsRegion=config["awsRegion"]
            def awsUser=config["awsUser"]

            pushRegistryUrl = getDockerPushUrlEcr(config)
            credsString = "ecr:${awsRegion}:${awsUser}"
            break
        case "bank":
            pushRegistryUrl=getDockerPushUrlOnPrem(config)
            credsString=env.JENKINS_CREDENTIALS_RO
            break
        default:
            pipelineLogger.error("dockerPushRegistryLocation found in .env as '${dockerPushRegistryLocation}' which does not match any of the known registry types")
            break
    }
    return [pushRegistryUrl, credsString]    
}

def getDockerPushUrl(Map config) {
    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "bank" : config["dockerPushRegistryLocation"]  
    
    //String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "docker-develop" : config["dockerPushRegistryLocation"]
    //utilities.checkConfigVarExistsNotEmpty(config,"dockerPushRegistryLocation")
    String dockerPushUrl=""
    switch (dockerPushRegistryLocation){
        case "aws":
            dockerPushUrl= getDockerPushUrlEcr(config)
            break
        case "bank":
            dockerPushUrl= getDockerPushUrlOnPrem(config)
            break
        default:
            pipelineLogger.error("dockerPushRegistryLocation found in .env as '${dockerPushRegistryLocation}' which does not match any of the known registry types")
            break
    }    
    return dockerPushUrl
}

def getDockerPushUrlOnPrem(Map config) {
    // read from jenkins global variables.
    def pushRegistryUrl = (config['releaseBranch'] == env.gitBranchName) ? "${env.dockerReleaseRegistryUrl}":"${env.dockerDevRegistryUrl}"
    pipelineLogger.info(" getDockerPushUrlOnPrem = ${pushRegistryUrl}")
    return pushRegistryUrl
}

def dockerPushOnPrem(Map config, List serviceNames){
    //the registry shoudl be based on env variable at build server. this is to restrict cross deployment. 
    //Stub method - this should be filled out to allow push to on-prem registry
    //Check variables in .env or generate based on branch name 
    
    List imagesToPush= validateOnPrem(config, serviceNames)
    push(config, imagesToPush)
}

  /**
    * Will take care of pushing docker images into the registry specified in properties
    *
    * Check the location of the docker push registry and switch to the correct function based on properties 
    *
    * @param config - Map of properties read in from properties file
    * @param serviceNames - List of service names parsed from config
    */
def call(Map config, List serviceNames=[], String role=null) {
    if (serviceNames.isEmpty()){serviceNames=utilities.generateServiceNamesListFromConfigMap(config)}

    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "bank" : config["dockerPushRegistryLocation"] 

    pipelineLogger.info("Entering Docker Push Stage")
    switch (dockerPushRegistryLocation){
        case "aws":
            pipelineLogger.info("Found dockerPushRegistryLocation as 'aws'")
            dockerPushEcr(config, serviceNames,role)
            break
        case "bank":
            pipelineLogger.info("Found dockerPushRegistryLocation as 'bank'")
            dockerPushOnPrem(config, serviceNames)
            break
        default:
            pipelineLogger.error("dockerPushRegistryLocation found in .env as '${dockerPushRegistryLocation}' which does not match any of the known registry types")
            break
    }    
}

  /**
    * Wrapper around 'stageDockerPush' call method to run it inside a stage
    *
    * @param config - Map of properties read in from properties file
    * @param serviceNames - List of service names parsed from config
    */
def runStage(Map config, serviceNames=[]) {
    stage ('Docker Push') {
        script {
            def service_names = (serviceNames.isEmpty()) ? utilities.generateServiceNamesListFromConfigMap(config) : serviceNames
            runDockerPush(config,service_names)
        }
    }
}

