#!/usr/bin/env groovy
/* This library allows you to access global jenkins functions - see https://www.github.com/namely/jenkins-shared-libraries */
@Library('jenkins-shared-libraries@v0.0.2')
import java.text.SimpleDateFormat

NODE_TAG = "alpine"
PRODUCTION_BRANCH = "master"
GIT_REPOSITORY = "git@github.com:namely/chief-of-state.git"

node(NODE_TAG) {
    /* clean directory and then checkout code */
    deleteDir()
    checkout scm
    /* reach out to jenkins-shared library for boilerplate setup */
    (COMMIT_HASH, COMMIT_HASH_WITH_SUFFIX) = prep.checkout(true)

    stage("get earth") {
        sh('wget https://github.com/earthly/earthly/releases/latest/download/earth-linux-amd64 -O /usr/local/bin/earth && chmod +x /usr/local/bin/earth')
    }

    stage("earth") {
        sh('earth +code')
    }
}
