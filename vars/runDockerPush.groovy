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

def updateGitTagForPromotion(config){
    //def prodBranch = (config['releaseBranch'] == null) ? "${env.PROD_BRANCH_NAME}" : config['releaseBranch']
    def prodBranch = utilities.getProdBranch(config)
    
    //create or update git tag for branches except for branches {prodBranch} and PR-*. Replace pc-* tag if exists
    if ( !env.BRANCH_NAME.equals(prodBranch) && !env.BRANCH_NAME.matches("PR-*") ) {      
        // find {pcGitTag}
        def pcGitTag = sh(returnStdout: true, script: "git tag --points-at ${env.gitCommitHash} | grep '^pc-' | tail -n 1").trim()
        // if {pcGitTag} is found remove the tag
        if ( pcGitTag.startsWith("pc-") ) { utilGit.removeGitTag("${pcGitTag}") }
        // Create new {pcGitTag}
        utilGit.checkCreateGitTag("pc-${env.tag}--b${env.BUILD_ID}.${env.BRANCH_NAME}", "Added using ${env.BUILD_TAG}", true, config)
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

def ecrPushImages(config, serviceNames, pushRegistryUrl){
    def dockerImages=[]
    def prodBranch = (config['releaseBranch'] == null) ? "${env.PROD_BRANCH_NAME}" : config['releaseBranch']
    for (serviceName in serviceNames) {
        String imageName=utilities.generateDockerImageName(serviceName, config)
        String imageNameWithTag="${imageName}:${env.tag}"
                    
        pipelineLogger.debug("Pushing image '${pushRegistryUrl}/${imageNameWithTag}'.")
        docker.image(imageNameWithTag).push()

        pipelineLogger.debug("Pushing image '${pushRegistryUrl}/${imageName}:latest'.")
        docker.image(imageNameWithTag).push("latest")

        //updateGitTagForPromotion(config)
        dockerImages.add("${imageNameWithTag}")
    }
    env.DOCKER_IMAGES=dockerImages.join(",")
}

/*
    Stub method... fill this out later
    It should check whether an image with specific tag already exists in the registry or not - 
    we should only push image:tag combinations that do not yet exist (with exception of 'latest') 
*/
def validateOnPrem(Map config, List serviceNames) {
    /*

    */
    filteredImageNameToTagListMap=serviceNames
    return serviceNames
}

    /**
    * Performs two tasks for each image:
    *   1. Checks whether repository exists in ECR yet; creates if it does not exist
    *   2. Checks whether tag is already pushed - compiles list of serviceNames associated 
    *       with images that do NOT yet have the current build tag pushed into ECR 
    *
    *
    * @param config - map of properties read in from .env
    * @param serviceNames 

    */
 def validateEcr(Map config, List serviceNames, String role=null) {
    env.DockerImageInvocationRegistry="dockerdev.ampf.com"
    //env.DockerImageInvocation="afi/devops/invocation-no-entrypoint:latest"
    env.DockerImageInvocation="drp/devops/invocation-no-entrypoint:latest"
    env.DockerImageInvocationRegistryCreds=env.JENKINS_CREDENTIALS_RO
    def fullInvocationImagePath = "${env.DockerImageInvocationRegistry}/${env.DockerImageInvocation}"
    def filteredImageNameToTagListMap=[:]
    List imagesToPush=[]
    pipelineLogger.debug("Attempting to validate properties around ECR docker registry.")
    docker.withRegistry("https://${env.DockerImageInvocationRegistry}", DockerImageInvocationRegistryCreds) {
        def awsRegion=config["awsRegion"]
        def awsUser=config["awsUser"]
        def awsAccountNumber=config["awsAccountNumber"]
        //docker.image(fullInvocationImagePath).inside("-e AWS_DEFAULT_REGION=${awsRegion}") {
        //    script {
                if(role != null){
                   def creds = utilAwsCmd.getRoleCredentials(awsAccountNumber,awsUser,role)
                   withEnv([
                        "AWS_ACCESS_KEY_ID=${creds.AccessKeyId}",
                        "AWS_SECRET_ACCESS_KEY=${creds.SecretAccessKey}",
                        "AWS_SESSION_TOKEN=${creds.SessionToken}"
                   ]) {
                      imagesToPush = generateImagesToPush(config,serviceNames)
                   } 
                }else{
                     withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: awsUser]]) {
                        imagesToPush = generateImagesToPush(config,serviceNames)
                     }
            //    }
               
            //}
        }
    }
    return imagesToPush
}

