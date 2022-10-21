def call(Boolean loadDefaultEnv=true)
{
    pipelineLogger("Cloning Code")
    checkout scm
    if(loadDefaultEnv)
        utilGit.setEnvVarsFromGitProperties()
}

def runStage(Boolean loadDefaultEnv=true) {
    stage ('Checkout latest code') {
        script{
            runGitCheckOut(loadDefaultEnv)
        }
    }
}
