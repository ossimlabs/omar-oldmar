properties([
    parameters([
        string(name: 'PROJECT_URL', defaultValue: 'https://github.com/ossimlabs/omar-oldmar', description: 'The project github URL'),
        string(name: 'DOCKER_REGISTRY_DOWNLOAD_URL', defaultValue: 'nexus-docker-private-group.ossim.io', description: 'Repository of docker images')
    ]),
    pipelineTriggers([
        [$class: "GitHubPushTrigger"]
    ]),
    [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: '${PROJECT_URL}'],
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '3', daysToKeepStr: '', numToKeepStr: '20')),
    disableConcurrentBuilds()
    ])

podTemplate(
    containers: [
        containerTemplate(
            name: 'docker',
            image: 'docker:19.03.11',
            ttyEnabled: true,
            command: 'cat',
            privileged: true
        ),
        containerTemplate(
            image: "${DOCKER_REGISTRY_DOWNLOAD_URL}/omar-builder:jdk11",
            name: 'builder',
            command: 'cat',
            ttyEnabled: true
        ),
        containerTemplate(
            image: "${DOCKER_REGISTRY_DOWNLOAD_URL}/alpine/helm:3.2.3",
            name: 'helm',
            command: 'cat',
            ttyEnabled: true
        ),
        containerTemplate(
            name: 'git',
            image: 'alpine/git:latest',
            ttyEnabled: true,
            command: 'cat',
            envVars: [
                envVar(key: 'HOME', value: '/root')
                ]
        ),
        containerTemplate(
            name: 'cypress',
            image: "${DOCKER_REGISTRY_DOWNLOAD_URL}/cypress/included:4.9.0",
            ttyEnabled: true,
            command: 'cat',
            privileged: true
        )
      ],
    volumes: [
        hostPathVolume(
            hostPath: '/var/run/docker.sock',
            mountPath: '/var/run/docker.sock'
        ),
    ]
)

{
  node(POD_LABEL){

    stage("Checkout branch") {
        APP_NAME = PROJECT_URL.tokenize('/').last()
        scmVars = checkout(scm)
        Date date = new Date()
        String currentDate = date.format("YYYY-MM-dd-HH-mm-ss")
        MASTER = "master"
        GIT_BRANCH_NAME = scmVars.GIT_BRANCH
        BRANCH_NAME = """${sh(returnStdout: true, script: "echo ${GIT_BRANCH_NAME} | awk -F'/' '{print \$2}'").trim()}"""
        VERSION = """${sh(returnStdout: true, script: "cat chart/Chart.yaml | grep version: | awk -F'version:' '{print \$2}'").trim()}"""
        GIT_TAG_NAME = APP_NAME + "-" + VERSION
        ARTIFACT_NAME = "ArtifactName"

            if (BRANCH_NAME == "${MASTER}") {
                buildName "${VERSION}"
                TAG_NAME = "${VERSION}"
            }
            else {
                buildName "${BRANCH_NAME}-${currentDate}"
                TAG_NAME = "${BRANCH_NAME}-${currentDate}"
            }
        
    }

    stage("Load Variables") {
        withCredentials([string(credentialsId: 'o2-artifact-project', variable: 'o2ArtifactProject')]) {
            step ([$class: "CopyArtifact",
                projectName: o2ArtifactProject,
                filter: "common-variables.groovy",
                flatten: true])
        }
        load "common-variables.groovy"
        DOCKER_IMAGE_PATH = "${DOCKER_REGISTRY_PRIVATE_UPLOAD_URL}/${APP_NAME}"
    }
      
      stage ("Run Cypress Test") {
        container('cypress') {
            try {
                sh """
                    cypress run --headless
                """
            } catch (err) {
                sh """
                    npm i -g xunit-viewer
                    xunit-viewer -r results -o results/omar-oldmar-test-results.html
                """
                junit 'results/*.xml'
                archiveArtifacts "results/*.xml"
                archiveArtifacts "results/*.html"
                s3Upload(file:'results/omar-oldmar-test-results.html', bucket:'ossimlabs', path:'cypressTests/')
            }
        }
    }

    stage('Build') {
      container('builder') {
        sh """
        ./gradlew assemble \
            -PossimMavenProxy=${MAVEN_DOWNLOAD_URL}
        ./gradlew copyJarToDockerDir \
            -PossimMavenProxy=${MAVEN_DOWNLOAD_URL}
        """
        archiveArtifacts "apps/*/build/libs/*.jar"
      }
    }

    

/*
    stage ("Publish Nexus"){	
      container('builder'){
          withCredentials([[$class: 'UsernamePasswordMultiBinding',
                          credentialsId: 'nexusCredentials',
                          usernameVariable: 'MAVEN_REPO_USERNAME',
                          passwordVariable: 'MAVEN_REPO_PASSWORD']])
          {
            sh """
            ./gradlew publish \
                -PossimMavenProxy=${MAVEN_DOWNLOAD_URL}
            """
          }
        }
    }
*/

    stage('Docker build') {
      container('docker') {
        withDockerRegistry(credentialsId: 'dockerCredentials', url: "https://${DOCKER_REGISTRY_DOWNLOAD_URL}") {  //TODO
          if (BRANCH_NAME == 'master'){
                sh """
                    docker build --network=host -t "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:"${VERSION}" ./docker
                """
          }
          else {
                sh """
                    docker build --network=host -t "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:"${VERSION}".a ./docker
                """
          }
        }
      }
    }

    stage('Docker push'){
        container('docker') {
          withDockerRegistry(credentialsId: 'dockerCredentials', url: "https://${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}") {
            if (BRANCH_NAME == 'master'){
                sh """
                    docker push "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:"${VERSION}"
                """
            }
            else if (BRANCH_NAME == 'dev') {
                sh """
                    docker tag "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:"${VERSION}".a "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:dev
                    docker push "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:"${VERSION}".a
                    docker push "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:dev
                """
            }
            else {
                sh """
                    docker push "${DOCKER_REGISTRY_PUBLIC_UPLOAD_URL}"/omar-oldmar:"${VERSION}".a           
                """
            }
          }
        }
      }

    stage('Package chart'){
      container('helm') {
        sh """
            mkdir packaged-chart
            helm package -d packaged-chart chart
          """
      }
    }
    
    stage('Upload chart'){
      container('builder') {
        withCredentials([usernameColonPassword(credentialsId: 'helmCredentials', variable: 'HELM_CREDENTIALS')]) {
          sh "curl -u ${HELM_CREDENTIALS} ${HELM_UPLOAD_URL} --upload-file packaged-chart/*.tgz -v"
        }
      }
    }
      
    /*  
    stage('New Deploy'){
        container('kubectl-aws-helm') {
            withAWS(
            credentials: 'Jenkins-AWS-IAM',
            region: 'us-east-1'){
                if (BRANCH_NAME == 'master'){
                    //insert future instructions here
                }
                else if (BRANCH_NAME == 'dev') {
                    sh "aws eks --region us-east-1 update-kubeconfig --name gsp-dev-v2 --alias dev"
                    sh "kubectl config set-context dev --namespace=omar-dev"
                    sh "kubectl rollout restart deployment/omar-oldmar"   
                }
                else {
                    sh "echo Not deploying ${BRANCH_NAME} branch"
                }
            }
        }
    } 
    */

    stage("Clean Workspace"){
      if ("${CLEAN_WORKSPACE}" == "true")
        step([$class: 'WsCleanup'])
    }
  }
}
