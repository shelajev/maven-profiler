# Maven Profiler

Uses [EventSpy](https://maven.apache.org/ref/3.8.5/maven-core/apidocs/org/apache/maven/eventspy/EventSpy.html) to collect CPU and memory data during the build. 

Uses [OperatingSystemMXBean](https://docs.oracle.com/en/java/javase/19/docs/api/jdk.management/com/sun/management/OperatingSystemMXBean.html) to get the data.

Uses [plotille](https://pypi.org/project/plotille/) to plot the data.

Uses [Testcontainers](https://testcontainers.org) to run Plotille.



