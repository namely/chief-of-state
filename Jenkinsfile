#!/usr/bin/env groovy
/* This library allows you to access global jenkins functions - see https://www.github.com/namely/jenkins-shared-libraries */
@Library('jenkins-shared-libraries@v0.0.2')
import java.text.SimpleDateFormat

NODE_TAG = "alpine"
PRODUCTION_BRANCH = "master"
GIT_REPOSITORY = "git@github.com:namely/chief-of-state.git"

node(NODE_TAG) {

    stage("checkout") {
        /* clean directory and then checkout code */
        deleteDir()
        checkout scm
    }

    stage("docker login") {
        withCredentials([string(credentialsId: 'registry-namely-land', variable: 'DOCKER_LOGIN')]) {
            sh('''docker login -u="namely+jenkins" -p="$DOCKER_LOGIN" registry.namely.land''')
        }
    }

    stage("checking environment") {
        sh('''printenv | sort''')
        sh('''
            git remote -v
            git tag --points-at HEAD
            git rev-parse --abbrev-ref HEAD
            git branch
        ''')
    }

    stage("build params") {
        sh('''
            bash -c "printenv | grep -ie '^(BRANCH|BUILD|CHANGE|TAG)' | sort > .build.env"
        ''')

        sh('''cat .build.env''')
    }


    stage("get earth") {
        sh('wget https://github.com/earthly/earthly/releases/latest/download/earth-linux-amd64 -O /usr/local/bin/earth && chmod +x /usr/local/bin/earth')
        sh('earth --version')
    }

    stage("earth") {
        withCredentials([
            string(credentialsId: 'data-jfrog-username', variable: 'JFROG_USERNAME'),
            string(credentialsId: 'data-jfrog-password', variable: 'JFROG_PASSWORD')
        ]) {
            sh('''
                earth \
                -s JFROG_USERNAME \
                -s JFROG_PASSWORD \
                +all
            ''')
        }
    }
}
