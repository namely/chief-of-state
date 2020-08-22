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

    // stage("earth 1") {
    //     environment {
    //         JFROG_USERNAME = credentials('data-jfrog-username')
    //         JFROG_PASSWORD = credentials('data-jfrog-password')
    //         EARTHLY_SECRETS = 'JFROG_USERNAME,JFROG_PASSWORD'
    //     }

    //     sh("""
    //         [ -z "$JFROG_USERNAME" ] && echo "JFROG_USERNAME empty"
    //         [ -z "$JFROG_PASSWORD" ] && echo "JFROG_PASSWORD empty"
    //         [ -z "$EARTHLY_SECRETS" ] && echo "EARTHLY_SECRETS empty"
    //     """)

    //     sh('''
    //         earth \
    //         --secret JFROG_USERNAME=\$JFROG_USERNAME \
    //         --secret JFROG_PASSWORD=\$JFROG_PASSWORD \
    //         --secret SOME_SECRET=xxx \
    //         --no-cache \
    //         +code
    //     ''')
    // }

    // stage("earth 2") {
    //     withCredentials([
    //         string(credentialsId: 'data-jfrog-username', variable: 'JFROG_USERNAME'),
    //         string(credentialsId: 'data-jfrog-password', variable: 'JFROG_PASSWORD')
    //     ]) {
    //         sh('''
    //             earth \
    //             --secret JFROG_USERNAME \
    //             --secret JFROG_PASSWORD \
    //             --secret SOME_SECRET=xxx \
    //             --no-cache \
    //             +code
    //         ''')
    //     }
    // }

    stage("earth 3") {
        withCredentials([
            string(credentialsId: 'data-jfrog-username', variable: 'JFROG_USERNAME'),
            string(credentialsId: 'data-jfrog-password', variable: 'JFROG_PASSWORD')
        ]) {
            sh('''
                earth \
                -s JFROG_USERNAME \
                -s JFROG_PASSWORD \
                -s SOME_SECRET=xxx \
                --no-cache \
                +code
            ''')
        }
    }
}
