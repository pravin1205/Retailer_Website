// ─────────────────────────────────────────────────────────────────────────────
// Marketly — Jenkins Declarative Pipeline
//
// What this pipeline does:
//   1. Checkout source code
//   2. Build all backend microservices (Maven, parallel)
//   3. Build the frontend (Node.js / Vite / TanStack Start)
//   4. Build Docker images for every service
//   5. Push images to Docker registry
//   6. Deploy the full stack via docker-compose on the target server
//
// Required Jenkins credentials (configure in Jenkins → Credentials):
//   docker-registry-credentials  → Username+Password for your Docker registry
//   deploy-server-ssh             → SSH private key for the deploy server
//
// Required Jenkins plugins:
//   Pipeline, Git, SSH Agent, Docker Pipeline, Credentials Binding
// ─────────────────────────────────────────────────────────────────────────────

pipeline {

    agent any

    // ── Tool versions ─────────────────────────────────────────────────────────
    // These must match tool names configured in Jenkins → Global Tool Config.
    tools {
        maven 'Maven-3.9'
        jdk   'JDK-21'
        nodejs 'Node-22'
    }

    // ── Pipeline-level env vars ───────────────────────────────────────────────
    environment {
        // Docker registry — change to your registry host, e.g. docker.io/yourorg
        // or a private registry like registry.yourcompany.com
        REGISTRY      = 'docker.io/marketly'

        // Image tag: use the short Git commit hash so every build is traceable
        IMAGE_TAG     = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"

        // SSH user on the deploy server
        DEPLOY_USER   = 'ubuntu'

        // IP or hostname of the machine that runs docker-compose
        DEPLOY_HOST   = '192.168.1.100'

        // Path on the deploy server where docker-compose files live
        DEPLOY_PATH   = '/opt/marketly'

        // JWT secret — override with Jenkins secret text credential in production
        JWT_SECRET    = credentials('marketly-jwt-secret')
    }

    // ── Options ───────────────────────────────────────────────────────────────
    options {
        // Keep last 10 builds, discard older ones to save disk
        buildDiscarder(logRotator(numToKeepStr: '10'))

        // Fail the build if any stage takes longer than 30 minutes
        timeout(time: 30, unit: 'MINUTES')

        // Timestamps in console output
        timestamps()
    }

    stages {

        // ── Stage 1: Checkout ─────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                echo "Building commit: ${env.GIT_COMMIT}"
                echo "Branch: ${env.GIT_BRANCH}"
            }
        }

        // ── Stage 2: Backend Tests ────────────────────────────────────────────
        stage('Backend: Test') {
            steps {
                dir('backend') {
                    sh '''
                        mvn test \
                            --no-transfer-progress \
                            -Dspring.profiles.active=test
                    '''
                }
            }
            post {
                always {
                    // Publish JUnit test results in Jenkins
                    junit allowEmptyResults: true,
                          testResults: 'backend/**/target/surefire-reports/*.xml'
                }
            }
        }

        // ── Stage 3: Backend Build ────────────────────────────────────────────
        // Build all modules into fat jars. Tests already ran in Stage 2.
        stage('Backend: Build JARs') {
            steps {
                dir('backend') {
                    sh '''
                        mvn package \
                            -DskipTests \
                            --no-transfer-progress
                    '''
                }
            }
        }

        // ── Stage 4: Frontend Build ───────────────────────────────────────────
        stage('Frontend: Build') {
            steps {
                sh 'npm ci --ignore-scripts'
                sh 'npm run build'
            }
        }

        // ── Stage 5: Docker Build ─────────────────────────────────────────────
        // Build all images in parallel to save time.
        // The backend Dockerfile accepts SERVICE_NAME as a build arg.
        stage('Docker: Build Images') {
            parallel {

                stage('image: identity-service') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=identity-service \
                                    -t ${REGISTRY}/identity-service:${IMAGE_TAG} \
                                    -t ${REGISTRY}/identity-service:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: tenant-service') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=tenant-service \
                                    -t ${REGISTRY}/tenant-service:${IMAGE_TAG} \
                                    -t ${REGISTRY}/tenant-service:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: product-service') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=product-service \
                                    -t ${REGISTRY}/product-service:${IMAGE_TAG} \
                                    -t ${REGISTRY}/product-service:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: customer-service') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=customer-service \
                                    -t ${REGISTRY}/customer-service:${IMAGE_TAG} \
                                    -t ${REGISTRY}/customer-service:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: order-service') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=order-service \
                                    -t ${REGISTRY}/order-service:${IMAGE_TAG} \
                                    -t ${REGISTRY}/order-service:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: notification-service') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=notification-service \
                                    -t ${REGISTRY}/notification-service:${IMAGE_TAG} \
                                    -t ${REGISTRY}/notification-service:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: analytics-service') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=analytics-service \
                                    -t ${REGISTRY}/analytics-service:${IMAGE_TAG} \
                                    -t ${REGISTRY}/analytics-service:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: api-gateway') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    --build-arg SERVICE_NAME=api-gateway \
                                    -t ${REGISTRY}/api-gateway:${IMAGE_TAG} \
                                    -t ${REGISTRY}/api-gateway:latest \
                                    -f Dockerfile .
                            """
                        }
                    }
                }

                stage('image: frontend') {
                    steps {
                        sh """
                            docker build \
                                -t ${REGISTRY}/frontend:${IMAGE_TAG} \
                                -t ${REGISTRY}/frontend:latest \
                                -f Dockerfile .
                        """
                    }
                }

            } // end parallel
        }

        // ── Stage 6: Docker Push ──────────────────────────────────────────────
        stage('Docker: Push Images') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'docker-registry-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'

                    script {
                        def services = [
                            'identity-service',
                            'tenant-service',
                            'product-service',
                            'customer-service',
                            'order-service',
                            'notification-service',
                            'analytics-service',
                            'api-gateway',
                            'frontend'
                        ]
                        services.each { svc ->
                            sh "docker push ${REGISTRY}/${svc}:${IMAGE_TAG}"
                            sh "docker push ${REGISTRY}/${svc}:latest"
                        }
                    }

                    sh 'docker logout'
                }
            }
        }

        // ── Stage 7: Deploy ───────────────────────────────────────────────────
        // SSH into the deploy server, pull latest images, restart containers.
        stage('Deploy') {
            steps {
                sshagent(credentials: ['deploy-server-ssh']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} '

                            # Go to deploy directory
                            cd ${DEPLOY_PATH}

                            # Pull latest images for all services
                            docker compose pull

                            # Restart only the microservices (keeps infra containers running)
                            docker compose up -d \\
                                --no-deps \\
                                --remove-orphans \\
                                identity-service \\
                                tenant-service \\
                                product-service \\
                                customer-service \\
                                order-service \\
                                notification-service \\
                                analytics-service \\
                                api-gateway \\
                                frontend

                            # Remove dangling images to free disk
                            docker image prune -f

                            echo "Deployment complete: \$(date)"
                        '
                    """
                }
            }
        }

    } // end stages

    // ── Post-build actions ────────────────────────────────────────────────────
    post {
        success {
            echo "Build ${IMAGE_TAG} deployed successfully."
        }
        failure {
            echo "Build failed. Check console output above."
        }
        always {
            // Clean workspace after build to free disk on Jenkins agent
            cleanWs()
        }
    }
}
