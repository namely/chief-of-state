#!/usr/bin/env groovy
/* This library allows you to access global jenkins functions - see https://www.github.com/namely/jenkins-shared-libraries */
@Library('jenkins-shared-libraries@v0.0.2')
import java.text.SimpleDateFormat

NODE_TAG = "alpine"
PRODUCTION_BRANCH = "master"
GIT_REPOSITORY = "git@github.com:namely/chief-of-state.git"

node(NODE_TAG) {

    stage("Checkout") {
        /* clean directory and then checkout code */
        deleteDir()
        checkout scm
    }

    stage("get earth") {
        sh('wget https://github.com/earthly/earthly/releases/latest/download/earth-linux-amd64 -O /usr/local/bin/earth && chmod +x /usr/local/bin/earth')
    }

    stage("env stuff") {
        sh('''printenv | sort''')
    }

    stage("git stuff") {
        sh('''
            git remote -v
            git tag --points-at HEAD
            git branch --show-current
        ''')
    }

    // stage("earth") {
    //     withCredentials([
    //         string(credentialsId: 'data-jfrog-username', variable: 'JFROG_USERNAME'),
    //         string(credentialsId: 'data-jfrog-password', variable: 'JFROG_PASSWORD')
    //     ]) {
    //         sh('''
    //             earth \
    //             -s JFROG_USERNAME \
    //             -s JFROG_PASSWORD \
    //             +all
    //         ''')
    //     }
    // }
}
