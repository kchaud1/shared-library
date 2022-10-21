def getNonSnapshotVersion() {
    return (readFile('pom.xml') =~ '<version>(.+)-SNAPSHOT</version>')[0][1]
}

def getVersion() {
    pom = readMavenPom file: "pom.xml"
    return pom.version
}

def getGroupID() {
    pom = readMavenPom file: "pom.xml"
    return pom.groupId
}

def getArtifactID() {
    pom = readMavenPom file: "pom.xml"
    return pom.artifactId
}

def getArtifactoryPath(String pomDir=""){
    def currentDir= sh(script: 'pwd', , returnStdout: true).trim()
    def packageDir = ("$pomDir" == "" ) ? "$currentDir" : "$pomDir"
    pom = readMavenPom file: "pom.xml"
    return pom.groupId.toString().replace(".", "/")
}
