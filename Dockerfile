FROM gcr.io/dataflow-templates-base/java11-template-launcher-base:latest

ARG WORKDIR=/template
ARG MAINCLASS=com.google.cloud.teleport.v2.templates.spannerchangestreamstobigquery.SpannerChangeStreamsToBigQuery
RUN mkdir -p ${WORKDIR}
RUN mkdir -p ${WORKDIR}/lib
WORKDIR ${WORKDIR}

COPY /v2/googlecloud-to-googlecloud/target/googlecloud-to-googlecloud-1.0-SNAPSHOT.jar /template/
COPY /v2/googlecloud-to-googlecloud/target/extra_libs/* ${WORKDIR}/lib/

ENV FLEX_TEMPLATE_JAVA_CLASSPATH=/template/googlecloud-to-googlecloud-1.0-SNAPSHOT.jar
ENV FLEX_TEMPLATE_JAVA_MAIN_CLASS=${MAINCLASS}

ENTRYPOINT ["/opt/google/dataflow/java_template_launcher"]