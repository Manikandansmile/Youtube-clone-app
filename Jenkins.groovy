pipeline {
    agent any
    tools {
        jdk 'jdk17'
        nodejs 'node16'
    }
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
    }
    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }
        stage('Checkout from Git') {
            steps {
                git branch: 'main', url: 'https://github.com/Manikandansmile/Youtube-clone-app.git'
            }
        }
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar') {
                    sh "$SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=youtube -Dsonar.projectKey=youtube"
                }
            }
        }
        stage('Install Dependencies') {
            steps {
                sh 'npm install'
            }
        }
        stage('OWASP Dependency Check') {
            steps {
                dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: 'DP-Check'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        stage('TRIVY FS SCAN') {
            steps {
                sh 'trivy fs . > trivyfs.txt'
            }
        }
    stage('Docker Build & Push') {
    steps {
        script {
            withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                sh 'docker build --build-arg X-RapidAPI-Key:cb558e2577mshba45ad288a23b01p142a07jsn9ad26dad7210 -t youtube .'
                sh 'docker tag youtube manikandan93smily/youtube:latest'
                sh 'docker push manikandan93smily/youtube:latest'
                    }
                }
            }
        }
        stage('TRIVY Image Scan') {
            steps {
                sh 'trivy image manikandan93smily/youtube:latest > trivyimage.txt'
            }
        }
        stage('Remove Previous Container') {
            steps {
                sh 'docker rm -f youtube || true'
            }
        }
        stage('Deploy to Container') {
            steps {
                sh 'docker run -itd --name youtube -p 8081:3000 --restart unless stopped manikandan93smily/youtube:latest'
            }
        }
    }
    post {
        always {
            script {
                def result = currentBuild.result ?: 'SUCCESS'
                
                // Notification message
                def body = "Jenkins Pipeline Execution Summary:\n\n"
                body += "Status: ${result}\n\n"
                
                // Read Trivy image scan report
                def trivyReport = readFile('trivyimage.txt')
                body += "Trivy Image Scan Report:\n"
                body += trivyReport
                
                // Send email notification
                emailext body: body,
                         subject: "Jenkins Pipeline ${result}",
                         to: 'maniraja802@gmail.com'
            }
        }
    }
}