//for ECR
def generateImagesToPush(Map config, List serviceNames){
        List imagesToPush=[]
        for ( serviceName in serviceNames) {
                            
           String imageName=utilities.generateDockerImageName(serviceName, config)
           def awsUser= config["awsUser"] //added user to display in error
           //Check if repository has been created in ECR yet - if not, then create it now
          def checkRepositoryExists= sh script: "aws ecr describe-repositories --repository-names ${imageName} > /dev/null 2>&1 || aws ecr create-repository --repository-name ${imageName} > /dev/null 2>&1", returnStatus: true
          assert checkRepositoryExists == 0 :"ERROR: Error response while checking/creating repository in ECR.  Repository name ='${imageName}', using credentials id '${awsUser}'\nLikely issues are: \n-Invalid repository name\n-Invalid credentials/incorrect permissions associated with credentials.  Requires full permissions for ECR in order to run."
          def imageAlreadyExists = sh script: "aws ecr describe-images --repository-name ${imageName} --image-ids imageTag=${env.tag} > /dev/null 2>&1", returnStatus: true
          pipelineLogger.debug("imageAlreadyExists set to ${imageAlreadyExists}")
          if (imageAlreadyExists == 0) {
                pipelineLogger.warn("Image ${imageName} with tag ${env.tag} already exists in ECR.  This tag will not be pushed again")
          }
          else {
                pipelineLogger.debug("Image ${imageName}:${env.tag} was not found in ecr, so it can be pushed.")
                imagesToPush.add(serviceName)
          }
        }
        return imagesToPush
}

def getRegistryProperties(Map config){
    String pushRegistryUrl=""
    String credsString=""
    String dockerPushRegistryLocation=(config["dockerPushRegistryLocation"]==null) ? "ampf" : config["dockerPushRegistryLocation"]  
    switch (dockerPushRegistryLocation){
        case "aws":
            def awsAccountNumber=config["awsAccountNumber"]
            def awsRegion=config["awsRegion"]
            def awsUser=config["awsUser"]

            pushRegistryUrl = getDockerPushUrlEcr(config)
            credsString = "ecr:${awsRegion}:${awsUser}"
            break
        case "ampf":
            pushRegistryUrl=getDockerPushUrlOnPrem(config)
            credsString=env.JENKINS_CREDENTIALS_RO
            break
        default:
            pipelineLogger.error("dockerPushRegistryLocation found in .env as '${dockerPushRegistryLocation}' which does not match any of the known registry types")
            break
    }
    return [pushRegistryUrl, credsString]    
}

def dockerPushEcr(Map config, List serviceNames,String role) {
    utilities.checkAwsVars(config)
    /*def awsAccountNumber=config["awsAccountNumber"]
    def awsRegion=config["awsRegion"]
    def awsUser=config["awsUser"]

    def pushRegistryUrl = getDockerPushUrlEcr(config)
    def credSetName = "ecr:${awsRegion}:${awsUser}"
    */
    String pushRegistryUrl
    String credSetName
    (pushRegistryUrl, credSetName) = getRegistryProperties(config)
    //def imageNameToTagList = generateImageNameToTagList(config, serviceNames)
    
    //['brc/dpe-bloomreachexperience/develop/cms':['akjllsdfjc9i3j2mx','latest']]
    //def filteredImageNameToTagListMap = validateEcr(config, imageNameToTagList)
    List serviceNamesToPush=validateEcr(config, serviceNames,role)
    //push(filteredImageNameToTagListMap, pushRegistryUrl, credSetName)    
    push(config, serviceNamesToPush,role)
}


