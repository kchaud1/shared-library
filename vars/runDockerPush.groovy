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
    
    List imagesToPush=[]
    String imageName=utilities.generateDockerImageName(serviceName, config)
    def awsAccountNumber=config["awsAccountNumber"]
    def awsRegion=config["awsRegion"]
    def awsUser=config["awsUser"]
    String dockerPushRegistryLocation=(config["dockerPushRegistryLocation"]==null) ? "hdfc" : config["dockerPushRegistryLocation"]  
      for ( serviceName in serviceNames) {
      pushRegistryUrl = getDockerPushUrlEcr(config)
      sh(script:""" aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${awsAccountNumber}.dkr.ecr.${awsRegion}.amazonaws.com""", returnStdout: true)
      def checkRepositoryExists= sh script: "aws ecr describe-repositories --repository-names ${imageName} > /dev/null 2>&1 || aws ecr create-repository --repository-name ${imageName} > /dev/null 2>&1", returnStatus: true
          assert checkRepositoryExists == 0 :"ERROR: Error response while checking/creating repository in ECR.  Repository name ='${imageName}', using credentials id '${awsUser}'\nLikely issues are: \n-Invalid repository name\n-Invalid credentials/incorrect permissions associated with credentials.  Requires full permissions for ECR in order to run."
          def imageAlreadyExists = sh script: "aws ecr describe-images --repository-name ${imageName} --image-ids imageTag=${env.tag} > /dev/null 2>&1", returnStatus: true
          pipelineLogger.debug("imageAlreadyExists set to ${imageAlreadyExists}")
          if (imageAlreadyExists == 0) {
                pipelineLogger.warn("Image ${imageName} with tag ${env.tag} already exists in ECR.  This tag will not be pushed again")
          }
      }
          else {
                pipelineLogger.debug("Image ${imageName}:${env.tag} was not found in ecr, so it can be pushed.")
                imagesToPush.add(serviceName)
          }}
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
