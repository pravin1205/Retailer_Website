// ─────────────────────────────────────────────────────────────────────────────
// Marketly — Jenkins Declarative Pipeline
//
// What this pipeline does:
//   1. Checkout source code from GitHub
//   2. Run backend tests (Maven)
//   3. Build all backend microservices into fat JARs (Maven)
//   4. Build the frontend (Node.js / Vite / TanStack Start)
//   5. Build Docker images for every service (parallel)
//   6. Push images to Docker Hub
//   7. Deploy the full stack via docker compose
//
// Agent: Linux (Jenkins runs in a Docker container on Linux)
//
// Required Jenkins credentials (Manage Jenkins → Credentials):
//   docker-registry-credentials  → Username+Password  (Docker Hub login)
//   marketly-jwt-secret           → Secret text        (JWT signing secret)
//
// Required Jenkins plugins:
//   Pipeline, Git, Docker Pipeline, Credentials Binding, NodeJS, JUnit
//
// Required Jenkins Global Tool Config (Manage Jenkins → Tools):
//   Maven  named "Maven-3.9"   → Install automatically, version 3.9.16
//   JDK    named "JDK-21"      → Install from adoptium.net, version 21
//   NodeJS named "Node-22"     → Install automatically, version 22.x
// ─────────────────────────────────────────────────────────────────────────────

pipeline {

    agent any

    // ── Tool versions ─────────────────────────────────────────────────────────
    // Names must match exactly what you set in Jenkins → Global Tool Config.
    tools {
        maven  'Maven-3.9'
        jdk    'JDK-21'
        nodejs 'Node-22'
    }

    // ── Pipeline-level env vars ───────────────────────────────────────────────
    environment {
        // Docker Hub registry prefix — images are tagged as:
        //   pravinjosh/identity-service:abc1234
        //   pravinjosh/identity-service:latest
        REGISTRY  = 'pravinjosh'

        // Image tag: short Git commit hash so every build is traceable.
        // Falls back to 'latest' if GIT_COMMIT is not set.
        IMAGE_TAG = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"

        // JWT secret injected from Jenkins credentials store.
        // Referenced in docker-compose.yml as ${JWT_SECRET}.
        JWT_SECRET = credentials('marketly-jwt-secret')
    }

    // ── Options ───────────────────────────────────────────────────────────────
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
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
                    junit allowEmptyResults: true,
                          testResults: 'backend/**/target/surefire-reports/*.xml'
                }
            }
        }

        // ── Stage 3: Backend Build JARs ───────────────────────────────────────
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

        // ── Stage 5: Docker Build (parallel) ─────────────────────────────────
        // All 9 images build in parallel to save time.
        // Backend Dockerfile accepts SERVICE_NAME as a build arg.
        // Frontend Dockerfile is at the project root.
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
        // Log in to Docker Hub, push all 9 images (commit tag + latest), log out.
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
        // Pull the freshly pushed images and restart the containers.
        // Runs docker compose from the backend/ directory in the workspace.
        //
        // NOTE: For this to work, the Jenkins container must have access to
        // the Docker socket. When running Jenkins in Docker, start it with:
        //   -v /var/run/docker.sock:/var/run/docker.sock
        //   -v /usr/bin/docker:/usr/bin/docker
        // And the docker-compose.yml + .env must be present on the host at
        // the path mounted into the Jenkins container, OR use the workspace
        // copy (which is what this stage does).
        stage('Deploy') {
            steps {
                dir('backend') {
                    sh """
                        # Write the .env file into the workspace backend/ dir
                        # so docker compose can read it when run from here.
                        cat > .env <<'ENVEOF'
DB_HOST=postgres
DB_PORT=5432
DB_NAME=marketly
DB_USERNAME=marketly
DB_PASSWORD=marketly_dev
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
JWT_SECRET=${JWT_SECRET}
JWT_ACCESS_TTL_SECONDS=900
JWT_REFRESH_TTL_DAYS=7
PLATFORM_DOMAIN=marketly.com
SMTP_HOST=smtp.mailtrap.io
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
MAIL_FROM=noreply@marketly.in
SPRING_PROFILES_ACTIVE=docker
ENVEOF

                        # Pull the latest images we just pushed
                        docker compose pull \
                            identity-service \
                            tenant-service \
                            product-service \
                            customer-service \
                            order-service \
                            notification-service \
                            analytics-service \
                            api-gateway \
                            frontend

                        # Start / recreate service containers.
                        # Infrastructure (postgres, redis, kafka) starts automatically
                        # via depends_on if not already running.
                        docker compose up -d \
                            --remove-orphans \
                            identity-service \
                            tenant-service \
                            product-service \
                            customer-service \
                            order-service \
                            notification-service \
                            analytics-service \
                            api-gateway \
                            frontend

                        # Remove dangling images to free disk
                        docker image prune -f

                        echo "Deployment complete: \$(date)"
                    """
                }
            }
        }

    } // end stages

    // ── Post-build actions ────────────────────────────────────────────────────
    post {
        success {
            echo "Build ${env.IMAGE_TAG} deployed successfully."
            echo "Frontend:    http://localhost:3000"
            echo "API Gateway: http://localhost:8081"
            echo "Kafka UI:    http://localhost:8090"
            echo "Grafana:     http://localhost:3001"
        }
        failure {
            echo "Build failed. Check the console output above for errors."
        }
        always {
            cleanWs()
        }
    }
}
