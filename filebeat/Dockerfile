FROM docker.elastic.co/beats/filebeat:6.3.2
ADD filebeat.yml /usr/share/filebeat/
USER root
RUN chown root /usr/share/filebeat/filebeat.yml
RUN chmod 700 /usr/share/filebeat/filebeat.yml