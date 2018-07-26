FROM rmalchow/openjdk8-springboot-docker
ADD target/logging-test-app.jar /usr/share/app/
ENTRYPOINT /assets/start.sh -j /usr/share/app/logging-test-app.jar