def getDockerPushUrl(Map config) {
    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "ampf" : config["dockerPushRegistryLocation"]  
    //utilities.checkConfigVarExistsNotEmpty(config,"dockerPushRegistryLocation")
    String dockerPushUrl=""
    switch (dockerPushRegistryLocation){
        case "aws":
            dockerPushUrl= getDockerPushUrlEcr(config)
            break
        case "ampf":
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

    String dockerPushRegistryLocation= (config["dockerPushRegistryLocation"]==null) ? "ampf" : config["dockerPushRegistryLocation"] 

    pipelineLogger.info("Entering Docker Push Stage")
    switch (dockerPushRegistryLocation){
        case "aws":
            pipelineLogger.info("Found dockerPushRegistryLocation as 'aws'")
            dockerPushEcr(config, serviceNames,role)
            break
        case "ampf":
            pipelineLogger.info("Found dockerPushRegistryLocation as 'ampf'")
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

/**
 * Using role credentials, do a ecr login to be able to docker commands against the registry
 */
def ecrLogin(config,awsRole){
    def awsAccountNumber=config["awsAccountNumber"]
    def awsRegion=config["awsRegion"]
    def awsUser=config["awsUser"]
    def creds = utilAwsCmd.getRoleCredentials(awsAccountNumber,awsUser,awsRole)
    try
    {
        withEnv([
            "AWS_ACCESS_KEY_ID=${creds.AccessKeyId}",
            "AWS_SECRET_ACCESS_KEY=${creds.SecretAccessKey}",
            "AWS_SESSION_TOKEN=${creds.SessionToken}"
        ])
        {
            sh(script:""" 
                    aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${awsAccountNumber}.dkr.ecr.${awsRegion}.amazonaws.com
                    """, returnStdout: true)
        }
    }
    catch (Exception ex) {
        pipelineLogger.fatal("AWS login failed")
      }
}
def uploadArtifactoryMetadata(imageName, pushRegistryUrl) {
  	// Set default security scan and environment variable values. 
    //def (blackDuckValue, veracodeValue, sonarValue) = ["","",""]
    //def reponame = scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.")[0]
    def deployedEnv=("${pushRegistryUrl}"=="dockerrelease.ampf.com") ? "prod":"non-prod"
	def targetrepo = ("${pushRegistryUrl}"=="dockerrelease.ampf.com") ? "docker-local-release/":"docker-local-dev/"

    // security scan booleans
    def blackDuckValue = (env.blackduckEnabled == "true") ? "true" : "false"
    def sonarValue = (env.sonarEnabled == "true") ? "true": "false"
    def veracodeValue = (env.veracodeEnabled == "true") ? "true" : "false"

  	// This upload spec will be used to find the artifact in Artifactory we want to upload Properties to.
    def uploadSpec2 = 
        """{
            \"files\": [
            {
                \"pattern\": \"${targetrepo}${imageName}/${env.tag}/manifest.json\",
                \"target": \"${targetrepo}\"
            }
            ]
        }"""

    pipelineLogger.debug("deployedEnv: '${deployedEnv}'")
    //pipelineLogger.debug("reponame: '${reponame}'")
  	pipelineLogger.debug("uploadSpec2: '${uploadSpec2}'")

  	pipelineLogger.info("BlackDuck scan performed: '${blackDuckValue}'")
  	pipelineLogger.info("SonarQube scan performed: '${sonarValue}'")
  	pipelineLogger.info("Veracode scan performed: '${veracodeValue}'")
    
  	// Add Properties in Artifactory.
    def artifactoryServer = Artifactory.server "${env.artifactoryServer}"
    artifactoryServer.setProps spec: uploadSpec2, props: "environment=${deployedEnv};blackduck.scan=${blackDuckValue};veracode.scan=${veracodeValue};sonarqube.scan=${sonarValue}"
}
