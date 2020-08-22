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
        environment {
            JFROG_USERNAME = credentials('data-jfrog-username')
            JFROG_PASSWORD = credentials('data-jfrog-password')
            EARTHLY_SECRETS = 'JFROG_USERNAME,JFROG_PASSWORD'
        }

        sh('''
            [ -z "\$JFROG_USERNAME" ] && echo "JFROG_USERNAME empty"
            [ -z "\$JFROG_PASSWORD" ] && echo "JFROG_PASSWORD empty"
            [ -z "\$EARTHLY_SECRETS" ] && echo "EARTHLY_SECRETS empty"
        ''')

        // TODO: use earthly secrets
        // sh('''
        //     touch .env
        //     echo "JFROG_USERNAME=$JFROG_USERNAME" >> .env
        //     echo "JFROG_PASSWORD=$JFROG_PASSWORD" >> .env
        // ''')

        sh('''
            earth \
            --secret JFROG_USERNAME=\$JFROG_USERNAME \
            --secret JFROG_PASSWORD=\$JFROG_PASSWORD \
            +all
        ''')

    }
}
