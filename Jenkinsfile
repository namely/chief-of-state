#!/usr/bin/env groovy
/* This library allows you to access global jenkins functions - see https://www.github.com/namely/jenkins-shared-libraries */
@Library('jenkins-shared-libraries@add-earthly-helpers')
import java.text.SimpleDateFormat

NODE_TAG = "alpine"
PRODUCTION_BRANCH = "master"
GIT_REPOSITORY = "git@github.com:namely/chief-of-state.git"
CODECOV_TOKEN = "a2d86796-50b0-4244-a850-d96ede6f917e"
EARTH_VERSION = "latest"

node(NODE_TAG) {

    stage("checkout") {
        /* clean directory and then checkout code */
        deleteDir()
        checkout scm
    }

    stage("docker login") {
        dockerTools.dockerLogin('registry-namely-land')
    }

    stage("build info") {
        buildInfo = tagging.getBuildInfo()
    }

    stage("earth") {
        earthly.downloadEarth(EARTH_VERSION)

        // define earth runner
        earthRunner = earthly.getRunner()

        // add constant args
        earthRunner.addBuildArg("VERSION", buildInfo.version)
        earthRunner.addBuildArg("COMMIT_HASH", buildInfo.commitHash)
        earthRunner.addBuildArg("CODECOV_TOKEN", CODECOV_TOKEN)

        earthRunner.addSecret("JFROG_USERNAME")
        earthRunner.addSecret("JFROG_PASSWORD")

        // add dynamic args
        if(buildInfo.shouldPush()) {
            earthRunner.addArg("--push")
        }

        // provide a context with the secrets we need as env vars
        withCredentials([
            string(credentialsId: 'data-jfrog-username', variable: 'JFROG_USERNAME'),
            string(credentialsId: 'data-jfrog-password', variable: 'JFROG_PASSWORD')
        ]) {
            // run the earthly command that was built
            sh earthRunner.getCommand("+all")
        }
    }
}
