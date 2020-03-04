# Base Alpine Linux based image with OpenJDK JRE only
FROM openjdk:13-alpine
# copy application WAR (with libraries inside)
COPY target/sfdc-cdc-sub-aws-*-SNAPSHOT-phat.jar /app.jar
# specify default command
CMD ["java", "-jar", "-Dspring.profiles.active=test", "/app.jar", "/data/ChangeEvents", "-2"]