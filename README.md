# Shipping logs in JSON in docker using filbeat

When logging from a docker container running a springboot application, the "normal" (i.e. raw text based) log format is often not practical. Multi-line stack traces, formatted MDCs and similar things require a lot of post processing, and even if you can do this, the results are often rigid and adapting to changes is difficult. 

A nice alternative would be to treat log messages as JSON objects rather than as lines of text. This way, it is easy to keep all structure in the data intact, and reassembling stack traces is not necessary.

For this to work, all components in the flow will have to know about the format of the input they are consuming and handle it appropriately. Here's an example of how to do this with the logback LogstashAppender and filebeat.

## Java Config

Logback is included in any springboot application by default anyways, but we need another dependency to get a JSON encoder. There are different ones available, and they should all work similarly. For this example, I'm using:

```
<dependency>
	<groupId>net.logstash.logback</groupId>
	<artifactId>logstash-logback-encoder</artifactId>
	<version>${logstash-logback-encoder.version}</version>
</dependency>
```

and then configure 

**src/main/resources/logback-spring.xml** 

as follows:

```
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
	<appender name="console" class="ch.qos.logback.core.ConsoleAppender">
		<encoder>
			<pattern>%d %-5level [%thread] %logger{0}: %msg%n</pattern>
			<outputPatternAsHeader>true</outputPatternAsHeader>
		</encoder>
		<springProfile name="json">
			<encoder class="net.logstash.logback.encoder.LogstashEncoder" />
		</springProfile>
	</appender>
	<root level="info">
		<appender-ref ref="console" />
	</root>
</configuration>
```

This will give us a normal **PatternAppender** as the default, and, if the "json" profile is enable, this will switch to logging in JSON format.

this will give us either this (default):

```
2018-07-26 12:40:42,107 INFO  [pool-1-thread-1]LoggingTestApp: Log output: 7/26/18 12:40 PM
```

Or this (with -Dspring.profiles.active=json) - albeit in one line:

```
{  
   "@timestamp":"2018-07-26T12:41:41.836+08:00",
   "@version":"1",
   "message":"Log output: 7/26/18 12:41 PM",
   "logger_name":"de.disk0.logging.test.app.LoggingTestApp",
   "thread_name":"pool-1-thread-1",
   "level":"INFO",
   "level_value":20000,
   "traceId":"a4c7a198bef419d7",
   "spanId":"a4c7a198bef419d7",
   "spanExportable":"false",
   "X-Span-Export":"false",
   "X-B3-SpanId":"a4c7a198bef419d7",
   "X-B3-TraceId":"a4c7a198bef419d7"
}
```

as you can see, we have some nice structure going on there already. This pretty much all that's needed from the JAVA side. The application does not need any further parameters, as the log is simply written to STDOUT and picked up by filebeat from there.

## Filebeat Config

In filebeat, we need to configure how filebeat will find the log files, and what metatdata is added to it. I'm using the filebeat docker auto discover for this.

```
filebeat.autodiscover:
  providers:
    - type: docker
      hints.enabled: true
      json.message_key: log  
      templates:
        - condition:
            equals:
              docker.container.labels.filebeat_enable: "true"
          config:
            - type: docker
              containers.ids:
                - "${data.docker.container.id}"
              json.keys_under_root: true
              json.add_error_key: false
processors:
- add_cloud_metadata: ~
- add_docker_metadata: ~ 
output.file:
  path: "/filebeat"
  filename: "filebeat.log"
  rotate_every_kb: 10000
  number_of_files: 7
  permissions: 0600
```

#### Filter by Label

Here, I am also setting a condition - by default, filebeat will pick up ALL container logs, with the condition as below, it will only pick up logs from containers with the appropriate tags:

```
docker.container.labels.filebeat_enable: "true"
```

and then dynamically create a new input for this with the configuration specified under the corresponding "config" section.

#### Add Infrastructure Metadata

In the "processors" section, I am adding some metadata:

```
- add_cloud_metadata: ~
```

which will add pretty much everything available (i.e. EC2 data or the like). with further configuration, you can add just the things you need. "Everything" in this case is very verbos.

#### Add Docker Metadata

Similar to "cloud" metadata, you can also add:

```
- add_docker_metadata: ~
```

which includes things like labels etc.

#### Output

The output here is configured to be in the filesystem. This is probably not what you want. In a "real" environment, you would want to ship this to ElasticSearch or something similar. Check the filebeat documentation for details on this.

## Containerize

Containerizing filebeat in rancher is pretty straightforward. You will have to create a custom image with the right filebeat.yml (make sure you chown and chmod correctly, or filebeat will complain):

```
FROM docker.elastic.co/beats/filebeat:6.3.2
ADD filebeat.yml /usr/share/filebeat/
USER root
RUN chown root /usr/share/filebeat/filebeat.yml
RUN chmod 700 /usr/share/filebeat/filebeat.yml
```

Then, I create a stack with this image, tell the scheduler to run one instance per host:

```
io.rancher.scheduler.global: 'true'
```

and mount both the docker socket and the "logs" directory:

```
    volumes:
    - /var/lib/docker/containers/:/var/lib/docker/containers
    - /var/run/docker.sock:/var/run/docker.sock
```

so the entire docker-compose file will look like this:

```
version: '2'
services:
  logging-filebeat:
    image: [your image with your filebeat.yml config]
    labels:
      io.rancher.container.agent.role: environmentAdmin
      io.rancher.container.create_agent: 'true'
      io.rancher.scheduler.global: 'true'
    volumes:
    - /var/lib/docker/containers/:/var/lib/docker/containers
    - /var/run/docker.sock:/var/run/docker.sock
```

## Output

You can see examples of the final output [here (a regular message)](msg_normal.json) and [here (with a stack trace)](msg_stacktrace.json). As you can see, any imaginable piece of metadata is available here, including the MDCs (such as the "traceID" field from Sleuth).

 

