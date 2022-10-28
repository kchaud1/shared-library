def getDockerRegistry(String releaseBranch){
    def dockerRegistry = ""
   	def prodBranch = (releaseBranch == "") ? "${env.PROD_BRANCH_NAME}": releaseBranch
  	pipelineLogger.debug("Prod Branch is -- ${prodBranch}")
    dockerRegistry = ("${env.BRANCH_NAME}" == "${prodBranch}") ? "${env.dockerReleaseRegistryUrl}" : "${env.dockerDevRegistryUrl}"
    pipelineLogger.debug("Docker Build registry is set as -- ${dockerRegistry}")
    return dockerRegistry
}

/***
 * Builds docker image for the dockerfile present in specified directory
 * @param dockerDirName
 * @return
 */
def build(Map config, List serviceNames=[]) {
    def dockerImages=[]
    serviceNames = (serviceNames.isEmpty()) ? utilities.generateServiceNamesListFromConfigMap(config) : serviceNames
    // this will be getting captured at pipeline setup.
    //utilGit.setAdvancedGitEnvProperties()
    pipelineLogger.info("Building docker images...")
    for ( serviceName in serviceNames ) {
        def dockerfile = (config["customDockerImagesPath"] == null || config["customDockerImagesPath"] == "") ? "docker-images/${serviceName}/Dockerfile": config["customDockerImagesPath"]
        def dockerIgnoreFile = "/docker-images/${serviceName}/.dockerignore"
        def dockerBuildRegistry = getDockerRegistry(config['releaseBranch'])

        //withCredentials([conjurSecretCredential(credentialsId: env.JENKINS_DAP_CREDS_RO, variable: 'CONJUR_SECRET')]){
            def registries = [env.dockerDevRegistryUrl, env.dockerReleaseRegistryUrl]
            def imageName=utilities.generateDockerImageName(serviceName, config)
            def imageNameWithTag="${imageName}:${env.tag}"
          	def default_build_args="--build-arg ARTIFACTORY_USERNAME=karan9397 --build-arg ARTIFACTORY_PASSWORD=Cloud@2022"
          	def dockerBuildArgsString=(config['dockerBuildArgs'] == null) ? "${default_build_args}" : config['dockerBuildArgs']+" ${default_build_args}"

            // login to docker registries
            // this will be used later when need to login to quay or jfrog
            //for (registry in registries) { sh """ docker login "${registry}" -u karan9397 -p Cloud@2022 """ }
            for (registry in registries) { sh """ docker login -u karan9397 -p Cloud@2022 """ }

            if (fileExists("${dockerfile}")){
                if (fileExists(dockerIgnoreFile)){
                    pipelineLogger.info("[[ ${serviceName} ]] : Using 'docker-images/${serviceName}/.dockerignore'")
                    sh """cp ${dockerIgnoreFile} ${env.WORKSPACE}"""          
                }

                pipelineLogger.debug("[[ ${serviceName} ]] : imageNameWithTag = ${imageNameWithTag}")
                pipelineLogger.info("[[ ${serviceName} ]] : Building image '${imageName}' using '${dockerfile}'")
                
                sh """
                    cat "${dockerfile}" | sed 's/^/\t\t/'
                    
                """

                // build an image
                sh (script: """
                    docker rmi -f `docker images --filter "label=com.bank.dockerImageName=${imageName}" --filter "label=com.bank.giHubRepositoryBranchName=${env.gitBranchName}" -q`  || :         
                    docker build  --network="host" --rm --pull --no-cache ${dockerBuildArgsString} \
                        --label com.bank.gitHubProjectName=${env.gitProjectName} \
                        --label com.bank.gitHubRepositoryName=${env.gitRepoName} \
                        --label com.bank.gitHubRepositoryBranchName=${env.gitBranchName} \
                        --label com.bank.gitHubRepositoryCommitID=${env.gitCommitHashShort} \
                        --label com.bank.buildUrl=${env.BUILD_URL} \
                        --label com.bank.buildID=${env.BUILD_ID} \
                        --label com.bank.buildTimeStamp=`date +%Y-%m-%dT%T` \
                        --label com.bank.buildSystem="Jenkins" \
                        --label com.bank.dockerImageName=${imageName}:${env.tag} \
                        --tag  ${imageName}:latest --tag ${imageNameWithTag} \
                        --file ${dockerfile} ${env.WORKSPACE} || exit 1

                    docker images --filter "label=com.bank.dockerImageName=${imageName}" --filter "label=com.bank.gitRepositoryBranchName=${env.gitBranchName}"
                    """
                )

                dockerImages.add("${imageNameWithTag}")
            }  else {
                pipelineLogger.fatal("[[ ${serviceName} ]] : File '${dockerfile}' does not exists. Skipping build.")
            }
       // }    
    }
    env.DOCKER_IMAGES=dockerImages.join(",")
}


